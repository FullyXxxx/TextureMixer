package com.fullyx.texturemixer.core;

import static com.fullyx.texturemixer.core.UiLanguage.tr;
import java.io.IOException;

/** Optional pack extensions do not invalidate the pack's ordinary native resources. */
public final class UnsupportedResources {
    public static String check(Assets source, MixerConfig.Kind kind, AssetId target) throws IOException {
        if (source == null) return null;
        for (AssetId file : source.ids()) {
            String path = file.path();
            if (!path.endsWith(".properties")) continue;
            if (kind == MixerConfig.Kind.ITEM && isExtension(path, "cit")) {
                return tr("包含可选 CIT 条件外观；当前混搭预览使用普通物品模型");
            }
            if (kind == MixerConfig.Kind.BLOCK && isExtension(path, "ctm")) {
                return tr("包含可选 CTM 连接纹理；当前混搭预览使用普通方块模型");
            }
        }
        return null;
    }

    private static boolean isExtension(String path, String extension) {
        return path.startsWith("optifine/" + extension + "/")
                || path.startsWith("mcpatcher/" + extension + "/");
    }
}
