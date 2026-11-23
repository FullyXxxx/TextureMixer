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
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class PreviewFlowSmoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void run(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        int scale=client.options.getGuiScale().getValue();
        List<Path> copies=new ArrayList<>();
        List<String> ids=new ArrayList<>();
        Path fixtures=client.runDirectory.toPath().resolve("fixtures").toRealPath();
        try {
            MixerConfig registered=original.copy();
            for(int index=1;index<=21;index++){
                Path path=fixtures.resolve("PreviewPerf-"+String.format(Locale.ROOT,"%02d",index)+".zip");
                check(!Files.exists(path),"exclusive preview fixture path");
                Files.copy(fixtures.resolve(index%2==0?"Pack-B.zip":"Pack-A.zip"),path);
                copies.add(path);
                ids.add(onClient(client,()->service.add(path,registered,false)).join());
            }
            Catalog.Target helmet=service.catalog.item("minecraft:diamond_helmet");
            var closure=CompletableFuture.supplyAsync(()->{
                try{
                    var view=Generation.view(ids.getFirst(),service.sources,service.vanilla);
                    var full=new GraphCompiler(view,"full closure").item(helmet.model(),helmet.equipment());
                    var worn=new GraphCompiler(view,"wear closure").wornPreview(helmet.equipment());
                    var icon=new GraphCompiler(view,"icon closure").itemPreview(helmet.model(),null,false);
                    return Map.of("fullFiles",full.resources().size(),"wornFiles",worn.resources().size(),"iconFiles",icon.resources().size());
                }catch(Exception error){throw new CompletionException(error);}
            },service.workers).get(30,TimeUnit.SECONDS);
            evidence.put("closureComparison",closure);
            MixerScreen mixer=onClient(client,()->{
                client.options.getGuiScale().setValue(1);client.onResolutionChanged();
                service.previews.invalidate();
                var screen=new MixerScreen(service);client.setScreen(screen);return screen;
            });
            await(client,()->Boolean.TRUE.equals(field(mixer,"discovered"))&&!mixer.libraryImporting());
            int reloads=SmokeControls.reloads;
            onClient(client,()->{click(client,"Item");search(client,"diamond_helmet");click(client,"Diamond Helmet");search(client,"PreviewPerf-");startFrames();click(client,"Worn");return true;});
            long start=System.nanoTime();
            awaitPreviews(client,helmet,ids,PreviewCache.Mode.WORN);
            evidence.put("worn21ReadyMs",(System.nanoTime()-start)/1_000_000.0);
            evidence.put("wornFrames",onClient(client,PreviewFlowSmoke::stopFrames));
            var wornUsage=onClient(client,()->service.previews.usage());evidence.put("wornUsage",wornUsage);
            check(wornUsage.gpuBytes()<=64L*1024*1024&&wornUsage.cpuBytes()<=128L*1024*1024,"21 worn previews fit configured budgets");
            onClient(client,()->{
                for(String id:ids)check(service.previews.get(helmet,id,0,PreviewCache.Mode.WORN).arena.modelArena==null,"ordinary wear has no model or trim atlas");
                return true;
            });
            capture(client,"alpha9-worn-21-sources.png");
            onClient(client,()->{click(client,"PreviewPerf-01.zip");return true;});
            check(client.currentScreen instanceof SelectionDialog,"source click opens focused preview dialog");
            for(var display:List.of(PreviewCache.Mode.ICON,PreviewCache.Mode.HAND,PreviewCache.Mode.WORN))
                awaitPreviews(client,helmet,List.of(ids.getFirst()),display);
            onClient(client,()->{
                check(client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                        .noneMatch(button->List.of("Icon","Hand","Worn").contains(button.getMessage().getString())),"popup shows three previews without mode-switch buttons");
                check(service.previews.get(helmet,ids.getFirst(),0,PreviewCache.Mode.HAND).arena
                        ==service.previews.get(helmet,ids.getFirst(),0,PreviewCache.Mode.ICON).arena,"popup Hand shares Icon arena");
                return true;
            });
            capture(client,"alpha10-popup-three-previews.png");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));check(((FlatButton)button(client,"Worn")).selected,"original Worn switch remains selected");startFrames();click(client,"Hand");return true;});
            start=System.nanoTime();awaitPreviews(client,helmet,ids,PreviewCache.Mode.HAND);
            evidence.put("hand21ReadyMs",(System.nanoTime()-start)/1_000_000.0);
            evidence.put("handFrames",onClient(client,PreviewFlowSmoke::stopFrames));
            onClient(client,()->{
                for(String id:ids)check(service.previews.get(helmet,id,0,PreviewCache.Mode.ICON).arena
                        ==service.previews.get(helmet,id,0,PreviewCache.Mode.HAND).arena,"all hand previews share Icon geometry");
                return true;
            });
            capture(client,"alpha9-hand-21-sources.png");
            for(String item:List.of("diamond_chestplate","diamond_leggings","diamond_boots","elytra","player_head","carved_pumpkin")){
                Catalog.Target target=service.catalog.item("minecraft:"+item);
                onClient(client,()->{click(client,SmokeLabels.text("返回"));search(client,item);click(client,target.name());search(client,"PreviewPerf-01");click(client,"PreviewPerf-01.zip");return true;});
                awaitPreviews(client,target,List.of(ids.getFirst()),PreviewCache.Mode.WORN);
                capture(client,"alpha9-popup-worn-"+item+".png");
                onClient(client,()->{click(client,SmokeLabels.text("取消"));return true;});
            }
            onClient(client,()->{
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                click(client,"PreviewPerf-01.zip");return true;
            });
            capture(client,"alpha9-popup-worn-320x240.png");
            awaitPreviews(client,service.catalog.item("minecraft:carved_pumpkin"),List.of(ids.getFirst()),PreviewCache.Mode.HAND);
            capture(client,"alpha9-popup-hand-320x240.png");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));return true;});
            check(SmokeControls.reloads==reloads,"preview browsing and switching never reload global resources");
            evidence.put("reloadsDuringPreview",0);evidence.put("sources",ids.size());

        }catch(Throwable error){
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            Files.writeString(client.runDirectory.toPath().resolve("preview-flow-result.txt"),"FAIL: "+trace);throw error;
        }finally{
            onClient(client,()->{SmokeControls.measureFrames=false;client.setScreen(null);service.previews.invalidate();
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);
                client.options.getGuiScale().setValue(scale);client.onResolutionChanged();return true;});
            onClient(client,()->service.apply(original)).join();
            for(Path path:copies){check(path.getParent().equals(fixtures)&&path.getFileName().toString().startsWith("PreviewPerf-"),"fixture cleanup stays in test directory");Files.deleteIfExists(path);}
            Files.writeString(client.runDirectory.toPath().resolve("preview-flow-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
        Files.writeString(client.runDirectory.toPath().resolve("preview-flow-result.txt"),"PASS: 21 custom worn previews with no partial/budget failures, bounded preparation, lightweight equipment resources, shared Icon/Hand arenas, simultaneous Icon/Hand/Worn popup previews, retained original controls, chest/legs/boots/elytra/head/pumpkin wear, compact popup bounds, zero global reloads. Frame timings are recorded per isolated test environment.");
    }
    private static void awaitPreviews(MinecraftClient client,Catalog.Target target,List<String> ids,PreviewCache.Mode mode)throws Exception {
        await(client,()->{
            var cache=TextureMixerClient.SERVICE.previews;
            Set<String> keys=new HashSet<>();boolean ready=true;
            for(String id:ids){
                keys.add(PreviewCache.key(target,id,0,mode));
                var entry=cache.get(target,id,0,mode);
                if(entry.error!=null)throw new AssertionError(id+" "+entry.error);
                ready&=entry.ready();
            }
            cache.retainPending(keys);
            check((Integer)field(cache,"activePreparations")<=2,"CPU preparation concurrency stays bounded");
            return ready;
        });
    }
    private static void startFrames(){SmokeControls.frameNanos.clear();SmokeControls.measureFrames=true;}
    private static Map<String,Object> stopFrames(){
        SmokeControls.measureFrames=false;
        List<Long> frames=SmokeControls.frameNanos.stream().sorted().toList();
        if(frames.isEmpty())return Map.of("frames",0);
        return Map.of("frames",frames.size(),"medianMs",frames.get(frames.size()/2)/1_000_000.0,
                "p95Ms",frames.get((int)((frames.size()-1)*.95))/1_000_000.0,"maxMs",frames.getLast()/1_000_000.0);
    }
    private static Object field(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception error){throw new CompletionException(error);}}
    private static ButtonWidget button(MinecraftClient client,String text){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(widget->widget.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new AssertionError("Missing button "+text));}
    private static void click(MinecraftClient client,String text){Screen screen=client.currentScreen;var button=button(client,text);check(button.active,"enabled "+text);Click input=new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0));check(screen.mouseClicked(input,false),"handled "+text);screen.mouseReleased(input);}
    private static void search(MinecraftClient client,String text){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(text);}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(450);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{for(Object child:client.currentScreen.children())if(child instanceof ButtonWidget button)check(button.getX()>=0&&button.getY()>=0&&button.getRight()<=client.currentScreen.width&&button.getBottom()<=client.currentScreen.height,"control bounds "+button.getMessage().getString());ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,ignored->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Preview timeout");Thread.sleep(25);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> future=new CompletableFuture<>();client.execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});return future.join();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
