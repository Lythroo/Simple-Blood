package com.simpleblood;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Tally {

    private Tally() {}

    public static final boolean ON = System.getProperty("simpleblood.selftest") != null;

    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();

    public static void count(String what) {
        if (ON) COUNTS.computeIfAbsent(what, k -> new AtomicLong()).incrementAndGet();
    }

    public static void add(String what, long n) {
        if (ON && n > 0) COUNTS.computeIfAbsent(what, k -> new AtomicLong()).addAndGet(n);
    }

    public static long get(String what) {
        AtomicLong n = COUNTS.get(what);
        return n == null ? 0 : n.get();
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> out = new TreeMap<>();
        COUNTS.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
