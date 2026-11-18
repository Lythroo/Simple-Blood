package com.simpleblood.surface.store;

public record SavedTile(Where where, Pixels pixels) {

    public record Where(long pos, int face, int box, int hitFace, String block,
                        double cx, double cy, double cz,
                        float ox, float oy, float oz, float w, float h,
                        int pw, int ph) {}

    public record Pixels(short[] index, byte[] tone, byte[] depth, byte[] thin, byte[] flags,
                         int[] rgb, int[] elapsed, int[] life) {

        public static final byte POWDER = 1;
        public static final int FOREVER_BIT = 1 << 30;

        public Pixels {
            int n = index.length;
            if (tone.length != n || depth.length != n || thin.length != n || flags.length != n
                    || rgb.length != n || elapsed.length != n || life.length != n) {
                throw new IllegalArgumentException("pixel arrays differ in length");
            }
        }

        public int count() {
            return index.length;
        }
    }
}
