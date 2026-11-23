package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import net.minecraft.registry.Registries;
import net.minecraft.item.*;
import net.minecraft.block.*;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;

public final class Catalog {
    public record Target(MixerConfig.Kind kind,String id,String name,ItemStack stack,BlockState blockState,
                         AssetId model,AssetId equipment){
        public boolean matches(String query){
            String translated=kind==MixerConfig.Kind.ITEM?UiLanguage.searchNames(stack.getItem().getTranslationKey(),name)
                    :kind==MixerConfig.Kind.BLOCK?UiLanguage.searchNames(blockState.getBlock().getTranslationKey(),name):UiLanguage.chineseLabel(name);
            return SearchQuery.matches(name+" "+translated+" "+id,query);
        }
        public List<Map<String,String>> states(){
            if(blockState==null)return List.of();List<Map<String,String>> result=new ArrayList<>();
            for(BlockState state:blockState.getBlock().getStateManager().getStates()){
                Map<String,String> values=new TreeMap<>();state.getEntries().forEach((property,value)->values.put(property.getName(),value.toString().toLowerCase(Locale.ROOT)));result.add(Map.copyOf(values));
            }return List.copyOf(result);
        }
    }
    public final List<Target> items,blocks,skies;
    private final EnumMap<MixerConfig.Kind,List<Target>> resourceLists=new EnumMap<>(MixerConfig.Kind.class);
    private final Map<String,Target> itemIndex=new HashMap<>(),blockIndex=new HashMap<>();
    public Catalog(Assets vanilla)throws IOException{
        JsonObject english=vanilla.json(AssetId.of("lang/en_us.json"));
        UiLanguage.loadGameNames(english,vanilla.read(AssetId.of("lang/zh_cn.json")));
        List<Target> itemList=new ArrayList<>(),blockList=new ArrayList<>();
        for(Item item:Registries.ITEM){Identifier id=Registries.ITEM.getId(item);if(!id.getNamespace().equals("minecraft")||item==Items.AIR)continue;
            ItemStack stack=item.getDefaultStack();
            Identifier model=id;
            AssetId asset=NativePackModels.equipment(stack);
            Target target=new Target(MixerConfig.Kind.ITEM,id.toString(),name(english,item.getTranslationKey(),id),stack,null,
                    model==null?AssetId.of(id.toString()):AssetId.of(model.toString()),asset);
            itemList.add(target);itemIndex.put(target.id,target);
        }
        for(Block block:Registries.BLOCK){Identifier id=Registries.BLOCK.getId(block);
            if(!id.getNamespace().equals("minecraft")||block==Blocks.AIR||block==Blocks.CAVE_AIR||block==Blocks.VOID_AIR)continue;
            Target target=new Target(MixerConfig.Kind.BLOCK,id.toString(),name(english,block.getTranslationKey(),id),block.asItem().getDefaultStack(),
                    block.getDefaultState(),AssetId.of(id.toString()),null);blockList.add(target);blockIndex.put(target.id,target);
        }
        Comparator<Target> sort=Comparator.comparing((Target t)->t.name.toLowerCase(Locale.ROOT)).thenComparing(Target::id);
        itemList.sort(sort);blockList.sort(sort);items=List.copyOf(itemList);blocks=List.copyOf(blockList);
        skies=List.of(new Target(MixerConfig.Kind.SKY,"overworld","Overworld Sky",ItemStack.EMPTY,null,null,null),
                new Target(MixerConfig.Kind.SKY,"end","End Sky",ItemStack.EMPTY,null,null,null),
                new Target(MixerConfig.Kind.SKY,"weather","Rain & Snow",ItemStack.EMPTY,null,null,null));
        for(MixerConfig.Kind kind:List.of(MixerConfig.Kind.ENTITY,MixerConfig.Kind.PARTICLE,MixerConfig.Kind.HUD,MixerConfig.Kind.SOUND,
                MixerConfig.Kind.ENCHANT,MixerConfig.Kind.MENU,MixerConfig.Kind.CONTAINER,MixerConfig.Kind.FONT)) {
            List<Target> list=new ArrayList<>();
            for(AssetId id:vanilla.ids()) {
                if(kind==MixerConfig.Kind.ENTITY&&ResourceCompiler.entityTexture(id)||kind==MixerConfig.Kind.HUD&&ResourceCompiler.hudTexture(id))
                    list.add(resourceTarget(kind,id.toString(),englishPath(id.path().replaceFirst("^textures/(entity/|gui/sprites/|misc/)", "").replaceFirst("\\.png$", ""))));
                if(kind==MixerConfig.Kind.PARTICLE&&id.path().startsWith("particles/")&&id.path().endsWith(".json")) {
                    String path=id.path().substring(10,id.path().length()-5);
                    list.add(resourceTarget(kind,new AssetId(id.namespace(),path).toString(),englishPath(path)));
                }
                if(kind==MixerConfig.Kind.SOUND&&id.path().equals("sounds.json"))
                    for(String event:vanilla.json(id).keySet())list.add(resourceTarget(kind,new AssetId(id.namespace(),event).toString(),englishPath(event.replace('.', '/'))));
                if(kind==MixerConfig.Kind.ENCHANT&&ResourceCompiler.enchantTexture(id))
                    list.add(resourceTarget(kind,id.toString(),id.path().contains("armor")?"Armor Glint":"Item Glint"));
                if(kind==MixerConfig.Kind.MENU&&ResourceCompiler.menuTexture(id)||kind==MixerConfig.Kind.CONTAINER&&ResourceCompiler.containerTexture(id)) {
                    String path=id.path().substring("textures/gui/".length()).replaceFirst("^sprites/","").replaceFirst("\\.png$","");
                    list.add(resourceTarget(kind,id.toString(),englishPath(path)));
                }
            }
            if(kind==MixerConfig.Kind.HUD)list.add(resourceTarget(kind,ResourceCompiler.FIRE_OVERLAY_TARGET,"Fire Overlay"));
            if(kind==MixerConfig.Kind.FONT)
                FontResources.TARGETS.forEach((id,label)->list.add(resourceTarget(kind,id,label)));
            list.sort(sort);resourceLists.put(kind,List.copyOf(list));
        }
    }
    private static Target resourceTarget(MixerConfig.Kind kind,String id,String name) {
        return new Target(kind,id,name,ItemStack.EMPTY,null,null,null);
    }
    private static String englishPath(String path) {
        List<String> segments=new ArrayList<>();
        for(String segment:path.split("/")) {
            if(segment.equals("hud")){segments.add("HUD");continue;}
            StringJoiner words=new StringJoiner(" ");
            for(String word:segment.split("_"))if(!word.isEmpty())words.add(Character.toUpperCase(word.charAt(0))+word.substring(1));
            segments.add(words.toString());
        }
        return String.join(" / ",segments);
    }
    private static String name(JsonObject english,String key,Identifier id){
        if(english.has(key))return english.get(key).getAsString();
        StringBuilder result=new StringBuilder();for(String word:id.getPath().split("_")){
            if(!result.isEmpty())result.append(' ');result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return result.toString();
    }
    public List<Target> list(MixerConfig.Kind kind){return switch(kind){case ITEM->items;case BLOCK->blocks;case SKY->skies;default->resourceLists.getOrDefault(kind,List.of());};}
    public Target item(String id){return itemIndex.get(id);}
    public Target block(String id){return blockIndex.get(id);}
}
