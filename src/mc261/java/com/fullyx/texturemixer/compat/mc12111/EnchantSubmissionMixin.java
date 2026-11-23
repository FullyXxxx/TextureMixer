package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class)
public abstract class EnchantSubmissionMixin {
    @ModifyArg(method="entity",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addPicturesInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;)V"))
    private PictureInPictureRenderState texturemixer$bind(PictureInPictureRenderState state){
        if(state instanceof GuiEntityRenderState entity)EnchantPreview.capture(entity);
        return state;
    }
}
