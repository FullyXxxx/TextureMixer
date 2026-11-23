package com.fullyx.texturemixer.client;

import com.fullyx.texturemixer.core.*;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loads only the focused sample and gives its glyph atlas exclusive texture IDs. */
public final class FontPreview implements AutoCloseable {
    private record Prepared(List<GlyphProvider.Conditional> providers,List<GlyphProvider> fonts,boolean custom) implements AutoCloseable {
        @Override public void close(){fonts.forEach(GlyphProvider::close);}
    }
    private record Key(String target,String source) {}
    private final MixerService service;
    private Key key;
    private String error;
    private Prepared loaded;
    private FontSet storage;
    private Font renderer;

    public FontPreview(MixerService service){this.service=service;}

    public void request(Catalog.Target target,String source) {
        Key requested=new Key(target.id(),source);
        if(requested.equals(key)||service.busy||service.vanilla==null)return;
        close();
        key=requested;
        java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{
                var result=new ResourceCompiler(Generation.view(source,service.sources,service.vanilla),target.id()).compile(target.kind(),target.id());
                return prepare(result.files(),AssetId.of(target.id()),result.custom());
            }catch(Exception exception){throw new CompletionException(exception);}
        },service.workers).whenCompleteAsync((result,failure)->{
            if(key!=requested){if(result!=null)result.close();return;}
            if(failure!=null){error=MixerService.message(failure);return;}
            loaded=result;
        },service.client);
    }

    public boolean ready(){return loaded!=null;}
    public boolean custom(){return loaded!=null&&loaded.custom();}
    public String error(){return error;}

    public void draw(GuiGraphicsExtractor context,int x,int y,int width,int height,String sample) {
        if(loaded==null)return;
        if(renderer==null){
            storage=NativeCompat.fontStorage(service.client.getTextureManager(),
                    Identifier.fromNamespaceAndPath("texturemixer","font_preview/"+UUID.randomUUID()));
            storage.reload(loaded.providers(),Set.of());
            renderer=NativeCompat.fontRenderer(storage);
        }
        var lines=renderer.split(net.minecraft.network.chat.Component.literal(sample),width-16);
        int count=Math.min(lines.size(),Math.max(1,(height-8)/12));
        context.enableScissor(x+4,y+3,x+width-4,y+height-3);
        for(int index=0;index<count;index++){
            var line=lines.get(index);
            context.text(renderer,line,x+Math.max(8,(width-renderer.width(line))/2),
                    y+(height-count*12)/2+index*12+2,0xffededed,false);
        }
        context.disableScissor();
    }

    public static void validate(Map<AssetId,byte[]> files,AssetId target) throws IOException {
        try(Prepared ignored=prepare(files,target,false)) {}
    }

    private static Prepared prepare(Map<AssetId,byte[]> files,AssetId root,boolean custom) throws IOException {
        List<GlyphProvider> fonts=new ArrayList<>();
        try(var resources=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(new MemoryPack("font-preview",new Assets.Memory(files))))) {
            var providers=load(root,resources,new HashMap<>(),fonts);
            return new Prepared(providers,List.copyOf(fonts),custom);
        }catch(Exception failure){
            fonts.forEach(GlyphProvider::close);
            throw new IOException("Invalid font "+root+": "+MixerService.message(failure),failure);
        }
    }

    private static List<GlyphProvider.Conditional> load(AssetId id,ResourceManager resources,
            Map<AssetId,List<GlyphProvider.Conditional>> definitions,List<GlyphProvider> fonts) throws IOException {
        if(definitions.containsKey(id))return definitions.get(id);
        List<GlyphProvider.Conditional> providers=new ArrayList<>();
        try(var reader=resources.openAsReader(Identifier.parse(id.toString()))) {
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers")){
                var provider=GlyphProviderDefinition.Conditional.CODEC.parse(JsonOps.INSTANCE,value).getOrThrow();
                var built=provider.definition().unpack();
                if(built.left().isPresent()){
                    GlyphProvider font=built.left().orElseThrow().load(resources);
                    fonts.add(font);
                    providers.add(new GlyphProvider.Conditional(font,provider.filter()));
                }else{
                    AssetId reference=AssetId.of(built.right().orElseThrow().id().toString()).file("font",".json");
                    for(var child:load(reference,resources,definitions,fonts))
                        providers.add(new GlyphProvider.Conditional(child.provider(),child.filter().merge(provider.filter())));
                }
            }
        }
        var result=List.copyOf(providers);
        definitions.put(id,result);
        return result;
    }

    @Override public void close(){
        key=null;error=null;renderer=null;
        if(storage!=null){storage.close();storage=null;}
        if(loaded!=null){loaded.close();loaded=null;}
    }
}
