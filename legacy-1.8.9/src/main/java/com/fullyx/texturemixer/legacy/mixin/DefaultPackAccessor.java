package com.fullyx.texturemixer.legacy.mixin;

import net.minecraft.resource.DefaultResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;
import java.util.Map;

@Mixin(DefaultResourcePack.class)
public interface DefaultPackAccessor {
    @Accessor("assetsIndex")
    Map<String, File> texturemixer$assetIndex();
}
