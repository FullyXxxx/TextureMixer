package com.fullyx.texturemixer.smoke.mixin;

import com.fullyx.texturemixer.smoke.SmokeControls;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class FireProbe {
    @Inject(method="renderFireOverlay",at=@At("HEAD"))
    private static void texturemixer$capture(MatrixStack matrices,VertexConsumerProvider vertices,Sprite sprite,CallbackInfo callback){
        SmokeControls.fireSprite=sprite;
    }
}
