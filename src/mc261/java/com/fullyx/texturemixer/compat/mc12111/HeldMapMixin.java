package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ItemInHandRenderer.class)
public abstract class HeldMapMixin {
    @WrapMethod(method="renderMap")
    private void texturemixer$map(PoseStack matrices,SubmitNodeCollector queue,int light,ItemStack stack,Operation<Void> original){
        RenderScope.in(EquipmentBindings.prefix(stack),()->{original.call(matrices,queue,light,stack);return null;});
    }
    @ModifyExpressionValue(method="renderMap",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;MAP_BACKGROUND:Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType texturemixer$background(RenderType original){return NativeCompat.textLayer(RenderScope.texture(Identifier.withDefaultNamespace("textures/map/map_background.png")));}
    @ModifyExpressionValue(method="renderMap",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/ItemInHandRenderer;MAP_BACKGROUND_CHECKERBOARD:Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType texturemixer$checker(RenderType original){return NativeCompat.textLayer(RenderScope.texture(Identifier.withDefaultNamespace("textures/map/map_background_checkerboard.png")));}
}
