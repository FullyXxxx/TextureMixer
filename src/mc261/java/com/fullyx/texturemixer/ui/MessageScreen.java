package com.fullyx.texturemixer.ui;

import com.mojang.blaze3d.platform.InputConstants;

import com.fullyx.texturemixer.client.NativeCompat;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
/** Accessible full diagnostic text, opened explicitly from the compact status row. */
public final class MessageScreen extends MixerBaseScreen {
    private final Screen parent;private final String message;private List<FormattedCharSequence> lines=List.of();private UiDraw.Window window;private int offset;
    public MessageScreen(Screen parent,String title,String message){super(Component.literal(title));this.parent=parent;this.message=message;}
    protected void init(){
        int w=Math.min(400,width-20);
        lines=font.split(Component.literal(message),w-31);
        window=UiDraw.Window.centered(width,height,w,Math.min(238,Math.max(90,60+lines.size()*11)));
        offset=Math.clamp(offset,0,Math.max(0,lines.size()-rows()));
        addRenderableWidget(new VerticalScrollbar(window.right()-13,window.y()+31,rows()*11,
                ()->offset,()->lines.size(),()->rows(),1,value->offset=(int)Math.round(value)));
        addRenderableWidget(new FlatButton(window.right()-67,window.bottom()-28,55,UiDraw.CONTROL,Component.literal(tr("返回")),b->onClose()));
    }
    private int rows(){return Math.max(1,(window.height()-60)/11);}
    public void extractRenderState(GuiGraphicsExtractor context,int mx,int my,float delta){
        window.draw(context);
        context.text(font,UiDraw.ellipsis(font,title.getString(),window.width()-24),window.x()+12,window.y()+12,UiDraw.TEXT,false);
        for(int i=offset;i<Math.min(lines.size(),offset+rows());i++)
            context.text(font,lines.get(i),window.x()+12,window.y()+31+(i-offset)*11,UiDraw.SECONDARY,false);
        super.extractRenderState(context,mx,my,delta);
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset=Math.clamp(offset-(int)Math.signum(vertical)*3,0,Math.max(0,lines.size()-rows()));return true;}
    protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla){if(key==InputConstants.KEY_PAGEDOWN||key==InputConstants.KEY_PAGEUP){offset=Math.clamp(offset+(key==InputConstants.KEY_PAGEDOWN?rows():-rows()),0,Math.max(0,lines.size()-rows()));return true;}return vanilla.getAsBoolean();}
    public boolean isPauseScreen(){return false;}public void onClose(){NativeCompat.setScreen(minecraft, parent);}
}
