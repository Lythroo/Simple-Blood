package com.simpleblood.surface;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Tiny cross-version shim around {@link NativeImage} pixel access. All colours here are
 * packed ABGR ({@code 0xAABBGGRR}), which is the in-memory layout of an RGBA image.
 */
public final class Pixels {
    private Pixels() {}

    public static void setAbgr(NativeImage image, int x, int y, int abgr) {
        //? if <=1.21.1 {
        /*image.setPixelRGBA(x, y, abgr);
        *///?} else {
        image.setPixelABGR(x, y, abgr);
        //?}
    }

    /** Red, green and blue of a pixel summed (0..765); enough for luminance comparisons. */
    static int luminanceSum(NativeImage image, int x, int y) {
        //? if <=1.21.1 {
        /*int abgr = image.getPixelRGBA(x, y);
        return (abgr & 0xFF) + ((abgr >> 8) & 0xFF) + ((abgr >> 16) & 0xFF);
        *///?} else {
        int argb = image.getPixel(x, y);
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
        //?}
    }

    static int alpha(NativeImage image, int x, int y) {
        //? if <=1.21.1 {
        /*return (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
        *///?} else {
        return (image.getPixel(x, y) >>> 24) & 0xFF;
        //?}
    }

    public static int packAbgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
