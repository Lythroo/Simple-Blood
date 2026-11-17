package com.simpleblood.geom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PolygonFormsTest {

    private static final float[] RAGDOLL_LEG = {
            2, 12, -2, 0.0625f, 0.5f,
            2, 12, 2, 0f, 0.5f,
            2, 0, 2, 0f, 0.3125f,
            2, 0, -2, 0.0625f, 0.3125f};

    private static final float[] PACK_LEG = {
            -4, -12, -2, 0.0625f, 0.3125f,
            -4, -12, 2, 0f, 0.3125f,
            -4, 0, 2, 0f, 0.5f,
            -4, 0, -2, 0.0625f, 0.5f};

    @Test
    void movedAndReorderedCopiesAgree() {
        assertEquals(PolygonForms.form(RAGDOLL_LEG), PolygonForms.form(PACK_LEG));
        assertEquals(PolygonForms.textureForm(RAGDOLL_LEG), PolygonForms.textureForm(PACK_LEG));
    }

    @Test
    void reversedCornerOrderStillAgrees() {
        float[] reversed = new float[20];
        for (int k = 0; k < 4; k++) System.arraycopy(RAGDOLL_LEG, (3 - k) * 5, reversed, k * 5, 5);
        assertEquals(PolygonForms.form(RAGDOLL_LEG), PolygonForms.form(reversed));
    }

    @Test
    void aMirroredTextureIsAnotherForm() {
        float[] mirrored = RAGDOLL_LEG.clone();
        mirrored[3] = 0f;
        mirrored[8] = 0.0625f;
        mirrored[13] = 0.0625f;
        mirrored[18] = 0f;
        assertNotEquals(PolygonForms.form(RAGDOLL_LEG), PolygonForms.form(mirrored));
    }

    @Test
    void aBiggerCubeHasAnotherFormButTheSameTextureForm() {
        float[] small = {4.25f, -8.25f, 4.25f, 0.75f, 0f, -4.25f, -8.25f, 4.25f, 0.625f, 0f,
                -4.25f, -8.25f, -4.25f, 0.625f, 0.125f, 4.25f, -8.25f, -4.25f, 0.75f, 0.125f};
        float[] big = {4.5f, -8.5f, 4.5f, 0.75f, 0f, -4.5f, -8.5f, 4.5f, 0.625f, 0f,
                -4.5f, -8.5f, -4.5f, 0.625f, 0.125f, 4.5f, -8.5f, -4.5f, 0.75f, 0.125f};
        assertNotEquals(PolygonForms.form(small), PolygonForms.form(big));
        assertEquals(PolygonForms.textureForm(small), PolygonForms.textureForm(big));
    }

    @Test
    void anotherTextureSpotIsAnotherForm() {
        float[] elsewhere = RAGDOLL_LEG.clone();
        for (int k = 0; k < 4; k++) elsewhere[k * 5 + 3] += 0.25f;
        assertNotEquals(PolygonForms.form(RAGDOLL_LEG), PolygonForms.form(elsewhere));
        assertNotEquals(PolygonForms.textureForm(RAGDOLL_LEG), PolygonForms.textureForm(elsewhere));
    }

    @Test
    void neverZero() {
        assertNotEquals(0L, PolygonForms.form(new float[20]));
        assertNotEquals(0L, PolygonForms.textureForm(new float[20]));
    }
}
