package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import javax.imageio.ImageIO;

/** Indexes only an explicitly supplied root. Never discovers or scans resourcepacks. */
public final class DirectorySource implements PackSource {
    private static volatile int textureLimit=16384;
    public static void textureLimit(int supported){textureLimit=Math.min(16384,supported);}
    private final Path root;
    private final Map<AssetId, Path> files = new TreeMap<>();
    private final Map<Path, Stamp> stamps = new LinkedHashMap<>();
    private final Map<Path,Long> directories = new LinkedHashMap<>();
    private final Stamp metadataStamp;
    private final boolean mainCompatible;
    private int indexedEntries;
    public DirectorySource(Path selected) throws IOException {
        root = selected.toRealPath();
        if (!Files.isDirectory(root.resolve("assets"))) throw new IOException(tr("请选择包含 assets 文件夹的资源包根目录"));
        JsonObject metadata = null;
        Path metadataPath=root.resolve("pack.mcmeta");
        metadataStamp=Files.exists(metadataPath)?new Stamp(Files.size(safe(metadataPath)),Files.getLastModifiedTime(metadataPath).toMillis()):null;
        if (metadataStamp != null) {
            int limit=1024*1024;
            if(metadataStamp.size()>limit)throw new IOException(tr("pack.mcmeta 超过 1 MiB"));
            try(var input=Files.newInputStream(safe(metadataPath))){
                byte[] bytes=input.readNBytes(limit+1);
                if(bytes.length>limit)throw new IOException(tr("pack.mcmeta 超过 1 MiB"));
                metadata=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(RuntimeException error){throw new IOException(tr("pack.mcmeta 无效"),error);}
        }
        if (metadata != null && metadata.has("filter")) throw new IOException(tr("首版不支持 pack.mcmeta filter；无法保证原版回退"));
        try{mainCompatible = metadata != null && compatible(metadata.getAsJsonObject("pack"));}
        catch(RuntimeException error){throw new IOException(tr("pack.mcmeta 的版本格式无效"),error);}
        index(root.resolve("assets"));
        if (metadata != null && metadata.has("overlays")) {
            JsonArray entries = metadata.getAsJsonObject("overlays").getAsJsonArray("entries");
            if (entries != null) for (JsonElement element : entries) {
                JsonObject overlay = element.getAsJsonObject();
                if (range(overlay.get("min_format"), overlay.get("max_format"), overlay.get("formats"))) {
                    Path directory = safe(root.resolve(overlay.get("directory").getAsString()));
                    if (Files.isDirectory(directory.resolve("assets"))) index(directory.resolve("assets"));
                }
            }
        }
    }
    private record Format(int major,int minor)implements Comparable<Format>{
        public int compareTo(Format other){int order=Integer.compare(major,other.major);return order==0?Integer.compare(minor,other.minor):order;}
    }
    private static volatile Format target=new Format(75,0);
    private static final Format MINIMUM=new Format(0,0),MAXIMUM=new Format(Integer.MAX_VALUE,Integer.MAX_VALUE);
    private static int versionNumber(JsonElement element){int value=element.getAsBigDecimal().intValueExact();if(value<0)throw new IllegalArgumentException(tr("版本不能为负数"));return value;}
    private static Format format(JsonElement element,Format fallback) {
        if (element == null) return fallback;
        if (element.isJsonArray()){
            JsonArray pair=element.getAsJsonArray();if(pair.size()!=2)throw new IllegalArgumentException(tr("版本应为 [major, minor]"));
            return new Format(versionNumber(pair.get(0)),versionNumber(pair.get(1)));
        }
        return new Format(versionNumber(element),0);
    }
    public static void targetFormat(int major,int minor){target=new Format(major,minor);}
    static boolean usesStandaloneTrims(){return target.compareTo(new Format(97,1))>=0;}
    static boolean range(JsonElement min, JsonElement max, JsonElement legacy) {
        Format current=target;
        if (min != null || max != null) return format(min,MINIMUM).compareTo(current)<=0&&format(max,MAXIMUM).compareTo(current)>=0;
        if (legacy == null) return false;
        if (legacy.isJsonPrimitive()) return format(legacy,MINIMUM).equals(current);
        if (legacy.isJsonArray()) return format(legacy.getAsJsonArray().get(0),MINIMUM).compareTo(current)<=0
                && format(legacy.getAsJsonArray().get(1),MINIMUM).compareTo(current)>=0;
        JsonObject object = legacy.getAsJsonObject();
        return format(object.get("min_inclusive"),MINIMUM).compareTo(current)<=0&&format(object.get("max_inclusive"),MAXIMUM).compareTo(current)>=0;
    }
    static boolean compatible(JsonObject pack) {
        if (pack == null) return false;
        return pack.has("min_format") || pack.has("max_format")
                ? range(pack.get("min_format"), pack.get("max_format"), null)
                : range(null, null, pack.has("supported_formats") ? pack.get("supported_formats") : pack.get("pack_format"));
    }
    private Path safe(Path path) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(root)) throw new IOException(tr("资源路径越出来源文件夹: ") + path);
        return real;
    }
    private void index(Path assets) throws IOException {
        Files.walkFileTree(assets, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                safe(dir);directories.put(dir,Files.getLastModifiedTime(dir).toMillis());return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (++indexedEntries > 100_000) throw new IOException(tr("来源超过 100,000 个条目"));
                Path real = safe(file);
                if (!Files.isRegularFile(real)) return FileVisitResult.CONTINUE;
                stamps.put(real, new Stamp(Files.size(real), Files.getLastModifiedTime(real).toMillis()));
                String relative = assets.relativize(file).toString().replace('\\', '/');
                int slash = relative.indexOf('/');
                if (slash > 0) try {
                    AssetId id = new AssetId(relative.substring(0, slash), relative.substring(slash + 1));
                    files.put(id, real);
                } catch (IllegalArgumentException ignored) {
                    // Match Minecraft's native index while preserving auxiliary files in place.
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public Path root() { return root; }
    public String name() { return root.getFileName().toString(); }
    public boolean mainCompatible() { return mainCompatible; }
    public long indexedBytes(){return files.values().stream().mapToLong(path->stamps.get(path).size).sum();}
    public String fingerprint() {
        StringBuilder indexed=new StringBuilder(root.toString()).append('|').append(metadataStamp);
        files.forEach((id,path)->indexed.append('\n').append(id).append('|').append(stamps.get(path)));
        return Assets.hash(indexed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Stat only the registered index; never discovers a sibling resource pack. New entries change their parent directory stamp. */
    public boolean unchanged()throws IOException {
        if(!Files.isDirectory(root))return false;
        Path metadata=root.resolve("pack.mcmeta");
        if(Files.exists(metadata)!=(metadataStamp!=null))return false;
        if(metadataStamp!=null&&(Files.size(metadata)!=metadataStamp.size||Files.getLastModifiedTime(metadata).toMillis()!=metadataStamp.modified))return false;
        for(var entry:directories.entrySet())if(!Files.isDirectory(entry.getKey())||Files.getLastModifiedTime(entry.getKey()).toMillis()!=entry.getValue())return false;
        for(var entry:stamps.entrySet()){
            Path file=entry.getKey();Stamp stamp=entry.getValue();
            if(!Files.isRegularFile(file)||Files.size(file)!=stamp.size||Files.getLastModifiedTime(file).toMillis()!=stamp.modified)return false;
        }
        return true;
    }
    @Override public Set<AssetId> ids() { return Collections.unmodifiableSet(files.keySet()); }
    @Override public byte[] read(AssetId id) throws IOException {
        Path file = files.get(id); if (file == null) return null;
        Stamp stamp = stamps.get(file);
        if (Files.size(file) != stamp.size || Files.getLastModifiedTime(file).toMillis() != stamp.modified)
            throw new IOException(tr("来源在读取期间已变化，请刷新: ") + name());
        if (stamp.size > 256L * 1024 * 1024) throw new IOException(tr("单文件超过 256 MiB: ") + id);
        return Files.readAllBytes(safe(file));
    }
    public Assets.Memory snapshot() throws IOException {
        Map<AssetId, byte[]> data = new TreeMap<>();
        for (AssetId id : ids()) data.put(id, read(id));
        return new Assets.Memory(data);
    }
    public static void validatePng(AssetId id, byte[] data) throws IOException {
        pngSize(id,data,true);
    }
    public record ImageSize(int width,int height) {}
    public static ImageSize pngSize(AssetId id,byte[] data,boolean decode) throws IOException {
        if(data.length<8||data[0]!=(byte)137||data[1]!='P'||data[2]!='N'||data[3]!='G'||data[4]!=13||data[5]!=10||data[6]!=26||data[7]!=10)throw new IOException(tr("无效 PNG 文件: ")+id);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException(tr("无效图像: ") + id);
            var reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long)w*h > 32L*1024*1024 || w > textureLimit || h > textureLimit)
                    throw new IOException(tr("图像超过尺寸限制: ") + id + " ("+w+"×"+h+")");
                if (decode&&reader.read(0) == null) throw new IOException(tr("图像解码失败: ") + id);
                return new ImageSize(w,h);
            } finally { reader.dispose(); }
        }
    }
    private record Stamp(long size, long modified) {}
}
