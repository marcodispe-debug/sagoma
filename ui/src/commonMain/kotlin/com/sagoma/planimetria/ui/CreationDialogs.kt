package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T

/**
 * Flusso di creazione stanza: forma → (orientamento L) → misure (§1). `onOpenProjects`: dalla scelta obbligatoria della prima stanza
 * (senza "Annulla") si può comunque tornare all'elenco dei progetti.
 */
@Composable
fun CreationFlow(step: CreationStep, vm: EditorViewModel, onOpenProjects: (() -> Unit)? = null) {
    // Senza "Annulla" la prima volta: la finestra non si chiude né col tasto indietro né toccando fuori.
    val props = DialogProperties(dismissOnBackPress = step.cancellable, dismissOnClickOutside = step.cancellable)
    when (step) {
        // Dove la scansione guidata è disponibile si chiede prima "Conosci le misure?" (sketch/KnowMeasures.kt); con "Sì" si arriva
        // al menu di sempre. Altrove (e per balconi e terrazze) il menu è quello di sempre, senza domanda.
        is CreationStep.PickShape -> com.sagoma.planimetria.ui.sketch.PickShapeEntry(step, vm, props) { sketchMode, backToQuestion -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text(if (sketchMode) com.sagoma.planimetria.ui.sketch.LocalSketchScanText.current.sketchShapeTitle else T.creazione.scegliFormaStanza) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sketchMode) Text(com.sagoma.planimetria.ui.sketch.LocalSketchScanText.current.approximateMeasuresHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShapeOption(T.creazione.quadrato, RoomFactory.rectangle(10.0, 10.0), Modifier.weight(1f)) { vm.pickShape(RoomShape.Square) }
                        ShapeOption(T.creazione.rettangolo, RoomFactory.rectangle(14.0, 9.0), Modifier.weight(1f)) { vm.pickShape(RoomShape.Rectangle) }
                        ShapeOption(T.comuni.formaL, lIcon(LOrientation.TopRight), Modifier.weight(1f)) { vm.pickShape(RoomShape.L) }
                    }
                    // Rilievo: lati e diagonali misurati sul posto, anche per stanze fuori squadra o con più lati.
                    // (Non nello schizzo: richiede misure esatte.)
                    if (!sketchMode) OutlinedCard(onClick = vm::pickSurvey, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(listOf(Vec2(0.0, 0.0), Vec2(14.0, 1.5), Vec2(12.0, 10.0), Vec2(1.0, 9.0)), Modifier.size(48.dp))
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(T.creazione.rilievo, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    T.creazione.latiDiagonaliMisuratiAnche,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // La vecchia "Scansiona con il telefono (prova)" (angoli toccati sul pavimento) non è più nei menu: la sostituisce la
                    // scansione guidata dallo schizzo ("Non conosco le misure"). Il suo codice resta (CreationStep.Scan).
                    // Disegno libero, come nei CAD: angolo dopo angolo sulla pianta, con agganci e misure scritte.
                    OutlinedCard(onClick = vm::startDrawWalls, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(listOf(Vec2(0.0, 0.0), Vec2(9.0, 0.0), Vec2(9.0, 4.0), Vec2(14.0, 4.0), Vec2(14.0, 10.0), Vec2(0.0, 10.0)), Modifier.size(48.dp))
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(T.creazione.muroMuro, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    T.creazione.disegniAngoliPiantaAgganci,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Prima stanza: chi ha già una piantina (catasto, progetto, foto) la mette sotto in scala e la ricalca.
                    if (!sketchMode && vm.state.value.plan.rooms.isEmpty() && vm.state.value.creationType?.outdoor != true) {
                        OutlinedCard(onClick = vm::startTracing, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🗺", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(T.creazione.ricalcaPiantina, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (LocalPlatform.current.pdfAsBackground) T.creazione.carichiFotoPdfPiantina
                                        else T.creazione.carichiFotoPiantinaMetti,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    // Un muro da solo (tramezzo): solo quando sul piano c'è già almeno una stanza in cui metterlo (non per la prima
                    // stanza del progetto né per la prima di un piano nuovo).
                    if (step.cancellable && !sketchMode && vm.state.value.plan.rooms.isNotEmpty()) {
                        OutlinedCard(onClick = vm::addFreeWall, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ShapeIcon(RoomFactory.rectangle(14.0, 1.6), Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(T.creazione.muroSingolo, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        T.creazione.tramezzoSoloAgganciaAgli,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row {
                    backToQuestion?.let { TextButton(onClick = it) { Text(com.sagoma.planimetria.ui.sketch.LocalSketchScanText.current.back) } }
                    if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text(T.comuni.annulla) }
                    else if (onOpenProjects != null) TextButton(onClick = onOpenProjects) { Text(T.creazione.progetti) }
                }
            },
        )
        }

        is CreationStep.PickLOrientation -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text(T.creazione.doveSporgeStanza) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LOrientation.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { o -> ShapeOption(o.label, lIcon(o), Modifier.weight(1f)) { vm.pickLOrientation(o) } }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::backToShapePicker) { Text(T.comuni.indietro) } },
        )

        is CreationStep.Measures -> MeasuresDialog(step, vm, props)
        is CreationStep.Survey -> SurveyDialog(vm, props)
        is CreationStep.Scan -> ScanHost(vm)
    }
}

/**
 * Rilievo: numero di lati, i lati in ordine a partire dall'angolo A (filo interno) e le diagonali da un angolo
 * scelto (A se non si sceglie altro); l'anteprima mostra subito la forma calcolata.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyDialog(vm: EditorViewModel, props: DialogProperties) {
    val preset = remember { vm.state.value.creationType }
    var type by remember { mutableStateOf(preset ?: RoomType.Altro) }
    var count by remember { mutableStateOf(4) }
    var sides by remember { mutableStateOf(List(12) { "" }) }
    var diagonals by remember { mutableStateOf(List(9) { "" }) }
    var ceiling by remember { mutableStateOf(formatCm(Room.DEFAULT_CEILING_HEIGHT)) }
    var thickness by remember { mutableStateOf(formatCm(Room.WALL_THICKNESS)) }
    val letters = "ABCDEFGHIJKL"
    var diagonalsFrom by remember { mutableStateOf(0) }
    val needed = com.sagoma.planimetria.geometry.Survey.diagonalsNeeded(count)
    // Con 4 lati le diagonali sono solo AC e BD; con più lati si sceglie l'angolo da cui partono tutte.
    val fromChoices = if (count == 4) listOf(0, 1) else (0 until count).toList()
    val from = diagonalsFrom.takeIf { it in fromChoices } ?: 0
    val sideValues = sides.take(count).map(::parsePositive)
    val diagValues = diagonals.take(needed).map(::parsePositive)
    val solved = if (sideValues.all { it != null } && diagValues.all { it != null })
        com.sagoma.planimetria.geometry.Survey.solve(sideValues.map { it!! }, diagValues.map { it!! }, from) else null
    val ceilingValue = parsePositive(ceiling)
    val thicknessValue = parsePositive(thickness)?.takeIf { it in 3.0..120.0 }
    AlertDialog(
        onDismissRequest = vm::cancelCreation,
        properties = props,
        title = { Text(T.creazione.stanzaRilievo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(T.creazione.tipoStanza, style = MaterialTheme.typography.labelLarge)
                RoomTypePicker(type) { type = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(T.creazione.numeroLati, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (count > 3) count-- }, enabled = count > 3) { Text("−") }
                    Text("$count", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { if (count < 12) count++ }, enabled = count < 12) { Text("+") }
                }
                Text(
                    T.creazione.misuraLatiFiloInterno,
                    style = MaterialTheme.typography.bodySmall,
                )
                for (i in 0 until count) {
                    SelectAllTextField(
                        value = sides[i],
                        onValueChange = { v -> sides = sides.toMutableList().also { it[i] = v } },
                        label = T.creazione.lato(letters[i], letters[(i + 1) % count]),
                        numeric = true,
                        suffix = "cm",
                        isError = sides[i].isNotEmpty() && sideValues[i] == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needed > 0) {
                    Text(if (count == 4) T.creazione.qualeDiagonale else T.creazione.diagonaliDallAngolo, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in fromChoices) FilterChip(
                            selected = c == from,
                            onClick = { diagonalsFrom = c },
                            label = { Text(if (count == 4) "${letters[c]}${letters[c + 2]}" else "${letters[c]}") },
                        )
                    }
                }
                for (k in 0 until needed) {
                    SelectAllTextField(
                        value = diagonals[k],
                        onValueChange = { v -> diagonals = diagonals.toMutableList().also { it[k] = v } },
                        label = T.creazione.diagonale(letters[from], letters[(from + k + 2) % count]),
                        numeric = true,
                        suffix = "cm",
                        isError = diagonals[k].isNotEmpty() && diagValues[k] == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (solved != null) {
                    Text(T.creazione.anteprima, style = MaterialTheme.typography.labelLarge)
                    ShapeIcon(solved.map { it - Vec2(solved.minOf { p -> p.x }, solved.minOf { p -> p.y }) + Vec2(0.01, 0.01) }, Modifier.fillMaxWidth().height(120.dp))
                } else if (sideValues.all { it != null } && diagValues.all { it != null }) {
                    Text(T.creazione.questeMisureStanzaNon, color = MaterialTheme.colorScheme.error)
                }
                SelectAllTextField(
                    value = thickness, onValueChange = { thickness = it }, label = T.creazione.spessoreMuri, numeric = true, suffix = "cm",
                    isError = thicknessValue == null, modifier = Modifier.fillMaxWidth(),
                )
                if (!type.outdoor) SelectAllTextField(
                    value = ceiling, onValueChange = { ceiling = it }, label = T.creazione.altezzaSoffitto, numeric = true, suffix = "cm",
                    isError = ceilingValue == null, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = solved != null && thicknessValue != null && (ceilingValue != null || type.outdoor),
                onClick = { vm.createRoomFromSurvey(type, solved!!, ceilingValue ?: Room.DEFAULT_CEILING_HEIGHT, thicknessValue!!) },
            ) { Text(T.creazione.creaStanza) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = vm::backToShapePicker) { Text(T.comuni.indietro) }
                if (props.dismissOnBackPress) TextButton(onClick = vm::cancelCreation) { Text(T.comuni.annulla) }
            }
        },
    )
}

private fun lIcon(o: LOrientation) =
    RoomFactory.lShape(ShapeDimensions(14.0, 7.0, 6.0, 5.0), o)

@Composable
private fun ShapeOption(label: String, points: List<Vec2>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        // Margini laterali stretti ed etichetta su una riga: con caratteri grandi "Rettangolo" andava a capo.
        Column(Modifier.padding(horizontal = 2.dp, vertical = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeIcon(points, Modifier.size(48.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

@Composable
private fun ShapeIcon(points: List<Vec2>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val k = (minOf(size.width / maxX, size.height / maxY) * 0.85).toFloat()
        val ox = (size.width - maxX.toFloat() * k) / 2
        val oy = (size.height - maxY.toFloat() * k) / 2
        val path = Path().apply {
            points.forEachIndexed { i, p ->
                val o = Offset(ox + p.x.toFloat() * k, oy + p.y.toFloat() * k)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
        drawPath(path, color.copy(alpha = 0.15f))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomTypePicker(selected: RoomType, onSelect: (RoomType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoomType.entries.forEach { t ->
            FilterChip(
                selected = t == selected,
                onClick = { onSelect(t) },
                label = { Text(t.label) },
                leadingIcon = { Box(Modifier.size(10.dp).background(Color(t.argb), CircleShape)) },
            )
        }
    }
}

@Composable
private fun MeasuresDialog(step: CreationStep.Measures, vm: EditorViewModel, props: DialogProperties) {
    val preset = remember { vm.state.value.creationType }
    var type by remember { mutableStateOf(preset ?: RoomType.Altro) }
    val defaults = when (step.shape) {
        RoomShape.Square -> listOf(if (preset?.outdoor == true) "300" else "400")
        // Un balcone è stretto e lungo.
        RoomShape.Rectangle -> if (preset == RoomType.Balcone) listOf("400", "150") else listOf("500", "400")
        RoomShape.L -> listOf("600", "400", "300", "250")
    }
    var values by remember { mutableStateOf(defaults) }
    var ceiling by remember { mutableStateOf(formatCm(Room.DEFAULT_CEILING_HEIGHT)) }

    val parsed = values.map(::parsePositive)
    val ceilingValue = parsePositive(ceiling)
    val lInvalid = step.shape == RoomShape.L && parsed.all { it != null } && parsed[2]!! >= parsed[0]!!
    val valid = parsed.all { it != null } && (ceilingValue != null || type.outdoor) && !lInvalid

    val labels = when (step.shape) {
        RoomShape.Square -> listOf(T.creazione.lato2)
        RoomShape.Rectangle -> listOf(T.comuni.larghezza, T.comuni.profondita)
        RoomShape.L -> listOf(T.creazione.larghezzaPrincipale, T.creazione.profonditaPrincipale, T.creazione.larghezzaProtuberanza, T.creazione.profonditaProtuberanza)
    }

    AlertDialog(
        onDismissRequest = vm::cancelCreation,
        properties = props,
        title = {
            Text(
                when (type) {
                    RoomType.Balcone -> T.creazione.misureBalcone
                    RoomType.Terrazza -> T.creazione.misureTerrazza
                    else -> T.creazione.misureStanza
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Percorso B: nello schizzo le misure sono indicative (le correggerà la scansione).
                if (vm.state.collectAsState().value.creationIntent?.isSketch == true) {
                    Text(com.sagoma.planimetria.ui.sketch.LocalSketchScanText.current.approximateMeasuresHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(T.creazione.tipoStanza, style = MaterialTheme.typography.labelLarge)
                RoomTypePicker(type) { type = it }
                labels.forEachIndexed { i, label ->
                    SelectAllTextField(
                        value = values[i],
                        onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
                        label = label,
                        numeric = true,
                        suffix = "cm",
                        isError = parsed[i] == null || (lInvalid && i == 2),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (lInvalid) Text(T.creazione.protuberanzaDeveEsserePiu, color = MaterialTheme.colorScheme.error)
                // Le misure inserite qui sono interne (calpestabili): lo si dice prima che l'utente le scriva.
                InteriorMeasuresNotice(Modifier.fillMaxWidth())
                // All'aperto non c'è soffitto: il parapetto si sceglie dopo, da Info.
                if (type.outdoor) Text(
                    T.creazione.spazioAllApertoPosto,
                    style = MaterialTheme.typography.bodySmall,
                ) else SelectAllTextField(
                    value = ceiling,
                    onValueChange = { ceiling = it },
                    label = T.creazione.altezzaSoffitto,
                    numeric = true,
                    suffix = "cm",
                    isError = ceilingValue == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val p = parsed.map { it!! }
                    val dims = when (step.shape) {
                        RoomShape.Square -> ShapeDimensions(p[0], p[0])
                        RoomShape.Rectangle -> ShapeDimensions(p[0], p[1])
                        RoomShape.L -> ShapeDimensions(p[0], p[1], p[2], p[3])
                    }
                    vm.createRoom(type, dims, ceilingValue ?: Room.DEFAULT_CEILING_HEIGHT)
                },
            ) { Text(if (type.outdoor) T.creazione.crea else T.creazione.creaStanza) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = vm::backToShapePicker) { Text(T.comuni.indietro) }
                if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text(T.comuni.annulla) }
            }
        },
    )
}


/** Schermata intera della scansione: sopra l'editor, senza toccare la pianta finché non si conferma. */
@Composable
private fun ScanHost(vm: EditorViewModel) {
    val scanner = LocalPlatform.current.roomScanner
    if (scanner == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.cancelScan() }
        return
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = vm::cancelScan,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            scanner.Screen { result -> if (result == null) vm.cancelScan() else vm.createRoomFromScan(result.room) }
        }
    }
}
