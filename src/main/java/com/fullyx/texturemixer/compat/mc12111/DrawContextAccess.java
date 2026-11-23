package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(DrawContext.class)
public interface DrawContextAccess { @Accessor("state") GuiRenderState texturemixer$state(); }
