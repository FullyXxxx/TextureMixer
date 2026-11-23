package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.BiomeColorCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.ColorResolver;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
public final class ColorBindings {
    private static final Map<String,Map<String,int[]>> RUNTIME=new ConcurrentHashMap<>();
    private static final Map<String,Map<String,int[]>> PREVIEWS=new ConcurrentHashMap<>();
    private static final Map<String,int[]> SHARED=Collections.synchronizedMap(new LinkedHashMap<>(64,.75f,true));
    private record WorldKey(ClientWorld world,String generation,String prefix,ColorResolver resolver){}
    private static final Map<WorldKey,BiomeColorCache> WORLD=new ConcurrentHashMap<>();
    public static void stage(Generation generation)throws IOException{RUNTIME.put(generation.id(),decode(generation.assets()));}
    public static void preview(String prefix,Map<AssetId,byte[]> files)throws IOException{PREVIEWS.put(prefix,decode(new Assets.Memory(files)));}
    public static void retirePreview(String prefix){PREVIEWS.remove(prefix);}
    private static Map<String,int[]> decode(Assets assets)throws IOException{
        Map<String,int[]> result=new HashMap<>();
        for(AssetId file:assets.ids())if(file.namespace().startsWith("texturemixer_")&&file.path().startsWith("textures/colormap/")&&file.path().endsWith(".png")){
            byte[] data=assets.read(file);String hash=Assets.hash(data);int[] colors=SHARED.get(hash);
            if(colors==null){var image=ImageIO.read(new ByteArrayInputStream(data));if(image==null||image.getWidth()!=256||image.getHeight()!=256)throw new IOException(tr("原版颜色映射必须为 256×256: ")+file);
                colors=image.getRGB(0,0,256,256,null,0,256);SHARED.put(hash,colors);synchronized(SHARED){while(SHARED.size()>192)SHARED.remove(SHARED.keySet().iterator().next());}}
            result.put(file.namespace()+"|"+file.path().substring("textures/colormap/".length(),file.path().length()-4),colors);
        }return Map.copyOf(result);
    }
    public static Integer sample(String prefix,String color,double temperature,double humidity){
        if(prefix==null)return null;Map<String,int[]> table=PREVIEWS.get(prefix);
        if(table==null){var service=TextureMixerClient.SERVICE;var generation=service==null?null:service.runtime();table=generation==null?null:RUNTIME.get(generation.id());}
        if(table==null)return null;int[] map=table.get(prefix+"minecraft|"+color);if(map==null)return null;
        double t=Math.clamp(temperature,0,1),h=Math.clamp(humidity,0,1)*t;
        int x=(int)((1-t)*255),y=(int)((1-h)*255);return map[y*256+x];
    }
    public static int worldColor(ClientWorld world,BlockPos pos,ColorResolver resolver){
        String prefix=RenderScope.currentPrefix();var service=TextureMixerClient.SERVICE;String generation=service!=null&&service.runtime()!=null?service.runtime().id():"preview";
        WorldKey key=new WorldKey(world,generation,prefix,resolver);
        return WORLD.computeIfAbsent(key,k->new BiomeColorCache(p->RenderScope.in(k.prefix,()->k.world.calculateColor(p,k.resolver)))).getBiomeColor(pos);
    }
    public static void clearWorld(){WORLD.clear();}
    public static void retain(String current,String previous){RUNTIME.keySet().removeIf(id->!id.equals(current)&&!id.equals(previous));WORLD.clear();}
}
