package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.*;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientWorld.class)
public abstract class WorldColorScopeMixin {
    @Inject(method="getColor",at=@At("HEAD"),cancellable=true)
    private void texturemixer$worldColor(BlockPos pos,ColorResolver resolver,CallbackInfoReturnable<Integer> callback){
        if(RenderScope.currentPrefix()!=null&&(resolver==BiomeColors.GRASS_COLOR||resolver==BiomeColors.FOLIAGE_COLOR||resolver==BiomeColors.DRY_FOLIAGE_COLOR))
            callback.setReturnValue(ColorBindings.worldColor((ClientWorld)(Object)this,pos,resolver));
    }
}
