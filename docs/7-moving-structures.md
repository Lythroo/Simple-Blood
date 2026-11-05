# 7. Moving structures (1.3.0)

[Docs home](README.md) · [Getting started](1-getting-started.md) · [Settings](2-blood-settings.md) · [Kinds](3-blood-kinds.md) · [Effects](4-effects.md) · [Examples](5-examples.md) · [FAQ](6-faq.md) · **Moving structures**

Blood on Create contraptions and on Sable (Aeronautics) ships rides along with them. If your
mod moves blocks around as one body too (ships, airships, moving platforms), you can tell
Simple Blood where your structures are, and blood on them works the same way:

- blood on them is drawn where the structure is, every frame;
- it runs and drips the way gravity falls across the structure as it tilts, and falls off
  when it hangs upside down;
- drops that fly into a structure land on it, even while it moves;
- blood that lets go of a moving structure keeps its motion.

## What your structures need to be like

This is for structures whose blocks really are in the client's level, somewhere out of the
way, and are drawn somewhere else through a pose. Valkyrien Skies keeps its ships in a
"shipyard" like that, Sable in "sub-levels". Simple Blood calls that place the **plot**.

Blood is painted on the plot blocks, and drawn through the pose you give for them.

## A pose

A pose maps between the plot and the world:

| Method | What it does |
|---|---|
| `Vec3 toWorld(Vec3 plot)` | Where a plot point is in the world now. |
| `Vec3 toPlot(Vec3 world)` | The other way round. |
| `Vec3 turn(Vec3 direction)` | A direction turned the way the structure is turned (same length). |
| `Vec3 lastToPlot(Vec3 world)` | As `toPlot`, as the structure stood a tick ago. Optional (default: as now). |
| `Vec3 lastToWorld(Vec3 plot)` | As `toWorld`, a tick ago. Optional (default: as now). |

The two "last" methods let a drop meet a structure that moved during the same tick, and let
blood thrown off a structure keep its motion. Leave them out and the structure counts as
standing still between ticks.

## Telling Simple Blood

Register a source once, in your client setup:

```java
SimpleBloodAPI.registerMovingStructures("yourmod", new SimpleBloodAPI.MovingStructures() {
    // The structures a drop moving from `from` to `to` this tick may meet.
    @Override
    public List<? extends SimpleBloodAPI.MovingStructures.Pose> crossing(ClientLevel level, Vec3 from, Vec3 to) {
        List<SimpleBloodAPI.MovingStructures.Pose> out = new ArrayList<>();
        for (MyShip ship : MyShips.near(level, from, to, 2.0)) out.add(new ShipPose(ship, 1f));
        return out;
    }

    // The pose to draw the blood on plot block `plot` with this frame, or null.
    @Override
    public SimpleBloodAPI.MovingStructures.Pose drawn(ClientLevel level, BlockPos plot, float partialTick) {
        MyShip ship = MyShips.holding(level, plot);
        return ship == null ? null : new ShipPose(ship, partialTick);
    }

    // The pose this tick of the structure holding plot block `plot`, or null.
    @Override
    public SimpleBloodAPI.MovingStructures.Pose logical(ClientLevel level, BlockPos plot) {
        MyShip ship = MyShips.holding(level, plot);
        return ship == null ? null : new ShipPose(ship, 1f);
    }
});
```

`ShipPose` is your own class implementing `SimpleBloodAPI.MovingStructures.Pose`, usually a
few lines around the matrix or quaternion your structure already has.

`SimpleBloodAPI.unregisterMovingStructures("yourmod")` takes it out again.

## Keep it quick

Simple Blood asks often: `crossing` for every flying drop, `drawn` for every block face with
blood on it, every frame. Sometimes it asks off the render thread (with AsyncParticles).

- Return `null` or an empty list as soon as you can tell a position or a move is nothing to do
  with your structures (a range check on your plot area first is ideal).
- In `crossing`, include structures whose bounds come within a block or two of the move: they
  may have moved during the tick.
- Don't change game state from these methods.

If your source or one of its poses throws, Simple Blood switches it off, writes one line in
the log, and carries on without it.

## Next

- Questions: [6. FAQ](6-faq.md)
