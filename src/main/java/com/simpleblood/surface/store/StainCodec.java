package com.simpleblood.surface.store;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

public final class StainCodec {

    public static final int VERSION = 1;

    private static final byte[] MAGIC = "SBST".getBytes(StandardCharsets.US_ASCII);
    private static final int END = 0x454E4421;

    private static final int MAX_CHUNKS = 32 * 32;
    private static final int MAX_TILES_PER_CHUNK = 1 << 16;
    private static final int MAX_SIDE = 64;
    private static final int MAX_BLOCK_ID = 256;

    @FunctionalInterface
    public interface PayloadReader {
        Map<Long, List<SavedTile>> read(DataInputStream payload) throws IOException;
    }

    public static final Map<Integer, PayloadReader> MIGRATIONS = Map.of();

    private StainCodec() {}

    public static byte[] encode(Map<Long, List<SavedTile>> chunks) throws IOException {
        if (chunks.size() > MAX_CHUNKS) throw new IllegalArgumentException("more chunks than a region holds: " + chunks.size());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        DataOutputStream head = new DataOutputStream(bytes);
        head.write(MAGIC);
        head.writeInt(VERSION);
        head.flush();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater, 8192))) {
            out.writeInt(chunks.size());
            for (Map.Entry<Long, List<SavedTile>> e : chunks.entrySet()) {
                out.writeLong(e.getKey());
                out.writeInt(e.getValue().size());
                for (SavedTile t : e.getValue()) writeTile(out, t);
            }
            out.writeInt(END);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    private static void writeTile(DataOutputStream out, SavedTile t) throws IOException {
        SavedTile.Where w = t.where();
        out.writeLong(w.pos());
        out.writeByte(w.face());
        out.writeInt(w.box());
        out.writeByte(w.hitFace());
        out.writeUTF(w.block());
        out.writeDouble(w.cx());
        out.writeDouble(w.cy());
        out.writeDouble(w.cz());
        out.writeFloat(w.ox());
        out.writeFloat(w.oy());
        out.writeFloat(w.oz());
        out.writeFloat(w.w());
        out.writeFloat(w.h());
        out.writeShort(w.pw());
        out.writeShort(w.ph());
        SavedTile.Pixels p = t.pixels();
        out.writeInt(p.count());
        for (int i = 0; i < p.count(); i++) {
            out.writeShort(p.index()[i]);
            out.writeByte(p.tone()[i]);
            out.writeByte(p.depth()[i]);
            out.writeByte(p.thin()[i]);
            out.writeByte(p.flags()[i]);
            out.writeInt(p.rgb()[i]);
            out.writeInt(p.elapsed()[i]);
            out.writeInt(p.life()[i]);
        }
    }

    public static Map<Long, List<SavedTile>> decode(InputStream in) throws IOException {
        return decode(in, MIGRATIONS);
    }

    public static Map<Long, List<SavedTile>> decode(InputStream in, Map<Integer, PayloadReader> older) throws IOException {
        DataInputStream head = new DataInputStream(in);
        byte[] magic = new byte[MAGIC.length];
        int version;
        try {
            head.readFully(magic);
            version = head.readInt();
        } catch (EOFException e) {
            throw new CorruptStainFileException("too short for a header", e);
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (magic[i] != MAGIC[i]) throw new CorruptStainFileException("not a Simple Blood stain file");
        }
        PayloadReader reader;
        if (version == VERSION) {
            reader = StainCodec::readCurrent;
        } else if (version > VERSION) {
            throw new UnsupportedStainVersionException(version);
        } else {
            reader = older.get(version);
            if (reader == null) throw new CorruptStainFileException("unknown old version " + version);
        }
        try (DataInputStream payload = new DataInputStream(new InflaterInputStream(in))) {
            Map<Long, List<SavedTile>> chunks = reader.read(payload);
            if (payload.read() != -1) throw new CorruptStainFileException("data after the end mark");
            return chunks;
        } catch (EOFException e) {
            throw new CorruptStainFileException("cut short", e);
        } catch (ZipException e) {
            throw new CorruptStainFileException("damaged compression", e);
        } catch (java.io.UTFDataFormatException e) {
            throw new CorruptStainFileException("damaged block id", e);
        } catch (IOException e) {
            if (e.getCause() instanceof DataFormatException) throw new CorruptStainFileException("damaged compression", e);
            throw e;
        }
    }

    private static Map<Long, List<SavedTile>> readCurrent(DataInputStream in) throws IOException {
        int chunks = in.readInt();
        check(chunks >= 0 && chunks <= MAX_CHUNKS, "chunk count " + chunks);
        Map<Long, List<SavedTile>> out = new LinkedHashMap<>();
        for (int c = 0; c < chunks; c++) {
            long key = in.readLong();
            int tiles = in.readInt();
            check(tiles >= 0 && tiles <= MAX_TILES_PER_CHUNK, "tile count " + tiles);
            List<SavedTile> list = new ArrayList<>(Math.min(tiles, 256));
            for (int t = 0; t < tiles; t++) list.add(readTile(in));
            check(out.put(key, Collections.unmodifiableList(list)) == null, "chunk listed twice");
        }
        check(in.readInt() == END, "no end mark");
        return out;
    }

    private static SavedTile readTile(DataInputStream in) throws IOException {
        long pos = in.readLong();
        int face = in.readByte();
        int box = in.readInt();
        int hitFace = in.readByte();
        String block = in.readUTF();
        check(face >= 0 && face < 6 && hitFace >= 0 && hitFace < 6, "face " + face + "/" + hitFace);
        check(!block.isEmpty() && block.length() <= MAX_BLOCK_ID, "block id");
        double cx = in.readDouble(), cy = in.readDouble(), cz = in.readDouble();
        float ox = in.readFloat(), oy = in.readFloat(), oz = in.readFloat(), w = in.readFloat(), h = in.readFloat();
        check(Double.isFinite(cx) && Double.isFinite(cy) && Double.isFinite(cz)
                && Float.isFinite(ox) && Float.isFinite(oy) && Float.isFinite(oz) && Float.isFinite(w) && Float.isFinite(h), "geometry");
        int pw = in.readShort(), ph = in.readShort();
        check(pw >= 1 && pw <= MAX_SIDE && ph >= 1 && ph <= MAX_SIDE, "size " + pw + "x" + ph);
        int n = in.readInt();
        check(n >= 0 && n <= pw * ph, "pixel count " + n);
        short[] index = new short[n];
        byte[] tone = new byte[n], depth = new byte[n], thin = new byte[n], flags = new byte[n];
        int[] rgb = new int[n], elapsed = new int[n], life = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = in.readShort();
            tone[i] = in.readByte();
            depth[i] = in.readByte();
            thin[i] = in.readByte();
            flags[i] = in.readByte();
            rgb[i] = in.readInt();
            elapsed[i] = in.readInt();
            life[i] = in.readInt();
            check(index[i] >= 0 && index[i] < pw * ph, "pixel index " + index[i]);
            check(tone[i] >= 1 && tone[i] <= 3 && depth[i] >= 1 && thin[i] >= 0 && thin[i] <= 3, "pixel values");
            check(elapsed[i] >= 0 && (life[i] & ~SavedTile.Pixels.FOREVER_BIT) > 0, "pixel age");
        }
        return new SavedTile(new SavedTile.Where(pos, face, box, hitFace, block, cx, cy, cz, ox, oy, oz, w, h, pw, ph),
                new SavedTile.Pixels(index, tone, depth, thin, flags, rgb, elapsed, life));
    }

    private static void check(boolean ok, String what) throws CorruptStainFileException {
        if (!ok) throw new CorruptStainFileException("bad " + what);
    }

    public static class CorruptStainFileException extends IOException {
        public CorruptStainFileException(String message) {
            super(message);
        }

        public CorruptStainFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class UnsupportedStainVersionException extends CorruptStainFileException {
        public final int version;

        public UnsupportedStainVersionException(int version) {
            super("written by a newer Simple Blood (format " + version + ", this one reads up to " + VERSION + ")");
            this.version = version;
        }
    }
}
