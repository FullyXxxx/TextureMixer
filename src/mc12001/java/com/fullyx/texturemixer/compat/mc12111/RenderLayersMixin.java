package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(RenderLayer.class)
public abstract class RenderLayersMixin {
    @ModifyVariable(method={"getEntitySolid","getEntityCutout","getEntityCutoutNoCull","getEntityCutoutNoCullZOffset",
            "getItemEntityTranslucentCull","getEntityTranslucent","getEntitySmoothCutout","getEntityTranslucentEmissive",
            "getEntityNoOutline","getArmorCutoutNoCull"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static Identifier texturemixer$scopedTexture(Identifier id){return RenderScope.texture(id);}

    @ModifyReturnValue(method="getArmorEntityGlint",at=@At("RETURN"))
    private static RenderLayer texturemixer$armorGlint(RenderLayer original){
        return EnchantPreview.armorLayer(original);
    }
}
