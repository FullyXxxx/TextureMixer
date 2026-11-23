package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.item.ItemAsset;
import net.minecraft.client.network.ClientDynamicRegistryType;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.model.json.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.sound.SoundEntry;
import net.minecraft.client.sound.SoundEntryDeserializer;
import net.minecraft.client.sound.OggAudioStream;
/** Run the actual target-version codecs before asking Minecraft to reload; it otherwise logs and skips bad assets. */
public final class NativeAssetValidator {
    public static void validate(Map<AssetId,byte[]> files)throws IOException{
        var registries=ClientDynamicRegistryType.createCombinedDynamicRegistries().getCombinedRegistryManager();
        for(var entry:files.entrySet()){
            AssetId file=entry.getKey();String path=file.path();byte[] bytes=entry.getValue();
            try{
                if(path.startsWith("sounds/")&&path.endsWith(".ogg")){
                    try(OggAudioStream stream=new OggAudioStream(new ByteArrayInputStream(bytes))){stream.getFormat();}
                    continue;
                }
                if(path.endsWith(".png")){
                    DirectorySource.validatePng(file,bytes);byte[] metadata=files.get(file.withPath(path+".mcmeta"));
                    if(metadata!=null)TextureMetadata.validate(file,bytes,JsonParser.parseString(new String(metadata,StandardCharsets.UTF_8)).getAsJsonObject());
                    continue;
                }
                if(!path.endsWith(".json")&&!path.endsWith(".mcmeta"))continue;
                String text=new String(bytes,StandardCharsets.UTF_8);JsonElement json=JsonParser.parseString(text);
                if(path.equals("sounds.json")){
                    Gson gson=new GsonBuilder().registerTypeAdapter(SoundEntry.class,new SoundEntryDeserializer()).create();
                    for(var sound:json.getAsJsonObject().entrySet())gson.fromJson(sound.getValue(),SoundEntry.class);
                }
                else if(path.startsWith("particles/")&&path.endsWith(".json")){
                    if(json.getAsJsonObject().has("textures"))for(JsonElement sprite:json.getAsJsonObject().getAsJsonArray("textures"))VersionedIdentifiers.of(sprite.getAsString());
                }
                else if(path.startsWith("models/")&&path.endsWith(".json"))JsonUnbakedModel.deserialize(new StringReader(text));
                else if(path.startsWith("items/")&&path.endsWith(".json")){
                    ItemAsset.CODEC.parse(registries.getOps(JsonOps.INSTANCE),json).getOrThrow();
                }else if(path.startsWith("equipment/")&&path.endsWith(".json"))EquipmentModel.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                else if(path.startsWith("blockstates/")&&path.endsWith(".json")){
                    var definition=ModelVariantMap.fromJson(json);
                    Identifier id=VersionedIdentifiers.of(file.namespace(),path.substring(12,path.length()-5));
                    if(Registries.BLOCK.containsId(id))definition.parse(Registries.BLOCK.get(id).getStateManager(),file.toString());
                }
            }catch(Exception error){throw new IOException(tr("资源校验失败 ")+file+": "+error.getMessage(),error);}
        }
    }
}
