package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Font.class)
public interface TextRendererAccess { @Accessor("provider")Font.Provider texturemixer$fonts(); }
