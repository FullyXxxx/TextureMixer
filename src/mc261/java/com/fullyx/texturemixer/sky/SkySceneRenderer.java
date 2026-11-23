package com.fullyx.texturemixer.sky;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.*;

public final class SkySceneRenderer implements AutoCloseable {
    public static void initialize(){SkyDrawBackend.initialize();}
    public static String prefix(String dimension){return SkyScene.namespacePrefix(dimension);}
    public static String pausedReason(){
        var client=Minecraft.getInstance();if(client!=null&&client.level!=null&&client.gui.getBossOverlay().shouldCreateWorldFog())return tr("原版 Boss 迷雾正在隐藏天空");
        var loader=FabricLoader.getInstance();if(loader.isModLoaded("nuit")||loader.isModLoaded("fabricskyboxes"))return tr("天空已由其他天空引擎接管");
        if(loader.isModLoaded("iris"))try{
            Object pack=Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null);
            if(pack instanceof Optional<?> optional&&optional.isPresent())return tr("天空已由 Iris 光影接管");
        }catch(ReflectiveOperationException e){return tr("当前 Iris 版本的天空兼容性未验证");}
        return "";
    }
    public static SkyScene current(){
        var service=TextureMixerClient.SERVICE;var client=Minecraft.getInstance();
        if(service==null||service.runtime()==null||client.level==null||!pausedReason().isEmpty())return null;
        String dimension=NativeCompat.skyDimension(client.level);
        return service.runtime().skies().get(dimension);
    }
    public static final SkySceneRenderer SHARED=new SkySceneRenderer();
    private String generation="";
    private final SkyDrawBackend backend=new SkyDrawBackend();
    private final Map<SkyScene.Layer,double[]> transitions=new HashMap<>();
    private final Set<Identifier> layerTextures=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public void trackTexture(Identifier texture){layerTextures.add(texture);}
    public void releaseTextures(){
        var manager=Minecraft.getInstance().getTextureManager();
        layerTextures.forEach(manager::release);layerTextures.clear();
    }
    private long overworldDraws,endDraws;
    public long draws(String dimension){return dimension.equals("end")?endDraws:overworldDraws;}
    public void render(){
        var service=com.fullyx.texturemixer.TextureMixerClient.SERVICE;
        String next=service==null||service.runtime()==null?"":service.runtime().id();
        if(!next.equals(generation)){close();generation=next;}
        SkyScene scene=current();if(scene==null||scene.layers().isEmpty())return;
        Minecraft client=Minecraft.getInstance();var world=client.level;var camera=client.gameRenderer.getMainCamera();
        long ticks=world.getOverworldClockTime();double partial=SkyDrawBackend.partialTick(client.getDeltaTracker());
        String biome=world.getBiome(camera.blockPosition()).unwrapKey().map(k->k.identifier().toString()).orElse("minecraft:plains");
        SkyScene.Context context=new SkyScene.Context(Math.floorMod(ticks,24000)+partial,Math.floorDiv(ticks,24000),world.getRainLevel((float)partial),world.getThunderLevel((float)partial),biome,SkyDrawBackend.cameraY(camera));
        for(SkyScene.Layer layer:scene.layers()){
            double condition=layer.condition(context),now=System.nanoTime()/1e9;double[] transition=transitions.computeIfAbsent(layer,k->new double[]{condition,now});
            double maxDelta=layer.transitionSeconds()<=0?1:Math.max(0,now-transition[1])/layer.transitionSeconds();
            transition[0]+=Math.clamp(condition-transition[0],-maxDelta,maxDelta);transition[1]=now;
            float alpha=(float)(layer.timeAlpha(context.ticks())*transition[0]);if(alpha<=.00001)continue;
            AssetId id=layer.texture();Identifier texture=Identifier.fromNamespaceAndPath(prefix(scene.dimension())+id.namespace(),id.path());
            trackTexture(texture);
            var image=client.getTextureManager().getTexture(texture);
            Matrix4f matrix=new Matrix4f().rotation(new org.joml.Quaternionf(camera.rotation()).conjugate())
                    .rotate((float)layer.rotation(context),(float)layer.axisX(),(float)layer.axisY(),(float)layer.axisZ());
            if(backend.draw(client.getMainRenderTarget(),image,matrix,alpha,layer.blend(),false)){
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
            builder.addVertex(x*100,y*100,z*100).setUv((face%3+(u+1)*.5f)/3,(face/3+(v+1)*.5f)/2).setColor(-1);
        }
    }
    static void celestialVertices(BufferBuilder builder){
        builder.addVertex(-1,0,-1).setUv(0,0).setColor(-1);
        builder.addVertex(1,0,-1).setUv(1,0).setColor(-1);
        builder.addVertex(1,0,1).setUv(1,1).setColor(-1);
        builder.addVertex(-1,0,1).setUv(0,1).setColor(-1);
    }
    public boolean renderCelestial(String object,int phase,float alpha,com.mojang.blaze3d.vertex.PoseStack matrices){
        SkyScene scene=current();if(scene==null||!scene.celestialBlends().containsKey(object))return false;
        if(alpha<=0)return true;
        String[] names={"full_moon","waning_gibbous","third_quarter","waning_crescent","new_moon","waxing_crescent","first_quarter","waxing_gibbous"};
        String path=object.equals("sun")?"sun":"moon/"+names[Math.floorMod(phase,8)];
        var client=Minecraft.getInstance();
        var id=Identifier.fromNamespaceAndPath(prefix(scene.dimension())+"minecraft","textures/environment/celestial/"+path+".png");
        trackTexture(id);
        var texture=client.getTextureManager().getTexture(id);
        Matrix4f matrix=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose()).translate(0,100,0).scale(object.equals("sun")?30:20,1,object.equals("sun")?30:20);
        backend.draw(client.getMainRenderTarget(),texture,matrix,alpha,scene.celestialBlends().get(object),true);
        return true;
    }
    public void close(){backend.close();transitions.clear();}
}
