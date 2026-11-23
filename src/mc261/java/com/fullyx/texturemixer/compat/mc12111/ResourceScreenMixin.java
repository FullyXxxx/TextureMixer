package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Minecraft.class)
public abstract class ResourceScreenMixin {
    /** Runs in the actual screen setter after external screen-replacement wrappers. */
    @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
    private Screen texturemixer$resourceScreen(Screen requested){return TextureMixerClient.resourcePackScreen(requested);}
}
