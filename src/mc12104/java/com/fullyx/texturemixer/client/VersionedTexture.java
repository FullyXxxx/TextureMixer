package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.fullyx.texturemixer.compat.mc12111.AtlasDimensions;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

public final class VersionedTexture {
    private VersionedTexture(){}

    public static int maxSize(){return RenderSystem.maxSupportedTextureSize();}

    public static NativeImageBackedTexture create(String name,NativeImage image){
        return new NativeImageBackedTexture(image);
    }

    public static long bytes(AbstractTexture texture){
        if(texture instanceof SpriteAtlasTexture){
            var atlas=(AtlasDimensions)texture;
            return (long)atlas.texturemixer$width()*atlas.texturemixer$height()*4;
        }
        if(texture instanceof NativeImageBackedTexture nativeTexture){
            var image=nativeTexture.getImage();
            if(image!=null)return (long)image.getWidth()*image.getHeight()*4;
        }
        return 0;
    }
}
