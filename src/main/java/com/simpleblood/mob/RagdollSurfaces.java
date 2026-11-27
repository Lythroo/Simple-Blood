package com.simpleblood.mob;

import com.simpleblood.geom.QuadSurface;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RagdollSurfaces {
    private RagdollSurfaces() {}

    private static final long STALE_NANOS = 500_000_000L;
    private static final int[] CORNERS = {0, 1, 3};

    private record Piece(Object key, QuadSurface faces, long nanos, int tick) {}

    private static final Map<Integer, Piece[]> BY_MOB = new ConcurrentHashMap<>();

    static void publish(int mobId, List<ModelFacesOfMob.Face> faces, float[] fade, int tick) {
        if (faces.isEmpty()) return;
        Object key = faces.get(0).key;
        Piece[] old = BY_MOB.get(mobId);
        int at = -1;
        if (old != null) {
            for (int i = 0; i < old.length; i++) {
                if (old[i].key() != key) continue;
                if (old[i].tick() == tick) return;
                at = i;
            }
        }
        long nanos = System.nanoTime();
        Piece piece = new Piece(key, surfaceOf(faces, fade), nanos, tick);
        Piece[] next;
        if (old == null) {
            next = new Piece[]{piece};
        } else if (at >= 0) {
            next = old.clone();
            next[at] = piece;
        } else {
            int fresh = 0;
            for (Piece p : old) if (nanos - p.nanos() <= STALE_NANOS) fresh++;
            next = new Piece[fresh + 1];
            int k = 0;
            for (Piece p : old) if (nanos - p.nanos() <= STALE_NANOS) next[k++] = p;
            next[k] = piece;
        }
        BY_MOB.put(mobId, next);
    }

    static QuadSurface surfaceOf(List<ModelFacesOfMob.Face> faces, float[] fade) {
        int n = 0;
        for (int i = 0; i < faces.size(); i++) if (fade == null || fade[i] > 0f) n++;
        double[] corners = new double[n * 9], normals = new double[n * 3], uvs = new double[n * 6];
        MobTextures.Alpha[] alphas = new MobTextures.Alpha[n];
        boolean[][] gone = null;
        double[] canvas = null;
        int[] sizes = null;
        int k = 0;
        for (int i = 0; i < faces.size(); i++) {
            if (fade != null && fade[i] <= 0f) continue;
            ModelFacesOfMob.Face f = faces.get(i);
            for (int j = 0; j < 3; j++) {
                corners[k * 9 + j * 3] = f.wx(CORNERS[j]);
                corners[k * 9 + j * 3 + 1] = f.wy(CORNERS[j]);
                corners[k * 9 + j * 3 + 2] = f.wz(CORNERS[j]);
                uvs[k * 6 + j * 2] = f.u[CORNERS[j]];
                uvs[k * 6 + j * 2 + 1] = f.v[CORNERS[j]];
            }
            normals[k * 3] = f.wnx;
            normals[k * 3 + 1] = f.wny;
            normals[k * 3 + 2] = f.wnz;
            alphas[k] = f.alpha;
            if (f.gone != null) {
                if (gone == null) {
                    gone = new boolean[n][];
                    canvas = new double[n * 6];
                    sizes = new int[n * 2];
                }
                gone[k] = f.gone;
                for (int j = 0; j < 3; j++) {
                    canvas[k * 6 + j * 2] = f.s[CORNERS[j]];
                    canvas[k * 6 + j * 2 + 1] = f.t[CORNERS[j]];
                }
                sizes[k * 2] = f.pw;
                sizes[k * 2 + 1] = f.ph;
            }
            k++;
        }
        return new QuadSurface(corners, normals, uvs, new DrawnTexels(alphas, gone, canvas, sizes));
    }

    private record DrawnTexels(MobTextures.Alpha[] alphas, boolean[][] gone, double[] canvas, int[] sizes) implements QuadSurface.Texels {
        @Override
        public boolean drawn(int face, double u, double v) {
            return alphas[face] == null || alphas[face].at(u, v);
        }

        @Override
        public boolean drawnAt(int face, double a, double b, double u, double v) {
            boolean[] cut = gone == null ? null : gone[face];
            if (cut != null) {
                int c = face * 6, w = sizes[face * 2], h = sizes[face * 2 + 1];
                double s = canvas[c] + (canvas[c + 2] - canvas[c]) * a + (canvas[c + 4] - canvas[c]) * b;
                double t = canvas[c + 1] + (canvas[c + 3] - canvas[c + 1]) * a + (canvas[c + 5] - canvas[c + 1]) * b;
                int x = Math.max(0, Math.min(w - 1, (int) Math.floor(s * w))), y = Math.max(0, Math.min(h - 1, (int) Math.floor(t * h)));
                if (cut[y * w + x]) return false;
            }
            return drawn(face, u, v);
        }
    }

    public static boolean drawn(int mobId) {
        Piece[] pieces = BY_MOB.get(mobId);
        if (pieces == null) return false;
        long now = System.nanoTime();
        for (Piece p : pieces) if (now - p.nanos() <= STALE_NANOS) return true;
        return false;
    }

    public static double firstHit(int mobId, double ax, double ay, double az, double bx, double by, double bz, double[] out) {
        Piece[] pieces = BY_MOB.get(mobId);
        if (pieces == null) return -1;
        long now = System.nanoTime();
        double best = -1;
        double x = 0, y = 0, z = 0, nx = 0, ny = 0, nz = 0, face = 0;
        int piece = -1;
        for (int i = 0; i < pieces.length; i++) {
            Piece p = pieces[i];
            if (now - p.nanos() > STALE_NANOS) continue;
            double t = p.faces().firstHit(ax, ay, az, bx, by, bz, out);
            if (t < 0 || best >= 0 && t >= best) continue;
            best = t;
            piece = i;
            x = out[0];
            y = out[1];
            z = out[2];
            nx = out[3];
            ny = out[4];
            nz = out[5];
            face = out[6];
        }
        if (best >= 0) {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            out[3] = nx;
            out[4] = ny;
            out[5] = nz;
            out[6] = face;
            if (out.length > 7) out[7] = piece;
        }
        return best;
    }

    public static double distanceToDrawnForTests(int mobId, double x, double y, double z) {
        Piece[] pieces = BY_MOB.get(mobId);
        double best = Double.POSITIVE_INFINITY;
        if (pieces == null) return best;
        for (Piece p : pieces) best = Math.min(best, p.faces().distanceToDrawn(x, y, z, 16));
        return best;
    }

    public static String describeForTests(int mobId, double x, double y, double z) {
        Piece[] pieces = BY_MOB.get(mobId);
        if (pieces == null) return "no pieces";
        long now = System.nanoTime();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pieces.length; i++) {
            Piece p = pieces[i];
            b.append(String.format(java.util.Locale.ROOT, "[piece %d: tick %d, %d ms old, %d faces, %.3f to its drawn texels] ",
                    i, p.tick(), (now - p.nanos()) / 1_000_000, p.faces().faces(), p.faces().distanceToDrawn(x, y, z, 16)));
        }
        return b.toString();
    }

    static void prune() {
        if (BY_MOB.isEmpty()) return;
        long now = System.nanoTime();
        BY_MOB.entrySet().removeIf(e -> {
            for (Piece p : e.getValue()) if (now - p.nanos() <= 20 * STALE_NANOS) return false;
            return true;
        });
    }

    static void clear() {
        BY_MOB.clear();
    }
}
