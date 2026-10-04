# 5. Examples

[Docs home](README.md) · [Getting started](1-getting-started.md) · [Settings](2-blood-settings.md) · [Kinds](3-blood-kinds.md) · [Effects](4-effects.md) · **Examples** · [FAQ](6-faq.md)

Copy, paste, change the id. All of these go in your `MyBlood.register()` from
[Getting started](1-getting-started.md#3-only-call-it-when-simple-blood-is-installed).

```java
import com.simpleblood.BloodKind;
import com.simpleblood.SimpleBloodAPI;
import com.simpleblood.SimpleBloodAPI.BloodSettings;
```

## A big animal that bleeds a lot

```java
SimpleBloodAPI.registerEntityBlood("yourmod:grizzly", new BloodSettings()
        .setColor(0x7A0A0A)
        .setParticleSizeMultiplier(1.4f)
        .setBurstIntensityMultiplier(1.5f));
```

## A robot

Metal flakes, no dripping, no footprints.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:sentry_bot", new BloodSettings()
        .setColor(0xB4B4B4)
        .setBloodKind(BloodKind.METAL)
        .setCanDripAtLowHealth(false)
        .setLeavesFootprints(false));
```

## A ghost

Glowing sparks that float away. It floats, so no footprints.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:wisp", new BloodSettings()
        .setColor(0x9EE7FF)
        .setBloodKind(BloodKind.SPIRIT)
        .setGlows(true)
        .setCanDripAtLowHealth(false)
        .setLeavesFootprints(false));
```

## A fire creature

Embers that cool to ash and hiss out in water.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:magma_beast", new BloodSettings()
        .setColor(0xFF6A00)
        .setBloodKind(BloodKind.EMBER));
```

## A tree creature

Wood splinters, and amber sap that pools like blood.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:treant", new BloodSettings()
        .setColor(0xC98A2E)
        .setBloodKind(BloodKind.WOOD)
        .setCanDripAtLowHealth(false));
```

## A skeleton-type mob

```java
SimpleBloodAPI.registerEntityBlood("yourmod:bone_knight", new BloodSettings()
        .setColor(0xE8E4D8)
        .setBloodKind(BloodKind.BONE)
        .setCanDripAtLowHealth(false));
```

## Glowing cave blood

```java
SimpleBloodAPI.registerEntityBlood("yourmod:cave_jelly", new BloodSettings()
        .setColor(0x3FA9F5)
        .setGlows(true));
```

## Blood that matches the mob's colour

For mobs that come in many colours, work it out per mob. Return `null` to fall back to
`setColor`.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:jelly", new BloodSettings()
        .setColor(0x55CC55)
        .setColorProvider(entity -> entity instanceof Jelly jelly ? jelly.getColorRGB() : null));
```

## A mob that doesn't bleed

```java
SimpleBloodAPI.registerEntityBlood("yourmod:stone_statue", new BloodSettings()
        .setCanBleed(false));
```

## Blood that doesn't stick to blocks

Drops fly and fade, but leave no puddles or clouds in water.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:slimeling", new BloodSettings()
        .setColor(0x66DD44)
        .setTransformToStains(false));
```

## A boss that doesn't bleed from falling

```java
SimpleBloodAPI.registerEntityBlood("yourmod:colossus", new BloodSettings()
        .setBurstIntensityMultiplier(2.0f)
        .setBleedPredicate((entity, source) -> !source.is(DamageTypes.FALL)));
```

## A fish that bleeds when it dries out

Simple Blood skips blood from drowning, suffocating and drying out. This turns it back on.

```java
SimpleBloodAPI.registerEntityBlood("yourmod:lungfish", new BloodSettings()
        .setBleedWhenAsphyxiating(true));
```

## No blood from fire or lava, for any mob

```java
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.IN_FIRE);
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.ON_FIRE);
SimpleBloodAPI.addNonBleedingDamageType(DamageTypes.LAVA);
```

## A blood burst in a cutscene

Client side, for example when your boss roars. The boss isn't hurt.

```java
SimpleBloodAPI.spawnHit(boss, 10.0f);
```

## Next

- Something not working? [6. FAQ and troubleshooting](6-faq.md)
