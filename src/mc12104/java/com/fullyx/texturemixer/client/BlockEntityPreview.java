package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.ItemDisplayModeAccess;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.math.*;

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
        ((ItemDisplayModeAccess)target).texturemixer$display(ModelTransformationMode.GUI);
        var model=arena.models.getBlockModels().getModel(entity.getCachedState());
        target.newLayer().setSpecialModel(this,entity,PreviewItemModels.forGui(model,java.util.List.of()));
    }
    public void render(BlockEntity data,ModelTransformationMode display,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay,boolean glint){
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderer.render(data,0,matrices,vertices,light,overlay);return null;}));
    }
    public BlockEntity getData(ItemStack stack){return entity;}
}
