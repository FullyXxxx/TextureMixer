package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.NativeSkyBindings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LevelRenderer.class)
public abstract class WorldSkyMixin {
    @ModifyExpressionValue(method="lambda$addSkyPass$0",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/LevelRenderer;skyRenderer:Lnet/minecraft/client/renderer/SkyRenderer;"))
    private SkyRenderer texturemixer$sky(SkyRenderer original){return NativeSkyBindings.selected(original);}
}
