package com.fullyx.texturemixer.compat.mc12111;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BakedQuad.MaterialInfo.class)
public abstract class PreviewMaterialMixin {
    @ModifyReturnValue(method = "of", at = @At("RETURN"))
    private static BakedQuad.MaterialInfo texturemixer$previewSheet(BakedQuad.MaterialInfo original) {
        var atlas = original.sprite().atlasLocation();
        if (!atlas.getNamespace().equals("texturemixer") || !atlas.getPath().startsWith("preview/")) return original;
        var layer = original.itemRenderType().hasBlending() ? RenderTypes.itemTranslucent(atlas) : RenderTypes.itemCutout(atlas);
        boolean translucent = layer.hasBlending();
        var glint = translucent ? RenderTypes.itemTranslucentGlint(atlas) : RenderTypes.itemCutoutGlint(atlas);
        var special = translucent ? RenderTypes.itemTranslucentGlintSpecial(atlas) : RenderTypes.itemCutoutGlintSpecial(atlas);
        return new BakedQuad.MaterialInfo(original.sprite(), original.layer(), layer, glint, special,
                original.tintIndex(), original.shadeDirectionOverride(), original.lightEmission());
    }
}
