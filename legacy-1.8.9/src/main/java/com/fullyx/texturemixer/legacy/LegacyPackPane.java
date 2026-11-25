package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.legacy.LegacyAssets.Pack;
import com.fullyx.texturemixer.ui.shared.MixerLayout;
import com.fullyx.texturemixer.ui.shared.MixerLayout.Symbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class LegacyPackPane {
    private static final int ROW = MixerLayout.PACK_ROW;
    private static final int STEP = MixerLayout.PACK_ACTION_STEP;
    private final LegacyScreen editor;
    private final LegacyMixer mixer;
    private final List<Entry> available = new ArrayList<>();
    private final List<Entry> selected = new ArrayList<>();
    private String group = "";
    private String query = "";
    private int x, y, width, height, column, capacity;
    private final int[] offsets = new int[2];
    private int dragging = -1;
    private int dragOrigin;

    private static final class Entry {
        final String id, name;
        final int count;
        Entry(String id, String name, int count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }
        boolean folder() { return count > 0; }
        boolean vanilla() { return id.equals(LegacyMixer.VANILLA) && !folder(); }
    }

    LegacyPackPane(LegacyScreen editor, LegacyMixer mixer) {
        this.editor = editor;
        this.mixer = mixer;
    }

    void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        column = (width - MixerLayout.PACK_GAP) / 2;
        capacity = Math.max(1, height / ROW);
        refresh(query);
    }

    void refresh(String query) {
        if (!this.query.equals(query)) offsets[0] = offsets[1] = 0;
        this.query = query;
        available.clear();
        selected.clear();
        String prefix = group.isEmpty() ? "" : group + "/";
        Map<String, Integer> folders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Entry> files = new ArrayList<>();
        List<String> stack = editor.stack();
        for (Pack pack : mixer.packs()) {
            if (!pack.name.startsWith(prefix)) continue;
            String relative = pack.name.substring(prefix.length());
            int slash = relative.indexOf('/');
            if (slash >= 0) {
                String folder = relative.substring(0, slash);
                folders.merge(folder, 1, Integer::sum);
            } else if (!stack.contains(pack.name) && MixerLayout.matches(relative, query)) {
                files.add(new Entry(pack.name, relative, 0));
            }
        }
        for (Map.Entry<String, Integer> folder : folders.entrySet()) {
            if (MixerLayout.matches(folder.getKey(), query)) {
                available.add(new Entry(prefix + folder.getKey(), folder.getKey(), folder.getValue()));
            }
        }
        available.addAll(files);
        for (String id : stack) selected.add(new Entry(id, name(id), 0));
        selected.add(new Entry(LegacyMixer.VANILLA, "Vanilla", 0));
        clamp(0);
        clamp(1);
    }

    static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1).replaceAll("\\s+", " ").trim();
    }

    private List<Entry> entries(int side) { return side == 0 ? available : selected; }

    private int columnX(int side) { return x + side * (column + MixerLayout.PACK_GAP); }

    private int maxOffset(int side) { return Math.max(0, entries(side).size() - capacity); }

    private void clamp(int side) { offsets[side] = Math.max(0, Math.min(maxOffset(side), offsets[side])); }

    void draw(int mouseX, int mouseY) {
        int right = columnX(1);
        if (group.isEmpty()) {
            editor.text(editor.tr("Available Packs"), x + 2, y - 12, column - 4, MixerLayout.SECONDARY);
        } else {
            LegacyUi.rounded(x, y - 17, 32, 15, 4, 0x70606060);
            editor.center(editor.tr("Back"), x + 16, y - 13, 30, MixerLayout.TEXT);
            editor.text(name(group), x + 39, y - 12, column - 40, MixerLayout.SECONDARY);
        }
        editor.text(editor.tr("Selected · Top Wins"), right + 2, y - 12, column - 4, MixerLayout.SECONDARY);
        for (int side = 0; side < 2; side++) {
            int left = columnX(side);
            LegacyUi.rounded(left, y, column, height, 5, MixerLayout.DETAIL);
            List<Entry> entries = entries(side);
            int stop = Math.min(entries.size(), offsets[side] + capacity);
            for (int index = offsets[side]; index < stop; index++) {
                drawRow(entries.get(index), side, index, left, y + (index - offsets[side]) * ROW, mouseX, mouseY);
            }
            drawScrollbar(side);
        }
        if (available.isEmpty()) {
            editor.text(editor.tr(query.isEmpty() ? "No available resource packs" : "No matching results"),
                    x + 8, y + 10, column - 16, MixerLayout.SECONDARY);
        }
    }

    private void drawRow(Entry entry, int side, int index, int left, int top, int mx, int my) {
        int count = entry.folder() || entry.vanilla() ? 0 : side == 1 ? 3 : 1;
        int actionsX = actionsStart(side, count);
        int end = count == 0 ? left + column - 11 : actionsX - 3;
        boolean hovered = inside(mx, my, left + 2, top + 1, end - left - 2, ROW - 2);
        if (hovered && !entry.vanilla()) LegacyUi.rounded(left + 2, top + 1, end - left - 2, ROW - 2, 4, 0x70606060);
        int iconSize = column < 180 ? 18 : 22;
        int ix = left + 6;
        int iy = top + (ROW - iconSize) / 2;
        if (entry.folder()) LegacyUi.symbol(Symbol.FOLDER, ix + iconSize / 2, iy + iconSize / 2, MixerLayout.SECONDARY);
        else editor.drawPackIcon(entry.id, ix, iy, iconSize);
        String subtitle = entry.folder() ? entry.count + editor.tr(entry.count == 1 ? " resource pack" : " resource packs")
                : entry.vanilla() ? editor.tr("Vanilla / server base") : mixer.pack(entry.id) == null ? editor.tr("Source missing") : "";
        int tx = ix + iconSize + 5;
        editor.text(entry.name, tx, top + (subtitle.isEmpty() ? 11 : 6), end - tx - 3, MixerLayout.TEXT);
        if (!subtitle.isEmpty()) editor.text(subtitle, tx, top + 17, end - tx - 3, MixerLayout.SECONDARY);
        if (hovered) editor.hint(entry.name);

        if (count == 1) action(Symbol.ADD, actionsX, top, mx, my, true);
        if (count == 3) {
            action(Symbol.UP, actionsX, top, mx, my, index > 0);
            action(Symbol.DOWN, actionsX + STEP, top, mx, my, index + 2 < selected.size());
            action(Symbol.REMOVE, actionsX + STEP * 2, top, mx, my, true);
        }
    }

    private static void action(Symbol symbol, int x, int top, int mx, int my, boolean active) {
        int y = top + (ROW - MixerLayout.CONTROL) / 2;
        int w = MixerLayout.PACK_ACTION_WIDTH;
        boolean hover = inside(mx, my, x, y, w, MixerLayout.CONTROL);
        LegacyUi.rounded(x, y, w, MixerLayout.CONTROL, 4, MixerLayout.buttonFill(active, hover, false, false, false));
        LegacyUi.symbol(symbol, x + w / 2, y + MixerLayout.CONTROL / 2, active ? MixerLayout.TEXT : MixerLayout.DISABLED);
    }

    private int actionsStart(int side, int count) { return columnX(side) + column - 11 - count * STEP + 2; }

    boolean click(int mx, int my) {
        if (!group.isEmpty() && inside(mx, my, x, y - 17, 32, 15)) {
            group = group.contains("/") ? group.substring(0, group.lastIndexOf('/')) : "";
            offsets[0] = 0;
            editor.clearSearch();
            return true;
        }
        int side = side(mx, my);
        if (side < 0) return false;
        if (maxOffset(side) > 0 && mx >= columnX(side) + column - 10) {
            int top = thumbY(side), size = thumbHeight(side);
            dragging = side;
            dragOrigin = my >= top && my < top + size ? my - top : size / 2;
            drag(my);
            return true;
        }
        int row = (my - y) / ROW;
        List<Entry> entries = entries(side);
        int index = row + offsets[side];
        if (row >= capacity || index >= entries.size()) return true;
        Entry entry = entries.get(index);
        if (entry.vanilla()) return true;
        if (entry.folder()) {
            group = entry.id;
            offsets[0] = 0;
            editor.clearSearch();
            return true;
        }
        int actionCount = side == 0 ? 1 : 3;
        int offset = mx - actionsStart(side, actionCount);
        if (offset >= 0 && offset / STEP < actionCount && offset % STEP < MixerLayout.PACK_ACTION_WIDTH
                && (my - y) % ROW >= (ROW - MixerLayout.CONTROL) / 2
                && (my - y) % ROW < (ROW + MixerLayout.CONTROL) / 2) {
            if (side == 0) editor.addPack(entry.id);
            else if (offset / STEP == 2) editor.removePack(entry.id);
            else {
                editor.movePack(entry.id, offset / STEP == 0 ? -1 : 1);
                int position = editor.stack().indexOf(entry.id);
                if (position < offsets[1]) offsets[1] = position;
                if (position >= offsets[1] + capacity) offsets[1] = position - capacity + 1;
            }
        } else {
            editor.inspectPack(entry.id, side == 1);
        }
        return true;
    }

    boolean scroll(int mx, int my, int direction) {
        int side = side(mx, my);
        if (side < 0) return false;
        offsets[side] += direction;
        clamp(side);
        return true;
    }

    void release() { dragging = -1; }

    void drag(int my) {
        if (dragging < 0) return;
        int travel = Math.max(1, height - 4 - thumbHeight(dragging));
        offsets[dragging] = Math.round((my - y - 2 - dragOrigin) * maxOffset(dragging) / (float) travel);
        clamp(dragging);
    }

    private void drawScrollbar(int side) {
        if (maxOffset(side) == 0) return;
        int track = columnX(side) + column - 5;
        LegacyUi.rect(track, y + 2, 2, height - 4, 0x90707070);
        LegacyUi.rounded(track - 1, thumbY(side), 4, thumbHeight(side), 2,
                dragging == side ? 0xffd7d7d7 : 0xff989898);
    }

    private int thumbHeight(int side) {
        return Math.max(12, (height - 4) * capacity / Math.max(1, entries(side).size()));
    }

    private int thumbY(int side) {
        return y + 2 + (height - 4 - thumbHeight(side)) * offsets[side] / Math.max(1, maxOffset(side));
    }

    private int side(int mx, int my) {
        if (inside(mx, my, x, y, column, height)) return 0;
        if (inside(mx, my, columnX(1), y, column, height)) return 1;
        return -1;
    }

    static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
