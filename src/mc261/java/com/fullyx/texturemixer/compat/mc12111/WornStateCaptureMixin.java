package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class WornStateCaptureMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("RETURN"))
    private void texturemixer$head(LivingEntity entity,LivingEntityRenderState state,float tick,CallbackInfo callback){
        var stack=entity.getItemBySlot(EquipmentSlot.HEAD);var data=(WornHeadState)state;
        data.texturemixer$headPrefix(EquipmentBindings.prefix(stack));data.texturemixer$headArena(EquipmentBindings.arena(stack));
    }
}
