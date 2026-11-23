package com.fullyx.texturemixer;

import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;

/** Resource isolation and inheritance regressions for the expanded categories. */
public final class ResourceRegression {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static byte[] text(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static byte[] png(int color)throws Exception {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,color);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"PNG",output);return output.toByteArray();
    }
    private static Assets.Memory assets(Map<String,byte[]> files){Map<AssetId,byte[]> result=new TreeMap<>();files.forEach((id,bytes)->result.put(AssetId.of(id),bytes));return new Assets.Memory(result);}
    public static int run()throws Exception {
        byte[] original=png(0xff33aaaa),custom=png(0xffee1177),ogg=text("OggSfixture");
        Assets vanilla=assets(Map.ofEntries(
            Map.entry("textures/entity/test.png",original),Map.entry("textures/gui/sprites/hud/crosshair.png",original),
            Map.entry("textures/particle/flame.png",original),Map.entry("particles/flame.json",text("{\"textures\":[\"minecraft:flame\"]}")),
            Map.entry("textures/environment/rain.png",original),Map.entry("textures/environment/snow.png",original),
            Map.entry("sounds.json",text("{\"entity.test.ambient\":{\"sounds\":[\"base\"]},\"nested\":{\"sounds\":[{\"name\":\"entity.test.ambient\",\"type\":\"event\"}]}}")),
            Map.entry("sounds/base.ogg",ogg)));
        Assets source=assets(Map.of("textures/entity/test.png",custom,"textures/particle/flame.png",custom,
            "sounds.json",text("{\"entity.test.ambient\":{\"replace\":true,\"sounds\":[\"custom\"]}}"),"sounds/custom.ogg",ogg));
        var view=new Assets.View(source,vanilla);
        var entity=new ResourceCompiler(view,"entity").compile(MixerConfig.Kind.ENTITY,"minecraft:textures/entity/test.png");
        check(entity.custom()&&Arrays.equals(entity.files().get(AssetId.of("textures/entity/test.png")),custom),"selected entity texture replaces native path");
        check(entity.files().containsKey(AssetId.of("textures/entity/test.png.mcmeta")),"explicit texture clears stale inherited metadata");
        var hud=new ResourceCompiler(view,"hud").compile(MixerConfig.Kind.HUD,"minecraft:textures/gui/sprites/hud/crosshair.png");
        check(!hud.custom()&&Arrays.equals(hud.files().get(AssetId.of("textures/gui/sprites/hud/crosshair.png")),original),"missing HUD texture falls back to vanilla");
        var particle=new ResourceCompiler(view,"particle").compile(MixerConfig.Kind.PARTICLE,"minecraft:flame");
        String mapped=JsonParser.parseString(new String(particle.files().get(AssetId.of("particles/flame.json")),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("textures").get(0).getAsString();
        check(particle.custom()&&mapped.startsWith("texturemixer_"),"particle sprite names are isolated");
        check(particle.files().containsKey(AssetId.of("atlases/particles.json"))&&Arrays.equals(particle.files().get(particle.images().get(0)),custom),"particle closure carries selected texture and atlas");
        var fallback=new ResourceCompiler(new Assets.View(null,vanilla),"fallback").compile(MixerConfig.Kind.PARTICLE,"minecraft:flame");
        check(!fallback.custom()&&!particle.images().equals(fallback.images()),"independent particle sources receive distinct namespaces");
        var weather=new ResourceCompiler(new Assets.View(null,vanilla),"weather").compile(MixerConfig.Kind.SKY,"weather");
        check(weather.images().size()==2&&weather.files().containsKey(AssetId.of("textures/environment/snow.png")),"weather includes rain and snow");
        var sound=new ResourceCompiler(view,"sound").compile(MixerConfig.Kind.SOUND,"minecraft:nested");
        check(sound.custom()&&sound.sounds().size()==1,"nested sound event resolves selected dependency");
        JsonObject manifest=JsonParser.parseString(new String(sound.files().get(AssetId.of("sounds.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        check(manifest.getAsJsonObject("nested").get("replace").getAsBoolean(),"explicit sound event replaces inherited candidates");
        check(sound.sounds().get(0).namespace().startsWith("texturemixer_")&&sound.files().containsKey(sound.sounds().get(0)),"audio files are isolated and included");
        check(new ResourceCompiler(view,"sample").sample("minecraft:nested").file().equals(AssetId.of("sounds/custom.ogg")),"audition resolves nested selected sound without global reload");
        JsonObject low=JsonParser.parseString("{\"sounds\":[\"a\"],\"replace\":true}").getAsJsonObject(),high=JsonParser.parseString("{\"sounds\":[\"b\"]}").getAsJsonObject();
        check(ResourceCompiler.combineSoundEvent(low,high).getAsJsonArray("sounds").size()==2&&ResourceCompiler.combineSoundEvent(low,high).get("replace").getAsBoolean(),"sound stacking appends and preserves replacement boundary");
        high.addProperty("replace",true);
        check(ResourceCompiler.combineSoundEvent(low,high).getAsJsonArray("sounds").size()==1,"upper replacing sound event discards lower choices");
        Assets cycle=assets(Map.of("sounds.json",text("{\"loop\":{\"sounds\":[{\"name\":\"loop\",\"type\":\"event\"}]}}")));
        boolean rejected=false;try{new ResourceCompiler(new Assets.View(cycle,vanilla),"cycle").compile(MixerConfig.Kind.SOUND,"minecraft:loop");}catch(IOException expected){rejected=true;}
        check(rejected,"cyclic sound event graph rejects before Apply");
        MixerConfig legacy=MixerConfig.GSON.fromJson("{}",MixerConfig.class);legacy.validate();
        check(legacy.entityOverrides.isEmpty()&&legacy.soundOverrides.isEmpty(),"older configs receive empty new category maps");
        return checks;
    }
}
