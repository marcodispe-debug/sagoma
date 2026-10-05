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

/** Flusso di creazione stanza: forma → (orientamento L) → misure (§1). */
@Composable
fun CreationFlow(step: CreationStep, vm: EditorViewModel) {
    // Senza "Annulla" la prima volta: la finestra non si chiude né col tasto indietro né toccando fuori.
    val props = DialogProperties(dismissOnBackPress = step.cancellable, dismissOnClickOutside = step.cancellable)
    when (step) {
        is CreationStep.PickShape -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text("Scegli la forma della stanza") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShapeOption("Quadrato", RoomFactory.rectangle(10.0, 10.0), Modifier.weight(1f)) { vm.pickShape(RoomShape.Square) }
                        ShapeOption("Rettangolo", RoomFactory.rectangle(14.0, 9.0), Modifier.weight(1f)) { vm.pickShape(RoomShape.Rectangle) }
                        ShapeOption("A L", lIcon(LOrientation.TopRight), Modifier.weight(1f)) { vm.pickShape(RoomShape.L) }
                    }
                    // Rilievo: lati e diagonali misurati sul posto, anche per stanze fuori squadra o con più lati.
                    OutlinedCard(onClick = vm::pickSurvey, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(listOf(Vec2(0.0, 0.0), Vec2(14.0, 1.5), Vec2(12.0, 10.0), Vec2(1.0, 9.0)), Modifier.size(48.dp))
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text("Da rilievo", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Lati e diagonali misurati: anche stanze fuori squadra o con 5 o più lati",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Scansione con la fotocamera (solo dove la piattaforma la offre): si toccano gli angoli sul pavimento.
                    if (LocalPlatform.current.roomScanner != null) {
                        OutlinedCard(onClick = vm::pickScan, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📱", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.size(48.dp).padding(top = 6.dp))
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text("Scansiona con il telefono (prova)", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Fotocamera e realtà aumentata: tocchi gli angoli sul pavimento e le misure si ricavano da sole",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    // Disegno libero, come nei CAD: angolo dopo angolo sulla pianta, con agganci e misure scritte.
                    OutlinedCard(onClick = vm::startDrawWalls, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(listOf(Vec2(0.0, 0.0), Vec2(9.0, 0.0), Vec2(9.0, 4.0), Vec2(14.0, 4.0), Vec2(14.0, 10.0), Vec2(0.0, 10.0)), Modifier.size(48.dp))
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text("Muro per muro", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Disegni gli angoli sulla pianta, con agganci e misure esatte: qualsiasi forma",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Un muro da solo (tramezzo): solo quando c'è già almeno una stanza in cui metterlo.
                    if (step.cancellable) {
                        OutlinedCard(onClick = vm::addFreeWall, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ShapeIcon(RoomFactory.rectangle(14.0, 1.6), Modifier.size(48.dp))
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text("Muro singolo", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Un tramezzo da solo, che si aggancia agli altri muri",
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
            dismissButton = { if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text("Annulla") } },
        )

        is CreationStep.PickLOrientation -> AlertDialog(
            onDismissRequest = vm::cancelCreation,
            properties = props,
            title = { Text("Dove sporge la stanza?") },
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
            dismissButton = { TextButton(onClick = vm::backToShapePicker) { Text("Indietro") } },
        )

        is CreationStep.Measures -> MeasuresDialog(step, vm, props)
        is CreationStep.Survey -> SurveyDialog(vm, props)
        is CreationStep.Scan -> ScanHost(vm)
    }
}

/**
 * Rilievo: numero di lati, i lati in ordine a partire dall'angolo A (filo interno) e le diagonali da A;
 * l'anteprima mostra subito la forma calcolata.
 */
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
    val sideValues = sides.take(count).map(::parsePositive)
    val diagValues = diagonals.take(com.sagoma.planimetria.geometry.Survey.diagonalsNeeded(count)).map(::parsePositive)
    val solved = if (sideValues.all { it != null } && diagValues.all { it != null })
        com.sagoma.planimetria.geometry.Survey.solve(sideValues.map { it!! }, diagValues.map { it!! }) else null
    val ceilingValue = parsePositive(ceiling)
    val thicknessValue = parsePositive(thickness)?.takeIf { it in 3.0..120.0 }
    AlertDialog(
        onDismissRequest = vm::cancelCreation,
        properties = props,
        title = { Text("Stanza da rilievo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo di stanza", style = MaterialTheme.typography.labelLarge)
                RoomTypePicker(type) { type = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Numero di lati", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (count > 3) count-- }, enabled = count > 3) { Text("−") }
                    Text("$count", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { if (count < 12) count++ }, enabled = count < 12) { Text("+") }
                }
                Text(
                    "Misura i lati sul filo interno dei muri, girando in senso orario dall'angolo A; poi le diagonali dall'angolo A " +
                        "agli angoli opposti. Con le diagonali la forma è quella vera, anche se gli angoli non sono a 90°.",
                    style = MaterialTheme.typography.bodySmall,
                )
                for (i in 0 until count) {
                    SelectAllTextField(
                        value = sides[i],
                        onValueChange = { v -> sides = sides.toMutableList().also { it[i] = v } },
                        label = "Lato ${letters[i]}${letters[(i + 1) % count]}",
                        numeric = true,
                        suffix = "cm",
                        isError = sides[i].isNotEmpty() && sideValues[i] == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                for (k in 0 until com.sagoma.planimetria.geometry.Survey.diagonalsNeeded(count)) {
                    SelectAllTextField(
                        value = diagonals[k],
                        onValueChange = { v -> diagonals = diagonals.toMutableList().also { it[k] = v } },
                        label = "Diagonale A${letters[k + 2]}",
                        numeric = true,
                        suffix = "cm",
                        isError = diagonals[k].isNotEmpty() && diagValues[k] == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (solved != null) {
                    Text("Anteprima", style = MaterialTheme.typography.labelLarge)
                    ShapeIcon(solved.map { it - Vec2(solved.minOf { p -> p.x }, solved.minOf { p -> p.y }) + Vec2(0.01, 0.01) }, Modifier.fillMaxWidth().height(120.dp))
                } else if (sideValues.all { it != null } && diagValues.all { it != null }) {
                    Text("Con queste misure la stanza non si chiude: controlla lati e diagonali.", color = MaterialTheme.colorScheme.error)
                }
                SelectAllTextField(
                    value = thickness, onValueChange = { thickness = it }, label = "Spessore dei muri", numeric = true, suffix = "cm",
                    isError = thicknessValue == null, modifier = Modifier.fillMaxWidth(),
                )
                if (!type.outdoor) SelectAllTextField(
                    value = ceiling, onValueChange = { ceiling = it }, label = "Altezza soffitto", numeric = true, suffix = "cm",
                    isError = ceilingValue == null, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = solved != null && thicknessValue != null && (ceilingValue != null || type.outdoor),
                onClick = { vm.createRoomFromSurvey(type, solved!!, ceilingValue ?: Room.DEFAULT_CEILING_HEIGHT, thicknessValue!!) },
            ) { Text("Crea stanza") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = vm::backToShapePicker) { Text("Indietro") }
                if (props.dismissOnBackPress) TextButton(onClick = vm::cancelCreation) { Text("Annulla") }
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
        RoomShape.Square -> listOf("Lato")
        RoomShape.Rectangle -> listOf("Larghezza", "Profondità")
        RoomShape.L -> listOf("Larghezza principale", "Profondità principale", "Larghezza protuberanza", "Profondità protuberanza")
    }

    AlertDialog(
        onDismissRequest = vm::cancelCreation,
        properties = props,
        title = {
            Text(
                when (type) {
                    RoomType.Balcone -> "Misure del balcone"
                    RoomType.Terrazza -> "Misure della terrazza"
                    else -> "Misure della stanza"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo di stanza", style = MaterialTheme.typography.labelLarge)
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
                if (lInvalid) Text("La protuberanza deve essere più stretta della stanza principale.", color = MaterialTheme.colorScheme.error)
                // Le misure inserite qui sono interne (calpestabili): lo si dice prima che l'utente le scriva.
                InteriorMeasuresNotice(Modifier.fillMaxWidth())
                // All'aperto non c'è soffitto: il parapetto si sceglie dopo, da Info.
                if (type.outdoor) Text(
                    "Spazio all'aperto: al posto dei muri c'è un parapetto (ringhiera, muretto o vetro), da scegliere poi in Info.",
                    style = MaterialTheme.typography.bodySmall,
                ) else SelectAllTextField(
                    value = ceiling,
                    onValueChange = { ceiling = it },
                    label = "Altezza soffitto",
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
            ) { Text(if (type.outdoor) "Crea" else "Crea stanza") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = vm::backToShapePicker) { Text("Indietro") }
                if (step.cancellable) TextButton(onClick = vm::cancelCreation) { Text("Annulla") }
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
