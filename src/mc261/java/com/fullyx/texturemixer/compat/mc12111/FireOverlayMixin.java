package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.ResourceCompiler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScreenEffectRenderer.class)
public abstract class FireOverlayMixin {
    @ModifyExpressionValue(method="renderScreenEffect",at=@At(value="FIELD",target="Lnet/minecraft/client/resources/model/ModelBakery;FIRE_1:Lnet/minecraft/client/resources/model/sprite/SpriteId;"))
    private SpriteId texturemixer$fire(SpriteId original){
        var service=TextureMixerClient.SERVICE;
        var generation=service==null?null:service.runtime();
        if(generation!=null&&generation.assets().contains(ResourceCompiler.FIRE_OVERLAY_TEXTURE))
            return new SpriteId(original.atlasLocation(),Identifier.parse(ResourceCompiler.FIRE_OVERLAY_SPRITE.toString()));
        return original;
    }
}
