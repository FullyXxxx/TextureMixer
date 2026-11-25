package com.fullyx.texturemixer.legacy.mixin;

import com.fullyx.texturemixer.legacy.LegacyMixer;
import com.fullyx.texturemixer.legacy.LegacyScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ResourcePackScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ReloadableResourceManager;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(MinecraftClient.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen texturemixer$resourceScreen(Screen requested) {
        if (!(requested instanceof ResourcePackScreen)) return requested;
        Screen parent = ((ResourcePackScreenAccessor) requested).texturemixer$parent();
        return new LegacyScreen(parent, LegacyMixer.get((MinecraftClient) (Object) this));
    }

    @Redirect(method = "reloadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ReloadableResourceManager;reload(Ljava/util/List;)V", ordinal = 0))
    private void texturemixer$reload(ReloadableResourceManager manager, List<ResourcePack> packs) {
        LegacyMixer.get((MinecraftClient) (Object) this).reload(manager, packs);
    }
}
