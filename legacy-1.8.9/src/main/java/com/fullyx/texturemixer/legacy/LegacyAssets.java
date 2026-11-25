package com.fullyx.texturemixer.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class LegacyAssets {
    static final String ROOT = "assets/minecraft/";
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    enum Category {
        ITEM("Item"), BLOCK("Block"), SKY("Sky"), ENTITY("Entity"), PARTICLE("Particle"),
        HUD("HUD"), SOUND("Sound & Music"), ENCHANT("Enchant"), MENU("Menu"),
        CONTAINER("Container GUI"), FONT("Font");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    static final class Target {
        final Category category;
        final String id;
        final String title;
        final List<String> paths;

        Target(Category category, String id, String title, List<String> paths) {
            this.category = category;
            this.id = id;
            this.title = title;
            this.paths = paths;
        }
    }

    static final class Pack {
        final Path path;
        final String name;
        final boolean folder;
        private final Map<String, String> entries;

        private Pack(Path path, String name, boolean folder, Map<String, String> entries) {
            this.path = path;
            this.name = name;
            this.folder = folder;
            this.entries = entries;
        }

        boolean contains(String key) {
            return entries.containsKey(key);
        }

        byte[] read(String key) throws IOException {
            String entry = entries.get(key);
            if (entry == null) return null;
            if (folder) {
                Path file = path.resolve(entry).normalize();
                if (!file.toRealPath().startsWith(path.toRealPath())) throw new IOException("Resource outside " + name);
                try (InputStream stream = Files.newInputStream(file)) {
                    return bytes(stream);
                }
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry file = zip.getEntry(entry);
                if (file == null || file.getSize() > MAX_BYTES) throw new IOException("Cannot read " + entry);
                try (InputStream stream = zip.getInputStream(file)) {
                    return bytes(stream);
                }
            }
        }
    }

    static List<Pack> scan(Path directory, Consumer<String> warning) throws IOException {
        List<Pack> packs = new ArrayList<>();
        if (!Files.isDirectory(directory)) return packs;
        discover(directory, directory, 0, packs, warning);
        packs.sort(Comparator.comparing(pack -> pack.name.toLowerCase(Locale.ROOT)));
        return packs;
    }

    private static void discover(Path root, Path directory, int depth, List<Pack> output, Consumer<String> warning)
            throws IOException {
        if (depth >= 8 || output.size() >= 256) return;
        try (Stream<Path> children = Files.list(directory)) {
            java.util.Iterator<Path> iterator = children.iterator();
            while (iterator.hasNext() && output.size() < 256) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) continue;
                String name = root.relativize(path).toString().replace('\\', '/');
                try {
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isDirectory(path.resolve("assets"), LinkOption.NOFOLLOW_LINKS)) {
                            output.add(folder(path, name));
                        } else {
                            discover(root, path, depth + 1, output, warning);
                        }
                    } else if (Files.isRegularFile(path) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        Pack pack = archive(path, name);
                        if (!pack.entries.isEmpty()) output.add(pack);
                    }
                } catch (IOException | IllegalArgumentException error) {
                    warning.accept("Skipped " + name + ": " + error.getMessage());
                }
            }
        }
    }

    private static Pack folder(Path path, String name) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(path.resolve("assets"), 20)) {
            java.util.Iterator<Path> files = walk.iterator();
            int count = 0;
            while (files.hasNext()) {
                Path file = files.next();
                if (++count > MAX_ENTRIES) throw new IOException("too many entries");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = path.relativize(file).toString().replace('\\', '/');
                index(entries, relative, relative);
            }
        }
        return new Pack(path, name, true, entries);
    }

    private static Pack archive(Path path, String name) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        Map<String, String> wrappedEntries = new LinkedHashMap<>();
        String wrapper = null;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> files = zip.entries();
            int count = 0;
            while (files.hasMoreElements()) {
                ZipEntry file = files.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("too many entries");
                if (file.isDirectory()) continue;
                String original = file.getName();
                int marker = original.indexOf(ROOT);
                if (marker == 0) {
                    index(entries, original, original);
                } else if (marker > 1 && original.indexOf('/') == marker - 1) {
                    String parent = original.substring(0, marker - 1);
                    if (wrapper == null) wrapper = parent;
                    if (wrapper.equals(parent)) index(wrappedEntries, original.substring(marker), original);
                }
            }
        }
        return new Pack(path, name, false, entries.isEmpty() ? wrappedEntries : entries);
    }

    private static void index(Map<String, String> entries, String source, String original) {
        if (!source.startsWith(ROOT) || !source.matches("[a-z0-9_/.-]+")
                || source.contains("..") || source.contains("//")) return;
        String key = source.substring(ROOT.length());
        String mapped = legacyPath(key);
        if (mapped.equals(key)) entries.put(mapped, original);
        else entries.putIfAbsent(mapped, original);
    }

    static String legacyPath(String path) {
        if (path.startsWith("textures/item/")) return "textures/items/" + path.substring(14);
        if (path.startsWith("textures/block/")) return "textures/blocks/" + path.substring(15);
        if (path.equals("textures/misc/enchanted_glint_item.png")) return "textures/misc/enchanted_item_glint.png";
        return path;
    }

    static List<Target> catalog(Iterable<String> paths) {
        TreeSet<String> sorted = new TreeSet<>();
        paths.forEach(sorted::add);
        List<Target> targets = new ArrayList<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String path : sorted) {
            Category category = category(path);
            if (category == null) continue;
            String group = baseGroup(path);
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(path);
        }
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            String id = entry.getKey();
            entry.getValue().sort(Comparator.comparingInt(LegacyAssets::previewOrder));
            targets.add(new Target(category(entry.getValue().get(0)), id, title(id), entry.getValue()));
        }
        targets.sort(Comparator.comparing(target -> target.title, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    private static int previewOrder(String path) {
        if (path.endsWith("_still.png") || path.endsWith("_standby.png")) return 0;
        return 1;
    }

    private static String baseGroup(String path) {
        if (path.matches("textures/blocks/(water|lava)_(still|flow|overlay)\\.png")) {
            return path.substring(0, path.lastIndexOf('_')) + ".png";
        }
        if (path.matches("textures/items/bow_(standby|pulling_[0-9]+)\\.png")) return "textures/items/bow.png";
        return path;
    }

    private static Category category(String key) {
        if (key.startsWith("sounds/") && key.endsWith(".ogg")) return Category.SOUND;
        if (!key.endsWith(".png")) return null;
        if (key.startsWith("textures/items/") || key.startsWith("textures/models/armor/")) return Category.ITEM;
        if (key.startsWith("textures/blocks/")) return Category.BLOCK;
        if (key.startsWith("textures/entity/")) return Category.ENTITY;
        if (key.startsWith("textures/particle/")) return Category.PARTICLE;
        if (key.startsWith("textures/environment/") || key.startsWith("textures/sky/")) return Category.SKY;
        if (key.startsWith("textures/gui/container/")) return Category.CONTAINER;
        if (key.startsWith("textures/gui/title/") || key.startsWith("textures/gui/achievement/")) return Category.MENU;
        if (key.startsWith("textures/gui/")) return Category.HUD;
        if (key.startsWith("textures/font/")) return Category.FONT;
        if (key.equals("textures/misc/enchanted_item_glint.png")) return Category.ENCHANT;
        if (key.startsWith("textures/misc/")) return Category.HUD;
        return null;
    }

    private static String title(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.')).replace('_', ' ');
        if (name.isEmpty()) return path;
        if (path.startsWith("textures/models/armor/")) name = name.replace(" layer ", " armor layer ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static byte[] bytes(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() > MAX_BYTES - count) throw new IOException("Resource exceeds 32 MiB");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
