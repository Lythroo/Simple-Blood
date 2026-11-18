package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.surface.store.SavedTile;
import com.simpleblood.surface.store.StainCodec;
import com.simpleblood.surface.store.StainStore;
import com.simpleblood.surface.store.StainWorldKey;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SavedStains {

    private SavedStains() {}

    private static final int POLL_TICKS = 20;
    private static final int FLUSH_TICKS = 20 * 30;
    private static final int RESTORES_PER_TICK = 48;
    private static final int SWAPS_PER_POLL = 32;
    private static final double SWAP_MARGIN = 8.0;
    private static final int MAX_PARKED = 2048;
    private static final int FLUSH_CHUNKS_PER_TICK = 8;

    private record Parked(SavedTile tile, int at) {}

    private record Pending(long chunk, SavedTile tile, int generation) {}

    private static StainStore store;
    private static ExecutorService io;
    private static int generation;
    private static String worldKey, dimension;
    private static boolean listed;
    private static final LongOpenHashSet REGIONS = new LongOpenHashSet();
    private static final LongOpenHashSet KNOWN = new LongOpenHashSet();
    private static final LongOpenHashSet READING = new LongOpenHashSet();
    private static final LongOpenHashSet ARRIVED = new LongOpenHashSet();
    private static final LongOpenHashSet UNSAVED = new LongOpenHashSet();
    private static final Long2ObjectOpenHashMap<Map<CanvasTile.Key, Parked>> PARKED = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Set<String>> AWAY = new Long2ObjectOpenHashMap<>();
    private static long[] flushing = new long[0];
    private static int flushed;
    private static final ArrayDeque<Pending> TO_RESTORE = new ArrayDeque<>();
    private static final Long2IntOpenHashMap PENDING = new Long2IntOpenHashMap();
    private static final ConcurrentLinkedQueue<Runnable> RESULTS = new ConcurrentLinkedQueue<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static int clock;
    private static boolean unkeyed;

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    static long chunkOf(BlockPos pos) {
        return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static int chunkX(long key) {
        return (int) key;
    }

    private static int chunkZ(long key) {
        return (int) (key >> 32);
    }

    private static long regionOf(long chunk) {
        return chunkKey(StainStore.regionOf(chunkX(chunk)), StainStore.regionOf(chunkZ(chunk)));
    }

    static boolean loaded(ClientLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    static boolean loaded(ClientLevel level, BlockPos pos) {
        return loaded(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    static boolean active() {
        return worldKey != null;
    }

    public static Path worldFolder() {
        return worldKey == null ? null : store().worldDir(worldKey);
    }

    public static Path folderOfSingleplayer(String saveFolder) {
        return store().worldDir(StainWorldKey.singleplayer(saveFolder));
    }

    private static StainStore store() {
        if (store == null) {
            store = new StainStore(Minecraft.getInstance().gameDirectory.toPath().resolve("simpleblood").resolve("stains"));
        }
        return store;
    }

    private static void submit(java.util.function.Consumer<StainStore> job) {
        StainStore st = store();
        if (io == null) {
            io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Simple Blood stain store");
                t.setDaemon(true);
                return t;
            });
        }
        io.execute(() -> job.accept(st));
    }

    private static String worldKeyOf(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            Path folder = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return folder == null ? null : StainWorldKey.singleplayer(folder.toString());
        }
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return null;
        return StainWorldKey.server(server.isLan() ? hostOf(server.ip) : server.ip);
    }

    private static String hostOf(String address) {
        int colon = address.lastIndexOf(':');
        return colon > 0 && address.indexOf(':') == colon ? address.substring(0, colon) : address;
    }

    private static String dimensionOf(ClientLevel level) {
        //? if <=1.21.1 {
        /*return StainWorldKey.dimension(level.dimension().location().toString());
        *///?} else {
        return StainWorldKey.dimension(level.dimension().identifier().toString());
        //?}
    }

    private static void enter(Minecraft mc, ClientLevel level) {
        String key = worldKeyOf(mc);
        if (key == null) return;
        worldKey = key;
        dimension = dimensionOf(level);
        int gen = ++generation;
        listed = false;
        String w = worldKey, d = dimension;
        submit(st -> {
            List<int[]> found;
            try {
                found = st.regions(w, d);
            } catch (IOException | RuntimeException e) {
                SimpleBlood.LOGGER.warn("Simple Blood: could not list the saved blood in {}/{}: {}", w, d, e.toString());
                return;
            }
            RESULTS.add(() -> {
                if (gen != generation) return;
                for (int[] r : found) REGIONS.add(chunkKey(r[0], r[1]));
                listed = true;
            });
        });
        SimpleBlood.LOGGER.debug("Simple Blood keeps blood on blocks in {}/{}", worldKey, dimension);
    }

    static void leave(int now) {
        if (worldKey != null) {
            LongOpenHashSet all = new LongOpenHashSet(KNOWN);
            all.addAll(READING);
            all.addAll(UNSAVED);
            all.addAll(PARKED.keySet());
            for (CanvasTile t : BloodSurfaces.tiles()) all.add(chunkOf(t.pos));
            write(all, now);
        }
        forget();
    }

    static void wipe() {
        Minecraft mc = Minecraft.getInstance();
        String w = worldKey != null ? worldKey : mc.level != null ? worldKeyOf(mc) : null;
        if (w == null) return;
        forget();
        submit(st -> {
            try {
                st.wipe(w);
            } catch (IOException e) {
                SimpleBlood.LOGGER.warn("Simple Blood: could not delete the saved blood of {}: {}", w, e.toString());
            }
        });
        com.simpleblood.Tally.count("stains.wiped");
    }

    private static void forget() {
        generation++;
        unkeyed = false;
        worldKey = null;
        dimension = null;
        listed = false;
        REGIONS.clear();
        KNOWN.clear();
        READING.clear();
        ARRIVED.clear();
        UNSAVED.clear();
        PARKED.clear();
        AWAY.clear();
        TO_RESTORE.clear();
        PENDING.clear();
        flushing = new long[0];
        flushed = 0;
    }

    static void tick(Minecraft mc, ClientLevel level, SimpleBloodConfig.SurfaceSettings s, int now) {
        if (worldKey == null) {
            if (unkeyed) return;
            enter(mc, level);
            if (worldKey == null) {
                unkeyed = true;
                return;
            }
        }
        for (Runnable r; (r = RESULTS.poll()) != null; ) r.run();
        restoreSome(level, s, now);
        clock++;
        if (clock % POLL_TICKS == 0) {
            dropGoneChunks(level, now);
            claimLoadedChunks(mc, level);
            swap(mc, level, s, now);
            capParked(mc, now);
        }
        if (clock % FLUSH_TICKS == 0 && flushed >= flushing.length && !UNSAVED.isEmpty()) {
            flushing = UNSAVED.toLongArray();
            flushed = 0;
            it.unimi.dsi.fastutil.longs.LongArrays.quickSort(flushing, (a, b) -> Long.compare(regionOf(a), regionOf(b)));
            com.simpleblood.Tally.count("stains.flush");
        }
        flushSome(now);
    }

    private static void flushSome(int now) {
        if (flushed >= flushing.length) return;
        LongOpenHashSet slice = new LongOpenHashSet(FLUSH_CHUNKS_PER_TICK);
        while (flushed < flushing.length && slice.size() < FLUSH_CHUNKS_PER_TICK) {
            long c = flushing[flushed++];
            if (UNSAVED.contains(c)) slice.add(c);
        }
        write(slice, now);
    }

    static void changed(BlockPos pos) {
        if (worldKey != null) UNSAVED.add(chunkOf(pos));
    }

    private static void dropGoneChunks(ClientLevel level, int now) {
        LongOpenHashSet gone = new LongOpenHashSet();
        for (LongOpenHashSet set : List.of(KNOWN, READING)) {
            for (LongIterator it = set.iterator(); it.hasNext(); ) {
                long c = it.nextLong();
                if (!loaded(level, chunkX(c), chunkZ(c))) gone.add(c);
            }
        }
        for (long c : PARKED.keySet()) {
            if (!loaded(level, chunkX(c), chunkZ(c))) gone.add(c);
        }
        if (!gone.isEmpty()) chunksGone(gone, now);
    }

    static void chunkGone(long chunk, int now) {
        LongOpenHashSet one = new LongOpenHashSet();
        one.add(chunk);
        chunksGone(one, now);
    }

    private static void chunksGone(LongOpenHashSet gone, int now) {
        Map<Long, List<CanvasTile>> tiles = new HashMap<>();
        for (CanvasTile t : BloodSurfaces.tiles()) {
            long c = chunkOf(t.pos);
            if (gone.contains(c)) tiles.computeIfAbsent(c, k -> new ArrayList<>()).add(t);
        }
        if (worldKey != null) {
            LongOpenHashSet toWrite = new LongOpenHashSet();
            for (LongIterator it = gone.iterator(); it.hasNext(); ) {
                long c = it.nextLong();
                if (tiles.containsKey(c) || UNSAVED.contains(c) || PARKED.containsKey(c)) toWrite.add(c);
            }
            write(toWrite, now);
            com.simpleblood.Tally.add("stains.chunk.unloaded", toWrite.size());
        }
        for (List<CanvasTile> list : tiles.values()) for (CanvasTile t : list) BloodSurfaces.removeTile(t);
        for (LongIterator it = gone.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            KNOWN.remove(c);
            READING.remove(c);
            UNSAVED.remove(c);
            PARKED.remove(c);
            AWAY.remove(c);
            if (PENDING.remove(c) != 0) TO_RESTORE.removeIf(p -> p.chunk() == c);
        }
    }

    private static void claimLoadedChunks(Minecraft mc, ClientLevel level) {
        if (!listed) return;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        int cx = camera.getBlockX() >> 4, cz = camera.getBlockZ() >> 4;
        int r = mc.options.getEffectiveRenderDistance() + 1;
        LongOpenHashSet toRead = new LongOpenHashSet();
        LongOpenHashSet arrived = new LongOpenHashSet();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                long c = chunkKey(x, z);
                if (KNOWN.contains(c) || READING.contains(c) || !loaded(level, x, z)) continue;
                long region = regionOf(c);
                if (!REGIONS.contains(region)) {
                    KNOWN.add(c);
                } else if (!ARRIVED.contains(c)) {
                    arrived.add(c);
                } else {
                    READING.add(c);
                    toRead.add(region);
                }
            }
        }
        ARRIVED.clear();
        ARRIVED.addAll(arrived);
        for (LongIterator it = toRead.iterator(); it.hasNext(); ) read(it.nextLong());
    }

    private static void read(long region) {
        int gen = generation;
        Path file = store().regionFile(worldKey, dimension, chunkX(region), chunkZ(region));
        submit(st -> {
            Map<Long, List<SavedTile>> found;
            try {
                found = st.read(file);
            } catch (IOException | RuntimeException e) {
                RESULTS.add(() -> reportOnce(file, "read", e));
                return;
            }
            RESULTS.add(() -> {
                if (gen == generation) arrived(region, found, gen);
            });
        });
    }

    private static void arrived(long region, Map<Long, List<SavedTile>> chunks, int gen) {
        LongArrayList ready = new LongArrayList();
        for (LongIterator it = READING.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            if (regionOf(c) != region || PENDING.containsKey(c)) continue;
            List<SavedTile> tiles = chunks.get(c);
            if (tiles == null || tiles.isEmpty()) {
                ready.add(c);
                continue;
            }
            for (SavedTile t : tiles) TO_RESTORE.add(new Pending(c, t, gen));
            PENDING.put(c, tiles.size());
        }
        for (int i = 0; i < ready.size(); i++) {
            READING.remove(ready.getLong(i));
            KNOWN.add(ready.getLong(i));
        }
    }

    private static void restoreSome(ClientLevel level, SimpleBloodConfig.SurfaceSettings s, int now) {
        for (int n = 0; n < RESTORES_PER_TICK && !TO_RESTORE.isEmpty(); n++) {
            Pending p = TO_RESTORE.poll();
            if (p.generation() != generation || !PENDING.containsKey(p.chunk())) continue;
            if (!loaded(level, chunkX(p.chunk()), chunkZ(p.chunk()))) {
                chunkGone(p.chunk(), now);
                continue;
            }
            if (BloodSurfaces.tileCount() >= s.maxTiles) {
                park(p.chunk(), p.tile(), now);
            } else {
                bringBack(level, p.chunk(), p.tile(), now);
            }
            int left = PENDING.addTo(p.chunk(), -1) - 1;
            if (left <= 0) {
                PENDING.remove(p.chunk());
                READING.remove(p.chunk());
                KNOWN.add(p.chunk());
            }
        }
    }

    private static void bringBack(ClientLevel level, long chunk, SavedTile tile, int now) {
        BloodSurfaces.Restored r = BloodSurfaces.restore(level, tile, now);
        com.simpleblood.Tally.count("stains." + r.name().toLowerCase(java.util.Locale.ROOT));
        if (r == BloodSurfaces.Restored.DROPPED) UNSAVED.add(chunk);
    }

    static boolean park(CanvasTile tile, int now) {
        if (worldKey == null || ContraptionRides.isPlot(tile.pos)) return false;
        SavedTile saved = BloodSurfaces.saveTile(tile, now);
        if (saved != null) park(chunkOf(tile.pos), saved, now);
        return true;
    }

    private static void park(long chunk, SavedTile saved, int now) {
        PARKED.computeIfAbsent(chunk, k -> new LinkedHashMap<>()).put(keyOf(saved), new Parked(saved, now));
        UNSAVED.add(chunk);
        com.simpleblood.Tally.count("stains.parked");
    }

    private static CanvasTile.Key keyOf(SavedTile t) {
        SavedTile.Where w = t.where();
        return new CanvasTile.Key(w.pos(), net.minecraft.core.Direction.values()[w.face()], w.box());
    }

    private static void swap(Minecraft mc, ClientLevel level, SimpleBloodConfig.SurfaceSettings s, int now) {
        if (PARKED.isEmpty()) return;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        Vec3 eye = camera.position();
        List<Near> parked = parkedByDistance(eye);
        List<Near> drawn = null;
        int far = 0;
        for (int n = 0; n < SWAPS_PER_POLL && n < parked.size(); n++) {
            Near next = parked.get(n);
            if (BloodSurfaces.tileCount() >= s.maxTiles) {
                if (drawn == null) {
                    drawn = new ArrayList<>(BloodSurfaces.tileCount());
                    for (CanvasTile t : BloodSurfaces.tiles()) {
                        if (ContraptionRides.isPlot(t.pos)) continue;
                        Vec3 c = t.center();
                        drawn.add(new Near(0L, null, t, Math.sqrt(distanceSqr(eye, c.x, c.y, c.z))));
                    }
                    drawn.sort(java.util.Comparator.comparingDouble(Near::distance).reversed());
                }
                if (far >= drawn.size() || next.distance() + SWAP_MARGIN >= drawn.get(far).distance()) break;
                CanvasTile victim = drawn.get(far++).tile();
                if (park(victim, now)) BloodSurfaces.removeTile(victim);
            }
            Map<CanvasTile.Key, Parked> inChunk = PARKED.get(next.chunk());
            inChunk.remove(keyOf(next.parked().tile()));
            if (inChunk.isEmpty()) PARKED.remove(next.chunk());
            bringBack(level, next.chunk(), aged(next.parked(), now), now);
            com.simpleblood.Tally.count("stains.swapped");
        }
    }

    private static List<Near> parkedByDistance(Vec3 eye) {
        List<Near> parked = new ArrayList<>();
        for (Long2ObjectOpenHashMap.Entry<Map<CanvasTile.Key, Parked>> e : PARKED.long2ObjectEntrySet()) {
            for (Parked p : e.getValue().values()) {
                SavedTile.Where w = p.tile().where();
                parked.add(new Near(e.getLongKey(), p, null, Math.sqrt(distanceSqr(eye, w.cx(), w.cy(), w.cz()))));
            }
        }
        parked.sort(java.util.Comparator.comparingDouble(Near::distance));
        return parked;
    }

    private static void capParked(Minecraft mc, int now) {
        int over = parkedCount() - MAX_PARKED;
        if (over <= 0) return;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        List<Near> parked = parkedByDistance(camera.position());
        List<Near> farthest = parked.subList(parked.size() - over, parked.size());
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (Near n : farthest) chunks.add(n.chunk());
        write(chunks, now);
        for (Near n : farthest) {
            Map<CanvasTile.Key, Parked> inChunk = PARKED.get(n.chunk());
            inChunk.remove(keyOf(n.parked().tile()));
            if (inChunk.isEmpty()) PARKED.remove(n.chunk());
            AWAY.computeIfAbsent(n.chunk(), k -> new HashSet<>()).add(faceKeyOf(n.parked().tile()));
        }
        com.simpleblood.Tally.add("stains.parked.away", over);
    }

    private static String faceKeyOf(SavedTile t) {
        SavedTile.Where w = t.where();
        return StainStore.faceKey(w.pos(), w.face(), w.box());
    }

    private record Near(long chunk, Parked parked, CanvasTile tile, double distance) {}

    private static double distanceSqr(Vec3 eye, double x, double y, double z) {
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static SavedTile aged(Parked p, int now) {
        int waited = now - p.at();
        if (waited <= 0) return p.tile();
        SavedTile.Pixels px = p.tile().pixels();
        int[] elapsed = px.elapsed().clone();
        for (int i = 0; i < elapsed.length; i++) elapsed[i] = (int) Math.min(Integer.MAX_VALUE, (long) elapsed[i] + waited);
        return new SavedTile(p.tile().where(), new SavedTile.Pixels(px.index(), px.tone(), px.depth(), px.thin(), px.flags(),
                px.rgb(), elapsed, px.life()));
    }

    private static void write(LongOpenHashSet chunks, int now) {
        if (worldKey == null || chunks.isEmpty()) return;
        Map<Long, List<SavedTile>> live = new HashMap<>();
        for (CanvasTile t : BloodSurfaces.tiles()) {
            long c = chunkOf(t.pos);
            if (!chunks.contains(c) || ContraptionRides.isPlot(t.pos)) continue;
            SavedTile saved = BloodSurfaces.saveTile(t, now);
            if (saved != null) live.computeIfAbsent(c, k -> new ArrayList<>()).add(saved);
        }
        Map<Long, Map<Long, List<SavedTile>>> replace = new HashMap<>(), merge = new HashMap<>();
        Map<Long, Map<Long, Set<String>>> keep = new HashMap<>();
        for (LongIterator it = chunks.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            List<SavedTile> tiles = live.getOrDefault(c, new ArrayList<>());
            Map<CanvasTile.Key, Parked> parked = PARKED.get(c);
            if (parked != null) for (Parked p : parked.values()) tiles.add(aged(p, now));
            boolean known = KNOWN.contains(c);
            UNSAVED.remove(c);
            Set<String> away = AWAY.get(c);
            if (away != null) {
                for (SavedTile t : tiles) away.remove(faceKeyOf(t));
                if (away.isEmpty()) {
                    AWAY.remove(c);
                } else if (known) {
                    keep.computeIfAbsent(regionOf(c), k -> new HashMap<>()).put(c, Set.copyOf(away));
                }
            }
            if (tiles.isEmpty() && (!known || !REGIONS.contains(regionOf(c)))) continue;
            (known ? replace : merge).computeIfAbsent(regionOf(c), k -> new HashMap<>()).put(c, List.copyOf(tiles));
            if (!tiles.isEmpty()) REGIONS.add(regionOf(c));
        }
        Set<Long> regions = new HashSet<>(replace.keySet());
        regions.addAll(merge.keySet());
        for (long region : regions) {
            Path file = store().regionFile(worldKey, dimension, chunkX(region), chunkZ(region));
            Map<Long, List<SavedTile>> r = replace.getOrDefault(region, Map.of()), m = merge.getOrDefault(region, Map.of());
            Map<Long, Set<String>> k = keep.getOrDefault(region, Map.of());
            submit(st -> {
                try {
                    Path aside = st.update(file, r, m, k);
                    if (aside != null) {
                        RESULTS.add(() -> reportOnce(file, "kept aside as " + aside.getFileName(), null));
                    }
                } catch (IOException | RuntimeException e) {
                    RESULTS.add(() -> reportOnce(file, "write", e));
                }
            });
            com.simpleblood.Tally.count("stains.region.written");
        }
    }

    private static void reportOnce(Path file, String what, Exception e) {
        if (e instanceof StainCodec.UnsupportedStainVersionException) com.simpleblood.Tally.count("stains.file.newer");
        if (!REPORTED.add(file + what)) return;
        if (e instanceof StainCodec.UnsupportedStainVersionException) {
            SimpleBlood.LOGGER.warn("Simple Blood: saved blood in {} is {}; left as it is, and the blood there is not saved this time",
                    file, e.getMessage());
        } else if (e instanceof StainCodec.CorruptStainFileException) {
            SimpleBlood.LOGGER.warn("Simple Blood: saved blood in {} could not be read ({}); skipped", file, e.getMessage());
        } else if (e == null) {
            SimpleBlood.LOGGER.warn("Simple Blood: saved blood in {} could not be read; {}", file, what);
        } else {
            SimpleBlood.LOGGER.warn("Simple Blood: could not {} saved blood in {}: {}", what, file, e.toString());
        }
        com.simpleblood.Tally.count("stains.file.bad");
    }

    public static boolean settle(long millis) {
        if (io == null) return true;
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        io.execute(done::countDown);
        try {
            return done.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static Path regionFileForTests(BlockPos pos) {
        if (worldKey == null) return null;
        long region = regionOf(chunkOf(pos));
        return store().regionFile(worldKey, dimension, chunkX(region), chunkZ(region));
    }

    public static boolean worldHasBlood() {
        if (BloodSurfaces.tileCount() > 0 || !PARKED.isEmpty()) return true;
        Minecraft mc = Minecraft.getInstance();
        String w = worldKey != null ? worldKey : mc.level != null ? worldKeyOf(mc) : null;
        if (w == null) return false;
        Path dir = store().worldDir(w);
        if (!java.nio.file.Files.isDirectory(dir)) return false;
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(dir, 2)) {
            return walk.anyMatch(p -> p.getFileName().toString().endsWith(".bin"));
        } catch (IOException | java.io.UncheckedIOException e) {
            return true;
        }
    }

    public static int parkedCount() {
        int n = 0;
        for (Map<CanvasTile.Key, Parked> m : PARKED.values()) n += m.size();
        return n;
    }
}
