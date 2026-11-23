package com.fullyx.texturemixer.core;

import java.io.IOException;
import java.nio.file.*;

/** One indexed resource pack, shared by automatic discovery and manual registration. */
public interface PackSource extends Assets {
    Path root();
    String name();
    boolean mainCompatible();
    long indexedBytes();
    String fingerprint();
    boolean unchanged() throws IOException;
    default Assets.Memory snapshot()throws IOException {
        java.util.Map<AssetId,byte[]> data=new java.util.TreeMap<>();
        for(AssetId id:ids())data.put(id,read(id));return new Assets.Memory(data);
    }
    static PackSource open(Path path) throws IOException {
        PackSource indexed=Files.isDirectory(path)?new DirectorySource(path):new ZipSource(path);
        return LegacyPack.adapt(indexed);
    }
    static void preserveLegacyModelOverrides(){
        LegacyPack.preserveOverrides=true;
    }
}
