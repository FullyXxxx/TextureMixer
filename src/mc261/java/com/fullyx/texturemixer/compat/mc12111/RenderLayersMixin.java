package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.client.EnchantPreview;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(RenderTypes.class)
public abstract class RenderLayersMixin {
    @ModifyVariable(method={"entitySolid","entitySolidZOffsetForward","entityCutout","entityCutoutCull","entityCutoutZOffset",
            "entityTranslucentCullItemTarget","entityTranslucent","itemTranslucent","entityTranslucentEmissive","breezeEyes",
            "armorCutoutNoCull"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static Identifier texturemixer$scopedTexture(Identifier id){return RenderScope.texture(id);}

    @ModifyReturnValue(method="armorEntityGlint",at=@At("RETURN"))
    private static RenderType texturemixer$armorGlint(RenderType original){
        return EnchantPreview.armorLayer(original);
    }
}
