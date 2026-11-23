# TextureMixer

TextureMixer 是 Minecraft Java 版的 Fabric 客户端模组。可叠加多个主资源包，并按物品、方块或其他资源目标选择单独的来源。混搭方案可以保存为自定义套装。

*TextureMixer is a client-side Fabric mod for combining resource packs, choosing individual assets, and saving mixes as reusable presets.*

## 安装

安装对应 Minecraft 版本的 Fabric Loader，把 TextureMixer 和对应版本的 Fabric API JAR 放进同一游戏实例的 mods 文件夹。Mod Menu 可选，可用于打开配置和切换 TextureMixer。加入多人游戏时，服务器无需安装本模组。

TextureMixer 1.0.0 为 24 个 Minecraft 正式版本提供独立构建。每个游戏版本的 Java、Fabric Loader 和 Fabric API 要求见 [版本矩阵](docs/RELEASE-1.0.0.md)。

## 使用

1. 打开 **选项 → 资源包**，也可以使用客户端命令 /texturemixer。
2. 在资源包页添加并排序主资源包。TextureMixer 自动识别当前实例资源包目录中的 ZIP、解压包和文件夹分组。
3. 切换分类，搜索资源，比较不同来源的预览后选择。物品支持图标、手持和适用的穿戴预览。Block 提供方块状态预览，包括水和岩浆。
4. 点击 **应用** 提交修改。使用 **自定义套装** 保存配置，或使用 **撤销** 回退本次编辑。

资源类别和已知边界见 [自定义范围](docs/CUSTOMIZATION.md)。TextureMixer 的配置和缓存保存在当前游戏实例中，来源资源包以只读方式使用。

## 从源码构建

安装 JDK 25 和 JDK 21，将 JAVA_HOME 指向 JDK 25。构建 Minecraft 1.20 至 1.20.4 时，还需安装 JDK 17。Gradle Wrapper 会下载 Gradle 和公开依赖。

| 操作 | Windows | Linux / macOS |
| --- | --- | --- |
| 构建默认目标 1.21.11 并运行回归 | `gradlew.bat build` | `./gradlew build` |
| 启动开发客户端 | `gradlew.bat runClient` | `./gradlew runClient` |

发布 JAR 位于 build/libs。使用 Python 3.10+ 时，可以选择其他目标：

```sh
python tools/build_matrix.py 1.20.4 1.21.11
python tools/build_native_port.py 26.3
```

去掉 build_matrix.py 后的版本参数可构建全部 1.20.x、1.21.x 目标。26.x 可用 build_native_port.py 逐版本构建。两个脚本都会为输出的 JAR 生成 SHA-256。

## 验证

- Gradle 的 build 会运行核心回归。使用 python tools/build_matrix.py --audit 1.21.11 还可启动隔离客户端并验证预览像素和资源应用。
- 26.x 实机验证脚本目前支持 Windows。传入 --audit 和 --assets-dir，再指定本机启动器的 Minecraft 资产目录。26.3 的验证参数见 [版本矩阵](docs/RELEASE-1.0.0.md)。
- 可选的 smoke 测试使用 Pillow 生成测试资源。运行 python tools/generate_fixtures.py 后，可用 Gradle 的 runSmokeClient 任务和 -PsmokeTest 参数启动。

## 许可证

TextureMixer 自有源码与素材采用 [MIT](LICENSE)。随附的 Barlow 字体和 Gradle Wrapper 适用各自的许可证。 [第三方声明](THIRD-PARTY-NOTICES.md)
