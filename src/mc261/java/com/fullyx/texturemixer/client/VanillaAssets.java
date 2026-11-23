package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;

public final class VanillaAssets implements Assets {
    private final VanillaPackResources pack;
    private final Set<AssetId> ids;
    private final Map<AssetId,byte[]> cache=new ConcurrentHashMap<>();
    public VanillaAssets(VanillaPackResources pack) {
        this.pack=pack;Set<AssetId> found=new TreeSet<>();
        for(String namespace:pack.getNamespaces(PackType.CLIENT_RESOURCES))
            for(String directory:List.of("atlases","blockstates","equipment","font","items","lang","models","particles","post_effect","shaders","sounds","texts","textures","waypoint_style"))
                pack.listResources(PackType.CLIENT_RESOURCES,namespace,directory,(id,supplier)->found.add(new AssetId(id.getNamespace(),id.getPath())));
        for(String namespace:pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
            if(pack.getResource(PackType.CLIENT_RESOURCES,Identifier.fromNamespaceAndPath(namespace,"sounds.json"))!=null)
                found.add(new AssetId(namespace,"sounds.json"));
        }
        ids=Collections.unmodifiableSet(found);
    }
    public byte[] read(AssetId id) throws IOException {
        byte[] bytes=cache.get(id);if(bytes!=null)return bytes;
        var input=pack.getResource(PackType.CLIENT_RESOURCES,Identifier.fromNamespaceAndPath(id.namespace(),id.path()));
        if(input==null)return null;
        try(var stream=input.get()){bytes=stream.readAllBytes();if(!id.path().endsWith(".ogg"))cache.put(id,bytes);return bytes;}
    }
    public Set<AssetId> ids(){return ids;}
}
