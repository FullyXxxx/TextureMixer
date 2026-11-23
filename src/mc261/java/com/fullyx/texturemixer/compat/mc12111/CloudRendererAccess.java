package com.fullyx.texturemixer.compat.mc12111;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CloudRenderer.class)
public interface CloudRendererAccess {
    @Invoker("packCellData")static long texturemixer$pack(int color,boolean north,boolean east,boolean south,boolean west){throw new AssertionError();}
}
