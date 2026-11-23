package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.VersionedIdentifiers;

import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets="net.minecraft.client.render.entity.equipment.EquipmentRenderer$TrimSpriteKey")
public abstract class TrimTextureMixin {
    @Shadow @Final private RegistryKey<EquipmentAsset> equipmentAssetId;

    @ModifyArg(method="getTexture",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/equipment/EquipmentRenderer$TrimSpriteKey;getAssetName(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/registry/RegistryKey;)Ljava/lang/String;"),index=1)
    private RegistryKey<EquipmentAsset> texturemixer$unscopedAsset(RegistryKey<EquipmentAsset> original){
        return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY,RenderScope.original(original.getValue()));
    }

    @ModifyReturnValue(method="getTexture",at=@At("RETURN"))
    private Identifier texturemixer$trim(Identifier original){
        String prefix=RenderScope.prefix(equipmentAssetId.getValue().getNamespace());
        return prefix==null?original:VersionedIdentifiers.of(prefix+original.getNamespace(),original.getPath());
    }
}
