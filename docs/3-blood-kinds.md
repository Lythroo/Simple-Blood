# 3. Blood kinds

[Docs home](README.md) · [Getting started](1-getting-started.md) · [Settings](2-blood-settings.md) · **Kinds** · [Effects](4-effects.md) · [Examples](5-examples.md) · [FAQ](6-faq.md)

Not every mob is full of blood. A skeleton crumbles, a golem sheds metal, a blaze throws
embers. The **kind** says what comes out of your mob and how it behaves afterwards.

```java
import com.simpleblood.BloodKind;

new SimpleBloodAPI.BloodSettings()
        .setColor(0xB4B4B4)
        .setBloodKind(BloodKind.METAL);
```

The colour still applies to every kind: grey flakes, blue sparks, green bone dust, whatever you
like.

## Which kind fits my mob?

| Kind | What comes out | Sticks to blocks? | Vanilla mobs that use it | Good for |
|---|---|---|---|---|
| `LIQUID` | Blood. Pools, spreads, runs down walls, drips off ledges, clouds up in water. Swords leave streaks, maces leave craters. | Yes, as puddles | Most mobs | Animals, monsters, anything alive. Also ink, slime or sap. |
| `BONE` | Bone dust and small chips that spin, bounce and settle. | No | Skeletons, strays, bogged, withers | Skeletons, liches, fossils |
| `METAL` | Flakes that tumble, glint in the light and clink when they land. | No | Iron golem, copper golem | Robots, golems, armour |
| `WOOD` | Splinters, plus drops of resin in your colour that pool like blood. | The resin does | Creaking | Treants, wooden constructs |
| `EMBER` | Embers that leave white-hot, flicker, cool to ash and hiss out in water. | No | Blaze, magma cube | Fire and lava creatures |
| `SPIRIT` | Glowing sparks that twinkle and float away. | No | Allay, vex | Ghosts, fairies, magic beings |
| `WIND` | Little gusts that curl open and blow upward. | No | Breeze | Air elementals |
| `POWDER` | Snow. Flat piles that never run and melt away. Vanishes in water. | Yes, as piles | Snow golem | Snow and ice creatures |
| `DEBRIS` | Plain dry bits that fly and come to rest. Nothing pools. | No | None | Anything solid that fits none of the above |

If you don't set a kind, your mob gets `LIQUID`.

## Things the kind changes for you

You don't need to set these yourself. The kind picks sensible defaults:

- **Underwater clouds** only come from `LIQUID`.
- **Puddles, runs and drips off ledges** only come from `LIQUID`. `WOOD` resin pools too.
- **Vanishing in water**: `POWDER` melts and `EMBER` hisses out.
- **Sounds** follow what was hit: a wet squelch for blood, cracking for bone, a clink for metal.

You can still override any of them in [Blood settings](2-blood-settings.md).

## Glowing blood

Any kind can glow in the dark with `setGlows(true)`. Drops, underwater clouds and fresh
puddles light up, and puddles lose the glow as they dry. The vanilla glow squid uses this.

```java
new SimpleBloodAPI.BloodSettings()
        .setColor(0x3FA9F5)
        .setGlows(true);
```

## Next

- Trigger effects yourself: [4. Effects on demand](4-effects.md)
- Copy a ready-made setup: [5. Examples](5-examples.md)
