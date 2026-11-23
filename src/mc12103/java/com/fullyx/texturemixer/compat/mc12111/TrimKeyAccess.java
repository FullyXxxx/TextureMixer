package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.util.Identifier;
import net.minecraft.item.equipment.EquipmentModel.LayerType;
import net.minecraft.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(targets="net.minecraft.client.render.entity.equipment.EquipmentRenderer$TrimSpriteKey")
public interface TrimKeyAccess {
    @Accessor("equipmentModelId")Identifier texturemixer$asset();
    @Accessor("trim")ArmorTrim texturemixer$trim();
    @Accessor("layerType")LayerType texturemixer$layer();
}
