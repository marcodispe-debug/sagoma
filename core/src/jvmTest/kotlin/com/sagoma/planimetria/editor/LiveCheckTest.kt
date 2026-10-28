package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Controlla la pianta» in tempo reale: conti per il segnale e solo gli errori appena fatti per l'avviso. */
class LiveCheckTest {
    private val living = Room(
        1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0), wallThickness = 15.0,
        openings = listOf(Opening.default(1, OpeningKind.Door, 0, 200.0).copy(entrance = true), Opening.default(2, OpeningKind.Door, 1, 150.0)),
    )
    private val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) }, wallThickness = 15.0)

    private fun issues(vararg rooms: Room) = PlanCheck.of(Building.single(FloorPlan(rooms.toList())))
    private fun Room.moved(dx: Double) = copy(points = points.map { it + Vec2(dx, 0.0) })

    @Test
    fun `conti per il segnale`() {
        val list = issues(living, kitchen.moved(-100.0))
        val c = LiveCheck.counts(list)
        assertEquals(list.count { it.kind.error }, c.errors)
        assertTrue(c.errors >= 1)
        assertEquals(list.size, c.total)
        assertEquals(LiveCheck.Counts(0, 0, 0).total, 0)
    }

    @Test
    fun `solo gli errori appena fatti`() {
        val ok = issues(living, kitchen)
        assertEquals(emptyList(), LiveCheck.newErrors(ok, ok))
        // La cucina trascinata dentro il soggiorno: stanze sovrapposte, errore nuovo.
        val over = issues(living, kitchen.moved(-100.0))
        val fresh = LiveCheck.newErrors(ok, over)
        assertEquals(listOf(PlanCheck.Kind.Overlap), fresh.map { it.kind })
        // Spostata ancora un po': è lo stesso errore (misure diverse), non uno nuovo, e l'avviso si aggiorna.
        val more = issues(living, kitchen.moved(-150.0))
        assertEquals(emptyList(), LiveCheck.newErrors(over, more))
        val updated = assertNotNull(LiveCheck.stillThere(fresh.single(), more))
        assertTrue(updated.a > fresh.single().a)
        // Rimessa a posto (o annullato): l'avviso sparisce.
        assertNull(LiveCheck.stillThere(fresh.single(), ok))
        // Un errore che resta ma cambia muro selezionato (un angolo tolto più indietro rinumera i muri) non è nuovo.
        val near = issues(living, kitchen.moved(20.0)).single { it.kind == PlanCheck.Kind.NearWalls }
        val renumbered = near.copy(selection = Selection.Wall(2, 2))
        assertEquals(emptyList(), LiveCheck.newErrors(listOf(near), listOf(renumbered)))
        assertEquals(listOf(near), LiveCheck.newErrors(emptyList(), listOf(near)))
        // Cose da controllare e norme nuove non fanno avviso: c'è il segnale.
        val alone = issues(kitchen)
        assertTrue(alone.any { !it.kind.error })
        assertEquals(emptyList(), LiveCheck.newErrors(emptyList(), alone))
    }
}
