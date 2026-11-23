package com.fullyx.texturemixer.client;

import org.joml.Vector3f;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class PreviewItemModels {
    private PreviewItemModels(){}

    static String readArena(CompoundTag data){return data.getString("texturemixer_preview").orElse("");}

    static Object update(ItemModelResolver manager,ItemDisplayState state,ItemStack stack,boolean hand,Minecraft client){
        var display=hand?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.GUI;
        manager.updateForTopItem(state,stack,display,client.level,client.player,0);
        return display;
    }

    static void appendBlock(ItemDisplayState state,ModelManager models,BlockState blockState,Minecraft client,String token){
        var model=models.getBlockStateModelSet().get(blockState);if(model==null)return;
        state.appendModelIdentityElement(token+"|"+blockState);var layer=state.newLayer();
        List<BakedQuad> quads=new java.util.ArrayList<>();
        var parts=new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
        model.collectParts(net.minecraft.util.RandomSource.create(0),parts);
        for(var part:parts){quads.addAll(part.getQuads(null));for(Direction side:Direction.values())quads.addAll(part.getQuads(side));}
        int count=quads.stream().mapToInt(quad->quad.materialInfo().tintIndex()).max().orElse(-1)+1;
        var tints=layer.tintLayers();
        for(int i=0;i<count;i++){
            var tint=client.getBlockColors().getTintSource(blockState,i);
            tints.add(tint==null?-1:tint.color(blockState));
        }
        layer.setQuads(net.minecraft.client.resources.model.geometry.ItemQuads.split(quads));
        NativeCompat.geometryVertices(layer,quads);
        layer.setUsesBlockLight(true);
        layer.setParticleMaterial(model.particleMaterial());
        layer.setItemTransform(new ItemTransform(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f)));
        if(quads.stream().anyMatch(quad->NativeCompat.isAnimated(quad.materialInfo().sprite().contents())))state.setAnimated();
    }
}
