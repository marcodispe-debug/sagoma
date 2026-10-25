package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.GuideMenu
import com.sagoma.planimetria.editor.GuideTarget
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.model.LengthUnit
import com.sagoma.planimetria.testi.T
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PendingOpening
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialCourse
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.editor.TutorialUi
import androidx.compose.foundation.layout.widthIn
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.OpeningKind
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.editor.MeasureState
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.persistence.RoomPhotoFiles
import com.sagoma.planimetria.geometry.Distances

/**
 * L'editor del progetto `projectId`. `courseLesson`: guida interattiva da far partire appena aperto (poi `onCourseStarted`);
 * `onCourse`: la guida chiede la sua lezione, sul suo progetto di prova (lo sceglie l'app).
 */
@Composable
fun EditorScreen(
    projectId: Long,
    onOpenProjects: () -> Unit,
    courseLesson: Lesson? = null,
    onCourseStarted: () -> Unit = {},
    onCourse: (Lesson) -> Unit = {},
) {
    val platform = LocalPlatform.current
    val projects = platform.projects
    // Un ViewModel per progetto: passando a un altro progetto si riparte dalla sua casa.
    val vm: EditorViewModel = viewModel(key = "progetto-$projectId") {
        EditorViewModel(projects.planStore(projectId), platform.tips).also {
            // Appena aperto Annulla è vuoto: le foto tolte nelle volte precedenti non servono più.
            RoomPhotoFiles.prune(projects, projectId, it.state.value.fullBuilding)
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm, courseLesson) {
        courseLesson?.let {
            vm.startCourse(it)
            onCourseStarted()
        }
    }
    // Nomi automatici di stanze e piani ("Piano terra", "Cucina 2") nella lingua scelta, anche aprendo un progetto fatto in un'altra.
    LaunchedEffect(vm, T.lingua.codice) { vm.translateAutoNames() }
    // Quando l'app va in background si salva subito, senza aspettare il ritardo del salvataggio automatico.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveNow() }
    // Chiudendo la finestra (computer) o uscendo dal progetto si salva subito, senza aspettare.
    DisposableEffect(vm) { onDispose { vm.saveBlocking() } }
    val keyFocus = remember { FocusRequester() }
    // La tastiera torna alla pianta all'avvio e quando si chiude la vista 3D (che la prende per muoversi);
    // anche un clic sulla pianta la riprende (vedi PlanCanvas), così le scorciatoie funzionano sempre.
    LaunchedEffect(state.view3d) { if (!state.view3d) keyFocus.requestFocusSafely() }
    val exportPng = rememberPngExporter(state.plan, state.building.layers)
    // Strumenti professionali: tavola PDF in scala, computo, pianta di sfondo da ricalcare.
    var showPdf by remember { mutableStateOf(false) }
    var showTakeoff by remember { mutableStateOf(false) }
    var showBackground by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val extraMenu = listOf(
        T.editor.progetti to { vm.saveNow(); onOpenProjects() },
        T.editor.livelli to { showLayers = true },
        T.editor.tavolaPdfScala to { showPdf = true },
        T.editor.computoListaArredi to { showTakeoff = true },
        T.editor.piantaSfondo to { showBackground = true },
        T.editor.temaChiaroScuro to { showTheme = true },
        if (state.building.units == LengthUnit.Meters) T.editor.misureInCentimetri to { vm.setUnits(LengthUnit.Centimeters) }
        else T.editor.misureInMetri to { vm.setUnits(LengthUnit.Meters) },
        T.editor.linguaMenu to { showLanguage = true },
    )
    if (showPdf) PdfExportDialog(state, projects.info(projectId), onDismiss = { showPdf = false })
    if (showTakeoff) TakeoffDialog(state, projects.info(projectId), onDismiss = { showTakeoff = false })
    // "Ricalca una piantina" al posto della prima stanza: la finestra resta aperta finché non si carica e si tara la piantina.
    val tracingSetup = state.tracing != null && state.underlayTool == null
    if (showBackground || tracingSetup) BackgroundDialog(state, vm, projectId, tracing = state.tracing != null, onDismiss = {
        showBackground = false
        // Chiusa senza tarare (o senza caricare nulla): si va avanti col ricalco, o si torna alla scelta della stanza.
        vm.state.value.let { if (it.tracing != null && it.underlayTool == null) vm.endTracing() }
        // Guida al ricalco aperta dal menu e finestra chiusa senza caricare la piantina: la guida non ha niente da ricalcare.
        if (vm.state.value.plan.underlay == null) vm.abandonTracingGuide()
    })
    if (showLayers) LayersDialog(state, vm, onDismiss = { showLayers = false })
    if (showTheme) ThemeDialog(onDismiss = { showTheme = false })
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
    state.textDialogAt?.let { at -> NewTextDialog(at, vm) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            // Scorciatoie da tastiera (§8). Dopo i campi di testo (onKeyEvent, non "preview"): dentro un campo
            // Ctrl+C, Ctrl+V, Ctrl+Z e Canc lavorano sul testo, non sugli oggetti della pianta.
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Selezione multipla: Canc elimina gli oggetti selezionati, Esc svuota la selezione (poi esce).
                if (!e.isCtrlPressed) return@onKeyEvent when {
                    // Canc: elimina ciò che è selezionato (gruppo, oggetto, muro → lato aperto, stanza con conferma).
                    !state.view3d && (e.key == Key.Delete || e.key == Key.Backspace) -> vm.deleteSelected()
                    state.pasting && e.key == Key.Escape -> { vm.stopPaste(); true }
                    state.dimensionDraw != null && e.key == Key.Escape -> { vm.cancelDimension(); true }
                    state.textPlacing && e.key == Key.Escape -> { vm.cancelText(); true }
                    state.selectMode && e.key == Key.Escape -> { if (state.multi.isEmpty()) vm.toggleSelectMode() else vm.clearMulti(); true }
                    else -> false
                }
                when {
                    e.key == Key.A && !state.view3d -> { vm.selectAll(); true }
                    // Copia e incolla come nei programmi del computer: Ctrl+C copia l'oggetto selezionato (con un
                    // messaggio), Ctrl+V incolla subito dove c'è il mouse (o accanto all'originale).
                    e.key == Key.C && !state.view3d -> {
                        if (vm.copy()) vm.state.value.clipboard?.let { platform.toast(T.editor.copiatoCtrlVIncollare(clipLabel(it))) }
                        else platform.toast(T.editor.selezionaPrimaQualcosaCopiare)
                        true
                    }
                    e.key == Key.V && !state.view3d -> {
                        if (!vm.pasteNow()) platform.toast(if (state.clipboard == null) T.editor.nienteIncollareCopiaPrima else T.editor.quiNonPuoIncollare)
                        true
                    }
                    // Ctrl+D: duplica l'oggetto selezionato (come il pulsante "⧉ Duplica" nella scheda Info).
                    e.key == Key.D && !state.view3d -> vm.duplicateSelected()
                    e.key == Key.Z && e.isShiftPressed -> { vm.redo(); true }
                    e.key == Key.Z -> { vm.undo(); true }
                    e.key == Key.Y -> { vm.redo(); true }
                    else -> false
                }
            }
            .focusRequester(keyFocus)
            .focusable(),
    ) {
        // In orizzontale (poca altezza) schede e barra stanno su una sola riga, per lasciare spazio alla pianta.
        val compactHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 500.dp
        // Finestra larga (computer, tablet): due righe più basse che sul telefono, per una striscia in alto più sottile.
        val wideWindow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 900.dp
        // E la barra di stato si nasconde (riappare temporaneamente scorrendo dal bordo superiore).
        LaunchedEffect(compactHeight) { platform.setStatusBarHidden(compactHeight) }
        // Vista 3D: telecamera e superficie OpenGL restano le stesse passando da 2D a 3D e ritorno.
        val view3dState = remember { View3DState() }
        var openGuide by remember { mutableStateOf<Guide?>(null) }
        openGuide?.let { GuideDialog(it, onDismiss = { openGuide = null }, onOpen3d = { vm.setView3d(true) }) }
        // Guide passo passo che si fanno sulla pianta: avanzano da sole a ogni azione.
        val guidedTours = listOfNotNull(
            // Sul computer, la guida interattiva al posto del vecchio tutorial (che resta sugli altri dispositivi).
            if (platform.interactiveGuide) MenuEntry(T.corso.voceMenu, { onCourse(Lesson.Square) })
            else MenuEntry(T.editor.tutorialPassoPasso, vm::restartTutorial),
            MenuEntry(T.editor.ricalcoPassoPasso, {
                if (state.plan.underlay == null) showBackground = true
                vm.startTracingGuide()
            }),
        )
        val toolbar = @Composable { compact: Boolean, fill: Boolean, rowHeight: Dp ->
            Toolbar(
                // Mentre si disegnano i muri Annulla toglie l'ultimo angolo (anche se la pianta non ha ancora modifiche da annullare).
                state.canUndo || state.wallDraw?.points?.isNotEmpty() == true,
                state.canRedo,
                vm,
                focusedRoom = state.plan.room(state.focusedRoomId),
                onExport = exportPng,
                extraMenu = extraMenu,
                compact = compact,
                fill = fill,
                rowHeight = rowHeight,
                onZoom = { f -> if (state.view3d) view3dState.zoom(f) else vm.zoomBy(f) },
                onFit = { if (state.view3d) view3dState.fitPlan(state.plan) else vm.fitToView() },
                // In orizzontale "❔" sta sulla riga unica; in verticale in cima, accanto alle schede (sulla riga di zoom non ci sta).
                guides = { if (compact || !fill) GuidesButton(onGuide = { openGuide = it }, tours = guidedTours) },
            )
        }
        // Piano che si sta modificando (e menu dei piani) prima delle schede delle stanze.
        if (compactHeight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FloorButton(state.building, vm, Modifier.padding(start = 8.dp), compact = true)
                RoomTabs(state.plan.rooms, state.focusedRoomId, onTap = vm::openRoomProperties, modifier = Modifier.weight(1f), compact = true)
                toolbar(true, false, 40.dp)
            }
        } else if (wideWindow) {
            // Computer: due righe come sul telefono, ma più basse (pulsanti a 34 dp, barra di zoom a 36 dp).
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                FloorButton(state.building, vm, Modifier.padding(start = 8.dp).height(34.dp))
                RoomTabs(state.plan.rooms, state.focusedRoomId, onTap = vm::openRoomProperties, modifier = Modifier.weight(1f), compact = true)
                Box(Modifier.padding(end = 8.dp).height(34.dp)) { GuidesButton(onGuide = { openGuide = it }, tours = guidedTours) }
            }
            toolbar(false, true, 36.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FloorButton(state.building, vm, Modifier.padding(start = 8.dp))
                RoomTabs(state.plan.rooms, state.focusedRoomId, onTap = vm::openRoomProperties, modifier = Modifier.weight(1f), compact = true)
                // Sul telefono in verticale "Guide" resta solo "❔", per lasciare posto alle schede delle stanze.
                Box(Modifier.padding(end = 8.dp)) { GuidesButton(onGuide = { openGuide = it }, tours = guidedTours, iconOnly = true) }
            }
            toolbar(false, true, 40.dp)
        }
        HorizontalDivider()

        Row(Modifier.weight(1f).fillMaxWidth()) {
        // In orizzontale i pulsanti di azione stanno in una colonna a sinistra: la larghezza abbonda,
        // l'altezza no, e così la pianta guadagna tutta l'altezza della barra in basso.
        if (compactHeight) {
            ActionRail(state, vm)
            VerticalDivider()
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val wide = maxWidth >= 600.dp
            if (state.view3d) Plan3DView(state, vm, view3dState, Modifier.fillMaxSize())
            else androidx.compose.runtime.CompositionLocalProvider(LocalProjectId provides projectId) {
                PlanCanvas(state, vm, Modifier.fillMaxSize().guideCanvas(), onPointerDown = { keyFocus.requestFocusSafely() })
            }
            // Marchio del piano gratuito sopra pianta e 3D (non sui pulsanti), così resta anche in uno screenshot.
            WatermarkOverlay(Modifier.fillMaxSize())
            // Suggerimento durante il posizionamento di un'apertura o di un impianto.
            val placingHint = state.pendingOpening?.let { T.editor.toccaMuroCuiPosizionare(T.lingua.minuscola(it.kind.label)) }
                ?: state.pendingFixture?.let {
                    if (it.mount == Mount.Wall) T.editor.toccaMuroCuiPosizionare2(T.lingua.minuscola(it.label))
                    else T.editor.toccaPuntoStanzaCui(T.lingua.minuscola(it.label))
                }
            // Schede in alto (posizionamento, suggerimento, tutorial): la pianta si centra sotto di loro.
            var topCardsHeightPx by remember { mutableIntStateOf(0) }
            // Percorso B: l'avviso "schizzo da misurare" sta tra le schede in alto (in basso coprirebbe la barra di porte e finestre).
            val pendingSketch = if (FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.SKETCH_GUIDED_SCAN) && state.creation == null &&
                state.wallDraw == null && state.sketchScanRoomId == null && placingHint == null
            ) state.plan.rooms.firstOrNull { it.sketchPending && it.id !in state.dismissedSketchBanners } else null
            val hasTopCards = placingHint != null || state.tip != null || state.tutorial != null || state.measure != null || state.underlayTool != null || state.wallDraw != null || state.selectMode || state.pasting || state.saveError != null ||
                state.dimensionDraw != null || state.textPlacing || pendingSketch != null
            val coveredTopPx = if (hasTopCards) topCardsHeightPx else 0
            Column(
                Modifier.align(Alignment.TopCenter).onSizeChanged { topCardsHeightPx = it.height }.padding(8.dp).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                placingHint?.let { hint ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hint,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TextButton(onClick = vm::cancelPlacingOpening) { Text(T.comuni.annulla, color = MaterialTheme.colorScheme.inversePrimary) }
                        }
                    }
                }
                // Strumento "Distanza": cosa toccare, poi il risultato.
                state.measure?.let { MeasureCard(it, state, vm) }
                // Pianta di sfondo: taratura o spostamento in corso.
                UnderlayToolCard(state, vm)
                // Disegno dei muri, muro per muro (non mentre si rifà la taratura: in alto c'è la sua scheda).
                if (state.underlayTool == null) WallDrawCard(state, vm)
                // Selezione multipla e suoi comandi.
                MultiSelectionCard(state, vm)
                // Incolla: cosa toccare per mettere le copie.
                PasteCard(state, vm)
                // Strumenti "Quota" e "Testo".
                NotesToolCard(state, vm)
                // Salvataggio non riuscito: non deve mai passare inosservato.
                state.saveError?.let { err ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(T.editor.progettoNonStatoSalvato, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                T.editor.ultimeModificheSonoSolo(err),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = vm::saveNow) { Text(T.editor.riprovaSalvare) }
                        }
                    }
                }
                // Schizzo ancora da misurare: "Avvia scansione" / "Chiudi".
                pendingSketch?.let { com.sagoma.planimetria.ui.sketch.SketchPendingBanner(it, vm) }
                // Suggerimento al primo utilizzo della funzione appena usata.
                state.tip?.let { TipCard(it, onDismiss = vm::dismissTip, onSkipAll = vm::skipAllTips) }
                // Tutorial guidato: la scheda del passo corrente, finché non è completato.
                state.tutorial?.let { TutorialCard(it, vm) }
            }
            // In basso: la striscia "Info" dell'oggetto selezionato (muro, apertura, impianto) oppure, se è
            // selezionata solo la stanza, della stanza. La tendina con le caratteristiche si apre a richiesta.
            val focusedRoom = state.plan.room(state.focusedRoomId)
            val panelShown = (state.selection != null || focusedRoom != null) && placingHint == null
            val expanded = panelShown && state.infoExpanded
            // Dimensione del riquadro: in verticale copre il fondo, su schermo largo (orizzontale, tablet)
            // la tendina aperta occupa il lato destro, la striscia chiusa l'angolo in basso a destra.
            var panelSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            val density = LocalDensity.current
            val panelGapPx = with(density) { 8.dp.roundToPx() }
            // Stesso comportamento per stanza e oggetti: quando la striscia compare, la tendina si apre o si
            // chiude, la pianta scorre (senza cambiare zoom) e si centra nello spazio che resta libero:
            // sopra il riquadro in verticale, alla sua sinistra in orizzontale; tutta l'area quando sparisce.
            val sideMode = wide && expanded
            val coveredBottomPx = if (panelShown && !sideMode) panelSizePx.height + (if (wide) panelGapPx else 0) else 0
            val coveredRightPx = if (sideMode && panelSizePx.width > 0) panelSizePx.width + panelGapPx else 0
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()
            // Dimensione dimenticata quando il riquadro sparisce: al prossimo che compare si aspetta la sua
            // misura vera, così la vista si sposta una volta sola (non prima a vuoto e poi corretta).
            LaunchedEffect(panelShown) { if (!panelShown) panelSizePx = androidx.compose.ui.unit.IntSize.Zero }
            // Anche quando cambia l'area della pianta (rotazione, barra di stato nascosta, tastiera).
            LaunchedEffect(coveredTopPx, coveredBottomPx) { vm.setCoveredEdges(coveredTopPx.toFloat(), coveredBottomPx.toFloat()) }
            LaunchedEffect(coveredBottomPx, coveredRightPx, coveredTopPx, state.selection, state.focusedRoomId, expanded, canvasWidthPx, canvasHeightPx) {
                if (panelShown && panelSizePx == androidx.compose.ui.unit.IntSize.Zero) return@LaunchedEffect
                // "Muro per muro" in corso: la vista la guida la scheda del disegno (stanza in costruzione al centro).
                if (vm.state.value.wallDraw?.points?.isNotEmpty() == true) return@LaunchedEffect
                // Se la selezione è nata iniziando a trascinare un angolo o un muro, la vista non si muove
                // sotto il dito: si ricentra quando il trascinamento è finito.
                vm.state.first { !it.dragging }
                val margin = with(density) { 24.dp.toPx() }
                fun aim() = vm.centeredCamera(canvasWidthPx - coveredRightPx, canvasHeightPx - coveredBottomPx, margin, coveredTopPx.toFloat())
                var target = aim() ?: return@LaunchedEffect
                var start = vm.state.value.camera
                if (abs(target.offsetX - start.offsetX) < 1f && abs(target.offsetY - start.offsetY) < 1f) return@LaunchedEffect
                animate(0f, 1f, animationSpec = tween(durationMillis = 280)) { t, _ ->
                    // Se nel frattempo lo zoom è cambiato (adattamento automatico, gesto dell'utente), si
                    // ricalcola la destinazione con lo zoom nuovo invece di riportare quello vecchio.
                    val current = vm.state.value.camera
                    if (current.scale != start.scale) {
                        start = current
                        target = aim() ?: current
                    }
                    vm.setCameraOffset(
                        start.offsetX + (target.offsetX - start.offsetX) * t,
                        start.offsetY + (target.offsetY - start.offsetY) * t,
                    )
                }
            }
            if (panelShown) {
                // Su schermo largo la tendina aperta è un pannello laterale a destra, alto quanto l'area della
                // pianta (il contenuto scorre); chiusa resta solo la striscia, in basso a destra.
                val panelModifier = when {
                    sideMode -> Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .width(minOf(360.dp, maxWidth * 0.45f))
                        .fillMaxHeight()
                    wide -> Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .width(minOf(360.dp, maxWidth * 0.45f))
                    else -> Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 8.dp, bottom = 8.dp)
                        .heightIn(max = maxHeight * 0.55f)
                }.onSizeChanged { panelSizePx = it }
                val sel = state.selection
                val selectedRoom = state.plan.room(sel?.roomId ?: state.focusedRoomId)
                InfoSheet(
                    title = if (sel != null) selectionTitle(sel, state.plan, state.levelHeight, state.planBelow)
                    else focusedRoom?.let { "${it.name} · ${formatArea(it)}" }.orEmpty(),
                    dotColor = selectedRoom?.let { Color(it.type.argb) },
                    expanded = expanded,
                    onToggle = vm::toggleInfo,
                    onExpandedChange = vm::setInfoExpanded,
                    modifier = panelModifier,
                    actions = { SnapToggle(state.snapOn, vm::setSnapOn, compact = true) },
                ) { contentModifier ->
                    if (sel != null) SelectionPanel(sel, state.plan, vm, contentModifier, state.levelHeight, state.planBelow, state.levelHeightBelow)
                    else if (focusedRoom != null) RoomInfo(focusedRoom, state.plan, vm, contentModifier)
                }
            }
            // Legenda dei simboli: in basso a destra, sopra la striscia Info e a sinistra del pannello laterale.
            var showLegend by remember { mutableStateOf(false) }
            var showVisibility by remember { mutableStateOf(false) }
            // Piantina appena caricata (o cambiata): i comandi per vederla e regolarla si aprono da soli. Riaprendo il progetto no.
            var underlayFile by remember { mutableStateOf(state.plan.underlay?.file) }
            LaunchedEffect(state.plan.underlay?.file) {
                val file = state.plan.underlay?.file
                if (file != null && file != underlayFile) showVisibility = true
                underlayFile = file
            }
            val cornerModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = with(density) { coveredRightPx.toDp() } + 8.dp,
                    bottom = with(density) { coveredBottomPx.toDp() } + 8.dp,
                )
            // Angolo in basso a destra: passaggio 2D/3D, e la legenda (2D) o Cammina / Vista dall'alto (3D).
            Column(cornerModifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendButton(
                    if (state.view3d) T.editor.pianta2d else T.editor.vista3d,
                    onClick = {
                        if (state.view3d && view3dState.walking) view3dState.stopWalking()
                        vm.setView3d(!state.view3d)
                    },
                )
                if (state.view3d) View3DControls(state, view3dState)
                else {
                    // Mostra/nascondi e trasparenza di piantina e disegno, regolate dal vivo.
                    if (showVisibility) VisibilityPanel(state, vm, onLoadPlan = { showBackground = true })
                    LegendButton(
                        if (showVisibility) T.editor.chiudi else T.editor.piantinaDisegno, onClick = { showVisibility = !showVisibility },
                        modifier = Modifier.guide(GuideTarget.PlanPanel, done = !showVisibility),
                    )
                    PhaseLegend(state)
                    LegendButton(T.editor.legenda, onClick = { showLegend = true })
                }
            }
            // Ora del giorno: su schermo largo o telefono girato, verticale a sinistra (sopra il joystick).
            if (state.view3d) {
                if (wide || compactHeight) TimeOfDayPanel(
                    view3dState, Modifier.align(Alignment.TopStart).padding(12.dp), vertical = true,
                    length = (maxHeight - 120.dp - 16.dp - 24.dp - 110.dp).coerceIn(90.dp, 260.dp),
                )
                else TimeOfDayPanel(view3dState, Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth())
            }
            // Joystick per spostarsi: camminando e anche nella vista dall'alto (stessi comandi).
            if (state.view3d) {
                WalkJoystick(
                    view3dState,
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = with(density) { coveredBottomPx.toDp() } + 16.dp),
                )
            }
            // Copia l'oggetto selezionato / incolla l'ultimo copiato: in basso a sinistra, sopra la striscia Info.
            CopyPasteChips(
                state, vm,
                Modifier.align(Alignment.BottomStart).padding(
                    start = 8.dp,
                    bottom = (if (wide) 0.dp else with(density) { coveredBottomPx.toDp() }) + 8.dp,
                ),
            )
            if (showLegend) LegendDialog(onDismiss = { showLegend = false })
        }
        }

        if (!compactHeight) {
            HorizontalDivider()
            // Tre gruppi, sempre tutti visibili: cosa si costruisce, cosa si arreda, con cosa si lavora sulla pianta.
            // Su un telefono stretto i nomi si accorciano ("🧱 Elementi") per stare in una riga senza scorrere.
            BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val narrow = maxWidth < 520.dp
                val pad = PaddingValues(horizontal = if (narrow) 4.dp else 12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Stretto: tre parti uguali; largo: i pulsanti restano della loro misura, a sinistra.
                    val m = if (narrow) Modifier.weight(1f) else Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                    ArchitectureButton(state.plan.rooms.isNotEmpty(), vm, m, pad, compact = narrow)
                    if (FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.FURNITURE)) FurnitureButton(vm, m, pad)
                    ToolsButton(state, vm, m, pad, compact = narrow)
                }
            }
        }
    }

    state.creation?.let { CreationFlow(it, vm, onOpenProjects = { vm.saveNow(); onOpenProjects() }) }
    // Percorso B (solo dove la funzione è disponibile): la scansione guidata a schermo intero. L'avviso "schizzo da misurare" è
    // tra le schede in alto (vedi sopra): alla riapertura del progetto torna per la prima stanza-schizzo non ancora misurata.
    if (FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.SKETCH_GUIDED_SCAN) && state.creation == null && state.wallDraw == null) {
        state.plan.room(state.sketchScanRoomId)?.let { com.sagoma.planimetria.ui.sketch.SketchScanHost(it, vm) }
    }
    state.floorDialog?.let { FloorEditDialog(it, state.fullBuilding, vm) }

    state.plan.room(state.confirmDeleteRoomId)?.let { DeleteRoomDialog(it, vm) }
    state.confirmMergeWall?.let { MergeWallDialog(it, state.plan, vm) }
    // Guida interattiva: sopra a tutto, comprese le finestre appena aperte.
    state.course?.let { CourseGuide(it, state, vm, onLesson = onCourse, onOpenProjects = { vm.saveNow(); onOpenProjects() }) }
}

/** Scheda dello strumento "Distanza": primo oggetto, secondo oggetto, distanza; "Nuova misura" e "Chiudi". */
@Composable
private fun MeasureCard(m: MeasureState, state: EditorUiState, vm: EditorViewModel) {
    val first = m.first
    val second = m.second
    val result = if (first != null && second != null) Distances.between(state.plan, first, second, state.levelHeight) else null
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp)) {
            val onColor = MaterialTheme.colorScheme.inverseOnSurface
            Text(T.editor.distanza, style = MaterialTheme.typography.titleSmall, color = onColor)
            val text = when {
                first == null -> T.editor.toccaPrimoOggettoMuro
                second == null -> T.editor.primoOraToccaSecondo(measureName(state.plan, first))
                result == null -> T.editor.nonRiescoMisurareQuesti
                else -> T.editor.traE(measureName(state.plan, first), measureName(state.plan, second))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
            if (result != null) {
                val dx = kotlin.math.abs(result.b.x - result.a.x)
                val dy = kotlin.math.abs(result.b.y - result.a.y)
                Text(
                    if (result.distance < 0.05) T.editor.toccano0Cm else formatDistance(result.distance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Se la distanza è di sbieco, anche le due componenti.
                if (dx > 0.5 && dy > 0.5) {
                    Text(
                        T.editor.orizzontaleVerticale(formatDistance(dx), formatDistance(dy)),
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.8f),
                    )
                }
                Text(
                    T.editor.distanzaBordiPiuVicini,
                    style = MaterialTheme.typography.bodySmall,
                    color = onColor.copy(alpha = 0.7f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (m.first != null) TextButton(onClick = vm::startMeasure) { Text(T.editor.nuovaMisura, color = onColor.copy(alpha = 0.8f)) }
                TextButton(onClick = vm::closeMeasure) { Text(T.comuni.chiudi, color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}

/** Scheda del suggerimento al primo utilizzo: titolo, spiegazione, "Salta tutti" e "Ho capito". */
@Composable
private fun TipCard(tip: Tip, onDismiss: () -> Unit, onSkipAll: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().guide(GuideTarget.Tip),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp)) {
            Row {
                SketchBox(tip.sketch)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "💡 ${tip.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Text(
                        tip.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkipAll) {
                    Text(T.editor.saltaTutti, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                }
                TextButton(onClick = onDismiss) { Text(T.editor.hoCapito, color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}

/** Riquadro chiaro con la mini animazione di un tutorial o di un suggerimento. */
@Composable
private fun SketchBox(sketch: Sketch, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFFF8F9FA)) {
        AnimatedSketch(sketch, Modifier.size(88.dp).padding(3.dp))
    }
}

/**
 * Scheda del tutorial guidato: passo N di M, cosa fare e avanzamento. Il passo si completa facendo
 * l'azione sulla pianta; "Salta passo" va avanti comunque, "Esci" chiude il tutorial.
 */
@Composable
private fun TutorialCard(t: TutorialUi, vm: EditorViewModel) {
    val onColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp)) {
            Row {
                SketchBox(t.step.sketch, Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    if (t.step.action) {
                        Text(
                            T.editor.passo(if (t.course == TutorialCourse.Tracing) T.editor.guidaRicalco else T.editor.tutorial, t.number, t.total),
                            style = MaterialTheme.typography.labelMedium,
                            color = onColor.copy(alpha = 0.7f),
                        )
                    }
                    Text(t.step.title, style = MaterialTheme.typography.titleSmall, color = onColor)
                    Text(
                        t.step.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onColor,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                    )
                    t.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (t.step) {
                    TutorialStep.Intro -> {
                        TextButton(onClick = { vm.finishTutorial(completed = false) }) { Text(T.editor.noGrazie, color = onColor.copy(alpha = 0.7f)) }
                        TextButton(onClick = vm::nextTutorialStep) { Text(T.editor.inizia) }
                    }
                    TutorialStep.Done, TutorialStep.TraceDone -> TextButton(onClick = { vm.finishTutorial() }) { Text(T.comuni.fine) }
                    else -> {
                        TextButton(onClick = { vm.finishTutorial(completed = false) }) { Text(T.comuni.esci, color = onColor.copy(alpha = 0.7f)) }
                        TextButton(onClick = vm::nextTutorialStep) { Text(T.editor.saltaPasso) }
                    }
                }
            }
        }
    }
}

/** Colonna di azioni a sinistra, usata in orizzontale al posto della barra in basso: gli stessi tre gruppi. */
@Composable
private fun ActionRail(state: EditorUiState, vm: EditorViewModel) {
    val buttonModifier = Modifier.fillMaxWidth()
    val pad = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    Column(
        Modifier
            .width(120.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArchitectureButton(state.plan.rooms.isNotEmpty(), vm, buttonModifier, pad, compact = true)
        if (FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.FURNITURE)) FurnitureButton(vm, buttonModifier, pad)
        ToolsButton(state, vm, buttonModifier, pad, compact = true)
    }
}

/**
 * "🧱 Elementi architettonici": stanza, porte, finestre, porte-finestre, impianti, scale, colonne e travi, balconi e terrazze.
 * Porte, finestre e impianti vanno su un muro: senza stanze restano visibili ma spenti.
 */
@Composable
private fun ArchitectureButton(
    hasRooms: Boolean,
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    compact: Boolean = false,
) {
    fun opening(kind: OpeningKind, style: PassageStyle = PassageStyle.Square, sliding: Boolean = false) =
        { vm.startPlacingOpening(PendingOpening(kind, style, sliding)) }
    fun fixture(kind: FixtureKind) = { vm.startPlacingFixture(kind) }
    val lights = listOf(
        FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.Chandelier, FixtureKind.CeilingLight, FixtureKind.LedStrip,
        FixtureKind.WallLight, FixtureKind.WallSpot, FixtureKind.WallLedStrip,
    )
    // Ogni gruppo di aperture ha anche la versione scorrevole; battente/scorrevole si cambia comunque dopo da Info.
    MenuButton(
        if (compact) T.editor.elementi else T.editor.elementiArchitettonici,
        true,
        listOf(
            MenuEntry(T.editor.stanza, vm::startAddRoom, guide = GuideTarget.Menu(GuideMenu.Room)),
            MenuEntry(
                T.editor.porte,
                enabled = hasRooms,
                guide = GuideTarget.Menu(GuideMenu.Doors),
                children = listOf(
                    MenuEntry(T.editor.portaAd1Anta, opening(OpeningKind.Door), guide = GuideTarget.Menu(GuideMenu.Door)),
                    MenuEntry(T.editor.porta2Ante, opening(OpeningKind.Door2)),
                    MenuEntry(T.editor.portaScorrevole, opening(OpeningKind.Door, sliding = true)),
                    MenuEntry(
                        T.editor.aperturaSenzaPorta,
                        children = PassageStyle.entries.map { st -> MenuEntry(st.label, opening(OpeningKind.Passage, st)) },
                    ),
                ),
            ),
            MenuEntry(
                T.editor.finestre,
                enabled = hasRooms,
                guide = GuideTarget.Menu(GuideMenu.Windows),
                children = listOf(
                    MenuEntry(T.editor.finestraAd1Anta, opening(OpeningKind.Window1)),
                    MenuEntry(T.editor.finestra2Ante, opening(OpeningKind.Window2), guide = GuideTarget.Menu(GuideMenu.Window)),
                    MenuEntry(T.editor.finestraScorrevole, opening(OpeningKind.Window2, sliding = true)),
                ),
            ),
            MenuEntry(
                T.editor.porteFinestre,
                enabled = hasRooms,
                guide = GuideTarget.Menu(GuideMenu.FrenchWindows),
                children = listOf(
                    MenuEntry(T.editor.balconeAd1Anta, opening(OpeningKind.Balcony1)),
                    MenuEntry(T.editor.balcone2Ante, opening(OpeningKind.Balcony2), guide = GuideTarget.Menu(GuideMenu.FrenchWindow)),
                    MenuEntry(T.editor.balconeScorrevole, opening(OpeningKind.Balcony2, sliding = true)),
                ),
            ),
            MenuEntry(
                T.editor.impianti,
                enabled = hasRooms,
                guide = GuideTarget.Menu(GuideMenu.Systems),
                children = listOf(
                    MenuEntry(T.editor.calorifero, fixture(FixtureKind.Radiator), guide = GuideTarget.Menu(GuideMenu.Radiator)),
                    MenuEntry(T.editor.presaCorrente, fixture(FixtureKind.Outlet)),
                    MenuEntry(T.editor.interruttore, fixture(FixtureKind.Switch)),
                    MenuEntry(T.editor.puntoAcqua, fixture(FixtureKind.WaterPoint)),
                    MenuEntry(
                        T.editor.luci,
                        guide = GuideTarget.Menu(GuideMenu.Lights),
                        children = lights.map { MenuEntry(it.label, fixture(it), guide = if (it == FixtureKind.Chandelier) GuideTarget.Menu(GuideMenu.Chandelier) else null) },
                    ),
                ),
            ),
            MenuEntry(
                T.editor.scale,
                guide = GuideTarget.Menu(GuideMenu.Stairs),
                children = listOf(StairKind.Straight, StairKind.StraightLanding, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.Spiral)
                    .map { k -> MenuEntry(k.label, { vm.addStair(k) }, guide = if (k == StairKind.Spiral) GuideTarget.Menu(GuideMenu.Spiral) else null) },
            ),
            MenuEntry(
                T.editor.colonneTravi,
                children = listOf(
                    MenuEntry(T.editor.colonnaQuadrata, { vm.addColumn(ColumnShape.Square) }),
                    MenuEntry(T.editor.colonnaRotonda, { vm.addColumn(ColumnShape.Round) }),
                    MenuEntry(T.editor.traveSoffitto, { vm.addBeam() }),
                ),
            ),
            MenuEntry(
                T.editor.balconiTerrazze,
                guide = GuideTarget.Menu(GuideMenu.Outdoor),
                children = listOf(RoomType.Balcone, RoomType.Terrazza).map { t ->
                    MenuEntry(t.label, { vm.startAddOutdoor(t) }, guide = if (t == RoomType.Balcone) GuideTarget.Menu(GuideMenu.Balcony) else null)
                },
            ),
        ),
        buttonModifier,
        contentPadding,
        arrow = !compact,
        guide = GuideTarget.Elements,
    )
}

/**
 * "🛠 Strumenti": selezione multipla, vista gialli e rossi, distanza, quota e testo (il metro è stato tolto: la quota fa lo stesso;
 * i metri dei progetti vecchi restano visibili e modificabili). Con uno strumento in uso
 * (o una vista diversa da gialli e rossi) il pulsante è pieno e ne mostra il nome; si esce dalla scheda in alto.
 */
@Composable
private fun ToolsButton(
    state: EditorUiState,
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    compact: Boolean = false,
) {
    val active = when {
        state.selectMode -> T.editor.seleziona
        state.measure != null -> T.editor.distanza2
        state.dimensionDraw != null -> T.editor.quota
        state.textPlacing -> T.editor.testoTesto
        state.phaseView != PhaseView.Compare -> state.phaseView.label
        else -> null
    }
    MenuButton(
        if (compact || active == null) T.editor.strumenti else T.editor.strumenti2(active),
        true,
        listOf(
            MenuEntry(if (state.selectMode) T.editor.selezionaPiuOggettiUso else T.editor.selezionaPiuOggetti, vm::toggleSelectMode),
            MenuEntry(
                T.editor.vista(state.phaseView.label),
                children = PhaseView.entries.map { v ->
                    MenuEntry(
                        (if (v == state.phaseView) "✓ " else "") + v.label,
                        { vm.setPhaseView(v) },
                        hint = when (v) {
                            PhaseView.Compare -> T.editor.tuttoDemolizioniGialloCostruzioni
                            PhaseView.Existing -> T.editor.comOraSenzaParti
                            PhaseView.Project -> T.editor.saraSenzaPartiDemolite
                        },
                    )
                },
            ),
            MenuEntry(T.editor.distanzaDueOggetti, vm::startMeasure),
            MenuEntry(T.editor.quota2, vm::startDimension),
            MenuEntry(T.editor.tTesto, vm::startText),
        ),
        buttonModifier,
        contentPadding,
        filled = active != null,
        arrow = !compact,
    )
}

/**
 * Pulsante della lingua e "❔ Guide", in alto a destra. "❔ Guide": prima le guide passo passo sulla pianta (`tours`), poi le miniguide animate, una per argomento.
 */
@Composable
private fun GuidesButton(onGuide: (Guide) -> Unit, tours: List<MenuEntry>, iconOnly: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    // La lingua sta accanto alle guide, così si trova in ogni disposizione della striscia in alto.
    Row(verticalAlignment = Alignment.CenterVertically) {
        LanguageButton(Modifier.padding(end = 6.dp), flagOnly = iconOnly)
        Box {
            OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = if (iconOnly) 4.dp else 12.dp)) {
                Text(if (iconOnly) "❔" else T.editor.guide, maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (t in tours) DropdownMenuItem(text = { Text(t.label) }, onClick = { open = false; t.action?.invoke() })
                HorizontalDivider()
                for (g in Guides) DropdownMenuItem(text = { Text("${g.icon}  ${g.title}") }, onClick = { open = false; onGuide(g) })
            }
        }
    }
}

/**
 * "+ Porte" (1/2 ante, scorrevole, Apertura senza porta ▸ squadrata/ad arco) e
 * "+ Infissi" (Finestre ▸ 1/2 ante, scorrevole; Balcone ▸ 1/2 ante, scorrevole) — §5.
 */
@Composable
private fun OpeningButtons(enabled: Boolean, vm: EditorViewModel, compact: Boolean = false, buttonModifier: Modifier = Modifier) {
    val pad = if (compact) PaddingValues(horizontal = 6.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding
    fun place(kind: OpeningKind, style: PassageStyle = PassageStyle.Square, sliding: Boolean = false) =
        { vm.startPlacingOpening(PendingOpening(kind, style, sliding)) }
    // Ogni gruppo ha anche la versione scorrevole (2 ante sovrapposte per finestre e balconi, 1 anta per la porta);
    // battente/scorrevole si può comunque cambiare dopo dal riquadro dell'apertura.
    MenuButton(
        T.editor.porte2,
        enabled,
        listOf(
            MenuEntry(T.editor.portaAd1Anta, place(OpeningKind.Door)),
            MenuEntry(T.editor.porta2Ante, place(OpeningKind.Door2)),
            MenuEntry(T.editor.portaScorrevole, place(OpeningKind.Door, sliding = true)),
            MenuEntry(
                T.editor.aperturaSenzaPorta,
                children = PassageStyle.entries.map { s -> MenuEntry(s.label, place(OpeningKind.Passage, s)) },
            ),
        ),
        buttonModifier,
        pad,
    )
    MenuButton(
        T.editor.infissi,
        enabled,
        listOf(
            MenuEntry(
                T.editor.finestre2,
                children = listOf(
                    MenuEntry(T.editor.finestraAd1Anta, place(OpeningKind.Window1)),
                    MenuEntry(T.editor.finestra2Ante, place(OpeningKind.Window2)),
                    MenuEntry(T.editor.finestraScorrevole, place(OpeningKind.Window2, sliding = true)),
                ),
            ),
            MenuEntry(
                T.editor.balcone,
                children = listOf(
                    MenuEntry(T.editor.balconeAd1Anta, place(OpeningKind.Balcony1)),
                    MenuEntry(T.editor.balcone2Ante, place(OpeningKind.Balcony2)),
                    MenuEntry(T.editor.balconeScorrevole, place(OpeningKind.Balcony2, sliding = true)),
                ),
            ),
        ),
        buttonModifier,
        pad,
    )
}

/** "+ Impianti": calorifero, presa di corrente, interruttore, punto acqua e Luci ▸ (faretto, neon lungo, lampadario, plafoniera, striscia LED). */
@Composable
private fun FixtureButton(
    enabled: Boolean,
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    fun place(kind: FixtureKind) = { vm.startPlacingFixture(kind) }
    val lights = listOf(
        FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.Chandelier, FixtureKind.CeilingLight, FixtureKind.LedStrip,
        FixtureKind.WallLight, FixtureKind.WallSpot, FixtureKind.WallLedStrip,
    )
    MenuButton(
        T.editor.impianti2,
        enabled,
        listOf(
            MenuEntry(T.editor.calorifero, place(FixtureKind.Radiator)),
            MenuEntry(T.editor.presaCorrente, place(FixtureKind.Outlet)),
            MenuEntry(T.editor.interruttore, place(FixtureKind.Switch)),
            MenuEntry(T.editor.puntoAcqua, place(FixtureKind.WaterPoint)),
            MenuEntry(T.editor.luci, children = lights.map { MenuEntry(it.label, place(it)) }),
        ),
        buttonModifier,
        contentPadding,
    )
}

/** Voce di menu: esegue un'azione oppure, se ha figli, apre un sottomenu; `hint` è una riga di spiegazione sotto. */
private class MenuEntry(
    val label: String,
    val action: (() -> Unit)? = null,
    val children: List<MenuEntry> = emptyList(),
    val enabled: Boolean = true,
    val hint: String? = null,
    /** Come la indica la guida interattiva. */
    val guide: GuideTarget? = null,
)

/**
 * Pulsante con menu a tendina a più livelli: una voce con figli ("Finestre ▸") sostituisce il
 * contenuto del menu con il suo sottomenu, da cui "‹ Indietro" torna al livello precedente.
 */
@Composable
private fun MenuButton(
    label: String,
    enabled: Boolean,
    items: List<MenuEntry>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /** Pulsante pieno: c'è qualcosa in uso dentro al menu. */
    filled: Boolean = false,
    /** "▾" dopo il nome (senza, dove lo spazio è poco). */
    arrow: Boolean = true,
    /** Come la guida interattiva indica il pulsante (fatto quando il menu è aperto). */
    guide: GuideTarget? = null,
) {
    var open by remember { mutableStateOf(false) }
    // Percorso dei sottomenu aperti (vuoto = menu principale).
    var path by remember { mutableStateOf(emptyList<MenuEntry>()) }
    fun close() { open = false; path = emptyList() }
    Box(modifier.guide(guide, done = open)) {
        if (filled) Button(enabled = enabled, onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = contentPadding) {
            Text(if (arrow) "$label ▾" else label, maxLines = 1)
        }
        else OutlinedButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = contentPadding) {
            Text(if (arrow) "$label ▾" else label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = ::close) {
            val current = path.lastOrNull()
            if (current != null) {
                DropdownMenuItem(text = { Text(T.comuni.indietroFreccia) }, onClick = { path = path.dropLast(1) })
                Text(
                    current.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                HorizontalDivider()
            }
            (current?.children ?: items).forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(if (entry.children.isNotEmpty()) "${entry.label}  ▸" else entry.label)
                            entry.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    enabled = entry.enabled,
                    onClick = {
                        if (entry.children.isNotEmpty()) path = path + entry
                        else { close(); entry.action?.invoke() }
                    },
                    modifier = Modifier.guide(entry.guide),
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    vm: EditorViewModel,
    focusedRoom: Room?,
    onExport: () -> Unit,
    /** Voci in più del menu ⋮ (progetti, tavola PDF, computo, pianta di sfondo). */
    extraMenu: List<Pair<String, () -> Unit>> = emptyList(),
    /** Orizzontale: sta accanto alle schede sulla stessa riga, Annulla/Ripeti solo come icone. */
    compact: Boolean = false,
    /** Riga tutta per sé (Annulla e Ripeti a sinistra, zoom a destra); `false` = accanto alle schede. */
    fill: Boolean = !compact,
    /** Altezza della riga quando ha una riga tutta per sé. */
    rowHeight: Dp = 40.dp,
    /** Zoom e "Adatta": sulla pianta 2D oppure, in 3D, sulla telecamera 3D. */
    onZoom: (Float) -> Unit = { vm.zoomBy(it) },
    onFit: () -> Unit = vm::fitToView,
    /** Pulsante "❔ Guide", prima dello zoom. */
    guides: @Composable () -> Unit = {},
) {
    // Compatta: deve stare su una riga anche sui telefoni stretti (~360 dp).
    val pad = PaddingValues(horizontal = 8.dp)
    Row(
        // Riga tutta per sé: più bassa dei 48 dp standard, per lasciare più spazio alla pianta.
        (if (fill) Modifier.fillMaxWidth().height(rowHeight) else Modifier).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            IconButton(onClick = vm::undo, enabled = canUndo) { Text("↶", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = vm::redo, enabled = canRedo) { Text("↷", style = MaterialTheme.typography.titleLarge) }
        } else {
            TextButton(onClick = vm::undo, enabled = canUndo, contentPadding = pad) { Text(T.editor.annulla, maxLines = 1) }
            TextButton(onClick = vm::redo, enabled = canRedo, contentPadding = pad) { Text(T.editor.ripeti, maxLines = 1) }
            if (fill) Box(Modifier.weight(1f))
        }
        guides()
        IconButton(onClick = { onZoom(1f / 1.25f) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        IconButton(onClick = { onZoom(1.25f) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = onFit, contentPadding = pad) { Text(T.editor.adatta, maxLines = 1) }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // Azioni sulla stanza selezionata; senza selezione restano visibili ma disattivate, così si scoprono.
                DropdownMenuItem(
                    text = { Text(focusedRoom?.let { T.editor.proprieta(it.name) } ?: T.editor.proprietaStanza) },
                    enabled = focusedRoom != null,
                    onClick = { menu = false; focusedRoom?.let { vm.showRoomInfo(it.id) } },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (focusedRoom == null) T.editor.eliminaStanza else T.editor.elimina(focusedRoom.name),
                        )
                    },
                    enabled = focusedRoom != null,
                    onClick = { menu = false; focusedRoom?.let { vm.requestDeleteRoom(it.id) } },
                )
                HorizontalDivider()
                for ((label, action) in extraMenu) DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                DropdownMenuItem(text = { Text(T.editor.esportaPng) }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text(T.editor.rivediSuggerimenti) }, onClick = { menu = false; vm.resetTips() })
            }
        }
    }
}

/** Schede in alto, una per stanza: un tocco la seleziona e ne apre le caratteristiche (§2, §4). */
@Composable
private fun RoomTabs(rooms: List<Room>, focusedId: Long?, onTap: (Long) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    ArrowLazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (compact) 0.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rooms, key = { it.id }) { room ->
            val focused = room.id == focusedId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onTap(room.id) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(room.type.argb), CircleShape))
                    Text(
                        "  ${room.name}  ${formatArea(room)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
