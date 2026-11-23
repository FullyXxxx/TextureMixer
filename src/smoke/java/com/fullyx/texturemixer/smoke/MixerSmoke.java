package com.fullyx.texturemixer.smoke;
import com.fullyx.texturemixer.*;
import com.fullyx.texturemixer.client.*;
import com.fullyx.texturemixer.ui.*;
import com.fullyx.texturemixer.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.item.Items;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.option.Perspective;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.equipment.trim.*;
import net.minecraft.registry.*;
import net.minecraft.world.*;
import net.minecraft.util.math.Vec3d;
import java.util.*;
public final class MixerSmoke implements ClientModInitializer {
    public void onInitializeClient(){new Thread(()->{
        MinecraftClient client=MinecraftClient.getInstance();
        try{
            await(()->client.isFinishedLoading()&&client.getOverlay()==null&&client.currentScreen!=null);
            onClient(()->{client.createIntegratedServerLoader().start("texturemixer-test",()->{});return true;});
            await(()->client.player!=null&&client.world!=null&&client.getOverlay()==null&&TextureMixerClient.SERVICE!=null&&TextureMixerClient.SERVICE.catalog!=null);
            if(TextureMixerClient.SERVICE.committed.enabled)await(()->TextureMixerClient.SERVICE.mounted!=null&&client.getOverlay()==null);
            await(()->!TextureMixerClient.SERVICE.busy);
            onClient(()->{var service=TextureMixerClient.SERVICE;var next=service.committed.copy();Path fixtures=client.runDirectory.toPath().resolve("fixtures").toAbsolutePath().normalize();
                var benchmarks=next.sources.values().stream().filter(source->source.lastKnownFolderName().matches("Benchmark-[0-9]+")&&Path.of(source.canonicalRoot()).getParent().equals(fixtures)).map(MixerConfig.SourceRecord::id).toList();
                benchmarks.forEach(id->{next.candidateSources.remove(id);next.sources.remove(id);service.sources.remove(id);});
                try{service.store.save(next,false);service.committed=next;}catch(Exception error){throw new CompletionException(error);}return true;});
            onClient(()->{com.fullyx.texturemixer.core.UiLanguage.select("en_us");return true;});
            String originalSuffix=emissiveSentinel(true,null);
            onClient(()->{client.options.pauseOnLostFocus=false;client.setScreen(null);client.onWindowFocusChanged(true);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);client.getToastManager().clear();TextureMixerClient.open(client);return true;});
            if(Boolean.getBoolean("texturemixer.smokeAlpha12")){Alpha12Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha11")){Alpha11Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha10")){Alpha10Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha9")){Alpha9Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokePreviewFlow")){PreviewFlowSmoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha8")){Alpha8Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha7")){Alpha7Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha6")){Alpha6Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha5")){Alpha5Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha4")){Alpha4Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokePackApply")){Alpha3Smoke.runPackApplications(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeAlpha3")){Alpha3Smoke.run(client);return;}
            if(Boolean.getBoolean("texturemixer.smokePickerCheck")){pickerCheck(client);return;}
            if(Boolean.getBoolean("texturemixer.smokeUiCheck")){uiCheck(client);return;}
            await(()->client.currentScreen instanceof MixerScreen);
            onClient(()->{click(client,"Item");return true;});
            await(()->{var preview=TextureMixerClient.SERVICE.previews.get(TextureMixerClient.SERVICE.catalog.item("minecraft:diamond_sword"),"vanilla",0);
                if(preview.error!=null)throw new AssertionError("Preview failed: "+preview.error);return preview.ready();});
            emissiveSentinel(false,originalSuffix);
            Thread.sleep(8000);
            onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-initial.png",client.getFramebuffer(),1,t->System.out.println(t.getString()));return true;});
            onClient(()->{var screen=(MixerScreen)client.currentScreen;var field=screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();field.setText("diamond sword");return true;});
            Thread.sleep(3000);
            onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-search.png",client.getFramebuffer(),1,t->System.out.println(t.getString()));
                var screen=client.currentScreen;var card=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals("Diamond Sword")).findFirst().orElseThrow();
                screen.mouseClicked(new Click(card.getX()+20,card.getY()+20,new MouseInput(0,0)),false);return true;});
            Thread.sleep(4000);
            onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-detail.png",client.getFramebuffer(),1,t->System.out.println(t.getString()));return true;});
            MixerService service=TextureMixerClient.SERVICE;
            MixerConfig draft=onClient(()->service.committed.copy());Path fixtures=client.runDirectory.toPath().resolve("fixtures");
            String main=onClient(()->service.add(fixtures.resolve("Main-Pack.zip"),draft,true)).join();
            String a=onClient(()->service.add(fixtures.resolve("Pack-A.zip"),draft,false)).join();
            String b=onClient(()->service.add(fixtures.resolve("Pack-B.zip"),draft,false)).join();
            String empty=onClient(()->service.add(fixtures.resolve("Empty-Pack.zip"),draft,false)).join();
            String galaxy=onClient(()->service.add(fixtures.resolve("Sky-Galaxy.zip"),draft,false)).join();
            String aurora=onClient(()->service.add(fixtures.resolve("Sky-Aurora.zip"),draft,false)).join();
            String invalid=onClient(()->service.add(fixtures.resolve("Invalid-Pack.zip"),draft,false)).join();
            draft.itemOverrides.put("minecraft:diamond_sword",a);draft.itemOverrides.put("minecraft:emerald",b);
            draft.itemOverrides.put("minecraft:diamond_block",a);draft.itemOverrides.put("minecraft:diamond_helmet",a);
            draft.itemOverrides.put("minecraft:diamond_chestplate",b);draft.itemOverrides.put("minecraft:chest",a);
            draft.blockOverrides.put("minecraft:diamond_block",b);draft.blockOverrides.put("minecraft:chest",b);
            draft.blockOverrides.put("minecraft:water",b);draft.skyOverrides.put("overworld",galaxy);draft.skyOverrides.put("end",aurora);
            int normalReloads=SmokeControls.reloads;onClient(()->service.apply(draft)).join();
            await(()->!service.busy&&client.getOverlay()==null);
            if(SmokeControls.reloads!=normalReloads+1)throw new AssertionError("A successful apply must trigger exactly one native reload");
            onClient(()->{
                if(service.runtime()==null||!service.committed.enabled)throw new AssertionError("Generation was not applied");
                var configScreen=new MixerScreen(service);client.setScreen(configScreen);click(client,"Item");
                var field=configScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();field.setText("diamond sword");
                var card=configScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(w->w.getMessage().getString().equals("Diamond Sword")).findFirst().orElseThrow();
                configScreen.mouseClicked(new Click(card.getX()+20,card.getY()+20,new MouseInput(0,0)),false);return true;
            });
            await(()->{var p=service.previews.get(service.catalog.item("minecraft:diamond_sword"),a,0);if(p.error!=null)throw new AssertionError(p.error);return p.ready();});
            await(()->{var p=service.previews.get(service.catalog.item("minecraft:diamond_sword"),b,0);if(p.error!=null)throw new AssertionError(p.error);return p.ready();});
            Thread.sleep(1000);
            onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-mixed-sources.png",client.getFramebuffer(),1,t->{});return true;});
            // Verify the requested absent source is not replaced by the selected main pack.
            onClient(()->{var p=service.previews.get(service.catalog.item("minecraft:diamond_sword"),b,0);
                if(p.status!=GraphCompiler.Status.VANILLA_FALLBACK)throw new AssertionError("Missing sword did not fall back to vanilla");return true;});
            CompletableFuture<Void> worldSetup=new CompletableFuture<>();
            client.getServer().execute(()->{try{
                var server=client.getServer();var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                player.teleportTo(new TeleportTarget(server.getOverworld(),new Vec3d(.5,80,.5),Vec3d.ZERO,0,0,TeleportTarget.NO_OP));
                player.changeGameMode(GameMode.CREATIVE);
                var helmet=Items.DIAMOND_HELMET.getDefaultStack();var chestplate=Items.DIAMOND_CHESTPLATE.getDefaultStack();
                var materials=server.getRegistryManager().getOrThrow(RegistryKeys.TRIM_MATERIAL);var patterns=server.getRegistryManager().getOrThrow(RegistryKeys.TRIM_PATTERN);
                helmet.set(DataComponentTypes.TRIM,new ArmorTrim(materials.getOrThrow(ArmorTrimMaterials.GOLD),patterns.getOrThrow(ArmorTrimPatterns.SENTRY)));
                chestplate.set(DataComponentTypes.TRIM,new ArmorTrim(materials.getOrThrow(ArmorTrimMaterials.GOLD),patterns.getOrThrow(ArmorTrimPatterns.SENTRY)));
                player.equipStack(EquipmentSlot.HEAD,helmet);player.equipStack(EquipmentSlot.CHEST,chestplate);
                player.equipStack(EquipmentSlot.MAINHAND,Items.DIAMOND_SWORD.getDefaultStack());
                server.getOverworld().setTimeOfDay(18000);
                for(int x=-6;x<=6;x++)for(int z=-6;z<=8;z++){server.getOverworld().setBlockState(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.getDefaultState());for(int y=80;y<85;y++)server.getOverworld().setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());}
                server.getOverworld().setBlockState(new BlockPos(-1,80,4),Blocks.DIAMOND_BLOCK.getDefaultState());
                server.getOverworld().setBlockState(new BlockPos(1,80,4),Blocks.CHEST.getDefaultState());worldSetup.complete(null);
            }catch(Throwable error){worldSetup.completeExceptionally(error);}});worldSetup.join();
            onClient(()->{client.setScreen(null);client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);client.player.setYaw(180);client.player.setPitch(-5);return true;});
            Thread.sleep(2500);
            onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,"texturemixer-worn-and-sky.png",client.getFramebuffer(),1,t->{});return true;});
            onServer(()->{var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.equipStack(EquipmentSlot.MAINHAND,Items.DIAMOND_BLOCK.getDefaultStack());return true;});
            onClient(()->{client.getToastManager().clear();client.options.setPerspective(Perspective.FIRST_PERSON);client.player.setYaw(0);client.player.setPitch(12);return true;});
            Thread.sleep(1800);capture(client,"texturemixer-item-block-independent.png");
            // Native Block preview must bake the placed block state, not reuse the item's selected source.
            onClient(()->{client.setScreen(new MixerScreen(service));click(client,"Item");click(client,"Block");search(client,"diamond block");click(client,"Block of Diamond");return true;});
            await(()->{var p=service.previews.get(service.catalog.block("minecraft:diamond_block"),b,0);if(p.error!=null)throw new AssertionError(p.error);return p.ready();});
            Thread.sleep(700);capture(client,"texturemixer-block-preview.png");
            onClient(()->{click(client,SmokeLabels.text("返回"));search(client,"chest");click(client,"Chest");return true;});
            await(()->{var p=service.previews.get(service.catalog.block("minecraft:chest"),b,0);if(p.error!=null)throw new AssertionError(p.error);return p.ready();});
            Thread.sleep(700);capture(client,"texturemixer-chest-preview.png");
            for(int guiScale:new int[]{1,2,3}){
                onClient(()->{client.options.getGuiScale().setValue(guiScale);client.onResolutionChanged();return true;});Thread.sleep(700);
                onClient(()->{verifyLayout(client);return true;});capture(client,"texturemixer-hud-scale-"+guiScale+".png");
            }
            onClient(()->{search(client,"diamond");click(client,"×");var field=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
                if(client.currentScreen.getFocused()!=field)throw new AssertionError("Clear did not transfer Screen focus to Search");
                client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_Y,0,0));client.currentScreen.charTyped(new net.minecraft.client.input.CharInput('y',0));
                if(!field.getText().equals("y"))throw new AssertionError("Typing Y after Clear did not edit Search");return true;});
            onClient(()->{org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),960,720);return true;});
            await(()->client.getWindow().getScaledWidth()==320&&client.getWindow().getScaledHeight()==240);
            onClient(()->{client.setScreen(new MixerScreen(service));click(client,"Item");click(client,"Sky");return true;});Thread.sleep(1200);capture(client,"texturemixer-sky-320x240.png");
            onClient(()->{click(client,SmokeLabels.text("Night / Clear · 条件"));return true;});capture(client,"texturemixer-sky-conditions-320x240.png");
            onClient(()->{click(client,SmokeLabels.text("匹配选中来源的首层条件"));org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);return true;});
            await(()->client.getWindow().getWidth()==1280);
            onClient(()->{client.setScreen(null);return true;});
            // Native reload failure is injected only in the test mod, after real assets were uploaded.
            String previousId=service.mounted.id(),previousSignature=service.committed.renderSignature();
            MixerConfig broken=service.committed.copy();broken.itemOverrides.put("minecraft:diamond_sword",invalid);
            int beforeInvalid=SmokeControls.reloads;expectFailure(onClient(()->service.apply(broken)));
            if(SmokeControls.reloads!=beforeInvalid||!service.mounted.id().equals(previousId))throw new AssertionError("Validation failure changed world resources");
            MixerConfig changed=service.committed.copy();changed.itemOverrides.put("minecraft:diamond_sword",MixerConfig.VANILLA);
            SmokeControls.failNextReload=true;int beforeRollback=SmokeControls.reloads;
            expectFailure(onClient(()->service.apply(changed)));await(()->!service.busy&&client.getOverlay()==null);
            if(SmokeControls.reloads!=beforeRollback+2||service.mounted==null||!service.mounted.id().equals(previousId)||!service.committed.renderSignature().equals(previousSignature))throw new AssertionError("Failed native reload did not restore previous generation: reloads="+beforeRollback+"->"+SmokeControls.reloads+", id="+(service.mounted==null?"null":service.mounted.id())+", expected="+previousId+", signature="+service.committed.renderSignature().equals(previousSignature)+", status="+service.status);
            // Server resources form the base; personal main and explicit selections remain above them.
            var serverStone=AssetId.of("textures/block/stone.png");
            var serverMarker=AssetId.of("texturemixer_smoke:server_only.txt");
            var selectedSword=AssetId.of("items/diamond_sword.json");
            var serverFiles=new TreeMap<AssetId,byte[]>();
            serverFiles.put(serverStone,PackSource.open(fixtures.resolve("Pack-B.zip")).read(AssetId.of("textures/block/diamond_block.png")));
            serverFiles.put(serverMarker,"server base remains available".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            serverFiles.put(selectedSword,"{\"model\":{\"type\":\"minecraft:empty\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SmokeControls.serverAssets=new Assets.Memory(serverFiles);
            SmokeControls.serverOnNextReload=true;onClient(()->service.apply(changed)).join();
            await(()->!service.busy&&client.getOverlay()==null);
            if(!service.serverPresent||service.runtime()==null||!service.committed.renderSignature().equals(changed.renderSignature()))throw new AssertionError("Server arrival interrupted the personal mix");
            onClient(()->{verifyServerResources(client,service,serverMarker,serverStone,selectedSword);return true;});
            MixerConfig withServer=service.committed.copy();withServer.itemOverrides.put("minecraft:emerald",MixerConfig.VANILLA);
            onClient(()->service.apply(withServer)).join();await(()->!service.busy&&client.getOverlay()==null);
            onClient(()->{verifyServerResources(client,service,serverMarker,serverStone,selectedSword);return true;});
            var inheritedSky=Generation.compile(new MixerConfig(),service.vanilla,Map.of(),service.catalog);
            if(!inheritedSky.skies().isEmpty())throw new AssertionError("An untouched sky created an implicit vanilla override over the server base");
            var legacySky=new com.fullyx.texturemixer.sky.OptifineSkyAdapter().parse("overworld",new Assets.View(null,service.vanilla));
            var legacySnapshot=new Generation("legacy-sky",new Assets.Memory(Map.of()),Map.of(),Map.of(),Map.of(),Map.of("overworld",legacySky));
            var skyChoices=new MixerConfig();
            if(!legacySnapshot.withSkySelections(skyChoices).skies().isEmpty())throw new AssertionError("Legacy implicit vanilla sky survived server-base migration");
            skyChoices.skyOverrides.put("overworld",MixerConfig.VANILLA);
            if(!legacySnapshot.withSkySelections(skyChoices).skies().containsKey("overworld"))throw new AssertionError("Migration dropped an explicit vanilla sky choice");
            previousId=service.mounted.id();
            onClient(()->{SmokeControls.serverPack=false;client.getResourcePackManager().scanPacks();return true;});
            onClient(client::reloadResources).join();await(()->client.getOverlay()==null);
            if(service.serverPresent||service.runtime()==null||!service.runtime().id().equals(previousId))throw new AssertionError("Server pack removal changed the personal mix");
            onClient(()->{if(client.getResourceManager().getResource(net.minecraft.util.Identifier.of(serverMarker.toString())).isPresent())throw new AssertionError("Removed server resource remained mounted");return true;});
            onServer(()->{var server=client.getServer();var end=server.getWorld(World.END);for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)end.setBlockState(new BlockPos(x,99,z),Blocks.END_STONE.getDefaultState());
                server.getPlayerManager().getPlayer(client.player.getUuid()).teleportTo(new TeleportTarget(end,new Vec3d(.5,100,.5),Vec3d.ZERO,0,-40,TeleportTarget.NO_OP));return true;});
            await(()->client.world.getRegistryKey().equals(World.END)&&client.getOverlay()==null&&client.currentScreen==null);
            onClient(()->{var scene=com.fullyx.texturemixer.sky.SkySceneRenderer.current();
                // Vanilla deliberately skips the sky during dragon fog; preserve that rule in production.
                if(scene==null&&com.fullyx.texturemixer.sky.SkySceneRenderer.pausedReason().isEmpty())throw new AssertionError("No End scene or explanation");return true;});
            onServer(()->{client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).requestTeleport(30,110,30);return true;});
            Thread.sleep(2500);onClient(()->{client.player.setPitch(-45);client.getToastManager().clear();client.inGameHud.getChatHud().clear(false);
                ((com.fullyx.texturemixer.smoke.mixin.BossProbe)client.inGameHud.getBossBarHud()).texturemixer$bars().values().forEach(bar->{bar.setThickenFog(false);bar.setDarkenSky(false);});return true;});Thread.sleep(600);capture(client,"texturemixer-end-aurora.png");
            onClient(()->{if(com.fullyx.texturemixer.sky.SkySceneRenderer.SHARED.draws("end")==0)throw new AssertionError("End layers were never drawn");return true;});
            onServer(()->{var server=client.getServer();server.getPlayerManager().getPlayer(client.player.getUuid()).teleportTo(new TeleportTarget(server.getOverworld(),new Vec3d(.5,80,.5),Vec3d.ZERO,0,0,TeleportTarget.NO_OP));return true;});
            await(()->client.world.getRegistryKey().equals(World.OVERWORLD)&&client.currentScreen==null);
            // F3+T follows this same native reload entry point and must retain the immutable generation.
            onClient(client::reloadResources).join();await(()->client.getOverlay()==null);
            if(!service.mounted.id().equals(previousId))throw new AssertionError("Native reload changed committed generation");
            benchmark(client,service,fixtures);
            Files.writeString(client.runDirectory.toPath().resolve("smoke-result.txt"),"PASS: native preview/search/manual source registration; exactly one successful-apply reload; vanilla fallback; independent Item/Block native preview and world setup; separate trimmed equipment slots; Overworld galaxy and End aurora; preflight rejection without reload; injected native reload failure with rollback; actual SERVER-profile arrival during Apply preserves the personal mix; main and selected item resources win over conflicting server files, untouched server files remain readable; Apply with server present succeeds; server removal preserves personal generation; untouched Sky inherits base; native reload preserves generation. Screenshots require visual review; full 71-case acceptance and performance remain incomplete.");
            System.out.println("TEXTUREMIXER_SMOKE_PASS");
        }catch(Throwable error){error.printStackTrace();try{Files.writeString(client.runDirectory.toPath().resolve("smoke-result.txt"),"FAIL: "+error);}catch(Exception ignored){}}
        finally{client.execute(client::scheduleStop);}
    },"texturemixer-smoke").start();}
    private static void await(Supplier<Boolean> predicate)throws Exception{long deadline=System.currentTimeMillis()+120000;while(!onClient(predicate)){if(System.currentTimeMillis()>deadline)throw new AssertionError("Timeout waiting for client state");Thread.sleep(250);}}
    private static void click(MinecraftClient client,String label){
        if(Arrays.stream(MixerConfig.Kind.values()).anyMatch(kind->kind.label.equals(label)))
            for(int attempt=0;attempt<8&&client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).noneMatch(widget->widget.getMessage().getString().equals(label));attempt++)click(client,SmokeLabels.text("后"));
        var button=client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(w->w.getMessage().getString().equals(label)).filter(w->!label.equals("×")||w instanceof FlatButton flat&&"Clear Search".equals(flat.narrationLabel)).findFirst().orElseThrow();client.currentScreen.mouseClicked(new Click(button.getX()+button.getWidth()/2.0,button.getY()+Math.min(15,button.getHeight()/2.0),new MouseInput(0,0)),false);}
    private static void search(MinecraftClient client,String value){client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow().setText(value);}
    private static void capture(MinecraftClient client,String name){onClient(()->{ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,t->{});return true;});}
    private static void expectFailure(CompletableFuture<?> task){try{task.join();throw new AssertionError("Expected transaction failure");}catch(CompletionException expected){System.out.println("EXPECTED_SMOKE_FAILURE: "+MixerService.message(expected));}}
    private static void verifyLayout(MinecraftClient client){
        var controls=client.currentScreen.children().stream().filter(ClickableWidget.class::isInstance).map(ClickableWidget.class::cast).toList();
        for(var control:controls){
            if(control.getClass().getSimpleName().equals("Card")){
                if(control.getWidth()!=34&&control.getWidth()!=40)throw new AssertionError("Preview size is wrong");
            }else if(control.getX()<0||control.getY()<0||control.getX()+control.getWidth()>client.currentScreen.width||control.getY()+control.getHeight()>client.currentScreen.height)
                throw new AssertionError("Control outside viewport: "+control.getMessage().getString());
        }
    }

    private static String emissiveSentinel(boolean install,String original)throws Exception {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("continuity"))return null;
        var field=Class.forName("me.pepperbell.continuity.client.resource.EmissiveSuffixLoader").getDeclaredField("emissiveSuffix");field.setAccessible(true);
        String current=(String)field.get(null);
        if(install)field.set(null,"_texturemixer_isolation_probe");
        else {if(!"_texturemixer_isolation_probe".equals(current))throw new AssertionError("Private preview changed Continuity world emissive suffix");field.set(null,original);}
        return current;
    }
    private static void awaitPicker(Supplier<Boolean> predicate)throws Exception {
        long deadline=System.currentTimeMillis()+600000;
        while(!onClient(predicate)){if(System.currentTimeMillis()>deadline)throw new AssertionError("Timeout waiting for native file dialog");Thread.sleep(250);}
    }
    private static void pickerCheck(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;var screen=(MixerScreen)client.currentScreen;
        Path source=client.runDirectory.toPath().resolve(SmokeLabels.text("fixtures/Picker 试验.zip")).toAbsolutePath();
        Files.copy(client.runDirectory.toPath().resolve("fixtures/Pack-B.zip"),source,StandardCopyOption.REPLACE_EXISTING);
        byte[] before=Files.readAllBytes(source);int count=screen.draft.sources.size(),reloads=SmokeControls.reloads;
        var picking=MixerScreen.class.getDeclaredField("picking");picking.setAccessible(true);
        System.out.println("PICKER_WAIT_FOCUS");
        awaitPicker(()->org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==org.lwjgl.glfw.GLFW.GLFW_TRUE);
        onClient(()->{screen.chooseFolder(false);return true;});System.out.println("PICKER_CANCEL_READY");
        awaitPicker(()->{try{return !picking.getBoolean(screen);}catch(Exception e){throw new CompletionException(e);}});
        if(screen.draft.sources.size()!=count)throw new AssertionError("Cancel changed the source library");
        onClient(()->{screen.chooseFolder(false);return true;});System.out.println("PICKER_IMPORT_READY "+source);
        awaitPicker(()->screen.draft.sources.values().stream().anyMatch(record->record.canonicalRoot().equals(source.toString())));
        if(!Arrays.equals(before,Files.readAllBytes(source))||SmokeControls.reloads!=reloads)throw new AssertionError("Import changed ZIP or reloaded global resources");
        Files.writeString(client.runDirectory.toPath().resolve("picker-result.txt"),"PASS: native ZIP file dialog cancellation leaves source library unchanged; selecting a ZIP containing spaces and Chinese path imports it; source bytes unchanged; browsing/import causes no global resource reload.");
    }
    private static void uiCheck(MinecraftClient client)throws Exception {
        var service=TextureMixerClient.SERVICE;String invalid=service.committed.sources.values().stream().filter(source->source.lastKnownFolderName().equals("Invalid-Pack.zip")).findFirst().orElseThrow().id();
        String old=service.mounted.id();MixerScreen failure=onClient(()->{var screen=new MixerScreen(service);screen.draft.itemOverrides.put("minecraft:diamond_sword",invalid);client.setScreen(screen);click(client,SmokeLabels.text("应用"));return screen;});
        await(()->!service.busy&&service.status.contains(SmokeLabels.text("校验失败")));Thread.sleep(400);
        if(!failure.draft.itemOverrides.get("minecraft:diamond_sword").equals(invalid)||!service.mounted.id().equals(old))throw new AssertionError("Failed UI apply discarded draft or changed world");
        onClient(()->{var status=client.currentScreen.children().stream().filter(FlatButton.class::isInstance).map(FlatButton.class::cast).filter(b->b.getHeight()==12).findFirst().orElseThrow();if(!status.getMessage().getString().contains(SmokeLabels.text("校验失败")))throw new AssertionError("Card feedback masked Apply failure: "+status.getMessage().getString());return true;});
        capture(client,"texturemixer-compact-validation-error.png");
        onClient(()->{var status=client.currentScreen.children().stream().filter(FlatButton.class::isInstance).map(FlatButton.class::cast).filter(b->b.getHeight()==12).findFirst().orElseThrow();click(client,status.getMessage().getString());return true;});
        Thread.sleep(300);capture(client,"texturemixer-complete-error-message.png");
        onClient(()->{client.setScreen(new MixerScreen(service));click(client,"Item");search(client,"diamond sword");click(client,"Diamond Sword");return true;});
        String empty=service.committed.sources.values().stream().filter(source->source.lastKnownFolderName().equals("Empty-Pack.zip")).findFirst().orElseThrow().id();
        await(()->service.previews.get(service.catalog.item("minecraft:diamond_sword"),empty,0).ready());
        onClient(()->{var card=client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals("Empty-Pack.zip")).findFirst().orElseThrow();client.currentScreen.setFocused(card);card.setFocused(true);return true;});
        Thread.sleep(300);capture(client,"texturemixer-compact-fallback-message.png");
        onClient(()->{var card=client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getMessage().getString().equals("Pack-A.zip")).findFirst().orElseThrow();
            try{var method=FlatButton.class.getDeclaredMethod("getNarrationMessage");method.setAccessible(true);String narration=((net.minecraft.text.Text)method.invoke(card)).getString();if(!narration.contains(SmokeLabels.text("当前选中")))throw new AssertionError("Selected source missing from narration: "+narration);}
            catch(ReflectiveOperationException error){throw new CompletionException(error);}
            search(client,"diamond");click(client,"×");var field=client.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
            if(client.currentScreen.getFocused()!=field)throw new AssertionError("Clear focus regression");client.currentScreen.charTyped(new net.minecraft.client.input.CharInput('y',0));if(!field.getText().equals("y"))throw new AssertionError("Y was not typed into Search");
            for(int key:new int[]{org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE}){
                var clear=client.currentScreen.children().stream().filter(FlatButton.class::isInstance).map(FlatButton.class::cast).filter(b->"Clear Search".equals(b.narrationLabel)).findFirst().orElseThrow();
                client.currentScreen.setFocused(clear);clear.setFocused(true);client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(key,0,0));
                if(client.currentScreen.getFocused()!=field||!field.getText().isEmpty())throw new AssertionError("Keyboard Clear did not transfer focus for "+key);
                client.currentScreen.charTyped(new net.minecraft.client.input.CharInput('x',0));client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_Y,0,0));client.currentScreen.charTyped(new net.minecraft.client.input.CharInput('y',0));
                if(!field.getText().equals("xy"))throw new AssertionError("Typing after keyboard Clear failed for "+key);
            }return true;});
        onClient(()->{
            String selected=service.committed.sources.values().stream().filter(source->source.lastKnownFolderName().equals("Pack-A.zip")).findFirst().orElseThrow().id();
            var removal=new MixerScreen(service);for(var kind:MixerConfig.Kind.values())removal.draft.overrides(kind).replaceAll((target,source)->source.equals(selected)?MixerConfig.VANILLA:source);
            if(service.committed.referenceCount(selected)==0)throw new AssertionError("Removal fixture must still be committed");
            var manager=new SourceManagerScreen(removal);client.setScreen(manager);
            try{var action=SourceManagerScreen.class.getDeclaredMethod("removeSource",String.class);action.setAccessible(true);action.invoke(manager,selected);}
            catch(ReflectiveOperationException error){throw new CompletionException(error);}
            click(client,SmokeLabels.text("移除并改用原版"));
            try{if(removal.draft.sources.containsKey(selected)||!service.committed.sources.containsKey(selected)||!service.store.load().sources.containsKey(selected))throw new AssertionError("Draft-only source removal prematurely changed committed library");}
            catch(java.io.IOException error){throw new CompletionException(error);}return true;
        });
        onClient(()->{
            var manager=new SourceManagerScreen(new MixerScreen(service));client.setScreen(manager);
            if(UiDraw.reduceMotion())throw new AssertionError("Animations should always be enabled");
            var labels=manager.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).map(button->button.getMessage().getString()).toList();
            if(labels.stream().anyMatch(label->label.startsWith(SmokeLabels.text("动效"))||label.equals(SmokeLabels.text("停用混搭")))||!labels.contains(SmokeLabels.text("添加文件夹")))throw new AssertionError("Manager actions do not match the new layout");
            return true;
        });capture(client,"texturemixer-source-manager.png");
        Files.writeString(client.runDirectory.toPath().resolve("ui-check-result.txt"),"PASS: failed UI Apply leaves draft/world unchanged; compact failure and full diagnostic route; focused fallback explanation; selected source narration; mouse, Enter and Space Clear activation transfer native Screen focus, subsequent ordinary text and Y edit Search; removing a source whose bindings were changed only in draft retains committed library until Apply. Animations stay enabled and the manager shows pack/folder import plus a separate return row. Screenshots require review.");
    }
    private static void benchmark(MinecraftClient client,MixerService service,Path fixtures)throws Exception{
        MixerConfig library=service.committed.copy();
        for(int i=0;library.candidateCount()<32;i++){Path path=fixtures.resolve("Benchmark-"+i);Files.createDirectories(path.resolve("assets"));onClient(()->service.add(path,library,false)).join();}
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<Double>> pending=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong started=new java.util.concurrent.atomic.AtomicLong();
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc,screen,w,h)->{
            if(screen instanceof MixerScreen)net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s,ctx,mx,my,delta)->{
                var future=pending.getAndSet(null);if(future!=null)future.complete((System.nanoTime()-started.get())/1e6);
            });
        });
        onClient(()->{client.setScreen(new MixerScreen(service));click(client,"Item");return true;});Thread.sleep(2000);
        List<Double> open=new ArrayList<>(),queries=new ArrayList<>();
        for(int i=0;i<50;i++){
            onClient(()->{client.setScreen(null);return true;});var frame=new CompletableFuture<Double>();
            onClient(()->{started.set(System.nanoTime());pending.set(frame);client.setScreen(new MixerScreen(service));click(client,"Item");return true;});
            double elapsed=frame.orTimeout(15,TimeUnit.SECONDS).join();if(i<30)open.add(elapsed);
        }
        String[] searches={"diamond sword","emerald","bow","oak door","chest","stone","potion","minecraft:iron","elytra","no_such_item"};
        for(int i=0;i<100;i++){String query=searches[i%searches.length];var frame=new CompletableFuture<Double>();
            onClient(()->{started.set(System.nanoTime());pending.set(frame);search(client,query);
                var expected=service.catalog.items.stream().filter(target->target.matches(query)).findFirst();
                if(expected.isPresent()&&client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).noneMatch(b->b.getMessage().getString().equals(expected.get().name())))throw new AssertionError("Wrong search result for "+query);
                return true;});queries.add(frame.orTimeout(15,TimeUnit.SECONDS).join());}
        onClient(()->{client.setScreen(null);return true;});await(()->service.previews.usage().pending()==0&&service.previews.usage().retired()==0);
        var data=new LinkedHashMap<String,Object>();data.put("note","Method invocation to ScreenEvents.afterRender CPU timestamp; excludes hardware key delivery, GPU completion and swap. 1280x720 GUI scale 3, "+library.candidateCount()+" candidates plus main. Not the full reference-hardware performance acceptance.");
        data.put("java",System.getProperty("java.version"));data.put("maxHeapBytes",Runtime.getRuntime().maxMemory());data.put("warmOpenMs",open);data.put("searchMs",queries);
        var sortedOpen=new ArrayList<>(open);Collections.sort(sortedOpen);var sortedSearch=new ArrayList<>(queries);Collections.sort(sortedSearch);
        data.put("warmOpenP95Ms",sortedOpen.get((int)Math.ceil(sortedOpen.size()*.95)-1));data.put("searchP95Ms",sortedSearch.get((int)Math.ceil(sortedSearch.size()*.95)-1));data.put("previewCacheAfterClose",onClient(()->service.previews.usage()));
        Files.writeString(client.runDirectory.toPath().resolve("benchmark-result.json"),MixerConfig.GSON.toJson(data));
    }
    private static void verifyServerResources(MinecraftClient client,MixerService service,AssetId marker,AssetId... overridden){
        if(client.getResourceManager().getResource(net.minecraft.util.Identifier.of(marker.toString())).isEmpty())throw new AssertionError("Untouched server resource is missing");
        for(AssetId asset:overridden)try{
            byte[] own=service.mounted.assets().read(asset);
            if(own==null)throw new AssertionError("Missing personal fixture: "+asset);
            var resource=client.getResourceManager().getResource(net.minecraft.util.Identifier.of(asset.toString())).orElseThrow();
            try(var stream=resource.getInputStream()){
                if(!Arrays.equals(own,stream.readAllBytes()))throw new AssertionError("Server resource replaced the personal selection: "+asset);
            }
        }catch(java.io.IOException error){throw new CompletionException(error);}
    }
    private static <T>T onServer(Supplier<T> action){var future=new CompletableFuture<T>();MinecraftClient.getInstance().getServer().execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});return future.join();}
    private static <T>T onClient(Supplier<T> action){CompletableFuture<T> future=new CompletableFuture<>();MinecraftClient.getInstance().execute(()->{try{future.complete(action.get());}catch(Throwable error){future.completeExceptionally(error);}});return future.join();}
}
