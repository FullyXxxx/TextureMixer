package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import net.minecraft.resource.ResourceReloader;
import java.util.*;
import java.util.concurrent.*;

/** Supplies only private, empty CTM preparation; never runs Continuity's global CTM apply listener. */
public final class PreviewCompat implements ResourceReloader {
    @SuppressWarnings("unchecked")
    private static void put(Store store,String owner,String field,Object value)throws ReflectiveOperationException {
        var key=(ResourceReloader.Key<Object>)Class.forName(owner).getField(field).get(null);store.put(key,value);
    }
    @Override public void prepareSharedState(Store store){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("continuity"))return;
        try {
            put(store,"me.pepperbell.continuity.client.resource.CtmResourceReloader","ALL_EXTRA_IDS_FUTURE_KEY",CompletableFuture.completedFuture(Map.of()));
            put(store,"me.pepperbell.continuity.client.resource.ModelWrappingHandler","WRAP_CTM_FUTURE_KEY",CompletableFuture.completedFuture(false));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(tr("Continuity 预览适配不兼容；请检查版本"),error);}
    }
    @Override public CompletableFuture<Void> reload(Store store,Executor prepare,Synchronizer barrier,Executor apply){return barrier.whenPrepared(null);}
}
