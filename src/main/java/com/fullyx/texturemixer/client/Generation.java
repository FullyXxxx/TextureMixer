package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.sky.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Disk snapshots are content addressed, so removing a source cannot invalidate an active generation. */
public record Generation(String id,Assets assets,Map<String,String> itemPrefixes,Map<String,String> blockPrefixes,
                         Map<String,String> equipment,Map<String,SkyScene> skies) {
    public static final long MAX_SNAPSHOT_BYTES=512L*1024*1024;
    /** Older snapshots included implicit vanilla Sky scenes; let unselected server skies render again. */
    public Generation withSkySelections(MixerConfig config) {
        Map<String,SkyScene> selected=new TreeMap<>();
        skies.forEach((dimension,scene)->{
            if(scene.fromSource()||config.skyOverrides.containsKey(dimension))selected.put(dimension,scene);
        });
        return new Generation(id,assets,itemPrefixes,blockPrefixes,equipment,Map.copyOf(selected));
    }
    public static Generation compile(MixerConfig config,Assets vanilla,Map<String,PackSource> sources,Catalog catalog)throws IOException{
        config.validate();Set<String> selected=new HashSet<>();selected.addAll(config.mainStack());
        for(var kind:MixerConfig.Kind.values())selected.addAll(config.overrides(kind).values());
        for(String id:selected)if(!id.equals(MixerConfig.VANILLA)&&!require(sources,id).unchanged())throw new IOException(tr("来源在索引后发生变化，请刷新: ")+require(sources,id).name());
        Map<AssetId,byte[]> output=new TreeMap<>();
        for(String mainId : config.mainStack().reversed()){
            PackSource main=require(sources,mainId);
            if(main.indexedBytes()>MAX_SNAPSHOT_BYTES)throw new IOException(tr("主包超过首版 512 MiB 快照预算；未修改已应用资源"));
            merge(output,main.snapshot().files());
            if(output.values().stream().mapToLong(bytes -> bytes.length).sum()>MAX_SNAPSHOT_BYTES)
                throw new IOException(tr("主材质包叠加超过 512 MiB 快照预算"));
        }
        Map<String,String> items=new TreeMap<>(),blocks=new TreeMap<>(),equipment=new TreeMap<>();
        for(var entry:config.itemOverrides.entrySet()){
            Catalog.Target target=catalog.item(entry.getKey());if(target==null)throw new IOException("Unknown item: "+entry.getKey());
            Assets.View view=view(entry.getValue(),sources,vanilla);
            UnsupportedResources.check(view.source(),MixerConfig.Kind.ITEM,AssetId.of(entry.getKey()));
            var compiler=new GraphCompiler(view,entry.getValue()+"|item|"+entry.getKey());
            GraphCompiler.Result graph=NativePackModels.compile(compiler,target);
            merge(output,graph.resources());items.put(entry.getKey(),graph.namespacePrefix());
            graph.equipment().forEach((from,to)->equipment.put(entry.getKey()+"|"+from,to.toString()));
        }
        for(var entry:config.blockOverrides.entrySet()){
            Catalog.Target target=catalog.block(entry.getKey());if(target==null)throw new IOException("Unknown block: "+entry.getKey());
            var view=view(entry.getValue(),sources,vanilla);UnsupportedResources.check(view.source(),MixerConfig.Kind.BLOCK,AssetId.of(entry.getKey()));
            GraphCompiler.Result graph=new GraphCompiler(view,entry.getValue()+"|block|"+entry.getKey()).block(target.model(),target.states());
            merge(output,graph.resources());blocks.put(entry.getKey(),graph.namespacePrefix());
        }
        for(var kind:MixerConfig.Kind.values()) {
            if(kind==MixerConfig.Kind.ITEM||kind==MixerConfig.Kind.BLOCK||kind==MixerConfig.Kind.SKY)continue;
            for(var selection:config.overrides(kind).entrySet()) {
                if(catalog.list(kind).stream().noneMatch(target->target.id().equals(selection.getKey())))
                    throw new IOException("Unknown "+kind+" resource: "+selection.getKey());
                var result=new ResourceCompiler(view(selection.getValue(),sources,vanilla),selection.getValue()+"|"+kind+"|"+selection.getKey())
                        .compile(kind,selection.getKey());
                if(kind==MixerConfig.Kind.FONT) {
                    FontPreview.validate(result.files(),AssetId.of(selection.getKey()));
                }
                merge(output,result.files());
                if(output.values().stream().mapToLong(bytes->bytes.length).sum()>MAX_SNAPSHOT_BYTES)
                    throw new IOException(tr("混搭产物超过 512 MiB 快照预算"));
            }
        }
        // Weather uses native precipitation textures. Its selection is independent of either sky dimension.
        if(config.skyOverrides.containsKey("weather")) {
            var result=new ResourceCompiler(view(config.skyOverrides.get("weather"),sources,vanilla),"weather")
                    .compile(MixerConfig.Kind.SKY,"weather");
            merge(output,result.files());
        }
        Map<String,SkyScene> skies=new TreeMap<>();
        for(String dimension:List.of("overworld","end")){
            SkyScene scene=new OptifineSkyAdapter().parse(dimension,view(config.source(MixerConfig.Kind.SKY,dimension),sources,vanilla));
            if(!scene.fromSource()&&!config.skyOverrides.containsKey(dimension))continue;
            // The selected scene is rendered by our engine. Prevent another OptiFine sky loader
            // from drawing the main pack's definitions over the selected private sky layers.
            String skyFolder="optifine/sky/world"+(dimension.equals("end")?"1":"0")+"/";
            output.keySet().removeIf(id->id.namespace().equals("minecraft")&&id.path().startsWith(skyFolder)&&id.path().endsWith(".properties"));
            skies.put(dimension,scene);String prefix=SkyScene.namespacePrefix(dimension);JsonArray sprites=new JsonArray();
            for(var texture:scene.textures().entrySet()){
                AssetId original=texture.getKey(),privateId=new AssetId(prefix+original.namespace(),original.path());output.put(privateId,texture.getValue());
                String celestial="textures/environment/celestial/";
                if(original.path().startsWith(celestial)&&original.path().endsWith(".png")){
                    String name=original.path().substring(celestial.length(),original.path().length()-4);
                    JsonObject single=new JsonObject();single.addProperty("type","minecraft:single");
                    single.addProperty("resource",privateId.withPath(privateId.path().substring(9,privateId.path().length()-4)).toString());
                    single.addProperty("sprite",new AssetId(privateId.namespace(),name).toString());sprites.add(single);
                }
            }
            JsonObject atlas=new JsonObject();atlas.add("sources",sprites);merge(output,Map.of(AssetId.of("atlases/celestials.json"),Assets.jsonBytes(atlas)));
        }
        if(output.values().stream().mapToLong(bytes->bytes.length).sum()>MAX_SNAPSHOT_BYTES)throw new IOException(tr("混搭产物超过首版 512 MiB 快照预算；未提交"));
        NativeAssetValidator.validate(output);
        for(String id:selected)if(!id.equals(MixerConfig.VANILLA)&&!require(sources,id).unchanged())throw new IOException(tr("来源在编译期间发生变化，请刷新: ")+require(sources,id).name());
        Generation generation=new Generation(UUID.randomUUID().toString(),new Assets.Memory(output),Map.copyOf(items),Map.copyOf(blocks),Map.copyOf(equipment),Map.copyOf(skies));
        ColorBindings.stage(generation);return generation;
    }
    private static PackSource require(Map<String,PackSource> sources,String id)throws IOException{
        PackSource source=sources.get(id);if(source==null)throw new IOException(tr("来源失联或仍未读取完成: ")+id);return source;
    }
    public static Assets.View view(String id,Map<String,PackSource> sources,Assets vanilla)throws IOException{
        if (id.startsWith(MixerConfig.STACK_PREFIX)) {
            List<Assets> layers = new ArrayList<>();
            for (String source : id.substring(MixerConfig.STACK_PREFIX.length()).split(",")) layers.add(require(sources, source));
            return new Assets.View(new Assets.Layered(layers), vanilla);
        }
        return new Assets.View(id.equals(MixerConfig.VANILLA)?null:require(sources,id),vanilla);
    }
    public static void merge(Map<AssetId,byte[]> destination,Map<AssetId,byte[]> addition){
        addition.forEach((id,bytes)->{
            if(id.path().startsWith("atlases/")&&destination.containsKey(id)){
                JsonObject current=JsonParser.parseString(new String(destination.get(id),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject added=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray all=current.getAsJsonArray("sources");if(all==null){all=new JsonArray();current.add("sources",all);}all.addAll(added.getAsJsonArray("sources"));
                destination.put(id,Assets.jsonBytes(current));
            }else if(id.path().equals("sounds.json")&&destination.containsKey(id)){
                JsonObject current=JsonParser.parseString(new String(destination.get(id),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject added=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                added.entrySet().forEach(entry->current.add(entry.getKey(),ResourceCompiler.combineSoundEvent(
                        current.has(entry.getKey())?current.getAsJsonObject(entry.getKey()):null,entry.getValue().getAsJsonObject())));
                destination.put(id,Assets.jsonBytes(current));
            }else destination.put(id,bytes);
        });
    }
    public void write(Path cache)throws IOException{
        JsonObject manifest=new JsonObject();
        manifest.addProperty("id",id);
        manifest.addProperty("minecraft",NativeCompat.gameVersionName());
        JsonObject files=new JsonObject();
        for(AssetId asset:assets.ids()){byte[] bytes=assets.read(asset);String hash=Assets.hash(bytes);store(cache,hash,bytes);files.addProperty(asset.toString(),hash);}
        manifest.add("files",files);manifest.add("itemPrefixes",MixerConfig.GSON.toJsonTree(itemPrefixes));
        manifest.add("blockPrefixes",MixerConfig.GSON.toJsonTree(blockPrefixes));manifest.add("equipment",MixerConfig.GSON.toJsonTree(equipment));
        JsonObject skyJson=new JsonObject();
        for(var entry:skies.entrySet()){
            SkyScene scene=entry.getValue();JsonObject data=new JsonObject();data.add("layers",MixerConfig.GSON.toJsonTree(scene.layers()));
            data.addProperty("fromSource",scene.fromSource());data.add("diagnostics",MixerConfig.GSON.toJsonTree(scene.diagnostics()));
            data.add("celestialBlends",MixerConfig.GSON.toJsonTree(scene.celestialBlends()));
            JsonObject textureHashes=new JsonObject();for(var texture:scene.textures().entrySet()){
                String hash=Assets.hash(texture.getValue());store(cache,hash,texture.getValue());textureHashes.addProperty(texture.getKey().toString(),hash);}
            data.add("textures",textureHashes);skyJson.add(entry.getKey(),data);
        }
        manifest.add("skies",skyJson);ConfigStore.atomic(cache.resolve("generations/"+id+".json"),Assets.jsonBytes(manifest));
    }
    private static void store(Path cache,String hash,byte[] data)throws IOException{
        Path file=cache.resolve("objects/"+hash);if(!Files.exists(file)||!Assets.hash(Files.readAllBytes(file)).equals(hash))ConfigStore.atomic(file,data);
    }
    public static Generation read(Path cache,String id)throws IOException{
        try{UUID.fromString(id);}catch(IllegalArgumentException error){throw new IOException("Invalid generation identity",error);}
        JsonObject manifest=JsonParser.parseString(Files.readString(cache.resolve("generations/"+id+".json"))).getAsJsonObject();
        if(!manifest.get("id").getAsString().equals(id))throw new IOException("Snapshot identity mismatch");
        String version=manifest.has("minecraft")?manifest.get("minecraft").getAsString():"1.21.11";
        if(!version.equals(NativeCompat.gameVersionName()))
            throw new IOException(tr("快照由其他 Minecraft 版本生成，请重新应用现有配置"));
        Map<AssetId,String> hashes=new TreeMap<>();manifest.getAsJsonObject("files").entrySet().forEach(e->hashes.put(AssetId.of(e.getKey()),e.getValue().getAsString()));
        Map<AssetId,byte[]> restored=new TreeMap<>();long size=0;
        for(var asset:hashes.entrySet()){byte[] bytes=verified(cache,asset.getValue());size+=bytes.length;if(size>MAX_SNAPSHOT_BYTES)throw new IOException("Snapshot exceeds memory budget");restored.put(asset.getKey(),bytes);}
        NativeAssetValidator.validate(restored);Assets disk=new Assets.Memory(restored);
        Map<String,SkyScene> skies=new TreeMap<>();
        for(var e:manifest.getAsJsonObject("skies").entrySet()){
            JsonObject data=e.getValue().getAsJsonObject();Map<AssetId,byte[]> textures=new TreeMap<>();
            for(var t:data.getAsJsonObject("textures").entrySet())textures.put(AssetId.of(t.getKey()),verified(cache,t.getValue().getAsString()));
            var layers=Arrays.asList(MixerConfig.GSON.fromJson(data.get("layers"),SkyScene.Layer[].class));
            var notes=Arrays.asList(MixerConfig.GSON.fromJson(data.get("diagnostics"),String[].class));
            Map<String,SkyScene.Blend> blends=new HashMap<>();if(data.has("celestialBlends"))data.getAsJsonObject("celestialBlends").entrySet().forEach(b->blends.put(b.getKey(),SkyScene.Blend.valueOf(b.getValue().getAsString())));
            skies.put(e.getKey(),new SkyScene(e.getKey(),layers,textures,data.get("fromSource").getAsBoolean(),notes,blends));
        }
        Generation generation=new Generation(id,disk,stringMap(manifest,"itemPrefixes"),stringMap(manifest,"blockPrefixes"),stringMap(manifest,"equipment"),Map.copyOf(skies));
        ColorBindings.stage(generation);return generation;
    }
    private static byte[] verified(Path cache,String hash)throws IOException{
        if(!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid cache object hash");
        byte[] bytes=Files.readAllBytes(cache.resolve("objects/"+hash));if(!Assets.hash(bytes).equals(hash))throw new IOException("Corrupt snapshot object: "+hash);return bytes;
    }
    private static Map<String,String> stringMap(JsonObject json,String key){Map<String,String> map=new TreeMap<>();json.getAsJsonObject(key).entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));return Map.copyOf(map);}
}
