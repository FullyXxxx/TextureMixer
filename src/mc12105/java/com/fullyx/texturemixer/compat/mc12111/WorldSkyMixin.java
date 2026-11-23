package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.sky.SkyScene;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SkyRendering.class)
public abstract class WorldSkyMixin {
    @WrapMethod(method="renderCelestialBodies")
    private void texturemixer$skyTextures(MatrixStack matrices,VertexConsumerProvider.Immediate vertices,
            float angle,int moon,float rain,float stars,Fog fog,Operation<Void> nativeDraw){
        var scene=SkySceneRenderer.current();
        String prefix=scene==null?null:SkyScene.namespacePrefix(scene.dimension());
        RenderScope.in(prefix,()->{nativeDraw.call(matrices,vertices,angle,moon,rain,stars,fog);return null;});
    }
    @WrapMethod(method="renderEndSky")
    private void texturemixer$endTextures(Operation<Void> nativeDraw){
        var scene=SkySceneRenderer.current();
        String prefix=scene==null?null:SkyScene.namespacePrefix(scene.dimension());
        RenderScope.in(prefix,()->{nativeDraw.call();return null;});
    }
}
