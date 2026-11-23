package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaAssets implements Assets {
    private final DefaultResourcePack pack;
    private final Set<AssetId> ids;
    private final Map<AssetId,byte[]> cache=new ConcurrentHashMap<>();
    public VanillaAssets(DefaultResourcePack pack) {
        this.pack=pack;Set<AssetId> found=new TreeSet<>();
        for(String namespace:pack.getNamespaces(ResourceType.CLIENT_RESOURCES))
            for(String directory:List.of("atlases","blockstates","equipment","font","items","lang","models","particles","post_effect","shaders","sounds","texts","textures","waypoint_style"))
                pack.findResources(ResourceType.CLIENT_RESOURCES,namespace,directory,(id,supplier)->found.add(new AssetId(id.getNamespace(),id.getPath())));
        for(String namespace:pack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
            if(pack.open(ResourceType.CLIENT_RESOURCES,VersionedIdentifiers.of(namespace,"sounds.json"))!=null)
                found.add(new AssetId(namespace,"sounds.json"));
        }
        ids=Collections.unmodifiableSet(found);
    }
    public byte[] read(AssetId id) throws IOException {
        byte[] bytes=cache.get(id);if(bytes!=null)return bytes;
        var input=pack.open(ResourceType.CLIENT_RESOURCES,VersionedIdentifiers.of(id.namespace(),id.path()));
        if(input==null)return null;
        try(var stream=input.get()){bytes=stream.readAllBytes();if(!id.path().endsWith(".ogg"))cache.put(id,bytes);return bytes;}
    }
    public Set<AssetId> ids(){return ids;}
}
