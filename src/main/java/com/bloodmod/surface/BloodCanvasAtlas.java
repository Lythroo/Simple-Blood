package com.bloodmod.surface;

import com.bloodmod.BloodMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BloodCanvasAtlas {

    public static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath(BloodMod.MOD_ID, "surface_blood_atlas");

    public static final int TILE = 16;
    private static final int GUTTER = 1;
    private static final int SLOT = TILE + GUTTER * 2;
    public static final int SIZE = 1024;
    private static final int SLOTS_PER_ROW = SIZE / SLOT;
    public static final int CAPACITY = SLOTS_PER_ROW * SLOTS_PER_ROW;

    private static BloodCanvasAtlas instance;

    private final DynamicTexture texture;
    private final NativeImage clear = new NativeImage(TILE, TILE, true);
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private int nextUnused = 0;

    private BloodCanvasAtlas() {
        //? if 1.21.1 {
        /*this.texture = new DynamicTexture(SIZE, SIZE, true) {
            @Override
            public void reset(net.minecraft.client.renderer.texture.TextureManager manager,
                              net.minecraft.server.packs.resources.ResourceManager resources,
                              net.minecraft.resources.Identifier id, java.util.concurrent.Executor executor) {
                manager.register(id, this);
            }
        };
        *///?} else {
        this.texture = new DynamicTexture(() -> "bloodmod surface atlas", SIZE, SIZE, true);
        //?}
        Minecraft.getInstance().getTextureManager().register(ATLAS_ID, texture);
        BloodMod.LOGGER.info("Surface blood atlas created ({}x{}, {} tile slots)", SIZE, SIZE, CAPACITY);
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

    int acquireSlot() {
        Integer reused = freeSlots.pollFirst();
        if (reused != null) return reused;
        if (nextUnused < CAPACITY) return nextUnused++;
        return -1;
    }

    void releaseSlot(int slot) {
        if (slot < 0) return;
        write(clear, slotX(slot), slotY(slot), TILE, TILE);
        freeSlots.addLast(slot);
    }

    void upload(int slot, NativeImage image) {
        write(image, slotX(slot), slotY(slot), image.getWidth(), image.getHeight());
    }

    private static int slotX(int slot) { return (slot % SLOTS_PER_ROW) * SLOT + GUTTER; }
    private static int slotY(int slot) { return (slot / SLOTS_PER_ROW) * SLOT + GUTTER; }

    static float u0(int slot) { return slotX(slot) / (float) SIZE; }
    static float v0(int slot) { return slotY(slot) / (float) SIZE; }
    static float texel() { return 1.0f / SIZE; }

    private void write(NativeImage image, int x, int y, int w, int h) {
        //? if 1.21.1 {
        /*texture.bind();
        image.upload(0, x, y, 0, 0, w, h, false, false);
        *///?} elif <26.2 {
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(texture.getTexture(), image, 0, 0, x, y, w, h, 0, 0);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(texture.getTexture(), image, 0, 0, x, y);
        *///?}
    }
}
