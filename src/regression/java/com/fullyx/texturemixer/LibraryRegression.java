package com.fullyx.texturemixer;

import com.fullyx.texturemixer.core.*;
import java.nio.file.*;
import java.util.*;

/** File persistence and identity regression checks for folder collections and saved mixes. */
public final class LibraryRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static String register(MixerConfig config, Path path) {
        String id = UUID.randomUUID().toString();
        config.sources.put(id, new MixerConfig.SourceRecord(id, path.toString(), path.getFileName().toString(), "fixture"));
        config.addCandidate(id);
        return id;
    }

    public static int run() throws Exception {
        checks = 0;
        Path temporary = Files.createTempDirectory("texturemixer-library-");
        Path configPath = temporary.resolve("config");
        Path collection = Files.createDirectories(temporary.resolve("中文 Packs"));
        MixerConfig current = new MixerConfig();
        String a = register(current, collection.resolve("Pack A.zip"));
        LibraryStore library = new LibraryStore(configPath);
        check(library.rootSourceIds(current).equals(List.of(a)), "legacy registration is visible at root");
        List<String> existingRoots = library.rootSourceIds(current);
        String b = register(current, collection.resolve("Pack B.zip"));
        var group = library.saveGroup(collection, List.of(a, b, b), existingRoots);
        check(group.name().equals("中文 Packs") && group.sourceIds().equals(List.of(a, b)), "collection name and deduplicated members persist");
        check(library.rootSourceIds(current).equals(List.of(a)), "preexisting flat pack remains flat; new grouped pack is nested");
        library.markStandalone(b);
        check(library.rootSourceIds(current).equals(List.of(a, b)), "pack may be both standalone and a folder member");
        LibraryStore reopened = new LibraryStore(configPath);
        check(reopened.groups().get(0).equals(group), "folder metadata survives restart");
        check(reopened.saveGroup(collection, List.of(b), List.of(a)).id().equals(group.id()), "reimport retains collection identity");

        TemplateStore templates = new TemplateStore(configPath);
        current.mainSource = a;
        current.candidateSources.remove(a);
        current.itemOverrides.put("minecraft:diamond_sword", b);
        current.skyOverrides.put("overworld", MixerConfig.VANILLA);
        String unused = register(current, temporary.resolve("Unused.zip"));
        var first = templates.save("PvP / 夜间", current);
        check(templates.list().size() == 1, "template is listed after saving");
        MixerConfig saved = templates.load(first.id());
        check(saved.sources.size() == 2 && !saved.sources.containsKey(unused), "saved template carries only referenced source identities");
        check(saved.mainSource.equals(a) && saved.itemOverrides.get("minecraft:diamond_sword").equals(b), "main and item mix choices are saved");
        check(saved.skyOverrides.get("overworld").equals(MixerConfig.VANILLA), "explicit vanilla selection survives template save");
        check(Files.exists(configPath.resolve("templates").resolve(first.id() + ".json")), "template filename is its UUID, independent of display name");
        boolean duplicateRejected = false;
        try { templates.save("pvp / 夜间", current); }
        catch (java.io.IOException expected) { duplicateRejected = true; }
        check(duplicateRejected, "overwriting an existing template requires explicit overwrite API");
        saved.itemOverrides.clear();
        check(!templates.load(first.id()).itemOverrides.isEmpty(), "returned template is an independent copy");
        TemplateStore restart = new TemplateStore(configPath);
        check(restart.load(first.id()).renderSignature().equals(templates.load(first.id()).renderSignature()), "template content survives restart");

        MixerConfig other = new MixerConfig();
        String sameA = register(other, collection.resolve("Pack A.zip"));
        String sameB = register(other, collection.resolve("Pack B.zip"));
        other.enabled = true;
        other.itemOverrides.put("minecraft:emerald", sameB);
        String before = other.renderSignature();
        MixerConfig merged = TemplateStore.merge(templates.load(first.id()), other);
        check(merged.mainSource.equals(sameA) && merged.itemOverrides.get("minecraft:diamond_sword").equals(sameB), "canonical paths remap different source UUIDs");
        check(merged.sources.size() == 2, "loading does not duplicate registered canonical sources");
        check(!merged.itemOverrides.containsKey("minecraft:emerald") && merged.enabled, "loading restores exact choices while preserving current enabled state");
        check(other.renderSignature().equals(before), "loading leaves current configuration untouched");
        check(!Files.exists(Path.of(merged.sources.get(sameB).canonicalRoot())), "missing source metadata is retained for repair");

        MixerConfig relocated = current.copy();
        relocated.sources.put(a, new MixerConfig.SourceRecord(a, temporary.resolve("Moved.zip").toString(), "Moved.zip", "new"));
        check(TemplateStore.merge(templates.load(first.id()), relocated).sources.get(a).lastKnownFolderName().equals("Moved.zip"), "stable source identity follows explicit relocation");
        current.blockOverrides.put("minecraft:diamond_block", b);
        var updated = templates.overwrite(first.id(), current);
        check(updated.id().equals(first.id()) && updated.createdAt() == first.createdAt(), "explicit overwrite keeps saved identity and creation time");
        check(templates.load(first.id()).blockOverrides.get("minecraft:diamond_block").equals(b), "explicit overwrite stores new mix choices");
        templates.delete(first.id());
        check(templates.list().isEmpty() && new TemplateStore(configPath).list().isEmpty(), "delete removes template registration and file");
        library.removeGroup(group.id());
        check(library.group(group.id()) == null && new LibraryStore(configPath).group(group.id()) == null, "folder removal survives restart");
        check(library.rootSourceIds(current).containsAll(List.of(a, b)), "removed folder members remain standalone");
        MixerConfig legacy = MixerConfig.GSON.fromJson("{\"mainSource\":\"" + a + "\"}", MixerConfig.class);
        check(legacy.mainStack().equals(List.of(a)), "legacy single main source migrates without rewriting its selection");
        current.setMainStack(List.of(a, b));
        current.validate();
        check(current.source(MixerConfig.Kind.ITEM, "minecraft:emerald").equals(current.mainView()), "implicit items follow ordered main stack");
        check(current.source(MixerConfig.Kind.ITEM, "minecraft:diamond_sword").equals(b), "explicit item remains isolated from main stack");
        String ordered = current.renderSignature();
        current.moveMain(b, -1);
        check(current.mainStack().equals(List.of(b, a)) && !ordered.equals(current.renderSignature()), "reordering changes render signature");
        check(current.referenceCount(a) > 0 && current.referenceCount(b) > 0, "every main layer is a source reference");
        var stacked = templates.save("Ordered layers", current);
        MixerConfig loadedStack = new TemplateStore(configPath).load(stacked.id());
        check(loadedStack.mainStack().equals(List.of(b, a)), "ordered stack survives template persistence");
        check(TemplateStore.merge(loadedStack, other).mainStack().equals(List.of(sameB, sameA)), "template remaps every ordered layer by canonical path");
        current.toggleMain(b);
        check(current.mainStack().equals(List.of(a)) && current.candidateSources.contains(b), "unselected main layer remains a candidate");
        current.toggleMain(MixerConfig.VANILLA);
        check(current.mainStack().isEmpty() && current.mainView().equals(MixerConfig.VANILLA), "Vanilla clears all main layers");
        Assets high = new Assets.Memory(Map.of(AssetId.of("test"), new byte[]{1}));
        Assets low = new Assets.Memory(Map.of(AssetId.of("test"), new byte[]{2}, AssetId.of("low"), new byte[]{3}));
        Assets layers = new Assets.Layered(List.of(high, low));
        check(layers.read(AssetId.of("test"))[0] == 1 && layers.read(AssetId.of("low"))[0] == 3, "top layer wins and lower layer fills missing files");
        return checks;
    }

    public static void main(String[] arguments) throws Exception {
        System.out.println("TextureMixer library regression: " + run() + " checks passed");
    }
}
