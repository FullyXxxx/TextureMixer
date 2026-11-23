package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public final class ConfigStore {
    private final Path directory;
    private String recoveryMessage = "";
    public ConfigStore(Path directory) { this.directory = directory; }
    public String recoveryMessage() { return recoveryMessage; }
    public MixerConfig load() throws IOException {
        Path file = directory.resolve("config.json");
        if (!Files.exists(file)) return new MixerConfig();
        try { return read(file); }
        catch (Exception error) {
            Files.createDirectories(directory);
            Files.copy(file, directory.resolve("config.corrupt-" + Instant.now().toEpochMilli() + ".json"));
            recoveryMessage = tr("配置损坏；已保留原文件");
            try { MixerConfig good = read(directory.resolve("config.last-good.json")); recoveryMessage += tr("，已恢复上次设置"); return good; }
            catch (Exception ignored) { recoveryMessage += tr("，混搭已停用"); return new MixerConfig(); }
        }
    }
    private MixerConfig read(Path path) throws IOException {
        MixerConfig config = MixerConfig.GSON.fromJson(Files.readString(path), MixerConfig.class);
        if (config == null) throw new IOException("Empty config");
        config.validate(); return config;
    }
    public void save(MixerConfig config, boolean committed) throws IOException {
        config.validate();
        Files.createDirectories(directory);
        byte[] bytes = MixerConfig.GSON.toJson(config).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        atomic(directory.resolve("config.json"), bytes);
        if (committed) atomic(directory.resolve("config.last-good.json"), bytes);
    }
    public static void atomic(Path destination, byte[] bytes) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp-" + java.util.UUID.randomUUID());
        try {
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
