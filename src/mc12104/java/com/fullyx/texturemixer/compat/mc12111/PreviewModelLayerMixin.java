package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BasicItemModel.class)
public abstract class PreviewModelLayerMixin {
    @ModifyExpressionValue(method="update",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/RenderLayers;getItemLayer(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$layer(RenderLayer original){
        return RenderScope.currentArena()==null?original
                :NativeCompat.translucentItemLayer(RenderScope.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
    }
}
