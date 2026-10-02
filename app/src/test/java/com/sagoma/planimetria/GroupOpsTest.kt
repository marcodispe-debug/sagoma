package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Alignment
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.GroupOps
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selezione multipla: sposta, ruota, specchia, copia, serie, allinea, distribuisci, elimina. */
class GroupOpsTest {
    // Stanza 400×300 con una porta sul muro in alto (muro 0) a 100 cm dall'angolo in alto a sinistra.
    private val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0))
        .copy(openings = listOf(Opening.default(1, OpeningKind.Door, 0, 100.0)))
    private val sofa = Furniture(1, "divano", Vec2(600.0, 100.0), rotation = 0.0, width = 200.0, depth = 90.0, height = 80.0)
    private val plan = FloorPlan(listOf(room), furniture = listOf(sofa), columns = listOf(Column(1, Vec2(900.0, 100.0))))
    private val all = setOf(GroupItem.RoomItem(1), GroupItem.FurnitureItem(1), GroupItem.ColumnItem(1))

    /** Centro della porta nel piano. */
    private fun doorCenter(p: FloorPlan): Vec2 {
        val r = p.room(1)!!
        val (a, b) = Openings.span(r, r.openings.single())
        return (a + b) / 2.0
    }

    private fun signedArea(pts: List<Vec2>) = pts.indices.sumOf { i -> val a = pts[i]; val b = pts[(i + 1) % pts.size]; a.x * b.y - b.x * a.y } / 2

    @Test
    fun `sposta e ruota restano coerenti`() {
        val moved = GroupOps.translate(plan, all, Vec2(50.0, 20.0))
        assertEquals(Vec2(650.0, 120.0), moved.furniture.single().center)
        assertEquals(doorCenter(plan) + Vec2(50.0, 20.0), doorCenter(moved))
        val r = GroupOps.rotate90(plan, all, Vec2(0.0, 0.0))
        assertEquals(90.0, r.furniture.single().rotation, 1e-9)
        // La porta (in alto a x = 100) ruotata di 90° in senso orario: x = 0, y = 100.
        val d = doorCenter(r)
        assertEquals(0.0, d.x, 1e-6)
        assertEquals(100.0, d.y, 1e-6)
    }

    @Test
    fun `specchiare mantiene la stanza in senso orario e la porta sul muro giusto`() {
        val m = GroupOps.mirror(plan, setOf(GroupItem.RoomItem(1)), Vec2(200.0, 150.0), horizontal = true)
        val r = m.room(1)!!
        assertTrue(signedArea(r.points) > 0) // come tutte le stanze
        assertEquals(Polygon.area(room.points), Polygon.area(r.points), 1e-6)
        // Porta specchiata: da x = 100 a x = 300, sempre sul muro in alto; il cardine cambia lato.
        val d = doorCenter(m)
        assertEquals(300.0, d.x, 1e-6)
        assertEquals(0.0, d.y, 1e-6)
        assertNotEquals(room.openings.single().hingeLeft, r.openings.single().hingeLeft)
    }

    @Test
    fun `specchiare ribalta anche il mobile`() {
        val one = setOf(GroupItem.FurnitureItem(1))
        val m = GroupOps.mirror(plan, one, sofa.center, horizontal = true)
        assertTrue(m.furniture.single().mirrored)
        assertEquals(sofa.center, m.furniture.single().center)
        // Due specchiature tornano all'originale.
        assertEquals(sofa, GroupOps.mirror(m, one, sofa.center, horizontal = true).furniture.single())
        val v = GroupOps.mirror(plan, one, sofa.center, horizontal = false).furniture.single()
        assertTrue(v.mirrored)
        assertEquals(180.0, v.rotation, 1e-9)
    }

    @Test
    fun `incolla su un altro piano`() {
        val other = FloorPlan(furniture = listOf(sofa.copy(id = 5)))
        val (p, created) = GroupOps.duplicate(plan, setOf(GroupItem.FurnitureItem(1), GroupItem.RoomItem(1)), Vec2(10.0, 0.0), into = other)
        assertEquals(2, p.furniture.size)
        assertEquals(1, p.rooms.size)
        assertEquals(Vec2(610.0, 100.0), p.furniture.last().center)
        assertTrue(p.furniture.last().id != 5L)
        assertEquals(2, created.size)
    }

    @Test
    fun `copia e serie con id nuovi`() {
        val (p, created) = GroupOps.duplicate(plan, all, Vec2(0.0, 500.0), count = 3)
        assertEquals(4, p.rooms.size)
        assertEquals(9, created.size)
        assertEquals(p.rooms.size, p.rooms.map { it.id }.distinct().size)
        assertEquals(4, p.rooms.flatMap { it.openings }.map { it.id }.distinct().size)
        assertEquals(1600.0, p.furniture.maxOf { it.center.y }, 1e-9)
        assertEquals(1, GroupOps.delete(p, created).rooms.size)
    }

    @Test
    fun `allinea, distribuisci e selezione a rettangolo`() {
        val a = GroupOps.align(plan, setOf(GroupItem.FurnitureItem(1), GroupItem.ColumnItem(1)), Alignment.Top)
        // Il divano (y da 55 a 145) è già il più in alto e resta; la colonna 30×30 sale fino a y = 55.
        assertEquals(100.0, a.furniture.single().center.y, 1e-6)
        assertEquals(70.0, a.column(1)!!.center.y, 1e-6)
        // Distribuisci: tre oggetti con spazi uguali.
        val three = plan.copy(columns = plan.columns + Column(2, Vec2(700.0, 100.0)))
        val items = setOf(GroupItem.FurnitureItem(1), GroupItem.ColumnItem(1), GroupItem.ColumnItem(2))
        val d = GroupOps.distribute(three, items, horizontal = true)
        val boxes = items.map { GroupOps.boundsOf(d, it)!! }.sortedBy { it.minX }
        assertEquals(boxes[1].minX - boxes[0].maxX, boxes[2].minX - boxes[1].maxX, 1e-6)
        // Da sinistra a destra solo ciò che è dentro; da destra a sinistra anche ciò che tocca.
        assertEquals(setOf<GroupItem>(GroupItem.FurnitureItem(1)), GroupOps.inRect(plan, Vec2(450.0, 0.0), Vec2(750.0, 200.0), crossing = false))
        assertTrue(GroupItem.RoomItem(1) in GroupOps.inRect(plan, Vec2(350.0, 0.0), Vec2(750.0, 200.0), crossing = true))
    }
}
