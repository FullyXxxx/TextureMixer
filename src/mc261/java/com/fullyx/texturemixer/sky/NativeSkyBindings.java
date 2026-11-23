package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import java.util.*;
import net.minecraft.client.renderer.SkyRenderer;
public final class NativeSkyBindings {
    private static String generation;
    private static final Map<String,SkyRenderer> SKIES=new HashMap<>();
    public static SkyRenderer selected(SkyRenderer original){
        var scene=SkySceneRenderer.current();if(scene==null)return original;
        var service=TextureMixerClient.SERVICE;String next=service.runtime().id();
        if(!next.equals(generation)){close();generation=next;}
        return SKIES.computeIfAbsent(scene.dimension(),dimension->RenderScope.in(SkySceneRenderer.prefix(dimension),()->{
            return NativeCompat.skyRendering();
        }));
    }
    public static void close(){SKIES.values().forEach(SkyRenderer::close);SKIES.clear();generation=null;}
}
