package com.fullyx.texturemixer.smoke.mixin;
import net.minecraft.client.gui.hud.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BossBarHud.class)
public interface BossProbe {
    @Accessor("bossBars") Map<UUID,ClientBossBar> texturemixer$bars();
}
