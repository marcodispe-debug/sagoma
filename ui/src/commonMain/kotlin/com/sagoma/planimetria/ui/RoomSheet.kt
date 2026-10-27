package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sagoma.planimetria.editor.GuideField
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.Ceilings
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.zoneArea
import androidx.compose.ui.text.style.TextAlign
import com.sagoma.planimetria.testi.T

/** Trascinamento minimo (px) sulla striscia per aprire (verso l'alto) o chiudere (verso il basso) la tendina. */
private const val DRAG_THRESHOLD_PX = 24f
/** Trascinamento verso il basso oltre la cima del contenuto che chiude la tendina. */
private const val PULL_TO_CLOSE_PX = 120f
/** Velocità (px/s) oltre la quale uno swipe rapido apre o chiude la tendina anche se breve. */
private const val FLING_VELOCITY = 1200f

/**
 * Striscia "Info" in basso, che compare quando si seleziona una stanza o un oggetto: nome di ciò che
 * è selezionato, pulsante "Info" e freccia ▲/▼. Entrambi (o un trascinamento della striscia) aprono e
 * chiudono la tendina con le caratteristiche, che non si apre mai da sola. Non è modale: la pianta
 * resta usabile. `content` riceve il modifier che lo fa scorrere nello spazio rimasto.
 */
@Composable
fun InfoSheet(
    title: String,
    dotColor: Color?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Comandi sempre visibili sulla striscia, prima di "Info" (per esempio gli agganci). */
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { dragged += it }
    val setExpanded by rememberUpdatedState(onExpandedChange)
    // Swipe verso il basso sul contenuto: quando è già in cima (o non scorre), il trascinamento in
    // eccesso e il lancio verso il basso chiudono la tendina, come su un pannello inferiore di sistema.
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > PULL_TO_CLOSE_PX) { pulled = 0f; setExpanded(false) }
                } else if (available.y < 0 || consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                if (available.y > FLING_VELOCITY) setExpanded(false)
                return Velocity.Zero
            }
        }
    }

    Surface(
        modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        dragState,
                        Orientation.Vertical,
                        onDragStarted = { dragged = 0f },
                        // Swipe sulla striscia: in su apre, in giù chiude (basta anche un colpo rapido).
                        onDragStopped = { velocity ->
                            if (dragged < -DRAG_THRESHOLD_PX || velocity < -FLING_VELOCITY) onExpandedChange(true)
                            else if (dragged > DRAG_THRESHOLD_PX || velocity > FLING_VELOCITY) onExpandedChange(false)
                        },
                    )
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dotColor != null) {
                    Box(Modifier.size(10.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                // Nome e misura su una riga; se non ci stanno (pannello stretto, lingue con parole lunghe) il nome può andare
                // su due righe e la misura sotto, più piccola, invece di sparire nei puntini. Col telefono in orizzontale
                // (poca altezza) resta solo il nome: i dettagli si leggono aprendo Info.
                var twoLines by remember(title) { mutableStateOf(false) }
                val lowWindow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 500.dp
                val head = title.substringBefore(" · ")
                val rest = title.substringAfter(" · ", "")
                if (twoLines) Column(Modifier.weight(1f)) {
                    Text(head, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (rest.isNotEmpty() && !lowWindow) Text(rest, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) twoLines = true },
                    modifier = Modifier.weight(1f),
                )
                actions()
                TextButton(onClick = onToggle, modifier = Modifier.guide(GuideTarget.Info, done = expanded)) { Text(T.schedaStanza.info) }
                IconButton(onClick = onToggle) {
                    Text(if (expanded) "▼" else "▲", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (expanded) {
                HorizontalDivider()
                // weight(fill = false): il contenuto prende solo lo spazio rimasto sotto la striscia e,
                // se non basta (orizzontale, riquadro basso), scorre invece di venire tagliato.
                content(Modifier.weight(1f, fill = false).nestedScroll(pullToClose))
            }
        }
    }
}

/**
 * Caratteristiche della stanza selezionata, nella tendina "Info": in alto Duplica ed Elimina, nome, tipo e misure
 * principali; sotto le schede Misure, Finiture, Porte e impianti, Progetto, Foto. Le modifiche si applicano subito (e sono annullabili).
 */
@Composable
fun RoomInfo(room: Room, plan: FloorPlan, vm: EditorViewModel, modifier: Modifier = Modifier) {
    key(room.id) { RoomSheetContent(room, plan, vm, modifier) }
}

/** Schede della tendina della stanza. */
// Etichette lette ogni volta (non nel costruttore dell'enum), così seguono il cambio di lingua.
private enum class RoomTab {
    Measures, Finishes, Openings, Project, Photos;

    val label: String get() = when (this) {
        Measures -> T.schedaStanza.misure
        Finishes -> T.schedaStanza.finiture
        Openings -> T.schedaStanza.porteImpianti
        Project -> T.schedaStanza.progetto
        Photos -> T.schedaStanza.foto
    }
}

/** Ultima scheda aperta: resta passando da una stanza all'altra (e riaprendo la tendina). */
private var lastRoomTab by mutableStateOf(RoomTab.Measures)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheetContent(room: Room, plan: FloorPlan, vm: EditorViewModel, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    // Balconi e terrazze non hanno muri propri: niente scheda Progetto.
    val tabs = if (room.outdoor) RoomTab.entries - RoomTab.Project else RoomTab.entries
    val tab = lastRoomTab.takeIf { it in tabs } ?: RoomTab.Measures
    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        // Parte fissa: comandi della stanza, nome, tipo e misure principali.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Copia intera della stanza (o del balcone), con porte, finestre e impianti; eliminazione. In cima, sempre a portata di mano.
            PanelActions {
                DuplicateButton(vm)
                TextButton(onClick = { vm.requestDeleteRoom(room.id) }) { Text(T.schedaStanza.eliminaStanza, color = MaterialTheme.colorScheme.error) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Nome: si applica alla conferma o quando il campo perde il fuoco, non a ogni lettera
                // (così la cronologia non si riempie di passi inutili).
                var name by remember(room.name) { mutableStateOf(room.name) }
                var nameFocused by remember { mutableStateOf(false) }
                SelectAllTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = T.comuni.nome,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.renameRoom(room.id, name); focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f).onFocusChanged {
                        if (nameFocused && !it.isFocused) vm.renameRoom(room.id, name)
                        nameFocused = it.isFocused
                    },
                )
                RoomTypeMenu(room.type) { vm.changeRoomType(room.id, it) }
            }
            // Lunghezze e perimetro sul lato interno dei muri, come l'area; con un sottotetto il soffitto in pendenza abbassa il volume.
            val perimeter = interiorLengths(room).sum()
            Text(
                T.schedaStanza.perimetroCm(formatArea(room), formatCm(perimeter)) +
                    if (room.outdoor) T.schedaStanza.allAperto else " · ${com.sagoma.planimetria.geometry.formatDecimal(Ceilings.volumeM3(room), 2)} m³",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Schede a larghezza fissa: tutte visibili anche sul telefono ("Porte e impianti" va su due righe).
            InfoTabRow(
                tabs.map { t -> if (t == RoomTab.Photos && room.photos.isNotEmpty()) "${t.label} (${room.photos.size})" else t.label },
                tabs.indexOf(tab),
                tabModifier = { i -> Modifier.guide(GuideTarget.MeasuresTab.takeIf { tabs[i] == RoomTab.Measures }, done = tab == RoomTab.Measures) },
            ) { lastRoomTab = tabs[it] }
        }
        // Contenuto della scheda: scorre nello spazio rimasto, con la barra di scorrimento a destra (ogni scheda riparte dall'alto).
        val scroll = key(tab) { rememberScrollState() }
        Box(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
            Column(Modifier.verticalScroll(scroll).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    RoomTab.Measures -> MeasuresTab(room, vm)
                    RoomTab.Finishes -> FinishesTab(room, plan, vm)
                    RoomTab.Openings -> OpeningsTab(room, vm)
                    RoomTab.Project -> ProjectTab(room, vm)
                    RoomTab.Photos -> PhotosTab(room, vm)
                }
            }
            ScrollBar(scroll, Modifier.align(Alignment.TopEnd).offset(x = 6.dp))
        }
    }
}

/** Tipo di stanza in un menu (rinomina automaticamente). */
@Composable
private fun RoomTypeMenu(selected: RoomType, onSelect: (RoomType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.OutlinedButton(
            onClick = { open = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        ) {
            Box(Modifier.size(10.dp).background(Color(selected.argb), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("${selected.label} ▾", maxLines = 1)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (t in RoomType.entries) androidx.compose.material3.DropdownMenuItem(
                text = { Text(t.label) },
                leadingIcon = { Box(Modifier.size(10.dp).background(Color(t.argb), CircleShape)) },
                onClick = { open = false; if (t != selected) onSelect(t) },
            )
        }
    }
}

/** Scheda Misure: soffitto (o parapetto), spessore, muri e zone non calpestabili. */
@Composable
private fun MeasuresTab(room: Room, vm: EditorViewModel) {
    // Percorso B: SOLO le stanze create come schizzo (e non ancora misurate) offrono la scansione guidata.
    if (room.sketchPending && FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.SKETCH_GUIDED_SCAN)) {
        androidx.compose.material3.Button(onClick = { vm.startSketchScan(room.id) }) {
            Text(com.sagoma.planimetria.ui.sketch.LocalSketchScanText.current.startScan)
        }
    }
    if (room.outdoor) {
        // Balcone o terrazza: niente soffitto, si sceglie il parapetto.
        Choice(T.schedaStanza.parapetto, Parapet.entries.map { it.label }, room.parapet.ordinal, Modifier.fillMaxWidth()) { k ->
            vm.setParapet(room.id, parapet = Parapet.entries[k])
        }
        var parapetH by remember(room.parapetHeight) { mutableStateOf(formatCm(room.parapetHeight)) }
        SelectAllTextField(
            value = parapetH,
            onValueChange = {
                parapetH = it
                parsePositive(it)?.let { h -> vm.setParapet(room.id, height = h) }
            },
            label = T.schedaStanza.altezzaParapetto,
            numeric = true,
            suffix = "cm",
            isError = parsePositive(parapetH) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            T.schedaStanza.parapettoSuiLatiVerso,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        var ceiling by remember(room.ceilingHeight) { mutableStateOf(formatCm(room.ceilingHeight)) }
        var thick by remember(room.wallThickness) { mutableStateOf(formatCm(room.wallThickness)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectAllTextField(
                value = ceiling,
                onValueChange = {
                    ceiling = it
                    parsePositive(it)?.let { h -> vm.setCeilingHeight(room.id, h) }
                },
                label = T.schedaStanza.altezzaSoffitto,
                numeric = true,
                suffix = "cm",
                isError = parsePositive(ceiling) == null,
                modifier = Modifier.weight(1f),
            )
            SelectAllTextField(
                value = thick,
                onValueChange = {
                    thick = it
                    parsePositive(it)?.let { t -> vm.setRoomWallThickness(room.id, t) }
                },
                label = T.schedaStanza.spessoreMuri,
                numeric = true,
                suffix = "cm",
                isError = parsePositive(thick)?.let { it in 3.0..120.0 } != true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            T.schedaStanza.spessoreValeTuttiMuri,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    InteriorMeasuresNotice(Modifier.fillMaxWidth())

    // Muri: lunghezza (come i campi sulla pianta) e altezza, anche personalizzata per mansarde (§2).
    SectionTitle(T.schedaStanza.muri)
    val lengths = interiorLengths(room)
    for (i in 0 until room.wallCount) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(T.schedaStanza.muro(i + 1), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
            CommitNumberField(
                label = T.comuni.lunghezza,
                value = lengths[i],
                // Per la guida conta la misura già applicata (dopo Invio), non quella che si sta scrivendo.
                modifier = Modifier.weight(1f).guide(GuideTarget.Field(GuideField.WallInfo, i), value = formatCm(lengths[i])),
            ) { vm.setWallLength(room.id, i, it) }
            if (!room.outdoor) CommitNumberField(
                label = if (room.wallHeights.containsKey(i)) T.schedaStanza.altezzaPers else T.comuni.altezza,
                value = room.wallHeight(i),
                modifier = Modifier.weight(1f),
            ) { vm.setWallHeight(room.id, i, it) }
        }
    }

    // Zone non calpestabili: si tolgono dai m² come nelle planimetrie (mansarde sotto 1,50 m, cavedi, pilastri).
    SectionTitle(T.schedaStanza.zoneNonCalpestabili)
    if (room.excludedZones.isEmpty()) Text(
        T.schedaStanza.partiNonContanoNei,
        style = MaterialTheme.typography.bodySmall,
    )
    for (z in room.excludedZones) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${z.name} · −${com.sagoma.planimetria.geometry.formatDecimal(room.zoneArea(z) / 10_000.0, 2)} m²",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.removeExcludedZone(room.id, z.id) }) { Text(T.schedaStanza.togli, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (room.excludedZones.isNotEmpty()) Text(
        T.schedaStanza.pavimentoInteroM2Computo(com.sagoma.planimetria.geometry.formatDecimal(room.interiorArea() / 10_000.0, 2)),
        style = MaterialTheme.typography.bodySmall,
    )
    androidx.compose.material3.OutlinedButton(onClick = { vm.startExcludedZone(room.id) }) { Text(T.schedaStanza.aggiungiZonaNonCalpestabile) }
}

/** Scheda Finiture: aspetto nel 3D, pavimento e pareti. */
@Composable
private fun FinishesTab(room: Room, plan: FloorPlan, vm: EditorViewModel) {
    // Pavimento: dove ci sono i materiali fotografici si sceglie solo tra quelli («Nessuno» = colore della stanza). I pavimenti
    // disegnati di prima restano solo nei progetti che li hanno, e si possono togliere; senza materiali (browser) restano i soli.
    if (FeatureAccess.isEnabled(LocalPlatform.current, AppFeature.PHOTO_FLOORS)) {
        if (room.floorFinish != FloorFinish.RoomColor) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(T.schedaStanza.pavimentoDiPrima(T.lingua.minuscola(room.floorFinish.label)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.setRoomLook(room.id, floor = FloorFinish.RoomColor) }) { Text(T.schedaStanza.togli, color = MaterialTheme.colorScheme.error) }
        }
        MaterialPicker(T.schedaStanza.pavimento, room.floorMaterial, forFloor = true, allowNone = true) { vm.setFloorMaterial(room.id, it) }
    } else {
        ModelPicker(T.schedaStanza.pavimento, FloorFinish.entries, room.floorFinish, { it.label }, { vm.setRoomLook(room.id, floor = it) }) {
            floorPreview(it, Color(room.type.argb))
        }
    }
    // Colore qualsiasi e rivestimenti. La facciata è una sola per tutta la casa: si cambia da qualunque stanza
    // e vale per tutti i piani; con più piani si può fare un'eccezione per il piano corrente.
    if (!room.outdoor) FinishPicker(T.schedaStanza.paretiInterne, room.roomFinish, { vm.setRoomFinish(room.id, it) })
    val floors = vm.state.collectAsState().value.fullBuilding.floors
    val differs = floors.any { it.plan.facadeOrPaint != plan.facadeOrPaint }
    var onlyThisFloor by remember(floors.size > 1, differs) { mutableStateOf(differs) }
    val label = if (onlyThisFloor) T.schedaStanza.paretiEsterneSoloPiano else T.schedaStanza.paretiEsterneTuttaCasa
    FinishPicker(label, plan.facadeOrPaint, { vm.setFacadeFinish(it, onlyThisFloor) })
    if (floors.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = onlyThisFloor, onCheckedChange = { on ->
            onlyThisFloor = on
            // Tornando a "tutta la casa", gli altri piani prendono la facciata di questo.
            if (!on && differs) vm.setFacadeFinish(plan.facadeOrPaint)
        })
        Text(T.schedaStanza.soloQuestoPiano, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Una riga dell'elenco: un tocco seleziona l'oggetto e ne apre il riquadro caratteristiche. */
@Composable
private fun ItemRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Nome sopra e dettagli sotto: su una riga sola i dettagli lunghi schiacciavano il nome («Finest-ra 2 ante»).
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("  ›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Scheda Porte e impianti: elenco delle aperture e degli impianti della stanza. */
@Composable
private fun OpeningsTab(room: Room, vm: EditorViewModel) {
    SectionTitle(T.schedaStanza.aperture(room.openings.size))
    if (room.openings.isEmpty()) {
        Text(T.schedaStanza.nessunaUsaElementiArchitettonici, style = MaterialTheme.typography.bodySmall)
    }
    for (o in room.openings) ItemRow(
        openingLabel(o),
        T.schedaStanza.muroCm(o.wallIndex + 1, formatCm(o.width), formatCm(o.height)) + (if (o.kind.hasSill) T.schedaStanza.terra(formatCm(o.sillHeight)) else ""),
    ) { vm.selectOpening(room.id, o.id) }

    SectionTitle(T.schedaStanza.impianti(room.fixtures.size))
    if (room.fixtures.isEmpty()) {
        Text(T.schedaStanza.nessunoUsaElementiArchitettonici, style = MaterialTheme.typography.bodySmall)
    }
    for (f in room.fixtures) ItemRow(f.kind.label, fixtureSummary(f)) { vm.selectFixture(room.id, f.id) }
}

/** Scheda Progetto: stato di fatto / progetto di tutti i muri insieme (es. stanza nuova, o tramezzi da demolire). */
@Composable
private fun ProjectTab(room: Room, vm: EditorViewModel) {
    val phases = (0 until room.wallCount).map { room.phaseOf(it) }.distinct()
    val all = phases.singleOrNull()
    Text(
        T.schedaStanza.muriStanza + (all?.label?.let(T.lingua::minuscola) ?: T.schedaStanza.statiDiversiCambianoMuro),
        style = MaterialTheme.typography.labelLarge,
    )
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((p, label) in com.sagoma.planimetria.model.Phase.entries.zip(listOf(T.schedaStanza.tuttiEsistenti, T.schedaStanza.tuttiDemolire, T.schedaStanza.tuttiNuovi))) {
            androidx.compose.material3.OutlinedButton(onClick = { vm.setRoomPhase(room.id, p) }, enabled = all != p, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Text(
        T.schedaStanza.singoloMuroPortaFinestra,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Dati principali di un impianto per l'elenco della stanza. */
private fun fixtureSummary(f: Fixture): String = when (f.kind) {
    FixtureKind.Radiator -> T.schedaStanza.muroCmTerra(f.wallIndex + 1, formatCm(f.length), formatCm(f.height), formatCm(f.elevation))
    FixtureKind.Outlet, FixtureKind.Switch, FixtureKind.WaterPoint, FixtureKind.WallLight, FixtureKind.WallSpot -> T.schedaStanza.muroTerraCm(f.wallIndex + 1, formatCm(f.elevation))
    FixtureKind.WallLedStrip -> T.schedaStanza.muroCmTerraCm(f.wallIndex + 1, formatCm(f.length), formatCm(f.elevation))
    FixtureKind.Neon, FixtureKind.LedStrip -> T.schedaStanza.cm(formatCm(f.length), formatCm(f.rotation))
    else -> T.schedaStanza.soffitto
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
}

/**
 * Campo in cm che applica il valore solo alla conferma o quando perde il fuoco: per le lunghezze dei
 * muri, dove applicare ogni cifra digitata deformerebbe la stanza mentre si scrive.
 */
@Composable
internal fun CommitNumberField(label: String, value: Double, modifier: Modifier, onCommit: (Double) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(formatCm(value)) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val v = parsePositive(text)
        if (v != null && formatCm(v) != formatCm(value)) onCommit(v) else text = formatCm(value)
    }
    SelectAllTextField(
        value = text,
        onValueChange = { text = it },
        label = label,
        numeric = true,
        suffix = "cm",
        isError = parsePositive(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit(); focusManager.clearFocus() }),
        modifier = modifier.onFocusChanged {
            if (focused && !it.isFocused) commit()
            focused = it.isFocused
        },
    )
}

/**
 * Agganci accesi o spenti: con la calamita accesa angoli e oggetti scattano su angoli, allineamenti, direzioni dritte e
 * muri vicini; spenta, vanno esattamente dove li si porta.
 */
@Composable
internal fun SnapToggle(on: Boolean, onChange: (Boolean) -> Unit, compact: Boolean = false, guide: GuideTarget? = null) {
    // Sulla striscia Info la versione corta, per lasciare spazio al nome di ciò che è selezionato.
    val label = if (compact) (if (on) T.schedaStanza.si else T.schedaStanza.no) else (if (on) T.schedaStanza.agganci else T.schedaStanza.agganciNo)
    androidx.compose.material3.FilterChip(selected = on, onClick = { onChange(!on) }, label = { Text(label, maxLines = 1) }, modifier = Modifier.guide(guide))
}
