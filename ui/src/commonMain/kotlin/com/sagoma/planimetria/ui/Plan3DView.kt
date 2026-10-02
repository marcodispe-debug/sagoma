package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.pow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sagoma.planimetria.editor.DragTarget
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Camera3D
import com.sagoma.planimetria.geometry.Pick
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.geometry.at
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Stato della vista 3D che deve sopravvivere alle ricomposizioni: telecamera, renderer e superficie
 * OpenGL, modalità (orbita o camminata) e joystick. I pulsanti della vista 3D stanno in [View3DControls].
 */
/** Tasti che muovono la telecamera finché restano premuti. */
private val MOVE_KEYS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.W, Key.A, Key.S, Key.D,
    Key.PageUp, Key.PageDown, Key.Plus, Key.Equals, Key.NumPadAdd, Key.Minus, Key.NumPadSubtract,
)

class View3DState {
    val camera = Camera3D()
    /** Renderer della superficie visibile (null quando la vista 3D è chiusa). */
    private var renderer: SceneRenderer? = null
    private var scene: Scene3D? = null
    var walking by mutableStateOf(false)
        private set
    /** Spinta del joystick: x a destra, y in avanti, tra −1 e 1. */
    var joystick by mutableStateOf(Offset.Zero)
    var fitted = false
    /** Proporzioni della vista (larghezza / altezza), per inquadrare tutta la pianta. */
    var aspect = 1.0

    /** Manda la telecamera al renderer e ridisegna. */
    /** Versione pro: ora del giorno (6:30 = 6.5) e luci della casa ("auto", "on", "off"). */
    var hour by mutableStateOf(11f)
    var lamps by mutableStateOf("auto")
    /** Cursore dell'ora aperto. */
    var timePanel by mutableStateOf(false)

    fun refresh() {
        renderer?.setDaylight(hour, lamps)
        renderer?.setCamera(camera.position, camera.position + camera.forward, camera.up, camera.fovY, camera.near, walking)
    }

    /** Pulsante delle luci: automatiche → accese → spente → automatiche. */
    fun nextLamps() {
        lamps = when (lamps) { "auto" -> "on"; "on" -> "off"; else -> "auto" }
        refresh()
    }

    /** Crea il renderer della vista 3D (quello della piattaforma e della versione). */
    fun attach(platform: Platform): SceneRenderer {
        renderer?.release()
        val r = platform.createSceneRenderer()
        renderer = r
        scene?.let(r::setScene)
        refresh()
        return r
    }

    fun detach() {
        renderer?.release()
        renderer = null
    }

    fun setScene(s: Scene3D) {
        scene = s
        renderer?.setScene(s)
        refresh()
    }

    fun onPause() { renderer?.onPause() }
    fun onResume() { renderer?.onResume() }

    fun fit(scene: Scene3D) {
        scene.bounds?.let { camera.fit(it, aspect) }
        fitted = true
        refresh()
    }

    /** "Adatta" in 3D: si torna a vedere tutta la pianta dall'alto. */
    fun fitPlan(plan: FloorPlan) {
        if (walking) stopWalking()
        com.sagoma.planimetria.geometry.Openings.planBounds(plan)?.let { camera.fit(it, aspect) }
        refresh()
    }

    /** Pulsanti − / +: in orbita avvicinano o allontanano, camminando fanno un passo avanti o indietro. */
    fun zoom(factor: Float) {
        if (walking) {
            val step = camera.walkForward * (if (factor > 1) 60.0 else -60.0)
            camera.eye = camera.eye + step
        } else {
            camera.distance = (camera.distance / factor).coerceIn(80.0, 60_000.0)
        }
        refresh()
    }

    /** Punto occupato da muri o mobili (chi cammina non ci passa); lo imposta la vista con la scena attuale. */
    var isBlocked: (Vec2) -> Boolean = { false }

    /** Passo di chi cammina: contro un muro si scivola lungo di esso invece di fermarsi del tutto. */
    fun walkMove(move: Vec3) {
        val eye = camera.eye
        camera.eye = listOf(eye + move, eye + Vec3(move.x, 0.0, 0.0), eye + Vec3(0.0, 0.0, move.z))
            .firstOrNull { !isBlocked(it.flat) } ?: eye
    }

    /** Destra dello schermo sul pavimento. */
    private val flatRight: Vec3 get() = camera.right.let { Vec3(it.x, 0.0, it.z) }.normalized()

    /**
     * Spostamento con due dita o col tasto destro del mouse, `delta` in pixel su una vista alta `height`:
     * dall'alto la vista scorre parallela al pavimento; camminando si va avanti e di lato.
     */
    fun panBy(delta: Offset, height: Float) {
        if (walking) {
            walkMove(camera.walkForward * (delta.y * 1.5) - flatRight * (delta.x * 1.5))
        } else {
            val k = camera.distance / height * 1.2
            val ahead = Vec3(camera.forward.x, 0.0, camera.forward.z).normalized()
            camera.target = camera.target - flatRight * (delta.x * k) + ahead * (delta.y * k)
        }
        refresh()
    }

    /** Tasti premuti in questo momento (frecce, WASD, PagSu/PagGiù, + e −, Maiusc). */
    val keys = mutableStateSetOf<Key>()

    /** C'è qualcosa che muove la telecamera a ogni fotogramma (tasti tenuti o joystick). */
    val moving: Boolean get() = keys.any { it in MOVE_KEYS } || joystick != Offset.Zero

    /** Un fotogramma di movimento, `dt` secondi dopo il precedente. */
    fun step(dt: Double) {
        fun held(a: Key, b: Key? = null, c: Key? = null) = a in keys || (b != null && b in keys) || (c != null && c in keys)
        val fast = if (held(Key.ShiftLeft, Key.ShiftRight)) 2.5 else 1.0
        val shift = held(Key.ShiftLeft, Key.ShiftRight)
        if (walking) {
            val speed = 170.0 * dt * fast // cm al secondo
            var ahead = joystick.y.toDouble()
            var side = joystick.x.toDouble()
            if (held(Key.DirectionUp, Key.W)) ahead += 1.0
            if (held(Key.DirectionDown, Key.S)) ahead -= 1.0
            if (held(Key.A)) side -= 1.0
            if (held(Key.D)) side += 1.0
            // ← → girano lo sguardo; con Maiusc fanno un passo di lato.
            val turn = (if (held(Key.DirectionLeft)) 1.0 else 0.0) - (if (held(Key.DirectionRight)) 1.0 else 0.0)
            if (shift) side -= turn else camera.walkYaw += turn * 90.0 * dt
            if (held(Key.PageUp)) camera.walkPitch = (camera.walkPitch + 60.0 * dt).coerceIn(-70.0, 70.0)
            if (held(Key.PageDown)) camera.walkPitch = (camera.walkPitch - 60.0 * dt).coerceIn(-70.0, 70.0)
            if (ahead != 0.0 || side != 0.0) walkMove(camera.walkForward * (ahead * speed) + flatRight * (side * speed))
        } else {
            // Stessi comandi della camminata: avanti/indietro e di lato si sposta la vista sul pavimento
            // (più veloce quanto più si è lontani), ← → girano, PagSu/PagGiù alzano o abbassano lo sguardo.
            val speed = camera.distance * 0.6 * dt * fast
            var ahead = joystick.y.toDouble()
            var side = joystick.x.toDouble()
            if (held(Key.DirectionUp, Key.W)) ahead += 1.0
            if (held(Key.DirectionDown, Key.S)) ahead -= 1.0
            if (held(Key.A)) side -= 1.0
            if (held(Key.D)) side += 1.0
            val turn = (if (held(Key.DirectionLeft)) 1.0 else 0.0) - (if (held(Key.DirectionRight)) 1.0 else 0.0)
            if (shift) side -= turn else camera.yaw += turn * 90.0 * dt
            if (held(Key.PageUp)) camera.pitch = (camera.pitch - 45.0 * dt).coerceIn(8.0, 89.0)
            if (held(Key.PageDown)) camera.pitch = (camera.pitch + 45.0 * dt).coerceIn(8.0, 89.0)
            if (ahead != 0.0 || side != 0.0) {
                val flatAhead = Vec3(camera.forward.x, 0.0, camera.forward.z).normalized()
                camera.target = camera.target + flatAhead * (ahead * speed) + flatRight * (side * speed)
            }
        }
        if (held(Key.Plus, Key.Equals, Key.NumPadAdd)) zoomContinuous(dt)
        if (held(Key.Minus, Key.NumPadSubtract)) zoomContinuous(-dt)
        refresh()
    }

    /** Zoom tenendo premuto + o −: circa un raddoppio al secondo. */
    private fun zoomContinuous(dt: Double) {
        if (walking) walkMove(camera.walkForward * (170.0 * dt))
        else camera.distance = (camera.distance * 0.5.pow(dt)).coerceIn(80.0, 60_000.0)
    }

    /**
     * Rotellina del mouse (o scorrimento a due dita del touchpad): `notches` scatti, negativi verso lo
     * schermo. In orbita avvicina o allontana del 12% a scatto; camminando fa un passo di 40 cm.
     */
    fun wheel(notches: Float) {
        if (notches == 0f) return
        if (walking) {
            camera.eye = camera.eye + camera.walkForward * (-notches * 40.0)
        } else {
            camera.distance = (camera.distance * 1.12.pow(notches.toDouble())).coerceIn(80.0, 60_000.0)
        }
        refresh()
    }

    /**
     * Camminata nella stanza selezionata (o nella prima): si parte in fondo alla stanza, guardandola
     * per il lungo e un poco verso il basso, così si vedono pavimento, muri e aperture.
     */
    fun startWalking(plan: FloorPlan, roomId: Long?) {
        val room = plan.room(roomId) ?: plan.rooms.firstOrNull() ?: return
        val center = Polygon.labelPoint(room.points)
        val b = Polygon.bounds(room.points)
        val back = Vec2(center.x, b.maxY - 45)
        val start = if (Polygon.contains(room.points, back)) back else center
        camera.eye = start.at(Scene3D.EYE_HEIGHT)
        camera.walkYaw = 0.0
        camera.walkPitch = -10.0
        camera.mode = Camera3D.Mode.Walk
        walking = true
        refresh()
    }

    /** Solo per le prove: modalità camminata senza scegliere una stanza. */
    internal fun startWalkingForTest() {
        walking = true
    }

    fun stopWalking() {
        camera.mode = Camera3D.Mode.Orbit
        walking = false
        joystick = Offset.Zero
        refresh()
    }
}

private fun Selection.toPick(): Pick? = when (this) {
    is Selection.Wall -> Pick.Wall(roomId, index)
    is Selection.Opening -> Pick.Opening(roomId, openingId)
    is Selection.Fixture -> Pick.Fixture(roomId, fixtureId)
    is Selection.Stair -> Pick.Stair(stairId)
    is Selection.StairWell -> Pick.StairWell(stairId)
    is Selection.Column -> Pick.Column(columnId)
    is Selection.Beam -> Pick.Beam(beamId)
    is Selection.FreeWall -> Pick.FreeWall(wallId)
    is Selection.Furniture -> Pick.Furniture(furnitureId)
    // Quote e testi non sono nel 3D.
    is Selection.Dimension, is Selection.Annotation -> null
}

/** Cosa si tocca: stanza, muro, apertura o impianto (come nella pianta 2D). */
private fun Pick.tapTarget(): DragTarget = when (this) {
    is Pick.RoomFloor -> DragTarget.RoomInterior(roomId)
    is Pick.Wall -> DragTarget.Wall(roomId, index)
    is Pick.Opening -> DragTarget.Opening(roomId, openingId)
    is Pick.Fixture -> DragTarget.Fixture(roomId, fixtureId)
    is Pick.Stair -> DragTarget.Stair(stairId)
    is Pick.StairWell -> DragTarget.StairWell(stairId)
    is Pick.Column -> DragTarget.Column(columnId)
    is Pick.Beam -> DragTarget.BeamBody(beamId)
    is Pick.FreeWall -> DragTarget.FreeWallBody(wallId)
    is Pick.Furniture -> DragTarget.FurnitureBody(furnitureId)
}

/** Si trascina solo ciò che è già selezionato: così un trascinamento sulla scena la fa ruotare. */
private fun Pick.isSelectedIn(s: EditorUiState): Boolean = when (this) {
    is Pick.RoomFloor -> s.focusedRoomId == roomId && s.selection == null
    else -> s.selection?.toPick() == this
}

/**
 * Vista 3D della pianta. Un dito ruota la vista (o, camminando, gira lo sguardo), due dita zoomano e
 * spostano; un tocco seleziona stanza, muro, apertura o impianto (e posiziona porte, infissi e impianti
 * in attesa). Trascinando un oggetto già selezionato lo si sposta: aperture e impianti a muro lungo il
 * muro, luci sul soffitto, muri avanti e indietro, la stanza selezionata trascinandone il pavimento.
 */
@Composable
fun Plan3DView(state: EditorUiState, vm: EditorViewModel, view: View3DState, modifier: Modifier = Modifier) {
    val selectedPick = state.selection?.toPick()
    // Il piano scelto sopra quelli di sotto; i piani di sopra non si vedono (come togliere il tetto).
    val below = state.building.floors.take(state.building.current)
    // Camminando si vede anche il piano di sopra, attraverso il vano scala nel soffitto.
    val above = if (view.walking) state.building.floors.getOrNull(state.building.current + 1) else null
    // Livello "Arredi" nascosto: anche nel 3D la casa si vede vuota.
    val noFurniture = !state.building.layers.visible(com.sagoma.planimetria.model.Layer.Furniture)
    // Stato di fatto / progetto: il 3D mostra come sarà (progetto) o, nella vista "stato di fatto", com'è ora.
    val phase = if (state.phaseView == com.sagoma.planimetria.geometry.PhaseView.Existing) com.sagoma.planimetria.geometry.PhaseView.Existing
    else com.sagoma.planimetria.geometry.PhaseView.Project
    val scene = remember(state.plan, below, above, state.levelHeight, state.focusedRoomId, selectedPick, view.walking, noFurniture, phase) {
        fun shown(p: com.sagoma.planimetria.model.FloorPlan) = com.sagoma.planimetria.geometry.Phases.view(p, phase)
        val plan = shown(if (noFurniture) state.plan.copy(furniture = emptyList()) else state.plan)
        Scene3D.build(
            plan, state.focusedRoomId, selectedPick, ceilings = view.walking, levelHeight = state.levelHeight,
            below = below.map { it.copy(plan = shown(it.plan)) }, above = above?.let { it.copy(plan = shown(it.plan)) },
        )
    }
    LaunchedEffect(scene) { view.setScene(scene) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { view.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { view.onResume() }

    val currentState by rememberUpdatedState(state)
    val currentScene by rememberUpdatedState(scene)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Chi cammina non attraversa muri e mobili della scena attuale.
    view.isBlocked = { p -> currentScene.blocked(p, 20.0) }
    // Joystick e tasti tenuti premuti muovono la telecamera a velocità costante, fotogramma per fotogramma;
    // quando non si muove nulla il ciclo si ferma (niente ridisegni inutili).
    LaunchedEffect(view) {
        snapshotFlow { view.moving }.collectLatest { moving ->
            if (!moving) return@collectLatest
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.0 else ((now - last) / 1e9).coerceAtMost(0.1)
                    last = now
                    if (dt > 0) view.step(dt)
                }
            }
        }
    }
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.requestFocusSafely() }

    Box(
        modifier.onSizeChanged {
            if (it.width <= 0 || it.height <= 0) return@onSizeChanged
            view.aspect = it.width.toDouble() / it.height
            // Prima apertura: tutta la pianta inquadrata, ora che si conoscono le proporzioni della vista.
            if (!view.fitted) view.fit(currentScene)
        },
    ) {
        val platform = LocalPlatform.current
        val renderer = remember { view.attach(platform) }
        DisposableEffect(renderer) { onDispose { view.detach() } }
        renderer.Surface(Modifier.fillMaxSize())
        // Gesti sopra la superficie 3D.
        Box(
            Modifier.fillMaxSize()
                // Tastiera (computer): frecce, WASD, PagSu/PagGiù, + e −, Maiusc per correre o spostare.
                .focusRequester(keyFocus)
                .focusable()
                .onKeyEvent { e ->
                    val k = e.key
                    val handled = k in MOVE_KEYS || k == Key.ShiftLeft || k == Key.ShiftRight
                    when (e.type) {
                        KeyEventType.KeyDown -> if (handled) view.keys += k
                        KeyEventType.KeyUp -> view.keys -= k
                    }
                    handled
                }
                .onFocusChanged { if (!it.isFocused) view.keys.clear() }
                // Rotellina del mouse: zoom (camminando, passi avanti e indietro).
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.type == PointerEventType.Scroll) {
                                view.wheel(wheelNotches(e.changes.first().scrollDelta.y))
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    keyFocus.requestFocusSafely()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val cam = view.camera
                    // Mouse: tasto destro o rotellina premuta spostano la vista invece di ruotarla.
                    val buttons = currentEvent.buttons
                    if (buttons.isSecondaryPressed || buttons.isTertiaryPressed) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            view.panBy(ch.position - ch.previousPosition, h)
                            ch.consume()
                        }
                        return@awaitEachGesture
                    }
                    val (o0, d0) = cam.ray(down.position.x, down.position.y, w, h)
                    val hit = currentScene.hit(o0, d0)
                    val s0 = currentState
                    // Camminando un trascinamento gira sempre lo sguardo: gli oggetti si spostano solo dalla vista dall'alto.
                    val canDrag = !view.walking && hit != null && hit.pick.isSelectedIn(s0) && s0.pendingOpening == null && s0.pendingFixture == null
                    // Piano su cui si muove l'oggetto trascinato: il muro per aperture e impianti a muro,
                    // un piano orizzontale per muri, luci e pavimento.
                    // Aperture e impianti a muro si trascinano su un piano orizzontale: così passano da un muro
                    // all'altro lungo il perimetro della stanza, come nella pianta.
                    val vertical = false
                    val planeNormal = if (vertical && hit != null) Vec3(hit.normal.x, 0.0, hit.normal.z).normalized() else Vec3.Up
                    val dragTarget = hit?.let { if (it.pick is Pick.RoomFloor) DragTarget.RoomLabel(it.pick.roomId) else it.pick.tapTarget() }
                    var dragging = false
                    var objectDrag = false
                    var multi = false
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (objectDrag) { vm.endDrag(); objectDrag = false }
                            multi = true
                            dragging = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!view.walking) {
                                cam.distance = (cam.distance / zoom).coerceIn(80.0, 60_000.0)
                                view.panBy(pan, h)
                            }
                            view.refresh()
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        if (multi) continue
                        val ch = event.changes.first { it.id == down.id || it.pressed }
                        val delta = ch.position - ch.previousPosition
                        total += delta
                        if (!dragging && total.getDistance() > touchSlop) {
                            dragging = true
                            if (canDrag && dragTarget != null) {
                                objectDrag = true
                                vm.beginDrag(dragTarget, hit!!.point.flat)
                            }
                        }
                        if (!dragging) continue
                        if (objectDrag && hit != null && dragTarget != null) {
                            val (o, d) = cam.ray(ch.position.x, ch.position.y, w, h)
                            cam.rayPlane(o, d, hit.point, planeNormal)?.let { p ->
                                vm.dragBy(dragTarget, (p - hit.point).flat)
                            }
                        } else if (view.walking) {
                            cam.walkYaw -= delta.x * 0.25
                            cam.walkPitch = (cam.walkPitch - delta.y * 0.25).coerceIn(-70.0, 70.0)
                            view.refresh()
                        } else {
                            cam.yaw -= delta.x * 0.3
                            cam.pitch = (cam.pitch + delta.y * 0.3).coerceIn(8.0, 89.0)
                            view.refresh()
                        }
                        ch.consume()
                    }
                    if (objectDrag) vm.endDrag()
                    if (!dragging) {
                        // Tocco: seleziona (o posiziona porta, infisso, impianto in attesa); nel vuoto deseleziona.
                        if (hit != null) vm.onTap(hit.pick.tapTarget(), hit.point.flat)
                        else vm.onTap(DragTarget.Background, Vec2.Zero)
                    }
                }
            },
        )
    }
}

/** Pulsante della vista 3D (in basso a destra, al posto della legenda): Cammina / Vista dall'alto. */
@Composable
fun View3DControls(state: EditorUiState, view: View3DState, modifier: Modifier = Modifier) {
    // Versione pro: ora del giorno (sole e luce dalle finestre) e luci della casa.
    if (com.sagoma.planimetria.Edition.isPro) {
        val h = view.hour.toInt()
        val m = ((view.hour - h) * 60).roundToInt()
        val icon = if (view.hour < 6.5f || view.hour > 19.5f) "🌙" else if (view.hour < 8f || view.hour > 18f) "🌅" else "☀"
        LegendButton("$icon $h:${m.toString().padStart(2, '0')}",modifier = modifier, onClick = { view.timePanel = !view.timePanel })
        val lampLabel = when (view.lamps) { "on" -> "💡 Luci accese"; "off" -> "💡 Luci spente"; else -> "💡 Luci: auto" }
        LegendButton(lampLabel, modifier = modifier, onClick = { view.nextLamps() })
    }
    LegendButton(if (view.walking) "🏠 Vista dall'alto" else "🚶 Cammina", modifier = modifier, onClick = {
        // Con un arredo selezionato si cammina nella stanza in cui si trova.
        val furnitureRoom = (state.selection as? Selection.Furniture)?.let { sel ->
            state.plan.furniture(sel.furnitureId)?.let { f -> state.plan.rooms.lastOrNull { Polygon.contains(it.points, f.center) }?.id }
        }
        if (view.walking) view.stopWalking() else view.startWalking(state.plan, state.focusedRoomId ?: furnitureRoom)
    })
}

/**
 * Cursore dell'ora del giorno (versione pro). In verticale (telefono dritto) sta in alto, largo; su schermi
 * larghi (computer, telefono girato) sta a sinistra, stretto e verticale (lungo `length`), per non coprire la vista.
 */
@Composable
fun TimeOfDayPanel(view: View3DState, modifier: Modifier = Modifier, vertical: Boolean = false, length: androidx.compose.ui.unit.Dp = 240.dp) {
    if (!view.timePanel) return
    if (vertical) {
        Surface(modifier, shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val h = view.hour.toInt()
                val m = ((view.hour - h) * 60).roundToInt()
                Text("$h:${m.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelLarge)
                Text("🌙 24", style = MaterialTheme.typography.labelSmall)
                // Slider girato: in basso mezzanotte, in alto la sera.
                androidx.compose.material3.Slider(
                    value = view.hour,
                    onValueChange = { view.hour = (it * 4).roundToInt() / 4f; view.refresh() },
                    valueRange = 0f..24f,
                    modifier = Modifier
                        .height(length)
                        .graphicsLayer { rotationZ = 270f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
                        .layout { measurable, constraints ->
                            val p = measurable.measure(
                                androidx.compose.ui.unit.Constraints(
                                    minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
                                    minHeight = 0, maxHeight = constraints.maxWidth,
                                ),
                            )
                            layout(p.height, p.width) { p.place(-p.width, 0) }
                        },
                )
                Text("0", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { view.timePanel = false }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) { Text("✕") }
            }
        }
        return
    }
    Surface(modifier, shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val h = view.hour.toInt()
            val m = ((view.hour - h) * 60).roundToInt()
            Text("Ora del giorno: $h:${m.toString().padStart(2, '0')}",style = MaterialTheme.typography.labelLarge)
            androidx.compose.material3.Slider(
                value = view.hour,
                onValueChange = { view.hour = (it * 4).roundToInt() / 4f; view.refresh() },
                valueRange = 0f..24f,
            )
            Text(
                "Il sole sorge a est (a destra della pianta) e tramonta a ovest; la luce entra dalle finestre. " +
                    "Quando fa buio si accendono le luci della casa.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Joystick per camminare: si trascina il pomello, la spinta è proporzionale allo spostamento. */
@Composable
fun WalkJoystick(view: View3DState, modifier: Modifier = Modifier) {
    val sizeDp = 120.dp
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .size(sizeDp)
            .background(Color.Black.copy(alpha = 0.18f), CircleShape)
            .pointerInput(Unit) {
                val radius = size.width / 2f
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(radius, radius)
                    fun update(p: Offset) {
                        var v = p - center
                        val d = v.getDistance()
                        if (d > radius) v = v * (radius / d)
                        knob = v
                        view.joystick = Offset(v.x / radius, -v.y / radius)
                    }
                    update(down.position)
                    down.consume()
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        update(c.position)
                        c.consume()
                    }
                    knob = Offset.Zero
                    view.joystick = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.85f), CircleShape),
        )
    }
}
