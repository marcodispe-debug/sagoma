package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PendingOpening
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Alignment
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Parapets
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Prova di resistenza dell'editor: tante sessioni di operazioni a caso, come farebbe un utente (stanze,
 * aperture, impianti, strutture, arredi, piani, trascinamenti, gruppi, copia/incolla, duplica, annulla...).
 * Dopo ogni passo: nessun errore, il progetto si salva e si rilegge uguale, identificativi unici, selezione
 * valida, coordinate finite; ogni tanto anche 3D, collisioni, computo e parapetti.
 */
class EditorStressTest {
    private class Store(var saved: Building?) : PlanStore {
        override fun load() = saved
        override fun save(building: Building) { saved = building }
    }

    init {
        File("../app/src/pro/assets/furniture/catalog.json").takeIf { it.isFile }?.let { FurnitureCatalog.load(it.readText()) }
    }

    @Test
    fun sessioniACaso() {
        val problems = linkedMapOf<String, String>()
        // Di solito 30 sessioni; per una prova lunga: variabile d'ambiente SAGOMA_STRESS=numero di sessioni.
        val sessions = System.getenv("SAGOMA_STRESS")?.toIntOrNull() ?: 30
        val steps = if (sessions > 30) 500 else 300
        for (seed in 1..sessions) runSession(seed, steps, problems)
        println("Prova di resistenza: $sessions sessioni × $steps passi; in media alla fine ${"%.1f".format(totals.values.sum().toDouble() / sessions)} oggetti per sessione: $totals")
        if (problems.isNotEmpty()) {
            fail("Problemi trovati (${problems.size}):\n" + problems.entries.joinToString("\n\n") { (k, v) -> "$k\n$v" })
        }
    }

    private fun runSession(seed: Int, steps: Int, problems: MutableMap<String, String>) {
        val rnd = Random(seed)
        val start = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
        val vm = EditorViewModel(Store(Building.single(FloorPlan(listOf(start)))))
        vm.setViewport(1200f, 800f, 24f)
        val log = ArrayList<String>()

        fun <T> pick(list: List<T>): T? = if (list.isEmpty()) null else list[rnd.nextInt(list.size)]
        fun plan() = vm.state.value.plan
        fun anyRoom(indoor: Boolean? = null) = pick(plan().rooms.filter { indoor == null || it.outdoor != indoor })
        fun wallPoint(r: Room, i: Int, f: Double = rnd.nextDouble(0.2, 0.8)) = r.wallStart(i) + (r.wallEnd(i) - r.wallStart(i)) * f
        fun clearModes() {
            vm.cancelPlacingOpening(); vm.stopPaste(); vm.closeMeasure(); vm.cancelDrawWalls()
            if (vm.state.value.selectMode) vm.toggleSelectMode()
            vm.cancelCreation()
        }
        fun targets(): List<Pair<DragTarget, Vec2>> {
            val p = plan()
            val out = ArrayList<Pair<DragTarget, Vec2>>()
            for (r in p.rooms) {
                for (i in 0 until r.wallCount) out += DragTarget.Wall(r.id, i) to wallPoint(r, i)
                for (k in r.points.indices) out += DragTarget.Corner(r.id, k) to r.points[k]
                out += DragTarget.RoomInterior(r.id) to Polygon.labelPoint(r.points)
                out += DragTarget.RoomLabel(r.id) to Polygon.labelPoint(r.points)
                for (o in r.openings) if (o.wallIndex < r.wallCount) out += DragTarget.Opening(r.id, o.id) to wallPoint(r, o.wallIndex, 0.5)
                for (f in r.fixtures) out += DragTarget.Fixture(r.id, f.id) to (if (f.kind.mount == Mount.Wall && f.wallIndex < r.wallCount) wallPoint(r, f.wallIndex, 0.5) else f.point)
            }
            p.furniture.forEach { out += DragTarget.FurnitureBody(it.id) to it.center }
            p.dimensions.forEach { out += DragTarget.DimensionLine(it.id) to it.lineA; out += DragTarget.DimensionEnd(it.id, 1) to it.b }
            p.annotations.forEach { out += DragTarget.AnnotationBody(it.id) to it.at; it.arrowTo?.let { tip -> out += DragTarget.AnnotationArrow(it.id) to tip } }
            p.columns.forEach { out += DragTarget.Column(it.id) to it.center }
            p.beams.forEach { out += DragTarget.BeamBody(it.id) to it.mid }
            p.freeWalls.forEach { out += DragTarget.FreeWallBody(it.id) to it.mid }
            p.stairs.forEach { out += DragTarget.Stair(it.id) to it.center }
            return out
        }
        fun rv(max: Double) = rnd.nextDouble(-max, max)

        val actions: List<Pair<String, () -> Unit>> = listOf(
            "nuova stanza" to {
                clearModes()
                val type = pick(RoomType.entries)!!
                if (type.outdoor) vm.startAddOutdoor(type) else vm.startAddRoom()
                val shape = pick(RoomShape.entries)!!
                vm.pickShape(shape)
                if (shape == RoomShape.L) vm.pickLOrientation(pick(LOrientation.entries)!!)
                val w = rnd.nextDouble(90.0, 700.0); val d = rnd.nextDouble(90.0, 600.0)
                vm.createRoom(type, ShapeDimensions(w, d, rnd.nextDouble(40.0, w * 0.8), rnd.nextDouble(40.0, d * 0.8)), rnd.nextDouble(220.0, 320.0))
            },
            "apertura" to {
                clearModes()
                anyRoom()?.let { r ->
                    val i = rnd.nextInt(r.wallCount)
                    // La porta-finestra non si offre più (nei file vecchi diventa un balcone ad 1 anta).
                    vm.startPlacingOpening(PendingOpening(pick(OpeningKind.entries.filter { it != OpeningKind.FrenchDoor })!!, sliding = rnd.nextBoolean()))
                    vm.onTap(DragTarget.Wall(r.id, i), wallPoint(r, i))
                }
            },
            "impianto" to {
                clearModes()
                anyRoom(indoor = true)?.let { r ->
                    val kind = pick(FixtureKind.entries)!!
                    vm.startPlacingFixture(kind)
                    if (kind.mount == Mount.Wall) { val i = rnd.nextInt(r.wallCount); vm.onTap(DragTarget.Wall(r.id, i), wallPoint(r, i)) }
                    else vm.onTap(DragTarget.RoomInterior(r.id), Polygon.labelPoint(r.points))
                }
            },
            "strutture" to {
                clearModes()
                when (rnd.nextInt(5)) {
                    0 -> vm.addStair(pick(StairKind.entries)!!)
                    1 -> vm.addColumn(pick(ColumnShape.entries)!!)
                    2 -> vm.addBeam()
                    3 -> vm.addFreeWall()
                    else -> vm.addRuler()
                }
            },
            "arredo" to { clearModes(); pick(FurnitureCatalog.items)?.let { vm.addFurniture(it) } },
            "seleziona" to { clearModes(); pick(targets())?.let { (t, at) -> vm.onTap(t, at) } },
            "trascina" to {
                clearModes()
                pick(targets())?.let { (t, at) ->
                    vm.beginDrag(t, at)
                    repeat(3) { k -> vm.dragBy(t, Vec2(rv(200.0), rv(200.0)) * ((k + 1) / 3.0)) }
                    vm.endDrag()
                }
            },
            "duplica" to { if (!vm.state.value.pasting) vm.duplicateSelected() },
            "copia e incolla" to {
                vm.copy()
                val p = pick(targets())
                vm.setHover(p?.second?.let { it + Vec2(rv(80.0), rv(80.0)) }, p?.first)
                vm.pasteNow()
                vm.setHover(null, null)
            },
            "incolla con tocco" to {
                vm.copy()
                vm.startPaste()
                pick(targets())?.let { (t, at) -> vm.onTap(t, at) }
                vm.stopPaste()
            },
            "gruppo" to {
                clearModes()
                vm.selectAll()
                if (rnd.nextBoolean()) vm.selectRectUpdate(Vec2(rv(600.0), rv(600.0)), Vec2(rv(900.0), rv(900.0))).also { vm.selectRectEnd(false) }
                when (rnd.nextInt(8)) {
                    0 -> vm.groupRotate(rnd.nextBoolean())
                    1 -> vm.groupMirror(rnd.nextBoolean())
                    2 -> vm.groupDuplicate(rv(300.0), rv(300.0), rnd.nextInt(1, 4))
                    3 -> vm.groupAlign(pick(Alignment.entries)!!)
                    4 -> vm.groupDistribute(rnd.nextBoolean())
                    5 -> vm.groupTranslate(rv(200.0), rv(200.0))
                    6 -> { vm.beginGroupMove(); vm.groupMoveBy(Vec2(rv(200.0), rv(200.0))); vm.endGroupMove() }
                    else -> vm.groupDelete()
                }
                if (vm.state.value.selectMode) vm.toggleSelectMode()
            },
            "muri" to {
                clearModes()
                anyRoom()?.let { r ->
                    val i = rnd.nextInt(r.wallCount)
                    when (rnd.nextInt(9)) {
                        7 -> vm.setWallRemoved(r.id, i, removed = true)
                        8 -> vm.setWallRemoved(r.id, i, removed = false)
                        0 -> vm.setWallLength(r.id, i, rnd.nextDouble(30.0, 800.0))
                        1 -> vm.setWallThickness(r.id, i, rnd.nextDouble(5.0, 50.0))
                        2 -> vm.splitWall(r.id, i)
                        3 -> vm.mergeWithNext(r.id, i)
                        4 -> vm.setWallCut(r.id, i, rnd.nextBoolean())
                        5 -> vm.setWallHeight(r.id, i, rnd.nextDouble(120.0, 400.0))
                        else -> vm.setCeilingHeight(r.id, rnd.nextDouble(200.0, 400.0))
                    }
                }
            },
            "stanza: tipo, nome, elimina" to {
                clearModes()
                anyRoom()?.let { r ->
                    when (rnd.nextInt(3)) {
                        0 -> vm.changeRoomType(r.id, pick(RoomType.entries)!!)
                        1 -> vm.renameRoom(r.id, "Stanza ${rnd.nextInt(100)}")
                        else -> if (plan().rooms.size > 1) vm.deleteRoom(r.id)
                    }
                }
            },
            "scala: pavimento sopra i primi gradini, ringhiera" to {
                pick(plan().stairs)?.let { st ->
                    vm.updateStair(st.copy(coveredSteps = rnd.nextInt(0, 30), railingAboveFloor = rnd.nextBoolean(), railing = pick(com.sagoma.planimetria.model.StairRailing.entries)!!, wellRailing = pick(com.sagoma.planimetria.model.StairRailing.entries)!!))
                }
            },
            "quote e testi" to {
                clearModes()
                when (rnd.nextInt(4)) {
                    0 -> { vm.startDimension(); repeat(rnd.nextInt(1, 5)) { vm.dimensionTap(Vec2(rv(600.0), rv(600.0))) }; vm.cancelDimension() }
                    1 -> { vm.startText(); vm.textTap(Vec2(rv(600.0), rv(600.0))); vm.state.value.textDialogAt?.let { vm.addAnnotation(it, "Nota ${rnd.nextInt(99)}", 25.0) }; vm.cancelText() }
                    2 -> pick(plan().annotations)?.let { vm.updateAnnotation(it.copy(arrowTo = if (it.arrowTo == null) it.at + Vec2(80.0, 40.0) else null, rotation = if (rnd.nextBoolean()) -90.0 else 0.0)) }
                    else -> pick(plan().dimensions)?.let { vm.updateDimension(it.copy(offset = -it.offset, text = if (rnd.nextBoolean()) "h ${rnd.nextInt(300)}" else null)) }
                }
            },
            "livelli" to {
                val l = pick(com.sagoma.planimetria.model.Layer.entries)!!
                if (rnd.nextBoolean()) vm.setLayerVisible(l, rnd.nextBoolean()) else vm.setLayerLocked(l, rnd.nextBoolean())
            },
            "canc" to { if (!vm.state.value.pasting) { vm.deleteSelected(); vm.cancelDeleteRoom() } },
            "elimina selezionato" to {
                when (val s = vm.state.value.selection) {
                    is Selection.Fixture -> vm.removeFixture(s.roomId, s.fixtureId)
                    is Selection.Opening -> vm.removeOpening(s.roomId, s.openingId)
                    is Selection.Furniture -> vm.removeFurniture(s.furnitureId)
                    is Selection.Column -> vm.removeColumn(s.columnId)
                    is Selection.Beam -> vm.removeBeam(s.beamId)
                    is Selection.FreeWall -> vm.removeFreeWall(s.wallId)
                    is Selection.Stair -> vm.removeStair(s.stairId)
                    else -> Unit
                }
            },
            "annulla/ripeti" to { repeat(rnd.nextInt(1, 5)) { if (rnd.nextBoolean()) vm.undo() else vm.redo() } },
            "piani" to {
                clearModes()
                val b = vm.state.value.building
                when (rnd.nextInt(3)) {
                    0 -> if (b.floors.size < 4) vm.addFloor("Piano ${b.floors.size}", rnd.nextDouble(260.0, 340.0), rnd.nextBoolean())
                    1 -> vm.selectFloor(rnd.nextInt(b.floors.size))
                    else -> if (b.floors.size > 1) vm.deleteFloor(rnd.nextInt(b.floors.size))
                }
            },
            "disegna muri" to {
                clearModes()
                vm.startDrawWalls()
                val o = Vec2(rv(800.0), rv(800.0))
                val n = rnd.nextInt(2, 6)
                repeat(n) { vm.drawWallsTap(o + Vec2(rv(400.0), rv(400.0))) }
                if (rnd.nextBoolean()) vm.drawWallsTyped(rnd.nextDouble(50.0, 500.0), if (rnd.nextBoolean()) rnd.nextDouble(0.0, 360.0) else null)
                vm.drawWallsClose()
                if (vm.state.value.wallDraw?.closing == true) vm.finishDrawWalls(pick(RoomType.entries)!!, 270.0, 25.0) else vm.cancelDrawWalls()
            },
        )

        for (step in 1..steps) {
            val (name, action) = actions[rnd.nextInt(actions.size)]
            log += name
            try {
                action()
                check(vm, step % 5 == 0)
            } catch (e: Throwable) {
                val where = e.stackTrace.firstOrNull { it.className.startsWith("com.sagoma") }
                val key = "${e::class.simpleName}: ${e.message?.take(160)} @ ${where?.fileName}:${where?.lineNumber}"
                if (key !in problems) {
                    problems[key] = "  sessione $seed, passo $step («$name»); ultimi passi: ${log.takeLast(6).joinToString(" → ")}\n  " +
                        e.stackTrace.filter { it.className.startsWith("com.sagoma") }.take(6).joinToString("\n  ")
                }
                return // la sessione non prosegue da uno stato rotto
            }
        }
        // Quanto si è costruito davvero (per essere sicuri che la prova non giri a vuoto).
        val b = vm.state.value.fullBuilding
        fun add(k: String, n: Int) { totals[k] = (totals[k] ?: 0) + n }
        add("piani", b.floors.size)
        for (f in b.floors) {
            add("stanze", f.plan.rooms.size); add("aperture", f.plan.rooms.sumOf { it.openings.size }); add("impianti", f.plan.rooms.sumOf { it.fixtures.size })
            add("arredi", f.plan.furniture.size); add("colonne", f.plan.columns.size); add("travi", f.plan.beams.size)
            add("muri singoli", f.plan.freeWalls.size); add("scale", f.plan.stairs.size)
        }
    }

    private val totals = sortedMapOf<String, Int>()

    /** Controlli dopo ogni passo; `heavy`: anche 3D, collisioni, computo e parapetti. */
    private fun check(vm: EditorViewModel, heavy: Boolean) {
        val s = vm.state.value
        val b = s.fullBuilding
        // Si salva e si rilegge uguale.
        val back = PlanJson.decodeBuilding(PlanJson.encode(b))
        require(back == b) {
            // Il primo oggetto diverso, per capire cosa si perde nel salvataggio.
            val diff = b.floors.zip(back.floors).firstNotNullOfOrNull { (f1, f2) ->
                f1.plan.rooms.zip(f2.plan.rooms).firstNotNullOfOrNull { (r1, r2) ->
                    if (r1 == r2) null else
                        r1.openings.zip(r2.openings).firstOrNull { (a, c) -> a != c }?.let { "apertura:\n  prima $it" }
                            ?: r1.fixtures.zip(r2.fixtures).firstOrNull { (a, c) -> a != c }?.let { "impianto:\n  prima $it" }
                            ?: "stanza:\n  prima $r1\n  dopo  $r2"
                } ?: listOf(
                    f1.plan.furniture to f2.plan.furniture, f1.plan.columns to f2.plan.columns, f1.plan.beams to f2.plan.beams,
                    f1.plan.freeWalls to f2.plan.freeWalls, f1.plan.stairs to f2.plan.stairs, f1.plan.rulers to f2.plan.rulers,
                ).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> "oggetti:\n  prima $x\n  dopo  $y" }
                    ?: if (f1 != f2) "piano:\n  prima ${f1.copy(plan = FloorPlan())}\n  dopo  ${f2.copy(plan = FloorPlan())}" else null
            } ?: "casa: ${b.current} vs ${back.current}"
            "il progetto riletto è diverso da quello salvato — $diff"
        }
        for (f in b.floors) {
            val p = f.plan
            fun <T> unique(what: String, ids: List<T>) = require(ids.size == ids.toSet().size) { "id ripetuti: $what $ids" }
            unique("stanze", p.rooms.map { it.id })
            unique("aperture", p.rooms.flatMap { r -> r.openings.map { it.id } })
            unique("impianti", p.rooms.flatMap { r -> r.fixtures.map { it.id } })
            unique("arredi", p.furniture.map { it.id })
            unique("colonne", p.columns.map { it.id })
            unique("travi", p.beams.map { it.id })
            unique("muri singoli", p.freeWalls.map { it.id })
            unique("scale", p.stairs.map { it.id })
            unique("quote", p.dimensions.map { it.id })
            unique("testi", p.annotations.map { it.id })
            require(p.dimensions.all { it.a.x.isFinite() && it.b.x.isFinite() && it.offset.isFinite() && it.length >= 0.99 }) { "quota non valida" }
            for (r in p.rooms) {
                require(r.points.size >= 3) { "stanza ${r.name} con ${r.points.size} angoli" }
                require(r.points.all { it.x.isFinite() && it.y.isFinite() }) { "coordinate non valide in ${r.name}" }
                require(r.openings.all { it.wallIndex in 0 until r.wallCount }) { "apertura su un muro che non c'è in ${r.name}" }
                require(r.removedWalls.all { it in 0 until r.wallCount }) { "muro eliminato che non c'è in ${r.name}: ${r.removedWalls}" }
                require(r.openings.none { r.isRemoved(it.wallIndex) }) { "apertura su un muro eliminato in ${r.name}" }
                require(r.fixtures.filter { it.kind.mount == Mount.Wall }.all { it.wallIndex in 0 until r.wallCount }) { "impianto su un muro che non c'è in ${r.name}" }
            }
            require(p.furniture.all { it.center.x.isFinite() && it.center.y.isFinite() }) { "arredo con coordinate non valide" }
        }
        // La selezione indica qualcosa che esiste.
        val p = s.plan
        when (val sel = s.selection) {
            is Selection.Wall -> require(p.room(sel.roomId)?.let { sel.index < it.wallCount } == true) { "selezionato un muro che non c'è" }
            is Selection.Opening -> require(p.room(sel.roomId)?.opening(sel.openingId) != null) { "selezionata un'apertura che non c'è" }
            is Selection.Fixture -> require(p.room(sel.roomId)?.fixture(sel.fixtureId) != null) { "selezionato un impianto che non c'è" }
            is Selection.Furniture -> require(p.furniture(sel.furnitureId) != null) { "selezionato un arredo che non c'è" }
            is Selection.Column -> require(p.column(sel.columnId) != null) { "selezionata una colonna che non c'è" }
            is Selection.Beam -> require(p.beam(sel.beamId) != null) { "selezionata una trave che non c'è" }
            is Selection.FreeWall -> require(p.freeWall(sel.wallId) != null) { "selezionato un muro singolo che non c'è" }
            is Selection.Stair -> require(p.stair(sel.stairId) != null) { "selezionata una scala che non c'è" }
            else -> Unit
        }
        s.focusedRoomId?.let { require(p.room(it) != null) { "stanza attiva che non c'è" } }
        if (heavy) {
            val below = b.floors.take(b.current)
            Scene3D.build(p, s.focusedRoomId, null, ceilings = false, levelHeight = s.levelHeight, below = below)
            // Camminando: con il vano scala nel soffitto e il piano di sopra.
            Scene3D.build(p, s.focusedRoomId, null, ceilings = true, levelHeight = s.levelHeight, below = below, above = b.floors.getOrNull(b.current + 1))
            Collisions.of(p)
            Takeoff.of(b)
            p.rooms.filter { it.outdoor }.forEach { Parapets.segments(p, it) }
            // Vuoti delle scale e loro ringhiere (anche con il pavimento sopra i primi gradini).
            for (st in p.stairs) {
                require(com.sagoma.planimetria.geometry.Stairs.well(st, s.levelHeight).isNotEmpty()) { "scala senza vuoto nel solaio" }
                com.sagoma.planimetria.geometry.Stairs.wellRailingRuns(st, s.levelHeight, emptyList())
            }
        }
    }
}
