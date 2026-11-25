# TextureMixer for Minecraft 1.8.9

Mix individual assets from several resource packs over Minecraft's active pack stack. The Legacy Fabric and Forge builds open the same TextureMixer editor directly from **Options → Resource Packs**. Its translucent, rounded window uses the modern edition's Resourcepack layout, Barlow typeface, category navigation, and action symbols.

## Install

| Loader for Minecraft 1.8.9 | Minimum build | Download |
| --- | --- | --- |
| [Legacy Fabric](https://legacyfabric.net/usage.html) | Fabric Loader 0.18.4 | [Legacy Fabric JAR](https://github.com/FullyXxxx/TextureMixer/releases/tag/v1.0.1-mc1.8.9) |
| [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html) | 11.15.1.2318 | [Forge JAR](https://github.com/FullyXxxx/TextureMixer/releases/tag/v1.0.1-mc1.8.9-forge) |

Use **64-bit Java 8**. Put only the JAR for your loader in the instance's `mods` directory. Both are client-side and require no additional mods.

## Use TextureMixer

1. Place ZIP archives or unpacked resource packs in the instance's `resourcepacks` directory. You can organize sources into subfolders.
2. Open **Options → Resource Packs**. Click **+** to add an available pack. Use the up and down arrows in **Selected · Top Wins** to change priority, or **−** to remove a pack. Search filters the current folder on the left. **Refresh** indexes new files, and **Open Folder** opens the current instance's pack directory.
3. Open a category, select an asset, and compare its **Follow pack stack**, **Vanilla**, and resource pack previews. Click a source to review it, then choose **Select**. Search also matches names typed without spaces and uses available English and Chinese names for items and blocks.
4. Use **Apply** to commit changes and stay in the editor, or **Done** to apply and return. **Custom Sets** saves both overrides and the base pack order. **Undo** reverses one edit, **Revert** restores the applied mix, and **Reset Mix** clears individual overrides while retaining the base pack stack.
5. Open **Settings** to switch between English and Simplified Chinese or check the installed build version. Narrow windows keep the two pack columns and show navigation arrows when the categories need more space.

The first visit retains local packs selected in the original Minecraft menu. Both loader builds use `config/texturemixer-legacy.json` in the current instance. When switching loaders between separate instances, you can copy that file along with your resource packs, keeping their paths relative to `resourcepacks`.

## Supported assets

- **Items and blocks.** Replace a diamond sword icon, the worn diamond armor texture layer, or water and lava. Water and lava each group their still and flowing textures. Bow variants are grouped as well. PNG animation metadata travels with the selected texture.
- **Environment and interface.** Choose textures for entities, the particle sheet, the sun and moon, clouds, rain and snow, HUD elements, menus, containers, bitmap fonts, and the shared 1.8.9 enchantment glint.
- **Sound and music.** Replace compatible Ogg Vorbis files such as a chest-opening effect. Audition up to eight seconds with an independent preview volume that starts at 50%.
- **Source compatibility.** TextureMixer reads matching 1.8.9 paths and maps supported newer paths such as `textures/item` to `textures/items`, `textures/block` to `textures/blocks`, and the modern item glint texture to the shared legacy glint. Individual source previews use the corresponding vanilla texture for missing assets. Choose **Follow pack stack** to use the first available asset in the active stack.

TextureMixer applies its selected packs over vanilla and the server pack, then applies the individual overrides. Previews show 2D texture images or play sound clips. Resource replacement is limited to compatible Minecraft 1.8.9 assets. OptiFine/MCPatcher custom skyboxes, model and shader conversion, and the modern edition's 3D equipment previews are outside this edition's scope.

## Build from source

Use JDK 25 to run the repository's Gradle Wrapper. Install JDK 8 for the development client.

```sh
./gradlew -p legacy-1.8.9 build
./gradlew -p legacy-1.8.9 forgeJar
```

On Windows, use `gradlew.bat` in place of `./gradlew`. Both installable JARs appear in `legacy-1.8.9/build/libs`. The Forge file ends in `-forge.jar`. The `runClient` command starts the Legacy Fabric development client.

The two 1.8.9 builds share the legacy editor, preview, pack, and persistence code. They also use the [shared UI source](../src/main/java/com/fullyx/texturemixer/ui/shared/MixerLayout.java) used by the modern edition. Legacy Fabric installs the screen and resource-reload hooks through Mixins. Forge uses its client transformer and writes the same common code with the SRG names required by Forge 1.8.9.

## Verification

The packaged JARs were checked in isolated Minecraft 1.8.9 clients with Java 8, using Forge 11.15.1.2318 and Legacy Fabric Loader 0.18.4. Checks covered the direct menu entry, grouped sources, source dialogs, priority over a test server pack, water and lava, sound audition, language settings, and draft controls. The active resource bytes were compared with the selected source files after applying and restarting the client.
