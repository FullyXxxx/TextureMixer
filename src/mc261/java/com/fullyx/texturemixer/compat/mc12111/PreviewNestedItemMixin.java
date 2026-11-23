package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.TextureMixerClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(ItemModelResolver.class)
public abstract class PreviewNestedItemMixin {
    /** Keep explicit and isolated models when a visual mod redirects an item's model component. */
    @ModifyVariable(method="appendItemLayers",at=@At("STORE"),ordinal=0)
    private Identifier texturemixer$chosenModel(Identifier model,ItemStackRenderState state,ItemStack stack,ItemDisplayContext display,Level world,ItemOwner held,int seed){
        var service=TextureMixerClient.SERVICE;
        Identifier selected=stack.get(DataComponents.ITEM_MODEL);
        if(selected==null)return model;
        var generation=service==null?null:service.runtime();
        boolean mainOwnsDefinition=generation!=null&&generation.assets().contains(
                new com.fullyx.texturemixer.core.AssetId(selected.getNamespace(),"items/"+selected.getPath()+".json"));
        if(RenderScope.currentArena()!=null||mainOwnsDefinition||service!=null&&service.prefixForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())!=null)
            return selected;
        return model;
    }
    @WrapMethod(method="appendItemLayers")
    private void texturemixer$nested(ItemStackRenderState state,ItemStack stack,ItemDisplayContext display,Level world,ItemOwner held,int seed,Operation<Void> original){
        var preview=EquipmentBindings.previewItems(stack);
        if(preview!=null&&preview!=(Object)this)preview.appendItemLayers(state,stack,display,world,held,seed);
        else original.call(state,stack,display,world,held,seed);
    }
}
