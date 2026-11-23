package com.fullyx.texturemixer.core;

import com.google.gson.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Reject invalid animations before the native sprite loader silently skips their bad frames. */
public final class TextureMetadata {
    private TextureMetadata() {}
    public static void validate(AssetId texture,byte[] image,JsonObject metadata)throws IOException {
        if(!metadata.has("animation"))return;
        try {
            JsonObject animation=metadata.getAsJsonObject("animation");
            int width,height;
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(image))){
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid animation image: "+texture);
                var reader=readers.next();try{reader.setInput(input);width=reader.getWidth(0);height=reader.getHeight(0);}finally{reader.dispose();}
            }
            int fw,fh;
            if(animation.has("width")){fw=positive(animation.get("width"));fh=animation.has("height")?positive(animation.get("height")):height;}
            else if(animation.has("height")){fw=width;fh=positive(animation.get("height"));}
            else fw=fh=Math.min(width,height);
            if(width%fw!=0||height%fh!=0)throw new IllegalArgumentException("Image dimensions must be divisible by animation frame dimensions");
            int frames=(width/fw)*(height/fh);
            if(animation.has("frametime"))positive(animation.get("frametime"));
            if(animation.has("interpolate")&&(!animation.get("interpolate").isJsonPrimitive()||!animation.getAsJsonPrimitive("interpolate").isBoolean()))
                throw new IllegalArgumentException("interpolate must be boolean");
            if(animation.has("frames"))for(JsonElement frame:animation.getAsJsonArray("frames")){
                int index;
                if(frame.isJsonObject()){
                    JsonObject object=frame.getAsJsonObject();index=integer(object.get("index"));
                    if(object.has("time"))positive(object.get("time"));
                }else index=integer(frame);
                if(index<0||index>=frames)throw new IllegalArgumentException("Animation frame index "+index+" outside 0.."+(frames-1));
            }
        }catch(RuntimeException error){throw new IOException("Invalid texture animation "+texture+": "+error.getMessage(),error);}
    }
    private static int integer(JsonElement value){
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer");
        return value.getAsBigDecimal().toBigIntegerExact().intValueExact();
    }
    private static int positive(JsonElement value){int number=integer(value);if(number<=0)throw new IllegalArgumentException("Expected positive integer");return number;}
}
