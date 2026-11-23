package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(targets="net.minecraft.client.render.entity.equipment.EquipmentRenderer$TrimSpriteKey")
public interface TrimKeyAccess {
    @Accessor("equipmentAssetId")RegistryKey<EquipmentAsset> texturemixer$asset();
    @Invoker("getTexture")Identifier texturemixer$texture();
}
