package com.fullyx.texturemixer.smoke.mixin;
import com.fullyx.texturemixer.smoke.SmokeControls;
import net.minecraft.client.MinecraftClient;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftClient.class)
public abstract class ReloadProbe {
    @Inject(method="reloadResources()Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"))
    private void texturemixer$serverArrival(CallbackInfoReturnable<CompletableFuture<Void>> callback){
        if(SmokeControls.serverOnNextReload){SmokeControls.serverOnNextReload=false;SmokeControls.serverPack=true;((MinecraftClient)(Object)this).getResourcePackManager().scanPacks();}
    }
    @Inject(method="reloadResources()Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),cancellable=true)
    private void texturemixer$probe(CallbackInfoReturnable<CompletableFuture<Void>> callback){
        if(SmokeControls.failNextReload){SmokeControls.failNextReload=false;
            callback.setReturnValue(callback.getReturnValue().thenCompose(v->CompletableFuture.failedFuture(new IOException("Smoke-injected failure after native reload"))));}
    }
}
