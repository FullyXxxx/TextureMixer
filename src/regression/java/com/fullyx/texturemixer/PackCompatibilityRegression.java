package com.fullyx.texturemixer;

import com.fullyx.texturemixer.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Regressions for packs mixing native resources and optional OptiFine auxiliary files. */
public final class PackCompatibilityRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static int verifyFixtures() throws Exception {
        int initial = checks;
        Path root = Files.createTempDirectory("texturemixer-pack-compat-");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", "{\"pack\":{\"pack_format\":75}}".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/minecraft/textures/item/wooden_sword.png", new byte[]{1, 2, 3});
        entries.put("assets/minecraft/optifine/cit/kb swords/kb_sword_2.properties", "items=wooden_sword".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/minecraft/optifine/cit/notes/NOTES.txt", new byte[]{4});
        entries.put("assets/minecraft/optifine/cit/swords/kb_sword_1.properties", "items=wooden_sword\nenchantmentIDs=knockback\n".getBytes(StandardCharsets.UTF_8));
        for (var entry : entries.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        var directory = new DirectorySource(root);
        AssetId sword = AssetId.of("textures/item/wooden_sword.png");
        check(directory.mainCompatible(), "directory metadata remains compatible");
        check(directory.ids().size() == 2, "directory indexes only native Identifier paths");
        check(Arrays.equals(directory.read(sword), new byte[]{1, 2, 3}), "ordinary directory texture remains readable");
        check(UnsupportedResources.check(directory, MixerConfig.Kind.ITEM, AssetId.of("wooden_sword")) != null,
                "conditional CIT is informative and does not reject ordinary textures");
        Path ignored = root.resolve("assets/minecraft/optifine/cit/notes/NOTES.txt");
        Files.write(ignored, new byte[]{4, 5});
        check(!directory.unchanged(), "ignored directory auxiliary files still participate in change detection");

        Path zipPath = root.resolve("Pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        byte[] original = Files.readAllBytes(zipPath);
        var zip = new ZipSource(zipPath);
        check(zip.mainCompatible(), "ZIP metadata remains compatible");
        check(zip.ids().equals(directory.ids()), "ZIP and directory use the same native path filter");
        check(Arrays.equals(zip.read(sword), directory.read(sword)), "ordinary ZIP texture remains readable");
        check(zip.snapshot().contains(sword), "native ZIP snapshot remains usable");
        check(UnsupportedResources.check(zip, MixerConfig.Kind.ITEM, AssetId.of("wooden_sword")) != null,
                "CIT presence does not invalidate a ZIP source");
        check(Arrays.equals(original, Files.readAllBytes(zipPath)), "compatibility filtering preserves original ZIP bytes");
        check(UnsupportedResources.check(new Assets.Memory(Map.of()), MixerConfig.Kind.ITEM, AssetId.of("wooden_sword")) == null,
                "ordinary packs do not acquire an extension note");
        return checks - initial;
    }

    private static Assets.Memory vanilla(Path jar) throws IOException {
        Map<AssetId, byte[]> files = new TreeMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith("assets/minecraft/")) continue;
                AssetId id = new AssetId("minecraft", entry.getName().substring("assets/minecraft/".length()));
                try (InputStream stream = zip.getInputStream(entry)) {
                    files.put(id, stream.readAllBytes());
                }
            }
        }
        return new Assets.Memory(files);
    }

    public static void main(String[] arguments) throws Exception {
        verifyFixtures();
        if (arguments.length > 0) {
            Assets vanilla = vanilla(Path.of(arguments[0]));
            for (int index = 1; index < arguments.length; index++) {
                Path path = Path.of(arguments[index]);
                String hash = Assets.hash(Files.readAllBytes(path));
                PackSource source = new ZipSource(path);
                check(source.mainCompatible(), "actual pack declares target compatibility");
                for (String item : List.of("wooden_sword", "diamond_sword", "enchanted_book")) {
                    UnsupportedResources.check(source, MixerConfig.Kind.ITEM, AssetId.of(item));
                    GraphCompiler.Result graph = new GraphCompiler(new Assets.View(source, vanilla), path.getFileName() + "|" + item)
                            .item(AssetId.of(item), null);
                    check(!graph.resources().isEmpty(), "actual pack ordinary item graph compiles");
                    System.out.println(path.getFileName() + " | " + item + " | " + graph.status());
                }
                check(source.unchanged(), "actual pack remains unchanged after graph compilation");
                check(hash.equals(Assets.hash(Files.readAllBytes(path))), "actual pack SHA-256 remains identical");
            }
        }
        System.out.println("Pack compatibility regression passed: " + checks + " checks");
    }
}
