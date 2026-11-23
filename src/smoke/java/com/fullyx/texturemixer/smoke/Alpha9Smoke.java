package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.MixerConfig;
import com.fullyx.texturemixer.ui.MixerScreen;
import com.fullyx.texturemixer.ui.SourceManagerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

public final class Alpha9Smoke {
    public static void run(MinecraftClient client) throws Exception {
        var service = TextureMixerClient.SERVICE;
        MixerConfig original = onClient(client, () -> service.committed.copy());
        var nativeIds = new ArrayList<>(client.getResourcePackManager().getEnabledIds());
        int scale = client.options.getGuiScale().getValue();
        Path root = client.getResourcePackDir().toRealPath();
        Path standalone = root.resolve("alpha9-Automatic.zip");
        Path collection = root.resolve("alpha9-Collection");
        Path nested = collection.resolve("Nested.zip");
        Path extracted = root.resolve("alpha9-Extracted");
        Path live = root.resolve("alpha9-Live.zip");
        String[] groupId = {null}, templateId = {null};
        try {
            for (Path path : List.of(standalone, collection, extracted, live)) {
                if (Files.exists(path)) throw new AssertionError("Fixture path already exists " + path);
            }
            Path fixtures = client.runDirectory.toPath().resolve("fixtures");
            Files.copy(fixtures.resolve("Pack-A.zip"), standalone);
            Files.createDirectories(collection);
            Files.copy(fixtures.resolve("Pack-B.zip"), nested);
            try (ZipFile zip = new ZipFile(fixtures.resolve("Main-Pack.zip").toFile())) {
                for (var entry : Collections.list(zip.entries())) {
                    Path target = extracted.resolve(entry.getName()).normalize();
                    check(target.startsWith(extracted), "fixture entry stays in test directory");
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        try (var input = zip.getInputStream(entry)) { Files.copy(input, target); }
                    }
                }
            }
            MixerScreen mixer = onClient(client, () -> {
                var first=service.library.discoverResourcePacks();
                check(first==service.library.discoverResourcePacks(),"overlapping discovery requests share one scan");
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                var screen = new MixerScreen(service);
                client.setScreen(screen);
                return screen;
            });
            await(client, () -> ready(mixer));
            await(client, () -> source(mixer,standalone)!=null&&source(mixer,extracted)!=null&&source(mixer,nested)!=null);
            String a = onClient(client, () -> source(mixer, standalone));
            String b = onClient(client, () -> source(mixer, extracted));
            check(a != null && b != null && onClient(client, () -> source(mixer, nested)) != null,
                    "ZIP, extracted pack and nested ZIP are discovered without registration calls");
            groupId[0] = onClient(client, () -> service.library.groups().stream()
                    .filter(group -> Path.of(group.canonicalRoot()).equals(collection)).findFirst().orElseThrow().id());
            int reloads = SmokeControls.reloads;
            onClient(client, () -> {
                mixer.draft.setMainStack(List.of());
                search(client, "alpha9");
                return true;
            });
            capture(client, "alpha9-auto-packs.png");
            onClient(client, () -> {
                packAction(client, "添加", "alpha9-Automatic.zip");
                packAction(client, "添加", "alpha9-Extracted");
                return true;
            });
            check(mixer.draft.mainStack().equals(List.of(b, a)), "add button adds a top-priority main pack");
            onClient(client, () -> {
                packAction(client, "上移，提高优先级", "alpha9-Automatic.zip");
                return true;
            });
            check(mixer.draft.mainStack().equals(List.of(a, b)), "up button moves pack up");
            onClient(client, () -> {
                var row = button(client, "alpha9-Automatic.zip");
                client.currentScreen.setFocused(row);
                client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_SHIFT));
                return true;
            });
            check(mixer.draft.mainStack().equals(List.of(b, a)), "Shift Down reorders native pack row");
            onClient(client, () -> {
                client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_UP,0,GLFW.GLFW_MOD_SHIFT));
                check(mixer.draft.mainStack().equals(List.of(a,b)),"keyboard sorting keeps focus for repeated input");
                client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_DOWN,0,GLFW.GLFW_MOD_SHIFT));
                return true;
            });
            onClient(client, () -> {
                var row = button(client, "alpha9-Automatic.zip");
                client.currentScreen.setFocused(row);
                double factor = client.getWindow().getScaleFactor();
                GLFW.glfwSetCursorPos(client.getWindow().getHandle(), (row.getX() + 12) * factor, (row.getY() + 18) * factor);
                return true;
            });
            capture(client, "alpha13-pack-actions.png");
            onClient(client, () -> {
                packAction(client, "移除", "alpha9-Automatic.zip");
                return true;
            });
            check(mixer.draft.mainStack().equals(List.of(b)), "remove button removes selected pack");
            await(client, () -> button(client, SmokeLabels.text("撤销")).active);
            onClient(client, () -> { click(client, SmokeLabels.text("撤销")); return true; });
            check(mixer.draft.mainStack().equals(List.of(b, a)), "Undo restores native removal");
            onClient(client, () -> {
                click(client, "alpha9-Automatic.zip");
                check(client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance)
                        .map(ButtonWidget.class::cast).noneMatch(widget -> widget.getMessage().getString().equals(SmokeLabels.text("重新定位"))),
                        "pack details has no relocate control");
                return true;
            });
            capture(client, "alpha13-pack-details.png");
            onClient(client, () -> { client.currentScreen.close(); return true; });
            onClient(client, () -> {
                mixer.edit(SmokeLabels.text("测试混搭"), () -> {
                    for (var kind : MixerConfig.Kind.values()) {
                        String target = kind == MixerConfig.Kind.SKY ? "overworld"
                                : kind == MixerConfig.Kind.ITEM ? "minecraft:diamond_sword"
                                : service.catalog.list(kind).getFirst().id();
                        mixer.draft.overrides(kind).put(target, a);
                    }
                });
                try { templateId[0] = service.library.saveTemplate("Alpha9 native reset", mixer.draft).id(); }
                catch (Exception error) { throw new CompletionException(error); }
                return true;
            });
            String before = mixer.draft.renderSignature();
            onClient(client, () -> { click(client, SmokeLabels.text("重置混搭")); click(client, SmokeLabels.text("取消")); return true; });
            check(before.equals(mixer.draft.renderSignature()), "reset cancellation keeps draft");
            onClient(client, () -> { click(client, SmokeLabels.text("重置混搭")); return true; });
            capture(client, "alpha13-reset-mix-confirm.png");
            onClient(client, () -> { click(client, SmokeLabels.text("确定")); return true; });
            check(mixer.draft.mainStack().equals(List.of(b, a)), "reset mix keeps main packs and priority");
            check(Arrays.stream(MixerConfig.Kind.values()).allMatch(kind -> mixer.draft.overrides(kind).isEmpty()), "reset clears every override category");
            check(service.library.templateByName("Alpha9 native reset") != null && mixer.draft.sources.containsKey(a), "reset preserves template and source library");
            check(SmokeControls.reloads == reloads, "all editing remains staged");
            await(client, () -> button(client, SmokeLabels.text("撤销")).active);
            onClient(client, () -> { click(client, SmokeLabels.text("撤销")); return true; });
            check(before.equals(mixer.draft.renderSignature()), "reset mix is undoable");
            onClient(client, () -> {
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                client.setScreen(mixer);
                click(client, "alpha9-Extracted");
                assertBounds(client);
                return true;
            });
            capture(client, "alpha13-details-320x240.png");
            onClient(client, () -> { click(client,SmokeLabels.text("选择")); check(client.currentScreen==mixer,"details Select returns directly to the editor without confirmation"); client.setScreen(new SourceManagerScreen(mixer)); assertBounds(client); return true; });
            capture(client,"alpha13-manager-320x240.png");
            onClient(client, () -> { client.currentScreen.close(); click(client,SmokeLabels.text("自定义套装")); assertBounds(client); return true; });
            capture(client,"alpha13-templates-320x240.png");
            onClient(client, () -> {
                client.currentScreen.close();
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                return true;
            });
            Files.copy(fixtures.resolve("Pack-B.zip"), live);
            await(client, () -> source(mixer, live) != null);
            String liveId = source(mixer, live);
            Files.delete(live);
            await(client, () -> !service.library.availableSourceIds(mixer.draft).contains(liveId));
            String fingerprint = mixer.draft.sources.get(a).lastKnownFingerprint();
            Files.copy(fixtures.resolve("Pack-B.zip"), standalone, StandardCopyOption.REPLACE_EXISTING);
            await(client, () -> !fingerprint.equals(mixer.draft.sources.get(a).lastKnownFingerprint()));
            check(source(mixer, standalone).equals(a), "changed ZIP refreshes in place with stable source ID");
            onClient(client, () -> {
                MixerConfig unconfigured = original.copy();
                unconfigured.enabled = false;
                unconfigured.setMainStack(List.of());
                service.committed = unconfigured;
                client.getResourcePackManager().scanPacks();
                var enabled = new ArrayList<>(nativeIds);
                enabled.add("file/alpha9-Automatic.zip");
                client.getResourcePackManager().setEnabledProfiles(enabled);
                client.setScreen(new MixerScreen(service));
                return true;
            });
            await(client, () -> client.currentScreen instanceof MixerScreen screen && ready(screen));
            var inherited = (MixerScreen)client.currentScreen;
            check(inherited.draft.mainStack().contains(source(inherited, standalone)), "first configuration inherits enabled native file packs");
        } catch (Throwable error) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            Files.writeString(client.runDirectory.toPath().resolve("alpha9-result.txt"), "FAIL: " + trace);
            throw error;
        } finally {
            onClient(client, () -> {
                client.setScreen(null);
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);
                client.getResourcePackManager().setEnabledProfiles(nativeIds);
                client.options.getGuiScale().setValue(scale);
                client.onResolutionChanged();
                try {
                    if (groupId[0] != null) service.library.removeGroup(groupId[0]);
                    if (templateId[0] != null) service.library.deleteTemplate(templateId[0]);
                } catch (Exception error) { throw new CompletionException(error); }
                return true;
            });
            for (Path path : List.of(standalone, collection, extracted, live)) removeFixture(root, path);
            onClient(client, () -> service.apply(original)).join();
        }
        Alpha7Smoke.run(client);
        Files.writeString(client.runDirectory.toPath().resolve("alpha9-result.txt"),
                "PASS: automatic ZIP/extracted/nested discovery, live additions/removals/changes, native enabled-pack inheritance, dedicated row and keyboard actions with Undo, no relocate control, category reset preserving main stack/library/template, staged edits, full alpha7 interaction and layout regression.");
    }

    private static boolean ready(MixerScreen screen) {
        try {
            var field = MixerScreen.class.getDeclaredField("discovered");
            field.setAccessible(true);
            return field.getBoolean(screen) && !screen.libraryImporting();
        } catch (Exception error) { throw new CompletionException(error); }
    }
    private static String source(MixerScreen mixer, Path path) {
        return mixer.draft.sources.values().stream().filter(record -> Path.of(record.canonicalRoot()).equals(path))
                .map(MixerConfig.SourceRecord::id).findFirst().orElse(null);
    }
    private static ButtonWidget button(MinecraftClient client, String name) {
        return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(name)).findFirst().orElseThrow(() -> new AssertionError("Missing button " + name));
    }
    private static void assertBounds(MinecraftClient client) {
        Screen screen=client.currentScreen;
        for(Object child:screen.children()){
            if(child instanceof ButtonWidget widget){
                check(widget.getX()>=0&&widget.getY()>=0&&widget.getRight()<=screen.width
                        &&widget.getBottom()<=screen.height,"control stays in compact viewport "+widget.getMessage().getString());
            }
        }
    }
    private static void click(MinecraftClient client, String name) {
        var widget = button(client, name);
        check(widget.active, "enabled control " + name);
        clickAt(client, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }
    private static void packAction(MinecraftClient client, String label, String name) {
        var row = button(client, name);
        var action = SmokeLabels.packAction(client,label,name);
        check(action.active && action.getX()>=row.getRight() && action.getY()>=row.getY()
                && action.getBottom()<=row.getBottom(),"separate action on the same row "+name);
        clickAt(client,action.getX()+action.getWidth()/2.0,action.getY()+action.getHeight()/2.0);
    }
    private static void clickAt(MinecraftClient client, double x, double y) {
        Screen screen = client.currentScreen;
        Click input = new Click(x, y, new MouseInput(0, 0));
        check(screen.mouseClicked(input, false), "handled native input");
        screen.mouseReleased(input);
    }
    private static void search(MinecraftClient client, String value) {
        client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast)
                .findFirst().orElseThrow().setText(value);
    }
    private static void capture(MinecraftClient client, String name) throws Exception {
        await(client, () -> client.getOverlay() == null);
        Thread.sleep(450);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        onClient(client, () -> {
            assertBounds(client);
            ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1, ignored -> saved.complete(null));
            return true;
        });
        saved.get(15, TimeUnit.SECONDS);
    }
    private static void await(MinecraftClient client, Supplier<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (!onClient(client, condition)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timeout");
            Thread.sleep(100);
        }
    }
    private static <T> T onClient(MinecraftClient client, Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        client.execute(() -> {
            try { future.complete(action.get()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future.join();
    }
    private static void removeFixture(Path root, Path path) throws Exception {
        check(path.toAbsolutePath().normalize().startsWith(root) && path.getFileName().toString().startsWith("alpha9-"), "fixture cleanup stays in test root");
        if (Files.exists(path)) {
            try (var entries = Files.walk(path)) {
                for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(entry);
            }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
