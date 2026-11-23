package com.fullyx.texturemixer.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.joml.Vector3f;

final class PreviewItemModels {
    private static final Transformation BLOCK_GUI=new Transformation(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f));

    private PreviewItemModels(){}

    static void blockTransform(MatrixStack matrices){
        BLOCK_GUI.apply(false,matrices);
        matrices.translate(-.5f,-.5f,-.5f);
    }

    static void addItem(ItemDisplayState state,ItemRenderer renderer,ItemStack stack,ModelTransformationMode mode,MinecraftClient client){
        var model=renderer.getModel(stack,client.world,client.player,0);
        state.setSideLit(model.isSideLit());
        state.add((matrices,vertices,light,overlay)->renderer.renderItem(stack,mode,false,matrices,vertices,light,overlay,model));
    }

    static ItemDisplayState.Layer block(PreviewCache.Arena arena,BlockState block){
        return (matrices,vertices,light,overlay)->{
            matrices.push();
            try{
                blockTransform(matrices);
                arena.blocks.renderBlockAsEntity(block,matrices,vertices,light,overlay);
            }finally{matrices.pop();}
        };
    }
}
