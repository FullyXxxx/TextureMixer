package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.PlaceholderLookupProvider;
/** Run the actual target-version codecs before asking Minecraft to reload; it otherwise logs and skips bad assets. */
public final class NativeAssetValidator {
    public static void validate(Map<AssetId,byte[]> files)throws IOException{
        var registries=ClientRegistryLayer.createRegistryAccess().compositeAccess();
        for(var entry:files.entrySet()){
            AssetId file=entry.getKey();String path=file.path();byte[] bytes=entry.getValue();
            try{
                if(path.startsWith("sounds/")&&path.endsWith(".ogg")){
                    try(JOrbisAudioStream stream=new JOrbisAudioStream(new ByteArrayInputStream(bytes))){stream.getFormat();}
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
                    Gson gson=new GsonBuilder().registerTypeAdapter(SoundEventRegistration.class,new SoundEventRegistrationSerializer()).create();
                    for(var sound:json.getAsJsonObject().entrySet())gson.fromJson(sound.getValue(),SoundEventRegistration.class);
                }
                else if(path.startsWith("particles/")&&path.endsWith(".json")){
                    if(json.getAsJsonObject().has("textures"))for(JsonElement sprite:json.getAsJsonObject().getAsJsonArray("textures"))Identifier.parse(sprite.getAsString());
                }
                else if(path.startsWith("models/")&&path.endsWith(".json"))CuboidModel.fromStream(new StringReader(text));
                else if(path.startsWith("items/")&&path.endsWith(".json")){
                    var lookup=new PlaceholderLookupProvider(registries);ClientItem.CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
                }else if(path.startsWith("equipment/")&&path.endsWith(".json"))EquipmentClientInfo.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                else if(path.startsWith("blockstates/")&&path.endsWith(".json")){
                    var definition=BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                    Identifier id=Identifier.fromNamespaceAndPath(file.namespace(),path.substring(12,path.length()-5));
                    if(BuiltInRegistries.BLOCK.containsKey(id))definition.instantiate(BuiltInRegistries.BLOCK.getValue(id).getStateDefinition(),file::toString);
                }
            }catch(Exception error){throw new IOException(tr("资源校验失败 ")+file+": "+error.getMessage(),error);}
        }
    }
}
