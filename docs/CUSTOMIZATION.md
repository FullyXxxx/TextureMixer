# Customization

| Category | Resources that can be selected independently |
| --- | --- |
| Item | Vanilla item models and textures, with inventory, handheld, and supported worn previews |
| Block | Vanilla block models, block states, supported block entity resources, and water and lava textures |
| Sky | Overworld sky, End sky, rain and snow, and supported OptiFine sky layers |
| Entity and Particle | Vanilla entity textures, particle definitions, and their texture sequences |
| HUD, Menu, and Container GUI | Cataloged HUD elements, screen overlays, menus, and container interface assets |
| Sound & Music | Sound events, with playback previews and a separate preview volume |
| Enchant | Item and armor enchantment glint textures |
| Font | Native font resources with customizable sample text in the preview |

Base packs can be stacked. An individual override takes priority over the base stack. The editor can show the vanilla appearance when the selected source lacks an asset. Assets without an individual override target can still load through a base pack.

## Format and feature limits

- The asset catalog focuses on vanilla resources. Support for CIT, CTM, CEM, custom mod assets, and shaders depends on the format and the game or companion mods that handle it.
- The Sky category reads supported OptiFine layers. Effects such as galaxies and auroras come from the pack's images and appearance conditions.
- An item choice selects the source's native model resources. Minecraft handles supported conditions such as enchantments, drawing a bow, and loading a crossbow. Worn previews require an open world.
- TextureMixer combines existing resources and the client renders the result. Use a separate editor to paint textures or edit models.
