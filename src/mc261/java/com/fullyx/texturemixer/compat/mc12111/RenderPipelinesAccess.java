package com.fullyx.texturemixer.compat.mc12111;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccess {
    @Invoker("register") static RenderPipeline texturemixer$register(RenderPipeline pipeline){throw new AssertionError();}
}
