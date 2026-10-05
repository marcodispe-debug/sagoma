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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.sagoma.planimetria.Edition
import com.sagoma.planimetria.model.Room

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
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggle) { Text("Info") }
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
 * Caratteristiche della stanza selezionata, nella tendina "Info": nome, tipo, altezza soffitto, misure,
 * muri, aperture, impianti, eliminazione. Le modifiche si applicano subito (e sono annullabili).
 */
@Composable
fun RoomInfo(room: Room, plan: FloorPlan, vm: EditorViewModel, modifier: Modifier = Modifier) {
    key(room.id) { RoomSheetContent(room, plan, vm, modifier) }
}

@Composable
private fun RoomSheetContent(room: Room, plan: FloorPlan, vm: EditorViewModel, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Copia intera della stanza (o del balcone), con porte, finestre e impianti.
        PanelActions { DuplicateButton(vm) }
        // Nome: si applica alla conferma o quando il campo perde il fuoco, non a ogni lettera
        // (così la cronologia non si riempie di passi inutili).
        var name by remember(room.name) { mutableStateOf(room.name) }
        var nameFocused by remember { mutableStateOf(false) }
        SelectAllTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nome",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.renameRoom(room.id, name); focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (nameFocused && !it.isFocused) vm.renameRoom(room.id, name)
                nameFocused = it.isFocused
            },
        )

        Text("Tipo di stanza (rinomina automaticamente)", style = MaterialTheme.typography.labelLarge)
        RoomTypePicker(room.type) { vm.changeRoomType(room.id, it) }

        // Stato di fatto / progetto di tutti i muri insieme (es. stanza nuova, o tramezzi da demolire).
        if (!room.outdoor) {
            val phases = (0 until room.wallCount).map { room.phaseOf(it) }.distinct()
            val all = phases.singleOrNull()
            Text(
                "Muri della stanza: " + (all?.label?.lowercase() ?: "stati diversi (si cambiano muro per muro)"),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((p, label) in com.sagoma.planimetria.model.Phase.entries.zip(listOf("Tutti esistenti", "Tutti da demolire", "Tutti nuovi"))) {
                    androidx.compose.material3.OutlinedButton(onClick = { vm.setRoomPhase(room.id, p) }, enabled = all != p, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        var ceiling by remember(room.ceilingHeight) { mutableStateOf(formatCm(room.ceilingHeight)) }
        // Balcone o terrazza: niente soffitto, si sceglie il parapetto.
        if (room.outdoor) {
            Choice("Parapetto", Parapet.entries.map { it.label }, room.parapet.ordinal, Modifier.fillMaxWidth()) { k ->
                vm.setParapet(room.id, parapet = Parapet.entries[k])
            }
            var parapetH by remember(room.parapetHeight) { mutableStateOf(formatCm(room.parapetHeight)) }
            SelectAllTextField(
                value = parapetH,
                onValueChange = {
                    parapetH = it
                    parsePositive(it)?.let { h -> vm.setParapet(room.id, height = h) }
                },
                label = "Altezza del parapetto",
                numeric = true,
                suffix = "cm",
                isError = parsePositive(parapetH) == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Il parapetto c'è sui lati verso l'esterno; dove il balcone tocca la casa resta il muro. " +
                    "Per uscirci metti una porta-finestra (+ Infissi → Balcone) sul muro della stanza.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else SelectAllTextField(
            value = ceiling,
            onValueChange = {
                ceiling = it
                parsePositive(it)?.let { h -> vm.setCeilingHeight(room.id, h) }
            },
            label = "Altezza soffitto",
            numeric = true,
            suffix = "cm",
            isError = parsePositive(ceiling) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!room.outdoor) {
            var thick by remember(room.wallThickness) { mutableStateOf(formatCm(room.wallThickness)) }
            SelectAllTextField(
                value = thick,
                onValueChange = {
                    thick = it
                    parsePositive(it)?.let { t -> vm.setRoomWallThickness(room.id, t) }
                },
                label = "Spessore dei muri",
                numeric = true,
                suffix = "cm",
                isError = parsePositive(thick)?.let { it in 3.0..120.0 } != true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Vale per tutti i muri della stanza; un singolo muro (per esempio un portante da 40 cm o un tramezzo da 10 cm) " +
                    "si cambia toccandolo.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Aspetto nel 3D: pavimento e colore delle pareti.
        SectionTitle("Finiture")
        ModelPicker("Pavimento", FloorFinish.entries, room.floorFinish, { it.label }, { vm.setRoomLook(room.id, floor = it) }) {
            floorPreview(it, Color(room.type.argb))
        }
        if (Edition.isPro) {
            // Pro: pavimento con un materiale fotografico (vince sulla finitura qui sopra).
            MaterialPicker("Pavimento fotografico", room.floorMaterial, forFloor = true, allowNone = true) { vm.setFloorMaterial(room.id, it) }
            // Pro: colore qualsiasi e rivestimenti.
            if (!room.outdoor) FinishPicker("Pareti interne", room.roomFinish, { vm.setRoomFinish(room.id, it) })
            FinishPicker("Pareti esterne (facciata del piano)", plan.facadeOrPaint, { vm.setFacadeFinish(it) })
        } else {
            if (!room.outdoor) {
                ColorPicker("Pareti interne", WallPaint.entries.map { it.label to it.argb }, room.wallPaint.ordinal, { k ->
                    vm.setRoomLook(room.id, paint = WallPaint.entries[k])
                })
            }
            // La facciata è una sola per tutto il piano: si cambia da qualunque stanza.
            ColorPicker("Pareti esterne (facciata del piano)", WallPaint.entries.map { it.label to it.argb }, plan.facade.ordinal, { k ->
                vm.setFacade(WallPaint.entries[k])
            })
        }

        // Misure calcolate.
        SectionTitle("Misure")
        // Lunghezze e perimetro sul lato interno dei muri, come l'area.
        val lengths = interiorLengths(room)
        val perimeter = lengths.sum()
        // Con un sottotetto il soffitto in pendenza abbassa il volume.
        val volumeM3 = Ceilings.volumeM3(room)
        Text(
            "Area ${formatArea(room)} · Perimetro ${formatCm(perimeter)} cm" +
                if (room.outdoor) " · all'aperto" else " · Volume ${com.sagoma.planimetria.geometry.formatDecimal(volumeM3, 2)} m³",
            style = MaterialTheme.typography.bodyMedium,
        )
        InteriorMeasuresNotice(Modifier.fillMaxWidth())

        // Muri: lunghezza (come i campi sulla pianta) e altezza, anche personalizzata per mansarde (§2).
        SectionTitle("Muri")
        for (i in 0 until room.wallCount) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Muro ${i + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                CommitNumberField(
                    label = "Lunghezza",
                    value = lengths[i],
                    modifier = Modifier.weight(1f),
                ) { vm.setWallLength(room.id, i, it) }
                if (!room.outdoor) CommitNumberField(
                    label = if (room.wallHeights.containsKey(i)) "Altezza (pers.)" else "Altezza",
                    value = room.wallHeight(i),
                    modifier = Modifier.weight(1f),
                ) { vm.setWallHeight(room.id, i, it) }
            }
        }

        // Aperture: un tocco seleziona l'apertura e ne apre il riquadro caratteristiche.
        SectionTitle("Aperture (${room.openings.size})")
        if (room.openings.isEmpty()) {
            Text("Nessuna. Usa + Porte o + Infissi in basso.", style = MaterialTheme.typography.bodySmall)
        }
        for (o in room.openings) {
            Surface(
                onClick = { vm.selectOpening(room.id, o.id) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(openingLabel(o), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "muro ${o.wallIndex + 1} · ${formatCm(o.width)}×${formatCm(o.height)} cm" +
                            (if (o.kind.hasSill) " · da terra ${formatCm(o.sillHeight)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("  ›", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Impianti: un tocco seleziona l'impianto e ne apre il riquadro caratteristiche.
        SectionTitle("Impianti (${room.fixtures.size})")
        if (room.fixtures.isEmpty()) {
            Text("Nessuno. Usa + Impianti per caloriferi, prese, interruttori, punti acqua e luci.", style = MaterialTheme.typography.bodySmall)
        }
        for (f in room.fixtures) {
            Surface(
                onClick = { vm.selectFixture(room.id, f.id) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.kind.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(fixtureSummary(f), style = MaterialTheme.typography.bodySmall)
                    Text("  ›", style = MaterialTheme.typography.titleMedium)
                }
            }
        }


        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.requestDeleteRoom(room.id) }) {
                Text("Elimina stanza", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Dati principali di un impianto per l'elenco della stanza. */
private fun fixtureSummary(f: Fixture): String = when (f.kind) {
    FixtureKind.Radiator -> "muro ${f.wallIndex + 1} · ${formatCm(f.length)}×${formatCm(f.height)} cm · da terra ${formatCm(f.elevation)}"
    FixtureKind.Outlet, FixtureKind.Switch, FixtureKind.WaterPoint, FixtureKind.WallLight, FixtureKind.WallSpot -> "muro ${f.wallIndex + 1} · da terra ${formatCm(f.elevation)} cm"
    FixtureKind.Neon, FixtureKind.LedStrip -> "${formatCm(f.length)} cm · ${formatCm(f.rotation)}°"
    else -> "soffitto"
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
private fun CommitNumberField(label: String, value: Double, modifier: Modifier, onCommit: (Double) -> Unit) {
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
