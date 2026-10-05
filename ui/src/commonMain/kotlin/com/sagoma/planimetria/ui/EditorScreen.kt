package com.sagoma.planimetria.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.PendingOpening
import com.sagoma.planimetria.editor.Tip
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
import com.sagoma.planimetria.editor.MeasureState
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Distances

@Composable
fun EditorScreen(projectId: Long, onOpenProjects: () -> Unit) {
    val platform = LocalPlatform.current
    val projects = platform.projects
    // Un ViewModel per progetto: passando a un altro progetto si riparte dalla sua casa.
    val vm: EditorViewModel = viewModel(key = "progetto-$projectId") {
        EditorViewModel(projects.planStore(projectId), platform.tips)
    }
    val state by vm.state.collectAsStateWithLifecycle()
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
    val extraMenu = listOf(
        "📁 Progetti" to { vm.saveNow(); onOpenProjects() },
        "🗂 Livelli…" to { showLayers = true },
        "📄 Tavola PDF in scala…" to { showPdf = true },
        "🧮 Computo e lista arredi" to { showTakeoff = true },
        "🗺 Pianta di sfondo…" to { showBackground = true },
    )
    if (showPdf) PdfExportDialog(state, projects.info(projectId), onDismiss = { showPdf = false })
    if (showTakeoff) TakeoffDialog(state, projects.info(projectId), onDismiss = { showTakeoff = false })
    if (showBackground) BackgroundDialog(state, vm, projectId, onDismiss = { showBackground = false })
    if (showLayers) LayersDialog(state, vm, onDismiss = { showLayers = false })
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
                        if (vm.copy()) vm.state.value.clipboard?.let { platform.toast("Copiato: ${clipLabel(it)} · Ctrl+V per incollare") }
                        else platform.toast("Seleziona prima qualcosa da copiare")
                        true
                    }
                    e.key == Key.V && !state.view3d -> {
                        if (!vm.pasteNow()) platform.toast(if (state.clipboard == null) "Niente da incollare: copia prima qualcosa con Ctrl+C" else "Qui non si può incollare")
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
        // E la barra di stato si nasconde (riappare temporaneamente scorrendo dal bordo superiore).
        LaunchedEffect(compactHeight) { platform.setStatusBarHidden(compactHeight) }
        // Vista 3D: telecamera e superficie OpenGL restano le stesse passando da 2D a 3D e ritorno.
        val view3dState = remember { View3DState() }
        val toolbar = @Composable { compact: Boolean ->
            Toolbar(
                state.canUndo,
                state.canRedo,
                vm,
                focusedRoom = state.plan.room(state.focusedRoomId),
                onExport = exportPng,
                extraMenu = extraMenu,
                compact = compact,
                onZoom = { f -> if (state.view3d) view3dState.zoom(f) else vm.zoomBy(f) },
                onFit = { if (state.view3d) view3dState.fitPlan(state.plan) else vm.fitToView() },
            )
        }
        // Piano che si sta modificando (e menu dei piani) prima delle schede delle stanze.
        if (compactHeight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FloorButton(state.building, vm, Modifier.padding(start = 8.dp), compact = true)
                RoomTabs(state.plan.rooms, state.focusedRoomId, onTap = vm::openRoomProperties, modifier = Modifier.weight(1f), compact = true)
                toolbar(true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FloorButton(state.building, vm, Modifier.padding(start = 8.dp))
                RoomTabs(state.plan.rooms, state.focusedRoomId, onTap = vm::openRoomProperties, modifier = Modifier.weight(1f))
            }
            toolbar(false)
        }
        HorizontalDivider()

        var openGuide by remember { mutableStateOf<Guide?>(null) }
        openGuide?.let { GuideDialog(it, onDismiss = { openGuide = null }, onOpen3d = { vm.setView3d(true) }) }

        Row(Modifier.weight(1f).fillMaxWidth()) {
        // In orizzontale i pulsanti di azione stanno in una colonna a sinistra: la larghezza abbonda,
        // l'altezza no, e così la pianta guadagna tutta l'altezza della barra in basso.
        if (compactHeight) {
            ActionRail(state.plan.rooms.isNotEmpty(), vm, onGuide = { openGuide = it })
            VerticalDivider()
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val wide = maxWidth >= 600.dp
            if (state.view3d) Plan3DView(state, vm, view3dState, Modifier.fillMaxSize())
            else androidx.compose.runtime.CompositionLocalProvider(LocalProjectId provides projectId) {
                PlanCanvas(state, vm, Modifier.fillMaxSize(), onPointerDown = { keyFocus.requestFocusSafely() })
            }
            // Suggerimento durante il posizionamento di un'apertura o di un impianto.
            val placingHint = state.pendingOpening?.let { "Tocca il muro su cui posizionare: ${it.kind.label.lowercase()}" }
                ?: state.pendingFixture?.let {
                    if (it.mount == Mount.Wall) "Tocca il muro su cui posizionare: ${it.label.lowercase()}"
                    else "Tocca il punto della stanza in cui mettere: ${it.label.lowercase()}"
                }
            // Schede in alto (posizionamento, suggerimento, tutorial): la pianta si centra sotto di loro.
            var topCardsHeightPx by remember { mutableIntStateOf(0) }
            val hasTopCards = placingHint != null || state.tip != null || state.tutorial != null || state.measure != null || state.underlayTool != null || state.wallDraw != null || state.selectMode || state.pasting || state.saveError != null ||
                state.dimensionDraw != null || state.textPlacing
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
                            TextButton(onClick = vm::cancelPlacingOpening) { Text("Annulla", color = MaterialTheme.colorScheme.inversePrimary) }
                        }
                    }
                }
                // Strumento "Distanza": cosa toccare, poi il risultato.
                state.measure?.let { MeasureCard(it, state, vm) }
                // Pianta di sfondo: taratura o spostamento in corso.
                UnderlayToolCard(state, vm)
                // Disegno dei muri, muro per muro.
                WallDrawCard(state, vm)
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
                            Text("⚠ Il progetto NON è stato salvato", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Le ultime modifiche sono solo in memoria: non chiudere Sagoma. Riprovo a ogni modifica. ($err)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = vm::saveNow) { Text("Riprova a salvare") }
                        }
                    }
                }
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
                ) { contentModifier ->
                    if (sel != null) SelectionPanel(sel, state.plan, vm, contentModifier, state.levelHeight, state.planBelow, state.levelHeightBelow)
                    else if (focusedRoom != null) RoomInfo(focusedRoom, state.plan, vm, contentModifier)
                }
            }
            // Legenda dei simboli: in basso a destra, sopra la striscia Info e a sinistra del pannello laterale.
            var showLegend by remember { mutableStateOf(false) }
            val cornerModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = with(density) { coveredRightPx.toDp() } + 8.dp,
                    bottom = with(density) { coveredBottomPx.toDp() } + 8.dp,
                )
            // Angolo in basso a destra: passaggio 2D/3D, e la legenda (2D) o Cammina / Vista dall'alto (3D).
            Column(cornerModifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendButton(
                    if (state.view3d) "◱ Pianta 2D" else "🧊 Vista 3D",
                    onClick = {
                        if (state.view3d && view3dState.walking) view3dState.stopWalking()
                        vm.setView3d(!state.view3d)
                    },
                )
                if (state.view3d) View3DControls(state, view3dState)
                else {
                    PhaseLegend(state)
                    LegendButton("Legenda", onClick = { showLegend = true })
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
            ScrollableBar(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Button(onClick = vm::startAddRoom) { Text("+ Aggiungi stanza") }
                VerticalDivider(Modifier.height(32.dp))
                if (state.selectMode) Button(onClick = vm::toggleSelectMode) { Text("☑ Seleziona") }
                else OutlinedButton(onClick = vm::toggleSelectMode) { Text("☐ Seleziona") }
                VerticalDivider(Modifier.height(32.dp))
                // Stato di fatto / progetto: gialli e rossi, stato di fatto, stato di progetto.
                PhaseViewButton(state, vm)
                VerticalDivider(Modifier.height(32.dp))
                OpeningButtons(enabled = state.plan.rooms.isNotEmpty(), vm)
                VerticalDivider(Modifier.height(32.dp))
                FixtureButton(enabled = state.plan.rooms.isNotEmpty(), vm)
                VerticalDivider(Modifier.height(32.dp))
                StairButton(vm)
                VerticalDivider(Modifier.height(32.dp))
                StructureButton(vm)
                VerticalDivider(Modifier.height(32.dp))
                if (com.sagoma.planimetria.Edition.isPro) {
                    FurnitureButton(vm)
                    VerticalDivider(Modifier.height(32.dp))
                }
                OutdoorButton(vm)
                VerticalDivider(Modifier.height(32.dp))
                OutlinedButton(onClick = { openGuide = AtticGuide }) { Text("⌂ Mansarda") }
                VerticalDivider(Modifier.height(32.dp))
                OutlinedButton(onClick = vm::addRuler) { Text("📏 Metro") }
                VerticalDivider(Modifier.height(32.dp))
                OutlinedButton(onClick = vm::startMeasure) { Text("📐 Distanza") }
                VerticalDivider(Modifier.height(32.dp))
                // Quote manuali e testi del disegno (tavole).
                if (state.dimensionDraw != null) Button(onClick = vm::cancelDimension) { Text("↔ Quota") }
                else OutlinedButton(onClick = vm::startDimension) { Text("↔ Quota") }
                if (state.textPlacing) Button(onClick = vm::cancelText) { Text("T Testo") }
                else OutlinedButton(onClick = vm::startText) { Text("T Testo") }
                VerticalDivider(Modifier.height(32.dp))
                GuidesButton(onGuide = { openGuide = it })
            }
        }
    }

    state.creation?.let { CreationFlow(it, vm) }
    state.floorDialog?.let { FloorEditDialog(it, state.fullBuilding, vm) }

    state.plan.room(state.confirmDeleteRoomId)?.let { DeleteRoomDialog(it, vm) }
    state.confirmMergeWall?.let { MergeWallDialog(it, state.plan, vm) }
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
            Text("📐 Distanza", style = MaterialTheme.typography.titleSmall, color = onColor)
            val text = when {
                first == null -> "Tocca il primo oggetto: un muro, una porta o finestra, un termosifone, una presa, una luce, una colonna, una trave o una scala."
                second == null -> "Primo: ${measureName(state.plan, first)}. Ora tocca il secondo oggetto."
                result == null -> "Non riesco a misurare questi due oggetti."
                else -> "Tra ${measureName(state.plan, first)} e ${measureName(state.plan, second)}"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
            if (result != null) {
                val dx = kotlin.math.abs(result.b.x - result.a.x)
                val dy = kotlin.math.abs(result.b.y - result.a.y)
                Text(
                    if (result.distance < 0.05) "Si toccano (0 cm)" else formatDistance(result.distance),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Se la distanza è di sbieco, anche le due componenti.
                if (dx > 0.5 && dy > 0.5) {
                    Text(
                        "in orizzontale ${formatDistance(dx)} · in verticale ${formatDistance(dy)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.8f),
                    )
                }
                Text(
                    "Distanza tra i bordi più vicini. Tocca un altro oggetto per una nuova misura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onColor.copy(alpha = 0.7f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (m.first != null) TextButton(onClick = vm::startMeasure) { Text("Nuova misura", color = onColor.copy(alpha = 0.8f)) }
                TextButton(onClick = vm::closeMeasure) { Text("Chiudi", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}

/** Scheda del suggerimento al primo utilizzo: titolo, spiegazione, "Salta tutti" e "Ho capito". */
@Composable
private fun TipCard(tip: Tip, onDismiss: () -> Unit, onSkipAll: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
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
                    Text("Salta tutti", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                }
                TextButton(onClick = onDismiss) { Text("Ho capito", color = MaterialTheme.colorScheme.inversePrimary) }
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
                            "Tutorial · passo ${t.number} di ${t.total}",
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
                        TextButton(onClick = { vm.finishTutorial(completed = false) }) { Text("No, grazie", color = onColor.copy(alpha = 0.7f)) }
                        TextButton(onClick = vm::nextTutorialStep) { Text("Inizia") }
                    }
                    TutorialStep.Done -> TextButton(onClick = { vm.finishTutorial() }) { Text("Fine") }
                    else -> {
                        TextButton(onClick = { vm.finishTutorial(completed = false) }) { Text("Esci", color = onColor.copy(alpha = 0.7f)) }
                        TextButton(onClick = vm::nextTutorialStep) { Text("Salta passo") }
                    }
                }
            }
        }
    }
}

/** Colonna di azioni a sinistra, usata in orizzontale al posto della barra in basso. */
@Composable
private fun ActionRail(openingsEnabled: Boolean, vm: EditorViewModel, onGuide: (Guide) -> Unit) {
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
        Button(onClick = vm::startAddRoom, modifier = buttonModifier, contentPadding = pad) { Text("+ Stanza", maxLines = 1) }
        HorizontalDivider()
        OpeningButtons(openingsEnabled, vm, compact = true, buttonModifier = buttonModifier)
        HorizontalDivider()
        FixtureButton(openingsEnabled, vm, buttonModifier = buttonModifier, contentPadding = pad)
        HorizontalDivider()
        StairButton(vm, buttonModifier, pad)
        HorizontalDivider()
        StructureButton(vm, buttonModifier, pad, compact = true)
        HorizontalDivider()
        if (com.sagoma.planimetria.Edition.isPro) {
            FurnitureButton(vm, buttonModifier, pad)
            HorizontalDivider()
        }
        OutdoorButton(vm, buttonModifier, pad, compact = true)
        HorizontalDivider()
        OutlinedButton(onClick = { onGuide(AtticGuide) }, modifier = buttonModifier, contentPadding = pad) { Text("⌂ Mansarda", maxLines = 1) }
        HorizontalDivider()
        OutlinedButton(onClick = vm::addRuler, modifier = buttonModifier, contentPadding = pad) { Text("📏 Metro", maxLines = 1) }
        HorizontalDivider()
        OutlinedButton(onClick = vm::startMeasure, modifier = buttonModifier, contentPadding = pad) { Text("📐 Distanza", maxLines = 1) }
        OutlinedButton(onClick = vm::startDimension, modifier = buttonModifier, contentPadding = pad) { Text("↔ Quota", maxLines = 1) }
        OutlinedButton(onClick = vm::startText, modifier = buttonModifier, contentPadding = pad) { Text("T Testo", maxLines = 1) }
        HorizontalDivider()
        GuidesButton(onGuide, buttonModifier, pad)
    }
}

/** "+ Scale": rampa dritta, a L, a U, a chiocciola. La scala compare al centro della vista (o della stanza scelta). */
@Composable
private fun StairButton(
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    MenuButton(
        "+ Scale",
        true,
        listOf(StairKind.Straight, StairKind.StraightLanding, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.Spiral)
            .map { k -> MenuEntry(k.label, { vm.addStair(k) }) },
        buttonModifier,
        contentPadding,
    )
}

/** "+ Colonne e travi": colonna quadrata o rotonda, trave a soffitto. */
@Composable
private fun StructureButton(
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    compact: Boolean = false,
) {
    MenuButton(
        if (compact) "+ Colonne" else "+ Colonne e travi",
        true,
        listOf(
            MenuEntry("Colonna quadrata", { vm.addColumn(ColumnShape.Square) }),
            MenuEntry("Colonna rotonda", { vm.addColumn(ColumnShape.Round) }),
            MenuEntry("Trave a soffitto", { vm.addBeam() }),
        ),
        buttonModifier,
        contentPadding,
    )
}

/** "+ Balconi e terrazze": si crea lo spazio all'aperto con il tipo già scelto. */
@Composable
private fun OutdoorButton(
    vm: EditorViewModel,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    compact: Boolean = false,
) {
    MenuButton(
        if (compact) "+ Esterni" else "+ Balconi e terrazze",
        true,
        listOf(RoomType.Balcone, RoomType.Terrazza).map { t -> MenuEntry(t.label, { vm.startAddOutdoor(t) }) },
        buttonModifier,
        contentPadding,
    )
}

/** "❔ Guide": elenco delle miniguide animate, una per argomento. */
@Composable
private fun GuidesButton(
    onGuide: (Guide) -> Unit,
    buttonModifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    MenuButton(
        "❔ Guide",
        true,
        Guides.map { g -> MenuEntry("${g.icon}  ${g.title}", { onGuide(g) }) },
        buttonModifier,
        contentPadding,
    )
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
        "+ Porte",
        enabled,
        listOf(
            MenuEntry("Porta ad 1 anta", place(OpeningKind.Door)),
            MenuEntry("Porta a 2 ante", place(OpeningKind.Door2)),
            MenuEntry("Porta scorrevole", place(OpeningKind.Door, sliding = true)),
            MenuEntry(
                "Apertura senza porta",
                children = PassageStyle.entries.map { s -> MenuEntry(s.label, place(OpeningKind.Passage, s)) },
            ),
        ),
        buttonModifier,
        pad,
    )
    MenuButton(
        "+ Infissi",
        enabled,
        listOf(
            MenuEntry(
                "Finestre",
                children = listOf(
                    MenuEntry("Finestra ad 1 anta", place(OpeningKind.Window1)),
                    MenuEntry("Finestra a 2 ante", place(OpeningKind.Window2)),
                    MenuEntry("Finestra scorrevole", place(OpeningKind.Window2, sliding = true)),
                ),
            ),
            MenuEntry(
                "Balcone",
                children = listOf(
                    MenuEntry("Balcone ad 1 anta", place(OpeningKind.Balcony1)),
                    MenuEntry("Balcone a 2 ante", place(OpeningKind.Balcony2)),
                    MenuEntry("Balcone scorrevole", place(OpeningKind.Balcony2, sliding = true)),
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
    val lights = listOf(FixtureKind.Spotlight, FixtureKind.Neon, FixtureKind.Chandelier, FixtureKind.CeilingLight, FixtureKind.LedStrip)
    MenuButton(
        "+ Impianti",
        enabled,
        listOf(
            MenuEntry("Calorifero", place(FixtureKind.Radiator)),
            MenuEntry("Presa di corrente", place(FixtureKind.Outlet)),
            MenuEntry("Interruttore", place(FixtureKind.Switch)),
            MenuEntry("Punto acqua", place(FixtureKind.WaterPoint)),
            MenuEntry("Luci", children = lights.map { MenuEntry(it.label, place(it)) }),
        ),
        buttonModifier,
        contentPadding,
    )
}

/** Voce di menu: esegue un'azione oppure, se ha figli, apre un sottomenu. */
private class MenuEntry(val label: String, val action: (() -> Unit)? = null, val children: List<MenuEntry> = emptyList())

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
) {
    var open by remember { mutableStateOf(false) }
    // Percorso dei sottomenu aperti (vuoto = menu principale).
    var path by remember { mutableStateOf(emptyList<MenuEntry>()) }
    fun close() { open = false; path = emptyList() }
    Box(modifier) {
        OutlinedButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = contentPadding) {
            Text("$label ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = ::close) {
            val current = path.lastOrNull()
            if (current != null) {
                DropdownMenuItem(text = { Text("‹ Indietro") }, onClick = { path = path.dropLast(1) })
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
                    text = { Text(if (entry.children.isNotEmpty()) "${entry.label}  ▸" else entry.label) },
                    onClick = {
                        if (entry.children.isNotEmpty()) path = path + entry
                        else { close(); entry.action?.invoke() }
                    },
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
    /** Zoom e "Adatta": sulla pianta 2D oppure, in 3D, sulla telecamera 3D. */
    onZoom: (Float) -> Unit = { vm.zoomBy(it) },
    onFit: () -> Unit = vm::fitToView,
) {
    // Compatta: deve stare su una riga anche sui telefoni stretti (~360 dp).
    val pad = PaddingValues(horizontal = 8.dp)
    Row(
        (if (compact) Modifier else Modifier.fillMaxWidth()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            IconButton(onClick = vm::undo, enabled = canUndo) { Text("↶", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = vm::redo, enabled = canRedo) { Text("↷", style = MaterialTheme.typography.titleLarge) }
        } else {
            TextButton(onClick = vm::undo, enabled = canUndo, contentPadding = pad) { Text("↶ Annulla", maxLines = 1) }
            TextButton(onClick = vm::redo, enabled = canRedo, contentPadding = pad) { Text("↷ Ripeti", maxLines = 1) }
            Box(Modifier.weight(1f))
        }
        IconButton(onClick = { onZoom(1f / 1.25f) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        IconButton(onClick = { onZoom(1.25f) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = onFit, contentPadding = pad) { Text("Adatta", maxLines = 1) }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // Azioni sulla stanza selezionata; senza selezione restano visibili ma disattivate, così si scoprono.
                DropdownMenuItem(
                    text = { Text(focusedRoom?.let { "Proprietà di ${it.name}…" } ?: "Proprietà stanza…") },
                    enabled = focusedRoom != null,
                    onClick = { menu = false; focusedRoom?.let { vm.showRoomInfo(it.id) } },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (focusedRoom == null) "Elimina stanza…" else "Elimina ${focusedRoom.name}…",
                        )
                    },
                    enabled = focusedRoom != null,
                    onClick = { menu = false; focusedRoom?.let { vm.requestDeleteRoom(it.id) } },
                )
                HorizontalDivider()
                for ((label, action) in extraMenu) DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                DropdownMenuItem(text = { Text("Esporta PNG") }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text("Rifai il tutorial") }, onClick = { menu = false; vm.restartTutorial() })
                DropdownMenuItem(text = { Text("Rivedi i suggerimenti") }, onClick = { menu = false; vm.resetTips() })
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
