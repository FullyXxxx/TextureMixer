package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.*;

public final class Alpha12Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void run(MinecraftClient client)throws Exception{
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        String locale=UiLanguage.language(),gameLanguage=client.options.language;
        boolean preload=service.preloadEnabled();int scale=client.options.getGuiScale().getValue();
        Path preferences=client.runDirectory.toPath().resolve("config/texturemixer/client.json");
        Path fixtures=client.runDirectory.toPath().resolve("fixtures").toAbsolutePath();
        Path source=fixtures.resolve("alpha12-transparent.zip"),renamed=fixtures.resolve("alpha12-renamed.zip");
        Path result=client.runDirectory.toPath().resolve("alpha12-result.txt");
        try{
            createPack(source);Files.copy(source,renamed,StandardCopyOption.REPLACE_EXISTING);
            String hash=Assets.hash(Files.readAllBytes(source));
            MixerConfig draft=original.copy();
            String id=onClient(client,()->service.add(source,draft,false)).get(60,TimeUnit.SECONDS);
            String other=onClient(client,()->service.add(renamed,draft,false)).get(60,TimeUnit.SECONDS);
            var crit=service.catalog.list(MixerConfig.Kind.PARTICLE).stream().filter(target->target.id().equals("minecraft:crit")).findFirst().orElseThrow();
            for(String sourceId:List.of(id,other)){
                var closure=new ResourceCompiler(Generation.view(sourceId,service.sources,service.vanilla),sourceId).compile(MixerConfig.Kind.PARTICLE,crit.id());
                check(closure.custom(),"transparent source texture is detected through vanilla particle definitions");
                var image=ImageIO.read(new ByteArrayInputStream(closure.files().get(closure.images().getFirst())));
                check(image.getRGB(0,0)==0,"renaming the ZIP preserves transparent resource semantics");
            }
            MixerScreen mixer=onClient(client,()->{
                try{
                    service.setPreloadEnabled(false);
                }catch(IOException error){throw new UncheckedIOException(error);}
                service.resourcePreviews.invalidate();
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                var screen=new MixerScreen(service);screen.draft=draft;screen.draft.setMainStack(List.of(id));
                client.setScreen(screen);return screen;
            });
            await(client,()->!mixer.libraryImporting());
            onClient(client,()->{tab(client,"Particle");search(client,"crit");return true;});
            long cold=System.nanoTime();
            await(client,()->{
                var actual=service.resourcePreviews.get(crit,id);var reference=service.resourcePreviews.get(crit,MixerConfig.VANILLA);
                if(actual.error!=null||reference.error!=null)throw new AssertionError(actual.error+" "+reference.error);
                return actual.texture!=null&&reference.texture!=null;
            });
            evidence.put("coldReferenceReadyMs",(System.nanoTime()-cold)/1e6);
            check(service.resourcePreviews.needsReference(service.resourcePreviews.get(crit,id)),"transparent selection uses an overview reference");
            Path overview=capture(client,"alpha15-transparent-overview.png");
            onClient(client,()->{
                var card=button(client,"Crit");
                try{
                    BufferedImage image=ImageIO.read(overview.toFile());int bright=0;
                    for(int y=(card.getY()+8)*2;y<(card.getY()+24)*2;y++)for(int x=(card.getX()+8)*2;x<(card.getX()+32)*2;x++){
                        int rgb=image.getRGB(x,y);if((rgb>>16&255)>140&&(rgb>>8&255)>140&&(rgb&255)>140)bright++;
                    }
                    check(bright>5,"overview has a visible reference image beyond the transparency badge");
                }catch(IOException error){throw new UncheckedIOException(error);}
                click(client,"Crit");click(client,"alpha12-transparent.zip");return true;
            });
            capture(client,"alpha15-transparent-detail.png");
            onClient(client,()->{click(client,"Cancel");click(client,"Back");return true;});
            var texture=service.resourcePreviews.get(crit,MixerConfig.VANILLA).texture;
            onClient(client,()->{click(client,"Settings");return true;});
            check(client.currentScreen instanceof MixerSettingsScreen,"bottom-left Settings opens preferences");
            onClient(client,()->{setting(client,"Language · English");return true;});
            check(UiLanguage.language().equals("zh_cn"),"UI language changes to Simplified Chinese");
            check(service.catalog.item("minecraft:diamond_sword").name().equals("Diamond Sword"),"item display name stays vanilla English");
            check(service.catalog.block("minecraft:diamond_block").name().equals("Block of Diamond"),"block display name stays vanilla English");
            check(service.catalog.item("minecraft:diamond_sword").matches("DiamondSword"),"Chinese UI searches joined English item names");
            check(service.catalog.block("minecraft:diamond_block").matches("钻石块"),"Chinese block search is available");
            check(client.options.language.equals(gameLanguage),"mod locale is independent of the game locale");
            check(ClientSettings.loadLanguage(preferences).equals("zh_cn"),"language persists");
            capture(client,"alpha15-settings-zh.png");
            onClient(client,()->{click(client,"返回");return true;});
            check(service.resourcePreviews.get(crit,MixerConfig.VANILLA).texture.equals(texture),"thumbnails survive leaving and reopening the editor");
            onClient(client,()->{tab(client,"材质包");return true;});
            assertAllCategories(client,List.of("材质包","物品","方块","天空","实体","粒子","界面","音乐和声音","附魔"));
            assertTools(client);
            onClient(client,()->{tab(client,"物品");search(client,"钻石剑");return true;});
            check(button(client,"Diamond Sword").getWidth()==40,"standard tile and vanilla English item name");
            capture(client,"alpha15-item-search-zh.png");
            onClient(client,()->{click(client,"设置");setting(client,"语言 · 简体中文");click(client,"Back");return true;});
            check(UiLanguage.language().equals("en_us"),"UI language changes back to English");
            assertAllCategories(client,List.of("Resourcepack","Item","Block","Sky","Entity","Particle","HUD","Sound & Music","Enchant"));
            onClient(client,()->{tab(client,"Block");search(client,"钻石块");return true;});
            check(button(client,"Block of Diamond")!=null,"Chinese search works in English mode");
            onClient(client,()->{tab(client,"Resourcepack");return true;});
            onClient(client,()->{assertEditorGeometry(client,false);return true;});
            capture(client,"alpha15-window-en.png");
            onClient(client,()->{
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);client.onResolutionChanged();client.setScreen(mixer);return true;
            });
            check(button(client,"Next")!=null||button(client,"Prev")!=null,"compact category navigation exposes arrows");
            onClient(client,()->{assertBounds(client);assertEditorGeometry(client,true);return true;});
            capture(client,"alpha15-window-320x240.png");
            onClient(client,()->{click(client,"Settings");return true;});
            assertBounds(client);capture(client,"alpha15-settings-320x240.png");
            onClient(client,()->{
                check(button(client,"Copy Version").active,"version information can be copied");
                click(client,"Keyboard Help");
                check(client.currentScreen instanceof MessageScreen,"keyboard help opens");
                return true;
            });
            capture(client,"alpha15-keyboard-help.png");
            onClient(client,()->{click(client,"Back");return true;});
            long preloadedBefore=service.resourcePreviews.usage().preloads();
            onClient(client,()->{setting(client,"Preload Images · OFF");click(client,"Back");client.setScreen(null);service.resourcePreviews.invalidate();return true;});
            await(client,()->service.resourcePreviews.usage().preloads()>=preloadedBefore+10&&service.resourcePreviews.usage().entries()>10);
            Thread.sleep(800);
            evidence.put("preloadUsage",onClient(client,()->service.resourcePreviews.usage()));
            check(service.resourcePreviews.usage().bytes()<=32L*1024*1024,"image cache stays within its memory budget");
            check(hash.equals(Assets.hash(Files.readAllBytes(source))),"imported ZIP remains byte-identical");
        }catch(Throwable error){
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));Files.writeString(result,"FAIL: "+trace);throw error;
        }finally{
            onClient(client,()->{
                client.setScreen(null);org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);
                client.options.getGuiScale().setValue(scale);client.onResolutionChanged();
                try{
                    service.setLanguage(locale);service.setPreloadEnabled(preload);
                }catch(IOException error){throw new UncheckedIOException(error);}return true;
            });
            onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
            for(Path path:List.of(source,renamed)){check(path.getParent().equals(fixtures)&&path.getFileName().toString().startsWith("alpha12-"),"fixture cleanup stays in test directory");Files.deleteIfExists(path);}
            Files.writeString(client.runDirectory.toPath().resolve("alpha12-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
        Alpha15Smoke.run(client);
        Alpha11Smoke.run(client);
        Files.writeString(result,"PASS: generic transparent-particle reference, shared bounded preview cache, fixed vanilla English item and block names, bilingual search, language settings, keyboard help, settings and viewport layout. Includes alpha11/alpha10/alpha9/alpha7/preview-flow regressions.");
    }
    private static void createPack(Path path)throws Exception{
        BufferedImage transparent=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);var png=new ByteArrayOutputStream();ImageIO.write(transparent,"PNG",png);
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))){
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"generic alpha fixture\"}}".getBytes());zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/minecraft/textures/particle/critical_hit.png"));zip.write(png.toByteArray());zip.closeEntry();
        }
    }
    private static void assertAllCategories(MinecraftClient client,List<String> labels){for(String label:labels)check(button(client,label)!=null,"category fits the wide row "+label);}
    private static void assertTools(MinecraftClient client){var settings=button(client,UiLanguage.tr("设置"));var sets=button(client,UiLanguage.tr("自定义套装"));check(settings.getX()<sets.getX()&&settings.getY()==sets.getY(),"custom sets sit beside Settings");}
    private static void assertEditorGeometry(MinecraftClient client,boolean narrow){
        var screen=client.currentScreen;
        var settings=button(client,"Settings");
        var folder=button(client,"Open Folder");
        var done=button(client,"Done");
        var picked=button(client,"alpha12-transparent.zip");
        var remove=SmokeLabels.packAction(client,"移除","alpha12-transparent.zip");
        check(picked.getX()>screen.width/2 && remove.getX()>picked.getRight(),"selected pack and its actions stay in the right column");
        check(settings.getY()==folder.getY() && (narrow?settings.getY()<done.getY():settings.getY()==done.getY()),"tools and submission controls reflow without overlap");
        check(done.getHeight()==18 && screen.height-done.getBottom()>=16,"compact controls leave a bottom margin");
        if(!narrow)check(settings.getX()>=screen.width/10 && done.getRight()<=screen.width*9/10,"wide editor is inset from both sides");
    }
    private static void assertBounds(MinecraftClient client){for(var child:client.currentScreen.children())if(child instanceof ButtonWidget button&&!button.getClass().getSimpleName().equals("Card"))check(button.getX()>=0&&button.getY()>=0&&button.getRight()<=client.currentScreen.width&&button.getBottom()<=client.currentScreen.height,"button fits viewport "+button.getMessage().getString());}
    private static ButtonWidget button(MinecraftClient client,String label){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(value->value.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));}
    private static void click(MinecraftClient client,String label){click(client,button(client,label));}
    private static void setting(MinecraftClient client,String label){
        var control=client.currentScreen.children().stream().filter(FlatButton.class::isInstance).map(FlatButton.class::cast)
                .filter(button->label.equals(button.narrationLabel)).findFirst().orElseThrow(()->new AssertionError("Missing setting "+label));
        click(client,control);
    }
    private static void click(MinecraftClient client,ButtonWidget widget){String label=widget.getMessage().getString();check(widget.active,"active "+label);var screen=client.currentScreen;Click input=new Click(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,new MouseInput(0,0));check(screen.mouseClicked(input,false),"handled "+label);screen.mouseReleased(input);}
    private static void tab(MinecraftClient client,String label){for(int step=0;step<9;step++){try{click(client,label);return;}catch(AssertionError missing){var next=button(client,UiLanguage.tr("后"));if(!next.active)break;click(client,UiLanguage.tr("后"));}}for(int step=0;step<9;step++){try{click(client,label);return;}catch(AssertionError missing){click(client,UiLanguage.tr("前"));}}throw new AssertionError(label);}
    private static void search(MinecraftClient client,String value){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(value);}
    private static Path capture(MinecraftClient client,String name)throws Exception{Thread.sleep(350);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,ignored->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);return client.runDirectory.toPath().resolve("screenshots").resolve(name);}
    private static void await(MinecraftClient client,Supplier<Boolean> predicate)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);while(!onClient(client,predicate)){if(System.nanoTime()>deadline)throw new AssertionError("alpha12 timeout");Thread.sleep(50);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> future=new CompletableFuture<>();client.execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});return future.join();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
