package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.sky.SkySceneRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyLayersMixin {
    @Shadow @Final private RenderTarget renderTarget;
    @Unique private List<Consumer<RenderPass>> texturemixer$celestials;
    @Unique private List<Consumer<RenderPass>> texturemixer$foreground;
    @Unique private List<Runnable> texturemixer$blendedCelestials;

    @WrapMethod(method = "render")
    private void texturemixer$render(GpuBufferSlice fog, SkyRenderState sky, Operation<Void> vanilla) {
        if (SkySceneRenderer.current() == null) {
            vanilla.call(fog, sky);
            return;
        }
        texturemixer$celestials = new ArrayList<>();
        texturemixer$foreground = new ArrayList<>();
        texturemixer$blendedCelestials = new ArrayList<>();
        try {
            // RenderPearl allows one active render pass. Finish the vanilla background
            // before the custom layers sample the current color attachment.
            vanilla.call(fog, sky);
            SkySceneRenderer.SHARED.render();
            texturemixer$replay(texturemixer$celestials);
            texturemixer$blendedCelestials.forEach(Runnable::run);
            texturemixer$replay(texturemixer$foreground);
        } finally {
            texturemixer$celestials = null;
            texturemixer$foreground = null;
            texturemixer$blendedCelestials = null;
        }
    }

    @Unique
    private void texturemixer$replay(List<Consumer<RenderPass>> draws) {
        if (draws.isEmpty()) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "TextureMixer sky foreground", renderTarget.getColorTextureView(),
                Optional.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            draws.forEach(draw -> draw.accept(pass));
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
    private void texturemixer$deferCelestials(SkyRenderer renderer, RenderPass pass, PoseStack matrices, float sun,
                                            float moon, float stars, MoonPhase phase, float rain, float starLight,
                                            Operation<Void> original) {
        if (texturemixer$celestials == null) original.call(renderer, pass, matrices, sun, moon, stars, phase, rain, starLight);
        else texturemixer$celestials.add(next -> original.call(renderer, next, matrices, sun, moon, stars, phase, rain, starLight));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderDarkDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void texturemixer$deferDarkDisc(SkyRenderer renderer, RenderPass pass, Operation<Void> original) {
        if (texturemixer$foreground == null) original.call(renderer, pass);
        else texturemixer$foreground.add(next -> original.call(renderer, next));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SkyRenderer;renderEndFlash(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V"))
    private void texturemixer$deferEndFlash(SkyRenderer renderer, RenderPass pass, PoseStack matrices,
                                          float intensity, float x, float y, Operation<Void> original) {
        if (texturemixer$foreground == null) original.call(renderer, pass, matrices, intensity, x, y);
        else texturemixer$foreground.add(next -> original.call(renderer, next, matrices, intensity, x, y));
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void texturemixer$sun(RenderPass pass, float alpha, PoseStack matrices, CallbackInfo callback) {
        if (texturemixer$deferBlend("sun", 0, alpha, matrices)) callback.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void texturemixer$moon(RenderPass pass, MoonPhase phase, float alpha, PoseStack matrices, CallbackInfo callback) {
        if (texturemixer$deferBlend("moon_phases", phase.index(), alpha, matrices)) callback.cancel();
    }

    @Unique
    private boolean texturemixer$deferBlend(String name, int phase, float alpha, PoseStack matrices) {
        var scene = SkySceneRenderer.current();
        if (texturemixer$blendedCelestials == null || scene == null || !scene.celestialBlends().containsKey(name)) return false;
        Matrix4f pose = new Matrix4f(matrices.last().pose());
        texturemixer$blendedCelestials.add(() -> {
            var saved = new PoseStack();
            saved.mulPose(pose);
            SkySceneRenderer.SHARED.renderCelestial(name, phase, alpha, saved);
        });
        return true;
    }
}
