package com.fullyx.texturemixer.compat.mc12111;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccess {
    @Invoker("create")
    static RenderType texturemixer$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
