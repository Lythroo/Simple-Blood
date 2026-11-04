package com.simpleblood.gui;

import com.simpleblood.Lang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.SkinColours;
import com.simpleblood.surface.BloodSurfaces;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class SettingsBackup {

    private SettingsBackup() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("simpleblood").resolve("settings");
    }

    static void saveToFile(Consumer<String> say) {
        String name = "settings-" + LocalDateTime.now().format(STAMP) + ".json";
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
        boolean shown = FileDialogs.save(Lang.t("simpleblood.backup.save_simple_blood_settings"), folder().resolve(name), picked -> {
            if (picked == null) {
                say.accept(Lang.t("simpleblood.backup.not_saved"));
                return;
            }
            if (!picked.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                picked = picked.resolveSibling(picked.getFileName() + ".json");
            }
            say.accept(write(picked) ? Lang.t("simpleblood.backup.saved", picked.getFileName()) : Lang.t("simpleblood.backup.could_not_save", picked.getFileName()));
        });
        if (!shown) say.accept(write(folder().resolve(name)) ? Lang.t("simpleblood.backup.saved_in_folder", name) : Lang.t("simpleblood.backup.could_not_save_the_settings"));
    }

    static void loadFromFile(Consumer<String> say, Runnable loaded) {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
        boolean shown = FileDialogs.open(Lang.t("simpleblood.backup.load_simple_blood_settings"), folder(), picked -> {
            if (picked == null) {
                say.accept(Lang.t("simpleblood.backup.nothing_loaded"));
                return;
            }
            if (read(picked)) {
                say.accept(Lang.t("simpleblood.backup.loaded", picked.getFileName()));
                loaded.run();
            } else {
                say.accept(Lang.t("simpleblood.backup.holds_no_settings", picked.getFileName()));
            }
        });
        if (shown) return;
        Path newest = newest();
        if (newest != null && read(newest)) {
            say.accept(Lang.t("simpleblood.backup.loaded", newest.getFileName()));
            loaded.run();
        } else {
            say.accept(Lang.t("simpleblood.backup.no_saved_settings_found"));
        }
    }

    static String copy() {
        Minecraft.getInstance().keyboardHandler.setClipboard(GSON.toJson(SimpleBloodClient.getConfig()));
        return Lang.t("simpleblood.backup.settings_copied_as_text");
    }

    static String paste() {
        return apply(Minecraft.getInstance().keyboardHandler.getClipboard()) ? Lang.t("simpleblood.backup.copied_settings_loaded") : Lang.t("simpleblood.backup.no_settings_copied");
    }

    private static boolean write(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(SimpleBloodClient.getConfig()));
            return true;
        } catch (IOException | RuntimeException e) {
            SimpleBlood.LOGGER.warn("Could not save the settings to {}", file, e);
            return false;
        }
    }

    private static boolean read(Path file) {
        try {
            return apply(Files.readString(file));
        } catch (IOException | RuntimeException e) {
            SimpleBlood.LOGGER.warn("Could not read {}", file, e);
            return false;
        }
    }

    static void openFolder() {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
        SkinColours.openFolder(folder());
    }

    private static boolean apply(String json) {
        if (json == null || json.isBlank() || !json.trim().startsWith("{")) return false;
        try {
            SimpleBloodConfig cfg = GSON.fromJson(json, SimpleBloodConfig.class);
            if (cfg == null || cfg.general == null) return false;
            cfg.fillGaps();
            cfg.vanillaEntities.ensureDefaults();
            SimpleBloodClient.setConfig(cfg);
            BloodSurfaces.clear();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Path newest() {
        if (!Files.isDirectory(folder())) return null;
        try (Stream<Path> files = Files.list(folder())) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .max(java.util.Comparator.comparing(f -> f.getFileName().toString())).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
