# Simple Blood

1.0.2 soon on Curseforge and Modrinth

Adds simple blood particles to Minecraft. Pixel art, client side only, works on any server.

- Blood on blocks: puddles that spread, run down walls and dry.
- Fences, stairs, signs, banners, lecterns and plants take blood on what you see, not the hitbox.
- Footprints through fresh blood.
- Directional hits: swords sweep, axes splash, maces crater, arrows punch through.
- Every mob its own blood, modded mobs too. Clouds underwater, rain washes it away.
- Mobs without blood shed what they are made of: bone dust and chips, metal flakes, wood
  splinters, embers, glowing sparks, gusts. Glow squid ink glows.
- Sounds for hits, drops, drips and footsteps, in the voice of what was hit.
- Physics Mod ragdolls bleed, smear and stain where they land.
- Your blood colour travels in your skin, so other players see it.
- Settings screen with live previews and presets. Blood button on the title screen or `/simpleblood`.

Fabric and NeoForge, Minecraft 1.21.1 to 26.3.

## Your blood colour in your skin

Simple Blood runs only on your computer, so a server cannot pass your blood colour on. Your
skin can. In the settings, General, "Show it to other players" puts your colour in a hidden
corner of your skin and makes that your Minecraft skin (it asks first). "Or do it yourself"
saves the same skin as a file to upload on minecraft.net or in the launcher.
Everyone with Simple Blood then sees you bleed that colour. On a new instance, Simple Blood
reads your colour back from your skin. "Take it out of your skin" removes it again.

By hand: in a 64x64 skin, paint pixel x 16, y 48 pure magenta `#FF00FF` and pixel x 17, y 48
your blood colour, both fully opaque. That corner, above the left leg, is never drawn, so your
skin looks the same.

## Building

One source tree, built with [Stonecutter](https://stonecutter.kikugie.dev/).

```
./gradlew build                         every version
./gradlew 26.3-fabric:build             one version
./gradlew 26.3-fabric:runClient         dev client
```

Jars land in `versions/<version>-<loader>/build/libs/`. Versions and their dependencies are
listed in `versions/<version>-<loader>/gradle.properties`.

## For mod developers

Other mods can set colour, kind and behaviour of their mobs' blood and trigger effects
through `com.simpleblood.SimpleBloodAPI`. See [FOR_MOD_DEVELOPERS.md](FOR_MOD_DEVELOPERS.md).

## Errors

If something in the mod fails, that part turns itself off and the game keeps running. A chat
message offers "Copy error"; please post it at
https://github.com/Lythroo/Simple-Blood/issues. `/simpleblood reset` turns the part back on.

## License

All rights reserved. The code is public to read and to report issues against; do not copy,
modify or redistribute it without permission.
