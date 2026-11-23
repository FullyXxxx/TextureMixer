package com.fullyx.texturemixer.compat.mc12111;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface ItemColorsAccess {
    @Accessor("itemColors") ItemColors texturemixer$itemColors();
}
