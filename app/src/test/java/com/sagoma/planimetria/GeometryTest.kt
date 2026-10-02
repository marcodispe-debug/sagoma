package com.sagoma.planimetria

import com.sagoma.planimetria.editor.History
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.Snapping
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private val lDims = ShapeDimensions(600.0, 400.0, 300.0, 250.0)

    @Test
    fun `area del rettangolo e della L`() {
        assertEquals(200_000.0, Polygon.area(RoomFactory.rectangle(500.0, 400.0)), 1e-6)
        for (o in LOrientation.entries) {
            assertEquals(600.0 * 400 + 300.0 * 250, Polygon.area(RoomFactory.lShape(lDims, o)), 1e-6)
        }
    }

    @Test
    fun `area interna calcolata sul filo interno dei muri`() {
        // Rettangolo 500×400 in mezzeria, muri da 15 cm: interno 485×385.
        assertEquals(485.0 * 385, Polygon.interiorArea(RoomFactory.rectangle(500.0, 400.0), 15.0), 1e-6)
        // Stesso risultato con l'ordine dei vertici invertito.
        assertEquals(485.0 * 385, Polygon.interiorArea(RoomFactory.rectangle(500.0, 400.0).reversed(), 15.0), 1e-6)
    }

    @Test
    fun `area interna della L`() {
        // L 600×400 + protuberanza 300×250: il contorno interno si ottiene togliendo 7,5 cm da ogni lato.
        // Contorno esterno (mezzeria): larghezza 600, altezza totale 650, rientranza 300×250.
        // Interno: rettangolo 585×635 meno la rientranza, che diventa 300×250 (i suoi due lati interni
        // si spostano verso l'esterno della rientranza e quelli esterni coincidono col bordo già ridotto).
        for (o in LOrientation.entries) {
            val inner = Polygon.interiorArea(RoomFactory.lShape(lDims, o), 15.0)
            assertEquals(585.0 * 635 - 300.0 * 250, inner, 1e-6)
        }
    }

    @Test
    fun `lunghezze dei muri sul lato interno`() {
        val lengths = Polygon.interiorEdgeLengths(RoomFactory.rectangle(500.0, 400.0), 15.0)
        assertEquals(listOf(485.0, 385.0, 485.0, 385.0), lengths.map { Math.round(it * 1000) / 1000.0 })
    }

    @Test
    fun `stanza creata dalle misure interne le rispetta esattamente`() {
        // Rettangolo: 500×400 interni → mezzeria 515×415.
        val rect = Polygon.fromInterior(RoomFactory.rectangle(500.0, 400.0), 15.0)
        assertEquals(515.0 * 415, Polygon.area(rect), 1e-6)
        assertEquals(500.0 * 400, Polygon.interiorArea(rect, 15.0), 1e-6)
        // L: il contorno interno torna identico alle misure inserite, in tutti gli orientamenti.
        for (o in LOrientation.entries) {
            val interior = RoomFactory.lShape(lDims, o)
            val center = Polygon.fromInterior(interior, 15.0)
            val expected = interior.indices.map { interior[it].distanceTo(interior[(it + 1) % interior.size]) }
            val actual = Polygon.interiorEdgeLengths(center, 15.0)
            expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-6) }
        }
    }

    @Test
    fun `stanza più stretta dei muri ha area interna zero`() {
        assertEquals(0.0, Polygon.interiorArea(RoomFactory.rectangle(10.0, 200.0), 15.0), 1e-9)
    }

    @Test
    fun `tutte le L sono orarie su schermo`() {
        for (o in LOrientation.entries) assertTrue(Polygon.signedArea(RoomFactory.lShape(lDims, o)) > 0)
    }

    @Test
    fun `la protuberanza sporge dal lato scelto`() {
        val tr = Polygon.bounds(RoomFactory.lShape(lDims, LOrientation.TopRight))
        val ptsTr = RoomFactory.lShape(lDims, LOrientation.TopRight)
        // In alto a destra: l'angolo in alto a sinistra del bounding box è "tagliato".
        assertFalse(Polygon.contains(ptsTr, Vec2(tr.minX + 10, tr.minY + 10)))
        assertTrue(Polygon.contains(ptsTr, Vec2(tr.maxX - 10, tr.minY + 10)))
        val ptsBl = RoomFactory.lShape(lDims, LOrientation.BottomLeft)
        val bl = Polygon.bounds(ptsBl)
        assertFalse(Polygon.contains(ptsBl, Vec2(bl.maxX - 10, bl.maxY - 10)))
        assertTrue(Polygon.contains(ptsBl, Vec2(bl.minX + 10, bl.maxY - 10)))
    }

    @Test
    fun `angolo vicino alla posizione pulita scatta esattamente`() {
        val pts = RoomFactory.rectangle(500.0, 400.0)
        val (moved, snapped) = Snapping.moveCorner(pts, 2, Vec2(512.0, 391.0))
        assertTrue(snapped)
        assertEquals(Vec2(500.0, 400.0), moved[2])
    }

    @Test
    fun `lo snap riconosce il rettangolo allungato, non la forma originale`() {
        val stretched = Snapping.moveWall(RoomFactory.rectangle(500.0, 400.0), 1, Vec2(100.0, 0.0))
        // Il muro destro ora è a x = 600: trascino l'angolo in basso a destra vicino a (600, 400).
        val (moved, snapped) = Snapping.moveCorner(stretched, 2, Vec2(590.0, 410.0))
        assertTrue(snapped)
        assertEquals(Vec2(600.0, 400.0), moved[2])
    }

    @Test
    fun `lontano dalla posizione pulita l'angolo resta libero`() {
        val (moved, snapped) = Snapping.moveCorner(RoomFactory.rectangle(500.0, 400.0), 2, Vec2(560.0, 450.0))
        assertFalse(snapped)
        assertEquals(Vec2(560.0, 450.0), moved[2])
    }

    @Test
    fun `lo snap raddrizza le imprecisioni residue sugli altri muri`() {
        // Angolo 1 leggermente storto (3 cm), poi aggancio l'angolo 3.
        val pts = RoomFactory.rectangle(500.0, 400.0).toMutableList().also { it[1] = Vec2(500.0, 3.0) }
        val (moved, _) = Snapping.moveCorner(pts, 3, Vec2(5.0, 395.0))
        moved.indices.forEach { i ->
            val a = moved[i]
            val b = moved[(i + 1) % moved.size]
            assertTrue("muro $i non dritto: $a -> $b", a.x == b.x || a.y == b.y)
        }
    }

    @Test
    fun `trascinare un muro sposta solo lungo la normale`() {
        val moved = Snapping.moveWall(RoomFactory.rectangle(500.0, 400.0), 1, Vec2(50.0, 30.0))
        assertEquals(Vec2(550.0, 0.0), moved[1])
        assertEquals(Vec2(550.0, 400.0), moved[2])
    }

    @Test
    fun `lunghezza esatta del muro mantiene il rettangolo pulito`() {
        val moved = Snapping.setWallLength(RoomFactory.rectangle(500.0, 400.0), 0, 420.0)
        assertEquals(420.0, moved[0].distanceTo(moved[1]), 1e-9)
        assertEquals(Vec2(420.0, 400.0), moved[2])
        assertEquals(420.0 * 400, Polygon.area(moved), 1e-6)
    }

    @Test
    fun `stanza spostata si appoggia all'angolo vicino`() {
        val a = Room(1, "A", RoomType.Altro, RoomFactory.rectangle(300.0, 300.0))
        val b = Room(2, "B", RoomType.Altro, RoomFactory.rectangle(200.0, 200.0).map { it + Vec2(400.0, 0.0) })
        val d = Snapping.snapRoomTranslation(b, listOf(a), Vec2(-90.0, 12.0))
        assertEquals(Vec2(-100.0, 0.0), d)
    }

    @Test
    fun `nuova stanza posizionata accanto senza sovrapporsi`() {
        val plan = FloorPlan(listOf(Room(1, "A", RoomType.Altro, RoomFactory.rectangle(300.0, 300.0))))
        val placed = RoomFactory.placeBeside(plan, RoomFactory.rectangle(200.0, 200.0))
        assertTrue(placed.minOf { it.x } > 300.0)
    }

    @Test
    fun `nomi automatici per tipo`() {
        var plan = FloorPlan()
        assertEquals("Cucina", RoomFactory.autoName(plan, RoomType.Cucina))
        plan = FloorPlan(listOf(Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(1.0, 1.0))))
        assertEquals("Cucina 2", RoomFactory.autoName(plan, RoomType.Cucina))
        // La stanza stessa non conta quando si cambia il suo tipo.
        assertEquals("Cucina", RoomFactory.autoName(plan, RoomType.Cucina, excludingRoomId = 1))
    }

    @Test
    fun `annulla e ripeti`() {
        val h = History<Int>()
        h.record(1)
        h.record(2)
        assertEquals(2, h.undo(3))
        assertEquals(1, h.undo(2))
        assertEquals(2, h.redo(1))
        h.record(2)
        assertFalse(h.canRedo)
    }

    @Test
    fun `etichetta dentro la L`() {
        for (o in LOrientation.entries) {
            val pts = RoomFactory.lShape(lDims, o)
            assertTrue(Polygon.contains(pts, Polygon.labelPoint(pts)))
        }
    }
}
