package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Client preferences are written separately from mixes and resource snapshots. */
public final class ClientSettings {
    private ClientSettings() {}
    public static boolean loadEnabled(Path path) throws IOException {
        if(!Files.exists(path))return true;
        try {
            JsonObject data=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(!data.has("enabled"))return true;
            JsonElement value=data.get("enabled");
            if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())
                throw new IOException(tr("TextureMixer 客户端开关必须为布尔值"));
            return value.getAsBoolean();
        }catch(RuntimeException error){throw new IOException(tr("无法读取 TextureMixer 客户端设置"),error);}
    }
    public static void saveEnabled(Path path,boolean enabled) throws IOException {
        save(path,"enabled",new JsonPrimitive(enabled));
    }
    public static String loadLanguage(Path path)throws IOException {
        return loadLanguage(path,"language","zh_cn");
    }
    private static String loadLanguage(Path path,String key,String fallback)throws IOException {
        JsonElement value=read(path).get(key);
        if(value==null)return fallback;
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IOException(tr("无法读取 TextureMixer 客户端设置"));
        return supportedLanguage(value.getAsString());
    }
    public static boolean loadPreload(Path path)throws IOException {
        JsonObject data=read(path);
        return !data.has("preload")||data.get("preload").getAsBoolean();
    }
    public static void saveLanguage(Path path,String language)throws IOException {
        save(path,"language",new JsonPrimitive(supportedLanguage(language)));
    }
    private static String supportedLanguage(String language)throws IOException {
        if(!"zh_cn".equals(language)&&!"en_us".equals(language))throw new IOException(tr("不支持的界面语言"));
        return language;
    }
    public static void savePreload(Path path,boolean enabled)throws IOException {save(path,"preload",new JsonPrimitive(enabled));}
    private static JsonObject read(Path path)throws IOException {
        if(!Files.exists(path))return new JsonObject();
        try{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
        catch(RuntimeException error){throw new IOException("Invalid TextureMixer client settings",error);}
    }
    private static synchronized void save(Path path,String key,JsonElement value)throws IOException {
        JsonObject data=read(path);data.add(key,value);ConfigStore.atomic(path,Assets.jsonBytes(data));
    }
}
