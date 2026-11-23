package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockRenderManager.class)
public abstract class BlockPreviewLayerMixin {
    @WrapOperation(method="renderBlockAsEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/RenderLayers;getEntityBlockLayer(Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/client/render/RenderLayer;"))
    private RenderLayer texturemixer$layer(BlockState state,boolean direct,Operation<RenderLayer> original){
        return RenderScope.currentArena()==null?original.call(state,direct)
                :NativeCompat.blockPreviewLayer(state,RenderScope.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
    }
}
