package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.function.Consumer;
import com.fullyx.texturemixer.compat.mc12111.TextRendererAccess;
import com.fullyx.texturemixer.core.DirectorySource;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.*;

/** Graphics API entry points specific to this Minecraft render pipeline. */
public final class NativeCompat {
    private NativeCompat(){}

    public static net.minecraft.client.gui.screens.Screen screen(Minecraft client) {
        return client.gui.screen();
    }
    public static void setScreen(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        client.gui.setScreen(screen);
    }
    public static net.minecraft.client.gui.screens.Overlay overlay(Minecraft client) {
        return client.gui.overlay();
    }
    public static long windowOwner(Minecraft client) {
        if (!com.fullyx.texturemixer.ui.PackFileDialog.windows()) return 0;
        return org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(client.getWindow().handle());
    }
    public static void openPackFolder(Minecraft client) {
        net.minecraft.util.Util.getPlatform().openPath(client.getResourcePackDirectory());
    }

    public static RenderType blockPreviewLayer(BlockState state,Identifier atlas){
        return RenderTypes.itemCutout(atlas);
    }
    public static RenderType translucentItemLayer(Identifier texture){return RenderTypes.itemTranslucent(texture);}
    public static RenderType armorCutout(Identifier texture){return RenderTypes.armorCutoutNoCull(texture);}
    public static RenderType textLayer(Identifier texture){return RenderTypes.text(texture);}
    public static RenderType armorGlint(RenderType original,Identifier texture){
        var setup=RenderSetup.builder(original.pipeline()).withTexture("Sampler0",texture)
                .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        return com.fullyx.texturemixer.compat.mc12111.RenderTypeAccess.texturemixer$create("texturemixer/armor_glint_preview",setup.createRenderSetup());
    }
    public static Identifier armorGlintTexture(Identifier original,Identifier selected){return original;}
    public static DynamicTexture thumbnail(NativeImage image,boolean glint){
        return new DynamicTexture(()->"TextureMixer resource preview",image){
            @Override public com.mojang.blaze3d.textures.GpuSampler getSampler(){
                return glint?super.getSampler():RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        };
    }
    public static BlockModelResolver blockRenderManager(PreviewCache.Arena arena,Minecraft client){
        return new BlockModelResolver(arena.models);
    }
    public static void vertices(SpecialModelRenderer<?> renderer,Consumer<Vector3fc> consumer){renderer.getExtents(consumer);}
    public static SkyRenderer skyRendering(){
        var client=Minecraft.getInstance();
        return new SkyRenderer(client.getTextureManager(),client.getAtlasManager(),client.gameRenderer.mainRenderTarget());
    }
    public static String skyDimension(ClientLevel world){
        return switch(world.dimensionType().skybox()){
            case OVERWORLD->"overworld";case END->"end";default->"";
        };
    }
    public static GpuBufferSlice skyUniforms(Matrix4f matrix,float alpha){
        return RenderSystem.getDynamicUniforms().writeTransform(matrix,new Vector4f(1,1,1,alpha),new Vector3f(),new Matrix4f());
    }
    public static void bindSkyTextures(RenderPass pass,AbstractTexture texture,GpuTextureView destination){
        pass.bindTexture("Sampler0",texture.getTextureView(),texture.getSampler());
        pass.bindTexture("DestSampler",destination,texture.getSampler());
    }
    public static boolean isAnimated(SpriteContents content){return content.isAnimated();}
    public static void configureField(EditBox field){field.setInvertHighlightedTextColor(true);}
    public static boolean hasSeparateItemAtlas(){return true;}
    public static void configurePackFormat(){
        var pack=SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        DirectorySource.targetFormat(pack.major(),pack.minor());
    }
    public static boolean inWorld(net.minecraft.world.entity.Entity entity,ClientLevel world){return entity.level()==world;}
    public static FontSet fontStorage(TextureManager textures,Identifier id){return new FontSet(new GlyphStitcher(textures,id));}
    public static Font fontRenderer(FontSet storage){
        return new Font(new Font.Provider(){
            public GlyphSource glyphs(net.minecraft.network.chat.FontDescription source){return storage.source(false);}
            public EffectGlyph effect(){return storage.whiteGlyph();}
        });
    }
    public static Style fontStyle(Style style,Identifier font){return style.withFont(new net.minecraft.network.chat.FontDescription.Resource(font));}
    public static Font uiRenderer(Font original,Identifier font){
        var delegate=((TextRendererAccess)original).texturemixer$fonts();
        var selected=new net.minecraft.network.chat.FontDescription.Resource(font);
        return new Font(new Font.Provider(){
            public GlyphSource glyphs(net.minecraft.network.chat.FontDescription ignored){return delegate.glyphs(selected);}
            public EffectGlyph effect(){return delegate.effect();}
        });
    }

    public static String gameVersionName(){return SharedConstants.getCurrentVersion().name();}
    public static void noFieldShadow(EditBox field){field.setTextShadow(false);}
    public static void newGuiLayer(net.minecraft.client.gui.GuiGraphicsExtractor context){context.nextStratum();}
    public static void drawGuiTexture(net.minecraft.client.gui.GuiGraphicsExtractor context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight,int color){
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,x,y,u,v,width,height,regionWidth,regionHeight,textureWidth,textureHeight,color);
    }
    public static void drawGuiTexture(net.minecraft.client.gui.GuiGraphicsExtractor context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        drawGuiTexture(context,texture,x,y,u,v,width,height,regionWidth,regionHeight,textureWidth,textureHeight,-1);
    }
    public static void geometryVertices(net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer,java.util.List<BakedQuad> quads){
        layer.setExtents(()->quads.stream().flatMap(quad->java.util.stream.IntStream.range(0,4).mapToObj(quad::position)).toArray(Vector3fc[]::new));
    }
    public static void entityVertices(net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer){
        layer.setExtents(()->new Vector3f[]{new Vector3f(),new Vector3f(1,1,1)});
    }
}
