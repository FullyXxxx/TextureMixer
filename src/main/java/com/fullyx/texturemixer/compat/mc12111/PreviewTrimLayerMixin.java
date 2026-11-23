package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(TexturedRenderLayers.class)
public abstract class PreviewTrimLayerMixin {
    @Inject(method="getArmorTrims",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$trimLayer(boolean decal,CallbackInfoReturnable<RenderLayer> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(com.fullyx.texturemixer.client.NativeCompat.armorCutout(RenderScope.atlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE)));
    }
}
