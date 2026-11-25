package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.legacy.LegacyAssets.Category;
import com.fullyx.texturemixer.legacy.LegacyAssets.Pack;
import com.fullyx.texturemixer.legacy.LegacyAssets.Target;
import com.fullyx.texturemixer.legacy.LegacyUi.Button;
import com.fullyx.texturemixer.ui.shared.MixerLayout;
import com.fullyx.texturemixer.ui.shared.MixerLayout.Symbol;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LegacyScreen extends Screen {
    private static final int BUTTON = MixerLayout.CONTROL;
    private static final int GAP = 4;
    private static final Category[] TABS = {null, Category.ITEM, Category.BLOCK, Category.SKY, Category.ENTITY,
            Category.PARTICLE, Category.HUD, Category.SOUND, Category.ENCHANT};
    private static final Category[] INTERFACE = {Category.HUD, Category.MENU, Category.CONTAINER, Category.FONT};
    private enum Dialog { NONE, PACK, SOURCE, RESET, SETTINGS, SETS, LOAD, REPLACE, DELETE, STATUS }

    private final Screen parent;
    private final LegacyMixer mixer;
    private final LegacyPackPane packPane;
    private final LegacyAudio audio = new LegacyAudio();
    private final ExecutorService indexer = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "TextureMixer discovery");
        worker.setDaemon(true);
        return worker;
    });
    private final List<Button> dialogButtons = new ArrayList<>();
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final List<Target> shown = new ArrayList<>();
    private final List<String> sources = new ArrayList<>();
    private final Map<String, String> queries = new LinkedHashMap<>();

    private LegacyFont font;
    private LegacyImages images;
    private Future<List<Pack>> scan;
    private List<String> stack;
    private Map<String, String> draft;
    private Category category;
    private Target target;
    private String source, inspectedPack;
    private boolean selectedPack;
    private String searchText = "", setText = "", selectedSet;
    private String status = "Scanning resource packs…", hoverHint = "", toast = "";
    private boolean error;
    private long toastAt;
    private Dialog dialog = Dialog.NONE;
    private TextFieldWidget search, setName;
    private Button apply, revert, undoButton, reset, refresh, play;
    private int navOffset, focused = -1, setOffset;
    private int left, top, panelWidth, panelHeight, contentX, contentWidth, searchY, gridTop, gridBottom;
    private int tile, columns, cellWidth, cellHeight, gridX;
    private int dialogX, dialogY, dialogWidth, dialogHeight;
    private double scroll, scrollTarget;
    private long lastFrame;
    private boolean draggingGrid;

    private static final class Edit {
        final List<String> stack;
        final Map<String, String> draft;
        final String description;

        Edit(List<String> stack, Map<String, String> draft, String description) {
            this.stack = new ArrayList<>(stack);
            this.draft = new LinkedHashMap<>(draft);
            this.description = description;
        }
    }

    public LegacyScreen(Screen parent, LegacyMixer mixer) {
        this.parent = parent;
        this.mixer = mixer;
        packPane = new LegacyPackPane(this, mixer);
        draft = mixer.selection();
        stack = mixer.stack();
        scan = indexer.submit(mixer::scan);
    }

    String tr(String english) { return mixer.chinese() ? LegacyLabels.translate(english) : english; }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        if (font == null) font = new LegacyFont(textRenderer);
        if (images == null) images = new LegacyImages();
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        focused = -1;
        panelWidth = MixerLayout.windowWidth(width);
        panelHeight = MixerLayout.windowHeight(height);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        contentX = left + MixerLayout.INSET;
        contentWidth = panelWidth - MixerLayout.INSET * 2;
        buildNavigation();
        searchY = top + 24 + BUTTON + 5;
        if (interfaceCategory()) {
            buildInterface(searchY);
            searchY += BUTTON + GAP;
        }
        int labelWidth = font.width(tr("Search")) + 8;
        search = field(contentX + labelWidth, searchY, contentWidth - labelWidth - 20, searchText);
        Button clear = tool(contentX + contentWidth - 16, searchY, 16, "Clear Search", () -> {
            clearSearch();
            search.setFocused(true);
        });
        clear.shape = Symbol.CLOSE;
        clear.quiet = true;
        int footer = top + panelHeight - BUTTON - 9;
        int toolsY = buildFooter(footer);
        gridTop = searchY + BUTTON + (category == null ? 19 : 4);
        if (target != null) {
            tool(contentX, gridTop, 36, "Back", this::backToCategory).quiet = true;
            int size = buttonWidth("Follow pack stack", 62);
            tool(contentX + contentWidth - size, gridTop, size, "Follow pack stack", () -> openSource(null)).quiet = true;
            gridTop += BUTTON + GAP;
        }
        gridBottom = Math.max(gridTop + 1, toolsY - 18);
        if (category == null) {
            packPane.layout(contentX, gridTop, contentWidth, gridBottom - gridTop);
        } else {
            layoutGrid();
        }
        refresh = tool(contentX + contentWidth - buttonWidth("Refresh", 40), top + 5,
                buttonWidth("Refresh", 40), "Refresh", this::refresh);
        refresh.quiet = true;
        refresh.active = scan == null;
        buildDialog();
        updateActions();
    }

    private void buildNavigation() {
        int[] widths = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) widths[i] = font.width(tabTitle(TABS[i])) + 12;
        int capacity = MixerLayout.navigationCapacity(contentWidth, widths);
        navOffset = Math.max(0, Math.min(navOffset, TABS.length - capacity));
        int reserve = capacity == TABS.length ? 0 : 36;
        int navY = top + 24;
        int x = contentX;
        if (reserve > 0) {
            Button previous = tool(x, navY, 16, "Previous categories", () -> stepNav(-1));
            previous.shape = Symbol.LEFT;
            previous.quiet = true;
            previous.active = navOffset > 0;
            Button next = tool(x + contentWidth - 16, navY, 16, "Next categories", () -> stepNav(1));
            next.shape = Symbol.RIGHT;
            next.quiet = true;
            next.active = navOffset + capacity < TABS.length;
            x += 18;
        }
        int used = 0;
        for (int i = navOffset; i < navOffset + capacity; i++) used += widths[i];
        int extra = Math.max(0, (contentWidth - reserve - used) / capacity);
        for (int i = navOffset; i < navOffset + capacity; i++) {
            Category tab = TABS[i];
            int span = Math.min(widths[i] + extra, contentX + contentWidth - reserve / 2 - x);
            Button button = button(buttons, x, navY, span - 4, BUTTON, tabTitle(tab), () -> goTo(tab));
            button.quiet = true;
            button.selected = category == tab || tab == Category.HUD && interfaceCategory();
            x += span;
        }
    }

    private void stepNav(int direction) {
        navOffset += direction;
        rebuild();
    }

    private String tabTitle(Category tab) { return tr(tab == null ? "Resourcepack" : tab.label); }

    private boolean interfaceCategory() {
        return category == Category.HUD || category == Category.MENU || category == Category.CONTAINER || category == Category.FONT;
    }

    private void buildInterface(int y) {
        int x = contentX;
        for (Category tab : INTERFACE) {
            String label = tab == Category.HUD ? "HUD Elements" : tab.label;
            int w = buttonWidth(label, 34);
            Button button = tool(x, y, w, label, () -> goTo(tab));
            button.selected = category == tab;
            button.quiet = true;
            x += w + GAP;
        }
    }

    private int buildFooter(int y) {
        int settingsW = buttonWidth("Settings", 44);
        int setsW = buttonWidth("Custom Sets", 66);
        int folderW = category == null ? buttonWidth("Open Folder", 72) : 0;
        int[] sizes = {buttonWidth("Reset Mix", 52), buttonWidth("Undo", 32), buttonWidth("Revert", 46),
                buttonWidth("Apply", 35), buttonWidth("Done", 38)};
        int rightWidth = 4 * GAP;
        for (int size : sizes) rightWidth += size;
        int toolsWidth = settingsW + setsW + GAP + (folderW == 0 ? 0 : folderW + GAP);
        int toolsY = toolsWidth + rightWidth + 10 > contentWidth ? y - BUTTON - GAP : y;
        tool(contentX, toolsY, settingsW, "Settings", () -> openDialog(Dialog.SETTINGS));
        tool(contentX + settingsW + GAP, toolsY, setsW, "Custom Sets", () -> openDialog(Dialog.SETS));
        if (folderW > 0) tool(contentX + settingsW + setsW + GAP * 2, toolsY, folderW, "Open Folder", this::openFolder).quiet = true;
        int x = contentX + contentWidth - rightWidth;
        reset = tool(x, y, sizes[0], "Reset Mix", () -> openDialog(Dialog.RESET));
        reset.quiet = true;
        x += sizes[0] + GAP;
        undoButton = tool(x, y, sizes[1], "Undo", this::undo);
        undoButton.quiet = true;
        x += sizes[1] + GAP;
        revert = tool(x, y, sizes[2], "Revert", this::revert);
        revert.quiet = true;
        x += sizes[2] + GAP;
        apply = tool(x, y, sizes[3], "Apply", () -> apply(false));
        x += sizes[3] + GAP;
        tool(x, y, sizes[4], "Done", () -> apply(true)).primary = true;
        return toolsY;
    }

    private void updateActions() {
        boolean dirty = dirty();
        apply.active = dirty && scan == null;
        revert.active = dirty;
        undoButton.active = !undo.isEmpty();
        reset.active = !draft.isEmpty();
    }

    private boolean dirty() { return !draft.equals(mixer.selection()) || !stack.equals(mixer.stack()); }

    private int buttonWidth(String label, int min) { return Math.max(min, font.width(tr(label)) + 12); }

    private Button tool(int x, int y, int w, String label, Runnable action) {
        return button(buttons, x, y, w, BUTTON, tr(label), action);
    }

    private Button modal(int x, int y, int w, String label, Runnable action) {
        return button(dialogButtons, x, y, w, BUTTON, tr(label), action);
    }

    private Button button(List<? super Button> list, int x, int y, int w, int h, String value, Runnable action) {
        Button button = new Button(font, x, y, Math.max(1, w), h, value, action);
        list.add(button);
        return button;
    }

    private TextFieldWidget field(int x, int y, int w, String value) {
        TextFieldWidget field = new TextFieldWidget(0, textRenderer, x + 4, y + 5, Math.max(1, w - 8), 12);
        field.setHasBorder(false);
        field.setMaxLength(128);
        field.setText(value);
        return field;
    }

    private void drawField(TextFieldWidget field, int x, int y, int w, String placeholder) {
        LegacyUi.frame(x, y, w, BUTTON, 4, 0xe4212121, field.isFocused() ? MixerLayout.FOCUS : 0xff787878);
        field.render();
        if (field.getText().isEmpty() && !field.isFocused()) text(tr(placeholder), x + 4, y + 5, w - 8, MixerLayout.SECONDARY);
    }

    private void goTo(Category next) {
        queries.put(page(), searchText);
        category = next;
        target = null;
        searchText = queries.getOrDefault(page(), "");
        scroll = scrollTarget = 0;
        rebuild();
    }

    private String page() {
        return category == null ? "packs" : category.name() + (target == null ? "" : "/" + target.id);
    }

    private void openTarget(Target item) {
        queries.put(page(), searchText);
        target = item;
        searchText = queries.getOrDefault(page(), "");
        scroll = scrollTarget = 0;
        rebuild();
    }

    private void backToCategory() { goTo(category); }

    void clearSearch() {
        searchText = "";
        search.setText("");
        searchChanged();
    }

    private void searchChanged() {
        searchText = search.getText();
        scroll = scrollTarget = 0;
        if (category == null) packPane.refresh(searchText);
        else layoutGrid();
    }

    private void layoutGrid() {
        tile = panelWidth < 400 || panelHeight < 245 ? 34 : 40;
        cellWidth = tile + 9;
        cellHeight = tile + 24;
        columns = Math.max(1, (contentWidth - 10) / cellWidth);
        gridX = contentX + (contentWidth - 10 - columns * cellWidth) / 2;
        shown.clear();
        sources.clear();
        if (target == null) {
            for (Target item : mixer.targets()) {
                if (item.category == category && (MixerLayout.matches(mixer.name(item, false), searchText)
                        || MixerLayout.matches(mixer.name(item, true), searchText)
                        || MixerLayout.matches(item.id, searchText))) shown.add(item);
            }
            shown.sort(java.util.Comparator.comparing(item -> mixer.name(item, false), String.CASE_INSENSITIVE_ORDER));
        } else {
            if (MixerLayout.matches("Vanilla", searchText)) sources.add(LegacyMixer.VANILLA);
            for (String id : stack) addSource(id);
            for (Pack pack : mixer.packs()) if (!stack.contains(pack.name)) addSource(pack.name);
        }
        clampScroll();
    }

    private void addSource(String id) {
        if (mixer.pack(id) != null && MixerLayout.matches(id, searchText)) sources.add(id);
    }

    private int count() { return target == null ? shown.size() : sources.size(); }

    private int maxScroll() {
        return Math.max(0, ((count() + columns - 1) / columns) * cellHeight - (gridBottom - gridTop));
    }

    private void clampScroll() {
        int max = maxScroll();
        scroll = Math.max(0, Math.min(max, scroll));
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
    }

    List<String> stack() { return stack; }

    void addPack(String id) {
        if (stack.contains(id) || mixer.pack(id) == null) return;
        edit(tr("Added ") + LegacyPackPane.name(id), () -> stack.add(0, id), true);
    }

    void removePack(String id) {
        if (stack.contains(id)) edit(tr("Removed ") + LegacyPackPane.name(id), () -> stack.remove(id), true);
    }

    void movePack(String id, int direction) {
        int index = stack.indexOf(id);
        int next = index + direction;
        if (index >= 0 && next >= 0 && next < stack.size()) {
            edit(tr("Moved ") + LegacyPackPane.name(id), () -> Collections.swap(stack, index, next), true);
        }
    }

    private void edit(String description, Runnable action, boolean previewChanged) {
        undo.push(new Edit(stack, draft, description));
        if (undo.size() > 100) undo.removeLast();
        action.run();
        if (previewChanged) images.clear();
        message(description + tr(" · Apply to use"));
        changed();
    }

    private void changed() {
        if (category == null) packPane.refresh(searchText);
        else layoutGrid();
        updateActions();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        Edit edit = undo.pop();
        stack = edit.stack;
        draft = edit.draft;
        images.clear();
        toast = tr("Undid · ") + edit.description;
        toastAt = System.nanoTime();
        message(toast + tr(" · Apply to use"));
        changed();
    }

    private void revert() {
        if (dirty()) edit(tr("Reverted draft"), () -> {
            stack = mixer.stack();
            draft = mixer.selection();
        }, true);
    }

    private void apply(boolean close) {
        if (!dirty()) {
            if (close) client.setScreen(parent);
            return;
        }
        if (scan != null) return;
        audio.stop();
        images.clear();
        try {
            mixer.apply(draft, stack);
            message(tr("Mix applied"));
            changed();
            if (close) client.setScreen(parent);
        } catch (IOException | RuntimeException failure) {
            failed(failure);
        }
    }

    private void refresh() {
        if (scan != null) return;
        scan = indexer.submit(mixer::scan);
        refresh.active = false;
        message(tr("Scanning resource packs…"));
        updateActions();
    }

    private void openFolder() {
        try {
            java.nio.file.Files.createDirectories(mixer.packDirectory());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(mixer.packDirectory().toFile());
            } else {
                String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
                String command = os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open";
                new ProcessBuilder(command, mixer.packDirectory().toString()).start();
            }
            message(tr("Resource pack folder opened"));
        } catch (IOException | RuntimeException failure) {
            message(tr("Unable to open the resource pack folder"));
            error = true;
        }
    }

    void inspectPack(String id, boolean selected) {
        inspectedPack = id;
        selectedPack = selected;
        openDialog(Dialog.PACK);
    }

    private void openSource(String id) {
        source = id;
        openDialog(Dialog.SOURCE);
    }

    private void openDialog(Dialog kind) {
        audio.stop();
        dialog = kind;
        focused = -1;
        buildDialog();
    }

    private void closeDialog() {
        Dialog next = dialog == Dialog.LOAD || dialog == Dialog.REPLACE || dialog == Dialog.DELETE ? Dialog.SETS : Dialog.NONE;
        openDialog(next);
    }

    private void buildDialog() {
        dialogButtons.clear();
        play = null;
        if (dialog == Dialog.NONE) return;
        dialogWidth = Math.min(contentWidth, dialog == Dialog.SOURCE ? 326 : dialog == Dialog.SETS ? 356 : 290);
        dialogHeight = Math.min(panelHeight - 16, dialog == Dialog.SETS ? 246 : dialog == Dialog.PACK ? 112
                : dialog == Dialog.SOURCE ? 166 : dialog == Dialog.STATUS ? 130 : dialog == Dialog.SETTINGS ? 112 : 92);
        dialogX = (width - dialogWidth) / 2;
        dialogY = (height - dialogHeight) / 2;
        int y = dialogY + dialogHeight - BUTTON - 9;
        int left = dialogX + 10;
        int right = dialogX + dialogWidth - 10;
        modal(left, y, 56, dialog == Dialog.SETTINGS || dialog == Dialog.STATUS || dialog == Dialog.SETS ? "Back" : "Cancel", this::closeDialog);
        switch (dialog) {
            case PACK:
                Button packAction = modal(right - 75, y, 75, selectedPack ? "Remove" : "Select", () -> {
                    if (selectedPack) removePack(inspectedPack);
                    else addPack(inspectedPack);
                    closeDialog();
                });
                packAction.primary = true;
                packAction.active = selectedPack || mixer.pack(inspectedPack) != null;
                break;
            case SOURCE:
                Button choose = modal(right - 67, y, 67, "Select", this::chooseSource);
                choose.primary = true;
                choose.active = !Objects.equals(draft.get(target.id), source);
                if (category == Category.SOUND) {
                    int center = (left + right) / 2;
                    play = modal(center - 30, dialogY + 59, 60, "Play", this::playSource);
                    modal(center - 55, dialogY + 86, 20, "-", () -> audio.changeVolume(-10));
                    modal(center + 35, dialogY + 86, 20, "+", () -> audio.changeVolume(10));
                }
                break;
            case RESET:
                modal(right - 66, y, 66, "Reset Mix", () -> {
                    edit(tr("Reset Mix"), draft::clear, true);
                    closeDialog();
                }).primary = true;
                break;
            case SETS:
                setName = field(left, dialogY + 30, dialogWidth - 20, setText);
                modal(right - 92, dialogY + 52, 92, "Save Current Mix", this::requestSave);
                modal(right - 66, y, 66, "Load Set", () -> openDialog(Dialog.LOAD)).active = selectedSet != null;
                modal(right - 121, y, 51, "Delete", () -> openDialog(Dialog.DELETE)).active = selectedSet != null;
                break;
            case LOAD: case REPLACE: case DELETE:
                modal(right - 67, y, 67, "Confirm", this::confirmSet).primary = true;
                break;
            case SETTINGS:
                Button english = modal(left, dialogY + 53, (dialogWidth - 24) / 2, "English", () -> language(false));
                Button chinese = modal(left + (dialogWidth - 24) / 2 + GAP, dialogY + 53, (dialogWidth - 24) / 2, "简体中文", () -> language(true));
                english.selected = !mixer.chinese();
                chinese.selected = mixer.chinese();
                break;
            default:
                break;
        }
    }

    private void chooseSource() {
        String label = assetName(target) + " → " + sourceName(source);
        edit(label, () -> {
            if (source == null) draft.remove(target.id);
            else draft.put(target.id, source);
        }, false);
        closeDialog();
    }

    private void playSource() {
        if (audio.playing()) {
            audio.stop();
            return;
        }
        try {
            byte[] clip = mixer.preview(target, source, stack);
            if (clip == null) throw new IOException(tr("No preview available"));
            audio.play(clip);
        } catch (IOException failure) {
            failed(failure);
        }
    }

    private void language(boolean chinese) {
        try {
            mixer.setChinese(chinese);
            rebuild();
        } catch (IOException failure) {
            failed(failure);
        }
    }

    private void requestSave() {
        String name = setText.trim();
        if (name.isEmpty()) return;
        if (mixer.sets().contains(name)) openDialog(Dialog.REPLACE);
        else saveSet(name);
    }

    private void saveSet(String name) {
        try {
            mixer.save(name, draft, stack);
            selectedSet = name;
            setText = "";
            setOffset = Math.max(0, mixer.sets().indexOf(name) - setRows() + 1);
            message(tr("Saved ") + name);
            openDialog(Dialog.SETS);
        } catch (IOException failure) {
            failed(failure);
        }
    }

    private void confirmSet() {
        if (dialog == Dialog.REPLACE) {
            saveSet(setText.trim());
        } else if (dialog == Dialog.LOAD && selectedSet != null) {
            edit(tr("Load Set") + " · " + selectedSet, () -> {
                stack = mixer.setStack(selectedSet, stack);
                draft = mixer.load(selectedSet);
            }, true);
            openDialog(Dialog.NONE);
        } else if (dialog == Dialog.DELETE && selectedSet != null) {
            try {
                mixer.deleteSet(selectedSet);
                selectedSet = null;
                setOffset = 0;
                openDialog(Dialog.SETS);
            } catch (IOException failure) {
                failed(failure);
            }
        }
    }

    private String sourceName(String id) {
        return id == null ? tr("Follow pack stack") : id.isEmpty() ? "Vanilla" : LegacyPackPane.name(id);
    }

    private String assetName(Target item) { return mixer.name(item, mixer.chinese()); }

    private boolean fallback(Target item, String id) {
        Pack pack = mixer.pack(id);
        if (pack == null) return false;
        for (String path : item.paths) if (pack.contains(path)) return false;
        return true;
    }

    private LegacyImages.Preview preview(Target item, String id) {
        String key = item.id + "\n" + (id == null ? "[stack]" : id);
        List<String> current = id == null ? new ArrayList<>(stack) : Collections.emptyList();
        return images.get(key, () -> mixer.preview(item, id, current));
    }

    void drawPackIcon(String id, int x, int y, int size) {
        Pack pack = mixer.pack(id);
        LegacyImages.Preview icon = images.get("pack\n" + id, () ->
                id.isEmpty() ? mixer.vanillaIcon() : pack == null ? null : pack.icon());
        if (!images.draw(icon, x, y, size, size)) {
            LegacyUi.rounded(x, y, size, size, 3, 0xa03d3d3d);
            LegacyUi.rect(x + size / 3, y + size / 2, size / 3, 1, MixerLayout.SECONDARY);
        }
    }

    void text(String value, int x, int y, int max, int color) {
        font.draw(font.fit(value, max), x, y, color);
    }

    void center(String value, int x, int y, int max, int color) { font.center(value, x, y, max, color); }

    void hint(String value) { hoverHint = value; }

    @Override
    public void render(int mx, int my, float delta) {
        if (client.world == null) renderBackground();
        LegacyUi.rect(0, 0, width, height, MixerLayout.SCRIM);
        LegacyUi.frame(left, top, panelWidth, panelHeight, MixerLayout.RADIUS, MixerLayout.WINDOW, MixerLayout.WINDOW_BORDER);
        hoverHint = "";
        text(target == null ? "TextureMixer" : assetName(target), contentX, top + 10, contentWidth - 70, MixerLayout.TEXT);
        text(tr("Search"), contentX, searchY + 5, contentWidth, MixerLayout.SECONDARY);
        int searchLeft = contentX + font.width(tr("Search")) + 8;
        drawField(search, searchLeft, searchY, contentWidth + contentX - searchLeft - 20, "");
        if (category == null) packPane.draw(mx, my);
        else drawGrid(mx, my);
        LegacyUi.rect(contentX, gridBottom + 1, contentWidth, 1, MixerLayout.DIVIDER);
        text(error || hoverHint.isEmpty() ? status : hoverHint, contentX, gridBottom + 5, contentWidth,
                error ? 0xffffbe96 : MixerLayout.SECONDARY);
        for (ButtonWidget widget : buttons) widget.render(client, mx, my);
        drawToast();
        if (dialog != Dialog.NONE) drawDialog(mx, my);
    }

    private void drawGrid(int mx, int my) {
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min(.05, (now - lastFrame) / 1e9);
        lastFrame = now;
        scroll += (scrollTarget - scroll) * (1 - Math.exp(-22 * elapsed));
        if (Math.abs(scrollTarget - scroll) < .2) scroll = scrollTarget;
        if (count() == 0) {
            center(tr("No matching results"), contentX + contentWidth / 2, gridTop + 18, contentWidth, MixerLayout.SECONDARY);
            return;
        }
        int first = (int) scroll / cellHeight;
        int end = Math.min((count() + columns - 1) / columns, first + (gridBottom - gridTop) / cellHeight + 2);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        org.lwjgl.opengl.GL11.glScissor(0, client.height - Math.round(gridBottom * client.height / (float) height),
                client.width, Math.round((gridBottom - gridTop) * client.height / (float) height));
        try {
            for (int row = first; row < end; row++) {
                for (int col = 0; col < columns; col++) {
                    int index = row * columns + col;
                    if (index >= count()) break;
                    drawCard(index, gridX + col * cellWidth, gridTop + row * cellHeight - (int) scroll, mx, my);
                }
            }
        } finally {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }
        if (maxScroll() > 0) {
            int track = gridBottom - gridTop - 4;
            int thumb = gridThumb();
            int x = contentX + contentWidth - 5;
            int y = gridTop + 2 + (int) ((track - thumb) * scroll / maxScroll());
            LegacyUi.rect(x, gridTop + 2, 2, track, 0x90707070);
            LegacyUi.rounded(x - 1, y, 4, thumb, 2, 0xff989898);
        }
    }

    private void drawCard(int index, int x, int y, int mx, int my) {
        Target item = target == null ? shown.get(index) : target;
        String id = target == null ? draft.get(item.id) : sources.get(index);
        String label = target == null ? assetName(item) : sourceName(id);
        int pictureX = x + (cellWidth - tile) / 2;
        boolean hover = my >= gridTop && my < gridBottom && LegacyPackPane.inside(mx, my, x, y, cellWidth, cellHeight);
        boolean selected = target != null && Objects.equals(id, draft.get(item.id));
        if (selected || hover) LegacyUi.frame(pictureX, y, tile, tile, 4, MixerLayout.DETAIL,
                selected ? MixerLayout.FOCUS : 0x90777777);
        else LegacyUi.rounded(pictureX, y, tile, tile, 4, MixerLayout.DETAIL);
        drawAsset(item, id, pictureX + 2, y + 2, tile - 4);
        if (target != null && fallback(item, id)) LegacyUi.rect(pictureX + 3, y + 3, 4, 1, 0xffffd592);
        center(label, x + cellWidth / 2, y + tile + 4, cellWidth - 2, MixerLayout.TEXT);
        if (hover) hint(label);
    }

    private boolean drawAsset(Target item, String id, int x, int y, int size) {
        if (item.category == Category.SOUND) {
            for (int line = 0; line < 7; line++) LegacyUi.rect(x + size / 2 - 3 + line, y + size / 2 - 7 + line, 1, 15 - line * 2, MixerLayout.SECONDARY);
            return true;
        }
        LegacyImages.Preview picture = preview(item, id);
        if (images.draw(picture, x, y, size, size)) return true;
        LegacyUi.rect(x + size / 2 - 3, y + size / 2, 6, 1,
                picture.failed ? 0xffffd592 : MixerLayout.SECONDARY);
        return false;
    }

    private void drawToast() {
        if (toast.isEmpty()) return;
        double elapsed = (System.nanoTime() - toastAt) / 1e9;
        float fade = (float) Math.max(0, Math.min(1, Math.min(elapsed / .18, (3.2 - elapsed) / .4)));
        if (fade <= .02) return;
        fade *= fade * (3 - 2 * fade);
        int alpha = (int) (255 * fade) << 24;
        int w = Math.min(contentWidth, font.width(toast) + 16);
        int x = contentX + contentWidth - w;
        LegacyUi.rounded(x, gridBottom - 23, w, 20, 4, alpha | 0x424242);
        text(toast, x + 8, gridBottom - 17, w - 16, alpha | 0xffffff);
    }

    private void drawDialog(int mx, int my) {
        LegacyUi.rect(0, 0, width, height, 0x99000000);
        LegacyUi.frame(dialogX, dialogY, dialogWidth, dialogHeight, MixerLayout.RADIUS, 0xf0363636, MixerLayout.WINDOW_BORDER);
        String title;
        String detail = "";
        switch (dialog) {
            case PACK: title = sourceName(inspectedPack); break;
            case SOURCE: title = tr("Select this source?"); break;
            case RESET: title = tr("Reset individual overrides?"); detail = tr("Your base packs stay in place."); break;
            case SETTINGS: title = tr("Settings"); break;
            case SETS: title = tr("Custom Sets"); break;
            case LOAD: title = tr("Load this custom set?"); detail = selectedSet; break;
            case REPLACE: title = tr("Replace the saved mix?"); detail = setText.trim(); break;
            case DELETE: title = tr("Delete this custom set?"); detail = selectedSet; break;
            default: title = "TextureMixer"; break;
        }
        center(title, width / 2, dialogY + 11, dialogWidth - 20, MixerLayout.TEXT);
        if (detail != null && !detail.isEmpty()) center(detail, width / 2, dialogY + 32, dialogWidth - 20, MixerLayout.SECONDARY);
        if (dialog == Dialog.PACK) {
            drawPackIcon(inspectedPack, dialogX + 12, dialogY + 28, 40);
            text(inspectedPack, dialogX + 61, dialogY + 39, dialogWidth - 72, MixerLayout.SECONDARY);
        } else if (dialog == Dialog.SOURCE) {
            center(assetName(target) + " · " + sourceName(source), width / 2, dialogY + 26, dialogWidth - 20, MixerLayout.SECONDARY);
            if (category == Category.SOUND) {
                center(audio.volume() + "%", width / 2, dialogY + 92, 60, MixerLayout.SECONDARY);
            } else {
                int size = 72;
                int x = (width - size) / 2, y = dialogY + 43;
                LegacyUi.rounded(x, y, size, size, 4, MixerLayout.DETAIL);
                drawAsset(target, source, x + 4, y + 4, size - 8);
                if (fallback(target, source)) center(tr("Vanilla fallback"), width / 2, y + size + 4, dialogWidth - 20, MixerLayout.SECONDARY);
            }
        } else if (dialog == Dialog.SETTINGS) {
            center(tr("Version") + " " + LegacyPlatform.version(), width / 2, dialogY + 28, dialogWidth - 20, MixerLayout.SECONDARY);
            center(tr("Language"), width / 2, dialogY + 40, dialogWidth - 20, MixerLayout.SECONDARY);
        } else if (dialog == Dialog.SETS) {
            drawSets(mx, my);
        } else if (dialog == Dialog.STATUS) {
            int y = dialogY + 32;
            for (String line : textRenderer.wrapLines(status, dialogWidth - 20)) {
                if (y >= dialogY + dialogHeight - 36) break;
                text(line, dialogX + 10, y, dialogWidth - 20, MixerLayout.SECONDARY);
                y += 11;
            }
        }
        for (Button widget : dialogButtons) widget.render(client, mx, my);
    }

    private void drawSets(int mx, int my) {
        int x = dialogX + 10;
        drawField(setName, x, dialogY + 30, dialogWidth - 20, "Set name");
        List<String> list = mixer.sets();
        int y = dialogY + 75;
        for (int index = setOffset; index < Math.min(list.size(), setOffset + setRows()); index++) {
            String name = list.get(index);
            boolean selected = name.equals(selectedSet);
            int w = dialogWidth - 20;
            boolean hovered = LegacyPackPane.inside(mx, my, x, y, w, 16);
            if (selected || hovered) LegacyUi.rounded(x, y, w, 16, 4, selected ? 0xff646464 : 0x70606060);
            text(name, x + 4, y + 4, w - 8, MixerLayout.TEXT);
            y += 16;
        }
    }

    private int setRows() { return Math.max(1, (dialogHeight - 110) / 16); }

    private void message(String value) { status = value; error = false; }

    private void failed(Throwable failure) {
        status = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        error = true;
    }

    @Override
    protected void mouseClicked(int x, int y, int key) {
        if (key != 0) return;
        clearFocus();
        if (dialog != Dialog.NONE) {
            if (dialog == Dialog.SETS) {
                setName.mouseClicked(x, y, key);
                int row = (y - dialogY - 75) / 16;
                if (y >= dialogY + 75 && row < setRows() && x >= dialogX + 10 && x < dialogX + dialogWidth - 10) {
                    List<String> names = mixer.sets();
                    if (setOffset + row < names.size()) {
                        selectedSet = names.get(setOffset + row);
                        buildDialog();
                        return;
                    }
                }
            }
            clickButtons(dialogButtons, x, y);
            return;
        }
        search.mouseClicked(x, y, key);
        if (clickButtons(buttons, x, y)) return;
        if (category == null) {
            if (packPane.click(x, y)) return;
        } else if (y >= gridTop && y < gridBottom) {
            if (maxScroll() > 0 && x >= contentX + contentWidth - 10 && x < contentX + contentWidth) {
                draggingGrid = true;
                dragGrid(y);
                return;
            }
            int col = (x - gridX) / cellWidth;
            int row = (y - gridTop + (int) scroll) / cellHeight;
            int index = row * columns + col;
            if (x >= gridX && col < columns && index < count()) {
                if (target == null) openTarget(shown.get(index));
                else openSource(sources.get(index));
            }
            return;
        }
        if (LegacyPackPane.inside(x, y, contentX, gridBottom + 3, contentWidth, 12)) openDialog(Dialog.STATUS);
    }

    private boolean clickButtons(List<? extends ButtonWidget> list, int x, int y) {
        for (ButtonWidget widget : list) {
            if (widget.isMouseOver(client, x, y)) {
                activate((Button) widget);
                return true;
            }
        }
        return false;
    }

    private void activate(Button widget) {
        widget.playDownSound(client.getSoundManager());
        widget.action.run();
    }

    private void clearFocus() {
        List<? extends ButtonWidget> widgets = dialog == Dialog.NONE ? buttons : dialogButtons;
        if (focused >= 0 && focused < widgets.size()) ((Button) widgets.get(focused)).focused = false;
        focused = -1;
    }

    @Override
    protected void mouseReleased(int x, int y, int button) {
        packPane.release();
        draggingGrid = false;
    }

    @Override
    protected void mouseDragged(int x, int y, int button, long elapsed) {
        if (button == 0 && dialog == Dialog.NONE) {
            packPane.drag(y);
            if (draggingGrid) dragGrid(y);
        }
    }

    private int gridThumb() {
        int h = gridBottom - gridTop - 4;
        return Math.max(12, h * h / (Math.max(1, h + maxScroll())));
    }

    private void dragGrid(int y) {
        int travel = Math.max(1, gridBottom - gridTop - 4 - gridThumb());
        scroll = scrollTarget = (y - gridTop - 2 - gridThumb() / 2) * maxScroll() / (double) travel;
        clampScroll();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / client.width;
        int y = height - Mouse.getEventY() * height / client.height - 1;
        int direction = wheel > 0 ? -1 : 1;
        if (dialog == Dialog.SETS) {
            setOffset = Math.max(0, Math.min(mixer.sets().size() - setRows(), setOffset + direction));
        } else if (dialog == Dialog.NONE) {
            if (y >= top + 24 && y < top + 24 + BUTTON) stepNav(direction);
            else if (category == null) packPane.scroll(x, y, direction);
            else if (y >= gridTop && y < gridBottom) {
                scrollTarget += direction * cellHeight * .75;
                clampScroll();
            }
        }
    }

    @Override
    protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (dialog != Dialog.NONE) closeDialog();
            else if (target != null) backToCategory();
            else client.setScreen(parent);
            return;
        }
        if (dialog == Dialog.SETS && setName.keyPressed(character, key)) {
            setText = setName.getText();
            return;
        }
        if (dialog == Dialog.NONE) {
            if (key == Keyboard.KEY_F && hasControlDown()) {
                search.setFocused(true);
                return;
            }
            if (search.keyPressed(character, key)) {
                if (!searchText.equals(search.getText())) searchChanged();
                return;
            }
        }
        List<? extends ButtonWidget> widgets = dialog == Dialog.NONE ? buttons : dialogButtons;
        if (key == Keyboard.KEY_TAB && !widgets.isEmpty()) {
            if (focused >= 0 && focused < widgets.size()) ((Button) widgets.get(focused)).focused = false;
            int direction = hasShiftDown() ? -1 : 1;
            for (int i = 0; i < widgets.size(); i++) {
                focused = Math.floorMod(focused + direction, widgets.size());
                if (widgets.get(focused).active) {
                    ((Button) widgets.get(focused)).focused = true;
                    break;
                }
            }
            search.setFocused(false);
            if (dialog == Dialog.SETS) setName.setFocused(false);
        } else if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE)
                && focused >= 0 && focused < widgets.size() && widgets.get(focused).active) {
            activate((Button) widgets.get(focused));
        }
    }

    @Override
    public void tick() {
        search.tick();
        if (dialog == Dialog.SETS) setName.tick();
        if (play != null) {
            try {
                audio.tick();
            } catch (IOException failure) {
                failed(failure);
            }
            play.message = tr(audio.playing() ? "Stop" : "Play");
        }
        images.tick();
        if (scan != null && scan.isDone()) {
            try {
                mixer.setPacks(scan.get());
                images.clear();
                message(tr("Resource packs detected"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException failure) {
                failed(failure.getCause());
            } finally {
                scan = null;
            }
            refresh.active = true;
            changed();
        }
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        indexer.shutdownNow();
        audio.close();
        if (images != null) images.close();
        if (font != null) font.close();
    }
}
