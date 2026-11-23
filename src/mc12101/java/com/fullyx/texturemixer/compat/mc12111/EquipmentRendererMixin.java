package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EquipmentBindings;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ArmorFeatureRenderer.class)
public abstract class EquipmentRendererMixin {
    @WrapMethod(method="renderArmor")
    private void texturemixer$armor(MatrixStack matrices,VertexConsumerProvider vertices,LivingEntity entity,
            EquipmentSlot slot,int light,BipedEntityModel<?> model,Operation<Void> original){
        var stack=entity.getEquippedStack(slot);
        RenderScope.in(EquipmentBindings.prefix(stack),()->RenderScope.arena(EquipmentBindings.arena(stack),()->{
            original.call(matrices,vertices,entity,slot,light,model);
            return null;
        }));
    }
}
