# TextureMixer 1.0.0 release verification

The November 21, 2026 verification covered 24 Minecraft Java Edition releases across 1.20.x, 1.21.x, and 26.x. Each game version has its own Fabric client JAR. All builds use TextureMixer version 1.0.0. [Release downloads](https://github.com/Xyifu/TextureMixer/releases/tag/v1.0.0)

## Water and lava

- The Block grid, source list, and confirmation dialog use a dedicated fluid preview model. The top uses the `_still` texture and the sides use the `_flow` texture. Previews use the existing model cache and the Minecraft texture atlas.
- A static replacement texture takes precedence over animation metadata from lower-priority resources. A pack's own `.png.mcmeta` stays with its texture. A missing water overlay falls back to vanilla.
- In-world rendering uses the selected source for each fluid. Water and lava buckets each have independent sources in the Item category.
- The shared confirmation dialog keeps asset previews visible on older clients.

## Client verification

Tests ran in isolated Fabric clients using temporary flat worlds. A test pack used distinct colors for the still and flowing textures, including a static lava texture and a flowing lava texture with animation metadata. For each game version, screenshots verified the vanilla Block card, custom source card, confirmation dialog, and fluid surfaces shown after applying the mix.

Snapshot checks covered all four fluid textures, flowing lava animation metadata, the vanilla water overlay fallback, and separate bucket overrides. Tests also covered diamond sword inventory and hand previews, a diamond block, a worn armor preview, the Overworld sky, snapshot save and restore, resource reload, and disabling TextureMixer.

## Compatibility matrix

| Minecraft | Minimum Java | Minimum Fabric Loader | Minimum Fabric API | Client test |
| --- | --- | --- | --- | --- |
| 1.20 | 17 | 0.18.4 | 0.83.0+1.20 | Passed |
| 1.20.1 | 17 | 0.18.4 | 0.92.12+1.20.1 | Passed |
| 1.20.2 | 17 | 0.18.4 | 0.91.6+1.20.2 | Passed |
| 1.20.3 | 17 | 0.18.4 | 0.91.1+1.20.3 | Passed |
| 1.20.4 | 17 | 0.18.4 | 0.97.3+1.20.4 | Passed |
| 1.20.5 | 21 | 0.18.4 | 0.97.8+1.20.5 | Passed |
| 1.20.6 | 21 | 0.18.4 | 0.100.8+1.20.6 | Passed |
| 1.21 | 21 | 0.18.4 | 0.102.0+1.21 | Passed |
| 1.21.1 | 21 | 0.18.4 | 0.116.18+1.21.1 | Passed |
| 1.21.2 | 21 | 0.18.4 | 0.106.1+1.21.2 | Passed |
| 1.21.3 | 21 | 0.18.4 | 0.114.1+1.21.3 | Passed |
| 1.21.4 | 21 | 0.18.4 | 0.119.4+1.21.4 | Passed |
| 1.21.5 | 21 | 0.18.4 | 0.128.2+1.21.5 | Passed |
| 1.21.6 | 21 | 0.18.4 | 0.128.2+1.21.6 | Passed |
| 1.21.7 | 21 | 0.18.4 | 0.129.0+1.21.7 | Passed |
| 1.21.8 | 21 | 0.18.4 | 0.136.1+1.21.8 | Passed |
| 1.21.9 | 21 | 0.18.4 | 0.134.1+1.21.9 | Passed |
| 1.21.10 | 21 | 0.18.4 | 0.138.4+1.21.10 | Passed |
| 1.21.11 | 21 | 0.18.4 | 0.141.6+1.21.11 | Passed |
| 26.1 | 25 | 0.19.3 | 0.145.1+26.1 | Passed |
| 26.1.1 | 25 | 0.19.3 | 0.145.4+26.1.1 | Passed |
| 26.1.2 | 25 | 0.19.3 | 0.155.3+26.1.2 | Passed |
| 26.2 | 25 | 0.19.5 | 0.161.0+26.2 | Passed |
| 26.3 | 25 | 0.20.1 | 0.166.2+26.3 | Passed |

## Test environment and scope

Minecraft 26.3 was verified on NVIDIA Vulkan with mipmapping level 0 in the isolated test instance. With the default mipmapping setting, the same machine encountered native access violation `0xC0000005` while reloading resources. A replay using a standard resource pack reproduced the exit code with TextureMixer removed. The OpenGL baseline also encountered a native startup crash.

The in-world screenshots cover water and lava surfaces. Snapshot checks include animation metadata. On Minecraft 1.20 through 1.21.3, the editor shows water in the texture's original colors, so vanilla water appears grayscale in the card. In-world water uses biome tinting. This matrix covers standard fluids in isolated clients. Waterlogged blocks, third-party fluids, server-delivered packs, and Lunar Client fall outside that matrix.

The 1.20.x and 1.21.x tests used isolated Loom clients with separate checks of their release JARs. The 26.x test clients loaded the release JARs directly.

## MIT source publication retest

On November 23, 2026, all 24 JARs were rebuilt and checked for version metadata, the MIT declaration and full license text, font notices, and SHA-256 checksums. Minecraft 1.20.4, 1.21.11, 26.1.2, and 26.3 were retested in isolated clients. The retest covered Chinese name lookup from installed Minecraft assets, mix previews, fluid rendering, and saving and restoring a mix. The 26.3 retest used NVIDIA Vulkan with mipmapping level 0.

An additional Minecraft 1.21.11 test used a custom pack with a legacy resource layout. The editor previewed the sword from that source. After the mix was applied, Minecraft's active resource manager returned a byte-for-byte match for the source texture.

## Build from source

Use JDK 25 to run the build tools. Install JDK 17 or 21 as required by the target for Gradle regressions and development clients.

```sh
python tools/build_matrix.py
python tools/build_native_port.py 26.1
python tools/build_native_port.py 26.1.1
python tools/build_native_port.py 26.1.2
python tools/build_native_port.py 26.2
python tools/build_native_port.py 26.3
```

To run the isolated Minecraft 26.3 audit on Windows, pass `--audit --backend vulkan --mipmap-levels 0` and use `--assets-dir` to point to your Minecraft assets directory. [Build and verification instructions](../README.md#build-from-source)

[Mojang Java Edition version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) · [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)
