package com.fullyx.texturemixer.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class VersionedScreen extends Screen {
    protected VersionedScreen(Text title){super(title);}

    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    @Override public final boolean keyPressed(KeyInput input){
        return mixerKey(input.key(),input.modifiers(),()->super.keyPressed(input));
    }
    @Override public final boolean mouseClicked(Click input,boolean doubled){
        return mixerClick(input.x(),input.y(),input.button(),()->super.mouseClicked(input,doubled));
    }
}
