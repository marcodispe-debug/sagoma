package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Luce delle lampade che esce dalle finestre (e dai balconi) verso fuori: di sera, davanti a ogni finestra illuminata, il terreno,
 * il balcone o la terrazza prendono la luce della stanza, come fa [LightSpill] tra una stanza e l'altra attraverso le porte. Fuori
 * la luce sta in un rettangolo davanti alla finestra ([Scene3D.SceneLight.zones]) che si ferma prima di altre stanze della casa,
 * così non entra dai muri (una casa a L vista dal cortile). Solo finestre su muri dritti rispetto alla pianta: davanti a un muro
 * girato il rettangolo prenderebbe anche un pezzo di casa.
 */
object LightOutside {
    /** Parte della luce che passa dal vetro. */
    private const val GLASS = 0.8

    /**
     * Fuori, di sera, l'occhio si abitua al buio e la luce delle finestre sul terreno si nota più di quanto direbbero i suoi lumen
     * (dentro casa la vista 3D tiene l'esposizione della stanza illuminata): tante volte quella vera.
     */
    private const val DARK_EYE = 3.0

    /** Parte della luce della stanza rimandata dalle pareti, come in [LightSpill]: esce anche questa. */
    private const val BOUNCE = 0.35

    /** Fin dove arriva fuori la luce (cm dal muro) e quanto si allarga ai lati della finestra, dal più largo. */
    private const val REACH = 500.0
    private val SIDES = listOf(300.0, 150.0, 0.0)

    /** La luce sta appena dietro il vetro (cm): illumina terreno, balcone e spallette della finestra, non la facciata attorno. */
    private const val BEHIND = 25.0

    /** Il rettangolo fuori parte appena oltre la mezzeria del muro (il renderer gli aggiunge 3 cm per lato). */
    private const val START = 4.0

    /** Sotto questa profondità (cm) davanti alla finestra c'è subito altra casa: la luce non esce. */
    private const val MIN_DEPTH = 80.0

    private const val COLUMNS = 4
    private const val ROWS = 4

    /**
     * Una luce fuori da ogni finestra di `windows` illuminata dalle luci di `lights` (quelle che arrivano dentro, sul vetro: lampade
     * della stanza e luce che passa dalle porte). `blockers`: rettangoli delle stanze chiuse del piano, dove la luce di fuori non deve
     * arrivare.
     */
    fun through(windows: List<Scene3D.SceneWindow>, lights: List<Scene3D.SceneLight>, blockers: List<Bounds>): List<Scene3D.SceneLight> {
        if (lights.isEmpty()) return emptyList()
        val out = mutableListOf<Scene3D.SceneLight>()
        for (w in windows) {
            if (w.zones.isEmpty() || w.width < 1.0 || w.height < 1.0) continue
            val o = Vec2(-w.inward.x, -w.inward.z)
            if (o.length < 0.5) continue
            val outward = o / o.length
            // Solo muri dritti rispetto alla pianta (entro una decina di gradi).
            if (max(abs(outward.x), abs(outward.y)) < 0.985) continue
            val along = Vec2(-outward.y, outward.x)
            val center = Vec2(w.center.x, w.center.z)
            val inside = center - outward * 15.0
            val lit = lights.filter { l -> (l.zones ?: listOfNotNull(l.room)).any { it.contains(inside) } }
            if (lit.isEmpty()) continue
            val zone = zoneOutside(center, outward, along, w.width, blockers.filterNot { it.contains(inside) }) ?: continue
            val n = Vec3(outward.x, 0.0, outward.y)
            val area = w.width * w.height
            var flux = 0.0
            var warmFlux = 0.0
            var dir = Vec3.Zero
            for (l in lit) {
                var direct = 0.0
                var lampDir = Vec3.Zero
                // La finestra divisa in pezzi: la luce che arriva su ognuno, secondo la distanza e la forma della lampada.
                for (k in 0 until COLUMNS) for (h in 0 until ROWS) {
                    val s = (center + along * (w.width * ((k + 0.5) / COLUMNS - 0.5))).at(w.center.y + w.height * ((h + 0.5) / ROWS - 0.5))
                    val d = s - l.position
                    val r2 = max(d dot d, 900.0)
                    val u = d * (1 / sqrt(r2))
                    val facing = u dot n
                    if (facing <= 0.0) continue
                    val intensity = l.lumens / (4 * PI) * l.profile.norm * l.profile.shape(u dot l.beamAxis.normalized())
                    val part = intensity * facing * (area / (COLUMNS * ROWS)) / r2
                    direct += part
                    lampDir += u * part
                }
                val bounce = if (w.surface > 0) l.lumens * BOUNCE * area / w.surface else 0.0
                val total = (direct + bounce) * GLASS * DARK_EYE
                flux += total
                if (l.warm) warmFlux += total
                dir += lampDir + n * bounce
            }
            if (flux < LightSpill.MIN_LUMENS) continue
            out += Scene3D.SceneLight(
                (center - outward * BEHIND).at(w.center.y), null, flux, warm = warmFlux >= flux / 2, room = zone, zones = listOf(zone),
                profile = LightProfile.Window, axis = dir.normalized().takeIf { it.length > 0.5 } ?: n, spill = true,
            )
        }
        return out
    }

    /**
     * Rettangolo davanti alla finestra (centro `center` sulla linea del muro, larga `width`), largo come lei più uno dei [SIDES] per
     * lato e profondo fino a [REACH], accorciato davanti alle stanze di `blockers`: il più largo che non resta molto più corto di
     * quello largo come la finestra (ai lati la luce cala piano, un rettangolo stretto la taglierebbe di netto).
     */
    private fun zoneOutside(center: Vec2, outward: Vec2, along: Vec2, width: Double, blockers: List<Bounds>): Bounds? {
        fun depth(half: Double): Double {
            var depth = REACH
            for (b in blockers) {
                // Il rettangolo della stanza visto dalla finestra: di lato (u) e in avanti (s).
                val corners = listOf(Vec2(b.minX, b.minY), Vec2(b.maxX, b.minY), Vec2(b.minX, b.maxY), Vec2(b.maxX, b.maxY)).map { it - center }
                val u0 = corners.minOf { it dot along }
                val u1 = corners.maxOf { it dot along }
                val s0 = corners.minOf { it dot outward }
                val s1 = corners.maxOf { it dot outward }
                if (u1 <= -half || u0 >= half || s1 <= START) continue
                depth = min(depth, max(s0, START) - 20.0)
            }
            return depth
        }
        val narrow = depth(width / 2)
        if (narrow < MIN_DEPTH) return null
        val half = SIDES.map { width / 2 + it }.first { depth(it) >= narrow * 0.8 }
        val far = depth(half)
        val ends = listOf(-half to START, half to START, -half to far, half to far).map { (u, s) -> center + along * u + outward * s }
        return Bounds(ends.minOf { it.x }, ends.minOf { it.y }, ends.maxOf { it.x }, ends.maxOf { it.y })
    }

    private fun Bounds.contains(p: Vec2) = p.x in minX..maxX && p.y in minY..maxY
}
