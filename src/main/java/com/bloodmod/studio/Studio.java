package com.bloodmod.studio;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodKind;
import com.bloodmod.BloodMod;
import com.bloodmod.BloodModAPI;
import com.bloodmod.BloodParticles;
import com.bloodmod.Chat;
import com.bloodmod.ClientBloodParticleSpawner;
import com.bloodmod.particle.BloodParticle;
import com.bloodmod.surface.BloodSurfaces;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Studio {

    private Studio() {}

    private static Boolean enabled;

    public static boolean enabled() {
        if (enabled == null) {
            enabled = Boolean.getBoolean("bloodmod.studio") || Studio.class.getResource("/bloodmod_studio.txt") != null;
        }
        return enabled;
    }

    public enum Type {
        DRIP,
        SPLASH,
        POUR,
        SPRAY,
        PUDDLE,
        FOG,
        RAIN;

        static Type parse(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return null;
        }

        int defaultAmount() {
            return switch (this) {
                case DRIP, FOG -> 1;
                case SPLASH -> 8;
                case POUR -> 3;
                case SPRAY -> 4;
                case PUDDLE -> 2;
                case RAIN -> 2;
            };
        }

        int defaultInterval() {
            return switch (this) {
                case DRIP -> 6;
                case SPLASH -> 30;
                case POUR -> 2;
                case SPRAY -> 20;
                case PUDDLE -> 5;
                case FOG -> 4;
                case RAIN -> 3;
            };
        }
    }

    public static final class Emitter {
        int id;
        Type type;
        double x, y, z;
        double dx, dy, dz;
        int interval;
        int amount;
        int colour;
        BloodKind kind;
        double radius;

        String describe() {
            return String.format(Locale.ROOT, "#%d %s at %.1f %.1f %.1f, every %d ticks, amount %d, %s #%06x%s",
                    id, type.name().toLowerCase(Locale.ROOT), x, y, z, interval, amount,
                    kind.name().toLowerCase(Locale.ROOT), colour, type == Type.RAIN ? String.format(Locale.ROOT, ", radius %.1f", radius) : "");
        }
    }

    private static final class Saved {
        List<Emitter> emitters = new ArrayList<>();
        int nextId = 1;
        boolean paused;
        int colour = 0x660303;
        BloodKind kind = BloodKind.LIQUID;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Saved state;
    private static int tick;
    private static final RandomSource RNG = RandomSource.create();

    private static Saved state() {
        if (state == null) load();
        return state;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        Saved s = state();
        if (s.paused || s.emitters.isEmpty()) return;
        tick++;
        for (Emitter e : s.emitters) {
            if (tick % Math.max(1, e.interval) == 0) fire(level, e);
        }
    }

    private static void fire(ClientLevel level, Emitter e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.distanceToSqr(e.x, e.y, e.z) > 96 * 96) return;
        prepare(e);
        try {
            switch (e.type) {
                case DRIP -> {
                    for (int i = 0; i < e.amount; i++) {
                        emit(level, BloodParticles.BLOOD_DROP, e.x, e.y - i * 0.1, e.z, 0, -0.02, 0);
                    }
                }
                case SPLASH -> {
                    for (int i = 0; i < e.amount; i++) {
                        double a = RNG.nextDouble() * Math.PI * 2, s = 0.12 + RNG.nextDouble() * 0.25;
                        emit(level, BloodParticles.BLOOD_SPLASH, e.x, e.y, e.z, Math.cos(a) * s, 0.1 + RNG.nextDouble() * 0.25, Math.sin(a) * s);
                    }
                }
                case POUR -> {
                    for (int i = 0; i < e.amount; i++) {
                        emit(level, BloodParticles.BLOOD_DROP, e.x, e.y - i * 0.08, e.z, 0, -0.05 - RNG.nextDouble() * 0.03, 0);
                    }
                }
                case SPRAY -> {
                    BloodParticle.setBallistic(true, 1.5f);
                    for (int i = 0; i < e.amount; i++) {
                        double s = 0.4 + RNG.nextDouble() * 0.3;
                        emit(level, i == 0 ? BloodParticles.BLOOD_STREAK : BloodParticles.BLOOD_SPLASH, e.x, e.y, e.z,
                                e.dx * s + jitter(0.08), e.dy * s + jitter(0.08), e.dz * s + jitter(0.08));
                    }
                    BloodParticle.setBallistic(false, 1.0f);
                }
                case PUDDLE -> {
                    Vec3 from = new Vec3(e.x + jitter(0.4), e.y + 0.5, e.z + jitter(0.4));
                    BloodSurfaces.Hit hit = BloodSurfaces.trace(level, from, from.add(0, -1.5, 0));
                    if (hit != null) BloodSurfaces.deposit(level, hit, e.colour, e.amount, new Vec3(0, -1, 0), e.kind == BloodKind.POWDER);
                }
                case FOG -> {
                    for (int i = 0; i < e.amount; i++) {
                        com.bloodmod.particle.BloodFogParticle.setCurrentBloodColor(new BloodColor.Color(e.colour));
                        mc.particleEngine.createParticle(BloodParticles.BLOOD_FOG, e.x + jitter(0.3), e.y + jitter(0.3), e.z + jitter(0.3), jitter(0.02), -0.01, jitter(0.02));
                    }
                }
                case RAIN -> {
                    for (int i = 0; i < e.amount; i++) {
                        double a = RNG.nextDouble() * Math.PI * 2, r = Math.sqrt(RNG.nextDouble()) * e.radius;
                        emit(level, BloodParticles.BLOOD_DRIP, e.x + Math.cos(a) * r, e.y, e.z + Math.sin(a) * r, 0, -0.05, 0);
                    }
                }
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
        }
    }

    private static void prepare(Emitter e) {
        BloodParticle.setCurrentBloodColor(new BloodColor.Color(e.colour));
        BloodParticle.setCurrentKind(e.kind);
        BloodParticle.setEntitySizeMultiplier(1.0f);
        BloodParticle.setPaintsSurfaces(true);
        BloodParticle.setShouldTransformToFog(e.kind == BloodKind.LIQUID);
        BloodParticle.setShouldDespawnInWater(e.kind == BloodKind.POWDER);
    }

    private static void emit(ClientLevel level, SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz) {
        ClientBloodParticleSpawner.emit(level, type, x, y, z, vx, vy, vz);
    }

    private static double jitter(double amount) {
        return (RNG.nextDouble() - 0.5) * 2 * amount;
    }

    private static Vec3 aim(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            var d = b.getDirection();
            return b.getLocation().add(d.getStepX() * 0.05, d.getStepY() * 0.05, d.getStepZ() * 0.05);
        }
        if (hit instanceof EntityHitResult eh) return eh.getLocation();
        if (mc.player == null) return Vec3.ZERO;
        return mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3));
    }

    private static LivingEntity aimedEntity(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static int add(Type type, Vec3 at, int interval, int amount, double radius) {
        Minecraft mc = Minecraft.getInstance();
        Saved s = state();
        Emitter e = new Emitter();
        e.id = s.nextId++;
        e.type = type;
        e.x = at.x; e.y = at.y; e.z = at.z;
        Vec3 look = mc.player != null ? mc.player.getLookAngle() : new Vec3(0, -1, 0);
        e.dx = look.x; e.dy = look.y; e.dz = look.z;
        e.interval = interval > 0 ? interval : type.defaultInterval();
        e.amount = amount > 0 ? amount : type.defaultAmount();
        e.colour = s.colour;
        e.kind = s.kind;
        e.radius = radius > 0 ? radius : 3;
        s.emitters.add(e);
        save();
        Chat.say("Added " + e.describe());
        return 1;
    }

    private static int remove(int id) {
        Saved s = state();
        boolean gone = s.emitters.removeIf(e -> e.id == id);
        save();
        Chat.say(gone ? "Removed #" + id : "No emitter #" + id);
        return gone ? 1 : 0;
    }

    private static int move(int id) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = aim(mc);
        for (Emitter e : state().emitters) {
            if (e.id != id) continue;
            e.x = at.x; e.y = at.y; e.z = at.z;
            Vec3 look = mc.player != null ? mc.player.getLookAngle() : new Vec3(0, -1, 0);
            e.dx = look.x; e.dy = look.y; e.dz = look.z;
            save();
            Chat.say("Moved " + e.describe());
            return 1;
        }
        Chat.say("No emitter #" + id);
        return 0;
    }

    private static int list() {
        Saved s = state();
        if (s.emitters.isEmpty()) {
            Chat.say("No emitters. /bloodstudio add <type> makes one where you look.");
            return 1;
        }
        Chat.say((s.paused ? "Paused. " : "") + s.emitters.size() + " emitter(s):");
        for (Emitter e : s.emitters) Chat.say("  " + e.describe());
        return 1;
    }

    private static int clear() {
        Saved s = state();
        int n = s.emitters.size();
        s.emitters.clear();
        save();
        Chat.say("Removed " + n + " emitter(s).");
        return 1;
    }

    private static int pause(boolean paused) {
        state().paused = paused;
        save();
        Chat.say(paused ? "Paused." : "Running.");
        return 1;
    }

    private static int colour(String hex) {
        try {
            int c = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
            state().colour = c;
            save();
            Chat.say(String.format(Locale.ROOT, "New emitters use #%06x.", c));
            return 1;
        } catch (NumberFormatException e) {
            Chat.say("Colour must be hex, e.g. 8b0000.");
            return 0;
        }
    }

    private static int kind(String name) {
        BloodKind k = BloodKind.parse(name);
        state().kind = k;
        save();
        Chat.say("New emitters make " + k.name().toLowerCase(Locale.ROOT) + ".");
        return 1;
    }

    private static int hit(float damage) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity target = aimedEntity(mc);
        if (target == null) {
            Chat.say("Look at a mob first.");
            return 0;
        }
        boolean did = BloodModAPI.spawnHit(target, damage);
        Chat.say(did ? "Hit burst on " + target.getName().getString() + "." : "That one does not bleed.");
        return did ? 1 : 0;
    }

    private static int death() {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity target = aimedEntity(mc);
        if (target == null) {
            Chat.say("Look at a mob first.");
            return 0;
        }
        boolean did = BloodModAPI.spawnDeath(target);
        Chat.say(did ? "Death burst on " + target.getName().getString() + "." : "That one does not bleed.");
        return did ? 1 : 0;
    }

    private static <S> SuggestionProvider<S> typeSuggestions() {
        return (ctx, b) -> {
            for (Type t : Type.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
            return b.buildFuture();
        };
    }

    private static <S> int addFromContext(CommandContext<S> ctx, boolean explicitPos, boolean hasInterval, boolean hasAmount, boolean hasRadius) {
        Type type = Type.parse(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            Chat.say("Types: drip, splash, pour, spray, puddle, fog, rain.");
            return 0;
        }
        Vec3 at = explicitPos
                ? new Vec3(DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"))
                : aim(Minecraft.getInstance());
        int interval = hasInterval ? IntegerArgumentType.getInteger(ctx, "interval") : 0;
        int amount = hasAmount ? IntegerArgumentType.getInteger(ctx, "amount") : 0;
        double radius = hasRadius ? DoubleArgumentType.getDouble(ctx, "radius") : 0;
        return add(type, at, interval, amount, radius);
    }

    private static <S> RequiredArgumentBuilder<S, String> addTail(boolean explicitPos) {
        return RequiredArgumentBuilder.<S, String>argument("type", StringArgumentType.word())
                .suggests(typeSuggestions())
                .executes(ctx -> addFromContext(ctx, explicitPos, false, false, false))
                .then(RequiredArgumentBuilder.<S, Integer>argument("interval", IntegerArgumentType.integer(1, 12000))
                        .executes(ctx -> addFromContext(ctx, explicitPos, true, false, false))
                        .then(RequiredArgumentBuilder.<S, Integer>argument("amount", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> addFromContext(ctx, explicitPos, true, true, false))
                                .then(RequiredArgumentBuilder.<S, Double>argument("radius", DoubleArgumentType.doubleArg(0.1, 64))
                                        .executes(ctx -> addFromContext(ctx, explicitPos, true, true, true)))));
    }

    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal("bloodstudio")
                .executes(ctx -> list())
                .then(LiteralArgumentBuilder.<S>literal("add").then(addTail(false)))
                .then(LiteralArgumentBuilder.<S>literal("at")
                        .then(RequiredArgumentBuilder.<S, Double>argument("x", DoubleArgumentType.doubleArg())
                                .then(RequiredArgumentBuilder.<S, Double>argument("y", DoubleArgumentType.doubleArg())
                                        .then(RequiredArgumentBuilder.<S, Double>argument("z", DoubleArgumentType.doubleArg())
                                                .then(addTail(true))))))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(ctx -> list()))
                .then(LiteralArgumentBuilder.<S>literal("remove")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> remove(IntegerArgumentType.getInteger(ctx, "id")))))
                .then(LiteralArgumentBuilder.<S>literal("move")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> move(IntegerArgumentType.getInteger(ctx, "id")))))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(ctx -> clear()))
                .then(LiteralArgumentBuilder.<S>literal("pause").executes(ctx -> pause(true)))
                .then(LiteralArgumentBuilder.<S>literal("resume").executes(ctx -> pause(false)))
                .then(LiteralArgumentBuilder.<S>literal("colour")
                        .then(RequiredArgumentBuilder.<S, String>argument("hex", StringArgumentType.word())
                                .executes(ctx -> colour(StringArgumentType.getString(ctx, "hex")))))
                .then(LiteralArgumentBuilder.<S>literal("color")
                        .then(RequiredArgumentBuilder.<S, String>argument("hex", StringArgumentType.word())
                                .executes(ctx -> colour(StringArgumentType.getString(ctx, "hex")))))
                .then(LiteralArgumentBuilder.<S>literal("kind")
                        .then(RequiredArgumentBuilder.<S, String>argument("kind", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (BloodKind k : BloodKind.values()) b.suggest(k.name().toLowerCase(Locale.ROOT));
                                    return b.buildFuture();
                                })
                                .executes(ctx -> kind(StringArgumentType.getString(ctx, "kind")))))
                .then(LiteralArgumentBuilder.<S>literal("hit")
                        .executes(ctx -> hit(6))
                        .then(RequiredArgumentBuilder.<S, Float>argument("damage", FloatArgumentType.floatArg(0.1f, 1000f))
                                .executes(ctx -> hit(FloatArgumentType.getFloat(ctx, "damage")))))
                .then(LiteralArgumentBuilder.<S>literal("death").executes(ctx -> death()));
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("bloodmod-studio.json");
    }

    private static void load() {
        state = new Saved();
        try {
            Path p = file();
            if (Files.exists(p)) {
                Saved read = GSON.fromJson(Files.readString(p), Saved.class);
                if (read != null) {
                    state = read;
                    if (state.emitters == null) state.emitters = new ArrayList<>();
                    state.emitters.removeIf(e -> e == null || e.type == null);
                    for (Emitter e : state.emitters) if (e.kind == null) e.kind = BloodKind.LIQUID;
                    if (state.kind == null) state.kind = BloodKind.LIQUID;
                }
            }
        } catch (Exception e) {
            BloodMod.LOGGER.warn("Simple Blood studio: could not read the emitters, starting empty", e);
        }
    }

    private static void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(state()));
        } catch (Exception e) {
            BloodMod.LOGGER.warn("Simple Blood studio: could not save the emitters", e);
        }
    }
}
