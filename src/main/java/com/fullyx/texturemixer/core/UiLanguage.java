package com.fullyx.texturemixer.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** TextureMixer locale is independent of Minecraft's global language and resource-pack stack. */
public final class UiLanguage {
    private static final Map<String,Map<String,String>> UI=Map.of("zh_cn",read("zh_cn.json"),"en_us",read("en_us.json"));
    private record GameNames(Map<String,String> english,Map<String,String> chinese){}
    private static volatile GameNames gameNames=new GameNames(Map.of(),Map.of());
    private static volatile String language="zh_cn";
    private UiLanguage(){}
    public static String language(){return language;}
    public static void select(String selected){
        if(!UI.containsKey(selected))throw new IllegalArgumentException("Unsupported TextureMixer language");
        language=selected;
    }
    public static String tr(String key){return UI.get(language).getOrDefault(key,key);}
    public static void loadGameNames(JsonObject english,byte[] chinese){
        JsonObject translations=chinese==null?new JsonObject():JsonParser.parseString(new String(chinese,StandardCharsets.UTF_8)).getAsJsonObject();
        gameNames=new GameNames(values(english),values(translations));
    }
    public static String englishGameName(String key,String fallback){return gameNames.english().getOrDefault(key,fallback);}
    public static String chineseLabel(String key){return UI.get("zh_cn").getOrDefault(key,key);}
    public static String searchNames(String key,String fallback){
        GameNames names=gameNames;
        return names.chinese().getOrDefault(key,fallback)+" "+names.english().getOrDefault(key,fallback);
    }
    private static Map<String,String> read(String file){
        try(InputStream input=UiLanguage.class.getResourceAsStream("/assets/texturemixer/i18n/"+file)){
            if(input==null)throw new IOException("Missing locale "+file);
            JsonObject json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            return values(json);
        }catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
    private static Map<String,String> values(JsonObject json){
        Map<String,String> values=new HashMap<>();
        json.entrySet().forEach(entry->{
            if(entry.getValue().isJsonPrimitive()&&entry.getValue().getAsJsonPrimitive().isString())
                values.put(entry.getKey(),entry.getValue().getAsString());
        });
        return Map.copyOf(values);
    }
}
