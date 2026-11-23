package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.world.level.GrassColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GrassColor.class)
public abstract class GrassColorMixin {
    @Inject(method="get",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$color(double t,double h,CallbackInfoReturnable<Integer> callback){Integer color=ColorBindings.sample(RenderScope.currentPrefix(),"grass",t,h);if(color!=null)callback.setReturnValue(color);}
}
