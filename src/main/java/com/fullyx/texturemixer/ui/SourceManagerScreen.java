package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.MixerConfig;
import com.fullyx.texturemixer.client.MixerService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.*;

public final class SourceManagerScreen extends MixerBaseScreen {
    private record Row(String id, boolean folder) {}
    private final MixerScreen parent;
    private final String groupId;
    private static final int ROW_HEIGHT = 30;
    private UiDraw.Window window;
    private int offset, pageSize, listY, listHeight;
    private String notice;
    private List<Row> rows = List.of();
    private PackIcons icons;
    private TextFieldWidget search;
    private FlatButton clearSearch;
    private String query = "";
    private final List<FlatButton> rowWidgets = new ArrayList<>();

    public SourceManagerScreen(MixerScreen parent) { this(parent, null, ""); }
    public SourceManagerScreen(MixerScreen parent, String groupId, String notice) {
        super(Text.literal(tr("材质来源管理")));
        this.parent = parent;
        this.groupId = groupId;
        this.notice = notice;
    }
    @Override protected void init() {
        if (icons != null) icons.close();
        icons = new PackIcons(parent.service);
        window = UiDraw.Window.centered(width, height, 422, 248);
        int x = window.x(), w = window.width(), bottom = window.bottom();
        listY = window.y() + 32;
        listHeight = bottom - 80 - listY;
        pageSize = Math.max(1, listHeight / ROW_HEIGHT);
        rowWidgets.clear();
        int labelWidth = textRenderer.getWidth(tr("Search")) + 8;
        search = addDrawableChild(new MixerField(textRenderer, x + 12 + labelWidth, bottom - 55, w - 44 - labelWidth, UiDraw.CONTROL, Text.literal(tr("Search"))));
        search.setMaxLength(128);
        search.setText(query);
        search.setChangedListener(value -> { query = value; offset = 0; refreshRows(); });
        clearSearch = addDrawableChild(new FlatButton(x + w - 28, bottom - 55, 16, UiDraw.CONTROL, Text.literal("×"), button -> {
            search.setText(""); setFocused(search);
        }).symbol(UiDraw.Symbol.CLOSE).quiet());
        clearSearch.narrationLabel = tr("Clear Search");
        clearSearch.setTooltip(Tooltip.of(Text.literal(tr("Clear Search"))));
        addDrawableChild(new VerticalScrollbar(x + w - 21, listY, listHeight,
                () -> offset, () -> rows.size(), () -> pageSize, 1, value -> {
                    offset = (int)Math.round(value); refreshRows();
                }));
        refreshRows();
        if (groupId != null) addDrawableChild(new FlatButton(x + w - 66, window.y()+6, 54, UiDraw.CONTROL, Text.literal(tr("刷新")), button -> {
            var group = parent.service.library.group(groupId);
            if (group != null) parent.importLibraryFolder(Path.of(group.canonicalRoot()), this);
        }));
        int actionWidth = Math.min(108, (w - 84) / 2);
        var addPack = addDrawableChild(new FlatButton(x + 12, bottom - 28, actionWidth, UiDraw.CONTROL,
                Text.literal(tr("添加材质包")), button -> parent.chooseFolder(false, this)));
        var addFolder = addDrawableChild(new FlatButton(x + 16 + actionWidth, bottom - 28, actionWidth, UiDraw.CONTROL,
                Text.literal(tr("添加文件夹")), button -> parent.chooseLibraryFolder(this)));
        addPack.active = addFolder.active = !parent.service.busy && !parent.libraryImporting();
        addDrawableChild(new FlatButton(window.right()-64, bottom-28, 52, UiDraw.CONTROL, Text.literal(tr("返回")), button -> close()));
        if (!notice.isBlank()) {
            addDrawableChild(new FlatButton(x + 12, bottom - 71, w - 24, 12, Text.literal(notice), button ->
                    client.setScreen(new MessageScreen(this, tr("导入状态"), notice))) {
                @Override public void drawIcon(DrawContext context, int mx, int my, float delta) {
                    context.drawText(SourceManagerScreen.this.textRenderer,
                            UiDraw.ellipsis(SourceManagerScreen.this.textRenderer,getMessage().getString().lines().findFirst().orElse(""), getWidth()),
                            getX(), getY() + 2, 0xffa0a0a0, false);
                }
            });
        }
    }
    private void refreshRows() {
        rowWidgets.forEach(this::remove);
        rowWidgets.clear();
        List<Row> entries = new ArrayList<>();
        if (groupId == null) {
            for (var group : parent.service.library.groups()) entries.add(new Row(group.id(), true));
            for (String id : parent.service.library.rootSourceIds(parent.draft)) entries.add(new Row(id, false));
        } else {
            for (String id : parent.service.library.groupSourceIds(groupId, parent.draft)) entries.add(new Row(id, false));
        }
        rows = entries.stream().filter(row -> com.fullyx.texturemixer.core.SearchQuery.matches(rowName(row),query)).toList();
        offset = Math.clamp(offset, 0, Math.max(0, rows.size() - pageSize));
        for (int i = offset; i < Math.min(rows.size(), offset + pageSize); i++) {
            Row row = rows.get(i);
            int y = listY + (i - offset) * ROW_HEIGHT;
            rowWidgets.add(addDrawableChild(new LibraryRow(window.x()+12, y, window.width()-86, row)));
            rowWidgets.add(addDrawableChild(new FlatButton(window.right()-69, y+5, 46, UiDraw.CONTROL, Text.literal(tr("移除")), button -> {
                if (row.folder()) removeGroup(row.id()); else removeSource(row.id());
            })));
        }
    }
    private void removeGroup(String id) {
        var group = parent.service.library.group(id);
        if (group == null) return;
        client.setScreen(new ChoiceScreen(this, tr("移除文件夹分组 ") + MixerScreen.plainName(group.name()) + tr("？成员将显示在根列表或其他所属分组。"),
                new ChoiceScreen.Choice(tr("移除文件夹"), () -> {
                    try {
                        parent.service.library.removeGroup(id);
                        notice = tr("已移除文件夹分组，成员可在根列表或其他分组查看");
                    } catch (java.io.IOException error) { notice = MixerService.message(error); }
                    client.setScreen(this);
                }), new ChoiceScreen.Choice(tr("取消"), () -> client.setScreen(this))));
    }
    private final class LibraryRow extends FlatButton {
        private final Row row;
        LibraryRow(int left, int top, int rowWidth, Row row) {
            super(left, top, rowWidth, ROW_HEIGHT-2, net.minecraft.text.Text.literal(rowName(row)), button -> {});
            this.row = row;
            setTooltip(Tooltip.of(net.minecraft.text.Text.literal(rowDescription(row))));
        }
        @Override protected void mixerPress(Runnable vanilla) {
            if (row.folder()) client.setScreen(new SourceManagerScreen(parent, row.id(), ""));
            else client.setScreen(new PackDetailsScreen(parent, SourceManagerScreen.this, row.id()));
        }
        @Override public void drawIcon(DrawContext context, int mx, int my, float delta) {
            if (isFocused()) UiDraw.frame(context, getX(), getY(), getWidth(), getHeight(), 4, 0x70606060, UiDraw.FOCUS);
            else if (isHovered()) UiDraw.rounded(context, getX(), getY(), getWidth(), getHeight(), 4, 0x70606060);
            if (row.folder()) UiDraw.symbol(context,UiDraw.Symbol.FOLDER,getX()+14,getY()+13,UiDraw.SECONDARY);
            else icons.draw(context, row.id(), getX() + 4, getY() + 4, 20);
            var font = SourceManagerScreen.this.textRenderer;
            context.drawText(font, UiDraw.ellipsis(font,rowName(row), getWidth() - 35), getX() + 30, getY() + 5, UiDraw.TEXT, false);
            context.drawText(font, UiDraw.ellipsis(font,rowSubtitle(row), getWidth() - 35), getX() + 30, getY() + 16, UiDraw.SECONDARY, false);
        }
    }
    private String rowName(Row row) {
        if (!row.folder()) return parent.sourceName(row.id());
        var group = parent.service.library.group(row.id());
        return group == null ? tr("文件夹失联") : MixerScreen.plainName(group.name());
    }
    private String rowSubtitle(Row row) {
        if (row.folder()) return parent.service.library.groupSourceIds(row.id(), parent.draft).size() + tr(" 个材质包");
        String error = parent.service.sourceErrors.get(row.id());
        if (error != null) return error;
        var record = parent.draft.sources.get(row.id());
        return record == null ? tr("来源失联") : MixerScreen.plainName(record.canonicalRoot());
    }
    private String rowDescription(Row row) {
        if (row.folder()) {
            var group = parent.service.library.group(row.id());
            return group == null ? tr("文件夹失联") : MixerScreen.plainName(group.name() + "\n" + group.canonicalRoot());
        }
        return rowName(row) + "\n" + rowSubtitle(row);
    }
    private void removeSource(String id) {
        long affected = parent.draft.referenceCount(id);
        client.setScreen(new ChoiceScreen(this, tr("移除 ") + parent.sourceName(id) + tr("，影响 ") + affected + tr(" 项，改用原版？"),
                new ChoiceScreen.Choice(tr("移除并改用原版"), () -> {
                    parent.edit(tr("移除来源 ")+parent.sourceName(id),()->{
                    for (var kind : MixerConfig.Kind.values())
                        parent.draft.overrides(kind).replaceAll((key, value) -> value.equals(id) ? MixerConfig.VANILLA : value);
                    parent.draft.setMainStack(parent.draft.mainStack().stream().filter(source -> !source.equals(id)).toList());
                    parent.draft.candidateSources.remove(id);
                    parent.draft.sources.remove(id);
                    });
                    notice = tr("来源移除将在应用后提交");
                    if (affected == 0 && parent.service.committed.referenceCount(id) == 0) {
                        var next = parent.service.committed.copy();
                        next.candidateSources.remove(id);
                        next.sources.remove(id);
                        try {
                            parent.service.store.save(next, false);
                            parent.service.committed = next;
                            notice = tr("已移除来源登记");
                        } catch (java.io.IOException error) { notice = MixerService.message(error); }
                    }
                    client.setScreen(this);
                }), new ChoiceScreen.Choice(tr("取消"), () -> client.setScreen(this))));
    }
    @Override public void render(DrawContext context, int mx, int my, float delta) {
        window.draw(context);
        int x = window.x(), w = window.width();
        UiDraw.rounded(context,x+10,listY,w-20,listHeight,5,UiDraw.DETAIL);
        String heading = tr("材质来源管理");
        var group = groupId == null ? null : parent.service.library.group(groupId);
        if (group != null) heading = MixerScreen.plainName(group.name());
        context.drawText(textRenderer, UiDraw.ellipsis(textRenderer,heading, w - (groupId == null ? 24 : 86)), x + 12, window.y()+12, UiDraw.TEXT, false);
        context.drawText(textRenderer, tr("Search"), x + 12, window.bottom()-50, UiDraw.SECONDARY, false);
        super.render(context, mx, my, delta);
        if (rows.isEmpty()) context.drawText(textRenderer,query.isBlank()?tr("尚无材质包"):tr("没有匹配结果"),x+17,listY+8,UiDraw.SECONDARY,false);
    }
    @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla) {
        boolean clearing = clearSearch != null && clearSearch.isMouseOver(mouseX, mouseY);
        boolean handled = vanilla.getAsBoolean();
        if (handled && (clearing || getFocused() != null && !children().contains(getFocused()))) setFocused(search);
        return handled;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        offset -= (int)Math.signum(vertical);
        refreshRows();
        return true;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla) {
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            setFocused(search); return true;
        }
        if (getFocused() == search) return vanilla.getAsBoolean();
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> offset += pageSize;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> offset -= pageSize;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> offset = 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> offset = rows.size();
            default -> { return vanilla.getAsBoolean(); }
        }
        refreshRows();
        return true;
    }
    @Override public void removed() { if (icons != null) icons.close(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() {
        client.setScreen(groupId == null ? parent : new SourceManagerScreen(parent));
    }
}
