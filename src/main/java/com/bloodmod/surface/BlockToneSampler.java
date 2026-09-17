package com.bloodmod.surface;

import com.bloodmod.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BlockToneSampler {

    private static final Map<TextureAtlasSprite, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final byte[] UNREADABLE = new byte[0];
    private static volatile int cachedRes = -1;
    private static final int MAX_CACHE = 512;

    private BlockToneSampler() {}

    static void clear() {
        CACHE.clear();
        CROSS_CACHE.clear();
        ALL_QUADS.clear();
    }

    static byte[] sample(CanvasTile tile, RandomSource rng) {
        if (cachedRes != tile.res) {
            CACHE.clear();
            cachedRes = tile.res;
        }
        TextureAtlasSprite sprite;
        try {
            sprite = faceSprite(tile.state, tile.face, rng);
        } catch (Throwable t) {
            return null;
        }
        if (sprite == null) return null;

        byte[] full = CACHE.get(sprite);
        if (full == null) {
            if (CACHE.size() >= MAX_CACHE) CACHE.clear();
            try {
                full = toneGrid(sprite, tile.res);
            } catch (Throwable t) {
                full = null;
            }
            if (full == null) full = UNREADABLE;
            CACHE.put(sprite, full);
        }
        if (full.length == 0) return null;

        int res = tile.res;
        byte[] out = new byte[tile.pw * tile.ph];
        int offU = (int) Math.round(faceOffsetU(tile) * res);
        int offV = (int) Math.round(faceOffsetV(tile) * res);
        for (int y = 0; y < tile.ph; y++) {
            for (int x = 0; x < tile.pw; x++) {
                int gx = Math.floorMod(offU + x, res);
                int gy = Math.floorMod(offV + y, res);
                out[y * tile.pw + x] = full[gy * res + gx];
            }
        }
        return out;
    }

    private static double faceOffsetU(CanvasTile t) {
        double bx = t.pos.getX(), by = t.pos.getY(), bz = t.pos.getZ();
        double cx = t.ux < 0 ? bx + 1 : bx, cy = by, cz = t.uz < 0 ? bz + 1 : bz;
        return (t.ox - cx) * t.ux + (t.oy - cy) * t.uy + (t.oz - cz) * t.uz;
    }

    private static double faceOffsetV(CanvasTile t) {
        double bx = t.pos.getX(), by = t.pos.getY(), bz = t.pos.getZ();
        double cx = bx, cy = t.vy < 0 ? by + 1 : by, cz = t.vz < 0 ? bz + 1 : bz;
        return (t.ox - cx) * t.vx + (t.oy - cy) * t.vy + (t.oz - cz) * t.vz;
    }

    private static byte[] toneGrid(TextureAtlasSprite sprite, int res) {
        SpriteContents contents = sprite.contents();
        NativeImage img = ((SpriteContentsAccessor) contents).bloodmod$originalImage();
        if (img == null) return null;
        int fw = contents.width(), fh = contents.height();
        if (fw <= 0 || fh <= 0 || img.getWidth() < fw || img.getHeight() < fh) return null;

        float[] lum = new float[res * res];
        float mean = 0;
        int opaqueCells = 0;
        for (int gy = 0; gy < res; gy++) {
            for (int gx = 0; gx < res; gx++) {
                int x0 = gx * fw / res, x1 = Math.max(x0 + 1, (gx + 1) * fw / res);
                int y0 = gy * fh / res, y1 = Math.max(y0 + 1, (gy + 1) * fh / res);
                long sum = 0;
                int n = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        if (Pixels.alpha(img, x, y) < 16) continue;
                        sum += Pixels.luminanceSum(img, x, y);
                        n++;
                    }
                }
                float v = n == 0 ? -1 : sum / (float) n;
                lum[gy * res + gx] = v;
                if (v >= 0) {
                    mean += v;
                    opaqueCells++;
                }
            }
        }
        if (opaqueCells == 0) return null;
        mean /= opaqueCells;
        byte[] tones = new byte[res * res];
        for (int i = 0; i < tones.length; i++) {
            float v = lum[i];
            if (v < 0 || mean <= 0) {
                tones[i] = CanvasTile.BASE;
            } else {
                float rel = v / mean;
                tones[i] = rel < 0.80f ? CanvasTile.DARK : CanvasTile.BASE;
            }
        }
        byte[] out = tones.clone();
        for (int gy = 0; gy < res; gy++) {
            for (int gx = 0; gx < res; gx++) {
                int i = gy * res + gx;
                if (tones[i] != CanvasTile.DARK) continue;
                boolean joined = (gx > 0 && tones[i - 1] == CanvasTile.DARK) || (gx < res - 1 && tones[i + 1] == CanvasTile.DARK)
                        || (gy > 0 && tones[i - res] == CanvasTile.DARK) || (gy < res - 1 && tones[i + res] == CanvasTile.DARK);
                if (!joined) out[i] = CanvasTile.BASE;
            }
        }
        return out;
    }

    record QuadInfo(Vec3[] corners, float[] u, float[] v, TextureAtlasSprite sprite) {}

    static int[] spriteSize(TextureAtlasSprite sprite) {
        SpriteContents c = sprite.contents();
        return new int[]{Math.max(1, c.width()), Math.max(1, c.height())};
    }

    static boolean[] alphaMask(TextureAtlasSprite sprite, float u0, float u1, float v0, float v1, int pw, int ph) {
        if (sprite == null) return null;
        try {
            return readAlphaMask(sprite, u0, u1, v0, v1, pw, ph);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean[] readAlphaMask(TextureAtlasSprite sprite, float u0, float u1, float v0, float v1, int pw, int ph) {
        SpriteContents contents = sprite.contents();
        NativeImage img = ((SpriteContentsAccessor) contents).bloodmod$originalImage();
        if (img == null) return null;
        int fw = contents.width(), fh = contents.height();
        if (fw <= 0 || fh <= 0 || img.getWidth() < fw || img.getHeight() < fh) return null;
        boolean[] mask = new boolean[pw * ph];
        for (int gy = 0; gy < ph; gy++) {
            for (int gx = 0; gx < pw; gx++) {
                int x0 = (int) Math.floor((u0 + (u1 - u0) * gx / pw) * fw);
                int x1 = (int) Math.ceil((u0 + (u1 - u0) * (gx + 1) / pw) * fw);
                int y0 = (int) Math.floor((v0 + (v1 - v0) * gy / ph) * fh);
                int y1 = (int) Math.ceil((v0 + (v1 - v0) * (gy + 1) / ph) * fh);
                x0 = Math.max(0, Math.min(fw - 1, x0)); x1 = Math.max(x0 + 1, Math.min(fw, x1));
                y0 = Math.max(0, Math.min(fh - 1, y0)); y1 = Math.max(y0 + 1, Math.min(fh, y1));
                boolean opaque = false;
                for (int y = y0; y < y1 && !opaque; y++) {
                    for (int x = x0; x < x1; x++) {
                        if (Pixels.alpha(img, x, y) >= 16) { opaque = true; break; }
                    }
                }
                mask[gy * pw + gx] = opaque;
            }
        }
        return mask;
    }

    static List<QuadInfo> unculledQuads(BlockState state) {
        return quads(state, false);
    }

    private static final Map<BlockState, List<QuadInfo>> ALL_QUADS = new ConcurrentHashMap<>();

    static List<QuadInfo> allQuads(BlockState state) {
        List<QuadInfo> cached = ALL_QUADS.get(state);
        if (cached == null) {
            if (ALL_QUADS.size() >= MAX_CACHE) ALL_QUADS.clear();
            cached = quads(state, true);
            ALL_QUADS.put(state, cached);
        }
        return cached;
    }

    private static List<QuadInfo> quads(BlockState state, boolean allFaces) {
        List<QuadInfo> out = new java.util.ArrayList<>();
        RandomSource rng = RandomSource.create(42);
        Direction[] faces = allFaces
                ? new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}
                : new Direction[]{null};
        try {
            //? if 1.21.1 {
            /*net.minecraft.client.resources.model.BakedModel model =
                    Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            for (Direction face : faces) {
                for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(state, face, rng)) {
                    int[] vtx = q.getVertices();
                    int stride = vtx.length / 4;
                    Vec3[] corners = new Vec3[4];
                    float[] u = new float[4], v = new float[4];
                    for (int i = 0; i < 4; i++) {
                        int b = i * stride;
                        corners[i] = new Vec3(Float.intBitsToFloat(vtx[b]), Float.intBitsToFloat(vtx[b + 1]), Float.intBitsToFloat(vtx[b + 2]));
                        u[i] = Float.intBitsToFloat(vtx[b + 4]);
                        v[i] = Float.intBitsToFloat(vtx[b + 5]);
                    }
                    out.add(normalised(corners, u, v, q.getSprite()));
                }
            }
            *///?} elif <26.1 {
            /*net.minecraft.client.renderer.block.model.BlockStateModel model =
                    Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new java.util.ArrayList<>();
            model.collectParts(rng, parts);
            for (net.minecraft.client.renderer.block.model.BlockModelPart part : parts) {
                for (Direction face : faces) {
                    for (net.minecraft.client.renderer.block.model.BakedQuad q : part.getQuads(face)) {
                        Vec3[] corners = new Vec3[4];
                        float[] u = new float[4], v = new float[4];
                        for (int i = 0; i < 4; i++) {
                            org.joml.Vector3fc p = q.position(i);
                            corners[i] = new Vec3(p.x(), p.y(), p.z());
                            u[i] = net.minecraft.client.model.geom.builders.UVPair.unpackU(q.packedUV(i));
                            v[i] = net.minecraft.client.model.geom.builders.UVPair.unpackV(q.packedUV(i));
                        }
                        out.add(normalised(corners, u, v, q.sprite()));
                    }
                }
            }
            *///?} else {
            net.minecraft.client.renderer.block.dispatch.BlockStateModel model =
                    Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
            model.collectParts(rng, parts);
            for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
                for (Direction face : faces) {
                    for (net.minecraft.client.resources.model.geometry.BakedQuad q : part.getQuads(face)) {
                        Vec3[] corners = new Vec3[4];
                        float[] u = new float[4], v = new float[4];
                        for (int i = 0; i < 4; i++) {
                            org.joml.Vector3fc p = q.position(i);
                            corners[i] = new Vec3(p.x(), p.y(), p.z());
                            u[i] = net.minecraft.client.model.geom.builders.UVPair.unpackU(q.packedUV(i));
                            v[i] = net.minecraft.client.model.geom.builders.UVPair.unpackV(q.packedUV(i));
                        }
                        out.add(normalised(corners, u, v, q.materialInfo().sprite()));
                    }
                }
            }
            //?}
        } catch (Throwable t) {
            out.clear();
        }
        return out;
    }

    static Direction quadFacing(QuadInfo q) {
        Vec3[] c = q.corners();
        Vec3 n = c[1].subtract(c[0]).cross(c[2].subtract(c[0]));
        if (n.lengthSqr() < 1.0e-10) return null;
        n = n.normalize();
        double ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);
        if (ax > 0.99) return n.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay > 0.99) return n.y > 0 ? Direction.UP : Direction.DOWN;
        if (az > 0.99) return n.z > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    static net.minecraft.world.phys.AABB quadBox(QuadInfo q) {
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        for (Vec3 c : q.corners()) {
            minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
            minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
            minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
        }
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static QuadInfo normalised(Vec3[] corners, float[] u, float[] v, TextureAtlasSprite sprite) {
        float su0 = sprite.getU0(), su1 = sprite.getU1(), sv0 = sprite.getV0(), sv1 = sprite.getV1();
        float du = Math.max(1.0e-6f, su1 - su0), dv = Math.max(1.0e-6f, sv1 - sv0);
        float[] nu = new float[4], nv = new float[4];
        for (int i = 0; i < 4; i++) {
            nu[i] = Math.max(0f, Math.min(1f, (u[i] - su0) / du));
            nv[i] = Math.max(0f, Math.min(1f, (v[i] - sv0) / dv));
        }
        return new QuadInfo(corners, nu, nv, sprite);
    }

    private static final Map<BlockState, Boolean> CROSS_CACHE = new ConcurrentHashMap<>();

    static boolean isCrossModel(BlockState state) {
        Boolean cached = CROSS_CACHE.get(state);
        if (cached != null) return cached;
        boolean cross;
        try {
            cross = detectCross(state);
        } catch (Throwable t) {
            cross = false;
        }
        if (CROSS_CACHE.size() > 2048) CROSS_CACHE.clear();
        CROSS_CACHE.put(state, cross);
        return cross;
    }

    private static boolean detectCross(BlockState state) {
        RandomSource rng = RandomSource.create(42);
        //? if 1.21.1 {
        /*net.minecraft.client.resources.model.BakedModel model =
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        if (model.getQuads(state, null, rng).isEmpty()) return false;
        for (Direction d : Direction.values()) {
            if (!model.getQuads(state, d, rng).isEmpty()) return false;
        }
        return true;
        *///?} elif <26.1 {
        /*net.minecraft.client.renderer.block.model.BlockStateModel model =
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(rng, parts);
        boolean unculled = false;
        for (net.minecraft.client.renderer.block.model.BlockModelPart part : parts) {
            if (!part.getQuads(null).isEmpty()) unculled = true;
            for (Direction d : Direction.values()) {
                if (!part.getQuads(d).isEmpty()) return false;
            }
        }
        return unculled;
        *///?} else {
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model =
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(rng, parts);
        boolean unculled = false;
        for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
            if (!part.getQuads(null).isEmpty()) unculled = true;
            for (Direction d : Direction.values()) {
                if (!part.getQuads(d).isEmpty()) return false;
            }
        }
        return unculled;
        //?}
    }

    static TextureAtlasSprite faceSprite(BlockState state, Direction face, RandomSource rng) {
        //? if 1.21.1 {
        /*net.minecraft.client.resources.model.BakedModel model =
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(state, face, rng);
        if (!quads.isEmpty()) return quads.get(0).getSprite();
        return model.getParticleIcon();
        *///?} elif <26.1 {
        /*net.minecraft.client.renderer.block.model.BlockStateModel model =
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<net.minecraft.client.renderer.block.model.BlockModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(rng, parts);
        for (net.minecraft.client.renderer.block.model.BlockModelPart part : parts) {
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads = part.getQuads(face);
            if (!quads.isEmpty()) return quads.get(0).sprite();
        }
        return model.particleIcon();
        *///?} else {
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model =
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(rng, parts);
        for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
            List<net.minecraft.client.resources.model.geometry.BakedQuad> quads = part.getQuads(face);
            if (!quads.isEmpty()) return quads.get(0).materialInfo().sprite();
        }
        return model.particleMaterial().sprite();
        //?}
    }
}
