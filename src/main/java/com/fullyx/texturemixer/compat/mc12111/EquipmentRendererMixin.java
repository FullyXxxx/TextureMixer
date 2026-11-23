package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V")
    private void texturemixer$equipment(LayerType type,RegistryKey<EquipmentAsset> asset,Model<?> model,Object state,ItemStack stack,
            MatrixStack matrices,OrderedRenderCommandQueue queue,int light,Identifier texture,int outline,int order,Operation<Void> original){
        var mapped=EquipmentBindings.key(asset,stack);String prefix=mapped.equals(asset)?null:EquipmentBindings.prefix(stack);
        RenderScope.in(prefix,()->RenderScope.arena(EquipmentBindings.arena(stack),()->{original.call(type,mapped,model,state,stack,matrices,queue,light,texture,outline,order);return null;}));
    }
}
