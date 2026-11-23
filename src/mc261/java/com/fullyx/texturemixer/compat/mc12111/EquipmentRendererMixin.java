package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo.LayerType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentRendererMixin {
    @WrapMethod(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V")
    private void texturemixer$equipment(LayerType type,ResourceKey<EquipmentAsset> asset,Model<?> model,Object state,ItemStack stack,
            PoseStack matrices,SubmitNodeCollector queue,int light,Identifier texture,int outline,int order,Operation<Void> original){
        var mapped=EquipmentBindings.key(asset,stack);String prefix=mapped.equals(asset)?null:EquipmentBindings.prefix(stack);
        RenderScope.in(prefix,()->RenderScope.arena(EquipmentBindings.arena(stack),()->{original.call(type,mapped,model,state,stack,matrices,queue,light,texture,outline,order);return null;}));
    }
}
