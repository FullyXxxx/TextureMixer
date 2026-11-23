package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TextRenderer.class)
public interface TextRendererAccess { @Accessor("fonts")TextRenderer.GlyphsProvider texturemixer$fonts(); }
