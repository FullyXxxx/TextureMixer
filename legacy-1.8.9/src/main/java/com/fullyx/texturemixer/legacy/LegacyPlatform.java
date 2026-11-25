package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.legacy.mixin.DefaultPackAccessor;
import com.fullyx.texturemixer.legacy.mixin.MinecraftAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DefaultResourcePack;

import java.io.File;
import java.util.Map;

final class LegacyPlatform {
    private LegacyPlatform() {}

    static DefaultResourcePack defaultPack(MinecraftClient client) {
        return ((MinecraftAccessor) client).texturemixer$defaultPack();
    }

    static Map<String, File> assetIndex(DefaultResourcePack pack) {
        return ((DefaultPackAccessor) pack).texturemixer$assetIndex();
    }

    static String version() {
        return FabricLoader.getInstance().getModContainer("texturemixer").get()
                .getMetadata().getVersion().getFriendlyString();
    }
}
