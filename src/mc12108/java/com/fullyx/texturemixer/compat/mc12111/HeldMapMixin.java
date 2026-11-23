package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(HeldItemRenderer.class)
public abstract class HeldMapMixin {
    @WrapMethod(method="renderFirstPersonMap")
    private void texturemixer$map(MatrixStack matrices,VertexConsumerProvider queue,int light,ItemStack stack,Operation<Void> original){
        RenderScope.in(EquipmentBindings.prefix(stack),()->{original.call(matrices,queue,light,stack);return null;});
    }
    @ModifyExpressionValue(method="renderFirstPersonMap",at=@At(value="FIELD",target="Lnet/minecraft/client/render/item/HeldItemRenderer;MAP_BACKGROUND:Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$background(RenderLayer original){return NativeCompat.textLayer(RenderScope.texture(VersionedIdentifiers.vanilla("textures/map/map_background.png")));}
    @ModifyExpressionValue(method="renderFirstPersonMap",at=@At(value="FIELD",target="Lnet/minecraft/client/render/item/HeldItemRenderer;MAP_BACKGROUND_CHECKERBOARD:Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$checker(RenderLayer original){return NativeCompat.textLayer(RenderScope.texture(VersionedIdentifiers.vanilla("textures/map/map_background_checkerboard.png")));}
}
