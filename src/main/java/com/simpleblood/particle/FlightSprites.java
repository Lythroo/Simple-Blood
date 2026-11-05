package com.simpleblood.particle;

public final class FlightSprites {

    private static final double LENGTH_MARGIN = 0.07;
    private static final double HEADING_MARGIN = 0.35;
    private static final double RESTING = 3.0e-4;

    private final int headings;
    private final double[] sideOnFrom;
    private final double[] screenFrom;

    public FlightSprites(int headings, double[] sideOnFrom, double[] screenFrom) {
        if (headings < 1 || sideOnFrom.length == 0 || sideOnFrom.length != screenFrom.length) {
            throw new IllegalArgumentException("headings >= 1 and one side-on and one speed threshold per length");
        }
        this.headings = headings;
        this.sideOnFrom = sideOnFrom.clone();
        this.screenFrom = screenFrom.clone();
    }

    public int sprites() {
        return 1 + headings * sideOnFrom.length;
    }

    public int lengths() {
        return sideOnFrom.length;
    }

    public int pick(double sx, double sy, double speed, int[] shown) {
        if (speed < RESTING) return index(shown);
        double screen = Math.sqrt(sx * sx + sy * sy);
        double sideOn = Math.min(1, screen / speed);
        if (shown[1] < 0) {
            shown[1] = lengthFor(sideOn, screen, 0);
        } else {
            int longer = lengthFor(sideOn, screen, LENGTH_MARGIN), shorter = lengthFor(sideOn, screen, -LENGTH_MARGIN);
            if (longer > shown[1]) shown[1] = longer;
            else if (shorter < shown[1]) shown[1] = shorter;
        }
        if (screen > 1.0e-5) {
            double step = Math.PI * 2 / headings;
            double angle = Math.atan2(sy, sx);
            if (shown[0] < 0) {
                shown[0] = nearestHeading(angle, step);
            } else {
                double off = Math.abs(Math.IEEEremainder(angle - shown[0] * step, Math.PI * 2));
                if (off > step * 0.5 * (1 + HEADING_MARGIN)) shown[0] = nearestHeading(angle, step);
            }
        }
        return index(shown);
    }

    private int index(int[] shown) {
        return shown[1] <= 0 || shown[0] < 0 ? 0 : 1 + (shown[1] - 1) * headings + shown[0];
    }

    private int lengthFor(double sideOn, double screen, double bias) {
        for (int l = sideOnFrom.length; l >= 1; l--) {
            if (sideOn > sideOnFrom[l - 1] + bias && screen > screenFrom[l - 1] * (1 + 2 * bias)) return l;
        }
        return 0;
    }

    private int nearestHeading(double angle, double step) {
        return Math.floorMod((int) Math.round(angle / step), headings);
    }
}
