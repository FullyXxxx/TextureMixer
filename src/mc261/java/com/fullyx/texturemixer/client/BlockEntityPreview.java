package com.fullyx.texturemixer.client;

import org.joml.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Local, unregistered block entities and local renderer models. Never attaches a preview to the live world. */
public final class BlockEntityPreview implements SpecialModelRenderer<BlockEntityRenderState> {
    private final PreviewCache.Arena arena;
    private final BlockEntityRenderDispatcher renderers;
    private final BlockEntityRenderState state;
    private final CameraRenderState camera=new CameraRenderState();
    @SuppressWarnings({"rawtypes","unchecked"})
    public static BlockEntityPreview create(PreviewCache.Arena arena,BlockState block,ResourceManager resources){
        if(!(block.getBlock() instanceof EntityBlock provider))return null;
        BlockEntity entity=provider.newBlockEntity(BlockPos.ZERO,block);if(entity==null)return null;
        var client=Minecraft.getInstance();
        var blocks=NativeCompat.blockRenderManager(arena,client);
        var renderers=new BlockEntityRenderDispatcher(UiFonts.renderer(),arena.models.entityModels(),blocks,arena.items,client.getEntityRenderDispatcher(),
                id->arena.modelArena.sprite(new SpriteId(id.atlasLocation(),Identifier.fromNamespaceAndPath(arena.prefix+id.texture().getNamespace(),id.texture().getPath()))),client.playerSkinRenderCache());
        renderers.onResourceManagerReload(resources);
        BlockEntityRenderer renderer=renderers.getRenderer(entity);if(renderer==null)return null;
        BlockEntityRenderState state=renderer.createRenderState();
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderer.extractRenderState(entity,state,0,Vec3.ZERO,null);return null;}));
        state.lightCoords=LightCoordsUtil.FULL_BRIGHT;
        return new BlockEntityPreview(arena,renderers,state);
    }
    private BlockEntityPreview(PreviewCache.Arena arena,BlockEntityRenderDispatcher renderers,BlockEntityRenderState state){
        this.arena=arena;this.renderers=renderers;this.state=state;camera.pos=Vec3.ZERO;camera.blockPos=BlockPos.ZERO;camera.orientation=new Quaternionf();camera.initialized=true;
    }
    public void addLayer(ItemDisplayState target){
        target.appendModelIdentityElement(arena.token+"|block-entity|"+((com.fullyx.texturemixer.compat.mc12111.BlockEntityStateAccess)state).texturemixer$blockState());var layer=target.newLayer();
        layer.setUsesBlockLight(true);layer.setItemTransform(new ItemTransform(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f)));
        NativeCompat.entityVertices(layer);layer.setupSpecialModel(this,state);
    }
    public void submit(BlockEntityRenderState data,PoseStack matrices,SubmitNodeCollector queue,int light,int overlay,boolean glint,int color){
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderers.submit(data,matrices,queue,camera);return null;}));
    }
    public void getExtents(Consumer<Vector3fc> consumer){consumer.accept(new Vector3f());consumer.accept(new Vector3f(1,1,1));}
    public void collectVertices(java.util.Set<Vector3f> vertices){vertices.add(new Vector3f());vertices.add(new Vector3f(1,1,1));}
    public BlockEntityRenderState extractArgument(ItemStack stack){return state;}
}
