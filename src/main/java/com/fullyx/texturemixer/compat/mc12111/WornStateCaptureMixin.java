package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class WornStateCaptureMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",at=@At("RETURN"))
    private void texturemixer$head(LivingEntity entity,LivingEntityRenderState state,float tick,CallbackInfo callback){
        var stack=entity.getEquippedStack(EquipmentSlot.HEAD);var data=(WornHeadState)state;
        data.texturemixer$headPrefix(EquipmentBindings.prefix(stack));data.texturemixer$headArena(EquipmentBindings.arena(stack));
    }
}
