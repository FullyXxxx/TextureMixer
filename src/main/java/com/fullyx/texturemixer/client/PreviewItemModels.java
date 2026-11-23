package com.fullyx.texturemixer.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import java.util.List;

final class PreviewItemModels {
    private PreviewItemModels(){}

    static String readArena(NbtCompound data){return data.getString("texturemixer_preview").orElse("");}

    static Object update(ItemModelManager manager,ItemDisplayState state,ItemStack stack,boolean hand,MinecraftClient client){
        var display=hand?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.GUI;
        manager.clearAndUpdate(state,stack,display,client.world,client.player,0);
        return display;
    }

    static void appendBlock(ItemDisplayState state,BakedModelManager models,BlockState blockState,MinecraftClient client,String token){
        var model=models.getBlockModels().getModel(blockState);if(model==null)return;
        state.addModelKey(token+"|"+blockState);var layer=state.newLayer();
        List<BakedQuad> quads=layer.getQuads();
        for(var part:model.getParts(net.minecraft.util.math.random.Random.create(0))){quads.addAll(part.getQuads(null));for(Direction side:Direction.values())quads.addAll(part.getQuads(side));}
        int count=quads.stream().mapToInt(BakedQuad::tintIndex).max().orElse(-1)+1;int[] tints=layer.initTints(count);
        for(int i=0;i<count;i++)tints[i]=0xff000000|client.getBlockColors().getColor(blockState,null,null,i);
        NativeCompat.geometryVertices(layer,quads);layer.setUseLight(true);layer.setParticle(model.particleSprite());
        layer.setTransform(new Transformation(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f)));
        var atlas=SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
        layer.setRenderLayer(NativeCompat.blockPreviewLayer(blockState,atlas));
        if(quads.stream().anyMatch(quad->NativeCompat.isAnimated(quad.sprite().getContents())))state.markAnimated();
    }
}
