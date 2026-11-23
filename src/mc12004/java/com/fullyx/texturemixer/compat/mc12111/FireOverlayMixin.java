package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.ResourceCompiler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameOverlayRenderer.class)
public abstract class FireOverlayMixin {
    @ModifyExpressionValue(method="renderFireOverlay",at=@At(value="FIELD",target="Lnet/minecraft/client/render/model/ModelLoader;FIRE_1:Lnet/minecraft/client/util/SpriteIdentifier;"))
    private static SpriteIdentifier texturemixer$fire(SpriteIdentifier original){
        var service=TextureMixerClient.SERVICE;
        var generation=service==null?null:service.runtime();
        if(generation!=null&&generation.assets().contains(ResourceCompiler.FIRE_OVERLAY_TEXTURE))
            return new SpriteIdentifier(original.getAtlasId(),new Identifier(ResourceCompiler.FIRE_OVERLAY_SPRITE.toString()));
        return original;
    }
}
