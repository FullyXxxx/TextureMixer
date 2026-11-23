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
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.*;

public final class Alpha11Smoke {
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void run(MinecraftClient client)throws Exception{
        var service=TextureMixerClient.SERVICE;
        MixerConfig original=onClient(client,()->service.committed.copy());
        String dynamicPack=System.getProperty("texturemixer.dynamicPack");
        if(dynamicPack==null||dynamicPack.isBlank())throw new IllegalArgumentException("Set -PdynamicPack to the resource pack to test.");
        Path packPath=Path.of(dynamicPack);
        String before=Assets.hash(Files.readAllBytes(packPath));
        Path fixture=client.runDirectory.toPath().resolve("fixtures/alpha11-effects.zip").toAbsolutePath();
        int scale=client.options.getGuiScale().getValue();
        Path result=client.runDirectory.toPath().resolve("alpha11-result.txt");
        try{
            MixerConfig registered=original.copy();
            String source=onClient(client,()->service.add(packPath,registered,false)).get(60,TimeUnit.SECONDS);
            createEffects(fixture);
            String effects=onClient(client,()->service.add(fixture,registered,false)).get(60,TimeUnit.SECONDS);
            check(service.catalog.skies.stream().map(Catalog.Target::name).toList().equals(List.of("Overworld Sky","End Sky","Rain & Snow")),"requested Sky order");
            check(service.catalog.list(MixerConfig.Kind.ENCHANT).size()==2,"both glint choices exist");
            MixerConfig main=registered.copy();main.clearOverrides();main.setMainStack(List.of(source));
            onClient(client,()->service.apply(main)).get(120,TimeUnit.SECONDS);
            int nativeStates=onClient(client,()->verifyNativeStates(client,source));
            MixerConfig mixed=registered.copy();mixed.clearOverrides();mixed.setMainStack(List.of());
            for(String id:itemIds(service.sources.get(source)))mixed.itemOverrides.put(id,source);
            mixed.hudOverrides.put(ResourceCompiler.FIRE_OVERLAY_TARGET,effects);
            for(var glint:service.catalog.list(MixerConfig.Kind.ENCHANT))mixed.enchantOverrides.put(glint.id(),effects);
            onClient(client,()->service.apply(mixed)).get(120,TimeUnit.SECONDS);
            nativeStates+=onClient(client,()->verifyNativeStates(client,source));
            evidence.put("mainAndIsolatedNativeStates",nativeStates);
            onClient(client,()->{
                SmokeControls.fireSprite=null;client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                client.player.setOnFire(true);client.setScreen(null);return true;
            });
            await(client,()->SmokeControls.fireSprite!=null);
            onClient(client,()->{
                var expected=client.getAtlasManager().getSprite(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,Identifier.of(ResourceCompiler.FIRE_OVERLAY_SPRITE.toString())));
                check(SmokeControls.fireSprite==expected,"first-person fire renderer actually uses the selected sprite");return true;
            });
            capture(client,"alpha11-first-person-fire.png");
            onClient(client,()->{client.player.setOnFire(false);return true;});
            onClient(client,()->{
                for(var glint:service.catalog.list(MixerConfig.Kind.ENCHANT)){
                    AssetId id=AssetId.of(glint.id());
                    try(var stream=client.getResourceManager().getResource(Identifier.of(id.toString())).orElseThrow().getInputStream()){
                        check(Arrays.equals(stream.readAllBytes(),service.sources.get(effects).read(id)),"selected global glint is readable");
                    }catch(IOException error){throw new CompletionException(error);}
                }
                var fire=client.getAtlasManager().getSprite(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,Identifier.of(ResourceCompiler.FIRE_OVERLAY_SPRITE.toString())));
                check(!fire.getContents().getId().equals(Identifier.of("missingno"))&&fire.getContents().isAnimated(),"independent animated screen fire is stitched");
                check(!service.mounted.assets().contains(AssetId.of(ResourceCompiler.FIRE_OVERLAY_TARGET)),"screen fire does not overwrite world fire texture");
                return true;
            });
            MixerScreen mixer=onClient(client,()->{
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                var screen=new MixerScreen(service);client.setScreen(screen);return screen;
            });
            await(client,()->!mixer.libraryImporting());
            onClient(client,()->{tab(client,"Item");search(client,"diamond_sword");click(client,service.catalog.item("minecraft:diamond_sword").name());search(client,"Vanilla");click(client,"Vanilla");return true;});
            check(client.currentScreen instanceof SelectionDialog,"single-item selection remains modal");
            check(((List<?>)field(client.currentScreen,"itemModes")).size()==2,"nonwearable item omits Worn");
            capture(client,"alpha11-no-worn.png");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));click(client,SmokeLabels.text("返回"));search(client,"diamond_helmet");click(client,service.catalog.item("minecraft:diamond_helmet").name());search(client,"Vanilla");click(client,"Vanilla");return true;});
            check(((List<?>)field(client.currentScreen,"itemModes")).size()==3,"wearable item retains Worn");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));click(client,SmokeLabels.text("返回"));search(client,"mace");click(client,"Mace");search(client,"Better Spear");return true;});
            Catalog.Target mace=service.catalog.item("minecraft:mace");
            await(client,()->ready(service,mace,source));
            onClient(client,()->{click(client,"Better Spear and Mace.zip");return true;});
            await(client,()->Boolean.TRUE.equals(field(client.currentScreen,"itemStateControls")));
            onClient(client,()->{click(client,"State · Normal");return true;});
            check(((ItemPreviewState)field(mixer,"itemVariant")).enchantments().size()==2,"popup switches to declared enchanted state");
            capture(client,"alpha11-enchanted-popup.png");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));return true;});
            Catalog.Target spear=service.catalog.item("minecraft:diamond_spear");
            await(client,()->{var entry=service.previews.get(spear,source,0);service.previews.retainPending(Set.of(PreviewCache.key(spear,source,0,PreviewCache.Mode.ICON),PreviewCache.key(mace,source,0,PreviewCache.Mode.ICON)));return entry.ready();});
            PreviewCache.Arena spearArena=service.previews.get(spear,source,0).arena;
            onClient(client,()->{
                spearArena.stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,false);
                client.setScreen(new AnimationScreen(spearArena));return true;
            });
            Path a=capture(client,"alpha11-animated-a.png");Thread.sleep(650);Path b=capture(client,"alpha11-animated-b.png");
            BufferedImage first=ImageIO.read(a.toFile()),second=ImageIO.read(b.toFile());
            List<Long> differences=new ArrayList<>();
            for(int index=0;index<3;index++){
                long difference=pixelDifferences(first,second,(40+index*150)*2,90*2,112*2,112*2);
                check(difference>0,"animated texture changes GPU pixels in pane "+index);differences.add(difference);
            }
            evidence.put("animatedGpuPixelDifferences",differences);
            onClient(client,()->{client.setScreen(mixer);tab(client,"Particle");search(client,"");return true;});
            ResourcePreviewCache cache=(ResourcePreviewCache)field(mixer,"resourcePreviews");
            for(String id:List.of("instant_effect","large_smoke","squid_ink","mycelium","poof","portal","reverse_portal","smoke","sneeze","snowflake")){
                Catalog.Target target=service.catalog.list(MixerConfig.Kind.PARTICLE).stream().filter(value->value.id().equals("minecraft:"+id)).findFirst().orElseThrow();
                onClient(client,()->{search(client,id);return true;});
                await(client,()->{
                    var entry=cache.get(target,MixerConfig.VANILLA);
                    if(entry.error!=null)throw new AssertionError(entry.error);
                    return entry.texture!=null;
                });
                onClient(client,()->{
                    var entry=cache.get(target,MixerConfig.VANILLA);boolean[] visible=(boolean[])field(entry,"visibleFrames");
                    for(boolean frame:visible)check(frame,"vanilla particle has a visible image in every sampled frame "+id);
                    return true;
                });
                capture(client,"alpha11-particle-"+id+".png");
            }
            onClient(client,()->{
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),640,480);client.onResolutionChanged();
                tab(client,"Enchant");return true;
            });
            capture(client,"alpha11-enchant-320x240.png");
            onClient(client,()->{tab(client,"HUD");search(client,"Fire Overlay");click(client,"Fire Overlay");search(client,"alpha11-effects");click(client,"alpha11-effects.zip");return true;});
            capture(client,"alpha11-fire-overlay-320x240.png");
            onClient(client,()->{click(client,SmokeLabels.text("取消"));return true;});
            check(before.equals(Assets.hash(Files.readAllBytes(packPath))),"provided dynamic ZIP remains byte-identical");
        }catch(Throwable error){
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));Files.writeString(result,"FAIL: "+trace);throw error;
        }finally{
            onClient(client,()->{client.setScreen(null);org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);client.options.getGuiScale().setValue(scale);client.onResolutionChanged();return true;});
            onClient(client,()->service.apply(original)).get(120,TimeUnit.SECONDS);
            check(fixture.getFileName().toString().equals("alpha11-effects.zip")&&fixture.startsWith(client.runDirectory.toPath().toAbsolutePath()),"test cleanup path");
            Files.deleteIfExists(fixture);
            Files.writeString(client.runDirectory.toPath().resolve("alpha11-evidence.json"),MixerConfig.GSON.toJson(evidence));
        }
        Alpha10Smoke.run(client);
        Files.writeString(result,"PASS: conditional Worn, ordered Sky, Enchant and independent animated Fire Overlay, legacy config compatibility, native main/isolated dynamic item branches, GPU animation for spear icon/hand and actual mace, preview enchantment states, particle frames and compact navigation. Includes alpha10, alpha9, alpha7 and preview-flow regressions.");
    }
    private static int verifyNativeStates(MinecraftClient client,String source){
        var service=TextureMixerClient.SERVICE;int count=0;
        for(String id:itemIds(service.sources.get(source))){
            var target=service.catalog.item(id);
            try{
                var definition=service.sources.get(source).json(target.model().file("items",".json"));
                for(ItemPreviewState variant:ItemPreviewState.fromDefinition(definition)){
                    ItemStack stack=variant.apply(target.stack(),client.world);stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,false);
                    for(ItemDisplayContext display:List.of(ItemDisplayContext.GUI,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)){
                        var state=new KeyedItemRenderState();client.getItemModelManager().clearAndUpdate(state,stack,display,client.world,client.player,0);
                        check(!state.isEmpty(),"native item is visible "+id+" "+variant.label());
                        if(!variant.enchantments().isEmpty()){
                            check(state.isAnimated(),"enchanted native item retains animation "+id+" "+variant.label());
                            String texture=state.getParticleSprite(Random.create(0)).getContents().getId().getPath();
                            check(texture.contains(id.endsWith("mace")?"mace_":"lunge"),"native predicate selects enchanted model "+texture);
                        }
                        count++;
                    }
                }
            }catch(IOException error){throw new CompletionException(error);}
        }
        return count;
    }
    private static List<String> itemIds(PackSource source){
        return source.ids().stream().filter(id->id.path().startsWith("items/")&&id.path().endsWith(".json"))
                .map(id->new AssetId(id.namespace(),id.path().substring(6,id.path().length()-5)).toString()).sorted().toList();
    }
    private static boolean ready(MixerService service,Catalog.Target target,String source){
        var entry=service.previews.get(target,source,0);if(entry.error!=null)throw new AssertionError(entry.error);return entry.ready();
    }
    private static void createEffects(Path path)throws Exception{
        BufferedImage fire=new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<16;x++)fire.setRGB(x,y,y<16?0xff00ff55:0xff00bbee);
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(fire,"PNG",png);
        Map<String,byte[]> files=Map.of("pack.mcmeta","{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"alpha11 effects\"}}".getBytes(),
                "assets/minecraft/textures/block/fire_1.png",png.toByteArray(),"assets/minecraft/textures/block/fire_1.png.mcmeta","{\"animation\":{\"frametime\":2}}".getBytes(),
                "assets/minecraft/textures/misc/enchanted_glint_item.png",png.toByteArray(),"assets/minecraft/textures/misc/enchanted_glint_armor.png",png.toByteArray());
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))){for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue());zip.closeEntry();}}
    }
    private static final class AnimationScreen extends Screen {
        private final PreviewCache.Arena spear;
        AnimationScreen(PreviewCache.Arena spear){super(Text.literal("TextureMixer animation validation"));this.spear=spear;}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext context,int mx,int my,float delta){
            context.fill(0,0,width,height,0xff181818);
            var service=TextureMixerClient.SERVICE;var target=service.catalog.item("minecraft:diamond_spear");
            ItemPreviewState lunge=new ItemPreviewState(List.of("minecraft:lunge"));
            spear.draw(context,target,PreviewCache.Mode.ICON,40,90,112,new ScreenRect(40,90,112,112),lunge);
            spear.draw(context,target,PreviewCache.Mode.HAND,190,90,112,new ScreenRect(190,90,112,112),lunge);
            ItemStack stack=new ItemPreviewState(List.of("minecraft:density","minecraft:wind_burst")).apply(service.catalog.item("minecraft:mace").stack(),client.world);
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,false);
            context.getMatrices().pushMatrix();context.getMatrices().translate(340,90);context.getMatrices().scale(7,7);context.drawItem(stack,0,0);context.getMatrices().popMatrix();
            context.drawText(textRenderer,"Spear Icon",40,70,0xffffffff,false);context.drawText(textRenderer,"Spear Hand",190,70,0xffffffff,false);context.drawText(textRenderer,"Native Mace",340,70,0xffffffff,false);
        }
    }
    private static long pixelDifferences(BufferedImage a,BufferedImage b,int x,int y,int width,int height){long count=0;for(int py=y;py<y+height;py++)for(int px=x;px<x+width;px++)if(a.getRGB(px,py)!=b.getRGB(px,py))count++;return count;}
    private static Object field(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception error){throw new CompletionException(error);}}
    private static ButtonWidget button(MinecraftClient client,String label){return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(value->value.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));}
    private static void click(MinecraftClient client,String label){var button=button(client,label);check(button.active,"active "+label);Screen screen=client.currentScreen;Click input=new Click(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseInput(0,0));check(screen.mouseClicked(input,false),"handled "+label);screen.mouseReleased(input);}
    private static void tab(MinecraftClient client,String label){for(int step=0;step<9;step++){try{click(client,label);return;}catch(AssertionError missing){var next=button(client,SmokeLabels.text("后"));if(!next.active)break;click(client,SmokeLabels.text("后"));}}for(int step=0;step<9;step++){try{click(client,label);return;}catch(AssertionError missing){click(client,SmokeLabels.text("前"));}}throw new AssertionError("Missing category "+label);}
    private static void search(MinecraftClient client,String value){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(value);}
    private static Path capture(MinecraftClient client,String name)throws Exception{Thread.sleep(250);CompletableFuture<Void> saved=new CompletableFuture<>();onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,ignored->saved.complete(null));return true;});saved.get(15,TimeUnit.SECONDS);return client.runDirectory.toPath().resolve("screenshots").resolve(name);}
    private static void await(MinecraftClient client,Supplier<Boolean> predicate)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);while(!onClient(client,predicate)){if(System.nanoTime()>deadline)throw new AssertionError("alpha11 timeout");Thread.sleep(50);}}
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){CompletableFuture<T> future=new CompletableFuture<>();client.execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});return future.join();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
