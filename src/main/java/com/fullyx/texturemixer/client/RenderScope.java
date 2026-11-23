package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.AssetId;
import net.minecraft.util.Identifier;
import java.util.function.Supplier;

/** Only spans extraction/submission; render commands retain their resolved layers afterwards. */
public final class RenderScope {
    private static final ThreadLocal<String> PREFIX=new ThreadLocal<>();
    private static final ThreadLocal<String> ARENA=new ThreadLocal<>();
    public static Identifier texture(Identifier id){
        if(id.getPath().startsWith("textures/atlas/"))return atlas(id);
        String prefix=PREFIX.get();if(prefix==null||id.getNamespace().startsWith("texturemixer_"))return id;
        Identifier mapped=VersionedIdentifiers.of(prefix+id.getNamespace(),id.getPath());
        var service=TextureMixerClient.SERVICE;
        if(EquipmentBindings.hasPreviewTexture(mapped)||service!=null&&service.runtime()!=null
                &&service.runtime().assets().contains(new AssetId(mapped.getNamespace(),mapped.getPath()))){
            if(mapped.getPath().startsWith("textures/environment/"))
                com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED.trackTexture(mapped);
            return mapped;
        }
        return id;
    }
    public static Identifier atlas(Identifier id){String arena=ARENA.get();return arena==null?id:VersionedIdentifiers.of("texturemixer","preview/"+arena+"/"+id.getNamespace()+"/"+id.getPath());}
    public static String currentArena(){return ARENA.get();}
    public static String currentPrefix(){return PREFIX.get();}
    public static <T>T in(String prefix,Supplier<T> action){String old=PREFIX.get();PREFIX.set(prefix);try{return action.get();}finally{if(old==null)PREFIX.remove();else PREFIX.set(old);}}
    public static <T>T arena(String arena,Supplier<T> action){String old=ARENA.get();ARENA.set(arena);try{return action.get();}finally{if(old==null)ARENA.remove();else ARENA.set(old);}}
    public static String prefix(String namespace){return namespace.matches("texturemixer_[a-f0-9]{16}_.+")?namespace.substring(0,"texturemixer_".length()+17):null;}
    public static Identifier original(Identifier id){String p=prefix(id.getNamespace());return p==null?id:VersionedIdentifiers.of(id.getNamespace().substring(p.length()),id.getPath());}
}
