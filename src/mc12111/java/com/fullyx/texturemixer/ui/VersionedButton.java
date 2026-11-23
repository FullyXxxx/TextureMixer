package com.fullyx.texturemixer.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

abstract class VersionedButton extends ButtonWidget {
    protected VersionedButton(int x,int y,int width,int height,net.minecraft.text.Text label,PressAction action,NarrationSupplier narration){
        super(x,y,width,height,label,action,narration);
    }
    public abstract void drawIcon(DrawContext context,int mouseX,int mouseY,float delta);
    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerDrag(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerRelease(BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected void mixerPress(Runnable vanilla){vanilla.run();}
    @Override public final boolean keyPressed(KeyInput input){
        return mixerKey(input.key(),input.modifiers(),()->super.keyPressed(input));
    }
    @Override public final boolean mouseClicked(Click input,boolean doubled){
        return mixerClick(input.x(),input.y(),input.button(),()->super.mouseClicked(input,doubled));
    }
    @Override public final boolean mouseDragged(Click input,double dx,double dy){
        return mixerDrag(input.x(),input.y(),input.button(),()->super.mouseDragged(input,dx,dy));
    }
    @Override public final boolean mouseReleased(Click input){
        return mixerRelease(()->super.mouseReleased(input));
    }
    @Override public final void onPress(AbstractInput input){mixerPress(()->super.onPress(input));}
}
