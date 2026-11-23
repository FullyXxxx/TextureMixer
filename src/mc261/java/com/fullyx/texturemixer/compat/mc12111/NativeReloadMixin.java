package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class NativeReloadMixin {
    @Inject(method="lambda$reloadResourcePacks$1",at=@At("HEAD"))
    private void texturemixer$failed(boolean forced,@Coerce Object loadingContext,Throwable error,CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeReloadFailed(error);
    }
    @Inject(method="rollbackResourcePacks",at=@At("HEAD"))
    private void texturemixer$initialFailure(Throwable error,@Coerce Object loadingContext,CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeReloadFailed(error);
    }
    @Inject(method={"lambda$reloadResourcePacks$2","abortResourcePackRecovery"},at=@At("RETURN"))
    private void texturemixer$settled(CallbackInfo callback){
        if(TextureMixerClient.SERVICE!=null)TextureMixerClient.SERVICE.nativeRecoveryFinished();
    }
}
