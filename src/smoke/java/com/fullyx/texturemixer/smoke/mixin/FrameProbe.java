package com.fullyx.texturemixer.smoke.mixin;

import com.fullyx.texturemixer.smoke.SmokeControls;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class FrameProbe {
    @Unique private long texturemixer$frameStarted;
    @Inject(method="render",at=@At("HEAD"))
    private void texturemixer$frameStart(CallbackInfo callback){
        texturemixer$frameStarted=SmokeControls.measureFrames?System.nanoTime():0;
    }
    @Inject(method="render",at=@At("RETURN"))
    private void texturemixer$frameEnd(CallbackInfo callback){
        if(texturemixer$frameStarted!=0&&SmokeControls.measureFrames&&SmokeControls.frameNanos.size()<20000)
            SmokeControls.frameNanos.add(System.nanoTime()-texturemixer$frameStarted);
    }
}
