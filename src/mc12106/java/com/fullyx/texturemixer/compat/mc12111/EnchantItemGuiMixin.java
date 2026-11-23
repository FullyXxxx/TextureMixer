package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiRenderer.class)
public abstract class EnchantItemGuiMixin {
    @WrapMethod(method="prepareItemInitially")
    private void texturemixer$glint(ItemRenderState state,MatrixStack matrices,int x,int y,int size,Operation<Void> original){
        var effect=EnchantPreview.effect(state);
        if(effect==null)original.call(state,matrices,x,y,size);
        else EnchantPreview.scoped(effect,()->original.call(state,matrices,x,y,size));
    }
}
