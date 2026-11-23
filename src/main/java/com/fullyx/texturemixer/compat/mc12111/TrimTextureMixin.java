package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType;
import net.minecraft.item.equipment.*;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.client.render.entity.equipment.EquipmentRenderer$TrimSpriteKey")
public abstract class TrimTextureMixin {
    @Shadow @Final private ArmorTrim trim;
    @Shadow @Final private LayerType layerType;
    @Shadow @Final private RegistryKey<EquipmentAsset> equipmentAssetId;
    @Inject(method="getTexture",at=@At("HEAD"),cancellable=true)
    private void texturemixer$trim(CallbackInfoReturnable<Identifier> callback){
        String prefix=RenderScope.prefix(equipmentAssetId.getValue().getNamespace());if(prefix==null)return;
        var original=RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY,RenderScope.original(equipmentAssetId.getValue()));
        Identifier texture=trim.getTextureId(layerType.getTrimsDirectory(),original);
        callback.setReturnValue(VersionedIdentifiers.of(prefix+texture.getNamespace(),texture.getPath()));
    }
}
