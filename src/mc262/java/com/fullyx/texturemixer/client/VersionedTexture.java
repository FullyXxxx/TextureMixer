package com.fullyx.texturemixer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class VersionedTexture {
    private VersionedTexture(){}

    public static int maxSize(){return RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();}

    public static DynamicTexture create(String name,NativeImage image){
        return new DynamicTexture(()->name,image);
    }

    public static long bytes(AbstractTexture texture){
        if(texture==null)return 0;
        var gpu=texture.getTexture();
        return gpu==null?0:(long)gpu.getWidth(0)*gpu.getHeight(0)*4;
    }
}
