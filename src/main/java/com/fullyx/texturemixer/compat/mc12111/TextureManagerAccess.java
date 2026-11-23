package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TextureManager.class)
public interface TextureManagerAccess { @Accessor("textures") Map<Identifier,AbstractTexture> texturemixer$textures(); }
