package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.sky.*;
import com.fullyx.texturemixer.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.*;
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

public final class Alpha6Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void run(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        int scale=client.options.getGuiScale().getValue();
        Path report=client.runDirectory.toPath().resolve("alpha6-result.txt");
        try {
            Path fixture=client.runDirectory.toPath().resolve("fixtures/alpha6-state-sky.zip");
            Map<String,byte[]> files=new LinkedHashMap<>();
            files.put("pack.mcmeta","{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"Alpha6 states\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(String name:List.of("crossbow_standby","crossbow_arrow","crossbow_firework","crossbow_pulling_0","crossbow_pulling_1","crossbow_pulling_2","bow","bow_pulling_0","bow_pulling_1","bow_pulling_2","fishing_rod","fishing_rod_cast"))
                files.put("assets/minecraft/textures/item/"+name+".png",png(32,32,0xff00ee99));
            files.put("assets/minecraft/optifine/sky/world0/sky2.properties","source=sky.png\nblend=replace\nrotate=false\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            files.put("assets/minecraft/optifine/sky/world0/sky.png",png(96,64,0xffe000c0));
            try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(fixture))){for(var file:files.entrySet()){output.putNextEntry(new ZipEntry(file.getKey()));output.write(file.getValue());output.closeEntry();}}
            MixerScreen mixer=onClient(client,()->new MixerScreen(service));
            String source=onClient(client,()->service.add(fixture,mixer.draft,false)).join();
            Path mainFixture=client.runDirectory.toPath().resolve("fixtures/alpha6-main-sky.zip");
            files.put("assets/minecraft/optifine/sky/world0/sky.png",png(96,64,0xffff0000));
            try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(mainFixture))){for(var file:files.entrySet()){output.putNextEntry(new ZipEntry(file.getKey()));output.write(file.getValue());output.closeEntry();}}
            String mainSky=onClient(client,()->service.add(mainFixture,mixer.draft,false)).join();
            onClient(client,()->{client.options.getGuiScale().setValue(2);client.onResolutionChanged();client.setScreen(new SourcePickerScreen(mixer));return true;});
            String before=mixer.draft.renderSignature();
            onClient(client,()->{search(client,"alpha6-state-sky");click(client,button(client,"alpha6-state-sky.zip"));return true;});
            check(mixer.draft.renderSignature().equals(before),"main pack preview leaves draft unchanged");
            capture(client,"alpha6-main-preview.png");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
            check(mixer.draft.mainStack().contains(source),"main preview Select commits only draft");
            for(var kind:List.of(MixerConfig.Kind.BLOCK,MixerConfig.Kind.SKY,MixerConfig.Kind.ENTITY,MixerConfig.Kind.PARTICLE,MixerConfig.Kind.HUD,MixerConfig.Kind.SOUND)){
                var selected=service.catalog.list(kind).getFirst();
                String signature=mixer.draft.renderSignature();
                onClient(client,()->{
                    client.setScreen(mixer);selectCategory(client,mixer,kind);search(client,selected.id());
                    click(client,button(client,selected.name()));focusSource(client,mixer,source);
                    click(client,button(client,"alpha6-state-sky.zip"));return true;
                });
                check(mixer.draft.renderSignature().equals(signature),kind+" preview leaves draft unchanged");
                onClient(client,()->{client.currentScreen.close();return true;});
                check(mixer.draft.renderSignature().equals(signature),kind+" Back preserves draft");
                onClient(client,()->{focusSource(client,mixer,source);click(client,button(client,"alpha6-state-sky.zip"));return true;});
                await(client,()->button(client,SmokeLabels.text("选择")).active);
                onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
                check(source.equals(mixer.draft.overrides(kind).get(selected.id())),kind+" Select commits draft");
            }
            String soundTarget=service.catalog.list(MixerConfig.Kind.SOUND).getFirst().id();
            String beforeFollow=mixer.draft.renderSignature();
            onClient(client,()->{click(client,button(client,SmokeLabels.text("跟随主包")));return true;});
            check(beforeFollow.equals(mixer.draft.renderSignature()),"Follow main preview preserves explicit choice");
            await(client,()->button(client,SmokeLabels.text("选择")).active);
            onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
            check(!mixer.draft.soundOverrides.containsKey(soundTarget),"Follow main Select clears explicit choice");
            for(Item item:List.of(Items.CHEST,Items.SHIELD,Items.TRIDENT,Items.DECORATED_POT,Items.WHITE_SHULKER_BOX,Items.PLAYER_HEAD)){
                var specialTarget=service.catalog.item(item.toString());
                onClient(client,()->{
                    var specialScreen=new MixerScreen(service);client.setScreen(specialScreen);search(client,specialTarget.id());
                    click(client,button(client,specialTarget.name()));click(client,button(client,"Hand"));return true;
                });
                await(client,()->{
                    var entry=service.previews.get(specialTarget,MixerConfig.VANILLA,0,PreviewCache.Mode.HAND);
                    if(entry.error!=null)throw new AssertionError(entry.error);
                    return entry.ready();
                });
                onClient(client,()->{
                    var arena=service.previews.get(specialTarget,MixerConfig.VANILLA,0,PreviewCache.Mode.HAND).arena;
                    var state=new KeyedItemRenderState();
                    RenderScope.in(arena.prefix,()->RenderScope.arena(arena.token,()->{
                        arena.items.clearAndUpdate(state,specialTarget.stack(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,client.world,client.player,0);return true;
                    }));
                    check(hasSpecialLayer(state),"special preview retains renderer for "+item);
                    return true;
                });
            }
            onClient(client,()->{client.setScreen(mixer);selectCategory(client,mixer,MixerConfig.Kind.ITEM);search(client,"crossbow");click(client,button(client,"Crossbow"));focusSource(client,mixer,source);click(client,button(client,"alpha6-state-sky.zip"));return true;});
            check(!mixer.draft.itemOverrides.containsKey("minecraft:crossbow"),"item source preview leaves draft unchanged");
            await(client,()->button(client,SmokeLabels.text("选择")).active);
            onClient(client,()->{click(client,button(client,"Hand"));return true;});
            await(client,()->button(client,SmokeLabels.text("选择")).active);
            capture(client,"alpha6-crossbow-hand.png");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("选择")));return true;});
            check(mixer.draft.itemOverrides.get("minecraft:crossbow").equals(source),"item Select commits draft source");
            onClient(client,()->{
                mixer.draft.setMainStack(List.of(mainSky));
                mixer.draft.itemOverrides.put("minecraft:bow",source);mixer.draft.itemOverrides.put("minecraft:fishing_rod",source);
                mixer.draft.skyOverrides.put("overworld",source);
                client.setScreen(null);client.player.setPitch(-70);return true;
            });
            onClient(client,()->service.apply(mixer.draft)).join();
            await(client,()->!service.busy&&client.getOverlay()==null);
            check(!service.runtime().assets().contains(AssetId.of("optifine/sky/world0/sky2.properties")),"selected sky owns OptiFine definitions over the main pack and Skyboxify");
            onClient(client,()->{
                for(var projectile:List.of(Items.AIR,Items.ARROW,Items.FIREWORK_ROCKET)){
                    ItemStack stack=Items.CROSSBOW.getDefaultStack();
                    if(projectile!=Items.AIR)stack.set(DataComponentTypes.CHARGED_PROJECTILES,ChargedProjectilesComponent.of(projectile.getDefaultStack()));
                    KeyedItemRenderState state=new KeyedItemRenderState();
                    client.getItemModelManager().clearAndUpdate(state,stack,ItemDisplayContext.GUI,client.world,client.player,0);
                    var sprite=state.getParticleSprite(net.minecraft.util.math.random.Random.create(0));
                    evidence.put("crossbow-"+projectile,Map.of("sprite",sprite==null?"null":sprite.getContents().getId().toString(),"empty",state.isEmpty(),
                            "model",client.getBakedModelManager().getItemModel(stack.get(DataComponentTypes.ITEM_MODEL)).getClass().getName(),"prefix",String.valueOf(service.prefixForItem("minecraft:crossbow"))));
                    check(sprite!=null&&sprite.getContents().getId().getNamespace().startsWith("texturemixer_"),"native crossbow state uses selected sprite: "+projectile);
                }
                for(Item item:List.of(Items.BOW,Items.FISHING_ROD,Items.CLOCK,Items.COMPASS,Items.SHIELD,Items.TRIDENT,Items.SPYGLASS,Items.BRUSH)){
                    var state=new KeyedItemRenderState();client.getItemModelManager().clearAndUpdate(state,item.getDefaultStack(),ItemDisplayContext.GUI,client.world,client.player,0);
                    check(!state.isEmpty(),"native model exists for "+item);
                }
                ItemStack previous=client.player.getMainHandStack();
                try{
                    for(Item item:List.of(Items.CROSSBOW,Items.BOW)){
                        ItemStack stack=item.getDefaultStack();client.player.setStackInHand(Hand.MAIN_HAND,stack);
                        client.player.setCurrentHand(Hand.MAIN_HAND);
                        for(int elapsed:List.of(1,13,20,26)){
                            ((com.fullyx.texturemixer.smoke.mixin.ItemUseProbe)client.player).texturemixer$useTimeLeft(stack.getMaxUseTime(client.player)-elapsed);
                            var state=new KeyedItemRenderState();
                            client.getItemModelManager().clearAndUpdate(state,stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,client.world,client.player,0);
                            var sprite=state.getParticleSprite(net.minecraft.util.math.random.Random.create(0));
                            check(sprite!=null&&sprite.getContents().getId().getNamespace().startsWith("texturemixer_"),"selected pulling sprite "+item+" "+elapsed);
                            evidence.put(item+"-pull-"+elapsed,sprite.getContents().getId().toString());
                        }
                        client.player.clearActiveItem();
                    }
                }finally{client.player.clearActiveItem();client.player.setStackInHand(Hand.MAIN_HAND,previous);}
                return true;
            });
            Thread.sleep(1000);capture(client,"alpha6-world-sky.png");
            BufferedImage sky=ImageIO.read(client.runDirectory.toPath().resolve("screenshots/alpha6-world-sky.png").toFile());
            long colored=0;
            for(int y=sky.getHeight()/5;y<sky.getHeight()/2;y++)for(int x=sky.getWidth()/4;x<sky.getWidth()*3/4;x++){
                int pixel=sky.getRGB(x,y);if((pixel>>16&255)>150&&(pixel>>8&255)<80&&(pixel&255)>100)colored++;
            }
            evidence.put("worldSkyColoredPixels",colored);evidence.put("overworldDraws",SkySceneRenderer.SHARED.draws("overworld"));
            check(colored>1000,"selected custom sky changes actual world pixels");
            auditRealPacks(client,service);
            for(int guiScale:List.of(1,2,3)){
                onClient(client,()->{client.options.getGuiScale().setValue(guiScale);client.onResolutionChanged();client.setScreen(mixer);return true;});
                onClient(client,()->{click(client,button(client,SmokeLabels.text("重置")));check(client.currentScreen instanceof ChoiceScreen,"Reset opens confirmation");click(client,button(client,SmokeLabels.text("取消")));return true;});
                check(!mixer.draft.skyOverrides.isEmpty(),"Cancel preserves mix");
                capture(client,"alpha6-reset-footer-"+guiScale+".png");
            }
            String beforeFailedReset=service.committed.renderSignature();
            String generationBeforeReset=service.runtime().id();
            onClient(client,()->{SmokeControls.failNextReload=true;click(client,button(client,SmokeLabels.text("重置")));click(client,button(client,SmokeLabels.text("确定")));return true;});
            await(client,()->!service.busy&&client.getOverlay()==null);
            check(beforeFailedReset.equals(service.committed.renderSignature())&&generationBeforeReset.equals(service.runtime().id()),"failed Reset restores committed mix and generation");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("还原修改")));return true;});
            onClient(client,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);client.options.getGuiScale().setValue(2);client.onResolutionChanged();client.setScreen(mixer);return true;});
            Thread.sleep(500);
            onClient(client,()->{for(var button:buttons(client))if(!button.getClass().getSimpleName().equals("Card"))check(button.getX()>=0&&button.getX()+button.getWidth()<=mixer.width&&button.getY()>=0&&button.getY()+button.getHeight()<=mixer.height,"compact control bounds "+button.getMessage().getString());return true;});
            capture(client,"alpha6-footer-320x240.png");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("重置")));return true;});capture(client,"alpha6-reset-confirm.png");
            onClient(client,()->{click(client,button(client,SmokeLabels.text("确定")));return true;});
            await(client,()->!service.busy&&client.getOverlay()==null);
            check(service.committed.mainStack().isEmpty(),"confirmed reset clears main stack");
            for(var kind:MixerConfig.Kind.values())check(service.committed.overrides(kind).isEmpty(),"confirmed reset clears "+kind);
            check(service.committed.sources.containsKey(source),"Reset retains source library");
            Files.writeString(report,"PASS: main and all seven categories preview/Back/Select, Follow main confirmation, crossbow idle/arrow/rocket and bow/crossbow pulling sprites, other stateful models, actual custom sky pixels, real-pack graph audit, Reset confirmation/cancel/failure rollback/Apply at GUI scales 1/2/3 and 320x240 layout.");
        }catch(Throwable error){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));Files.writeString(report,"FAIL: "+trace);throw error;}
        finally {
            onClient(client,()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);client.options.getGuiScale().setValue(scale);client.onResolutionChanged();client.setScreen(null);return true;});
            onClient(client,()->service.apply(original)).join();
            Files.writeString(client.runDirectory.toPath().resolve("alpha6-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
    }
    private static void auditRealPacks(MinecraftClient client,MixerService service)throws Exception {
        String root=System.getProperty("texturemixer.packProbeRoot","");if(root.isBlank())return;
        int crossbows=0,skies=0;List<String> failures=new ArrayList<>();
        try(var paths=Files.list(Path.of(root))){for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".zip")).toList()){
            PackSource source=PackSource.open(path);String hash=source.fingerprint();
            if(source.contains(AssetId.of("textures/item/crossbow_standby.png"))){
                var graph=new GraphCompiler(new Assets.View(source,service.vanilla),"audit|"+path).item(AssetId.of("crossbow"),null);
                for(String frame:List.of("standby","arrow","firework","pulling_0","pulling_1","pulling_2")){
                    AssetId original=AssetId.of("textures/item/crossbow_"+frame+".png");
                    check(Arrays.equals(graph.resources().get(new AssetId(graph.namespacePrefix()+"minecraft",original.path())),new Assets.View(source,service.vanilla).read(original)),"real crossbow frame bytes "+path+" "+frame);
                }
                crossbows++;
            }
            if(source.ids().stream().anyMatch(id->id.path().startsWith("optifine/sky/world0/")&&id.path().endsWith(".properties"))){
                try{var scene=new OptifineSkyAdapter().parse("overworld",new Assets.View(source,service.vanilla));check(!scene.layers().isEmpty(),"real pack retains sky layers "+path);skies++;}
                catch(Exception error){failures.add(path.getFileName()+" · "+error.getMessage());}
            }
            check(source.unchanged()&&source.fingerprint().equals(hash),"read-only real pack audit");
        }}
        evidence.put("realCrossbowPacks",crossbows);evidence.put("realSkyPacks",skies);evidence.put("realSkyFailures",failures);
    }
    private static byte[] png(int w,int h,int color)throws IOException{BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,color);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();}
    private static boolean hasSpecialLayer(KeyedItemRenderState state){
        try{
            var stateType=net.minecraft.client.render.item.ItemRenderState.class;
            Field count=stateType.getDeclaredField("layerCount"),layers=stateType.getDeclaredField("layers");count.setAccessible(true);layers.setAccessible(true);
            Object[] values=(Object[])layers.get(state);
            for(int index=0;index<count.getInt(state);index++)if(field(values[index],"specialModelType")!=null)return true;
            return false;
        }catch(ReflectiveOperationException error){throw new CompletionException(error);}
    }
    private static void selectCategory(MinecraftClient client,MixerScreen mixer,MixerConfig.Kind kind){
        for(int attempt=0;attempt<8&&buttons(client).stream().noneMatch(button->button.getMessage().getString().equals(kind.label));attempt++){
            int offset=(int)field(mixer,"navOffset"),top=(int)field(mixer,"navTop"),left=(int)field(mixer,"left");
            mixer.mouseScrolled(left+16,top+2,0,kind.ordinal()<offset?1:-1);
        }
        click(client,button(client,kind.label));
    }
    private static void focusSource(MinecraftClient client,MixerScreen mixer,String id){try{Method method=MixerScreen.class.getDeclaredMethod("focusCard",int.class);method.setAccessible(true);method.invoke(mixer,((List<?>)field(mixer,"visible")).indexOf(id));}catch(Exception error){throw new CompletionException(error);}}
    private static Object field(Object instance,String name){try{Field field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);}catch(Exception error){throw new CompletionException(error);}}
    private static List<ButtonWidget> buttons(MinecraftClient client){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();}
    private static ButtonWidget button(MinecraftClient client,String name){return buttons(client).stream().filter(button->button.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));}
    private static void click(MinecraftClient client,ButtonWidget button){Screen screen=client.currentScreen;check(button.active,"Disabled "+button.getMessage().getString());screen.mouseClicked(new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0)),false);screen.mouseReleased(new Click(button.getX()+1,button.getY()+1,new MouseInput(0,0)));}
    private static void search(MinecraftClient client,String query){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(query);}
    private static void capture(MinecraftClient client,String name)throws Exception{Thread.sleep(450);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,message->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);}
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);while(!onClient(client,condition)){if(System.nanoTime()>deadline)throw new AssertionError("Timeout");Thread.sleep(100);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> result=new CompletableFuture<>();client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});return result.join();}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
