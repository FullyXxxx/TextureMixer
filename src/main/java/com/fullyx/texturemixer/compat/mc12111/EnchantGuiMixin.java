package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.OversizedItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SpecialGuiElementRenderer.class)
public abstract class EnchantGuiMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/gui/render/state/special/SpecialGuiElementRenderState;Lnet/minecraft/client/gui/render/state/GuiRenderState;I)V")
    private void texturemixer$glint(SpecialGuiElementRenderState state,GuiRenderState gui,int scale,Operation<Void> original){
        var effect=switch(state){
            case EntityGuiElementRenderState entity->EnchantPreview.take(entity);
            case OversizedItemGuiElementRenderState item->EnchantPreview.effect(item.guiItemRenderState().state());
            default->null;
        };
        if(effect==null)original.call(state,gui,scale);
        else EnchantPreview.scoped(effect,()->original.call(state,gui,scale));
    }
}
