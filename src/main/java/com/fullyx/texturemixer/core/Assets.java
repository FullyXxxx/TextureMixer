package com.fullyx.texturemixer.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public interface Assets {
    byte[] read(AssetId id) throws IOException;
    Set<AssetId> ids();
    default boolean contains(AssetId id) { return ids().contains(id); }
    default JsonObject json(AssetId id) throws IOException {
        byte[] bytes = read(id);
        if (bytes == null) throw new IOException("Missing resource: " + id);
        try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Invalid JSON: " + id + ": " + e.getMessage(), e); }
    }
    static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static byte[] jsonBytes(JsonElement value) { return value.toString().getBytes(StandardCharsets.UTF_8); }

    record Memory(Map<AssetId, byte[]> files) implements Assets {
        public Memory { files = Map.copyOf(files); }
        public byte[] read(AssetId id) { return files.get(id); }
        public Set<AssetId> ids() { return files.keySet(); }
    }

    /** Priority is highest first, matching the game's pack selection list. */
    record Layered(List<Assets> layers) implements Assets {
        public Layered { layers = List.copyOf(layers); }
        public List<Assets> lowestFirst() {
            var order=new ArrayList<>(layers);
            Collections.reverse(order);
            return order;
        }
        public byte[] read(AssetId id) throws IOException {
            for (Assets layer : layers) {
                byte[] bytes = layer.read(id);
                if (bytes != null) return bytes;
            }
            return null;
        }
        public Set<AssetId> ids() {
            Set<AssetId> result = new TreeSet<>();
            layers.forEach(layer -> result.addAll(layer.ids()));
            return Collections.unmodifiableSet(result);
        }
    }

    /** No implicit global resource pack manager: precisely source first, vanilla second. */
    record View(Assets source, Assets vanilla) implements Assets {
        public byte[] read(AssetId id) throws IOException {
            byte[] own = source == null ? null : source.read(id);
            return own == null ? vanilla.read(id) : own;
        }
        public Set<AssetId> ids() {
            Set<AssetId> result = new TreeSet<>(vanilla.ids());
            if (source != null) result.addAll(source.ids());
            return Collections.unmodifiableSet(result);
        }
        public boolean fromSource(AssetId id) { return source != null && source.contains(id); }

        /** A pack can supply metadata for a lower image. A higher image hides lower metadata. */
        public boolean hasMetadataForImage(AssetId image, AssetId metadata) {
            if (source instanceof Layered layered) {
                for (Assets layer : layered.layers()) {
                    if (layer.contains(metadata)) return true;
                    if (layer.contains(image)) return false;
                }
            } else if (source != null) {
                if (source.contains(metadata)) return true;
                if (source.contains(image)) return false;
            }
            return vanilla.contains(metadata);
        }
    }
}
