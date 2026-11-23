package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.sky.SkyPreviewCache;
import com.fullyx.texturemixer.ui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.*;

final class Alpha15Smoke {
    private static final AssetId MENU=AssetId.of("textures/gui/menu_background.png");
    private static final AssetId CONTAINER=AssetId.of("textures/gui/container/inventory.png");
    private static final AssetId FONT=AssetId.of("font/default.json");
    private static final AssetId ARMOR_GLINT=AssetId.of("textures/misc/enchanted_glint_armor.png");
    private static final AssetId ITEM_GLINT=AssetId.of("textures/misc/enchanted_glint_item.png");
    private static final String PACK="§balpha16-interface.zip";
    private static final String TEMPLATE="Alpha15 preview and confirm";

    static void run(MinecraftClient client) throws Exception {
        MixerService service=TextureMixerClient.SERVICE;
        Path tests=client.runDirectory.toPath().toAbsolutePath(), fixture=tests.resolve("fixtures").resolve(PACK);
        if(Files.exists(fixture))throw new IOException("Test path already exists: "+fixture);
        Path report=tests.resolve("alpha16-result.txt");
        MixerConfig original=onClient(client,()->service.committed.copy());
        boolean preload=service.preloadEnabled();
        int scale=client.options.getGuiScale().getValue();
        String[] templateId={null};
        try {
            var assets=createFixture(service,fixture);
            var draft=original.copy();
            String source=onClient(client,()->service.add(fixture,draft,false)).get(30,TimeUnit.SECONDS);
            String sky=source;
            draft.setMainStack(List.of(source));
            draft.skyOverrides.put("overworld",sky);
            draft.menuOverrides.put(MENU.toString(),source);
            draft.containerOverrides.put(CONTAINER.toString(),source);
            draft.fontOverrides.put(FONT.toString(),source);
            var mixer=onClient(client,()->{
                try{service.setPreloadEnabled(true);}catch(IOException error){throw new UncheckedIOException(error);}
                client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                var screen=new MixerScreen(service);screen.draft=draft;client.setScreen(screen);return screen;
            });
            long warmStart=System.nanoTime();
            await(client,()->{
                var ready=service.skyPreviews.getIfPresent("overworld",sky,SkyPreviewCache.DEFAULT_CONTEXT);
                if(ready!=null&&ready.error!=null)throw new AssertionError(ready.error);
                return ready!=null&&ready.texture!=null;
            });
            long warmMs=(System.nanoTime()-warmStart)/1_000_000;
            var cached=onClient(client,()->service.skyPreviews.getIfPresent("overworld",sky,SkyPreviewCache.DEFAULT_CONTEXT));
            check(cached.scene.fromSource(),"custom sky was prepared before opening the category");
            onClient(client,()->{click(client,"Sky");return true;});
            capture(client,"alpha15-sky-preloaded.png");
            onClient(client,()->{click(client,"Settings");click(client,"Back");return true;});
            check(onClient(client,()->cached==service.skyPreviews.getIfPresent("overworld",sky,SkyPreviewCache.DEFAULT_CONTEXT)),
                    "settings round trip keeps the uploaded sky preview");

            onClient(client,()->{click(client,"HUD");click(client,"Menu");search(client,MENU.toString());return true;});
            check(button(client,"Menu Background")!=null,"main menu textures appear under Menu");
            onClient(client,()->{click(client,"Container GUI");search(client,CONTAINER.toString());return true;});
            check(button(client,"Container / Inventory")!=null,"inventory texture appears under Container GUI");
            var container=service.catalog.list(MixerConfig.Kind.CONTAINER).stream()
                    .filter(target->target.id().equals(CONTAINER.toString())).findFirst().orElseThrow();
            await(client,()->{
                var entry=service.resourcePreviews.getIfPresent(container,source);
                return entry!=null&&entry.texture!=null&&!service.resourcePreviews.needsReference(entry);
            });
            checkCardPixels(client,"Container / Inventory","alpha16-container-overview.png");
            onClient(client,()->{click(client,"Container / Inventory");search(client,PACK);return true;});
            checkCardPixels(client,PACK,"alpha16-container-source.png");
            onClient(client,()->{click(client,"Font");search(client,"Default Font");click(client,"Default Font");search(client,PACK);click(client,PACK);return true;});
            await(client,()->button(client,"Select").active);
            onClient(client,()->{
                var field=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance)
                        .map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                field.setText("");
                field.write("§bAW§r AW");
                check(field.getText().equals("§bAW§r AW"),"formatting survives input filter");
                return true;
            });
            Path fontScreenshot=capture(client,"alpha16-font-sample.png");
            var fontImage=ImageIO.read(fontScreenshot.toFile());
            check(aquaPixels(fontImage,fontImage.getWidth()/2-95,fontImage.getHeight()/2-95,
                    fontImage.getWidth()/2+95,fontImage.getHeight()/2+40)>40,"custom font renders §b aqua");
            onClient(client,()->{click(client,"Select");return true;});

            for(String glint:List.of("Armor Glint","Item Glint")){
                onClient(client,()->{
                    click(client,"Enchant");search(client,glint);click(client,glint);search(client,PACK);click(client,PACK);
                    return true;
                });
                var entry=service.catalog.list(MixerConfig.Kind.ENCHANT).stream().filter(value->value.name().equals(glint)).findFirst().orElseThrow();
                await(client,()->{
                    var preview=service.resourcePreviews.getIfPresent(entry,source);
                    return preview!=null&&preview.texture!=null;
                });
                String screenshot="alpha15-"+glint.toLowerCase(Locale.ROOT).replace(' ','-')+".png";
                capture(client,screenshot);
                checkGlintPixels(tests.resolve("screenshots").resolve(screenshot),glint.equals("Armor Glint"));
                onClient(client,()->{click(client,"Cancel");return true;});
            }

            checkShieldCache(client,service,source);
            onClient(client,()->{
                click(client,"Sound & Music");
                search(client,"Block / Anvil / Land");
                click(client,"Block / Anvil / Land");
                search(client,PACK);
                click(client,PACK);
                var slider=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.SliderWidget.class::isInstance)
                        .map(net.minecraft.client.gui.widget.SliderWidget.class::cast).findFirst().orElseThrow();
                check(slider.getMessage().getString().equals("Preview volume 50%"),"preview volume starts centered");
                var click=new Click(slider.getRight()-4,slider.getY()+4,new MouseInput(0,0));
                client.currentScreen.mouseClicked(click,false);client.currentScreen.mouseReleased(click);
                check(slider.getMessage().getString().equals("Preview volume 100%"),"preview slider applies current value");
                click(client,"Cancel");
                return true;
            });
            templateId[0]=onClient(client,()->{
                try{return service.library.saveTemplate(TEMPLATE,mixer.draft).id();}
                catch(IOException error){throw new UncheckedIOException(error);}
            });
            MixerConfig base=onClient(client,()->service.committed.copy());
            onClient(client,()->{mixer.draft=base.copy();click(client,"Custom Sets");click(client,TEMPLATE);return true;});
            check(client.currentScreen instanceof ChoiceScreen&&mixer.draft.changesFrom(base)==0,"selecting a set always waits for confirmation");
            capture(client,"alpha15-template-confirm.png");
            onClient(client,()->{click(client,"Cancel");return true;});
            check(mixer.draft.changesFrom(base)==0,"cancel leaves the draft unchanged");
            onClient(client,()->{click(client,TEMPLATE);click(client,"Confirm");return true;});
            await(client,()->client.currentScreen==mixer&&source.equals(mixer.draft.fontOverrides.get(FONT.toString())));
            check(!source.equals(service.committed.fontOverrides.get(FONT.toString())),"confirm loads the draft before applying");
            onClient(client,()->{client.setScreen(null);return service.apply(mixer.draft);}).get(120,TimeUnit.SECONDS);
            onClient(client,()->{
                try{
                    for(var id:List.of(MENU,CONTAINER)){
                        try(InputStream stream=client.getResourceManager().open(Identifier.of(id.toString()))){
                            check(Arrays.equals(assets.get(id),stream.readAllBytes()),"active Minecraft resources use selected "+id);
                        }
                    }
                    try(var sword=client.getResourceManager().open(Identifier.of("minecraft:textures/item/diamond_sword.png"))){
                        check(Arrays.equals(assets.get(AssetId.of("textures/items/diamond_sword.png")),sword.readAllBytes()),"old-format main pack applies legacy sword path");
                    }
                    Identifier defaultFont=Identifier.of(FONT.toString());
                    check(client.getResourceManager().getAllResources(defaultFont).size()==1,"font selection masks lower provider stacks");
                    check(client.textRenderer.getWidth("W")>=12,"the game's text renderer uses the selected font");
                    try(var stream=client.getResourceManager().open(defaultFont)){
                        check(new String(stream.readAllBytes(),StandardCharsets.UTF_8).contains("texturemixer_font_"),"font dependencies are isolated");
                    }
                }catch(IOException error){throw new UncheckedIOException(error);}
                return true;
            });
            Files.writeString(report,"PASS: old-format pack applies, sparse container art in both grids, styled custom font input, centered audio slider, Shield previews under cache pressure, item inventory and hand glint, armor glint, preloaded Sky, Menu and Container overrides, and set confirmation. Sky warm elapsed "+warmMs+" ms.");
        } catch(Throwable error){
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
            Files.writeString(report,"FAIL: "+trace);throw error;
        } finally {
            onClient(client,()->{
                client.setScreen(null);
                client.options.getGuiScale().setValue(scale);client.onResolutionChanged();
                try{
                    service.setPreloadEnabled(preload);
                    if(templateId[0]!=null)service.library.deleteTemplate(templateId[0]);
                }catch(IOException error){throw new UncheckedIOException(error);}
                return service.apply(original);
            }).get(120,TimeUnit.SECONDS);
            Files.deleteIfExists(fixture);
        }
    }

    private static Map<AssetId,byte[]> createFixture(MixerService service,Path path)throws Exception {
        Map<AssetId,byte[]> assets=new LinkedHashMap<>();
        assets.put(MENU,recolor(service.vanilla.read(MENU),0xff377dad));
        BufferedImage sparse=new BufferedImage(2048,2048,BufferedImage.TYPE_INT_ARGB);
        for(int i=63;i<1791;i++){
            sparse.setRGB(i,603,0xffff19ef);
            sparse.setRGB(71,i,0xffff19ef);
        }
        assets.put(CONTAINER,png(sparse));
        assets.put(AssetId.of("textures/items/diamond_sword.png"),
                recolor(service.vanilla.read(AssetId.of("textures/item/diamond_sword.png")),0xff495d80));
        AssetId shield=AssetId.of("textures/entity/shield_base_nopattern.png");
        assets.put(shield,recolor(service.vanilla.read(shield),0xff245cc7));
        assets.put(ARMOR_GLINT,recolor(service.vanilla.read(ARMOR_GLINT),0xffff205b));
        assets.put(ITEM_GLINT,recolor(service.vanilla.read(ITEM_GLINT),0xff50ff14));
        assets.put(AssetId.of("optifine/sky/world0/sky1.properties"),
                "source=./sky1.png\nblend=replace\nrotate=false\n".getBytes(StandardCharsets.UTF_8));
        BufferedImage sky=new BufferedImage(768,512,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<sky.getHeight();y++)for(int x=0;x<sky.getWidth();x++)
            sky.setRGB(x,y,0xff262058|((x+y)%151+40)<<8);
        assets.put(AssetId.of("optifine/sky/world0/sky1.png"),png(sky));
        assets.put(FONT,"{\"providers\":[{\"type\":\"bitmap\",\"file\":\"minecraft:font/alpha15_fixture.png\",\"ascent\":7,\"height\":8,\"chars\":[\"AW\"]}]}"
                .getBytes(StandardCharsets.UTF_8));
        BufferedImage glyphs=new BufferedImage(32,8,BufferedImage.TYPE_INT_ARGB);
        for(int offset:List.of(0,16))for(int y=1;y<7;y++)for(int x=1;x<15;x++)
            if(y==3||x==2||x==14)glyphs.setRGB(x+offset,y,0xffffffff);
        assets.put(AssetId.of("textures/font/alpha15_fixture.png"),png(glyphs));
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW))){
            put(zip,"pack.mcmeta","""
                    {"pack":{"pack_format":1,"description":"TextureMixer compatibility fixture"}}
                    """.getBytes(StandardCharsets.UTF_8));
            for(var entry:assets.entrySet())put(zip,"assets/"+entry.getKey().namespace()+"/"+entry.getKey().path(),entry.getValue());
        }
        return assets;
    }

    private static byte[] recolor(byte[] original,int color)throws IOException {
        var image=ImageIO.read(new ByteArrayInputStream(original));
        BufferedImage output=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            output.setRGB(x,y,(image.getRGB(x,y)&0xff000000)|(color&0xffffff));
        return png(output);
    }
    private static byte[] png(BufferedImage image)throws IOException{var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);return bytes.toByteArray();}
    private static void checkShieldCache(MinecraftClient client,MixerService service,String source)throws Exception{
        var target=service.catalog.item("minecraft:shield");
        onClient(client,()->{click(client,"Item");search(client,"shield");click(client,"Shield");return true;});
        var mode=com.fullyx.texturemixer.client.PreviewCache.Mode.ICON;
        await(client,()->{
            var custom=service.previews.getIfPresent(target,source,0,mode);
            var vanilla=service.previews.getIfPresent(target,MixerConfig.VANILLA,0,mode);
            return custom!=null&&vanilla!=null&&custom.ready()&&vanilla.ready();
        });
        var field=com.fullyx.texturemixer.client.PreviewCache.Arena.class.getDeclaredField("textureBytes");
        field.setAccessible(true);
        onClient(client,()->{
            var custom=service.previews.getIfPresent(target,source,0,mode);
            var vanilla=service.previews.getIfPresent(target,MixerConfig.VANILLA,0,mode);
            check(custom.arena!=vanilla.arena,"distinct custom Shield uses its own native model and texture");
            try{
                long customBytes=field.getLong(custom.arena),vanillaBytes=field.getLong(vanilla.arena);
                var customArena=custom.arena;
                var vanillaArena=vanilla.arena;
                try{
                    field.setLong(customArena,40L*1024*1024);
                    field.setLong(vanillaArena,40L*1024*1024);
                    service.previews.tick(true);
                    check(custom.ready()&&vanilla.ready(),"visible Shield cards retain their textures above the offscreen cache budget");
                }finally{
                    field.setLong(customArena,customBytes);field.setLong(vanillaArena,vanillaBytes);
                }
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            return true;
        });
        capture(client,"alpha16-shield-sources.png");
    }
    private static void checkCardPixels(MinecraftClient client,String card,String screenshot)throws Exception {
        int[] location=onClient(client,()->{
            var button=button(client,card);
            return new int[]{button.getX()*2,button.getY()*2,button.getWidth()*2};
        });
        var image=ImageIO.read(capture(client,screenshot).toFile());
        int count=0;
        for(int y=location[1];y<location[1]+location[2];y++)for(int x=location[0];x<location[0]+location[2];x++){
            int rgb=image.getRGB(x,y);
            int red=rgb>>16&255,green=rgb>>8&255,blue=rgb&255;
            if(red>green+45&&blue>green+45)count++;
        }
        check(count>10,"sparse custom material is visible in "+screenshot+" ("+count+")");
        if(card.equals(PACK))check(aquaPixels(image,location[0],location[1]+location[2],
                location[0]+location[2],location[1]+location[2]+44)>10,"source name renders §b aqua in the grid");
    }
    private static int aquaPixels(BufferedImage image,int left,int top,int right,int bottom){
        int count=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
            int rgb=image.getRGB(x,y);
            int red=rgb>>16&255,green=rgb>>8&255,blue=rgb&255;
            // Allow the color rounding from the GUI text shader and glyph coverage.
            if(Math.abs(red-85)<=5&&green>=250&&blue>=250)count++;
        }
        return count;
    }
    private static void checkGlintPixels(Path path,boolean armor)throws IOException {
        BufferedImage image=ImageIO.read(path.toFile());
        int cx=image.getWidth()/2,cy=image.getHeight()/2;
        int left=colorCount(image,cx-196,cy-105,cx-74,cy+45,armor);
        check(left>(armor?500:90),"selected glint changes left preview pixels ("+left+")");
        if(!armor){
            int right=colorCount(image,cx+74,cy-105,cx+196,cy+45,false);
            check(right>300,"held sword glint covers the visible model ("+right+")");
            int width=0;
            for(int x=cx+74;x<cx+196;x++)
                if(colorCount(image,x,cy-105,x+1,cy+45,false)>0)width++;
            check(width>14,"held sword model is turned toward the camera ("+width+" pixels wide)");
        }
    }
    private static int colorCount(BufferedImage image,int left,int top,int right,int bottom,boolean armor){
        int count=0;
        for(int y=Math.max(0,top);y<Math.min(bottom,image.getHeight());y++)
            for(int x=Math.max(0,left);x<Math.min(right,image.getWidth());x++){
                int pixel=image.getRGB(x,y),r=pixel>>16&255,g=pixel>>8&255,b=pixel&255;
                if(armor?r>=g+40&&r>=b+30&&r>180:g>=r+40&&g>=b+32&&g>100)count++;
            }
        return count;
    }
    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private static void search(MinecraftClient client,String text){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(text);}
    private static ButtonWidget button(MinecraftClient client,String text){
        return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(widget->widget.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new AssertionError("Missing button "+text));
    }
    private static void click(MinecraftClient client,String text){
        var screen=client.currentScreen;
        var widget=button(client,text);
        check(widget.active,"active "+text);
        var click=new Click(widget.getX()+widget.getWidth()/2.0,widget.getY()+widget.getHeight()/2.0,new MouseInput(0,0));
        check(screen.mouseClicked(click,false),"handled "+text);screen.mouseReleased(click);
    }
    private static Path capture(MinecraftClient client,String name)throws Exception{
        await(client,()->client.getOverlay()==null);
        Thread.sleep(350);
        var result=new CompletableFuture<Void>();
        onClient(client,()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,ignored->result.complete(null));return true;});
        result.get(15,TimeUnit.SECONDS);
        return client.runDirectory.toPath().resolve("screenshots").resolve(name);
    }
    private static void await(MinecraftClient client,Supplier<Boolean> condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(!onClient(client,condition)){
            check(System.nanoTime()<deadline,"timed out waiting for the client");
            Thread.sleep(50);
        }
    }
    private static <T>T onClient(MinecraftClient client,Supplier<T> action){
        var result=new CompletableFuture<T>();
        client.execute(()->{try{result.complete(action.get());}catch(Throwable error){result.completeExceptionally(error);}});
        return result.join();
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
