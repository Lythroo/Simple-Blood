package com.simpleblood;

import net.minecraft.locale.Language;

import java.util.IllegalFormatException;
import java.util.Locale;

public final class Lang {
    private Lang() {}

    public static String t(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    public static String[] all(String[] keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) out[i] = t(keys[i]);
        return out;
    }

    public static String t(String key, Object... args) {
        String text = t(key);
        try {
            return String.format(Locale.ROOT, text, args);
        } catch (IllegalFormatException e) {
            return text;
        }
    }
}
