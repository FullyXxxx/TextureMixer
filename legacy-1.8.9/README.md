# TextureMixer for Minecraft 1.8.9

Mix individual assets from several resource packs over Minecraft's active pack stack. The Legacy Fabric edition uses the original Resource Packs screen for base pack selection and priorities, then opens TextureMixer to choose per-asset sources.

## Install

1. Install [Legacy Fabric](https://legacyfabric.net/usage.html) for **Minecraft 1.8.9**, with **Fabric Loader 0.18.4** or later.
2. Configure the Minecraft 1.8.9 profile to run on **64-bit Java 8**.
3. Place the 1.8.9 TextureMixer JAR in that profile's `mods` directory. The JAR has no additional mod dependencies. [Download the 1.8.9 release](https://github.com/FullyXxxx/TextureMixer/releases/tag/v1.0.0-mc1.8.9)

## Use TextureMixer

1. Place ZIP archives or unpacked resource packs in the instance's `resourcepacks` directory. You can organize sources into subfolders.
2. If you want to change the base stack, arrange the packs under **Options → Resource Packs** and click **Done** to apply it.
3. Open **Options → Resource Packs → TextureMixer**. Choose an asset, compare its **Follow pack stack**, **Vanilla**, and matching pack sources, then click **Select**.
4. Click **Apply** to load the mix. **Sets** saves and reloads combinations. **Undo** restores the previous draft, and **Reset Mix** clears individual overrides while retaining the base stack.

## Supported assets

- **Items and blocks.** Replace a diamond sword icon, the worn diamond armor texture layer, or water and lava. Water and lava each group their still and flowing textures. Bow variants are grouped as well. PNG animation metadata travels with the selected texture.
- **Environment and interface.** Choose textures for entities, the particle sheet, the sun and moon, clouds, rain and snow, HUD elements, menus, containers, bitmap fonts, and the shared 1.8.9 enchantment glint.
- **Sound and music.** Replace compatible Ogg Vorbis files such as a chest-opening effect. Audition up to eight seconds with an independent preview volume that starts at 50%.
- **Source compatibility.** TextureMixer reads matching 1.8.9 paths and maps supported newer paths such as `textures/item` to `textures/items`, `textures/block` to `textures/blocks`, and the modern item glint texture to the shared legacy glint. Assets and variants missing from a chosen pack continue to follow the active stack.

TextureMixer applies its overrides after Minecraft's active base and server packs. Previews show 2D texture images or play sound clips. Resource replacement is limited to compatible Minecraft 1.8.9 assets. OptiFine/MCPatcher custom skyboxes, model and shader conversion, and the modern edition's 3D equipment previews are outside this edition's scope.

## Build from source

Use JDK 25 to run the repository's Gradle Wrapper. Install JDK 8 for the development client.

```sh
./gradlew -p legacy-1.8.9 build
```

On Windows, use `gradlew.bat -p legacy-1.8.9 build`. The installable JAR is written to `legacy-1.8.9/build/libs/TextureMixer-1.0.0+1.8.9.jar`. Run the same wrapper with `runClient` in place of `build` to start the development client.

## Verification

The release JAR was checked in an isolated Minecraft 1.8.9 client with Legacy Fabric Loader 0.18.4 and Java 8. The checks covered the vanilla menu entry, asset search, grouped ZIP and unpacked pack discovery, texture previews, sound audition, base and server pack priority, and Custom Sets, Undo, and Reset Mix. The active resource bytes were compared with the selected source files after applying, reloading, and restarting the client.
