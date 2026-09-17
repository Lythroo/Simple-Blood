# Simple Blood - unified multi-loader / multi-version

One source tree that builds **Fabric** and **NeoForge** jars for seven Minecraft
versions, driven by [Stonecutter](https://stonecutter.kikugie.dev/).

| Minecraft | Fabric | NeoForge | Java | Mappings |
|-----------|:------:|:--------:|:----:|----------|
| 1.21.1    | ✅ | ✅ | 21 | obfuscated (Mojang mappings, `ResourceLocation`) |
| 1.21.11   | ✅ | ✅ | 21 | obfuscated (Mojang mappings, `Identifier`) |
| 26.1      | ✅ | ✅ | 25 | unobfuscated |
| 26.1.1    | ✅ | ✅ | 25 | unobfuscated |
| 26.1.2    | ✅ | ✅ | 25 | unobfuscated |
| 26.2      | ✅ | ✅ | 25 | unobfuscated |
| 26.3      | ✅ | ✅ | 25 | unobfuscated |

## Building

```
./gradlew <version>-<loader>:build     # one target, e.g. 26.1.2-fabric:build
./gradlew build                         # every target
```

Built jars land in `versions/<version>-<loader>/build/libs/`. A copy of the
latest set is kept in `dist/`.

## Studio build (filming)

`./gradlew <node>:build -Pstudio` makes `simpleblood-<loader>-<version>-studio.jar`: the
same mod plus the client command `/bloodstudio`, for footage. (Any jar does the same with
the JVM argument `-Dbloodmod.studio=true`.) Emitters keep bleeding at a fixed spot, are
saved in `config/bloodmod-studio.json`, and keep running across restarts and in replays.

```
/bloodstudio add <type> [interval] [amount] [radius]   emitter where you look
/bloodstudio at <x> <y> <z> <type> [interval] [amount] [radius]
/bloodstudio list | remove <id> | move <id> | clear | pause | resume
/bloodstudio colour <hex> | kind <liquid|debris|ember|powder>   for new emitters
/bloodstudio hit [damage] | death                     burst on the mob you look at
```

Types: `drip` (single drops, dead straight down), `splash` (bursts in all directions), `pour` (a straight stream),
`spray` (thrown the way you looked when adding), `puddle` (paint only, grows), `fog`
(underwater clouds), `rain` (drops over an area, `radius`). `interval` is in ticks.

## How it's wired

* **`settings.gradle.kts`** declares the 14 nodes (`<mcversion>-<loader>`) and
  picks a buildscript per node:
  * `build.fabric.gradle` - unobfuscated Fabric (26.x), plain `fabric-loom`.
  * `build.fabric-legacy.gradle` - obfuscated Fabric (1.21.x), `fabric-loom-remap`
    with official Mojang mappings.
  * `build.neoforge.gradle` - all NeoForge versions, `net.neoforged.moddev`.
* **`stonecutter.gradle.kts`** is the controller (active version, loader constant).
* **`versions/<node>/gradle.properties`** holds that node's dependency versions.

### Source layout (`src/main/java/com/bloodmod/`)

* Shared logic (loader-agnostic): `BloodMod`, `BloodModClient`, `BloodColor`,
  `BloodModConfig`, `BloodModAPI`, `ClientBloodBurstTask`,
  `ClientBloodParticleSpawner`, `BloodModConfigScreenFactory`, `particle/*`,
  `mixin/LivingEntityMixin`.
* Loader glue: `fabric/*` and `neoforge/*`, each fully wrapped in a Stonecutter
  `//? if fabric {` / `//? if neoforge {` guard.
* `BloodParticles` forks registration per loader (Fabric registers eagerly;
  NeoForge uses a `DeferredRegister`).

### Version differences (Stonecutter directives)

* `Identifier` ⇄ `ResourceLocation`, and the older `ParticleFactoryRegistry`
  name, are handled by string replacements in the legacy Fabric / NeoForge
  buildscripts (1.21.1 / all 1.21.x respectively).
* 1.21.1 particle classes fork the base type (`TextureSheetParticle` vs
  `SingleQuadParticle`), the render-type method (`getRenderType` vs `getLayer`),
  and the `createParticle` signature (no `RandomSource` param) via `//? if 1.21.1`.

## Optional dependencies

Cloth Config and (Fabric) Mod Menu are compile-only and guarded at runtime via
`isModLoaded`, so every jar loads even where those libraries have no release yet
- the mod then falls back to editing `config/bloodmod.json` directly.

## Developer API (`com.bloodmod.BloodModAPI`)

Other mods can fully control how Simple Blood treats their entities. Everything
registered via the API takes priority over the JSON config and built-in defaults,
and every `BloodSettings` field is optional - unset fields inherit the config.

Call it from your **client** initializer (Simple Blood is client-only).

```java
// Register by EntityType (recommended), Identifier, or "namespace:path" string.
BloodModAPI.registerEntityBlood(MyEntities.GRIZZLY_BEAR, new BloodModAPI.BloodSettings()
        .setColor(0x8B0000)                  // static colour 0xRRGGBB (or setColor(r,g,b))
        .setParticleSizeMultiplier(1.4f)     // bigger droplets
        .setBurstIntensityMultiplier(1.5f)); // more particles per hit/death
```

### `BloodSettings` options

| Method | Effect |
|--------|--------|
| `setColor(int rgb)` / `setColor(r,g,b)` | Static blood colour. |
| `setColorProvider(Function<LivingEntity,Integer>)` | **Dynamic** per-instance colour (e.g. follow an entity's tint). Return `null` to fall back. |
| `setCanBleed(bool)` | Whether it bleeds at all. |
| `setCanDripAtLowHealth(bool)` | Continuous drip while wounded (false for constructs/undead). |
| `setTransformToStains(bool)` | Leaves ground stains + water fog. |
| `setCreatesFogUnderwater(bool)` | Force the underwater-fog behaviour independently of stains. |
| `setParticlesDespawnInWater(bool)` | Particles melt on water contact (snow-golem style). |
| `setBleedWhenAsphyxiating(bool)` | Opt back **in** to bleeding from drowning/suffocation/dry-out (suppressed by default). |
| `setParticleSizeMultiplier(float)` | Scale droplet size. |
| `setBurstIntensityMultiplier(float)` | Scale hit/death particle counts. |
| `setDripIntensityMultiplier(float)` | Scale low-health drip counts. |
| `setBloodSound(SoundEvent)` | Custom blood sound. |
| `setSoundEnabled(bool)` | Per-entity sound toggle. |
| `setBleedPredicate(BiPredicate<LivingEntity,DamageSource>)` | Full control over which hits bleed. |

```java
// Dynamic colour that follows the entity:
BloodModAPI.registerEntityBlood("mymod:rainbow_slime", new BloodModAPI.BloodSettings()
        .setColorProvider(e -> ((RainbowSlime) e).getTintRGB()));

// A water elemental that DOES bleed when it dries out:
BloodModAPI.registerEntityBlood("mymod:water_elemental", new BloodModAPI.BloodSettings()
        .setBleedWhenAsphyxiating(true));

// Only bleed from real attacks, never fall damage:
BloodModAPI.registerEntityBlood("mymod:golem", new BloodModAPI.BloodSettings()
        .setBleedPredicate((entity, source) -> !source.is(DamageTypes.FALL)));
```

### Global controls

```java
// Never produce blood from these damage types, for ANY entity:
BloodModAPI.addNonBleedingDamageType(DamageTypes.IN_FIRE);
BloodModAPI.addNonBleedingDamageType(DamageTypes.LAVA);

BloodModAPI.getRegisteredEntities();          // inspect what's registered
BloodModAPI.unregisterEntityBlood("mymod:x"); // remove one
```

The built-in drowning / suffocation / dry-out suppression always applies unless an
entity opts in via `setBleedWhenAsphyxiating(true)` or its own `bleedPredicate`.
