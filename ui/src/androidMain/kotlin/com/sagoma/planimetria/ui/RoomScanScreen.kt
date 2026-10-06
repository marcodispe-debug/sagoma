package com.sagoma.planimetria.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ScanDraft
import com.sagoma.planimetria.scan.ScanResult
import com.sagoma.planimetria.scan.WallObservation
import com.sagoma.planimetria.scan.WallOutline
import com.sagoma.planimetria.scan.WallScan
import com.sagoma.planimetria.scanner.ArScanView
import com.sagoma.planimetria.scanner.ArSupport
import com.sagoma.planimetria.scanner.ArSupportCheck
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Scansione delle stanze su Android: ARCore per la fotocamera e il pavimento, Compose per messaggi e comandi. */
class AndroidRoomScanner(private val activity: ComponentActivity) : RoomScanner {
    @Composable
    override fun Screen(onResult: (ScanResult?) -> Unit) = RoomScanScreen(activity, onResult)
}

private sealed interface ScanPhase {
    data object Checking : ScanPhase

    /** La scansione non si può fare: `message` spiega perché; `askCamera` offre di richiedere (o aprire le impostazioni) il permesso. */
    data class Blocked(val title: String, val message: String, val askCamera: Boolean = false, val denied: Boolean = false) : ScanPhase

    data object Scanning : ScanPhase
}

@Composable
private fun RoomScanScreen(activity: ComponentActivity, onResult: (ScanResult?) -> Unit) {
    var phase by remember { mutableStateOf<ScanPhase>(ScanPhase.Checking) }
    fun cameraGranted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val needCamera = ScanPhase.Blocked(
        "Serve la fotocamera",
        "Per scansionare la stanza Sagoma deve usare la fotocamera. Non salva né invia nessuna immagine.",
        askCamera = true,
    )
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        phase = if (granted) ScanPhase.Scanning
        else needCamera.copy(message = "Senza il permesso della fotocamera non posso scansionare la stanza.", denied = true)
    }
    // 1) Il telefono supporta ARCore? 2) Il permesso della fotocamera c'è?
    LaunchedEffect(Unit) {
        var support = ArSupportCheck.check(activity)
        var tries = 0
        while (support == ArSupport.Checking && tries < 30) { // il sistema, al primo avvio, può rispondere "sto controllando"
            delay(200)
            support = ArSupportCheck.check(activity)
            tries++
        }
        phase = when (support) {
            ArSupport.Unsupported -> ScanPhase.Blocked(
                "Telefono non compatibile",
                "Questo telefono non supporta ARCore, la realtà aumentata di Google: la scansione non è disponibile. " +
                    "Puoi creare la stanza con \"Da rilievo\" o \"Muro per muro\".",
            )
            ArSupport.Checking -> ScanPhase.Blocked(
                "Verifica non riuscita",
                "Non riesco a verificare se questo telefono supporta ARCore. Controlla la connessione e riprova più tardi.",
            )
            // NeedsInstall: l'installazione di ARCore la propone la sessione, appena parte (serve comunque la fotocamera).
            ArSupport.Ready, ArSupport.NeedsInstall -> if (cameraGranted()) ScanPhase.Scanning else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
                needCamera
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val p = phase) {
            ScanPhase.Checking -> Text("Controllo la realtà aumentata…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            is ScanPhase.Blocked -> BlockedPanel(
                p,
                onGrant = {
                    if (p.denied) {
                        activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
                        )
                    } else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onExit = { onResult(null) },
            )
            ScanPhase.Scanning -> ScanningContent(activity, onResult)
        }
    }
}

@Composable
private fun BlockedPanel(p: ScanPhase.Blocked, onGrant: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.size(12.dp))
        Text(p.message, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.size(24.dp))
        if (p.askCamera) {
            Button(onClick = onGrant) { Text(if (p.denied) "Apri le impostazioni" else "Consenti la fotocamera") }
            Spacer(Modifier.size(8.dp))
        }
        TextButton(onClick = onExit) { Text(if (p.askCamera) "Annulla" else "Indietro") }
    }
}

@Composable
private fun ScanningContent(activity: ComponentActivity, onResult: (ScanResult?) -> Unit) {
    // Due modi, che si possono usare insieme: le pareti rilevate da ARCore (automatico) e gli angoli toccati a mano (come prima).
    var draft by remember { mutableStateOf(ScanDraft()) }
    var wallScan by remember { mutableStateOf(WallScan()) }
    // "Chiudi" con una parete mancante: la serie di pareti si chiude con un lato dritto tra i due estremi.
    var chainClosed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ArScanView.Status()) }
    var corners by remember { mutableStateOf<List<ArScanView.ScreenPoint?>>(emptyList()) }
    var mids by remember { mutableStateOf<List<ArScanView.ScreenPoint?>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var installPending by remember { mutableStateOf(false) }
    val view = remember { ArScanView(activity) }

    DisposableEffect(view) {
        view.listener = object : ArScanView.Listener {
            override fun onStatus(s: ArScanView.Status) { status = s }
            override fun onWallObservations(observations: List<WallObservation>) { wallScan = wallScan.update(observations) }
            override fun onFloorPoint(point: ArPoint) {
                draft = draft.add(point)
                message = null
            }
            override fun onTapRejected(reason: String) { message = reason }
            override fun onOverlay(c: List<ArScanView.ScreenPoint?>, edgeMidpoints: List<ArScanView.ScreenPoint?>) {
                corners = c
                mids = edgeMidpoints
            }
        }
        onDispose {
            view.listener = null
            view.stop()
            view.release()
        }
    }

    val walls = remember(wallScan) { wallScan.walls() }
    val layout = remember(walls) { WallOutline.layout(walls) }
    val floorY = status.floorY ?: walls.minOfOrNull { it.bottomY } ?: 0.0
    // Il perimetro mostrato: quello toccato a mano, se c'è; altrimenti quello ricostruito dalle pareti (chiuso da solo, o chiuso con "Chiudi").
    val wallDraft: ScanDraft? = layout.closed?.let { WallOutline.toDraft(it, floorY) }
        ?: if (chainClosed) layout.closableChain?.let { WallOutline.toDraft(it, floorY) } else null
    val effective = if (draft.points.isNotEmpty()) draft else wallDraft ?: ScanDraft()
    val room = effective.toScannedRoom()
    val canCloseChain = draft.points.isEmpty() && wallDraft == null && layout.closableChain != null

    LaunchedEffect(effective) { view.draft = effective }
    LaunchedEffect(walls) { view.walls = walls }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        when (val r = view.start(activity)) {
            ArScanView.StartResult.Started -> { fatal = null; installPending = false }
            ArScanView.StartResult.InstallRequested -> installPending = true
            is ArScanView.StartResult.Failed -> fatal = r.message
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { view.stop() }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        // Numero dei punti e lunghezza dei lati, sopra la scena.
        for ((i, c) in corners.withIndex()) {
            if (c != null) Marker("${i + 1}", c, density, 24.dp, Color(0xFFE8A200))
        }
        for ((i, m) in mids.withIndex()) {
            val length = effective.edgeLengthsCm.getOrNull(i)
            if (m != null && length != null) Marker(formatMeters(length), m, density, 76.dp, Color(0xCC000000))
        }

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xCC000000)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        instruction(status, draft.points.size, walls.size, effective.closed, room != null, fatal, installPending),
                        color = Color.White, style = MaterialTheme.typography.titleSmall,
                    )
                    if (fatal == null) {
                        Text(
                            "Pareti rilevate: ${walls.size}" + if (draft.points.isNotEmpty()) " · Punti: ${draft.points.size}" else "",
                            color = Color(0xFFFF8AD8), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (draft.points.isEmpty() && layout.closed == null && !chainClosed && layout.closableChain != null) {
                        Text(
                            "Se una parete non si riesce a inquadrare, premi Chiudi: il perimetro si chiude con un lato dritto.",
                            color = Color.White, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    message?.let { Text(it, color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall) }
                    if (effective.closed && room == null) {
                        Text(
                            "Il perimetro si incrocia o è troppo piccolo: annulla e correggi.",
                            color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color(0xE6101010)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val lengths = effective.edgeLengthsCm
                if (walls.isNotEmpty() || lengths.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 110.dp).verticalScroll(rememberScrollState())) {
                        for ((i, w) in walls.withIndex()) {
                            val height = status.floorY?.let { f ->
                                " · da terra ${formatMeters(w.bottomAboveFloor(f) * 100)} – ${formatMeters(w.topAboveFloor(f) * 100)}"
                            } ?: ""
                            Text(
                                "Parete ${i + 1}: ${formatMeters(w.length * 100)}$height",
                                color = Color(0xFFFF8AD8), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        for ((i, l) in lengths.withIndex()) {
                            val to = if (i + 1 < effective.points.size) i + 2 else 1
                            Text("Lato ${i + 1} → $to: ${formatMeters(l)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        if (effective.closed) {
                            Text(
                                "Perimetro ${formatMeters(effective.perimeterCm)} · Area ${formatSquareMeters(effective.areaM2)}",
                                color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            when {
                                draft.points.isNotEmpty() -> draft = draft.undoLast()
                                chainClosed -> chainClosed = false
                                else -> wallScan = wallScan.removeNewest()
                            }
                        },
                        enabled = draft.points.isNotEmpty() || chainClosed || walls.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("↶ Annulla") }
                    OutlinedButton(
                        onClick = { draft = ScanDraft(); wallScan = wallScan.clear(); chainClosed = false },
                        enabled = draft.points.isNotEmpty() || !wallScan.isEmpty || chainClosed,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancella") }
                    OutlinedButton(
                        onClick = { if (draft.canClose) draft = draft.close() else chainClosed = true },
                        enabled = draft.canClose || canCloseChain,
                        modifier = Modifier.weight(1f),
                    ) { Text("Chiudi") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onResult(null) }, modifier = Modifier.weight(1f)) { Text("Esci") }
                    Button(
                        onClick = { room?.let { onResult(ScanResult(it, walls.map { w -> w.toScanned(status.floorY) })) } },
                        enabled = room != null,
                        modifier = Modifier.weight(2f),
                    ) { Text("Conferma stanza") }
                }
            }
        }

        fatal?.let {
            Surface(Modifier.fillMaxSize(), color = Color(0xF2000000)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Scansione non disponibile", style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(12.dp))
                    Text(it, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(24.dp))
                    Button(onClick = { onResult(null) }) { Text("Indietro") }
                }
            }
        }
    }
}

/** Etichetta larga `width`, centrata su un punto dello schermo (pixel). */
@Composable
private fun Marker(text: String, at: ArScanView.ScreenPoint, density: androidx.compose.ui.unit.Density, width: androidx.compose.ui.unit.Dp, background: Color) {
    val halfWidth = with(density) { (width / 2).roundToPx() }
    val halfHeight = with(density) { 11.dp.roundToPx() }
    Box(
        Modifier.offset { IntOffset(at.x.roundToInt() - halfWidth, at.y.roundToInt() - halfHeight) }.width(width),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun instruction(
    status: ArScanView.Status,
    manualPoints: Int,
    wallCount: Int,
    closed: Boolean,
    valid: Boolean,
    fatal: String?,
    installPending: Boolean,
): String = when {
    fatal != null -> "Scansione non disponibile"
    installPending -> "Sto installando ARCore: completa l'installazione e torna qui"
    !status.tracking -> status.trackingHint ?: "Muovi lentamente il telefono per rilevare il pavimento"
    !status.floorDetected -> "Muovi lentamente il telefono per rilevare il pavimento"
    closed && valid -> if (manualPoints > 0) "Perimetro chiuso: controlla le misure e conferma" else "Perimetro ricostruito dalle pareti: controlla le misure e conferma"
    closed -> "Perimetro chiuso, ma non valido"
    manualPoints > 0 -> if (manualPoints < 3) "Tocca gli angoli della stanza, a terra" else "Continua con gli angoli, poi tocca il primo punto o premi Chiudi"
    wallCount == 0 -> "Pavimento rilevato ✓ — Muovi lentamente il telefono lungo le pareti (o tocca gli angoli a terra)"
    else -> "Muovi lentamente il telefono lungo le pareti"
}

/** Centimetri → "3,42 m". */
private fun formatMeters(cm: Double): String {
    val c = cm.roundToInt()
    return "${c / 100},${(c % 100).toString().padStart(2, '0')} m"
}

/** Metri quadrati → "12,35 m²". */
private fun formatSquareMeters(m2: Double): String {
    val c = (m2 * 100).roundToInt()
    return "${c / 100},${(c % 100).toString().padStart(2, '0')} m²"
}
