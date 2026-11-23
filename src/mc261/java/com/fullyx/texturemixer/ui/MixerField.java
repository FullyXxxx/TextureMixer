package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Uses the game's editing and focus behavior inside TextureMixer's own field surface. */
public final class MixerField extends VersionedField {
    private static final int INSET = 6;

    MixerField(Font font, int x, int y, int width, int height, Component label) {
        this(font,x,y,width,height,label,false);
    }
    MixerField(Font font,int x,int y,int width,int height,Component label,boolean allowFormatting) {
        super(font, x + INSET, y + (height - 8) / 2, width - 2 * INSET, 8, label);
        setBordered(false);
        com.fullyx.texturemixer.client.NativeCompat.noFieldShadow(this);
        setTextColor(UiDraw.TEXT);
        com.fullyx.texturemixer.client.NativeCompat.configureField(this);
        this.allowFormatting=allowFormatting;
        this.insetTop = (height - 8) / 2;
        this.insetBottom = height - 8 - insetTop;
    }

    private final int insetTop, insetBottom;
    private final boolean allowFormatting;
    public boolean allowsFormatting(){return allowFormatting;}

    @Override public boolean isMouseOver(double x, double y) {
        return isVisible() && active && x >= getX() - INSET && x < getRight() + INSET
                && y >= getY() - insetTop && y < getBottom() + insetBottom;
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        int x = getX() - INSET, y = getY() - insetTop;
        int width = getWidth() + 2 * INSET, height = getHeight() + insetTop + insetBottom;
        int border = isFocused() ? UiDraw.FOCUS : isMouseOver(mouseX, mouseY) ? 0xff777777 : 0xff535353;
        UiDraw.frame(context, x, y, width, height, 4, 0xe8252525, border);
        super.extractWidgetRenderState(context,mouseX,mouseY,delta);
    }
}
