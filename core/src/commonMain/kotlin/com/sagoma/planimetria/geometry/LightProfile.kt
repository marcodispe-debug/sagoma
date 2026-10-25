package com.sagoma.planimetria.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Forma della luce di una lampada, come i profili fotometrici (IES) dei produttori: quanta luce esce in ogni direzione
 * rispetto all'asse della lampada ([Scene3D.SceneLight.axis]: in basso per le lampade a soffitto e da tavolo, verso la
 * stanza per quelle a parete, dove punta per i faretti). Le forme ricalcano i profili tipici di ogni tipo di lampada.
 *
 * [shape] e [glsl] sono la stessa formula (in Kotlin e nello shader del computer), in funzione di `c`, il coseno
 * dell'angolo tra l'asse e la direzione della luce. [norm] riporta il flusso totale a quello di una luce uguale in
 * tutte le direzioni: un faretto concentra i suoi lumen in un cono e lì illumina molto di più.
 */
enum class LightProfile(val glsl: String) {
    /** Uguale in tutte le direzioni (lampadina nuda). */
    Uniform("1.0"),
    /** Faretto: fascio di circa 40° con un filo di luce attorno. */
    Spot("(0.02 + 0.98 * smoothstep(0.82, 0.96, c))"),
    /** Plafoniera e incasso con diffusore: tutta verso il basso, più forte in verticale, niente verso il soffitto. */
    Downlight("(max(c, 0.0) * 0.96 + 0.04)"),
    /** Lampadario: un po' più verso il basso, ma anche ai lati e sul soffitto. */
    Pendant("(0.6 + 0.4 * c)"),
    /** Paralume (abat-jour, lampada da terra): luce sopra e sotto dalle aperture, attenuata ai lati dalla stoffa. */
    Shade("(0.22 + 0.78 * pow(abs(c), 1.5))"),
    /** Applique e plafoniera a parete: verso la stanza, niente dietro. */
    Wall("(max(c, 0.0) * 0.9 + 0.1)"),
    /** Striscia LED e neon con diffusore: larga, più forte davanti. */
    Strip("(0.35 + 0.65 * max(c, 0.0))");

    fun shape(c: Double): Double = when (this) {
        Uniform -> 1.0
        Spot -> 0.02 + 0.98 * smoothstep(0.82, 0.96, c)
        Downlight -> maxOf(c, 0.0) * 0.96 + 0.04
        Pendant -> 0.6 + 0.4 * c
        Shade -> 0.22 + 0.78 * abs(c).pow(1.5)
        Wall -> maxOf(c, 0.0) * 0.9 + 0.1
        Strip -> 0.35 + 0.65 * maxOf(c, 0.0)
    }

    /**
     * Fattore che dà alla forma lo stesso flusso di una luce uniforme (∫ forma · sinθ dθ = 2 su 0..π), con un massimo:
     * oltre ×8 il centro del fascio di un faretto bruciava i colori.
     */
    val norm: Double by lazy {
        val n = 720
        var sum = 0.0
        for (k in 0 until n) {
            val t = PI * (k + 0.5) / n
            sum += shape(cos(t)) * sin(t) * PI / n
        }
        minOf(2.0 / sum, MAX_NORM)
    }

    companion object {
        const val MAX_NORM = 8.0

        private fun smoothstep(a: Double, b: Double, x: Double): Double {
            val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }

        /**
         * Funzione GLSL `float lightBeam(float p, float c)`: forma già normalizzata del profilo numero `p` ([ordinal]).
         * Generata da qui, così shader e calcolo in Kotlin restano uguali.
         */
        fun glslFunction(): String = buildString {
            append("float lightBeam(float p, float c) {\n")
            for (e in entries.dropLast(1)) append("    if (p < ${e.ordinal}.5) return ${glslNumber(e.norm)} * ${e.glsl};\n")
            append("    return ${glslNumber(entries.last().norm)} * ${entries.last().glsl};\n}\n")
        }

        private fun glslNumber(v: Double): String {
            val s = ((v * 10000).toLong() / 10000.0).toString()
            return if ('.' in s) s else "$s.0"
        }
    }
}
