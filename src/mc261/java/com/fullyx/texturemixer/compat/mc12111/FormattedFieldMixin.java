package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.ui.MixerField;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EditBox.class)
public abstract class FormattedFieldMixin {
    @WrapOperation(method="insertText",at=@At(value="INVOKE",target="Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String texturemixer$preserveFormatting(String value,Operation<String> original){
        if(!((Object)this instanceof MixerField field)||!field.allowsFormatting())return original.call(value);
        StringBuilder filtered=new StringBuilder(value.length());
        value.codePoints().filter(codepoint->codepoint=='§'||(codepoint>Character.MAX_VALUE||StringUtil.isAllowedChatCharacter((char)codepoint))).forEach(filtered::appendCodePoint);
        return filtered.toString();
    }
}
