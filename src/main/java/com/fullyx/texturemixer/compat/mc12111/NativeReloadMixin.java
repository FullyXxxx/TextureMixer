package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class NativeReloadMixin {
    @Inject(method="method_49293",at=@At("HEAD"))
    private void texturemixer$failed(boolean forced,@Coerce Object loadingContext,Throwable error,CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeReloadFailed(error);
    }
    @Inject(method="handleResourceReloadException",at=@At("HEAD"))
    private void texturemixer$initialFailure(Throwable error,@Coerce Object loadingContext,CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeReloadFailed(error);
    }
    @Inject(method={"method_29339","onForcedResourceReloadFailure"},at=@At("RETURN"))
    private void texturemixer$settled(CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeRecoveryFinished();
    }
}
