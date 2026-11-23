package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;

/** A perspective view of the scene, not the source pack icon or the unfolded cubemap. */
public final class SkyPreviewCache implements AutoCloseable {
    public static final SkyScene.Context DEFAULT_CONTEXT=new SkyScene.Context(18000,0,0,0,"minecraft:plains",80);
    public static final class Entry {
        public String error;
        public SkyScene scene;
        public Identifier texture;
        private boolean discarded;
        private Request request;
    }
    private record Key(String dimension,String source,SkyScene.Context context) {}
    private record Request(Key key,Entry entry) {}
    private record Prepared(SkyScene scene,NativeImage image) {}
    private static final int SIZE=128, MAX_ENTRIES=48, MAX_WARM=5;
    private static final List<String> TARGETS=List.of("overworld","end","weather");
    private final MixerService service;
    private final LinkedHashMap<Key,Entry> entries=new LinkedHashMap<>(48,.75f,true);
    private final Deque<Request> visible=new ArrayDeque<>(),background=new ArrayDeque<>();
    private final List<Identifier> retired=new ArrayList<>();
    private boolean working,closed;
    public SkyPreviewCache(MixerService service){this.service=service;}
    public Entry getIfPresent(String dimension,String source,SkyScene.Context context){
        return entries.get(key(dimension,source,context));
    }
    public Entry get(String dimension,String source,int time,int weather){
        return get(dimension,source,new SkyScene.Context(time,0,weather>0?1:0,weather>1?1:0,"minecraft:plains",80));
    }
    public Entry get(String dimension,String source,SkyScene.Context context){
        return request(key(dimension,source,context),false);
    }
    public void warm(MixerConfig mix,String source,SkyScene.Context context){
        if(!service.preloadEnabled()||service.busy||service.vanilla==null)return;
        for(String dimension:TARGETS){
            String selected=source==null?mix.source(MixerConfig.Kind.SKY,dimension):source;
            if(background.size()<MAX_WARM)request(key(dimension,selected,context),true);
        }
    }
    public void warmVanilla(String dimension,SkyScene.Context context){
        if(service.preloadEnabled()&&background.size()<MAX_WARM)
            request(key(dimension,MixerConfig.VANILLA,context),true);
    }
    private static Key key(String dimension,String source,SkyScene.Context context){
        return new Key(dimension,source,dimension.equals("weather")?DEFAULT_CONTEXT:context);
    }
    private Entry request(Key key,boolean warm){
        Entry entry=entries.get(key);
        if(entry!=null){
            if(!warm&&entry.request!=null&&background.remove(entry.request))visible.addFirst(entry.request);
            return entry;
        }
        entry=new Entry();
        Request request=new Request(key,entry);entry.request=request;
        entries.put(key,entry);
        if(warm)background.addLast(request);else visible.addLast(request);
        if(entries.size()>MAX_ENTRIES){
            var iterator=entries.entrySet().iterator();
            discard(iterator.next().getValue());
            iterator.remove();
        }
        return entry;
    }
    public void tick(boolean browsing){
        if(closed||service.busy||service.vanilla==null)return;
        for(Identifier id:retired)service.client.getTextureManager().release(id);
        retired.clear();
        if(!browsing)warm(service.committed,null,DEFAULT_CONTEXT);
        if(working)return;
        Request next=visible.pollFirst();
        if(next==null&&service.preloadEnabled())next=background.pollFirst();
        if(next!=null)start(next);
    }
    private void start(Request request){
        Entry entry=request.entry();entry.request=null;working=true;
        Key key=request.key();
        CompletableFuture.supplyAsync(()->{
            try{
                Assets.View view=Generation.view(key.source(),service.sources,service.vanilla);
                SkyScene scene;
                if(key.dimension().equals("weather")) {
                    var result=new ResourceCompiler(view,"weather-preview").compile(MixerConfig.Kind.SKY,"weather");
                    Map<AssetId,byte[]> textures=new TreeMap<>();
                    for(AssetId id:result.images())textures.put(id,result.files().get(id));
                    scene=new SkyScene("weather",List.of(),textures,result.custom(),List.of());
                } else scene=new OptifineSkyAdapter().parsePreview(key.dimension(),view);
                return new Prepared(scene,render(scene,key.context(),SIZE));
            }catch(Exception e){throw new CompletionException(e);}
        },service.workers).whenCompleteAsync((prepared,error)->{
            working=false;
            if(closed||entry.discarded){if(prepared!=null)prepared.image().close();return;}
            if(error!=null){entry.error=MixerService.message(error);return;}
            SkyScene scene=prepared.scene();
            entry.scene=new SkyScene(scene.dimension(),scene.layers(),Map.of(),scene.fromSource(),scene.diagnostics(),scene.celestialBlends());
            try{
                Identifier texture=Identifier.fromNamespaceAndPath("texturemixer","sky_preview/"+UUID.randomUUID());
                service.client.getTextureManager().register(texture,VersionedTexture.create("TextureMixer sky preview",prepared.image()));
                entry.texture=texture;
            }catch(Exception e){prepared.image().close();entry.error=MixerService.message(e);}
        },service.client);
    }
    public void draw(Entry entry,GuiGraphicsExtractor context,int x,int y,int size){
        if(entry.texture!=null)com.fullyx.texturemixer.client.NativeCompat.drawGuiTexture(context,entry.texture,x,y,0,0,size,size,128,128,128,128);
    }
    private static NativeImage render(SkyScene scene,SkyScene.Context context,int size)throws IOException{
        if(scene.dimension().equals("weather"))return renderWeather(scene,size);
        var activeLayers=scene.layers().stream().filter(layer->layer.alpha(context)>0)
                .map(layer->new ActiveLayer(layer,layer.alpha(context),Math.cos(layer.rotation(context)),-Math.sin(layer.rotation(context)))).toList();
        Set<AssetId> used=new HashSet<>();
        for(var layer:activeLayers)used.add(layer.layer().texture());
        if(scene.dimension().equals("end"))used.add(AssetId.of("textures/environment/end_sky.png"));
        else {
            used.add(AssetId.of("textures/environment/celestial/sun.png"));
            used.add(AssetId.of("textures/environment/clouds.png"));
        }
        String[] phases={"full_moon","waning_gibbous","third_quarter","waning_crescent","new_moon","waxing_crescent","first_quarter","waxing_gibbous"};
        AssetId moonId=AssetId.of("textures/environment/celestial/moon/"+phases[Math.floorMod(context.day(),8)]+".png");
        if(scene.dimension().equals("overworld"))used.add(moonId);
        Map<AssetId,BufferedImage> images=new HashMap<>();
        for(AssetId id:used){
            byte[] bytes=scene.textures().get(id);
            if(bytes==null)continue;
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
            if(image==null)throw new IOException("Cannot decode sky preview: "+id);
            images.put(id,image);
        }
        BufferedImage end=images.get(AssetId.of("textures/environment/end_sky.png"));
        BufferedImage sun=images.get(AssetId.of("textures/environment/celestial/sun.png"));
        BufferedImage moon=images.get(moonId);
        BufferedImage clouds=images.get(AssetId.of("textures/environment/clouds.png"));
        boolean isEnd=scene.dimension().equals("end");
        double angle=context.ticks()/24000*Math.PI*2,cos=Math.cos(angle),sin=Math.sin(angle);
        NativeImage output=new NativeImage(size,size,false);
        double daylight=Math.max(0,Math.cos((context.ticks()-6000)/24000*Math.PI*2));
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            double dx=(x+.5-size/2.0)/size*1.35,localY=(size/2.0-y-.5)/size*1.35;
            double dy=localY*.5+Math.sqrt(3)/2,dz=localY*Math.sqrt(3)/2-.5;
            double[] rgb=isEnd?new double[]{.13,.1,.16}:new double[]{.025+.3*daylight,.035+.48*daylight,.09+.7*daylight};
            if(isEnd){
                if(end!=null){int pixel=end.getRGB(Math.floorMod((int)(dx*end.getWidth()*8),end.getWidth()),Math.floorMod((int)(dy*end.getHeight()*8),end.getHeight()));
                    for(int channel=0;channel<3;channel++)rgb[channel]=((pixel>>(16-channel*8))&255)/255.0*.16;}
            }
            for(var active:activeLayers){
                SkyScene.Layer layer=active.layer();
                double alpha=active.alpha();
                double[] ray=rotate(dx,dy,dz,active.cos(),active.sin(),layer.axisX(),layer.axisY(),layer.axisZ());
                BufferedImage image=images.get(layer.texture());if(image==null)continue;
                int color=sampleCube(image,ray[0],ray[1],ray[2]);double a=((color>>>24)&255)/255.0;
                for(int channel=0;channel<3;channel++)rgb[channel]=SkyScene.blend(layer.blend(),rgb[channel],((color>>(16-8*channel))&255)/255.0,a,alpha);
            }
            if(!isEnd){
                solar(rgb,sun,dx,dy,dz,cos,sin,1-context.rain(),scene.celestialBlends().getOrDefault("sun",SkyScene.Blend.ADD));
                solar(rgb,moon,dx,dy,dz,-cos,-sin,1-context.rain(),scene.celestialBlends().getOrDefault("moon_phases",SkyScene.Blend.ADD));
                if(clouds!=null&&dy>0){double t=(192-context.height())/dy;
                    int pixel=clouds.getRGB(Math.floorMod((int)(dx*t/12+context.ticks()*.03/12),clouds.getWidth()),Math.floorMod((int)(dz*t/12),clouds.getHeight()));
                    double a=(pixel>>>24)/255.0*.85;for(int c=0;c<3;c++){double value=((pixel>>(16-c*8))&255)/255.0*(.18+.72*daylight);rgb[c]=value*a+rgb[c]*(1-a);}}
            }
            int color=0xff000000;for(int c=0;c<3;c++)color|=(int)(Math.clamp(rgb[c],0,1)*255)<<(16-8*c);output.setPixel(x,y,color);
        }
        return output;
    }
    private record ActiveLayer(SkyScene.Layer layer,double alpha,double cos,double sin) {}
    private static NativeImage renderWeather(SkyScene scene,int size)throws IOException {
        BufferedImage rain=ImageIO.read(new ByteArrayInputStream(scene.textures().get(AssetId.of("textures/environment/rain.png"))));
        BufferedImage snow=ImageIO.read(new ByteArrayInputStream(scene.textures().get(AssetId.of("textures/environment/snow.png"))));
        NativeImage output=new NativeImage(size,size,false);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
            BufferedImage image=x<size/2?rain:snow;
            int color=image.getRGB(Math.floorMod(x+y/3,image.getWidth()),Math.floorMod(y,image.getHeight()));
            double alpha=(color>>>24)/255.0;
            int result=0xff000000;
            int[] background={36,50,68};
            for(int channel=0;channel<3;channel++)result|=(int)((color>>(16-channel*8)&255)*alpha+background[channel]*(1-alpha))<<(16-channel*8);
            output.setPixel(x,y,result);
        }
        return output;
    }
    private static void solar(double[] rgb,BufferedImage image,double x,double y,double z,double nx,double ny,double visibility,SkyScene.Blend blend){
        if(image==null||ny<-.25||visibility<=0)return;double dot=x*nx+y*ny;if(dot<=0)return;
        double u=z/dot/.16,v=(x*ny-y*nx)/dot/.16;if(Math.abs(u)>1||Math.abs(v)>1)return;
        int pixel=image.getRGB(Math.clamp((int)((u+1)*.5*image.getWidth()),0,image.getWidth()-1),Math.clamp((int)((v+1)*.5*image.getHeight()),0,image.getHeight()-1));
        double a=(pixel>>>24)/255.0;for(int c=0;c<3;c++)rgb[c]=SkyScene.blend(blend,rgb[c],((pixel>>(16-c*8))&255)/255.0,a,visibility);
    }
    public static double[] rotate(double x,double y,double z,double angle,double ax,double ay,double az){
        return rotate(x,y,z,Math.cos(angle),Math.sin(angle),ax,ay,az);
    }
    private static double[] rotate(double x,double y,double z,double c,double s,double ax,double ay,double az){
        double dot=ax*x+ay*y+az*z;
        return new double[]{x*c+(ay*z-az*y)*s+ax*dot*(1-c),y*c+(az*x-ax*z)*s+ay*dot*(1-c),z*c+(ax*y-ay*x)*s+az*dot*(1-c)};
    }
    public static int sampleCube(BufferedImage image,double x,double y,double z){
        double a=Math.abs(x),b=Math.abs(y),c=Math.abs(z),u,v;int face;
        if(b>=a&&b>=c){if(y>0){face=4;u=x/b;v=z/b;}else{face=1;u=x/b;v=-z/b;}}
        else if(c>=a){if(z<0){face=0;u=-x/c;v=-y/c;}else{face=2;u=x/c;v=-y/c;}}
        else if(x>0){face=5;u=-z/a;v=-y/a;}else{face=3;u=z/a;v=-y/a;}
        int n=image.getWidth()/3;
        int px=face%3*n+Math.clamp((int)((u+1)*.5*n),0,n-1),py=face/3*n+Math.clamp((int)((v+1)*.5*n),0,n-1);
        return image.getRGB(px,py);
    }
    private void discard(Entry entry){
        entry.discarded=true;
        if(entry.request!=null){visible.remove(entry.request);background.remove(entry.request);}
        if(entry.texture!=null)retired.add(entry.texture);
    }
    public void invalidateCustom(){
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(entry.getKey().source().equals(MixerConfig.VANILLA))continue;
            discard(entry.getValue());iterator.remove();
        }
    }
    public void close(){
        closed=true;
        entries.values().forEach(this::discard);
        entries.clear();
        retired.forEach(service.client.getTextureManager()::release);
        retired.clear();
    }
}
