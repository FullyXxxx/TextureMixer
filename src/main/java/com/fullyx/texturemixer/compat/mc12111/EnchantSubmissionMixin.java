package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DrawContext.class)
public abstract class EnchantSubmissionMixin {
    @ModifyArg(method="addEntity",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/gui/render/state/GuiRenderState;addSpecialElement(Lnet/minecraft/client/gui/render/state/special/SpecialGuiElementRenderState;)V"))
    private SpecialGuiElementRenderState texturemixer$bind(SpecialGuiElementRenderState state){
        if(state instanceof EntityGuiElementRenderState entity)EnchantPreview.capture(entity);
        return state;
    }
}
