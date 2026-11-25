package com.fullyx.texturemixer.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Barlow from the same font resource used by the modern TextureMixer screen. */
final class LegacyFont {
    private static final int SCALE = 3;
    private static final int CELL = 36;
    private static final int SIZE = 576;
    private static final String FORMATS = "0123456789abcdefklmnor";
    private final TextRenderer fallback;
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private NativeImageBackedTexture texture;

    private static final class Glyph {
        final int u, v, advance;
        Glyph(int u, int v, int advance) {
            this.u = u;
            this.v = v;
            this.advance = advance;
        }
    }

    LegacyFont(TextRenderer fallback) {
        this.fallback = fallback;
        try (InputStream font = getClass().getResourceAsStream("/assets/texturemixer/font/barlow-medium.ttf")) {
            if (font != null) build(Font.createFont(Font.TRUETYPE_FONT, font).deriveFont(30f));
        } catch (IOException | java.awt.FontFormatException error) {
            glyphs.clear();
        }
    }

    private void build(Font font) {
        BufferedImage pixels = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = pixels.createGraphics();
        try {
            graphics.setFont(font);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = graphics.getFontMetrics();
            for (char character = 32; character < 256; character++) append(graphics, metrics, character);
            for (char character : "…·’→×–—".toCharArray()) append(graphics, metrics, character);
        } finally {
            graphics.dispose();
        }
        texture = new NativeImageBackedTexture(pixels);
        texture.setFilter(true, false);
    }

    private void append(Graphics2D graphics, FontMetrics metrics, char character) {
        int index = glyphs.size();
        int u = index % 16 * CELL;
        int v = index / 16 * CELL;
        graphics.drawString(Character.toString(character), u + 2, v + 25);
        glyphs.put(character, new Glyph(u, v, metrics.charWidth(character)));
    }

    int width(String value) { return Math.round(measure(value)); }

    private float measure(String value) {
        float width = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '§' && i + 1 < value.length()) {
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(character);
            width += glyph == null ? fallback.getCharWidth(character) : glyph.advance / (float) SCALE;
        }
        return width;
    }

    String fit(String value, int limit) {
        if (limit <= 0) return "";
        if (width(value) <= limit) return value;
        int budget = limit - width("…");
        if (budget < 0) return "";
        StringBuilder result = new StringBuilder();
        float used = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '§' && i + 1 < value.length()) {
                result.append(character).append(value.charAt(++i));
                continue;
            }
            Glyph glyph = glyphs.get(character);
            used += glyph == null ? fallback.getCharWidth(character) : glyph.advance / (float) SCALE;
            if (used > budget) break;
            result.append(character);
        }
        return result.append("…").toString();
    }

    void center(String value, int x, int y, int limit, int color) {
        String visible = fit(value, limit);
        draw(visible, x - width(visible) / 2, y, color);
    }

    void draw(String value, int x, int y, int originalColor) {
        if (texture == null || value.isEmpty()) {
            fallback.draw(value, x, y, originalColor);
            return;
        }
        float position = x;
        int color = originalColor;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '§' && i + 1 < value.length()) {
                int code = FORMATS.indexOf(Character.toLowerCase(value.charAt(++i)));
                if (code >= 0 && code < 16) color = (originalColor & 0xff000000) | fallback.getColor(FORMATS.charAt(code));
                if (code == FORMATS.length() - 1) color = originalColor;
                continue;
            }
            Glyph glyph = glyphs.get(character);
            if (glyph == null) {
                fallback.draw(Character.toString(character), position, y, color, false);
                position += Math.max(0, fallback.getCharWidth(character));
                continue;
            }
            GlStateManager.enableTexture();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 771);
            GlStateManager.disableAlphaTest();
            GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                    (color & 255) / 255f, (color >>> 24) / 255f);
            GlStateManager.bindTexture(texture.getGlId());
            GlStateManager.pushMatrix();
            GlStateManager.translate(position - 2f / SCALE, y - 1, 0);
            GlStateManager.scale(1f / SCALE, 1f / SCALE, 1);
            DrawableHelper.drawTexture(0, 0, glyph.u, glyph.v, CELL, CELL, SIZE, SIZE);
            GlStateManager.popMatrix();
            position += glyph.advance / (float) SCALE;
        }
        GlStateManager.enableAlphaTest();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    void close() {
        if (texture != null) texture.clearGlId();
    }
}
