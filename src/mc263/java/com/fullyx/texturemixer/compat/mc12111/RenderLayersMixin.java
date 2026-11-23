package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(RenderTypes.class)
public abstract class RenderLayersMixin {
    @ModifyVariable(method={"entitySolid","entitySolidZOffsetForward","entityCutout","entityCutoutCull","entityCutoutZOffset",
            "entityTranslucentCull","entityTranslucent","itemTranslucent","entityTranslucentEmissive","breezeEyes",
            "armorCutoutNoCull","armorCutoutNoCullGlint","entitySolidGlint"},at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static Identifier texturemixer$scopedTexture(Identifier id){return RenderScope.texture(id);}
}
