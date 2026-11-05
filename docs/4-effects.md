# 4. Effects on demand

[Docs home](README.md) · [Getting started](1-getting-started.md) · [Settings](2-blood-settings.md) · [Kinds](3-blood-kinds.md) · **Effects** · [Examples](5-examples.md) · [FAQ](6-faq.md)

Simple Blood plays its effects on its own whenever a mob gets hurt. Sometimes you want one
without real damage: a cutscene, a boss phase, a trap, a custom weapon. These calls do that.

All of them are **client side**. You can call them from any thread; Simple Blood moves the
work to the render thread for you.

## A hit

```java
SimpleBloodAPI.spawnHit(entity, 6.0f);
```

Plays a hit burst as if `entity` had just taken 6 damage: same colour, kind, size and puddles
as a real hit. The mob is not hurt. More damage means a bigger burst.

Returns `false` and does nothing if the mob can't bleed, is dead, `damage` is 0 or less, or
blood is switched off in the player's settings.

## A death

```java
SimpleBloodAPI.spawnDeath(entity);
```

Plays the death burst. Returns `false` if the mob can't bleed or blood is switched off.

## Blood on a block

```java
// Paint where the player is looking, up to 4 blocks away
Vec3 eye = player.getEyePosition();
Vec3 end = eye.add(player.getLookAngle().scale(4));
SimpleBloodAPI.paint(level, eye, end, 0x8B0000, 4);
```

Draws a line from `from` to `to` and paints blood on the first block face it hits, as if a drop
had landed there. The puddle then spreads, runs down the sides and dries like any other.

- `rgb`: the colour, `0xRRGGBB`.
- `pixels`: how much blood. A block face is 16 x 16 pixels. A drop leaves about 1 to 3, a
  splash 3 to 6.

Returns `false` if nothing was hit or nothing was painted (for example, the player turned
puddles off). Painted blood never glows.

## Sparks (1.2.0)

```java
SimpleBloodAPI.spawnSparks(level, at, towardsPlayer);
```

Sparks struck off metal at `at`, flying towards `towardsPlayer` (a direction; `null` sends
them up), with the metal clank. Uses the player's Armor settings.

## Blood on a body (1.2.0)

```java
SimpleBloodAPI.stainBody(entity, at, direction, 0x8B0000);
```

Blood landing on `entity` at `at`, travelling along `direction`: it stays on the body, runs
down and dries like blood thrown onto it. Needs the player's "Blood on mobs" on.

## A pool (1.2.0)

```java
SimpleBloodAPI.spawnPool(entity);
```

A pool of the mob's blood spreads on the ground under it, as under a body that has just
died. Needs "Pool under the body" on.

## An explosion (1.3.0)

```java
SimpleBloodAPI.spawnExplosion(level, centre, 4.0f);
```

Tells Simple Blood an explosion went off at `centre`, with power `radius` (TNT is 4, a creeper
3). Mobs it hurts in the next few ticks get the explosion look: their blood bursts from the
side that faced the blast and is blown away from it. A lit creeper at `centre` that is gone a
moment later (it blew up) bursts in its own blood; one that is still there does not.

You only need this for a blast of your own that the client never hears about. Explosions made
the vanilla way (`level.explode(...)` on the server) are seen automatically. Call it on the
client, before or in the same tick as the damage. Does nothing on a server level or when
blood is switched off.

## Cleaning up

```java
SimpleBloodAPI.surfaceBloodCount();   // how many block faces have blood on them right now
SimpleBloodAPI.clearSurfaceBlood();   // wipe all of it, like /simpleblood clear
```

## Next

- See it all together: [5. Examples](5-examples.md)
