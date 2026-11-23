package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.TextureManagerAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

public final class PreviewModelArena implements AutoCloseable {
    private static final java.util.Map<String,PreviewModelArena> ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
    public final BakedModelManager models;
    private final MixerService service;
    private final ResourceManager resources;
    private final String token;
    private final TextureManager textures;

    private final List<Identifier> aliases=new ArrayList<>();

    PreviewModelArena(MixerService service,ResourceManager resources,String token){
        this.service=service;
        this.resources=resources;
        this.token=token;
        textures=new TextureManager(resources);
        models=RenderScope.arena(token,()->new BakedModelManager(textures,service.client.getBlockColors(),0));
    }

    CompletableFuture<?> load(Executor prepare,Executor apply){
        return RenderScope.arena(token,()->SimpleResourceReload.create(resources,List.of(models),prepare,apply,CompletableFuture.completedFuture(Unit.INSTANCE)).whenComplete())
                .thenRunAsync(()->{
                    eachAtlas((id,texture)->{
                        ((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures().put(texture.getId(),texture);
                        aliases.add(texture.getId());
                    });
                    ACTIVE.put(token,this);
                },service.client);
    }

    public static Sprite activeSprite(SpriteIdentifier id){
        String token=RenderScope.currentArena();
        if(token==null)return null;
        var arena=ACTIVE.get(token);
        return arena==null?null:arena.sprite(id);
    }

    public Sprite sprite(SpriteIdentifier id){return models.getAtlas(id.getAtlasId()).getSprite(id.getTextureId());}
    public void eachAtlas(BiConsumer<Identifier,SpriteAtlasTexture> consumer){((TextureManagerAccess)textures).texturemixer$textures().forEach((id,texture)->{
        if(texture instanceof SpriteAtlasTexture atlas)consumer.accept(id,atlas);
    });}
    void tick(){textures.tick();}

    @Override public void close(){
        ACTIVE.remove(token,this);
        var global=((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures();
        for(var id:aliases)global.remove(id);
        models.close();
        textures.close();
    }
}
