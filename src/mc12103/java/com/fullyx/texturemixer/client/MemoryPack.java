package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.BlockEntry;
import net.minecraft.resource.metadata.ResourceFilter;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public final class MemoryPack implements ResourcePack {
    private final Assets assets;
    private final ResourcePackInfo info;
    private final Map<String,Set<AssetId>> index=new TreeMap<>();
    private final ResourceFilter fontFilter;
    public MemoryPack(String id,Assets assets){
        this.assets=assets;this.info=new ResourcePackInfo(id,Text.literal("TextureMixer"),ResourcePackSource.BUILTIN,Optional.empty());
        for(AssetId asset:assets.ids())index.computeIfAbsent(asset.namespace(),key->new TreeSet<>()).add(asset);
        List<BlockEntry> filters=new ArrayList<>();
        if(id.startsWith("texturemixer/")&&!id.equals("texturemixer/ui-font")) {
            for(String target:FontResources.TARGETS.keySet()) {
                AssetId font=AssetId.of(target);
                if(!assets.contains(FontResources.marker(font)))continue;
                JsonObject rule=new JsonObject();
                rule.addProperty("namespace",font.namespace());
                rule.addProperty("path",Pattern.quote(font.path()));
                filters.add(BlockEntry.CODEC.parse(JsonOps.INSTANCE,rule).getOrThrow());
            }
        }
        fontFilter=filters.isEmpty()?null:new ResourceFilter(filters);
    }
    public InputSupplier<InputStream> openRoot(String... segments){return null;}
    public InputSupplier<InputStream> open(ResourceType type,Identifier id){
        if(type!=ResourceType.CLIENT_RESOURCES)return null;
        AssetId asset=new AssetId(id.getNamespace(),id.getPath());
        return assets.contains(asset)?()->new ByteArrayInputStream(Objects.requireNonNull(assets.read(asset))):null;
    }
    public void findResources(ResourceType type,String namespace,String prefix,ResultConsumer consumer){
        if(type!=ResourceType.CLIENT_RESOURCES)return;
        String directory=prefix.isEmpty()||prefix.endsWith("/")?prefix:prefix+"/";
        for(AssetId asset:index.getOrDefault(namespace,Set.of()))if(asset.path().startsWith(directory))
            consumer.accept(VersionedIdentifiers.of(asset.namespace(),asset.path()),()->new ByteArrayInputStream(Objects.requireNonNull(assets.read(asset))));
    }
    public Set<String> getNamespaces(ResourceType type){return type==ResourceType.CLIENT_RESOURCES?index.keySet():Set.of();}
    @SuppressWarnings("unchecked")
    public <T>T parseMetadata(ResourceMetadataReader<T> serializer){
        return serializer==ResourceFilter.SERIALIZER?(T)fontFilter:null;
    }
    public ResourcePackInfo getInfo(){return info;}
    public void close(){}
}
