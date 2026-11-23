package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

/** Folder collections and saved mixes share registrations, while Apply owns world changes. */
public final class MixerLibrary {
    public record ImportFailure(String path, String message) {}
    public record ImportResult(LibraryStore.Group group, int imported, List<ImportFailure> failures) {
        public ImportResult {
            failures = List.copyOf(failures);
        }
    }
    private record Scan(Path root, List<Path> packs, List<ImportFailure> failures) {}
    public record DiscoveryResult(Map<String, MixerConfig.SourceRecord> sources, boolean changed,
                                  List<ImportFailure> failures) {}
    private record Discovered(Scan scan, Map<Path, PackSource> sources, List<ImportFailure> failures) {}

    private final MixerService service;
    private final LibraryStore folders;
    private final TemplateStore templates;
    private Set<Path> automaticPaths;
    private Path automaticRoot;
    private CompletableFuture<DiscoveryResult> discovery;

    public MixerLibrary(MixerService service) throws IOException {
        this.service = service;
        Path config = service.client.gameDirectory.toPath().resolve("config/texturemixer");
        folders = new LibraryStore(config);
        templates = new TemplateStore(config);
    }

    public List<LibraryStore.Group> groups() {
        return folders.groups();
    }

    public LibraryStore.Group group(String id) {
        return folders.group(id);
    }

    public List<String> rootSourceIds(MixerConfig config) {
        return visibleSources(folders.rootSourceIds(config), config);
    }

    public List<String> groupSourceIds(String id, MixerConfig config) {
        LibraryStore.Group group = folders.group(id);
        return group == null ? List.of() : visibleSources(group.sourceIds(), config);
    }

    private List<String> visibleSources(Collection<String> ids, MixerConfig config) {
        return sortedSources(ids, config).stream().filter(id -> {
            Path path = Path.of(config.sources.get(id).canonicalRoot());
            return automaticPaths == null || !path.startsWith(automaticRoot)
                    || automaticPaths.contains(path) || config.referenceCount(id) > 0;
        }).toList();
    }

    public List<String> availableSourceIds(MixerConfig config) {
        return visibleSources(config.sources.keySet(), config);
    }

    /** Discovers the active instance's resourcepacks using the existing folder scanner. */
    public synchronized CompletableFuture<DiscoveryResult> discoverResourcePacks() {
        if (service.busy) return CompletableFuture.failedFuture(new IOException(tr("正在应用资源")));
        if (discovery != null && !discovery.isDone()) return discovery;
        Path directory = service.client.getResourcePackDirectory();
        Map<Path, PackSource> indexed = new HashMap<>();
        service.sources.values().forEach(source -> indexed.put(source.root(), source));
        discovery = CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(directory);
                Scan found = scan(directory);
                Map<Path, PackSource> available = new LinkedHashMap<>();
                List<ImportFailure> failures = new ArrayList<>(found.failures());
                for (Path path : found.packs()) {
                    try {
                        PackSource source = indexed.get(path);
                        if (source == null || !source.unchanged()) source = PackSource.open(path);
                        available.put(path, source);
                    } catch (IOException error) {
                        failures.add(new ImportFailure(path.toString(), MixerService.message(error)));
                    }
                }
                return new Discovered(found, available, failures);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, service.workers).thenApplyAsync(found -> registerDiscovered(found), service.client);
        return discovery;
    }

    private DiscoveryResult registerDiscovered(Discovered found) {
        if (service.busy) throw new CompletionException(new IOException(tr("正在应用资源，请稍后刷新")));
        MixerConfig next = service.committed.copy();
        Map<Path, String> existing = new HashMap<>();
        next.sources.values().forEach(source -> existing.put(Path.of(source.canonicalRoot()), source.id()));
        Map<String, MixerConfig.SourceRecord> discovered = new LinkedHashMap<>();
        Map<Path, List<String>> collections = new LinkedHashMap<>();
        Set<String> standalone = new LinkedHashSet<>(folders.rootSourceIds(next));
        boolean changed = automaticPaths == null || !automaticPaths.equals(new HashSet<>(found.scan().packs()));
        for (var entry : found.sources().entrySet()) {
            Path path = entry.getKey();
            PackSource source = entry.getValue();
            String id = existing.computeIfAbsent(path, ignored -> UUID.randomUUID().toString());
            var record = new MixerConfig.SourceRecord(id, path.toString(), source.name(), source.fingerprint());
            changed |= !record.equals(next.sources.put(id, record));
            discovered.put(id, record);
            next.addCandidate(id);
            Path relative = found.scan().root().relativize(path);
            if (relative.getNameCount() == 1) standalone.add(id);
            else collections.computeIfAbsent(found.scan().root().resolve(relative.getName(0)), ignored -> new ArrayList<>()).add(id);
        }
        try {
            if (!next.sources.equals(service.committed.sources)
                    || !next.candidateSources.equals(service.committed.candidateSources)) {
                service.store.save(next, false);
                service.committed = next;
            }
            for (var collection : collections.entrySet()) {
                var previous = folders.groupForPath(collection.getKey());
                if (previous == null || !previous.sourceIds().equals(collection.getValue())) {
                    folders.saveGroup(collection.getKey(), collection.getValue(), standalone);
                    changed = true;
                }
            }
            for (String id : standalone) {
                if (!folders.rootSourceIds(next).contains(id)) folders.markStandalone(id);
            }
        } catch (IOException error) {
            throw new CompletionException(error);
        }
        found.sources().forEach((path, source) -> {
            String id = existing.get(path);
            service.sources.put(id, source);
            service.sourceErrors.remove(id);
        });
        for (ImportFailure failure : found.failures()) {
            String id = existing.get(Path.of(failure.path()));
            if (id != null) {
                changed |= !failure.message().equals(service.sourceErrors.put(id, failure.message()));
                service.sources.remove(id);
            }
        }
        Set<Path> paths = Set.copyOf(found.scan().packs());
        for (var record : next.sources.values()) {
            Path path = Path.of(record.canonicalRoot());
            if (path.startsWith(found.scan().root()) && !paths.contains(path)) {
                service.sources.remove(record.id());
                service.sourceErrors.put(record.id(), tr("来源已从资源包文件夹移除"));
            }
        }
        automaticRoot = found.scan().root();
        automaticPaths = paths;
        return new DiscoveryResult(Map.copyOf(discovered), changed, List.copyOf(found.failures()));
    }

    public void removeGroup(String id) throws IOException {
        folders.removeGroup(id);
    }

    public void markStandalone(String id) throws IOException {
        folders.markStandalone(id);
    }

    public String recoveryMessage() {
        List<String> messages = new ArrayList<>(templates.recoveryMessages());
        if (!folders.recoveryMessage().isEmpty()) messages.add(folders.recoveryMessage());
        return String.join("；", messages);
    }

    public CompletableFuture<ImportResult> importFolder(Path selected, MixerConfig draft) {
        if (service.busy) return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后添加文件夹")));
        Set<String> standalone = new LinkedHashSet<>(folders.rootSourceIds(draft));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return scan(selected);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, service.workers).thenCompose(scan -> importPacks(scan, draft, standalone));
    }

    private CompletableFuture<ImportResult> importPacks(Scan scan, MixerConfig draft, Set<String> standalone) {
        List<ImportFailure> failures = new ArrayList<>(scan.failures());
        Set<String> imported = new LinkedHashSet<>();
        Set<String> successfulIds = new HashSet<>();
        CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
        for (Path pack : scan.packs()) {
            pending = pending.thenCompose(ignored -> service.add(pack, draft, false).handleAsync((id, error) -> {
                if (error == null) {
                    imported.add(id);
                    successfulIds.add(id);
                    service.sourceErrors.remove(id);
                } else {
                    String message = MixerService.message(error);
                    failures.add(new ImportFailure(pack.toString(), message));
                    // Keep a previously registered corrupt/moved pack visible inside its collection.
                    draft.sources.values().stream()
                            .filter(source -> Path.of(source.canonicalRoot()).equals(pack))
                            .findFirst().ifPresent(source -> {
                                imported.add(source.id());
                                service.sourceErrors.put(source.id(), message);
                            });
                }
                return (Void) null;
            }, service.client));
        }
        return pending.thenApplyAsync(ignored -> {
            LibraryStore.Group previous = folders.groupForPath(scan.root());
            if (previous != null) {
                previous.sourceIds().stream().filter(draft.sources::containsKey).forEach(imported::add);
            }
            try {
                LibraryStore.Group group = folders.saveGroup(scan.root(), imported, standalone);
                return new ImportResult(group, successfulIds.size(), failures);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, service.client);
    }

    private static Scan scan(Path selected) throws IOException {
        Path root = selected.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException(tr("请选择材质包所在的文件夹"));
        List<Path> packs = new ArrayList<>();
        List<ImportFailure> failures = new ArrayList<>();
        Set<Path> visited = new HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                Path real;
                try {
                    real = directory.toRealPath();
                } catch (IOException error) {
                    failures.add(new ImportFailure(directory.toString(), error.getMessage()));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!real.startsWith(root) || !visited.add(real)) return FileVisitResult.SKIP_SUBTREE;
                if (Files.isDirectory(directory.resolve("assets"), LinkOption.NOFOLLOW_LINKS)) {
                    packs.add(real);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    try {
                        Path real = file.toRealPath();
                        if (real.startsWith(root)) packs.add(real);
                    } catch (IOException error) {
                        failures.add(new ImportFailure(file.toString(), error.getMessage()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                failures.add(new ImportFailure(file.toString(), error.getMessage()));
                return FileVisitResult.CONTINUE;
            }
        });
        packs.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
        return new Scan(root, List.copyOf(new LinkedHashSet<>(packs)), List.copyOf(failures));
    }

    public List<TemplateStore.Summary> listTemplates() {
        return templates.list();
    }

    public TemplateStore.Summary templateByName(String name) {
        return templates.findByName(name);
    }

    public TemplateStore.Summary saveTemplate(String name, MixerConfig draft) throws IOException {
        return templates.save(name, draft);
    }

    public TemplateStore.Summary overwriteTemplate(String id, MixerConfig draft) throws IOException {
        return templates.overwrite(id, draft);
    }

    public void deleteTemplate(String id) throws IOException {
        templates.delete(id);
    }

    public CompletableFuture<MixerConfig> loadTemplate(String id, MixerConfig currentDraft) {
        if (service.busy) return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后载入自定义套装")));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return templates.load(id);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, service.workers).thenComposeAsync(saved -> {
            if (service.busy) return CompletableFuture.failedFuture(new IOException(tr("正在应用资源，请稍后载入自定义套装")));
            MixerConfig restored;
            try {
                restored = TemplateStore.merge(saved, currentDraft);
            } catch (IOException error) {
                return CompletableFuture.failedFuture(error);
            }
            MixerConfig registered = service.committed.copy();
            restored.sources.forEach(registered.sources::putIfAbsent);
            try {
                service.store.save(registered, false);
                service.committed = registered;
            } catch (IOException error) {
                return CompletableFuture.failedFuture(error);
            }
            List<CompletableFuture<Void>> indexing = new ArrayList<>();
            for (String sourceId : TemplateStore.selectedIds(restored)) {
                var source = restored.sources.get(sourceId);
                indexing.add(service.index(sourceId, source.canonicalRoot()));
            }
            return CompletableFuture.allOf(indexing.toArray(CompletableFuture[]::new))
                    .thenApplyAsync(ignored -> restored, service.client);
        }, service.client);
    }

    private static List<String> sortedSources(Collection<String> ids, MixerConfig config) {
        return ids.stream().filter(config.sources::containsKey)
                .sorted(Comparator.comparing((String id) -> config.sources.get(id).lastKnownFolderName().toLowerCase(Locale.ROOT))
                        .thenComparing(id -> id))
                .toList();
    }
}
