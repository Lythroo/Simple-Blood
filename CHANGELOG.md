# Simple Blood changelog

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
