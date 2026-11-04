package com.simpleblood.gui;

import com.simpleblood.Lang;

import com.simpleblood.SimpleBlood;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

final class FileDialogs {

    private FileDialogs() {}

    private static volatile boolean open;

    static boolean busy() { return open; }

    static boolean save(String title, Path suggested, Consumer<Path> done) {
        return show(true, title, suggested, done);
    }

    static boolean open(String title, Path folder, Consumer<Path> done) {
        return show(false, title, folder, done);
    }

    private static boolean show(boolean save, String title, Path at, Consumer<Path> done) {
        if (open) return true;
        open = true;
        Consumer<Path> back = p -> Minecraft.getInstance().execute(() -> {
            open = false;
            done.accept(p);
        });
        try {
            //? if <26.3 {
            Thread t = new Thread(() -> {
                Path picked = null;
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(0, stack.UTF8("*.json"));
                    String start = save ? at.toString() : at.toString() + File.separator;
                    String got = save
                            ? org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(title, start, filters, Lang.t("simpleblood.file_dialog.settings_json"))
                            : org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, Lang.t("simpleblood.file_dialog.settings_json"), false);
                    if (got != null && !got.isBlank()) picked = Path.of(got);
                } catch (Throwable e) {
                    SimpleBlood.LOGGER.warn("The file window failed", e);
                }
                back.accept(picked);
            }, "Simple Blood file window");
            t.setDaemon(true);
            t.start();
            //?} else {
            /*            java.nio.ByteBuffer name = org.lwjgl.system.MemoryUtil.memUTF8(Lang.t("simpleblood.file_dialog.settings"));
            java.nio.ByteBuffer pattern = org.lwjgl.system.MemoryUtil.memUTF8("json");
            org.lwjgl.sdl.SDL_DialogFileFilter.Buffer filters = org.lwjgl.sdl.SDL_DialogFileFilter.calloc(1);
            filters.get(0).set(name, pattern);
            org.lwjgl.sdl.SDL_DialogFileCallback[] callback = new org.lwjgl.sdl.SDL_DialogFileCallback[1];
            callback[0] = org.lwjgl.sdl.SDL_DialogFileCallback.create((userdata, files, filter) -> {
                Path picked = null;
                if (files != 0L) {
                    long first = org.lwjgl.system.MemoryUtil.memGetAddress(files);
                    if (first != 0L) picked = Path.of(org.lwjgl.system.MemoryUtil.memUTF8(first));
                }
                Path got = picked;
                Minecraft.getInstance().execute(() -> {
                    callback[0].free();
                    filters.free();
                    org.lwjgl.system.MemoryUtil.memFree(name);
                    org.lwjgl.system.MemoryUtil.memFree(pattern);
                });
                back.accept(got);
            });
            String start = save ? at.toString() : at.toString() + File.separator;
            if (save) org.lwjgl.sdl.SDLDialog.SDL_ShowSaveFileDialog(callback[0], 0L, 0L, filters, start);
            else org.lwjgl.sdl.SDLDialog.SDL_ShowOpenFileDialog(callback[0], 0L, 0L, filters, start, false);
            *///?}
            return true;
        } catch (Throwable e) {
            open = false;
            SimpleBlood.LOGGER.warn("No file window here", e);
            return false;
        }
    }
}
