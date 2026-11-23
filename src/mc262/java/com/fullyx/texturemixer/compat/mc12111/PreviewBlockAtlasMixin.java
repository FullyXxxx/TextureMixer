package com.fullyx.texturemixer.compat.mc12111;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(SimpleModelWrapper.class)
public abstract class PreviewBlockAtlasMixin {
    @ModifyExpressionValue(method="findNonBlockSprites",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/texture/TextureAtlas;LOCATION_BLOCKS:Lnet/minecraft/resources/Identifier;"))
    private static Identifier texturemixer$acceptedAtlas(Identifier original){return RenderScope.atlas(original);}
}
