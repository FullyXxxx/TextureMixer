package com.fullyx.texturemixer.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

abstract class VersionedField extends EditBox {
    VersionedField(Font renderer,int x,int y,int width,int height,Component label){super(renderer,x,y,width,height,label);}
    public abstract boolean allowsFormatting();
    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent input){
        if(allowsFormatting()&&canConsumeInput()&&input.codepoint()=='§'){insertText(input.codepointAsString());return true;}
        return super.charTyped(input);
    }
}
