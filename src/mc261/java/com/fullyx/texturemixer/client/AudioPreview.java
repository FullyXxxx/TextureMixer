package com.fullyx.texturemixer.client;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.*;
import javax.sound.sampled.*;
import net.minecraft.client.sounds.JOrbisAudioStream;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** An isolated, cancellable audition of the selected source's first sound variant. */
public final class AudioPreview implements AutoCloseable {
    private final MixerService service;
    private final AtomicInteger generation=new AtomicInteger();
    private volatile SourceDataLine line;
    private volatile double volume=.5;
    public double volume(){return volume;}
    public void setVolume(double value){volume=Math.clamp(value,0,1);}
    public volatile boolean playing;
    public volatile String status="";
    public AudioPreview(MixerService service){this.service=service;}
    public void play(String event,String source) {
        stop();int token=generation.get();playing=true;status=tr("Loading audio…");
        service.workers.execute(()->{
            SourceDataLine current=null;
            try {
                var sample=new ResourceCompiler(Generation.view(source,service.sources,service.vanilla),"audition").sample(event);
                if(token!=generation.get())return;
                if(sample==null){status=tr("This event is silent");return;}
                try(JOrbisAudioStream stream=new JOrbisAudioStream(new ByteArrayInputStream(sample.data()))) {
                    AudioFormat format=stream.getFormat();
                    current=AudioSystem.getSourceDataLine(format);
                    int frameSize=format.getFrameSize();
                    int bufferBytes=Math.max(1,(int)(format.getFrameRate()*.04))*frameSize;
                    current.open(format,bufferBytes);
                    synchronized(this){if(token!=generation.get())return;line=current;}
                    current.start();status=tr("Playing · ")+event;
                    while(token==generation.get()) {
                        ByteBuffer buffer=stream.read(Math.max(frameSize,bufferBytes/4/frameSize*frameSize));
                        if(!buffer.hasRemaining())break;
                        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
                        applyGain(bytes,volume);
                        int written=0;
                        while(written<bytes.length&&token==generation.get())written+=current.write(bytes,written,bytes.length-written);
                    }
                    if(token==generation.get())current.drain();
                }
            } catch(Exception error) {if(token==generation.get())status=tr("Audio preview failed · ")+MixerService.message(error);}
            finally {
                if(current!=null){current.stop();current.close();}
                if(token==generation.get()){line=null;playing=false;if(status.startsWith(tr("Playing")))status=tr("Preview finished");}
            }
        });
    }
    static void applyGain(byte[] bytes,double gain){
        if(gain==1)return;
        // OggAudioStream returns signed little-endian 16-bit PCM.
        for(int index=0;index+1<bytes.length;index+=2){
            short original=(short)((bytes[index]&255)|(bytes[index+1]<<8));
            int scaled=(int)(original*gain);
            bytes[index]=(byte)scaled;bytes[index+1]=(byte)(scaled>>8);
        }
    }
    public synchronized void stop(){generation.incrementAndGet();SourceDataLine active=line;line=null;if(active!=null){active.stop();active.flush();active.close();}playing=false;status="";}
    @Override public void close(){stop();}
}
