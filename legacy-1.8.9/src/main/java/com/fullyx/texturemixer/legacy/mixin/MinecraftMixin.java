package com.fullyx.texturemixer.legacy.mixin;

import com.fullyx.texturemixer.legacy.LegacyMixer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ReloadableResourceManager;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(MinecraftClient.class)
public abstract class MinecraftMixin {
    @Redirect(method = "reloadResources", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ReloadableResourceManager;reload(Ljava/util/List;)V", ordinal = 0))
    private void texturemixer$reload(ReloadableResourceManager manager, List<ResourcePack> packs) {
        LegacyMixer.get((MinecraftClient) (Object) this).reload(manager, packs);
    }
}
