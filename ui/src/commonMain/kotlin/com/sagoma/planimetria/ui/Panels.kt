package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Box
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
import com.sagoma.planimetria.geometry.WallArc
import com.sagoma.planimetria.geometry.arcOf
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.WallCollision
import com.sagoma.planimetria.geometry.SpotAim
import com.sagoma.planimetria.model.WallCut
import androidx.compose.material3.FilterChip
import com.sagoma.planimetria.testi.T
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
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.WindowModel
import androidx.compose.ui.graphics.Color

/** Nome breve del tipo di scala (schede di scelta e titolo). */
internal fun stairKindName(k: StairKind): String = when (k) {
    StairKind.Straight -> T.pannelli.dritta
    StairKind.StraightLanding -> T.pannelli.drittaPianerottolo
    StairKind.LTurn -> T.comuni.formaL
    StairKind.LWinder -> T.pannelli.ventaglio
    StairKind.UTurn -> T.comuni.formaU
    StairKind.Spiral -> T.pannelli.chiocciola
}

/** Titolo dell'oggetto selezionato, mostrato nella striscia "Info". */
fun selectionTitle(selection: Selection, plan: FloorPlan, levelHeight: Double = Floor.DEFAULT_LEVEL_HEIGHT, planBelow: FloorPlan? = null): String {
    if (selection is Selection.StairWell) {
        val s = planBelow?.stair(selection.stairId) ?: return T.pannelli.vanoScala
        return T.pannelli.vanoScala2 + if (s.wellRailing == StairRailing.None) T.pannelli.senzaRinghiera else T.pannelli.ringhiera(T.lingua.minuscola(s.wellRailing.label))
    }
    if (selection is Selection.Column) {
        val c = plan.column(selection.columnId) ?: return ""
        val h = c.height?.let { " · h ${formatCm(it)}" }.orEmpty()
        return if (c.shape == ColumnShape.Round) T.pannelli.colonnaRotondaCm(formatCm(c.width), h)
        else T.pannelli.colonnaQuadrataCm(formatCm(c.width), formatCm(c.depth), h)
    }
    if (selection is Selection.Beam) {
        val b = plan.beam(selection.beamId) ?: return ""
        return T.pannelli.traveCm(formatCm(b.length))
    }
    if (selection is Selection.FreeWall) {
        val w = plan.freeWall(selection.wallId) ?: return ""
        return T.pannelli.muroSingoloCm(formatCm(w.length))
    }
    if (selection is Selection.Furniture) {
        val f = plan.furniture(selection.furnitureId) ?: return ""
        val name = com.sagoma.planimetria.model.FurnitureCatalog.item(f.model)?.nome ?: T.pannelli.arredo
        return T.pannelli.cm(name, formatCm(f.width), formatCm(f.depth))
    }
    if (selection is Selection.Stair) {
        val s = plan.stair(selection.stairId) ?: return ""
        return T.pannelli.scalaAlzate(Regex("\\b[lu]\\b").replace(T.lingua.minuscola(stairKindName(s.kind))) { it.value.uppercase() }, Stairs.risersOf(s, levelHeight))
    }
    if (selection is Selection.Dimension) {
        val d = plan.dimension(selection.dimensionId) ?: return ""
        return T.pannelli.quotaCm(com.sagoma.planimetria.model.Dimension.formatLength(d.length))
    }
    if (selection is Selection.Annotation) {
        val t = plan.annotation(selection.annotationId) ?: return ""
        return T.pannelli.testoTesto(t.text.lineSequence().first().take(30))
    }
    val room = plan.room(selection.roomId) ?: return ""
    return when (selection) {
        is Selection.Wall -> T.pannelli.muroCm(selection.index + 1, formatCm(interiorLengths(room)[selection.index]), room.name)
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
    // Compatto: su telefono deve lasciare visibile gran parte della pianta. Barra di scorrimento a destra.
    val scroll = rememberScrollState()
    Box(modifier) {
    Column(
        Modifier.verticalScroll(scroll).padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
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
                    T.pannelli.quiArrivaScalaPiano,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
                WellRailingPicker(s, levelHeightBelow) { vm.updateStairBelow(it) }
            }
        }
    }
    ScrollBar(scroll, Modifier.align(Alignment.TopEnd).padding(end = 2.dp))
    }
}

/** Colonna: forma, misure, rotazione (quadrata) e altezza fino al soffitto. */
@Composable
private fun ColumnPanel(sel: Selection.Column, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val c = plan.column(sel.columnId) ?: return
    fun update(g: (Column) -> Column) = vm.updateColumn(Structure.snapped(plan, g(c)))
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeColumn(c.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    ModelPicker(T.comuni.forma, ColumnShape.entries, c.shape, { it.label }, { v -> update { it.copy(shape = v) } }) { columnPreview(it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.shape == ColumnShape.Round) {
            NumberField(T.comuni.diametro, c.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
        } else {
            NumberField(T.comuni.larghezza, c.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
            NumberField(T.comuni.profondita, c.depth, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(depth = v) } }
            NumberField(T.comuni.rotazione, c.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = v % 360) } }
        }
    }
    // Altezza: fino al soffitto oppure più bassa (mezza colonna, pilastrino, base).
    val ceiling = Structure.ceilingAt(plan, c.center, levelHeight)
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(T.comuni.altezza, Structure.columnHeight(plan, c, levelHeight), Modifier.weight(1f)) { v ->
            update { it.copy(height = if (v >= ceiling - 0.5) null else v) }
        }
        if (c.height != null) TextButton(onClick = { update { it.copy(height = null) } }) { Text(T.pannelli.finoSoffittoCm(formatCm(ceiling))) }
    }
    Text(
        (if (c.height == null) T.pannelli.vaPavimentoSoffitto else T.pannelli.piuBassaSoffittoCm(formatCm(ceiling))) +
            T.pannelli.trascinalaVicinoMuroAccosta,
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
        TextButton(onClick = { vm.removeFreeWall(w.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    PhaseChoice(w.phase, PhaseLabels.wall) { vm.setFreeWallPhase(w.id, it) }
    val ceiling = Structure.ceilingAt(plan, w.mid, levelHeight)
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(T.comuni.lunghezza, w.length, Modifier.weight(1f)) { v ->
            // Si allunga o si accorcia dalla fine, tenendo ferma la partenza.
            if (v >= 10 && w.length > 1e-6) update { it.copy(end = it.start + (it.end - it.start).normalized() * v) }
        }
        NumberField(T.comuni.spessore, w.thickness, Modifier.weight(1f)) { v -> if (v >= 3) update { it.copy(thickness = v) } }
        NumberField(T.comuni.altezza, Structure.freeWallHeight(plan, w, levelHeight), Modifier.weight(1f)) { v ->
            update { it.copy(height = if (v >= ceiling - 0.5) null else v) }
        }
    }
    if (w.height != null) TextButton(onClick = { update { it.copy(height = null) } }) { Text(T.pannelli.finoSoffittoCm2(formatCm(ceiling))) }
    FinishPicker(T.pannelli.finituraSuiDueLati, w.finishOrPaint, { vm.setFreeWallFinish(w.id, it) })
    Text(
        T.pannelli.trascinaloSpostarloPalliniEstremita,
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
        TextButton(onClick = { update { it.copy(mirrored = !it.mirrored) } }) { Text(T.pannelli.specchia) }
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeFurniture(f.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(T.comuni.larghezza, f.width, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(width = v) } }
        NumberField(T.comuni.profondita, f.depth, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(depth = v) } }
        NumberField(T.comuni.altezza, f.height, Modifier.weight(1f)) { v -> if (v >= 1) update { it.copy(height = v) } }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(T.comuni.daTerra, f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
        NumberField(T.comuni.rotazione, f.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = ((v % 360) + 360) % 360) } }
    }
    val ceiling = Structure.ceilingAt(plan, f.center, levelHeight)
    if (f.elevation + f.height > ceiling + 0.5) {
        Text(
            T.pannelli.arrivaCmPiuAlto(formatCm(f.elevation + f.height), formatCm(ceiling)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    val kitchen = com.sagoma.planimetria.model.KitchenModules.parse(f.model)
    if (kitchen != null) KitchenFinishes(f.id, kitchen, plan, vm)
    val parametric = com.sagoma.planimetria.model.ParametricFurniture.parse(f.model)
    if (parametric != null) {
        val finishes = com.sagoma.planimetria.model.ParametricFurniture.finishes
        ColorPicker(T.comuni.finitura, finishes.map { it.label to it.argb }, finishes.indexOf(parametric.finish), { k ->
            update { it.copy(model = com.sagoma.planimetria.model.ParametricFurniture.model(parametric.copy(finish = finishes[k]))) }
        })
    }
    Text(
        if (kitchen != null) T.pannelli.trascinaloSpostarloAccostaMuri
        else if (parametric != null) T.pannelli.trascinaloSpostarloAccostaMuri2
        else T.pannelli.trascinaloSpostarloAccostaMuri3,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/**
 * Finiture di un modulo della cucina: colore delle ante e piano di lavoro (solo per le basi). Di solito si cambiano per tutta la
 * cucina insieme (un solo passo da annullare); spegnendo "Tutta la cucina" vale solo per questo modulo.
 */
@Composable
private fun KitchenFinishes(id: Long, spec: com.sagoma.planimetria.model.KitchenModules.Spec, plan: FloorPlan, vm: EditorViewModel) {
    val modules = plan.furniture.count { com.sagoma.planimetria.model.KitchenModules.isModule(it.model) }
    var all by remember { mutableStateOf(true) }
    val fronts = com.sagoma.planimetria.model.KitchenModules.Front.entries
    val tops = com.sagoma.planimetria.model.KitchenModules.Top.entries
    ColorPicker(T.pannelli.ante, fronts.map { it.label to it.argb }, fronts.indexOf(spec.front), { k -> vm.setKitchenFinish(id, fronts[k], null, all && modules > 1) })
    if (spec.kind.hasTop) {
        ColorPicker(T.pannelli.pianoLavoro, tops.map { it.label to it.argb }, tops.indexOf(spec.top), { k -> vm.setKitchenFinish(id, null, tops[k], all && modules > 1) })
    }
    if (modules > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
        androidx.compose.material3.Switch(checked = all, onCheckedChange = { all = it })
        Text(
            if (all) T.pannelli.tuttaCucinaModuli(modules) else T.pannelli.soloQuestoModulo,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Trave: larghezza e quanto sporge sotto il soffitto; la lunghezza si cambia trascinando le estremità. */
@Composable
private fun BeamPanel(sel: Selection.Beam, plan: FloorPlan, vm: EditorViewModel, levelHeight: Double) {
    val b = plan.beam(sel.beamId) ?: return
    fun update(g: (Beam) -> Beam) = vm.updateBeam(g(b))
    PanelActions {
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeBeam(b.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(T.comuni.larghezza, b.width, Modifier.weight(1f)) { v -> if (v >= Structure.MIN_SIZE) update { it.copy(width = v) } }
        NumberField(T.pannelli.sporgeSotto, b.depth, Modifier.weight(1f)) { v -> if (v >= 5) update { it.copy(depth = v) } }
        NumberField(T.comuni.lunghezza, b.length, Modifier.weight(1f)) { v ->
            // Si allunga o si accorcia dalla fine, tenendo ferma la partenza.
            if (v >= 20 && b.length > 1e-6) update { it.copy(end = it.start + (it.end - it.start).normalized() * v) }
        }
    }
    val ceiling = Structure.ceilingAt(plan, b.mid, levelHeight)
    Text(
        T.pannelli.sottoTraveRestanoCm(formatCm(ceiling - b.depth)),
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
    Text(T.pannelli.pavimentoPianoSopraSopra, style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { onChange(s.copy(coveredSteps = k - 1)) }, enabled = k > 0, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("−") }
        Text(
            when {
                k == 0 -> T.pannelli.nessunGradinoCopertoVuoto
                landing != null && k == landing + 1 -> T.pannelli.copertiPrimiGradiniFino(k)
                else -> T.pannelli.copertiPrimi(k, if (k == 1) T.pannelli.gradino else T.pannelli.gradini2)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onChange(s.copy(coveredSteps = k + 1)) }, enabled = k < max, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("+") }
    }
    if (landing != null && k != landing + 1) {
        TextButton(onClick = { onChange(s.copy(coveredSteps = landing + 1)) }) { Text(T.pannelli.copriFinoPianerottolo) }
    }
    Stairs.headroom(s, rise, Floor.SLAB)?.let { h ->
        val low = h < Stairs.MIN_HEADROOM
        Text(
            T.pannelli.altezzaLiberaSopraUltimo(formatCm(h)) +
                if (low) T.pannelli.troppoBassaServonoAlmeno(formatCm(Stairs.MIN_HEADROOM)) else "",
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
            T.pannelli.ringhieraScalaPianoSopra,
            listOf(T.pannelli.continuaSopraPavimento, T.pannelli.tagliataFiloPavimento),
            if (s.railingAboveFloor) 0 else 1,
            Modifier.fillMaxWidth(),
        ) { k -> onChange(s.copy(railingAboveFloor = k == 0)) }
    }
    ModelPicker(T.pannelli.ringhieraVanoScala, StairRailing.entries, s.wellRailing, { it.label }, { v -> onChange(s.copy(wellRailing = v)) }) {
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
        TextButton(onClick = { vm.rotateStair(s.id) }) { Text(T.pannelli.ruota90) }
        DuplicateButton(vm)
        TextButton(onClick = { vm.removeStair(s.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    ModelPicker(
        T.comuni.tipo,
        listOf(StairKind.Straight, StairKind.StraightLanding, StairKind.LTurn, StairKind.LWinder, StairKind.UTurn, StairKind.Spiral),
        s.kind,
        ::stairKindName,
        { k -> update { it.copy(kind = k) } },
    ) { stairKindPreview(it) }
    if (s.kind != StairKind.Straight && s.kind != StairKind.StraightLanding) {
        Row(Modifier.padding(end = 8.dp)) {
            val label = if (s.kind == StairKind.Spiral) T.pannelli.saleSenso else T.pannelli.giraVerso
            val options = if (s.kind == StairKind.Spiral) listOf(T.pannelli.orario, T.pannelli.antiorario) else listOf(T.comuni.destra, T.comuni.sinistra)
            Choice(label, options, if (s.turnLeft) 1 else 0, Modifier.weight(1f)) { k -> update { it.copy(turnLeft = k == 1) } }
        }
    }
    // Pendenza: scelta rapida (imposta alzate e pedata comoda) e, per chi vuole, alzate una per una.
    val n = layout.risers
    val presets = listOf(T.pannelli.comoda to 16.0, T.pannelli.normale to 17.5, T.pannelli.ripida to 19.5, T.pannelli.moltoRipida to 21.5)
    val preset = presets.indexOfFirst { (_, r) ->
        abs(layout.riser - r) < 0.9 && abs(s.tread - Stairs.withSlope(s, levelHeight, r).tread) < 0.6
    }
    Row(Modifier.padding(end = 8.dp)) {
        Choice(T.pannelli.pendenza, presets.map { it.first }, preset, Modifier.weight(1f)) { k ->
            update { Stairs.withSlope(it, levelHeight, presets[k].second) }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Stepper(
            T.pannelli.alzate,
            "$n",
            onMinus = if (n > Stairs.MIN_RISERS) ({ update { it.copy(risers = n - 1) } }) else null,
            onPlus = if (n < Stairs.MAX_RISERS) ({ update { it.copy(risers = n + 1) } }) else null,
        )
        Text(
            T.pannelli.alteCmInclinazione(formatCm(layout.riser), Stairs.slopeDegrees(layout.riser, s.tread).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    if (Stairs.effectiveKind(s, n).let { it != StairKind.Straight && it != StairKind.Spiral }) {
        val t1 = Stairs.firstFlightOf(s, n)
        val t2 = Stairs.secondFlightOf(s, n)
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Stepper(
                if (Stairs.effectiveKind(s, n) == StairKind.LWinder) T.pannelli.gradiniPrimaSvolta else T.pannelli.gradiniPrimaPianerottolo,
                "$t1",
                onMinus = if (t1 > 1) ({ update { it.copy(firstFlight = t1 - 1) } }) else null,
                onPlus = if (t2 > 1) ({ update { it.copy(firstFlight = t1 + 1) } }) else null,
            )
            Text(T.pannelli.dopo(t2), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.kind == StairKind.Spiral) {
            NumberField(T.comuni.diametro, s.diameter, Modifier.weight(1f)) { v -> if (v >= 100) update { it.copy(diameter = v) } }
        } else {
            NumberField(T.comuni.larghezza, s.width, Modifier.weight(1f)) { v -> if (v >= 50) update { it.copy(width = v) } }
            NumberField(T.pannelli.pedata, s.tread, Modifier.weight(1f)) { v -> if (v >= 15) update { it.copy(tread = v) } }
        }
        NumberField(T.comuni.rotazione, s.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v -> update { it.copy(rotation = v % 360) } }
    }
    // Aspetto: struttura, materiale dei gradini, ringhiera.
    val stepColor = Color(s.material.argb)
    if (s.kind != StairKind.Spiral) {
        ModelPicker(T.pannelli.struttura, StairStructure.entries, s.structure, { it.label }, { v -> update { it.copy(structure = v) } }) {
            structurePreview(it, stepColor)
        }
    }
    ColorPicker(T.pannelli.gradini, StairMaterial.entries.map { it.label to it.argb }, s.material.ordinal, { k -> update { it.copy(material = StairMaterial.entries[k]) } })
    ModelPicker(T.pannelli.ringhiera2, StairRailing.entries, s.railing, { it.label }, { v -> update { it.copy(railing = v) } }) {
        railingPreview(it, stepColor)
    }
    // La stessa ringhiera del vano si può scegliere anche da qui, senza salire al piano di sopra.
    WellRailingPicker(s, levelHeight) { vm.updateStair(it) }
    val b = layout.bounds
    Text(
        T.pannelli.saleCmInterpianoQuesto(formatCm(levelHeight), formatCm(b.width), formatCm(b.height)),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
    if (layout.riser > 20.0) {
        Text(
            T.pannelli.scalaMoltoRipidaGradini,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    // Regola di comodità: 2 alzate + 1 pedata tra 62 e 64 cm.
    val blondel = 2 * layout.riser + s.tread
    if (s.kind != StairKind.Spiral && (blondel < 60 || blondel > 66)) {
        Text(
            T.pannelli.gradiniPocoComodiDue(formatCm(blondel), formatCm(63 - 2 * layout.riser)),
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
            if (room.outdoor) T.pannelli.latoApertoQuiParapetto else T.pannelli.muroEliminatoQuestoLato(room.name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        OutlinedButton(onClick = { vm.setWallRemoved(room.id, sel.index, removed = false) }, modifier = Modifier.padding(end = 8.dp).fillMaxWidth()) {
            Text(if (room.outdoor) T.pannelli.rimettiParapetto else T.pannelli.rimettiMuro)
        }
        return
    }
    // Dal muro si duplica la stanza (o il balcone) a cui appartiene; oppure si elimina il muro (lato aperto).
    PanelActions {
        DuplicateButton(vm, if (room.outdoor) T.pannelli.duplica(T.lingua.minuscola(room.type.defaultName)) else T.pannelli.duplicaStanza)
        TextButton(onClick = { vm.setWallRemoved(room.id, sel.index, removed = true) }) {
            Text(if (room.outdoor) T.pannelli.togliParapetto else T.pannelli.eliminaMuro, color = MaterialTheme.colorScheme.error)
        }
    }
    // Stato di fatto / progetto del muro (pratiche edilizie): esistente, da demolire (giallo), nuovo (rosso).
    if (!room.outdoor) PhaseChoice(room.phaseOf(sel.index), PhaseLabels.wall) { vm.setWallPhase(room.id, sel.index, it) }
    // Lato di un balcone: il parapetto è uguale su tutti i lati e si sceglie da Info del balcone.
    if (room.outdoor) Text(
        T.pannelli.parapettoCmCambiaInfo(T.lingua.minuscola(room.parapet.label), formatCm(room.parapetHeight), room.name),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(end = 8.dp),
    ) else Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectAllTextField(
            value = text,
            onValueChange = {
                text = it
                parsePositive(it)?.let { h -> vm.setWallHeight(sel.roomId, sel.index, h) }
            },
            label = T.pannelli.altezzaMuro,
            numeric = true,
            suffix = "cm",
            isError = parsePositive(text) == null,
            modifier = Modifier.weight(1f),
        )
        if (custom != null) {
            TextButton(onClick = { vm.setWallHeight(sel.roomId, sel.index, null) }) { Text(T.pannelli.standardCm(formatCm(room.ceilingHeight))) }
        }
    }
    // Colore di questa sola parete (lato verso la stanza), diverso da quello delle altre.
    if (!room.outdoor) {
        val own = room.hasOwnFinish(sel.index)
        FinishPicker(
            T.pannelli.finituraQuestaParete + if (!own) T.pannelli.stanza else "",
            room.finishOf(sel.index),
            { vm.setWallFinish(room.id, sel.index, it) },
        )
        if (own) TextButton(onClick = { vm.setWallFinish(room.id, sel.index, null) }) { Text(T.pannelli.altreParetiStanza) }
    }
    // Spessore di questo muro (portante, tramezzo…) e forma libera: un angolo in più o in meno.
    if (!room.outdoor) {
        val own = room.wallThicknesses[sel.index]
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(T.pannelli.spessoreMuro, room.thicknessOf(sel.index), Modifier.weight(1f)) { v -> vm.setWallThickness(room.id, sel.index, v) }
            if (own != null) TextButton(onClick = { vm.setWallThickness(room.id, sel.index, null) }) { Text(T.pannelli.altriCm(formatCm(room.wallThickness))) }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.splitWall(room.id, sel.index) }, modifier = Modifier.weight(1f)) { Text(T.pannelli.angoloMetaMuro, maxLines = 2) }
        if (room.wallCount > 3) OutlinedButton(onClick = { vm.mergeWithNext(room.id, sel.index) }, modifier = Modifier.weight(1f)) { Text(T.pannelli.unisciMuroDopo, maxLines = 2) }
    }
    Text(
        T.pannelli.angoloMetaMuroMuro,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(end = 8.dp),
    )
    // Muro curvo: la curva si sceglie trascinando il pallino a metà muro, oppure con i numeri (freccia, raggio o angolo).
    if (!room.outdoor) {
        val arc = room.arcOf(sel.index)
        val chord = room.wallLength(sel.index)
        val sign = if ((arc?.bulge ?: 1.0) < 0) -1.0 else 1.0
        Text(T.pannelli.curvaMuro, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(T.pannelli.frecciaCurva, arc?.bulge ?: 0.0, Modifier.weight(1f), allowZero = true, allowNegative = true) { v ->
                vm.setWallBulge(room.id, sel.index, v)
            }
            if (arc != null) TextButton(onClick = { vm.setWallBulge(room.id, sel.index, 0.0) }) { Text(T.pannelli.raddrizzaMuro) }
        }
        if (arc != null) Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(T.pannelli.raggioCurva, kotlin.math.abs(arc.radius), Modifier.weight(1f)) { v ->
                vm.setWallBulge(room.id, sel.index, WallArc.bulgeForRadius(chord, sign * v))
            }
            NumberField(T.pannelli.angoloCurva, arc.degrees, Modifier.weight(1f), suffix = "°") { v ->
                vm.setWallBulge(room.id, sel.index, WallArc.bulgeForDegrees(chord, sign * v))
            }
        }
        Text(T.pannelli.curvaSuggerimento, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
    }
    WallCutSection(sel, room, vm)
    // Oggetti su questo muro che non ci stanno sotto la parete.
    for (c in Collisions.of(plan).filter { it.roomId == room.id && it.wallIndex == sel.index }) {
        val name = c.openingId?.let { id -> room.opening(id)?.let(::openingLabel) } ?: c.fixtureId?.let { id -> room.fixture(id)?.kind?.label } ?: T.pannelli.oggetto
        CollisionWarning(c, name)
    }
    // Muro in comune con altre stanze: ogni tratto in comune si può eliminare per farne un unico ambiente.
    val partners = remember(plan, sel) { RoomMerge.mergeablePartners(plan, sel.roomId, sel.index) }
    for (partner in partners) {
        OutlinedButton(
            onClick = { vm.requestMergeWall(MergeRequest(sel.roomId, sel.index, partner.id)) },
            modifier = Modifier.padding(end = 8.dp).fillMaxWidth(),
        ) {
            Text(T.pannelli.eliminaMuroUnisci(partner.name), color = MaterialTheme.colorScheme.error)
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
    fun lowName(toward: Boolean) = Ceilings.lowWall(room, i, WallCut(toward, 0.0)).let { T.pannelli.muroCm2(it + 1, formatCm(room.wallHeight(it))) }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = cut != null,
            enabled = cut != null || options.isNotEmpty(),
            onClick = { vm.setWallCut(room.id, i, on = cut == null) },
            label = { Text(T.pannelli.taglioDiagonaleSottotetto) },
        )
    }
    if (cut == null) {
        if (options.isEmpty()) {
            Text(
                T.pannelli.taglioServeMuroPerpendicolare,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        return
    }
    val low = Ceilings.lowWall(room, i, cut)
    if (options.size > 1) {
        Row(Modifier.padding(end = 8.dp)) {
            Choice(T.pannelli.scendeVerso, options.map { lowName(it).substringBefore(" (").replaceFirstChar(Char::uppercase) }, options.indexOf(cut.towardEnd).coerceAtLeast(0), Modifier.weight(1f)) { k ->
                vm.setWallCut(room.id, i, on = true, towardEnd = options[k])
            }
        }
    }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(T.pannelli.iniziaScendere, cut.start, Modifier.weight(1f)) { v -> vm.setWallCutStart(room.id, i, v) }
        Text(T.pannelli.dal(lowName(cut.towardEnd).substringBefore(" (")), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        if (Ceilings.effectiveCut(room, i) != null)
            T.pannelli.pareteAltaCmFino(formatCm(room.wallHeight(i)), formatCm(room.wallHeight(low)), lowName(cut.towardEnd).substringBefore(" ("))
        else T.pannelli.nonPiuBassoQuesta(lowName(cut.towardEnd).substringBefore(" (")),
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
            (name?.let { "⚠ $it: " } ?: "⚠ ") + T.pannelli.nonStaSottoParete(formatCm(c.objectTop), formatCm(c.wallTop)),
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
        title = { Text(T.pannelli.unire(room.name, partner.name)) },
        text = {
            Text(
                T.pannelli.muroComuneVieneEliminato(room.name) +
                    when (lost) {
                        0 -> ""
                        1 -> T.pannelli.vieneRimossoAncheElemento
                        else -> T.pannelli.vengonoRimossiAncheElementi(lost)
                    } + T.pannelli.potraiTornareIndietroAnnulla,
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.mergeWall(req) }) { Text(T.pannelli.eliminaMuro2, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = vm::cancelMergeWall) { Text(T.comuni.mantieni) } },
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
        TextButton(onClick = { vm.removeOpening(room.id, o.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    Collisions.of(plan).firstOrNull { it.roomId == room.id && it.openingId == o.id }?.let { CollisionWarning(it) }
    PhaseChoice(o.phase, PhaseLabels.opening) { vm.setOpeningPhase(room.id, o.id, it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(T.comuni.larghezza, o.width, Modifier.weight(1f)) { v -> update { it.copy(width = v) } }
        NumberField(T.comuni.altezza, o.height, Modifier.weight(1f)) { v -> update { it.copy(height = v) } }
        if (o.kind.hasSill) {
            NumberField(T.comuni.daTerra, o.sillHeight, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(sillHeight = v) } }
        }
    }
    // Porte, finestre e balconi: a battente o scorrevoli.
    if (o.kind.hasLeaves) {
        Row(Modifier.padding(end = 8.dp)) {
            Choice(T.pannelli.apertura, listOf(T.pannelli.battente, T.pannelli.scorrevole), if (o.sliding) 1 else 0, Modifier.weight(1f)) { i ->
                update { it.copy(sliding = i == 1) }
            }
        }
    }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (o.hasSideChoice) {
            // Stesso dato: a battente è il lato del cardine, da scorrevole il lato verso cui scorre l'anta.
            Choice(if (o.sliding) T.pannelli.scorreVerso else T.pannelli.cardine, listOf(T.comuni.sinistra, T.comuni.destra), if (o.hingeLeft) 0 else 1, Modifier.weight(1f)) { i ->
                update { it.copy(hingeLeft = i == 0) }
            }
        }
        if (o.swings) {
            Choice(T.pannelli.apreVerso, listOf(T.pannelli.interno, T.pannelli.esterno), if (o.opensInward) 0 else 1, Modifier.weight(1f)) { i ->
                update { it.copy(opensInward = i == 0) }
            }
        }
        if (o.kind == OpeningKind.Passage) {
            Choice(T.pannelli.stile, PassageStyle.entries.map { it.label }, o.style.ordinal, Modifier.weight(1f)) { i ->
                update { it.copy(style = PassageStyle.entries[i]) }
            }
        }
    }
    // Aspetto: modello, oscuranti e colore (si vedono nel 3D).
    val color = Color(o.finish.argb)
    if (o.kind.glazed) {
        ModelPicker(T.comuni.modello, WindowModel.entries, o.windowModel, { it.label }, { v -> update { it.copy(windowModel = v) } }) {
            windowPreview(it, Shading.None, color)
        }
        ModelPicker(T.pannelli.oscuranti, Shading.entries, o.shading, { it.label }, { v -> update { it.copy(shading = v) } }) {
            windowPreview(o.windowModel, it, color)
        }
        // Nei muri spessi: infisso verso l'esterno e lati del vano inclinati verso la stanza.
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(T.pannelli.infisso, FramePosition.entries.map { it.label }, o.frame.ordinal, Modifier.weight(2f)) { i ->
                update { it.copy(frame = FramePosition.entries[i]) }
            }
            NumberField(T.pannelli.sguincio, o.splay, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(splay = v.coerceIn(0.0, 60.0)) } }
        }
        if (o.splay > 0) Row(Modifier.padding(end = 8.dp)) {
            Choice(T.pannelli.latiVano, listOf(T.pannelli.inclinati, T.pannelli.arrotondati), if (o.splayRounded) 1 else 0, Modifier.weight(1f)) { i ->
                update { it.copy(splayRounded = i == 1) }
            }
        }
    } else if (o.kind.hasLeaves) {
        ModelPicker(T.comuni.modello, DoorModel.entries, o.doorModel, { it.label }, { v -> update { it.copy(doorModel = v) } }) {
            doorPreview(it, color)
        }
        // Porta d'ingresso: in pianta il segno dell'ingresso fuori dalla porta.
        Row(Modifier.padding(end = 8.dp)) {
            Choice(T.pannelli.tipoPorta, listOf(T.pannelli.portaInterna, T.pannelli.ingresso), if (o.entrance) 1 else 0, Modifier.weight(1f)) { i ->
                update { it.copy(entrance = i == 1) }
            }
        }
    }
    if (o.kind.hasLeaves) {
        ColorPicker(if (o.kind.glazed) T.pannelli.coloreInfisso else T.comuni.colore, Finish.entries.map { it.label to it.argb }, o.finish.ordinal, { k ->
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
        TextButton(onClick = { vm.removeFixture(room.id, f.id) }) { Text(T.comuni.rimuovi, color = MaterialTheme.colorScheme.error) }
    }
    Collisions.of(plan).firstOrNull { it.roomId == room.id && it.fixtureId == f.id }?.let { CollisionWarning(it) }
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (f.kind) {
            FixtureKind.Radiator -> {
                NumberField(T.comuni.larghezza, f.length, Modifier.weight(1f)) { v -> update { it.copy(length = v) } }
                NumberField(T.comuni.altezza, f.height, Modifier.weight(1f)) { v -> update { it.copy(height = v) } }
                NumberField(T.comuni.daTerra, f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
            }
            FixtureKind.Chandelier -> Text(
                T.pannelli.lampadarioSoffittoTrascinaloSpostarlo,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FixtureKind.Outlet, FixtureKind.Switch, FixtureKind.WaterPoint, FixtureKind.WallLight ->
                NumberField(T.comuni.daTerra, f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
            FixtureKind.WallSpot -> {
                NumberField(T.comuni.daTerra, f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
                // Valori di precisione: il fascio si orienta soprattutto trascinando la maniglia nella vista 3D.
                NumberField(T.pannelli.direzione, f.aimYaw, Modifier.weight(1f), allowZero = true, suffix = "°") { v ->
                    update { it.copy(aimYaw = SpotAim.normalizeYaw(v)) }
                }
                NumberField(T.pannelli.inclinazione, f.aimTilt, Modifier.weight(1f), allowZero = true, allowNegative = true, suffix = "°") { v ->
                    update { it.copy(aimTilt = SpotAim.clampTilt(v)) }
                }
            }
            FixtureKind.WallLedStrip -> {
                // Segue il muro: lunghezza e altezza da terra, nessuna rotazione.
                NumberField(T.comuni.lunghezza, f.length, Modifier.weight(1f)) { v -> update { it.copy(length = v) } }
                NumberField(T.comuni.daTerra, f.elevation, Modifier.weight(1f), allowZero = true) { v -> update { it.copy(elevation = v) } }
            }
            FixtureKind.Neon, FixtureKind.LedStrip -> {
                NumberField(T.comuni.lunghezza, f.length, Modifier.weight(1f)) { v -> update { it.copy(length = v) } }
                NumberField(T.comuni.rotazione, f.rotation, Modifier.weight(1f), allowZero = true, suffix = "°") { v ->
                    update { it.copy(rotation = v % 360) }
                }
            }
            else -> Text(
                T.pannelli.luceSoffittoTrascinalaSpostarla,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    when (f.kind) {
        FixtureKind.Radiator -> ModelPicker(T.comuni.modello, RadiatorModel.entries, f.radiatorModel, { it.label }, { v ->
            // Lo scaldasalviette è alto e stretto: si parte da misure adatte.
            update {
                if (v == RadiatorModel.TowelRail && it.radiatorModel != v) it.copy(radiatorModel = v, length = 50.0, height = 120.0, elevation = 20.0)
                else it.copy(radiatorModel = v)
            }
        }) { radiatorPreview(it) }
        FixtureKind.WallSpot -> Text(
            T.pannelli.orientaFascioTrascinandoManiglia,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        FixtureKind.Chandelier -> ModelPicker(T.comuni.modello, LampModel.entries, f.lampModel, { it.label }, { v -> update { it.copy(lampModel = v) } }) {
            lampPreview(it)
        }
        else -> Unit
    }
}

/** Nome dell'apertura come lo si mostra: "Porta ad 1 anta", "Finestra 2 ante scorrevole"… */
internal fun openingLabel(o: Opening): String = if (o.sliding) T.pannelli.scorrevole2(o.kind.label) else o.kind.label

/** Campo numerico in cm che applica il valore a ogni modifica valida (il riquadro si aggiorna dal vivo). */
@Composable
private fun NumberField(
    label: String,
    value: Double,
    modifier: Modifier,
    allowZero: Boolean = false,
    suffix: String = "cm",
    allowNegative: Boolean = false,
    onValue: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatCm(value)) }
    val valid = { v: Double -> allowNegative || (if (allowZero) v >= 0 else v > 0) }
    val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf(valid)
    SelectAllTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.takeIf(valid)?.let(onValue)
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
    val wall = listOf(T.pannelli.esistente, T.pannelli.demolire, T.pannelli.nuovo)
    val opening = listOf(T.pannelli.esistente, T.pannelli.chiudere, T.pannelli.aprire)
}

/** Scelta dello stato di fatto / progetto, con i colori della tavola comparativa. */
@Composable
internal fun PhaseChoice(phase: com.sagoma.planimetria.model.Phase, labels: List<String>, onChange: (com.sagoma.planimetria.model.Phase) -> Unit) {
    Choice(
        T.pannelli.statoPraticheEdilizieDemolizioni,
        labels,
        com.sagoma.planimetria.model.Phase.entries.indexOf(phase),
        Modifier.fillMaxWidth().padding(end = 8.dp),
    ) { onChange(com.sagoma.planimetria.model.Phase.entries[it]) }
}

/** "⧉ Duplica" dell'oggetto selezionato (o della stanza attiva): copia accanto, subito selezionata. Ctrl+D sul computer. */
@Composable
internal fun DuplicateButton(vm: EditorViewModel, label: String = T.pannelli.duplica2) {
    TextButton(onClick = { vm.duplicateSelected() }) { Text(label) }
}

/** Conferma eliminazione stanza, con il numero di aperture che verranno perse. */
@Composable
fun DeleteRoomDialog(room: Room, vm: EditorViewModel) {
    val n = room.openings.size
    AlertDialog(
        onDismissRequest = vm::cancelDeleteRoom,
        title = { Text(T.pannelli.eliminare(room.name)) },
        text = {
            Text(
                when (n) {
                    0 -> T.pannelli.stanzaVerraRimossaPianta
                    1 -> T.pannelli.verrannoRimosseStanzaSua
                    else -> T.pannelli.verrannoRimosseStanzaSue(n)
                } + T.pannelli.potraiRecuperarlaAnnullaAlto,
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.deleteRoom(room.id) }) { Text(T.comuni.elimina, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = vm::cancelDeleteRoom) { Text(T.comuni.mantieni) } },
    )
}
