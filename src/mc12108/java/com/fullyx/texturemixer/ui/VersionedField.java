package com.fullyx.texturemixer.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

abstract class VersionedField extends TextFieldWidget {
    VersionedField(TextRenderer renderer,int x,int y,int width,int height,Text label){super(renderer,x,y,width,height,label);}
    public abstract boolean allowsFormatting();
    @Override public boolean charTyped(char input,int modifiers){
        if(allowsFormatting()&&isActive()&&input=='§'){write(String.valueOf(input));return true;}
        return super.charTyped(input,modifiers);
    }
}
