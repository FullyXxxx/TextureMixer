package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiGraphicsExtractor.class)
public interface DrawContextAccess { @Accessor("guiRenderState") GuiRenderState texturemixer$state(); }
