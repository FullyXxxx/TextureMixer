package com.fullyx.texturemixer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

public final class MixerConfig {
    public static final String VANILLA = "vanilla";
    public static final String STACK_PREFIX = "main-stack:";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int schemaVersion = 1;
    public boolean enabled;
    /** The generation committed with this config. Null also permits loading pre-1.0 configurations. */
    public UUID snapshotId;
    public LinkedHashMap<String, SourceRecord> sources = new LinkedHashMap<>();
    public String mainSource = VANILLA;
    public ArrayList<String> mainSources = new ArrayList<>();
    public ArrayList<String> candidateSources = new ArrayList<>();
    public TreeMap<String,String> itemOverrides = new TreeMap<>();
    public TreeMap<String,String> blockOverrides = new TreeMap<>();
    public TreeMap<String,String> skyOverrides = new TreeMap<>();

    public TreeMap<String,String> entityOverrides = new TreeMap<>();
    public TreeMap<String,String> particleOverrides = new TreeMap<>();
    public TreeMap<String,String> hudOverrides = new TreeMap<>();
    public TreeMap<String,String> soundOverrides = new TreeMap<>();
    public TreeMap<String,String> enchantOverrides = new TreeMap<>();
    public TreeMap<String,String> menuOverrides = new TreeMap<>();
    public TreeMap<String,String> containerOverrides = new TreeMap<>();
    public TreeMap<String,String> fontOverrides = new TreeMap<>();

    public record SourceRecord(String id, String canonicalRoot, String lastKnownFolderName, String lastKnownFingerprint) {}
    public enum Kind {
        ITEM("Item"), BLOCK("Block"), SKY("Sky"), ENTITY("Entity"), PARTICLE("Particle"), HUD("HUD"), SOUND("Sound & Music"), ENCHANT("Enchant"),
        MENU("Menu"), CONTAINER("Container GUI"), FONT("Font");
        public final String label;
        Kind(String label) { this.label = label; }
        public boolean interfaceCategory(){return this==HUD||this==MENU||this==CONTAINER||this==FONT;}
    }
    public Map<String,String> overrides(Kind kind) {
        return switch(kind) {
            case ITEM -> itemOverrides; case BLOCK -> blockOverrides; case SKY -> skyOverrides;
            case ENTITY -> entityOverrides; case PARTICLE -> particleOverrides; case HUD -> hudOverrides;
            case SOUND -> soundOverrides; case ENCHANT -> enchantOverrides; case MENU -> menuOverrides;
            case CONTAINER -> containerOverrides; case FONT -> fontOverrides;
        };
    }
    public void clearOverrides() {
        for (Kind kind : Kind.values()) overrides(kind).clear();
    }
    public List<String> mainStack() {
        if (mainSources.isEmpty()) return mainSource.equals(VANILLA) ? List.of() : List.of(mainSource);
        return List.copyOf(mainSources);
    }
    public String mainView() {
        List<String> stack = mainStack();
        if (stack.isEmpty()) return VANILLA;
        return stack.size() == 1 ? stack.get(0) : STACK_PREFIX + String.join(",", stack);
    }
    public String source(Kind kind, String target) { return overrides(kind).getOrDefault(target, mainView()); }
    public void setMainStack(Collection<String> ids) {
        List<String> previous = mainStack();
        mainSources = new ArrayList<>(new LinkedHashSet<>(ids));
        mainSources.remove(VANILLA);
        mainSource = mainSources.isEmpty() ? VANILLA : mainSources.get(0);
        candidateSources.removeAll(mainSources);
        for (String id : previous) addCandidate(id);
    }
    public void addMain(String id) {
        if (id.equals(VANILLA)) { setMainStack(List.of()); return; }
        var stack = new ArrayList<>(mainStack());
        stack.remove(id);
        stack.add(0,id);
        setMainStack(stack);
    }
    public void toggleMain(String id) {
        var stack = new ArrayList<>(mainStack());
        if (id.equals(VANILLA)) stack.clear();
        else if (!stack.remove(id)) stack.add(0,id);
        setMainStack(stack);
    }
    public void moveMain(String id, int direction) {
        var stack = new ArrayList<>(mainStack());
        int from = stack.indexOf(id), to = from + direction;
        if (from >= 0 && to >= 0 && to < stack.size()) {
            Collections.swap(stack, from, to);
            setMainStack(stack);
        }
    }
    public long referenceCount(String id) {
        long count=mainStack().contains(id)?1:0;
        for(Kind kind:Kind.values())count+=overrides(kind).values().stream().filter(id::equals).count();
        return count;
    }
    public int candidateCount() {
        return (int)candidateSources.stream().filter(id -> !mainStack().contains(id) && !id.equals(VANILLA)).distinct().count();
    }
    public void addCandidate(String id) {
        if (id.equals(VANILLA) || mainStack().contains(id) || candidateSources.contains(id)) return;
        candidateSources.add(id);
    }
    public void selectMain(String id) {
        setMainStack(id.equals(VANILLA) ? List.of() : List.of(id));
    }
    public MixerConfig copy() { return GSON.fromJson(GSON.toJson(this), MixerConfig.class); }
    public String renderSignature() {
        StringJoiner signature=new StringJoiner("|").add(Boolean.toString(enabled)).add(mainStack().toString());
        for(Kind kind:Kind.values())signature.add(overrides(kind).toString());
        return signature.toString();
    }
    public int changesFrom(MixerConfig other) {
        int changes = enabled == other.enabled ? 0 : 1;
        if (!mainStack().equals(other.mainStack())) changes++;
        for (Kind kind : Kind.values()) {
            Set<String> keys = new HashSet<>(overrides(kind).keySet()); keys.addAll(other.overrides(kind).keySet());
            for (String key : keys) if (!Objects.equals(overrides(kind).get(key), other.overrides(kind).get(key))) changes++;
        }
        return changes;
    }
    public void validate() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported configuration schema: " + schemaVersion);
        if (sources == null || candidateSources == null || mainSource == null || mainSources == null || itemOverrides == null
                || blockOverrides == null || skyOverrides == null || entityOverrides == null || particleOverrides == null
                || hudOverrides == null || soundOverrides == null || enchantOverrides == null
                || menuOverrides == null || containerOverrides == null || fontOverrides == null) throw new IllegalArgumentException("Incomplete configuration");
        if (new HashSet<>(candidateSources).size() != candidateSources.size() || candidateSources.contains(VANILLA))
            throw new IllegalArgumentException("Invalid candidate source roles");
        for (var entry : sources.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new IllegalArgumentException("Source identity mismatch");
            UUID.fromString(entry.getKey());
        }
        if (mainSources.contains(VANILLA) || new HashSet<>(mainSources).size() != mainSources.size()
                || (!mainSources.isEmpty() && !mainSources.get(0).equals(mainSource)))
            throw new IllegalArgumentException("Invalid main source stack");
        Set<String> references = new HashSet<>(candidateSources); references.addAll(mainStack());
        for (Kind kind : Kind.values()) {
            references.addAll(overrides(kind).values());
            for (String key : overrides(kind).keySet()) {
                if (kind == Kind.SKY) { if (!Set.of("overworld", "end", "weather").contains(key)) throw new IllegalArgumentException("Unknown sky: "+key); }
                else AssetId.of(key);
            }
        }
        for (String reference : references)
            if (!VANILLA.equals(reference) && !sources.containsKey(reference)) throw new IllegalArgumentException("Unknown source: " + reference);
    }
}
