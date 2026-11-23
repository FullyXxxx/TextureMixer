package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.RenderScope;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Optional third-party target. Private previews neither replace nor inherit global emissive suffix state. */
@Pseudo
@Mixin(targets="me.pepperbell.continuity.client.resource.EmissiveSuffixLoader",remap=false)
public abstract class ContinuityEmissiveMixin {
    @Inject(method="load",at=@At("HEAD"),cancellable=true,remap=false)
    private static void texturemixer$preserveWorldSuffix(CallbackInfo ci){if(RenderScope.currentArena()!=null)ci.cancel();}
    @Inject(method="getEmissiveSuffix",at=@At("HEAD"),cancellable=true,remap=false)
    private static void texturemixer$isolatedSuffix(CallbackInfoReturnable<String> cir){if(RenderScope.currentArena()!=null)cir.setReturnValue(null);}
}
