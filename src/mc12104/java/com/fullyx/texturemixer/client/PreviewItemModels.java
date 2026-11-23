package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.ItemDisplayModeAccess;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;
import java.util.List;

final class PreviewItemModels {
    private PreviewItemModels(){}

    static String readArena(NbtCompound data){return data.getString("texturemixer_preview");}

    static Object update(ItemModelManager manager,ItemDisplayState state,ItemStack stack,boolean hand,MinecraftClient client){
        var display=hand?ModelTransformationMode.FIRST_PERSON_RIGHT_HAND:ModelTransformationMode.GUI;
        manager.update(state,stack,display,false,client.world,client.player,0);
        return display;
    }

    private static final Transformation BLOCK_GUI=new Transformation(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f));
    private static final ModelTransformation BLOCK_DISPLAY=new ModelTransformation(
            Transformation.IDENTITY,Transformation.IDENTITY,Transformation.IDENTITY,Transformation.IDENTITY,
            Transformation.IDENTITY,BLOCK_GUI,Transformation.IDENTITY,Transformation.IDENTITY);
    private static final Map<Direction,List<BakedQuad>> EMPTY_SIDES=emptySides();

    private static Map<Direction,List<BakedQuad>> emptySides(){
        var faces=new EnumMap<Direction,List<BakedQuad>>(Direction.class);
        for(Direction face:Direction.values())faces.put(face,List.of());
        return Map.copyOf(faces);
    }

    static BakedModel forGui(BakedModel model,List<BakedQuad> quads){
        return new BasicBakedModel(quads,EMPTY_SIDES,false,true,true,model.getParticleSprite(),BLOCK_DISPLAY);
    }

    static void appendBlock(ItemDisplayState state,BakedModelManager models,BlockState block,MinecraftClient client,String token){
        var model=models.getBlockModels().getModel(block);
        if(model==null)return;
        ((ItemDisplayModeAccess)state).texturemixer$display(ModelTransformationMode.GUI);
        List<BakedQuad> quads=new ArrayList<>(model.getQuads(block,null,Random.create(0)));
        for(Direction side:Direction.values())quads.addAll(model.getQuads(block,side,Random.create(0)));
        var layer=state.newLayer();
        int count=quads.stream().mapToInt(BakedQuad::getTintIndex).max().orElse(-1)+1;
        int[] tints=layer.initTints(count);
        for(int i=0;i<count;i++)tints[i]=0xff000000|client.getBlockColors().getColor(block,null,null,i);
        var atlas=RenderScope.atlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        layer.setModel(forGui(model,quads),NativeCompat.blockPreviewLayer(block,atlas));
    }
}
