package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(RenderLayers.class)
public abstract class RenderLayersMixin {
    @ModifyVariable(method={"entitySolid","entitySolidZOffsetForward","entityCutout","entityCutoutNoCull","entityCutoutNoCullZOffset",
            "itemEntityTranslucentCull","entityTranslucent","entitySmoothCutout","entityTranslucentEmissive","entityTranslucentEmissiveNoOutline",
            "entityNoOutline","armorCutoutNoCull"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static Identifier texturemixer$scopedTexture(Identifier id){return RenderScope.texture(id);}

    @ModifyReturnValue(method="armorEntityGlint",at=@At("RETURN"))
    private static RenderLayer texturemixer$armorGlint(RenderLayer original){
        return EnchantPreview.armorLayer(original);
    }
}
