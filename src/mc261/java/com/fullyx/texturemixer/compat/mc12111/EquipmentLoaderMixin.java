package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.EquipmentBindings;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EquipmentAssetManager.class)
public abstract class EquipmentLoaderMixin {
    @Inject(method="get",at=@At("HEAD"),cancellable=true)
    private void texturemixer$preview(ResourceKey<EquipmentAsset> key,CallbackInfoReturnable<EquipmentClientInfo> callback){
        EquipmentClientInfo model=EquipmentBindings.previewModel(key);if(model!=null)callback.setReturnValue(model);
    }
}
