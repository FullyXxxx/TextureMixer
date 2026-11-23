package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.client.UiFonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
public class FlatButton extends VersionedButton {
    public FlatButton(int x,int y,int width,int height,net.minecraft.text.Text text,PressAction action){
        super(x,y,width,height,text,action,DEFAULT_NARRATION_SUPPLIER);
    }
    public boolean selected;
    public String narrationLabel;
    private boolean primary;
    private boolean quiet;
    private UiDraw.Symbol symbol;
    private Tooltip description;

    FlatButton primary() { primary = true; return this; }
    FlatButton quiet() { quiet = true; return this; }
    FlatButton symbol(UiDraw.Symbol value) { symbol = value; return this; }
    @Override public void setTooltip(Tooltip tooltip) { description = tooltip; }
    Tooltip descriptionTooltip() { return description; }
    boolean tooltipHovered(int x,int y) { return visible && isHovered(); }
    @Override public void appendClickableNarrations(NarrationMessageBuilder builder) {
        super.appendClickableNarrations(builder);
        if (description != null) description.appendNarrations(builder);
    }
    @Override protected net.minecraft.text.MutableText getNarrationMessage(){
        var message=narrationLabel==null?super.getNarrationMessage():net.minecraft.client.gui.widget.ClickableWidget.getNarrationMessage(net.minecraft.text.Text.literal(narrationLabel));
        return selected?message.append(net.minecraft.text.Text.literal(tr("，当前选中"))):message;
    }
    @Override public void drawIcon(DrawContext ctx,int mouseX,int mouseY,float delta){
        boolean highlighted = active && isHovered();
        boolean light = primary && active;
        int fill = light ? (highlighted ? 0xffededed : 0xffd7d7d7)
                : !active ? (quiet ? 0 : 0x70414141)
                : selected ? 0xff646464 : highlighted ? 0xff575757 : quiet ? 0 : 0xe4474747;
        if (isFocused()) UiDraw.frame(ctx,getX(),getY(),getWidth(),getHeight(),4,fill,light?0xff8a8a8a:UiDraw.FOCUS);
        else UiDraw.rounded(ctx,getX(),getY(),getWidth(),getHeight(),4,fill);

        int color = light ? 0xff222222 : active ? UiDraw.TEXT : UiDraw.DISABLED;
        if (symbol != null) {
            UiDraw.symbol(ctx,symbol,getX()+getWidth()/2,getY()+getHeight()/2,color);
            return;
        }
        var font=UiFonts.renderer();
        String value=UiDraw.ellipsis(font,getMessage().getString(),getWidth()-8);
        ctx.drawText(font,value,getX()+(getWidth()-font.getWidth(value))/2,getY()+(getHeight()-8)/2,color,false);
    }
}
