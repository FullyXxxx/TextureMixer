package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.sky.NativeSkyBindings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(WorldRenderer.class)
public abstract class WorldSkyMixin {
    @ModifyExpressionValue(method="renderSky",at=@At(value="FIELD",target="Lnet/minecraft/client/render/WorldRenderer;skyRendering:Lnet/minecraft/client/render/SkyRendering;"))
    private SkyRendering texturemixer$sky(SkyRendering original){return NativeSkyBindings.selected(original);}
}
