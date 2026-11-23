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
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.*;

/** Expanded categories through native screen inputs and real resource reloads in the synthetic instance. */
public final class Alpha5Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void run(MinecraftClient client)throws Exception {
        MixerService service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        int scale=client.options.getGuiScale().getValue();
        Path report=client.runDirectory.toPath().resolve("alpha5-result.txt");
        try {
            Map<String,byte[]> pack=new LinkedHashMap<>();
            pack.put("pack.mcmeta","{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"Alpha5 fixture\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String entity="textures/entity/zombie/zombie.png",hud="textures/gui/sprites/hud/crosshair.png";
            for(String path:List.of(entity,hud,"textures/particle/flame.png","textures/environment/rain.png","textures/environment/snow.png")) {
                byte[] vanilla=service.vanilla.read(AssetId.of(path));check(vanilla!=null,"vanilla image exists: "+path);
                BufferedImage source=ImageIO.read(new ByteArrayInputStream(vanilla));
                BufferedImage image=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,(x+y)%5==0?0x00000000:path.contains("flame")?0xff33ee55:0xffff3399);
                ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(image,"PNG",png);pack.put("assets/minecraft/"+path,png.toByteArray());
            }
            String event="minecraft:block.note_block.harp";
            var sample=new ResourceCompiler(new Assets.View(null,service.vanilla),"fixture").sample(event);
            check(sample!=null,"native Ogg sample exists");
            pack.put("assets/minecraft/sounds/tm_test/harp.ogg",sample.data());
            pack.put("assets/minecraft/sounds.json","{\"block.note_block.harp\":{\"replace\":true,\"sounds\":[\"tm_test/harp\"]},\"music.menu\":{\"replace\":true,\"sounds\":[{\"name\":\"tm_test/harp\",\"stream\":true}]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path zip=client.runDirectory.toPath().resolve("fixtures/alpha5-resources.zip");
            try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(zip))) {
                for(var file:pack.entrySet()){output.putNextEntry(new ZipEntry(file.getKey()));output.write(file.getValue());output.closeEntry();}
            }
            MixerScreen mixer=onClient(client,()->new MixerScreen(service));
            String source=onClient(client,()->service.add(zip,mixer.draft,false)).join();
            Map<MixerConfig.Kind,String> targets=new LinkedHashMap<>();
            targets.put(MixerConfig.Kind.ENTITY,"minecraft:"+entity);targets.put(MixerConfig.Kind.PARTICLE,"minecraft:flame");
            targets.put(MixerConfig.Kind.HUD,"minecraft:"+hud);targets.put(MixerConfig.Kind.SOUND,event);targets.put(MixerConfig.Kind.SKY,"weather");
            for(var kind:MixerConfig.Kind.values())evidence.put(kind.label+" targets",service.catalog.list(kind).size());
            for(int guiScale:List.of(1,2,3)) {
                onClient(client,()->{client.options.getGuiScale().setValue(guiScale);client.onResolutionChanged();client.setScreen(mixer);return true;});
                for(var selection:targets.entrySet()) {
                    MixerConfig.Kind kind=selection.getKey();String id=selection.getValue();
                    Catalog.Target target=service.catalog.list(kind).stream().filter(value->value.id().equals(id)).findFirst().orElseThrow();
                    onClient(client,()->{selectCategory(client,mixer,kind);search(client,id);click(client,button(client,target.name()));search(client,"");return true;});
                    if(kind==MixerConfig.Kind.SOUND) {
                        onClient(client,()->{clickSource(client,mixer,source);return true;});
                        await(client,()->!((AudioPreview)field(mixer,"audioPreview")).status.equals("Loading audio…"));
                        String status=onClient(client,()->((AudioPreview)field(mixer,"audioPreview")).status);
                        check(!status.startsWith("Audio preview failed"),status);
                        evidence.put("audition",status);
                        onClient(client,()->{click(client,button(client,"Stop"));check(!((AudioPreview)field(mixer,"audioPreview")).playing,"Stop cancels audition");return true;});
                    } else if(kind==MixerConfig.Kind.SKY) {
                        await(client,()->{
                            var cache=(com.fullyx.texturemixer.sky.SkyPreviewCache)field(mixer,"skyPreviews");
                            var preview=cache.get("weather",source,18000,0);check(preview.error==null,"weather preview "+preview.error);return preview.texture!=null;
                        });
                        onClient(client,()->{clickSource(client,mixer,source);return true;});
                    } else {
                        await(client,()->{var entry=((ResourcePreviewCache)field(mixer,"resourcePreviews")).get(target,source);check(entry.error==null,"preview "+entry.error);return entry.texture!=null;});
                        onClient(client,()->{clickSource(client,mixer,source);return true;});
                    }
                    onClient(client,()->{check(mixer.draft.overrides(kind).get(id).equals(source),"native category choice");assertBounds(client);return true;});
                    capture(client,"alpha5-"+kind.name().toLowerCase(Locale.ROOT)+"-scale-"+guiScale+".png");
                }
            }
            onClient(client,()->{
                selectCategory(client,mixer,MixerConfig.Kind.SOUND);search(client,"minecraft:music.menu");
                Catalog.Target music=service.catalog.list(MixerConfig.Kind.SOUND).stream().filter(target->target.id().equals("minecraft:music.menu")).findFirst().orElseThrow();
                click(client,button(client,music.name()));search(client,"");clickSource(client,mixer,source);return true;
            });
            await(client,()->!((AudioPreview)field(mixer,"audioPreview")).status.equals("Loading audio…"));
            onClient(client,()->{check(!((AudioPreview)field(mixer,"audioPreview")).status.startsWith("Audio preview failed"),"music audition");click(client,button(client,"Stop"));return true;});
            capture(client,"alpha5-music-preview.png");
            String template=onClient(client,()->{try{return service.library.saveTemplate("Alpha5 "+UUID.randomUUID(),mixer.draft).id();}catch(IOException error){throw new CompletionException(error);}});
            MixerConfig restored=onClient(client,()->service.library.loadTemplate(template,mixer.draft)).join();
            check(restored.soundOverrides.get("minecraft:music.menu").equals(source),"template preserves music");
            for(var selection:targets.entrySet())check(restored.overrides(selection.getKey()).get(selection.getValue()).equals(source),"template preserves "+selection.getKey());
            onClient(client,()->{try{service.library.deleteTemplate(template);}catch(IOException error){throw new CompletionException(error);}return true;});
            onClient(client,()->service.apply(mixer.draft)).join();
            await(client,()->!service.busy&&client.getOverlay()==null);
            onClient(client,()->{
                for(String path:List.of(entity,hud,"textures/environment/rain.png","textures/environment/snow.png"))
                    try(var input=client.getResourceManager().getResource(Identifier.of("minecraft",path)).orElseThrow().getInputStream()){
                        check(Arrays.equals(input.readAllBytes(),pack.get("assets/minecraft/"+path)),"native applied bytes "+path);
                    }catch(IOException error){throw new CompletionException(error);}
                try {
                    var resource=client.getResourceManager().getResource(Identifier.of("minecraft","particles/flame.json")).orElseThrow();
                    String sprite;
                    try(var input=resource.getInputStream()){sprite=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("textures").get(0).getAsString();}
                    check(sprite.startsWith("texturemixer_"),"native particle definition uses isolated sprite");
                    var actual=client.getAtlasManager().getSprite(new SpriteIdentifier(net.minecraft.client.texture.SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE,Identifier.of(sprite)));
                    check(!actual.getContents().getId().equals(Identifier.of("missingno")),"native particle sprite stitched");
                }catch(IOException error){throw new CompletionException(error);}
                var sound=client.getSoundManager().get(Identifier.of(event)).getSound(net.minecraft.util.math.random.Random.create(0));
                check(sound.getIdentifier().getNamespace().startsWith("texturemixer_"),"native sound set resolves selected private file");
                check(client.getSoundManager().get(Identifier.of("music.menu")).getSound(net.minecraft.util.math.random.Random.create(0)).isStreamed(),"native music event preserves streaming");
                return true;
            });
            Path badZip=client.runDirectory.toPath().resolve("fixtures/alpha5-invalid-audio.zip");
            try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(badZip))){
                Map<String,byte[]> invalid=Map.of("pack.mcmeta",pack.get("pack.mcmeta"),
                        "assets/minecraft/sounds.json","{\"music.menu\":{\"replace\":true,\"sounds\":[\"broken\"]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "assets/minecraft/sounds/broken.ogg","OggSinvalid-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(var file:invalid.entrySet()){output.putNextEntry(new ZipEntry(file.getKey()));output.write(file.getValue());output.closeEntry();}
            }
            MixerConfig invalid=mixer.draft.copy();
            String badSource=onClient(client,()->service.add(badZip,invalid,false)).join();
            invalid.soundOverrides.put("minecraft:music.menu",badSource);
            Generation mounted=service.mounted;int reloads=SmokeControls.reloads;
            boolean rejected=false;
            try{onClient(client,()->service.apply(invalid)).join();}catch(CompletionException expected){rejected=true;}
            check(rejected&&service.mounted==mounted&&SmokeControls.reloads==reloads,"invalid Ogg fails before reload and preserves mounted generation");
            evidence.put("invalidOggPreflight",true);
            onClient(client,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),960,720);client.options.getGuiScale().setValue(3);client.onResolutionChanged();client.setScreen(mixer);return true;});
            await(client,()->client.getWindow().getScaledWidth()==320&&client.getWindow().getScaledHeight()==240);
            onClient(client,()->{selectCategory(client,mixer,MixerConfig.Kind.SOUND);assertBounds(client);return true;});
            capture(client,"alpha5-sidebar-320x240.png");
            onClient(client,()->{
                try{Class<?> type=Class.forName("com.fullyx.texturemixer.ui.PackDetailsScreen");var constructor=type.getDeclaredConstructor(MixerScreen.class,Screen.class,String.class);constructor.setAccessible(true);client.setScreen((Screen)constructor.newInstance(mixer,mixer,source));}
                catch(ReflectiveOperationException error){throw new CompletionException(error);}
                assertBounds(client);return true;
            });
            capture(client,"alpha5-pack-details-320x240.png");
            Files.writeString(report,"PASS: seven categories, scoped resource previews, native selection, isolated Ogg audition/Stop, template round-trip, Apply resource bytes, stitched particle sprite and native selected sound set; GUI scales 1/2/3 and 320x240.");
        }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));Files.writeString(report,"FAIL: "+trace);throw error;}
        finally {
            onClient(client,()->{client.options.getGuiScale().setValue(scale);client.onResolutionChanged();client.setScreen(null);return true;});
            onClient(client,()->service.apply(original)).join();
            Files.writeString(client.runDirectory.toPath().resolve("alpha5-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
    }
    private static void clickSource(MinecraftClient client,MixerScreen mixer,String source){
        try{Method focus=MixerScreen.class.getDeclaredMethod("focusCard",int.class);focus.setAccessible(true);focus.invoke(mixer,((List<?>)field(mixer,"visible")).indexOf(source));}
        catch(ReflectiveOperationException error){throw new CompletionException(error);}
        click(client,button(client,mixer.sourceName(source)));
    }
    private static void selectCategory(MinecraftClient client,MixerScreen mixer,MixerConfig.Kind kind){
        for(int attempt=0;attempt<8&&buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(kind.label));attempt++) {
            int offset=(int)field(mixer,"navOffset"),top=(int)field(mixer,"navTop"),left=(int)field(mixer,"left");
            mixer.mouseScrolled(left+16,top+2,0,kind.ordinal()<offset?1:-1);
        }
        click(client,button(client,kind.label));
    }
    private static void assertBounds(MinecraftClient client){for(var button:buttons(client))if(!button.getClass().getSimpleName().equals("Card"))check(button.getX()>=0&&button.getY()>=0&&button.getX()+button.getWidth()<=client.currentScreen.width&&button.getY()+button.getHeight()<=client.currentScreen.height,"control bounds "+button.getMessage().getString());}
    private static Object field(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception error){throw new CompletionException(error);}}
    private static List<ButtonWidget> buttons(MinecraftClient client){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();}
    private static ButtonWidget button(MinecraftClient client,String name){return buttons(client).stream().filter(button->button.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));}
    private static void click(MinecraftClient client,ButtonWidget button){Screen screen=client.currentScreen;check(button.active,"disabled button "+button.getMessage().getString());screen.mouseClicked(new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0)),false);screen.mouseReleased(new Click(button.getX()+1,button.getY()+1,new MouseInput(0,0)));}
    private static void search(MinecraftClient client,String query){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(query);}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(350);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,message->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void await(MinecraftClient client,Supplier<Boolean> predicate)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);while(!onClient(client,predicate)){if(System.nanoTime()>deadline)throw new AssertionError("Timeout");Thread.sleep(100);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});return result.join();}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
