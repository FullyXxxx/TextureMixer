package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named mix choices with source identities. Resource bytes stay in the user's original packs. */
public final class TemplateStore {
    public record Summary(String id, String name, long createdAt, long updatedAt) {}
    private record SavedTemplate(int schemaVersion, String id, String name, long createdAt,
                                 long updatedAt, MixerConfig config) {}

    private final Path directory;
    private final Map<String, SavedTemplate> templates = new LinkedHashMap<>();
    private final List<String> recoveryMessages = new ArrayList<>();

    public TemplateStore(Path configDirectory) throws IOException {
        directory = configDirectory.resolve("templates");
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                read(file);
            }
        }
    }

    private void read(Path file) throws IOException {
        try {
            String filename = file.getFileName().toString();
            String fileId = filename.substring(0, filename.length() - 5);
            UUID.fromString(fileId);
            SavedTemplate template = MixerConfig.GSON.fromJson(Files.readString(file), SavedTemplate.class);
            if (template == null || template.schemaVersion() != 1 || !fileId.equals(template.id())
                    || template.config() == null || template.createdAt() < 0 || template.updatedAt() < template.createdAt()) {
                throw new IOException(tr("自定义套装无效"));
            }
            requireName(template.name());
            template.config().validate();
            templates.put(template.id(), template);
        } catch (Exception error) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + UUID.randomUUID()));
            recoveryMessages.add(tr("自定义套装读取失败，已保留原文件：") + file.getFileName());
        }
    }

    public synchronized List<String> recoveryMessages() {
        return List.copyOf(recoveryMessages);
    }

    public synchronized List<Summary> list() {
        return templates.values().stream().map(TemplateStore::summary)
                .sorted(Comparator.comparingLong(Summary::updatedAt).reversed()
                        .thenComparing(Summary::name).thenComparing(Summary::id))
                .toList();
    }

    public synchronized Summary findByName(String name) {
        String normalized = requireName(name);
        return templates.values().stream()
                .filter(template -> template.name().equalsIgnoreCase(normalized))
                .map(TemplateStore::summary).findFirst().orElse(null);
    }

    public synchronized Summary save(String name, MixerConfig draft) throws IOException {
        String normalized = requireName(name);
        if (findByName(normalized) != null) throw new IOException(tr("同名自定义套装已存在"));
        long now = System.currentTimeMillis();
        SavedTemplate template = new SavedTemplate(1, UUID.randomUUID().toString(), normalized,
                now, now, selectedConfig(draft));
        write(template);
        return summary(template);
    }

    public synchronized Summary overwrite(String id, MixerConfig draft) throws IOException {
        SavedTemplate previous = require(id);
        SavedTemplate template = new SavedTemplate(1, id, previous.name(), previous.createdAt(),
                Math.max(previous.createdAt(), System.currentTimeMillis()), selectedConfig(draft));
        write(template);
        return summary(template);
    }

    public synchronized MixerConfig load(String id) throws IOException {
        return require(id).config().copy();
    }

    public synchronized void delete(String id) throws IOException {
        require(id);
        Files.deleteIfExists(directory.resolve(id + ".json"));
        templates.remove(id);
    }

    private SavedTemplate require(String id) throws IOException {
        SavedTemplate template = templates.get(id);
        if (template == null) throw new IOException(tr("自定义套装已不存在"));
        return template;
    }

    private void write(SavedTemplate template) throws IOException {
        Path file = directory.resolve(template.id() + ".json");
        ConfigStore.atomic(file, MixerConfig.GSON.toJson(template).getBytes(StandardCharsets.UTF_8));
        templates.put(template.id(), template);
    }

    private static Summary summary(SavedTemplate template) {
        return new Summary(template.id(), template.name(), template.createdAt(), template.updatedAt());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException(tr("请输入自定义套装名称"));
        String normalized = name.strip();
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(tr("自定义套装名称不能包含换行或控制字符"));
        }
        return normalized;
    }

    private static MixerConfig selectedConfig(MixerConfig draft) {
        draft.validate();
        MixerConfig saved = draft.copy();
        Set<String> selected = selectedIds(saved);
        saved.sources.keySet().retainAll(selected);
        saved.candidateSources.removeIf(id -> !selected.contains(id) || saved.mainStack().contains(id));
        for (String id : selected) {
            if (!saved.mainStack().contains(id) && !saved.candidateSources.contains(id)) saved.addCandidate(id);
        }
        saved.validate();
        return saved;
    }

    public static Set<String> selectedIds(MixerConfig config) {
        Set<String> selected = new LinkedHashSet<>();
        selected.addAll(config.mainStack());
        for (MixerConfig.Kind kind : MixerConfig.Kind.values()) selected.addAll(config.overrides(kind).values());
        selected.remove(MixerConfig.VANILLA);
        return selected;
    }

    /** Restores choices into a copy, retaining current library registrations and stable relocated identities. */
    public static MixerConfig merge(MixerConfig saved, MixerConfig current) throws IOException {
        saved.validate();
        current.validate();
        MixerConfig result = current.copy();
        Map<String, String> remap = new HashMap<>();
        remap.put(MixerConfig.VANILLA, MixerConfig.VANILLA);
        for (var source : saved.sources.values()) {
            String id = matchingSource(source, result);
            if (id == null) {
                id = source.id();
                result.sources.put(id, source);
            }
            remap.put(source.id(), id);
        }
        result.setMainStack(saved.mainStack().stream().map(remap::get).toList());
        for (MixerConfig.Kind kind : MixerConfig.Kind.values()) {
            result.overrides(kind).clear();
            for (var selection : saved.overrides(kind).entrySet()) {
                result.overrides(kind).put(selection.getKey(), remap.get(selection.getValue()));
            }
        }
        result.candidateSources.removeAll(result.mainStack());
        try {
            for (String id : selectedIds(result)) result.addCandidate(id);
            result.validate();
        } catch (IllegalArgumentException error) {
            throw new IOException(tr("无法载入自定义套装：") + error.getMessage(), error);
        }
        return result;
    }

    private static String matchingSource(MixerConfig.SourceRecord saved, MixerConfig current) {
        if (current.sources.containsKey(saved.id())) return saved.id();
        Path savedPath = Path.of(saved.canonicalRoot()).toAbsolutePath().normalize();
        return current.sources.values().stream()
                .filter(source -> Path.of(source.canonicalRoot()).toAbsolutePath().normalize().equals(savedPath))
                .map(MixerConfig.SourceRecord::id).findFirst().orElse(null);
    }
}
