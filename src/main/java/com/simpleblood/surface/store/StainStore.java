package com.simpleblood.surface.store;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class StainStore {

    public static final int REGION_SHIFT = 5;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.bin");

    private final Path root;

    public StainStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path worldDir(String worldKey) {
        return root.resolve(worldKey);
    }

    public Path regionFile(String worldKey, String dimension, int regionX, int regionZ) {
        return worldDir(worldKey).resolve(dimension).resolve("r." + regionX + "." + regionZ + ".bin");
    }

    public List<int[]> regions(String worldKey, String dimension) throws IOException {
        Path dir = worldDir(worldKey).resolve(dimension);
        List<int[]> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.bin")) {
            for (Path f : files) {
                Matcher m = REGION_FILE.matcher(f.getFileName().toString());
                if (m.matches()) out.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            }
        }
        return out;
    }

    public Map<Long, List<SavedTile>> read(Path file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 16384)) {
            return StainCodec.decode(in);
        } catch (NoSuchFileException e) {
            return new LinkedHashMap<>();
        }
    }

    public Path update(Path file, Map<Long, List<SavedTile>> replace, Map<Long, List<SavedTile>> merge) throws IOException {
        return update(file, replace, merge, Map.of());
    }

    public Path update(Path file, Map<Long, List<SavedTile>> replace, Map<Long, List<SavedTile>> merge,
                       Map<Long, ? extends java.util.Set<String>> keep) throws IOException {
        Map<Long, List<SavedTile>> chunks;
        Path movedAside = null;
        try {
            chunks = new LinkedHashMap<>(read(file));
        } catch (StainCodec.UnsupportedStainVersionException e) {
            throw e;
        } catch (StainCodec.CorruptStainFileException e) {
            movedAside = file.resolveSibling(file.getFileName() + ".bad");
            Files.move(file, movedAside, StandardCopyOption.REPLACE_EXISTING);
            chunks = new LinkedHashMap<>();
        }
        for (Map.Entry<Long, List<SavedTile>> e : replace.entrySet()) {
            List<SavedTile> tiles = withKept(chunks.get(e.getKey()), e.getValue(), keep.get(e.getKey()));
            if (tiles.isEmpty()) chunks.remove(e.getKey());
            else chunks.put(e.getKey(), tiles);
        }
        for (Map.Entry<Long, List<SavedTile>> e : merge.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Map<String, SavedTile> byFace = new LinkedHashMap<>();
            for (SavedTile t : chunks.getOrDefault(e.getKey(), List.of())) byFace.put(faceKey(t), t);
            for (SavedTile t : e.getValue()) byFace.put(faceKey(t), t);
            chunks.put(e.getKey(), List.copyOf(byFace.values()));
        }
        if (chunks.isEmpty()) {
            Files.deleteIfExists(file);
        } else {
            writeAtomically(file, StainCodec.encode(chunks));
        }
        return movedAside;
    }

    private static List<SavedTile> withKept(List<SavedTile> old, List<SavedTile> tiles, java.util.Set<String> keep) {
        if (old == null || keep == null || keep.isEmpty()) return tiles;
        Map<String, SavedTile> byFace = new LinkedHashMap<>();
        for (SavedTile t : old) if (keep.contains(faceKey(t))) byFace.put(faceKey(t), t);
        for (SavedTile t : tiles) byFace.put(faceKey(t), t);
        return byFace.size() == tiles.size() ? tiles : List.copyOf(byFace.values());
    }

    private static String faceKey(SavedTile t) {
        SavedTile.Where w = t.where();
        return faceKey(w.pos(), w.face(), w.box());
    }

    public static String faceKey(long pos, int face, int box) {
        return pos + "/" + face + "/" + box;
    }

    public static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void wipe(String worldKey) throws IOException {
        Path dir = worldDir(worldKey);
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    public static int regionOf(int chunk) {
        return chunk >> REGION_SHIFT;
    }
}
