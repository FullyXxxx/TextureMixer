package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Reads a ZIP in place, never extracts or modifies the user's archive. No retained OS file handles. */
public final class ZipSource implements PackSource {
    private static final long FILE_LIMIT=256L*1024*1024, TOTAL_LIMIT=2L*1024*1024*1024;
    private final Path root;
    private final long size,modified;
    private final Map<AssetId,String> files=new TreeMap<>();
    private final Map<String,Long> lengths=new HashMap<>();
    private final boolean mainCompatible;
    private final String fingerprint;
    public ZipSource(Path selected)throws IOException {
        root=selected.toRealPath();
        if(!Files.isRegularFile(root)||!root.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))throw new IOException(tr("请选择 ZIP 材质包压缩文件"));
        size=Files.size(root);modified=Files.getLastModifiedTime(root).toMillis();
        try(ZipFile zip=new ZipFile(root.toFile())) {
            Set<String> names=new HashSet<>();long total=0;int count=0;
            var entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();String name=entry.getName();
                validatePath(name);
                if(++count>100_000)throw new IOException(tr("ZIP 超过 100,000 个条目"));
                if(!names.add(name))throw new IOException(tr("ZIP 含重复路径: ")+name);
                if(entry.isDirectory())continue;
                if(entry.getSize()<0||entry.getSize()>FILE_LIMIT)throw new IOException(tr("ZIP 单文件超过 256 MiB 或长度无效: ")+name);
                total+=entry.getSize();if(total>TOTAL_LIMIT)throw new IOException(tr("ZIP 解压总量超过 2 GiB"));
                lengths.put(name,entry.getSize());
            }
            String prefix="";
            if(names.stream().noneMatch(n->n.startsWith("assets/"))) {
                Set<String> roots=new HashSet<>();for(String name:names){int i=name.indexOf("/assets/");if(i>0)roots.add(name.substring(0,i+1));}
                if(roots.size()!=1)throw new IOException(tr("ZIP 应包含一个资源包根目录及 assets"));
                prefix=roots.iterator().next();
            }
            JsonObject metadata=null;String meta=prefix+"pack.mcmeta";
            if(lengths.containsKey(meta)) {
                if(lengths.get(meta)>1024*1024)throw new IOException(tr("pack.mcmeta 超过 1 MiB"));
                try {metadata=JsonParser.parseString(new String(read(zip,meta),StandardCharsets.UTF_8)).getAsJsonObject();}
                catch(RuntimeException e){throw new IOException(tr("pack.mcmeta 无效"),e);}
            }
            if(metadata!=null&&metadata.has("filter"))throw new IOException(tr("首版不支持 pack.mcmeta filter；无法保证原版回退"));
            try {
                mainCompatible=metadata!=null&&DirectorySource.compatible(metadata.getAsJsonObject("pack"));
                index(prefix+"assets/");
                if(metadata!=null&&metadata.has("overlays")) {
                    JsonArray overlays=metadata.getAsJsonObject("overlays").getAsJsonArray("entries");
                    if(overlays!=null)for(JsonElement value:overlays) {
                        JsonObject overlay=value.getAsJsonObject();String directory=overlay.get("directory").getAsString();validatePath(directory);
                        if(DirectorySource.range(overlay.get("min_format"),overlay.get("max_format"),overlay.get("formats")))index(prefix+directory+"/assets/");
                    }
                }
            }catch(RuntimeException e){throw new IOException(tr("pack.mcmeta 版本或 overlays 无效"),e);}
            String assetsPrefix=prefix+"assets/";
            if(names.stream().noneMatch(name->name.startsWith(assetsPrefix)))throw new IOException(tr("ZIP 中没有 assets 目录"));
            StringBuilder signature=new StringBuilder(root.toString()).append('|').append(size).append('|').append(modified);
            names.stream().sorted().forEach(name->{ZipEntry e=zip.getEntry(name);signature.append('\n').append(name).append('|').append(e.getSize()).append('|').append(e.getCrc());});
            fingerprint=Assets.hash(signature.toString().getBytes(StandardCharsets.UTF_8));
        }
        if(!unchanged())throw new IOException(tr("ZIP 在索引期间发生变化，请重试"));
    }
    private static void validatePath(String name)throws IOException {
        if(name.isEmpty()||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.indexOf('\0')>=0)throw new IOException(tr("ZIP 含非法路径: ")+name);
        for(String part:name.split("/",-1))if(part.equals("..")||part.equals("."))throw new IOException(tr("ZIP 路径越界: ")+name);
    }
    private void index(String prefix)throws IOException {
        for(String name:lengths.keySet())if(name.startsWith(prefix)) {
            String relative=name.substring(prefix.length());int slash=relative.indexOf('/');
            if (slash > 0) {
                try {
                    AssetId id = new AssetId(relative.substring(0, slash), relative.substring(slash + 1));
                    files.put(id, name);
                } catch (IllegalArgumentException ignored) {
                    // Minecraft ignores non-Identifier paths. Packs commonly include auxiliary
                    // OptiFine files with spaces or uppercase names alongside valid native assets.
                    // Keep their original archive paths untouched instead of inventing aliases.
                }
            }
        }
    }
    private byte[] read(ZipFile zip,String name)throws IOException {
        ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IOException(tr("ZIP 条目已变化: ")+name);
        try(InputStream stream=zip.getInputStream(entry)) {
            byte[] data=stream.readNBytes((int)Math.min(FILE_LIMIT+1,lengths.get(name)+1));
            if(data.length!=lengths.get(name))throw new IOException(tr("ZIP 条目长度异常: ")+name);
            CRC32 crc=new CRC32();crc.update(data);if(crc.getValue()!=entry.getCrc())throw new IOException(tr("ZIP 条目校验失败: ")+name);
            return data;
        }
    }
    public Path root(){return root;}
    public String name(){return root.getFileName().toString();}
    public boolean mainCompatible(){return mainCompatible;}
    public long indexedBytes(){return files.values().stream().mapToLong(lengths::get).sum();}
    public String fingerprint(){return fingerprint;}
    public boolean unchanged()throws IOException{return Files.isRegularFile(root)&&Files.size(root)==size&&Files.getLastModifiedTime(root).toMillis()==modified;}
    public Set<AssetId> ids(){return Collections.unmodifiableSet(files.keySet());}
    @Override public Assets.Memory snapshot()throws IOException {
        if(!unchanged())throw new IOException(tr("ZIP 已变化，请刷新: ")+name());
        Map<AssetId,byte[]> data=new TreeMap<>();
        try(ZipFile zip=new ZipFile(root.toFile())){for(var entry:files.entrySet())data.put(entry.getKey(),read(zip,entry.getValue()));}
        if(!unchanged())throw new IOException(tr("ZIP 在读取期间发生变化，请刷新: ")+name());
        return new Assets.Memory(data);
    }
    public byte[] read(AssetId id)throws IOException {
        String name=files.get(id);if(name==null)return null;
        if(!unchanged())throw new IOException(tr("ZIP 已变化，请刷新: ")+name());
        try(ZipFile zip=new ZipFile(root.toFile())){return read(zip,name);}
    }
}
