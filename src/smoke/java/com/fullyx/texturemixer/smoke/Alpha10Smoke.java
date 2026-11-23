package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.core.MixerConfig;
import com.fullyx.texturemixer.ui.MixerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Unit;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class Alpha10Smoke {
    public static void run(MinecraftClient client)throws Exception{
        Alpha9Smoke.run(client);
        PreviewFlowSmoke.run(client);
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        Path result=client.runDirectory.toPath().resolve("alpha10-result.txt");
        AtomicInteger failures=new AtomicInteger();
        List<String> actualNames=new ArrayList<>();
        try{
            String actual=System.getProperty("texturemixer.actualPackConfig");
            if(actual!=null){
                MixerConfig imported=MixerConfig.GSON.fromJson(Files.readString(Path.of(actual)),MixerConfig.class);
                MixerConfig draft=original.copy();List<String> stack=new ArrayList<>();
                for(String id:imported.mainStack()){
                    var record=imported.sources.get(id);
                    String registered=onClient(client,()->service.add(Path.of(record.canonicalRoot()),draft,false)).get(60,TimeUnit.SECONDS);
                    stack.add(registered);actualNames.add(record.lastKnownFolderName());
                }
                draft.setMainStack(stack);
                onClient(client,()->service.apply(draft)).get(120,TimeUnit.SECONDS);
                check(!service.busy&&service.mounted!=null,"real resource stack loads without atlas path failure");
                onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
            }
            onClient(client,()->{
                ((ReloadableResourceManagerImpl)client.getResourceManager()).registerReloader(new ResourceReloader(){
                    @Override public CompletableFuture<Void> reload(Store store,Executor prepare,Synchronizer sync,Executor apply){
                        return sync.whenPrepared(Unit.INSTANCE).thenRunAsync(()->{
                            if(failures.getAndUpdate(count->Math.max(0,count-1))>0)
                                throw new CompletionException(new java.io.IOException("alpha10 native atlas failure"));
                        },apply);
                    }
                });
                return true;
            });
            for(int count:List.of(1,2)){
                MixerScreen mixer=onClient(client,()->{var screen=new MixerScreen(service);client.setScreen(screen);return screen;});
                CompletableFuture<Void> application=onClient(client,()->{failures.set(count);return service.apply(original);});
                try{application.get(120,TimeUnit.SECONDS);throw new AssertionError("Injected native failure was committed");}
                catch(ExecutionException expected){check(expected.getCause()!=null,"native failure reaches application caller");}
                await(client,()->!service.busy&&client.getOverlay()==null);
                check(service.committed.renderSignature().equals(original.renderSignature()),"failed reload preserves committed configuration");
                check(service.mounted!=null,"previous generation is restored after native recovery");
                if(count==2)check(client.world==null,"forced reload failure exercises disconnect and title-screen recovery");
                onClient(client,()->{client.setScreen(mixer);mixer.tick();return true;});
                await(client,()->!mixer.libraryImporting());
                onClient(client,()->{
                    mixer.tick();
                    check(buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(SmokeLabels.text("管理"))),"pack page removes management entry");
                    check(buttons(client).stream().anyMatch(button->button.getMessage().getString().equals(SmokeLabels.text("自定义套装"))),"custom outfits entry is present");
                    check(buttons(client).stream().anyMatch(button->button.active&&button.getMessage().getString().equals(SmokeLabels.text("完成"))),"bottom controls recover");
                    var field=mixer.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                    field.setText("no-focused-pack");mixer.setFocused(null);
                    mixer.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE,0,0));
                    check(client.currentScreen!=mixer,"Escape exits after failed reload");
                    return true;
                });
                onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
            }
            onClient(client,()->{failures.set(1);client.reloadResources();return true;});
            await(client,()->!service.busy&&service.mounted==null&&client.getOverlay()==null);
            MixerScreen recoveryScreen=onClient(client,()->{var screen=new MixerScreen(service);client.setScreen(screen);return screen;});
            await(client,()->{recoveryScreen.tick();return !recoveryScreen.libraryImporting();});
            onClient(client,()->{
                recoveryScreen.tick();
                check(buttons(client).stream().anyMatch(button->button.active&&button.getMessage().getString().equals(SmokeLabels.text("应用"))),"external reload failure allows retry with unchanged selections");
                return true;
            });
            onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
            MixerScreen busyScreen=onClient(client,()->{var screen=new MixerScreen(service);client.setScreen(screen);return screen;});
            CompletableFuture<Void> background=onClient(client,()->{
                var future=service.apply(original);
                busyScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE,0,0));
                check(client.currentScreen!=busyScreen,"Escape can leave while application is running");
                return future;
            });
            background.get(120,TimeUnit.SECONDS);
            check(client.currentScreen!=busyScreen,"completed background apply does not reopen an exited screen");
            Files.writeString(result,"PASS: direct main selection and arrows, custom outfits naming, removed management entry, three simultaneous previews, compact layouts, real stack "+actualNames+", native reload failure, double failure with server disconnect, rollback, Escape and bottom controls, external reload failure and unchanged-selection retry, background exit. Includes alpha9/alpha7 and preview-flow regressions.");
        }catch(Throwable error){
            Files.writeString(result,"FAIL: "+error);
            throw error;
        }finally{
            failures.set(0);
            onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
        }
    }
    private static List<ButtonWidget> buttons(MinecraftClient client){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Recovery timeout");Thread.sleep(100);}
    }
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){
        CompletableFuture<T> future=new CompletableFuture<>();
        client.execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});
        return future.join();
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
