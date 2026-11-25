# TextureMixer

TextureMixer is a client-side Fabric mod for Minecraft Java Edition. Stack your usual resource packs, then choose a separate source for individual assets. You can keep your building textures, select a sword from an older pack, and use a custom sky from another pack in the same mix.

## Features

- **Resource pack mixing.** Arrange base packs in priority order and override individual assets. TextureMixer detects ZIP archives and unpacked packs, including packs organized into folders.
- **Cross-version pack support.** TextureMixer adapts supported legacy asset paths. A sword texture from an older pack can be previewed and applied alongside assets from newer packs.
- **Asset-specific replacements.** Choose sources for items, blocks, skies, weather, entities, particles, HUD elements, menus, container screens, fonts, sound effects, music, and enchantment glints.
- **Previews before applying.** Compare sources, preview item icons and handheld or supported worn appearances, and audition sounds with a separate preview volume.
- **Reusable mixes.** Save a combination as a **Custom Set**. Use **Undo** to step back through edits or **Reset Mix** to clear individual overrides while keeping the base pack stack.

## Installation

For **Minecraft 1.8.9**, see the [installation guide](legacy-1.8.9/README.md) and choose the build for your loader. [Legacy Fabric](https://github.com/FullyXxxx/TextureMixer/releases/tag/v1.0.1-mc1.8.9) · [Forge](https://github.com/FullyXxxx/TextureMixer/releases/tag/v1.0.1-mc1.8.9-forge)

For Minecraft 1.20.x, 1.21.x, and 26.x:

1. Install **Fabric Loader** for the exact Minecraft version you play.
2. Download the matching TextureMixer JAR from [GitHub Releases](https://github.com/FullyXxxx/TextureMixer/releases/latest) and the corresponding [Fabric API](https://modrinth.com/mod/fabric-api).
3. Place both JARs in that game instance's `mods` directory.

TextureMixer runs on the client in single-player and multiplayer. **Mod Menu** is optional and adds an in-game on/off control. See the [version matrix](docs/RELEASE-1.0.0.md#compatibility-matrix) for the minimum Java, Fabric Loader, and Fabric API versions for the 24 builds in these series.

## In-game use

For the 1.8.9 interface, see the [Minecraft 1.8.9 guide](legacy-1.8.9/README.md#use-texturemixer).

1. Open **Options → Resource Packs** or run the client command `/texturemixer`.
2. Add and order your base packs.
3. Browse or search a category, compare the previews, and select a source.
4. Click **Apply** to load the mix. Save it under **Custom Sets** to use it again later.

TextureMixer reads source packs without modifying their files. Settings and cached resources are stored in the current game instance. See [Customization](docs/CUSTOMIZATION.md) for supported assets and format limits.

## Build from source

For Minecraft 1.8.9, see the [Legacy Fabric and Forge builds](legacy-1.8.9/README.md#build-from-source).

Install JDK 25 and JDK 21. Set `JAVA_HOME` to JDK 25 to run Gradle. Install JDK 17 as well if you build Minecraft 1.20 through 1.20.4. The Gradle Wrapper downloads Gradle and resolves public dependencies.

| Task | Windows | Linux or macOS |
| --- | --- | --- |
| Build the configured Minecraft target and run regressions | `gradlew.bat build` | `./gradlew build` |
| Start the development client | `gradlew.bat runClient` | `./gradlew runClient` |

Binary JARs are written to `build/libs`. With Python 3.10 or later, use the command for your target series and replace `<minecraft-version>` with a version from the [compatibility matrix](docs/RELEASE-1.0.0.md#compatibility-matrix).

```sh
python tools/build_matrix.py "<minecraft-version>"
python tools/build_native_port.py "<minecraft-version>"
```

Run `python tools/build_matrix.py` without version arguments to build all supported 1.20.x and 1.21.x targets. Run `build_native_port.py` once per 26.x target. Both scripts generate a SHA-256 checksum for each binary.

## Verification

- Gradle's `build` task runs the core regression suite. For a supported 1.20.x or 1.21.x target, use `python tools/build_matrix.py --audit "<minecraft-version>"` to verify preview pixels and applied assets in an isolated client.
- The 26.x graphical audit supports Windows. Pass `--audit` and point `--assets-dir` to the launcher's Minecraft assets directory. See the [release notes](docs/RELEASE-1.0.0.md) for the 26.3 test settings.
- For optional smoke tests, install Pillow, run `python tools/generate_fixtures.py`, and start Gradle's `runSmokeClient` task with `-PsmokeTest`.

## License

TextureMixer's original code and assets are licensed under [MIT](LICENSE). The bundled Barlow font and Gradle Wrapper are covered by their respective licenses. [Third-party notices](THIRD-PARTY-NOTICES.md)
