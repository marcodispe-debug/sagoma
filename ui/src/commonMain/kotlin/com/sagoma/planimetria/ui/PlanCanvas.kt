package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.editor.UnderlayTool
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.Phases
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

private val GridColor = Color(0xFFE9ECEF)
internal val PlanBackgroundColor = Color(0xFFF8F9FA)

@Composable
fun PlanCanvas(state: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier, onPointerDown: () -> Unit = {}) {
    val pointerDown by rememberUpdatedState(onPointerDown)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // Zone di presa più larghe dell'elemento visibile (§13).
    val cornerHitPx = with(density) { 22.dp.toPx() }
    val wallHitPx = with(density) { 14.dp.toPx() }
    val wallStrictPx = with(density) { 6.dp.toPx() }
    val fixtureHitPx = with(density) { 16.dp.toPx() }
    val labelPadPx = with(density) { 8.dp.toPx() }

    // Nome + area solo per la stanza a fuoco (§4).
    // Oggetti che non ci stanno sotto la parete (sottotetti): si ricalcolano solo quando cambia la pianta.
    val collisions = remember(state.plan) { Collisions.of(state.plan) }
    val labels = remember(state.plan, state.focusedRoomId) {
        measureRoomLabels(state.plan, measurer) { it.id == state.focusedRoomId }
    }

    val underlayImage = rememberUnderlayImage(state.plan.underlay)
    val currentState by rememberUpdatedState(state)
    val currentLabels by rememberUpdatedState(labels)

    fun labelRect(l: RoomLabel, cam: Camera): Rect = l.rect(cam, labelPadPx)

    /** Cosa c'è sotto il dito: prima la stanza a fuoco, poi le altre (dall'ultima disegnata). */
    fun hitTest(p: Offset): DragTarget {
        val s = currentState
        val cam = s.camera
        val ordered = s.plan.rooms.sortedByDescending { if (it.id == s.focusedRoomId) Int.MAX_VALUE else s.plan.rooms.indexOf(it) }
        val world = cam.toWorld(p.x, p.y)
        val layers = s.building.layers
        // Il metro sta sopra la pianta: ha la precedenza (l'ultimo creato per primo).
        if (layers.editable(Layer.Rulers)) for (r in s.plan.rulers.asReversed()) RulerGeom(r, cam, density).hitTest(p)?.let { return it }
        // Testi e quote (sopra il disegno): maniglie dell'oggetto selezionato, poi il testo o la linea di quota.
        if (layers.editable(Layer.Texts)) {
            (s.selection as? Selection.Annotation)?.let { sel ->
                s.plan.annotation(sel.annotationId)?.arrowTo?.let { tip ->
                    if (world.distanceTo(tip) * cam.scale <= cornerHitPx) return DragTarget.AnnotationArrow(sel.annotationId)
                }
            }
            s.plan.annotations.asReversed().firstOrNull { noteContains(it, measureNote(it, cam, measurer, density), cam, p, labelPadPx) }
                ?.let { return DragTarget.AnnotationBody(it.id) }
        }
        if (layers.editable(Layer.Dimensions)) {
            (s.selection as? Selection.Dimension)?.let { sel ->
                s.plan.dimension(sel.dimensionId)?.let { d ->
                    if (world.distanceTo(d.a) * cam.scale <= cornerHitPx) return DragTarget.DimensionEnd(d.id, 0)
                    if (world.distanceTo(d.b) * cam.scale <= cornerHitPx) return DragTarget.DimensionEnd(d.id, 1)
                }
            }
            s.plan.dimensions.asReversed().firstOrNull { Polygon.distanceToSegment(world, it.lineA, it.lineB) * cam.scale <= wallHitPx }
                ?.let { return DragTarget.DimensionLine(it.id) }
        }
        // Pallino del sottotetto sul muro selezionato: prima di angoli e muri, che sono proprio lì sotto.
        cutHandle(s)?.let { h ->
            val sel = s.selection as Selection.Wall
            if (world.distanceTo(h) * cam.scale <= cornerHitPx) return DragTarget.CutStart(sel.roomId, sel.index)
        }
        // Estremità della trave selezionata: per allungarla.
        (s.selection as? Selection.Beam)?.let { sel ->
            s.plan.beam(sel.beamId)?.let { b ->
                listOf(b.start, b.end).forEachIndexed { k, p -> if (world.distanceTo(p) * cam.scale <= cornerHitPx) return DragTarget.BeamEnd(b.id, k) }
            }
        }
        // Estremità del muro singolo selezionato: per allungarlo o girarlo.
        (s.selection as? Selection.FreeWall)?.let { sel ->
            s.plan.freeWall(sel.wallId)?.let { w ->
                listOf(w.start, w.end).forEachIndexed { k, p -> if (world.distanceTo(p) * cam.scale <= cornerHitPx) return DragTarget.FreeWallEnd(w.id, k) }
            }
        }
        // Maniglia di rotazione dell'arredo selezionato.
        (s.selection as? Selection.Furniture)?.let { sel ->
            s.plan.furniture(sel.furnitureId)?.let { f ->
                if (world.distanceTo(Furnishings.handle(f)) * cam.scale <= cornerHitPx) return DragTarget.FurnitureRotate(f.id)
            }
        }
        /** Il dito è su un angolo o proprio su un muro: quelli hanno la precedenza sugli oggetti accostati. */
        fun onWall() = s.plan.rooms.any { r ->
            r.points.any { it.distanceTo(world) * cam.scale <= cornerHitPx } ||
                (0 until r.wallCount).any { Polygon.distanceToSegment(world, r.wallStart(it), r.wallEnd(it)) * cam.scale <= wallStrictPx + Room.WALL_THICKNESS / 2 * cam.scale }
        }
        // Arredi (tranne i tappeti, che stanno sotto a tutto): l'ultimo messo per primo.
        s.plan.furniture.takeIf { s.building.layers.editable(Layer.Furniture) }.orEmpty().asReversed().firstOrNull { it.height >= 3 && Furnishings.contains(it, world) }?.let { f ->
            if (s.selection == Selection.Furniture(f.id) || !onWall()) return DragTarget.FurnitureBody(f.id)
        }
        // Muri singoli: si prendono anche un po' fuori dal loro spessore, come i muri.
        s.plan.freeWalls.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().firstOrNull {
            Polygon.distanceToSegment(world, it.start, it.end) <= it.thickness / 2 + wallStrictPx / cam.scale
        }?.let { return DragTarget.FreeWallBody(it.id) }
        // Colonne, poi travi: stanno sopra le stanze (le colonne anche sui muri, come pilastri).
        s.plan.columns.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().firstOrNull { c ->
            Structure.contains(c, world) || world.distanceTo(c.center) * cam.scale <= fixtureHitPx
        }?.let { return DragTarget.Column(it.id) }
        s.plan.beams.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().firstOrNull { Structure.contains(it, world) }?.let { b ->
            val onWall = s.plan.rooms.any { r ->
                r.points.any { it.distanceTo(world) * cam.scale <= cornerHitPx } ||
                    (0 until r.wallCount).any { Polygon.distanceToSegment(world, r.wallStart(it), r.wallEnd(it)) * cam.scale <= wallStrictPx + Room.WALL_THICKNESS / 2 * cam.scale }
            }
            if (!onWall) return DragTarget.BeamBody(b.id)
        }
        // Scale: sopra il pavimento delle stanze, ma angoli e muri (spesso proprio accanto) hanno la precedenza.
        s.plan.stairs.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().firstOrNull { Stairs.layout(it, s.levelHeight).contains(world) }?.let { st ->
            val onWall = s.plan.rooms.any { r ->
                r.points.any { it.distanceTo(world) * cam.scale <= cornerHitPx } ||
                    (0 until r.wallCount).any { Polygon.distanceToSegment(world, r.wallStart(it), r.wallEnd(it)) * cam.scale <= wallStrictPx + Room.WALL_THICKNESS / 2 * cam.scale }
            }
            if (!onWall) return DragTarget.Stair(st.id)
        }
        // Vano di una scala che sale dal piano di sotto: si tocca per scegliere la ringhiera.
        s.planBelow?.stairs?.takeIf { s.building.layers.editable(Layer.Structure) }?.asReversed()?.firstOrNull { st -> Stairs.well(st, s.levelHeightBelow).any { Polygon.contains(it, world) } }?.let { st ->
            val onWall = s.plan.rooms.any { r ->
                r.points.any { it.distanceTo(world) * cam.scale <= cornerHitPx } ||
                    (0 until r.wallCount).any { Polygon.distanceToSegment(world, r.wallStart(it), r.wallEnd(it)) * cam.scale <= wallStrictPx + Room.WALL_THICKNESS / 2 * cam.scale }
            }
            if (!onWall) return DragTarget.StairWell(st.id)
        }
        for (room in ordered) {
            val cornerIdx = room.points.indices
                .map { it to Offset(cam.toScreenX(room.points[it].x), cam.toScreenY(room.points[it].y)) }
                .map { (i, o) -> i to (o - p).getDistance() }
                .filter { it.second <= cornerHitPx }
                .minByOrNull { it.second }?.first
            if (cornerIdx != null) return DragTarget.Corner(room.id, cornerIdx)
            // Impianti: prima di aperture e muri, perché sono piccoli e spesso proprio sul muro.
            val fixtureHit = room.fixtures
                .filter { s.building.layers.editable(Layer.Fixtures) && (it.kind.mount == Mount.Ceiling || it.wallIndex < room.wallCount) }
                .map { f ->
                    val d = when {
                        f.kind.linear -> Fixtures.linearEnds(f).let { (a, b) -> Polygon.distanceToSegment(world, a, b) }
                        f.kind == FixtureKind.Radiator -> radiatorSpan(room, f).let { (a, b) ->
                            Polygon.distanceToSegment(world, a, b) - Fixtures.RADIATOR_DEPTH / 2
                        }
                        else -> world.distanceTo(Fixtures.center(room, f))
                    }
                    f to d * cam.scale
                }
                .filter { it.second <= fixtureHitPx }
                .minByOrNull { it.second }?.first
            if (fixtureHit != null) return DragTarget.Fixture(room.id, fixtureHit.id)
            // Aperture prima di etichetta e muro: si afferra la loro linea (§5).
            val openingHit = room.openings
                .filter { it.wallIndex < room.wallCount }
                .map { o -> o to Openings.span(room, o).let { (a, b) -> Polygon.distanceToSegment(world, a, b) * cam.scale } }
                .filter { it.second <= wallHitPx + Room.WALL_THICKNESS / 2 * cam.scale }
                .minByOrNull { it.second }?.first
            if (openingHit != null) return DragTarget.Opening(room.id, openingHit.id)
            val wallDistances = (0 until room.wallCount)
                .map { it to Polygon.distanceToSegment(world, room.wallStart(it), room.wallEnd(it)) * cam.scale }
            // Tocco proprio sul muro: vince sull'etichetta, che in una stanza stretta copre i muri laterali.
            wallDistances.filter { it.second <= wallStrictPx + Room.WALL_THICKNESS / 2 * cam.scale }
                .minByOrNull { it.second }?.let { return DragTarget.Wall(room.id, it.first) }
            currentLabels.firstOrNull { it.room.id == room.id }?.let { l ->
                if (labelRect(l, cam).contains(p)) return DragTarget.RoomLabel(room.id)
            }
            val wallIdx = wallDistances
                .filter { it.second <= wallHitPx + Room.WALL_THICKNESS / 2 * cam.scale }
                .minByOrNull { it.second }?.first
            if (wallIdx != null) return DragTarget.Wall(room.id, wallIdx)
        }
        s.plan.furniture.takeIf { s.building.layers.editable(Layer.Furniture) }.orEmpty().asReversed().firstOrNull { Furnishings.contains(it, world) }?.let { return DragTarget.FurnitureBody(it.id) }
        for (room in ordered) if (Polygon.contains(room.points, world)) return DragTarget.RoomInterior(room.id)
        return DragTarget.Background
    }

    /**
     * Tutti gli oggetti sotto il dito (non solo il primo): arredi, strutture, scale, impianti, aperture e, in
     * fondo, i muri. Per far scegliere quale selezionare quando sono sovrapposti.
     */
    fun hitAll(p: Offset): List<DragTarget> {
        val s = currentState
        val cam = s.camera
        val world = cam.toWorld(p.x, p.y)
        val objects = mutableListOf<DragTarget>()
        val walls = mutableListOf<DragTarget>()
        s.plan.furniture.takeIf { s.building.layers.editable(Layer.Furniture) }.orEmpty().asReversed().filter { Furnishings.contains(it, world) }.forEach { objects += DragTarget.FurnitureBody(it.id) }
        s.plan.freeWalls.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().filter { Polygon.distanceToSegment(world, it.start, it.end) <= it.thickness / 2 + wallStrictPx / cam.scale }
            .forEach { objects += DragTarget.FreeWallBody(it.id) }
        s.plan.columns.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().filter { Structure.contains(it, world) || world.distanceTo(it.center) * cam.scale <= fixtureHitPx }
            .forEach { objects += DragTarget.Column(it.id) }
        s.plan.beams.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().filter { Structure.contains(it, world) }.forEach { objects += DragTarget.BeamBody(it.id) }
        s.plan.stairs.takeIf { s.building.layers.editable(Layer.Structure) }.orEmpty().asReversed().filter { Stairs.layout(it, s.levelHeight).contains(world) }.forEach { objects += DragTarget.Stair(it.id) }
        for (room in s.plan.rooms) {
            room.fixtures.filter { s.building.layers.editable(Layer.Fixtures) && (it.kind.mount == Mount.Ceiling || it.wallIndex < room.wallCount) }.forEach { f ->
                val d = when {
                    f.kind.linear -> Fixtures.linearEnds(f).let { (a, b) -> Polygon.distanceToSegment(world, a, b) }
                    f.kind == FixtureKind.Radiator -> radiatorSpan(room, f).let { (a, b) -> Polygon.distanceToSegment(world, a, b) - Fixtures.RADIATOR_DEPTH / 2 }
                    else -> world.distanceTo(Fixtures.center(room, f))
                }
                if (d * cam.scale <= fixtureHitPx) objects += DragTarget.Fixture(room.id, f.id)
            }
            room.openings.filter { it.wallIndex < room.wallCount }.forEach { o ->
                val d = Openings.span(room, o).let { (a, b) -> Polygon.distanceToSegment(world, a, b) * cam.scale }
                if (d <= wallHitPx + Room.WALL_THICKNESS / 2 * cam.scale) objects += DragTarget.Opening(room.id, o.id)
            }
            for (i in 0 until room.wallCount) {
                val d = Polygon.distanceToSegment(world, room.wallStart(i), room.wallEnd(i)) * cam.scale
                if (d <= wallHitPx + Room.WALL_THICKNESS / 2 * cam.scale) walls += DragTarget.Wall(room.id, i)
            }
        }
        return objects + walls
    }

    // Scelta tra oggetti sovrapposti (es. finestra e calorifero sullo stesso punto del muro).
    var overlapChoice by remember { mutableStateOf<Pair<Vec2, List<DragTarget>>?>(null) }
    overlapChoice?.let { (at, targets) ->
        OverlapChooser(state, targets, onPick = { overlapChoice = null; vm.onTap(it, at) }, onDismiss = { overlapChoice = null })
    }

    Box(
        modifier
            // La pianta (e i campi sui muri) non deve mai disegnare sopra barre e schede quando la vista scorre.
            .clipToBounds()
            .onSizeChanged { vm.setViewport(it.width.toFloat(), it.height.toFloat(), with(density) { 24.dp.toPx() }) }
            // Rotellina del mouse (§11).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Scroll) {
                            val ch = e.changes.first()
                            vm.zoomBy(1.1f.pow(-wheelNotches(ch.scrollDelta.y)), ch.position.x, ch.position.y)
                            ch.consume()
                        }
                        // Disegno dei muri con il mouse: il muro successivo segue il puntatore.
                        if (e.type == PointerEventType.Move && currentState.wallDraw != null && e.changes.none { it.pressed }) {
                            val ch = e.changes.first()
                            vm.drawWallsCursor(currentState.camera.toWorld(ch.position.x, ch.position.y))
                        }
                        // Incolla con il mouse: la copia tratteggiata segue il puntatore.
                        if (e.type == PointerEventType.Move && currentState.pasting && e.changes.none { it.pressed }) {
                            val ch = e.changes.first()
                            vm.pasteCursor(currentState.camera.toWorld(ch.position.x, ch.position.y))
                        }
                        // Strumento "Quota" con il mouse: l'anteprima segue il puntatore.
                        if (e.type == PointerEventType.Move && currentState.dimensionDraw != null && e.changes.none { it.pressed }) {
                            val ch = e.changes.first()
                            vm.dimensionCursor(currentState.camera.toWorld(ch.position.x, ch.position.y))
                        }
                        // Cosa c'è sotto il mouse: Ctrl+V incolla lì (sul muro puntato per impianti e finestre).
                        if (e.type == PointerEventType.Move && e.changes.none { it.pressed }) {
                            val ch = e.changes.first()
                            vm.setHover(currentState.camera.toWorld(ch.position.x, ch.position.y), hitTest(ch.position))
                        }
                        if (e.type == PointerEventType.Exit) vm.setHover(null, null)
                    }
                }
            },
    ) {
        val gestures = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // Un clic sulla pianta le ridà la tastiera (scorciatoie come Ctrl+C / Ctrl+V).
                    pointerDown()
                    // Strumenti della pianta di sfondo: il disegno non si tocca.
                    val tool = currentState.underlayTool
                    val moveUnderlay = tool == UnderlayTool.Move && currentState.plan.underlay != null
                    // Disegno dei muri, quote e testi: i tocchi mettono i punti, i trascinamenti spostano la vista.
                    val drawing = currentState.wallDraw != null || currentState.dimensionDraw != null || currentState.textPlacing
                    val target = if (tool != null || drawing) DragTarget.Background else hitTest(down.position)
                    val startCam = currentState.camera
                    var dragging = false
                    var multiTouch = false
                    var total = Offset.Zero
                    // Selezione multipla: in modalità Seleziona (o con Ctrl sul computer) i tocchi aggiungono o tolgono
                    // oggetti; trascinando un oggetto selezionato si sposta il gruppo, altrove si disegna il rettangolo.
                    val mods = currentEvent.keyboardModifiers
                    val ctrl = mods.isCtrlPressed || mods.isMetaPressed
                    val selecting = currentState.selectMode && tool == null && !drawing
                    val item = if (selecting || ctrl) vm.groupItemOf(target) else null
                    val groupMove = selecting && item != null && item in currentState.multi
                    val rectSelect = selecting && !groupMove
                    val rectStart = startCam.toWorld(down.position.x, down.position.y)
                    val draggable = !selecting && !ctrl && (target is DragTarget.Corner || target is DragTarget.Wall || target is DragTarget.RoomLabel ||
                        target is DragTarget.Opening || target is DragTarget.Fixture || target is DragTarget.CutStart ||
                        target is DragTarget.RulerEnd || target is DragTarget.RulerBody || target is DragTarget.RulerRotate ||
                        target is DragTarget.Stair || target is DragTarget.Column || target is DragTarget.BeamBody || target is DragTarget.BeamEnd ||
                        target is DragTarget.FreeWallBody || target is DragTarget.FreeWallEnd ||
                        target is DragTarget.FurnitureBody || target is DragTarget.FurnitureRotate ||
                        target is DragTarget.DimensionLine || target is DragTarget.DimensionEnd ||
                        target is DragTarget.AnnotationBody || target is DragTarget.AnnotationArrow)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            // Pinch: zoom + pan a due dita; un eventuale trascinamento in corso si chiude.
                            if (dragging && draggable) vm.endDrag()
                            if (dragging && moveUnderlay && !multiTouch) vm.endUnderlayMove()
                            if (dragging && groupMove && !multiTouch) vm.endGroupMove()
                            if (rectSelect && !multiTouch) vm.selectRectUpdate(rectStart, rectStart)
                            multiTouch = true
                            dragging = true
                            val c = event.calculateCentroid()
                            vm.zoomBy(event.calculateZoom(), c.x, c.y)
                            val pan = event.calculatePan()
                            vm.pan(pan.x, pan.y)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        if (multiTouch) continue
                        val ch = event.changes.first { it.id == down.id || it.pressed }
                        val delta = ch.position - ch.previousPosition
                        total += delta
                        if (!dragging && total.getDistance() > touchSlop) {
                            dragging = true
                            if (draggable) vm.beginDrag(target, startCam.toWorld(down.position.x, down.position.y))
                            if (moveUnderlay) vm.beginUnderlayMove()
                            if (groupMove) vm.beginGroupMove()
                        }
                        if (dragging) {
                            if (groupMove) {
                                vm.groupMoveBy(Vec2((total.x / startCam.scale).toDouble(), (total.y / startCam.scale).toDouble()))
                            } else if (rectSelect) {
                                vm.selectRectUpdate(rectStart, startCam.toWorld(ch.position.x, ch.position.y))
                            } else if (moveUnderlay) {
                                vm.moveUnderlay(Vec2((total.x / startCam.scale).toDouble(), (total.y / startCam.scale).toDouble()))
                            } else if (draggable) {
                                vm.dragBy(target, Vec2((total.x / startCam.scale).toDouble(), (total.y / startCam.scale).toDouble()))
                            } else {
                                vm.pan(delta.x, delta.y)
                            }
                            ch.consume()
                        }
                    }
                    if (dragging && draggable && !multiTouch) vm.endDrag()
                    if (dragging && moveUnderlay && !multiTouch) vm.endUnderlayMove()
                    if (dragging && groupMove && !multiTouch) vm.endGroupMove()
                    if (dragging && rectSelect && !multiTouch) vm.selectRectEnd(add = mods.isShiftPressed)
                    if (!dragging) {
                        val at = startCam.toWorld(down.position.x, down.position.y)
                        when {
                            drawing -> when {
                                currentState.dimensionDraw != null -> vm.dimensionTap(at)
                                currentState.textPlacing -> vm.textTap(at)
                                else -> vm.drawWallsTap(at)
                            }
                            ctrl && item != null -> vm.toggleInMulti(target)
                            selecting -> if (item != null) vm.toggleInMulti(target) else vm.clearMulti()
                            tool == null -> {
                                // Più oggetti uno sull'altro (almeno due, muri esclusi): si sceglie da un elenco, muro compreso.
                                // Non mentre si posiziona o si incolla qualcosa, né toccando maniglie e angoli.
                                val s = currentState
                                val placing = s.pendingOpening != null || s.pendingFixture != null || s.measure != null || s.pasting
                                val handle = target is DragTarget.Corner || target is DragTarget.CutStart || target is DragTarget.BeamEnd ||
                                    target is DragTarget.FreeWallEnd || target is DragTarget.FurnitureRotate || target is DragTarget.RulerEnd ||
                                    target is DragTarget.RulerBody || target is DragTarget.RulerRotate || target is DragTarget.RulerDelete ||
                                    target is DragTarget.DimensionLine || target is DragTarget.DimensionEnd ||
                                    target is DragTarget.AnnotationBody || target is DragTarget.AnnotationArrow
                                val all = if (placing || handle) emptyList() else hitAll(down.position)
                                if (all.count { it !is DragTarget.Wall } >= 2) overlapChoice = at to all
                                else vm.onTap(target, at)
                            }
                            tool is UnderlayTool.Calibrate -> vm.underlayTap(at)
                            else -> {}
                        }
                    }
                }
            }
        // I gesti stanno sul Canvas: i campi lunghezza sovrapposti ricevono i propri tocchi senza interferenze.
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val layers = state.building.layers
            // Stato di fatto / progetto: la pianta come appare nella vista scelta (comparativa = tutta, a colori).
            val shown = if (state.phaseView == PhaseView.Compare) state else state.copy(plan = Phases.view(state.plan, state.phaseView))
            drawRect(PlanBackgroundColor)
            drawGrid(state.camera)
            if (layers.visible(Layer.Underlay)) underlayImage?.let { img -> state.plan.underlay?.let { drawUnderlay(it, img, state.camera) } }
            drawFloorBelow(state.planBelow, state.camera)
            drawPlan(shown, measurer)
            if (layers.visible(Layer.Structure)) drawStairs(state, measurer)
            if (layers.visible(Layer.Furniture)) drawFurniture(state.plan.furniture, state.camera, (state.selection as? Selection.Furniture)?.furnitureId)
            // Quote dei muri (solo lettura: le lunghezze si cambiano dalla tendina Info della stanza).
            if (layers.visible(Layer.WallLengths)) drawWallDimensions(state.plan, state.camera, measurer, avoid = labels.map { it.rect(state.camera, labelPadPx) })
            drawWallCuts(state)
            drawRoomLabels(labels, state.camera, labelPadPx) { it.id == state.focusedRoomId }
            // Sopra le etichette: una colonna appena messa sta proprio al centro della stanza.
            if (layers.visible(Layer.Structure)) drawStructures(shown, measurer)
            drawMeasure(state, measurer)
            if (layers.visible(Layer.Fixtures)) drawAllFixtures(shown)
            drawCollisionWarnings(state, collisions, measurer)
            // Quote manuali e testi.
            drawNotes(
                state.plan, state.camera, measurer, PlanBackgroundColor,
                showDimensions = layers.visible(Layer.Dimensions), showTexts = layers.visible(Layer.Texts),
                selectedDimension = (state.selection as? Selection.Dimension)?.dimensionId,
                selectedText = (state.selection as? Selection.Annotation)?.annotationId,
            )
            state.dimensionDraw?.let { drawDimensionDraft(it, state.camera, measurer) }
            if (layers.visible(Layer.Rulers)) for (r in state.plan.rulers) {
                drawRuler(RulerGeom(r, state.camera, this), state.camera, r.id == state.rotatingRulerId, measurer)
            }
            (state.underlayTool as? UnderlayTool.Calibrate)?.let { drawCalibration(it, state.camera, measurer) }
            state.wallDraw?.let { drawWallDraft(it, state.camera, measurer) }
            drawMultiSelection(state, levelHeight = state.levelHeight)
            drawPastePreview(state)
            drawSnapGuides(state.snapGuides, state.camera, measurer)
        }
        // Vicino ai bordi dello schermo i trascinamenti di angoli e metro non devono diventare il gesto "indietro".
        val cam = state.camera
        GestureExclusionZones(
            state.plan.room(state.focusedRoomId)?.points.orEmpty().map { Offset(cam.toScreenX(it.x), cam.toScreenY(it.y)) } +
                state.plan.rulers.flatMap { r -> RulerGeom(r, cam, density).let { listOf(it.start, it.end, it.rotateHandle, it.deleteHandle) } },
        )
    }
}

/** Griglia di sfondo ogni metro (ogni 10 cm se molto ingranditi). */
private fun DrawScope.drawGrid(cam: Camera) {
    val stepCm = if (cam.scale > 4f) 10.0 else if (cam.scale > 0.3f) 100.0 else 500.0
    val tl = cam.toWorld(0f, 0f)
    val br = cam.toWorld(size.width, size.height)
    var x = kotlin.math.floor(tl.x / stepCm) * stepCm
    while (x <= br.x) {
        val sx = cam.toScreenX(x)
        drawLine(GridColor, Offset(sx, 0f), Offset(sx, size.height), 1f)
        x += stepCm
    }
    var y = kotlin.math.floor(tl.y / stepCm) * stepCm
    while (y <= br.y) {
        val sy = cam.toScreenY(y)
        drawLine(GridColor, Offset(0f, sy), Offset(size.width, sy), 1f)
        y += stepCm
    }
}

/** Formattazione cm senza decimali inutili. */
internal fun formatCm(v: Double): String =
    if (abs(v - v.roundToLong()) < 0.05) v.roundToLong().toString() else com.sagoma.planimetria.geometry.formatDecimal(v, 1)
