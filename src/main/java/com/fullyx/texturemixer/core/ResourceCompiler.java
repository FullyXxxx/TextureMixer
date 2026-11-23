package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native resource closures for texture sheets, particle definitions, precipitation and sound events. */
public final class ResourceCompiler {
    public static final String FIRE_OVERLAY_TARGET="minecraft:textures/block/fire_1.png";
    public static final AssetId FIRE_OVERLAY_TEXTURE=AssetId.of("texturemixer:textures/overlay/fire_1.png");
    public static final AssetId FIRE_OVERLAY_SPRITE=AssetId.of("texturemixer:fire_overlay");
    public record Result(Map<AssetId,byte[]> files, List<AssetId> images, List<AssetId> sounds, boolean custom) {}
    private final Assets.View view;
    private final String prefix;
    private final Map<AssetId,byte[]> files = new TreeMap<>();
    private final List<AssetId> images = new ArrayList<>(), sounds = new ArrayList<>();
    private final Map<AssetId,JsonObject> soundManifests = new TreeMap<>();
    private final Set<AssetId> visiting = new HashSet<>(), visited = new HashSet<>();
    private boolean custom;
    private long retainedBytes;

    public ResourceCompiler(Assets.View view, String identity) {
        this.view = view;
        prefix = "texturemixer_" + Assets.hash(identity.getBytes(StandardCharsets.UTF_8)).substring(0,16) + "_";
    }
    public Result compile(MixerConfig.Kind kind, String target) throws IOException {
        switch (kind) {
            case ENTITY, ENCHANT, MENU, CONTAINER -> image(AssetId.of(target), false);
            case FONT -> {
                var font=new FontResources(view,target).compile();
                return new Result(font.files(),List.of(),List.of(),font.custom());
            }
            case HUD -> {
                if(target.equals(FIRE_OVERLAY_TARGET))fireOverlay();else image(AssetId.of(target),false);
            }
            case PARTICLE -> particle(AssetId.of(target));
            case SOUND -> soundEvent(AssetId.of(target), true, 0);
            case SKY -> {
                if (!target.equals("weather")) throw new IOException("Unknown weather group: " + target);
                image(AssetId.of("textures/environment/rain.png"), false);
                image(AssetId.of("textures/environment/snow.png"), false);
            }
            default -> throw new IOException("Unsupported resource category: " + kind);
        }
        for(var manifest:soundManifests.entrySet())store(manifest.getKey(),Assets.jsonBytes(manifest.getValue()));
        return new Result(Map.copyOf(files), List.copyOf(images), List.copyOf(sounds), custom);
    }
    private AssetId privateId(AssetId id) { return new AssetId(prefix + id.namespace(), id.path()); }
    private byte[] read(AssetId id) throws IOException {
        byte[] bytes = view.read(id);
        if (bytes == null) throw new IOException("Missing resource: " + id);
        custom |= view.fromSource(id);
        return bytes;
    }
    private AssetId image(AssetId original, boolean isolate) throws IOException {
        AssetId output = isolate ? privateId(original) : original;
        return image(original,output,isolate);
    }
    private AssetId image(AssetId original,AssetId output,boolean isolate)throws IOException {
        if (files.containsKey(output)) return output;
        byte[] bytes = read(original);
        DirectorySource.validatePng(original, bytes);
        store(output, bytes);
        AssetId metadata = original.withPath(original.path() + ".mcmeta");
        byte[] meta = view.read(metadata);
        if (meta != null) {
            TextureMetadata.validate(original, bytes, view.json(metadata));
            custom |= view.fromSource(metadata);
            store(output.withPath(output.path() + ".mcmeta"), meta);
        } else if (!isolate) store(output.withPath(output.path() + ".mcmeta"), Assets.jsonBytes(new JsonObject()));
        images.add(output);
        return output;
    }
    private void fireOverlay()throws IOException {
        image(AssetId.of(FIRE_OVERLAY_TARGET),FIRE_OVERLAY_TEXTURE,false);
        JsonObject single=new JsonObject();single.addProperty("type","minecraft:single");
        single.addProperty("resource","texturemixer:overlay/fire_1");single.addProperty("sprite",FIRE_OVERLAY_SPRITE.toString());
        JsonArray sources=new JsonArray();sources.add(single);JsonObject atlas=new JsonObject();atlas.add("sources",sources);
        store(AssetId.of("atlases/blocks.json"),Assets.jsonBytes(atlas));
    }
    public static boolean enchantTexture(AssetId id){
        return id.namespace().equals("minecraft")&&Set.of("textures/misc/enchanted_glint_item.png","textures/misc/enchanted_glint_armor.png").contains(id.path());
    }
    private void particle(AssetId id) throws IOException {
        AssetId definition = id.file("particles", ".json");
        JsonObject json = JsonParser.parseString(new String(read(definition), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray original = json.has("textures") ? json.getAsJsonArray("textures") : new JsonArray();
        JsonArray rewritten = new JsonArray(), sprites = new JsonArray();
        for (JsonElement entry : original) {
            AssetId sprite = AssetId.of(entry.getAsString());
            AssetId image = image(sprite.file("textures/particle", ".png"), true);
            AssetId mapped = new AssetId(image.namespace(), sprite.path());
            rewritten.add(mapped.toString());
            JsonObject single = new JsonObject();
            single.addProperty("type", "minecraft:single");
            single.addProperty("resource", new AssetId(image.namespace(), "particle/" + sprite.path()).toString());
            single.addProperty("sprite", mapped.toString());
            sprites.add(single);
        }
        json.add("textures", rewritten);
        store(definition, Assets.jsonBytes(json));
        JsonObject atlas = new JsonObject(); atlas.add("sources", sprites);
        store(AssetId.of("atlases/particles.json"), Assets.jsonBytes(atlas));
    }
    private JsonObject event(AssetId id) throws IOException {
        AssetId manifest = new AssetId(id.namespace(), "sounds.json");
        JsonObject combined = eventIn(view.vanilla(), manifest, id.path());
        List<Assets> layers = view.source() instanceof Assets.Layered layered ? layered.lowestFirst()
                : view.source() == null ? List.of() : List.of(view.source());
        for (Assets layer : layers) {
            JsonObject own = eventIn(layer, manifest, id.path());
            if (own != null) { custom = true; combined = combineSoundEvent(combined, own); }
        }
        if (combined == null) throw new IOException("Missing sound event: " + id);
        return combined;
    }
    public static JsonObject combineSoundEvent(JsonObject lower, JsonObject upper) {
        if (lower == null || upper.has("replace") && upper.get("replace").getAsBoolean()) return upper.deepCopy();
        JsonObject merged = lower.deepCopy();
        JsonArray sounds = lower.has("sounds") ? lower.getAsJsonArray("sounds").deepCopy() : new JsonArray();
        if (upper.has("sounds")) sounds.addAll(upper.getAsJsonArray("sounds"));
        upper.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
        merged.add("sounds", sounds);
        if (lower.has("replace") && lower.get("replace").getAsBoolean()) merged.addProperty("replace", true);
        return merged;
    }
    public record Sample(AssetId file, byte[] data) {}
    public Sample sample(String id) throws IOException { return sample(AssetId.of(id), 0); }
    private Sample sample(AssetId id, int depth) throws IOException {
        if (depth > 64 || !visiting.add(id)) throw new IOException("Cyclic sound event: " + id);
        JsonObject event = event(id);
        for (JsonElement entry : event.has("sounds") ? event.getAsJsonArray("sounds") : new JsonArray()) {
            String name = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("name").getAsString();
            AssetId reference = AssetId.of(name);
            if (entry.isJsonObject() && entry.getAsJsonObject().has("type") && entry.getAsJsonObject().get("type").getAsString().equals("event")) {
                Sample nested = sample(reference, depth + 1);
                if (nested != null) return nested;
            } else {
                AssetId file = reference.file("sounds", ".ogg");
                return new Sample(file, read(file));
            }
        }
        visiting.remove(id);
        return null;
    }
    private static JsonObject eventIn(Assets assets, AssetId manifest, String name) throws IOException {
        byte[] bytes = assets.read(manifest);
        if (bytes == null) return null;
        try {
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.has(name) ? json.getAsJsonObject(name).deepCopy() : null;
        } catch (RuntimeException error) { throw new IOException("Invalid sound manifest: " + manifest, error); }
    }
    private AssetId soundEvent(AssetId id, boolean root, int depth) throws IOException {
        if (depth > 64 || !visiting.add(id)) throw new IOException("Cyclic or excessive sound event references: " + id);
        AssetId mapped = root ? id : privateId(id);
        if (visited.contains(mapped)) { visiting.remove(id); return mapped; }
        JsonObject json = event(id);
        JsonArray rewritten = new JsonArray();
        for (JsonElement entry : json.has("sounds") ? json.getAsJsonArray("sounds") : new JsonArray()) {
            JsonObject sound = entry.isJsonPrimitive() ? new JsonObject() : entry.getAsJsonObject().deepCopy();
            String name = entry.isJsonPrimitive() ? entry.getAsString() : sound.get("name").getAsString();
            AssetId reference = AssetId.of(name);
            boolean nested = sound.has("type") && sound.get("type").getAsString().equals("event");
            if (nested) sound.addProperty("name", soundEvent(reference, false, depth + 1).toString());
            else {
                AssetId original = reference.file("sounds", ".ogg"), file = privateId(original);
                if(!files.containsKey(file)) {
                    byte[] bytes = read(original);
                    if (bytes.length < 4 || bytes[0]!='O' || bytes[1]!='g' || bytes[2]!='g' || bytes[3]!='S')
                        throw new IOException("Invalid Ogg audio: " + original);
                    store(file,bytes);
                }
                if (!sounds.contains(file)) sounds.add(file);
                sound.addProperty("name", privateId(reference).toString());
            }
            rewritten.add(sound);
        }
        json.add("sounds", rewritten);
        json.addProperty("replace", true);
        soundManifests.computeIfAbsent(new AssetId(mapped.namespace(), "sounds.json"), key -> new JsonObject()).add(mapped.path(), json);
        visiting.remove(id); visited.add(mapped);
        return mapped;
    }
    private void store(AssetId id,byte[] bytes)throws IOException {
        byte[] previous=files.get(id);
        long next=retainedBytes+bytes.length-(previous==null?0:previous.length);
        if(next>512L*1024*1024)throw new IOException(tr("资源闭包超过 512 MiB 快照预算: ")+id);
        files.put(id,bytes);retainedBytes=next;
    }
    public static boolean entityTexture(AssetId id) {
        return id.path().startsWith("textures/entity/") && !id.path().startsWith("textures/entity/equipment/") && id.path().endsWith(".png");
    }
    public static boolean hudTexture(AssetId id) {
        return id.path().endsWith(".png") && (id.path().startsWith("textures/gui/sprites/hud/")
                || id.path().startsWith("textures/gui/sprites/boss_bar/") || id.path().startsWith("textures/gui/sprites/mob_effect/")
                || id.path().startsWith("textures/gui/sprites/spectator/") || id.path().equals("textures/misc/pumpkinblur.png")
                || id.path().equals("textures/misc/vignette.png") || id.path().equals("textures/misc/powder_snow_outline.png"));
    }
    public static boolean menuTexture(AssetId id) {
        String path=id.path();
        return path.endsWith(".png")&&(path.startsWith("textures/gui/title/")
                ||path.startsWith("textures/gui/sprites/widget/")||path.startsWith("textures/gui/sprites/transferable_list/")
                ||path.startsWith("textures/gui/sprites/server_list/")||path.startsWith("textures/gui/sprites/world_list/")
                ||path.startsWith("textures/gui/sprites/notification/")||path.startsWith("textures/gui/sprites/popup/")
                ||path.startsWith("textures/gui/sprites/toast/")||path.startsWith("textures/gui/sprites/icon/")
                ||path.startsWith("textures/gui/")&&path.substring("textures/gui/".length()).matches("(?:inworld_)?menu_(?:list_)?background\\.png"));
    }
    public static boolean containerTexture(AssetId id) {
        String path=id.path();
        return path.endsWith(".png")&&(path.startsWith("textures/gui/container/")||path.startsWith("textures/gui/sprites/container/")
                ||path.startsWith("textures/gui/sprites/recipe_book/"));
    }
}
