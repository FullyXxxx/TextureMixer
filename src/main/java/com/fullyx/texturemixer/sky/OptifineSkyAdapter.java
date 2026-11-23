package com.fullyx.texturemixer.sky;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import java.io.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class OptifineSkyAdapter {
    private static final Set<String> KNOWN=Set.of("source","startFadeIn","endFadeIn","startFadeOut","endFadeOut",
            "blend","rotate","speed","axis","days","daysLoop","weather","biomes","heights","transition");
    public SkyScene parse(String dimension,Assets.View view) throws IOException {
        return parse(dimension,view,true);
    }
    public SkyScene parsePreview(String dimension,Assets.View view) throws IOException {
        return parse(dimension,view,false);
    }
    private SkyScene parse(String dimension,Assets.View view,boolean validatePixels) throws IOException {
        String folder="optifine/sky/world"+(dimension.equals("end")?"1":"0")+"/";
        List<SkyScene.Layer> layers=new ArrayList<>();Map<AssetId,byte[]> textures=new TreeMap<>();List<String> notes=new ArrayList<>();
        boolean own=false;
        Map<String,SkyScene.Blend> celestialBlends=new HashMap<>();
        List<AssetId> definitions=new ArrayList<>();
        Set<AssetId> ids=view.ids();
        for(AssetId id:ids)if(id.namespace().equals("minecraft")&&id.path().startsWith(folder)&&id.path().endsWith(".properties")){
            String name=id.path().substring(folder.length());
            if(!name.contains("/")&&!Set.of("sun.properties","moon_phases.properties").contains(name))definitions.add(id);
        }
        definitions.sort(Comparator.comparingInt(OptifineSkyAdapter::layerOrder).thenComparing(AssetId::path));
        if(view.source()!=null) for(AssetId id:view.source().ids()) {
            if(id.path().startsWith("sky/") && id.path().endsWith(".json") || id.path().startsWith("fabricskyboxes/") || id.path().startsWith("nuit/")) {
                if(definitions.isEmpty())throw new IOException(tr("不支持 Nuit/FSB 天空格式: ")+id);
                notes.add(tr("使用包内 OptiFine 天空，附带的 Nuit/FSB 定义未参与混搭"));
                break;
            }
        }
        for(AssetId definition:definitions) {
            if(layers.size()>=32) throw new IOException(tr("每维度天空最多 32 层"));
            Properties p=properties(view,definition);
            for(String key:p.stringPropertyNames()) if(!KNOWN.contains(key)) throw new IOException(tr("不支持的天空属性 ")+key+" in "+definition);
            AssetId texture=resolve(definition,p.getProperty("source","./"+definition.path().substring(folder.length(),definition.path().length()-11)+".png"));
            byte[] data=view.read(texture);
            if(data==null) {
                notes.add(tr("天空图层缺少贴图，已跳过: ")+definition+" → "+texture);
                continue;
            }
            var image=DirectorySource.pngSize(texture,data,validatePixels);
            if(image.width()%3!=0 || image.height()%2!=0 || image.width()/3!=image.height()/2)
                throw new IOException(tr("天空必须是 3×2 等边六面 PNG: ")+texture);
            textures.put(texture,data);
            double[] axis=vector(p.getProperty("axis","0 0 1"));
            int s=time(p,"startFadeIn"),e=time(p,"endFadeIn"),o=time(p,"startFadeOut"),f=time(p,"endFadeOut");
            if(s>=0 || e>=0 || o>=0 || f>=0) {
                if(s<0 || e<0 || f<0) throw new IOException(tr("天空淡入淡出缺少必要时间: ")+definition);
                if(o<0) o=(int)SkyScene.mod(f-SkyScene.mod(e-s,24000),24000);
                double a=SkyScene.mod(e-s,24000),b=SkyScene.mod(o-e,24000),c=SkyScene.mod(f-o,24000),d=SkyScene.mod(s-f,24000);
                if(a+b+c+d!=24000) throw new IOException(tr("天空淡入淡出时间顺序无效: ")+definition);
            }
            String biomeText=p.getProperty("biomes","").trim();boolean exclude=biomeText.startsWith("!");
            Set<String> biomes=new LinkedHashSet<>();for(String b:words(exclude?biomeText.substring(1):biomeText)) biomes.add(AssetId.of(b).toString());
            int daysLoop=integer(p,"daysLoop",8);if(daysLoop<1) throw new IOException(tr("daysLoop 必须为正整数"));
            Set<String> weather=new LinkedHashSet<>(words(p.getProperty("weather","clear")));
            if(!Set.of("clear","rain","thunder").containsAll(weather)) throw new IOException(tr("无效天空天气条件"));
            SkyScene.Blend blend;
            try { blend=SkyScene.Blend.valueOf(p.getProperty("blend","add").toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException error) { throw new IOException(tr("不支持的天空混合模式"),error); }
            layers.add(new SkyScene.Layer(texture,blend,bool(p,"rotate",true),number(p,"speed",1),axis[0],axis[1],axis[2],
                    s,e,o,f,ranges(p.getProperty("days","")),daysLoop,weather,biomes,exclude,ranges(p.getProperty("heights","")),Math.max(0,number(p,"transition",1))));
            own|=view.fromSource(definition)||view.fromSource(texture);
        }
        for(AssetId id:ids) if(id.namespace().equals("minecraft") && (dimension.equals("overworld")
                ? id.path().startsWith("textures/environment/celestial/")&&!id.path().endsWith("end_flash.png")||id.path().equals("textures/environment/clouds.png")
                        ||view.vanilla().contains(id)&&Set.of("textures/environment/sun.png","textures/environment/moon_phases.png").contains(id.path())
                : Set.of("textures/environment/end_sky.png","textures/environment/celestial/end_flash.png").contains(id.path()))) {
            byte[] data=view.read(id);if(data!=null) { DirectorySource.pngSize(id,data,validatePixels);textures.put(id,data);own|=view.fromSource(id); }
        }
        for(String object:List.of("sun","moon_phases")) {
            AssetId definition=AssetId.of(folder+object+".properties");
            if(view.read(definition)==null) continue;
            if(dimension.equals("end"))throw new IOException(tr("首版末地天空不支持 sun/moon properties；请使用六面图层"));
            Properties p=properties(view,definition);
            for(String key:p.stringPropertyNames())if(!Set.of("source","blend").contains(key))throw new IOException(tr("不支持的天体属性 ")+key+" in "+definition);
            try{celestialBlends.put(object,SkyScene.Blend.valueOf(p.getProperty("blend","add").toUpperCase(Locale.ROOT)));}
            catch(IllegalArgumentException error){throw new IOException(tr("不支持的天体混合模式: ")+definition,error);}
            AssetId texture=resolve(definition,p.getProperty("source","~/sky/world"+(dimension.equals("end")?1:0)+"/"+object+".png"));
            byte[] data=view.read(texture);if(data==null) throw new IOException(tr("天体图像缺失: ")+texture);
            DirectorySource.pngSize(texture,data,validatePixels);
            if(object.equals("sun")) textures.put(AssetId.of("textures/environment/celestial/sun.png"),data);
            else splitMoon(textures,data);
            own|=view.fromSource(definition)||view.fromSource(texture);
        }
        return new SkyScene(dimension,layers,textures,own,notes,celestialBlends);
    }
    private static int layerOrder(AssetId id){
        String name=id.path().substring(id.path().lastIndexOf('/')+1);
        if(name.matches("sky[0-9]+\\.properties"))try{return Integer.parseInt(name.substring(3,name.length()-11));}catch(NumberFormatException ignored){}
        return Integer.MAX_VALUE;
    }
    private static void splitMoon(Map<AssetId,byte[]> textures,byte[] data) throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(data));
        if(image.getWidth()%4!=0 || image.getHeight()%2!=0) throw new IOException(tr("旧月相贴图必须为 4×2 布局"));
        String[] names={"full_moon","waning_gibbous","third_quarter","waning_crescent","new_moon","waxing_crescent","first_quarter","waxing_gibbous"};
        int w=image.getWidth()/4,h=image.getHeight()/2;
        for(int i=0;i<8;i++) {var out=new ByteArrayOutputStream();ImageIO.write(image.getSubimage(i%4*w,i/4*h,w,h),"PNG",out);
            textures.put(AssetId.of("textures/environment/celestial/moon/"+names[i]+".png"),out.toByteArray());}
    }
    public static AssetId resolve(AssetId definition,String source) throws IOException {
        try {
            AssetId id;
            if(source.startsWith("./")) id=definition.withPath(definition.path().substring(0,definition.path().lastIndexOf('/')+1)+source.substring(2));
            else if(source.startsWith("~/")) id=AssetId.of("optifine/"+source.substring(2));
            else if(source.contains(":"))id=AssetId.of(source);
            else if(source.startsWith("textures/")||source.startsWith("optifine/"))id=AssetId.of(source);
            else if(source.startsWith("/"))id=AssetId.of(source.substring(1));
            else id=definition.withPath(definition.path().substring(0,definition.path().lastIndexOf('/')+1)+source);
            if(!id.path().endsWith(".png")) id=id.withPath(id.path()+".png");
            return id;
        } catch(IllegalArgumentException error) { throw new IOException(tr("无效天空资源路径: ")+source,error); }
    }
    private static Properties properties(Assets view,AssetId id) throws IOException {
        Properties p=new Properties();byte[] data=view.read(id);if(data==null)throw new IOException("Missing "+id);
        p.load(new ByteArrayInputStream(data));return p;
    }
    private static List<String> words(String value) { return value.isBlank()?List.of():List.of(value.trim().split("\\s+")); }
    private static int time(Properties p,String key) throws IOException {
        if(!p.containsKey(key))return -1;
        try {String[] v=p.getProperty(key).split(":");int h=Integer.parseInt(v[0]),m=Integer.parseInt(v[1]);
            if(v.length!=2||h<0||h>23||m<0||m>59)throw new IllegalArgumentException();
            return (int)SkyScene.mod((h*60+m-360)*1000.0/60,24000);
        }catch(RuntimeException e){throw new IOException(tr("无效时间 ")+key,e);}
    }
    private static double number(Properties p,String key,double fallback) throws IOException {
        try {double v=Double.parseDouble(p.getProperty(key,Double.toString(fallback)));if(!Double.isFinite(v))throw new IllegalArgumentException();return v;}
        catch(RuntimeException e){throw new IOException(tr("无效数值 ")+key,e);}
    }
    private static int integer(Properties p,String key,int fallback) throws IOException {
        double v=number(p,key,fallback);if(v!=Math.rint(v))throw new IOException(tr("无效整数 ")+key);return (int)v;
    }
    private static boolean bool(Properties p,String key,boolean fallback) throws IOException {
        String value=p.getProperty(key,Boolean.toString(fallback));if(!value.equals("true")&&!value.equals("false"))throw new IOException(tr("无效布尔值 ")+key);return Boolean.parseBoolean(value);
    }
    private static double[] vector(String text) throws IOException {
        try {String[] values=text.trim().split("\\s+");if(values.length!=3)throw new IllegalArgumentException();
            double[] a=Arrays.stream(values).mapToDouble(Double::parseDouble).toArray();double n=Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);
            if(!Double.isFinite(n)||n==0)throw new IllegalArgumentException();for(int i=0;i<3;i++)a[i]/=n;return a;
        }catch(RuntimeException e){throw new IOException(tr("无效天空旋转轴: ")+text,e);}
    }
    private static List<SkyScene.Range> ranges(String text) throws IOException {
        List<SkyScene.Range> result=new ArrayList<>();
        for(String word:words(text))try {
            var matcher=java.util.regex.Pattern.compile("(\\(?-?\\d+\\)?)(?:-(\\(?-?\\d+\\)?))?").matcher(word);
            if(!matcher.matches())throw new IllegalArgumentException();
            int a=Integer.parseInt(matcher.group(1).replace("(","").replace(")",""));
            int b=matcher.group(2)==null?a:Integer.parseInt(matcher.group(2).replace("(","").replace(")",""));
            if(b<a)throw new IllegalArgumentException();result.add(new SkyScene.Range(a,b));
        }catch(RuntimeException e){throw new IOException(tr("无效天空范围: ")+word,e);}
        return result;
    }
}
