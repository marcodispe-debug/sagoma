package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.ScanCoordinates
import com.sagoma.planimetria.scan.ScanGeometry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Percorso B: dalla stanza disegnata come schizzo alla sessione, e dal risultato della sessione alla STESSA stanza. Puro.
 *  - lo schizzo è la stanza stessa (filo interno, stesso ordine dei muri): il lato k dello schizzo è il muro k della stanza;
 *  - l'aggiornamento cambia SOLO la posizione dei vertici: stesso id, stesso numero e ordine dei muri, quindi aperture,
 *    impianti, altezze, spessori e finiture (legati all'indice del muro) restano dove sono;
 *  - la stanza resta nel punto della pianta in cui era: la forma misurata si sovrappone allo schizzo (rotazione e traslazione
 *    che lo avvicinano di più; nessuna scala, nessuno specchio).
 */

/** Come cambia un'apertura quando il suo muro cambia lunghezza. `fits` falso: è più larga del muro nuovo (la conferma si blocca). */
data class OpeningChange(
    val openingId: Long,
    val wallIndex: Int,
    val oldPosition: Double,
    val newPosition: Double,
    /** Larghezza com'era nello schizzo. */
    val width: Double,
    /** Larghezza dopo l'eventuale correzione dell'utente (mai oltre il muro: una correzione più larga non vale). */
    val newWidth: Double,
    val newWallLength: Double,
    val fits: Boolean,
)

/** Anteprima dell'aggiornamento: la stanza nuova (null se la forma non è valida) e cosa succede a porte e finestre. */
data class SketchApplyPreview(val room: Room?, val openings: List<OpeningChange>) {
    /** Si può applicare: forma valida e ogni apertura sta nel suo muro. */
    val canApply: Boolean get() = room != null && openings.all { it.fits }
}

object SketchApply {
    /** Lo schizzo della stanza: angoli del filo interno, in metri, nell'ordine dei muri. */
    fun sketchOf(room: Room): RoomSketch = RoomSketch.fromPlanInterior(room.interior())

    /** Porte e finestre della stanza, per lato, come riferimenti per la verifica dell'associazione. */
    fun openingsOf(room: Room): List<SketchOpening> = room.openings.map { o ->
        SketchOpening(
            o.wallIndex,
            when {
                o.kind == OpeningKind.Passage -> OpeningMark.PASSAGE
                o.kind.glazed -> OpeningMark.WINDOW
                else -> OpeningMark.DOOR
            },
        )
    }

    /**
     * La stanza aggiornata con gli angoli misurati (mondo AR, filo interno, nell'ordine dei muri) e le posizioni delle aperture.
     * `positionOverrides` e `widthOverrides`: posizione (cm, centro dall'inizio del muro) e larghezza corrette dall'utente nella
     * revisione, per id. Una larghezza corretta vale solo se è positiva e non supera il muro nuovo; altrimenti resta quella di
     * prima (e se non entra, blocca). Altezza, davanzale, tipo e verso NON cambiano; la posizione segue in proporzione la nuova
     * lunghezza del muro (o quella corretta) e resta dentro il muro.
     */
    fun preview(
        room: Room,
        cornersAr: List<ArXZ>,
        positionOverrides: Map<Long, Double> = emptyMap(),
        widthOverrides: Map<Long, Double> = emptyMap(),
    ): SketchApplyPreview {
        val n = room.wallCount
        if (cornersAr.size != n || cornersAr.any { !it.x.isFinite() || !it.z.isFinite() }) return SketchApplyPreview(null, emptyList())
        val old = room.interior()
        val measured = cornersAr.map { ScanCoordinates.toPlan(com.sagoma.planimetria.scan.ArPoint(it.x, 0.0, it.z)) }
        val placed = fit(measured, old)
        if (!ScanGeometry.isSimple(placed) || Polygon.area(placed) < 1.0) return SketchApplyPreview(null, emptyList())
        val points = Polygon.inset(placed, room.thicknesses.map { -it / 2 })
        val updated = room.copy(points = points)
        val changes = room.openings.map { o ->
            val oldLen = room.wallLength(o.wallIndex)
            val newLen = updated.wallLength(o.wallIndex)
            val width = widthOverrides[o.id]?.takeIf { it > 0 && it <= newLen } ?: o.width
            val proposed = positionOverrides[o.id] ?: if (oldLen > 0) o.position * newLen / oldLen else newLen / 2
            OpeningChange(o.id, o.wallIndex, o.position, Openings.clampPosition(newLen, width, proposed), o.width, width, newLen, width <= newLen)
        }
        val byId = changes.associateBy { it.openingId }
        val fixtures = room.fixtures.map { f ->
            val oldLen = room.wallLength(f.wallIndex)
            val newLen = updated.wallLength(f.wallIndex)
            if (oldLen > 0) f.copy(position = (f.position * newLen / oldLen).coerceIn(0.0, newLen)) else f
        }
        val result = updated.copy(
            openings = room.openings.map { o -> byId.getValue(o.id).let { c -> o.copy(position = c.newPosition, width = c.newWidth) } },
            fixtures = fixtures,
        )
        return SketchApplyPreview(result, changes)
    }

    /** Rotazione + traslazione (nessuna scala, nessuno specchio) che porta `moving` più vicino a `target`, punto per punto. */
    internal fun fit(moving: List<Vec2>, target: List<Vec2>): List<Vec2> {
        val cm = moving.reduce { a, b -> a + b } * (1.0 / moving.size)
        val ct = target.reduce { a, b -> a + b } * (1.0 / target.size)
        var sCross = 0.0
        var sDot = 0.0
        for (i in moving.indices) {
            val a = moving[i] - cm
            val b = target[i] - ct
            sCross += a.x * b.y - a.y * b.x
            sDot += a.x * b.x + a.y * b.y
        }
        val th = atan2(sCross, sDot)
        val c = cos(th)
        val s = sin(th)
        return moving.map { p -> val d = p - cm; Vec2(d.x * c - d.y * s, d.x * s + d.y * c) + ct }
    }
}
