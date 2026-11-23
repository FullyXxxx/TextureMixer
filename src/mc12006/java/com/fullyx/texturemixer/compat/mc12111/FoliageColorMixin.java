package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.color.world.FoliageColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FoliageColors.class)
public abstract class FoliageColorMixin {
    @Inject(method="getColor",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$color(double t,double h,CallbackInfoReturnable<Integer> callback){Integer color=ColorBindings.sample(RenderScope.currentPrefix(),"foliage",t,h);if(color!=null)callback.setReturnValue(color);}
}
