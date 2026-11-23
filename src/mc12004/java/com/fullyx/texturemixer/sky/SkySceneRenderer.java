package com.fullyx.texturemixer.sky;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.util.*;

public final class SkySceneRenderer implements AutoCloseable {
    public static void initialize(){SkyDrawBackend.initialize();}
    public static String prefix(String dimension){return SkyScene.namespacePrefix(dimension);}
    public static String pausedReason(){
        var client=MinecraftClient.getInstance();if(client!=null&&client.world!=null&&client.inGameHud.getBossBarHud().shouldThickenFog())return tr("原版 Boss 迷雾正在隐藏天空");
        var loader=FabricLoader.getInstance();if(loader.isModLoaded("nuit")||loader.isModLoaded("fabricskyboxes"))return tr("天空已由其他天空引擎接管");
        if(loader.isModLoaded("iris"))try{
            Object pack=Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null);
            if(pack instanceof Optional<?> optional&&optional.isPresent())return tr("天空已由 Iris 光影接管");
        }catch(ReflectiveOperationException e){return tr("当前 Iris 版本的天空兼容性未验证");}
        return "";
    }
    public static SkyScene current(){
        var service=TextureMixerClient.SERVICE;var client=MinecraftClient.getInstance();
        if(service==null||service.runtime()==null||client.world==null||!pausedReason().isEmpty())return null;
        String dimension=NativeCompat.skyDimension(client.world);
        return service.runtime().skies().get(dimension);
    }
    public static final SkySceneRenderer SHARED=new SkySceneRenderer();
    private String generation="";
    private final SkyDrawBackend backend=new SkyDrawBackend();
    private final Map<SkyScene.Layer,double[]> transitions=new HashMap<>();
    private final Set<Identifier> layerTextures=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public void trackTexture(Identifier texture){layerTextures.add(texture);}
    public void releaseTextures(){
        var manager=MinecraftClient.getInstance().getTextureManager();
        layerTextures.forEach(manager::destroyTexture);layerTextures.clear();
    }
    private long overworldDraws,endDraws;
    public long draws(String dimension){return dimension.equals("end")?endDraws:overworldDraws;}
    public void render(){
        var service=com.fullyx.texturemixer.TextureMixerClient.SERVICE;
        String next=service==null||service.runtime()==null?"":service.runtime().id();
        if(!next.equals(generation)){close();generation=next;}
        SkyScene scene=current();if(scene==null||scene.layers().isEmpty())return;
        MinecraftClient client=MinecraftClient.getInstance();var world=client.world;var camera=client.gameRenderer.getCamera();
        long ticks=world.getTimeOfDay();double partial=client.getTickDelta();
        String biome=world.getBiome(camera.getBlockPos()).getKey().map(k->k.getValue().toString()).orElse("minecraft:plains");
        SkyScene.Context context=new SkyScene.Context(Math.floorMod(ticks,24000)+partial,Math.floorDiv(ticks,24000),world.getRainGradient((float)partial),world.getThunderGradient((float)partial),biome,SkyDrawBackend.cameraY(camera));
        for(SkyScene.Layer layer:scene.layers()){
            double condition=layer.condition(context),now=System.nanoTime()/1e9;double[] transition=transitions.computeIfAbsent(layer,k->new double[]{condition,now});
            double maxDelta=layer.transitionSeconds()<=0?1:Math.max(0,now-transition[1])/layer.transitionSeconds();
            transition[0]+=net.minecraft.util.math.MathHelper.clamp(condition-transition[0],-maxDelta,maxDelta);transition[1]=now;
            float alpha=(float)(layer.timeAlpha(context.ticks())*transition[0]);if(alpha<=.00001)continue;
            AssetId id=layer.texture();Identifier texture=new Identifier(prefix(scene.dimension())+id.namespace(),id.path());
            trackTexture(texture);
            var image=client.getTextureManager().getTexture(texture);
            Matrix4f matrix=new Matrix4f().rotation(new org.joml.Quaternionf(camera.getRotation()).conjugate())
                    .rotate((float)layer.rotation(context),(float)layer.axisX(),(float)layer.axisY(),(float)layer.axisZ());
            if(backend.draw(client.getFramebuffer(),image,matrix,alpha,layer.blend(),false)){
                if(scene.dimension().equals("end"))endDraws++;else overworldDraws++;
            }
        }
    }
    static void cubeVertices(BufferBuilder builder){
        int[][] corners={{-1,-1},{-1,1},{1,1},{1,-1}};
        for(int face=0;face<6;face++)for(int[] corner:corners){
            float u=corner[0],v=corner[1],x=0,y=0,z=0;
            switch(face){case 0->{x=-u;y=-v;z=-1;}case 1->{x=u;y=-1;z=-v;}case 2->{x=u;y=-v;z=1;}
                case 3->{x=-1;y=-v;z=u;}case 4->{x=u;y=1;z=v;}case 5->{x=1;y=-v;z=-u;}}
            builder.vertex(x*100,y*100,z*100).texture((face%3+(u+1)*.5f)/3,(face/3+(v+1)*.5f)/2).color(-1).next();
        }
    }
    static void celestialVertices(BufferBuilder builder){
        builder.vertex(-1,0,-1).texture(0,0).color(-1).next();
        builder.vertex(1,0,-1).texture(1,0).color(-1).next();
        builder.vertex(1,0,1).texture(1,1).color(-1).next();
        builder.vertex(-1,0,1).texture(0,1).color(-1).next();
    }
    public boolean renderCelestial(String object,int phase,float alpha,net.minecraft.client.util.math.MatrixStack matrices){
        SkyScene scene=current();if(scene==null||!scene.celestialBlends().containsKey(object))return false;
        if(alpha<=0)return true;
        String[] names={"full_moon","waning_gibbous","third_quarter","waning_crescent","new_moon","waxing_crescent","first_quarter","waxing_gibbous"};
        String path=object.equals("sun")?"sun":"moon/"+names[Math.floorMod(phase,8)];
        var client=MinecraftClient.getInstance();
        var id=new Identifier(prefix(scene.dimension())+"minecraft","textures/environment/celestial/"+path+".png");
        trackTexture(id);
        var texture=client.getTextureManager().getTexture(id);
        Matrix4f matrix=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix()).translate(0,100,0).scale(object.equals("sun")?30:20,1,object.equals("sun")?30:20);
        backend.draw(client.getFramebuffer(),texture,matrix,alpha,scene.celestialBlends().get(object),true);
        return true;
    }
    public void close(){backend.close();transitions.clear();}
}
