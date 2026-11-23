package com.fullyx.texturemixer.smoke;

import com.fullyx.texturemixer.core.UiLanguage;
import com.fullyx.texturemixer.ui.FlatButton;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Keep historical interaction checks readable while the UI uses its selected locale. */
public final class SmokeLabels {
    private static final Map<String,String> ENGLISH=load();
    private SmokeLabels(){}

    static FlatButton packAction(MinecraftClient client, String label, String pack) {
        String narration = UiLanguage.tr(label) + " · " + pack;
        return client.currentScreen.children().stream().filter(FlatButton.class::isInstance).map(FlatButton.class::cast)
                .filter(button -> narration.equals(button.narrationLabel)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing pack action " + narration));
    }
    public static String text(String original){
        if(original.equals("资源包"))return "Resourcepack";
        if(UiLanguage.language().equals("zh_cn"))return UiLanguage.tr(original);
        String result=original;
        for(var entry:ENGLISH.entrySet())result=result.replace(entry.getKey(),entry.getValue());
        return result;
    }
    private static Map<String,String> load(){
        try(var input=SmokeLabels.class.getResourceAsStream("/assets/texturemixer/i18n/en_us.json")){
            var json=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,String> values=new LinkedHashMap<>();
            json.entrySet().stream().filter(entry->entry.getKey().matches(".*[\\p{IsHan}].*"))
                    .sorted(Comparator.comparingInt((Map.Entry<String,JsonElement> entry)->entry.getKey().length()).reversed())
                    .forEach(entry->{values.put(entry.getKey(),entry.getValue().getAsString());values.putIfAbsent(entry.getKey().strip(),entry.getValue().getAsString().strip());});
            return values;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
}
