package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import com.fullyx.texturemixer.core.*;
import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Shared, bounded thumbnails with visible work ahead of optional preloading. */
public final class ResourcePreviewCache implements AutoCloseable {
    public static final class Entry {
        public String error;
        public boolean custom,empty;
        public Identifier texture;
        private int width,height;
        private EnchantPreview.Effect enchantment;
        private boolean glint;
        private int frames;
        private long[] durations;
        private boolean[] visibleFrames;
        private boolean anyVisible;
        private long started,bytes;
        private volatile boolean discarded;
        private Request request;
        private Prepared prepared;
    }
    private record Prepared(NativeImage image,int frames,long[] durations,boolean[] visibleFrames,boolean custom,boolean empty) implements AutoCloseable {
        @Override public void close(){image.close();}
    }
    private record Request(Catalog.Target target,String source,String key,Entry entry) {}
    private static final int MAX_FRAMES=64,MAX_ENTRIES=128;
    private static final long MAX_BYTES=32L*1024*1024;
    private final MixerService service;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(64,.75f,true);
    private final ArrayDeque<Request> visibleQueue=new ArrayDeque<>(),preloadQueue=new ArrayDeque<>();
    private final ArrayDeque<Entry> uploads=new ArrayDeque<>();
    private final List<Identifier> retired=new ArrayList<>();
    private final Set<String> pinned=new HashSet<>();
    private volatile boolean closed;
    private int activePreparations,warmCursor;
    private long cacheHits,completedPreloads,maxUploadNanos;
    public ResourcePreviewCache(MixerService service){this.service=service;}
    public static String key(Catalog.Target target,String source){return target.kind()+"|"+target.id()+"|"+source;}
    public Entry getIfPresent(Catalog.Target target,String source){return entries.get(key(target,source));}
    public Entry get(Catalog.Target target,String source){return request(target,source,false);}
    public void prefetch(Catalog.Target target,String source){
        if(service.preloadEnabled()&&preloadQueue.size()<8&&entries.size()<MAX_ENTRIES)request(target,source,true);
    }
    public void retain(Set<String> keys){
        pinned.clear();pinned.addAll(keys);
        var iterator=visibleQueue.iterator();
        while(iterator.hasNext()){
            Request request=iterator.next();if(pinned.contains(request.key()))continue;
            iterator.remove();
            if(service.preloadEnabled()&&preloadQueue.size()<8)preloadQueue.addLast(request);
            else{entries.remove(request.key());discard(request.entry());}
        }
    }
    private Entry request(Catalog.Target target,String source,boolean background){
        String key=key(target,source);Entry entry=entries.get(key);
        if(entry!=null){
            cacheHits++;
            if(!background&&entry.request!=null){
                boolean queued=preloadQueue.remove(entry.request)||visibleQueue.remove(entry.request);
                if(queued)visibleQueue.addFirst(entry.request);
            }
            return entry;
        }
        entry=new Entry();entries.put(key,entry);
        Request request=new Request(target,source,key,entry);entry.request=request;
        if(background)preloadQueue.addLast(request);else visibleQueue.addLast(request);
        trim();return entry;
    }
    public void tick(boolean browsing){
        if(closed||service.busy||service.catalog==null)return;
        if(service.preloadEnabled()&&!browsing&&preloadQueue.size()<4){
            var targets=service.catalog.list(MixerConfig.Kind.PARTICLE);
            if(warmCursor<targets.size())prefetch(targets.get(warmCursor++),MixerConfig.VANILLA);
        }
        while(activePreparations<2&&!visibleQueue.isEmpty())start(visibleQueue.removeFirst());
        if(activePreparations==0&&visibleQueue.isEmpty()&&service.preloadEnabled()&&!preloadQueue.isEmpty())start(preloadQueue.removeFirst());
        if(!browsing)beginFrame();
    }
    private void start(Request request){
        Entry entry=request.entry();entry.request=null;
        if(entry.discarded)return;
        boolean background=!pinned.contains(request.key());activePreparations++;
        CompletableFuture.supplyAsync(()->{
            try{
                if(entry.discarded)throw new CancellationException();
                var result=new ResourceCompiler(Generation.view(request.source(),service.sources,service.vanilla),request.key()).compile(request.target().kind(),request.target().id());
                return prepare(result,request.target().kind());
            }catch(Exception error){throw new CompletionException(error);}
        },service.workers).whenComplete((prepared,error)->{
            if(closed){if(prepared!=null)prepared.close();return;}
            service.client.execute(()->{
                activePreparations--;
                if(closed||entry.discarded){if(prepared!=null)prepared.close();return;}
                if(error!=null){entry.error=MixerService.message(error);return;}
                entry.custom=prepared.custom();entry.empty=prepared.empty();entry.frames=prepared.frames();
                entry.glint=request.target().kind()==MixerConfig.Kind.ENCHANT;
                entry.durations=prepared.durations();entry.visibleFrames=prepared.visibleFrames();
                for(boolean frame:entry.visibleFrames)entry.anyVisible|=frame;
                entry.width=prepared.image().getWidth();entry.height=prepared.image().getHeight();
                entry.bytes=(long)entry.width*entry.height*4;entry.prepared=prepared;
                uploads.addLast(entry);if(background)completedPreloads++;
                trim();
            });
        });
    }
    public void beginFrame(){
        if(closed||service.busy)return;
        for(Identifier texture:retired)service.client.getTextureManager().destroyTexture(texture);
        retired.clear();
        while(!uploads.isEmpty()){
            Entry entry=uploads.peekFirst();
            for(String key:pinned){
                Entry visible=entries.get(key);
                if(visible!=null&&visible.prepared!=null&&uploads.contains(visible)){entry=visible;break;}
            }
            uploads.remove(entry);if(entry.discarded||entry.prepared==null)continue;
            Prepared prepared=entry.prepared;entry.prepared=null;
            long start=System.nanoTime();
            try{
                Identifier texture=VersionedIdentifiers.of("texturemixer","resource_preview/"+UUID.randomUUID().toString().replace("-",""));
                var thumbnail=NativeCompat.thumbnail(prepared.image(),entry.glint);
                service.client.getTextureManager().registerTexture(texture,thumbnail);
                entry.texture=texture;entry.started=System.nanoTime();
            }catch(Throwable error){prepared.close();entry.error=MixerService.message(error);entry.bytes=0;}
            long elapsed=System.nanoTime()-start;maxUploadNanos=Math.max(maxUploadNanos,elapsed);
            break;
        }
    }
    private void trim(){
        long bytes=entries.values().stream().mapToLong(entry->entry.bytes).sum();
        var iterator=entries.entrySet().iterator();
        while((entries.size()>MAX_ENTRIES||bytes>MAX_BYTES)&&iterator.hasNext()){
            var entry=iterator.next();if(pinned.contains(entry.getKey()))continue;
            iterator.remove();bytes-=entry.getValue().bytes;discard(entry.getValue());
        }
        visibleQueue.removeIf(request->request.entry().discarded);preloadQueue.removeIf(request->request.entry().discarded);
    }
    private static Prepared prepare(ResourceCompiler.Result result,MixerConfig.Kind kind)throws IOException {
        if(kind==MixerConfig.Kind.ENCHANT)return prepareGlint(result);
        List<BufferedImage> frames=new ArrayList<>();
        List<Long> durations=new ArrayList<>();
        int imageCount=Math.min(kind==MixerConfig.Kind.PARTICLE?MAX_FRAMES:1,result.images().size());
        for(int imageIndex=0;imageIndex<imageCount;imageIndex++) {
            AssetId id=result.images().get((int)((long)imageIndex*result.images().size()/imageCount));
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(result.files().get(id)));
            if(image==null)throw new IOException(tr("无法解码预览图片 ")+id);
            byte[] meta=result.files().get(id.withPath(id.path()+".mcmeta"));
            JsonObject metadata=meta==null?new JsonObject():JsonParser.parseString(new String(meta,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(metadata.has("animation")) {
                JsonObject animation=metadata.getAsJsonObject("animation");
                int fw=animation.has("width")?animation.get("width").getAsInt():animation.has("height")?image.getWidth():Math.min(image.getWidth(),image.getHeight());
                int fh=animation.has("height")?animation.get("height").getAsInt():animation.has("width")?image.getHeight():Math.min(image.getWidth(),image.getHeight());
                int columns=image.getWidth()/fw,total=columns*(image.getHeight()/fh);
                int duration=animation.has("frametime")?animation.get("frametime").getAsInt():1;
                JsonArray sequence=animation.has("frames")?animation.getAsJsonArray("frames"):new JsonArray();
                int count=sequence.isEmpty()?total:sequence.size();
                int samples=Math.min((MAX_FRAMES-frames.size())/(imageCount-imageIndex),count);
                BitSet transparent=new BitSet(total);
                for(int sample=0;sample<samples;sample++) {
                    int from=(int)((long)sample*count/samples),to=(int)((long)(sample+1)*count/samples);
                    BufferedImage representative=null;long time=0;
                    for(int step=from;step<to;step++){
                        JsonElement frame=sequence.isEmpty()?null:sequence.get(step);
                        int index=frame==null?step:frame.isJsonObject()?frame.getAsJsonObject().get("index").getAsInt():frame.getAsInt();
                        if(representative==null&&!transparent.get(index)){
                            BufferedImage candidate=thumbnail(image.getSubimage(index%columns*fw,index/columns*fh,fw,fh));
                            if(hasPixels(candidate))representative=candidate;
                            else transparent.set(index);
                        }
                        time+=frame!=null&&frame.isJsonObject()&&frame.getAsJsonObject().has("time")?frame.getAsJsonObject().get("time").getAsInt():duration;
                    }
                    frames.add(representative!=null?representative:new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB));
                    durations.add(Math.max(1,time));
                }
            } else {frames.add(thumbnail(image));durations.add(2L);}
            if(kind!=MixerConfig.Kind.PARTICLE||frames.size()>=MAX_FRAMES)break;
        }
        boolean empty=frames.isEmpty();
        if(empty){frames.add(new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB));durations.add(2L);}
        NativeImage sheet=new NativeImage(64,64*frames.size(),false);
        boolean[] visible=new boolean[frames.size()];
        for(int index=0;index<frames.size();index++){
            BufferedImage frame=frames.get(index);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                int pixel=frame.getRGB(x,y);
                ImagePixels.set(sheet,x,y+index*64,pixel);
                visible[index]|=(pixel>>>24)!=0;
            }
        }
        return new Prepared(sheet,frames.size(),durations.stream().mapToLong(Long::longValue).toArray(),visible,result.custom(),empty);
    }
    private static Prepared prepareGlint(ResourceCompiler.Result result)throws IOException {
        try(NativeImage original=NativeImage.read(result.files().get(result.images().getFirst()))){
            int width=Math.min(512,original.getWidth()),height=Math.min(512,original.getHeight());
            NativeImage image=new NativeImage(width,height,false);
            boolean visible=false;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int color=ImagePixels.get(original,x*original.getWidth()/width,y*original.getHeight()/height);
                ImagePixels.set(image,x,y,color);
                visible|=(color>>>24)!=0;
            }
            return new Prepared(image,1,new long[]{1},new boolean[]{visible},result.custom(),false);
        }
    }
    private static BufferedImage thumbnail(BufferedImage image) {
        BufferedImage output=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        double scale=Math.min(56.0/image.getWidth(),56.0/image.getHeight());
        int width=Math.max(1,(int)(image.getWidth()*scale)),height=Math.max(1,(int)(image.getHeight()*scale));
        int[] row=new int[image.getWidth()];
        int[] pixels=new int[width*height];
        for(int y=0;y<image.getHeight();y++){
            image.getRGB(0,y,row.length,1,row,0,row.length);
            int offset=y*height/image.getHeight()*width;
            for(int x=0;x<row.length;x++){
                int index=offset+x*width/row.length;
                if((row[x]>>>24)>(pixels[index]>>>24))pixels[index]=row[x];
            }
        }
        if(scale>=1){
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)
                pixels[y*width+x]=image.getRGB(x*image.getWidth()/width,y*image.getHeight()/height);
        }
        output.setRGB((64-width)/2,(64-height)/2,width,height,pixels,0,width);
        return output;
    }
    private static boolean hasPixels(BufferedImage image){
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if((image.getRGB(x,y)>>>24)!=0)return true;
        return false;
    }
    private int frame(Entry entry){
        long tick=((System.nanoTime()-entry.started)/50_000_000L)%Arrays.stream(entry.durations).sum();int frame=0;
        while(frame<entry.frames-1&&tick>=entry.durations[frame])tick-=entry.durations[frame++];
        return frame;
    }
    private void drawFrame(Entry entry,int frame,DrawContext context,int x,int y,int size,int color){
        int frameHeight=entry.height/entry.frames;
        com.fullyx.texturemixer.client.NativeCompat.drawGuiTexture(context,entry.texture,x,y,0,frame*frameHeight,size,size,entry.width,frameHeight,entry.width,entry.height,color);
    }
    public EnchantPreview.Effect glint(Entry entry,Catalog.Target target){
        if(entry.texture==null)return null;
        if(entry.enchantment==null)entry.enchantment=new EnchantPreview.Effect(entry.texture,target.id().endsWith("_armor.png"));
        return entry.enchantment;
    }
    public void draw(Entry entry,DrawContext context,int x,int y,int size){
        if(entry.texture==null)return;
        int frame=frame(entry);drawFrame(entry,frame,context,x,y,size,0xffffffff);
        if(entry.empty||!entry.visibleFrames[frame])context.drawCenteredTextWithShadow(UiFonts.renderer(),tr(entry.empty?"无贴图":"透明帧"),x+size/2,y+size/2-4,0xffa0a0a0);
    }
    public boolean needsReference(Entry entry){return entry.texture!=null&&!entry.anyVisible;}
    public void drawOverview(Entry entry,Catalog.Target target,String source,boolean allowRequest,DrawContext context,int x,int y,int size){
        if(entry.texture==null)return;
        if(!entry.empty&&needsReference(entry)&&!source.equals(MixerConfig.VANILLA)){
            pinned.add(key(target,MixerConfig.VANILLA));
            Entry reference=allowRequest?get(target,MixerConfig.VANILLA):getIfPresent(target,MixerConfig.VANILLA);
            if(reference!=null&&reference.texture!=null&&reference.anyVisible){
                drawFrame(reference,visibleFrame(reference),context,x,y,size,0xbbffffff);
                String label=tr("透明");int width=Math.min(size-4,UiFonts.renderer().getWidth(label)+4);
                context.fill(x+2,y+size-12,x+2+width,y+size-2,0xdd202020);
                context.drawText(UiFonts.renderer(),label,x+4,y+size-11,0xffdddddd,false);return;
            }
        }
        if(entry.anyVisible)drawFrame(entry,visibleFrame(entry),context,x,y,size,0xffffffff);else draw(entry,context,x,y,size);
    }
    private int visibleFrame(Entry entry){
        int frame=frame(entry);
        for(int step=0;step<entry.frames;step++){int candidate=(frame+step)%entry.frames;if(entry.visibleFrames[candidate])return candidate;}
        return frame;
    }
    public record Usage(int entries,int preparing,int queued,int uploads,long bytes,long cacheHits,long preloads,double maxUploadMs){}
    public Usage usage(){return new Usage(entries.size(),activePreparations,visibleQueue.size()+preloadQueue.size(),uploads.size(),entries.values().stream().mapToLong(entry->entry.bytes).sum(),cacheHits,completedPreloads,maxUploadNanos/1e6);}
    private void discard(Entry entry){
        entry.discarded=true;if(entry.prepared!=null){entry.prepared.close();entry.prepared=null;}
        if(entry.texture!=null)retired.add(entry.texture);
    }
    public void invalidate(){for(Entry entry:entries.values())discard(entry);entries.clear();visibleQueue.clear();preloadQueue.clear();uploads.clear();pinned.clear();warmCursor=0;}
    public void invalidateCustom(){
        var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();if(entry.getKey().endsWith("|"+MixerConfig.VANILLA))continue;
            discard(entry.getValue());iterator.remove();
        }
        visibleQueue.removeIf(request->request.entry().discarded);preloadQueue.removeIf(request->request.entry().discarded);
    }
    @Override public void close(){closed=true;invalidate();for(Identifier texture:retired)service.client.getTextureManager().destroyTexture(texture);retired.clear();}
}
