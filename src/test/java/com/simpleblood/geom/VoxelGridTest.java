package com.simpleblood.geom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoxelGridTest {

    private static final VoxelGrid HEAD = new VoxelGrid(-4, -8, -4, 4, 0, 4, 8, 8, 8);

    @Test
    void aPointInsideFindsItsCellXFastestThenZThenY() {
        assertEquals((2 * 8 + 3) * 8 + 1, HEAD.cellAt(-2.5, -5.5, -0.5));
    }

    @Test
    void aTexelOnTheSurfaceBelongsToTheOutermostCell() {
        assertEquals((7 * 8 + 4) * 8 + 4, HEAD.cellAt(0.5, 0, 0.5));
        assertEquals((3 * 8 + 4) * 8, HEAD.cellAt(-4, -4.5, 0.5));
    }

    @Test
    void pointsJustOffTheCubeAreClampedIntoIt() {
        assertEquals(HEAD.cellAt(3.9, -0.1, 3.9), HEAD.cellAt(4.2, 0.3, 4.2));
        assertEquals(0, HEAD.cellAt(-5, -9, -5));
    }

    @Test
    void anInflatedCubeKeepsItsCellCount() {
        VoxelGrid hat = new VoxelGrid(-4.5f, -8.5f, -4.5f, 4.5f, 0.5f, 4.5f, 8, 8, 8);
        assertEquals((7 * 8 + 7) * 8 + 7, hat.cellAt(4.4, 0.4, 4.4));
        assertEquals(0, hat.cellAt(-4.4, -8.4, -4.4));
    }

    @Test
    void aFlatCubeHasOneCellAcrossIt() {
        VoxelGrid plane = new VoxelGrid(0, 0, 2, 4, 4, 2, 4, 4, 1);
        assertEquals((3 * 1) * 4 + 2, plane.cellAt(2.5, 3.5, 2));
        assertEquals(16, plane.cells());
    }

    @Test
    void aGridWithoutCellsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new VoxelGrid(0, 0, 0, 1, 1, 1, 0, 1, 1));
    }

    @Test
    void cutTexelsAreNotDrawnThroughTheWeightedTexelCheck() {
        QuadSurface.Texels halfCut = new QuadSurface.Texels() {
            @Override
            public boolean drawn(int face, double u, double v) {
                return true;
            }

            @Override
            public boolean drawnAt(int face, double a, double b, double u, double v) {
                return a >= 0.5;
            }
        };
        QuadSurface s = new QuadSurface(new double[]{0, 0.5, 0, 1, 0.5, 0, 0, 0.5, 1}, new double[]{0, 1, 0},
                new double[]{0, 0, 1, 0, 0, 1}, halfCut);
        double[] out = new double[7];
        assertEquals(-1, s.firstHit(0.25, 1, 0.5, 0.25, 0, 0.5, out), 1e-9);
        assertEquals(0.5, s.firstHit(0.75, 1, 0.5, 0.75, 0, 0.5, out), 1e-9);
        assertFalse(s.drawnAt(0, 0.2, 0.5));
    }
}
