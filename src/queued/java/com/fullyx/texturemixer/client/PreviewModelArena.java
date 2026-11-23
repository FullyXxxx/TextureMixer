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
    public final BakedModelManager models;
    private final MixerService service;
    private final ResourceManager resources;
    private final String token;
    private final TextureManager textures;
    final AtlasManager atlases;
    private final List<Identifier> aliases=new ArrayList<>();

    PreviewModelArena(MixerService service,ResourceManager resources,String token){
        this.service=service;
        this.resources=resources;
        this.token=token;
        textures=new TextureManager(resources);
        atlases=RenderScope.arena(token,()->new AtlasManager(textures,0));
        models=new BakedModelManager(service.client.getBlockColors(),atlases,service.client.getPlayerSkinCache());
    }

    CompletableFuture<?> load(Executor prepare,Executor apply){
        return RenderScope.arena(token,()->SimpleResourceReload.create(resources,List.of(new PreviewCompat(),atlases,models),prepare,apply,CompletableFuture.completedFuture(Unit.INSTANCE)).whenComplete())
                .thenRunAsync(()->eachAtlas((id,texture)->{
                    ((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures().put(texture.getId(),texture);
                    aliases.add(texture.getId());
                }),service.client);
    }

    public Sprite sprite(SpriteIdentifier id){return atlases.getSprite(id);}
    public void eachAtlas(BiConsumer<Identifier,SpriteAtlasTexture> consumer){atlases.acceptAtlasTextures(consumer);}
    void tick(){textures.tick();}

    @Override public void close(){
        var global=((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures();
        for(var id:aliases)global.remove(id);
        atlases.close();
        textures.close();
    }
}
