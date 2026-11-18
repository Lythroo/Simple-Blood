package com.simpleblood.surface.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StainStoreTest {

    @TempDir
    Path dir;

    @Test
    void replaceMergeAndDelete() throws IOException {
        StainStore store = new StainStore(dir);
        Path file = store.regionFile("world-a", "overworld", 0, -1);
        assertTrue(store.read(file).isEmpty());

        store.update(file, Map.of(1L, List.of(StainCodecTest.tile(10L, 1, 4, false))), Map.of());
        store.update(file, Map.of(2L, List.of(StainCodecTest.tile(20L, 1, 4, false))), Map.of());
        assertEquals(2, store.read(file).size());

        store.update(file, Map.of(), Map.of(1L, List.of(StainCodecTest.tile(10L, 1, 9, true), StainCodecTest.tile(11L, 1, 2, false))));
        List<SavedTile> one = store.read(file).get(1L);
        assertEquals(2, one.size());
        assertEquals(9, one.get(0).pixels().count());

        store.update(file, Map.of(1L, List.of()), Map.of());
        assertEquals(List.of(2L), List.copyOf(store.read(file).keySet()));
        store.update(file, Map.of(2L, List.of()), Map.of());
        assertFalse(Files.exists(file));
        assertEquals(List.of(), store.regions("world-a", "overworld"));
    }

    @Test
    void noTemporaryFileIsLeft() throws IOException {
        StainStore store = new StainStore(dir);
        Path file = store.regionFile("world-a", "overworld", 3, 4);
        store.update(file, Map.of(1L, List.of(StainCodecTest.tile(10L, 1, 4, false))), Map.of());
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of("r.3.4.bin"), files.map(p -> p.getFileName().toString()).toList());
        }
        List<int[]> regions = store.regions("world-a", "overworld");
        assertEquals(1, regions.size());
        assertEquals(3, regions.get(0)[0]);
        assertEquals(4, regions.get(0)[1]);
    }

    @Test
    void aBrokenFileIsMovedAsideNotLost() throws IOException {
        StainStore store = new StainStore(dir);
        Path file = store.regionFile("world-a", "overworld", 0, 0);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {'S', 'B', 'S', 'T', 0, 0, 0, 1, 9, 9, 9});
        Path aside = store.update(file, Map.of(5L, List.of(StainCodecTest.tile(1L, 1, 2, false))), Map.of());
        assertNotNull(aside);
        assertTrue(Files.exists(aside));
        assertEquals(List.of(5L), List.copyOf(store.read(file).keySet()));
        assertNull(store.update(file, Map.of(), Map.of()));
    }

    @Test
    void wipeTakesTheWholeWorld() throws IOException {
        StainStore store = new StainStore(dir);
        store.update(store.regionFile("world-a", "overworld", 0, 0), Map.of(1L, List.of(StainCodecTest.tile(1L, 1, 2, false))), Map.of());
        store.update(store.regionFile("world-a", "the_nether", 0, 0), Map.of(1L, List.of(StainCodecTest.tile(1L, 1, 2, false))), Map.of());
        store.update(store.regionFile("world-b", "overworld", 0, 0), Map.of(1L, List.of(StainCodecTest.tile(1L, 1, 2, false))), Map.of());
        store.wipe("world-a");
        assertFalse(Files.exists(store.worldDir("world-a")));
        assertTrue(Files.exists(store.regionFile("world-b", "overworld", 0, 0)));
    }

    @Test
    void regionOfNegativeChunks() {
        assertEquals(0, StainStore.regionOf(31));
        assertEquals(1, StainStore.regionOf(32));
        assertEquals(-1, StainStore.regionOf(-1));
        assertEquals(-1, StainStore.regionOf(-32));
        assertEquals(-2, StainStore.regionOf(-33));
    }
}
