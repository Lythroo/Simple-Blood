package com.bloodmod;

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

public final class Guard {

    public enum Part {
        HITS("hit and death detection", true),
        PARTICLES("blood particles", true),
        SURFACES("blood on blocks", true),
        SCREEN("the settings screen", true),
        API("the developer API", false),
        OTHER("Simple Blood", false);

        public final String label;
        final boolean fuse;

        Part(String label, boolean fuse) {
            this.label = label;
            this.fuse = fuse;
        }
    }

    public static final String ISSUES_URL = "https://github.com/Lythroo/Simple-Blood/issues";
    private static final String REPORT_FILE = "logs/simpleblood-error.txt";
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
    private static final Queue<Component> PENDING = new ConcurrentLinkedQueue<>();
    private static final List<String> REPORTS = new ArrayList<>();
    private static volatile boolean closeScreen;

    private Guard() {}

    public static boolean ok(Part part) {
        return !OFF[part.ordinal()].get();
    }

    public static void run(Part part, String doing, Runnable task) {
        if (!ok(part)) return;
        try {
            task.run();
        } catch (Throwable t) {
            fail(part, doing, t);
        }
    }

    public static <T> T call(Part part, String doing, Supplier<T> task, T fallback) {
        if (!ok(part)) return fallback;
        try {
            return task.get();
        } catch (Throwable t) {
            fail(part, doing, t);
            return fallback;
        }
    }

    public static void fail(Part part, String doing, Throwable t) {
        if (t instanceof OutOfMemoryError oom) throw oom;
        try {
            int i = part.ordinal();
            if (part.fuse) OFF[i].set(true);
            if (part == Part.SCREEN) closeScreen = true;
            String report = report(part, doing, t);
            long now = System.currentTimeMillis();
            long last = LAST_LOG[i].get();
            if (now - last >= LOG_INTERVAL_MS && LAST_LOG[i].compareAndSet(last, now)) {
                BloodMod.LOGGER.error("Simple Blood: error in {} ({}). The game keeps running; {} is now off. Full report:\n{}",
                        part.label, doing, part.fuse ? "that part" : "nothing", report);
            } else {
                BloodMod.LOGGER.warn("Simple Blood: another error in {} ({}): {}", part.label, doing, t.toString());
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
        }
    }

    public static void reset() {
        for (AtomicBoolean off : OFF) off.set(false);
    }

    public static void retryScreen() {
        OFF[Part.SCREEN.ordinal()].set(false);
        closeScreen = false;
    }

    public static void tick(Minecraft mc) {
        try {
            selfTest(mc);
            if (closeScreen) {
                closeScreen = false;
                if (com.bloodmod.gui.Screens.current(mc) instanceof com.bloodmod.gui.BloodConfigScreen screen) {
                    screen.abandon();
                }
            }
            if (mc.player == null || PENDING.isEmpty()) return;
            Component line;
            while ((line = PENDING.poll()) != null) {
                Chat.say(line);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean tested;

    private static void selfTest(Minecraft mc) {
        if (tested || mc.player == null) return;
        String want = System.getProperty("bloodmod.testError");
        if (want == null) return;
        tested = true;
        for (Part p : Part.values()) {
            if (p.name().equalsIgnoreCase(want)) {
                fail(p, "a test error (-Dbloodmod.testError)", new IllegalStateException("Test error, nothing is actually wrong"));
            }
        }
    }

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
                    : " is off until the game restarts or /bloodmod reset\n");
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

    private static String settings() {
        BloodModConfig cfg = BloodModClient.getConfig();
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
        }
    }

    private static List<Component> message(Part part, String report) {
        MutableComponent head = Component.literal("Simple Blood: error in " + part.label + ". ").withStyle(ChatFormatting.RED);
        if (part == Part.SCREEN) {
            head.append(Component.literal("The screen was closed; you can open it again.").withStyle(ChatFormatting.GRAY));
        } else if (part.fuse) {
            head.append(Component.literal("That part is off until you restart or run /bloodmod reset.").withStyle(ChatFormatting.GRAY));
        } else {
            head.append(Component.literal("Everything else keeps running.").withStyle(ChatFormatting.GRAY));
        }
        Component copyTip = Component.literal("Copies the full error report (also in " + REPORT_FILE + ")");
        Component openTip = Component.literal(ISSUES_URL);
        //? if 1.21.1 {
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

    private static void toast() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player != null) return;
            Component title = Component.literal("Simple Blood: error");
            Component text = Component.literal("See " + REPORT_FILE);
            SystemToast.SystemToastId id = new SystemToast.SystemToastId(8000L);
            //? if 1.21.1 {
            /*SystemToast.add(mc.getToasts(), id, title, text);
            *///?} elif <26.2 {
            SystemToast.add(mc.getToastManager(), id, title, text);
            //?} else {
            /*SystemToast.add(mc.gui.toastManager(), id, title, text);
            *///?}
        } catch (Throwable ignored) {
        }
    }
}
