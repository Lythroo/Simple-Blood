## For Mod Developers

> Simple Blood exposes a client-side API to fully control blood behaviour for your
> mod's entities. Call it from your **client initializer**. Everything you register
> overrides the config and defaults; every option is optional, so you only set what
> you want to change.

The API class is `com.simpleblood.SimpleBloodAPI` on every target (Fabric and NeoForge,
Minecraft 1.21.1 through 26.3); the same calls work everywhere. Simple Blood is a
client-only mod, so there is nothing to call on the server.

### What is new in 1.0.2

- Package renamed to `com.simpleblood`.
- API class is now `SimpleBloodAPI`.
- Mod ID is now `simpleblood`.
- The API itself is unchanged.
- New kinds: `BONE`, `METAL`, `WOOD`, `SPIRIT`, `WIND`.
- `EMBER` now throws embers that cool.
- New `setGlows(true)`: blood glows in the dark.

### Depending on Simple Blood

Simple Blood is optional for your mod: check it is loaded before you call the API.

```java
// Fabric
if (FabricLoader.getInstance().isModLoaded("simpleblood")) MyBloodSetup.register();
// NeoForge
if (ModList.get().isLoaded("simpleblood")) MyBloodSetup.register();
```

Keep the calls in a class of their own (`MyBloodSetup` above), so nothing loads
`SimpleBloodAPI` when Simple Blood is not installed. To compile against it, use the
jar for your loader and Minecraft version, e.g. through the Modrinth Maven with that
file's version ID from its Modrinth page: `compileOnly "maven.modrinth:simple-blood:<id>"`
(`modCompileOnly` for Fabric on 1.21.x).

### What is new in 1.0.0

- **Blood kinds.** An entity's blood is `LIQUID`, `DEBRIS`, `EMBER` or `POWDER`
  (`com.simpleblood.BloodKind`). The kind decides what happens after the particles fly:
  liquid pools on blocks, runs down walls, clouds up in water and gets sword sweeps and
  mace craters; debris comes to rest and does nothing else; embers glow and fizzle in
  water; powder leaves flat piles. Set it with `setBloodKind`.
- **Blood on blocks.** Drops paint puddles onto block faces (fences, stairs, signs,
  banners and plants included). `setTransformToStains(false)` now keeps an entity's blood
  off blocks entirely, as well as out of the water.
- **Footprints.** Anything that walks through fresh blood leaves prints. Opt an entity out
  with `setLeavesFootprints(false)`.
- **Effects on demand.** `spawnHit`, `spawnDeath`, `paint`, `clearSurfaceBlood` and
  `surfaceBloodCount` let your own code trigger or inspect the effects (see below).
- Colours copied from vanilla's own particles are the defaults for vanilla mobs, and hits
  are analysed for direction, weapon and severity; none of that needs API calls.

```java
import com.simpleblood.SimpleBloodAPI;
import com.simpleblood.BloodKind;

// Registering by the raw id string works on every loader & MC version:
SimpleBloodAPI.registerEntityBlood("yourmod:custom_mob", new SimpleBloodAPI.BloodSettings()
        .setColor(0xFF0000)             // blood colour 0xRRGGBB (or setColor(r, g, b))
        .setCanBleed(true)
        .setCanDripAtLowHealth(true)    // continuous drip while wounded
        .setTransformToStains(true));   // ground stains + underwater fog

// You can also register by EntityType:
SimpleBloodAPI.registerEntityBlood(YourEntities.CUSTOM_MOB, new SimpleBloodAPI.BloodSettings()
        .setColor(0x8B0000));
```

### All `BloodSettings` options

`BloodKind` lives in `com.simpleblood.BloodKind`; `Color`, `SoundEvent`, `DamageSource` and
`LivingEntity` are the usual Minecraft types.

**Colour**
- `setColor(int rgb)` / `setColor(int r, int g, int b)`: static blood colour.
- `setColorProvider(Function<LivingEntity, Integer>)`: **dynamic** colour resolved per
  entity instance (e.g. follow an entity's tint or state). Return `null` to fall back.

**Core behaviour**
- `setCanBleed(boolean)`: whether it bleeds at all.
- `setCanDripAtLowHealth(boolean)`: set `false` for constructs/undead.
- `setTransformToStains(boolean)`: blood sticks to blocks (puddles, runs, stains) and
  clouds up in water. `false`: it stays as drops that fade where they land.
- `setBloodKind(BloodKind)`: what comes out (default `LIQUID`):
  - `LIQUID`: blood. Pools, runs down walls, clouds up in water.
  - `DEBRIS`: dry bits that come to rest.
  - `EMBER`: embers that cool to ash, hiss out in water.
  - `POWDER`: snow. Flat piles that melt.
  - `BONE`: bone dust and spinning chips.
  - `METAL`: tumbling flakes that glint and clink.
  - `WOOD`: splinters, plus resin drops (in your colour) that pool.
  - `SPIRIT`: glowing sparks that float away.
  - `WIND`: little gusts.
- `setGlows(boolean)`: drops, clouds in water and fresh puddles glow in the dark; puddles
  lose the glow as they dry (1.0.2).
- `setLeavesFootprints(boolean)`: feet pick up fresh blood and leave a trail (default
  yes while the Footprints setting is on).

**Water & environment**
- `setCreatesFogUnderwater(boolean)`: force the underwater-fog behaviour on its own.
- `setParticlesDespawnInWater(boolean)`: particles vanish on water contact (snow-golem style).
- `setBleedWhenAsphyxiating(boolean)`: opt back **in** to bleeding from drowning /
  suffocation / drying-out (Simple Blood suppresses those by default).

**Amount, size & sound**
- `setParticleSizeMultiplier(float)`: droplet size (1.0 = default).
- `setBurstIntensityMultiplier(float)`: particles per hit/death.
- `setDripIntensityMultiplier(float)`: low-health drip amount.
- `setBloodSound(SoundEvent)`: custom blood sound.
- `setSoundEnabled(boolean)`: per-entity sound toggle.

**Advanced**
- `setBleedPredicate(BiPredicate<LivingEntity, DamageSource>)`: full control over which
  hits bleed; return `true` to bleed. Overrides the default asphyxiation filtering.

### Examples

```java
// Dynamic colour that follows the entity:
SimpleBloodAPI.registerEntityBlood("yourmod:rainbow_slime", new SimpleBloodAPI.BloodSettings()
        .setColorProvider(e -> ((RainbowSlime) e).getTintRGB()));

// A treant: wood splinters, and amber sap that pools like blood:
SimpleBloodAPI.registerEntityBlood("yourmod:treant", new SimpleBloodAPI.BloodSettings()
        .setColor(0xC98A2E)
        .setBloodKind(BloodKind.WOOD)
        .setCanDripAtLowHealth(false));

// A clockwork knight: grey metal flakes, and no footprints:
SimpleBloodAPI.registerEntityBlood("yourmod:clockwork_knight", new SimpleBloodAPI.BloodSettings()
        .setColor(0xB4B4B4)
        .setBloodKind(BloodKind.METAL)
        .setLeavesFootprints(false));

// A cave jelly: blue blood that glows in the dark:
SimpleBloodAPI.registerEntityBlood("yourmod:cave_jelly", new SimpleBloodAPI.BloodSettings()
        .setColor(0x3FA9F5)
        .setGlows(true));

// A magma beast: embers that cool to ash and hiss out in water:
SimpleBloodAPI.registerEntityBlood("yourmod:magma_beast", new SimpleBloodAPI.BloodSettings()
        .setColor(0xFF6A00)
        .setBloodKind(BloodKind.EMBER));

// A water elemental that DOES bleed when it dries out:
SimpleBloodAPI.registerEntityBlood("yourmod:water_elemental", new SimpleBloodAPI.BloodSettings()
        .setBleedWhenAsphyxiating(true));

// A big boss that bleeds heavily, but never from fall damage:
SimpleBloodAPI.registerEntityBlood("yourmod:colossus", new SimpleBloodAPI.BloodSettings()
        .setParticleSizeMultiplier(1.5f)
        .setBurstIntensityMultiplier(2.0f)
        .setBleedPredicate((entity, source) -> !source.is(DamageTypes.FALL)));
```

### Global controls

```java
// Stop these damage types from ever producing blood, for ANY entity:
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.IN_FIRE);
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.LAVA);

SimpleBloodAPI.getRegisteredEntities();            // Set<String> of registered ids
SimpleBloodAPI.unregisterEntityBlood("yourmod:x"); // remove one
```

### Effects on demand (1.0.0)

All client side. The render thread (a client tick or event handler) is the natural place,
but since 1.0.1 any thread works: the effect is queued for the render thread.

```java
// A hit burst as if the entity had just taken 6 damage. Same colour, kind, size and
// puddles as a real hit; the entity is not hurt. Returns false if it cannot bleed.
SimpleBloodAPI.spawnHit(entity, 6.0f);

// The death burst.
SimpleBloodAPI.spawnDeath(entity);

// Blood on the first block face along a line, as if a drop had landed there. It spreads,
// runs down sides and dries like any puddle. pixels: about 1-3 for a drop, 3-6 for a
// splash (at 16 px per block edge). Honours the player's "Puddles" settings. Painted
// blood does not glow, whatever the entity's setGlows.
SimpleBloodAPI.paint(level, eyePos, eyePos.add(look.scale(4)), 0x8B0000, 12);

SimpleBloodAPI.surfaceBloodCount();   // block faces holding blood right now
SimpleBloodAPI.clearSurfaceBlood();   // wipe it all (what /simpleblood clear does)
```

The built-in drowning / suffocation / dry-out suppression always applies unless an
entity opts out via `setBleedWhenAsphyxiating(true)` or its own `bleedPredicate`.

### Errors (1.0.1)

Nothing in the API throws into your code. An error inside Simple Blood is caught, logged
with a report (`logs/simpleblood-error.txt`) and shown to the player as a chat line with a
"Copy error" link; the call then returns `false`, `null` or does nothing. If a part of the
mod keeps failing it switches itself off until `/simpleblood reset` or the next world.
