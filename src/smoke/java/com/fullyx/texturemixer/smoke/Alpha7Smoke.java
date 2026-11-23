package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Run after the entire alpha.7 change is implemented. */
public final class Alpha7Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();

    public static void run(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        int scale=client.options.getGuiScale().getValue();
        String[] importedGroup={null};
        try {
            Path fixtures=client.runDirectory.toPath().resolve("fixtures");
            Path sourceA=fixtures.resolve("alpha7-Boss-Bar.zip"),sourceB=fixtures.resolve("alpha7-Other.zip");
            Files.copy(fixtures.resolve("Pack-A.zip"),sourceA,StandardCopyOption.REPLACE_EXISTING);
            Files.copy(fixtures.resolve("Pack-B.zip"),sourceB,StandardCopyOption.REPLACE_EXISTING);
            onClient(client,()->{
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                client.setScreen(new OptionsScreen(new TitleScreen(),client.options));
                click(client,button(client,net.minecraft.text.Text.translatable("options.resourcepack").getString()));return true;
            });
            check(client.currentScreen instanceof MixerScreen,"vanilla Options resource-pack entry opens integrated editor");
            MixerScreen mixer=(MixerScreen)client.currentScreen;
            await(client,()->Boolean.TRUE.equals(field(mixer,"discovered"))&&!mixer.libraryImporting());
            check(Arrays.stream(client.options.allKeys).noneMatch(binding->binding.getId().equals("key.texturemixer.open")),"Y opener is unregistered");
            String a=onClient(client,()->service.add(sourceA,mixer.draft,false)).join();
            String b=onClient(client,()->service.add(sourceB,mixer.draft,false)).join();
            onClient(client,()->{
                try{service.library.markStandalone(a);service.library.markStandalone(b);}catch(IOException error){throw new CompletionException(error);}
                mixer.draft.setMainStack(List.of());for(var kind:MixerConfig.Kind.values())mixer.draft.overrides(kind).clear();
                client.setScreen(mixer);search(client,"BossBar");return true;
            });
            check(buttons(client).stream().anyMatch(widget->widget.getMessage().getString().equals("alpha7-Boss-Bar.zip")),"joined search finds hyphenated pack name");
            int reloads=SmokeControls.reloads;
            String before=mixer.draft.renderSignature();
            onClient(client,()->{packAction(client,"添加","alpha7-Boss-Bar.zip");return true;});
            check(client.currentScreen==mixer,"main activation stays in editor without confirmation");
            check(mixer.draft.mainStack().equals(List.of(a)),"main arrow immediately adds the source to the right pane");
            capture(client,"alpha10-main-selected.png");
            onClient(client,()->{search(client,"Other");packAction(client,"添加","alpha7-Other.zip");return true;});
            check(mixer.draft.mainStack().equals(List.of(b,a)),"new confirmed main layer receives top priority");
            onClient(client,()->{search(client,"BossBar");packAction(client,"上移，提高优先级","alpha7-Boss-Bar.zip");search(client,"");return true;});
            check(mixer.draft.mainStack().equals(List.of(a,b)),"native main reorder changes priority");
            await(client,()->button(client,SmokeLabels.text("撤销")).active);
            onClient(client,()->{click(client,button(client,SmokeLabels.text("撤销")));return true;});
            check(mixer.draft.mainStack().equals(List.of(b,a)),"Undo restores main priority");
            capture(client,"alpha7-undo-visible.png");
            check(((String)field(mixer,"undoToast")).contains(SmokeLabels.text("上移主包")),"Undo toast names the reverted action");
            Thread.sleep(3500);capture(client,"alpha7-undo-hidden.png");
            Path groupFolder=fixtures.resolve("alpha7-Library");Files.createDirectories(groupFolder);
            Files.copy(fixtures.resolve("Pack-A.zip"),groupFolder.resolve("Boss-Bar-Inside.zip"),StandardCopyOption.REPLACE_EXISTING);
            var imported=onClient(client,()->service.library.importFolder(groupFolder,mixer.draft)).join();
            importedGroup[0]=imported.group().id();
            onClient(client,()->{client.setScreen(mixer);search(client,"alpha7Library");click(client,button(client,"alpha7-Library"));return true;});
            check(buttons(client).stream().anyMatch(widget->widget.getMessage().getString().equals("Boss-Bar-Inside.zip")),"entering folder clears root search and lists its packs");
            onClient(client,()->{search(client,"BossBarInside");return true;});
            check(buttons(client).stream().anyMatch(widget->widget.getMessage().getString().equals("Boss-Bar-Inside.zip")),"joined search works inside folder");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("返回")));return true;});
            onClient(client,()->{
                packAction(client,"移除","alpha7-Other.zip");return true;
            });
            check(mixer.draft.mainStack().equals(List.of(a)),"native main remove changes draft");
            await(client,()->button(client,SmokeLabels.text("撤销")).active);
            onClient(client,()->{click(client,button(client,SmokeLabels.text("撤销")));return true;});
            check(mixer.draft.mainStack().equals(List.of(b,a)),"Undo restores removed main layer");
            for(var kind:MixerConfig.Kind.values()){
                Catalog.Target target=kind==MixerConfig.Kind.ITEM?service.catalog.item("minecraft:diamond_sword")
                        :kind==MixerConfig.Kind.BLOCK?service.catalog.block("minecraft:diamond_block")
                        :kind==MixerConfig.Kind.SKY?service.catalog.skies.stream().filter(value->value.id().equals("overworld")).findFirst().orElseThrow()
                        :service.catalog.list(kind).getFirst();
                String signature=mixer.draft.renderSignature();
                onClient(client,()->{
                    if(kind.interfaceCategory()&&kind!=MixerConfig.Kind.HUD)selectTab(client,"HUD");
                    selectTab(client,kind.label);search(client,target.id());click(client,button(client,target.name()));search(client,"BossBar");
                    click(client,button(client,"alpha7-Boss-Bar.zip"));return true;
                });
                check(client.currentScreen instanceof SelectionDialog,"source stays in grid and opens modal for "+kind);
                check(signature.equals(mixer.draft.renderSignature()),"source modal preserves draft for "+kind);
                onClient(client,()->{client.currentScreen.close();return true;});
                check(client.currentScreen==mixer&&signature.equals(mixer.draft.renderSignature()),"source modal returns to same editor for "+kind);
                onClient(client,()->{click(client,button(client,"alpha7-Boss-Bar.zip"));return true;});
                if(kind==MixerConfig.Kind.FONT)await(client,()->button(client,SmokeLabels.text("选择")).active);
                onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
                check(a.equals(mixer.draft.overrides(kind).get(target.id())),"modal Select updates "+kind);
                await(client,()->button(client,SmokeLabels.text("撤销")).active);
                onClient(client,()->{click(client,button(client,SmokeLabels.text("撤销")));return true;});
                check(signature.equals(mixer.draft.renderSignature()),"single Undo restores "+kind);
                onClient(client,()->{click(client,button(client,"alpha7-Boss-Bar.zip"));return true;});
                if(kind==MixerConfig.Kind.FONT)await(client,()->button(client,SmokeLabels.text("选择")).active);
                onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
            }
            onClient(client,()->{selectTab(client,"HUD");search(client,"BossBar");return true;});
            check(!((List<?>)field(mixer,"visible")).isEmpty(),"BossBar finds HUD Boss Bar targets");
            check(SmokeControls.reloads==reloads,"browsing/modal/Undo do not reload global resources");
            onClient(client,()->{selectTab(client,SmokeLabels.text("资源包"));search(client,"");return true;});
            MixerConfig template=mixer.draft.copy();
            onClient(client,()->{mixer.edit(SmokeLabels.text("测试主包移除"),()->mixer.draft.setMainStack(List.of(b)));mixer.acceptTemplate(template,"Alpha7 test");return true;});
            await(client,()->button(client,SmokeLabels.text("撤销")).active);
            onClient(client,()->{click(client,button(client,SmokeLabels.text("撤销")));return true;});
            check(mixer.draft.mainStack().equals(List.of(b)),"template load is undoable");
            onClient(client,()->{mixer.acceptTemplate(template,"Alpha7 test");return true;});
            onClient(client,()->{mixer.edit(SmokeLabels.text("测试重置单项"),()->mixer.draft.itemOverrides.put("minecraft:diamond_sword",a));return true;});
            String beforeReset=mixer.draft.renderSignature();
            onClient(client,()->{click(client,button(client,SmokeLabels.text("重置混搭")));click(client,button(client,SmokeLabels.text("取消")));return true;});
            check(beforeReset.equals(mixer.draft.renderSignature()),"Reset Cancel preserves draft");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("重置混搭")));click(client,button(client,SmokeLabels.text("确定")));return true;});
            check(mixer.draft.mainStack().equals(template.mainStack())&&Arrays.stream(MixerConfig.Kind.values()).allMatch(kind->mixer.draft.overrides(kind).isEmpty()),"Reset clears overrides and preserves main stack");
            check(SmokeControls.reloads==reloads,"Reset is staged until Apply or Done");
            await(client,()->button(client,SmokeLabels.text("撤销")).active);
            onClient(client,()->{click(client,button(client,SmokeLabels.text("撤销")));return true;});
            check(beforeReset.equals(mixer.draft.renderSignature()),"Undo restores full mix after Reset");
            for(int guiScale:List.of(1,2,3)){
                onClient(client,()->{client.options.getGuiScale().setValue(guiScale);client.onResolutionChanged();client.setScreen(mixer);return true;});
                capture(client,"alpha7-packs-scale-"+guiScale+".png");
                onClient(client,()->{assertBounds(client);selectTab(client,"Item");search(client,"diamond_sword");click(client,button(client,"Diamond Sword"));search(client,"BossBar");click(client,button(client,"alpha7-Boss-Bar.zip"));return true;});
                capture(client,"alpha7-source-modal-scale-"+guiScale+".png");
                onClient(client,()->{assertBounds(client);click(client,button(client,SmokeLabels.text("取消")));selectTab(client,SmokeLabels.text("资源包"));return true;});
            }
            onClient(client,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);client.options.getGuiScale().setValue(2);client.onResolutionChanged();client.setScreen(mixer);return true;});
            Thread.sleep(500);onClient(client,()->{assertBounds(client);return true;});capture(client,"alpha7-packs-320x240.png");
            onClient(client,()->{selectTab(client,"Sound & Music");search(client,"ambient");return true;});capture(client,"alpha7-category-320x240.png");
            onClient(client,()->{assertBounds(client);selectTab(client,SmokeLabels.text("资源包"));click(client,button(client,SmokeLabels.text("应用")));return true;});
            await(client,()->!service.busy&&client.getOverlay()==null);
            check(service.committed.renderSignature().equals(mixer.draft.renderSignature()),"Apply commits complete mixed draft");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("完成")));return true;});
            check(client.currentScreen instanceof OptionsScreen,"Done returns through vanilla parent callback");
            evidence.put("categories",MixerConfig.Kind.values().length);evidence.put("mainStack",template.mainStack());evidence.put("undoToast",field(mixer,"undoToast"));
            evidence.put("guiScales",List.of(1,2,3));evidence.put("compactViewport","320x240");
            Files.writeString(client.runDirectory.toPath().resolve("alpha7-result.txt"),"PASS: native resource-pack Options entry/return, no Y binding, joined-word search, main and all seven source modals, Cancel/Select, step Undo with toast, main order, template/reset Undo, staged Reset, GUI scales 1/2/3 and 320x240, actual Apply.");
        }catch(Throwable error){
            capture(client,"alpha7-failure.png");
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            Files.writeString(client.runDirectory.toPath().resolve("alpha7-result.txt"),"FAIL: "+trace);throw error;
        }finally{
            if(importedGroup[0]!=null)onClient(client,()->{try{service.library.removeGroup(importedGroup[0]);}catch(IOException error){throw new CompletionException(error);}return true;});
            onClient(client,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);client.options.getGuiScale().setValue(scale);client.onResolutionChanged();client.setScreen(null);return true;});
            onClient(client,()->service.apply(original)).join();
            Files.writeString(client.runDirectory.toPath().resolve("alpha7-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
    }

    private static void selectTab(MinecraftClient client,String label){
        for(int attempt=0;attempt<8&&buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(label));attempt++){
            var next=button(client,SmokeLabels.text("后"));if(!next.active)break;click(client,next);
        }
        if(buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(label)))
            for(int attempt=0;attempt<8&&buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(label));attempt++)click(client,button(client,SmokeLabels.text("前")));
        click(client,button(client,label));
    }
    private static void assertBounds(MinecraftClient client){for(var button:buttons(client))if(!button.getClass().getSimpleName().equals("Card"))check(button.getX()>=0&&button.getY()>=0&&button.getX()+button.getWidth()<=client.currentScreen.width&&button.getY()+button.getHeight()<=client.currentScreen.height,"control bounds "+button.getMessage().getString());}
    private static Object field(Object instance,String name){try{Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(Exception error){throw new CompletionException(error);}}
    private static List<ButtonWidget> buttons(MinecraftClient client){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();}
    private static ButtonWidget button(MinecraftClient client,String name){return buttons(client).stream().filter(button->button.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));}
    private static void click(MinecraftClient client,ButtonWidget button){Screen screen=client.currentScreen;check(button.active,"Disabled "+button.getMessage().getString());double cx=button.getX()+button.getWidth()/2.0,cy=button.getY()+button.getHeight()/2.0;
        if(button.getMessage().getString().equals(SmokeLabels.text("启用")))evidence.put("enableHits",screen.children().stream().filter(net.minecraft.client.gui.widget.ClickableWidget.class::isInstance).map(net.minecraft.client.gui.widget.ClickableWidget.class::cast).filter(widget->widget.isMouseOver(cx,cy)).map(widget->widget.getClass().getSimpleName()+" "+widget.getMessage().getString()+" "+widget.getX()+","+widget.getY()).toList());
        boolean handled=screen.mouseClicked(new Click(cx,cy,new MouseInput(0,0)),false);
        if(button.getMessage().getString().equals(SmokeLabels.text("启用"))){evidence.put("enableHandled",handled);evidence.put("enableBusy",TextureMixerClient.SERVICE.busy);}
        screen.mouseReleased(new Click(button.getX()+1,button.getY()+1,new MouseInput(0,0)));}
    private static void packAction(MinecraftClient client,String label,String name){
        Screen screen=client.currentScreen;
        var action=SmokeLabels.packAction(client,label,name);
        check(action.active,"pack action is enabled "+label);
        Click input=new Click(action.getX()+action.getWidth()/2.0,action.getY()+action.getHeight()/2.0,new MouseInput(0,0));
        check(screen.mouseClicked(input,false),"pack row action "+name);
        screen.mouseReleased(input);
    }
    private static void search(MinecraftClient client,String text){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(text);}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(450);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,message->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Timeout");Thread.sleep(100);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});return result.join();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
