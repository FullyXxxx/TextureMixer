package com.fullyx.texturemixer.compat.mc12111;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface ItemDisplayModeAccess {
    @Accessor("modelTransformationMode")
    void texturemixer$display(ModelTransformationMode mode);
}
