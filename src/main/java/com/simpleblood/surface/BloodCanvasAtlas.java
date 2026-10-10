package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.compat.IrisPbr;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BloodCanvasAtlas {

    public static final Identifier ATLAS_ID = com.simpleblood.Ids.mod("surface_blood_atlas");

    public static final int TILE = 16;
    private static final int GUTTER = 1;
    private static final int SLOT = TILE + GUTTER * 2;
    public static final int SIZE = 1024;
    private static final int SLOTS_PER_ROW = SIZE / SLOT;
    public static final int CAPACITY = SLOTS_PER_ROW * SLOTS_PER_ROW;

    private static BloodCanvasAtlas instance;

    private final AtlasTexture texture;
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

    public static BloodCanvasAtlas get() {
        if (instance == null) {
            instance = new BloodCanvasAtlas();
        }
        return instance;
    }

    public static boolean exists() {
        return instance != null;
    }

    boolean wantsSpecular() {
        return specular != null;
    }

    int acquireSlot() {
        Integer reused = freeSlots.pollFirst();
        if (reused != null) return reused;
        if (nextUnused < CAPACITY) return nextUnused++;
        return -1;
    }

    void releaseSlot(int slot) {
        if (slot < 0) return;
        write(texture, clear, slotX(slot), slotY(slot), TILE, TILE);
        if (specular != null) write(specular, clear, slotX(slot), slotY(slot), TILE, TILE);
        freeSlots.addLast(slot);
    }

    void upload(int slot, NativeImage image, NativeImage spec) {
        write(texture, image, slotX(slot), slotY(slot), image.getWidth(), image.getHeight());
        if (specular != null && spec != null) {
            write(specular, spec, slotX(slot), slotY(slot), spec.getWidth(), spec.getHeight());
        }
    }

    private static int slotX(int slot) { return (slot % SLOTS_PER_ROW) * SLOT + GUTTER; }
    private static int slotY(int slot) { return (slot / SLOTS_PER_ROW) * SLOT + GUTTER; }

    static float u0(int slot) { return slotX(slot) / (float) SIZE; }
    static float v0(int slot) { return slotY(slot) / (float) SIZE; }
    static float texel() { return 1.0f / SIZE; }

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
            manager.register(id, this);
        }
        *///?}
    }

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
        }

        void release() {
            super.close();
        }
    }
}
