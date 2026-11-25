package com.fullyx.texturemixer.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class LegacyLabels {
    private static final Map<String, String> CHINESE = chinese();

    private LegacyLabels() {}

    static String translate(String english) { return CHINESE.getOrDefault(english, english); }

    private static Map<String, String> chinese() {
        Map<String, String> values = new HashMap<>();
        try (InputStream stream = LegacyLabels.class.getResourceAsStream("/assets/texturemixer/i18n/en_us.json")) {
            if (stream != null) {
                JsonObject entries = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    values.putIfAbsent(entry.getValue().getAsString(), entry.getKey());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // The English labels are always available.
        }
        String[][] translations = {
                {"Resourcepack", "材质包"}, {"Item", "物品"}, {"Block", "方块"}, {"Sky", "天空"},
                {"Entity", "实体"}, {"Particle", "粒子"}, {"HUD", "HUD"}, {"Enchant", "附魔"},
                {"Sound & Music", "音效与音乐"}, {"HUD Elements", "HUD 元素"},
                {"Menu", "菜单"}, {"Container GUI", "容器界面"}, {"Font", "字体"},
                {"Resource packs detected", "已识别资源包"}, {"Scanning resource packs…", "正在扫描资源包…"},
                {" source missing", " 来源失联"}, {"Source missing", "来源失联"}, {"Language", "语言"},
                {"Version", "版本"}, {"Available Packs", "可用资源包"}, {"Selected · Top Wins", "已选资源包 · 上方优先"},
                {" resource pack", " 个资源包"},
                {"Reset individual overrides?", "重置混搭？"}, {"Your base packs stay in place.", "主资源包及其排序将保留。"},
                {"Follow pack stack", "跟随主包"}, {"No matching results", "没有匹配结果"},
                {"No available resource packs", "没有可用资源包"}, {"No preview available", "没有可用预览"},
                {"Select this source?", "是否选择此材质？"}, {"Vanilla fallback", "使用原版材质"},
                {"Save Current Mix", "保存当前混搭"}, {"Load Set", "载入套装"}, {"Set name", "套装名称"},
                {"Replace the saved mix?", "覆盖已保存的套装？"}, {"Load this custom set?", "载入自定义套装？"},
                {"Delete this custom set?", "删除自定义套装？"}, {"Mix applied", "混搭已应用"},
                {"Resource pack folder opened", "已打开资源包文件夹"}, {"Unable to open the resource pack folder", "无法打开资源包文件夹"},
                {" · Apply to use", " · 应用后生效"}, {"Undid · ", "已撤销 · "}, {"Added ", "已添加 "},
                {"Removed ", "已移除 "}, {"Moved ", "已移动 "}, {"Reverted draft", "已还原草稿"},
                {"Reset Mix", "重置混搭"}, {"Custom Sets", "自定义套装"},
                {"Open Folder", "打开文件夹"}, {"Apply", "应用"}, {"Done", "完成"}, {"Undo", "撤销"},
                {"Revert", "还原修改"}, {"Settings", "设置"}, {"Refresh", "刷新"}, {"Search", "搜索"},
                {"Choose", "选择"}, {"Select", "选择"}, {"Back", "返回"}, {"Close", "关闭"}, {"Cancel", "取消"},
                {"Confirm", "确定"}, {"Delete", "删除"}, {"Play", "播放"}, {"Stop", "停止"}
        };
        for (String[] row : translations) values.put(row[0], row[1]);
        return values;
    }
}
