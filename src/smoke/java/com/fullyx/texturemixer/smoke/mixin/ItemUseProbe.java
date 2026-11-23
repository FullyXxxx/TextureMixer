package com.fullyx.texturemixer.smoke.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface ItemUseProbe {
    @Accessor("itemUseTimeLeft") void texturemixer$useTimeLeft(int ticks);
}
