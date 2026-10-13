package com.simpleblood;

import net.minecraft.resources.Identifier;

/** Resource ids, made the same way on every version (1.20.1 only has the constructor). */
public final class Ids {
    private Ids() {}

    public static Identifier of(String namespace, String path) {
        //? if 1.20.1 {
        /*return new Identifier(namespace, path);
        *///?} else {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?}
    }

    /** Parses {@code namespace:path}; throws on an invalid id. */
    public static Identifier parse(String id) {
        //? if 1.20.1 {
        /*return new Identifier(id);
        *///?} else {
        return Identifier.parse(id);
        //?}
    }

    /** An id in the {@code minecraft} namespace. */
    public static Identifier vanilla(String path) {
        return of("minecraft", path);
    }

    /** An id in this mod's namespace. */
    public static Identifier mod(String path) {
        return of(SimpleBlood.MOD_ID, path);
    }
}
