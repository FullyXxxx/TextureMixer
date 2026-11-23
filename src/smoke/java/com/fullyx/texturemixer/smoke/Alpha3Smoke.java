package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Runs only in the dedicated run-smoke instance, using its existing synthetic world. */
public final class Alpha3Smoke {
    private static final String TEMPLATE_NAME = "Alpha3 native mix";
    private static final Map<String, Object> evidence = new LinkedHashMap<>();

    private Alpha3Smoke() {}

    public static void run(MinecraftClient client) throws Exception {
        Path report = client.runDirectory.toPath().resolve("alpha3-result.txt");
        evidence.clear();
        try {
            execute(client);
            Files.writeString(report, "PASS: folder ZIP/extracted-pack discovery with isolated per-pack failure; "
                    + "folder/standalone coexistence and restart persistence; management/detail/read-only Item preview; "
                    + "native template save/explicit overwrite/load confirmation/delete; source selections restored without global reload; "
                    + "always-on motion and management actions at GUI scales 1/2/3 and 320x240. "
                    + "Real-pack probe details and SHA checks are in alpha3-evidence.json. Screenshots require visual review.");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            Files.writeString(report, "FAIL: " + failure + "\n" + trace);
            throw new RuntimeException("Alpha3 native smoke failed", failure);
        } finally {
            Files.writeString(client.runDirectory.toPath().resolve("alpha3-evidence.json"), MixerConfig.GSON.toJson(evidence));
        }
    }

    public static void runPackApplications(MinecraftClient client) throws Exception {
        MixerService service = TextureMixerClient.SERVICE;
        await(client, () -> !service.busy && client.getOverlay() == null);
        MixerConfig original = onClient(client, () -> service.committed.copy());
        Path instance = client.runDirectory.toPath();
        List<Map<String, Object>> applications = new ArrayList<>();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("applications", applications);
        Throwable failure = null;
        try {
            String configured = System.getProperty("texturemixer.packProbeRoot", "");
            check(!configured.isBlank(), "packProbeRoot must identify the real-pack collection");
            Map<String, Path> packs = discoverRealPacks(Path.of(configured).toRealPath());
            check(packs.keySet().containsAll(List.of("pink", "exk", "faze")), "PINK/EXK/FaZe main packs were not all found");
            for (String kind : List.of("pink", "exk", "faze")) {
                Path path = packs.get(kind);
                Map<String, Object> application = new LinkedHashMap<>();
                application.put("kind", kind);
                application.put("path", path.toString());
                application.put("stage", "index");
                applications.add(application);
                String before = digest(path);
                application.put("sha256Before", before);
                MixerConfig probe = onClient(client, () -> {
                    MixerConfig config = service.committed.copy();
                    for (MixerConfig.Kind category : MixerConfig.Kind.values()) config.overrides(category).clear();
                    return config;
                });
                String sourceId = onClient(client, () -> service.add(path, probe, true)).join();
                String itemName = kind.equals("pink") ? "diamond_sword" : "wooden_sword";
                AssetId texture = AssetId.of("textures/item/" + itemName + ".png");
                byte[] expected = service.sources.get(sourceId).read(texture);
                application.put("stage", "apply");
                onClient(client, () -> service.apply(probe)).join();
                await(client, () -> !service.busy && client.getOverlay() == null);
                check(service.committed.mainSource.equals(sourceId), "Main-pack Apply did not commit " + kind);
                if (expected != null) {
                    byte[] actual = onClient(client, () -> {
                        try (InputStream input = client.getResourceManager()
                                .getResource(net.minecraft.util.Identifier.of(texture.toString())).orElseThrow().getInputStream()) {
                            return input.readAllBytes();
                        } catch (IOException error) { throw new CompletionException(error); }
                    });
                    check(Arrays.equals(expected, actual), "Applied ordinary texture differs from main source for " + kind);
                }
                String after = digest(path);
                check(before.equals(after), "Main-pack Apply changed original pack bytes for " + kind);
                application.put("texture", texture.toString());
                application.put("textureProvided", expected != null);
                application.put("sha256After", after);
                application.put("stage", "passed");
            }
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                await(client, () -> !service.busy && client.getOverlay() == null);
                MixerConfig restored = onClient(client, () -> {
                    MixerConfig config = service.committed.copy();
                    original.sources.forEach(config.sources::putIfAbsent);
                    config.setMainStack(original.mainStack());
                    for (MixerConfig.Kind kind : MixerConfig.Kind.values()) {
                        config.overrides(kind).clear();
                        config.overrides(kind).putAll(original.overrides(kind));
                    }
                    return config;
                });
                onClient(client, () -> service.apply(restored)).join();
                if (!original.enabled) onClient(client, service::disable).join();
                await(client, () -> !service.busy && client.getOverlay() == null);
                check(service.committed.renderSignature().equals(original.renderSignature()), "Original mix choices were not restored");
                report.put("originalChoicesRestored", true);
            } catch (Throwable restoreError) {
                report.put("originalChoicesRestored", false);
                report.put("restoreError", restoreError.toString());
                if (failure == null) failure = restoreError;
                else failure.addSuppressed(restoreError);
            }
            Files.writeString(instance.resolve("pack-apply-evidence.json"), MixerConfig.GSON.toJson(report));
        }
        if (failure != null) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            Files.writeString(instance.resolve("pack-apply-result.txt"), "FAIL: " + failure + "\n" + trace);
            throw new RuntimeException("Real main-pack Apply smoke failed", failure);
        }
        Files.writeString(instance.resolve("pack-apply-result.txt"), "PASS: PINK, EXK and FaZe each imported as main, applied through MixerService, "
                + "and supplied ordinary target texture bytes matched the active ResourceManager. Source SHA checks passed. "
                + "Original mix choices were restored using the latest source library.");
    }

    private static void execute(MinecraftClient client) throws Exception {
        MixerService service = TextureMixerClient.SERVICE;
        await(client, () -> !service.busy && client.getOverlay() == null);
        Generation mounted = service.mounted;
        int reloads = SmokeControls.reloads;
        String committedChoices = service.committed.renderSignature();
        Path fixtures = prepareFixtures(client);
        MixerScreen mixer = onClient(client, () -> new MixerScreen(service));
        String a = onClient(client, () -> service.add(fixtures.resolve("Alpha3-A.zip"), mixer.draft, false)).join();
        onClient(client, () -> { markStandalone(service, a); return true; });
        MixerLibrary.ImportResult imported = onClient(client, () -> service.library.importFolder(fixtures, mixer.draft)).join();
        evidence.put("folderImport", imported);
        check(imported.imported() == 3, "Expected three usable packs, got " + imported.imported());
        check(imported.failures().size() == 1 && imported.failures().getFirst().path().endsWith("Invalid.zip"),
                "Invalid pack did not remain an isolated import failure");
        String b = sourceAt(mixer.draft, fixtures.resolve("nested/Alpha3-B.zip"));
        String extracted = sourceAt(mixer.draft, fixtures.resolve("Extracted-Pack"));
        String groupId = imported.group().id();
        check(service.library.groupSourceIds(groupId, mixer.draft).containsAll(List.of(a, b, extracted)), "Group members are incomplete");
        check(service.library.rootSourceIds(mixer.draft).contains(a), "Directly added pack disappeared from root after folder import");
        check(!service.library.rootSourceIds(mixer.draft).contains(b), "New grouped pack also appeared as a standalone registration");
        MixerLibrary restarted = new MixerLibrary(service);
        check(restarted.group(groupId).name().equals(fixtures.getFileName().toString()), "Folder name did not survive restart");
        check(restarted.groupSourceIds(groupId, mixer.draft).size() == 3, "Folder membership did not survive restart");
        assertWorld(service, mounted, reloads, committedChoices);
        evidence.put("folderImport", Map.of("group", imported.group().name(), "imported", imported.imported(),
                "failures", imported.failures(), "members", imported.group().sourceIds()));

        onClient(client, () -> {
            client.options.getGuiScale().setValue(2);
            client.onResolutionChanged();
            mixer.sourceChanged();
            client.setScreen(mixer);
            click(client, SmokeLabels.text("管理"));
            assertManagerActions(client);
            return true;
        });
        capture(client, "alpha3-library-root.png");
        onClient(client, () -> { click(client, imported.group().name()); return true; });
        capture(client, "alpha3-library-group.png");
        onClient(client, () -> { click(client, "Alpha3-A.zip"); return true; });
        check(onClient(client, () -> client.currentScreen.getClass().getSimpleName().equals("PackDetailsScreen")), "Pack row did not open pack details");
        capture(client, "alpha3-library-pack-details.png");
        String beforePreview = mixer.draft.renderSignature();
        onClient(client, () -> { click(client, SmokeLabels.text("预览 Item")); search(client, "diamond sword"); click(client, "Diamond Sword"); return true; });
        awaitPreview(client, service, "minecraft:diamond_sword", a);
        onClient(client, () -> {
            MixerScreen preview = (MixerScreen) client.currentScreen;
            String signature = preview.draft.renderSignature();
            click(client, "Alpha3-A.zip");
            check(preview.draft.renderSignature().equals(signature), "Read-only preview changed its draft selection");
            check(buttons(client).stream().noneMatch(button -> button.getMessage().getString().equals(SmokeLabels.text("应用"))), "Read-only preview exposes Apply");
            return true;
        });
        capture(client, "alpha3-library-readonly-preview.png");
        check(mixer.draft.renderSignature().equals(beforePreview), "Library preview changed the editor's mix");
        assertWorld(service, mounted, reloads, committedChoices);

        testTemplates(client, service, mixer, a, b, mounted, reloads, committedChoices);
        testLayouts(client, mixer);
        deleteTestTemplate(client, service, mixer);
        testRealPacks(client, service, mounted, reloads, committedChoices);
        assertWorld(service, mounted, reloads, committedChoices);
        check(!UiDraw.reduceMotion(), "Motion is not enabled by default");
    }

    private static Path prepareFixtures(MinecraftClient client) throws IOException {
        Path instance = client.runDirectory.toPath().toAbsolutePath().normalize();
        Path originals = instance.resolve("fixtures");
        Path root = instance.resolve("alpha3-library-fixtures");
        Files.createDirectories(root.resolve("nested"));
        Files.copy(originals.resolve("Pack-A.zip"), root.resolve("Alpha3-A.zip"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(originals.resolve("Pack-B.zip"), root.resolve("nested/Alpha3-B.zip"), StandardCopyOption.REPLACE_EXISTING);
        Path extracted = root.resolve("Extracted-Pack");
        Files.createDirectories(extracted.resolve("assets/minecraft/textures/item"));
        Files.copy(originals.resolve("Pack-B/assets/minecraft/textures/item/emerald.png"),
                extracted.resolve("assets/minecraft/textures/item/emerald.png"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(originals.resolve("Pack-B/assets/minecraft/textures/item/emerald.png"), extracted.resolve("pack.png"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(extracted.resolve("pack.mcmeta"), "{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"Alpha3 test only\"}}");
        Files.writeString(root.resolve("Invalid.zip"), "Deliberately invalid ZIP fixture");
        return root.toRealPath();
    }

    private static void testTemplates(MinecraftClient client, MixerService service, MixerScreen mixer,
                                      String a, String b, Generation mounted, int reloads, String committedChoices) throws Exception {
        onClient(client, () -> {
            client.setScreen(mixer);
            click(client, SmokeLabels.text("保存模板"));
            if (service.library.templateByName(TEMPLATE_NAME) != null) deleteTemplateRow(client, TEMPLATE_NAME);
            mixer.draft.itemOverrides.put("minecraft:diamond_sword", a);
            mixer.draft.itemOverrides.put("minecraft:emerald", b);
            nameField(client).setText(TEMPLATE_NAME);
            click(client, SmokeLabels.text("保存当前搭配"));
            return true;
        });
        TemplateStore.Summary saved = service.library.templateByName(TEMPLATE_NAME);
        check(saved != null, "Saving through TemplatesScreen did not create a named template");
        capture(client, "alpha3-templates-saved.png");
        onClient(client, () -> {
            mixer.draft.blockOverrides.put("minecraft:diamond_block", b);
            mixer.draft.skyOverrides.put("overworld", MixerConfig.VANILLA);
            click(client, SmokeLabels.text("保存当前搭配"));
            check(client.currentScreen instanceof ChoiceScreen, "Existing template was overwritten without confirmation");
            return true;
        });
        capture(client, "alpha3-template-overwrite-confirmation.png");
        onClient(client, () -> { click(client, SmokeLabels.text("替换模板")); return true; });
        TemplateStore.Summary overwritten = service.library.templateByName(TEMPLATE_NAME);
        check(saved.id().equals(overwritten.id()) && saved.createdAt() == overwritten.createdAt(), "Overwrite replaced template identity");
        MixerConfig expected = onClient(client, () -> mixer.draft.copy());
        onClient(client, () -> {
            mixer.draft.setMainStack(List.of());
            for (MixerConfig.Kind kind : MixerConfig.Kind.values()) mixer.draft.overrides(kind).clear();
            mixer.draft.itemOverrides.put("minecraft:stick", a);
            click(client, TEMPLATE_NAME);
            check(client.currentScreen instanceof ChoiceScreen, "Replacing a dirty draft did not request confirmation");
            return true;
        });
        capture(client, "alpha3-template-load-confirmation.png");
        onClient(client, () -> { click(client, SmokeLabels.text("载入模板")); return true; });
        await(client, () -> client.currentScreen == mixer);
        check(mixer.draft.mainSource.equals(expected.mainSource), "Template main source was not restored");
        for (MixerConfig.Kind kind : MixerConfig.Kind.values()) {
            check(mixer.draft.overrides(kind).equals(expected.overrides(kind)), "Template did not restore exact " + kind + " choices");
        }
        MixerLibrary reopened = new MixerLibrary(service);
        check(reopened.templateByName(TEMPLATE_NAME).id().equals(saved.id()), "Named template was lost after controller restart");
        MixerConfig reopenedDraft = onClient(client, () -> reopened.loadTemplate(saved.id(), mixer.draft)).join();
        check(reopenedDraft.renderSignature().equals(mixer.draft.renderSignature()), "Restarted template did not restore the same choices");
        assertWorld(service, mounted, reloads, committedChoices);
        evidence.put("template", Map.of("name", TEMPLATE_NAME, "id", saved.id(), "sourceCount", expected.sources.size(),
                "itemChoices", expected.itemOverrides, "blockChoices", expected.blockOverrides, "skyChoices", expected.skyOverrides));
        capture(client, "alpha3-template-restored-draft.png");
    }

    private static void testLayouts(MinecraftClient client, MixerScreen mixer) throws Exception {
        onClient(client, () -> { GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720); return true; });
        await(client, () -> client.getWindow().getWidth() == 1280 && client.getWindow().getHeight() == 720);
        for (int scale : new int[]{1, 2, 3}) {
            onClient(client, () -> {
                client.options.getGuiScale().setValue(scale);
                client.onResolutionChanged();
                client.setScreen(new SourceManagerScreen(mixer));
                assertManagerActions(client);
                assertControlBounds(client);
                return true;
            });
            capture(client, "alpha3-manager-scale-" + scale + ".png");
            onClient(client, () -> { client.setScreen(new TemplatesScreen(mixer)); assertControlBounds(client); return true; });
            capture(client, "alpha3-templates-scale-" + scale + ".png");
        }
        onClient(client, () -> { GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 960, 720); return true; });
        await(client, () -> client.getWindow().getScaledWidth() == 320 && client.getWindow().getScaledHeight() == 240);
        onClient(client, () -> { client.setScreen(new SourceManagerScreen(mixer)); assertManagerActions(client); assertControlBounds(client); return true; });
        capture(client, "alpha3-manager-320x240.png");
        onClient(client, () -> { client.setScreen(new TemplatesScreen(mixer)); assertControlBounds(client); return true; });
        capture(client, "alpha3-templates-320x240.png");
        onClient(client, () -> {
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
            client.options.getGuiScale().setValue(2);
            client.onResolutionChanged();
            return true;
        });
        await(client, () -> client.getWindow().getWidth() == 1280);
        evidence.put("layoutScales", List.of(1, 2, 3));
        evidence.put("compactLogicalViewport", "320x240");
    }

    private static void deleteTestTemplate(MinecraftClient client, MixerService service, MixerScreen mixer) {
        onClient(client, () -> {
            client.setScreen(new TemplatesScreen(mixer));
            deleteTemplateRow(client, TEMPLATE_NAME);
            check(service.library.templateByName(TEMPLATE_NAME) == null, "Dedicated test template remained after confirmed deletion");
            return true;
        });
    }

    private static void deleteTemplateRow(MinecraftClient client, String name) {
        ButtonWidget row = button(client, name);
        ButtonWidget delete = buttons(client).stream()
                .filter(candidate -> candidate.getMessage().getString().equals(SmokeLabels.text("删除")) && candidate.getY() == row.getY())
                .findFirst().orElseThrow();
        click(client, delete);
        check(client.currentScreen instanceof ChoiceScreen, "Template deletion did not request confirmation");
        click(client, SmokeLabels.text("删除模板"));
    }

    private static void testRealPacks(MinecraftClient client, MixerService service, Generation mounted,
                                      int reloads, String committedChoices) throws Exception {
        String configured = System.getProperty("texturemixer.packProbeRoot", "");
        if (configured.isBlank()) {
            evidence.put("realPackProbes", "Not configured");
            return;
        }
        Path root = Path.of(configured).toRealPath();
        Map<String, Path> found = discoverRealPacks(root);
        check(found.keySet().containsAll(List.of("pink", "exk", "faze")), "PINK/EXK/FaZe were not all found under " + root + ": " + found);
        List<Map<String, Object>> results = new ArrayList<>();
        evidence.put("realPackProbes", results);
        for (String kind : List.of("pink", "exk", "faze")) {
            Path pack = found.get(kind);
            evidence.put("activeRealPack", kind + " " + pack);
            String before = digest(pack);
            MixerScreen screen = onClient(client, () -> new MixerScreen(service));
            String source = onClient(client, () -> service.add(pack, screen.draft, false)).join();
            onClient(client, () -> { markStandalone(service, source); return true; });
            String item = kind.equals("pink") ? "minecraft:diamond_sword" : "minecraft:wooden_sword";
            onClient(client, () -> {
                client.setScreen(screen);
                search(client, item.substring("minecraft:".length()).replace('_', ' '));
                click(client, service.catalog.item(item).name());
                search(client, screen.sourceName(source));
                return true;
            });
            PreviewCache.Entry preview = awaitPreview(client, service, item, source);
            onClient(client, () -> {
                ButtonWidget card = button(client, screen.sourceName(source));
                check(card.active, "Imported pack's ready preview card is disabled");
                click(client, card);
                check(source.equals(screen.draft.itemOverrides.get(item)), "Choosing imported pack did not update the draft");
                return true;
            });
            capture(client, "alpha3-realpack-" + kind + ".png");
            String after = digest(pack);
            check(before.equals(after), "Original pack bytes changed: " + pack);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", kind);
            result.put("path", pack.toString());
            result.put("item", item);
            result.put("ready", preview.ready());
            result.put("error", preview.error);
            result.put("note", preview.note);
            result.put("sha256Before", before);
            result.put("sha256After", after);
            results.add(result);
            assertWorld(service, mounted, reloads, committedChoices);
        }
        evidence.remove("activeRealPack");
    }

    private static Map<String, Path> discoverRealPacks(Path root) throws IOException {
        Map<String, Path> found = new LinkedHashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private void consider(Path path) {
                String name = MixerScreen.plainName(path.getFileName().toString()).toLowerCase(Locale.ROOT);
                for (String kind : List.of("pink", "exk", "faze")) if (name.contains(kind)) found.putIfAbsent(kind, path);
            }
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (Files.isDirectory(directory.resolve("assets"), LinkOption.NOFOLLOW_LINKS)) {
                    consider(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) consider(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    private static String digest(Path source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> files;
        if (Files.isDirectory(source)) {
            try (var paths = Files.walk(source)) { files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList(); }
        } else files = List.of(source);
        byte[] buffer = new byte[65536];
        for (Path file : files) {
            if (Files.isDirectory(source)) {
                digest.update(source.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(Files.size(file)).array());
            }
            try (InputStream input = Files.newInputStream(file)) {
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static PreviewCache.Entry awaitPreview(MinecraftClient client, MixerService service, String item, String source) throws Exception {
        await(client, () -> {
            PreviewCache.Entry entry = service.previews.get(service.catalog.item(item), source, 0);
            if (entry.error != null) throw new AssertionError("Preview failed for " + item + " from " + source + ": " + entry.error);
            return entry.ready();
        });
        return onClient(client, () -> service.previews.get(service.catalog.item(item), source, 0));
    }

    private static void assertWorld(MixerService service, Generation mounted, int reloads, String committedChoices) {
        check(service.mounted == mounted, "Browsing/import/template operation changed the mounted generation");
        check(SmokeControls.reloads == reloads, "Browsing/import/template operation triggered a global reload");
        check(service.committed.renderSignature().equals(committedChoices), "Library operation submitted draft rendering choices");
    }

    private static void assertManagerActions(MinecraftClient client) {
        ButtonWidget pack = button(client, SmokeLabels.text("添加材质包"));
        ButtonWidget folder = button(client, SmokeLabels.text("添加文件夹"));
        ButtonWidget back = button(client, SmokeLabels.text("返回"));
        check(pack.getY() == folder.getY() && folder.getX() > pack.getX(), "Manager import actions are not on one row");
        check(back.getY() > pack.getY() && back.getWidth() > pack.getWidth(), "Manager Return is not a separate full row");
        check(buttons(client).stream().noneMatch(candidate -> candidate.getMessage().getString().contains(SmokeLabels.text("动效"))
                || candidate.getMessage().getString().contains(SmokeLabels.text("停用混搭"))), "Removed manager controls are still present");
    }

    private static void assertControlBounds(MinecraftClient client) {
        for (ButtonWidget button : buttons(client)) {
            check(button.getX() >= 0 && button.getY() >= 0 && button.getX() + button.getWidth() <= client.currentScreen.width
                    && button.getY() + button.getHeight() <= client.currentScreen.height,
                    "Button lies outside viewport: " + button.getMessage().getString());
        }
    }

    private static void markStandalone(MixerService service, String id) {
        try { service.library.markStandalone(id); }
        catch (IOException error) { throw new CompletionException(error); }
    }

    private static String sourceAt(MixerConfig config, Path path) throws IOException {
        Path expected = path.toRealPath();
        return config.sources.values().stream().filter(source -> Path.of(source.canonicalRoot()).equals(expected))
                .map(MixerConfig.SourceRecord::id).findFirst().orElseThrow();
    }

    private static TextFieldWidget nameField(MinecraftClient client) {
        return client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance)
                .map(TextFieldWidget.class::cast).findFirst().orElseThrow();
    }

    private static void search(MinecraftClient client, String query) { nameField(client).setText(query); }

    private static List<ButtonWidget> buttons(MinecraftClient client) {
        return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
    }

    private static ButtonWidget button(MinecraftClient client, String label) {
        List<ButtonWidget> matches = buttons(client).stream()
                .filter(candidate -> candidate.getMessage().getString().equals(label)).toList();
        if (matches.isEmpty()) throw new AssertionError("Button not found: " + label);
        return matches.stream().filter(candidate -> candidate.active).findFirst().orElse(matches.getFirst());
    }

    private static void click(MinecraftClient client, String label) { click(client, button(client, label)); }

    private static void click(MinecraftClient client, ButtonWidget button) {
        check(button.active, "Attempted to activate a disabled button: " + button.getMessage().getString());
        Screen screen = client.currentScreen;
        screen.mouseClicked(new Click(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0,
                new MouseInput(0, 0)), false);
    }

    private static void capture(MinecraftClient client, String name) throws Exception {
        Thread.sleep(250);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        onClient(client, () -> {
            ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1, message -> saved.complete(null));
            return true;
        });
        saved.get(15, TimeUnit.SECONDS);
        check(Files.isRegularFile(client.runDirectory.toPath().resolve("screenshots").resolve(name)), "Screenshot was not saved: " + name);
    }

    private static void await(MinecraftClient client, Supplier<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        while (!onClient(client, condition)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timeout waiting for native alpha3 state");
            Thread.sleep(100);
        }
    }

    private static <T> T onClient(MinecraftClient client, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        client.execute(() -> {
            try { result.complete(action.get()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.join();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
