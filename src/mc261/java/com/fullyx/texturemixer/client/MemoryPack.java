package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import net.minecraft.util.IdentifierPattern;

public final class MemoryPack implements PackResources {
    private final Assets assets;
    private final PackLocationInfo info;
    private final Map<String,Set<AssetId>> index=new TreeMap<>();
    private final ResourceFilterSection fontFilter;
    public MemoryPack(String id,Assets assets){
        this.assets=assets;this.info=new PackLocationInfo(id,Component.literal("TextureMixer"),PackSource.BUILT_IN,Optional.empty());
        for(AssetId asset:assets.ids())index.computeIfAbsent(asset.namespace(),key->new TreeSet<>()).add(asset);
        List<IdentifierPattern> filters=new ArrayList<>();
        if(id.startsWith("texturemixer/")&&!id.equals("texturemixer/ui-font")) {
            for(String target:FontResources.TARGETS.keySet()) {
                AssetId font=AssetId.of(target);
                if(!assets.contains(FontResources.marker(font)))continue;
                JsonObject rule=new JsonObject();
                rule.addProperty("namespace",font.namespace());
                rule.addProperty("path",Pattern.quote(font.path()));
                filters.add(IdentifierPattern.CODEC.parse(JsonOps.INSTANCE,rule).getOrThrow());
            }
        }
        fontFilter=filters.isEmpty()?null:new ResourceFilterSection(filters);
    }
    public IoSupplier<InputStream> getRootResource(String... segments){return null;}
    public IoSupplier<InputStream> getResource(PackType type,Identifier id){
        if(type!=PackType.CLIENT_RESOURCES)return null;
        AssetId asset=new AssetId(id.getNamespace(),id.getPath());
        return assets.contains(asset)?()->new ByteArrayInputStream(Objects.requireNonNull(assets.read(asset))):null;
    }
    public void listResources(PackType type,String namespace,String prefix,ResourceOutput consumer){
        if(type!=PackType.CLIENT_RESOURCES)return;
        String directory=prefix.isEmpty()||prefix.endsWith("/")?prefix:prefix+"/";
        for(AssetId asset:index.getOrDefault(namespace,Set.of()))if(asset.path().startsWith(directory))
            consumer.accept(Identifier.fromNamespaceAndPath(asset.namespace(),asset.path()),()->new ByteArrayInputStream(Objects.requireNonNull(assets.read(asset))));
    }
    public Set<String> getNamespaces(PackType type){return type==PackType.CLIENT_RESOURCES?index.keySet():Set.of();}
    @SuppressWarnings("unchecked")
    public <T>T getMetadataSection(MetadataSectionType<T> serializer){
        return serializer==ResourceFilterSection.TYPE?(T)fontFilter:null;
    }
    public PackLocationInfo location(){return info;}
    public void close(){}
}
