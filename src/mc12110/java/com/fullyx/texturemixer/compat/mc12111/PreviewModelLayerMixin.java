package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.NativeCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.model.BasicItemModel;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BasicItemModel.class)
public abstract class PreviewModelLayerMixin {
    @Shadow @Final private List<BakedQuad> quads;

    @ModifyExpressionValue(method="update",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/RenderLayers;getItemLayer(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$layer(RenderLayer original){
        if(quads.isEmpty())return original;
        var atlas=quads.getFirst().sprite().getAtlasId();
        return atlas.getNamespace().equals("texturemixer")&&atlas.getPath().startsWith("preview/")
                ?NativeCompat.translucentItemLayer(atlas):original;
    }
}
