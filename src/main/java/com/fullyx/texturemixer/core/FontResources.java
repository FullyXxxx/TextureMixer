package com.fullyx.texturemixer.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A font and its providers are installed together, with private dependency IDs. */
public final class FontResources {
    public static final Map<String,String> TARGETS=Map.of(
            "minecraft:font/default.json","Default Font",
            "minecraft:font/uniform.json","Unicode Font",
            "minecraft:font/alt.json","Enchanting Font",
            "minecraft:font/illageralt.json","Illager Font");
    public static final String FILTER_PATH="internal/font_selection/";
    public record Result(Map<AssetId,byte[]> files,boolean custom) {}

    private final Assets.View view;
    private final AssetId root;
    private final String namespacePrefix;
    private final Map<AssetId,byte[]> files=new TreeMap<>();
    private final Set<AssetId> visiting=new HashSet<>();
    private boolean custom;
    private long bytes;

    public FontResources(Assets.View view,String font) {
        this.view=view;
        root=AssetId.of(font);
        namespacePrefix="texturemixer_font_"+Assets.hash(font.getBytes(StandardCharsets.UTF_8)).substring(0,16)+"_";
    }

    public Result compile() throws IOException {
        if(!TARGETS.containsKey(root.toString()))throw new IOException("Unknown font: "+root);
        definition(root,root);
        put(marker(root),new byte[]{'{','}'});
        return new Result(Map.copyOf(files),custom);
    }

    public static AssetId marker(AssetId font) {
        return new AssetId("texturemixer",FILTER_PATH+font.path().substring("font/".length()));
    }

    private void definition(AssetId input,AssetId output) throws IOException {
        if(files.containsKey(output))return;
        if(visiting.size()>=64||!visiting.add(input))throw new IOException("Cyclic or excessive font references: "+input);
        byte[] base=view.vanilla().read(input);
        byte[] own=view.source()==null?null:view.source().read(input);
        if(base==null&&own==null)throw new IOException("Missing font definition: "+input);
        custom|=own!=null;
        JsonArray providers=new JsonArray();
        if(own!=null)addProviders(own,providers);
        if(base!=null&&!Arrays.equals(base,own))addProviders(base,providers);
        JsonObject result=new JsonObject();
        result.add("providers",providers);
        put(output,Assets.jsonBytes(result));
        visiting.remove(input);
    }

    private void addProviders(byte[] data,JsonArray providers) throws IOException {
        JsonArray input=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("providers");
        for(JsonElement value:input) {
            JsonObject provider=value.getAsJsonObject();
            String type=provider.get("type").getAsString();
            if(type.startsWith("minecraft:"))type=type.substring("minecraft:".length());
            switch(type) {
                case "reference" -> {
                    AssetId id=AssetId.of(provider.get("id").getAsString());
                    AssetId mapped=privateId(id);
                    definition(id.file("font",".json"),mapped.file("font",".json"));
                    provider.addProperty("id",mapped.toString());
                }
                case "bitmap" -> dependency(provider,"file","textures/");
                case "ttf" -> dependency(provider,"file","font/");
                case "unihex" -> dependency(provider,"hex_file","");
                case "space" -> {}
                default -> throw new IOException("Unsupported font provider: "+type);
            }
            providers.add(provider);
        }
    }

    private void dependency(JsonObject provider,String property,String directory) throws IOException {
        AssetId id=AssetId.of(provider.get(property).getAsString()),mapped=privateId(id);
        AssetId input=id.withPath(directory+id.path()),output=mapped.withPath(directory+mapped.path());
        if(!files.containsKey(output)) {
            byte[] data=view.read(input);
            if(data==null)throw new IOException("Missing font provider resource: "+input);
            custom|=view.fromSource(input);
            put(output,data);
        }
        provider.addProperty(property,mapped.toString());
    }

    private AssetId privateId(AssetId asset){return new AssetId(namespacePrefix+asset.namespace(),asset.path());}
    private void put(AssetId id,byte[] data) throws IOException {
        bytes+=data.length;
        if(bytes>64L*1024*1024)throw new IOException("Font resources exceed the 64 MiB budget: "+root);
        files.put(id,data);
    }
}
