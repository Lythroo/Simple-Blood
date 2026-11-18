package com.simpleblood.surface.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StainWorldKeyTest {

    private static final java.util.regex.Pattern SAFE = java.util.regex.Pattern.compile("[a-z0-9_][a-z0-9._-]*-[0-9a-f]{8}");

    @Test
    void singleplayerUsesTheSaveFolder() {
        String key = StainWorldKey.singleplayer("My World");
        assertTrue(key.startsWith("world-my_world-"), key);
        assertEquals(key, StainWorldKey.singleplayer("My World"));
        assertNotEquals(key, StainWorldKey.singleplayer("My World (1)"));
        assertNotEquals(StainWorldKey.singleplayer("a b"), StainWorldKey.singleplayer("a_b"));
    }

    @Test
    void namesAreFolderSafe() {
        for (String odd : new String[] {"..", "con", "Wörld ☃", "a/b\\c:d*e?f\"g<h>i|j", "", "   ", ".hidden"}) {
            String part = StainWorldKey.singleplayer(odd).substring("world-".length());
            assertTrue(SAFE.matcher(part).matches(), odd + " -> " + part);
        }
        String dim = StainWorldKey.dimension("minecraft:the_nether");
        assertTrue(dim.startsWith("minecraft_the_nether-"), dim);
        assertTrue(SAFE.matcher(dim).matches(), dim);
    }

    @Test
    void longNamesStayShortAndApart() {
        String a = StainWorldKey.singleplayer("x".repeat(300) + "1");
        String b = StainWorldKey.singleplayer("x".repeat(300) + "2");
        assertTrue(a.length() < 70, a);
        assertNotEquals(a, b);
    }

    @Test
    void serverAddressesAreNormalised() {
        String plain = StainWorldKey.server("play.example.com");
        assertEquals(plain, StainWorldKey.server("Play.Example.COM"));
        assertEquals(plain, StainWorldKey.server("  play.example.com:25565 "));
        assertEquals(plain, StainWorldKey.server("play.example.com."));
        assertEquals(plain, StainWorldKey.server("play.example.com:"));
        assertNotEquals(plain, StainWorldKey.server("play.example.com:25566"));
        assertNotEquals(plain, StainWorldKey.server("other.example.com"));
        assertTrue(plain.startsWith("server-play.example.com-"), plain);
    }

    @Test
    void ipAddressesWithPorts() {
        assertEquals("127.0.0.1", StainWorldKey.normaliseAddress("127.0.0.1:25565"));
        assertEquals("127.0.0.1:25570", StainWorldKey.normaliseAddress("127.0.0.1:25570"));
        assertEquals("::1", StainWorldKey.normaliseAddress("[::1]:25565"));
        assertEquals("::1:25570", StainWorldKey.normaliseAddress("[::1]:25570"));
        assertEquals("fe80::1", StainWorldKey.normaliseAddress("FE80::1"));
        assertEquals("", StainWorldKey.normaliseAddress(null));
        assertTrue(SAFE.matcher(StainWorldKey.server("[::1]:25570").substring("server-".length())).matches());
    }

    @Test
    void worldsAndServersNeverShareAFolder() {
        assertNotEquals(StainWorldKey.singleplayer("localhost"), StainWorldKey.server("localhost"));
    }
}
