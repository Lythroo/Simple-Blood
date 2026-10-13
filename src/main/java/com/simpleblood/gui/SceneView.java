package com.simpleblood.gui;

import com.simpleblood.surface.PreviewScene;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntSupplier;

/**
 * A live 3D diorama of one kind, drawn wherever the screen puts it (the hover preview). The
 * view swings slowly so puddles, runs and sprays are seen from more than one angle.
 */
public final class SceneView {

    public final PreviewScene.Kind kind;
    private final PreviewScene scene;
    private final float baseYaw, pitch;
    private final Vec3 focus;
    private final float blocksAcross, blocksTall;
    private float t;
    private IntSupplier colour;

    public SceneView(PreviewScene.Kind kind, IntSupplier colour) {
        this.kind = kind;
        this.colour = colour;
        this.scene = new PreviewScene(kind, () -> this.colour.getAsInt());
        switch (kind) {
            case PUDDLE -> { baseYaw = 0.55f; pitch = 0.62f; focus = new Vec3(0.5, 1.15, 0.35); blocksAcross = 4.0f; blocksTall = 2.6f; }
            case FOOTPRINTS -> { baseYaw = 0.30f; pitch = 0.85f; focus = new Vec3(0.5, 1.1, 0.5); blocksAcross = 4.6f; blocksTall = 5.6f; }
            case WATER -> { baseYaw = 0.45f; pitch = 0.32f; focus = new Vec3(0.5, 1.55, 0.5); blocksAcross = 4.2f; blocksTall = 3.1f; }
            default -> { baseYaw = 0.45f; pitch = 0.38f; focus = new Vec3(0.5, 1.45, 0.5); blocksAcross = 4.2f; blocksTall = 3.4f; }
        }
    }

    public PreviewScene scene() { return scene; }

    /** Height over width the diorama box wants: the footprint strip is tall, the rest 4:3. */
    public float aspect() { return kind == PreviewScene.Kind.FOOTPRINTS ? 1.15f : 0.75f; }

    /** Which blood colour the scene uses (a mob's, the player's...). */
    public void colour(IntSupplier colour) { this.colour = colour; }

    /** Settings to play the scene with instead of the live ones (null: the live ones). */
    public void config(com.simpleblood.SimpleBloodConfig config) { scene.config(config); }

    /** Which parts of a hit to play (HIT scenes). */
    public void show(java.util.EnumSet<PreviewScene.Aspect> aspects) { scene.show(aspects); }

    /** Plays the effect now (a setting was just hovered). */
    public void trigger() { scene.trigger(); }

    public void dispose() { scene.dispose(); }

    /** Draws the diorama into the box. */
    public void draw(Gfx g, int x, int y, int w, int h, float dt) {
        t += dt;
        scene.touch();
        g.fill(x, y, x + w, y + h, 0xFF000000);
        if (kind == PreviewScene.Kind.WATER) g.gradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF14449A, 0xFF071B48); // deep water
        else g.gradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF232B38, 0xFF10141B);
        float yaw = baseYaw + (float) Math.sin(t * 0.25f) * 0.20f;
        float scale = Math.min((w - 2) / blocksAcross, (h - 2) / blocksTall);
        g.scene(scene, x + 1, y + 1, x + w - 1, y + h - 1, scale, yaw, pitch, focus);
        g.bevel(x, y, x + w, y + h, 0x50FFFFFF, 0xA0000000);
    }
}
