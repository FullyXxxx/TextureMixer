package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiItemAtlas.class)
public abstract class EnchantItemGuiMixin {
    @WrapMethod(method="getOrUpdate")
    private GuiItemAtlas.SlotView texturemixer$glint(TrackingItemStackRenderState state,Operation<GuiItemAtlas.SlotView> original){
        var effect=EnchantPreview.effect(state);
        if(effect==null)return original.call(state);
        return EnchantPreview.inScope(effect,()->original.call(state));
    }
}
