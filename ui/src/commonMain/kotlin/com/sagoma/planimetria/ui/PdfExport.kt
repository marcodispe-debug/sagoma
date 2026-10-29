package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.access.OutputFormat
import com.sagoma.planimetria.access.Rights
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.HousingRules
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.persistence.ProjectInfo
import com.sagoma.planimetria.testi.T
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tavole PDF in scala: una pagina per piano (A4 o A3 orizzontale) con cornice, cartiglio, scala grafica,
 * quote interne dei muri, quote esterne complessive e progressive, misure di porte e finestre; in fondo,
 * a scelta, il computo. Le unità della pagina sono punti tipografici (1 pt = 0,3528 mm), quindi la scala
 * 1:N è esatta se il PDF si stampa "a dimensione reale". Tutto è disegnato con Compose: il contenitore
 * PDF lo crea la piattaforma ([Platform.createPdf]).
 */
object PdfExport {
    enum class Paper(val label: String, val w: Int, val h: Int) { A4("A4", 842, 595), A3("A3", 1191, 842) }

    /** Scale proposte, dalla più grande alla più piccola. */
    val scales = listOf(20, 25, 50, 100, 200, 500)

    /** Punti di pagina per cm reale alla scala 1:[denominator]. */
    fun ptPerCm(denominator: Int) = 10.0 / denominator / 0.352778

    internal const val MARGIN = 28f        // cornice (≈ 1 cm)
    private const val BLOCK_W = 250f      // cartiglio
    internal const val BLOCK_H = 96f
    private const val DIM_SPACE = 56f     // spazio per le quote esterne attorno alla pianta (le due file di [ChainLayout], oltre il filo dei muri)
    /** Punti per dp nei disegni della pianta: testi e tratti un po' più fini che sullo schermo. */
    val drawDensity = Density(0.62f, 1f)

    /** [watermark]: marchio del piano gratuito su ogni foglio (lo decide chi esporta, con [com.sagoma.planimetria.access.Rights]). */
    data class Options(val paper: Paper, val scale: Int?, val allFloors: Boolean, val takeoff: Boolean, val watermark: Boolean = false, val withoutZones: Boolean = true)

    /** Riquadro utile per la pianta nella pagina (sopra al cartiglio). */
    private fun drawArea(p: Paper) = floatArrayOf(MARGIN + DIM_SPACE, MARGIN + DIM_SPACE, p.w - MARGIN - DIM_SPACE, p.h - MARGIN - BLOCK_H - DIM_SPACE)

    /** La scala più grande tra quelle normali in cui la pianta ci sta. */
    fun autoScale(plan: FloorPlan, paper: Paper): Int {
        val b = Openings.planBounds(plan) ?: return 100
        val a = drawArea(paper)
        return scales.firstOrNull { d -> b.width * ptPerCm(d) <= a[2] - a[0] && b.height * ptPerCm(d) <= a[3] - a[1] } ?: scales.last()
    }

    /** Pagine della tavola: una per piano e, a scelta, il computo. */
    fun pages(building: Building, info: ProjectInfo?, o: Options, measurer: TextMeasurer, date: String): List<PdfPage> {
        val floors = if (o.allFloors) building.floors.filter { it.plan.rooms.isNotEmpty() || it.plan.furniture.isNotEmpty() } else listOf(building.floor)
        val takeoff = if (o.takeoff) takeoffSheets(Takeoff.of(building, o.withoutZones), o.paper) else emptyList()
        val sheets = floors.size + takeoff.size
        val result = floors.mapIndexed { i, fl ->
            PdfPage(o.paper.w, o.paper.h) {
                drawPlanSheet(fl, info, o, measurer, i + 1, sheets, date, building.layers)
                if (o.watermark) with(Watermark) { drawWatermark(Watermark.exportLetterPx(size.width, size.height)) }
            }
        }.toMutableList()
        takeoff.forEachIndexed { k, lines ->
            result += PdfPage(o.paper.w, o.paper.h) {
                drawTakeoffSheet(lines, info, o.paper, floors.size + k + 1, sheets, measurer, date)
                if (o.watermark) with(Watermark) { drawWatermark(Watermark.exportLetterPx(size.width, size.height)) }
            }
        }
        return result
    }

    private fun DrawScope.drawPlanSheet(
        fl: Floor, info: ProjectInfo?, o: Options, measurer: TextMeasurer, sheet: Int, sheets: Int, date: String,
        layers: com.sagoma.planimetria.model.LayerSettings = com.sagoma.planimetria.model.LayerSettings(),
    ) {
        fun shown(l: com.sagoma.planimetria.model.Layer) = layers.visible(l)
        val plan = fl.plan
        val paper = o.paper
        val denom = o.scale ?: autoScale(plan, paper)
        val k = ptPerCm(denom).toFloat()
        val a = drawArea(paper)
        val b = Openings.planBounds(plan)
        val cam = if (b == null) Camera(k, a[0], a[1]) else Camera(
            k,
            ((a[0] + a[2]) / 2 - (b.minX + b.width / 2) * k).toFloat(),
            ((a[1] + a[3]) / 2 - (b.minY + b.height / 2) * k).toFloat(),
        )
        val state = EditorUiState(plan = plan, camera = cam)
        drawRect(Color.White)
        // Fuori dalla cornice non si disegna (piante più grandi del foglio alla scala scelta).
        clipRect(MARGIN, MARGIN, paper.w - MARGIN, paper.h - MARGIN - BLOCK_H - 4f) {
            drawPlan(state, measurer, background = Color.White)
            // Solo i livelli visibili (es. tavola senza arredi per il Comune).
            if (shown(com.sagoma.planimetria.model.Layer.Structure)) for (s in plan.stairs) drawStair(s, fl.levelHeight, cam, measurer, selected = false)
            if (shown(com.sagoma.planimetria.model.Layer.Furniture)) drawFurniture(plan.furniture, cam, null)
            if (shown(com.sagoma.planimetria.model.Layer.Structure)) drawStructures(plan, cam, measurer)
            val pad = 8.dp.toPx()
            val labels = measureRoomLabels(plan, measurer) { true }
            val outside = com.sagoma.planimetria.geometry.ExteriorChains.of(plan)
            if (shown(com.sagoma.planimetria.model.Layer.WallLengths)) {
                drawWallDimensions(plan, cam, measurer, avoid = labels.map { it.rect(cam, pad) }, skip = { id, i -> (id to i) in outside.covered })
            }
            drawWallCuts(state)
            drawRoomLabels(labels, cam, pad) { true }
            if (shown(com.sagoma.planimetria.model.Layer.Fixtures)) drawAllFixtures(state)
            if (shown(com.sagoma.planimetria.model.Layer.WallLengths)) {
                drawOpeningSizes(plan.expandCurves().plan, outside, cam, measurer, Color.White)
                drawExteriorChains(outside, cam, measurer)
            }
            // Quote manuali e testi del disegno.
            drawNotes(
                plan, cam, measurer, Color.White,
                showDimensions = shown(com.sagoma.planimetria.model.Layer.Dimensions), showTexts = shown(com.sagoma.planimetria.model.Layer.Texts),
            )
        }
        drawFrame(paper)
        drawScaleBar(paper, k, denom, measurer)
        drawTitleBlock(paper, info, T.pdf.pianta(T.lingua.minuscola(fl.name)), "1:$denom", sheet, sheets, measurer, date)
    }

    // ---------------------------------------------------------------------------------------------------
    // Quote

    // ---------------------------------------------------------------------------------------------------
    // Cornice, cartiglio, scala grafica. Misure in punti della pagina.

    private val Ink = Color(0xFF212529)
    private val Grey = Color(0xFF6C757D)

    /**
     * Testo alto `pt` punti con la linea di base in `baseline`; tagliato con "…" oltre `maxWidth`. `right`:
     * `x` è il bordo destro invece che quello sinistro. Restituisce la larghezza.
     */
    private fun DrawScope.text(
        measurer: TextMeasurer, s: String, x: Float, baseline: Float, pt: Float,
        bold: Boolean = false, color: Color = Ink, maxWidth: Float = 2000f, right: Boolean = false,
    ): Float {
        val style = TextStyle(fontSize = (pt / drawDensity.density).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
        val t = measurer.measure(s, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))
        val left = if (right) x - t.size.width else x
        drawText(t, topLeft = Offset(left, baseline - t.firstBaseline))
        return t.size.width.toFloat()
    }

    private fun DrawScope.line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 0.5f) =
        drawLine(Ink, Offset(x0, y0), Offset(x1, y1), strokeWidth = w)

    private fun DrawScope.box(l: Float, t: Float, r: Float, b: Float, w: Float) =
        drawRect(Ink, Offset(l, t), Size(r - l, b - t), style = Stroke(w))

    private fun DrawScope.drawFrame(p: Paper) = box(MARGIN, MARGIN, p.w - MARGIN, p.h - MARGIN, 1.2f)

    private fun DrawScope.drawTitleBlock(p: Paper, info: ProjectInfo?, sheetTitle: String, scale: String, sheet: Int, sheets: Int, m: TextMeasurer, date: String) {
        val r = p.w - MARGIN
        val bottom = p.h - MARGIN
        val l = r - BLOCK_W
        val t = bottom - BLOCK_H
        drawRect(Color.White, Offset(l, t), Size(BLOCK_W, BLOCK_H))
        box(l, t, r, bottom, 1.2f)
        // Riga 1: progetto (grande)
        text(m, T.pdf.progetto, l + 5, t + 9, 5.5f, color = Grey)
        text(m, info?.name ?: T.pdf.progetto2, l + 5, t + 22, 11f, bold = true, maxWidth = BLOCK_W - 10)
        line(l, t + 28, r, t + 28)
        // Righe 2-3: cliente | progettista, indirizzo
        fun cell(x: Float, y: Float, w: Float, k: String, v: String) {
            text(m, k, x + 5, y + 8, 5.5f, color = Grey)
            text(m, v.ifBlank { "—" }, x + 5, y + 19, 8.5f, maxWidth = w - 10)
        }
        val half = BLOCK_W / 2
        cell(l, t + 28, half, T.pdf.cliente, info?.client.orEmpty())
        cell(l + half, t + 28, half, T.pdf.progettista, info?.author.orEmpty())
        line(l + half, t + 28, l + half, t + 51)
        line(l, t + 51, r, t + 51)
        cell(l, t + 51, BLOCK_W, T.pdf.indirizzo, info?.address.orEmpty())
        line(l, t + 74, r, t + 74)
        // Ultima riga: tavola, scala, data, foglio
        val w4 = floatArrayOf(96f, 44f, 60f, 50f)
        var x = l
        listOf(T.pdf.tavola to sheetTitle, T.pdf.scala to scale, T.pdf.data to date, T.pdf.foglio to "$sheet / $sheets").forEachIndexed { i, (k, v) ->
            text(m, k, x + 4, t + 80, 5.5f, color = Grey)
            text(m, v, x + 4, t + 91, 7.5f, bold = i == 1, maxWidth = w4[i] - 8)
            x += w4[i]
            if (i < 3) line(x, t + 74, x, bottom)
        }
        // Nota sulle misure, a sinistra del cartiglio
        val note = Color(0xFF495057)
        text(m, T.pdf.quoteCmQuoteInterne, MARGIN + 6, bottom - 18, 6.5f, color = note)
        text(m, T.pdf.apertureLarghezzaAltezzaH, MARGIN + 6, bottom - 8, 6.5f, color = note)
    }

    /** Scala grafica 0–1–2–5 m (0–5–10 m nelle scale piccole) sopra la nota. */
    private fun DrawScope.drawScaleBar(p: Paper, k: Float, denom: Int, m: TextMeasurer) {
        val steps = if (denom >= 200) listOf(0, 1, 2, 5, 10) else listOf(0, 1, 2, 5)
        val x0 = MARGIN + 6
        val y = p.h - MARGIN - 34
        for (i in 0 until steps.size - 1) {
            val a = x0 + steps[i] * 100 * k
            val b = x0 + steps[i + 1] * 100 * k
            if (i % 2 == 0) drawRect(Ink, Offset(a, y), Size(b - a, 3f)) else box(a, y, b, y + 3, 0.5f)
        }
        for (s in steps) text(m, if (s == steps.last()) "$s m" else "$s", x0 + s * 100 * k - 2, y - 2, 6f)
        text(m, T.pdf.scala1(denom), x0 + steps.last() * 100 * k + 16, y + 4, 7f, bold = true)
    }

    // ---------------------------------------------------------------------------------------------------
    // Foglio del computo

    /** Una riga del computo nella tavola: un titolo o una riga di tabella, già messa al suo posto nel foglio. */
    internal class TakeoffLine(val x: Float, val y: Float, val cells: List<String>, val widths: List<Float>, val bold: Boolean, val right: Set<Int>, val title: Boolean)

    /**
     * Righe del computo divise in fogli, due colonne per foglio. Con tante stanze, arredi e impianti la seconda colonna
     * si riempiva e le righe scendevano sopra il cartiglio e fuori dal foglio: ora continuano in un foglio nuovo.
     */
    internal fun takeoffSheets(res: Takeoff.Result, p: Paper): List<List<TakeoffLine>> {
        val left = MARGIN + 14
        val maxY = p.h - MARGIN - BLOCK_H - 10
        val top = MARGIN + 22
        val colW = (p.w - 2 * MARGIN - 28) / 2
        val sheets = mutableListOf(mutableListOf<TakeoffLine>())
        var y = top
        var colX = left
        fun nl(h: Float = 11f) {
            y += h
            if (y <= maxY) return
            y = top
            if (colX == left) colX = left + colW + 14
            else { colX = left; sheets += mutableListOf<TakeoffLine>() }
        }
        fun row(cells: List<String>, widths: List<Float>, bold: Boolean = false, right: Set<Int> = emptySet()) {
            sheets.last() += TakeoffLine(colX, y, cells, widths, bold, right, title = false)
            nl()
        }
        fun title(s: String) {
            nl(6f)
            // Un titolo non resta da solo in fondo alla colonna: va in cima alla prossima con la sua tabella.
            if (y + 14f + 11f > maxY) nl(maxY)
            sheets.last() += TakeoffLine(colX, y, listOf(s), emptyList(), bold = true, right = emptySet(), title = true)
            nl(14f)
        }
        val w = listOf(colW * 0.30f, colW * 0.13f, colW * 0.15f, colW * 0.14f, colW * 0.28f)
        val multi = res.rooms.map { it.floor }.distinct().size > 1
        val nums = setOf(1, 2, 3)
        // Con demolizioni o parti nuove il computo è quello dello stato di progetto, anche in una tavola dello stato di fatto.
        title(if (res.phased) "${T.pdf.stanze} · ${PhaseView.Project.title}" else T.pdf.stanze)
        row(listOf(T.pdf.stanza, T.pdf.pavM2, T.pdf.paretiM2, T.pdf.battiscM, T.pdf.pavimento), w, bold = true, right = nums)
        for (l in res.rooms) row(listOf(if (multi) "${l.room} (${l.floor})" else l.room, fmt2(l.floorArea), fmt2(l.wallArea), fmt2(l.skirting), l.floorFinish), w, right = nums)
        row(listOf(T.pdf.totale, fmt2(res.totalFloor), fmt2(res.totalWalls), fmt2(res.totalSkirting), ""), w, bold = true, right = nums)
        val w2 = listOf(colW * 0.55f, colW * 0.2f, colW * 0.25f)
        val nums2 = setOf(1, 2)
        if (res.works.isNotEmpty()) {
            title(T.computo.opere)
            val w4 = listOf(colW * 0.55f, colW * 0.11f, colW * 0.17f, colW * 0.17f)
            val nums3 = setOf(1, 2, 3)
            row(listOf(T.computo.voce, T.computo.numero, "m²", "m³"), w4, bold = true, right = nums3)
            val lines = if (res.works.size > 1) res.works + Takeoff.WorksLine(T.pdf.totale, res.totalWorks) else res.works
            for (l in lines) {
                if (lines.size > 1) row(listOf(l.floor), listOf(colW), bold = true)
                for (i in Takeoff.worksItems(l.works)) row(listOf(i.label, i.count?.toString() ?: "", fmt2(i.m2), fmt2(i.m3)), w4, right = nums3)
            }
            row(listOf(T.computo.notaOperePdf), listOf(colW))
        }
        if (res.areas.isNotEmpty()) {
            title(T.computo.superfici)
            row(listOf(T.computo.piano, T.computo.utileM2, T.computo.lordaM2, T.computo.balconiM2, ""), w, bold = true, right = nums)
            for (a in res.areas) row(listOf(a.floor, fmt2(a.usable), fmt2(a.gross), fmt2(a.outdoor), ""), w, right = nums)
            row(listOf(T.computo.commerciale, fmt2(res.commercial), ""), w2, bold = true, right = nums2)
            row(listOf(T.computo.notaSuperficiPdf), listOf(colW))
        }
        title(T.pdf.pavimenti)
        row(listOf(T.pdf.materiale, "m²", T.pdf.n10Sfrido), w2, bold = true, right = nums2)
        for (t in res.floorMaterials) row(listOf(t.what, fmt2(t.quantity), fmt2(t.quantity * 1.1)), w2, right = nums2)
        title(T.pdf.paretiNettoAperture)
        for (t in res.wallFinishes) row(listOf(t.what, fmt2(t.quantity), ""), w2, right = nums2)
        if (res.ceilings > 0) row(listOf(T.computo.soffitti, fmt2(res.ceilings), ""), w2, right = nums2)
        if (res.furniture.isNotEmpty()) {
            title(T.pdf.arredi)
            val w3 = listOf(colW * 0.08f, colW * 0.40f, colW * 0.24f, colW * 0.28f)
            for (i in res.furniture) row(listOf("${i.count}×", i.name, i.size, i.where), w3, right = setOf(2))
        }
        if (res.fixtures.isNotEmpty()) {
            title(T.pdf.impianti)
            val w3 = listOf(colW * 0.08f, colW * 0.40f, colW * 0.52f)
            for (i in res.fixtures) row(listOf("${i.count}×", i.name, i.where), w3)
        }
        if (res.light.isNotEmpty()) {
            title(T.computo.luceAria)
            row(listOf(T.pdf.stanza, T.pdf.pavM2, T.computo.finM2, T.computo.rapporto, T.computo.almenoUnOttavo), w, bold = true, right = nums)
            for (l in res.light) {
                row(listOf(if (multi) "${l.rooms} (${l.floor})" else l.rooms, fmt2(l.floorArea), fmt2(l.windowArea), HousingRules.fraction(l.ratio), if (l.enough) T.computo.si else T.computo.no), w, right = nums)
            }
            row(listOf(T.computo.notaLucePdf), listOf(colW))
        }
        return sheets.filter { it.isNotEmpty() }
    }

    private fun DrawScope.drawTakeoffSheet(lines: List<TakeoffLine>, info: ProjectInfo?, p: Paper, sheet: Int, sheets: Int, m: TextMeasurer, date: String) {
        drawRect(Color.White)
        drawFrame(p)
        drawTitleBlock(p, info, T.pdf.computoArredi, "—", sheet, sheets, m, date)
        for (l in lines) {
            if (l.title) { text(m, l.cells[0], l.x, l.y, 10f, bold = true); continue }
            var x = l.x
            l.cells.forEachIndexed { i, s ->
                if (i in l.right) text(m, s, x + l.widths[i] - 6, l.y, 7.5f, bold = l.bold, maxWidth = l.widths[i] - 6, right = true)
                else text(m, s, x, l.y, 7.5f, bold = l.bold, maxWidth = l.widths[i] - 6)
                x += l.widths[i]
            }
        }
    }
}

/** Scelte della tavola (foglio, scala, piani, computo) e salvataggio del PDF. */
@Composable
fun PdfExportDialog(state: EditorUiState, info: ProjectInfo?, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    // Testi misurati alla densità del foglio, non a quella dello schermo.
    val fonts = LocalFontFamilyResolver.current
    val measurer = remember(fonts) { TextMeasurer(fonts, PdfExport.drawDensity, LayoutDirection.Ltr) }
    var paper by remember { mutableStateOf(PdfExport.Paper.A4) }
    var scale by remember { mutableStateOf<Int?>(null) }
    var allFloors by remember { mutableStateOf(state.fullBuilding.floors.size > 1) }
    var takeoff by remember { mutableStateOf(true) }
    val auto = remember(paper, state.plan) { PdfExport.autoScale(state.plan, paper) }

    fun save() {
        val rooms = (if (allFloors) state.fullBuilding.floors.map { it.plan } else listOf(state.plan)).flatMap { p -> p.rooms.map { it.id } }
        val watermark = !Rights.cleanOutput(platform.account, OutputFormat.Plan, rooms)
        val opts = PdfExport.Options(paper, scale, allFloors, takeoff, watermark, TakeoffChoice.withoutZones)
        val pages = PdfExport.pages(state.fullBuilding, info, opts, measurer, platform.formatDate(platform.now()))
        val pdf = runCatching { platform.createPdf(pages, PdfExport.drawDensity) }.onFailure { it.printStackTrace() }.getOrNull()
        if (pdf == null) {
            platform.toast(T.pdf.tavolaPdfNonDisponibile)
            return
        }
        val name = fileNameOf(info?.name ?: "", "planimetria")
        platform.saveFile("$name.pdf", "application/pdf", pdf) { ok ->
            platform.toast(if (!ok) T.pdf.esportazioneNonRiuscita else if (watermark) T.pdf.tavolaPdfSalvataMarchio else T.pdf.tavolaPdfSalvata)
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.pdf.tavolaPdfScala) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(T.pdf.foglioOrizzontale, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in PdfExport.Paper.entries) FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(p.label) })
                }
                Text(T.pdf.scala2, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = scale == null, onClick = { scale = null }, label = { Text(T.pdf.auto1(auto)) })
                    for (d in listOf(50, 100, 200)) FilterChip(selected = scale == d, onClick = { scale = d }, label = { Text("1:$d") })
                }
                val chosen = scale
                if (chosen != null && chosen < auto) Text(
                    T.pdf.n1PiantaNonEntra(chosen, paper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.fullBuilding.floors.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allFloors, onCheckedChange = { allFloors = it })
                    Text(T.pdf.tuttiPianiTavolaPiano)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = takeoff, onCheckedChange = { takeoff = it })
                    Text(T.pdf.aggiungiFoglioComputo)
                }
                if (takeoff) ExcludedZonesChoice(state.fullBuilding)
                Text(
                    T.pdf.cartiglioUsaNomeCliente,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text(T.pdf.salvaPdf) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T.comuni.annulla) } },
    )
}
