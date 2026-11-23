package com.fullyx.texturemixer.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

abstract class VersionedButton extends Button {
    protected VersionedButton(int x,int y,int width,int height,net.minecraft.network.chat.Component label,OnPress action,CreateNarration narration){
        super(x,y,width,height,label,action,narration);
    }
    @Override protected final void extractContents(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta){
        renderContents(context,mouseX,mouseY,delta);
    }
    public abstract void renderContents(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta);
    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerDrag(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerRelease(BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected void mixerPress(Runnable vanilla){vanilla.run();}
    @Override public final boolean keyPressed(KeyEvent input){
        return mixerKey(input.key(),input.modifiers(),()->super.keyPressed(input));
    }
    @Override public final boolean mouseClicked(MouseButtonEvent input,boolean doubled){
        return mixerClick(input.x(),input.y(),input.button(),()->super.mouseClicked(input,doubled));
    }
    @Override public final boolean mouseDragged(MouseButtonEvent input,double dx,double dy){
        return mixerDrag(input.x(),input.y(),input.button(),()->super.mouseDragged(input,dx,dy));
    }
    @Override public final boolean mouseReleased(MouseButtonEvent input){
        return mixerRelease(()->super.mouseReleased(input));
    }
    @Override public final void onPress(InputWithModifiers input){mixerPress(()->super.onPress(input));}
}
