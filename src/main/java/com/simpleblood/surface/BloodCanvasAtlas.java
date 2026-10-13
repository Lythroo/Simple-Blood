package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.compat.IrisPbr;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One dynamic texture that holds every surface-blood canvas tile.
 *
 * The atlas is a grid of fixed-size slots. Each slot is {@link #TILE} pixels plus a one pixel
 * transparent gutter on every side so nearest-neighbour sampling never bleeds into the
 * neighbouring tile. A {@link CanvasTile} owns a small {@link NativeImage} of its own size and
 * uploads it into its slot whenever its pixels change; only the changed tile is transferred.
 *
 * With Iris installed the atlas has a twin of the same layout holding a labPBR specular map
 * (smoothness, F0), which Iris binds alongside it so shader packs render wet pools glossy.
 *
 * All methods must be called on the render thread.
 */
public final class BloodCanvasAtlas {

    public static final Identifier ATLAS_ID = com.simpleblood.Ids.mod("surface_blood_atlas");

    /** Maximum tile edge in pixels (a full block face at the highest supported resolution). */
    public static final int TILE = 16;
    private static final int GUTTER = 1;
    private static final int SLOT = TILE + GUTTER * 2;
    public static final int SIZE = 1024;
    private static final int SLOTS_PER_ROW = SIZE / SLOT;
    public static final int CAPACITY = SLOTS_PER_ROW * SLOTS_PER_ROW;

    private static BloodCanvasAtlas instance;

    private final AtlasTexture texture;
    /** labPBR specular twin of {@link #texture} for Iris, or null without Iris. */
    private final SpecularTexture specular;
    private final NativeImage clear = new NativeImage(TILE, TILE, true);
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private int nextUnused = 0;

    private BloodCanvasAtlas() {
        this.texture = new AtlasTexture();
        Minecraft.getInstance().getTextureManager().register(ATLAS_ID, texture);
        SpecularTexture spec = IrisPbr.present() ? new SpecularTexture() : null;
        if (spec != null && !IrisPbr.registerSpecular(AtlasTexture.class, spec)) {
            spec.release();
            spec = null;
        }
        this.specular = spec;
        SimpleBlood.LOGGER.info("Surface blood atlas created ({}x{}, {} tile slots)", SIZE, SIZE, CAPACITY);
    }

    /** Lazily creates the GPU texture; safe to call every frame. */
    public static BloodCanvasAtlas get() {
        if (instance == null) {
            instance = new BloodCanvasAtlas();
        }
        return instance;
    }

    public static boolean exists() {
        return instance != null;
    }

    /** Whether tiles should paint a specular image: only when Iris is there to read it. */
    public boolean wantsSpecular() {
        return specular != null;
    }

    /** @return a free slot index, or -1 when the atlas is full. */
    public int acquireSlot() {
        Integer reused = freeSlots.pollFirst();
        if (reused != null) return reused;
        if (nextUnused < CAPACITY) return nextUnused++;
        return -1;
    }

    /** Returns a slot to the pool and blanks it so a later, smaller tile never shows stale pixels. */
    public void releaseSlot(int slot) {
        if (slot < 0) return;
        write(texture, clear, slotX(slot), slotY(slot), TILE, TILE);
        if (specular != null) write(specular, clear, slotX(slot), slotY(slot), TILE, TILE);
        freeSlots.addLast(slot);
    }

    /** Uploads a tile's colour image, and its specular image when there is one, into its slot. */
    public void upload(int slot, NativeImage image, NativeImage spec) {
        write(texture, image, slotX(slot), slotY(slot), image.getWidth(), image.getHeight());
        if (specular != null && spec != null) {
            write(specular, spec, slotX(slot), slotY(slot), spec.getWidth(), spec.getHeight());
        }
    }

    private static int slotX(int slot) { return (slot % SLOTS_PER_ROW) * SLOT + GUTTER; }
    private static int slotY(int slot) { return (slot / SLOTS_PER_ROW) * SLOT + GUTTER; }

    /** Texture-space UV of the top-left pixel of a slot. */
    public static float u0(int slot) { return slotX(slot) / (float) SIZE; }
    public static float v0(int slot) { return slotY(slot) / (float) SIZE; }
    public static float texel() { return 1.0f / SIZE; }

    private static void write(DynamicTexture target, NativeImage image, int x, int y, int w, int h) {
        //? if <=1.21.1 {
        /*target.bind();
        image.upload(0, x, y, 0, 0, w, h, false, false);
        *///?} elif <26.2 {
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(target.getTexture(), image, 0, 0, x, y, w, h, 0, 0);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(target.getTexture(), image, 0, 0, x, y);
        *///?}
    }

    /** The colour atlas. A class of its own: Iris looks up PBR loaders by exact texture class. */
    static final class AtlasTexture extends DynamicTexture {
        AtlasTexture() {
            //? if <=1.21.1 {
            /*super(SIZE, SIZE, true);
            *///?} else {
            super(() -> "simpleblood surface atlas", SIZE, SIZE, true);
            //?}
        }

        //? if <=1.21.1 {
        /*@Override
        public void reset(net.minecraft.client.renderer.texture.TextureManager manager,
                          net.minecraft.server.packs.resources.ResourceManager resources,
                          net.minecraft.resources.Identifier id, java.util.concurrent.Executor executor) {
            // 1.21.1 resets every registered texture on a resource reload (F3+T, a server
            // pack), which would free the atlas storage and leave the blood invisible. The
            // atlas is ours and never comes from a resource pack: keep it as it is.
            manager.register(id, this);
        }
        *///?}
    }

    /**
     * The specular twin. Iris closes the textures its PBR loaders hand over on every shader
     * reload, which would free this one under the atlas, so {@code close()} is ignored; the
     * atlas lives as long as the game.
     */
    static final class SpecularTexture extends DynamicTexture {
        SpecularTexture() {
            //? if <=1.21.1 {
            /*super(SIZE, SIZE, true);
            *///?} else {
            super(() -> "simpleblood surface specular", SIZE, SIZE, true);
            //?}
        }

        @Override
        public void close() {
            // owned by the atlas, not by Iris; see release()
        }

        void release() {
            super.close();
        }
    }
}
