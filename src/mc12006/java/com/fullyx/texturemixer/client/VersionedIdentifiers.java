package com.fullyx.texturemixer.client;

import net.minecraft.util.Identifier;

public final class VersionedIdentifiers {
    private VersionedIdentifiers(){}
    public static Identifier of(String id){return new Identifier(id);}
    public static Identifier of(String namespace,String path){return new Identifier(namespace,path);}
    public static Identifier vanilla(String path){return new Identifier("minecraft",path);}
}
