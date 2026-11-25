package com.fullyx.texturemixer.legacy.mixin;

import com.fullyx.texturemixer.legacy.LegacyMixer;
import com.fullyx.texturemixer.legacy.LegacyScreen;
import net.minecraft.client.gui.screen.ResourcePackScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourcePackScreen.class)
abstract class ResourcePackScreenMixin extends Screen {
    private static final int MIXER_BUTTON = 21592;

    @Inject(method = "init", at = @At("RETURN"))
    private void texturemixer$addButton(CallbackInfo ci) {
        buttons.add(new ButtonWidget(MIXER_BUTTON, width - 99, 4, 94, 18, "TextureMixer"));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void texturemixer$open(ButtonWidget button, CallbackInfo ci) {
        if (button.id != MIXER_BUTTON || !button.active) return;
        client.setScreen(new LegacyScreen(this, LegacyMixer.get(client)));
        ci.cancel();
    }
}
