package com.fullyx.texturemixer.compat.mc12111;

import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteAtlasTexture.class)
public interface AtlasDimensions {
    @Accessor("width") int texturemixer$width();
    @Accessor("height") int texturemixer$height();
}
