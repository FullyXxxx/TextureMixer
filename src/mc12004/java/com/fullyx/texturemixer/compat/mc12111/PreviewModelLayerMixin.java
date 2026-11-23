package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.client.RenderScope;
import com.fullyx.texturemixer.TextureMixerClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.registry.Registries;
import java.util.Objects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class PreviewModelLayerMixin {
    private static final String DRAW_ITEM="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V";

    @ModifyExpressionValue(method=DRAW_ITEM,at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/RenderLayers;getItemLayer(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$layer(RenderLayer original){
        return RenderScope.currentArena()==null?original
                :NativeCompat.translucentItemLayer(RenderScope.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
    }

    @WrapOperation(method=DRAW_ITEM,at=@At(value="INVOKE",
            target="Lnet/minecraft/client/render/item/BuiltinModelItemRenderer;render(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"))
    private void texturemixer$builtinTexture(BuiltinModelItemRenderer renderer,ItemStack stack,ModelTransformationMode display,
            MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay,Operation<Void> draw){
        if(RenderScope.currentArena()!=null){
            draw.call(renderer,stack,display,matrices,vertices,light,overlay);
            return;
        }
        var service=TextureMixerClient.SERVICE;
        String prefix=service==null?null:service.prefixForItem(Registries.ITEM.getId(stack.getItem()).toString());
        RenderScope.in(prefix,()->{draw.call(renderer,stack,display,matrices,vertices,light,overlay);return null;});
    }
}
