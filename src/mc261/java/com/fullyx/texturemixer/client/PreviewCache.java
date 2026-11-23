package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.compat.mc12111.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

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
        boolean equipmentOnly=mode==Mode.WORN&&target.equipment()!=null&&target.stack().get(DataComponents.TRIM)==null;
        if(target.kind()==MixerConfig.Kind.ITEM){
            graph=equipmentOnly?compiler.wornPreview(target.equipment())
                    :compiler.itemPreview(target.model(),mode==Mode.WORN?target.equipment():null,target.stack().get(DataComponents.TRIM)!=null);files.putAll(graph.resources());model=Identifier.parse(target.model().toString());stack=target.stack().copy();
        }else{
            graph=compiler.block(target.model(),target.states());files.putAll(graph.resources());
            if(target.blockState().hasBlockEntity()&&!target.stack().isEmpty()){
                Identifier real=target.stack().get(DataComponents.ITEM_MODEL);
                GraphCompiler.Result item=new GraphCompiler(view,"preview|"+token+"|"+source+"|"+target.id()).item(AssetId.of(real.toString()),null);
                Generation.merge(files,item.resources());model=real;stack=target.stack().copy();
            }else{
                model=Identifier.fromNamespaceAndPath("texturemixer","block_preview");stack=service.catalog.defaultStack(Items.STONE);
                JsonArray models=new JsonArray();AssetId stateFile=target.model().file("blockstates",".json");
                if(!fluid&&files.containsKey(stateFile)){
                    JsonObject definition=JsonParser.parseString(new String(files.get(stateFile),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    List<BlockState> states=target.blockState().getBlock().getStateDefinition().getPossibleStates();BlockState state=states.get(Math.floorMod(stateIndex,states.size()));
                    if(definition.has("variants"))for(var variant:definition.getAsJsonObject("variants").entrySet())
                        if(matches(state,variant.getKey())){addModels(models,variant.getValue());break;}
                    if(definition.has("multipart"))for(var part:definition.getAsJsonArray("multipart")){
                        JsonObject object=part.getAsJsonObject();if(!object.has("when")||when(state,object.get("when")))addModels(models,object.get("apply"));
                    }
                }
                if (fluid) {
                    AssetId cubeId = AssetId.of("texturemixer:block_preview");
                    files.put(cubeId.file("models", ".json"), FluidPreviewModel.create(compiler, target.model(), true));
                    JsonObject fluidModel = new JsonObject();
                    fluidModel.addProperty("type", "minecraft:model");
                    fluidModel.addProperty("model", cubeId.toString());
                    if (target.model().path().equals("water")) {
                        JsonArray tints = new JsonArray();
                        JsonObject tint = new JsonObject();
                        tint.addProperty("type", "minecraft:constant");
                        tint.addProperty("value", 0x3f76e4);
                        tints.add(tint);
                        fluidModel.add("tints", tints);
                    }
                    models.add(fluidModel);
                }
                JsonObject root=new JsonObject(),node=new JsonObject();node.addProperty("type",models.isEmpty()?"minecraft:empty":"minecraft:composite");
                if(!models.isEmpty())node.add("models",models);root.add("model",node);
                files.put(AssetId.of(model.toString()).file("items",".json"),Assets.jsonBytes(root));
            }
        }
        BlockState selectedState=target.kind()==MixerConfig.Kind.BLOCK&&!fluid?target.blockState().getBlock().getStateDefinition().getPossibleStates().get(Math.floorMod(stateIndex,target.states().size())):null;
        return new Prepared(files,model,stack,graph.namespacePrefix(),graph.status(),selectedState,note,equipmentOnly,EquipmentBindings.preparePreview(files));
    }
    private static void addModels(JsonArray output,JsonElement value){
        if(value.isJsonArray())value=value.getAsJsonArray().get(0);
        JsonObject object=value.getAsJsonObject(),node=new JsonObject();node.addProperty("type","minecraft:model");node.add("model",object.get("model"));output.add(node);
    }
    private static boolean matches(BlockState state,String text){
        if(text.isEmpty())return true;
        for(String part:text.split(",")){String[] pair=part.split("=");if(pair.length!=2||!property(state,pair[0]).equals(pair[1]))return false;}return true;
    }
    private static String property(BlockState state,String name){var property=state.getBlock().getStateDefinition().getProperty(name);return property==null?"":state.getValue(property).toString().toLowerCase(Locale.ROOT);}
    private static boolean when(BlockState state,JsonElement value){
        JsonObject object=value.getAsJsonObject();
        if(object.has("OR")){for(var e:object.getAsJsonArray("OR"))if(when(state,e))return true;return false;}
        if(object.has("AND")){for(var e:object.getAsJsonArray("AND"))if(!when(state,e))return false;return true;}
        for(var e:object.entrySet())if(!Arrays.asList(e.getValue().getAsString().split("\\|" )).contains(property(state,e.getKey())))return false;return true;
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
        public final ModelManager models;
        public final ItemModelResolver items;
        public boolean ready;
        private final MultiPackResourceManager resources;
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
                resources=null;modelArena=null;models=null;items=null;
                return;
            }
            for(String atlas:List.of("armor_trims","banner_patterns","beds","blocks","items","chests","decorated_pot","gui","map_decorations","paintings","particles","shield_patterns","shulker_boxes","signs","celestials"))
                files.putIfAbsent(AssetId.of("atlases/"+atlas+".json"),"{\"sources\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(new MemoryPack("texturemixer/preview/"+token,new Assets.Memory(files))));
            modelArena=new PreviewModelArena(service,resources,token);
            models=modelArena.models;
            items=new ItemModelResolver(models);
        }
        CompletableFuture<?> load(){
            if(resources==null){
                EquipmentBindings.registerPreview(token,prefix,equipment,null,null);
                equipment=null;
                return CompletableFuture.completedFuture(null);
            }
            Executor prepare=task->service.workers.execute(()->RenderScope.arena(token,()->{task.run();return null;}));
            Executor apply=task->service.client.execute(()->RenderScope.arena(token,()->{task.run();return null;}));
            return modelArena.load(prepare,apply).thenRunAsync(()->{
                    EquipmentBindings.registerPreview(token,prefix,equipment,modelArena::sprite,items);
                    equipment=null;
                    try{ColorBindings.preview(prefix,files);}catch(IOException e){throw new CompletionException(e);}
                    if(blockState!=null&&blockState.hasBlockEntity())blockEntityPreview=BlockEntityPreview.create(this,blockState,resources);
                },service.client);
        }
        public void draw(GuiGraphicsExtractor context,Catalog.Target target,Mode mode,int x,int y,int size,ScreenRectangle clip){
            draw(context,target,mode,x,y,size,clip,ItemPreviewState.NORMAL);
        }
        public void draw(GuiGraphicsExtractor context,Catalog.Target target,Mode mode,int x,int y,int size,ScreenRectangle clip,ItemPreviewState previewState){
            if(!ready)return;
            if(mode==Mode.WORN){EquipmentBindings.drawWorn(this,context,target,x,y,size);return;}
            var state=new ItemDisplayState(mode==Mode.HAND,target.id().equals("minecraft:crossbow"));
            ItemStack renderStack=previewState.apply(stack,service.client.level);renderStack.set(DataComponents.ITEM_MODEL,modelId);
            RenderScope.in(prefix,()->RenderScope.arena(token,()->{PreviewItemModels.update(items,state,renderStack,mode==Mode.HAND,service.client);return null;}));
            if(blockState!=null&&(blockState.getRenderShape()==RenderShape.MODEL||blockEntityPreview!=null))
                RenderScope.in(prefix,()->RenderScope.arena(token,()->{state.clear();if(blockState.getRenderShape()==RenderShape.MODEL)PreviewItemModels.appendBlock(state,models,blockState,service.client,token);if(blockEntityPreview!=null)blockEntityPreview.addLayer(state);return null;}));
            PreviewGui.submit(context,token,state,x+6,y+6,size-12,clip);
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
