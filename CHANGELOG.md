# Simple Blood changelog

## 1.0.2

### Debris
- Skeletons no longer bleed tinted blood drops. They shed puffs of bone dust that bloom and crumble into drifting grains, plus little bone chips that spin, bounce and settle with a dry tick.
- Iron and copper golems shed metal flakes that tumble, catch the light and clink as they land, then lie flat on the ground, heap up where they fall on each other, and twinkle.
- The creaking sheds wood splinters that tumble and lie where they fall, along with drops of orange resin that pool like blood.
- Blazes and magma cubes throw embers: they leave white-hot, flicker, cool through orange and deep red to ash, hiss out in water and now and then go up in a wisp of smoke.
- Allays and vexes give off glowing sparks that twinkle and float away; breezes throw out little curling gusts.
- Five new blood kinds in the mob table: Bone, Metal, Wood, Spirit and Wind. Skeletons, strays, bogged, parched, wither skeletons, skeleton horses and the wither use Bone; golems Metal; the creaking Wood; allays and vexes Spirit; breezes Wind. Mobs you set to another kind by hand keep it.

### Glow and water
- Glow squid ink glows: drops, clouds in water and fresh puddles light themselves, and puddles lose the glow as they dry. A new Glow column in the mob table turns it on for any mob.
- Blood clouds under water are redrawn and bigger: a wound gives off a few big, loose clouds that billow out and break up into drifting patches, instead of a swarm of small round blobs. Glowing clouds are as bright as glow squid ink.
- Water that comes over blood later (a flooded hole, a waterlogged block) washes it off and clouds up, instead of leaving paint under the water. Trickles running down a wall stop at water that has risen since, and blood falling off a broken block into water clouds up instead of painting the bottom.

### Physics Mod
- Ragdolls bleed ([#6](https://github.com/Lythroo/Simple-Blood/issues/6)): a mob that dies into a Physics Mod ragdoll, or falls apart into blocky or fractured pieces, keeps dripping for a few seconds, smears blood along the ground where a part slides, and leaves a stain right where a part slams into the ground or a wall. Breaking ragdolls and loose pieces stain at every hard landing; whole ragdolls now and then, and smaller. Skeletons, golems and the like knock off their own pieces instead. Switch: General, "Ragdolls bleed" (shown when Physics Mod is installed).

### Sounds
- Hits sound like what the mob is made of: a thick drop (with a low wet squelch on hard blows), bone cracking, a metal tink, a knock on wood, a glassy chime, a rush of wind, embers crackling, snow crunching. Deaths get a heavier version. A sound set through the API still replaces it.
- Under water, blood is heard as a muffled burst of bubbles.
- Drops running off ledges and ceilings drip, now and then, like a cave.
- Drops falling into water patter faintly; embers hiss as they go out.
- Mace smashes and slams land with a wet slap.
- Drying blood tacks underfoot instead of squelching; snow crunches.
- Bone chips tick, metal flakes clink and splinters tock as they land.

### Puddles
- Landing ripples are fainter, die out at the rim of the pool, and scale with the pool and the drop: small drips and specks no longer ripple.
- No more ripples on walls, ceilings, signs and other upright or overhanging surfaces.
- Blood keeps to what can be seen: on anvils, lecterns, fences, stairs and other shaped blocks it no longer spreads, runs or shows behind parts of the block (or a block pressed against it); a run stops where something covers the face and pools on top of it.
- Blood that hits a banner or sign trickles down it and drips off the bottom edge.
- Blood on glass, panes, ice, slime and honey blocks shows through from the other side, like blood on a window ([#4](https://github.com/Lythroo/Simple-Blood/issues/4)).

### Settings
- New "Menu button" switch (General) hides the Blood button on the title and pause screens; `/simpleblood` still opens the settings ([#5](https://github.com/Lythroo/Simple-Blood/issues/5)).

### Colours
- Sulfur cube bleeds pale sulfur goo (liquid, taken from its vanilla goo particle) instead of glowing yellow embers.
- Allay bleeds its own cyan instead of the vex's grey-blue.
- Iron golems bleed a light steel grey taken from their texture.
- Existing configs pick up these fixes unless those mobs' colours were changed by hand.

### Fixes
- Blood on a swinging banner moves smoothly with the cloth instead of tick by tick, so it no longer dips into the bottom of the cloth.
- Blood at the corner of a block next to a drop no longer leaks drops into the block beside it, where they showed as black specks and kept coming.
- Blood draining into water no longer makes a constant splashing; ledge drips have a short pause between them.

### Renamed inside: bloodmod is now simpleblood
- The mod ID, resource namespace and Java package are `simpleblood` / `com.simpleblood`, to match the mod's name.
- The command is now `/simpleblood` (`/simpleblood clear`, `/simpleblood reset`).
- Your settings carry over: `config/bloodmod.json` is moved to `config/simpleblood.json` on first start.
- For mod developers: the API is `com.simpleblood.SimpleBloodAPI` (was `com.bloodmod.BloodModAPI`); `BloodKind` moved to `com.simpleblood.BloodKind`. Everything else about it is unchanged.

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
