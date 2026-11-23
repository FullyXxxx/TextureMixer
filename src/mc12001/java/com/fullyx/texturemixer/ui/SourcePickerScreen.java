package com.fullyx.texturemixer.ui;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.core.MixerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class SourcePickerScreen extends MixerBaseScreen {
    private final MixerScreen parent;
    private static final int ROW_HEIGHT=23;
    private UiDraw.Window window;
    private int offset, pageSize, listY, listHeight;
    private String query = "";
    private TextFieldWidget search;
    private FlatButton clearSearch;
    private List<String> ids = List.of();
    private final List<FlatButton> rowWidgets = new ArrayList<>();

    public SourcePickerScreen(MixerScreen parent) {
        super(Text.literal(tr("选择主材质包")));
        this.parent = parent;
    }
    @Override protected void init() {
        window=UiDraw.Window.centered(width,height,392,238);
        int left=window.x(), w=window.width(), bottom=window.bottom();
        listY=window.y()+48;
        listHeight=bottom-63-listY;
        pageSize=Math.max(1,listHeight/ROW_HEIGHT);
        rowWidgets.clear();
        int labelWidth=textRenderer.getWidth(tr("Search"))+8;
        search = addDrawableChild(new MixerField(textRenderer, left+12+labelWidth,bottom-54,w-44-labelWidth,UiDraw.CONTROL,Text.literal(tr("Search"))));
        search.setMaxLength(128);
        search.setText(query);
        search.setChangedListener(value -> { query = value; offset = 0; refreshRows(); });
        clearSearch = addDrawableChild(new FlatButton(left+w-28,bottom-54,16,UiDraw.CONTROL,Text.literal("×"),button -> {
            search.setText(""); setFocused(search);
        }).symbol(UiDraw.Symbol.CLOSE).quiet());
        clearSearch.narrationLabel = tr("Clear Search");
        clearSearch.setTooltip(Tooltip.of(Text.literal(tr("Clear Search"))));
        addDrawableChild(new VerticalScrollbar(left+w-21,listY,listHeight,
                () -> offset, () -> ids.size(), () -> pageSize, 1, value -> {
                    offset = (int)Math.round(value); refreshRows();
                }));
        addDrawableChild(new FlatButton(left+12,bottom-28,110,UiDraw.CONTROL,
                Text.literal(tr("添加主材质包")),button -> { client.setScreen(parent); parent.chooseFolder(true); }).primary());
        addDrawableChild(new FlatButton(window.right()-64,bottom-28,52,UiDraw.CONTROL,
                Text.literal(tr("返回")), button -> close()));
        refreshRows();
    }
    private void refreshRows() {
        rowWidgets.forEach(this::remove);
        rowWidgets.clear();
        List<String> stack = parent.draft.mainStack();
        List<String> ordered = new ArrayList<>(stack);
        parent.registeredIds().stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
        ids = ordered.stream().filter(id -> com.fullyx.texturemixer.core.SearchQuery.matches(parent.sourceName(id),query)).toList();
        offset = net.minecraft.util.math.MathHelper.clamp(offset, 0, Math.max(0, ids.size() - pageSize));
        int left=window.x(), w=window.width();
        for (int index = offset; index < Math.min(ids.size(), offset + pageSize); index++) {
            String id = ids.get(index);
            int y = listY+(index-offset)*ROW_HEIGHT;
            boolean selected = stack.contains(id);
            var source = parent.service.sources.get(id);
            boolean compatible = id.equals(MixerConfig.VANILLA) || source != null;
            FlatButton row = new FlatButton(left+12,y,selected?w-119:w-34,UiDraw.CONTROL,Text.literal(parent.sourceName(id)),button -> parent.previewMain(id,this));
            row.selected = selected || id.equals(MixerConfig.VANILLA) && stack.isEmpty();
            row.active = !parent.service.busy && (compatible || selected);
            String path = id.equals(MixerConfig.VANILLA) ? tr("清空主材质包叠加，使用基础资源") :
                    MixerScreen.plainName(parent.draft.sources.get(id).canonicalRoot());
            row.setTooltip(Tooltip.of(Text.literal(parent.sourceName(id)+"\n"+path+(compatible?"":tr("\n来源仍未读取完成")))));
            rowWidgets.add(addDrawableChild(row));
            if (selected) {
                int order = stack.indexOf(id);
                FlatButton up = orderButton(left + w - 104, y, id, -1);
                up.active = order > 0 && !parent.service.busy;
                up.setTooltip(Tooltip.of(Text.literal(tr("上移，提高优先级"))));
                rowWidgets.add(addDrawableChild(up));
                FlatButton down = orderButton(left + w - 78, y, id, 1);
                down.active = order + 1 < stack.size() && !parent.service.busy;
                down.setTooltip(Tooltip.of(Text.literal(tr("下移，降低优先级"))));
                rowWidgets.add(addDrawableChild(down));
            }
            if(selected){
                FlatButton action=new FlatButton(left+w-52,y,30,UiDraw.CONTROL,Text.literal(tr("移除")),button->toggle(id));
                action.active=row.active;rowWidgets.add(addDrawableChild(action));
            }
        }
    }
    private FlatButton orderButton(int x, int y, String id, int direction) {
        FlatButton button = new FlatButton(x,y,22,UiDraw.CONTROL,Text.empty(),pressed -> move(id,direction))
                .symbol(direction<0?UiDraw.Symbol.UP:UiDraw.Symbol.DOWN);
        button.narrationLabel = direction < 0 ? tr("上移，提高优先级") : tr("下移，降低优先级");
        return button;
    }
    private void toggle(String id) {
        parent.edit(tr("移除主包 ")+parent.sourceName(id),()->parent.draft.toggleMain(id));
        refreshRows();
    }
    private void move(String id, int direction) {
        parent.moveMain(id,direction);
        refreshRows();
    }
    @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla) {
        boolean clearing = clearSearch != null && clearSearch.isMouseOver(mouseX, mouseY);
        boolean handled = vanilla.getAsBoolean();
        if (handled && (clearing || getFocused() != null && !children().contains(getFocused()))) setFocused(search);
        return handled;
    }
    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        offset -= (int)Math.signum(vertical); refreshRows(); return true;
    }
    @Override protected boolean mixerKey(int key,int modifiers,java.util.function.BooleanSupplier vanilla) {
        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            setFocused(search); return true;
        }
        if (getFocused() == search) return vanilla.getAsBoolean();
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> offset += pageSize;
            case GLFW.GLFW_KEY_PAGE_UP -> offset -= pageSize;
            case GLFW.GLFW_KEY_HOME -> offset = 0;
            case GLFW.GLFW_KEY_END -> offset = ids.size();
            default -> { return vanilla.getAsBoolean(); }
        }
        refreshRows(); return true;
    }
    @Override public void render(DrawContext context, int mx, int my, float delta) {
        window.draw(context);
        int left=window.x(), y=window.y();
        UiDraw.rounded(context,left+10,listY-3,window.width()-20,listHeight+3,5,UiDraw.DETAIL);
        context.drawText(textRenderer,title,left+12,y+12,UiDraw.TEXT,false);
        context.drawText(textRenderer,tr("已选材质包在上方，上方优先"),left+12,y+29,UiDraw.SECONDARY,false);
        context.drawText(textRenderer,tr("Search"),left+12,window.bottom()-49,UiDraw.SECONDARY,false);
        super.render(context, mx, my, delta);
        if (ids.isEmpty()) context.drawText(textRenderer,tr("没有匹配结果"),left+17,listY+8,UiDraw.SECONDARY,false);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
}
