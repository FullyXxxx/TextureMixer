package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

public final class MixerService implements AutoCloseable {
    public final ExecutorService workers=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"TextureMixer-worker");t.setDaemon(true);return t;});
    public final Minecraft client;
    public final Path cache;
    public final ConfigStore store;
    public final MixerLibrary library;
    public final Map<String,PackSource> sources=new ConcurrentHashMap<>();
    public final Map<String,String> sourceErrors=new ConcurrentHashMap<>();
    public volatile VanillaAssets vanilla;
    public volatile Catalog catalog;
    public volatile MixerConfig committed;
    public volatile Generation mounted;
    public volatile boolean busy,serverPresent;
    /** Kept for callers of alpha.2; server packs now remain below the personal mix. */
    @Deprecated public volatile boolean serverSuspended;
    public volatile String status=tr("正在准备原版资源…");
    public PreviewCache previews;
    public final ResourcePreviewCache resourcePreviews;
    public final com.fullyx.texturemixer.sky.SkyPreviewCache skyPreviews;
    private CompletableFuture<Void> initialization;
    private CompletableFuture<Void> pendingReload;
    private Throwable nativeReloadFailure;
    private boolean nativeRecoverySettled;
    private boolean nativeRecoveryOwnsBusy;
    private CompletableFuture<Void> terrainReady;
    private int terrainTicks;
    private long terrainDeadline;
    private net.minecraft.client.multiplayer.ClientLevel lastWorld;
    private String serverPackSignature="";
    private final Path clientSettings;
    private volatile boolean modEnabled;
    private volatile boolean preloadEnabled;
    public MixerService(Minecraft client)throws IOException{
        this.client=client;cache=client.gameDirectory.toPath().resolve(".texturemixer/cache");
        clientSettings=client.gameDirectory.toPath().resolve("config/texturemixer/client.json");
        UiLanguage.select(ClientSettings.loadLanguage(clientSettings));
        preloadEnabled=ClientSettings.loadPreload(clientSettings);
        resourcePreviews=new ResourcePreviewCache(this);
        skyPreviews=new com.fullyx.texturemixer.sky.SkyPreviewCache(this);
        store=new ConfigStore(client.gameDirectory.toPath().resolve("config/texturemixer"));committed=store.load();
        library=new MixerLibrary(this);
        modEnabled=ClientSettings.loadEnabled(clientSettings);
    }
    private void texturemixerInvalidateGeometry() {
        if (client.level != null) {
            client.levelRenderer.invalidateCompiledGeometry(client.level, client.options,
                    client.gameRenderer.mainCamera(), client.getBlockColors());
        }
    }
    public void initialize(){
        if(initialization!=null)return;
        DirectorySource.textureLimit(VersionedTexture.maxSize());
        NativeCompat.configurePackFormat();
        GraphCompiler.useSeparateItemAtlas(NativeCompat.hasSeparateItemAtlas());
        initialization=CompletableFuture.supplyAsync(()->new VanillaAssets(client.getVanillaPackResources()),workers)
            .thenAcceptAsync(assets->{try{
                vanilla=assets;catalog=new Catalog(assets);previews=new PreviewCache(this);status=store.recoveryMessage().isEmpty()?tr("就绪"):store.recoveryMessage();
                refreshSources();
                Path current=cache.resolve("current");
                MixerConfig saved=committed;
                if(saved.enabled){busy=true;status=tr("正在恢复已提交快照…");
                    record Restoration(Generation generation,String notice){}
                    CompletableFuture.supplyAsync(()->{
                        try{
                            if(saved.snapshotId==null&&!Files.exists(current))throw new IOException(tr("缺少已提交快照"));
                            String id=saved.snapshotId==null?Files.readString(current).trim():saved.snapshotId.toString();
                            return new Restoration(Generation.read(cache,id).withSkySelections(saved),tr("已恢复已提交快照"));
                        }
                        catch(Exception error){
                            TextureMixerClient.LOG.error("Saved snapshot unavailable; staging isolated vanilla fallback",error);
                            MixerConfig fallback=saved.copy();fallback.setMainStack(List.of());
                            for(var kind:MixerConfig.Kind.values()){
                                var available=new HashSet<String>();
                                for(var target:catalog.list(kind))available.add(target.id());
                                fallback.overrides(kind).keySet().retainAll(available);
                                fallback.overrides(kind).replaceAll((key,value)->MixerConfig.VANILLA);
                            }
                            try{Generation generation=Generation.compile(fallback,vanilla,Map.of(),catalog);generation.write(cache);return new Restoration(generation,tr("快照不可用，临时使用原版；请求的来源已保留，请修复后应用: ")+message(error));}
                            catch(IOException failure){throw new CompletionException(failure);}
                        }
                    },workers).thenComposeAsync(restored->{mounted=restored.generation();if(!modEnabled){status=tr("TextureMixer 已关闭");return CompletableFuture.completedFuture(null);}return reloadVisibleWorld().thenRunAsync(()->status=restored.notice(),client);},client)
                        .whenCompleteAsync((v,error)->{busy=false;
                            if(error!=null){mounted=null;status=tr("快照恢复失败，混搭已停用: ")+message(error);client.reloadResourcePacks();}
                        },client);
                }
            }catch(Exception error){throw new CompletionException(error);}
        },client).whenCompleteAsync((ignored,error)->{
            if(error!=null){
                status=tr("初始化失败: ")+message(error);
                TextureMixerClient.LOG.error(status,error);
            }
        },client);
    }
    public CompletableFuture<Void> refreshSources(){
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后刷新")));
        List<CompletableFuture<Void>> tasks=new ArrayList<>();
        for(var entry:committed.sources.entrySet())tasks.add(index(entry.getKey(),entry.getValue().canonicalRoot()));
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    public CompletableFuture<Void> checkSources(){return CompletableFuture.runAsync(()->{
        for(var entry:committed.sources.entrySet())try{
            Path path=Path.of(entry.getValue().canonicalRoot());PackSource source=sources.get(entry.getKey());
            if(!Files.exists(path))sourceErrors.put(entry.getKey(),tr("来源失联；已应用快照不受影响"));
            else if(source!=null&&!source.unchanged())sourceErrors.put(entry.getKey(),tr("来源已有变化；请刷新后应用"));
        }catch(Exception error){sourceErrors.put(entry.getKey(),message(error));}
    },workers);}
    public CompletableFuture<Void> index(String id,String path){return CompletableFuture.runAsync(()->{
        try{PackSource source=PackSource.open(Path.of(path));sources.put(id,source);sourceErrors.remove(id);}
        catch(Exception e){sources.remove(id);sourceErrors.put(id,message(e));}
    },workers);}
    public CompletableFuture<String> add(Path path,MixerConfig draft,boolean main){
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后添加来源")));
        return CompletableFuture.supplyAsync(()->{
            try{
                PackSource source=PackSource.open(path);
                for(var entry:draft.sources.entrySet())if(Path.of(entry.getValue().canonicalRoot()).equals(source.root())){
                    sources.put(entry.getKey(),source);return entry.getKey();
                }
                String id=UUID.randomUUID().toString();sources.put(id,source);
                return id;
            }catch(Exception e){throw new CompletionException(e);}
        },workers).thenApplyAsync(id->{
            if(busy)throw new CompletionException(new IOException(tr("正在应用资源，请稍后添加来源")));
            PackSource source=sources.get(id);var record=new MixerConfig.SourceRecord(id,source.root().toString(),source.name(),source.fingerprint());
            MixerConfig updated=draft.copy();updated.sources.put(id,record);
            if(main)updated.addMain(id);else updated.addCandidate(id);
            // Registering a folder persists the library, never the draft rendering selection.
            MixerConfig next=committed.copy();next.sources.put(id,record);
            if(!main)next.addCandidate(id);
            try{store.save(next,false);committed=next;}catch(IOException e){throw new CompletionException(e);}
            draft.sources=updated.sources;draft.mainSource=updated.mainSource;draft.mainSources=updated.mainSources;draft.candidateSources=updated.candidateSources;
            return id;
        },client);
    }
    public CompletableFuture<Void> relocate(String id,Path path,MixerConfig draft){
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后重新定位")));
        return CompletableFuture.supplyAsync(()->{
            try{PackSource source=PackSource.open(path);
                for(var other:draft.sources.entrySet())if(!other.getKey().equals(id)&&Path.of(other.getValue().canonicalRoot()).equals(source.root()))throw new IOException(tr("此材质包已登记为其他来源"));
                return source;
            }catch(IOException e){throw new CompletionException(e);}
        },workers).thenAcceptAsync(source->{
            if(busy)throw new CompletionException(new IOException(tr("正在应用资源，请稍后重新定位")));
            var record=new MixerConfig.SourceRecord(id,source.root().toString(),source.name(),source.fingerprint());
            MixerConfig next=committed.copy();next.sources.put(id,record);
            try{store.save(next,false);committed=next;draft.sources.put(id,record);sources.put(id,source);sourceErrors.remove(id);}catch(IOException e){throw new CompletionException(e);}
        },client);
    }
    public CompletableFuture<Void> apply(MixerConfig draft){
        if(!modEnabled)return CompletableFuture.failedFuture(new IOException(tr("请先从 Mod Menu 开启 TextureMixer")));
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在应用资源")));
        busy=true;status=tr("正在校验与编译资源…");MixerConfig next=draft.copy();next.enabled=true;
        Generation old=mounted;MixerConfig previous=committed.copy();
        var activated=new java.util.concurrent.atomic.AtomicBoolean();
        Map<String,PackSource> indexed=Map.copyOf(sources);
        return CompletableFuture.supplyAsync(()->{
            try{Generation generation=Generation.compile(next,vanilla,indexed,catalog);generation.write(cache);return generation;}
            catch(Exception e){throw new CompletionException(e);}
        },workers).thenComposeAsync(g->{
            status=tr("正在重载资源…");mounted=g;activated.set(true);
            return reloadMountedResources().thenComposeAsync(v->{
                texturemixerInvalidateGeometry();if(client.level!=null)client.level.clearTintCaches();
                return awaitTerrain();
            },client).thenRunAsync(()->{
                try{
                    next.snapshotId=UUID.fromString(g.id());
                    store.save(next,true);
                    committed=next;ColorBindings.retain(g.id(),old==null?"":old.id());status=tr("已应用");}
                catch(Exception e){throw new CompletionException(e);}
            },client);
        },client).handleAsync((nothing,error)->{
            if(error==null){busy=false;return CompletableFuture.<Void>completedFuture(null);}
            if(!activated.get()){busy=false;status=tr("校验失败，未更改已应用资源: ")+message(error);return CompletableFuture.<Void>failedFuture(error);}
            TextureMixerClient.LOG.error("Apply failed; restoring previous generation",error);
            mounted=old;committed=previous;status=tr("应用失败，正在恢复…");
            try{store.save(previous,false);}
            catch(IOException recoveryError){TextureMixerClient.LOG.error("Could not restore persistent configuration",recoveryError);}
            return reloadVisibleWorld().handleAsync((ignored,rollbackError)->{
                if(rollbackError==null){busy=false;status=tr("应用失败，已恢复上次设置: ")+message(error);return CompletableFuture.<Void>failedFuture(error);}
                mounted=null;status=tr("回滚失败，正在恢复原生资源…");
                return reloadVisibleWorld().handleAsync((fallback,fallbackError)->{
                    busy=false;status=fallbackError==null?tr("回滚失败；混搭已停用，已恢复原生资源: ")+message(rollbackError):tr("原生资源恢复也失败: ")+message(fallbackError);
                    throw new CompletionException(error);
                },client).thenApply(fallback->(Void)null);
            },client).thenCompose(future->future);
        },client).thenCompose(future->future);
    }
    private CompletableFuture<Void> reloadMountedResources(){
        com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED.releaseTextures();
        String expected=runtime()==null?"":runtime().id();
        return reloadNativeResources().thenComposeAsync(ignored->{
            if(isGenerationLoaded(expected))return CompletableFuture.completedFuture(null);
            // A server reload may already have built its pack list when Apply starts.
            // Complete one fresh reload with the requested personal generation before committing it.
            return reloadNativeResources().thenRunAsync(()->{
                if(!isGenerationLoaded(expected))throw new CompletionException(new IOException(tr("个人材质包尚未完成重载，请重试应用")));
            },client);
        },client);
    }
    private CompletableFuture<Void> reloadNativeResources(){
        if(pendingReload!=null)return CompletableFuture.failedFuture(new IOException(tr("资源重载尚未结束")));
        CompletableFuture<Void> result=new CompletableFuture<>();
        pendingReload=result;
        try{
            client.reloadResourcePacks().whenCompleteAsync((ignored,error)->{
                if(pendingReload!=result||nativeReloadFailure!=null)return;
                pendingReload=null;
                if(error==null)result.complete(null);else result.completeExceptionally(error);
            },client);
        }catch(Throwable error){pendingReload=null;result.completeExceptionally(error);}
        return result;
    }
    /** Vanilla abandons the original reload future on failure and starts a forced recovery. */
    public void nativeReloadFailed(Throwable error){
        if(runtime()==null&&pendingReload==null)return;
        if(!busy){busy=true;nativeRecoveryOwnsBusy=true;}
        if(nativeReloadFailure==null)nativeReloadFailure=error;
        nativeRecoverySettled=false;
        mounted=null;
        status=tr("资源重载失败，正在恢复原生资源…");
        TextureMixerClient.LOG.error("Native resource reload failed; unmounting personal resources before recovery",error);
    }
    public void nativeRecoveryFinished(){
        if(nativeReloadFailure!=null)nativeRecoverySettled=true;
    }
    private boolean isGenerationLoaded(String expected) {
        var loaded = client.getResourceManager().listPacks()
                .map(PackResources::packId)
                .filter(id -> id.startsWith("texturemixer/") && !id.equals("texturemixer/ui-font"));
        return expected.isEmpty() ? loaded.findAny().isEmpty() : loaded.anyMatch(id -> id.equals("texturemixer/" + expected));
    }
    private CompletableFuture<Void> reloadVisibleWorld(){
        return reloadMountedResources().thenComposeAsync(v->{texturemixerInvalidateGeometry();ColorBindings.clearWorld();if(client.level!=null)client.level.clearTintCaches();return awaitTerrain();},client);
    }
    private CompletableFuture<Void> awaitTerrain(){
        if(client.level==null)return CompletableFuture.completedFuture(null);
        status=tr("正在重建可见方块…");terrainReady=new CompletableFuture<>();terrainTicks=0;terrainDeadline=System.nanoTime()+120_000_000_000L;return terrainReady;
    }
    public void tick(){
        boolean browsing=NativeCompat.screen(client) instanceof com.fullyx.texturemixer.ui.MixerScreen
                ||NativeCompat.screen(client) instanceof com.fullyx.texturemixer.ui.SelectionDialog;
        skyPreviews.tick(browsing);
        resourcePreviews.tick(browsing);
        if(nativeRecoverySettled&&NativeCompat.overlay(client)==null){
            Throwable failure=nativeReloadFailure;
            CompletableFuture<Void> waiting=pendingReload;
            pendingReload=null;nativeReloadFailure=null;nativeRecoverySettled=false;
            if(nativeRecoveryOwnsBusy){busy=false;nativeRecoveryOwnsBusy=false;}
            if(waiting!=null)waiting.completeExceptionally(failure);
            else status=tr("资源重载失败，混搭已暂时停用，请调整后重新应用: ")+message(failure);
        }
        if(previews!=null)previews.tick((NativeCompat.screen(client) instanceof com.fullyx.texturemixer.ui.MixerScreen||NativeCompat.screen(client) instanceof com.fullyx.texturemixer.ui.SelectionDialog));
        if(lastWorld!=client.level){lastWorld=client.level;ColorBindings.clearWorld();EquipmentBindings.clearWorld();
            if(client.level==null){if(previews!=null)previews.invalidate();com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED.close();}}
        if(terrainReady!=null&&!terrainReady.isDone()){
            if(++terrainTicks>2&&(client.level==null||client.levelRenderer.hasRenderedAllSections())){terrainReady.complete(null);terrainReady=null;}
            else if(System.nanoTime()>terrainDeadline){terrainReady.completeExceptionally(new IOException(tr("可见区块重建超时")));terrainReady=null;}
        }
    }
    public CompletableFuture<Void> disable(){
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在应用资源")));
        busy=true;Generation old=mounted;MixerConfig previous=committed.copy();mounted=null;
        return reloadVisibleWorld().thenRunAsync(()->{
            try{MixerConfig next=committed.copy();next.enabled=false;next.snapshotId=null;store.save(next,true);committed=next;status=tr("混搭已停用");}
            catch(IOException e){throw new CompletionException(e);}
        },client).handleAsync((v,error)->{
            if(error==null){busy=false;return CompletableFuture.<Void>completedFuture(null);}
            mounted=old;committed=previous;status=tr("停用失败，正在恢复上次资源…");
            try{store.save(previous,false);}catch(IOException recoveryError){TextureMixerClient.LOG.error("Could not restore configuration after disable",recoveryError);}
            return reloadVisibleWorld().handleAsync((ignored,recoveryError)->{
                busy=false;status=recoveryError==null?tr("停用失败，已恢复上次资源: ")+message(error):tr("停用与恢复均失败: ")+message(recoveryError);
                throw new CompletionException(error);
            },client).thenApply(ignored->(Void)null);
        },client).thenCompose(future->future);
    }
    public boolean modEnabled(){return modEnabled;}
    public boolean preloadEnabled(){return preloadEnabled;}
    public void setPreloadEnabled(boolean enabled)throws IOException {ClientSettings.savePreload(clientSettings,enabled);preloadEnabled=enabled;}
    public void setLanguage(String language)throws IOException {
        ClientSettings.saveLanguage(clientSettings,language);UiLanguage.select(language);
        if(!busy)status=tr("就绪");
    }
    public CompletableFuture<Void> setModEnabled(boolean enabled){
        if(busy)return CompletableFuture.failedFuture(new IOException(tr("正在切换资源，请稍候")));
        if(enabled==modEnabled)return CompletableFuture.completedFuture(null);
        boolean previous=modEnabled;modEnabled=enabled;busy=true;status=tr("正在切换 TextureMixer…");
        return reloadVisibleWorld().thenRunAsync(()->{
            try{ClientSettings.saveEnabled(clientSettings,enabled);}
            catch(IOException error){throw new CompletionException(error);}
            status=enabled?tr("TextureMixer 已开启"):tr("TextureMixer 已关闭");
        },client).handleAsync((ignored,error)->{
            if(error==null){busy=false;return CompletableFuture.<Void>completedFuture(null);}
            modEnabled=previous;status=tr("切换失败，正在恢复…");
            return reloadVisibleWorld().handleAsync((restored,rollbackError)->{
                busy=false;
                status=rollbackError==null?tr("开关切换失败，已恢复原状态: ")+message(error):tr("开关恢复失败: ")+message(rollbackError);
                throw new CompletionException(error);
            },client).thenApply(restored->(Void)null);
        },client).thenCompose(future->future);
    }
    public Generation runtime(){return modEnabled?mounted:null;}
    public List<PackResources> resourcePacks(List<PackResources> original){
        String signature=original.stream().filter(MixerService::isServerPack).map(PackResources::packId).reduce("",(left,right)->left+"\n"+right);
        if(!serverPackSignature.equals(signature)){
            serverPackSignature=signature;serverPresent=!signature.isEmpty();
            if(!busy&&modEnabled)status=serverPresent?tr("服务器资源包已作为基础，个人混搭继续生效"):tr("服务器资源包已离开，个人混搭继续生效");
        }
        Generation generation=runtime();if(generation==null)return original;
        List<PackResources> result=new ArrayList<>();
        for(PackResources pack:original){Pack profile=client.getResourcePackRepository().getPack(pack.packId());
            if(pack.packId().equals("vanilla")||isServerPack(pack)||profile!=null&&profile.isRequired())result.add(pack);else pack.close();}
        result.add(new MemoryPack("texturemixer/"+generation.id(),generation.assets()));return result;
    }
    private static boolean isServerPack(PackResources pack){return pack.location().source()==net.minecraft.server.packs.repository.PackSource.SERVER||pack.packId().startsWith("server/");}
    public String prefixForItem(String id){Generation g=runtime();return g==null?null:g.itemPrefixes().get(id);}
    public String prefixForBlock(String id){Generation g=runtime();return g==null?null:g.blockPrefixes().get(id);}
    public static String message(Throwable error){while(error.getCause()!=null&&(error instanceof CompletionException||error instanceof ExecutionException))error=error.getCause();return error.getMessage()==null?error.toString():error.getMessage();}
    @Override public void close(){skyPreviews.close();resourcePreviews.close();if(previews!=null)previews.close();ColorBindings.clearWorld();EquipmentBindings.clearWorld();com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED.close();com.fullyx.texturemixer.sky.NativeSkyBindings.close();workers.shutdownNow();}
}
