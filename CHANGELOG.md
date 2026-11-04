# Simple Blood changelog

## 1.3.0

Everything new since 1.2.1, in one place.

### Settings
- Settings rebuilt with categories and search.
- Big live preview, only where useful.
- Previews run the real hit code.
- Previews can replay your own hit.
- Your skin shows in colour previews.
- Sliders ignore the scroll wheel now.
- Dropdowns for settings with many choices.
- Mobs shown as cards with eggs.
- Cleaner mob settings panel.
- Export, import and reset settings.
- Save and load settings as files.
- Menu animations can be switched off.
- All text can now be translated.

### Blood on blocks
- Big pools: darker deep middle, dried rim.
- Fresh snow buries the blood.
- Blood soaks into snow, stays red.
- A bite of cake takes its blood.
- Blood sizzles and dries near heat.
- Drops falling into lava sizzle away.
- Blood sits only where a block's texture is drawn.
- Drops fall through holes in leaves, bars and trapdoors.
- Blood in a water cauldron clouds it and turns the water itself redder (it waves along with shaders), clearing again with time; nothing lies on the water.
- Runs flow down every step of a stair.
- Blood follows 3D block models from packs.
- Switching packs moves blood to new shapes.
- Interactive Foliage plants stay clean.

### Moving blocks
- Blood opens with chest lids.
- Shulker box lids carry it too.
- Shulker blood respects the shell's arches.
- Runs cross from lid to base when shut.
- Blood lands inside open chests.
- Tilting surfaces: excess blood runs downhill.
- The puddle stays behind as a stain.
- Runs go round a lid's edge.
- Turned upside down, blood drips.
- Doors and trapdoors keep their blood when opened.
- Turning lets only a few fresh drops go.
- Drops keep the motion of what they leave.

### Create, Aeronautics and Sable
- Blood rides along on Sable structures.
- Drops hit falling Sable ships properly.
- Blood rides along on Create contraptions.
- Blood turns with shafts, cogs and wheels.
- Also on fans, drills and millstones.
- Fast Create parts fling their blood off.
- No blood floats over Create parts' gaps.
- Blood on Create parts sits on their texels.

### Mobs
- New Armor column in the mob table.
- Set any mob to Armor or Metal.
- Modded knights are guessed armored.
- Mob table fits narrow windows.
- Blood skips see-through mob parts.
- Every hit leaves blood on bodies.
- Dinnerbone mobs keep their blood.

### Players
- Pick your blood type too.
- It travels in your skin.

### Ragdolls
- Bleed time goes up to 60 seconds.
- Blood on mobs stays on Ragdollified ragdolls.
- Saro's ragdolls keep the player's blood.
- Cut-off limbs keep their blood and bleed.
- Stumps spurt when a limb comes off.
- Physics Mod ragdolls keep blood exactly.
- Physics Mod pieces keep it too.
- Still ragdolls bleed into a puddle.

### Weapons
- New page: wound per weapon.
- Pick a wound for any weapon.
- Modded weapons are sorted automatically.
- Epic Fight weapons get proper wounds.
- Axes can sweep like swords.
- Spears skewer, bursting out the back.
- Spear stabs leave an exit wound.
- Every damage type gets its switch.

### Sound
- Play button next to every sound.
- Previews loop and name each file.
- Louder drops, drips and water.
- New Heat slider for the sizzle.
- New squelch, splat and slap sounds.
- No more reused slime sounds.

### Fixes
- Blood on mobs safe with AsyncParticles.
- Giblet trails safe with AsyncParticles.
- No lost drops with AsyncParticles.
- Quick follow-up hits keep their weapon.
- No blood inside shulker boxes' hidden faces.
- Dropdown text no longer overlaps.
- Banners no longer turn white under blood.
- Drops fly off moving things with their motion.

### Known issues
- Chat names installed mods with known issues.
- Epic Fight mobs get no body blood.
  Splatter, pools and drips still work.

## 1.2.1

### Fixes
- Armor sparks go out in water.
- No sparks from blows under water.
- Armor clank a little quieter.

## 1.2.0

### Combat
- Sparks and shards off hard armor.
- Metal clank, sharper tink for arrows.
- Armor takes some of the blood.
- Iron golems spark when struck too.
- Anvils and dripstone hit like maces.

### Bodies
- Blood lands on other mobs and players.
- Feet get bloody in puddles.
- Wound drips follow the moving limb.
- A pool spreads under every corpse.
- Fresh Animations support through EMF.

### Ragdolls
- Ragdollified ragdolls bleed like Physics Mod.
- Saro's Player Ragdoll bleeds too.

### Water
- Splash potions wash blood away.
- Lingering clouds and cauldrons too.

### Look and sound
- Drops and splashes tumble while flying.
- Every sound has its own slider.

### Settings
- New Combat tabs, ragdolls get their own.
- Many new previews with captions.
- Preview zombie fixed with Fresh Animations.

### Fixes
- No crash under water on 26.3.
- High drag no longer freezes the game.
- Hurt undead drip blood again.
- Wound drips follow limbs on newer versions.
- Blood splashed on walls runs down.
- No more ETF log spam with EMF.

### For mod developers
- Metal bodies, pools, giblets per mob.
- Register hard and soft items.
- Hook in your own ragdolls.
- Trigger sparks, body stains and pools.

### Fixes
- No crash on 26.3 under water ([#12](https://github.com/Lythroo/Simple-Blood/issues/12)).
- No blood floating over dying mobs.

## 1.1.1

### Blood on mobs
- Optional blood that sticks to mobs.
- Runs down and drips off the body.
- Dries away after a while.
- Off by default; the Gore preset turns it on.

### Modded mobs
- Mob table matches the game you run.
- Mobs from other mods get a first guess.
- Guessed from the name: bones, metal, embers...

### Settings
- Done is now Save.
- New Reset to defaults button.
- Smaller bottom bar.
- Sidebar tabs no longer overflow.
- Presets switch giblets and blood on mobs off; Gore on.
- Old Reset all on the presets page removed.

### Fixes
- No crash with AsyncParticles under water ([#11](https://github.com/Lythroo/Simple-Blood/issues/11)).
- Blood on mobs works with Sodium on 1.20.1 and 1.21.1.

## 1.1.0

### Versions
- Minecraft 1.20.1, Fabric and NeoForge.

### Shaders
- Wet, glossy puddles under Iris.
- Drying blood loses its shine.
- Packs need labPBR switched on.
- Underwater clouds no longer glare.
- Swaying leaves and plants skip blood.

### Giblets
- Optional meat chunks fly off mobs.
- Off by default, under Hits.
- The Gore preset turns them on.
- They leave a trail of blood.

### Kinds
- New settings page for debris blood.
- Amount per kind: bone to wind.
- Dust, gust and glow cloud thickness.
- Toggles for glint, smoke, glowing blood.
- Fainter bone dust, thinner glowing clouds.

### Physics Mod
- New settings page for ragdolls.
- Bleed time, drips, splashes, stains, smears.
- Pieces bleeding can be switched off.

### Moving blocks
- Blood swings with doors and trapdoors.
- Fence gates carry their blood along.
- Pressed buttons and plates keep it.
- Pistons push and pull blood along.
- Piston heads take the blood out.

### Fixes
- Heavy runs drip off the bottom.
- Test buttons always paint puddles.
- Drops no longer freeze mid-air.
- No blood floating above fences.
- Drops land on edges and corners.
- Blood falls through fence gaps.
- Stray drops soak away.
- Underwater blood and debris visible from above.
- Blood on ice and glass no longer see-through.
- Settings previews no longer crash.

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
