package com.fullyx.texturemixer.smoke.mixin;
import com.fullyx.texturemixer.smoke.SmokeControls;
import com.fullyx.texturemixer.sky.SkySceneRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SkyRendering.class)
public abstract class SkyProbe {
    @Inject(method="renderEndSky",at=@At("RETURN"),order=2000)
    private void texturemixer$skyFrame(CallbackInfo callback){
        if(!SmokeControls.capturedEnd&&SkySceneRenderer.SHARED.draws("end")>3){SmokeControls.capturedEnd=true;
            var client=MinecraftClient.getInstance();ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-end-sky-pass.png",client.getFramebuffer(),1,t->{});
            var scene=SkySceneRenderer.current();var id=scene.layers().getFirst().texture();var texture=client.getTextureManager().getTexture(net.minecraft.util.Identifier.of(SkySceneRenderer.prefix("end")+id.namespace(),id.path()));
            System.out.println("END_GPU: "+texture.getGlTexture().getWidth(0)+"x"+texture.getGlTexture().getHeight(0)+", modelview="+com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        }
    }
}
