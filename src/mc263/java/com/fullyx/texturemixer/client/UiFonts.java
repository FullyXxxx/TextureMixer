package com.fullyx.texturemixer.client;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
public final class UiFonts {
    private static MemoryPack pack;
    private static Font renderer;
    private static final Identifier FONT=Identifier.fromNamespaceAndPath("texturemixer_ui","default");
    public static Component text(String value){return Component.literal(value).withStyle(style->NativeCompat.fontStyle(style,FONT));}
    public static Font renderer(){
        if(renderer==null)renderer=NativeCompat.uiRenderer(Minecraft.getInstance().font,FONT);
        return renderer;
    }
    public static PackResources pack(VanillaPackResources vanilla){
        if(pack==null)try{
            Map<AssetId,byte[]> output=new TreeMap<>();
            AssetId root=AssetId.of("default"), fallback=own(AssetId.of("minecraft_fallback"));
            copyFont(vanilla,root,output,new HashSet<>());
            output.put(fallback.file("font",".json"),output.get(own(root.file("font",".json"))));
            try(var font=UiFonts.class.getResourceAsStream("/assets/texturemixer/font/barlow-medium.ttf")){
                if(font==null)throw new IOException("Missing Barlow UI font");
                output.put(AssetId.of("texturemixer_ui:font/barlow-medium.ttf"),font.readAllBytes());
            }
            JsonObject uiFont=JsonParser.parseString("""
                    {"providers":[
                      {"type":"ttf","file":"texturemixer_ui:barlow-medium.ttf","size":10,"oversample":3,"shift":[0,0]},
                      {"type":"reference","id":"texturemixer_ui:minecraft_fallback"}
                    ]}
                    """).getAsJsonObject();
            output.put(own(root.file("font",".json")),Assets.jsonBytes(uiFont));
            pack=new MemoryPack("texturemixer/ui-font",new Assets.Memory(output));
        }catch(IOException error){throw new IllegalStateException("Cannot load the built-in UI font",error);}
        return pack;
    }
    private static byte[] read(VanillaPackResources vanilla,AssetId file)throws IOException{
        var input=vanilla.fullResources().getResource(PackType.CLIENT_RESOURCES,Identifier.parse(file.toString()));
        if(input==null)throw new IOException("Missing built-in font resource: "+file);
        try(var stream=input.get()){return stream.readAllBytes();}
    }
    private static AssetId own(AssetId id){return new AssetId("texturemixer_ui",id.path());}
    private static void copyFont(VanillaPackResources vanilla,AssetId id,Map<AssetId,byte[]> output,Set<AssetId> visited)throws IOException{
        if(!visited.add(id))return;AssetId file=id.file("font",".json");
        JsonObject data=JsonParser.parseString(new String(read(vanilla,file),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        for(JsonElement e:data.getAsJsonArray("providers")){
            JsonObject provider=e.getAsJsonObject();String type=provider.get("type").getAsString().replace("minecraft:","");
            if(type.equals("reference")){
                AssetId next=AssetId.of(provider.get("id").getAsString());copyFont(vanilla,next,output,visited);provider.addProperty("id",own(next).toString());
            }else if(provider.has("file")||provider.has("hex_file")){
                String key=provider.has("hex_file")?"hex_file":"file";AssetId source=AssetId.of(provider.get(key).getAsString());
                AssetId asset=type.equals("bitmap")?source.withPath("textures/"+source.path()):source;
                output.put(own(asset),read(vanilla,asset));provider.addProperty(key,own(source).toString());
            }
        }output.put(own(file),Assets.jsonBytes(data));
    }
}
