package com.fullyx.texturemixer;

import com.fullyx.texturemixer.core.*;
import com.fullyx.texturemixer.sky.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.google.gson.*;

public final class CoreRegression {
    private static int checks;
    private static void check(boolean value,String label) { checks++;if(!value)throw new AssertionError(label); }
    private static byte[] text(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static byte[] png(int w,int h,int color) throws Exception {
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,color);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
    }
    private static Assets.Memory memory(Map<String,byte[]> map) {
        Map<AssetId,byte[]> result=new TreeMap<>();map.forEach((k,v)->result.put(AssetId.of(k),v));return new Assets.Memory(result);
    }
    @FunctionalInterface private interface Checked {void run()throws Exception;}
    private static void rejects(Checked action,String label)throws Exception {
        try{action.run();throw new AssertionError(label+" was accepted");}
        catch(IOException|IllegalArgumentException expected){checks++;}
    }
    private static void archive(Path file,Map<String,byte[]> entries)throws IOException {
        try(var zip=new ZipOutputStream(Files.newOutputStream(file))){for(var entry:entries.entrySet()){
            zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
    }
    private static void verifyLegacyModels(byte[] original,byte[] selected)throws Exception {
        var model=AssetId.of("models/item/bow.json");
        var pulled=AssetId.of("models/item/bow_pulling_0.json");
        var vanilla=memory(Map.of(
                model.path(),text("""
                    {"parent":"builtin/generated","textures":{"layer0":"item/bow"},"overrides":[
                      {"predicate":{"pulling":1,"pull":0.5},"model":"item/bow_pulling_0"}]}
                    """),
                pulled.path(),text("""
                    {"parent":"item/bow","textures":{"layer0":"item/bow_pulling_0"}}
                    """),
                "textures/item/bow.png",original,
                "textures/item/bow_pulling_0.png",original));
        var pack=memory(Map.of("textures/item/bow_pulling_0.png",selected));
        var graph=new GraphCompiler(new Assets.View(pack,vanilla),"legacy-bow");
        var result=graph.legacyItem(AssetId.of("bow"),null,false);
        var output=new Assets.Memory(result.resources());
        var branch=output.json(model).getAsJsonArray("overrides").get(0).getAsJsonObject();
        check(branch.getAsJsonObject("predicate").get("pull").getAsDouble()==0.5,"legacy override predicate survives");
        check(branch.get("model").getAsString().equals(graph.privateId(AssetId.of("item/bow_pulling_0")).toString()),"legacy branch points to an isolated model");
        check(output.json(graph.privateId(pulled)).get("parent").getAsString().equals(graph.privateId(AssetId.of("item/bow")).toString()),"legacy variant can inherit its base");
        check(Arrays.equals(output.read(graph.privateId(AssetId.of("textures/item/bow_pulling_0.png"))),selected),"legacy override loads selected texture");
    }
    private static void verifyZipSources(Path temp,byte[] texture)throws Exception {
        Path zip=temp.resolve("中文材质.zip");String resource="assets/minecraft/textures/item/test.png";
        byte[] metadata=text("{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0]},\"overlays\":{\"entries\":[{\"directory\":\"new\",\"min_format\":[75,0],\"max_format\":[75,0]}]}}");
        archive(zip,Map.of("pack.mcmeta",metadata,resource,text("base"),"new/"+resource,texture));
        byte[] before=Files.readAllBytes(zip);var source=new ZipSource(zip);
        check(source.mainCompatible(),"ZIP metadata supports target format");
        check(Arrays.equals(source.read(AssetId.of("textures/item/test.png")),texture),"ZIP active overlay wins");
        check(source.unchanged()&&source.indexedBytes()==texture.length,"ZIP index metadata and selected bytes");
        check(PackSource.open(zip) instanceof ZipSource,"registered ZIP restores through source factory");
        check(Arrays.equals(before,Files.readAllBytes(zip)),"source ZIP remains byte identical after reads");
        archive(zip,Map.of(resource,texture));check(!source.unchanged(),"ZIP external change invalidates index");
        check(!new ZipSource(zip).mainCompatible(),"ZIP without metadata does not declare a target format");
        Path legacy=temp.resolve("legacy-format-1.zip");
        archive(legacy,Map.of("pack.mcmeta",text("""
                {"pack":{"pack_format":1}}
                """),
                "assets/minecraft/textures/items/diamond_sword.png",texture,
                "assets/minecraft/textures/blocks/wool_colored_silver.png",texture,
                "assets/minecraft/textures/gui/widgets.png",png(256,256,0xff224477),
                "assets/minecraft/textures/environment/moon_phases.png",png(128,64,0xff224466),
                "assets/minecraft/models/item/diamond_sword.json",text("""
                {"parent":"item/generated","textures":{"layer0":"items/diamond_sword"},"overrides":[{"predicate":{"damaged":1},"model":"item/old"}]}
                """)));
        var adapted=PackSource.open(legacy);
        var sword=AssetId.of("textures/item/diamond_sword.png");
        check(Arrays.equals(adapted.read(sword),texture),"legacy items layout is visible to previews");
        check(Arrays.equals(adapted.snapshot().read(sword),texture),"legacy items layout is included in the applied snapshot");
        check(adapted.contains(AssetId.of("textures/block/light_gray_wool.png")),"flattened block names resolve");
        check(adapted.json(AssetId.of("models/item/diamond_sword.json")).getAsJsonObject("textures").get("layer0").getAsString().equals("minecraft:item/diamond_sword"),"legacy model texture references resolve");
        byte[] hotbar=adapted.read(AssetId.of("textures/gui/sprites/hud/hotbar.png"));
        check(DirectorySource.pngSize(sword,hotbar,true).equals(new DirectorySource.ImageSize(182,22)),"legacy GUI sprites are sliced at the native size");
        byte[] moon=adapted.read(AssetId.of("textures/environment/celestial/moon/waxing_gibbous.png"));
        check(DirectorySource.pngSize(sword,moon,true).equals(new DirectorySource.ImageSize(32,32)),"legacy moon sheet is split by phase");
        check(SearchQuery.matches("§bFrost §lMashup", "FrostMashup"),"pack color codes do not disrupt search");
        var gain=com.fullyx.texturemixer.client.AudioPreview.class.getDeclaredMethod("applyGain",byte[].class,double.class);
        gain.setAccessible(true);
        byte[] pcm={0,64,0,-64};
        gain.invoke(null,pcm,.5);
        check(Arrays.equals(pcm,new byte[]{0,32,0,-32}),"audition gain scales signed PCM in either direction");
        gain.invoke(null,pcm,0.0);
        check(Arrays.equals(pcm,new byte[4]),"minimum audition volume mutes PCM");
        Path wrapped=temp.resolve("wrapped.zip");archive(wrapped,Map.of("My Pack/"+resource,texture));
        check(Arrays.equals(new ZipSource(wrapped).read(AssetId.of("textures/item/test.png")),texture),"single enclosing folder ZIP");
        Path empty=temp.resolve("empty.zip");archive(empty,Map.of("assets/",new byte[0]));
        check(new ZipSource(empty).ids().isEmpty(),"empty candidate ZIP allows vanilla fallback");
        Path invalid=temp.resolve("bad.zip");
        archive(invalid,Map.of("../"+resource,texture));rejects(()->new ZipSource(invalid),"ZIP traversal");
        archive(invalid,Map.of("C:/"+resource,texture));rejects(()->new ZipSource(invalid),"ZIP absolute drive path");
        archive(invalid,Map.of("a/"+resource,texture,"b/"+resource,texture));rejects(()->new ZipSource(invalid),"ambiguous ZIP roots");
        archive(invalid,Map.of("aa/"+resource,texture,"bb/"+resource,texture));
        byte[] duplicate=Files.readAllBytes(invalid),from=text("bb/"+resource),to=text("aa/"+resource);
        for(int offset=0;offset<=duplicate.length-from.length;offset++)if(Arrays.equals(duplicate,offset,offset+from.length,from,0,from.length))System.arraycopy(to,0,duplicate,offset,to.length);
        Files.write(invalid,duplicate);rejects(()->new ZipSource(invalid),"duplicate ZIP paths");
        archive(invalid,Map.of(resource,texture,"pack.mcmeta",text("{\"filter\":{}}")));rejects(()->new ZipSource(invalid),"ZIP filter rejected");
        Files.writeString(invalid,"broken ZIP");rejects(()->new ZipSource(invalid),"corrupt ZIP rejected");
        Path rar=temp.resolve("pack.rar");Files.copy(wrapped,rar);rejects(()->new ZipSource(rar),"non ZIP imports rejected");
    }
    public static void main(String[] arguments) throws Exception {
        var pack=new com.fullyx.texturemixer.client.MemoryPack("directory-boundary",memory(Map.of(
                "textures/entity/shield.png",text("item"),
                "textures/entity/shield/base.png",text("base"),
                "textures/entity/shield_extra/other.png",text("sibling"))));
        List<net.minecraft.util.Identifier> found=new ArrayList<>();
        pack.findResources(net.minecraft.resource.ResourceType.CLIENT_RESOURCES,"minecraft","textures/entity/shield",(id,input)->found.add(id));
        check(found.equals(List.of(com.fullyx.texturemixer.client.VersionedIdentifiers.vanilla("textures/entity/shield/base.png"))),"directory enumeration excludes same-name files and sibling prefixes");
        found.clear();
        pack.findResources(net.minecraft.resource.ResourceType.CLIENT_RESOURCES,"minecraft","textures/entity/shield/",(id,input)->found.add(id));
        check(found.size()==1,"directory enumeration accepts trailing separator");
        found.clear();
        pack.findResources(net.minecraft.resource.ResourceType.CLIENT_RESOURCES,"minecraft","",(id,input)->found.add(id));
        check(found.size()==3,"root enumeration includes all assets");
        check(SearchQuery.matches("Boss Bar","BossBar"),"search accepts joined words");
        check(SearchQuery.matches("minecraft:gui/boss_bar","bossbar"),"search ignores underscore separators");
        check(SearchQuery.matches("My-Boss-Bar.zip","BOSS BAR"),"search ignores case and dashes");
        check(SearchQuery.matches("Boss Bar Red","red boss"),"search preserves multiple keyword matching");
        check(!SearchQuery.matches("Boss Bar","crossbow"),"search rejects unrelated names");
        check(SearchQuery.matches("Boss Bar","   "),"blank search shows all entries");
        MixerConfig resetMix=new MixerConfig();
        String baseId=UUID.randomUUID().toString(),candidateId=UUID.randomUUID().toString();
        resetMix.sources.put(baseId,new MixerConfig.SourceRecord(baseId,"base.zip","base.zip","test"));
        resetMix.sources.put(candidateId,new MixerConfig.SourceRecord(candidateId,"candidate.zip","candidate.zip","test"));
        resetMix.setMainStack(List.of(baseId));resetMix.addCandidate(candidateId);
        for(var kind:MixerConfig.Kind.values())resetMix.overrides(kind).put("minecraft:test",candidateId);
        resetMix.clearOverrides();
        check(resetMix.mainStack().equals(List.of(baseId)),"reset mix preserves main stack");
        check(Arrays.stream(MixerConfig.Kind.values()).allMatch(kind->resetMix.overrides(kind).isEmpty()),"reset mix clears all seven override categories");
        check(resetMix.sources.size()==2&&resetMix.candidateSources.contains(candidateId),"reset mix preserves source library");
        byte[] original=png(16,16,0xffcccccc),custom=png(16,16,0xff0055ff);

        MixerConfig legacy=MixerConfig.GSON.fromJson("{\"schemaVersion\":1}",MixerConfig.class);
        legacy.validate();check(legacy.enchantOverrides.isEmpty(),"older configs initialize the Enchant category");
        var fireAssets=memory(Map.of(ResourceCompiler.FIRE_OVERLAY_TARGET,original));
        var overlay=new ResourceCompiler(new Assets.View(null,fireAssets),"fire-only").compile(MixerConfig.Kind.HUD,ResourceCompiler.FIRE_OVERLAY_TARGET);
        check(Arrays.equals(overlay.files().get(ResourceCompiler.FIRE_OVERLAY_TEXTURE),original),"screen fire uses a dedicated texture");
        check(!overlay.files().containsKey(AssetId.of(ResourceCompiler.FIRE_OVERLAY_TARGET))&&overlay.files().containsKey(AssetId.of("atlases/blocks.json")),"screen fire leaves world fire independently bound");
        check(ResourceCompiler.enchantTexture(AssetId.of("textures/misc/enchanted_glint_item.png"))&&ResourceCompiler.enchantTexture(AssetId.of("textures/misc/enchanted_glint_armor.png")),"both native glint textures are cataloged");
        var glintAssets=memory(Map.of("textures/misc/enchanted_glint_item.png",custom));
        var glint=new ResourceCompiler(new Assets.View(glintAssets,glintAssets),"glint").compile(MixerConfig.Kind.ENCHANT,"minecraft:textures/misc/enchanted_glint_item.png");
        check(glint.custom()&&Arrays.equals(glint.files().get(AssetId.of("textures/misc/enchanted_glint_item.png")),custom),"Enchant compiles selected glint bytes");
        Map<String,byte[]> previewOnly=new HashMap<>();
        previewOnly.put("items/preview.json",text("{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:trim_material\",\"cases\":[{\"when\":\"minecraft:gold\",\"model\":{\"type\":\"minecraft:model\",\"model\":\"item/missing_trim\"}}],\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"item/plain\"}}}"));
        previewOnly.put("models/item/plain.json",text("{\"parent\":\"minecraft:builtin/generated\",\"textures\":{\"layer0\":\"minecraft:item/plain\"}}"));
        previewOnly.put("textures/item/plain.png",original);
        previewOnly.put("equipment/preview.json",text("{\"layers\":{\"humanoid\":[{\"texture\":\"minecraft:preview\"}]}}"));
        previewOnly.put("textures/entity/equipment/humanoid/preview.png",original);
        var previewView=new Assets.View(null,memory(previewOnly));
        var plainPreview=new GraphCompiler(previewView,"plain preview").itemPreview(AssetId.of("preview"),null,false);
        check(!new String(plainPreview.resources().get(AssetId.of("items/preview.json")),java.nio.charset.StandardCharsets.UTF_8).contains("cases"),"untrimmed preview prunes unused trim variants");
        rejects(()->new GraphCompiler(previewView,"full item").item(AssetId.of("preview"),null),"full application retains every model branch");
        rejects(()->new GraphCompiler(previewView,"trimmed preview").itemPreview(AssetId.of("preview"),null,true),"trimmed preview retains trim branches");
        var wornOnly=new GraphCompiler(previewView,"worn only").wornPreview(AssetId.of("preview"));
        check(wornOnly.resources().keySet().stream().noneMatch(asset->asset.path().startsWith("items/")||asset.path().startsWith("atlases/"))&&wornOnly.equipment().size()==1,"ordinary worn preview loads equipment without model or trim atlases");

        Assets.Memory vanilla=memory(Map.of("items/test.json",text("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/test\"}}"),
                "models/item/test.json",text("{\"parent\":\"minecraft:builtin/generated\",\"textures\":{\"layer0\":\"minecraft:item/test\"}}"),
                "textures/item/test.png",original));
        var empty=new Assets.Memory(Map.of());var textureOnly=memory(Map.of("textures/item/test.png",custom));
        var view=new Assets.View(textureOnly,vanilla);
        var a=new GraphCompiler(view,"A|item/test").item(AssetId.of("test"),null);
        var b=new GraphCompiler(new Assets.View(empty,vanilla),"B|item/test").item(AssetId.of("test"),null);
        check(a.status()==GraphCompiler.Status.MIXED_WITH_VANILLA,"texture-only pack identified");
        check(b.status()==GraphCompiler.Status.VANILLA_FALLBACK,"empty source falls back to vanilla");
        check(!a.namespacePrefix().equals(b.namespacePrefix()),"target/source namespaces are isolated");
        check(a.fromSource().contains(AssetId.of("textures/item/test.png")),"resource provenance");
        check(Arrays.equals(b.resources().get(new AssetId(b.namespacePrefix()+"minecraft","textures/item/test.png")),original),"fallback bytes are vanilla");
        Map<String,byte[]> cycleMap=new HashMap<>();cycleMap.put("models/item/test.json",text("{\"parent\":\"minecraft:item/test\"}"));
        try {new GraphCompiler(new Assets.View(memory(cycleMap),vanilla),"cycle").item(AssetId.of("test"),null);throw new AssertionError("cycle accepted");}
        catch(IOException expected){check(expected.getMessage().contains("Cyclic"),"cycle diagnostic");}
        Path temp=Files.createTempDirectory("texturemixer-regression-");
        Path localeSettings=temp.resolve("locale.json");
        Files.writeString(localeSettings,"{\"enabled\":false,\"custom\":7,\"categoryLanguage\":\"zh_cn\",\"largePreviews\":true}");
        ClientSettings.saveLanguage(localeSettings,"en_us");ClientSettings.savePreload(localeSettings,false);ClientSettings.saveEnabled(localeSettings,true);
        check(ClientSettings.loadLanguage(localeSettings).equals("en_us")&&!ClientSettings.loadPreload(localeSettings)&&ClientSettings.loadEnabled(localeSettings),"client settings updates preserve sibling fields");
        check(JsonParser.parseString(Files.readString(localeSettings)).getAsJsonObject().get("largePreviews").getAsBoolean(),"retired settings survive writing active preferences");
        check(JsonParser.parseString(Files.readString(localeSettings)).getAsJsonObject().get("custom").getAsInt()==7,"client settings retain unknown fields");
        rejects(()->ClientSettings.saveLanguage(localeSettings,"invalid"),"unsupported UI locale rejected");
        UiLanguage.select("en_us");check(UiLanguage.tr("取消").equals("Cancel"),"English UI strings are bundled");
        check(UiLanguage.chineseLabel("Enchant").equals("附魔"),"category translations are independent of interface language");
        var englishNames=new com.google.gson.JsonObject();
        englishNames.addProperty("item.minecraft.diamond_sword","Diamond Sword");
        var chineseNames=new com.google.gson.JsonObject();
        chineseNames.addProperty("item.minecraft.diamond_sword","钻石剑");
        UiLanguage.loadGameNames(englishNames,Assets.jsonBytes(chineseNames));
        UiLanguage.select("zh_cn");check(UiLanguage.englishGameName("item.minecraft.diamond_sword","").equals("Diamond Sword"),"native English names are fixed");
        check(UiLanguage.searchNames("item.minecraft.diamond_sword","").contains("钻石剑"),"search includes the Chinese alias");
        check(ResourceCompiler.menuTexture(AssetId.of("textures/gui/sprites/widget/button.png"))
                &&!ResourceCompiler.containerTexture(AssetId.of("textures/gui/sprites/widget/button.png")),"menu and container targets are disjoint");
        check(ResourceCompiler.containerTexture(AssetId.of("textures/gui/container/inventory.png"))
                &&!ResourceCompiler.hudTexture(AssetId.of("textures/gui/container/inventory.png")),"container targets and HUD are disjoint");
        MixerConfig older=MixerConfig.GSON.fromJson("{\"schemaVersion\":1}",MixerConfig.class);
        older.validate();
        check(older.menuOverrides.isEmpty()&&older.containerOverrides.isEmpty()&&older.fontOverrides.isEmpty(),"older configurations default to following the main pack");
        Path switchPath=temp.resolve("client-switch.json");
        check(ClientSettings.loadEnabled(switchPath),"feature switch defaults on for old installations");
        ClientSettings.saveEnabled(switchPath,false);check(!ClientSettings.loadEnabled(switchPath),"feature off persists");
        ClientSettings.saveEnabled(switchPath,true);check(ClientSettings.loadEnabled(switchPath),"feature on persists");
        Files.writeString(switchPath,"{}");check(ClientSettings.loadEnabled(switchPath),"missing switch field keeps default on");
        Files.writeString(switchPath,"{\"enabled\":\"false\"}");rejects(()->ClientSettings.loadEnabled(switchPath),"switch rejects a string boolean");
        Files.writeString(switchPath,"[]");rejects(()->ClientSettings.loadEnabled(switchPath),"switch rejects invalid settings shape");
        Path source=temp.resolve("Manual-Pack");Files.createDirectories(source.resolve("assets/minecraft/textures/item"));
        Files.write(source.resolve("assets/minecraft/textures/item/test.png"),custom);
        DirectorySource indexed=new DirectorySource(source);
        check(indexed.ids().size()==1,"manual index has only selected root");
        check(!indexed.mainCompatible(),"raw assets have no format declaration");
        check(Arrays.equals(indexed.read(AssetId.of("textures/item/test.png")),custom),"manual source bytes");
        check(indexed.unchanged(),"fresh index is unchanged");
        Files.write(source.resolve("assets/minecraft/textures/item/added.png"),original);
        check(!indexed.unchanged(),"new resource is detected from registered directory stamps");
        check(!new DirectorySource(source).fingerprint().equals(indexed.fingerprint()),"index revision changes after a new file");
        MixerConfig config=new MixerConfig();String id=UUID.randomUUID().toString();
        config.sources.put(id,new MixerConfig.SourceRecord(id,source.toString(),"Manual-Pack",""));config.candidateSources.add(id);
        config.itemOverrides.put("minecraft:test",MixerConfig.VANILLA);config.validate();
        ConfigStore store=new ConfigStore(temp.resolve("config"));store.save(config,true);
        check(store.load().itemOverrides.containsKey("minecraft:test"),"explicit vanilla survives persistence");
        check(!config.copy().renderSignature().isBlank(),"draft copies are independent");
        verifyZipSources(temp,custom);
        verifyLegacyModels(original,custom);
        checks += PackCompatibilityRegression.verifyFixtures();
        checks += LibraryRegression.run();
        checks += ResourceRegression.run();
        checks += LibraryScanRegression.run();
        verifySourceRoles();
        verifyGraphs(vanilla,original,custom);
        verifyAnimations();
        Files.writeString(source.resolve("pack.mcmeta"),"{\"pack\":{\"min_format\":[75,1],\"max_format\":[75,2],\"description\":\"future minor\"}}");
        check(!new DirectorySource(source).mainCompatible(),"75.1 pack is incompatible with target 75.0");
        Files.writeString(source.resolve("pack.mcmeta"),"{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,1],\"description\":\"compatible\"},\"overlays\":{\"entries\":[{\"directory\":\"active\",\"min_format\":[75,0],\"max_format\":[75,0]},{\"directory\":\"future\",\"min_format\":[75,1],\"max_format\":[75,2]}]}}");
        Files.createDirectories(source.resolve("active/assets/minecraft/textures/item"));Files.write(source.resolve("active/assets/minecraft/textures/item/test.png"),original);
        Files.createDirectories(source.resolve("future/assets/minecraft/textures/item"));Files.write(source.resolve("future/assets/minecraft/textures/item/test.png"),custom);
        var overlaid=new DirectorySource(source);
        check(overlaid.mainCompatible()&&Arrays.equals(overlaid.read(AssetId.of("textures/item/test.png")),original),"only target-version overlays activate");
        Files.writeString(source.resolve("pack.mcmeta"),"{\"pack\":{\"min_format\":[74,1000001],\"max_format\":[75,0]}}");
        check(new DirectorySource(source).mainCompatible(),"pack versions compare lexicographically without minor overflow");
        Files.writeString(source.resolve("pack.mcmeta"),"{\"pack\":{\"min_format\":[75.5,0],\"max_format\":[76,0]}}");
        rejects(()->new DirectorySource(source),"fractional pack version");
        Files.writeString(temp.resolve("config/config.json"),"broken JSON");
        check(store.load().itemOverrides.containsKey("minecraft:test"),"last-good recovery");
        var skySource=memory(Map.of("optifine/sky/world0/sky0.properties",text("source=./sky0.png\nstartFadeIn=18:00\nendFadeIn=19:00\nstartFadeOut=05:00\nendFadeOut=06:00\nweather=clear rain\nheights=(-64)-320\naxis=0 1 0\n"),
                "optifine/sky/world0/sky0.png",png(48,32,0xff102080)));
        var sky=new OptifineSkyAdapter().parse("overworld",new Assets.View(skySource,new Assets.Memory(Map.of())));
        check(sky.fromSource()&&sky.layers().size()==1,"custom sky parsed");
        var layer=sky.layers().get(0);
        check(layer.alpha(new SkyScene.Context(18000,0,0,0,"minecraft:plains",80))==1,"night sky visible");
        check(layer.alpha(new SkyScene.Context(6000,0,0,0,"minecraft:plains",80))==0,"day sky hidden");
        check(layer.condition(new SkyScene.Context(18000,0,0,0,"minecraft:plains",-65))==0,"negative height range");
        check(Math.abs(layer.rotation(new SkyScene.Context(23999,0,0,0,"minecraft:plains",80))-layer.rotation(new SkyScene.Context(0,1,0,0,"minecraft:plains",80)))<0.001,"rotation continuous across midnight");
        check(Math.abs(SkyScene.blend(SkyScene.Blend.ALPHA,.2,.8,.5,1)-.5)<1e-9,"alpha blend reference");
        var partialSky=new TreeMap<AssetId,byte[]>(skySource.files());
        partialSky.put(AssetId.of("optifine/sky/world0/sky1.properties"),text("source=./missing.png"));
        partialSky.put(AssetId.of("optifine/sky/world0/sky2.properties"),text("source=./sky0.png"));
        var partialScene=new OptifineSkyAdapter().parse("overworld",new Assets.View(new Assets.Memory(partialSky),empty));
        check(partialScene.layers().size()==2,"missing optional sky image preserves later valid layers");
        check(partialScene.diagnostics().stream().anyMatch(note->note.contains("missing.png")),"missing optional sky image produces visible diagnostic");
        var sparseSky=new TreeMap<AssetId,byte[]>();
        sparseSky.put(AssetId.of("optifine/sky/world0/sky2.properties"),text("source=sky.png"));
        sparseSky.put(AssetId.of("optifine/sky/world0/sky7.properties"),text("source=./sky.png"));
        sparseSky.put(AssetId.of("optifine/sky/world0/aurora.properties"),text("source=./sky.png"));
        sparseSky.put(AssetId.of("optifine/sky/world0/sky.png"),png(48,32,0xff00aaff));
        var sparseScene=new OptifineSkyAdapter().parse("overworld",new Assets.View(new Assets.Memory(sparseSky),empty));
        check(sparseScene.layers().size()==3,"sky numbering gaps and named definitions retain valid layers");
        sparseSky.put(new AssetId("fabricskyboxes","sky/overworld/skies.json"),text("{}"));
        check(new OptifineSkyAdapter().parse("overworld",new Assets.View(new Assets.Memory(sparseSky),empty)).layers().size()==3,"OptiFine sky remains usable in a dual-format pack");
        check(OptifineSkyAdapter.resolve(AssetId.of("optifine/sky/world0/sky2.properties"),"sky.png").equals(AssetId.of("optifine/sky/world0/sky.png")),"bare sky image is relative to its properties");
        var solar=memory(Map.of("optifine/sky/world0/sun.properties",text("source=./sun.png\nblend=multiply\n"),"optifine/sky/world0/sun.png",original,
                "optifine/sky/world0/moon_phases.properties",text("source=./moon.png\nblend=alpha\n"),"optifine/sky/world0/moon.png",png(64,32,0xff80c0ff)));
        var solarScene=new OptifineSkyAdapter().parse("overworld",new Assets.View(solar,empty));
        check(solarScene.celestialBlends().get("sun")==SkyScene.Blend.MULTIPLY&&solarScene.celestialBlends().get("moon_phases")==SkyScene.Blend.ALPHA,"celestial blend overrides are retained");
        check(solarScene.textures().keySet().stream().filter(file->file.path().contains("celestial/moon/")).count()==8,"legacy moon atlas becomes eight native phases");
        try {OptifineSkyAdapter.resolve(AssetId.of("optifine/sky/world0/sky0.properties"),"../../outside.png");throw new AssertionError("escape accepted");}
        catch(IOException expected){checks++;}
        if(Boolean.getBoolean("texturemixer.testVanilla")) verifyVanilla();
        System.out.println("TextureMixer regression: "+checks+" behavior checks passed. Temporary fixtures: "+temp);
    }
    private static void verifySourceRoles()throws Exception {
        MixerConfig roles=new MixerConfig();List<String> ids=new ArrayList<>();
        for(int i=0;i<66;i++){String id=UUID.randomUUID().toString();ids.add(id);roles.sources.put(id,new MixerConfig.SourceRecord(id,"test/"+i,"pack-"+i,""));}
        roles.selectMain(ids.get(0));for(int i=1;i<=64;i++)roles.addCandidate(ids.get(i));
        check(roles.candidateCount()==64,"64 candidates plus a separate main");roles.validate();
        roles.addCandidate(ids.get(65));
        check(roles.candidateCount()==65,"folder library admits more than64 candidates");
        roles.selectMain(MixerConfig.VANILLA);
        check(roles.candidateCount()==66,"former main remains registered when selecting vanilla");
        roles.selectMain(ids.get(1));
        check(roles.candidateCount()==65&&roles.candidateSources.contains(ids.get(0))&&!roles.candidateSources.contains(ids.get(1)),"main exchange preserves former main as candidate");
        roles.addCandidate(ids.get(1));check(roles.candidateCount()==65,"adding main as candidate is idempotent");
        roles.validate();
    }
    private static void verifyGraphs(Assets.Memory vanilla,byte[] original,byte[] custom)throws Exception {
        for(String node:List.of(
                "{\"type\":\"minecraft:condition\",\"on_true\":{\"type\":\"example:unknown\"},\"on_false\":{\"type\":\"minecraft:empty\"}}",
                "{\"type\":\"minecraft:composite\",\"models\":[{\"type\":\"minecraft:unknown\"}]}",
                "{\"type\":\"minecraft:select\",\"cases\":[{\"when\":\"x\",\"model\":{\"type\":\"example:unknown\"}}]}",
                "{\"type\":\"minecraft:range_dispatch\",\"entries\":[],\"fallback\":{\"type\":\"example:unknown\"}}")){
            var source=memory(Map.of("items/test.json",text("{\"model\":"+node+"}")));
            rejects(()->new GraphCompiler(new Assets.View(source,vanilla),node).item(AssetId.of("test"),null),"unsupported nested model");
        }
        Map<AssetId,byte[]> base=new TreeMap<>(vanilla.files());
        base.put(AssetId.of("blockstates/test.json"),text("{\"variants\":{\"lit=true\":{\"model\":\"item/test\"},\"lit=false\":{\"model\":\"item/test\"}}}"));
        var partial=memory(Map.of("blockstates/test.json",text("{\"variants\":{\"lit=true\":{\"model\":\"item/blue\"}}}"),
                "models/item/blue.json",text("{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"item/blue\"}}"),"textures/item/blue.png",custom));
        var states=List.of(Map.of("lit","true"),Map.of("lit","false"));
        var block=new GraphCompiler(new Assets.View(partial,new Assets.Memory(base)),"partial-block").block(AssetId.of("test"),states);
        String stateJson=new String(block.resources().get(AssetId.of("blockstates/test.json")),java.nio.charset.StandardCharsets.UTF_8);
        check(stateJson.contains("item/blue")&&stateJson.contains("item/test")&&stateJson.contains("false"),"partial variants include vanilla fallback for uncovered states");
        check(Arrays.equals(block.resources().get(new AssetId(block.namespacePrefix()+"minecraft","textures/item/test.png")),original),"partial block fallback retains vanilla bytes");
        var duplicate=memory(Map.of("blockstates/test.json",text("{\"multipart\":[{\"when\":{\"lit\":\"true\"},\"apply\":{\"model\":\"item/test\"}},{\"when\":{\"lit\":\"true\"},\"apply\":{\"model\":\"item/test\"}}]}")));
        var duplicateBlock=new GraphCompiler(new Assets.View(duplicate,new Assets.Memory(base)),"duplicates").block(AssetId.of("test"),states);
        var parts=JsonParser.parseString(new String(duplicateBlock.resources().get(AssetId.of("blockstates/test.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("multipart");
        check(parts.size()==2&&parts.get(0).getAsJsonObject().has("when")&&parts.get(1).getAsJsonObject().has("when"),"duplicate multipart draws retain each state condition");
        for(String entry:List.of("{\"type\":\"minecraft:single\",\"resource\":\"renamed/test\",\"sprite\":\"item/test\"}",
                "{\"type\":\"minecraft:directory\",\"source\":\"renamed\",\"prefix\":\"item/\"}")){
            var alias=memory(Map.of("atlases/items.json",text("{\"sources\":["+entry+"]}"),"textures/renamed/test.png",custom));
            var graph=new GraphCompiler(new Assets.View(alias,vanilla),entry).item(AssetId.of("test"),null);
            check(Arrays.equals(graph.resources().get(new AssetId(graph.namespacePrefix()+"minecraft","textures/renamed/test.png")),custom),"atlas alias resolves selected source texture");
        }
        base.put(AssetId.of("models/item/test.json"),text("{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"trim_gold\"}}"));
        base.put(AssetId.of("atlases/items.json"),text("{\"sources\":[{\"type\":\"minecraft:paletted_permutations\",\"textures\":[\"trim\"],\"palette_key\":\"key\",\"permutations\":{\"gold\":\"gold\"}}]}"));
        for(String name:List.of("trim","key","gold"))base.put(AssetId.of("textures/"+name+".png"),original);
        var palettes=new GraphCompiler(new Assets.View(memory(Map.of("textures/gold.png",custom)),new Assets.Memory(base)),"palettes").item(AssetId.of("test"),null);
        check(palettes.resources().containsKey(AssetId.of("atlases/items.json"))&&Arrays.equals(palettes.resources().get(new AssetId(palettes.namespacePrefix()+"minecraft","textures/gold.png")),custom),"inherited palette definition uses selected-source colors");
        for(String model:List.of("{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"#layer1\",\"layer1\":\"#layer0\"}}",
                "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"up\":{\"texture\":\"#undefined\"}}}]}")){
            var invalid=memory(Map.of("models/item/test.json",text(model)));
            rejects(()->new GraphCompiler(new Assets.View(invalid,vanilla),"bindings").item(AssetId.of("test"),null),"invalid texture binding");
        }
    }
    private static void verifyAnimations()throws Exception {
        AssetId id=AssetId.of("textures/animated.png");byte[] image=png(16,32,0xff0077ff);
        TextureMetadata.validate(id,image,JsonParser.parseString("{\"animation\":{\"frames\":[0,{\"index\":1,\"time\":3}],\"frametime\":2}}").getAsJsonObject());
        check(true,"valid animation frame metadata");
        for(String metadata:List.of("{\"animation\":{\"frames\":[2]}}","{\"animation\":{\"frames\":[-1]}}","{\"animation\":{\"frametime\":0}}",
                "{\"animation\":{\"width\":3}}","{\"animation\":{\"frames\":[{\"index\":1,\"time\":0}]}}","{\"animation\":{\"frames\":[0.5]}}"))
            rejects(()->TextureMetadata.validate(id,image,JsonParser.parseString(metadata).getAsJsonObject()),"invalid animation metadata");
    }
    private static void verifyVanilla() throws Exception {
        Map<AssetId,byte[]> files=new TreeMap<>();
        for(String entry:System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path path=Path.of(entry);if(!entry.endsWith(".jar"))continue;
            try(ZipFile zip=new ZipFile(path.toFile())) {
                if(zip.getEntry("assets/minecraft/models/item/diamond_sword.json")==null)continue;
                var entries=zip.entries();while(entries.hasMoreElements()) {
                    var f=entries.nextElement();if(f.isDirectory()||!f.getName().startsWith("assets/minecraft/"))continue;
                    files.put(new AssetId("minecraft",f.getName().substring(17)),zip.getInputStream(f).readAllBytes());
                }
                break;
            }
        }
        check(!files.isEmpty(),"vanilla jar located");
        var vanilla=new Assets.Memory(files);var view=new Assets.View(null,vanilla);int items=0,blocks=0;
        GraphCompiler.useSeparateItemAtlas(vanilla.contains(AssetId.of("atlases/items.json")));
        boolean legacy=vanilla.read(AssetId.of("items/diamond_sword.json"))==null;
        for(AssetId file:files.keySet()) {
            if(file.path().startsWith("items/")&&file.path().endsWith(".json")) {
                AssetId item=file.withPath(file.path().substring(6,file.path().length()-5));
                new GraphCompiler(view,"vanilla|item|"+item).item(item,null);items++;
            }
            if(file.path().startsWith("blockstates/")&&file.path().endsWith(".json")) {
                AssetId block=file.withPath(file.path().substring(12,file.path().length()-5));
                new GraphCompiler(view,"vanilla|block|"+block).block(block);blocks++;
            }
        }
        if(legacy){
            for(String item:List.of("diamond_sword","bow","crossbow","fishing_rod","shield","trident_in_hand","spyglass_in_hand")){
                var graph=new GraphCompiler(view,"legacy-vanilla|"+item).legacyItem(AssetId.of(item),null,false);
                check(graph.resources().containsKey(AssetId.of("models/item/"+item+".json")),"legacy vanilla item model "+item);
                items++;
            }
            check(blocks>1000,"complete legacy vanilla block graph coverage");
            System.out.println("Legacy vanilla graph verification: "+items+" selected item models, "+blocks+" block definitions");
            return;
        }
        for(var audit:Map.of("crossbow",List.of("crossbow_standby","crossbow_arrow","crossbow_firework","crossbow_pulling_0","crossbow_pulling_1","crossbow_pulling_2"),
                "bow",List.of("bow","bow_pulling_0","bow_pulling_1","bow_pulling_2"),"fishing_rod",List.of("fishing_rod","fishing_rod_cast")).entrySet()){
            var graph=new GraphCompiler(view,"state-audit|"+audit.getKey()).item(AssetId.of(audit.getKey()),null);
            check(audit.getValue().stream().allMatch(name->graph.dependencies().contains(AssetId.of("textures/item/"+name+".png"))),audit.getKey()+" includes every native texture state");
        }
        var shield=AssetId.of("shield");
        var shieldPreview=new GraphCompiler(view,"shield-preview").itemPreview(shield,null,false);
        var shieldFull=new GraphCompiler(view,"shield-full").item(shield,null);
        check(shieldPreview.dependencies().contains(AssetId.of("textures/entity/shield_base_nopattern.png")),"plain Shield preview includes the base model texture");
        check(shieldPreview.dependencies().stream().noneMatch(id->id.path().startsWith("textures/entity/shield/")),"plain Shield preview skips patterned layers");
        check(shieldFull.dependencies().stream().anyMatch(id->id.path().startsWith("textures/entity/shield/")),"applied Shield retains patterned layers");
        check(items>1300&&blocks>1000,"complete vanilla resource graph coverage");
        System.out.println("Vanilla graph verification: "+items+" item definitions, "+blocks+" block definitions");
    }
}
