package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientLevel.class)
public abstract class WorldColorScopeMixin {
    @Inject(method="getBlockTint",at=@At("HEAD"),cancellable=true)
    private void texturemixer$worldColor(BlockPos pos,ColorResolver resolver,CallbackInfoReturnable<Integer> callback){
        if(RenderScope.currentPrefix()!=null&&(resolver==BiomeColors.GRASS_COLOR_RESOLVER||resolver==BiomeColors.FOLIAGE_COLOR_RESOLVER||resolver==BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER))
            callback.setReturnValue(ColorBindings.worldColor((ClientLevel)(Object)this,pos,resolver));
    }
}
