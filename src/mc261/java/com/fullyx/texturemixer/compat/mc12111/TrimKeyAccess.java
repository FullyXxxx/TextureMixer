package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(targets="net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer$TrimSpriteKey")
public interface TrimKeyAccess {
    @Accessor("equipmentAssetId")ResourceKey<EquipmentAsset> texturemixer$asset();
    @Invoker("spriteId")Identifier texturemixer$texture();
}
