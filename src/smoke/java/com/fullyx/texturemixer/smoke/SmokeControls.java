package com.fullyx.texturemixer.smoke;
import com.fullyx.texturemixer.client.MemoryPack;
import com.fullyx.texturemixer.core.Assets;
import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;
public final class SmokeControls {
    public static volatile int reloads;
    public static volatile net.minecraft.client.texture.Sprite fireSprite;
    public static boolean measureFrames;
    public static final List<Long> frameNanos=new ArrayList<>();
    public static volatile boolean failNextReload,serverPack,serverOnNextReload,capturedEnd;
    public static Assets serverAssets=new Assets.Memory(Map.of());
    public static ResourcePackProfile profile(){
        var info=new ResourcePackInfo("server/texturemixer-smoke",Text.literal("TextureMixer test server pack"),ResourcePackSource.SERVER,Optional.empty());
        ResourcePackProfile.PackFactory factory=new ResourcePackProfile.PackFactory(){
            public ResourcePack open(ResourcePackInfo selected){return new ServerPack(selected);}
            public ResourcePack openWithOverlays(ResourcePackInfo selected,ResourcePackProfile.Metadata metadata){return open(selected);}
        };
        return new ResourcePackProfile(info,factory,new ResourcePackProfile.Metadata(Text.literal("Test only"),ResourcePackCompatibility.COMPATIBLE,FeatureSet.empty(),List.of()),
                new ResourcePackPosition(true,ResourcePackProfile.InsertionPosition.TOP,true));
    }
    private static final class ServerPack implements ResourcePack {
        private final ResourcePackInfo info;private final MemoryPack content=new MemoryPack("server/texturemixer-smoke",serverAssets);
        ServerPack(ResourcePackInfo info){this.info=info;}
        public InputSupplier<InputStream> openRoot(String... paths){return null;}
        public InputSupplier<InputStream> open(ResourceType type,Identifier id){return content.open(type,id);}
        public void findResources(ResourceType type,String ns,String prefix,ResultConsumer consumer){content.findResources(type,ns,prefix,consumer);}
        public Set<String> getNamespaces(ResourceType type){return content.getNamespaces(type);}
        public <T>T parseMetadata(ResourceMetadataSerializer<T> serializer){return null;}
        public ResourcePackInfo getInfo(){return info;}
        public void close(){}
    }
}
