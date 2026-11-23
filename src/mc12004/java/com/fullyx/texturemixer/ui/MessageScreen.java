package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.*;
import java.util.List;
import org.lwjgl.glfw.GLFW;
/** Accessible full diagnostic text, opened explicitly from the compact status row. */
public final class MessageScreen extends MixerBaseScreen {
    private final Screen parent;private final String message;private List<OrderedText> lines=List.of();private UiDraw.Window window;private int offset;
    public MessageScreen(Screen parent,String title,String message){super(Text.literal(title));this.parent=parent;this.message=message;}
    protected void init(){
        int w=Math.min(400,width-20);
        lines=textRenderer.wrapLines(Text.literal(message),w-31);
        window=UiDraw.Window.centered(width,height,w,Math.min(238,Math.max(90,60+lines.size()*11)));
        offset=net.minecraft.util.math.MathHelper.clamp(offset,0,Math.max(0,lines.size()-rows()));
        addDrawableChild(new VerticalScrollbar(window.right()-13,window.y()+31,rows()*11,
                ()->offset,()->lines.size(),()->rows(),1,value->offset=(int)Math.round(value)));
        addDrawableChild(new FlatButton(window.right()-67,window.bottom()-28,55,UiDraw.CONTROL,Text.literal(tr("返回")),b->close()));
    }
    private int rows(){return Math.max(1,(window.height()-60)/11);}
    public void render(DrawContext context,int mx,int my,float delta){
        window.draw(context);
        context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,title.getString(),window.width()-24),window.x()+12,window.y()+12,UiDraw.TEXT,false);
        for(int i=offset;i<Math.min(lines.size(),offset+rows());i++)
            context.drawText(textRenderer,lines.get(i),window.x()+12,window.y()+31+(i-offset)*11,UiDraw.SECONDARY,false);
        super.render(context,mx,my,delta);
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset=net.minecraft.util.math.MathHelper.clamp(offset-(int)Math.signum(vertical)*3,0,Math.max(0,lines.size()-rows()));return true;}
    protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla){if(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP){offset=net.minecraft.util.math.MathHelper.clamp(offset+(key==GLFW.GLFW_KEY_PAGE_DOWN?rows():-rows()),0,Math.max(0,lines.size()-rows()));return true;}return vanilla.getAsBoolean();}
    public boolean shouldPause(){return false;}public void close(){client.setScreen(parent);}
}
