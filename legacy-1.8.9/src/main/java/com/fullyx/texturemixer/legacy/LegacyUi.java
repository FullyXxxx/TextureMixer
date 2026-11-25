package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.ui.shared.MixerLayout;
import com.fullyx.texturemixer.ui.shared.MixerLayout.Symbol;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImageBackedTexture;

final class LegacyUi {
    private LegacyUi() {}

    static void rect(int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0 && color >>> 24 != 0) DrawableHelper.fill(x, y, x + width, y + height, color);
    }

    static void rounded(int x, int y, int w, int h, int radius, int color) {
        MixerLayout.rounded(DrawableHelper::fill, x, y, w, h, radius, color);
    }

    static void frame(int x, int y, int w, int h, int radius, int fill, int border) {
        MixerLayout.frame(DrawableHelper::fill, x, y, w, h, radius, fill, border);
    }

    static void symbol(Symbol symbol, int x, int y, int color) {
        MixerLayout.symbol(DrawableHelper::fill, symbol, x, y, color);
    }

    static void texture(NativeImageBackedTexture texture, int x, int y, int w, int h) {
        GlStateManager.enableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 771);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(texture.getGlId());
        DrawableHelper.drawTexture(x, y, 0, 0, w, h, w, h);
        GlStateManager.disableBlend();
    }

    static final class Button extends ButtonWidget {
        private final LegacyFont font;
        final Runnable action;
        boolean quiet;
        boolean primary;
        boolean selected;
        boolean focused;
        Symbol shape;
        String description;

        Button(LegacyFont font, int x, int y, int w, int h, String label, Runnable action) {
            super(0, x, y, w, h, label);
            this.font = font;
            this.action = action;
        }

        @Override
        public void render(MinecraftClient client, int mouseX, int mouseY) {
            if (!visible) return;
            hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            int fill = MixerLayout.buttonFill(active, hovered, selected, quiet, primary);
            if (focused) frame(x, y, width, height, 4, fill, MixerLayout.FOCUS);
            else rounded(x, y, width, height, 4, fill);
            int color = primary && active ? 0xff222222 : active ? MixerLayout.TEXT : MixerLayout.DISABLED;
            if (shape != null) symbol(shape, x + width / 2, y + height / 2, color);
            else font.center(message, x + width / 2, y + (height - 8) / 2, width - 6, color);
        }
    }
}
