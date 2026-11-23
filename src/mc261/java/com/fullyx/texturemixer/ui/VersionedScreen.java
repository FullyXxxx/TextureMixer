package com.fullyx.texturemixer.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

abstract class VersionedScreen extends Screen {
    protected VersionedScreen(Component title){super(title);}

    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    @Override public final boolean keyPressed(KeyEvent input){
        return mixerKey(input.key(),input.modifiers(),()->super.keyPressed(input));
    }
    @Override public final boolean mouseClicked(MouseButtonEvent input,boolean doubled){
        return mixerClick(input.x(),input.y(),input.button(),()->super.mouseClicked(input,doubled));
    }
}
