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
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import java.nio.file.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Native input and resource regression checks confined to the dedicated synthetic instance. */
public final class Alpha4Smoke {
    private static final Map<String,Object> evidence = new LinkedHashMap<>();
    public static void run(MinecraftClient client) throws Exception {
        Path result = client.runDirectory.toPath().resolve("alpha4-result.txt");
        MixerService service = TextureMixerClient.SERVICE;
        MixerConfig original = onClient(client, () -> service.committed.copy());
        int scale = client.options.getGuiScale().getValue();
        try {
            Path fixtures = client.runDirectory.toPath().resolve("fixtures").toAbsolutePath();
            MixerScreen mixer = onClient(client, () -> new MixerScreen(service));
            String a = onClient(client, () -> service.add(fixtures.resolve("Main-Pack"), mixer.draft, false)).join();
            String b = onClient(client, () -> service.add(fixtures.resolve("Pack-A"), mixer.draft, false)).join();
            var imported = onClient(client, () -> service.library.importFolder(fixtures.resolve("Main-Pack"), mixer.draft)).join();
            for (int guiScale : List.of(1, 2, 3)) {
                onClient(client, () -> {
                    client.options.getGuiScale().setValue(guiScale); client.onResolutionChanged();
                    client.setScreen(new SourceManagerScreen(mixer)); return true;
                });
                onClient(client, () -> {
                    searchFocus(client);
                    search(client, "main pack");
                    check(rows(client).stream().anyMatch(button -> button.getMessage().getString().equals("Main-Pack")), "root folder Search");
                    search(client, "zz_missing");
                    check(rows(client).isEmpty(), "empty Search result");
                    search(client, "");
                    drag(client, true);
                    check(((Number)field(client.currentScreen, "offset")).intValue() > 0, "manager scrollbar reaches bottom");
                    int before=((Number)field(client.currentScreen,"offset")).intValue();
                    client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_UP,0,0));
                    check(((Number)field(client.currentScreen,"offset")).intValue()==before-1,"scrollbar keyboard moves one row");
                    drag(client, false);
                    check(((Number)field(client.currentScreen, "offset")).intValue() == 0, "manager scrollbar returns to top");
                    return true;
                });
                capture(client, "alpha4-manager-scale-" + guiScale + ".png");
                onClient(client, () -> {
                    client.setScreen(new SourceManagerScreen(mixer, imported.group().id(), ""));
                    search(client, "Pack-A"); check(rows(client).isEmpty(), "folder Search stays in folder");
                    search(client, "main-pack"); check(rows(client).size() == 1, "folder Search finds its member");
                    mixer.draft.setMainStack(List.of());
                    client.setScreen(new SourcePickerScreen(mixer));
                    searchFocus(client);
                    search(client, "Main-Pack");
                    var row = buttons(client).stream().filter(button -> button.getMessage().getString().equals("Main-Pack")).findFirst().orElseThrow();
                    click(client, row); check(mixer.draft.mainStack().contains(a), "main picker adds layer");
                    search(client, "Pack-A");
                    click(client, buttons(client).stream().filter(button -> button.getMessage().getString().equals("Pack-A")).findFirst().orElseThrow());
                    check(mixer.draft.mainStack().contains(b), "main picker adds second layer");
                    mixer.draft.setMainStack(List.of(a,b));
                    search(client, "");
                    click(client, buttons(client).stream().filter(button -> button instanceof FlatButton flat
                            && SmokeLabels.text("上移，提高优先级").equals(flat.narrationLabel) && button.active).findFirst().orElseThrow());
                    check(mixer.draft.mainStack().equals(List.of(b,a)), "native main layer move button");
                    mixer.draft.setMainStack(List.of(a,b));
                    search(client,"refresh");search(client, "");
                    drag(client, true); check(((Number)field(client.currentScreen, "offset")).intValue() > 0, "picker scrollbar");
                    drag(client, false);
                    return true;
                });
                capture(client, "alpha4-picker-scale-" + guiScale + ".png");
                onClient(client, () -> { mixer.draft.setMainStack(List.of()); return true; });
            }
            onClient(client, () -> {
                client.setScreen(new SourceManagerScreen(mixer));
                search(client, "Main-Pack");
                var folder = rows(client).stream().filter(button -> button.getMessage().getString().equals("Main-Pack")).findFirst().orElseThrow();
                click(client, buttons(client).stream().filter(button -> button.getMessage().getString().equals(SmokeLabels.text("移除")) && button.getY() == folder.getY()+6).findFirst().orElseThrow());
                click(client, button(client, SmokeLabels.text("取消")));
                check(service.library.group(imported.group().id()) != null, "cancel retains folder");
                click(client, buttons(client).stream().filter(button -> button.getMessage().getString().equals(SmokeLabels.text("移除")) && button.getY() == folder.getY()+6).findFirst().orElseThrow());
                click(client, button(client, SmokeLabels.text("移除文件夹")));
                check(service.library.group(imported.group().id()) == null, "folder removal persisted");
                check(service.library.rootSourceIds(mixer.draft).contains(a), "folder member retained");
                mixer.draft.setMainStack(List.of(a,b));
                for (var kind : MixerConfig.Kind.values()) mixer.draft.overrides(kind).clear();
                return true;
            });
            AssetId sword = AssetId.of("textures/item/diamond_sword.png");
            byte[] expected = service.sources.get(a).read(sword);
            AssetId stone=AssetId.of("textures/block/stone.png");
            Generation compiled = CompletableFuture.supplyAsync(() -> {
                try { return Generation.compile(mixer.draft, service.vanilla, service.sources, service.catalog); }
                catch (IOException error) { throw new CompletionException(error); }
            }).join();
            check(Arrays.equals(compiled.assets().read(sword), expected), "top main layer wins compilation");
            check(Arrays.equals(Generation.view(mixer.draft.mainView(),service.sources,service.vanilla).read(sword), expected), "stack preview sees same priority");
            onClient(client, () -> service.apply(mixer.draft)).join();
            await(client, () -> !service.busy && client.getOverlay() == null);
            onClient(client, () -> {
                try (var stream = client.getResourceManager().getResource(Identifier.of(sword.toString())).orElseThrow().getInputStream()) {
                    check(Arrays.equals(stream.readAllBytes(), expected), "native resource manager top-layer priority");
                } catch (IOException error) { throw new CompletionException(error); }
                mixer.draft.moveMain(b,-1); return true;
            });
            onClient(client, () -> service.apply(mixer.draft)).join();
            await(client, () -> !service.busy && client.getOverlay() == null);
            onClient(client, () -> {
                try (var stream = client.getResourceManager().getResource(Identifier.of(sword.toString())).orElseThrow().getInputStream()) {
                    check(Arrays.equals(stream.readAllBytes(), service.sources.get(b).read(sword)), "native reordered top-layer priority");
                    try(var lower=client.getResourceManager().getResource(Identifier.of(stone.toString())).orElseThrow().getInputStream()) {
                        check(Arrays.equals(lower.readAllBytes(),service.sources.get(a).read(stone)),"lower main layer fills missing resource");
                    }
                } catch (IOException error) { throw new CompletionException(error); }
                client.options.getGuiScale().setValue(1); client.onResolutionChanged();
                client.setScreen(mixer); search(client,"diamond leggings");
                click(client, button(client,"Diamond Leggings"));
                return true;
            });
            String probeRoot=System.getProperty("texturemixer.packProbeRoot", "");
            if(!probeRoot.isBlank()) {
                List<Path> packs;
                try(var files=Files.list(Path.of(probeRoot))){packs=files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")).sorted().toList();}
                Map<String,String> hashes=new LinkedHashMap<>();
                for(Path path:packs) {
                    PackSource pack;
                    try{pack=PackSource.open(path);}catch(IOException error){continue;}
                    hashes.put(path.toString(),pack.fingerprint());
                    onClient(client,()->{
                        String id=UUID.randomUUID().toString();service.sources.put(id,pack);
                        mixer.draft.sources.put(id,new MixerConfig.SourceRecord(id,pack.root().toString(),pack.name(),pack.fingerprint()));
                        mixer.draft.addCandidate(id);return true;
                    });
                }
                onClient(client,()->{mixer.refreshCards();return true;});
                await(client,()->service.previews.usage().pending()==0);
                Map<String,PreviewCache.Entry> visible=new LinkedHashMap<>();
                onClient(client,()->{
                    for(Object card:(List<?>)field(mixer,"cards")) {
                        if(((ButtonWidget)card).getY()>=(int)field(mixer,"gridBottom"))continue;
                        String source=(String)field(card,"source");
                        var entry=service.previews.get(service.catalog.item("minecraft:diamond_leggings"),source,0);
                        check(entry.ready(),"real-pack visible leggings preview must be ready: "+mixer.sourceName(source)+" "+entry.error);
                        visible.put(source,entry);
                    }
                    return true;
                });
                for(int frame=0;frame<60;frame++) {
                    onClient(client,()->{
                        for(var entry:visible.entrySet())check(service.previews.get(service.catalog.item("minecraft:diamond_leggings"),entry.getKey(),0)==entry.getValue()&&entry.getValue().ready(),"real-pack preview remains stable");
                        return true;
                    });Thread.sleep(20);
                }
                capture(client,"alpha4-real-legging-previews.png");
                evidence.put("realPacksIndexed",hashes.size());evidence.put("realVisiblePreviews",visible.size());
                evidence.put("realPackPreviewCache",onClient(client,service.previews::usage));
                for(var entry:hashes.entrySet())check(PackSource.open(Path.of(entry.getKey())).fingerprint().equals(entry.getValue()),"source ZIP unchanged");
            }
            // More than the former 96-entry limit. Empty packs share one vanilla arena.
            Map<String,PreviewCache.Entry> stable = new LinkedHashMap<>();
            List<String> aliases = new ArrayList<>();
            PackSource empty = PackSource.open(fixtures.resolve("Empty-Pack"));
            onClient(client, () -> {
                for(int i=0;i<112;i++) {
                    String id=UUID.randomUUID().toString();aliases.add(id);service.sources.put(id,empty);
                    mixer.draft.sources.put(id,new MixerConfig.SourceRecord(id,empty.root().toString(),"Fallback-"+i,"test"));
                    mixer.draft.addCandidate(id);
                }
                Set<String> pinned=new HashSet<>();
                for(String id:aliases) pinned.add(id+"|ITEM|minecraft:diamond_leggings|0");
                service.previews.retainPending(pinned);
                return true;
            });
            await(client, () -> {
                boolean ready=true;
                for(String id:aliases){var entry=service.previews.get(service.catalog.item("minecraft:diamond_leggings"),id,0);
                    check(entry.error==null,"fallback preview failure");ready &= entry.ready();}
                return ready;
            });
            onClient(client, () -> { for(String id:aliases)stable.put(id,service.previews.get(service.catalog.item("minecraft:diamond_leggings"),id,0));return true; });
            for(int frame=0;frame<60;frame++) {
                onClient(client, () -> {
                    for(String id:aliases){var entry=service.previews.get(service.catalog.item("minecraft:diamond_leggings"),id,0);
                        check(entry==stable.get(id)&&entry.ready()&&!entry.discarded,"visible preview must stay ready and retain identity");}
                    return true;
                }); Thread.sleep(20);
            }
            evidence.put("previewCache112Fallbacks",onClient(client,service.previews::usage));
            onClient(client, () -> { mixer.refreshCards(); return true; });
            await(client, () -> service.previews.usage().pending()==0);
            capture(client,"alpha4-legging-previews.png");
            onClient(client, () -> {
                drag(client,true);return true;
            });
            Thread.sleep(1500);
            onClient(client, () -> { drag(client,false);return true; });
            Thread.sleep(1500);
            capture(client,"alpha4-legging-scroll-return.png");
            onClient(client,()->{search(client,"Pack-A");click(client,button(client,"Worn"));return true;});
            await(client,()->service.previews.get(service.catalog.item("minecraft:diamond_leggings"),b,0,PreviewCache.Mode.WORN).ready());
            onClient(client,()->{
                var target=service.catalog.item("minecraft:diamond_leggings");
                var icon=service.previews.get(target,b,0,PreviewCache.Mode.ICON);
                var worn=service.previews.get(target,b,0,PreviewCache.Mode.WORN);
                check(icon!=worn&&icon.arena!=worn.arena,"Icon and Worn have independent cache resources");
                Map<?,?> iconFiles=(Map<?,?>)field(icon.arena,"files"),wornFiles=(Map<?,?>)field(worn.arena,"files");
                check(iconFiles.keySet().stream().map(AssetId.class::cast).noneMatch(id->id.path().startsWith("equipment/")),"Icon omits equipment dependencies");
                check(wornFiles.keySet().stream().map(AssetId.class::cast).anyMatch(id->id.path().startsWith("equipment/")),"Worn retains equipment dependencies");
                return true;
            });
            capture(client,"alpha4-mode-worn-fixture.png");
            onClient(client,()->{click(client,button(client,"Hand"));return true;});
            await(client,()->service.previews.get(service.catalog.item("minecraft:diamond_leggings"),b,0,PreviewCache.Mode.HAND).ready());
            capture(client,"alpha4-mode-hand-fixture.png");
            onClient(client, () -> {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),960,720);
                client.options.getGuiScale().setValue(3);client.onResolutionChanged();return true;
            });
            await(client, () -> client.getWindow().getScaledWidth()==320 && client.getWindow().getScaledHeight()==240);
            onClient(client, () -> { client.setScreen(new SourceManagerScreen(mixer));return true; });
            capture(client,"alpha4-manager-320x240.png");
            onClient(client, () -> { client.setScreen(new SourcePickerScreen(mixer));return true; });
            capture(client,"alpha4-picker-320x240.png");
            Files.writeString(result,"PASS: scoped Search, native scrollbar track/drag/release, folder removal/cancel, ordered stack compile/Apply/reorder, 112 stable fallback previews across 60 samples; GUI scales 1/2/3 and 320x240 screenshots.");
        } catch(Throwable error) {
            var trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            Files.writeString(result,"FAIL: "+trace);throw error;
        } finally {
            onClient(client, () -> { client.options.getGuiScale().setValue(scale);client.onResolutionChanged();return true; });
            onClient(client, () -> service.apply(original)).join();
            Files.writeString(client.runDirectory.toPath().resolve("alpha4-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
    }
    private static void searchFocus(MinecraftClient client) {
        search(client,"temporary");click(client,button(client,"×"));
        var input=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
        check(input.getText().isEmpty()&&client.currentScreen.getFocused()==input,"clear retains Search focus");
        client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL));
        check(client.currentScreen.getFocused()==input,"Ctrl+F focuses Search");
    }
    private static Object field(Object instance,String name) {
        try{Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}
        catch(ReflectiveOperationException error){throw new CompletionException(error);}
    }
    private static void drag(MinecraftClient client,boolean bottom) {
        Screen screen=client.currentScreen;
        ButtonWidget bar=buttons(client).stream().filter(button -> button.getMessage().getString().equals(SmokeLabels.text("垂直滚动条"))).findFirst().orElseThrow();
        double x=bar.getX()+5,y=bar.getY()+bar.getHeight()/2.0;
        check(screen.mouseClicked(new Click(x,y,new MouseInput(0,0)),false),"scrollbar track click");
        double end=bottom?bar.getY()+bar.getHeight()+50:bar.getY()-50;
        check(screen.mouseDragged(new Click(x,end,new MouseInput(0,0)),0,end-y),"scrollbar drag outside track");
        screen.mouseReleased(new Click(x,end,new MouseInput(0,0)));
    }
    private static List<ButtonWidget> buttons(MinecraftClient client) {return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();}
    private static List<ButtonWidget> rows(MinecraftClient client){return buttons(client).stream().filter(button -> button.getClass().getSimpleName().equals("LibraryRow")).toList();}
    private static ButtonWidget button(MinecraftClient client,String name){return buttons(client).stream().filter(button -> button.getMessage().getString().equals(name)).findFirst().orElseThrow();}
    private static void click(MinecraftClient client,ButtonWidget button){Screen screen=client.currentScreen;screen.mouseClicked(new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0)),false);screen.mouseReleased(new Click(button.getX()+1,button.getY()+1,new MouseInput(0,0)));}
    private static void search(MinecraftClient client,String value){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(value);}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(300);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,message->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Timeout");Thread.sleep(100);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});return result.join();}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
