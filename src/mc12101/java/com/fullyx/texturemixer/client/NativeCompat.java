package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.TextRendererAccess;
import com.fullyx.texturemixer.core.DirectorySource;
import com.fullyx.texturemixer.core.PackSource;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.font.*;
import net.minecraft.text.Style;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.joml.*;

/** Graphics API entry points specific to this Minecraft render pipeline. */
public final class NativeCompat {
    private NativeCompat(){}
    public static RenderLayer blockPreviewLayer(BlockState state,Identifier atlas){
        return RenderLayers.getBlockLayer(state)==RenderLayer.getTranslucent()
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
        var texture=VersionedTexture.create("TextureMixer resource preview",image);
        if(!glint)texture.setFilter(true,false);
        return texture;
    }
    public static String skyDimension(ClientWorld world){
        if(world.getRegistryKey().equals(net.minecraft.world.World.END))return "end";
        if(world.getDimension().hasSkyLight()&&!world.getDimension().hasCeiling())return "overworld";
        return "";
    }
    public static boolean isAnimated(SpriteContents content){return content.getDistinctFrameCount().limit(2).count()>1;}
    public static void configureField(TextFieldWidget field){}
    public static boolean hasSeparateItemAtlas(){return false;}
    public static void configurePackFormat(){
        DirectorySource.targetFormat(SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES),0);
        PackSource.preserveLegacyModelOverrides();
    }
    public static boolean inWorld(net.minecraft.entity.Entity entity,ClientWorld world){return entity.getWorld()==world;}
    public static FontStorage fontStorage(TextureManager textures,Identifier id){return new FontStorage(textures,id);}
    public static TextRenderer fontRenderer(FontStorage storage){return new TextRenderer(id->storage,false);}
    public static Style fontStyle(Style style,Identifier font){return style.withFont(font);}
    public static TextRenderer uiRenderer(TextRenderer original,Identifier font){
        var storage=((TextRendererAccess)original).texturemixer$fonts();
        return new TextRenderer(id->storage.apply(font),false);
    }

    public static String gameVersionName(){return SharedConstants.getGameVersion().getName();}
    public static void noFieldShadow(TextFieldWidget field){}
    public static void newGuiLayer(net.minecraft.client.gui.DrawContext context){context.draw();}
    public static void drawGuiTexture(net.minecraft.client.gui.DrawContext context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight,int color){
        float[] previous=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        context.setShaderColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f);
        try{context.drawTexture(texture,x,y,width,height,u,v,regionWidth,regionHeight,textureWidth,textureHeight);}
        finally{context.setShaderColor(previous[0],previous[1],previous[2],previous[3]);}
    }
    public static void drawGuiTexture(net.minecraft.client.gui.DrawContext context,Identifier texture,int x,int y,float u,float v,
            int width,int height,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        drawGuiTexture(context,texture,x,y,u,v,width,height,regionWidth,regionHeight,textureWidth,textureHeight,-1);
    }
}
