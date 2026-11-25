package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.legacy.LegacyAssets.Category;
import com.fullyx.texturemixer.legacy.LegacyAssets.Target;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Resolve names from the installed 1.8.9 language files when an asset maps to a registry entry. */
final class LegacyNames {
    private final Map<String, String> english;
    private final Map<String, String> chinese;
    private final Map<String, String> keys = new HashMap<>();

    LegacyNames(DefaultResourcePack vanilla, Iterable<Target> targets) {
        english = language(vanilla, "en_US");
        chinese = language(vanilla, "zh_CN");
        for (Target target : targets) {
            String key = translationKey(target);
            if (key != null) keys.put(target.id, key + ".name");
        }
    }

    String name(Target target, boolean useChinese) {
        String key = keys.get(target.id);
        String fallback = english.getOrDefault(key, target.title);
        return useChinese ? chinese.getOrDefault(key, fallback) : fallback;
    }

    private static String translationKey(Target target) {
        int start = target.id.lastIndexOf('/') + 1;
        int end = target.id.lastIndexOf('.');
        Identifier id = new Identifier("minecraft", target.id.substring(start, end));
        if (target.category == Category.ITEM && Item.REGISTRY.containsKey(id)) {
            Item item = Item.REGISTRY.get(id);
            if (item != null) return item.getTranslationKey();
        } else if (target.category == Category.BLOCK && Block.REGISTRY.containsKey(id)) {
            Block block = Block.REGISTRY.get(id);
            if (block != null) return block.getTranslationKey();
        }
        return null;
    }

    private static Map<String, String> language(DefaultResourcePack vanilla, String code) {
        Map<String, String> translated = new HashMap<>();
        Identifier id = new Identifier("minecraft", "lang/" + code + ".lang");
        try (InputStream file = vanilla.open(id);
             BufferedReader reader = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (!line.startsWith("#") && separator > 0) {
                    translated.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        } catch (IOException ignored) {
            // The raw asset name remains searchable if a launcher has omitted its language files.
        }
        return translated;
    }
}
