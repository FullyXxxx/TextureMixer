package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

public final class VersionedTexture {
    private VersionedTexture(){}

    public static int maxSize(){return RenderSystem.getDevice().getMaxTextureSize();}

    public static NativeImageBackedTexture create(String name,NativeImage image){
        return new NativeImageBackedTexture(()->name,image);
    }

    public static long bytes(AbstractTexture texture){
        if(texture==null)return 0;
        var gpu=texture.getGlTexture();
        return gpu==null?0:(long)gpu.getWidth(0)*gpu.getHeight(0)*4;
    }
}
