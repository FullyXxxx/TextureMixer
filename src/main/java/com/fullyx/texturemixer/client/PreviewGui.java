package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.DrawContextAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.joml.Matrix3x2f;

final class PreviewGui {
    private PreviewGui(){}
    static void submit(DrawContext context,String name,ItemDisplayState state,float x,float y,float size,ScreenRect clip){
        var matrices=context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x,y);
        matrices.scale(size/16,size/16);
        ((DrawContextAccess)context).texturemixer$state().addItem(new ItemGuiElementRenderState(name,new Matrix3x2f(matrices),state,0,0,clip));
        matrices.popMatrix();
    }
}
