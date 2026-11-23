package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.client.UiFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
public abstract class MixerBaseScreen extends VersionedScreen {
    protected final Font font=UiFonts.renderer();
    private FlatButton tooltipOwner;
    private long tooltipSince;
    protected MixerBaseScreen(Component title){super(title);}

    @Override public void extractBackground(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        if (minecraft.level == null) extractPanorama(context, delta);
        context.fill(0, 0, width, height, 0x4a000000);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        super.extractRenderState(context,mouseX,mouseY,delta);
        if (NativeCompat.screen(minecraft) != this) return;
        FlatButton hovered = null;
        for (var child : children()) {
            if (child instanceof FlatButton button && button.descriptionTooltip()!=null && button.tooltipHovered(mouseX,mouseY)) {
                hovered=button;
                break;
            }
        }
        FlatButton focused = getFocused() instanceof FlatButton button && button.visible && button.descriptionTooltip()!=null
                && minecraft.getLastInputType().isKeyboard() ? button : null;
        FlatButton owner = hovered != null ? hovered : focused;
        long now=System.nanoTime();
        if (owner != tooltipOwner) {
            tooltipOwner=owner;
            tooltipSince=now;
        }
        if (owner==null || now-tooltipSince<270_000_000L) return;
        var lines=owner.descriptionTooltip().toCharSequence(minecraft);
        if (lines.isEmpty()) return;
        int availableLines=Math.min(lines.size(),Math.max(1,(height-20)/10));
        int w=Math.min(width-12,lines.stream().mapToInt(font::width).max().orElse(0)+12);
        int h=availableLines*10+8;
        int anchorX=hovered==null?owner.getX():mouseX+10;
        int anchorY=hovered==null?owner.getBottom()+4:mouseY+12;
        int y=anchorY+h>height-4 ? (hovered==null?owner.getY():mouseY)-h-5 : anchorY;
        int x=Math.clamp(anchorX,4,width-w-4);
        y=Math.clamp(y,4,height-h-4);
        com.fullyx.texturemixer.client.NativeCompat.newGuiLayer(context);
        UiDraw.frame(context,x,y,w,h,4,0xf0333333,0xff777777);
        context.enableScissor(x+6,y+3,x+w-5,y+h-3);
        for(int i=0;i<availableLines;i++)
            context.text(font,lines.get(i),x+6,y+4+i*10,UiDraw.TEXT,false);
        context.disableScissor();
    }
}
