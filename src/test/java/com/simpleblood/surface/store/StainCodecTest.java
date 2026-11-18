package com.simpleblood.surface.store;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StainCodecTest {

    static SavedTile tile(long pos, int face, int pixels, boolean forever) {
        int n = pixels;
        short[] index = new short[n];
        byte[] tone = new byte[n], depth = new byte[n], thin = new byte[n], flags = new byte[n];
        int[] rgb = new int[n], elapsed = new int[n], life = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = (short) (i * 3);
            tone[i] = (byte) (1 + i % 3);
            depth[i] = (byte) (1 + i % 6);
            thin[i] = (byte) (i % 4);
            flags[i] = (byte) (i % 5 == 0 ? SavedTile.Pixels.POWDER : 0);
            rgb[i] = 0x8A0707 + i;
            elapsed[i] = 17 * i;
            life[i] = 1200 + i | (forever ? SavedTile.Pixels.FOREVER_BIT : 0);
        }
        return new SavedTile(new SavedTile.Where(pos, face, 0, face, "minecraft:stone", 1.5, 65.0, -3.5,
                1f, 65f, -4f, 1f, 1f, 16, 16),
                new SavedTile.Pixels(index, tone, depth, thin, flags, rgb, elapsed, life));
    }

    static Map<Long, List<SavedTile>> region() {
        Map<Long, List<SavedTile>> m = new LinkedHashMap<>();
        m.put(5L, List.of(tile(123L, 1, 40, false), tile(124L, 2, 7, true)));
        m.put(-9L, List.of(tile(-77L, 0, 0, false)));
        return m;
    }

    static void assertSame(Map<Long, List<SavedTile>> want, Map<Long, List<SavedTile>> got) {
        assertEquals(want.keySet(), got.keySet());
        for (Long k : want.keySet()) {
            List<SavedTile> a = want.get(k), b = got.get(k);
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).where(), b.get(i).where());
                SavedTile.Pixels p = a.get(i).pixels(), q = b.get(i).pixels();
                assertArrayEquals(p.index(), q.index());
                assertArrayEquals(p.tone(), q.tone());
                assertArrayEquals(p.depth(), q.depth());
                assertArrayEquals(p.thin(), q.thin());
                assertArrayEquals(p.flags(), q.flags());
                assertArrayEquals(p.rgb(), q.rgb());
                assertArrayEquals(p.elapsed(), q.elapsed());
                assertArrayEquals(p.life(), q.life());
            }
        }
    }

    @Test
    void roundTripKeepsEverything() throws IOException {
        Map<Long, List<SavedTile>> want = region();
        byte[] bytes = StainCodec.encode(want);
        assertSame(want, StainCodec.decode(new ByteArrayInputStream(bytes)));
    }

    @Test
    void emptyRegionRoundTrips() throws IOException {
        byte[] bytes = StainCodec.encode(Map.of());
        assertTrue(StainCodec.decode(new ByteArrayInputStream(bytes)).isEmpty());
    }

    @Test
    void headerNamesTheFormatAndVersion() throws IOException {
        byte[] bytes = StainCodec.encode(region());
        assertEquals("SBST", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        int version = ((bytes[4] & 0xFF) << 24) | ((bytes[5] & 0xFF) << 16) | ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        assertEquals(StainCodec.VERSION, version);
    }

    @Test
    void newerVersionIsRefusedNotGuessed() throws IOException {
        byte[] bytes = withVersion(StainCodec.encode(region()), StainCodec.VERSION + 1);
        StainCodec.UnsupportedStainVersionException e = assertThrows(StainCodec.UnsupportedStainVersionException.class,
                () -> StainCodec.decode(new ByteArrayInputStream(bytes)));
        assertEquals(StainCodec.VERSION + 1, e.version);
    }

    @Test
    void olderVersionGoesThroughItsMigration() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream head = new DataOutputStream(raw);
        head.writeBytes("SBST");
        head.writeInt(0);
        head.flush();
        try (DataOutputStream out = new DataOutputStream(new java.util.zip.DeflaterOutputStream(raw))) {
            out.writeLong(42L);
        }
        Map<Integer, StainCodec.PayloadReader> migrations = Map.of(0, payload -> {
            long chunk = payload.readLong();
            return Map.of(chunk, List.of(tile(1L, 1, 3, true)));
        });
        Map<Long, List<SavedTile>> got = StainCodec.decode(new ByteArrayInputStream(raw.toByteArray()), migrations);
        assertEquals(List.of(42L), List.copyOf(got.keySet()));
        assertEquals(3, got.get(42L).get(0).pixels().count());
        assertThrows(StainCodec.CorruptStainFileException.class, () -> StainCodec.decode(new ByteArrayInputStream(raw.toByteArray())));
    }

    @Test
    void truncatedFileIsCorrupt() throws IOException {
        byte[] bytes = StainCodec.encode(region());
        for (int cut : new int[] {0, 3, 7, 8, 12, bytes.length / 2, bytes.length - 1}) {
            byte[] part = Arrays.copyOf(bytes, cut);
            assertThrows(StainCodec.CorruptStainFileException.class, () -> StainCodec.decode(new ByteArrayInputStream(part)),
                    "cut at " + cut);
        }
    }

    @Test
    void damagedBytesAreCorruptNeverSomethingElse() throws IOException {
        byte[] bytes = StainCodec.encode(region());
        java.util.Random rng = new java.util.Random(7);
        for (int round = 0; round < 300; round++) {
            byte[] bad = bytes.clone();
            int at = 8 + rng.nextInt(bad.length - 8);
            bad[at] ^= (byte) (1 + rng.nextInt(255));
            try {
                StainCodec.decode(new ByteArrayInputStream(bad));
            } catch (StainCodec.CorruptStainFileException expected) {
            }
        }
    }

    @Test
    void wrongMagicIsCorrupt() throws IOException {
        byte[] bytes = StainCodec.encode(region());
        bytes[0] = 'X';
        assertThrows(StainCodec.CorruptStainFileException.class, () -> StainCodec.decode(new ByteArrayInputStream(bytes)));
    }

    @Test
    void pixelArraysMustAgree() {
        assertThrows(IllegalArgumentException.class, () -> new SavedTile.Pixels(new short[2], new byte[2], new byte[2],
                new byte[2], new byte[2], new int[2], new int[1], new int[2]));
    }

    private static byte[] withVersion(byte[] bytes, int version) {
        byte[] out = bytes.clone();
        out[4] = (byte) (version >>> 24);
        out[5] = (byte) (version >>> 16);
        out[6] = (byte) (version >>> 8);
        out[7] = (byte) version;
        return out;
    }
}
