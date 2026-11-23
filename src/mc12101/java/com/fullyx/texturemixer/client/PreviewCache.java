package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.compat.mc12111.*;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.registry.Registries;
import net.minecraft.block.*;
import net.minecraft.resource.*;
import net.minecraft.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Each preview bakes only its target's closure; it never reloads the global ResourceManager. */
public final class PreviewCache implements AutoCloseable {
    public enum Mode { ICON,HAND,WORN }
    public final class Entry {
        public final String key;
        public volatile String error;
        public volatile String note;
        public volatile GraphCompiler.Status status;
        public volatile Arena arena;
        public volatile boolean discarded;
        public volatile boolean budgetLimited;
        private CompletableFuture<?> computation;
        private Entry shared;
        private Prepared prepared;
        Entry(String key){this.key=key;}
        public boolean ready(){return !discarded&&(shared != null ? shared.ready() : arena!=null&&arena.ready);}
    }
    private record Prepared(Map<AssetId,byte[]> files,Identifier itemModel,ItemStack stack,String prefix,GraphCompiler.Status status,BlockState blockState,String note,boolean equipmentOnly,EquipmentBindings.PreparedPreview equipment) implements AutoCloseable {
        @Override public void close(){equipment.close();}
    }
    private final MixerService service;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(32,.75f,true);
    private final Set<Arena> retired=new LinkedHashSet<>();
    private record GpuJob(Entry entry,Runnable action){}
    private final ArrayDeque<GpuJob> gpuTasks=new ArrayDeque<>();
    private record LoadRequest(Entry entry,Catalog.Target target,String source,int stateIndex,Mode mode,String token) {}
    private final ArrayDeque<LoadRequest> preparations=new ArrayDeque<>();
    private int activeLoads,activePreparations;
    private Set<String> retained = Set.of();
    public PreviewCache(MixerService service){this.service=service;}
    public static String key(Catalog.Target target,String source,int stateIndex,Mode mode){
        if(target.kind()==MixerConfig.Kind.ITEM)stateIndex=0;
        return source+"|"+target.kind()+"|"+target.id()+"|"+stateIndex+(mode==Mode.ICON?"":"|"+mode);
    }
    public Entry get(Catalog.Target target,String source,int stateIndex){return get(target,source,stateIndex,Mode.ICON);}
    public Entry get(Catalog.Target target,String source,int stateIndex,Mode mode){
        String key=key(target,source,stateIndex,mode);
        if(mode==Mode.HAND){
            Entry base=get(target,source,stateIndex,Mode.ICON);
            Entry alias=entries.computeIfAbsent(key,Entry::new);
            alias.shared=base;alias.status=base.status;alias.note=base.note;
            syncAlias(alias);
            return alias;
        }
        Entry existing=entries.get(key);
        if(existing!=null){
            if(existing.shared!=null)existing.shared=get(target,MixerConfig.VANILLA,stateIndex,mode);
            syncAlias(existing);
            return existing;
        }
        Entry entry=new Entry(key);
        entries.put(key,entry);
        preparations.addLast(new LoadRequest(entry,target,source,stateIndex,mode,UUID.randomUUID().toString().replace("-","")));
        return entry;
    }
    public Entry getIfPresent(Catalog.Target target,String source,int stateIndex,Mode mode){
        Entry entry=entries.get(key(target,source,stateIndex,mode));
        if(entry!=null)syncAlias(entry);
        return entry;
    }
    private void syncAlias(Entry entry){
        if(entry.shared==null)return;
        syncAlias(entry.shared);
        entry.arena=entry.shared.ready()?entry.shared.arena:null;
        entry.error=entry.shared.error;
        entry.budgetLimited=entry.shared.budgetLimited;
    }
    private void startPreparation(LoadRequest request){
        Entry entry=request.entry();
        activePreparations++;
        entry.computation=CompletableFuture.supplyAsync(()->{
            if(entry.discarded)throw new CancellationException();
            try{return prepare(request.target(),request.source(),request.stateIndex(),request.token(),request.mode());}
            catch(Exception error){throw new CompletionException(error);}
        },service.workers);
        entry.computation.whenCompleteAsync((value,error)->{
            activePreparations--;
            Prepared prepared=(Prepared)value;
            if(entry.discarded){if(prepared!=null)prepared.close();return;}
            if(error!=null){entry.error=MixerService.message(error);com.fullyx.texturemixer.TextureMixerClient.LOG.error("Preview preparation failed: "+entry.key,error);return;}
            entry.status=prepared.status();entry.note=prepared.note();
            if(prepared.status()==GraphCompiler.Status.VANILLA_FALLBACK&&!request.source().equals(MixerConfig.VANILLA)){
                prepared.close();
                entry.shared=get(request.target(),MixerConfig.VANILLA,request.stateIndex(),request.mode());
                syncAlias(entry);
                return;
            }
            entry.prepared=prepared;
            gpuTasks.addLast(new GpuJob(entry,()->loadArena(request)));
        },service.client);
    }
    private void loadArena(LoadRequest request){
        Entry entry=request.entry();
        Prepared prepared=entry.prepared;
        entry.prepared=null;
        if(entry.discarded){if(prepared!=null)prepared.close();return;}
        try{
            Arena arena=new Arena(request.token(),prepared);
            entry.arena=arena;
            arena.loading=arena.load();
            activeLoads++;
            arena.loading.whenCompleteAsync((ignored,error)->{
                activeLoads--;
                if(entry.discarded){retired.add(arena);return;}
                if(error!=null){
                    entry.error=MixerService.message(error);
                    com.fullyx.texturemixer.TextureMixerClient.LOG.error("Preview baking failed: "+entry.key,error);
                    retired.add(arena);entry.arena=null;
                    return;
                }
                arena.measure();arena.ready=true;
                if(arena.bytes()>64L*1024*1024||arena.cpuBytes()>128L*1024*1024){
                    entry.budgetLimited=true;entry.error=tr("此目标超出预览缓存预算，仍可选择后应用");
                    arena.ready=false;retired.add(arena);entry.arena=null;
                }
            },service.client);
        }catch(Exception error){
            if(prepared!=null)prepared.close();
            entry.error=MixerService.message(error);
            com.fullyx.texturemixer.TextureMixerClient.LOG.error("Preview arena failed: "+entry.key,error);
            if(entry.arena!=null){retired.add(entry.arena);entry.arena=null;}
        }
    }
    private Prepared prepare(Catalog.Target target,String source,int stateIndex,String token,Mode mode)throws IOException{
        Assets.View view=Generation.view(source,service.sources,service.vanilla);
        String note=UnsupportedResources.check(view.source(),target.kind(),AssetId.of(target.id()));
        GraphCompiler compiler=new GraphCompiler(view,"preview|"+token+"|"+source+"|"+target.id());
        boolean fluid = target.kind() == MixerConfig.Kind.BLOCK && FluidPreviewModel.isFluid(target.model());
        GraphCompiler.Result graph;
        Map<AssetId,byte[]> files=new TreeMap<>();Identifier model;ItemStack stack;
        boolean equipmentOnly=mode==Mode.WORN&&target.equipment()!=null&&target.stack().get(DataComponentTypes.TRIM)==null;
        if(target.kind()==MixerConfig.Kind.ITEM){
            graph=equipmentOnly?NativePackModels.previewWear(compiler,target)
                    :NativePackModels.previewItem(compiler,target,mode==Mode.WORN);
            files.putAll(graph.resources());
            model=VersionedIdentifiers.of(target.model().toString());
            stack=target.stack().copy();
        }else{
            graph=compiler.block(target.model(),target.states());
            files.putAll(graph.resources());
            stack=target.stack().copy();
            model=VersionedIdentifiers.of(target.model().toString());
            if(target.blockState().hasBlockEntity()&&!stack.isEmpty()){
                var item=Registries.ITEM.getId(stack.getItem());
                if(item!=null){
                    var graphForItem=new GraphCompiler(view,"preview|"+token+"|"+source+"|item|"+item).legacyItem(AssetId.of(item.toString()),null,true);
                    Generation.merge(files,graphForItem.resources());
                    model=item;
                }
            }else if(fluid){
                model=VersionedIdentifiers.vanilla("stone");
                stack=Items.STONE.getDefaultStack();
                files.put(AssetId.of("models/item/stone.json"), FluidPreviewModel.create(compiler, target.model(), false));
            }
        }
        BlockState selectedState=target.kind()==MixerConfig.Kind.BLOCK&&!fluid?target.blockState().getBlock().getStateManager().getStates().get(Math.floorMod(stateIndex,target.states().size())):null;
        return new Prepared(files,model,stack,graph.namespacePrefix(),graph.status(),selectedState,note,equipmentOnly,EquipmentBindings.preparePreview(files));
    }
    public void tick(boolean browsing){
        Set<Arena> animated=new HashSet<>();
        for(Entry entry:entries.values())if(entry.ready()&&entry.arena!=null&&animated.add(entry.arena)&&entry.arena.modelArena!=null)
            entry.arena.modelArena.tick();
        // Deferred GUI commands from the preceding frame no longer reference retired arenas.
        var disposed=retired.iterator();while(disposed.hasNext()){Arena arena=disposed.next();if(arena.loading!=null&&!arena.loading.isDone())continue;arena.close();disposed.remove();break;}
        if(!browsing){
            var pending=entries.values().iterator();while(pending.hasNext()){Entry entry=pending.next();if(!entry.ready()){discard(entry);pending.remove();}}
            gpuTasks.clear();preparations.removeIf(request->request.entry().discarded);return;
        }
        while(activePreparations<2&&activeLoads<2&&gpuTasks.size()<2&&!preparations.isEmpty()){
            LoadRequest request=preparations.removeFirst();
            if(!request.entry().discarded)startPreparation(request);
        }
        while(activeLoads<2&&!gpuTasks.isEmpty()){
            GpuJob job=gpuTasks.removeFirst();if(job.entry.discarded)continue;job.action.run();break;
        }
        long bytes=entries.values().stream().filter(e->e.shared==null&&e.ready()).mapToLong(e->e.arena.bytes()).sum();
        long cpu=entries.values().stream().filter(e->e.shared==null&&e.arena!=null).mapToLong(e->e.arena.cpuBytes()).sum();
        Set<String> pinned=new HashSet<>(retained);
        retainAliases(pinned);
        var iterator=entries.entrySet().iterator();
        while((bytes>64L*1024*1024||cpu>128L*1024*1024||entries.size()>96)&&iterator.hasNext()){
            Entry e=iterator.next().getValue();if(pinned.contains(e.key)||e.arena!=null&&!e.arena.ready)continue;
            iterator.remove();if(e.shared==null&&e.arena!=null){bytes-=e.arena.bytes();cpu-=e.arena.cpuBytes();}discard(e);
        }
        // The aggregate budget caps retained offscreen arenas. Individual arenas are
        // capped on load. Keep the displayed working set intact until it leaves the grid.
    }
    private void discard(Entry entry){
        entry.discarded=true;
        if(entry.prepared!=null){entry.prepared.close();entry.prepared=null;}
        if(entry.shared==null&&entry.arena!=null)retired.add(entry.arena);
    }
    private void retainAliases(Set<String> keys){
        for(String key:new ArrayList<>(keys)){
            if(key.endsWith("|HAND"))keys.add(key.substring(0,key.length()-5));
        }
        for(Entry entry:entries.values())if(keys.contains(entry.key)){
            Entry shared=entry.shared;
            while(shared!=null){keys.add(shared.key);shared=shared.shared;}
        }
    }
    public void retainPending(Set<String> keys){
        retained=Set.copyOf(keys);
        Set<String> needed=new HashSet<>(keys);
        retainAliases(needed);
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(!entry.getValue().ready()&&!needed.contains(entry.getKey())){
                discard(entry.getValue());iterator.remove();
            }
        }
        gpuTasks.removeIf(job->job.entry.discarded);
        preparations.removeIf(request->request.entry().discarded);
    }
    public record Usage(int entries,int pending,int retired,long cpuBytes,long gpuBytes){}
    public Usage usage(){return new Usage(entries.size(),(int)entries.values().stream().filter(e->!e.ready()&&e.error==null).count(),retired.size(),
            entries.values().stream().filter(e->e.shared==null&&e.arena!=null).mapToLong(e->e.arena.cpuBytes()).sum(),entries.values().stream().filter(e->e.shared==null&&e.ready()).mapToLong(e->e.arena.bytes()).sum());}
    public void invalidate(){retained=Set.of();for(Entry entry:entries.values())discard(entry);entries.clear();gpuTasks.clear();preparations.clear();}
    @Override public void close(){invalidate();for(Arena arena:retired)arena.close();retired.clear();}

    public final class Arena implements AutoCloseable {
        public final String token,prefix;
        public final ItemStack stack;
        public final Identifier modelId;
        public final List<ItemPreviewState> previewStates;
        public final PreviewModelArena modelArena;
        public final BakedModelManager models;
        public final ItemRenderer items;
        public final BuiltinModelItemRenderer builtin;
        public final BlockRenderManager blocks;
        private final BlockEntityRenderDispatcher blockRenderers;
        public boolean ready;
        private final LifecycledResourceManagerImpl resources;
        private final Map<AssetId,byte[]> files;
        private boolean closed;
        private CompletableFuture<?> loading;
        private final BlockState blockState;
        private BlockEntityPreview blockEntityPreview;
        private EquipmentBindings.PreparedPreview equipment;
        private long textureBytes,retainedBytes;
        Arena(String token,Prepared prepared){
            this.token=token;prefix=prepared.prefix;modelId=prepared.itemModel;stack=prepared.stack;blockState=prepared.blockState;
            files=new TreeMap<>(prepared.files);
            byte[] definition=files.get(new AssetId(modelId.getNamespace(),"items/"+modelId.getPath()+".json"));
            previewStates=definition==null?List.of(ItemPreviewState.NORMAL):ItemPreviewState.fromDefinition(JsonParser.parseString(new String(definition,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            equipment=prepared.equipment();
            if(prepared.equipmentOnly()){
                resources=null;modelArena=null;models=null;items=null;builtin=null;blockRenderers=null;blocks=null;
                return;
            }
            for(String atlas:List.of("armor_trims","banner_patterns","beds","blocks","items","chests","decorated_pot","gui","map_decorations","paintings","particles","shield_patterns","shulker_boxes","signs","celestials"))
                files.putIfAbsent(AssetId.of("atlases/"+atlas+".json"),"{\"sources\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            resources=new LifecycledResourceManagerImpl(ResourceType.CLIENT_RESOURCES,List.of(new MemoryPack("texturemixer/preview/"+token,new Assets.Memory(files))));
            modelArena=new PreviewModelArena(service,resources,token);
            models=modelArena.models;
            var client=service.client;
            blockRenderers=new BlockEntityRenderDispatcher(UiFonts.renderer(),client.getEntityModelLoader(),
                    this::previewBlocks,this::previewItems,client::getEntityRenderDispatcher);
            builtin=new BuiltinModelItemRenderer(blockRenderers,client.getEntityModelLoader());
            blocks=new BlockRenderManager(models.getBlockModels(),builtin,client.getBlockColors());
            items=new ItemRenderer(client,client.getTextureManager(),models,((ItemColorsAccess)client).texturemixer$itemColors(),builtin);
        }
        private BlockRenderManager previewBlocks(){
            return blocks;
        }
        private ItemRenderer previewItems(){
            return items;
        }
        public BlockEntityRenderDispatcher blockRenderers(){
            return blockRenderers;
        }
        CompletableFuture<?> load(){
            if(resources==null){
                EquipmentBindings.registerPreview(token,prefix,equipment,null);
                equipment=null;
                return CompletableFuture.completedFuture(null);
            }
            Executor prepare=task->service.workers.execute(()->RenderScope.arena(token,()->{task.run();return null;}));
            Executor apply=task->service.client.execute(()->RenderScope.arena(token,()->{task.run();return null;}));
            return modelArena.load(prepare,apply).thenRunAsync(()->{
                    EquipmentBindings.registerPreview(token,prefix,equipment,modelArena::sprite);
                    equipment=null;
                    try{ColorBindings.preview(prefix,files);}catch(IOException e){throw new CompletionException(e);}
                    blockRenderers.reload(resources);
                    builtin.reload(resources);
                    items.reload(resources);
                    if(blockState!=null&&blockState.hasBlockEntity())blockEntityPreview=BlockEntityPreview.create(this,blockState);
                },service.client);
        }
        public void draw(DrawContext context,Catalog.Target target,Mode mode,int x,int y,int size,ScreenRect clip){
            draw(context,target,mode,x,y,size,clip,ItemPreviewState.NORMAL);
        }
        public void draw(DrawContext context,Catalog.Target target,Mode mode,int x,int y,int size,ScreenRect clip,ItemPreviewState previewState){
            if(!ready)return;
            if(mode==Mode.WORN){EquipmentBindings.drawWorn(this,context,target,x,y,size);return;}
            boolean hand=mode==Mode.HAND;
            var client=service.client;
            ItemStack renderStack=previewState.apply(stack,client.world);
            var modeId=hand?ModelTransformationMode.FIRST_PERSON_RIGHT_HAND:ModelTransformationMode.GUI;
            RenderScope.in(prefix,()->RenderScope.arena(token,()->{
                var state=new ItemDisplayState(hand,target.id().equals("minecraft:crossbow"));
                if(blockState!=null){
                    state.setSideLit(true);
                    if(blockState.getRenderType()==BlockRenderType.MODEL){
                        state.add(PreviewItemModels.block(this,blockState));
                    }else if(!renderStack.isEmpty()&&blockEntityPreview==null){
                        PreviewItemModels.addItem(state,items,renderStack,modeId,client);
                    }
                    if(blockEntityPreview!=null)state.add(blockEntityPreview::render);
                }else{
                    PreviewItemModels.addItem(state,items,renderStack,modeId,client);
                }
                PreviewGui.submit(context,token,state,x+6,y+6,size-12,clip);
                return null;
            }));
        }
        private void measure(){
            final long[] size={0};
            if(modelArena!=null)modelArena.eachAtlas((id,t)->size[0]+=VersionedTexture.bytes(t));
            long equipmentBytes=EquipmentBindings.previewTextureBytes(token);
            textureBytes=size[0]+equipmentBytes;
            retainedBytes=files.values().stream().mapToLong(bytes->bytes.length).sum()+2*size[0]+equipmentBytes;
        }
        long bytes(){return textureBytes;}
        // Include decoded/upload atlas storage and retained direct NativeImages, not just PNG payloads.
        // Native model and driver allocations remain outside this conservative texture-accounting estimate.
        long cpuBytes(){return retainedBytes;}
        public void close(){if(closed)return;closed=true;ready=false;EquipmentBindings.unregisterPreview(token);ColorBindings.retirePreview(prefix);
            if(equipment!=null){equipment.close();equipment=null;}
            if(modelArena!=null)modelArena.close();if(resources!=null)resources.close();}
    }
}
