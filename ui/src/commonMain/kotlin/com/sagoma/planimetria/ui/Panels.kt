package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.MergeRequest
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomMerge
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.WallCollision
import com.sagoma.planimetria.model.WallCut
import androidx.compose.material3.FilterChip
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.layout.size
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.Edition
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.WindowModel
import androidx.compose.ui.graphics.Color

/** Nome breve del tipo di scala (schede di scelta e titolo). */
internal fun stairKindName(k: StairKind): String = when (k) {
    StairKind.Straight -> "Dritta"
    StairKind.StraightLanding -> "Dritta con pianerottolo"
    StairKind.LTurn -> "A L"
    StairKind.LWinder -> "A L a ventaglio"
    StairKind.UTurn -> "A U"
    StairKind.Spiral -> "A chiocciola"
}

/** Titolo dell'oggetto selezionato, mostrato nella striscia "Info". */
fun selectionTitle(selection: Selection, plan: FloorPlan, levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT, planBelow: FloorPlan? = null): String {
    if (selection is Selection.StairWell) {
        val s = planBelow?.stair(selection.stairId) ?: return "Vano scala"
        return "Vano scala · " + if (s.wellRailing == StairRailing.None) "senza ringhiera" else "ringhiera in ${s.wellRailing.label.lowercase()}"
    }
    if (selection is Selection.Column) {
        val c = plan.column(selection.columnId) ?: return ""
        val h = c.height?.let { " · h ${formatCm(it)}" }.orEmpty()
        return if (c.shape == ColumnShape.Round) "Colonna rotonda · Ø ${formatCm(c.width)} cm$h"
        else "Colonna quadrata · ${formatCm(c.width)} × ${formatCm(c.depth)} cm$h"
    }
    if (selection is Selection.Beam) {
        val b = plan.beam(selection.beamId) ?: return ""
        return "Trave · ${formatCm(b.length)} cm"
    }
    if (selection is Selection.FreeWall) {
        val w = plan.freeWall(selection.wallId) ?: return ""
        return "Muro singolo · ${formatCm(w.length)} cm"
    }
    if (selection is Selection.Furniture) {
        val f = plan.furniture(selection.furnitureId) ?: return ""
        val name = com.sagoma.planimetria.model.FurnitureCatalog.item(f.model)?.label ?: "Arredo"
        return "$name · ${formatCm(f.width)} × ${formatCm(f.depth)} cm"
    }
    if (selection is Selection.Stair) {
        val s = plan.stair(selection.stairId) ?: return ""
        return "Scala ${stairKindName(s.kind).lowercase().replace(" l", " L").replace(" u", " U")} · ${Stairs.risersOf(s, levelHeight)} alzate"
    }
    if (selection is Selection.Dimension) {
        val d = plan.dimension(selection.dimensionId) ?: return ""
        return "Quota · ${com.sagoma.planimetria.model.Dimension.formatLength(d.length)} cm"
    }
    if (selection is Selection.Annotation) {
        val t = plan.annotation(selection.annotationId) ?: return ""
        return "Testo · «${t.text.lineSequence().first().take(30)}»"
    }
    val room = plan.room(selection.roomId) ?: return ""
    return when (selection) {
        is Selection.Wall -> "Muro ${selection.index + 1} · ${formatCm(interiorLengths(room)[selection.index])} cm · ${room.name}"
        is Selection.Opening -> room.opening(selection.openingId)?.let { "${openingLabel(it)} · ${room.name}" } ?: room.name
        is Selection.Fixture -> room.fixture(selection.fixtureId)?.let { "${it.kind.label} · ${room.name}" } ?: room.name
        is Selection.Stair, is Selection.StairWell, is Selection.Column, is Selection.Beam, is Selection.FreeWall, is Selection.Furniture,
        is Selection.Dimension, is Selection.Annotation -> ""
    }
}

/**
 * Caratteristiche dell'oggetto selezionato (§10), dentro la tendina "Info". Si aggiornano in tempo
 * reale mentre l'oggetto viene trascinato.
 */
@Composable
fun SelectionPanel(
    selection: Selection,
    plan: FloorPlan,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT,
    planBelow: FloorPlan? = null,
    levelHeightBelow: Double = levelHeight,
) {
    // Compatto: su telefono deve lasciare visibile gran parte della pianta.
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (selection) {
            is Selection.Wall -> WallPanel(selection, plan, vm)
            is Selection.Opening -> OpeningPanel(selection, plan, vm)
            is Selection.Fixture -> FixturePanel(selection, plan, vm)
            is Selection.Stair -> StairPanel(selection, plan, vm, levelHeight)
            is Selection.Column -> ColumnPanel(selection, plan, vm, levelHeight)
            is Selection.Beam -> BeamPanel(selection, plan, vm, levelHeight)
            is Selection.FreeWall -> FreeWallPanel(selection, plan, vm, levelHeight)
            is Selection.Furniture -> FurniturePanel(selection, plan, vm, levelHeight)
            is Selection.Dimension -> DimensionPanel(selection, plan, vm)
            is Selection.Annotation -> TextNotePanel(selection, plan, vm)
            is Selection.StairWell -> planBelow?.stair(selection.stairId)?.let { s ->
                Text(
                    "Qui arriva una scala dal piano di sotto. Attorno al vuoto puoi mettere una ringhiera: resta libero il lato da cui si esce dalla scala e quello contro i muri.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
                WellRailingPicker(s, levelHeightBelow) { vm.updateStairBelow(it) }
            }
        }
    }
}

/** Colonna: forma, misure, rotazione (quadrata) e altezza fino al soffitto. */
@Composable
private fun ColumnPanel(sel: Selection.Column, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val c = plan.column(sel.columnId) ?: return
    fun update(g: (Column) -> Column) = vm.updateColumn(Structure.snapped(plan, g(c)))
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeColumn(c.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    ModelPicker("Forma", ColumnShape.entries, c.shape, { it.label }, { v -> update { it.copy(shape = v) } }) { columnPreview(it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.shape == ColumnShape.Round) {
            NumberField("Diametro", c.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
        } else {
            NumberField("Larghezza", c.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
            NumberField("Profondità", c.depth, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(depth = v) } }
            NumberField("Rotazione", c.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = v % 360) } }
        }
    }
    // Altezza: fino al soffitto oppure più bassa (mezza colonna, pilastrino, base).
    val ceiling = Structure.ceilingAt(plan, c.center, levelHeight)
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Altezza", Structure.columnHeight(plan, c, levelHeight), Modifier.weight(1f)) { v ->
            update { it.copy(height = if (v >= ceiling - 0.5) null else v) }
        }
        if (c.height != null) TextButton(onClick = { update { it.copy(height = null) } }) { Text("Fino al soffitto\n(${formatCm(ceiling)} cm)") }
    }
    Text(
        (if (c.height == null) "Va dal pavimento al soffitto. " else "Più bassa del soffitto (${formatCm(ceiling)} cm). ") +
            "Trascinala: vicino a un muro ci si accosta e diventa un pilastro che sporge dal muro.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Muro singolo: lunghezza, spessore, altezza (fino al soffitto o più basso), colore. */
@Composable
private fun FreeWallPanel(sel: Selection.FreeWall, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val w = plan.freeWall(sel.wallId) ?: return
    fun update(g: (FreeWall) -> FreeWall) = vm.updateFreeWall(g(w))
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeFreeWall(w.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    PhaseChoice(w.phase, PhaseLabels.wall) { vm.setFreeWallPhase(w.id, it) }
    val ceiling = Structure.ceilingAt(plan, w.mid, levelHeight)
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Lunghezza", w.length, Modifier.weight(1f)) { v ->
            // Si allunga o si accorcia dalla fine, tenendo ferma la partenza.
            if (v >= 10 && w.length > 1e-6) update { it.copy(end = it.start + (it.end - it.start).normalized() * v) }
        }
        NumberField("Spessore", w.thickness, Modifier.weight(1f)) { v -> if (v >= 3) update { it.copy(thickness = v) } }
        NumberField("Altezza", Structure.freeWallHeight(plan, w, levelHeight), Modifier.weight(1f)) { v ->
            update { it.copy(height = if (v >= ceiling - 0.5) null else v) }
        }
    }
    if (w.height != null) TextButton(onClick = { update { it.copy(height = null) } }) { Text("Fino al soffitto (${formatCm(ceiling)} cm)") }
    if (Edition.isPro) FinishPicker("Finitura (sui due lati)", w.finishOrPaint, { vm.setFreeWallFinish(w.id, it) })
    else ColorPicker("Colore", WallPaint.entries.map { it.label to it.argb }, w.paint.ordinal, { k -> update { it.copy(paint = WallPaint.entries[k], finish = null) } })
    Text(
        "Trascinalo per spostarlo, i pallini alle estremità per allungarlo o girarlo: si aggancia alle facce degli altri muri, " +
            "alle estremità di altri muri singoli e si raddrizza a 90°.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Arredo: misure, rotazione e altezza da terra; si trascina per spostarlo, la maniglia lo ruota. */
@Composable
private fun FurniturePanel(sel: Selection.Furniture, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val f = plan.furniture(sel.furnitureId) ?: return
    fun update(g: (com.sagoma.planimetria.model.Furniture) -> com.sagoma.planimetria.model.Furniture) = vm.updateFurniture(g(f))
    PanelActions {
        TextButton(onClick = { vm.rotateFurniture(f.id) }) { Text("↻ 90°") }
        // Ribalta sinistra ↔ destra restando al suo posto (divano angolare con la penisola dall'altra parte).
        TextButton(onClick = { update { it.copy(mirrored = !it.mirrored) } }) { Text("⇆ Specchia") }
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeFurniture(f.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Larghezza", f.width, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(width = v) } }
        NumberField("Profondità", f.depth, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(depth = v) } }
        NumberField("Altezza", f.height, Modifier.weight(1f)) { v -> if (v >= 1) update { it.copy(height = v) } }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Da terra", f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
        NumberField("Rotazione", f.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = ((v % 360) + 360) % 360) } }
    }
    val ceiling = Structure.ceilingAt(plan, f.center, levelHeight)
    if (f.elevation + f.height > ceiling + 0.5) {
        Text(
            "Arriva a ${formatCm(f.elevation + f.height)} cm: più in alto del soffitto (${formatCm(ceiling)} cm).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    Text(
        "Trascinalo per spostarlo: si accosta ai muri e agli altri mobili. Il pallino davanti lo fa girare (si raddrizza a 90°). " +
            "Il modello 3D si adatta alle misure scritte qui.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** Trave: larghezza e quanto sporge sotto il soffitto; la lunghezza si cambia trascinando le estremità. */
@Composable
private fun BeamPanel(sel: Selection.Beam, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val b = plan.beam(sel.beamId) ?: return
    fun update(g: (Beam) -> Beam) = vm.updateBeam(g(b))
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeBeam(b.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Larghezza", b.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
        NumberField("Sporge sotto", b.depth, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(depth = v) } }
        NumberField("Lunghezza", b.length, Modifier.weight(1f)) { v ->
            // Si allunga o si accorcia dalla fine, tenendo ferma la partenza.
            if (v >= 20 && b.length > 1e-6) update { it.copy(end = it.start + (it.end - it.start).normalized() * v) }
        }
    }
    val ceiling = Structure.ceilingAt(plan, b.mid, levelHeight)
    Text(
        "Sotto la trave restano ${formatCm(ceiling - b.depth)} cm dal pavimento. Trascinala per spostarla, " +
            "i pallini alle estremità per allungarla: vicino a un muro si appoggiano alla sua faccia.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/**
 * Pavimento del piano di sopra sopra i primi gradini (e il pianerottolo): il vuoto nel solaio si accorcia.
 * Mostra l'altezza libera sopra l'ultimo gradino coperto, con l'avviso se è meno di 2 m.
 */
@Composable
private fun CoveredStepsPicker(s: Stair, rise: Double, onChange: (Stair) -> Unit) {
    val l = remember(s, rise) { Stairs.layout(s, rise) }
    val max = Stairs.maxCovered(l)
    if (max == 0) return
    val k = Stairs.coveredOf(s, l)
    val landing = l.steps.indexOfFirst { it.landing }.takeIf { it in 0 until max }
    Text("Pavimento del piano di sopra sopra la scala", style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { onChange(s.copy(coveredSteps = k - 1)) }, enabled = k > 0, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("−") }
        Text(
            when {
                k == 0 -> "Nessun gradino coperto: vuoto sopra tutta la scala"
                landing != null && k == landing + 1 -> "Coperti i primi $k gradini, fino al pianerottolo compreso"
                else -> "Coperti i primi $k ${if (k == 1) "gradino" else "gradini"}"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onChange(s.copy(coveredSteps = k + 1)) }, enabled = k < max, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("+") }
    }
    if (landing != null && k != landing + 1) {
        TextButton(onClick = { onChange(s.copy(coveredSteps = landing + 1)) }) { Text("Copri fino al pianerottolo") }
    }
    Stairs.headroom(s, rise, Floor.SLAB)?.let { h ->
        val low = h < Stairs.MIN_HEADROOM
        Text(
            "Altezza libera sopra l'ultimo gradino coperto: ${formatCm(h)} cm" +
                if (low) " — troppo bassa, servono almeno ${formatCm(Stairs.MIN_HEADROOM)} cm per passare senza chinarsi" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Al piano di sopra: la ringhiera della scala continua oltre il pavimento o è tagliata a filo, e la ringhiera
 * attorno al vano scala. Si sceglie sia dalla scala sia dal suo vano al piano di sopra.
 */
@Composable
private fun WellRailingPicker(s: Stair, rise: Double, onChange: (Stair) -> Unit) {
    CoveredStepsPicker(s, rise, onChange)
    if (s.railing != StairRailing.None) {
        Choice(
            "Ringhiera della scala al piano di sopra",
            listOf("Continua sopra il pavimento", "Tagliata a filo del pavimento"),
            if (s.railingAboveFloor) 0 else 1,
            Modifier.fillMaxWidth(),
        ) { k -> onChange(s.copy(railingAboveFloor = k == 0)) }
    }
    ModelPicker("Ringhiera sul vano scala", StairRailing.entries, s.wellRailing, { it.label }, { v -> onChange(s.copy(wellRailing = v)) }) {
        wellRailingPreview(it)
    }
}

/**
 * Caratteristiche di una scala: tipo, verso, larghezza, pedata (o diametro della chiocciola), rotazione.
 * Numero e altezza dei gradini vengono dall'interpiano e si mostrano soltanto.
 */
@Composable
private fun StairPanel(sel: Selection.Stair, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val s = plan.stair(sel.stairId) ?: return
    val layout = Stairs.layout(s, levelHeight)
    fun update(g: (Stair) -> Stair) = vm.updateStair(g(s))
    PanelActions {
        TextButton(onClick = { vm.rotateStair(s.id) }) { Text("↻ Ruota di 90°") }
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeStair(s.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    ModelPicker(
        "Tipo",
        listOf(StairKind.Straight, StairKind.StraightLanding, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.Spiral),
        s.kind,
        ::stairKindName,
        { k -> update { it.copy(kind = k) } },
    ) { stairKindPreview(it) }
    if (s.kind != StairKind.Straight && s.kind != StairKind.StraightLanding) {
        Row(Modifier.padding(end = 8.dp)) {
            val label = if (s.kind == StairKind.Spiral) "Sale in senso" else "Gira verso"
            val options = if (s.kind == StairKind.Spiral) listOf("Orario", "Antiorario") else listOf("Destra", "Sinistra")
            Choice(label, options, if (s.turnLeft) 1 else 0, Modifier.weight(1f)) { k -> update { it.copy(turnLeft = k == 1) } }
        }
    }
    // Pendenza: scelta rapida (imposta alzate e pedata comoda) e, per chi vuole, alzate una per una.
    val n = layout.risers
    val presets = listOf("Comoda" to 16.0, "Normale" to 17.5, "Ripida" to 19.5, "Molto ripida" to 21.5)
    val preset = presets.indexOfFirst { (_, r) ->
        abs(layout.riser - r) < 0.9 && abs(s.tread - Stairs.withSlope(s, levelHeight, r).tread) < 0.6
    }
    Row(Modifier.padding(end = 8.dp)) {
        Choice("Pendenza", presets.map { it.first }, preset, Modifier.weight(1f)) { k ->
            update { Stairs.withSlope(it, levelHeight, presets[k].second) }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Stepper(
            "Alzate",
            "$n",
            onMinus = if (n > Stairs.MIN_RISERS) ({ update { it.copy(risers = n - 1) } }) else null,
            onPlus = if (n < Stairs.MAX_RISERS) ({ update { it.copy(risers = n + 1) } }) else null,
        )
        Text(
            "alte ${formatCm(layout.riser)} cm\ninclinazione ${Stairs.slopeDegrees(layout.riser, s.tread).roundToInt()}°",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    if (Stairs.effectiveKind(s, n).let { it != StairKind.Straight && it != StairKind.Spiral }) {
        val t1 = Stairs.firstFlightOf(s, n)
        val t2 = Stairs.secondFlightOf(s, n)
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Stepper(
                if (Stairs.effectiveKind(s, n) == StairKind.LWinder) "Gradini prima della svolta" else "Gradini prima del pianerottolo",
                "$t1",
                onMinus = if (t1 > 1) ({ update { it.copy(firstFlight = t1 - 1) } }) else null,
                onPlus = if (t2 > 1) ({ update { it.copy(firstFlight = t1 + 1) } }) else null,
            )
            Text("dopo: $t2", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.kind == StairKind.Spiral) {
            NumberField("Diametro", s.diameter, Modifier.weight(1f)) { v -> if (v >= 100) update { it.copy(diameter = v) } }
        } else {
            NumberField("Larghezza", s.width, Modifier.weight(1f)) { v -> if (v >= 50) update { it.copy(width = v) } }
            NumberField("Pedata", s.tread, Modifier.weight(1f)) { v -> if (v >= 15) update { it.copy(tread = v) } }
        }
        NumberField("Rotazione", s.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = v % 360) } }
    }
    // Aspetto: struttura, materiale dei gradini, ringhiera.
    val stepColor = Color(s.material.argb)
    if (s.kind != StairKind.Spiral) {
        ModelPicker("Struttura", StairStructure.entries, s.structure, { it.label }, { v -> update { it.copy(structure = v) } }) {
            structurePreview(it, stepColor)
        }
    }
    ColorPicker("Gradini", StairMaterial.entries.map { it.label to it.argb }, s.material.ordinal, { k -> update { it.copy(material = StairMaterial.entries[k]) } })
    ModelPicker("Ringhiera", StairRailing.entries, s.railing, { it.label }, { v -> update { it.copy(railing = v) } }) {
        railingPreview(it, stepColor)
    }
    // La stessa ringhiera del vano si può scegliere anche da qui, senza salire al piano di sopra.
    WellRailingPicker(s, levelHeight) { vm.updateStair(it) }
    val b = layout.bounds
    Text(
        "Sale di ${formatCm(levelHeight)} cm (l'interpiano di questo piano). Più alzate: gradini più bassi e scala più lunga. " +
            "Ingombro ${formatCm(b.width)} × ${formatCm(b.height)} cm. Al piano di sopra compare il vuoto della scala.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
    if (layout.riser > 20.0) {
        Text(
            "Scala molto ripida: gradini più alti di 20 cm vanno bene per un soppalco o una scala di servizio, meno per l'uso di tutti i giorni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    // Regola di comodità: 2 alzate + 1 pedata tra 62 e 64 cm.
    val blondel = 2 * layout.riser + s.tread
    if (s.kind != StairKind.Spiral && (blondel < 60 || blondel > 66)) {
        Text(
            "Gradini poco comodi: due alzate più una pedata fanno ${formatCm(blondel)} cm, di solito si sta tra 62 e 64 cm. " +
                "Prova una pedata di circa ${formatCm(63 - 2 * layout.riser)} cm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun WallPanel(sel: Selection.Wall, plan: FloorPlan, vm: EditorViewModel) {
    val room = plan.room(sel.roomId) ?: return
    val custom = room.wallHeights[sel.index]
    var text by remember(sel, custom, room.ceilingHeight) { mutableStateOf(formatCm(room.wallHeight(sel.index))) }
    // Muro già eliminato: resta solo il confine tratteggiato; lo si può rimettere.
    if (room.isRemoved(sel.index)) {
        Text(
            if (room.outdoor) "Lato aperto: qui il parapetto è stato tolto." else "Muro eliminato: da questo lato ${room.name} è aperta. La linea tratteggiata ne segna il confine.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        OutlinedButton(onClick = { vm.setWallRemoved(room.id, sel.index, removed = false) }, modifier = Modifier.padding(end = 8.dp).fillMaxWidth()) {
            Text(if (room.outdoor) "↺ Rimetti il parapetto" else "↺ Rimetti il muro")
        }
        return
    }
    // Dal muro si duplica la stanza (o il balcone) a cui appartiene; oppure si elimina il muro (lato aperto).
    PanelActions {
        DuplicateButton(vm, if (room.outdoor) "⧉ Duplica ${room.type.defaultName.lowercase()}" else "⧉ Duplica stanza")
        TextButton(onClick = { vm.setWallRemoved(room.id, sel.index, removed = true) }) {
            Text(if (room.outdoor) "Togli parapetto" else "🗑 Elimina muro", color = MaterialTheme.colorScheme.error)
        }
    }
    // Stato di fatto / progetto del muro (pratiche edilizie): esistente, da demolire (giallo), nuovo (rosso).
    if (!room.outdoor) PhaseChoice(room.phaseOf(sel.index), PhaseLabels.wall) { vm.setWallPhase(room.id, sel.index, it) }
    // Lato di un balcone: il parapetto è uguale su tutti i lati e si sceglie da Info del balcone.
    if (room.outdoor) Text(
        "Parapetto: ${room.parapet.label.lowercase()}, ${formatCm(room.parapetHeight)} cm (si cambia da Info di ${room.name}). Dove il balcone tocca la casa resta il muro della casa.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(end = 8.dp),
    ) else Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectAllTextField(
            value = text,
            onValueChange = {
                text = it
                parsePositive(it)?.let { h -> vm.setWallHeight(sel.roomId, sel.index, h) }
            },
            label = "Altezza muro",
            numeric = true,
            suffix = "cm",
            isError = parsePositive(text) == null,
            modifier = Modifier.weight(1f),
        )
        if (custom != null) {
            TextButton(onClick = { vm.setWallHeight(sel.roomId, sel.index, null) }) { Text("Standard\n(${formatCm(room.ceilingHeight)} cm)") }
        }
    }
    // Colore di questa sola parete (lato verso la stanza), diverso da quello delle altre.
    if (!room.outdoor && Edition.isPro) {
        val own = room.hasOwnFinish(sel.index)
        FinishPicker(
            "Finitura di questa parete" + if (!own) " (come la stanza)" else "",
            room.finishOf(sel.index),
            { vm.setWallFinish(room.id, sel.index, it) },
        )
        if (own) TextButton(onClick = { vm.setWallFinish(room.id, sel.index, null) }) { Text("Come le altre pareti della stanza") }
    } else if (!room.outdoor) {
        val own = room.wallPaints[sel.index]
        ColorPicker(
            "Colore di questa parete" + if (own == null) " (come la stanza)" else "",
            WallPaint.entries.map { it.label to it.argb },
            room.paintOf(sel.index).ordinal,
            { k -> vm.setWallPaint(room.id, sel.index, WallPaint.entries[k]) },
        )
        if (own != null) {
            TextButton(onClick = { vm.setWallPaint(room.id, sel.index, null) }) {
                Text("Come le altre pareti (${room.wallPaint.label.lowercase()})")
            }
        }
    }
    // Spessore di questo muro (portante, tramezzo…) e forma libera: un angolo in più o in meno.
    if (!room.outdoor) {
        val own = room.wallThicknesses[sel.index]
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Spessore muro", room.thicknessOf(sel.index), Modifier.weight(1f)) { v -> vm.setWallThickness(room.id, sel.index, v) }
            if (own != null) TextButton(onClick = { vm.setWallThickness(room.id, sel.index, null) }) { Text("Come gli altri\n(${formatCm(room.wallThickness)} cm)") }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.splitWall(room.id, sel.index) }, modifier = Modifier.weight(1f)) { Text("＋ Angolo a metà muro", maxLines = 2) }
        if (room.wallCount > 3) OutlinedButton(onClick = { vm.mergeWithNext(room.id, sel.index) }, modifier = Modifier.weight(1f)) { Text("Unisci al muro dopo", maxLines = 2) }
    }
    Text(
        "Con \"Angolo a metà muro\" il muro si divide in due: trascina il nuovo angolo per fare pareti inclinate, rientranze o " +
            "forme qualsiasi (vicino a 90° si raddrizza da solo).",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
    WallCutSection(sel, room, vm)
    // Oggetti su questo muro che non ci stanno sotto la parete.
    for (c in Collisions.of(plan).filter { it.roomId == room.id && it.wallIndex == sel.index }) {
        val name = c.openingId?.let { id -> room.opening(id)?.let(::openingLabel) } ?: c.fixtureId?.let { id -> room.fixture(id)?.kind?.label } ?: "Oggetto"
        CollisionWarning(c, name)
    }
    // Muro in comune con altre stanze: ogni tratto in comune si può eliminare per farne un unico ambiente.
    val partners = remember(plan, sel) { RoomMerge.mergeablePartners(plan, sel.roomId, sel.index) }
    for (partner in partners) {
        OutlinedButton(
            onClick = { vm.requestMergeWall(MergeRequest(sel.roomId, sel.index, partner.id)) },
            modifier = Modifier.padding(end = 8.dp).fillMaxWidth(),
        ) {
            Text("Elimina muro · unisci con ${partner.name}", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Parete sotto il tetto: taglio diagonale della parte alta. La parete scende verso il muro perpendicolare
 * più basso fino alla sua altezza, a partire dal punto scelto (distanza da quel muro, oppure trascinando
 * il pallino sulla pianta). Serve un muro perpendicolare più basso: altrimenti si spiega come fare.
 */
@Composable
private fun WallCutSection(sel: Selection.Wall, room: Room, vm: EditorViewModel) {
    if (room.outdoor) return // all'aperto non c'è sottotetto
    val i = sel.index
    val cut = room.wallCuts[i]
    val options = Ceilings.cutOptions(room, i)
    fun lowName(toward: Boolean) = Ceilings.lowWall(room, i, WallCut(toward, 0.0)).let { "muro ${it + 1} (${formatCm(room.wallHeight(it))} cm)" }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = cut != null,
            enabled = cut != null || options.isNotEmpty(),
            onClick = { vm.setWallCut(room.id, i, on = cut == null) },
            label = { Text("Taglio diagonale (sottotetto)") },
        )
    }
    if (cut == null) {
        if (options.isEmpty()) {
            Text(
                "Per il taglio serve un muro perpendicolare più basso (es. 200 cm): abbassa prima il muro accanto verso cui scende il tetto.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        return
    }
    val low = Ceilings.lowWall(room, i, cut)
    if (options.size > 1) {
        Row(Modifier.padding(end = 8.dp)) {
            Choice("Scende verso", options.map { lowName(it).substringBefore(" (").replaceFirstChar(Char::uppercase) }, options.indexOf(cut.towardEnd).coerceAtLeast(0), Modifier.weight(1f)) { k ->
                vm.setWallCut(room.id, i, on = true, towardEnd = options[k])
            }
        }
    }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Inizia a scendere a", cut.start, Modifier.weight(1f)) { v -> vm.setWallCutStart(room.id, i, v) }
        Text("dal ${lowName(cut.towardEnd).substringBefore(" (")}", style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        if (Ceilings.effectiveCut(room, i) != null)
            "La parete è alta ${formatCm(room.wallHeight(i))} cm fino al punto indicato (il pallino sulla pianta, che puoi trascinare), " +
                "poi scende fino a ${formatCm(room.wallHeight(low))} cm, l'altezza del ${lowName(cut.towardEnd).substringBefore(" (")}. Anche il soffitto scende così."
        else "Il ${lowName(cut.towardEnd).substringBefore(" (")} non è più basso di questa parete: abbassalo per vedere il taglio.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/**
 * Avviso in rosso: l'oggetto arriva più in alto della parete (di solito sotto un taglio diagonale).
 * `name` si scrive quando l'avviso compare nel riquadro del muro, dove gli oggetti possono essere più d'uno.
 */
@Composable
private fun CollisionWarning(c: WallCollision, name: String? = null) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.padding(end = 8.dp).fillMaxWidth(),
    ) {
        Text(
            (name?.let { "⚠ $it: " } ?: "⚠ ") + "non ci sta sotto la parete. Arriva a ${formatCm(c.objectTop)} cm, " +
                "ma lì la parete è alta ${formatCm(c.wallTop)} cm (taglio del sottotetto o muro più basso). Riduci l'altezza o sposta l'oggetto lungo il muro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Conferma dell'eliminazione di un muro in comune: le due stanze diventano un unico ambiente. */
@Composable
fun MergeWallDialog(req: MergeRequest, plan: FloorPlan, vm: EditorViewModel) {
    val room = plan.room(req.roomId) ?: return
    val partner = plan.room(req.otherRoomId) ?: return
    val result = RoomMerge.merge(plan, req.roomId, req.index, req.otherRoomId) ?: return
    val lost = result.removedOpenings + result.removedFixtures
    AlertDialog(
        onDismissRequest = vm::cancelMergeWall,
        title = { Text("Unire ${room.name} e ${partner.name}?") },
        text = {
            Text(
                "Il muro in comune viene eliminato e le due stanze diventano un unico ambiente, " +
                    "con nome, tipo e altezza soffitto di ${room.name}." +
                    when (lost) {
                        0 -> ""
                        1 -> " Viene rimosso anche l'elemento (porta, finestra o impianto) che si trova sul muro."
                        else -> " Vengono rimossi anche i $lost elementi (porte, finestre, impianti) che si trovano sul muro."
                    } + " Potrai tornare indietro con ↶ Annulla in alto.",
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.mergeWall(req) }) { Text("Elimina muro", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = vm::cancelMergeWall) { Text("Mantieni") } },
    )
}

/** Caratteristiche di porta / finestra / apertura senza porta: solo i campi pertinenti al tipo (§5). */
@Composable
private fun OpeningPanel(sel: Selection.Opening, plan: FloorPlan, vm: EditorViewModel) {
    val room = plan.room(sel.roomId) ?: return
    val o = room.opening(sel.openingId) ?: return
    fun update(f: (Opening) -> Opening) = vm.updateOpening(room.id, f(o))

    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeOpening(room.id, o.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    Collisions.of(plan).firstOrNull { it.roomId == room.id && it.openingId == o.id }?.let { CollisionWarning(it) }
    PhaseChoice(o.phase, PhaseLabels.opening) { vm.setOpeningPhase(room.id, o.id, it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Larghezza", o.width, Modifier.weight(1f)) { v -> update { it.copy(width = v) } }
        NumberField("Altezza", o.height, Modifier.weight(1f)) { v -> update { it.copy(height = v) } }
        if (o.kind.hasSill) {
            NumberField("Da terra", o.sillHeight, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(sillHeight = v) } }
        }
    }
    // Porte, finestre e balconi: a battente o scorrevoli.
    if (o.kind.hasLeaves) {
        Row(Modifier.padding(end = 8.dp)) {
            Choice("Apertura", listOf("Battente", "Scorrevole"), if (o.sliding) 1 else 0, Modifier.weight(1f)) { i ->
                update { it.copy(sliding = i == 1) }
            }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (o.hasSideChoice) {
            // Stesso dato: a battente è il lato del cardine, da scorrevole il lato verso cui scorre l'anta.
            Choice(if (o.sliding) "Scorre verso" else "Cardine", listOf("Sinistra", "Destra"), if (o.hingeLeft) 0 else 1, Modifier.weight(1f)) { i ->
                update { it.copy(hingeLeft = i == 0) }
            }
        }
        if (o.swings) {
            Choice("Apre verso", listOf("Interno", "Esterno"), if (o.opensInward) 0 else 1, Modifier.weight(1f)) { i ->
                update { it.copy(opensInward = i == 0) }
            }
        }
        if (o.kind == OpeningKind.Passage) {
            Choice("Stile", PassageStyle.entries.map { it.label }, o.style.ordinal, Modifier.weight(1f)) { i ->
                update { it.copy(style = PassageStyle.entries[i]) }
            }
        }
    }
    // Aspetto: modello, oscuranti e colore (si vedono nel 3D).
    val color = Color(o.finish.argb)
    if (o.kind.glazed) {
        ModelPicker("Modello", WindowModel.entries, o.windowModel, { it.label }, { v -> update { it.copy(windowModel = v) } }) {
            windowPreview(it, Shading.None, color)
        }
        ModelPicker("Oscuranti", Shading.entries, o.shading, { it.label }, { v -> update { it.copy(shading = v) } }) {
            windowPreview(o.windowModel, it, color)
        }
    } else if (o.kind.hasLeaves) {
        ModelPicker("Modello", DoorModel.entries, o.doorModel, { it.label }, { v -> update { it.copy(doorModel = v) } }) {
            doorPreview(it, color)
        }
    }
    if (o.kind.hasLeaves) {
        ColorPicker(if (o.kind.glazed) "Colore infisso" else "Colore", Finish.entries.map { it.label to it.argb }, o.finish.ordinal, { k ->
            update { it.copy(color = Finish.entries[k]) }
        })
    }
}

/**
 * Caratteristiche di un impianto: calorifero (larghezza, altezza, da terra), presa e interruttore (da terra),
 * neon e striscia LED (lunghezza, rotazione); faretto, lampadario e plafoniera si spostano soltanto.
 */
@Composable
private fun FixturePanel(sel: Selection.Fixture, plan: FloorPlan, vm: EditorViewModel) {
    val room = plan.room(sel.roomId) ?: return
    val f = room.fixture(sel.fixtureId) ?: return
    fun update(g: (Fixture) -> Fixture) = vm.updateFixture(room.id, g(f))

    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeFixture(room.id, f.id) }) { Text("Rimuovi", color = MaterialTheme.colorScheme.error) }
    }
    Collisions.of(plan).firstOrNull { it.roomId == room.id && it.fixtureId == f.id }?.let { CollisionWarning(it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (f.kind) {
            FixtureKind.Radiator -> {
                NumberField("Larghezza", f.length, Modifier.weight(1f)) { v -> update { it.copy(length = v) } }
                NumberField("Altezza", f.height, Modifier.weight(1f)) { v -> update { it.copy(height = v) } }
                NumberField("Da terra", f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
            }
            FixtureKind.Chandelier -> Text(
                "Lampadario a soffitto: trascinalo per spostarlo.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FixtureKind.Outlet, FixtureKind.Switch, FixtureKind.WaterPoint ->
                NumberField("Da terra", f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
            FixtureKind.Neon, FixtureKind.LedStrip -> {
                NumberField("Lunghezza", f.length, Modifier.weight(1f)) { v -> update { it.copy(length = v) } }
                NumberField("Rotazione", f.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v ->
                    update { it.copy(rotation = v % 360) }
                }
            }
            else -> Text(
                "Luce a soffitto: trascinala per spostarla.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    when (f.kind) {
        FixtureKind.Radiator -> ModelPicker("Modello", RadiatorModel.entries, f.radiatorModel, { it.label }, { v ->
            // Lo scaldasalviette è alto e stretto: si parte da misure adatte.
            update {
                if (v == RadiatorModel.TowelRail && it.radiatorModel != v) it.copy(radiatorModel = v, length = 50.0, height = 120.0, elevation = 20.0)
                else it.copy(radiatorModel = v)
            }
        }) { radiatorPreview(it) }
        FixtureKind.Chandelier -> ModelPicker("Modello", LampModel.entries, f.lampModel, { it.label }, { v -> update { it.copy(lampModel = v) } }) {
            lampPreview(it)
        }
        else -> Unit
    }
}

/** Nome dell'apertura come lo si mostra: "Porta ad 1 anta", "Finestra 2 ante scorrevole"… */
internal fun openingLabel(o: Opening): String = if (o.sliding) "${o.kind.label} scorrevole" else o.kind.label

/** Campo numerico in cm che applica il valore a ogni modifica valida (il riquadro si aggiorna dal vivo). */
@Composable
private fun NumberField(
    label: String,
    value: Double,
    modifier: Modifier,
    allowZero: Boolean = false,
    suffix: String = "cm",
    onValue: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatCm(value)) }
    val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf { if (allowZero) it >= 0 else it > 0 }
    SelectAllTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> if (allowZero) v >= 0 else v > 0 }?.let(onValue)
        },
        label = label,
        numeric = true,
        suffix = suffix,
        isError = parsed == null,
        modifier = modifier,
    )
}

@Composable
internal fun Choice(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, opt ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {}, // niente spunta: lo spazio serve al testo
                ) { Text(opt, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
        }
    }
}

/** Numero intero con i pulsanti − e + (null = pulsante disattivato). */
@Composable
private fun Stepper(label: String, value: String, onMinus: (() -> Unit)?, onPlus: (() -> Unit)?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pad = androidx.compose.foundation.layout.PaddingValues(0.dp)
            OutlinedButton(onClick = { onMinus?.invoke() }, enabled = onMinus != null, contentPadding = pad, modifier = Modifier.size(44.dp)) { Text("−") }
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
            OutlinedButton(onClick = { onPlus?.invoke() }, enabled = onPlus != null, contentPadding = pad, modifier = Modifier.size(44.dp)) { Text("+") }
        }
    }
}

/** Azioni del riquadro (es. "Rimuovi"), allineate a destra: il titolo sta nella striscia "Info". */
@Composable
internal fun PanelActions(actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        actions()
    }
}

/** Nomi degli stati per muri e aperture (per un'apertura "nuova" vuol dire da aprire, "da demolire" da chiudere). */
internal object PhaseLabels {
    val wall = listOf("Esistente", "Da demolire", "Nuovo")
    val opening = listOf("Esistente", "Da chiudere", "Da aprire")
}

/** Scelta dello stato di fatto / progetto, con i colori della tavola comparativa. */
@Composable
internal fun PhaseChoice(phase: com.sagoma.planimetria.model.Phase, labels: List<String>, onChange: (com.sagoma.planimetria.model.Phase) -> Unit) {
    Choice(
        "Stato (pratiche edilizie: 🟡 demolizioni, 🔴 costruzioni)",
        labels,
        com.sagoma.planimetria.model.Phase.entries.indexOf(phase),
        Modifier.fillMaxWidth().padding(end = 8.dp),
    ) { onChange(com.sagoma.planimetria.model.Phase.entries[it]) }
}

/** "⧉ Duplica" dell'oggetto selezionato (o della stanza attiva): copia accanto, subito selezionata. Ctrl+D sul computer. */
@Composable
internal fun DuplicateButton(vm: EditorViewModel, label: String = "⧉ Duplica") {
    TextButton(onClick = { vm.duplicateSelected() }) { Text(label) }
}

/** Conferma eliminazione stanza, con il numero di aperture che verranno perse. */
@Composable
fun DeleteRoomDialog(room: Room, vm: EditorViewModel) {
    val n = room.openings.size
    AlertDialog(
        onDismissRequest = vm::cancelDeleteRoom,
        title = { Text("Eliminare ${room.name}?") },
        text = {
            Text(
                when (n) {
                    0 -> "La stanza verrà rimossa dalla pianta."
                    1 -> "Verranno rimosse la stanza e la sua apertura."
                    else -> "Verranno rimosse la stanza e le sue $n aperture."
                } + " Potrai recuperarla con ↶ Annulla in alto.",
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.deleteRoom(room.id) }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = vm::cancelDeleteRoom) { Text("Mantieni") } },
    )
}
