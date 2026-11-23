package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SkyRendering.class)
public abstract class SkyLayersMixin {
    @Inject(method="renderSun",at=@At("HEAD"),cancellable=true)
    private void texturemixer$sun(float alpha,net.minecraft.client.util.math.MatrixStack matrices,CallbackInfo callback){
        if(SkySceneRenderer.SHARED.renderCelestial("sun",0,alpha,matrices))callback.cancel();
    }
    @Inject(method="renderMoon",at=@At("HEAD"),cancellable=true)
    private void texturemixer$moon(net.minecraft.world.MoonPhase phase,float alpha,net.minecraft.client.util.math.MatrixStack matrices,CallbackInfo callback){
        if(SkySceneRenderer.SHARED.renderCelestial("moon_phases",phase.getIndex(),alpha,matrices))callback.cancel();
    }
    @Inject(method="renderCelestialBodies",at=@At("HEAD"))
    private void texturemixer$overworld(CallbackInfo callback){SkySceneRenderer.SHARED.render();}
    @Inject(method="renderEndSky",at=@At("RETURN"))
    private void texturemixer$end(CallbackInfo callback){SkySceneRenderer.SHARED.render();}
}
