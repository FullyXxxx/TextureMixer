package com.fullyx.texturemixer.legacy;

import net.minecraft.client.texture.NativeImageBackedTexture;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

final class LegacyImages {
    private static final int LIMIT = 112;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TextureMixer thumbnails");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Preview> images = new LinkedHashMap<>(32, .75f, true);

    static final class Preview {
        Future<BufferedImage> pending;
        NativeImageBackedTexture texture;
        int width, height;
        boolean failed;

        Preview(Future<BufferedImage> pending) { this.pending = pending; }
    }

    Preview get(String key, Callable<byte[]> bytes) {
        Preview picture = images.get(key);
        if (picture != null) return picture;
        Iterator<Preview> oldest = images.values().iterator();
        if (images.size() >= LIMIT && oldest.hasNext()) {
            release(oldest.next());
            oldest.remove();
        }
        picture = new Preview(decoder.submit(() -> {
            byte[] data = bytes.call();
            return data == null ? null : LegacyMixer.thumbnail(data);
        }));
        images.put(key, picture);
        return picture;
    }

    void tick() {
        int completed = 0;
        for (Preview picture : images.values()) {
            if (picture.pending == null || !picture.pending.isDone()) continue;
            try {
                BufferedImage image = picture.pending.get();
                if (image != null) {
                    picture.width = image.getWidth();
                    picture.height = image.getHeight();
                    picture.texture = new NativeImageBackedTexture(image);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | RuntimeException error) {
                picture.failed = true;
            } finally {
                picture.pending = null;
            }
            if (++completed == 6) return;
        }
    }

    boolean draw(Preview picture, int x, int y, int width, int height) {
        if (picture.texture == null) return false;
        double scale = Math.min((double) width / picture.width, (double) height / picture.height);
        int w = Math.max(1, (int) (picture.width * scale));
        int h = Math.max(1, (int) (picture.height * scale));
        LegacyUi.texture(picture.texture, x + (width - w) / 2, y + (height - h) / 2, w, h);
        return true;
    }

    private void release(Preview picture) {
        if (picture.pending != null) picture.pending.cancel(true);
        if (picture.texture != null) picture.texture.clearGlId();
    }

    void clear() {
        images.values().forEach(this::release);
        images.clear();
    }

    void close() {
        clear();
        decoder.shutdownNow();
    }
}
