package com.fullyx.texturemixer.client;

import net.minecraft.client.texture.NativeImage;

public final class ImagePixels {
    private ImagePixels(){}
    public static int get(NativeImage image,int x,int y){return image.getColorArgb(x,y);}
    public static void set(NativeImage image,int x,int y,int argb){image.setColorArgb(x,y,argb);}
}
