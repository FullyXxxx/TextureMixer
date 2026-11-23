package com.fullyx.texturemixer.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

abstract class VersionedButton extends ButtonWidget {
    protected VersionedButton(int x,int y,int width,int height,net.minecraft.text.Text label,PressAction action,NarrationSupplier narration){
        super(x,y,width,height,label,action,narration);
    }
    public abstract void drawIcon(DrawContext context,int mouseX,int mouseY,float delta);
    @Override public final void renderButton(DrawContext context,int mouseX,int mouseY,float delta){
        drawIcon(context,mouseX,mouseY,delta);
    }
    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerDrag(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerRelease(BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected void mixerPress(Runnable vanilla){vanilla.run();}
    @Override public final boolean keyPressed(int key,int scanCode,int modifiers){
        return mixerKey(key,modifiers,()->super.keyPressed(key,scanCode,modifiers));
    }
    @Override public final boolean mouseClicked(double x,double y,int button){
        return mixerClick(x,y,button,()->super.mouseClicked(x,y,button));
    }
    @Override public final boolean mouseDragged(double x,double y,int button,double dx,double dy){
        return mixerDrag(x,y,button,()->super.mouseDragged(x,y,button,dx,dy));
    }
    @Override public final boolean mouseReleased(double x,double y,int button){
        return mixerRelease(()->super.mouseReleased(x,y,button));
    }
    @Override public final void onPress(){mixerPress(super::onPress);}
}
