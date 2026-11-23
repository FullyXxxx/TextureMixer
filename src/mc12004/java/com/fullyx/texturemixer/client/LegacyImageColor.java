package com.fullyx.texturemixer.client;

import net.minecraft.client.texture.NativeImage;

public final class LegacyImageColor {
    private LegacyImageColor(){}

    private static int exchangeRedAndBlue(int color){
        return (color&0xff00ff00)|((color&0xff)<<16)|((color>>>16)&0xff);
    }

    public static int get(NativeImage image,int x,int y){
        return exchangeRedAndBlue(image.getColor(x,y));
    }

    public static void set(NativeImage image,int x,int y,int argb){
        image.setColor(x,y,exchangeRedAndBlue(argb));
    }
}
