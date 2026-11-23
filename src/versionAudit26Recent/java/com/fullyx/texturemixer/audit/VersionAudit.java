package com.fullyx.texturemixer.audit;

import com.fullyx.texturemixer.TextureMixerClient;
import com.fullyx.texturemixer.client.Generation;
import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.client.NativeCompat;
import com.fullyx.texturemixer.client.Catalog;
import com.fullyx.texturemixer.client.PreviewCache;
import com.fullyx.texturemixer.core.AssetId;
import com.fullyx.texturemixer.core.MixerConfig;
import com.fullyx.texturemixer.ui.MixerScreen;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.function.IntPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.MixinEnvironment;

public final class VersionAudit implements ClientModInitializer {
    private static final AssetId SWORD=AssetId.of("textures/item/diamond_sword.png");

    @Override public void onInitializeClient(){
        Thread audit=new Thread(VersionAudit::run,"texturemixer-version-audit");
        audit.setDaemon(true);
        audit.start();
    }

    private static void run(){
        Minecraft client=Minecraft.getInstance();
        Path root=client.gameDirectory.toPath();
        Path report=root.resolve("version-audit.txt");
        Path file=root.resolve("texturemixer-release-audit-fixture.zip");
        try{
            await(client,()->client.gui.overlay()==null&&TextureMixerClient.SERVICE!=null
                    &&TextureMixerClient.SERVICE.catalog!=null&&!TextureMixerClient.SERVICE.busy);
            byte[] pixels=createPack(file);
            MixerService service=TextureMixerClient.SERVICE;
            onClient(client,()->{MixinEnvironment.getCurrentEnvironment().audit();return true;});
            MixerConfig draft=onClient(client,()->{
                TextureMixerClient.open(client);
                require(client.gui.screen() instanceof MixerScreen,"TextureMixer editor opens on the title screen");
                return ((MixerScreen)client.gui.screen()).draft.copy();
            });
            String source=onClient(client,()->service.add(file,draft,false)).get(25,TimeUnit.SECONDS);
            onClient(client,()->{
                com.fullyx.texturemixer.core.UiLanguage.select("en_us");
                var editor=new MixerScreen(service);
                editor.draft=draft;
                client.gui.setScreen(editor);
                press(client,"Item");
                search(client,"diamond sword");
                press(client,"Diamond Sword");
                search(client,file.getFileName().toString());
                return true;
            });
            var target=service.catalog.item("minecraft:diamond_sword");
            require(target.matches("钻石剑"),"bilingual item search reads the installed Chinese game locale");
            String packName=file.getFileName().toString();
            awaitPreview(client,service,target,source,PreviewCache.Mode.ICON);
            requireCustomPreviewPixels(client,packName,"custom-sword",VersionAudit::magenta,"diamond sword Icon");
            boolean world=Boolean.getBoolean("texturemixer.versionWorldAudit");
            boolean features=Boolean.getBoolean("texturemixer.versionFeatureAudit");
            if(features){
                onClient(client,()->{press(client,"Hand");return true;});
                awaitPreview(client,service,target,source,PreviewCache.Mode.HAND);
                requireCustomPreviewPixels(client,packName,"custom-hand",VersionAudit::magenta,"diamond sword Hand");
                onClient(client,()->{
                    var editor=new MixerScreen(service);
                    editor.draft=draft;
                    client.gui.setScreen(editor);
                    press(client,"Item");
                    press(client,"Block");
                    search(client,"diamond block");
                    press(client,"Block of Diamond");
                    search(client,packName);
                    return true;
                });
                var block=service.catalog.block("minecraft:diamond_block");
                awaitPreview(client,service,block,source,PreviewCache.Mode.ICON);
                requireCustomPreviewPixels(client,packName,"custom-block",VersionAudit::azure,"diamond block");
                verifyFluidPreview(client, service, draft, source, packName, "Water");
                verifyFluidPreview(client, service, draft, source, packName, "Lava");
            }
            draft.itemOverrides.put(target.id(),source);
            if(world)draft.skyOverrides.put("overworld",source);
            if(features){
                draft.itemOverrides.put("minecraft:diamond_chestplate",source);
                draft.blockOverrides.put("minecraft:diamond_block",source);
            }
            onClient(client,()->service.apply(draft)).get(100,TimeUnit.SECONDS);
            var committed=onClient(client,()->service.committed.copy());
            require(committed.snapshotId!=null&&committed.snapshotId.equals(service.store.load().snapshotId),"render generation is committed with configuration");
            Generation restored=Generation.read(service.cache,committed.snapshotId.toString());
            String prefix=restored.itemPrefixes().get(target.id());
            var replacement=new AssetId(prefix+SWORD.namespace(),SWORD.path());
            require(Arrays.equals(pixels,restored.assets().read(replacement)),"snapshot retains selected texture");
            onClient(client,()->{
                try(var data=client.getResourceManager().open(Identifier.fromNamespaceAndPath(replacement.namespace(),replacement.path()))){
                    require(Arrays.equals(pixels,data.readAllBytes()),"Minecraft mounted selected isolated texture");
                }catch(Exception error){throw new CompletionException(error);}
                return true;
            });
            if (features) verifyFluidResources(service, restored);
            if(world)verifyWorldSky(client);
            if (features && world) verifyWorldFluids(client);
            if(features){
                var chestplate=service.catalog.item("minecraft:diamond_chestplate");
                onClient(client,()->{
                    TextureMixerClient.open(client);
                    press(client,"Item");
                    search(client,"diamond chestplate");
                    press(client,"Diamond Chestplate");
                    search(client,packName);
                    press(client,"Worn");
                    return true;
                });
                awaitPreview(client,service,chestplate,source,PreviewCache.Mode.WORN);
                requireCustomPreviewPixels(client,packName,"custom-worn",VersionAudit::orange,"diamond chestplate Worn");
            }
            onClient(client,service::disable).get(100,TimeUnit.SECONDS);
            require(service.store.load().snapshotId==null,"disabling clears committed generation");
            Files.writeString(report,"PASS: "+NativeCompat.gameVersionName()+" audited mixins, opened editor, verified custom sword preview pixels, "
                    +"committed and restored snapshot, applied native resource reload and disabled."
                    +(world?" Verified custom Overworld sky pixels in a synthetic world.":"")
                    +(features?" Verified Hand, Block and Worn preview pixels. Verified Water and Lava preview pixels in Block, source list and confirmation.":"")
                    +(features&&world?" Verified Water and Lava world pixels.":"")+"\n");
        }catch(Throwable failure){
            failure.printStackTrace();
            try{
                var trace=new StringWriter();
                failure.printStackTrace(new PrintWriter(trace));
                Files.writeString(report,"FAIL: "+trace);
            }catch(Exception error){failure.addSuppressed(error);}
        }finally{
            client.execute(client::stop);
        }
    }

    private static byte[] createPack(Path file)throws Exception{
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xffed24b8);
        var output=new ByteArrayOutputStream();
        ImageIO.write(image,"PNG",output);
        var metadata=new com.google.gson.JsonObject();
        var pack=new com.google.gson.JsonObject();
        metadata.add("pack",pack);
        pack.addProperty("description","TextureMixer version audit");
        var version=SharedConstants.getCurrentVersion();
        Class<?> versionType=SharedConstants.class.getMethod("getCurrentVersion").getReturnType();
        java.lang.reflect.Method packVersion;
        try{packVersion=versionType.getMethod("packVersion",PackType.class);}
        catch(NoSuchMethodException older){packVersion=versionType.getMethod("getResourceVersion",PackType.class);}
        Object target=packVersion.invoke(version,PackType.CLIENT_RESOURCES);
        if(target instanceof Integer old)pack.addProperty("pack_format",old);
        else{
            var format=new com.google.gson.JsonArray();
            format.add((Number)target.getClass().getMethod("major").invoke(target));
            format.add((Number)target.getClass().getMethod("minor").invoke(target));
            pack.add("min_format",format);
            pack.add("max_format",format);
        }
        String meta=metadata.toString();
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))){
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(meta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SWORD.assetPath()));
            zip.write(output.toByteArray());
            zip.closeEntry();
            if(Boolean.getBoolean("texturemixer.versionWorldAudit")){
                zip.putNextEntry(new ZipEntry("assets/minecraft/optifine/sky/world0/sky1.properties"));
                zip.write("source=./sky1.png\nblend=replace\nrotate=false\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                var sky=new BufferedImage(48,32,BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<32;y++)for(int x=0;x<48;x++)sky.setRGB(x,y,0xff26d35e);
                zip.putNextEntry(new ZipEntry("assets/minecraft/optifine/sky/world0/sky1.png"));
                ImageIO.write(sky,"PNG",zip);
                zip.closeEntry();
            }
            if(Boolean.getBoolean("texturemixer.versionFeatureAudit")){
                solidTexture(zip,"assets/minecraft/textures/block/diamond_block.png",16,16,0xff1956f3);
                solidTexture(zip,"assets/minecraft/textures/block/water_still.png",16,16,0xffef08fa);
                solidTexture(zip,"assets/minecraft/textures/block/water_flow.png",32,32,0xff06efff);
                solidTexture(zip,"assets/minecraft/textures/block/lava_still.png",16,16,0xffc9fd14);
                solidTexture(zip,"assets/minecraft/textures/block/lava_flow.png",32,64,0xffea25be);
                zip.putNextEntry(new ZipEntry("assets/minecraft/textures/block/lava_flow.png.mcmeta"));
                zip.write("{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                solidTexture(zip,"assets/minecraft/textures/entity/equipment/humanoid/diamond.png",64,32,0xffff862c);
                solidTexture(zip,"assets/minecraft/textures/models/armor/diamond_layer_1.png",64,32,0xffff862c);
            }
        }
        return output.toByteArray();
    }

    private static void solidTexture(ZipOutputStream zip,String path,int width,int height,int color)throws Exception{
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,color);
        zip.putNextEntry(new ZipEntry(path));
        ImageIO.write(image,"PNG",zip);
        zip.closeEntry();
    }

    private static void press(Minecraft client,String name){
        var screen=client.gui.screen();
        var button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate->candidate.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing "+name));
        double x=button.getX()+button.getWidth()/2.0,y=button.getY()+button.getHeight()/2.0;
        var click = new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
        require(screen.mouseClicked(click, false), "click " + name);
        screen.mouseReleased(click);
    }

    private static void awaitPreview(Minecraft client,MixerService service,Catalog.Target target,String source,PreviewCache.Mode mode)throws Exception{
        await(client,()->{
            var entry=service.previews.getIfPresent(target,source,0,mode);
            if(entry!=null&&entry.error!=null)throw new AssertionError(entry.error);
            return entry!=null&&entry.ready();
        });
    }
    private static boolean magenta(int rgb){
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        return r>115&&g<68&&b>87&&r-b>17;
    }
    private static boolean azure(int rgb){
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        return r<45&&g>29&&g<110&&b>93&&b>g*2;
    }
    private static boolean orange(int rgb){
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        return r>120&&g>55&&g<168&&b<72&&r>g*1.55;
    }
    private static void requireCustomPreviewPixels(Minecraft client,String label,String shot,IntPredicate color,String description)throws Exception{
        Path file=client.gameDirectory.toPath().resolve("screenshots/port-audit-"+shot+".png");
        int[] bounds=onClient(client,()->{
            var button=client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(candidate->candidate.getMessage().getString().equals(label)).findFirst().orElseThrow();
            double scale=client.getWindow().getGuiScale();
            return new int[]{(int)(button.getX()*scale),(int)(button.getY()*scale),(int)(button.getWidth()*scale)};
        });
        Thread.sleep(500);
        BufferedImage screenshot=capture(client,file);
        int matching=0;
        for(int y=bounds[1];y<bounds[1]+bounds[2];y++)for(int x=bounds[0];x<bounds[0]+bounds[2];x++)
            if(color.test(screenshot.getRGB(x,y)))matching++;
        require(matching>12,description+" renders the selected texture ("+matching+" matching pixels)");
    }


    private static void verifyFluidPreview(Minecraft client, MixerService service, MixerConfig draft,
                                           String source, String packName, String name) throws Exception {
        String id = name.toLowerCase(java.util.Locale.ROOT);
        var target = service.catalog.block("minecraft:" + id);
        boolean water = id.equals("water");
        IntPredicate vanilla = water ? VersionAudit::vanillaWater : VersionAudit::orange;
        IntPredicate still = water ? VersionAudit::waterStill : VersionAudit::lavaStill;
        IntPredicate flow = water ? VersionAudit::waterFlow : VersionAudit::magenta;

        onClient(client, () -> {
            var editor = new MixerScreen(service);
            editor.draft = draft;
            client.gui.setScreen(editor);
            press(client, "Item");
            press(client, "Block");
            search(client, name);
            return true;
        });
        awaitPreview(client, service, target, MixerConfig.VANILLA, PreviewCache.Mode.ICON);
        verifyFluidPixels(client, cardBounds(client, name), id + "-vanilla-grid", vanilla);

        onClient(client, () -> { press(client, name); search(client, packName); return true; });
        awaitPreview(client, service, target, source, PreviewCache.Mode.ICON);
        verifyFluidPixels(client, cardBounds(client, packName), id + "-custom-source", still, flow);

        onClient(client, () -> { press(client, packName); return true; });
        var bounds = onClient(client, () -> {
            var dialog = client.gui.screen();
            require(dialog instanceof com.fullyx.texturemixer.ui.SelectionDialog, "opens confirmation for " + name);
            int size = dialogField(dialog, "previewSize");
            int top = dialogField(dialog, "previewY");
            int left = dialogField(dialog, "x") + (dialogField(dialog, "panelWidth") - size) / 2;
            return pixels(client.getWindow().getGuiScale(), left, top, size, size);
        });
        verifyFluidPixels(client, bounds, id + "-custom-confirmation", still, flow);
        onClient(client, () -> {
            press(client, com.fullyx.texturemixer.core.UiLanguage.tr("选择"));
            require(source.equals(draft.blockOverrides.get(target.id())), "confirmed selection for " + target.id());
            return true;
        });
    }

    private static int[] cardBounds(Minecraft client, String name) throws Exception {
        return onClient(client, () -> {
            var button = client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(candidate -> candidate.getMessage().getString().equals(name)).findFirst().orElseThrow();
            return pixels(client.getWindow().getGuiScale(), button.getX(), button.getY(), button.getWidth(), button.getWidth());
        });
    }

    private static int dialogField(Object dialog, String name) {
        try {
            var field = dialog.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(dialog);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static int[] pixels(double scale, int x, int y, int width, int height) {
        return new int[]{(int)(x*scale), (int)(y*scale), (int)(width*scale), (int)(height*scale)};
    }

    private static void verifyFluidPixels(Minecraft client, int[] bounds, String shot, IntPredicate... colors) throws Exception {
        Path file = client.gameDirectory.toPath().resolve("screenshots/port-audit-" + shot + ".png");
        int[] counts = new int[colors.length];
        for (int attempt = 0; attempt < 8; attempt++) {
            Thread.sleep(300);
            var image = capture(client, file);
            Arrays.fill(counts, 0);
            for (int y = bounds[1]; y < Math.min(image.getHeight(), bounds[1] + bounds[3]); y++) {
                for (int x = bounds[0]; x < Math.min(image.getWidth(), bounds[0] + bounds[2]); x++) {
                    int pixel = image.getRGB(x, y);
                    for (int index = 0; index < colors.length; index++) {
                        if (colors[index].test(pixel)) counts[index]++;
                    }
                }
            }
            if (Arrays.stream(counts).allMatch(count -> count > 12)) return;
        }
        throw new AssertionError(shot + " missing expected pixels " + Arrays.toString(counts) + " in " + file);
    }

    private static boolean vanillaWater(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return b > 65 && b > r + 18 && b > g + 15
                || r > 105 && r < 220 && Math.abs(r - g) < 4 && Math.abs(g - b) < 4;
    }

    private static boolean waterStill(int rgb) {
        return (rgb & 255) > 95 && ((rgb >> 8) & 255) < 25;
    }

    private static boolean waterFlow(int rgb) {
        return (rgb & 255) > 110 && ((rgb >> 8) & 255) > 55 && ((rgb >> 16) & 255) < 27;
    }

    private static boolean lavaStill(int rgb) {
        return (rgb & 255) < 36 && ((rgb >> 8) & 255) > 106 && ((rgb >> 16) & 255) > 70;
    }

    private static void verifyFluidResources(MixerService service, Generation generation) throws Exception {
        for (String fluid : java.util.List.of("water", "lava")) {
            String prefix = generation.blockPrefixes().get("minecraft:" + fluid);
            require(prefix != null, "generated fluid scope for " + fluid);
            require(!generation.itemPrefixes().containsKey("minecraft:" + fluid + "_bucket"), "bucket remains independently selectable");
            for (String type : java.util.List.of("still", "flow")) {
                var key = new AssetId(prefix + "minecraft", "textures/block/" + fluid + "_" + type + ".png");
                require(generation.assets().read(key) != null, "snapshot retains " + key);
            }
        }
        String lava = generation.blockPrefixes().get("minecraft:lava");
        require(generation.assets().read(new AssetId(lava + "minecraft", "textures/block/lava_flow.png.mcmeta")) != null,
                "snapshot retains flowing lava animation");
        String water = generation.blockPrefixes().get("minecraft:water");
        var overlay = AssetId.of("textures/block/water_overlay.png");
        require(Arrays.equals(service.vanilla.read(overlay),
                generation.assets().read(new AssetId(water + "minecraft", overlay.path()))), "missing pack overlay falls back to vanilla");
    }

    private static void verifyWorldFluids(Minecraft client) throws Exception {
        var origin = onClient(client, () -> client.player.blockPosition());
        var server = onClient(client, client::getSingleplayerServer);
        var placed = new CompletableFuture<Void>();
        server.execute(() -> {
            try {
                var world = server.overworld();
                for (boolean water : new boolean[]{true, false}) {
                    int centerX = origin.getX() + (water ? -4 : 4);
                    var block = water ? net.minecraft.world.level.block.Blocks.WATER : net.minecraft.world.level.block.Blocks.LAVA;
                    for (int z = origin.getZ() + 4; z <= origin.getZ() + 6; z++) {
                        for (int x = centerX - 1; x <= centerX + 1; x++) {
                            world.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, origin.getY() - 1, z), block.defaultBlockState());
                        }
                    }
                }
                placed.complete(null);
            } catch (Throwable error) {
                placed.completeExceptionally(error);
            }
        });
        placed.get(20, TimeUnit.SECONDS);
        for (boolean water : new boolean[]{true, false}) {
            onClient(client, () -> {
                client.player.setYRot(water ? 39 : -39);
                client.player.setXRot(21);
                return true;
            });
            String name = water ? "water" : "lava";
            Path file = client.gameDirectory.toPath().resolve("screenshots/port-audit-world-" + name + ".png");
            Thread.sleep(500);
            var image = capture(client, file);
            verifyFluidPixels(client, new int[]{image.getWidth()/3, image.getHeight()/3, image.getWidth()/3, image.getHeight()/3},
                    "world-" + name, water ? VersionAudit::waterStill : VersionAudit::lavaStill);
        }
    }

    private static void verifyWorldSky(Minecraft client)throws Exception{
        onClient(client,()->{
            client.options.pauseOnLostFocus=false;
            CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(null));
            return true;
        });
        await(client,()->client.gui.screen() instanceof CreateWorldScreen);
        onClient(client,()->{
            var creation=(CreateWorldScreen)client.gui.screen();
            var settings=creation.getUiState();
            settings.setName("TextureMixer isolated port audit");
            settings.setSeed("5724");
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setGenerateStructures(false);
            settings.setWorldType(settings.getNormalPresetList().stream()
                    .filter(type->type.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
            try{
                var create=CreateWorldScreen.class.getDeclaredMethod("onCreate");
                create.setAccessible(true);
                create.invoke(creation);
            }catch(ReflectiveOperationException error){throw new CompletionException(error);}
            return true;
        });
        await(client,()->client.level!=null&&client.player!=null&&client.gui.screen()==null&&client.gui.overlay()==null);
        var renderer=com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED;
        onClient(client,()->{
            client.player.setXRot(-65);
            client.gui.toastManager().clear();
            return true;
        });
        await(client,()->renderer.draws("overworld")>3);
        Path file=client.gameDirectory.toPath().resolve("screenshots/port-audit-world-sky.png");
        var screenshot=capture(client,file);
        int colored=0;
        for(int y=0;y<screenshot.getHeight()/3;y++)for(int x=0;x<screenshot.getWidth();x++){
            int rgb=screenshot.getRGB(x,y);
            if(Math.abs(((rgb>>16)&255)-38)<5&&Math.abs(((rgb>>8)&255)-211)<5&&Math.abs((rgb&255)-94)<5)colored++;
        }
        require(colored>100,"custom Overworld sky is visible ("+colored+" matching pixels)");
    }

    private static BufferedImage capture(Minecraft client,Path file)throws Exception{
        Files.deleteIfExists(file);
        var ready=new CompletableFuture<Void>();
        onClient(client,()->{
            net.minecraft.client.Screenshot.grab(client.gameDirectory, file.getFileName().toString(),
                    client.gameRenderer.mainRenderTarget(), 1, message -> ready.complete(null));
            return true;
        });
        ready.get(20,TimeUnit.SECONDS);
        return ImageIO.read(file.toFile());
    }

    private static void search(Minecraft client,String text){
        client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow() .setValue(text);
    }
    private static void await(Minecraft client,Supplier<Boolean> condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!onClient(client,condition)){
            require(System.nanoTime()<deadline,"timed out");
            Thread.sleep(100);
        }
    }
    private static <T>T onClient(Minecraft client,Supplier<T> work)throws Exception{
        return client.submit(work).get(65,TimeUnit.SECONDS);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
