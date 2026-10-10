//? if 1.20.1 {
/*package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/^*
 * 1.20.1 particles can only face the camera fully. Later versions draw a quad at any rotation
 * ({@code renderRotatedQuad}, {@code FacingCameraMode}); this is that drawing for the particles
 * that need it (upright drops, flakes lying flat), in 1.20.1's own vertex order: its camera
 * rotation is half a turn off the one 1.21 uses, so the 1.21 order would be culled.
 ^/
final class LegacyQuads {
    private LegacyQuads() {}

    /^* The camera's rotation turned around the Y axis only, so the quad stays upright. ^/
    static Quaternionf uprightTowards(Camera camera) {
        Quaternionf r = camera.rotation();
        return new Quaternionf(0f, r.y(), 0f, r.w());
    }

    /^*
     * One quad of half-size {@code size} centred on {@code (x, y, z)} in world space.
     *
     * @param rotation   the camera rotation (or part of it) for a quad facing the viewer, wound
     *                   the way 1.20.1 winds its own particles so it is not culled; any fixed
     *                   rotation (a flake lying flat) is drawn from both sides instead, since
     *                   which side faces the camera then changes as you walk around it
     * @param fixed      whether {@code rotation} is fixed in the world rather than taken from the camera
     ^/
    static void draw(VertexConsumer buffer, Camera camera, Quaternionf rotation, boolean fixed,
                     double x, double y, double z, float size,
                     float u0, float u1, float v0, float v1,
                     float r, float g, float b, float a, int light) {
        Vec3 cam = camera.getPosition();
        float cx = (float) (x - cam.x), cy = (float) (y - cam.y), cz = (float) (z - cam.z);
        vertex(buffer, rotation, cx, cy, cz, -1f, -1f, size, u1, v1, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, -1f, 1f, size, u1, v0, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, 1f, 1f, size, u0, v0, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, 1f, -1f, size, u0, v1, r, g, b, a, light);
        if (!fixed) return;
        vertex(buffer, rotation, cx, cy, cz, 1f, -1f, size, u0, v1, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, 1f, 1f, size, u0, v0, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, -1f, 1f, size, u1, v0, r, g, b, a, light);
        vertex(buffer, rotation, cx, cy, cz, -1f, -1f, size, u1, v1, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer buffer, Quaternionf rotation, float x, float y, float z,
                               float dx, float dy, float size, float u, float v,
                               float r, float g, float b, float a, int light) {
        Vector3f p = new Vector3f(dx, dy, 0f).rotate(rotation).mul(size).add(x, y, z);
        buffer.vertex(p.x(), p.y(), p.z()).uv(u, v).color(r, g, b, a).uv2(light).endVertex();
    }
}
*///?}
