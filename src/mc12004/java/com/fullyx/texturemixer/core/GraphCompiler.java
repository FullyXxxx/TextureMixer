package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-target dependency closure. Registry values and model type names are deliberately not rewritten. */
public final class GraphCompiler {
    private static volatile boolean separateItemAtlas=true;
    public static void useSeparateItemAtlas(boolean separate){separateItemAtlas=separate;}

    public enum Status { CUSTOM, MIXED_WITH_VANILLA, VANILLA_FALLBACK }
    public record Result(Map<AssetId,byte[]> resources, String namespacePrefix, Status status,
                         Set<AssetId> dependencies, Set<AssetId> fromSource, Map<AssetId,AssetId> equipment) {}
    private final Assets.View view;
    private final String prefix;
    private final Map<AssetId,byte[]> output = new TreeMap<>();
    private final Set<AssetId> visiting = new LinkedHashSet<>(), visited = new LinkedHashSet<>(), own = new LinkedHashSet<>();
    private final Map<AssetId,AssetId> equipment = new LinkedHashMap<>();
    private final Map<String,JsonArray> atlasEntries = new TreeMap<>();
    private final Set<String> atlasKeys = new HashSet<>();
    private final Set<AssetId> generatedTextures = new HashSet<>();
    private final Set<String> paletteAtlases = new HashSet<>();
    private final Set<String> usedColors = new HashSet<>();
    private record AtlasEntry(JsonObject data,boolean source,AssetId file){}
    private record SpriteResource(AssetId actual,boolean generated){}
    private final Map<String,List<AtlasEntry>> atlasDefinitions=new HashMap<>();
    private final Map<AssetId,JsonObject> originalModels=new HashMap<>();
    public GraphCompiler(Assets.View view, String bindingIdentity) {
        this.view = view;
        this.prefix = "texturemixer_" + Assets.hash(bindingIdentity.getBytes(StandardCharsets.UTF_8)).substring(0,16) + "_";
    }
    public AssetId privateId(AssetId id) { return new AssetId(prefix + id.namespace(), id.path()); }
    private boolean previewWithoutTrims, previewItem;
    private boolean legacyItemModels;
    public Result itemPreview(AssetId item, AssetId equipmentAsset, boolean trimmed) throws IOException {
        previewWithoutTrims = !trimmed;
        previewItem = true;
        return item(item, equipmentAsset);
    }
    public Result wornPreview(AssetId equipmentAsset) throws IOException {
        previewWithoutTrims = true;
        equipment(equipmentAsset);
        return finish();
    }
    public Result item(AssetId item, AssetId equipmentAsset) throws IOException {
        AssetId definition = item.file("items", ".json");
        JsonObject json = json(definition, 0);
        rewriteItemNode(json.getAsJsonObject("model"), 0);
        output.put(definition, Assets.jsonBytes(json));
        if(item.namespace().equals("minecraft")&&item.path().equals("filled_map"))specialTextures("map");
        if (equipmentAsset != null) equipment(equipmentAsset);
        return finish();
    }
    /** Compile the models/item root used before the dedicated items directory. */
    public Result legacyItem(AssetId item, AssetId equipmentAsset, boolean forPreview) throws IOException {
        legacyItemModels=true;
        previewItem=forPreview;
        previewWithoutTrims=forPreview;
        publishLegacyRoot(item);
        String renderer=item.path();
        if(renderer.endsWith("_in_hand")){
            renderer=renderer.substring(0,renderer.length()-"_in_hand".length());
            AssetId icon=item.withPath(renderer);
            if(view.vanilla().contains(icon.withPath("item/"+renderer).file("models",".json")))publishLegacyRoot(icon);
        }else if(renderer.equals("trident")||renderer.equals("spyglass")){
            var hand=item.withPath(renderer+"_in_hand");
            if(view.vanilla().contains(hand.withPath("item/"+hand.path()).file("models",".json")))publishLegacyRoot(hand);
        }
        specialTextures(renderer);
        if(renderer.equals("filled_map"))specialTextures("map");
        if(equipmentAsset!=null)equipment(equipmentAsset);
        return finish();
    }

    public Result legacyDirectWear(Collection<AssetId> textures,boolean withTrims)throws IOException{
        for(AssetId file:textures){
            if(!file.path().startsWith("textures/")||!file.path().endsWith(".png"))
                throw new IOException("Expected worn texture path: "+file);
            texture(file.withPath(file.path().substring(9,file.path().length()-4)),null);
        }
        if(withTrims)copyPaletteAtlas("armor_trims");
        return finish();
    }

    private void publishLegacyRoot(AssetId item)throws IOException{
        AssetId root=item.withPath("item/"+item.path());
        AssetId privateRoot=model(root,"blocks",0);
        validateModelReferences(root);
        byte[] rewritten=output.get(privateRoot.file("models",".json"));
        output.put(root.file("models",".json"),rewritten);
    }

    public Result legacyWornPreview(AssetId equipmentAsset)throws IOException{
        legacyItemModels=true;
        return wornPreview(equipmentAsset);
    }
    public Result block(AssetId block) throws IOException {
        return block(block,null);
    }
    public Result block(AssetId block,List<Map<String,String>> states) throws IOException {
        if(Set.of("grass_block","fern","large_fern","short_grass","tall_grass","potted_fern","bush","pink_petals","wildflowers","sugar_cane").contains(block.path()))usedColors.add("grass");
        if(Set.of("oak_leaves","jungle_leaves","acacia_leaves","dark_oak_leaves","vine","mangrove_leaves").contains(block.path()))usedColors.add("foliage");
        if(block.path().equals("leaf_litter"))usedColors.add("dry_foliage");
        AssetId definition = block.file("blockstates", ".json");
        if (view.read(definition) != null) {
            JsonObject json = json(definition, 0);
            if(states!=null&&view.fromSource(definition))json=completeBlockStates(json,view.vanilla().json(definition),states);
            rewriteBlockNode(json, 0);
            output.put(definition, Assets.jsonBytes(json));
        }
        specialTextures(block.path());
        if (FluidPreviewModel.isFluid(block)) {
            texture(AssetId.of("block/" + block.path() + "_still"), "blocks");
            texture(AssetId.of("block/" + block.path() + "_flow"), "blocks");
            if (block.path().equals("water")) texture(AssetId.of("block/water_overlay"), "blocks");
        }
        return finish();
    }
    private static JsonObject completeBlockStates(JsonObject selected,JsonObject vanilla,List<Map<String,String>> states)throws IOException{
        Map<String,JsonElement> applies=new LinkedHashMap<>();Map<String,List<JsonObject>> conditions=new LinkedHashMap<>();
        for(Map<String,String> state:states){
            List<JsonElement> matches=stateApplies(selected,state);
            // A multipart definition owns all states, including states with an intentionally empty mesh.
            if(matches==null)matches=stateApplies(vanilla,state);
            if(matches==null)throw new IOException("No vanilla block model for state: "+state);
            JsonObject when=new JsonObject();state.forEach(when::addProperty);
            // Multipart is a multiset: two equal parts in one state must remain two draws.
            // Group each occurrence separately so a duplicate cannot make a conditional part unconditional.
            Map<String,Integer> occurrences=new HashMap<>();
            for(JsonElement apply:matches){String value=apply.toString();int rank=occurrences.merge(value,1,Integer::sum);
                String key=value+"|"+rank;applies.putIfAbsent(key,apply);conditions.computeIfAbsent(key,k->new ArrayList<>()).add(when);}
        }
        JsonArray parts=new JsonArray();
        for(var entry:applies.entrySet()){
            JsonObject part=new JsonObject();part.add("apply",entry.getValue());List<JsonObject> when=conditions.get(entry.getKey());
            if(when.size()!=states.size()){
                if(when.size()==1)part.add("when",when.get(0));
                else{JsonObject union=new JsonObject();JsonArray values=new JsonArray();when.forEach(values::add);union.add("OR",values);part.add("when",union);}
            }
            parts.add(part);
        }
        JsonObject completed=new JsonObject();completed.add("multipart",parts);return completed;
    }
    private static List<JsonElement> stateApplies(JsonObject definition,Map<String,String> state)throws IOException{
        List<JsonElement> result=new ArrayList<>();
        if(definition.has("multipart")){
            for(var e:definition.getAsJsonArray("multipart")){
                JsonObject part=e.getAsJsonObject();if(!part.has("when")||matchesCondition(part.get("when"),state))result.add(part.get("apply"));
            }return result;
        }
        if(definition.has("variants"))for(var entry:definition.getAsJsonObject("variants").entrySet()){
            boolean match=true;
            if(!entry.getKey().isEmpty())for(String condition:entry.getKey().split(",")){
                String[] pair=condition.split("=",2);if(pair.length!=2||!state.containsKey(pair[0]))throw new IOException("Invalid block state selector: "+entry.getKey());
                if(!propertyMatch(state.get(pair[0]),pair[1])){match=false;break;}
            }
            if(match){if(!result.isEmpty())throw new IOException("Overlapping block variants: "+state);result.add(entry.getValue());}
        }
        return result.isEmpty()?null:result;
    }
    private static boolean matchesCondition(JsonElement element,Map<String,String> state)throws IOException{
        JsonObject object=element.getAsJsonObject();
        if(object.has("OR")){for(var e:object.getAsJsonArray("OR"))if(matchesCondition(e,state))return true;return false;}
        if(object.has("AND")){for(var e:object.getAsJsonArray("AND"))if(!matchesCondition(e,state))return false;return true;}
        for(var entry:object.entrySet()){
            if(!state.containsKey(entry.getKey()))throw new IOException("Unknown block state property: "+entry.getKey());
            if(!propertyMatch(state.get(entry.getKey()),entry.getValue().getAsString()))return false;
        }return true;
    }
    private static boolean propertyMatch(String actual,String expected){boolean inverse=expected.startsWith("!");boolean match=Arrays.asList((inverse?expected.substring(1):expected).split("\\|")).contains(actual);return inverse?!match:match;}
    private Result finish() throws IOException {
        for(String color:usedColors){
            AssetId file=AssetId.of("textures/colormap/"+color+".png");byte[] bytes=view.read(file);
            if(bytes!=null)output.put(privateId(file),data(file));
        }
        atlasEntries.forEach((atlas, entries) -> {
            JsonObject json = new JsonObject(); json.add("sources", entries);
            output.put(AssetId.of("atlases/" + atlas + ".json"), Assets.jsonBytes(json));
        });
        Status status = own.isEmpty() ? Status.VANILLA_FALLBACK : own.size() == visited.size() ? Status.CUSTOM : Status.MIXED_WITH_VANILLA;
        return new Result(Collections.unmodifiableMap(output), prefix, status, Set.copyOf(visited), Set.copyOf(own), Map.copyOf(equipment));
    }
    private byte[] data(AssetId id) throws IOException {
        byte[] bytes = view.read(id);
        if (bytes == null) throw new IOException("Missing dependency: " + id + " via " + visiting);
        visited.add(id); if (view.fromSource(id)) own.add(id);
        return bytes;
    }
    private JsonObject json(AssetId id, int depth) throws IOException {
        if (depth > 64) throw new IOException("Model depth exceeds 64: " + visiting);
        try { return JsonParser.parseString(new String(data(id), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid JSON " + id + ": " + e.getMessage(), e); }
    }
    private void rewriteBlockNode(JsonElement element, int depth) throws IOException {
        if (element.isJsonArray()) { for (var child : element.getAsJsonArray()) rewriteBlockNode(child, depth+1); }
        else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (var entry : new ArrayList<>(object.entrySet())) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()){
                    AssetId root=AssetId.of(entry.getValue().getAsString());object.addProperty("model", model(root, "blocks", depth+1).toString());validateModelReferences(root);
                }
                else if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) rewriteBlockNode(entry.getValue(), depth+1);
            }
        }
    }
    private void rewriteItemNode(JsonObject object, int depth) throws IOException {
        if (object == null) throw new IOException("Item model node is missing");
        if (depth > 64) throw new IOException("Item model depth exceeds 64");
        String type = object.has("type") ? object.get("type").getAsString() : "";
        if (previewWithoutTrims && type.equals("minecraft:select") && object.has("property")
                && object.get("property").getAsString().equals("minecraft:trim_material")) {
            JsonObject fallback = object.has("fallback") ? object.getAsJsonObject("fallback").deepCopy() : new JsonObject();
            if (!fallback.has("type")) fallback.addProperty("type", "minecraft:empty");
            object.keySet().clear();
            fallback.entrySet().forEach(entry -> object.add(entry.getKey(), entry.getValue()));
            rewriteItemNode(object, depth + 1);
            return;
        }
        if (!type.startsWith("minecraft:")) throw new IOException("Unsupported item model type: " + type);
        if(!isModel(object))throw new IOException("Unsupported item model type: "+type);
        if(object.has("tints"))for(var tint:object.getAsJsonArray("tints"))
            if(tint.getAsJsonObject().get("type").getAsString().equals("minecraft:grass"))usedColors.add("grass");
        if (type.equals("minecraft:model")) {
            AssetId id = AssetId.of(object.get("model").getAsString());
            String atlas = id.path().startsWith("block/") ? "blocks" : "items";
            object.addProperty("model", model(id, atlas, 0).toString());
            validateModelReferences(id);
        } else if (type.equals("minecraft:special")) {
            AssetId base = AssetId.of(object.get("base").getAsString());
            object.addProperty("base", model(base, "items", 0).toString());
            validateModelReferences(base);
            JsonObject renderer = object.getAsJsonObject("model");
            specialTextures(renderer.get("type").getAsString().replace("minecraft:", ""));
            // Special renderers use their native resource naming; a scoped SpriteHolder/texture binding
            // resolves these identifiers to the private copies, rather than changing registry semantics.
        }
        // Follow semantic child slots, rather than looking for known type strings anywhere:
        // unknown types in a model slot must fail, while tint/property codecs are not model nodes.
        switch(type) {
            case "minecraft:condition" -> { childModel(object,"on_true",true,depth); childModel(object,"on_false",true,depth); }
            case "minecraft:composite" -> {
                if(!object.has("models")||!object.get("models").isJsonArray())throw new IOException("Composite models are missing");
                for(var child:object.getAsJsonArray("models"))rewriteItemNode(child.getAsJsonObject(),depth+1);
            }
            case "minecraft:select", "minecraft:range_dispatch" -> {
                childModel(object,"fallback",false,depth);
                String key=type.equals("minecraft:select")?"cases":"entries";
                if(!object.has(key)||!object.get(key).isJsonArray())throw new IOException("Item model "+key+" are missing");
                for(var child:object.getAsJsonArray(key))childModel(child.getAsJsonObject(),"model",true,depth);
            }
            default -> {}
        }
    }
    private static boolean isModel(JsonObject object) {
        String type = object.get("type").getAsString();
        return Set.of("minecraft:model","minecraft:special","minecraft:condition","minecraft:select",
                "minecraft:range_dispatch","minecraft:composite","minecraft:empty","minecraft:bundle/selected_item").contains(type);
    }
    private void childModel(JsonObject object,String key,boolean required,int depth)throws IOException {
        if(!object.has(key)){if(required)throw new IOException("Missing child item model: "+key);return;}
        if(!object.get(key).isJsonObject())throw new IOException("Invalid child item model: "+key);
        rewriteItemNode(object.getAsJsonObject(key),depth+1);
    }
    private AssetId model(AssetId id, String atlas, int depth) throws IOException {
        if (id.path().startsWith("builtin/")) return id;
        AssetId file = id.file("models", ".json"), result=privateId(id);
        if (visiting.contains(file)) throw new IOException("Cyclic model dependency: " + visiting + " -> " + file);
        if (output.containsKey(privateId(file))) return result;
        JsonObject json;
        visiting.add(file);
        try {
            json = json(file,depth);
            originalModels.put(id,json.deepCopy());
            if(!legacyItemModels&&json.has("overrides")&&!json.getAsJsonArray("overrides").isEmpty())
                throw new IOException(tr("不支持旧版物品 overrides；请使用 1.21.11 模型格式: ")+file);
            if (json.has("parent")) json.addProperty("parent",model(AssetId.of(json.get("parent").getAsString()),atlas,depth+1).toString());
            JsonObject textures=json.has("textures")?json.getAsJsonObject("textures"):null;
            if (textures!=null) for (var entry:new ArrayList<>(textures.entrySet())) {
                String value=entry.getValue().getAsString();
                if (!value.startsWith("#")) textures.addProperty(entry.getKey(),texture(AssetId.of(value),atlas).toString());
            }
            output.put(privateId(file), Assets.jsonBytes(json));
        } finally { visiting.remove(file); }
        // Legacy bow and crossbow variants inherit the base whose overrides select them.
        // Resolve their parent against the completed geometry before walking conditional branches.
        if(legacyItemModels&&json.has("overrides")){
            rewriteOverrides(json.getAsJsonArray("overrides"),id,atlas,depth);
            output.put(privateId(file), Assets.jsonBytes(json));
        }
        return result;
    }
    private void rewriteOverrides(JsonArray overrides,AssetId current,String atlas,int depth)throws IOException{
        for(JsonElement entry:overrides){
            JsonObject override=entry.getAsJsonObject();
            if(!override.has("predicate")||!override.get("predicate").isJsonObject()||!override.has("model"))
                throw new IOException("Invalid item override in "+current);
            AssetId target=AssetId.of(override.get("model").getAsString());
            // Keep native self-overrides without revisiting the model under construction.
            AssetId selected=target.equals(current)?privateId(current):model(target,atlas,depth+1);
            if(!target.equals(current))validateModelReferences(target);
            override.addProperty("model",selected.toString());
        }
    }
    private void validateModelReferences(AssetId root)throws IOException {
        Map<String,String> textures=new HashMap<>();JsonArray elements=null;boolean generated=false;AssetId cursor=root;
        for(int depth=0;depth<65;depth++){
            if(cursor.path().startsWith("builtin/")){generated=cursor.path().equals("builtin/generated");break;}
            JsonObject model=originalModels.get(cursor);if(model==null)throw new IOException("Missing model dependency: "+cursor);
            if(elements==null&&model.has("elements"))elements=model.getAsJsonArray("elements");
            if(model.has("textures"))for(var binding:model.getAsJsonObject("textures").entrySet())textures.putIfAbsent(binding.getKey(),binding.getValue().getAsString());
            if(!model.has("parent"))break;cursor=AssetId.of(model.get("parent").getAsString());
        }
        if(elements!=null)for(var element:elements)for(var face:element.getAsJsonObject().getAsJsonObject("faces").entrySet()){
            String reference=face.getValue().getAsJsonObject().get("texture").getAsString();
            if(reference.startsWith("#"))resolveBinding(root,reference,textures);
        }
        if(generated)for(int layer=0;layer<5;layer++)if(textures.containsKey("layer"+layer))resolveBinding(root,"#layer"+layer,textures);
    }
    private static void resolveBinding(AssetId root,String reference,Map<String,String> textures)throws IOException {
        Set<String> chain=new LinkedHashSet<>();
        while(reference.startsWith("#")){
            if(!chain.add(reference))throw new IOException("Cyclic texture binding in "+root+": "+chain);
            String resolved=textures.get(reference.substring(1));if(resolved==null)throw new IOException("Undefined texture binding "+reference+" in "+root);reference=resolved;
        }
    }
    public AssetId texture(AssetId id,String atlas) throws IOException {
        if(!separateItemAtlas&&"items".equals(atlas))atlas="blocks";
        if (id.equals(AssetId.of("missingno"))) return id;
        AssetId result=privateId(id);SpriteResource resolved=atlas==null?new SpriteResource(id,false):resolveSprite(id,atlas);
        if(resolved.generated){copyPaletteAtlas(atlas);generatedTextures.add(id);return result;}
        AssetId resource=resolved.actual,file=resource.file("textures",".png");
        if (generatedTextures.contains(id)) return result;
        if (view.read(file)==null && atlas!=null && paletteTexture(id,atlas)) return result;
        if (!output.containsKey(privateId(file))) {
            byte[] bytes=data(file); DirectorySource.validatePng(file,bytes); output.put(privateId(file),bytes);
            AssetId metadata=file.withPath(file.path()+".mcmeta");
            boolean hasMetadata = FluidPreviewModel.isFluidSprite(id)
                    ? view.hasMetadataForImage(file, metadata) : view.read(metadata) != null;
            if (hasMetadata) { JsonObject json=json(metadata,0);TextureMetadata.validate(file,bytes,json);output.put(privateId(metadata),Assets.jsonBytes(json)); }
        }
        if (atlas!=null && atlasKeys.add(atlas+"|"+result)) {
            JsonObject single=new JsonObject();single.addProperty("type","minecraft:single");single.addProperty("resource",privateId(resource).toString());
            if(!resource.equals(id))single.addProperty("sprite",result.toString());
            atlasEntries.computeIfAbsent(atlas,k->new JsonArray()).add(single);
        }
        return result;
    }
    private void equipment(AssetId asset) throws IOException {
        AssetId file=asset.file(legacyItemModels?"models/equipment":"equipment",".json");
        JsonObject json=json(file,0);
        for (var entry:json.getAsJsonObject("layers").entrySet()) for (var e:entry.getValue().getAsJsonArray()) {
            JsonObject layer=e.getAsJsonObject();AssetId original=AssetId.of(layer.get("texture").getAsString());
            texture(original.withPath("entity/equipment/"+entry.getKey()+"/"+original.path()),null);
            layer.addProperty("texture",privateId(original).toString());
        }
        output.put(privateId(file),Assets.jsonBytes(json));equipment.put(asset,privateId(asset));
        // Both item and worn trims are palette-generated. Clone their atlas sources in the target namespace.
        if (!previewWithoutTrims) { copyPaletteAtlas("items"); copyPaletteAtlas("armor_trims"); }
    }
    private void copyPaletteAtlas(String atlas) throws IOException {
        if (!paletteAtlases.add(atlas)) return;
        for (var source:atlasSources(atlas)) {
            JsonObject object=source.data;
            if (!object.get("type").getAsString().equals("minecraft:paletted_permutations")) continue;
            object=object.deepCopy();
            object.addProperty("palette_key",texture(AssetId.of(object.get("palette_key").getAsString()),null).toString());
            JsonArray textures=object.getAsJsonArray("textures");
            for(int i=0;i<textures.size();i++) textures.set(i,new JsonPrimitive(texture(AssetId.of(textures.get(i).getAsString()),null).toString()));
            JsonObject permutations=object.getAsJsonObject("permutations");
            for(var entry:new ArrayList<>(permutations.entrySet())) permutations.addProperty(entry.getKey(),texture(AssetId.of(entry.getValue().getAsString()),null).toString());
            atlasEntries.computeIfAbsent(atlas,k->new JsonArray()).add(object);
        }
    }
    private boolean paletteTexture(AssetId id,String atlas) throws IOException {
        for (var e:atlasSources(atlas)) {
            JsonObject source=e.data;
            if(!source.get("type").getAsString().equals("minecraft:paletted_permutations")) continue;
            for(var texture:source.getAsJsonArray("textures")) for(var entry:source.getAsJsonObject("permutations").entrySet()) {
                AssetId base=AssetId.of(texture.getAsString());
                AssetId generated=base.withPath(base.path()+"_"+entry.getKey());
                if(generated.equals(id)) { copyPaletteAtlas(atlas); generatedTextures.add(generated); return true; }
            }
        }
        return false;
    }
    private List<AtlasEntry> atlasSources(String atlas)throws IOException{
        List<AtlasEntry> cached=atlasDefinitions.get(atlas);if(cached!=null)return cached;
        AssetId file=AssetId.of("atlases/"+atlas+".json");List<AtlasEntry> result=new ArrayList<>();
        List<Assets> layers=new ArrayList<>();layers.add(view.vanilla());
        if(view.source() instanceof Assets.Layered layered)layers.addAll(layered.lowestFirst());
        else if(view.source()!=null)layers.add(view.source());
        for(int i=0;i<layers.size();i++){byte[] bytes=layers.get(i).read(file);if(bytes==null)continue;
            try{JsonObject data=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray sources=data.getAsJsonArray("sources");if(sources!=null)for(var source:sources)result.add(new AtlasEntry(source.getAsJsonObject(),i>0,file));
            }catch(RuntimeException error){throw new IOException("Invalid atlas: "+file,error);}}
        atlasDefinitions.put(atlas,List.copyOf(result));return result;
    }
    private SpriteResource resolveSprite(AssetId sprite,String atlas)throws IOException{
        List<AtlasEntry> sources=atlasSources(atlas);
        for(int i=sources.size()-1;i>=0;i--){AtlasEntry entry=sources.get(i);JsonObject source=entry.data;
            String type=source.get("type").getAsString().replace("minecraft:","");AssetId actual=null;
            if(type.equals("single")){
                AssetId resource=AssetId.of(source.get("resource").getAsString());
                AssetId name=source.has("sprite")?AssetId.of(source.get("sprite").getAsString()):resource;
                if(name.equals(sprite))actual=resource;
            }else if(type.equals("directory")){
                String prefix=source.get("prefix").getAsString();
                if(sprite.path().startsWith(prefix)){
                    AssetId candidate=new AssetId(sprite.namespace(),source.get("source").getAsString()+"/"+sprite.path().substring(prefix.length()));
                    if(view.read(candidate.file("textures",".png"))!=null)actual=candidate;
                }
            }else if(type.equals("paletted_permutations")){
                for(var texture:source.getAsJsonArray("textures"))for(var permutation:source.getAsJsonObject("permutations").entrySet()){
                    AssetId base=AssetId.of(texture.getAsString());
                    if(base.withPath(base.path()+"_"+permutation.getKey()).equals(sprite)){
                        visited.add(entry.file);if(entry.source)own.add(entry.file);return new SpriteResource(sprite,true);
                    }
                }
            }else if(type.equals("filter")&&source.has("pattern")){
                JsonObject pattern=source.getAsJsonObject("pattern");String ns=pattern.has("namespace")?pattern.get("namespace").getAsString():".*",path=pattern.has("path")?pattern.get("path").getAsString():".*";
                if(sprite.namespace().matches(ns)&&sprite.path().matches(path))throw new IOException("Unsupported atlas filter for sprite: "+sprite);
            }
            if(actual!=null){visited.add(entry.file);if(entry.source)own.add(entry.file);return new SpriteResource(actual,false);}
        }
        return new SpriteResource(sprite,false);
    }
    private void specialTextures(String type) throws IOException {
        String hint=type;
        if(type.endsWith("_bed")) hint="bed";
        if(type.endsWith("_banner") || type.endsWith("_wall_banner")) hint="banner";
        if(type.endsWith("shulker_box")) hint="shulker";
        if(type.endsWith("head")||type.endsWith("skull")) hint="head";
        if(type.endsWith("copper_golem_statue")) hint="copper_golem_statue";
        if(type.contains("sign")) hint="sign";
        Set<String> prefixes=switch(hint) {
            case "chest","trapped_chest","ender_chest" -> Set.of("entity/chest/");
            case "bed" -> Set.of("entity/bed/");
            case "banner" -> Set.of("entity/banner/","entity/banner_base");
            case "shield" -> previewItem ? Set.of("entity/shield_base") : Set.of("entity/shield/","entity/shield_base");
            case "shulker","shulker_box" -> Set.of("entity/shulker/");
            case "sign" -> Set.of("entity/signs/");
            case "decorated_pot" -> Set.of("entity/decorated_pot/");
            case "conduit" -> Set.of("entity/conduit/");
            case "trident" -> Set.of("entity/trident");
            case "map" -> Set.of("map/");
            case "copper_golem_statue" -> Set.of("entity/copper_golem/");
            case "head","player_head" -> Set.of("entity/skeleton/","entity/zombie/","entity/creeper/","entity/enderdragon/","entity/piglin/");
            default -> Set.of();
        };
        if(prefixes.isEmpty())return;
        for(AssetId file:view.ids()) if(file.path().startsWith("textures/") && file.path().endsWith(".png")) {
            String path=file.path().substring(9,file.path().length()-4);
            if(prefixes.stream().anyMatch(path::startsWith)) {
                if(hint.equals("map")&&path.startsWith("map/decorations/")){texture(new AssetId(file.namespace(),path.substring("map/decorations/".length())),"map_decorations");continue;}
                String atlas=path.startsWith("entity/chest/")?"chests":path.startsWith("entity/bed/")?"beds"
                        :path.startsWith("entity/banner")?"banner_patterns":path.startsWith("entity/shield")?"shield_patterns"
                        :path.startsWith("entity/shulker/")?"shulker_boxes":path.startsWith("entity/signs/")?"signs"
                        :path.startsWith("entity/decorated_pot/")?"decorated_pot":null;
                texture(new AssetId(file.namespace(),path),atlas);
            }
        }
    }
}
