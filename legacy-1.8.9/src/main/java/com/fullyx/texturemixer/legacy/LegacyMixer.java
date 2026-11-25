package com.fullyx.texturemixer.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.fullyx.texturemixer.legacy.LegacyAssets.Pack;
import com.fullyx.texturemixer.legacy.LegacyAssets.Target;
import com.fullyx.texturemixer.legacy.mixin.DefaultPackAccessor;
import com.fullyx.texturemixer.legacy.mixin.MinecraftAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.ReloadableResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;

public final class LegacyMixer {
    static final String VANILLA = "";
    private static final Logger LOG = LogManager.getLogger("TextureMixer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final byte[] EMPTY_METADATA = "{}".getBytes(StandardCharsets.UTF_8);
    private static LegacyMixer instance;

    final MinecraftClient client;
    private final Path config;
    private final Path packDirectory;
    private final List<Target> targets;
    private final DefaultResourcePack vanilla;

    private List<Pack> packs = Collections.emptyList();
    private List<ResourcePack> basePacks = Collections.emptyList();
    private State state;
    private Map<String, byte[]> overlay = Collections.emptyMap();
    private Map<String, byte[]> restoreOverlay;
    private RuntimeException reloadFailure;
    private boolean loaded;

    private static final class State {
        Map<String, String> selection = new LinkedHashMap<>();
        Map<String, Map<String, String>> sets = new LinkedHashMap<>();
    }

    public static LegacyMixer get(MinecraftClient client) {
        if (instance == null || instance.client != client) instance = new LegacyMixer(client);
        return instance;
    }

    private LegacyMixer(MinecraftClient client) {
        this.client = client;
        this.config = client.runDirectory.toPath().resolve("config/texturemixer-legacy.json");
        this.packDirectory = client.runDirectory.toPath().resolve("resourcepacks");
        this.vanilla = ((MinecraftAccessor) client).texturemixer$defaultPack();
        this.state = readState();
        this.targets = LegacyAssets.catalog(vanillaPaths());
    }

    private Iterable<String> vanillaPaths() {
        TreeSet<String> names = new TreeSet<>();
        URL sample = DefaultResourcePack.class.getResource("/assets/minecraft/textures/items/diamond_sword.png");
        if (sample != null) {
            try {
                java.net.URLConnection url = sample.openConnection();
                if (url instanceof JarURLConnection) {
                    JarURLConnection connection = (JarURLConnection) url;
                    connection.setUseCaches(false);
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<? extends ZipEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (name.startsWith(LegacyAssets.ROOT)) {
                                names.add(name.substring(LegacyAssets.ROOT.length()));
                            }
                        }
                    }
                }
            } catch (IOException error) {
                LOG.warn("Could not index built-in textures", error);
            }
        }
        for (String key : ((DefaultPackAccessor) vanilla).texturemixer$assetIndex().keySet()) {
            if (key.startsWith("minecraft:")) names.add(key.substring("minecraft:".length()));
        }
        names.add("textures/items/diamond_sword.png");
        return names;
    }

    private State readState() {
        if (Files.isRegularFile(config)) {
            try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                State saved = GSON.fromJson(reader, State.class);
                if (saved != null && saved.selection != null && saved.sets != null) return saved;
            } catch (IOException | RuntimeException error) {
                LOG.warn("Could not read TextureMixer settings", error);
            }
        }
        return new State();
    }

    private void writeState() throws IOException {
        Files.createDirectories(config.getParent());
        Path temporary = Files.createTempFile(config.getParent(), "texturemixer-", ".tmp");
        try {
            Files.write(temporary, GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, config, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void refresh() throws IOException {
        packs = LegacyAssets.scan(packDirectory, LOG::warn);
    }

    List<Target> targets() {
        return targets;
    }

    List<Pack> sources(Target target) {
        List<Pack> sources = new ArrayList<>();
        for (Pack pack : packs) {
            for (String path : target.paths) {
                if (pack.contains(path)) {
                    sources.add(pack);
                    break;
                }
            }
        }
        return sources;
    }

    Map<String, String> selection() {
        return new LinkedHashMap<>(state.selection);
    }

    List<String> sets() {
        return new ArrayList<>(state.sets.keySet());
    }

    Map<String, String> load(String name) {
        Map<String, String> saved = state.sets.get(name);
        return saved == null ? Collections.emptyMap() : new LinkedHashMap<>(saved);
    }

    void save(String name, Map<String, String> selection) throws IOException {
        Map<String, String> previous = state.sets.put(name, new LinkedHashMap<>(selection));
        try {
            writeState();
        } catch (IOException error) {
            if (previous == null) state.sets.remove(name);
            else state.sets.put(name, previous);
            throw error;
        }
    }

    byte[] preview(Target target, Pack pack, boolean vanillaOnly) throws IOException {
        for (String path : target.paths) {
            if (pack != null) {
                byte[] selected = pack.read(path);
                if (selected != null) return selected;
            } else if (vanillaOnly) {
                Identifier id = new Identifier("minecraft", path);
                if (vanilla.contains(id)) {
                    try (InputStream stream = vanilla.open(id)) {
                        return LegacyAssets.bytes(stream);
                    }
                }
            } else {
                byte[] base = basePreview(path);
                if (base != null) return base;
            }
        }
        return null;
    }

    private byte[] basePreview(String path) throws IOException {
        Identifier id = new Identifier("minecraft", path);
        for (int i = basePacks.size() - 1; i >= 0; i--) {
            ResourcePack pack = basePacks.get(i);
            if (pack.contains(id)) {
                try (InputStream stream = pack.open(id)) {
                    return LegacyAssets.bytes(stream);
                }
            }
        }
        return null;
    }

    private Map<String, byte[]> compile(Map<String, String> selection, boolean restoring) throws IOException {
        Map<String, Pack> byName = new LinkedHashMap<>();
        for (Pack pack : packs) byName.put(pack.name, pack);
        Map<String, byte[]> result = new LinkedHashMap<>();
        long size = 0;
        for (Target target : targets) {
            String name = selection.get(target.id);
            if (name == null) continue;
            Pack source = byName.get(name);
            if (source == null && !VANILLA.equals(name)) {
                if (!restoring) throw new IOException("Source pack missing: " + name);
                LOG.warn("Source pack missing for {}: {}", target.id, name);
                continue;
            }
            boolean present = false;
            for (String path : target.paths) {
                byte[] data = source == null ? vanillaBytes(path) : source.read(path);
                if (data == null) continue;
                present = true;
                byte[] metadata = source == null ? vanillaBytes(path + ".mcmeta") : source.read(path + ".mcmeta");
                if (path.endsWith(".png")) {
                    try {
                        image(data, 1);
                        if (metadata != null && !new JsonParser().parse(new String(metadata, StandardCharsets.UTF_8)).isJsonObject()) {
                            throw new IOException("Invalid texture metadata");
                        }
                    } catch (IOException | RuntimeException error) {
                        throw new IOException("Invalid texture or metadata: " + path, error);
                    }
                    if (metadata == null) metadata = EMPTY_METADATA;
                }
                size += data.length + (metadata == null ? 0L : metadata.length);
                if (size > 128 * 1024 * 1024L) throw new IOException("The active mix exceeds 128 MiB");
                result.put(path, data);
                if (metadata != null) result.put(path + ".mcmeta", metadata);
            }
            if (!present && !restoring) throw new IOException("Source no longer contains " + target.title);
        }
        return result;
    }

    private byte[] vanillaBytes(String path) throws IOException {
        Identifier id = new Identifier("minecraft", path);
        if (!vanilla.contains(id)) return null;
        try (InputStream stream = vanilla.open(id)) {
            return LegacyAssets.bytes(stream);
        }
    }

    private static int longestSide(byte[] data) throws IOException {
        if (data.length < 24 || ByteBuffer.wrap(data).getLong() != 0x89504e470d0a1a0aL) {
            throw new IOException("Invalid PNG texture");
        }
        ByteBuffer header = ByteBuffer.wrap(data);
        int width = header.getInt(16);
        int height = header.getInt(20);
        if (width < 1 || height < 1 || (long) width * height > 16_777_216L) {
            throw new IOException("Texture exceeds the 16 megapixel limit");
        }
        return Math.max(width, height);
    }

    static BufferedImage thumbnail(byte[] data) throws IOException {
        int side = longestSide(data);
        return image(data, (side + 511) / 512);
    }

    private static BufferedImage image(byte[] data, int step) throws IOException {
        longestSide(data);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) throw new IOException("PNG decoder unavailable");
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input);
            ImageReadParam settings = reader.getDefaultReadParam();
            settings.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, settings);
        } finally {
            reader.dispose();
        }
    }

    void apply(Map<String, String> selection) throws IOException {
        refresh();
        Map<String, byte[]> compiled = compile(selection, false);
        Map<String, String> previous = state.selection;
        restoreOverlay = overlay;
        overlay = compiled;
        reloadFailure = null;
        try {
            client.reloadResources();
            if (reloadFailure != null) throw new IOException("Minecraft rejected the selected resources", reloadFailure);
            state.selection = new LinkedHashMap<>(selection);
            writeState();
        } catch (IOException | RuntimeException error) {
            state.selection = previous;
            overlay = restoreOverlay;
            try {
                client.reloadResources();
            } catch (RuntimeException rollbackFailure) {
                error.addSuppressed(rollbackFailure);
            }
            throw error;
        } finally {
            restoreOverlay = null;
        }
    }

    public void reload(ReloadableResourceManager manager, List<ResourcePack> packs) {
        basePacks = new ArrayList<>(packs);
        if (!loaded) {
            loaded = true;
            try {
                if (!state.selection.isEmpty()) {
                    refresh();
                    overlay = compile(state.selection, true);
                }
            } catch (IOException error) {
                LOG.warn("Could not restore the saved mix", error);
            }
        }
        if (overlay.isEmpty()) {
            manager.reload(packs);
            return;
        }
        try {
            manager.reload(withOverlay(packs, overlay));
        } catch (RuntimeException failure) {
            reloadFailure = failure;
            LOG.warn("Could not reload TextureMixer resources", failure);
            overlay = restoreOverlay == null ? Collections.emptyMap() : restoreOverlay;
            manager.reload(withOverlay(packs, overlay));
        }
    }

    private static List<ResourcePack> withOverlay(List<ResourcePack> packs, Map<String, byte[]> resources) {
        if (resources.isEmpty()) return packs;
        List<ResourcePack> result = new ArrayList<>(packs);
        result.add(new OverlayPack(resources));
        return result;
    }
}
