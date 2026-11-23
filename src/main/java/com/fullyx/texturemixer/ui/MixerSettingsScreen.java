package com.fullyx.texturemixer.ui;

import com.fullyx.texturemixer.client.VersionedIdentifiers;

import static com.fullyx.texturemixer.core.UiLanguage.tr;

import com.fullyx.texturemixer.client.MixerService;
import com.fullyx.texturemixer.core.UiLanguage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MixerSettingsScreen extends MixerBaseScreen {
    private static final Identifier COVER = VersionedIdentifiers.of("texturemixer", "textures/gui/icon.png");
    private static final int FIRST_OPTION = 72, OPTION_STEP = 22;
    private final MixerScreen parent;
    private final List<String> optionLabels = new ArrayList<>();
    private final List<FlatButton> optionButtons = new ArrayList<>();
    private final String version = versionOf("texturemixer");
    private final String minecraft = versionOf("minecraft");
    private final String loader = versionOf("fabricloader");
    private UiDraw.Window window;
    private String notice = "";
    private boolean error;
    private int preferredOption = -1;

    public MixerSettingsScreen(MixerScreen parent) {
        super(Text.literal(tr("设置")));
        this.parent = parent;
    }

    @Override protected void init() {
        window = UiDraw.Window.centered(width, height, 350, 152);
        optionLabels.clear();
        optionButtons.clear();
        MixerService service = parent.service;
        option(tr("语言"), languageName(UiLanguage.language()), tr("设置菜单和操作文字的语言。"), () -> {
            service.setLanguage(otherLanguage(UiLanguage.language()));
            parent.localeChanged();
        });
        option(tr("预加载图片"), tr(service.preloadEnabled() ? "开启" : "关闭"),
                tr("预加载会占用额外内存和显存，后台解码与分批上传可能短暂影响帧时间。"),
                () -> service.setPreloadEnabled(!service.preloadEnabled()));

        int copyWidth = textRenderer.getWidth(tr("复制版本")) + 12;
        var copy = addDrawableChild(new FlatButton(window.right()-15-copyWidth, window.y()+37, copyWidth, UiDraw.CONTROL,
                Text.literal(tr("复制版本")), button -> {
                    client.keyboard.setClipboard("TextureMixer " + version + "\nMinecraft " + minecraft + "\nFabric Loader " + loader);
                    notice = tr("已复制版本信息");
                    error = false;
                }));
        copy.setTooltip(Tooltip.of(Text.literal("Minecraft " + minecraft + "\nFabric Loader " + loader)));

        int footer = window.bottom()-28;
        var help = addDrawableChild(new FlatButton(window.x()+12, footer, 86, UiDraw.CONTROL,
                Text.literal(tr("快捷键帮助")), button -> showKeyboardHelp()));
        help.quiet();
        var back = addDrawableChild(new FlatButton(window.right()-68, footer, 56, UiDraw.CONTROL,
                Text.literal(tr("返回")), button -> close()));
        setInitialFocus(preferredOption<0?back:optionButtons.get(preferredOption));
    }

    private void option(String label, String value, String description, SettingChange change) {
        int index = optionLabels.size();
        int y = window.y()+FIRST_OPTION+index*OPTION_STEP;
        optionLabels.add(label);
        var button = addDrawableChild(new FlatButton(window.right()-104, y, 92, UiDraw.CONTROL, Text.literal(value), control -> {
            try {
                change.apply();
                notice = "";
                error = false;
                preferredOption = index;
                clearAndInit();
            } catch (IOException exception) {
                notice = MixerService.message(exception);
                error = true;
            }
        }));
        button.narrationLabel = label + " · " + value;
        button.setTooltip(Tooltip.of(Text.literal(description)));
        optionButtons.add(button);
    }

    @Override protected boolean mixerClick(double mouseX,double mouseY,int button,java.util.function.BooleanSupplier vanilla) {
        boolean handled=vanilla.getAsBoolean();
        if (handled&&preferredOption>=0&&getFocused()!=null&&!children().contains(getFocused()))
            setFocused(optionButtons.get(preferredOption));
        return handled;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        window.draw(context);
        int x=window.x(), y=window.y(), w=window.width();
        context.drawText(textRenderer,tr("设置"),x+12,y+12,UiDraw.TEXT,false);
        UiDraw.rounded(context,x+12,y+27,w-24,38,5,UiDraw.DETAIL);
        com.fullyx.texturemixer.client.NativeCompat.drawGuiTexture(context,COVER,x+16,y+31,0,0,30,30,128,128,128,128);
        int copyWidth=textRenderer.getWidth(tr("复制版本"))+12;
        String versionLabel=UiDraw.ellipsis(textRenderer,"TextureMixer "+version,w-copyWidth-77);
        context.drawText(textRenderer,versionLabel,x+53,y+34,UiDraw.TEXT,false);
        context.drawText(textRenderer,"Minecraft "+minecraft,x+53,y+48,UiDraw.SECONDARY,false);
        for(int index=0;index<optionLabels.size();index++)
            context.drawText(textRenderer,optionLabels.get(index),x+12,y+FIRST_OPTION+index*OPTION_STEP+5,UiDraw.TEXT,false);
        if(!notice.isEmpty())
            context.drawText(textRenderer,UiDraw.ellipsis(textRenderer,notice,w-174),x+103,window.bottom()-23,error?0xffffff8a:UiDraw.SECONDARY,false);
        super.render(context,mouseX,mouseY,delta);
    }

    private void showKeyboardHelp() {
        String shortcuts = String.join("\n",
                tr("Ctrl + F  聚焦搜索框"),
                tr("Tab / Shift + Tab  切换控件焦点"),
                tr("方向键  在预览网格移动焦点"),
                tr("Enter  操作当前控件，或启用 / 移除已聚焦的资源包"),
                tr("Shift + ↑ / ↓  调整已选资源包优先级"),
                tr("Esc  返回或关闭编辑器"));
        client.setScreen(new MessageScreen(this, tr("快捷键帮助"), shortcuts));
    }

    private static String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("—");
    }

    private static String otherLanguage(String language) { return language.equals("zh_cn") ? "en_us" : "zh_cn"; }
    private static String languageName(String language) { return language.equals("zh_cn") ? "简体中文" : "English"; }
    @FunctionalInterface private interface SettingChange { void apply() throws IOException; }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
}
