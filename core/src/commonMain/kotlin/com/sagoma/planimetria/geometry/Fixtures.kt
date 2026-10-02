package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.cos
import kotlin.math.sin

/** Posizione degli impianti nella pianta. */
object Fixtures {

    /** Profondità del calorifero verso l'interno della stanza, in cm. */
    const val RADIATOR_DEPTH = 10.0

    /**
     * Impianto a muro: punto sul filo interno del muro (dove sta il centro dell'impianto) e normale
     * verso l'interno. La posizione è limitata al muro, come per le aperture.
     */
    fun wallAnchor(room: Room, f: Fixture): Pair<Vec2, Vec2> {
        val i = f.wallIndex.coerceIn(0, room.wallCount - 1)
        val start = room.wallStart(i)
        val len = room.wallLength(i)
        val u = (room.wallEnd(i) - start).normalized()
        val n = Openings.inwardNormal(room, i)
        val c = Openings.clampPosition(len, f.length, f.position)
        return (start + u * c + n * (room.thicknessOf(f.wallIndex) / 2)) to n
    }

    /** Centro dell'impianto: sul filo interno del muro (a muro) o il punto scelto (al soffitto). */
    fun center(room: Room, f: Fixture): Vec2 = when (f.kind.mount) {
        // Il calorifero sporge nella stanza: il suo centro è a metà della sua profondità.
        Mount.Wall -> wallAnchor(room, f).let { (p, n) -> if (f.kind == FixtureKind.Radiator) p + n * (RADIATOR_DEPTH / 2) else p }
        Mount.Ceiling -> f.point
    }

    /** Estremi di una luce lineare (neon, striscia LED), secondo lunghezza e rotazione. */
    fun linearEnds(f: Fixture): Pair<Vec2, Vec2> {
        val r = toRadians(f.rotation)
        val half = Vec2(cos(r), sin(r)) * (f.length / 2)
        return (f.point - half) to (f.point + half)
    }

    /**
     * Stanza spostata di `d`: si spostano gli angoli e anche le luci al soffitto (che sono in
     * coordinate della pianta); aperture e impianti a muro seguono già i muri.
     */
    fun movedRoom(room: Room, d: Vec2): Room = room.copy(
        points = room.points.map { it + d },
        fixtures = room.fixtures.map { if (it.kind.mount == Mount.Ceiling) it.copy(point = it.point + d) else it },
    )
}
