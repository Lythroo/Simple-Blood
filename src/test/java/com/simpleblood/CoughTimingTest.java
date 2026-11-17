package com.simpleblood;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoughTimingTest {

    @Test
    void noCoughingAboveTheThreshold() {
        assertTrue(CoughTiming.severity(10f, 20f, 0.3f) < 0f);
        assertTrue(CoughTiming.severity(5f, 0f, 0.3f) < 0f);
        assertTrue(CoughTiming.severity(1f, 20f, 0f) < 0f);
    }

    @Test
    void severityRunsFromThresholdToDeath() {
        assertEquals(0f, CoughTiming.severity(6f, 20f, 0.3f), 1e-6f);
        assertEquals(0.5f, CoughTiming.severity(3f, 20f, 0.3f), 1e-6f);
        assertEquals(1f, CoughTiming.severity(0f, 20f, 0.3f), 1e-6f);
    }

    @Test
    void worseMeansMoreOften() {
        int mild = CoughTiming.gapTicks(0f, 1f, 0.5f);
        int bad = CoughTiming.gapTicks(1f, 1f, 0.5f);
        assertEquals(CoughTiming.SLOWEST_GAP_TICKS, mild);
        assertEquals(CoughTiming.FASTEST_GAP_TICKS, bad);
        assertTrue(CoughTiming.gapTicks(0.5f, 2f, 0.5f) < CoughTiming.gapTicks(0.5f, 1f, 0.5f));
    }

    @Test
    void gapNeverBelowTheFloor() {
        assertEquals(CoughTiming.MIN_GAP_TICKS, CoughTiming.gapTicks(1f, 100f, 0f));
        assertTrue(CoughTiming.gapTicks(1f, 0f, 0f) > 0);
    }

    @Test
    void dropsGrowWithSeverityAndAmount() {
        assertEquals(0, CoughTiming.drops(1f, 0f, 0.5f));
        assertEquals(3, CoughTiming.drops(0f, 1f, 0.5f));
        assertEquals(7, CoughTiming.drops(1f, 1f, 0.5f));
        assertEquals(1, CoughTiming.drops(0f, 0.05f, 0f));
        assertTrue(CoughTiming.drops(1f, 3f, 0.5f) > CoughTiming.drops(1f, 1f, 0.5f));
    }
}
