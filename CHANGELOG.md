# Simple Blood changelog

## 1.0.2

### Debris
- Skeletons shed bone dust and chips.
- Golems shed tumbling, glinting metal flakes.
- Metal flakes lie flat and pile up.
- The creaking sheds splinters and resin.
- Blazes and magma cubes throw embers.
- Embers cool to ash, hiss in water.
- Allays and vexes give off sparks.
- Breezes throw out little gusts.
- New kinds: Bone, Metal, Wood, Spirit, Wind.
- Hand-picked kinds in your config stay.

### Glow and water
- Glow squid ink glows.
- New Glow column in the mob table.
- Glowing puddles fade as they dry.
- Underwater clouds redrawn: fewer, bigger, looser.
- Floodwater washes blood off into clouds.
- Runs stop at rising water.
- Blood falling into water clouds up.

### Physics Mod
- Ragdolls bleed ([#6](https://github.com/Lythroo/Simple-Blood/issues/6)).
- Blocky and fractured mobs bleed too.
- Sliding parts smear the ground.
- Hard landings leave stains.
- Toggle: General, "Ragdolls bleed".

### Sounds
- Hits sound like what was hit.
- Deaths sound heavier.
- Muffled bubbles under water.
- Ledges drip like a cave.
- Drops patter into water.
- Mace smashes land with a slap.
- Drying blood sticks underfoot.
- Chips tick, flakes clink, splinters tock.

### Puddles
- Fainter ripples, only on bigger pools.
- No ripples on walls or ceilings.
- Blood stays on visible surfaces only.
- No blood inside anvils, lecterns, fences.
- Blood runs down banners and signs.
- Blood shows through glass and ice ([#4](https://github.com/Lythroo/Simple-Blood/issues/4)).

### Players ([#7](https://github.com/Lythroo/Simple-Blood/issues/7))
- Skins can carry your blood colour.
- Others with Simple Blood see it.
- One click uploads it as your skin.
- Or save the skin file yourself.
- New instances read it from your skin.
- Take it back out anytime.
- Settings show what colour others see.
- Toggle: General, "Colours from skins".

### Settings
- Option to hide the menu button ([#5](https://github.com/Lythroo/Simple-Blood/issues/5)).

### Colours
- Sulfur cube bleeds pale sulfur goo.
- Allay bleeds its own cyan.
- Iron golem bleeds light steel grey.
- Hand-picked colours in your config stay.

### Fixes
- Banner blood no longer clips.
- No more black specks at corners.
- No constant splashing next to water.
- Covered buttons no longer light up.

### Renamed: bloodmod is now simpleblood
- Command is now `/simpleblood`.
- Settings carry over automatically.
- API: `com.simpleblood.SimpleBloodAPI`.
- `BloodKind` moved to `com.simpleblood`.

## 1.0.1

- Fixed a crash (ConcurrentModificationException in SurfaceRenderer) with AsyncParticles and other mods that tick particles on their own threads. Blood painted from another thread now waits for the render thread.
- Crash guard: if anything in the mod fails, that part (hits, particles, blood on blocks, settings screen) turns itself off, the game keeps running, and a chat message offers "Copy error" and "Report on GitHub". The report also lands in logs/simpleblood-error.txt. `/bloodmod reset` turns the part back on.
- Blocks whose shape cannot be read just take no blood instead of failing.
- Broken config sections fall back to defaults.
- 1.21.1: blood on blocks survives resource reloads (F3+T, server packs).

## 1.0.0

Compared with 0.1.8. Still 100 % client side.

### Blood on blocks
- Drops paint pixel art puddles onto block faces, 16 px per block or chunky 8 px.
- Puddles spread, run down walls, pool below, wrap round edges and drip. Runs drain the pool they came from.
- Puddles dry: sink to a film, darken, erode from the edge.
- Fences, walls, stairs, gates, signs, banners, lecterns and plants take blood on the drawn shape, not the hitbox. Can be turned off ("Detailed shapes").
- Water stops blood and clouds it up. Rain washes it. Placed blocks cover it, broken blocks drop it.
- Footprints from anything that walks through fresh blood, at the entity's own step rhythm.
- Landing sounds and squelching footsteps.

### Hits
- Blood comes out of the wound and flies back toward the attacker.
- Swords sweep streaks, axes splash, maces crater, arrows punch through the far side.
- Mace smashes explode. Hard hits fountain.

### Particles
- Blood kinds per mob: liquid, debris, ember, powder. Colours copied from vanilla.
- One "How much" amount and a drop cap.

### Settings screen
- New custom screen, no Cloth Config. Tabs, live 3D previews, everything applies live.
- Presets: Vanilla+, Subtle, Realistic, Cinematic, Retro, Gore, Performance.
- Mob table with colour and kind per mob, modded mobs by id.
- Open it without Mod Menu: the Blood button on the title and pause screens, or `/bloodmod`. `/bloodmod clear` wipes all blood.

### Versions
- Minecraft 26.3 added. 14 targets: 1.21.1, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3, Fabric and NeoForge.

### API
- `setBloodKind`, `setLeavesFootprints`, `spawnHit`, `spawnDeath`, `paint`, `clearSurfaceBlood`.
- `setTransformToStains(false)` now also keeps blood off blocks.

### Other
- Old configs still load. New icon. License: All Rights Reserved. Issues on GitHub.
