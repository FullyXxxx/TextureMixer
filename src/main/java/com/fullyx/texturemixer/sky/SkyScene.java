package com.fullyx.texturemixer.sky;

import com.fullyx.texturemixer.core.AssetId;
import java.util.*;

public record SkyScene(String dimension, List<Layer> layers, Map<AssetId,byte[]> textures,
                       boolean fromSource, List<String> diagnostics,Map<String,Blend> celestialBlends) {
    public SkyScene { layers=List.copyOf(layers);textures=Map.copyOf(textures);diagnostics=List.copyOf(diagnostics);celestialBlends=Map.copyOf(celestialBlends); }
    public SkyScene(String dimension,List<Layer> layers,Map<AssetId,byte[]> textures,boolean fromSource,List<String> diagnostics){this(dimension,layers,textures,fromSource,diagnostics,Map.of());}
    public static String namespacePrefix(String dimension){return "texturemixer_"+com.fullyx.texturemixer.core.Assets.hash(("sky|"+dimension).getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16)+"_";}
    private static double unit(double value){return Math.max(0,Math.min(1,value));}
    public enum Blend { ADD, SUBTRACT, MULTIPLY, DODGE, BURN, SCREEN, REPLACE, OVERLAY, ALPHA }
    public record Context(double ticks, long day, double rain, double thunder, String biome, double height) {}
    public record Range(int min,int max) { public boolean contains(int value) { return value>=min && value<=max; } }
    public record Layer(AssetId texture, Blend blend, boolean rotate, double speed,
                        double axisX,double axisY,double axisZ,
                        int startFadeIn,int endFadeIn,int startFadeOut,int endFadeOut,
                        List<Range> days,int daysLoop,Set<String> weather,Set<String> biomes,boolean excludeBiomes,
                        List<Range> heights,double transitionSeconds) {
        public double timeAlpha(double ticks) {
            if(startFadeIn<0) return 1;
            double t=mod(ticks-startFadeIn,24000);
            double a=mod(endFadeIn-startFadeIn,24000), b=mod(startFadeOut-startFadeIn,24000), c=mod(endFadeOut-startFadeIn,24000);
            if(t<a) return a==0?1:t/a;
            if(t<b) return 1;
            if(t<c) return c==b?0:1-(t-b)/(c-b);
            return 0;
        }
        public double condition(Context context) {
            if(!days.isEmpty() && days.stream().noneMatch(r->r.contains((int)Math.floorMod(context.day,daysLoop)))) return 0;
            if(!heights.isEmpty() && heights.stream().noneMatch(r->r.contains((int)Math.floor(context.height)))) return 0;
            boolean biome=biomes.contains(context.biome);
            if(!biomes.isEmpty() && (excludeBiomes?biome:!biome)) return 0;
            double result=0;
            if(weather.contains("clear")) result+=1-context.rain;
            if(weather.contains("rain")) result+=context.rain-context.thunder;
            if(weather.contains("thunder")) result+=context.thunder;
            return unit(result);
        }
        public double alpha(Context context) { return timeAlpha(context.ticks)*condition(context); }
        public double rotation(Context context) { return rotate ? (context.day+context.ticks/24000.0)*speed*Math.PI*2 : 0; }
    }
    public static double mod(double x,double n) { return ((x%n)+n)%n; }

    /** Reference equations for the GPU blend implementation; values are linear RGBA in [0,1]. */
    public static double blend(Blend mode,double previous,double texture,double textureAlpha,double visibility) {
        double a=unit(textureAlpha*visibility),s=unit(texture),d=unit(previous);
        double result=switch(mode) {
            case ADD -> d+s*a;
            case SUBTRACT -> d-s*a;
            case MULTIPLY -> d*(s*a+1-a);
            case DODGE -> d/Math.max(.00001,1-s*a);
            case BURN -> 1-Math.min(1,(1-d)/Math.max(.00001,s*a+1-a));
            case SCREEN -> 1-(1-d)*(1-s*a);
            case REPLACE -> visibility>0?s:d;
            case OVERLAY -> d*(2*s*a+1-a);
            case ALPHA -> s*a+d*(1-a);
        };
        return unit(result);
    }
}
