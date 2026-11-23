package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Persists folder membership independently from the currently applied resource choices. */
public final class LibraryStore {
    public record Group(String id, String name, String canonicalRoot, List<String> sourceIds) {
        public Group {
            UUID.fromString(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(canonicalRoot);
            if (!Path.of(canonicalRoot).isAbsolute()) throw new IllegalArgumentException(tr("分组路径必须为绝对路径"));
            sourceIds = List.copyOf(new LinkedHashSet<>(sourceIds));
            sourceIds.forEach(UUID::fromString);
        }
    }

    private static final class State {
        int schemaVersion = 1;
        LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
        LinkedHashSet<String> standaloneSources = new LinkedHashSet<>();
    }

    private final Path file;
    private State state;
    private String recoveryMessage = "";

    public LibraryStore(Path directory) throws IOException {
        file = directory.resolve("library.json");
        state = read();
    }

    private State read() throws IOException {
        if (!Files.exists(file)) return new State();
        try {
            State loaded = MixerConfig.GSON.fromJson(Files.readString(file), State.class);
            if (loaded == null || loaded.schemaVersion != 1 || loaded.groups == null || loaded.standaloneSources == null) {
                throw new IOException(tr("材质库分组配置无效"));
            }
            for (var entry : loaded.groups.entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) throw new IOException(tr("材质库分组身份不一致"));
            }
            loaded.standaloneSources.forEach(UUID::fromString);
            return loaded;
        } catch (Exception error) {
            Path backup = file.resolveSibling("library.corrupt-" + UUID.randomUUID() + ".json");
            Files.copy(file, backup);
            recoveryMessage = tr("分组配置损坏，已保留原文件；已登记材质包仍可单独浏览");
            return new State();
        }
    }

    public synchronized String recoveryMessage() {
        return recoveryMessage;
    }

    public synchronized List<Group> groups() {
        return state.groups.values().stream()
                .sorted(Comparator.comparing((Group group) -> group.name().toLowerCase(Locale.ROOT))
                        .thenComparing(Group::canonicalRoot).thenComparing(Group::id))
                .toList();
    }

    public synchronized Group group(String id) {
        return state.groups.get(id);
    }

    public synchronized Group groupForPath(Path root) {
        String path = root.toAbsolutePath().normalize().toString();
        return state.groups.values().stream()
                .filter(group -> Path.of(group.canonicalRoot()).equals(Path.of(path)))
                .findFirst().orElse(null);
    }

    public synchronized List<String> rootSourceIds(MixerConfig config) {
        Set<String> members = new HashSet<>();
        state.groups.values().forEach(group -> members.addAll(group.sourceIds()));
        return config.sources.keySet().stream()
                .filter(id -> !members.contains(id) || state.standaloneSources.contains(id))
                .toList();
    }

    public synchronized void markStandalone(String id) throws IOException {
        UUID.fromString(id);
        State next = copy();
        next.standaloneSources.add(id);
        save(next);
    }

    /** Existing standalone registrations remain at the root when also included in a folder. */
    public synchronized Group saveGroup(Path root, Collection<String> sourceIds,
                                        Collection<String> existingRootSources) throws IOException {
        Group previous = groupForPath(root);
        String id = previous == null ? UUID.randomUUID().toString() : previous.id();
        Path name = root.getFileName();
        Group group = new Group(id, name == null ? root.toString() : name.toString(),
                root.toString(), new ArrayList<>(sourceIds));
        State next = copy();
        next.standaloneSources.addAll(existingRootSources);
        next.groups.put(id, group);
        save(next);
        return group;
    }

    public synchronized void removeGroup(String id) throws IOException {
        State next = copy();
        next.groups.remove(id);
        save(next);
    }

    private State copy() {
        State next = new State();
        next.groups.putAll(state.groups);
        next.standaloneSources.addAll(state.standaloneSources);
        return next;
    }

    private void save(State next) throws IOException {
        ConfigStore.atomic(file, MixerConfig.GSON.toJson(next).getBytes(StandardCharsets.UTF_8));
        state = next;
    }
}
