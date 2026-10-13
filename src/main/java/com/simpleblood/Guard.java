package com.simpleblood;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Keeps the game alive when something in this mod breaks. Every way into the mod (the render
 * hooks, the ticks, the particles, the mixins, the settings screen, the API) runs through
 * here: an exception is caught, the part it came from is switched off, a report with the
 * full stack trace goes to the log, to {@code logs/simpleblood-error.txt} and into the chat
 * as a click-to-copy message, and the game carries on without that part. Other mods can
 * reach us from any thread, so everything here is thread-safe, and nothing here throws.
 */
public final class Guard {

    /** The parts of the mod that can be switched off on their own. */
    public enum Part {
        HITS("hit and death detection", true),
        PARTICLES("blood particles", true),
        SURFACES("blood on blocks", true),
        SCREEN("the settings screen", true),
        API("the developer API", false),
        OTHER("Simple Blood", false);

        public final String label;
        /** Whether a failure switches the part off until {@link #reset} (the screen: until it is opened again). */
        final boolean fuse;

        Part(String label, boolean fuse) {
            this.label = label;
            this.fuse = fuse;
        }
    }

    public static final String ISSUES_URL = "https://github.com/Lythroo/Simple-Blood/issues";
    private static final String REPORT_FILE = "logs/simpleblood-error.txt";
    /** Full reports for the same part are logged at most this often; the rest get one line. */
    private static final long LOG_INTERVAL_MS = 10_000;

    private static final AtomicBoolean[] OFF = new AtomicBoolean[Part.values().length];
    private static final AtomicBoolean[] TOLD = new AtomicBoolean[Part.values().length];
    private static final AtomicLong[] LAST_LOG = new AtomicLong[Part.values().length];
    static {
        for (int i = 0; i < OFF.length; i++) {
            OFF[i] = new AtomicBoolean();
            TOLD[i] = new AtomicBoolean();
            LAST_LOG[i] = new AtomicLong(Long.MIN_VALUE / 2);
        }
    }
    /** Chat lines waiting for a player to show them to. */
    private static final Queue<Component> PENDING = new ConcurrentLinkedQueue<>();
    /** Every report of this session, for the file. */
    private static final List<String> REPORTS = new ArrayList<>();
    private static volatile boolean closeScreen;

    private Guard() {}

    /** Whether a part is still allowed to run. */
    public static boolean ok(Part part) {
        return !OFF[part.ordinal()].get();
    }

    /** Runs {@code task} unless its part is off; a throw switches the part off and reports it. */
    public static void run(Part part, String doing, Runnable task) {
        if (!ok(part)) return;
        try {
            task.run();
        } catch (Throwable t) {
            fail(part, doing, t);
        }
    }

    /** Like {@link #run}, returning {@code fallback} when the part is off or the task throws. */
    public static <T> T call(Part part, String doing, Supplier<T> task, T fallback) {
        if (!ok(part)) return fallback;
        try {
            return task.get();
        } catch (Throwable t) {
            fail(part, doing, t);
            return fallback;
        }
    }

    /**
     * Records a failure: switches the part off when it is one that can be, writes the report
     * everywhere and tells the player once per part and session.
     *
     * @param doing what was going on, e.g. "drawing blood on blocks"
     */
    public static void fail(Part part, String doing, Throwable t) {
        if (t instanceof OutOfMemoryError oom) throw oom; // nothing sensible left to do
        try {
            int i = part.ordinal();
            if (part.fuse) OFF[i].set(true);
            if (part == Part.SCREEN) closeScreen = true;
            String report = report(part, doing, t);
            long now = System.currentTimeMillis();
            long last = LAST_LOG[i].get();
            if (now - last >= LOG_INTERVAL_MS && LAST_LOG[i].compareAndSet(last, now)) {
                SimpleBlood.LOGGER.error("Simple Blood: error in {} ({}). The game keeps running; {} is now off. Full report:\n{}",
                        part.label, doing, part.fuse ? "that part" : "nothing", report);
            } else {
                SimpleBlood.LOGGER.warn("Simple Blood: another error in {} ({}): {}", part.label, doing, t.toString());
            }
            synchronized (REPORTS) {
                REPORTS.add(report);
                if (REPORTS.size() > 20) REPORTS.remove(0);
            }
            writeFile();
            if (!TOLD[i].getAndSet(true)) {
                PENDING.addAll(message(part, report));
                RenderThread.run(Guard::toast);
            }
        } catch (Throwable ignored) {
            // The guard itself must never take the game down.
        }
    }

    /** Puts every part back into service (a new world, or {@code /simpleblood reset}). */
    public static void reset() {
        for (AtomicBoolean off : OFF) off.set(false);
    }

    /** The settings screen was opened by hand: let it try again. */
    public static void retryScreen() {
        OFF[Part.SCREEN.ordinal()].set(false);
        closeScreen = false;
    }

    /** From the client tick: hands waiting chat lines to the player, closes a broken screen. */
    public static void tick(Minecraft mc) {
        try {
            selfTest(mc);
            if (closeScreen) {
                closeScreen = false;
                if (com.simpleblood.gui.Screens.current(mc) instanceof com.simpleblood.gui.BloodConfigScreen screen) {
                    screen.abandon();
                }
            }
            if (mc.player == null || PENDING.isEmpty()) return;
            Component line;
            while ((line = PENDING.poll()) != null) {
                Chat.say(line);
            }
        } catch (Throwable ignored) {
            // Never from here.
        }
    }

    private static boolean tested;

    /** {@code -Dsimpleblood.testError=surfaces} (or hits, particles, screen) raises one made-up error once a player exists, to see all of this at work. */
    private static void selfTest(Minecraft mc) {
        if (tested || mc.player == null) return;
        String want = System.getProperty("simpleblood.testError");
        if (want == null) return;
        tested = true;
        for (Part p : Part.values()) {
            if (p.name().equalsIgnoreCase(want)) {
                fail(p, "a test error (-Dsimpleblood.testError)", new IllegalStateException("Test error, nothing is actually wrong"));
            }
        }
    }

    // ------------------------------------------------------------------ the report

    private static String report(Part part, String doing, Throwable t) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("Simple Blood error report\n");
        sb.append("Mod: Simple Blood ").append(safe(Platform::modVersion)).append(" (").append(safe(Platform::loader)).append(")\n");
        sb.append("Minecraft: ").append(safe(Platform::minecraftVersion))
                .append(", Java ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vendor")).append("), ")
                .append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append('\n');
        sb.append("Where: ").append(part.label).append(", ").append(doing).append('\n');
        sb.append("Thread: ").append(Thread.currentThread().getName()).append('\n');
        sb.append("When: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        if (part.fuse) {
            sb.append("Effect: ").append(part.label).append(part == Part.SCREEN
                    ? " was closed; it can be opened again\n"
                    : " is off until the game restarts or /simpleblood reset\n");
        }
        sb.append('\n').append(stackTrace(t)).append('\n');
        sb.append("Settings: ").append(safe(Guard::settings)).append('\n');
        List<String> mods = safe(Platform::modIds, List.of());
        sb.append("Mods (").append(mods.size()).append("): ").append(String.join(", ", mods)).append('\n');
        sb.append("\nPlease post this at ").append(ISSUES_URL).append('\n');
        return sb.toString();
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString().replace("\r\n", "\n").stripTrailing();
    }

    /** The settings without the two mob tables, on one line. */
    private static String settings() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null) return "(none)";
        JsonObject o = new Gson().toJsonTree(cfg).getAsJsonObject();
        o.remove("vanillaEntities");
        o.remove("moddedEntities");
        return o.toString();
    }

    private static String safe(Supplier<String> s) {
        return safe(s, "?");
    }

    private static <T> T safe(Supplier<T> s, T fallback) {
        try {
            T v = s.get();
            return v == null ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void writeFile() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return;
            Path path = mc.gameDirectory.toPath().resolve(REPORT_FILE);
            Files.createDirectories(path.getParent());
            String all;
            synchronized (REPORTS) {
                all = String.join("\n\n----------------------------------------\n\n", REPORTS);
            }
            Files.writeString(path, all + "\n");
        } catch (Throwable ignored) {
            // The log has it.
        }
    }

    // ------------------------------------------------------------------ telling the player

    private static List<Component> message(Part part, String report) {
        MutableComponent head = Component.literal("Simple Blood: error in " + part.label + ". ").withStyle(ChatFormatting.RED);
        if (part == Part.SCREEN) {
            head.append(Component.literal("The screen was closed; you can open it again.").withStyle(ChatFormatting.GRAY));
        } else if (part.fuse) {
            head.append(Component.literal("That part is off until you restart or run /simpleblood reset.").withStyle(ChatFormatting.GRAY));
        } else {
            head.append(Component.literal("Everything else keeps running.").withStyle(ChatFormatting.GRAY));
        }
        Component copyTip = Component.literal("Copies the full error report (also in " + REPORT_FILE + ")");
        Component openTip = Component.literal(ISSUES_URL);
        //? if <=1.21.1 {
        /*Style copy = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, report))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, copyTip));
        Style open = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ISSUES_URL))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, openTip));
        *///?} else {
        Style copy = Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard(report))
                .withHoverEvent(new HoverEvent.ShowText(copyTip));
        Style open = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(ISSUES_URL)))
                .withHoverEvent(new HoverEvent.ShowText(openTip));
        //?}
        MutableComponent links = Component.literal("[Copy error]").withStyle(copy.withColor(ChatFormatting.YELLOW).withUnderlined(true))
                .append(Component.literal("  "))
                .append(Component.literal("[Report on GitHub]").withStyle(open.withColor(ChatFormatting.AQUA).withUnderlined(true)));
        List<Component> lines = new ArrayList<>();
        lines.add(head);
        lines.add(links);
        return lines;
    }

    /** A small toast as well, for errors outside a world (the title screen, the settings screen). */
    private static void toast() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player != null) return; // the chat has it
            Component title = Component.literal("Simple Blood: error");
            Component text = Component.literal("See " + REPORT_FILE);
            //? if 1.20.1 {
            /*SystemToast.add(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, text);
            *///?} elif 1.21.1 {
            /*SystemToast.add(mc.getToasts(), new SystemToast.SystemToastId(8000L), title, text);
            *///?} elif <26.2 {
            SystemToast.add(mc.getToastManager(), new SystemToast.SystemToastId(8000L), title, text);
            //?} else {
            /*SystemToast.add(mc.gui.toastManager(), new SystemToast.SystemToastId(8000L), title, text);
            *///?}
        } catch (Throwable ignored) {
            // Never from here.
        }
    }
}
