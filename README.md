# Simple Blood

Adds simple blood particles to Minecraft. Pixel art, client side only, works on any server.

- Blood on blocks: puddles that spread, run down walls and dry.
- Fences, stairs, signs, banners, lecterns and plants take blood on what you see, not the hitbox.
- Footprints through fresh blood.
- Directional hits: swords sweep, axes splash, maces crater, arrows punch through.
- Every mob its own blood, modded mobs too. Clouds underwater, rain washes it away.
- Settings screen with live previews and presets. Blood button on the title screen or `/bloodmod`.

Fabric and NeoForge, Minecraft 1.21.1 to 26.3.

## Building

One source tree, built with [Stonecutter](https://stonecutter.kikugie.dev/).

```
./gradlew build                         every version
./gradlew 26.3-fabric:build             one version
./gradlew 26.3-fabric:runClient         dev client
./gradlew 26.3-fabric:build -Pstudio    studio build (adds /bloodstudio, a filming aid)
```

Jars land in `versions/<version>-<loader>/build/libs/`. Versions and their dependencies are
listed in `versions/<version>-<loader>/gradle.properties`.

## For mod developers

Other mods can set colour, kind and behaviour of their mobs' blood and trigger effects
through `com.bloodmod.BloodModAPI`. See [FOR_MOD_DEVELOPERS.md](FOR_MOD_DEVELOPERS.md).

## Errors

If something in the mod fails, that part turns itself off and the game keeps running. A chat
message offers "Copy error"; please post it at
https://github.com/Lythroo/Simple-Blood/issues. `/bloodmod reset` turns the part back on.

## License

All rights reserved. The code is public to read and to report issues against; do not copy,
modify or redistribute it without permission.
