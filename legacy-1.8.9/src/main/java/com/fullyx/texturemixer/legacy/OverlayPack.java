package com.fullyx.texturemixer.legacy;

import net.minecraft.client.resource.ResourceMetadataProvider;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.MetadataSerializer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class OverlayPack implements ResourcePack {
    static final String NAME = "TextureMixer overrides";
    private final Map<String, byte[]> files;

    OverlayPack(Map<String, byte[]> files) {
        this.files = files;
    }

    @Override
    public InputStream open(Identifier id) throws FileNotFoundException {
        if (!contains(id)) throw new FileNotFoundException(id.toString());
        return new ByteArrayInputStream(files.get(id.getPath()));
    }

    @Override
    public boolean contains(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && files.containsKey(id.getPath());
    }

    @Override
    public Set<String> getNamespaces() {
        return Collections.singleton("minecraft");
    }

    @Override
    public <T extends ResourceMetadataProvider> T parseMetadata(MetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getIcon() {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
