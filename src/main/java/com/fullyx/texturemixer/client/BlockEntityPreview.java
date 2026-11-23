package com.fullyx.texturemixer.client;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.*;
import java.util.function.Consumer;

/** Local, unregistered block entities and local renderer models. Never attaches a preview to the live world. */
public final class BlockEntityPreview implements SpecialModelRenderer<BlockEntityRenderState> {
    private final PreviewCache.Arena arena;
    private final BlockEntityRenderManager renderers;
    private final BlockEntityRenderState state;
    private final CameraRenderState camera=new CameraRenderState();
    @SuppressWarnings({"rawtypes","unchecked"})
    public static BlockEntityPreview create(PreviewCache.Arena arena,BlockState block,ResourceManager resources){
        if(!(block.getBlock() instanceof BlockEntityProvider provider))return null;
        BlockEntity entity=provider.createBlockEntity(BlockPos.ORIGIN,block);if(entity==null)return null;
        var client=MinecraftClient.getInstance();
        var blocks=NativeCompat.blockRenderManager(arena,client);
        var renderers=new BlockEntityRenderManager(UiFonts.renderer(),arena.models.getEntityModelsSupplier(),blocks,arena.items,client.getItemRenderer(),client.getEntityRenderDispatcher(),
                id->arena.modelArena.sprite(new SpriteIdentifier(id.getAtlasId(),VersionedIdentifiers.of(arena.prefix+id.getTextureId().getNamespace(),id.getTextureId().getPath()))),client.getPlayerSkinCache());
        renderers.reload(resources);
        BlockEntityRenderer renderer=renderers.get(entity);if(renderer==null)return null;
        BlockEntityRenderState state=renderer.createRenderState();
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderer.updateRenderState(entity,state,0,Vec3d.ZERO,null);return null;}));
        state.lightmapCoordinates=LightmapTextureManager.MAX_LIGHT_COORDINATE;
        return new BlockEntityPreview(arena,renderers,state);
    }
    private BlockEntityPreview(PreviewCache.Arena arena,BlockEntityRenderManager renderers,BlockEntityRenderState state){
        this.arena=arena;this.renderers=renderers;this.state=state;camera.pos=Vec3d.ZERO;camera.blockPos=BlockPos.ORIGIN;camera.entityPos=Vec3d.ZERO;camera.orientation=new Quaternionf();camera.initialized=true;
    }
    public void addLayer(ItemDisplayState target){
        target.addModelKey(arena.token+"|block-entity|"+state.blockState);var layer=target.newLayer();
        layer.setUseLight(true);layer.setTransform(new Transformation(new Vector3f(30,225,0),new Vector3f(),new Vector3f(.625f)));
        NativeCompat.entityVertices(layer);layer.setSpecialModel(this,state);
    }
    public void render(BlockEntityRenderState data,ItemDisplayContext display,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,int overlay,boolean glint,int color){
        RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{renderers.render(data,matrices,queue,camera);return null;}));
    }
    public void collectVertices(Consumer<Vector3fc> consumer){consumer.accept(new Vector3f());consumer.accept(new Vector3f(1,1,1));}
    public void collectVertices(java.util.Set<Vector3f> vertices){vertices.add(new Vector3f());vertices.add(new Vector3f(1,1,1));}
    public BlockEntityRenderState getData(ItemStack stack){return state;}
}
