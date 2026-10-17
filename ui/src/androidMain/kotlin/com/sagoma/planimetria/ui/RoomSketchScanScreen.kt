package com.sagoma.planimetria.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.DetectedWall
import com.sagoma.planimetria.scan.WallObservation
import com.sagoma.planimetria.scan.assisted.AimFrameBuilder
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.recording.RecordedFrame
import com.sagoma.planimetria.scan.sketch.Answer
import com.sagoma.planimetria.scan.sketch.GeometryIssue
import com.sagoma.planimetria.scan.sketch.IssueCode
import com.sagoma.planimetria.scan.sketch.SessionEvent
import com.sagoma.planimetria.scan.sketch.SketchApply
import com.sagoma.planimetria.scan.sketch.SessionLogEntry
import com.sagoma.planimetria.scan.sketch.SessionLogHeader
import com.sagoma.planimetria.scan.sketch.SketchSession
import com.sagoma.planimetria.scan.sketch.SketchSessionLog
import com.sagoma.planimetria.scan.sketch.StartDeclaration
import com.sagoma.planimetria.scan.sketch.StartReference
import com.sagoma.planimetria.scanner.ArScanView
import com.sagoma.planimetria.scanner.ArSupport
import com.sagoma.planimetria.scanner.ArSupportCheck
import com.sagoma.planimetria.scanner.ScanRecorder
import com.sagoma.planimetria.ui.sketch.LocalSketchScanText
import com.sagoma.planimetria.ui.sketch.AimStatus
import com.sagoma.planimetria.ui.sketch.GuidePhase
import com.sagoma.planimetria.ui.sketch.GuideState
import com.sagoma.planimetria.ui.sketch.GuideWindow
import com.sagoma.planimetria.ui.sketch.WindowMemory
import com.sagoma.planimetria.ui.sketch.SketchScanGuide
import com.sagoma.planimetria.scan.sketch.SessionResult
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.ui.sketch.SketchScanText
import kotlinx.coroutines.delay
import java.io.File

/*
 * Percorso B (solo dove la funzione è disponibile, vedi FeatureAccess): la scansione guidata dallo schizzo. Lo schizzo è la
 * stanza stessa; la sessione ([SketchSession]) decide tutto, questa schermata la mostra e raccoglie i gesti. Nessun testo
 * fisso: le parole vengono da [SketchScanText]. La scansione "Scansiona con il telefono" (RoomScanScreen) non cambia.
 */

@Composable
internal fun RoomSketchScanScreen(activity: ComponentActivity, room: Room, onResult: (SketchScanOutcome?) -> Unit) {
    val t = LocalSketchScanText.current
    // Tutto scuro sopra la fotocamera: i pulsanti senza fondo (testo e contorno) usano colori chiari, leggibili. Con i colori
    // chiari dell'app il loro testo era scuro su fondo scuro.
    MaterialTheme(colorScheme = ScanColors) {
        ArGate(activity, t, onExit = { onResult(null) }) { SketchScanContent(activity, room, onResult) }
    }
}

private val ScanColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1F3A),
    surface = Color(0xFF202124),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCFD8DC),
    outline = Color(0xFF9AA0A6),
)

/** Supporto ARCore e permesso della fotocamera, poi il contenuto. (Stessa logica della scansione esistente, testi dal confine.) */
@Composable
internal fun ArGate(activity: ComponentActivity, t: SketchScanText, onExit: () -> Unit, content: @Composable () -> Unit) {
    data class Blocked(val title: String, val message: String, val askCamera: Boolean = false, val denied: Boolean = false)
    var ready by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<Blocked?>(null) }
    fun cameraGranted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val needCamera = Blocked(t.cameraTitle, t.cameraMessage, askCamera = true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { ready = true; blocked = null } else blocked = needCamera.copy(message = t.cameraDenied, denied = true)
    }
    LaunchedEffect(Unit) {
        var support = ArSupportCheck.check(activity)
        var tries = 0
        while (support == ArSupport.Checking && tries < 30) { delay(200); support = ArSupportCheck.check(activity); tries++ }
        when (support) {
            ArSupport.Unsupported -> blocked = Blocked(t.unsupportedTitle, t.unsupportedMessage)
            ArSupport.Checking -> blocked = Blocked(t.checkFailedTitle, t.checkFailedMessage)
            ArSupport.Ready, ArSupport.NeedsInstall -> if (cameraGranted()) ready = true else { launcher.launch(Manifest.permission.CAMERA); blocked = needCamera }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val b = blocked
        when {
            ready -> content()
            b != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(b.title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.size(12.dp))
                Text(b.message, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.size(24.dp))
                if (b.askCamera) {
                    Button(onClick = {
                        if (b.denied) activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                        else launcher.launch(Manifest.permission.CAMERA)
                    }) { Text(if (b.denied) t.openSettings else t.allowCamera) }
                }
                TextButton(onClick = onExit) { Text(if (b.askCamera) t.cancel else t.back) }
            }
            else -> Text(t.arChecking, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SketchScanContent(activity: ComponentActivity, room: Room, onResult: (SketchScanOutcome?) -> Unit) {
    val t = LocalSketchScanText.current
    val platform = LocalPlatform.current
    var session by remember { mutableStateOf(SketchSession(SketchApply.sketchOf(room), SketchApply.openingsOf(room))) }
    var builder by remember { mutableStateOf(AimFrameBuilder()) }
    var status by remember { mutableStateOf(ArScanView.Status()) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var installPending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var overrides by remember { mutableStateOf(mapOf<Long, Double>()) }
    var widthOverrides by remember { mutableStateOf(mapOf<Long, Double>()) }
    var recording by remember { mutableStateOf(false) }
    var lastRecording by remember { mutableStateOf<File?>(null) }
    var askRecordingConsent by remember { mutableStateOf(false) }
    var recordingConsent by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var declRef by remember { mutableStateOf<StartReference?>(null) }
    var declNote by remember { mutableStateOf("") }
    // Procedura guidata: revisione chiesta prima di finire le pareti, cambio della partenza, scelta del prossimo lato, dettagli.
    var reviewRequested by remember { mutableStateOf(false) }
    var choosingStart by remember { mutableStateOf(false) }
    var choosingNext by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showOpenings by remember { mutableStateOf(false) }
    val view = remember { ArScanView(activity) }
    // Registrazione: SOLO nelle build di prova (app installabile in debug), con consenso esplicito; mai nella versione pubblica.
    val recordingAllowed = remember { (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    // Costo del lavoro AR (frame → motore → sessione) sul telefono: media e massimo ogni 100 frame nei log, per la prova sul campo.
    val stepTimes = remember { LongArray(100) }
    var stepCount by remember { mutableStateOf(0) }
    // Registro degli eventi (file laterale della registrazione): partenza, pareti confermate con l'istante, risposte della revisione,
    // blocchi, esito. Si apre con la registrazione e resta aperto fino alla chiusura della schermata, anche dopo "Ferma" (per
    // esportare bisogna fermarsi, ma scelte ed esito vengono dopo: scan5 e scan6). Scrive solo eventi, niente immagini né profondità.
    // `frameClock` = timestamp ARCore ed elapsedMs dell'ultimo frame, per allineare i tempi.
    val frameClock = remember { java.util.concurrent.atomic.AtomicReference<LongArray?>(null) }
    var eventLog by remember { mutableStateOf<SessionEventFile?>(null) }
    val logged = remember { arrayOfNulls<SketchSession>(1) }
    val loggedBlocking = remember { arrayOfNulls<List<String>>(1) }

    /** Scrive ciò che è cambiato nella sessione dall'ultima scrittura; [issues] = i problemi già calcolati, se disponibili. */
    fun flushLog(issues: List<GeometryIssue>? = null) {
        val w = eventLog ?: return
        val base = logged[0] ?: return
        val now = session
        // I problemi servono per i valori delle risposte e per i blocchi: si usano quelli già calcolati dalla schermata.
        val current = issues ?: if (base.answers != now.answers || loggedBlocking[0] == null) now.result().geometry.issues else null
        w.write(SketchSessionLog.diff(base, now, current ?: emptyList()))
        if (current != null) {
            val blocking = SketchSessionLog.blockingSummary(current)
            if (blocking != loggedBlocking[0]) { w.write(listOf(SketchSessionLog.blocking(now, blocking))); loggedBlocking[0] = blocking }
        }
        logged[0] = now
    }

    fun startRecording() {
        val rec = view.startRecording() ?: return
        recording = true
        eventLog = SessionEventFile(File(rec.directory, SketchSessionLog.FILE_NAME), frameClock).also { it.header(SketchSessionLog.header(session)) }
        // Da una sessione nuova: il primo passaggio scrive tutto lo stato già raggiunto (se si registra a metà).
        logged[0] = SketchSession(session.sketch, session.openings)
        loggedBlocking[0] = null
    }

    fun stopRecording() {
        flushLog()
        // Il registro resta aperto (vedi sopra): lo ZIP esportato ora contiene gli eventi fino a qui, la cartella sul telefono
        // anche quelli dopo (scelte, blocchi, esito).
        lastRecording = view.stopRecording()
        recording = false
    }

    /** Fine della schermata: nel registro l'esito (pianta applicata o uscita), poi il risultato. */
    fun finish(outcome: SketchScanOutcome?) {
        flushLog()
        eventLog?.write(listOf(if (outcome != null) SketchSessionLog.applied(session, outcome.cornersAr) else SketchSessionLog.cancelled(session)))
        onResult(outcome)
    }

    /** "Esci" e il tasto indietro passano entrambi da qui: se c'è qualcosa da perdere, prima si chiede conferma. */
    fun requestExit() {
        if (session.hasUnsavedWork) confirmExit = true else finish(null)
    }

    DisposableEffect(view) {
        view.assistedMode = true
        view.listener = object : ArScanView.Listener {
            override fun onStatus(s: ArScanView.Status) { status = s }
            override fun onFloorPoint(point: ArPoint) {}
            override fun onWallObservations(observations: List<WallObservation>) {}
            override fun onTapRejected(reason: String) {}
            override fun onOverlay(c: List<ArScanView.ScreenPoint?>, edgeMidpoints: List<ArScanView.ScreenPoint?>) {}
            override fun onAimFrame(frame: RecordedFrame) {
                frameClock.set(longArrayOf(frame.timestampNs, frame.elapsedMs))
                val t0 = System.nanoTime()
                session = session.step(builder.next(frame))
                val i = stepCount % stepTimes.size
                stepTimes[i] = System.nanoTime() - t0
                stepCount++
                if (stepCount % stepTimes.size == 0) {
                    android.util.Log.i(
                        "SagomaSketch",
                        "lavoro AR per frame (ultimi ${stepTimes.size}): media ${"%.2f".format(stepTimes.average() / 1e6)} ms, " +
                            "massimo ${"%.2f".format(stepTimes.max() / 1e6)} ms",
                    )
                }
            }
        }
        onDispose {
            view.listener = null
            view.assistedMode = false
            if (view.recordingStats() != null) view.stopRecording()
            view.stop()
            view.release()
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

    // Messaggio del gesto rifiutato, per qualche secondo.
    LaunchedEffect(session.lastEvent) {
        val e = session.lastEvent
        if (e is SessionEvent.Rejected) { message = t.sessionRejection(e.why, e.side); delay(3000); message = null }
    }
    val result = remember(session) { session.result() }
    // Registro: a ogni cambio della sessione, se il registro è aperto (il confronto costa poco: niente da scrivere quasi sempre).
    LaunchedEffect(session, eventLog) { flushLog(result.geometry.issues) }
    val corners = result.geometry.corners
    val preview = remember(corners, overrides, widthOverrides) { corners?.let { SketchApply.preview(room, it, overrides, widthOverrides) } }
    // Una superficie che non corrisponde allo schizzo (vedi SketchSession.fitsSketch) non si disegna nemmeno.
    val proposed = session.candidate?.takeIf { it.state == CandidateState.PROPOSED && session.fitsSketch(it) }
    // Pareti confermate e parete proposta disegnate sopra la scena.
    LaunchedEffect(session.assigned, session.engine.walls) {
        val ids = session.assigned.values.map { it.wallId }.toSet()
        view.walls = session.engine.walls.filter { it.id in ids }.map { DetectedWall(it.line.a, it.line.b, it.line.minY, it.line.maxY, emptyList(), it.id) }
    }
    LaunchedEffect(proposed) { view.candidate = proposed?.line?.let { DetectedWall(it.a, it.b, it.minY, it.maxY, emptyList(), -1) } }

    // ---- Procedura a passi: cosa mostrare lo decide SketchScanGuide (fase, parete attiva, finestra del passo) ----
    val guide = remember(session, result, status.tracking, reviewRequested, choosingStart, preview) {
        SketchScanGuide.of(session, result, status.tracking, reviewRequested, preview?.canApply == true, choosingStart)
    }
    var memory by remember { mutableStateOf(WindowMemory()) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(guide.done) { memory = memory.normalized(guide.done) }
    val window = SketchScanGuide.window(guide, memory)
    fun nameOf(k: Int) = t.sideName(k, session.openings.filter { it.side == k }.map { it.kind })
    var choosingRedo by remember { mutableStateOf(false) }
    /** "Rifai": cancella la parete del lato [k] e riapre subito le istruzioni per inquadrarla di nuovo. */
    fun redo(k: Int) {
        session = session.removeWall(k)
        reviewRequested = false
        choosingRedo = false
        memory = memory.copy(introSeenFor = null)
    }
    fun undoLast() { session.assigned.maxByOrNull { it.value.confirmedAtMs }?.key?.let { redo(it) } }
    fun onRecord() { if (recording) stopRecording() else if (!recordingConsent) askRecordingConsent = true else startRecording() }
    // Indietro: prima si chiude la finestra aperta sopra il passo (dettagli, menu, scelta), poi si torna indietro, poi si esce.
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showDetails -> showDetails = false
            showMenu -> showMenu = false
            choosingNext -> choosingNext = false
            choosingRedo -> choosingRedo = false
            choosingStart && session.startSide != null -> choosingStart = false
            guide.phase == GuidePhase.REVIEW && reviewRequested -> reviewRequested = false
            else -> requestExit()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        // Fotocamera libera: nessuna finestra aperta, si inquadra la parete attiva.
        val aiming = window == null && guide.phase == GuidePhase.SCAN && fatal == null && !installPending &&
            !showMenu && !showDetails && !choosingNext && !choosingRedo
        val aimColor = when (guide.aim) {
            AimStatus.READY -> Color(0xFF66BB6A)
            AimStatus.STABILIZING -> Color(0xFFFFD91A)
            AimStatus.ALREADY_TAKEN, AimStatus.OFF_SKETCH -> Color(0xFFFF8A80)
            AimStatus.NO_TRACKING -> Color(0xFFFFC107)
            AimStatus.SEARCHING -> Color(0xFFB0BEC5)
        }
        if (aiming) {
            // Mirino: giallo quando la parete è pronta.
            Box(Modifier.align(Alignment.Center).size(28.dp), contentAlignment = Alignment.Center) {
                Text("+", color = if (guide.aim == AimStatus.READY) Color(0xFFFFD91A) else Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            // In alto, una barra sottile: quale parete, come va, menu.
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp), color = Color(0xCC000000)) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(aimColor, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.wallOfTotal(guide.wallNumber, guide.total) + (guide.activeSide?.let { " · " + nameOf(it) } ?: ""),
                            color = Color.White, style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (guide.aim == AimStatus.NO_TRACKING) status.trackingIssue?.let { t.trackingIssue(it) } ?: t.aimStatus(guide.aim) else t.aimStatus(guide.aim),
                            color = aimColor, style = MaterialTheme.typography.bodySmall,
                        )
                        if (recording) Text(t.recordingOn, color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { showMenu = true }) { Text(t.menu, color = Color.White) }
                }
            }
            // In basso, solo il pulsante grande (e gli avvisi del momento).
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Avvisi utili a chi scansiona (per esempio "potrebbe essere un mobile"), solo sulla parete pronta.
                if (guide.aim == AimStatus.READY) session.candidate?.advisories?.firstOrNull()?.let {
                    Hint(t.advisory(it), Color(0xFFFFC107))
                }
                message?.let { Hint(it, Color(0xFFFF8A80)) }
                Button(
                    onClick = { session = session.confirmWall(session.engine.lastTimeMs) },
                    enabled = guide.canConfirmWall, modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text(t.confirmWall, style = MaterialTheme.typography.titleMedium) }
            }
        }

        // ---- Le finestre: una alla volta ----
        val openMenu = { showMenu = true }
        when {
            fatal != null -> {}
            installPending -> StepWindow(t.installingArcore, t, onMenu = null) {}
            showDetails -> StepWindow(t.detailsShow, t, onMenu = null) {
                DetailsPanel(session, result, t, recordingAllowed, recording, lastRecording, platform,
                    onSession = { session = it }, onRecord = { onRecord() }, onDeleted = { lastRecording = null })
                Button(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth()) { Text(t.close) }
            }
            showMenu -> StepWindow(t.menuTitle, t, onMenu = null) {
                if (guide.phase == GuidePhase.SCAN && guide.activeSide != null) MenuItem(t.readInstructions) { showMenu = false; memory = memory.copy(introSeenFor = null) }
                if (session.startSide != null && !session.startConfirmed && session.assigned.isEmpty()) MenuItem(t.changeStart) { showMenu = false; choosingStart = true }
                if (session.startConfirmed && guide.phase == GuidePhase.SCAN) MenuItem(t.scanOtherWall) { showMenu = false; choosingNext = true }
                if (session.assigned.isNotEmpty()) MenuItem(t.redoWall) { showMenu = false; choosingRedo = true }
                if (session.assigned.isNotEmpty() && guide.phase == GuidePhase.SCAN) MenuItem(t.goToReview) { showMenu = false; reviewRequested = true }
                if (guide.canResumeScan) MenuItem(t.backToScan) { showMenu = false; reviewRequested = false }
                if (recordingAllowed) MenuItem(if (recording) t.stopRecording else t.record) { showMenu = false; onRecord() }
                MenuItem(t.detailsShow) { showMenu = false; showDetails = true }
                MenuItem(t.exit) { showMenu = false; requestExit() }
                TextButton(onClick = { showMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(t.close) }
            }
            choosingNext -> StepWindow(t.chooseNextTitle, t, onMenu = null) {
                GuideSketch(session, guide, Modifier.fillMaxWidth().height(150.dp))
                for (k in 0 until session.n) if (k !in session.assigned) OutlinedButton(
                    onClick = { session = session.setTarget(k); choosingNext = false; memory = memory.copy(doneSeen = guide.done) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(nameOf(k)) }
                TextButton(onClick = { choosingNext = false }) { Text(t.back) }
            }
            choosingRedo -> StepWindow(t.redoTitle, t, onMenu = null) {
                Text(t.redoExplain, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                GuideSketch(session, guide, Modifier.fillMaxWidth().height(150.dp))
                // Dalla più recente: di solito si rifà quella appena fatta.
                for (k in session.assigned.entries.sortedByDescending { it.value.confirmedAtMs }.map { it.key }) OutlinedButton(
                    onClick = { redo(k) }, modifier = Modifier.fillMaxWidth(),
                ) { Text(t.redoSide(nameOf(k))) }
                TextButton(onClick = { choosingRedo = false }) { Text(t.back) }
            }
            window == GuideWindow.START -> StepWindow(t.startQuestion, t, onMenu = openMenu) {
                GuideSketch(session, guide, Modifier.fillMaxWidth().height(170.dp))
                Text(t.startExplain, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodyMedium)
                for (k in 0 until session.n) {
                    val label = nameOf(k) + if (guide.suggestedStart == k) " · " + t.suggestedTag else ""
                    val pick = { session = session.chooseStartSide(k); choosingStart = false }
                    if (guide.suggestedStart == k) Button(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    else OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                if (recordingAllowed && !recording) {
                    Text(t.recordFirstHint, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick = { onRecord() }) { Text(t.record) }
                }
                if (recording) Text(t.recordingOn, color = Color(0xFFFF5252), style = MaterialTheme.typography.labelSmall)
            }
            window == GuideWindow.WALL_INTRO -> StepWindow(t.wallOfTotal(guide.wallNumber, guide.total), t, onMenu = openMenu) {
                guide.activeSide?.let { Text(nameOf(it), color = Color(0xFFFFD91A), style = MaterialTheme.typography.titleMedium) }
                GuideSketch(session, guide, Modifier.fillMaxWidth().height(150.dp))
                t.introSteps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { memory = memory.copy(introSeenFor = guide.activeSide) }, modifier = Modifier.fillMaxWidth()) { Text(t.begin) }
                if (!session.startConfirmed && session.assigned.isEmpty()) TextButton(onClick = { choosingStart = true }) { Text(t.changeStart) }
                else if (session.startConfirmed) TextButton(onClick = { choosingNext = true }) { Text(t.scanOtherWall) }
            }
            window == GuideWindow.WALL_DONE -> {
                val allDone = guide.remaining == 0
                StepWindow(if (allDone) t.allWallsDone else t.wallDone(guide.lastDoneSide?.let { nameOf(it) } ?: ""), t, onMenu = openMenu) {
                    Text(t.progress(guide.done, guide.total), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodyMedium)
                    GuideSketch(session, guide, Modifier.fillMaxWidth().height(150.dp))
                    if (allDone) Text(t.allWallsDoneExplain, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    else guide.activeSide?.let { side ->
                        Text(t.nextWall(guide.wallNumber, guide.total) + ": " + nameOf(side), color = Color(0xFFFFD91A), style = MaterialTheme.typography.titleMedium)
                        Text(guide.nextTurn?.let { t.turnHint(it) } ?: t.turnUnknown, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { memory = memory.afterDone(guide) }, modifier = Modifier.fillMaxWidth()) { Text(t.next) }
                    // Non convinto della parete appena presa: si rifà subito, con un tocco.
                    OutlinedButton(onClick = { undoLast() }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.redoSide(guide.lastDoneSide?.let { nameOf(it) } ?: ""))
                    }
                    if (!allDone && session.startConfirmed) TextButton(onClick = { choosingNext = true }) { Text(t.scanOtherWall) }
                }
            }
            window == GuideWindow.QUESTION -> guide.questions.firstOrNull()?.let { q ->
                StepWindow(if (guide.phase == GuidePhase.QUESTION) t.questionTitle else t.reviewQuestionsTitle, t, onMenu = openMenu) {
                    QuestionCard(q, session, t, declRef, declNote,
                        onDecl = { r, note -> declRef = r; declNote = note; r?.let { session = session.declareStart(StartDeclaration(it, note)) } },
                        onAnswer = { session = session.answer(q.key, it) })
                }
            }
            window == GuideWindow.ANGLES -> guide.angleChoice?.let { a ->
                StepWindow(t.angleTitle, t, onMenu = openMenu) {
                    Text(t.angleExplain(a.values["spostamentoMaxM"], a.values["scartoMaxDeg"]), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    for (ans in a.code.allowedAnswers) OutlinedButton(onClick = { session = session.answer(a.key, ans) }, modifier = Modifier.fillMaxWidth()) { Text(t.answer(ans)) }
                    if (guide.canResumeScan) TextButton(onClick = { reviewRequested = false }) { Text(t.backToScan) }
                }
            }
            window == GuideWindow.FIX -> StepWindow(t.reviewTodoTitle, t, onMenu = openMenu) {
                for (b in guide.blocking) {
                    Text("• " + t.issue(b), color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodyMedium)
                    if (b.code == IssueCode.UNDERDETERMINED) ManualLengths(b, session, t) { session = it }
                }
                if (guide.remaining > 0) {
                    Text(t.wallsMissing(guide.remaining), color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { reviewRequested = false }, modifier = Modifier.fillMaxWidth()) { Text(t.backToScan) }
                }
                AngleRecap(guide, session, t) { session = it }
                if (session.assigned.isNotEmpty()) OutlinedButton(onClick = { choosingRedo = true }, modifier = Modifier.fillMaxWidth()) { Text(t.redoWall) }
                TextButton(onClick = { showDetails = true }) { Text(t.detailsShow) }
            }
            window == GuideWindow.SUMMARY -> StepWindow(t.measuresTitle, t, onMenu = openMenu) {
                val g = result.geometry
                if (guide.canApply && g.warnings.isEmpty()) Text(t.reviewAllGood, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodyMedium)
                if (guide.remaining > 0) {
                    Text(t.wallsMissing(guide.remaining), color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { reviewRequested = false }, modifier = Modifier.fillMaxWidth()) { Text(t.backToScan) }
                }
                for (s in g.sides) Text(t.sideLine(s.index, s.lengthM, s.sketchLengthM, s.lengthSources, s.isEstimate), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                g.areaM2?.let { ar -> g.perimeterM?.let { p -> Text(t.areaPerimeter(ar, p), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodyMedium) } }
                for (w in g.warnings) Text("• " + t.issue(w), color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall)
                AngleRecap(guide, session, t) { session = it }
                val changes = preview?.openings.orEmpty()
                if (changes.isNotEmpty()) {
                    TextButton(onClick = { showOpenings = !showOpenings }) { Text((if (showOpenings) "▾ " else "▸ ") + t.openingsAdjustTitle) }
                    // Un'apertura che non entra blocca "Applica": la sezione si apre da sola per farla vedere.
                    if (showOpenings || changes.any { !it.fits }) OpeningsAdjust(room, changes, t, overrides, widthOverrides,
                        onPosition = { id, cm -> overrides = if (cm == null) overrides - id else overrides + (id to cm) },
                        onWidth = { id, cm -> widthOverrides = if (cm == null) widthOverrides - id else widthOverrides + (id to cm) })
                }
                if (!guide.canApply) Text(t.applyNeedsFixes, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall)
                Button(
                    onClick = { corners?.let { finish(SketchScanOutcome(it, overrides, widthOverrides)) } },
                    enabled = guide.canApply, modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(t.applyRoom) }
                if (session.assigned.isNotEmpty()) TextButton(onClick = { choosingRedo = true }, modifier = Modifier.fillMaxWidth()) { Text(t.redoWall) }
            }
        }

        if (confirmExit) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            text = { Text(t.exitConfirm) },
            confirmButton = { TextButton(onClick = { confirmExit = false; finish(null) }) { Text(t.exitConfirmYes) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(t.stay) } },
        )
        if (askRecordingConsent) androidx.compose.material3.AlertDialog(
            onDismissRequest = { askRecordingConsent = false },
            title = { Text(t.recordingConsentTitle) },
            text = { Text(t.recordingConsentText) },
            confirmButton = {
                TextButton(onClick = {
                    askRecordingConsent = false
                    recordingConsent = true
                    startRecording()
                }) { Text(t.recordingConsentYes) }
            },
            dismissButton = { TextButton(onClick = { askRecordingConsent = false }) { Text(t.cancel) } },
        )

        fatal?.let {
            Surface(Modifier.fillMaxSize(), color = Color(0xF2000000)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.unavailableTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(12.dp))
                    Text(it, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(24.dp))
                    Button(onClick = { finish(null) }) { Text(t.back) }
                }
            }
        }
    }
}

/**
 * Una finestra della procedura: sopra la fotocamera, che resta visibile scurita. Scura come il resto della scansione (i testi
 * delle parti in comune sono chiari). [onMenu] null = senza "Menu" (il menu stesso, i dettagli).
 */
@Composable
private fun StepWindow(title: String, t: SketchScanText, onMenu: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xF2202124), contentColor = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (onMenu != null) TextButton(onClick = onMenu) { Text(t.menu) }
                }
                content()
            }
        }
    }
}

/** Un avviso breve sopra il pulsante, leggibile sulla fotocamera. */
@Composable
private fun Hint(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xCC000000)) {
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/** La scelta sugli angoli già fatta, con "Cambia" (riapre la domanda). */
@Composable
private fun AngleRecap(guide: GuideState, session: SketchSession, t: SketchScanText, onChange: (SketchSession) -> Unit) {
    val a = guide.angleChoice ?: return
    val answer = a.answer ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t.angleTitle + ": " + t.answer(answer), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(session.answer(a.key, null)) }) { Text(t.changeChoice) }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }

@Composable
private fun Section(title: String) = Text(title, color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelLarge)

/** "Sposta su…": riassegna la parete del lato `from` a un altro lato (se è occupato le due si scambiano). */
@Composable
private fun MoveMenu(n: Int, from: Int, t: SketchScanText, onMove: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(t.moveTo) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (k in 0 until n) if (k != from) DropdownMenuItem(text = { Text(t.side(k)) }, onClick = { open = false; onMove(k) })
        }
    }
}

/** Lati senza misura: lunghezza a mano (metri) oppure, dichiaratamente, la stima dello schizzo. */
@Composable
private fun ManualLengths(issue: GeometryIssue, session: SketchSession, t: SketchScanText, onChange: (SketchSession) -> Unit) {
    Text(t.manualLengthTitle, color = Color.White, style = MaterialTheme.typography.labelMedium)
    for (k in issue.sides) {
        var text by remember(k) { mutableStateOf(session.manualLengthsM[k]?.toString() ?: "") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { v ->
                text = v
                onChange(session.setManualLength(k, v.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }))
            }, label = { Text(t.side(k) + " · " + t.manualLengthLabel) }, modifier = Modifier.weight(1f))
            Checkbox(checked = k in session.acceptedSketchLengths, onCheckedChange = { onChange(session.acceptSketchLength(k, it)) })
            Text(t.useSketchLength, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Lo schizzo in piccolo, con i numeri dei lati. Non solo colori: la parete ATTIVA è gialla, spessa e tratteggiata; quelle
 * COMPLETATE sono verdi, spesse e piene; le altre sottili; la partenza ha un pallino.
 */
@Composable
private fun GuideSketch(session: SketchSession, guide: GuideState, modifier: Modifier) {
    val c = session.sketch.corners
    val measurer = rememberTextMeasurer()
    Canvas(modifier.background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))) {
        if (c.size < 3) return@Canvas
        val minX = c.minOf { it.x }; val maxX = c.maxOf { it.x }; val minZ = c.minOf { it.z }; val maxZ = c.maxOf { it.z }
        val scale = 0.70f * minOf(size.width / (maxX - minX).toFloat(), size.height / (maxZ - minZ).toFloat())
        val ox = (size.width - (maxX - minX).toFloat() * scale) / 2
        val oy = (size.height - (maxZ - minZ).toFloat() * scale) / 2
        fun p(k: Int) = c[k % c.size].let { Offset(ox + (it.x - minX).toFloat() * scale, oy + (it.z - minZ).toFloat() * scale) }
        val centre = Offset(size.width / 2, size.height / 2)
        val dashed = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
        for (k in c.indices) {
            val active = k == guide.activeSide
            val done = k in guide.doneSides
            val color = when { active -> Color(0xFFFFD91A); done -> Color(0xFF66BB6A); else -> Color(0xFFCFD8DC) }
            drawLine(color, p(k), p(k + 1), strokeWidth = if (active || done) 7f else 2.5f, pathEffect = if (active) dashed else null)
            val mid = (p(k) + p(k + 1)) / 2f
            if (k == guide.startSide) drawCircle(color, radius = 6f, center = mid)
            // Numero del lato, appena fuori dal perimetro (verso l'esterno rispetto al centro).
            val dir = (mid - centre).let { d -> val l = d.getDistance(); if (l > 0f) d / l else d }
            val label = measurer.measure((k + 1).toString(), TextStyle(color = color, fontSize = 11.sp))
            drawText(label, topLeft = mid + dir * 11f - Offset(label.size.width / 2f, label.size.height / 2f))
        }
    }
}

/** Una domanda con le sue risposte; per la partenza ambigua, prima la dichiarazione del riferimento usato (è dell'utente). */
@Composable
private fun QuestionCard(
    q: GeometryIssue, session: SketchSession, t: SketchScanText, declRef: StartReference?, declNote: String,
    onDecl: (StartReference?, String) -> Unit, onAnswer: (Answer) -> Unit,
) {
    Text(t.issue(q), color = Color.White, style = MaterialTheme.typography.bodyMedium)
    if (q.answer != null) { Text("✓ " + t.answer(q.answer!!), color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall); return }
    if (q.code == IssueCode.ASSOCIATION_AMBIGUOUS) {
        Text(t.declarationTitle, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in StartReference.entries) FilterChip(selected = declRef == r, onClick = { onDecl(r, declNote) }, label = { Text(t.startReference(r)) })
        }
        OutlinedTextField(value = declNote, onValueChange = { v -> onDecl(declRef, v) }, label = { Text(t.declarationNoteHint) }, modifier = Modifier.fillMaxWidth())
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (a in q.code.allowedAnswers) {
            val enabled = a != Answer.START_WALL_VERIFIED || session.startDeclaration != null
            OutlinedButton(onClick = { onAnswer(a) }, enabled = enabled) { Text(t.answer(a)) }
        }
    }
}

/** Porte e finestre dopo la misura: posizione e larghezza correggibili (una larghezza oltre il muro non vale). */
@Composable
private fun OpeningsAdjust(
    room: Room, changes: List<com.sagoma.planimetria.scan.sketch.OpeningChange>, t: SketchScanText,
    overrides: Map<Long, Double>, widthOverrides: Map<Long, Double>,
    onPosition: (Long, Double?) -> Unit, onWidth: (Long, Double?) -> Unit,
) {
    for (ch in changes) {
        val o = room.openings.first { it.id == ch.openingId }
        Text(t.openingLine(t.openingKind(o.kind), ch.wallIndex, ch.oldPosition, ch.newPosition, ch.newWidth, ch.fits),
            color = if (ch.fits) Color.White else Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
        var text by remember(ch.openingId) { mutableStateOf(overrides[ch.openingId]?.toString() ?: "") }
        OutlinedTextField(value = text, onValueChange = { v ->
            text = v
            onPosition(ch.openingId, v.replace(',', '.').toDoubleOrNull())
        }, label = { Text(t.openingPositionLabel) }, modifier = Modifier.fillMaxWidth())
        var widthText by remember(ch.openingId) { mutableStateOf(widthOverrides[ch.openingId]?.toString() ?: "") }
        OutlinedTextField(value = widthText, onValueChange = { v ->
            widthText = v
            onWidth(ch.openingId, v.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= ch.newWallLength })
        }, label = { Text(t.openingWidthLabel + " (" + t.centimeters(ch.newWallLength) + ")") },
            isError = widthText.isNotBlank() && widthOverrides[ch.openingId] == null, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Dettagli tecnici e strumenti, fuori dal percorso normale: tabella lati ↔ pareti (sposta, togli, tutte ±1 lato), stato del
 * motore con i motivi, risposte non valide, registrazione per le prove (solo build di prova).
 */
@Composable
private fun DetailsPanel(
    session: SketchSession, result: SessionResult, t: SketchScanText, recordingAllowed: Boolean, recording: Boolean,
    lastRecording: File?, platform: Platform, onSession: (SketchSession) -> Unit, onRecord: () -> Unit, onDeleted: () -> Unit,
) {
    Section(t.associationTitle)
    for (row in result.associations) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val parts = listOfNotNull(
                row.wallId?.let { t.wall(it) } ?: t.noWall,
                row.expectedOpenings.takeIf { it.isNotEmpty() }?.joinToString { t.opening(it) },
                t.startMark.takeIf { row.isStart },
                t.targetMark.takeIf { row.isTarget },
                t.inwardNotMeasured.takeIf { row.inwardKnown == false },
                row.openingCheck?.let { "✓ " + t.answer(it) },
            )
            Text(t.side(row.side) + ": " + parts.joinToString(" · "), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (row.wallId != null) {
                MoveMenu(session.n, row.side, t) { to -> onSession(session.reassign(row.side, to)) }
                TextButton(onClick = { onSession(session.removeWall(row.side)) }) { Text(t.remove) }
            }
        }
    }
    if (session.assigned.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onSession(session.shiftAll(-1)) }) { Text(t.shiftAllBack) }
        OutlinedButton(onClick = { onSession(session.shiftAll(1)) }) { Text(t.shiftAllForward) }
    }
    session.candidate?.let { c ->
        Text(t.candidateState(c.state) + if (c.reasons.isNotEmpty()) " (" + c.reasons.joinToString { t.reason(it) } + ")" else "",
            color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
        if (c.advisories.isNotEmpty()) Text(c.advisories.joinToString("; ") { t.advisory(it) }, color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall)
    }
    for (r in result.geometry.rejectedAnswers) Text("✗ " + t.answer(r.answer) + ": " + t.rejection(r.reason), color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
    Text(listOf(t.legendStart, t.legendTarget, t.legendAssigned).joinToString("  "), color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall)
    // Registrazione (solo build di prova): consenso esplicito, salvata sul telefono, esportabile e cancellabile.
    if (recordingAllowed) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRecord) { Text(if (recording) t.stopRecording else t.record) }
        val android = platform as? AndroidPlatform
        if (!recording && lastRecording != null && lastRecording.exists()) {
            if (lastRecording.isDirectory && android != null) OutlinedButton(onClick = {
                android.saveStream(lastRecording.name + ".zip", "application/zip", { out -> ScanRecorder.writeZip(lastRecording, out) }) { ok ->
                    platform.toast(if (ok) t.recordingExported else t.recordingExportFailed)
                }
            }) { Text(t.export) }
            OutlinedButton(onClick = {
                lastRecording.deleteRecursively()
                onDeleted()
                platform.toast(t.recordingDeleted)
            }) { Text(t.deleteRecording) }
        }
    }
}

/**
 * Il file degli eventi della sessione accanto alla registrazione ([SketchSessionLog.FILE_NAME]): una riga per evento, scritta
 * subito (se l'app si chiude non si perde niente). L'istante sull'orologio di ARCore si ricava dall'ultimo frame:
 * timestamp − (elapsedMs dell'ultimo frame − sessionMs dell'evento).
 */
internal class SessionEventFile(private val file: File, private val clock: java.util.concurrent.atomic.AtomicReference<LongArray?>) {
    private var seq = 0

    fun header(h: SessionLogHeader) = append(SketchSessionLog.encode(h))

    fun write(entries: List<SessionLogEntry>) {
        for (e in entries) {
            val c = clock.get()
            val ms = e.sessionMs
            val ar = if (c != null && ms != null) c[0] - (c[1] - ms) * 1_000_000L else null
            append(SketchSessionLog.encode(e.copy(seq = seq++, arTimestampNs = ar)))
        }
    }

    private fun append(line: String) {
        try {
            file.appendText(line + "\n")
        } catch (e: Exception) {
            android.util.Log.e("SagomaSketch", "registro degli eventi non scritto", e)
        }
    }
}
