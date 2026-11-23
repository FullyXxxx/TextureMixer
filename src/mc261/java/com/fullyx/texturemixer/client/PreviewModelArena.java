package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.compat.mc12111.TextureManagerAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;

public final class PreviewModelArena implements AutoCloseable {
    public final ModelManager models;
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
        models=new ModelManager(service.client.getBlockColors(),atlases,service.client.playerSkinRenderCache());
    }

    CompletableFuture<?> load(Executor prepare,Executor apply){
        return RenderScope.arena(token,()->SimpleReloadInstance.of(resources,List.of(new PreviewCompat(),atlases,models),prepare,apply,CompletableFuture.completedFuture(Unit.INSTANCE)).done())
                .thenRunAsync(()->eachAtlas((id,texture)->{
                    ((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures().put(texture.location(),texture);
                    aliases.add(texture.location());
                }),service.client);
    }

    public TextureAtlasSprite sprite(SpriteId id){return atlases.get(id);}
    public void eachAtlas(BiConsumer<Identifier,TextureAtlas> consumer){atlases.forEach(consumer);}
    void tick(){textures.tick();}

    @Override public void close(){
        var global=((TextureManagerAccess)service.client.getTextureManager()).texturemixer$textures();
        for(var id:aliases)global.remove(id);
        atlases.close();
        textures.close();
    }
}
