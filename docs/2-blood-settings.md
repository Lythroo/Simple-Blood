# 2. Blood settings

[Docs home](README.md) · [Getting started](1-getting-started.md) · **Settings** · [Kinds](3-blood-kinds.md) · [Effects](4-effects.md) · [Examples](5-examples.md) · [FAQ](6-faq.md)

You describe a mob's blood with a `SimpleBloodAPI.BloodSettings`. Chain only the setters you
need. Anything you leave out keeps Simple Blood's default and the player's settings.

```java
new SimpleBloodAPI.BloodSettings()
        .setColor(0x8B0000)
        .setBloodKind(BloodKind.LIQUID)
        .setBurstIntensityMultiplier(1.5f);
```

`BloodKind` is `com.simpleblood.BloodKind`. Everything else is a normal Minecraft or Java type.

## Colour

| Setter | What it does | Default |
|---|---|---|
| `setColor(int rgb)` | Blood colour as `0xRRGGBB`, e.g. `0x8B0000`. | Plain red |
| `setColor(int r, int g, int b)` | The same, from three values 0 to 255. | Plain red |
| `setColorProvider(Function<LivingEntity, Integer>)` | Works out the colour per mob, each time it bleeds. Return `0xRRGGBB`, or `null` to use `setColor` instead. | None |

Every drop gets a small random shade, so the blood never looks flat.

## Does it bleed?

| Setter | What it does | Default |
|---|---|---|
| `setCanBleed(boolean)` | `false`: no blood at all. | `true` |
| `setCanDripAtLowHealth(boolean)` | Drips while badly hurt. Turn it off for robots, ghosts and the undead. | `true` |
| `setBleedWhenAsphyxiating(boolean)` | Bleeds from drowning, suffocating or drying out. Simple Blood skips those by default, so a fish on land doesn't bleed. | `false` |
| `setBleedPredicate(BiPredicate<LivingEntity, DamageSource>)` | Decide hit by hit. Return `true` to bleed. Replaces the drowning/suffocation rule above for this mob. | None |

> **Heads up:** once you register a mob, `setCanBleed`, `setCanDripAtLowHealth` and
> `setTransformToStains` count as `true` unless you set them. Set them to `false` yourself
> when your mob shouldn't do those things.

## What comes out

| Setter | What it does | Default |
|---|---|---|
| `setBloodKind(BloodKind)` | Blood, bone dust, metal flakes, embers, sparks... See [Blood kinds](3-blood-kinds.md). | `LIQUID` |
| `setGlows(boolean)` | Glows in the dark: drops, clouds in water and fresh puddles. Puddles lose the glow as they dry. | `false` |

## On blocks and in water

| Setter | What it does | Default |
|---|---|---|
| `setTransformToStains(boolean)` | Blood sticks to blocks: puddles that spread, run down walls and dry, plus red clouds in water. `false`: drops just fade where they land. | `true` |
| `setLeavesFootprints(boolean)` | Feet that step in fresh blood leave prints. Turn it off for mobs that float, roll or slither. | `true` (if the player has footprints on) |
| `setCreatesFogUnderwater(boolean)` | Force the red cloud in water on or off, separately from blocks. | On for `LIQUID` blood that sticks to blocks |
| `setParticlesDespawnInWater(boolean)` | Drops vanish when they touch water, like snow melting. | On for `POWDER` and `EMBER`, off for the rest |

## How much

| Setter | What it does | Default |
|---|---|---|
| `setParticleSizeMultiplier(float)` | Size of each drop. `1.5f` = 50% bigger. | `1.0` |
| `setBurstIntensityMultiplier(float)` | How many drops a hit or death throws. | `1.0` |
| `setDripIntensityMultiplier(float)` | How much it drips while badly hurt. | `1.0` |

These multiply the player's own settings, so a player who turned blood down still sees less.

## Sound

| Setter | What it does | Default |
|---|---|---|
| `setBloodSound(SoundEvent)` | Your own sound for hits and drips, instead of Simple Blood's. | Simple Blood's sounds |
| `setSoundEnabled(boolean)` | `false`: no hit sounds for this mob. | `true` |

## Global settings

These are on `SimpleBloodAPI` itself and apply to every mob.

```java
// This damage type never draws blood, for any mob:
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.IN_FIRE);
SimpleBloodAPI.removeNonBleedingDamageType(DamageTypes.IN_FIRE);   // undo
```

## Looking things up

```java
SimpleBloodAPI.hasCustomSettings("yourmod:cave_jelly");     // is it registered?
SimpleBloodAPI.getEntityBloodSettings("yourmod:cave_jelly"); // its settings, or null
SimpleBloodAPI.getRegisteredEntities();                      // every registered id
SimpleBloodAPI.unregisterEntityBlood("yourmod:cave_jelly");  // back to the defaults
```

Registering the same mob again replaces its old settings.

## Next

- Pick what your mob is made of: [3. Blood kinds](3-blood-kinds.md)
