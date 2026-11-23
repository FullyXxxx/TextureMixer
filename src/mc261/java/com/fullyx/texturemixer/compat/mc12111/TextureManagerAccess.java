package com.fullyx.texturemixer.compat.mc12111;
import java.util.Map;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TextureManager.class)
public interface TextureManagerAccess { @Accessor("byPath") Map<Identifier,AbstractTexture> texturemixer$textures(); }
