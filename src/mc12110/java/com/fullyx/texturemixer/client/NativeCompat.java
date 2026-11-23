package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.HashSet;
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
        return RenderLayers.getBlockLayer(state)==BlockRenderLayer.TRANSLUCENT
                ?translucentItemLayer(atlas):RenderLayer.getEntityCutoutNoCull(atlas);
    }
    public static RenderLayer translucentItemLayer(Identifier texture){return RenderLayer.getItemEntityTranslucentCull(texture);}
    public static RenderLayer armorCutout(Identifier texture){return RenderLayer.getArmorCutoutNoCull(texture);}
    public static RenderLayer textLayer(Identifier texture){return RenderLayer.getText(texture);}
    public static RenderLayer armorGlint(RenderLayer original,Identifier texture){return original;}
    public static Identifier armorGlintTexture(Identifier original,Identifier selected){
        return original.equals(net.minecraft.client.render.item.ItemRenderer.ENTITY_ENCHANTMENT_GLINT)?selected:original;
    }
    public static NativeImageBackedTexture thumbnail(NativeImage image,boolean glint){
        var texture=new NativeImageBackedTexture(()->"TextureMixer resource preview",image);
        if(!glint)texture.setFilter(true,false);
        return texture;
    }
    public static BlockRenderManager blockRenderManager(PreviewCache.Arena arena,MinecraftClient client){
        return new BlockRenderManager(arena.models.getBlockModels(),arena.modelArena.atlases,arena.models.getBlockEntityModelsSupplier(),client.getBlockColors());
    }
    public static void vertices(SpecialModelRenderer<?> renderer,Consumer<Vector3fc> consumer){
        var vertices=new HashSet<Vector3f>();
        renderer.collectVertices(vertices);
        vertices.forEach(consumer);
    }
    public static SkyRendering skyRendering(){return new SkyRendering();}
    public static String skyDimension(ClientWorld world){
        if(world.getRegistryKey().equals(net.minecraft.world.World.END))return "end";
        if(world.getDimension().hasSkyLight()&&!world.getDimension().hasCeiling())return "overworld";
        return "";
    }
    public static GpuBufferSlice skyUniforms(Matrix4f matrix,float alpha){
        return RenderSystem.getDynamicUniforms().write(matrix,new Vector4f(1,1,1,alpha),new Vector3f(),new Matrix4f(),1f);
    }
    public static void bindSkyTextures(RenderPass pass,AbstractTexture texture,GpuTextureView destination){
        pass.bindSampler("Sampler0",texture.getGlTextureView());
        pass.bindSampler("DestSampler",destination);
    }
    public static boolean isAnimated(SpriteContents content){return content.isAnimated();}
    public static void configureField(TextFieldWidget field){}
    public static boolean hasSeparateItemAtlas(){return false;}
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
