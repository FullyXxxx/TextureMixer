package com.fullyx.texturemixer.client;

import net.minecraft.util.Identifier;

/** Resource identifier factories shared by the client adapters. */
public final class VersionedIdentifiers {
    private VersionedIdentifiers(){}
    public static Identifier of(String id){return Identifier.of(id);}
    public static Identifier of(String namespace,String path){return Identifier.of(namespace,path);}
    public static Identifier vanilla(String path){return Identifier.ofVanilla(path);}
}
