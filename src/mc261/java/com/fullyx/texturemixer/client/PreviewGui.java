package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.DrawContextAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.joml.Matrix3x2f;

final class PreviewGui {
    private PreviewGui(){}
    static void submit(GuiGraphicsExtractor context,String name,ItemDisplayState state,float x,float y,float size,ScreenRectangle clip){
        var matrices=context.pose();
        matrices.pushMatrix();
        matrices.translate(x,y);
        matrices.scale(size/16,size/16);
        ((DrawContextAccess)context).texturemixer$state().addItem(new GuiItemRenderState(new Matrix3x2f(matrices),state,0,0,clip));
        matrices.popMatrix();
    }
}
