package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.ui.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class Alpha8Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();

    public static void run(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        boolean originallyEnabled=service.modEnabled();
        Path settings=client.runDirectory.toPath().resolve("config/texturemixer/client.json");
        int scale=client.options.getGuiScale().getValue();
        try {
            evidence.put("startupEnabled",originallyEnabled);
            if(!originallyEnabled)check(service.runtime()==null,"OFF persisted across client startup");
            boolean modmenu=FabricLoader.getInstance().isModLoaded("modmenu");
            boolean packed=FabricLoader.getInstance().isModLoaded("packed_packs");
            evidence.put("modmenuLoaded",modmenu);evidence.put("packedPacksLoaded",packed);
            onClient(client,()->{
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                Screen parent=new OptionsScreen(new TitleScreen(),client.options);
                Screen screen;
                if(modmenu){
                    try{
                        Class<?> api=Class.forName("com.terraformersmc.modmenu.ModMenu");
                        check((Boolean)api.getMethod("hasConfigScreen",String.class).invoke(null,"texturemixer"),"Mod Menu registers TextureMixer config factory");
                        Screen factoryScreen=(Screen)api.getMethod("getConfigScreen",String.class,Screen.class).invoke(null,"texturemixer",parent);
                        check(factoryScreen instanceof ModSettingsScreen,"config factory creates settings screen");
                        screen=(Screen)Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                                .getConstructor(Screen.class).newInstance(parent);
                    }catch(ReflectiveOperationException error){throw new CompletionException(error);}
                }else screen=TextureMixerClient.configurationScreen(parent);
                client.setScreen(screen);
                if(modmenu){
                    TextFieldWidget search=(TextFieldWidget)field(screen,"searchBox");
                    search.setText("TextureMixer");
                    EntryListWidget<?> list=(EntryListWidget<?>)field(screen,"modList");
                    net.minecraft.client.gui.widget.Widget entry=null;
                    for(Object candidate:list.children()){
                        if(modId(candidate).equals("texturemixer")){
                            entry=(net.minecraft.client.gui.widget.Widget)candidate;
                            break;
                        }
                    }
                    check(entry!=null,"Mod Menu search lists TextureMixer");
                    Click click=new Click(entry.getX()+entry.getWidth()/2.0,entry.getY()+entry.getHeight()/2.0,new MouseInput(0,0));
                    screen.mouseClicked(click,false);
                    screen.mouseReleased(click);
                    check(((ClickableWidget)field(screen,"configureButton")).active,"Mod Menu enables TextureMixer configure button");
                }
                return true;
            });
            if(modmenu){
                capture(client,"alpha8-modmenu-list.png");
                onClient(client,()->{
                    Screen screen=client.currentScreen;
                    ClickableWidget configure=(ClickableWidget)field(screen,"configureButton");
                    Click click=new Click(configure.getX()+configure.getWidth()/2.0,configure.getY()+configure.getHeight()/2.0,new MouseInput(0,0));
                    screen.mouseClicked(click,false);
                    screen.mouseReleased(click);
                    return true;
                });
            }
            check(client.currentScreen instanceof ModSettingsScreen,"Mod Menu configure input opens TextureMixer settings");
            if(!service.modEnabled()){
                await(client,()->button(client,"TextureMixer OFF").active);
                onClient(client,()->{click(client,button(client,"TextureMixer OFF"));return true;});
                awaitReady(client,service);
            }
            capture(client,"alpha8-modmenu-on.png");
            // The standard Options entry must also work when Packed Packs replaces its screen.
            onClient(client,()->{optionsEntry(client);return true;});
            check(client.currentScreen instanceof MixerScreen,"ON intercepts the actual resource-pack entry");
            capture(client,"alpha8-resource-entry-on.png");
            var draft=service.committed.copy();draft.setMainStack(List.of());
            for(var kind:MixerConfig.Kind.values())draft.overrides(kind).clear();
            String source=onClient(client,()->service.add(client.runDirectory.toPath().resolve("fixtures/Pack-A.zip"),draft,false)).join();
            draft.itemOverrides.put("minecraft:diamond_sword",source);
            onClient(client,()->service.apply(draft)).join();awaitReady(client,service);
            String signature=service.committed.renderSignature(),generation=service.runtime().id();
            check(onClient(client,()->sprite(client)).startsWith("texturemixer_"),"selected item is live before OFF");
            onClient(client,()->{client.setScreen(TextureMixerClient.configurationScreen(new TitleScreen()));click(client,button(client,"TextureMixer ON"));return true;});
            awaitReady(client,service);
            check(!service.modEnabled()&&!ClientSettings.loadEnabled(settings),"OFF is live and persisted");
            check(service.runtime()==null&&service.prefixForItem("minecraft:diamond_sword")==null,"OFF disables bindings");
            check(onClient(client,()->sprite(client)).startsWith("minecraft:"),"OFF restores the actual base item model");
            check(signature.equals(service.committed.renderSignature()),"OFF preserves saved mix selections");
            check(onClient(client,()->client.getResourceManager().streamResourcePacks().noneMatch(pack->pack.getId().equals("texturemixer/"+generation))),"OFF unmounts personal resources");
            capture(client,"alpha8-modmenu-off.png");
            onClient(client,()->{optionsEntry(client);return true;});
            check(!(client.currentScreen instanceof MixerScreen),"OFF returns the normal resource-pack UI");
            if(packed)check(client.currentScreen.getClass().getName().equals("io.github.fishstiz.packed_packs.gui.screens.PackedPacksScreen"),"OFF restores Packed Packs screen");
            evidence.put("disabledResourceScreen",client.currentScreen.getClass().getName());
            capture(client,"alpha8-resource-entry-off.png");
            onClient(client,()->{client.currentScreen.close();client.setScreen(TextureMixerClient.configurationScreen(new TitleScreen()));click(client,button(client,"TextureMixer OFF"));return true;});
            awaitReady(client,service);
            check(service.modEnabled()&&ClientSettings.loadEnabled(settings),"ON is live and persisted");
            check(service.runtime().id().equals(generation)&&signature.equals(service.committed.renderSignature()),"ON restores the same snapshot and selections");
            check(onClient(client,()->sprite(client)).startsWith("texturemixer_"),"ON restores the actual chosen item model");
            onClient(client,()->{SmokeControls.failNextReload=true;click(client,button(client,"TextureMixer ON"));return true;});
            awaitReady(client,service);
            check(service.modEnabled()&&ClientSettings.loadEnabled(settings),"failed OFF restores enabled flag and persistence");
            check(service.runtime().id().equals(generation)&&onClient(client,()->sprite(client)).startsWith("texturemixer_"),"failed OFF restores previous resources");
            Path dataFolder=client.runDirectory.toPath().resolve("fixtures/alpha8-data-packs");Files.createDirectories(dataFolder);
            onClient(client,()->{
                Screen previous=client.currentScreen;
                client.setScreen(new PackScreen(new ResourcePackManager(),manager->client.setScreen(previous),dataFolder,Text.translatable("dataPack.title")));
                return true;
            });
            check(!(client.currentScreen instanceof MixerScreen),"data-pack screens are excluded");
            onClient(client,()->{client.currentScreen.close();return true;});
            for(int guiScale:List.of(1,2,3)){
                onClient(client,()->{client.options.getGuiScale().setValue(guiScale);client.onResolutionChanged();client.setScreen(TextureMixerClient.configurationScreen(new TitleScreen()));return true;});
                capture(client,"alpha8-settings-scale-"+guiScale+".png");
            }
            evidence.put("sameGeneration",generation);evidence.put("savedMixPreserved",true);
            evidence.put("nativeModMenuConfigureButton",modmenu);
            Files.writeString(client.runDirectory.toPath().resolve("alpha8-result.txt"),"PASS: startup OFF, optional Mod Menu factory, standard resource-pack entry with Packed Packs, native ON/OFF controls, persisted flag, actual resource unmount/restore, retained snapshot/mix, reload-failure rollback, data-pack exclusion, GUI scales 1/2/3.");
        }catch(Throwable error){
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            Files.writeString(client.runDirectory.toPath().resolve("alpha8-result.txt"),"FAIL: "+trace);throw error;
        }finally{
            if(!service.modEnabled())onClient(client,()->service.setModEnabled(true)).join();
            onClient(client,()->{client.options.getGuiScale().setValue(scale);client.onResolutionChanged();client.setScreen(null);return true;});
            onClient(client,()->service.apply(original)).join();
            if(!originallyEnabled)onClient(client,()->service.setModEnabled(false)).join();
            Files.writeString(client.runDirectory.toPath().resolve("alpha8-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
    }

    private static void optionsEntry(MinecraftClient client){client.setScreen(new OptionsScreen(new TitleScreen(),client.options));click(client,button(client,Text.translatable("options.resourcepack").getString()));}
    private static String sprite(MinecraftClient client){var state=new KeyedItemRenderState();client.getItemModelManager().clearAndUpdate(state,Items.DIAMOND_SWORD.getDefaultStack(),ItemDisplayContext.GUI,client.world,client.player,0);return state.getParticleSprite(net.minecraft.util.math.random.Random.create(0)).getContents().getId().toString();}
    private static ButtonWidget button(MinecraftClient client,String name){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(widget->widget.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));}
    private static void click(MinecraftClient client,ButtonWidget button){Screen screen=client.currentScreen;check(button.active,"Disabled "+button.getMessage().getString());screen.mouseClicked(new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0)),false);screen.mouseReleased(new Click(button.getX()+1,button.getY()+1,new MouseInput(0,0)));}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(450);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,message->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void awaitReady(MinecraftClient client,MixerService service)throws Exception{await(client,()->!service.busy&&client.getOverlay()==null);onClient(client,()->{if(client.currentScreen!=null)client.currentScreen.tick();return true;});}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Timeout");Thread.sleep(100);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});return result.join();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Object field(Object target,String name){
        try{
            var field=target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }catch(ReflectiveOperationException error){throw new CompletionException(error);}
    }
    private static String modId(Object entry){
        try{
            Object mod=entry.getClass().getMethod("getMod").invoke(entry);
            return (String)mod.getClass().getMethod("getId").invoke(mod);
        }catch(ReflectiveOperationException error){throw new CompletionException(error);}
    }
}
