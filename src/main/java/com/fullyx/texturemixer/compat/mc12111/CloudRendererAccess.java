package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CloudRenderer.class)
public interface CloudRendererAccess {
    @Invoker("packCloudCell")static long texturemixer$pack(int color,boolean north,boolean east,boolean south,boolean west){throw new AssertionError();}
}
