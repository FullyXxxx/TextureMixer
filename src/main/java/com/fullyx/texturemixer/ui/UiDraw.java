package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.ui.shared.MixerLayout;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;

/** Small native geometry; motion uses wall time and never delays an action or changes hitboxes. */
public final class UiDraw {
    static final int TEXT = MixerLayout.TEXT;
    static final int SECONDARY = MixerLayout.SECONDARY;
    static final int DISABLED = MixerLayout.DISABLED;
    static final int DETAIL = MixerLayout.DETAIL;
    static final int DIVIDER = MixerLayout.DIVIDER;
    static final int FOCUS = MixerLayout.FOCUS;
    static final int DIALOG_SHADE = 0x99000000;
    static final int CONTROL = MixerLayout.CONTROL;

    enum Symbol { ADD, REMOVE, UP, DOWN, LEFT, RIGHT, CLOSE, FOLDER }

    record Window(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }

        static Window centered(int screenWidth, int screenHeight, int maxWidth, int maxHeight) {
            int width = Math.min(maxWidth, screenWidth - 20);
            int height = Math.min(maxHeight, screenHeight - 16);
            return new Window((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        }

        void draw(DrawContext context) { window(context, x, y, width, height); }
    }

    static String ellipsis(TextRenderer font, String value, int width) {
        if (width < font.getWidth("…")) return "";
        if (font.getWidth(value) <= width) return value;
        return font.trimToWidth(value, width - font.getWidth("…")) + "…";
    }

    static net.minecraft.text.OrderedText ellipsis(TextRenderer font,net.minecraft.text.OrderedText text,int width){
        StringVisitableBuilder fitted=new StringVisitableBuilder(font,width-font.getWidth("…"));
        text.accept(fitted::append);
        fitted.parts.add(net.minecraft.text.OrderedText.styledForwardsVisitedString("…",net.minecraft.text.Style.EMPTY));
        return net.minecraft.text.OrderedText.concat(fitted.parts);
    }
    private static final class StringVisitableBuilder {
        private final java.util.List<net.minecraft.text.OrderedText> parts=new java.util.ArrayList<>();
        private final TextRenderer font;
        private int remaining;
        StringVisitableBuilder(TextRenderer font,int remaining){this.font=font;this.remaining=remaining;}
        boolean append(int index,net.minecraft.text.Style style,int codepoint){
            var character=net.minecraft.text.OrderedText.styled(codepoint,style);
            remaining-=font.getWidth(character);
            if(remaining<0)return false;
            parts.add(character);
            return true;
        }
    }

    public static boolean reduceMotion(){return false;}
    public static final class Motion {
        private float current;private long previous=System.nanoTime();
        public float value(float target,int duration){
            long now=System.nanoTime();double elapsed=Math.min(.05,(now-previous)/1e9);previous=now;
            current=(float)(current+(target-current)*(1-Math.exp(-elapsed*4600/duration)));
            if(Math.abs(current-target)<.002)current=target;return current;
        }
    }
    public static int mix(int a,int b,float amount){
        float t=Math.clamp(amount,0,1);int color=0;
        for(int shift:new int[]{0,8,16,24})color|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;
        return color;
    }
    public static void panel(DrawContext ctx,int x,int y,int width,int height,int color){
        if(width>0&&height>0&&(color>>>24)!=0)ctx.fill(x,y,x+width,y+height,color);
    }
    public static void window(DrawContext context,int x,int y,int width,int height){
        frame(context,x,y,width,height,MixerLayout.RADIUS,MixerLayout.WINDOW,MixerLayout.WINDOW_BORDER);
    }
    static void frame(DrawContext context, int x, int y, int width, int height, int radius, int fill, int border) {
        MixerLayout.frame(context::fill, x, y, width, height, radius, fill, border);
    }
    static void rounded(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        MixerLayout.rounded(context::fill, x, y, width, height, radius, color);
    }
    static void symbol(DrawContext context, Symbol symbol, int x, int y, int color) {
        MixerLayout.symbol(context::fill, MixerLayout.Symbol.valueOf(symbol.name()), x, y, color);
    }
    public static void outline(DrawContext ctx,int x,int y,int width,int height,int color){
        ctx.fill(x,y,x+width,y+1,color);ctx.fill(x,y+height-1,x+width,y+height,color);
        ctx.fill(x,y,x+1,y+height,color);ctx.fill(x+width-1,y,x+width,y+height,color);
    }
}
