package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/** Al piano di sopra gli angoli e i muri del piano di sotto sono riferimenti di aggancio (disegno, angoli, muri). */
class FloorSnapTest {
    private val lowerRoom = Room(1, "Sotto", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(100.0, 50.0) })
    // Stanza di sopra poco spostata rispetto a quella di sotto (13, 9 cm).
    private val upperRoom = Room(1, "Sopra", RoomType.Camera, RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(113.0, 59.0) })

    private fun vm(current: Int, upper: List<Room> = listOf(upperRoom)): EditorViewModel {
        val b = Building(
            listOf(Floor(1, "Piano terra", FloorPlan(listOf(lowerRoom))), Floor(2, "Primo piano", FloorPlan(upper))),
            current = current,
        )
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    @Test
    fun `al piano 1 il disegno dei muri scatta sull'angolo del piano 0`() {
        val vm = vm(1, upper = emptyList())
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(502.0, 48.0)) // a 2,8 cm dall'angolo (500, 50) del piano 0
        assertEquals(Vec2(500.0, 50.0), vm.state.value.wallDraw!!.points.single())
    }

    @Test
    fun `al piano 0 il disegno dei muri continua a scattare sui propri angoli e non su quelli di sopra`() {
        val vm = vm(0)
        vm.startDrawWalls()
        vm.drawWallsTap(Vec2(102.0, 52.0))
        assertEquals(Vec2(100.0, 50.0), vm.state.value.wallDraw!!.points.single())
        vm.drawWallsUndo()
        vm.drawWallsTap(Vec2(514.0, 360.0)) // a 1,4 cm dall'angolo (513, 359) della stanza di SOPRA, che al piano 0 non conta
        assertNotEquals(Vec2(513.0, 359.0), vm.state.value.wallDraw!!.points.single())
    }

    @Test
    fun `trascinando un angolo di sopra scatta sull'angolo di sotto e il piano di sotto resta uguale`() {
        val vm = vm(1)
        val lowerBefore = vm.state.value.fullBuilding.floors[0].plan
        vm.beginDrag(DragTarget.Corner(1, 0), Vec2(113.0, 59.0))
        vm.dragBy(DragTarget.Corner(1, 0), Vec2(-12.0, -8.0)) // arriva a (101, 51): a 1,4 cm dall'angolo (100, 50)
        assertEquals(Vec2(100.0, 50.0), vm.state.value.plan.room(1)!!.points[0])
        vm.endDrag()
        assertEquals(lowerBefore, vm.state.value.fullBuilding.floors[0].plan)
        assertEquals(RoomFactory.rectangle(400.0, 300.0).map { it + Vec2(100.0, 50.0) }, vm.state.value.fullBuilding.floors[0].plan.rooms.single().points)
    }

    @Test
    fun `trascinando un muro di sopra si allinea alla linea del muro di sotto`() {
        val vm = vm(1)
        // Muro 0 (in alto, y = 59): spostato di -8,5 cm arriva a y = 50,5, a 0,5 cm dal muro in alto del piano 0 (y = 50).
        vm.beginDrag(DragTarget.Wall(1, 0), Vec2(300.0, 59.0))
        vm.dragBy(DragTarget.Wall(1, 0), Vec2(0.0, -8.5))
        val p = vm.state.value.plan.room(1)!!.points
        assertEquals(50.0, p[0].y, 1e-9)
        assertEquals(50.0, p[1].y, 1e-9)
        vm.endDrag()
    }

    // ---------- Creazione con la scelta forma (Quadrato / Rettangolo / L con misure) ----------

    private val lowerB = Room(2, "Sotto B", RoomType.Camera, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(600.0, 50.0) })

    private fun vmCreate(current: Int, upper: List<Room> = emptyList(), lower: List<Room> = listOf(lowerRoom, lowerB)): EditorViewModel {
        val b = Building(
            listOf(Floor(1, "Piano terra", FloorPlan(lower)), Floor(2, "Primo piano", FloorPlan(upper))),
            current = current,
        )
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun EditorViewModel.create(shape: com.sagoma.planimetria.model.RoomShape, w: Double, d: Double): Room {
        startAddRoom()
        pickShape(shape)
        createRoom(RoomType.Camera, com.sagoma.planimetria.geometry.ShapeDimensions(w, d), 270.0)
        return state.value.plan.rooms.maxByOrNull { it.id }!!
    }

    private fun Room.bounds() = com.sagoma.planimetria.geometry.Polygon.bounds(points)

    @Test
    fun `al piano 1 la stanza creata dalla scelta forma si posa sull'angolo della stanza di sotto, con le misure scelte`() {
        val vm = vmCreate(1)
        val r = vm.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0)
        val b = r.bounds()
        assertEquals(100.0, b.minX, 1e-9) // angolo in alto a sinistra di "Sotto" (100, 50)
        assertEquals(50.0, b.minY, 1e-9)
        // Le dimensioni sono quelle richieste (interne 300 × 200 più i muri): non cambiano.
        val plain = vmCreate(0, lower = emptyList()).create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0).bounds()
        assertEquals(plain.width, b.width, 1e-9)
        assertEquals(plain.height, b.height, 1e-9)
    }

    @Test
    fun `le stanze successive di sopra si posano sulle stanze di sotto ancora scoperte, poi su un angolo di sotto`() {
        val vm = vmCreate(1)
        val first = vm.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0)
        assertEquals(Vec2(100.0, 50.0), Vec2(first.bounds().minX, first.bounds().minY))
        val second = vm.create(com.sagoma.planimetria.model.RoomShape.Square, 250.0, 250.0)
        assertEquals(Vec2(600.0, 50.0), Vec2(second.bounds().minX, second.bounds().minY)) // "Sotto B"
        // Tutte le stanze di sotto sono coperte: si posa comunque con un angolo su un angolo di sotto, senza sovrapporsi alle altre.
        val third = vm.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 200.0, 200.0)
        val corner = Vec2(third.bounds().minX, third.bounds().minY)
        assertTrue(corner in (lowerRoom.points + lowerB.points), "angolo $corner non su un angolo di sotto")
        for (other in listOf(first, second)) {
            val o = other.bounds(); val t = third.bounds()
            assertTrue(t.minX >= o.maxX - 1.0 || t.maxX <= o.minX + 1.0 || t.minY >= o.maxY - 1.0 || t.maxY <= o.minY + 1.0)
        }
    }

    @Test
    fun `al piano terra la creazione resta come prima e il piano di sotto non cambia`() {
        val vm = vmCreate(0, lower = listOf(lowerRoom))
        val r = vm.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0)
        assertEquals(lowerRoom.bounds().maxX + 60.0, r.bounds().minX, 1e-9) // accanto, con la distanza di sempre
        // Piano 1: il piano 0 resta uguale a com'era.
        val up = vmCreate(1)
        val before = up.state.value.fullBuilding.floors[0].plan
        up.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0)
        assertEquals(before, up.state.value.fullBuilding.floors[0].plan)
    }

    @Test
    fun `una stanza a L di sopra si posa con l'angolo della sua forma sull'angolo di sotto`() {
        val vm = vmCreate(1)
        startAddRoom(vm)
        val r = vm.state.value.plan.rooms.single()
        assertEquals(Vec2(100.0, 50.0), Vec2(r.bounds().minX, r.bounds().minY))
    }

    private fun startAddRoom(vm: EditorViewModel) {
        vm.startAddRoom()
        vm.pickShape(com.sagoma.planimetria.model.RoomShape.L)
        vm.pickLOrientation(com.sagoma.planimetria.model.LOrientation.TopRight)
        vm.createRoom(RoomType.Camera, com.sagoma.planimetria.geometry.ShapeDimensions(400.0, 300.0, 200.0, 150.0), 270.0)
    }

    /**
     * Percorso reale: il piano di sopra nasce con "Parti dai muri del piano di sotto" (acceso di default), quindi ha già una copia di
     * ogni stanza di sotto; poi si aggiunge una stanza con la scelta forma (createRoom). Tutte le stanze di sotto sono "coperte":
     * la nuova stanza deve comunque posarsi con un angolo su un angolo del piano di sotto, non a 60 cm di distanza.
     */
    @Test
    fun `piano di sopra nato con i muri copiati, la nuova stanza da scelta forma si aggancia a un angolo di sotto`() {
        val one = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(lowerRoom)))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = one
            override fun save(building: Building) {}
        })
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        assertEquals(1, vm.state.value.building.current)
        assertEquals(listOf(lowerRoom.points), vm.state.value.plan.rooms.map { it.points }) // copia identica
        val lowerCorners = lowerRoom.points
        val r = vm.create(com.sagoma.planimetria.model.RoomShape.Rectangle, 300.0, 200.0)
        val corner = Vec2(r.bounds().minX, r.bounds().minY)
        assertTrue(corner in lowerCorners, "angolo ($corner) non su un angolo del piano di sotto")
        // Non si sovrappone alla stanza copiata.
        val existing = vm.state.value.plan.rooms.first { it.id != r.id }.bounds()
        assertTrue(r.bounds().minX >= existing.maxX - 1.0 || r.bounds().maxX <= existing.minX + 1.0 || r.bounds().minY >= existing.maxY - 1.0 || r.bounds().maxY <= existing.minY + 1.0)
        // E il piano di sotto resta com'era.
        assertEquals(listOf(lowerRoom.points), vm.state.value.fullBuilding.floors[0].plan.rooms.map { it.points })
    }

    // ---------- Spostamento dell'intera stanza (DragTarget.RoomLabel) ----------

    private fun EditorViewModel.moveRoom(roomId: Long, delta: Vec2) {
        beginDrag(DragTarget.RoomLabel(roomId), Vec2.Zero)
        dragBy(DragTarget.RoomLabel(roomId), delta)
        endDrag()
    }

    @Test
    fun `spostando una stanza di sopra vicino a un angolo di sotto scatta sull'angolo`() {
        val vm = vm(1) // sopra: stanza a (113, 59), sotto: stanza a (100, 50)
        vm.moveRoom(1, Vec2(-11.0, -7.0)) // l'angolo arriva a (102, 52): a 2,8 cm da (100, 50)
        assertEquals(Vec2(100.0, 50.0), vm.state.value.plan.room(1)!!.points[0])
        assertEquals(lowerRoom.points, vm.state.value.plan.room(1)!!.points) // tutta la stanza coincide con quella di sotto
    }

    @Test
    fun `una stanza copiata dal piano di sotto, spostata e riportata vicino, scatta sulla stanza di sotto`() {
        val one = Building(listOf(Floor(1, "Piano terra", FloorPlan(listOf(lowerRoom)))))
        val vm = EditorViewModel(object : PlanStore {
            override fun load() = one
            override fun save(building: Building) {}
        })
        vm.addFloor("Primo piano", 300.0, copyRooms = true, autoLevel = true)
        val copy = vm.state.value.plan.rooms.single()
        assertEquals(lowerRoom.points, copy.points)
        vm.moveRoom(copy.id, Vec2(180.0, 120.0)) // la porta lontano (oltre 25 cm: nessuno scatto)
        assertEquals(lowerRoom.points.map { it + Vec2(180.0, 120.0) }, vm.state.value.plan.room(copy.id)!!.points)
        vm.moveRoom(copy.id, Vec2(-177.0, -118.0)) // torna a (3, 2) cm dalla posizione di sotto
        assertEquals(lowerRoom.points, vm.state.value.plan.room(copy.id)!!.points)
    }

    @Test
    fun `spostando una stanza di sopra i muri paralleli si allineano a quelli di sotto`() {
        // Sopra: stanza più stretta, con il muro in alto a y = 59 e quello a sinistra a x = 113.
        val narrow = Room(1, "Sopra", RoomType.Camera, RoomFactory.rectangle(250.0, 200.0).map { it + Vec2(113.0, 59.0) })
        val vm = vm(1, upper = listOf(narrow))
        // Nessun angolo entro 25 cm da un angolo di sotto: il muro in alto arriva a 3 cm dal muro in alto di sotto.
        vm.moveRoom(1, Vec2(60.0, -6.0)) // angolo a (173, 53): muro in alto a y = 53, a 3 cm dal muro di sotto (y = 50)
        val p = vm.state.value.plan.room(1)!!.points
        assertEquals(50.0, p[0].y, 1e-9)
        assertEquals(50.0, p[1].y, 1e-9)
        assertEquals(173.0, p[0].x, 1e-9) // lo spostamento lungo il muro non cambia
    }

    @Test
    fun `lo spostamento non modifica il piano di sotto`() {
        val vm = vm(1)
        val before = vm.state.value.fullBuilding.floors[0]
        vm.moveRoom(1, Vec2(-11.0, -7.0))
        vm.moveRoom(1, Vec2(40.0, 30.0))
        assertEquals(before, vm.state.value.fullBuilding.floors[0])
        assertEquals(before.plan, vm.state.value.planBelow) // e il riferimento che il VM usa è lo stesso piano, intatto
    }

    @Test
    fun `al piano terra lo spostamento resta quello di prima, senza riferimenti di sopra`() {
        val vm = vm(0) // sopra c'e una stanza a (113, 59): al piano terra non conta
        val moved = lowerRoom.points.map { it + Vec2(-8.0, -8.0) }
        vm.moveRoom(1, Vec2(-8.0, -8.0))
        assertEquals(moved, vm.state.value.plan.room(1)!!.points) // nessuno scatto sull'angolo (113, 59) di sopra
    }
}
