package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.ImmediateVertices;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;

final class PreviewGui {
    private PreviewGui(){}
    static void submit(DrawContext context,String name,ItemDisplayState state,float x,float y,float size,ScreenRect clip){
        var matrices=context.getMatrices();
        boolean flat=!state.isSideLit();
        context.draw();
        if(clip!=null)context.enableScissor(clip.getLeft(),clip.getTop(),clip.getRight(),clip.getBottom());
        matrices.push();
        matrices.translate(x+size/2,y+size/2,150);
        matrices.scale(size,-size,size);
        if(flat)DiffuseLighting.disableGuiDepthLighting();
        try{
            context.draw(()->state.render(matrices,((ImmediateVertices)context).texturemixer$vertices(),0xf000f0,OverlayTexture.DEFAULT_UV));
        }finally{
            if(flat)DiffuseLighting.enableGuiDepthLighting();
            matrices.pop();
            if(clip!=null)context.disableScissor();
        }
    }
}
