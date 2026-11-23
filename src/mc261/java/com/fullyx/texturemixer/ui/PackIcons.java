package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.NativeCompat;

import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.client.VersionedTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Small pack.png thumbnails, scoped to one management page. */
final class PackIcons implements AutoCloseable {
    private static final long MAX_ICON_BYTES=4L*1024*1024;
    private static final class Entry { Identifier texture; }
    private final MixerService service;
    private final Map<String,Entry> icons=new HashMap<>();
    private boolean closed;
    PackIcons(MixerService service){this.service=service;}

    void draw(GuiGraphicsExtractor context,String source,int x,int y,int size){
        Entry entry=icons.computeIfAbsent(source,this::load);
        UiDraw.panel(context,x,y,size,size,0xff303030);
        if(entry.texture!=null)com.fullyx.texturemixer.client.NativeCompat.drawGuiTexture(context,entry.texture,x,y,0,0,size,size,64,64,64,64);
        else com.fullyx.texturemixer.client.NativeCompat.drawGuiTexture(context,Identifier.withDefaultNamespace("textures/misc/unknown_pack.png"),x,y,0,0,size,size,32,32,32,32);
    }
    private Entry load(String id){
        Entry entry=new Entry();
        var source=service.sources.get(id);
        if(source==null&&!id.equals(com.fullyx.texturemixer.core.MixerConfig.VANILLA))return entry;
        CompletableFuture.supplyAsync(()->{
            try{
                if(source!=null)return thumbnail(readIcon(source.root()));
                var icon=service.client.getVanillaPackResources().getRootResource("pack.png");
                if(icon==null)return null;
                try(var input=icon.get()){return thumbnail(input.readNBytes((int)MAX_ICON_BYTES+1));}
            }catch(Exception ignored){return null;}
        },service.workers).thenAcceptAsync(bytes->{
            if(closed||bytes==null)return;
            try {
                Identifier texture=Identifier.fromNamespaceAndPath("texturemixer","pack_icon/"+UUID.randomUUID().toString().replace("-",""));
                service.client.getTextureManager().register(texture,VersionedTexture.create("TextureMixer pack icon",NativeImage.read(bytes)));
                entry.texture=texture;
            }catch(IOException ignored){ }
        },service.client);
        return entry;
    }
    private static byte[] readIcon(Path root)throws IOException {
        if(Files.isDirectory(root)){
            Path icon=root.resolve("pack.png");
            if(!Files.isRegularFile(icon)||!icon.toRealPath().startsWith(root)||Files.size(icon)>MAX_ICON_BYTES)return null;
            return Files.readAllBytes(icon);
        }
        try(ZipFile zip=new ZipFile(root.toFile())){
            String icon="pack.png";
            if(zip.getEntry(icon)==null){
                Set<String> prefixes=new HashSet<>();
                var entries=zip.entries();
                while(entries.hasMoreElements()){
                    String name=entries.nextElement().getName();int assets=name.indexOf("/assets/");
                    if(assets>0)prefixes.add(name.substring(0,assets+1));
                }
                if(prefixes.size()!=1)return null;
                icon=prefixes.iterator().next()+"pack.png";
            }
            var entry=zip.getEntry(icon);
            if(entry==null||entry.getSize()<0||entry.getSize()>MAX_ICON_BYTES)return null;
            try(var input=zip.getInputStream(entry)){
                byte[] bytes=input.readNBytes((int)MAX_ICON_BYTES+1);
                return bytes.length>MAX_ICON_BYTES?null:bytes;
            }
        }
    }
    private static byte[] thumbnail(byte[] bytes)throws IOException {
        if(bytes==null)return null;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return null;
            var reader=readers.next();
            try {
                reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||(long)width*height>4L*1024*1024)return null;
                BufferedImage original=reader.read(0),scaled=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
                var graphics=scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    double factor=64.0/Math.max(width,height);int w=Math.max(1,(int)(width*factor)),h=Math.max(1,(int)(height*factor));
                    graphics.drawImage(original,(64-w)/2,(64-h)/2,w,h,null);
                }finally{graphics.dispose();}
                var output=new ByteArrayOutputStream();ImageIO.write(scaled,"PNG",output);return output.toByteArray();
            }finally{reader.dispose();}
        }
    }
    @Override public void close(){
        closed=true;
        for(Entry entry:icons.values())if(entry.texture!=null)service.client.getTextureManager().release(entry.texture);
        icons.clear();
    }
}
