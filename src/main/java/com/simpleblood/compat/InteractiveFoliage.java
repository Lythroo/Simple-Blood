package com.simpleblood.compat;

import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InteractiveFoliage {
    private InteractiveFoliage() {}

    private static boolean looked;
    private static Method contains, interactive;
    private static final Map<Block, Boolean> SWAYS = new ConcurrentHashMap<>();

    public static boolean sways(BlockState state) {
        if (!looked) lookUp();
        if (contains == null && interactive == null) return false;
        return SWAYS.computeIfAbsent(state.getBlock(), InteractiveFoliage::ask);
    }

    private static synchronized void lookUp() {
        if (looked) return;
        if (Platform.modIds().contains("mc2_interactivefoliage")) {
            try {
                contains = Class.forName("net.karto.mc2.mc2_interactivefoliage.WavingWhitelist", false, InteractiveFoliage.class.getClassLoader())
                        .getMethod("contains", Block.class);
                SimpleBlood.LOGGER.info("Interactive Foliage found: the plants it moves take no blood");
            } catch (Throwable t) {
                contains = null;
                SimpleBlood.LOGGER.warn("Interactive Foliage found, but not a version Simple Blood knows; blood may stay put on its moving plants ({})", t.toString());
            }
        }
        if (Platform.modIds().contains("sway")) {
            try {
                interactive = Class.forName("com.github.razorplay01.sway.api.SwayAPI", false, InteractiveFoliage.class.getClassLoader())
                        .getMethod("isInteractive", Block.class);
                SimpleBlood.LOGGER.info("SWAY found: the plants it bends take no blood");
            } catch (Throwable t) {
                interactive = null;
                SimpleBlood.LOGGER.warn("SWAY found, but not a version Simple Blood knows; blood may stay put on the plants it bends ({})", t.toString());
            }
        }
        looked = true;
    }

    private static boolean ask(Block block) {
        return asks(contains, block) || asks(interactive, block);
    }

    private static boolean asks(Method m, Block block) {
        if (m == null) return false;
        try {
            return Boolean.TRUE.equals(m.invoke(null, block));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clear() {
        SWAYS.clear();
    }
}
