package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SkyRenderer.class)
public abstract class SkyLayersMixin {
    @Inject(method="renderSun",at=@At("HEAD"),cancellable=true)
    private void texturemixer$sun(float alpha,com.mojang.blaze3d.vertex.PoseStack matrices,CallbackInfo callback){
        if(SkySceneRenderer.SHARED.renderCelestial("sun",0,alpha,matrices))callback.cancel();
    }
    @Inject(method="renderMoon",at=@At("HEAD"),cancellable=true)
    private void texturemixer$moon(net.minecraft.world.level.MoonPhase phase,float alpha,com.mojang.blaze3d.vertex.PoseStack matrices,CallbackInfo callback){
        if(SkySceneRenderer.SHARED.renderCelestial("moon_phases",phase.index(),alpha,matrices))callback.cancel();
    }
    @Inject(method="renderSunMoonAndStars",at=@At("HEAD"))
    private void texturemixer$overworld(CallbackInfo callback){SkySceneRenderer.SHARED.render();}
    @Inject(method="renderEndSky",at=@At("RETURN"))
    private void texturemixer$end(CallbackInfo callback){SkySceneRenderer.SHARED.render();}
}
