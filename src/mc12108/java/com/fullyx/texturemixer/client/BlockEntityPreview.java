package com.fullyx.texturemixer.client;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.*;
import java.util.function.Consumer;

/** Local, unregistered block entities and local renderer models. Never attaches a preview to the live world. */
public final class BlockEntityPreview implements SpecialModelRenderer<BlockEntity> {
    private final PreviewCache.Arena arena;
    private final BlockEntityRenderer<BlockEntity> renderer;
    private final BlockEntity entity;

    public static BlockEntityPreview create(PreviewCache.Arena arena,BlockState block,ResourceManager resources){
        if(!(block.getBlock() instanceof BlockEntityProvider provider))return null;
        var entity=provider.createBlockEntity(BlockPos.ORIGIN,block);
        if(entity==null)return null;
        var client=MinecraftClient.getInstance();
        var blocks=NativeCompat.blockRenderManager(arena,client);
        var renderers=new BlockEntityRenderDispatcher(UiFonts.renderer(),arena.models.getEntityModelsSupplier(),blocks,
                arena.items,client.getItemRenderer(),client.getEntityRenderDispatcher());
        renderers.reload(resources);
        BlockEntityRenderer<BlockEntity> renderer=renderers.get(entity);
        return renderer==null?null:new BlockEntityPreview(arena,renderer,entity);
    }
    private BlockEntityPreview(PreviewCache.Arena arena,BlockEntityRenderer<BlockEntity> renderer,BlockEntity entity){
        this.arena=arena;this.renderer=renderer;this.entity=entity;
    }
    public void addLayer(ItemDisplayState target){
        target.addModelKey(arena.token+"|block-entity|"+entity.getCachedState());var layer=target.newLayer();
        layer.setUseLight(true);layer.setTransform(new Transformation(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f)));
        NativeCompat.entityVertices(layer);layer.setSpecialModel(this,entity);
    }
    public void render(BlockEntity data,ItemDisplayContext display,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay,boolean glint){
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderer.render(data,0,matrices,vertices,light,overlay,Vec3d.ZERO);return null;}));
    }
    public void collectVertices(java.util.Set<Vector3f> vertices){vertices.add(new Vector3f());vertices.add(new Vector3f(1,1,1));}
    public BlockEntity getData(ItemStack stack){return entity;}
}
