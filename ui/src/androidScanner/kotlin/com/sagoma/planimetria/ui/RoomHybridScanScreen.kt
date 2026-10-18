package com.sagoma.planimetria.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ArXZ
import com.sagoma.planimetria.scan.DetectedWall
import com.sagoma.planimetria.scan.WallObservation
import com.sagoma.planimetria.scan.hybrid.BoxState
import com.sagoma.planimetria.scan.hybrid.CandidateStatus
import com.sagoma.planimetria.scan.hybrid.CoverageQuad
import com.sagoma.planimetria.scan.hybrid.DepthPoints
import com.sagoma.planimetria.scan.hybrid.FragmentStatus
import com.sagoma.planimetria.scan.hybrid.HybridPhase
import com.sagoma.planimetria.scan.hybrid.HybridScan
import com.sagoma.planimetria.scan.hybrid.HybridSnapshot
import com.sagoma.planimetria.scan.hybrid.PlaneEstimate
import com.sagoma.planimetria.scan.hybrid.SurfaceKind
import com.sagoma.planimetria.scan.hybrid.WallState
import com.sagoma.planimetria.scan.sketch.RoomSketch
import com.sagoma.planimetria.scan.sketch.SketchApply
import com.sagoma.planimetria.scanner.ArScanView
import com.sagoma.planimetria.scanner.ScanRecorder
import com.sagoma.planimetria.ui.hybrid.HybridScanText as T
import com.sagoma.planimetria.ui.sketch.LocalSketchScanText
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * PROTOTIPO della scansione ibrida (separato dalla scansione guidata, che non cambia): pavimento, soffitto e pareti ricostruiti dalla
 * profondità grezza del telefono, da riferimenti confermati dall'utente, con lo schizzo come guida. Tutta la geometria è nel nucleo
 * (scan/hybrid/HybridScan); qui solo profondità in ingresso, gesti e disegno.
 */

@Composable
internal fun RoomHybridScanScreen(activity: ComponentActivity, room: Room, onResult: (SketchScanOutcome?) -> Unit) {
    val t = LocalSketchScanText.current
    // Schermo bloccato in verticale: inquadrando pavimento o soffitto il telefono sembra girato, Android ricreava la schermata e la
    // scansione ripartiva da capo (prova del 2026-10-13 sera: due volte durante il soffitto, scambiate per crash).
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = before }
    }
    MaterialTheme(colorScheme = HybridColors) {
        ArGate(activity, t, onExit = { onResult(null) }) { HybridContent(activity, room, onResult) }
    }
}

private val HybridColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF0B1F3A), surface = Color(0xFF202124), onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCFD8DC), outline = Color(0xFF9AA0A6),
)

private val Yellow = Color(0xFFFFD91A)
private val Green = Color(0xFF66BB6A)
private val Red = Color(0xFFFF8A80)
private val Amber = Color(0xFFFFC107)
private val Grey = Color(0xFFB0BEC5)

/** Colori delle superfici viste nella scena (RGBA): registrata come buona, dubbia, vista ma ancora da completare. */
private val COVER_GOOD = floatArrayOf(0.30f, 0.85f, 0.40f, 0.35f)
private val COVER_DOUBT = floatArrayOf(1.00f, 0.70f, 0.10f, 0.35f)
private val COVER_SEEN = floatArrayOf(1.00f, 1.00f, 1.00f, 0.18f)

@Composable
private fun HybridContent(activity: ComponentActivity, room: Room, onResult: (SketchScanOutcome?) -> Unit) {
    val st = LocalSketchScanText.current
    val platform = LocalPlatform.current
    val sketch = remember { SketchApply.sketchOf(room) }
    val openings = remember { SketchApply.openingsOf(room) }
    fun sideName(k: Int) = st.sideName(k, openings.filter { it.side == k }.map { it.kind })
    // Riferimento dalla zona centrale; poi l'area si allarga all'immagine intera tenendo SOLO i punti buoni, su tutte le superfici.
    val engine = remember { HybridScan(sketch, room.ceilingHeight / 100.0).also { it.goodPointKinds = SurfaceKind.entries.toSet() } }
    val sketchHeightIsDefault = room.ceilingHeight == Room.DEFAULT_CEILING_HEIGHT
    var snap by remember { mutableStateOf(engine.snapshot()) }
    // Superfici viste, colorate sopra la scena; si aggiornano al massimo due volte al secondo (calcolo dei frammenti).
    var coverage by remember { mutableStateOf<List<CoverageQuad>>(emptyList()) }
    val lastCoverageMs = remember { java.util.concurrent.atomic.AtomicLong(0) }
    val view = remember { ArScanView(activity) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val busy = remember { AtomicBoolean(false) }
    val frameCounter = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val lastDepthNs = remember { java.util.concurrent.atomic.AtomicLong(0) }
    var tracking by remember { mutableStateOf(false) }
    var trackingIssue by remember { mutableStateOf<com.sagoma.planimetria.scan.TrackingIssue?>(null) }
    val lastTracking = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    // Avviso breve sopra i pulsanti (conferma non riuscita, riallineamento fatto): sparisce da solo.
    var notice by remember { mutableStateOf<Pair<String, Color>?>(null) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var installPending by remember { mutableStateOf(false) }

    // Domande iniziali (scale, superfici inclinate) e finestre.
    var stairs by remember { mutableStateOf<Boolean?>(null) }
    var slopedFloor by remember { mutableStateOf<Boolean?>(null) }
    var slopedCeiling by remember { mutableStateOf<Boolean?>(null) }
    var questionsDone by remember { mutableStateOf(false) }
    var unsupportedAccepted by remember { mutableStateOf(false) }
    val unsupported = stairs == true || slopedFloor == true || slopedCeiling == true
    var introSeen by remember { mutableStateOf(setOf<HybridPhase>()) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var choosingRedo by remember { mutableStateOf(false) }
    var choosingTarget by remember { mutableStateOf(false) }
    var choosingRealign by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var askRecordingConsent by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var lastRecording by remember { mutableStateOf<File?>(null) }
    val recordingAllowed = remember { (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    var eventLog by remember { mutableStateOf<HybridEventFile?>(null) }

    fun nowMs() = System.nanoTime() / 1_000_000

    /**
     * Un gesto: sul motore (sincronizzato con il thread della profondità), poi la fotografia nuova. Vero se è riuscito (un gesto
     * che non restituisce un esito riesce sempre); nel registro un gesto non riuscito ha il suffisso _FAILED.
     */
    fun act(kind: String? = null, side: Int? = null, block: HybridScan.() -> Any?): Boolean {
        val (ok, s) = synchronized(engine) { (engine.block() != false) to engine.snapshot() }
        snap = s
        coverage = synchronized(engine) { engine.coverage() } // dopo un gesto (es. "Rifai") i colori cambiano subito
        if (kind != null) eventLog?.event(if (ok) kind else kind + "_FAILED", side, lastDepthNs.get(), s)
        return ok
    }

    /** Conferma: se non riesce lo si dice, con il motivo (prima la conferma poteva essere ignorata in silenzio). */
    fun confirm(kind: String, side: Int?, block: HybridScan.() -> Boolean) {
        if (!act(kind, side, block)) notice = T.notConfirmed(T.candidate(snap.candidate, snap.phase, ::sideName, snap.realignTarget != null)) to Amber
    }

    /** Riconferma per il riallineamento: nel registro anche la correzione del mondo che ne risulta. */
    fun realign(kind: String, side: Int?, block: HybridScan.() -> Boolean) {
        val (ok, s) = synchronized(engine) { engine.block() to engine.snapshot() }
        snap = s
        eventLog?.realign(kind, side, ok, lastDepthNs.get(), s)
        notice = when {
            !ok && s.realignMismatch -> T.realignMismatch to Amber
            !ok -> T.notConfirmed(T.candidate(s.candidate, s.phase, ::sideName, s.realignTarget != null)) to Amber
            kind == "REALIGN_FLOOR" -> T.realignFloorDone to Green
            s.realignTarget == null -> T.realignDoneMsg to Green
            else -> null
        }
    }

    fun finish(outcome: SketchScanOutcome?) {
        eventLog?.event(if (outcome != null) "APPLIED" else "EXIT", null, lastDepthNs.get(), snap)
        onResult(outcome)
    }

    DisposableEffect(view) {
        view.depthListening = true
        view.drawArPlanes = false
        view.poseJumpThresholdM = engine.th.poseJumpM
        view.poseJumpRotationDeg = engine.th.poseJumpRotationDeg
        view.listener = object : ArScanView.Listener {
            override fun onStatus(s: ArScanView.Status) {
                tracking = s.tracking; trackingIssue = s.trackingIssue
                // Nel registro ogni cambio: posizione persa (con il motivo di ARCore) o ritrovata.
                val key = if (s.tracking) "TRACKING" else "LOST:" + (s.trackingIssue?.name ?: "UNKNOWN")
                if (lastTracking.getAndSet(key) != key) eventLog?.tracking(s.tracking, s.trackingIssue?.name, lastDepthNs.get())
            }
            override fun onFloorPoint(point: ArPoint) {}
            override fun onWallObservations(observations: List<WallObservation>) {}
            override fun onTapRejected(reason: String) {}
            override fun onOverlay(c: List<ArScanView.ScreenPoint?>, edgeMidpoints: List<ArScanView.ScreenPoint?>) {}
            override fun onPoseJump(distanceM: Double, rotationDeg: Double, afterPause: Boolean) {
                // Nella stessa coda della profondità, prima di quella dello stesso fotogramma: nessun punto "spostato" sfugge.
                // Dopo una pausa o una rotazione brusca, con pareti acquisite, il motore chiede il riallineamento.
                worker.execute {
                    val s = synchronized(engine) { engine.poseJump(afterPause, rotationDeg); engine.snapshot() }
                    eventLog?.jump(distanceM, rotationDeg, afterPause, lastDepthNs.get(), s)
                    view.post { snap = s }
                }
            }
            override fun onDepth(depth: ArScanView.LiveDepth) {
                // Sul thread del disegno: si passa al lavoratore; se è ancora occupato, questa profondità si salta.
                if (!busy.compareAndSet(false, true)) return
                worker.execute {
                    try {
                        val pts = DepthPoints.fromDepth(frameCounter.getAndIncrement(), nowMs(), depth.mm, depth.confidence, depth.k, depth.pose)
                        lastDepthNs.set(depth.timestampNs)
                        val due = nowMs() - lastCoverageMs.get() >= 500
                        val (s, cov) = synchronized(engine) { engine.addDepth(pts); engine.snapshot() to (if (due) engine.coverage() else null) }
                        if (due) lastCoverageMs.set(nowMs())
                        view.post { snap = s; cov?.let { coverage = it } }
                    } catch (e: Exception) {
                        android.util.Log.e("SagomaHybrid", "profondità non elaborata", e)
                    } finally { busy.set(false) }
                }
            }
        }
        onDispose {
            view.listener = null
            view.depthListening = false
            view.drawArPlanes = true
            if (view.recordingStats() != null) view.stopRecording()
            view.stop()
            view.release()
            worker.shutdown()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        when (val r = view.start(activity)) {
            ArScanView.StartResult.Started -> { fatal = null; installPending = false }
            ArScanView.StartResult.InstallRequested -> installPending = true
            is ArScanView.StartResult.Failed -> fatal = r.message
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { view.stop() }
    // Il tempo passa anche senza profondità: così "nessun dato" compare davvero.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(1000)
            worker.execute {
                val s = synchronized(engine) { engine.tick(nowMs()); engine.snapshot() }
                if (++ticks % 5 == 0) eventLog?.state(lastDepthNs.get(), s) // ogni 5 s nel registro: ciò che l'utente vede
                view.post { snap = s }
            }
        }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(5000); notice = null } }
    // Sopra la scena (solo dati della profondità; niente piani di ARCore):
    // - le superfici viste, a celle di 10 cm: verde = registrata come buona (frammento accettato), arancione = dubbia, bianco
    //   tenue = vista ma ancora da completare;
    // - la superficie candidata, con la sua inclinazione vera: verde se si può confermare, grigia se instabile o non piana,
    //   arancione con un avviso. Le superfici non considerate (oltre 10° dallo schizzo) non hanno disegno.
    // Le superfici stanno nel sistema in cui sono state confermate: si portano nel mondo ATTUALE di ARCore con la correzione dei
    // riallineamenti (snap.world).
    LaunchedEffect(coverage, snap.candidate, snap.phase, snap.world) {
        val w = snap.world
        fun quad(corners: List<ArPoint>, color: FloatArray) =
            ArScanView.OverlayQuad(FloatArray(12) { i -> corners[i / 3].let { q -> when (i % 3) { 0 -> w.x(q.x, q.z); 1 -> w.y(q.y); else -> w.z(q.x, q.z) }.toFloat() } }, color)
        val seen = if (snap.phase == HybridPhase.START_SIDE) emptyList() else coverage.map { q ->
            quad(q.corners, when (q.status) { FragmentStatus.ACCEPTED -> COVER_GOOD; FragmentStatus.DOUBTFUL -> COVER_DOUBT; FragmentStatus.INSUFFICIENT -> COVER_SEEN })
        }
        val p = snap.candidate.patch
        val cand = if (p == null || snap.phase == HybridPhase.REVIEW || snap.phase == HybridPhase.START_SIDE) emptyList() else {
            val color = when (snap.candidate.status) {
                CandidateStatus.READY -> floatArrayOf(0.40f, 0.90f, 0.45f, 0.40f)
                CandidateStatus.SURFACE_BEHIND, CandidateStatus.PARALLEL_OFFSET, CandidateStatus.ALREADY_ACQUIRED -> floatArrayOf(1.00f, 0.75f, 0.10f, 0.35f)
                CandidateStatus.WRONG_ORIENTATION, CandidateStatus.WRONG_SIDE -> floatArrayOf(1.00f, 0.40f, 0.35f, 0.35f)
                else -> floatArrayOf(0.85f, 0.85f, 0.85f, 0.25f)
            }
            listOf(quad(p, color))
        }
        view.overlayQuads = seen + cand
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showDetails -> showDetails = false
            choosingRedo -> choosingRedo = false
            choosingTarget -> choosingTarget = false
            choosingRealign -> choosingRealign = false
            showMenu -> showMenu = false
            snap.phase == HybridPhase.REVIEW -> act { backToScan() }
            else -> confirmExit = true
        }
    }

    val phase = snap.phase
    val c = snap.candidate
    val depthUnsupported = view.depthSupported == false
    val windowOpen = !questionsDone || (unsupported && !unsupportedAccepted) || depthUnsupported || phase == HybridPhase.START_SIDE ||
        phase !in introSeen || phase == HybridPhase.REVIEW || showMenu || showDetails || choosingRedo || choosingTarget || choosingRealign
    val realignTarget = snap.realignTarget

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        if (!windowOpen && fatal == null) {
            val ready = c.status == CandidateStatus.READY
            val statusColor = when {
                !tracking -> Red
                else -> when (c.status) {
                CandidateStatus.READY -> Green
                CandidateStatus.NO_DEPTH -> Red
                CandidateStatus.SURFACE_BEHIND, CandidateStatus.PARALLEL_OFFSET -> Amber
                else -> Grey
                }
            }
            // Mirino al centro: la zona usata per il riferimento.
            Box(Modifier.align(Alignment.Center).size(28.dp), contentAlignment = Alignment.Center) {
                Text("+", color = if (ready) Yellow else Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            // In alto: fase, stato della superficie inquadrata, profondità, schizzo con la scatola.
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xCC000000)) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (realignTarget != null) T.realignTitle + " · " + sideName(realignTarget)
                                else T.phaseTitle(phase) + (if (phase == HybridPhase.WALLS) snap.targetSide?.let { " · " + sideName(it) } ?: "" else ""),
                                color = if (realignTarget != null) Amber else Color.White, style = MaterialTheme.typography.titleSmall)
                            // Posizione persa: il motivo di ARCore e cosa fare, non solo "muovi il telefono".
                            Text(if (!tracking) T.trackingLost(trackingIssue) else T.candidate(c, phase, ::sideName, realignTarget != null),
                                color = statusColor, style = MaterialTheme.typography.bodySmall)
                            // Per giudicare la superficie prima di confermarla: inclinazione e angolo con la parete adiacente già misurata.
                            if (c.patch != null) {
                                val extra = listOfNotNull(
                                    c.tiltDeg?.let { T.tiltLine(phase == HybridPhase.WALLS, it) },
                                    if (c.adjacentSide != null && c.adjacentAngleDeg != null && c.sketchAdjacentAngleDeg != null)
                                        T.adjacentLine(sideName(c.adjacentSide!!), c.adjacentAngleDeg!!, c.sketchAdjacentAngleDeg!!) else null,
                                )
                                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(T.depthLine(snap.depth.lastFrameValidPoints, snap.depth.lastFramePixels, snap.depth.msSinceValid),
                                color = if ((snap.depth.msSinceValid ?: Long.MAX_VALUE) > 3000) Red else Grey, style = MaterialTheme.typography.labelSmall)
                            if (snap.jumps > 0) Text(T.jumpLine(snap.jumps, snap.unverified, snap.lastDisplacementM, snap.discardedFrames),
                                color = if (snap.unverified) Amber else Grey, style = MaterialTheme.typography.labelSmall)
                            if (recording) Text(T.recordingOn, color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { showMenu = true }) { Text(T.menu, color = Color.White) }
                    }
                }
                Surface(Modifier.align(Alignment.End), shape = RoundedCornerShape(10.dp), color = Color(0xAA000000)) {
                    HybridMap(sketch, snap, Modifier.size(150.dp).padding(6.dp))
                }
            }
            // In basso: informazioni della fase e i pulsanti.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notice?.let { (text, color) -> Hint(text, color) }
                // Riallineamento (dopo una pausa del tracciamento o una rotazione brusca): si riconfermano pareti già acquisite;
                // finché non è fatto (o saltato) non si confermano superfici nuove.
                if (realignTarget != null) {
                    Hint(T.realignExplain(sideName(realignTarget), snap.realignDone.size, snap.realignNeeded), Amber)
                    BigButton(T.realignButton(sideName(realignTarget)), ready && c.side != null) { realign("REALIGN_WALL", realignTarget) { confirmRealign() } }
                    if (ready && c.side == null) OutlinedButton(onClick = { realign("REALIGN_FLOOR", null) { confirmRealignFloor() } }, modifier = Modifier.fillMaxWidth()) {
                        Text(T.realignFloor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (snap.walls.count { it.state != WallState.MISSING } > 1) TextButton(onClick = { choosingRealign = true }) { Text(T.realignOther) }
                        TextButton(onClick = { realign("REALIGN_SKIPPED", null) { skipRealign(); true } }) { Text(T.realignSkip) }
                    }
                } else when (phase) {
                    HybridPhase.FLOOR -> if (snap.floor.reference == null) {
                        BigButton(T.confirmFloor, ready) { confirm("FLOOR_REFERENCE", null) { confirmReference() } }
                    } else {
                        SurfaceProgress("Pavimento", snap.floor)
                        BigButton(T.nextWalls, true) { act("FLOOR_DONE") { nextPhase() } }
                        OutlinedButton(onClick = { act("FLOOR_REDO") { redoHorizontal() } }, modifier = Modifier.fillMaxWidth()) { Text(T.redoFloorRef) }
                    }
                    HybridPhase.CEILING -> {
                        if (snap.ceiling.reference == null) BigButton(T.confirmCeiling, ready) { confirm("CEILING_REFERENCE", null) { confirmReference() } }
                        else {
                            SurfaceProgress("Soffitto", snap.ceiling)
                            BigButton(T.nextReview, true) { act("CEILING_DONE") { nextPhase() } }
                            OutlinedButton(onClick = { act("CEILING_REDO") { redoHorizontal() } }, modifier = Modifier.fillMaxWidth()) { Text(T.redoCeilingRef) }
                        }
                        if (snap.ceiling.reference == null) OutlinedButton(onClick = { act("CEILING_SKIPPED") { skipCeiling() } }, modifier = Modifier.fillMaxWidth()) { Text(T.skipCeiling) }
                    }
                    HybridPhase.WALLS -> {
                        val last = snap.walls.filter { it.state != WallState.MISSING }.lastOrNull()
                        last?.let { w ->
                            val acc = w.fragments.count { it.status == FragmentStatus.ACCEPTED }
                            val dub = w.fragments.count { it.status == FragmentStatus.DOUBTFUL }
                            val ins = w.fragments.count { it.status == FragmentStatus.INSUFFICIENT }
                            Hint(T.wallProgress(sideName(w.side), acc, dub, ins) + " · " + T.pointsLine(w.points), Color.White)
                            if (dub > 0) Hint(T.doubtfulHint, Amber)
                        }
                        val side = c.side ?: snap.targetSide
                        val first = snap.walls.none { it.state != WallState.MISSING }
                        // Nessuna "conferma comunque": con un avviso si cerca un'altra superficie o si rifà il riferimento.
                        if (side != null) BigButton(T.confirmWall(sideName(side), first), ready) { confirm("WALL_REFERENCE", side) { confirmReference() } }
                        // Tutte confermate: si resta qui per percorrerle, poi il soffitto (per ultimo, P0.19).
                        if (snap.targetSide == null) {
                            Hint(T.allWallsDone, Green)
                            BigButton(T.nextCeiling, true) { act("WALLS_DONE") { nextPhase() } }
                        } else if (!first) TextButton(onClick = { act("WALLS_DONE") { nextPhase() } }) { Text(T.nextCeiling) }
                    }
                    else -> {}
                }
            }
        }

        // ---- Finestre ----
        when {
            fatal != null -> {}
            installPending -> HybridWindow(st.installingArcore) {}
            depthUnsupported -> HybridWindow(T.title) {
                Text(T.noDepthPhone, color = Color.White)
                Button(onClick = { finish(null) }, modifier = Modifier.fillMaxWidth()) { Text(T.exit) }
            }
            !questionsDone -> HybridWindow(T.title) {
                Text(T.intro, color = Grey, style = MaterialTheme.typography.bodyMedium)
                YesNo(T.qStairs, stairs) { stairs = it }
                YesNo(T.qSlopedFloor, slopedFloor) { slopedFloor = it }
                YesNo(T.qSlopedCeiling, slopedCeiling) { slopedCeiling = it }
                Button(onClick = { questionsDone = true }, enabled = stairs != null && slopedFloor != null && slopedCeiling != null, modifier = Modifier.fillMaxWidth()) { Text(T.next) }
                TextButton(onClick = { finish(null) }) { Text(T.exit) }
            }
            unsupported && !unsupportedAccepted -> HybridWindow(T.unsupportedTitle) {
                Text(T.unsupported(stairs == true, slopedFloor == true, slopedCeiling == true), color = Amber)
                OutlinedButton(onClick = { unsupportedAccepted = true }, modifier = Modifier.fillMaxWidth()) { Text(T.tryAnyway) }
                Button(onClick = { finish(null) }, modifier = Modifier.fillMaxWidth()) { Text(T.exit) }
            }
            showDetails -> HybridWindow(T.details) {
                Text(T.toleranceNote, color = Grey, style = MaterialTheme.typography.labelSmall)
                for ((label, frags) in listOf("Pavimento" to snap.floor.fragments, "Soffitto" to snap.ceiling.fragments) + snap.walls.map { sideName(it.side) to it.fragments }) {
                    if (frags.isEmpty()) continue
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    for (f in frags.take(8)) Text("• " + T.fragmentLine(f.status, f.points, f.frames, f.medianOffsetM, f.angleDeg, f.extentAM, f.extentBM),
                        color = when (f.status) { FragmentStatus.ACCEPTED -> Green; FragmentStatus.DOUBTFUL -> Amber; else -> Grey }, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth()) { Text(T.close) }
            }
            choosingRedo -> HybridWindow(T.redoTitle) {
                HybridMap(sketch, snap, Modifier.fillMaxWidth().height(160.dp))
                for (w in snap.walls) if (w.state != WallState.MISSING) OutlinedButton(onClick = { choosingRedo = false; act("REDO", w.side) { redoWall(w.side) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(T.redoSide(sideName(w.side)))
                }
                TextButton(onClick = { choosingRedo = false }) { Text(T.back) }
            }
            choosingTarget -> HybridWindow(T.chooseTarget) {
                HybridMap(sketch, snap, Modifier.fillMaxWidth().height(160.dp))
                for (w in snap.walls) if (w.state == WallState.MISSING) OutlinedButton(onClick = { choosingTarget = false; act { setTarget(w.side) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(sideName(w.side))
                }
                TextButton(onClick = { choosingTarget = false }) { Text(T.back) }
            }
            choosingRealign -> HybridWindow(T.realignChoose) {
                HybridMap(sketch, snap, Modifier.fillMaxWidth().height(160.dp))
                for (w in snap.walls) if (w.state != WallState.MISSING && w.side != snap.realignTarget && w.side !in snap.realignDone)
                    OutlinedButton(onClick = { choosingRealign = false; act("REALIGN_TARGET", w.side) { setRealignTarget(w.side) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(sideName(w.side))
                    }
                TextButton(onClick = { choosingRealign = false }) { Text(T.back) }
            }
            showMenu -> HybridWindow(T.menu) {
                if (snap.walls.any { it.state != WallState.MISSING }) MenuButton(T.redoWall) { showMenu = false; choosingRedo = true }
                if (phase == HybridPhase.WALLS && snap.walls.any { it.state == WallState.MISSING }) MenuButton(T.chooseTarget) { showMenu = false; choosingTarget = true }
                if (phase == HybridPhase.WALLS) MenuButton(T.goToReview) { showMenu = false; act { goToReview() } }
                if (recordingAllowed) MenuButton(if (recording) T.stopRecording else T.record) {
                    showMenu = false
                    if (recording) { lastRecording = view.stopRecording(); recording = false } else askRecordingConsent = true
                }
                val android = platform as? AndroidPlatform
                val rec = lastRecording
                if (!recording && rec != null && rec.isDirectory && android != null) MenuButton(T.export) {
                    showMenu = false
                    android.saveStream(rec.name + ".zip", "application/zip", { out -> ScanRecorder.writeZip(rec, out) }) { ok ->
                        platform.toast(if (ok) st.recordingExported else st.recordingExportFailed)
                    }
                }
                MenuButton(T.details) { showMenu = false; showDetails = true }
                MenuButton(T.exit) { showMenu = false; confirmExit = true }
                TextButton(onClick = { showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(T.close) }
            }
            phase == HybridPhase.START_SIDE -> HybridWindow(T.startTitle) {
                HybridMap(sketch, snap, Modifier.fillMaxWidth().height(170.dp))
                Text(T.startExplain, color = Grey, style = MaterialTheme.typography.bodyMedium)
                for (k in 0 until sketch.sideCount) OutlinedButton(onClick = { act("START_SIDE", k) { chooseStartSide(k) } }, modifier = Modifier.fillMaxWidth()) { Text(sideName(k)) }
                if (recordingAllowed && !recording) OutlinedButton(onClick = { askRecordingConsent = true }) { Text(T.record) }
                if (recording) Text(T.recordingOn, color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
            }
            phase != HybridPhase.REVIEW && phase !in introSeen -> HybridWindow(T.phaseTitle(phase)) {
                val steps = when (phase) { HybridPhase.FLOOR -> T.floorSteps; HybridPhase.CEILING -> T.ceilingSteps; else -> T.wallSteps }
                if (phase == HybridPhase.WALLS) snap.targetSide?.let { Text(T.wallOf(sideName(it)), color = Yellow, style = MaterialTheme.typography.titleMedium) }
                if (phase == HybridPhase.WALLS) HybridMap(sketch, snap, Modifier.fillMaxWidth().height(150.dp))
                steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { introSeen = introSeen + phase }, modifier = Modifier.fillMaxWidth()) { Text(T.begin) }
            }
            phase == HybridPhase.REVIEW -> HybridWindow(T.phaseTitle(phase)) {
                HybridMap(sketch, snap, Modifier.fillMaxWidth().height(200.dp))
                if (unsupported) Text(T.unsupportedFlag, color = Amber, style = MaterialTheme.typography.bodySmall)
                Text(T.box(snap.box), color = if (snap.box == BoxState.COMPLETE) Green else Amber, style = MaterialTheme.typography.bodyMedium)
                val n = sketch.sideCount
                for (k in 0 until n) {
                    val c0 = snap.corners[k]; val c1 = snap.corners[(k + 1) % n]
                    Text(T.sideLine(sideName(k), if (c0 != null && c1 != null) c0.distanceTo(c1) else null, (sketch.end(k) - sketch.start(k)).length),
                        color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                for (k in 0 until n) {
                    val a = snap.corners[(k + n - 1) % n]; val b = snap.corners[k]; val cc = snap.corners[(k + 1) % n]
                    Text(T.angleLine(k, if (a != null && b != null && cc != null) angleAt(a, b, cc) else null, angleAt(sketch.start((k + n - 1) % n), sketch.start(k), sketch.end(k))),
                        color = Grey, style = MaterialTheme.typography.bodySmall)
                }
                Text(T.height(snap.heightM, snap.sketchHeightM, sketchHeightIsDefault, snap.ceilingSkipped), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(T.floorSummary(snap.floor.areaM2, snap.ceiling.areaM2), color = Grey, style = MaterialTheme.typography.bodySmall)
                Text(T.discrepanciesTitle, color = Grey, style = MaterialTheme.typography.labelLarge)
                if (snap.discrepancies.isEmpty()) Text(T.noDiscrepancies, color = Green, style = MaterialTheme.typography.bodySmall)
                for (d in snap.discrepancies) Text("• " + T.discrepancy(d, ::sideName), color = Amber, style = MaterialTheme.typography.bodySmall)
                Text(T.toleranceNote, color = Grey, style = MaterialTheme.typography.labelSmall)
                val corners = snap.corners.takeIf { snap.box == BoxState.COMPLETE }?.map { it!! }
                val preview = corners?.let { SketchApply.preview(room, it) }
                if (corners == null) Text(T.applyNeedsComplete, color = Grey, style = MaterialTheme.typography.labelSmall)
                else if (preview?.canApply != true) Text(T.applyOpeningsDontFit, color = Red, style = MaterialTheme.typography.labelSmall)
                Button(onClick = { corners?.let { finish(SketchScanOutcome(it, emptyMap())) } }, enabled = corners != null && preview?.canApply == true,
                    modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(T.apply) }
                if (snap.walls.any { it.state != WallState.MISSING }) OutlinedButton(onClick = { choosingRedo = true }, modifier = Modifier.fillMaxWidth()) { Text(T.redoWall) }
                OutlinedButton(onClick = { act { backToScan() } }, modifier = Modifier.fillMaxWidth()) { Text(T.backToScan) }
                TextButton(onClick = { confirmExit = true }) { Text(T.exit) }
            }
        }

        if (confirmExit) AlertDialog(
            onDismissRequest = { confirmExit = false },
            text = { Text(T.exitConfirm) },
            confirmButton = { TextButton(onClick = { confirmExit = false; finish(null) }) { Text(T.exit) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(T.stay) } },
        )
        if (askRecordingConsent) AlertDialog(
            onDismissRequest = { askRecordingConsent = false },
            title = { Text(st.recordingConsentTitle) },
            text = { Text(st.recordingConsentText) },
            confirmButton = {
                TextButton(onClick = {
                    askRecordingConsent = false
                    view.startRecording()?.let { rec ->
                        recording = true
                        eventLog = HybridEventFile(File(rec.directory, HybridEventFile.FILE_NAME)).also {
                            it.header(sketch, openings.map { o -> "${o.side}:${o.kind}" }, snap.sketchHeightM, engine.goodPointKinds, engine.th.pointMaxImageRadius)
                        }
                    }
                }) { Text(st.recordingConsentYes) }
            },
            dismissButton = { TextButton(onClick = { askRecordingConsent = false }) { Text(st.cancel) } },
        )
        fatal?.let {
            Surface(Modifier.fillMaxSize(), color = Color(0xF2000000)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.unavailableTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(12.dp))
                    Text(it, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(24.dp))
                    Button(onClick = { finish(null) }) { Text(st.back) }
                }
            }
        }
    }
}

private fun angleAt(a: ArXZ, b: ArXZ, c: ArXZ): Double {
    val u = a - b; val v = c - b
    return Math.toDegrees(kotlin.math.acos(((u dot v) / (u.length * v.length)).coerceIn(-1.0, 1.0)))
}

/**
 * Pulsante grande, SEMPRE premibile: [ready] cambia solo il colore. Prima era disattivato quando la superficie non era pronta, e
 * un tocco nell'istante in cui lo stato passava a "instabile" si perdeva senza avviso; ora il gesto arriva al motore e, se non
 * riesce, si dice perché.
 */
@Composable
private fun BigButton(label: String, ready: Boolean, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = if (ready) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Color(0xFF5F6368), contentColor = Color(0xFFE8EAED))) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }

/** Pavimento o soffitto confermati: area vista e accettata e, se la parte più grande è dubbia, il perché. */
@Composable
private fun SurfaceProgress(kind: String, s: com.sagoma.planimetria.scan.hybrid.HorizontalSnapshot) {
    Hint(T.surfaceDone(kind, s.seenAreaM2, s.areaM2) + " · " + T.pointsLine(s.points), Color.White)
    s.fragments.maxByOrNull { it.points }?.takeIf { it.status == FragmentStatus.DOUBTFUL }?.let { Hint(T.surfaceDoubtful(it.medianOffsetM, it.angleDeg), Amber) }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) = OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }

@Composable
private fun Hint(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xCC000000)) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun YesNo(question: String, value: Boolean?, onChange: (Boolean) -> Unit) {
    Text(question, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, v) in listOf(T.no to false, T.yes to true)) {
            if (value == v) Button(onClick = { onChange(v) }) { Text(label) } else OutlinedButton(onClick = { onChange(v) }) { Text(label) }
        }
    }
}

/** Finestra del prototipo: sopra la fotocamera scurita, scorrevole. */
@Composable
private fun HybridWindow(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xF2202124), contentColor = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                content()
            }
        }
    }
}

/**
 * Lo schizzo con la scatola misurata sopra. Lati dello schizzo: gialli tratteggiati = da acquisire, verdi = misurati, arancioni =
 * confermati ma senza tratti accettati, grigi = mancanti. In bianco le pareti misurate (tratto visto) e in azzurro gli angoli
 * calcolati, portati nel riferimento dello schizzo solo per il disegno (rotazione dalle pareti, traslazione sui baricentri).
 */
@Composable
private fun HybridMap(sketch: RoomSketch, snap: HybridSnapshot, modifier: Modifier) {
    Canvas(modifier) {
        val n = sketch.sideCount
        val sk = (0 until n).map { sketch.start(it) }
        // Dal mondo allo schizzo: rotazione completa dalle normali interne (medoide), traslazione dai baricentri.
        val skCentroid = ArXZ(sk.sumOf { it.x } / n, sk.sumOf { it.z } / n)
        fun skInward(k: Int): ArXZ { val d = sketch.sideDirection(k); val nrm = ArXZ(-d.z, d.x); return if (((skCentroid - sketch.start(k)) dot nrm) >= 0) nrm else nrm * -1.0 }
        val measured = snap.walls.filter { it.state == WallState.MEASURED }
        val rots = measured.map { w -> val a = atan2(w.inward!!.z, w.inward!!.x) - atan2(skInward(w.side).z, skInward(w.side).x); atan2(sin(a), cos(a)) }
        val rot = rots.minByOrNull { r -> rots.sumOf { o -> abs(atan2(sin(o - r), cos(o - r))) } }
        fun toSk(p: ArXZ, shift: ArXZ): ArXZ { val r = -(rot ?: 0.0); return ArXZ(p.x * cos(r) - p.z * sin(r), p.x * sin(r) + p.z * cos(r)) + shift }
        val shift: ArXZ = run {
            val pts = snap.corners.withIndex().filter { it.value != null }
            if (rot == null) ArXZ(0.0, 0.0)
            else if (pts.size >= 2) {
                val m = pts.map { toSk(it.value!!, ArXZ(0.0, 0.0)) }; val s = pts.map { sk[it.index] }
                ArXZ(s.sumOf { it.x } / s.size - m.sumOf { it.x } / m.size, s.sumOf { it.z } / s.size - m.sumOf { it.z } / m.size)
            } else {
                val w = measured.first()
                val mid = toSk(w.point!! + w.direction!! * ((w.fromU!! + w.toU!!) / 2), ArXZ(0.0, 0.0))
                val target = (sketch.start(w.side) + sketch.end(w.side)) * 0.5
                target - mid
            }
        }
        val walls = measured.map { w -> toSk(w.point!! + w.direction!! * w.fromU!!, shift) to toSk(w.point!! + w.direction!! * w.toU!!, shift) }
        val corners = snap.corners.map { it?.let { c -> toSk(c, shift) } }
        // La superficie candidata (tratto inquadrato, in pianta), in tempo reale: dalla seconda parete si vede il suo angolo vero
        // rispetto alle pareti già misurate. Per la prima non c'è un riferimento di direzione: la si disegna sul suo lato.
        val cand = snap.candidate.patch?.takeIf { snap.phase == HybridPhase.WALLS }?.let { p -> ArXZ(p[0].x, p[0].z) to ArXZ(p[1].x, p[1].z) }
        val candSk = cand?.let { (a, b) ->
            if (rot != null) toSk(a, shift) to toSk(b, shift)
            else snap.targetSide?.let { t ->
                val d = b - a; val sd = sketch.sideDirection(t)
                var r = atan2(sd.z, sd.x) - atan2(d.z, d.x)
                r = atan2(sin(r), cos(r)); if (r > Math.PI / 2) r -= Math.PI else if (r < -Math.PI / 2) r += Math.PI
                fun rotp(q: ArXZ) = ArXZ(q.x * cos(r) - q.z * sin(r), q.x * sin(r) + q.z * cos(r))
                val ra = rotp(a); val rb = rotp(b); val off = (sketch.start(t) + sketch.end(t)) * 0.5 - (ra + rb) * 0.5
                (ra + off) to (rb + off)
            }
        }
        val all = sk + walls.flatMap { listOf(it.first, it.second) } + corners.filterNotNull() + listOfNotNull(candSk?.first, candSk?.second)
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }; val minZ = all.minOf { it.z }; val maxZ = all.maxOf { it.z }
        val scale = 0.85f * minOf(size.width / (maxX - minX).toFloat().coerceAtLeast(0.01f), size.height / (maxZ - minZ).toFloat().coerceAtLeast(0.01f))
        val ox = (size.width - (maxX - minX).toFloat() * scale) / 2; val oy = (size.height - (maxZ - minZ).toFloat() * scale) / 2
        fun o(p: ArXZ) = Offset(ox + (p.x - minX).toFloat() * scale, oy + (p.z - minZ).toFloat() * scale)
        val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
        for (k in 0 until n) {
            val w = snap.walls[k]
            // In giallo la parete da inquadrare: quella da riconfermare durante il riallineamento, altrimenti quella da acquisire.
            val target = k == (snap.realignTarget ?: snap.targetSide) && snap.phase == HybridPhase.WALLS
            val color = when {
                target -> Yellow
                w.state == WallState.MEASURED -> Green
                w.state == WallState.COLLECTING -> Amber
                else -> Color(0xFF78909C)
            }
            drawLine(color, o(sketch.start(k)), o(sketch.end(k)), strokeWidth = if (target) 6f else 3f, pathEffect = if (target || w.state == WallState.MISSING) dashed else null)
        }
        for ((a, b) in walls) drawLine(Color.White, o(a), o(b), strokeWidth = 6f)
        candSk?.let { (a, b) ->
            drawLine(if (snap.candidate.status == CandidateStatus.READY) Green else Color(0xFFE0E0E0), o(a), o(b), strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        }
        val cs = corners
        if (cs.all { it != null } && snap.box != BoxState.INCOMPLETE) for (k in 0 until n) drawLine(Color(0xFF4FC3F7), o(cs[k]!!), o(cs[(k + 1) % n]!!), strokeWidth = 2f)
        for (p in cs) if (p != null) drawCircle(Color(0xFF4FC3F7), radius = 5f, center = o(p))
    }
}

/** Registro del prototipo, accanto alla registrazione: riferimenti confermati, rifacimenti, esito. Una riga JSON per evento. */
internal class HybridEventFile(private val file: File) {
    companion object { const val FILE_NAME = "hybrid-events.jsonl" }

    private fun n(v: Double?) = v?.let { String.format(Locale.ROOT, "%.5f", it) } ?: "null"
    private fun plane(p: PlaneEstimate?) = p?.let { "[${n(it.nx)},${n(it.ny)},${n(it.nz)},${n(it.cx)},${n(it.cy)},${n(it.cz)},${n(it.sigmaM)},${it.points},${it.frames}]" } ?: "null"

    /** Intestazione: schizzo, aperture e scelta dei punti (punti buoni su quali superfici, limite nell'immagine; null = intera). */
    fun header(sketch: RoomSketch, openings: List<String>, sketchHeightM: Double?, goodPoints: Set<SurfaceKind>, maxImageRadius: Double?) = append(
        "{\"type\":\"header\",\"format\":\"sagoma-hybrid-events\",\"version\":1,\"sketch\":[" +
            (0 until sketch.sideCount).joinToString(",") { "${n(sketch.start(it).x)},${n(sketch.start(it).z)}" } +
            "],\"openings\":[${openings.joinToString(",") { "\"$it\"" }}],\"sketchHeightM\":${n(sketchHeightM)}," +
            "\"goodPoints\":[${goodPoints.joinToString(",") { "\"$it\"" }}],\"maxImageRadius\":${n(maxImageRadius)}}",
    )

    fun event(kind: String, side: Int?, arTimestampNs: Long, s: HybridSnapshot) {
        val ref = when {
            kind.endsWith("_FAILED") -> null // conferma non riuscita: nessun piano nuovo
            kind.startsWith("FLOOR") -> s.floor.reference
            kind.startsWith("CEILING") -> s.ceiling.reference
            side != null -> s.walls.getOrNull(side)?.reference
            else -> null
        }
        append(
            "{\"type\":\"event\",\"kind\":\"$kind\",\"side\":${side ?: "null"},\"arTimestampNs\":$arTimestampNs,\"phase\":\"${s.phase}\"," +
                "\"plane\":${plane(ref)},\"override\":${side?.let { s.walls.getOrNull(it)?.overriddenWarning?.let { o -> "\"$o\"" } } ?: "null"}," +
                "\"box\":\"${s.box}\",\"corners\":[${s.corners.joinToString(",") { c -> c?.let { "[${n(it.x)},${n(it.z)}]" } ?: "null" }}]}",
        )
    }

    /** Salto di posizione o di orientamento, o ripresa dopo un'interruzione del tracciamento ([afterPause]). */
    fun jump(distanceM: Double, rotationDeg: Double, afterPause: Boolean, arTimestampNs: Long, s: HybridSnapshot) = append(
        "{\"type\":\"jump\",\"distanceM\":${n(distanceM)},\"rotationDeg\":${n(rotationDeg)},\"afterPause\":$afterPause,\"arTimestampNs\":$arTimestampNs," +
            "\"unverified\":${s.unverified},\"jumps\":${s.jumps},\"discarded\":${s.discardedFrames},\"realignTarget\":${s.realignTarget ?: "null"}}",
    )

    /** Tracciamento di ARCore: perso (con il motivo, se ARCore lo dà) o ritrovato. */
    fun tracking(ok: Boolean, issue: String?, arTimestampNs: Long) = append(
        "{\"type\":\"tracking\",\"tracking\":$ok,\"issue\":${issue?.let { "\"$it\"" } ?: "null"},\"arTimestampNs\":$arTimestampNs}",
    )

    /** Riallineamento: riconferma (parete o pavimento) o salto, esito e correzione del mondo che ne risulta. */
    fun realign(kind: String, side: Int?, ok: Boolean, arTimestampNs: Long, s: HybridSnapshot) = append(
        "{\"type\":\"realign\",\"kind\":\"$kind\",\"side\":${side ?: "null"},\"ok\":$ok,\"arTimestampNs\":$arTimestampNs," +
            "\"pending\":${s.realignTarget != null},\"done\":[${s.realignDone.joinToString(",")}],\"needed\":${s.realignNeeded}," +
            "\"world\":[${n(Math.toDegrees(s.world.yaw))},${n(s.world.tx)},${n(s.world.ty)},${n(s.world.tz)}]}",
    )

    /** Ciò che l'utente vede, ogni 5 s: fase, stato, aree, pareti, pausa. */
    fun state(arTimestampNs: Long, s: HybridSnapshot) = append(
        "{\"type\":\"state\",\"arTimestampNs\":$arTimestampNs,\"phase\":\"${s.phase}\",\"candidate\":\"${s.candidate.status}\"," +
            "\"floorSeenM2\":${n(s.floor.seenAreaM2)},\"floorAcceptedM2\":${n(s.floor.areaM2)},\"ceilingSeenM2\":${n(s.ceiling.seenAreaM2)},\"ceilingAcceptedM2\":${n(s.ceiling.areaM2)}," +
            "\"walls\":[${s.walls.joinToString(",") { "\"${it.state}\"" }}],\"unverified\":${s.unverified},\"jumps\":${s.jumps},\"discarded\":${s.discardedFrames},\"unchecked\":${s.uncheckedFrames},\"depthFrames\":${s.depth.framesReceived}}",
    )

    private fun append(line: String) {
        try { file.appendText(line + "\n") } catch (e: Exception) { android.util.Log.e("SagomaHybrid", "registro non scritto", e) }
    }
}
