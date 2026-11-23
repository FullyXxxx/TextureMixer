package com.fullyx.texturemixer.sky;
import com.fullyx.texturemixer.core.AssetId;
import com.fullyx.texturemixer.compat.mc12111.CloudRendererAccess;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.client.texture.NativeImage;
import java.util.*;
import java.io.*;
public final class CloudBindings {
    private static final Map<SkyScene,CloudRenderer.CloudCells> CACHE=new WeakHashMap<>();
    public static CloudRenderer.CloudCells cells(SkyScene scene){return CACHE.computeIfAbsent(scene,key->{
        byte[] bytes=key.textures().get(AssetId.of("textures/environment/clouds.png"));if(bytes==null)return null;
        try(NativeImage image=NativeImage.read(bytes)){
            int w=image.getWidth(),h=image.getHeight();long[] cells=new long[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int color=image.getColorArgb(x,y);if((color>>>24)<10)continue;
                boolean n=(image.getColorArgb(x,Math.floorMod(y-1,h))>>>24)<10;
                boolean e=(image.getColorArgb(Math.floorMod(x+1,w),y)>>>24)<10;
                boolean s=(image.getColorArgb(x,Math.floorMod(y+1,h))>>>24)<10;
                boolean west=(image.getColorArgb(Math.floorMod(x-1,w),y)>>>24)<10;
                cells[y*w+x]=CloudRendererAccess.texturemixer$pack(color,n,e,s,west);
            }return new CloudRenderer.CloudCells(cells,w,h);
        }catch(IOException error){throw new IllegalArgumentException("Invalid sky clouds",error);}
    });}
}
