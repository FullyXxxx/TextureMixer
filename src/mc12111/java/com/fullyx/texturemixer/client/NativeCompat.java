package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.function.Consumer;
import com.fullyx.texturemixer.compat.mc12111.TextRendererAccess;
import com.fullyx.texturemixer.core.DirectorySource;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.font.*;
import net.minecraft.text.Style;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.texture.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.joml.*;

/** Graphics API entry points specific to this Minecraft render pipeline. */
public final class NativeCompat {
    private NativeCompat(){}
    public static RenderLayer blockPreviewLayer(BlockState state,Identifier atlas){
        return BlockRenderLayers.getBlockLayer(state)==BlockRenderLayer.TRANSLUCENT
                ?translucentItemLayer(atlas):RenderLayers.entityCutoutNoCull(atlas);
    }
    public static RenderLayer translucentItemLayer(Identifier texture){return RenderLayers.itemEntityTranslucentCull(texture);}
    public static RenderLayer armorCutout(Identifier texture){return RenderLayers.armorCutoutNoCull(texture);}
    public static RenderLayer textLayer(Identifier texture){return RenderLayers.text(texture);}
    public static RenderLayer armorGlint(RenderLayer original,Identifier texture){
        var setup=RenderSetup.builder(original.getRenderPipeline()).texture("Sampler0",texture)
                .textureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        return RenderLayer.of("texturemixer/armor_glint_preview",setup.build());
    }
    public static Identifier armorGlintTexture(Identifier original,Identifier selected){return original;}
    public static NativeImageBackedTexture thumbnail(NativeImage image,boolean glint){
        return new NativeImageBackedTexture(()->"TextureMixer resource preview",image){
            @Override public net.minecraft.client.gl.GpuSampler getSampler(){
                return glint?super.getSampler():RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            }
        };
    }
    public static BlockRenderManager blockRenderManager(PreviewCache.Arena arena,MinecraftClient client){
        return new BlockRenderManager(arena.models.getBlockModels(),arena.modelArena.atlases,client.getBlockColors());
    }
    public static void vertices(SpecialModelRenderer<?> renderer,Consumer<Vector3fc> consumer){renderer.collectVertices(consumer);}
    public static SkyRendering skyRendering(){
        var client=MinecraftClient.getInstance();
        return new SkyRendering(client.getTextureManager(),client.getAtlasManager());
    }
    public static String skyDimension(ClientWorld world){
        return switch(world.getDimension().skybox()){
            case OVERWORLD->"overworld";case END->"end";default->"";
        };
    }
    public static GpuBufferSlice skyUniforms(Matrix4f matrix,float alpha){
        return RenderSystem.getDynamicUniforms().write(matrix,new Vector4f(1,1,1,alpha),new Vector3f(),new Matrix4f());
    }
    public static void bindSkyTextures(RenderPass pass,AbstractTexture texture,GpuTextureView destination){
        pass.bindTexture("Sampler0",texture.getGlTextureView(),texture.getSampler());
        pass.bindTexture("DestSampler",destination,texture.getSampler());
    }
    public static boolean isAnimated(SpriteContents content){return content.isAnimated();}
    public static void configureField(TextFieldWidget field){field.setInvertSelectionBackground(true);}
    public static boolean hasSeparateItemAtlas(){return true;}
    public static void configurePackFormat(){
        var pack=SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
        DirectorySource.targetFormat(pack.major(),pack.minor());
    }
    public static boolean inWorld(net.minecraft.entity.Entity entity,ClientWorld world){return entity.getEntityWorld()==world;}
    public static FontStorage fontStorage(TextureManager textures,Identifier id){return new FontStorage(new GlyphBaker(textures,id));}
    public static TextRenderer fontRenderer(FontStorage storage){
        return new TextRenderer(new TextRenderer.GlyphsProvider(){
            public GlyphProvider getGlyphs(net.minecraft.text.StyleSpriteSource source){return storage.getGlyphs(false);}
            public EffectGlyph getRectangleGlyph(){return storage.getRectangleBakedGlyph();}
        });
    }
    public static Style fontStyle(Style style,Identifier font){return style.withFont(new net.minecraft.text.StyleSpriteSource.Font(font));}
    public static TextRenderer uiRenderer(TextRenderer original,Identifier font){
        var delegate=((TextRendererAccess)original).texturemixer$fonts();
        var selected=new net.minecraft.text.StyleSpriteSource.Font(font);
        return new TextRenderer(new TextRenderer.GlyphsProvider(){
            public GlyphProvider getGlyphs(net.minecraft.text.StyleSpriteSource ignored){return delegate.getGlyphs(selected);}
            public EffectGlyph getRectangleGlyph(){return delegate.getRectangleGlyph();}
        });
    }

    public static String gameVersionName(){return SharedConstants.getGameVersion().name();}
    public static void noFieldShadow(TextFieldWidget field){field.setTextShadow(false);}
    public static void newGuiLayer(net.minecraft.client.gui.DrawContext context){context.createNewRootLayer();}
    public static void drawGuiTexture(net.minecraft.client.gui.DrawContext context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight,int color){
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,texture,x,y,u,v,width,height,regionWidth,regionHeight,textureWidth,textureHeight,color);
    }
    public static void drawGuiTexture(net.minecraft.client.gui.DrawContext context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        drawGuiTexture(context,texture,x,y,u,v,width,height,regionWidth,regionHeight,textureWidth,textureHeight,-1);
    }
    public static void geometryVertices(net.minecraft.client.render.item.ItemRenderState.LayerRenderState layer,java.util.List<BakedQuad> quads){
        layer.setVertices(()->net.minecraft.client.render.item.model.BasicItemModel.bakeQuads(quads));
    }
    public static void entityVertices(net.minecraft.client.render.item.ItemRenderState.LayerRenderState layer){
        layer.setVertices(()->new Vector3f[]{new Vector3f(),new Vector3f(1,1,1)});
    }
}
