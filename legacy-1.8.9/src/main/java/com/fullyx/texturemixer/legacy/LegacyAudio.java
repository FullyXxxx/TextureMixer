package com.fullyx.texturemixer.legacy;

import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class LegacyAudio {
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TextureMixer audio preview");
        thread.setDaemon(true);
        return thread;
    });
    private Future<SoundBuffer> pending;
    private Clip clip;
    private int volume = 50;

    void play(byte[] ogg) {
        stop();
        pending = decoder.submit(() -> decode(ogg));
    }

    boolean playing() {
        return pending != null || (clip != null && clip.isRunning());
    }

    int volume() {
        return volume;
    }

    void changeVolume(int change) {
        volume = Math.max(0, Math.min(100, volume + change));
        if (clip != null) adjust(clip);
    }

    private void adjust(Clip sound) {
        if (sound.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) sound.getControl(FloatControl.Type.MASTER_GAIN);
            float value = volume == 0 ? gain.getMinimum() : (float) (20 * Math.log10(volume / 100.0));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), value)));
        }
        if (sound.isControlSupported(javax.sound.sampled.BooleanControl.Type.MUTE)) {
            javax.sound.sampled.BooleanControl mute = (javax.sound.sampled.BooleanControl)
                    sound.getControl(javax.sound.sampled.BooleanControl.Type.MUTE);
            mute.setValue(volume == 0);
        }
    }

    void tick() throws IOException {
        if (clip != null && !clip.isRunning() && clip.getFramePosition() >= clip.getFrameLength()) stop();
        if (pending == null || !pending.isDone()) return;
        try {
            SoundBuffer sound = pending.get();
            if (volume != 0) {
                clip = AudioSystem.getClip();
                clip.open(sound.audioFormat, sound.audioData, 0, sound.audioData.length);
                adjust(clip);
                clip.start();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Sound preview interrupted", error);
        } catch (ExecutionException | javax.sound.sampled.LineUnavailableException | IllegalArgumentException error) {
            stop();
            throw new IOException("Could not play sound preview", error);
        } finally {
            pending = null;
        }
    }

    void stop() {
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        if (clip != null) {
            clip.close();
            clip = null;
        }
    }

    void close() {
        stop();
        decoder.shutdownNow();
    }

    private static SoundBuffer decode(byte[] ogg) throws IOException {
        URL source = new URL(null, "texturemixer:preview.ogg", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {}

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(ogg);
                    }
                };
            }
        });
        CodecJOrbis codec = new CodecJOrbis();
        try {
            if (!codec.initialize(source)) throw new IOException("Invalid Ogg Vorbis file");
            AudioFormat format = codec.getAudioFormat();
            int frameSize = format.getFrameSize();
            if (frameSize < 1 || frameSize > 4 || format.getSampleRate() < 1 || format.getSampleRate() > 192000) {
                throw new IOException("Unsupported audio format");
            }
            int limit = (int) format.getSampleRate() * frameSize * 8;
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            while (!codec.endOfStream() && data.size() < limit && !Thread.currentThread().isInterrupted()) {
                SoundBuffer buffer = codec.read();
                if (buffer == null || buffer.audioData.length == 0) break;
                data.write(buffer.audioData, 0, Math.min(limit - data.size(), buffer.audioData.length));
            }
            if (data.size() == 0) throw new IOException("Sound file contains no audio");
            return new SoundBuffer(data.toByteArray(), format);
        } finally {
            codec.cleanup();
        }
    }
}
