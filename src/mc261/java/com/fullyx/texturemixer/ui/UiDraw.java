package com.fullyx.texturemixer.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small native geometry; motion uses wall time and never delays an action or changes hitboxes. */
public final class UiDraw {
    static final int TEXT = 0xfff2f2f2;
    static final int SECONDARY = 0xffc0c0c0;
    static final int DISABLED = 0xff969696;
    static final int DETAIL = 0x70202020;
    static final int DIVIDER = 0x497c7c7c;
    static final int FOCUS = 0xffdedede;
    static final int DIALOG_SHADE = 0x99000000;
    static final int CONTROL = 18;

    enum Symbol { ADD, REMOVE, UP, DOWN, LEFT, RIGHT, CLOSE, FOLDER }

    record Window(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }

        static Window centered(int screenWidth, int screenHeight, int maxWidth, int maxHeight) {
            int width = Math.min(maxWidth, screenWidth - 20);
            int height = Math.min(maxHeight, screenHeight - 16);
            return new Window((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        }

        void draw(GuiGraphicsExtractor context) { window(context, x, y, width, height); }
    }

    static String ellipsis(Font font, String value, int width) {
        if (width < font.width("…")) return "";
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, width - font.width("…")) + "…";
    }

    static net.minecraft.util.FormattedCharSequence ellipsis(Font font,net.minecraft.util.FormattedCharSequence text,int width){
        StringVisitableBuilder fitted=new StringVisitableBuilder(font,width-font.width("…"));
        text.accept(fitted::append);
        fitted.parts.add(net.minecraft.util.FormattedCharSequence.forward("…",net.minecraft.network.chat.Style.EMPTY));
        return net.minecraft.util.FormattedCharSequence.composite(fitted.parts);
    }
    private static final class StringVisitableBuilder {
        private final java.util.List<net.minecraft.util.FormattedCharSequence> parts=new java.util.ArrayList<>();
        private final Font font;
        private int remaining;
        StringVisitableBuilder(Font font,int remaining){this.font=font;this.remaining=remaining;}
        boolean append(int index,net.minecraft.network.chat.Style style,int codepoint){
            var character=net.minecraft.util.FormattedCharSequence.codepoint(codepoint,style);
            remaining-=font.width(character);
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
    public static void panel(GuiGraphicsExtractor ctx,int x,int y,int width,int height,int color){
        if(width>0&&height>0&&(color>>>24)!=0)ctx.fill(x,y,x+width,y+height,color);
    }
    public static void window(GuiGraphicsExtractor context,int x,int y,int width,int height){
        frame(context,x,y,width,height,8,0xe0323232,0x927d7d7d);
    }
    static void frame(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, int fill, int border) {
        if (width<3||height<3) return;
        radius=Math.clamp(radius,1,Math.min(width,height)/2);
        panel(context,x+1,y+radius,width-2,height-2*radius,fill);
        panel(context,x,y+radius,1,height-2*radius,border);
        panel(context,x+width-1,y+radius,1,height-2*radius,border);
        for(int row=0;row<radius;row++){
            int outer=cornerInset(radius,row);
            int inner=row==0?(width+1)/2:1+cornerInset(radius-1,row-1);
            frameRow(context,x,y+row,width,outer,inner,fill,border);
            frameRow(context,x,y+height-row-1,width,outer,inner,fill,border);
        }
    }
    private static void frameRow(GuiGraphicsExtractor context,int x,int y,int width,int outer,int inner,int fill,int border){
        if(width<=2*inner){
            panel(context,x+outer,y,width-2*outer,1,border);
            return;
        }
        panel(context,x+outer,y,inner-outer,1,border);
        panel(context,x+inner,y,width-2*inner,1,fill);
        panel(context,x+width-inner,y,inner-outer,1,border);
    }
    private static int cornerInset(int radius,int row){
        double distance=radius-row-.5;
        return (int)Math.ceil(radius-Math.sqrt(radius*radius-distance*distance));
    }
    static void rounded(GuiGraphicsExtractor context,int x,int y,int width,int height,int radius,int color){
        if(width<=0||height<=0||(color>>>24)==0)return;
        radius=Math.min(radius,Math.min(width,height)/2);
        context.fill(x,y+radius,x+width,y+height-radius,color);
        for(int row=0;row<radius;row++){
            int inset=cornerInset(radius,row);
            context.fill(x+inset,y+row,x+width-inset,y+row+1,color);
            context.fill(x+inset,y+height-row-1,x+width-inset,y+height-row,color);
        }
    }
    static void symbol(GuiGraphicsExtractor context, Symbol symbol, int x, int y, int color) {
        switch (symbol) {
            case ADD, REMOVE -> {
                context.fill(x - 3, y, x + 4, y + 1, color);
                if (symbol == Symbol.ADD) context.fill(x, y - 3, x + 1, y + 4, color);
            }
            case LEFT, RIGHT, UP, DOWN -> {
                int direction = symbol == Symbol.LEFT || symbol == Symbol.UP ? -1 : 1;
                for (int offset = -3; offset <= 3; offset++) {
                    int tip = direction * (2 - Math.abs(offset));
                    if (symbol == Symbol.LEFT || symbol == Symbol.RIGHT)
                        context.fill(x + tip, y + offset, x + tip + 1, y + offset + 1, color);
                    else context.fill(x + offset, y + tip, x + offset + 1, y + tip + 1, color);
                }
            }
            case CLOSE -> {
                for (int i = -2; i <= 2; i++) {
                    context.fill(x + i, y + i, x + i + 1, y + i + 1, color);
                    context.fill(x + i, y - i, x + i + 1, y - i + 1, color);
                }
            }
            case FOLDER -> {
                context.fill(x - 6, y - 5, x - 1, y - 3, color);
                rounded(context, x - 6, y - 3, 13, 9, 2, color);
            }
        }
    }
    public static void outline(GuiGraphicsExtractor ctx,int x,int y,int width,int height,int color){
        ctx.fill(x,y,x+width,y+1,color);ctx.fill(x,y+height-1,x+width,y+height,color);
        ctx.fill(x,y,x+1,y+height,color);ctx.fill(x+width-1,y,x+width,y+height,color);
    }
}
