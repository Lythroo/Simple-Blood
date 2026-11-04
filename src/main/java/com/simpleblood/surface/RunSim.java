package com.simpleblood.surface;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

final class RunSim<R extends RunSim.Run> {
    static final int POUR_GATE = 32;
    static final int BODY_TILES = 6;

    static class Run {
        final CanvasTile tile;
        final int column;
        final int colour;
        int row;
        int remaining;
        int volume;
        int interval;
        int nextTick;
        CanvasTile source;
        int sourceX, sourceY;

        Run(CanvasTile tile, int column, int row, int remaining, int volume, int colour,
            CanvasTile source, int sourceX, int sourceY) {
            this.tile = tile;
            this.column = column;
            this.row = row;
            this.remaining = remaining;
            this.volume = volume;
            this.colour = colour;
            feedFrom(source, sourceX, sourceY);
        }

        void feedFrom(CanvasTile source, int x, int y) {
            this.source = source;
            this.sourceX = x;
            this.sourceY = y;
        }
    }

    final List<R> runs;
    private final CanvasTile.FlowContext flow;
    private final Predicate<CanvasTile> live;
    private final RandomSource rng;
    private final IntSupplier clock;

    RunSim(List<R> runs, CanvasTile.FlowContext flow, Predicate<CanvasTile> live, RandomSource rng, IntSupplier clock) {
        this.runs = runs;
        this.flow = flow;
        this.live = live;
        this.rng = rng;
        this.clock = clock;
    }

    int pour(CanvasTile source, int px, int py) {
        CanvasTile.Body body = CanvasTile.body(source, px, py, flow, BODY_TILES);
        int mass = Math.max(1, body.mass());
        int pouring = 0;
        for (Run run : runs) if (run.source != null && body.contains(run.source)) pouring++;
        if (pouring > 0 && mass < Math.max(1, POUR_GATE * source.pw * source.ph / 256) * (pouring + 1)) return 0;
        return Math.max(2, Math.min(3 * source.res, mass / 2));
    }

    void add(R run) {
        run.interval = Math.max(2, (6 + rng.nextInt(4)) * 8 / run.tile.res);
        run.nextTick = clock.getAsInt() + run.interval;
        runs.add(run);
    }

    R inColumn(CanvasTile tile, int column) {
        for (R run : runs) if (run.tile == tile && run.column == column) return run;
        return null;
    }

    boolean due(Run run) {
        return clock.getAsInt() >= run.nextTick;
    }

    void schedule(Run run) {
        run.nextTick = clock.getAsInt() + run.interval;
    }

    boolean drink(Run run, int crept) {
        if (run.source == null) return false;
        if (!live.test(run.source)
                || !CanvasTile.body(run.source, run.sourceX, run.sourceY, flow, BODY_TILES)
                        .draw(run.source.pixelCenter(run.sourceX, run.sourceY), rng)) {
            run.source = null;
            run.remaining = Math.min(run.remaining, tail(run.tile.res));
            return false;
        }
        if ((crept & 1) == 1) run.volume++;
        return true;
    }

    static int tail(int res) {
        return Math.max(1, res / 4);
    }

    static int thin(int crept, int res) {
        return 1 + Math.min(2, crept / Math.max(1, res / 2));
    }
}
