package com.simpleblood;

import net.minecraft.resources.Identifier;

public final class Ids {
    private Ids() {}

    public static Identifier of(String namespace, String path) {
        //? if 1.20.1 {
        /*return new Identifier(namespace, path);
        *///?} else {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?}
    }

    public static Identifier parse(String id) {
        //? if 1.20.1 {
        /*return new Identifier(id);
        *///?} else {
        return Identifier.parse(id);
        //?}
    }

    public static Identifier vanilla(String path) {
        return of("minecraft", path);
    }

    public static Identifier mod(String path) {
        return of(SimpleBlood.MOD_ID, path);
    }
}
