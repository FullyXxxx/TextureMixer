package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PictureInPictureRenderer.class)
public abstract class EnchantGuiMixin {
    @WrapMethod(method="prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;I)V")
    private void texturemixer$glint(PictureInPictureRenderState state,GuiRenderState gui,FeatureRenderDispatcher features,int scale,Operation<Void> original){
        var effect=switch(state){
            case GuiEntityRenderState entity->EnchantPreview.take(entity);
            case OversizedItemRenderState item->EnchantPreview.effect(item.guiItemRenderState().itemStackRenderState());
            default->null;
        };
        if(effect==null)original.call(state,gui,features,scale);
        else EnchantPreview.scoped(effect,()->original.call(state,gui,features,scale));
    }
}
