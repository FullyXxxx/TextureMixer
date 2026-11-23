package com.fullyx.texturemixer.client;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

public final class BlockEntityPreview {
    private final BlockEntity entity;
    private final BlockEntityRenderer<BlockEntity> renderer;

    public static BlockEntityPreview create(PreviewCache.Arena arena,BlockState state){
        if(!(state.getBlock() instanceof BlockEntityProvider provider))return null;
        var blockEntity=provider.createBlockEntity(BlockPos.ORIGIN,state);
        if(blockEntity==null)return null;
        var renderer=arena.blockRenderers().get(blockEntity);
        return renderer==null?null:new BlockEntityPreview(blockEntity,renderer);
    }

    private BlockEntityPreview(BlockEntity entity,BlockEntityRenderer<BlockEntity> renderer){
        this.entity=entity;
        this.renderer=renderer;
    }

    void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay){
        matrices.push();
        try{
            PreviewItemModels.blockTransform(matrices);
            renderer.render(entity,0,matrices,vertices,light,overlay);
        }finally{matrices.pop();}
    }
}
