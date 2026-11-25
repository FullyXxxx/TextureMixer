package com.fullyx.texturemixer.ui.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** Geometry and colors shared by the modern and Legacy Fabric renderers. */
public final class MixerLayout {
    public static final int CONTROL = 18;
    public static final int INSET = 10;
    public static final int PACK_ROW = 30;
    public static final int PACK_GAP = 8;
    public static final int PACK_ACTION_STEP = 18;
    public static final int PACK_ACTION_WIDTH = 16;
    public static final int TEXT = 0xfff2f2f2;
    public static final int SECONDARY = 0xffc0c0c0;
    public static final int DISABLED = 0xff969696;
    public static final int DETAIL = 0x70202020;
    public static final int DIVIDER = 0x497c7c7c;
    public static final int FOCUS = 0xffdedede;
    public static final int WINDOW = 0xe0323232;
    public static final int WINDOW_BORDER = 0x927d7d7d;
    public static final int SCRIM = 0x4a000000;
    public static final int RADIUS = 8;

    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_\\-]+");
    private static final Pattern WORDS = Pattern.compile("\\s+");

    private MixerLayout() {}

    @FunctionalInterface
    public interface Fill {
        void draw(int x1, int y1, int x2, int y2, int color);
    }

    public enum Symbol { ADD, REMOVE, UP, DOWN, LEFT, RIGHT, CLOSE, FOLDER }

    public static int windowWidth(int viewport) {
        return Math.min(Math.min(560, Math.max(312, viewport * 4 / 5)), viewport - 20);
    }

    public static int windowHeight(int viewport) {
        return Math.min(Math.min(330, Math.max(222, viewport * 4 / 5)), viewport - 16);
    }

    private static int cornerInset(int radius, int row) {
        double distance = radius - row - .5;
        return (int) Math.ceil(radius - Math.sqrt(radius * radius - distance * distance));
    }

    private static void rect(Fill fill, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0 && color >>> 24 != 0) fill.draw(x, y, x + w, y + h, color);
    }

    public static void rounded(Fill fill, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
        radius = Math.min(radius, Math.min(w, h) / 2);
        rect(fill, x, y + radius, w, h - 2 * radius, color);
        for (int row = 0; row < radius; row++) {
            int inset = cornerInset(radius, row);
            rect(fill, x + inset, y + row, w - inset * 2, 1, color);
            rect(fill, x + inset, y + h - row - 1, w - inset * 2, 1, color);
        }
    }

    public static void frame(Fill fill, int x, int y, int w, int h, int radius, int face, int border) {
        if (w < 3 || h < 3) return;
        radius = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        rect(fill, x + 1, y + radius, w - 2, h - 2 * radius, face);
        rect(fill, x, y + radius, 1, h - 2 * radius, border);
        rect(fill, x + w - 1, y + radius, 1, h - 2 * radius, border);
        for (int row = 0; row < radius; row++) {
            int outer = cornerInset(radius, row);
            int inner = row == 0 ? (w + 1) / 2 : 1 + cornerInset(radius - 1, row - 1);
            frameRow(fill, x, y + row, w, outer, inner, face, border);
            frameRow(fill, x, y + h - row - 1, w, outer, inner, face, border);
        }
    }

    private static void frameRow(Fill fill, int x, int y, int w, int outer, int inner, int face, int border) {
        if (w <= 2 * inner) {
            rect(fill, x + outer, y, w - 2 * outer, 1, border);
            return;
        }
        rect(fill, x + outer, y, inner - outer, 1, border);
        rect(fill, x + inner, y, w - 2 * inner, 1, face);
        rect(fill, x + w - inner, y, inner - outer, 1, border);
    }

    public static void symbol(Fill fill, Symbol symbol, int x, int y, int color) {
        switch (symbol) {
            case ADD:
            case REMOVE:
                rect(fill, x - 3, y, 7, 1, color);
                if (symbol == Symbol.ADD) rect(fill, x, y - 3, 1, 7, color);
                break;
            case LEFT:
            case RIGHT:
            case UP:
            case DOWN:
                int direction = symbol == Symbol.LEFT || symbol == Symbol.UP ? -1 : 1;
                for (int offset = -3; offset <= 3; offset++) {
                    int tip = direction * (2 - Math.abs(offset));
                    if (symbol == Symbol.LEFT || symbol == Symbol.RIGHT) rect(fill, x + tip, y + offset, 1, 1, color);
                    else rect(fill, x + offset, y + tip, 1, 1, color);
                }
                break;
            case CLOSE:
                for (int offset = -2; offset <= 2; offset++) {
                    rect(fill, x + offset, y + offset, 1, 1, color);
                    rect(fill, x + offset, y - offset, 1, 1, color);
                }
                break;
            case FOLDER:
                rect(fill, x - 6, y - 5, 5, 2, color);
                rounded(fill, x - 6, y - 3, 13, 9, 2, color);
                break;
        }
    }

    public static int buttonFill(boolean active, boolean highlighted, boolean selected, boolean quiet, boolean primary) {
        if (primary && active) return highlighted ? 0xffededed : 0xffd7d7d7;
        if (!active) return quiet ? 0 : 0x70414141;
        if (selected) return 0xff646464;
        if (highlighted) return 0xff575757;
        return quiet ? 0 : 0xe4474747;
    }

    public static int navigationCapacity(int width, int[] widths) {
        int total = 0;
        for (int tab : widths) total += tab;
        if (total <= width) return widths.length;
        for (int count = widths.length - 1; count > 1; count--) {
            int widest = 0;
            for (int start = 0; start <= widths.length - count; start++) {
                int required = 0;
                for (int i = start; i < start + count; i++) required += widths[i];
                widest = Math.max(widest, required);
            }
            if (widest <= width - 36) return count;
        }
        return 1;
    }

    public static boolean matches(String value, String query) {
        String searchable = normalize(value);
        for (String word : WORDS.split(query.trim())) {
            if (!searchable.contains(normalize(word))) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return SEPARATORS.matcher(FORMATTING.matcher(value).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
