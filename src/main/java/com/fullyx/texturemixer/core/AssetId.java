package com.fullyx.texturemixer.core;

/** A resource location independent from Minecraft initialization, also used in regression tests. */
public record AssetId(String namespace, String path) implements Comparable<AssetId> {
    public AssetId {
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]+")
                || path.startsWith("/") || path.contains("..") || path.contains("//"))
            throw new IllegalArgumentException("Invalid resource location: " + namespace + ":" + path);
    }
    public static AssetId of(String value) {
        int colon = value.indexOf(':');
        return colon < 0 ? new AssetId("minecraft", value) : new AssetId(value.substring(0, colon), value.substring(colon + 1));
    }
    public AssetId file(String directory, String suffix) { return new AssetId(namespace, directory + "/" + path + suffix); }
    public AssetId withPath(String value) { return new AssetId(namespace, value); }
    public String assetPath() { return "assets/" + namespace + "/" + path; }
    @Override public String toString() { return namespace + ":" + path; }
    @Override public int compareTo(AssetId other) { return toString().compareTo(other.toString()); }
}
