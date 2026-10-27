package com.sagoma.planimetria.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightProfileTest {
    /** Flusso della forma normalizzata (∫ forma · sinθ dθ su 0..π): 2 come una luce uguale in tutte le direzioni. */
    private fun flux(p: LightProfile): Double {
        val n = 2000
        return (0 until n).sumOf { k -> val t = PI * (k + 0.5) / n; p.norm * p.shape(cos(t)) * sin(t) * PI / n }
    }

    @Test
    fun `ogni forma ha lo stesso flusso di una lampadina nuda, salvo il faretto che ha un massimo`() {
        for (p in LightProfile.entries) {
            if (p.norm < LightProfile.MAX_NORM) assertEquals(2.0, flux(p), 0.01, p.name)
        }
        assertEquals(LightProfile.MAX_NORM, LightProfile.Spot.norm)
    }

    @Test
    fun `il faretto illumina nel cono e quasi niente fuori, la plafoniera niente verso il soffitto`() {
        val spot = LightProfile.Spot
        assertTrue(spot.shape(1.0) > 0.99 && spot.shape(cos(60 * PI / 180)) < 0.05)
        assertTrue(LightProfile.Downlight.shape(-1.0) < 0.05 && LightProfile.Downlight.shape(1.0) > 0.99)
        // Il paralume fa luce sopra e sotto più che ai lati.
        assertTrue(LightProfile.Shade.shape(1.0) > 3 * LightProfile.Shade.shape(0.0) && LightProfile.Shade.shape(-1.0) > 3 * LightProfile.Shade.shape(0.0))
    }

    @Test
    fun `la funzione dello shader ha un ramo per ogni forma`() {
        val f = LightProfile.glslFunction()
        for (p in LightProfile.entries) assertTrue(p.glsl in f, p.name)
        assertEquals(LightProfile.entries.size - 1, Regex("if \\(p < ").findAll(f).count())
    }
}
