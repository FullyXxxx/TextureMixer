package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.model.GeometryBakedModel;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(GeometryBakedModel.class)
public abstract class PreviewBlockAtlasMixin {
    @ModifyExpressionValue(method="create",at=@At(value="FIELD",target="Lnet/minecraft/client/texture/SpriteAtlasTexture;BLOCK_ATLAS_TEXTURE:Lnet/minecraft/util/Identifier;"))
    private static Identifier texturemixer$acceptedAtlas(Identifier original){return RenderScope.atlas(original);}
}
