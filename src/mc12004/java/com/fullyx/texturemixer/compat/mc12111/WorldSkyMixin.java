package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldSkyMixin {
    private static final String RENDER_SKY="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V";

    @Inject(method=RENDER_SKY,at=@At(value="INVOKE",ordinal=0,shift=At.Shift.AFTER,
            target="Lnet/minecraft/client/gl/VertexBuffer;draw(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/gl/ShaderProgram;)V"))
    private void texturemixer$overworld(CallbackInfo callback){SkySceneRenderer.SHARED.render();}

    @WrapOperation(method=RENDER_SKY,at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;renderEndSky(Lnet/minecraft/client/util/math/MatrixStack;)V"))
    private void texturemixer$end(WorldRenderer renderer,MatrixStack matrices,Operation<Void> original){
        var scene=SkySceneRenderer.current();
        String prefix=scene==null?null:SkySceneRenderer.prefix(scene.dimension());
        RenderScope.in(prefix,()->{original.call(renderer,matrices);return null;});
        SkySceneRenderer.SHARED.render();
    }

    @WrapMethod(method="renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V")
    private void texturemixer$clouds(MatrixStack matrices,Matrix4f projection,float tick,
            double x,double y,double z,Operation<Void> original){
        var scene=SkySceneRenderer.current();
        String prefix=scene==null?null:SkySceneRenderer.prefix(scene.dimension());
        RenderScope.in(prefix,()->{original.call(matrices,projection,tick,x,y,z);return null;});
    }
}
