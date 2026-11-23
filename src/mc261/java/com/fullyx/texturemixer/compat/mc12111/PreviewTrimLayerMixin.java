package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Sheets.class)
public abstract class PreviewTrimLayerMixin {
    @Inject(method="armorTrimsSheet",at=@At("HEAD"),cancellable=true)
    private static void texturemixer$trimLayer(boolean decal,CallbackInfoReturnable<RenderType> callback){
        if(RenderScope.currentArena()!=null)callback.setReturnValue(com.fullyx.texturemixer.client.NativeCompat.armorCutout(RenderScope.atlas(Sheets.ARMOR_TRIMS_SHEET)));
    }
}
