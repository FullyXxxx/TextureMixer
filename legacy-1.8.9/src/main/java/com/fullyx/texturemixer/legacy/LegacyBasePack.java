package com.fullyx.texturemixer.legacy;

import net.minecraft.resource.AbstractFileResourcePack;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A base pack includes the legacy resource paths and any supported path aliases. */
final class LegacyBasePack extends AbstractFileResourcePack implements Closeable {
    private final Path path;
    private final String name;
    private final boolean folder;
    private final Map<String, String> entries;
    private final String prefix;
    private ZipFile archive;

    LegacyBasePack(Path path, String name, boolean folder, Map<String, String> entries, String prefix) {
        super(path.toFile());
        this.path = path;
        this.name = name;
        this.folder = folder;
        this.entries = entries;
        this.prefix = prefix;
    }

    @Override
    protected boolean containsFile(String file) {
        return file.startsWith(LegacyAssets.ROOT) && entries.containsKey(file.substring(LegacyAssets.ROOT.length()));
    }

    @Override
    protected synchronized InputStream openFile(String file) throws IOException {
        String entry;
        if (file.startsWith(LegacyAssets.ROOT)) {
            entry = entries.get(file.substring(LegacyAssets.ROOT.length()));
        } else {
            entry = file.equals("pack.mcmeta") || file.equals("pack.png") ? prefix + file : null;
        }
        if (entry == null) throw new FileNotFoundException(file);
        if (folder) {
            Path resolved = path.resolve(entry).toRealPath();
            if (!resolved.startsWith(path.toRealPath()) || !Files.isRegularFile(resolved)) {
                throw new FileNotFoundException(file);
            }
            return Files.newInputStream(resolved);
        }
        if (archive == null) archive = new ZipFile(path.toFile());
        ZipEntry member = archive.getEntry(entry);
        if (member == null) throw new FileNotFoundException(file);
        return archive.getInputStream(member);
    }

    @Override
    public Set<String> getNamespaces() {
        return Collections.singleton("minecraft");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized void close() throws IOException {
        if (archive != null) {
            archive.close();
            archive = null;
        }
    }
}
