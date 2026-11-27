package com.simpleblood.geom;

public record VoxelGrid(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int nx, int ny, int nz) {

    public VoxelGrid {
        if (nx < 1 || ny < 1 || nz < 1) throw new IllegalArgumentException("a grid needs a cell along each axis: " + nx + "x" + ny + "x" + nz);
    }

    public int cells() {
        return nx * ny * nz;
    }

    public int cellAt(double x, double y, double z) {
        int gx = along(x, minX, maxX, nx), gy = along(y, minY, maxY, ny), gz = along(z, minZ, maxZ, nz);
        return (gy * nz + gz) * nx + gx;
    }

    private static int along(double v, float min, float max, int n) {
        float size = max - min;
        if (n == 1 || size <= 0f) return 0;
        int g = (int) Math.floor((v - min) / size * n);
        return g < 0 ? 0 : Math.min(n - 1, g);
    }
}
