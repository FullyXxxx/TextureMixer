package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** Supplies only private, empty CTM preparation; never runs Continuity's global CTM apply listener. */
public final class PreviewCompat implements PreparableReloadListener {
    @SuppressWarnings("unchecked")
    private static void put(SharedState store,String owner,String field,Object value)throws ReflectiveOperationException {
        var key=(PreparableReloadListener.StateKey<Object>)Class.forName(owner).getField(field).get(null);store.set(key,value);
    }
    @Override public void prepareSharedState(SharedState store){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("continuity"))return;
        try {
            put(store,"me.pepperbell.continuity.client.resource.CtmResourceReloader","ALL_EXTRA_IDS_FUTURE_KEY",CompletableFuture.completedFuture(Map.of()));
            put(store,"me.pepperbell.continuity.client.resource.ModelWrappingHandler","WRAP_CTM_FUTURE_KEY",CompletableFuture.completedFuture(false));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(tr("Continuity 预览适配不兼容；请检查版本"),error);}
    }
    @Override public CompletableFuture<Void> reload(SharedState store,Executor prepare,PreparationBarrier barrier,Executor apply){return barrier.wait(null);}
}
