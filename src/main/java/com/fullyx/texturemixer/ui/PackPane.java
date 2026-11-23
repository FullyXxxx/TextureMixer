package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.MixerConfig;
import com.fullyx.texturemixer.core.SearchQuery;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Available and selected packs stay visible together, with dedicated row actions. */
final class PackPane implements AutoCloseable {
    private record Entry(String id, boolean folder) {}
    private static final int ROW_HEIGHT = 30, COLUMN_GAP = 8, ACTION_WIDTH = 16, ACTION_STEP = 18;
    private final MixerScreen parent;
    private final List<FlatButton> rows = new ArrayList<>();
    private PackIcons icons;
    private String group, query = "";
    private int x, y, width, height, columnWidth, pageSize, availableOffset, selectedOffset;
    private List<Entry> available = List.of();
    private List<String> selected = List.of();
    private String pendingFocus;

    PackPane(MixerScreen parent) { this.parent = parent; }

    void layout(int x, int y, int width, int height, String query) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        columnWidth = (width - COLUMN_GAP) / 2;
        pageSize = Math.max(1, height / ROW_HEIGHT);
        if (icons == null) icons = new PackIcons(parent.service);
        refresh(query);
        parent.addPackWidget(new VerticalScrollbar(x + columnWidth - 9, y + 2, height - 4,
                () -> availableOffset, () -> available.size(), () -> pageSize, 1, value -> {
                    availableOffset = (int)Math.round(value);
                    refresh(this.query);
                }));
        parent.addPackWidget(new VerticalScrollbar(right() + columnWidth - 9, y + 2, height - 4,
                () -> selectedOffset, () -> selected.size(), () -> pageSize, 1, value -> {
                    selectedOffset = (int)Math.round(value);
                    refresh(this.query);
                }));
    }

    void refresh(String query) {
        if (!this.query.equals(query)) {
            availableOffset = selectedOffset = 0;
            this.query = query;
        }
        rows.forEach(parent::removePackWidget);
        rows.clear();
        var entries = new ArrayList<Entry>();
        if (group == null) {
            for (var folder : parent.service.library.groups()) entries.add(new Entry(folder.id(), true));
            for (String id : parent.service.library.rootSourceIds(parent.draft)) entries.add(new Entry(id, false));
        } else {
            for (String id : parent.service.library.groupSourceIds(group, parent.draft)) entries.add(new Entry(id, false));
        }
        available = entries.stream().filter(entry -> entry.folder || !parent.draft.mainStack().contains(entry.id))
                .filter(entry -> SearchQuery.matches(name(entry), query)).toList();
        var enabled = new ArrayList<>(parent.draft.mainStack());
        enabled.add(MixerConfig.VANILLA);
        selected = List.copyOf(enabled);
        availableOffset = Math.clamp(availableOffset, 0, Math.max(0, available.size() - pageSize));
        selectedOffset = Math.clamp(selectedOffset, 0, Math.max(0, selected.size() - pageSize));
        for (int index = availableOffset; index < Math.min(available.size(), availableOffset + pageSize); index++) {
            Entry entry = available.get(index);
            addRow(x, y + (index - availableOffset) * ROW_HEIGHT, entry.id, entry.folder, false, name(entry));
        }
        if (group != null) {
            add(new FlatButton(x, y - 17, 34, 15, Text.literal(tr("返回")), button -> {
                group = null;
                availableOffset = 0;
                parent.clearPackSearch();
                refresh("");
            }).quiet());
        }
        for (int index = selectedOffset; index < Math.min(selected.size(), selectedOffset + pageSize); index++) {
            String id = selected.get(index);
            addRow(right(), y + (index - selectedOffset) * ROW_HEIGHT, id, false, true, parent.sourceName(id));
        }
    }

    private int right() { return x + columnWidth + COLUMN_GAP; }

    private void addRow(int columnX, int y, String id, boolean folder, boolean selected, String name) {
        boolean vanilla = id.equals(MixerConfig.VANILLA);
        int actionCount = folder || vanilla ? 0 : selected ? 3 : 1;
        int actionsRight = columnX + columnWidth - 11;
        int actionsX = actionsRight - actionCount * ACTION_STEP + 2;
        int rowWidth = (actionCount == 0 ? actionsRight : actionsX - 3) - columnX - 2;
        var row = new PackRow(columnX + 2, y + 1, rowWidth, id, folder, selected, name);
        add(row);
        if (actionCount == 0) return;
        int actionY = y + (ROW_HEIGHT - UiDraw.CONTROL) / 2;
        if (selected) {
            int position = parent.draft.mainStack().indexOf(id);
            action(actionsX, actionY, name, "上移，提高优先级", UiDraw.Symbol.UP, position > 0, () -> move(id, -1));
            action(actionsX + ACTION_STEP, actionY, name, "下移，降低优先级", UiDraw.Symbol.DOWN,
                    position + 1 < parent.draft.mainStack().size(), () -> move(id, 1));
            action(actionsX + 2 * ACTION_STEP, actionY, name, "移除", UiDraw.Symbol.REMOVE, true, row::toggle);
        } else {
            action(actionsX, actionY, name, "添加", UiDraw.Symbol.ADD, row.canEnable(), row::toggle);
        }
    }

    private void action(int x, int y, String name, String label, UiDraw.Symbol symbol, boolean available, Runnable run) {
        var button = new FlatButton(x, y, ACTION_WIDTH, UiDraw.CONTROL, Text.literal(tr(label)), pressed -> {
            if (!parent.service.busy && !parent.libraryImporting()) run.run();
        }).symbol(symbol);
        button.narrationLabel = tr(label) + " · " + name;
        button.setTooltip(Tooltip.of(Text.literal(button.narrationLabel)));
        button.active = available;
        add(button);
    }

    private void add(FlatButton button) {
        rows.add(button);
        parent.addPackWidget(button);
    }

    private void move(String source, int direction) {
        parent.moveMain(source, direction);
        int position = parent.draft.mainStack().indexOf(source);
        if (position < selectedOffset) selectedOffset = position;
        if (position >= selectedOffset + pageSize) selectedOffset = position - pageSize + 1;
        refresh(query);
        pendingFocus = source;
    }

    boolean restoreFocus() {
        String source = pendingFocus;
        pendingFocus = null;
        if (source == null) return false;
        for (FlatButton row : rows) {
            if (row instanceof PackRow pack && pack.source.equals(source)) {
                parent.setFocused(row);
                return true;
            }
        }
        return false;
    }

    private String name(Entry entry) {
        if (!entry.folder) return parent.sourceName(entry.id);
        var folder = parent.service.library.group(entry.id);
        return folder == null ? tr("文件夹失联") : MixerScreen.plainName(folder.name());
    }

    void draw(DrawContext context) {
        var font = parent.textRenderer;
        int headingInset = group == null ? 2 : 39;
        context.drawText(font, UiDraw.ellipsis(font, group == null ? tr("可用的资源包") : tr("文件夹"), columnWidth - headingInset),
                x + headingInset, y - 12, UiDraw.SECONDARY, false);
        context.drawText(font, UiDraw.ellipsis(font, tr("已选资源包 · 上方优先"), columnWidth - 4), right() + 2, y - 12, UiDraw.SECONDARY, false);
        UiDraw.rounded(context, x, y, columnWidth, height, 5, UiDraw.DETAIL);
        UiDraw.rounded(context, right(), y, columnWidth, height, 5, UiDraw.DETAIL);
        if (available.isEmpty()) {
            context.drawText(parent.textRenderer, parent.textRenderer.trimToWidth(query.isBlank() ? tr("没有可用的资源包") : tr("没有匹配结果"), columnWidth - 16),
                    x + 8, y + 10, 0xffa0a0a0, false);
        }
    }

    boolean scroll(double mx, double my, double vertical) {
        if (mx < x || mx >= x + width || my < y || my >= y + height || mx >= x + columnWidth && mx < right()) return false;
        if (mx < x + columnWidth) {
            availableOffset = Math.clamp(availableOffset - (int)Math.signum(vertical), 0, Math.max(0, available.size() - pageSize));
        } else {
            selectedOffset = Math.clamp(selectedOffset - (int)Math.signum(vertical), 0, Math.max(0, selected.size() - pageSize));
        }
        refresh(query);
        return true;
    }

    @Override public void close() {
        if (icons != null) {
            icons.close();
            icons = null;
        }
    }

    private final class PackRow extends FlatButton {
        private final String source;
        private final boolean folder, selected, vanilla;

        PackRow(int x, int y, int width, String source, boolean folder, boolean selected, String name) {
            super(x, y, width, ROW_HEIGHT - 2, net.minecraft.text.Text.literal(name), button -> {});
            this.source = source;
            this.folder = folder;
            this.selected = selected;
            vanilla = source.equals(MixerConfig.VANILLA);
            String detail = folder ? name : vanilla ? tr("原版与服务器资源基础") : parent.draft.sources.get(source).canonicalRoot();
            setTooltip(Tooltip.of(net.minecraft.text.Text.literal(name + "\n" + detail)));
            narrationLabel = name + (vanilla ? tr("，基础资源") : selected ? tr("，Enter移除，Shift加方向键排序") : tr("，Enter启用"));
        }

        private boolean canEnable() {
            var pack = parent.service.sources.get(source);
            return pack != null;
        }

        private void toggle() {
            if (parent.service.busy || parent.libraryImporting() || vanilla) return;
            if (selected) parent.removeMain(source);
            else if (canEnable()) parent.previewMain(source, parent);
        }

        @Override protected void mixerPress(Runnable nativePress) {
            if (parent.service.busy || parent.libraryImporting()) return;
            if (folder) {
                group = source;
                availableOffset = 0;
                parent.clearPackSearch();
                refresh("");
            } else if (!vanilla) parent.showPackDetails(source);
        }

        @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier nativeKey) {
            if (selected && !vanilla && ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) && ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) || (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN))) {
                if (!parent.service.busy && !parent.libraryImporting()) {
                    move(source, (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) ? -1 : 1);
                }
                return true;
            }
            if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && !folder) {
                toggle();
                return true;
            }
            return nativeKey.getAsBoolean();
        }

        @Override public void drawIcon(DrawContext context, int mx, int my, float delta) {
            int clipX = selected ? right() : PackPane.this.x;
            context.enableScissor(clipX, PackPane.this.y, clipX + columnWidth, PackPane.this.y + PackPane.this.height);
            if (isFocused()) UiDraw.frame(context, getX(), getY(), getWidth(), getHeight(), 4, 0x70606060, UiDraw.FOCUS);
            else if (isHovered() && !vanilla) UiDraw.rounded(context, getX(), getY(), getWidth(), getHeight(), 4, 0x70606060);
            int iconSize = PackPane.this.columnWidth < 180 ? 18 : 22;
            int iconX = getX() + 4, iconY = getY() + (getHeight() - iconSize) / 2;
            if (folder) UiDraw.symbol(context, UiDraw.Symbol.FOLDER, iconX + iconSize / 2, iconY + iconSize / 2, UiDraw.SECONDARY);
            else icons.draw(context, source, iconX, iconY, iconSize);
            String subtitle = folder ? parent.service.library.groupSourceIds(source, parent.draft).size() + tr(" 个资源包")
                    : vanilla ? tr("原版 / 服务器基础") : parent.service.sourceErrors.getOrDefault(source, "");
            int textX = iconX + iconSize + 5, textWidth = getRight() - textX - 3;
            int nameY = getY() + (subtitle.isBlank() ? 10 : 5);
            context.drawText(parent.textRenderer, UiDraw.ellipsis(parent.textRenderer, getMessage().getString(), textWidth),
                    textX, nameY, UiDraw.TEXT, false);
            if (!subtitle.isBlank()) context.drawText(parent.textRenderer,
                    UiDraw.ellipsis(parent.textRenderer, subtitle, textWidth), textX, getY() + 16,
                    vanilla || folder ? UiDraw.SECONDARY : 0xffffff8a, false);
            context.disableScissor();
        }

        @Override public boolean isMouseOver(double mx, double my) {
            return my >= PackPane.this.y && my < PackPane.this.y + PackPane.this.height && super.isMouseOver(mx, my);
        }
    }
}
