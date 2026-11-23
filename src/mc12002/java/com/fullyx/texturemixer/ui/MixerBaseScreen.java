package com.fullyx.texturemixer.ui;
import com.fullyx.texturemixer.client.UiFonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
public abstract class MixerBaseScreen extends VersionedScreen {
    protected final TextRenderer textRenderer=UiFonts.renderer();
    private FlatButton tooltipOwner;
    private long tooltipSince;
    private boolean skipBackground;
    protected MixerBaseScreen(Text title){super(title);}

    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) {
        if (skipBackground) return;
        if (client.world == null) super.renderBackground(context,mouseX,mouseY,delta);
        context.fill(0, 0, width, height, 0x4a000000);
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderWidgets(context, mouseX, mouseY, delta, true);
    }

    protected final void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta, boolean background) {
        // Older Screen.render draws its background before the widgets.
        skipBackground = !background;
        try {
            super.render(context, mouseX, mouseY, delta);
        } finally {
            skipBackground = false;
        }
        if (client.currentScreen != this) return;
        FlatButton hovered = null;
        for (var child : children()) {
            if (child instanceof FlatButton button && button.descriptionTooltip()!=null && button.tooltipHovered(mouseX,mouseY)) {
                hovered=button;
                break;
            }
        }
        FlatButton focused = getFocused() instanceof FlatButton button && button.visible && button.descriptionTooltip()!=null
                && client.getNavigationType().isKeyboard() ? button : null;
        FlatButton owner = hovered != null ? hovered : focused;
        long now=System.nanoTime();
        if (owner != tooltipOwner) {
            tooltipOwner=owner;
            tooltipSince=now;
        }
        if (owner==null || now-tooltipSince<270_000_000L) return;
        var lines=owner.descriptionTooltip().getLines(client);
        if (lines.isEmpty()) return;
        int availableLines=Math.min(lines.size(),Math.max(1,(height-20)/10));
        int w=Math.min(width-12,lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0)+12);
        int h=availableLines*10+8;
        int anchorX=hovered==null?owner.getX():mouseX+10;
        int anchorY=hovered==null?(owner.getY()+owner.getHeight())+4:mouseY+12;
        int y=anchorY+h>height-4 ? (hovered==null?owner.getY():mouseY)-h-5 : anchorY;
        int x=net.minecraft.util.math.MathHelper.clamp(anchorX,4,width-w-4);
        y=net.minecraft.util.math.MathHelper.clamp(y,4,height-h-4);
        com.fullyx.texturemixer.client.NativeCompat.newGuiLayer(context);
        UiDraw.frame(context,x,y,w,h,4,0xf0333333,0xff777777);
        context.enableScissor(x+6,y+3,x+w-5,y+h-3);
        for(int i=0;i<availableLines;i++)
            context.drawText(textRenderer,lines.get(i),x+6,y+4+i*10,UiDraw.TEXT,false);
        context.disableScissor();
    }
}
