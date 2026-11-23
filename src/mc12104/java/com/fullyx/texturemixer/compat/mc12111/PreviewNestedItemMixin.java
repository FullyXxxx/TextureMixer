package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.TextureMixerClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.*;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(ItemModelManager.class)
public abstract class PreviewNestedItemMixin {
    /** Keep explicit and isolated models when a visual mod redirects an item's model component. */
    @ModifyVariable(method="update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V",at=@At("STORE"),ordinal=0)
    private Identifier texturemixer$chosenModel(Identifier model,ItemRenderState state,ItemStack stack,ModelTransformationMode display,World world,LivingEntity held,int seed){
        var service=TextureMixerClient.SERVICE;
        Identifier selected=stack.get(DataComponentTypes.ITEM_MODEL);
        if(selected==null)return model;
        var generation=service==null?null:service.runtime();
        boolean mainOwnsDefinition=generation!=null&&generation.assets().contains(
                new com.fullyx.texturemixer.core.AssetId(selected.getNamespace(),"items/"+selected.getPath()+".json"));
        if(RenderScope.currentArena()!=null||mainOwnsDefinition||service!=null&&service.prefixForItem(Registries.ITEM.getId(stack.getItem()).toString())!=null)
            return selected;
        return model;
    }
    @WrapMethod(method="update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;I)V")
    private void texturemixer$nested(ItemRenderState state,ItemStack stack,ModelTransformationMode display,World world,LivingEntity held,int seed,Operation<Void> original){
        var preview=EquipmentBindings.previewItems(stack);
        if(preview!=null&&preview!=(Object)this)preview.update(state,stack,display,world,held,seed);
        else original.call(state,stack,display,world,held,seed);
    }
}
