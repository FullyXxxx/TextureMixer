package com.fullyx.texturemixer.sky;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.client.NativeCompat;
import net.minecraft.client.render.SkyRendering;
import java.util.*;
public final class NativeSkyBindings {
    private static String generation;
    private static final Map<String,SkyRendering> SKIES=new HashMap<>();
    public static SkyRendering selected(SkyRendering original){
        var scene=SkySceneRenderer.current();if(scene==null)return original;
        var service=TextureMixerClient.SERVICE;String next=service.runtime().id();
        if(!next.equals(generation)){close();generation=next;}
        return SKIES.computeIfAbsent(scene.dimension(),dimension->RenderScope.in(SkySceneRenderer.prefix(dimension),()->{
            return NativeCompat.skyRendering();
        }));
    }
    public static void close(){SKIES.values().forEach(SkyRendering::close);SKIES.clear();generation=null;}
}
