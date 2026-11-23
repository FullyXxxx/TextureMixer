package com.fullyx.texturemixer.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/** Preview-only stacks for enchantment predicates declared by native item definitions. */
public record ItemPreviewState(List<String> enchantments) {
    public static final ItemPreviewState NORMAL=new ItemPreviewState(List.of());
    public ItemPreviewState { enchantments=List.copyOf(enchantments); }
    public String label(){
        if(enchantments.isEmpty())return com.fullyx.texturemixer.core.UiLanguage.tr("Normal");
        return String.join(" + ",enchantments.stream().map(id->{
            String name=Identifier.parse(id).getPath().replace('_',' ');
            return com.fullyx.texturemixer.core.UiLanguage.englishGameName("enchantment.minecraft."+Identifier.parse(id).getPath(),Character.toUpperCase(name.charAt(0))+name.substring(1));
        }).toList());
    }
    public ItemStack apply(ItemStack original,ClientLevel world){
        ItemStack stack=original.copy();
        if(world!=null){
            var registry=world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for(String id:enchantments)registry.get(ResourceKey.create(Registries.ENCHANTMENT,Identifier.parse(id)))
                    .ifPresent(enchantment->stack.enchant(enchantment,1));
        }
        return stack;
    }
    public static List<ItemPreviewState> fromDefinition(JsonObject definition){
        Set<ItemPreviewState> states=new LinkedHashSet<>();states.add(NORMAL);
        collect(definition,states);
        return List.copyOf(states);
    }
    private static void collect(JsonElement value,Set<ItemPreviewState> states){
        if(value.isJsonArray()){
            for(JsonElement child:value.getAsJsonArray())collect(child,states);
        }else if(value.isJsonObject()){
            JsonObject object=value.getAsJsonObject();
            if(object.has("predicate")&&object.get("predicate").isJsonPrimitive()
                    &&object.get("predicate").getAsString().equals("minecraft:enchantments")
                    &&object.has("value")&&object.get("value").isJsonArray()){
                List<String> enchantments=new ArrayList<>();
                for(JsonElement entry:object.getAsJsonArray("value")){
                    if(entry.isJsonObject()&&entry.getAsJsonObject().has("enchantments")){
                        JsonElement ids=entry.getAsJsonObject().get("enchantments");
                        if(ids.isJsonPrimitive()&&!ids.getAsString().startsWith("#"))enchantments.add(Identifier.parse(ids.getAsString()).toString());
                    }
                }
                if(!enchantments.isEmpty())states.add(new ItemPreviewState(enchantments));
            }
            for(JsonElement child:object.asMap().values())collect(child,states);
        }
    }
}
