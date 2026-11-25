package com.fullyx.texturemixer.legacy;

import com.fullyx.texturemixer.legacy.LegacyAssets.Category;
import com.fullyx.texturemixer.legacy.LegacyAssets.Pack;
import com.fullyx.texturemixer.legacy.LegacyAssets.Target;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class LegacyScreen extends Screen {
    private static final int ROW = 13;
    private static final int GAP = 6;
    private static final Pattern SEARCH_FORMAT = Pattern.compile("§[0-9a-fk-or]|[^\\p{L}\\p{N}]");
    private static final int BACK = 0;
    private static final int APPLY = 1;
    private static final int UNDO = 2;
    private static final int RESET = 3;
    private static final int SETS = 4;
    private static final int SELECT = 5;
    private static final int SAVE = 6;
    private static final int LOAD = 7;
    private static final int GROUP = 20;
    private static final int PREVIOUS_GROUP = 8;
    private static final int NEXT_GROUP = 9;
    private static final int PLAY = 10;
    private static final int QUIETER = 11;
    private static final int LOUDER = 12;

    private final Screen parent;
    private final LegacyMixer mixer;
    private final LegacyAudio audio = new LegacyAudio();
    private final Deque<Map<String, String>> undo = new ArrayDeque<>();
    private final List<Target> shown = new ArrayList<>();
    private Map<String, String> draft;
    private List<Pack> packs = new ArrayList<>();
    private Category category = Category.ITEM;
    private Target target;
    private int categoryStart;
    private int assetOffset;
    private int sourceOffset;
    private int setOffset;
    private int sourceRow;
    private boolean setsOpen;
    private boolean resetOpen;
    private String selectedSet;
    private String searchText = "";
    private String setText = "";
    private String status = "";
    private boolean error;
    private TextFieldWidget search;
    private TextFieldWidget setName;
    private ButtonWidget selectButton;
    private ButtonWidget applyButton;
    private ButtonWidget undoButton;
    private ButtonWidget resetButton;
    private ButtonWidget saveButton;
    private ButtonWidget loadButton;
    private ButtonWidget playButton;
    private Identifier preview;
    private int previewWidth;
    private int previewHeight;
    private String previewMessage = "Select an asset";
    private int left;
    private int right;
    private int assetX;
    private int sourceX;
    private int previewX;
    private int listTop;
    private int listBottom;
    private int rows;

    public LegacyScreen(Screen parent, LegacyMixer mixer) {
        this.parent = parent;
        this.mixer = mixer;
        this.draft = mixer.selection();
        try {
            mixer.refresh();
        } catch (IOException failure) {
            failed(failure);
        }
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        left = Math.max(8, (width - 740) / 2);
        right = width - left;
        assetX = left;
        sourceX = left + (right - left) / 3;
        previewX = left + (right - left) * 2 / 3;
        listTop = 76;
        listBottom = Math.max(listTop + ROW, height - 49);
        rows = Math.max(1, (listBottom - listTop) / ROW);

        buttons.clear();
        if (setsOpen) {
            initSets();
        } else if (resetOpen) {
            initReset();
        } else {
            initEditor();
        }
    }

    private void initEditor() {
        int available = right - left - 36;
        int slot = Math.max(1, Math.min(Category.values().length, available / 76));
        categoryStart = Math.max(0, Math.min(categoryStart, Category.values().length - slot));
        int tabWidth = available / slot;
        button(PREVIOUS_GROUP, left, 24, 16, "<").active = categoryStart > 0;
        button(NEXT_GROUP, right - 16, 24, 16, ">").active = categoryStart + slot < Category.values().length;
        for (int i = 0; i < slot; i++) {
            Category tab = Category.values()[categoryStart + i];
            ButtonWidget widget = button(GROUP + tab.ordinal(), left + 18 + i * tabWidth, 24,
                    tabWidth - 2, textRenderer.trimToWidth(tab.label, tabWidth - 9));
            widget.active = tab != category;
        }
        search = new TextFieldWidget(0, textRenderer, left + 1, 49, right - left - 2, 14);
        search.setMaxLength(80);
        search.setText(searchText);
        renderListState();
        selectButton = button(SELECT, previewX + 1, listBottom - 20, right - previewX - 2, "Select");
        playButton = null;
        if (category == Category.SOUND) {
            playButton = button(PLAY, previewX + 1, listBottom - 69, right - previewX - 2, "Play");
            button(QUIETER, previewX + 1, listBottom - 45, 20, "-");
            button(LOUDER, right - 21, listBottom - 45, 20, "+");
        }
        int footer = right - left;
        int segment = (footer - 4 * GAP) / 5;
        button(BACK, left, height - 26, segment, "Back");
        button(SETS, left + segment + GAP, height - 26, segment, "Sets");
        undoButton = button(UNDO, left + 2 * (segment + GAP), height - 26, segment, "Undo");
        resetButton = button(RESET, left + 3 * (segment + GAP), height - 26, segment, "Reset Mix");
        applyButton = button(APPLY, left + 4 * (segment + GAP), height - 26,
                right - left - 4 * (segment + GAP), "Apply");
        updateButtons();
        updatePreview();
    }

    private void initSets() {
        setName = new TextFieldWidget(1, textRenderer, left + 1, 28, right - left - 92, 16);
        setName.setMaxLength(48);
        setName.setText(setText);
        saveButton = button(SAVE, right - 83, 26, 83, "Save Mix");
        button(BACK, left, height - 26, 78, "Back");
        loadButton = button(LOAD, right - 83, height - 26, 83, "Load Set");
        updateSetButtons();
    }

    private void initReset() {
        button(BACK, width / 2 - 94, height / 2 + 20, 90, "Cancel");
        button(RESET, width / 2 + 4, height / 2 + 20, 90, "Reset Mix");
    }

    private ButtonWidget button(int id, int x, int y, int w, String title) {
        ButtonWidget widget = new ButtonWidget(id, x, y, w, 20, title);
        buttons.add(widget);
        return widget;
    }

    private static String searchable(String value) {
        return SEARCH_FORMAT.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private void renderListState() {
        shown.clear();
        String query = searchable(searchText);
        for (Target item : mixer.targets()) {
            if (item.category == category && (query.isEmpty()
                    || searchable(item.title).contains(query)
                    || searchable(item.id).contains(query))) {
                shown.add(item);
            }
        }
        if (target != null && !shown.contains(target)) setTarget(null);
        assetOffset = clampOffset(assetOffset, shown.size());
    }

    private void setTarget(Target item) {
        target = item;
        sourceOffset = 0;
        sourceRow = 0;
        packs = item == null ? new ArrayList<>() : mixer.sources(item);
        String selected = item == null ? null : draft.get(item.id);
        if (LegacyMixer.VANILLA.equals(selected)) sourceRow = 1;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).name.equals(selected)) sourceRow = i + 2;
        }
        sourceOffset = clampOffset(Math.max(0, sourceRow - rows + 1), packs.size() + 2);
        updateButtons();
        updatePreview();
    }

    private String sourceName(int row) {
        if (row == 0) return "Follow pack stack";
        if (row == 1) return "Vanilla";
        return packs.get(row - 2).name;
    }

    private String sourceKey(int row) {
        if (row == 0) return null;
        if (row == 1) return LegacyMixer.VANILLA;
        return packs.get(row - 2).name;
    }

    private void updateButtons() {
        if (selectButton == null) return;
        selectButton.active = target != null && !Objects.equals(sourceKey(sourceRow), draft.get(target.id));
        applyButton.active = !draft.equals(mixer.selection());
        undoButton.active = !undo.isEmpty();
        resetButton.active = !draft.isEmpty();
        if (playButton != null) playButton.active = target != null;
    }

    private void updateSetButtons() {
        saveButton.active = !setText.trim().isEmpty();
        loadButton.active = selectedSet != null && mixer.sets().contains(selectedSet);
    }

    private void updatePreview() {
        releasePreview();
        audio.stop();
        if (target == null) {
            previewMessage = "Select an asset";
            return;
        }
        if (category == Category.SOUND) {
            previewMessage = "";
            return;
        }
        try {
            byte[] bytes = previewBytes();
            if (bytes == null) {
                previewMessage = "No preview available";
                return;
            }
            BufferedImage image = LegacyMixer.thumbnail(bytes);
            previewWidth = image.getWidth();
            previewHeight = image.getHeight();
            preview = client.getTextureManager().registerDynamicTexture("texturemixer", new NativeImageBackedTexture(image));
        } catch (IOException | RuntimeException failure) {
            previewMessage = "Could not load preview";
        }
    }

    private byte[] previewBytes() throws IOException {
        Pack pack = sourceRow >= 2 ? packs.get(sourceRow - 2) : null;
        return mixer.preview(target, pack, sourceRow == 1);
    }

    private void releasePreview() {
        if (preview != null) {
            client.getTextureManager().close(preview);
            preview = null;
        }
    }

    @Override
    public void render(int x, int y, float delta) {
        fill(0, 0, width, height, 0xe41b1d22);
        if (resetOpen) {
            drawCenteredString(textRenderer, "Reset individual overrides?", width / 2, height / 2 - 17, 0xffffff);
            drawCenteredString(textRenderer, "Your base packs stay in place.", width / 2, height / 2 - 3, 0xa9b1bd);
        } else if (setsOpen) {
            renderSets(x, y);
        } else {
            renderEditor(x, y);
        }
        if (!resetOpen) text(status, left, height - 39, right - left, error ? 0xff776f : 0xa9c5d3);
        super.render(x, y, delta);
    }

    private void renderEditor(int x, int y) {
        drawCenteredString(textRenderer, "TextureMixer", width / 2, 10, 0xffffff);
        search.render();
        if (searchText.isEmpty() && !search.isFocused()) text("Search assets", left + 5, 52, 120, 0xa6aab1);
        text("ASSET", assetX + 3, 66, sourceX - assetX - 5, 0xa6aab1);
        text("SOURCE", sourceX + 3, 66, previewX - sourceX - 5, 0xa6aab1);
        text("PREVIEW", previewX + 3, 66, right - previewX - 5, 0xa6aab1);
        fill(assetX, listTop, sourceX - 2, listBottom, 0xb0141518);
        fill(sourceX, listTop, previewX - 2, listBottom, 0xb0141518);
        fill(previewX, listTop, right, listBottom, 0xb0141518);
        for (int i = assetOffset; i < Math.min(shown.size(), assetOffset + rows); i++) {
            Target item = shown.get(i);
            String label = draft.containsKey(item.id) ? "* " + item.title : item.title;
            row(label, assetX, sourceX - 2, i - assetOffset, item == target, x, y);
        }
        if (shown.isEmpty()) text("No matching assets", assetX + 3, listTop + 4, sourceX - assetX - 8, 0xa6aab1);
        drawPreview();
        if (target != null) {
            for (int i = sourceOffset; i < Math.min(packs.size() + 2, sourceOffset + rows); i++) {
                row(sourceName(i), sourceX, previewX - 2, i - sourceOffset, i == sourceRow, x, y);
            }
            if (x >= assetX && x < sourceX - 2 && y >= listTop && y < listTop + rows * ROW) {
                int index = (y - listTop) / ROW + assetOffset;
                if (index < shown.size()) tooltip(shown.get(index).id, x, y);
            } else if (x >= sourceX && x < previewX - 2 && y >= listTop && y < listTop + rows * ROW) {
                int index = (y - listTop) / ROW + sourceOffset;
                if (index < packs.size() + 2) tooltip(sourceName(index), x, y);
            }
        }
    }

    private void drawPreview() {
        if (target != null) {
            text(target.title, previewX + 4, listTop + 5, right - previewX - 8, 0xffffff);
            text(sourceName(sourceRow), previewX + 4, listTop + 17, right - previewX - 8, 0xa9c5d3);
        }
        if (preview == null) {
            text(previewMessage, previewX + 4, listTop + 32, right - previewX - 8, 0xa6aab1);
            if (playButton != null) {
                drawCenteredString(textRenderer, audio.volume() + "%", (previewX + right) / 2,
                        listBottom - 39, 0xa9c5d3);
            }
            return;
        }
        int available = Math.max(1, listBottom - listTop - 54);
        double scale = Math.min((right - previewX - 12.0) / previewWidth, (double) available / previewHeight);
        int w = Math.max(1, (int) (previewWidth * scale));
        int h = Math.max(1, (int) (previewHeight * scale));
        int x = (previewX + right - w) / 2;
        int y = listTop + 29 + (available - h) / 2;
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableBlend();
        client.getTextureManager().bindTexture(preview);
        drawTexture(x, y, 0, 0, w, h, w, h);
        GlStateManager.disableBlend();
    }

    private void renderSets(int x, int y) {
        drawCenteredString(textRenderer, "Custom Sets", width / 2, 10, 0xffffff);
        setName.render();
        if (setText.isEmpty() && !setName.isFocused()) text("Set name", left + 5, 32, right - left - 98, 0xa6aab1);
        text("SAVED SETS", left + 3, 66, right - left - 8, 0xa6aab1);
        fill(left, listTop, right, listBottom, 0xb0141518);
        List<String> names = mixer.sets();
        for (int i = setOffset; i < Math.min(names.size(), setOffset + rows); i++) {
            String name = names.get(i);
            row(name, left, right, i - setOffset, name.equals(selectedSet), x, y);
        }
    }

    private void row(String label, int start, int end, int index, boolean selected, int x, int y) {
        int top = listTop + index * ROW;
        boolean hovered = x >= start && x < end && y >= top && y < top + ROW;
        if (selected || hovered) fill(start, top, end, top + ROW, selected ? 0xff3a535f : 0xff292f37);
        text(label, start + 4, top + 3, end - start - 8, 0xffffff);
    }

    private void text(String value, int x, int y, int maxWidth, int color) {
        if (maxWidth > 0) drawWithShadow(textRenderer, textRenderer.trimToWidth(value, maxWidth), x, y, color);
    }

    private void tooltip(String value, int x, int y) {
        if (textRenderer.getStringWidth(value) > 90) renderTooltip(value, x, y);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) return;
        if (button.id >= GROUP) {
            category = Category.values()[button.id - GROUP];
            assetOffset = 0;
            setTarget(null);
            init();
            return;
        }
        switch (button.id) {
            case BACK:
                back();
                break;
            case PREVIOUS_GROUP:
            case NEXT_GROUP:
                categoryStart += button.id == PREVIOUS_GROUP ? -1 : 1;
                init();
                break;
            case SELECT:
                remember();
                String source = sourceKey(sourceRow);
                if (source == null) draft.remove(target.id);
                else draft.put(target.id, source);
                message("Selected " + sourceName(sourceRow) + " for " + target.title);
                break;
            case APPLY:
                try {
                    mixer.apply(draft);
                    setTarget(target);
                    message("Mix applied");
                } catch (IOException | RuntimeException failure) {
                    failed(failure);
                }
                break;
            case UNDO:
                draft = undo.pop();
                setTarget(target);
                message("Undid the last edit");
                break;
            case RESET:
                if (!resetOpen) {
                    resetOpen = true;
                    audio.stop();
                } else {
                    remember();
                    draft.clear();
                    resetOpen = false;
                    setTarget(target);
                    message("Overrides cleared. Apply to save the change.");
                }
                init();
                break;
            case SETS:
                setsOpen = true;
                releasePreview();
                audio.stop();
                init();
                break;
            case SAVE:
                saveSet();
                break;
            case LOAD:
                remember();
                draft = mixer.load(selectedSet);
                setsOpen = false;
                setTarget(target);
                message("Loaded " + selectedSet + ". Apply to use it.");
                init();
                break;
            case PLAY:
                try {
                    if (audio.playing()) audio.stop();
                    else {
                        byte[] sound = previewBytes();
                        if (sound == null) throw new IOException("Sound file unavailable");
                        audio.play(sound);
                    }
                } catch (IOException failure) {
                    failed(failure);
                }
                break;
            case QUIETER:
            case LOUDER:
                audio.changeVolume(button.id == QUIETER ? -10 : 10);
                break;
            default:
                break;
        }
        if (!setsOpen && !resetOpen) updateButtons();
    }

    private void saveSet() {
        String name = setText.trim();
        if (mixer.sets().contains(name)) {
            status = "A set with that name already exists";
            error = true;
            return;
        }
        try {
            mixer.save(name, draft);
            selectedSet = name;
            setText = "";
            setName.setText("");
            setOffset = clampOffset(mixer.sets().size() - rows, mixer.sets().size());
            message("Saved " + name);
        } catch (IOException failure) {
            failed(failure);
        }
        updateSetButtons();
    }

    private void remember() {
        undo.push(new LinkedHashMap<>(draft));
        if (undo.size() > 32) undo.removeLast();
    }

    private void message(String value) {
        status = value;
        error = false;
    }

    private void failed(Throwable failure) {
        status = failure.getMessage();
        if (status == null) status = "TextureMixer could not complete the operation";
        error = true;
    }

    private void back() {
        if (setsOpen || resetOpen) {
            setsOpen = false;
            resetOpen = false;
            init();
        } else {
            client.setScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (!resetOpen && setsOpen) setName.mouseClicked(x, y, button);
        else if (!resetOpen) search.mouseClicked(x, y, button);
        if (!resetOpen && button == 0 && y >= listTop && y < listTop + rows * ROW) {
            int row = (y - listTop) / ROW;
            if (setsOpen && x >= left && x < right) {
                List<String> names = mixer.sets();
                if (row + setOffset < names.size()) selectedSet = names.get(row + setOffset);
                updateSetButtons();
            } else if (!setsOpen && x >= assetX && x < sourceX - 2 && row + assetOffset < shown.size()) {
                setTarget(shown.get(row + assetOffset));
            } else if (!setsOpen && target != null && x >= sourceX && x < previewX - 2
                    && row + sourceOffset < packs.size() + 2) {
                sourceRow = row + sourceOffset;
                updateButtons();
                updatePreview();
            }
        }
        if (button != 0) return;
        for (ButtonWidget widget : buttons) {
            if (widget.isMouseOver(client, x, y)) {
                widget.playDownSound(client.getSoundManager());
                buttonClicked(widget);
                return;
            }
        }
    }

    @Override
    protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            back();
        } else if (setsOpen && setName.keyPressed(character, key)) {
            setText = setName.getText();
            updateSetButtons();
        } else if (!setsOpen && !resetOpen && search.keyPressed(character, key)) {
            if (!searchText.equals(search.getText())) {
                searchText = search.getText();
                assetOffset = 0;
                renderListState();
            }
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / client.width;
        int y = height - Mouse.getEventY() * height / client.height - 1;
        if (wheel == 0 || resetOpen || y < listTop || y >= listBottom || x < left || x >= right) return;
        int change = wheel > 0 ? -3 : 3;
        if (setsOpen) setOffset = clampOffset(setOffset + change, mixer.sets().size());
        else if (x < sourceX) assetOffset = clampOffset(assetOffset + change, shown.size());
        else if (x < previewX && target != null) sourceOffset = clampOffset(sourceOffset + change, packs.size() + 2);
    }

    private int clampOffset(int offset, int count) {
        return Math.max(0, Math.min(offset, count - rows));
    }

    @Override
    public void tick() {
        if (!setsOpen && !resetOpen && playButton != null) {
            try {
                audio.tick();
            } catch (IOException failure) {
                failed(failure);
            }
            playButton.message = audio.playing() ? "Stop" : "Play";
        }
        if (setsOpen) setName.tick();
        else if (!resetOpen) search.tick();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        audio.close();
        releasePreview();
    }
}
