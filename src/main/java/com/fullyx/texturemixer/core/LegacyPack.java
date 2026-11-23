package com.fullyx.texturemixer.core;

import com.google.gson.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Virtual compatibility assets. The source and its fingerprint remain the original ZIP or directory. */
final class LegacyPack implements PackSource {
    static volatile boolean preserveOverrides;
    private record Crop(String sheet,int x,int y,int width,int height,int columns,int rows) {
        Crop(String sheet,int x,int y,int width,int height){this("gui/"+sheet,x,y,width,height,256,256);}
    }
    private static final Map<String,String> TEXTURES=Map.ofEntries(
            Map.entry("item/wood_sword","item/wooden_sword"),
            Map.entry("item/gold_sword","item/golden_sword"),
            Map.entry("item/totem","item/totem_of_undying"),
            Map.entry("item/bow_standby","item/bow"),
            Map.entry("item/fishing_rod_uncast","item/fishing_rod"),
            Map.entry("item/clock","item/clock_00"),
            Map.entry("item/compass","item/compass_16"),
            Map.entry("block/planks_oak","block/oak_planks"),
            Map.entry("block/log_oak","block/oak_log"),
            Map.entry("block/log_oak_top","block/oak_log_top"),
            Map.entry("block/grass_top","block/grass_block_top"),
            Map.entry("block/grass_side","block/grass_block_side"),
            Map.entry("block/grass_side_overlay","block/grass_block_side_overlay"),
            Map.entry("block/fire_layer_0","block/fire_0"),
            Map.entry("block/fire_layer_1","block/fire_1"),
            Map.entry("block/wool_colored_white","block/white_wool"),
            Map.entry("environment/sun","environment/celestial/sun"));
    private static final List<String> WOOD=List.of("oak","spruce","birch","jungle","acacia","dark_oak");
    private static final List<String> COLORS=List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
            "light_gray","cyan","purple","blue","brown","green","red","black");
    private static final Map<String,Crop> GUI=Map.ofEntries(
            Map.entry("hud/hotbar",new Crop("widgets",0,0,182,22)),
            Map.entry("hud/hotbar_selection",new Crop("widgets",0,22,24,23)),
            Map.entry("hud/hotbar_offhand_left",new Crop("widgets",24,22,29,24)),
            Map.entry("hud/hotbar_offhand_right",new Crop("widgets",53,22,29,24)),
            Map.entry("hud/crosshair",new Crop("icons",0,0,15,15)),
            Map.entry("hud/experience_bar_background",new Crop("icons",0,64,182,5)),
            Map.entry("hud/experience_bar_progress",new Crop("icons",0,69,182,5)),
            Map.entry("hud/heart/container",new Crop("icons",16,0,9,9)),
            Map.entry("hud/heart/full",new Crop("icons",52,0,9,9)),
            Map.entry("hud/heart/half",new Crop("icons",61,0,9,9)),
            Map.entry("hud/armor_empty",new Crop("icons",16,9,9,9)),
            Map.entry("hud/armor_half",new Crop("icons",25,9,9,9)),
            Map.entry("hud/armor_full",new Crop("icons",34,9,9,9)));
    private final PackSource source;
    private final Map<AssetId,AssetId> aliases=new TreeMap<>();
    private final Map<AssetId,Crop> crops=new TreeMap<>();
    private final Set<AssetId> ids;
    private final boolean convertModels;

    static PackSource adapt(PackSource source){
        LegacyPack adapted=new LegacyPack(source);
        return adapted.convertModels||!adapted.aliases.isEmpty()||!adapted.crops.isEmpty()?adapted:source;
    }
    private LegacyPack(PackSource source){
        this.source=source;
        convertModels=!source.mainCompatible();
        for(AssetId id:source.ids()){
            String path=id.path();
            if(path.startsWith("textures/")&&(path.endsWith(".png")||path.endsWith(".png.mcmeta"))){
                int end=path.length()-(path.endsWith(".mcmeta")?11:4);
                String texture=path.substring(9,end);
                String upgraded=texturePath(texture);
                if(!texture.equals(upgraded))alias(id.withPath("textures/"+upgraded+path.substring(end)),id);
                if(DirectorySource.usesStandaloneTrims()){
                    String trim=standaloneTrimPath(texture);
                    if(trim!=null)alias(id.withPath("textures/"+trim+path.substring(end)),id);
                }
                if(texture.equals("misc/enchanted_item_glint")){
                    alias(id.withPath("textures/misc/enchanted_glint_item"+path.substring(end)),id);
                    alias(id.withPath("textures/misc/enchanted_glint_armor"+path.substring(end)),id);
                }
            }
        }
        GUI.forEach((name,crop)->{
            AssetId id=AssetId.of("textures/gui/sprites/"+name+".png");
            if(!source.contains(id)&&source.contains(sheet(crop)))crops.put(id,crop);
        });
        List<String> moonPhases=List.of("full_moon","waning_gibbous","third_quarter","waning_crescent",
                "new_moon","waxing_crescent","first_quarter","waxing_gibbous");
        if(source.contains(AssetId.of("textures/environment/moon_phases.png"))){
            for(int index=0;index<moonPhases.size();index++){
                AssetId id=AssetId.of("textures/environment/celestial/moon/"+moonPhases.get(index)+".png");
                if(!source.contains(id))crops.put(id,new Crop("environment/moon_phases",index%4,index/4,1,1,4,2));
            }
        }
        Set<AssetId> all=new TreeSet<>(source.ids());all.addAll(aliases.keySet());all.addAll(crops.keySet());
        ids=Collections.unmodifiableSet(all);
    }
    private void alias(AssetId target,AssetId original){
        if(!source.contains(target)){
            // A rename in the current directory takes precedence over the old plural layout.
            if(!aliases.containsKey(target)||!original.path().startsWith("textures/items/")&&!original.path().startsWith("textures/blocks/"))
                aliases.put(target,original);
        }
    }
    private static AssetId sheet(Crop crop){return AssetId.of("textures/"+crop.sheet()+".png");}
    private static String standaloneTrimPath(String texture){
        String armor="trims/models/armor/";
        String palette="trims/color_palettes/";
        if(texture.startsWith(armor)){
            String pattern=texture.substring(armor.length());
            String leggings="_leggings";
            if(pattern.endsWith(leggings))
                return "trims/entity/humanoid_leggings/"+pattern.substring(0,pattern.length()-leggings.length());
            return "trims/entity/humanoid/"+pattern;
        }
        if(texture.startsWith(palette)){
            String color=texture.substring(palette.length());
            return color.equals("trim_palette")?"palettes/trim_base":"palettes/trim/"+color;
        }
        return null;
    }
    private static String texturePath(String value){
        String path=value.replaceFirst("^items/","item/").replaceFirst("^blocks/","block/");
        String renamed=TEXTURES.get(path);
        if(renamed!=null)return renamed;
        for(String wood:WOOD){
            if(path.equals("block/planks_"+wood))return "block/"+wood+"_planks";
            if(path.equals("block/log_"+wood))return "block/"+wood+"_log";
            if(path.equals("block/log_"+wood+"_top"))return "block/"+wood+"_log_top";
        }
        for(String color:COLORS){
            String old=color.equals("light_gray")?"silver":color;
            if(path.equals("block/wool_colored_"+old))return "block/"+color+"_wool";
            if(path.equals("block/glass_"+old))return "block/"+color+"_stained_glass";
        }
        if(path.startsWith("item/")){
            String name=path.substring(5);
            for(String prefix:List.of("wood_","gold_"))
                if(name.startsWith(prefix)&&Set.of("sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots").contains(name.substring(prefix.length())))
                    return "item/"+(prefix.equals("wood_")?"wooden_":"golden_")+name.substring(prefix.length());
        }
        return path;
    }
    @Override public byte[] read(AssetId id)throws IOException{
        return read(id,source);
    }
    private byte[] read(AssetId id,Assets originals)throws IOException {
        Crop crop=crops.get(id);
        if(crop!=null){
            byte[] bytes=originals.read(sheet(crop));
            DirectorySource.ImageSize size=DirectorySource.pngSize(sheet(crop),bytes,false);
            if(size.width()%crop.columns()!=0||size.height()%crop.rows()!=0)return null;
            int scale=size.width()/crop.columns();
            if(scale!=size.height()/crop.rows())return null;
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
            var output=new ByteArrayOutputStream();
            ImageIO.write(image.getSubimage(crop.x()*scale,crop.y()*scale,crop.width()*scale,crop.height()*scale),"PNG",output);
            return output.toByteArray();
        }
        byte[] bytes=originals.read(aliases.getOrDefault(id,id));
        if(bytes==null||!id.path().startsWith("models/")||!id.path().endsWith(".json")||!convertModels)return bytes;
        JsonObject model=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        boolean changed=!preserveOverrides&&model.remove("overrides")!=null;
        JsonObject textures=model.getAsJsonObject("textures");
        if(textures!=null)for(var texture:textures.entrySet()){
            if(!texture.getValue().isJsonPrimitive()||!texture.getValue().getAsJsonPrimitive().isString())continue;
            String value=texture.getValue().getAsString();
            if(value.startsWith("#"))continue;
            AssetId ref=AssetId.of(value);
            String upgraded=texturePath(ref.path());
            if(!upgraded.equals(ref.path())){
                texture.setValue(new JsonPrimitive(ref.withPath(upgraded).toString()));
                changed=true;
            }
        }
        return changed?Assets.jsonBytes(model):bytes;
    }
    @Override public Assets.Memory snapshot()throws IOException {
        Assets originals=source.snapshot();
        Map<AssetId,byte[]> result=new TreeMap<>();
        long total=0;
        for(AssetId id:ids){
            byte[] bytes=read(id,originals);
            if(bytes==null)continue;
            total+=bytes.length;
            if(total>512L*1024*1024)throw new IOException(UiLanguage.tr("主材质包叠加超过 512 MiB 快照预算"));
            result.put(id,bytes);
        }
        return new Assets.Memory(result);
    }
    @Override public Set<AssetId> ids(){return ids;}
    @Override public Path root(){return source.root();}
    @Override public String name(){return source.name();}
    @Override public boolean mainCompatible(){return source.mainCompatible();}
    @Override public long indexedBytes(){return source.indexedBytes();}
    @Override public String fingerprint(){return source.fingerprint();}
    @Override public boolean unchanged()throws IOException{return source.unchanged();}
}
