package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.GlobalMap
import com.sagoma.planimetria.scan.reconstruction.Surface
import com.sagoma.planimetria.scan.reconstruction.SurfaceExtractor
import com.sagoma.planimetria.scan.reconstruction.SurfaceKind
import com.sagoma.planimetria.scan.reconstruction.SurfaceParams
import com.sagoma.planimetria.scan.reconstruction.SurfaceResult
import kotlin.math.max

/*
 * AUDIT (solo test) — il segnale "spazio libero visto dietro" (FREE_SPACE) di R2 e il punteggio strutturale delle verticali.
 * Replica ESATTA del calcolo di produzione (verificata contro Surface.freeBehind e Surface.structuralScore) con il dettaglio di ogni
 * campione: stato della cella (libera, occupata, ignota, fuori griglia) e, sul sintetico, cosa c'è davvero lì (dietro un'apertura
 * vera, dietro una parete piena, dentro un mobile). Nessuna soglia cambiata: i controfattuali sono calcoli in memoria.
 */
object FreeSpaceAudit {
    private val offsets = doubleArrayOf(0.15, 0.25, 0.40)

    class Detail(
        val surfaceId: Int, val samples: Int, val free: Int, val occupied: Int, val unknown: Int, val outside: Int,
        val freeBehind: Double, val freeBehindProduction: Double?,
        /** (etichetta di verità → (stato → conteggio)). Etichette: "dietro apertura", "dietro parete piena", "dentro la stanza/mobile", "?". */
        val byTruth: Map<String, Map<String, Int>>,
        /** Campioni (x, z, stato, verità) per la vista. */
        val points: List<Triple<DoubleArray, Int, String>>,
    ) {
        /** Lo stesso rapporto ma su TUTTI i campioni nella griglia (ignoto = non libero): copertura reale della prova. */
        val freeOfAll: Double get() = if (samples - outside == 0) 0.0 else free.toDouble() / (samples - outside)
        val observedFraction: Double get() = if (samples - outside == 0) 0.0 else (free + occupied).toDouble() / (samples - outside)
    }

    private fun stateName(s: Int) = when (s) { 1 -> "libera"; 2 -> "occupata"; 0 -> "ignota"; else -> "fuori griglia" }

    /** Dettaglio di freeBehind per la superficie [sf]; con [room] e [wall] etichetta ogni campione con la verità. */
    fun detail(map: GlobalMap, sf: Surface, room: GtRoom? = null, wall: GtWall? = null): Detail {
        val idx = SurfaceExtractor.pointsOf(map, sf.memberVoxels)
        val stride = max(1, idx.size / 300)
        var free = 0; var occ = 0; var unk = 0; var out = 0; var seen = 0; var n = 0
        val truth = HashMap<String, HashMap<String, Int>>()
        val pts = mutableListOf<Triple<DoubleArray, Int, String>>()
        var t = 0
        while (t < idx.size) {
            val i = idx[t]
            for (off in offsets) {
                // Stessa espressione di produzione (Float − Double).
                val x = map.points.x[i] - sf.plane.nx * off; val y = map.points.y[i] - sf.plane.ny * off; val z = map.points.z[i] - sf.plane.nz * off
                val st = map.freeSpace.state(x, y, z)
                n++
                when (st) { 1 -> { free++; seen++ }; 2 -> { occ++; seen++ }; 0 -> unk++; else -> out++ }
                val tag = when {
                    room == null -> "?"
                    wall != null && wall.openings.any { o -> wall.along(x, z) in o.fromM..o.toM && y in o.bottomM..o.topM } -> "dietro apertura"
                    !room.inside(x, z) -> "dietro parete piena"
                    else -> "dentro la stanza/mobile"
                }
                truth.getOrPut(tag) { HashMap() }.merge(stateName(st), 1, Int::plus)
                pts.add(Triple(doubleArrayOf(x, z), st, tag))
            }
            t += stride
        }
        val fb = if (seen == 0) 0.0 else free.toDouble() / seen
        return Detail(sf.id, n, free, occ, unk, out, fb, sf.freeBehind, truth.mapValues { it.value.toSortedMap() }.toSortedMap(), pts)
    }

    class Score(
        val height: Double, val bottom: Double, val freeTerm: Double, val outermost: Double, val length: Double, val arcore: Double,
        val score: Double, val weak: Boolean, val kind: SurfaceKind,
    )

    /** Punteggio strutturale con la formula di produzione (Surfaces.classifyVertical); [fb] e [heightOverride] per i controfattuali. */
    fun score(s: SurfaceResult, sf: Surface, fb: Double, heightOverride: Double? = null, p: SurfaceParams = SurfaceParams()): Score {
        val top = sf.topAboveFloor; val bottom = sf.bottomAboveFloor
        val floorY = s.floor?.y; val ceilingY = s.ceilingY
        val ceilingH = if (ceilingY != null && floorY != null) ceilingY - floorY else null
        val h = heightOverride ?: when {
            top == null -> 0.5
            ceilingH != null && top >= ceilingH - 0.25 -> 1.0
            else -> ((top - 1.2) / 0.8).coerceIn(0.0, 1.0)
        }
        val b = if (bottom == null) 0.5 else (1 - (bottom - 0.15) / 0.45).coerceIn(0.0, 1.0)
        val o = if (sf.outermost == true) 1.0 else 0.0
        val l = ((sf.lengthM - 0.3) / 0.7).coerceIn(0.0, 1.0)
        val a = if (sf.arcorePlane != null) 1.0 else 0.0
        val sc = 0.30 * h + 0.15 * b + 0.25 * (1 - fb) + 0.20 * o + 0.05 * l + 0.05 * a
        val weak = sf.samples < p.minSamples || sf.effectiveFrames < p.minEffectiveFrames || sf.areaM2 < p.minAreaM2
        val kind = when { weak -> SurfaceKind.UNKNOWN; sc >= 0.6 -> SurfaceKind.VERTICAL_STRUCTURAL; sc < 0.5 -> SurfaceKind.VERTICAL_OBJECT; else -> SurfaceKind.UNKNOWN }
        return Score(h, b, 1 - fb, o, l, a, sc, weak, kind)
    }

    /** Classe che la superficie avrebbe con i controfattuali (nessuna modifica di R2: solo calcolo). */
    class Counterfactual(val production: SurfaceKind, val noOpeningSamples: SurfaceKind?, val freeOfAll: SurfaceKind, val fullHeight: SurfaceKind, val noOpeningFullHeight: SurfaceKind?, val freeZero: SurfaceKind)

    fun counterfactual(s: SurfaceResult, sf: Surface, d: Detail): Counterfactual {
        val noOpen = d.byTruth["dietro apertura"]?.let { op ->
            val f = d.free - (op["libera"] ?: 0); val sn = d.free + d.occupied - (op["libera"] ?: 0) - (op["occupata"] ?: 0)
            if (sn == 0) 0.0 else f.toDouble() / sn
        }
        return Counterfactual(
            score(s, sf, d.freeBehind).kind, noOpen?.let { score(s, sf, it).kind }, score(s, sf, d.freeOfAll).kind,
            score(s, sf, d.freeBehind, 1.0).kind, noOpen?.let { score(s, sf, it, 1.0).kind }, score(s, sf, 0.0).kind,
        )
    }
}
