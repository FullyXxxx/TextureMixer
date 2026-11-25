package com.fullyx.texturemixer.legacy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ResourcePackScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.ReloadableResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public final class LegacyPlatform {
    private static final Field DEFAULT_PACK = ReflectionHelper.findField(MinecraftClient.class,
            "field_110450_ap", "mcDefaultResourcePack");
    private static final Field ASSET_INDEX = ReflectionHelper.findField(DefaultResourcePack.class,
            "field_152781_b", "mapAssets");
    private static final Field PACK_PARENT = ReflectionHelper.findField(ResourcePackScreen.class,
            "field_146965_f", "parentScreen");

    private LegacyPlatform() {}

    static DefaultResourcePack defaultPack(MinecraftClient client) {
        return (DefaultResourcePack) read(DEFAULT_PACK, client);
    }

    @SuppressWarnings("unchecked")
    static Map<String, File> assetIndex(DefaultResourcePack pack) {
        return (Map<String, File>) read(ASSET_INDEX, pack);
    }

    static String version() {
        return Loader.instance().getIndexedModList().get("texturemixer").getVersion();
    }

    public static Screen openScreen(MinecraftClient client, Screen requested) {
        if (!(requested instanceof ResourcePackScreen)) return requested;
        Screen parent = (Screen) read(PACK_PARENT, requested);
        return new LegacyScreen(parent, LegacyMixer.get(client));
    }

    public static void reload(ReloadableResourceManager manager, List<ResourcePack> packs, MinecraftClient client) {
        LegacyMixer.get(client).reload(manager, packs);
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not read the Minecraft 1.8.9 resource pack field", failure);
        }
    }
}
