package com.fullyx.texturemixer.compat.mc12111;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.RenderScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityScopeMixin {
    @WrapMethod(method="render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V")
    private void texturemixer$blockEntity(BlockEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider vertices,Operation<Void> nativeRender){
        var service=TextureMixerClient.SERVICE;
        String prefix=service==null?null:service.prefixForBlock(Registries.BLOCK.getId(entity.getCachedState().getBlock()).toString());
        RenderScope.in(prefix,()->{nativeRender.call(entity,delta,matrices,vertices);return null;});
    }
}
