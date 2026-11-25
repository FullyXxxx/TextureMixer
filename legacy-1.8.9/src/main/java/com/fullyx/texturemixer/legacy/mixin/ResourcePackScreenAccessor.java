package com.fullyx.texturemixer.legacy.mixin;

import net.minecraft.client.gui.screen.ResourcePackScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePackScreen.class)
public interface ResourcePackScreenAccessor {
    @Accessor("parent")
    Screen texturemixer$parent();
}
