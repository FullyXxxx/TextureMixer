package com.fullyx.texturemixer.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class VersionedScreen extends Screen {
    protected VersionedScreen(Text title){super(title);}

    protected boolean mixerKey(int key,int modifiers,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    protected boolean mixerClick(double mouseX,double mouseY,int button,BooleanSupplier vanilla){return vanilla.getAsBoolean();}
    @Override public final boolean keyPressed(int key,int scanCode,int modifiers){
        return mixerKey(key,modifiers,()->super.keyPressed(key,scanCode,modifiers));
    }
    @Override public final boolean mouseClicked(double x,double y,int button){
        return mixerClick(x,y,button,()->super.mouseClicked(x,y,button));
    }
}
