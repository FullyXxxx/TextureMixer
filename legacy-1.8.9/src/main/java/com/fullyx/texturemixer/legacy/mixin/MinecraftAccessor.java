package com.fullyx.texturemixer.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DefaultResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftAccessor {
    @Accessor("defaultResourcePack")
    DefaultResourcePack texturemixer$defaultPack();
}
