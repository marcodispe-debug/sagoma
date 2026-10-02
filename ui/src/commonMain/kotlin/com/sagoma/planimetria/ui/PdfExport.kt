package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.geometry.exterior
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.persistence.ProjectInfo
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

    private const val MARGIN = 28f        // cornice (≈ 1 cm)
    private const val BLOCK_W = 250f      // cartiglio
    private const val BLOCK_H = 96f
    private const val DIM_SPACE = 44f     // spazio per le quote esterne attorno alla pianta
    /** Punti per dp nei disegni della pianta: testi e tratti un po' più fini che sullo schermo. */
    val drawDensity = Density(0.62f, 1f)

    data class Options(val paper: Paper, val scale: Int?, val allFloors: Boolean, val takeoff: Boolean)

    /** Riquadro utile per la pianta nella pagina (sopra al cartiglio). */
    private fun drawArea(p: Paper) = floatArrayOf(MARGIN + DIM_SPACE, MARGIN + DIM_SPACE, p.w - MARGIN - 12f, p.h - MARGIN - BLOCK_H - 16f)

    /** La scala più grande tra quelle normali in cui la pianta ci sta. */
    fun autoScale(plan: FloorPlan, paper: Paper): Int {
        val b = Openings.planBounds(plan) ?: return 100
        val a = drawArea(paper)
        return scales.firstOrNull { d -> b.width * ptPerCm(d) <= a[2] - a[0] && b.height * ptPerCm(d) <= a[3] - a[1] } ?: scales.last()
    }

    /** Pagine della tavola: una per piano e, a scelta, il computo. */
    fun pages(building: Building, info: ProjectInfo?, o: Options, measurer: TextMeasurer, date: String): List<PdfPage> {
        val floors = if (o.allFloors) building.floors.filter { it.plan.rooms.isNotEmpty() || it.plan.furniture.isNotEmpty() } else listOf(building.floor)
        val sheets = floors.size + if (o.takeoff) 1 else 0
        val result = floors.mapIndexed { i, fl ->
            PdfPage(o.paper.w, o.paper.h) { drawPlanSheet(fl, info, o, measurer, i + 1, sheets, date, building.layers) }
        }.toMutableList()
        if (o.takeoff) {
            val res = Takeoff.of(building)
            result += PdfPage(o.paper.w, o.paper.h) { drawTakeoffSheet(res, info, o.paper, sheets, measurer, date) }
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
            if (shown(com.sagoma.planimetria.model.Layer.WallLengths)) drawWallDimensions(plan, cam, measurer, avoid = labels.map { it.rect(cam, pad) })
            drawWallCuts(state)
            drawRoomLabels(labels, cam, pad) { true }
            if (shown(com.sagoma.planimetria.model.Layer.Fixtures)) drawAllFixtures(state)
            if (shown(com.sagoma.planimetria.model.Layer.WallLengths)) {
                drawOpeningSizes(plan, cam, measurer)
                drawOverallDimensions(plan, cam, measurer)
            }
            // Quote manuali e testi del disegno.
            drawNotes(
                plan, cam, measurer, Color.White,
                showDimensions = shown(com.sagoma.planimetria.model.Layer.Dimensions), showTexts = shown(com.sagoma.planimetria.model.Layer.Texts),
            )
        }
        drawFrame(paper)
        drawScaleBar(paper, k, denom, measurer)
        drawTitleBlock(paper, info, "Pianta ${fl.name.lowercase()}", "1:$denom", sheet, sheets, measurer, date)
    }

    // ---------------------------------------------------------------------------------------------------
    // Quote

    private val DimColor = Color(0xFF1D3557)

    /** Larghezza × altezza (e davanzale) di ogni porta e finestra, vicino all'apertura, verso l'interno. */
    private fun DrawScope.drawOpeningSizes(plan: FloorPlan, cam: Camera, measurer: TextMeasurer) {
        val style = TextStyle(fontSize = 9.sp, color = Color(0xFF6C4A1E))
        for (r in plan.rooms) for (o in r.openings) {
            val (p0, p1) = Openings.span(r, o)
            val n = Openings.inwardNormal(r, o.wallIndex)
            val mid = (p0 + p1) / 2.0 + n * (r.thicknessOf(o.wallIndex) / 2 + 14 / cam.scale)
            val txt = "${formatCmShort(o.width)}×${formatCmShort(o.height)}" + if (o.kind.hasSill && o.sillHeight > 0) " h${formatCmShort(o.sillHeight)}" else ""
            val t = measurer.measure(txt, style)
            val at = Offset(cam.toScreenX(mid.x), cam.toScreenY(mid.y))
            drawRect(Color.White.copy(alpha = 0.85f), at - Offset(t.size.width / 2f + 1, t.size.height / 2f), Size(t.size.width + 2f, t.size.height.toFloat()))
            drawText(t, topLeft = at - Offset(t.size.width / 2f, t.size.height / 2f))
        }
    }

    /**
     * Quote esterne: sopra e a sinistra della pianta, una catena progressiva tra gli spigoli esterni dei
     * muri e, più fuori, la misura totale.
     */
    private fun DrawScope.drawOverallDimensions(plan: FloorPlan, cam: Camera, measurer: TextMeasurer) {
        val pts = plan.rooms.flatMap { it.exterior() }
        if (pts.isEmpty()) return
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val xs = stops(pts.map { it.x })
        val ys = stops(pts.map { it.y })
        val gap1 = 16.dp.toPx()
        val gap2 = 30.dp.toPx()
        val top = cam.toScreenY(minY)
        dimChain(xs.map { cam.toScreenX(it) }, xs, top - gap1, horizontal = true, measurer)
        dimChain(listOf(cam.toScreenX(minX), cam.toScreenX(maxX)), listOf(minX, maxX), top - gap2, horizontal = true, measurer)
        val left = cam.toScreenX(minX)
        dimChain(ys.map { cam.toScreenY(it) }, ys, left - gap1, horizontal = false, measurer)
        dimChain(listOf(cam.toScreenY(minY), cam.toScreenY(maxY)), listOf(minY, maxY), left - gap2, horizontal = false, measurer)
    }

    /** Coordinate distinte (tolleranza 2 cm) in ordine. */
    private fun stops(v: List<Double>): List<Double> {
        val out = mutableListOf<Double>()
        for (x in v.sorted()) if (out.isEmpty() || x - out.last() > 2.0) out += x
        return out
    }

    /** Linea di quota con trattini obliqui agli estremi e la misura di ogni tratto. */
    private fun DrawScope.dimChain(screen: List<Float>, world: List<Double>, at: Float, horizontal: Boolean, measurer: TextMeasurer) {
        if (screen.size < 2) return
        val stroke = 0.6.dp.toPx()
        val tick = 4.dp.toPx()
        val style = TextStyle(fontSize = 11.sp, color = DimColor)
        fun p(s: Float) = if (horizontal) Offset(s, at) else Offset(at, s)
        drawLine(DimColor, p(screen.first()), p(screen.last()), strokeWidth = stroke)
        for (s in screen) {
            val c = p(s)
            drawLine(DimColor, c + Offset(-tick / 2, tick / 2), c + Offset(tick / 2, -tick / 2), strokeWidth = stroke * 1.6f)
            // Linea di richiamo verso la pianta
            val ext = if (horizontal) Offset(0f, 6.dp.toPx()) else Offset(6.dp.toPx(), 0f)
            drawLine(DimColor.copy(alpha = 0.5f), c - ext / 3f, c + ext, strokeWidth = stroke * 0.7f)
        }
        for (i in 0 until screen.size - 1) {
            val len = world[i + 1] - world[i]
            val t = measurer.measure(formatCmShort(len), style)
            val mid = (screen[i] + screen[i + 1]) / 2
            if (abs(screen[i + 1] - screen[i]) < t.size.width * 0.8f) continue // troppo corto per scriverci
            if (horizontal) {
                drawText(t, topLeft = Offset(mid - t.size.width / 2f, at - t.size.height - 1f))
            } else {
                rotate(-90f, pivot = Offset(at, mid)) {
                    drawText(t, topLeft = Offset(at - t.size.width / 2f, mid - t.size.height - 1f))
                }
            }
        }
    }

    private fun formatCmShort(cm: Double): String = cm.roundToInt().toString()

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
        text(m, "PROGETTO", l + 5, t + 9, 5.5f, color = Grey)
        text(m, info?.name ?: "Progetto", l + 5, t + 22, 11f, bold = true, maxWidth = BLOCK_W - 10)
        line(l, t + 28, r, t + 28)
        // Righe 2-3: cliente | progettista, indirizzo
        fun cell(x: Float, y: Float, w: Float, k: String, v: String) {
            text(m, k, x + 5, y + 8, 5.5f, color = Grey)
            text(m, v.ifBlank { "—" }, x + 5, y + 19, 8.5f, maxWidth = w - 10)
        }
        val half = BLOCK_W / 2
        cell(l, t + 28, half, "CLIENTE", info?.client.orEmpty())
        cell(l + half, t + 28, half, "PROGETTISTA", info?.author.orEmpty())
        line(l + half, t + 28, l + half, t + 51)
        line(l, t + 51, r, t + 51)
        cell(l, t + 51, BLOCK_W, "INDIRIZZO", info?.address.orEmpty())
        line(l, t + 74, r, t + 74)
        // Ultima riga: tavola, scala, data, foglio
        val w4 = floatArrayOf(96f, 44f, 60f, 50f)
        var x = l
        listOf("TAVOLA" to sheetTitle, "SCALA" to scale, "DATA" to date, "FOGLIO" to "$sheet / $sheets").forEachIndexed { i, (k, v) ->
            text(m, k, x + 4, t + 80, 5.5f, color = Grey)
            text(m, v, x + 4, t + 91, 7.5f, bold = i == 1, maxWidth = w4[i] - 8)
            x += w4[i]
            if (i < 3) line(x, t + 74, x, bottom)
        }
        // Nota sulle misure, a sinistra del cartiglio
        val note = Color(0xFF495057)
        text(m, "Quote in cm. Quote interne dei muri e aree al netto dello spessore dei muri.", MARGIN + 6, bottom - 18, 6.5f, color = note)
        text(m, "Aperture: larghezza × altezza (h = altezza del davanzale). Stampare a dimensione reale (100%).", MARGIN + 6, bottom - 8, 6.5f, color = note)
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
        text(m, "Scala 1:$denom", x0 + steps.last() * 100 * k + 16, y + 4, 7f, bold = true)
    }

    // ---------------------------------------------------------------------------------------------------
    // Foglio del computo

    private fun DrawScope.drawTakeoffSheet(res: Takeoff.Result, info: ProjectInfo?, p: Paper, sheet: Int, m: TextMeasurer, date: String) {
        drawRect(Color.White)
        drawFrame(p)
        drawTitleBlock(p, info, "Computo e arredi", "—", sheet, sheet, m, date)
        val left = MARGIN + 14
        val maxY = p.h - MARGIN - BLOCK_H - 10
        var y = MARGIN + 22
        var colX = left
        val colW = (p.w - 2 * MARGIN - 28) / 2
        fun nl(h: Float = 11f) {
            y += h
            if (y > maxY && colX == left) { colX = left + colW + 14; y = MARGIN + 22 }
        }
        fun row(cells: List<String>, widths: List<Float>, bold: Boolean = false, right: Set<Int> = emptySet()) {
            var x = colX
            cells.forEachIndexed { i, s ->
                if (i in right) text(m, s, x + widths[i] - 6, y, 7.5f, bold = bold, maxWidth = widths[i] - 6, right = true)
                else text(m, s, x, y, 7.5f, bold = bold, maxWidth = widths[i] - 6)
                x += widths[i]
            }
            nl()
        }
        fun title(s: String) { nl(6f); text(m, s, colX, y, 10f, bold = true); nl(14f) }
        val w = listOf(colW * 0.30f, colW * 0.13f, colW * 0.15f, colW * 0.14f, colW * 0.28f)
        val multi = res.rooms.map { it.floor }.distinct().size > 1
        val nums = setOf(1, 2, 3)
        title("Stanze")
        row(listOf("Stanza", "Pav. m²", "Pareti m²", "Battisc. m", "Pavimento"), w, bold = true, right = nums)
        for (l in res.rooms) row(listOf(if (multi) "${l.room} (${l.floor})" else l.room, fmt2(l.floorArea), fmt2(l.wallArea), fmt2(l.skirting), l.floorFinish), w, right = nums)
        row(listOf("Totale", fmt2(res.totalFloor), fmt2(res.totalWalls), fmt2(res.totalSkirting), ""), w, bold = true, right = nums)
        val w2 = listOf(colW * 0.55f, colW * 0.2f, colW * 0.25f)
        val nums2 = setOf(1, 2)
        title("Pavimenti")
        row(listOf("Materiale", "m²", "+10% sfrido"), w2, bold = true, right = nums2)
        for (t in res.floorMaterials) row(listOf(t.what, fmt2(t.quantity), fmt2(t.quantity * 1.1)), w2, right = nums2)
        title("Pareti (al netto delle aperture)")
        for (t in res.wallFinishes) row(listOf(t.what, fmt2(t.quantity), ""), w2, right = nums2)
        if (res.furniture.isNotEmpty()) {
            title("Arredi")
            val w3 = listOf(colW * 0.08f, colW * 0.40f, colW * 0.24f, colW * 0.28f)
            for (i in res.furniture) row(listOf("${i.count}×", i.name, i.size, i.where), w3, right = setOf(2))
        }
        if (res.fixtures.isNotEmpty()) {
            title("Impianti")
            val w3 = listOf(colW * 0.08f, colW * 0.40f, colW * 0.52f)
            for (i in res.fixtures) row(listOf("${i.count}×", i.name, i.where), w3)
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
        val opts = PdfExport.Options(paper, scale, allFloors, takeoff)
        val pages = PdfExport.pages(state.fullBuilding, info, opts, measurer, platform.formatDate(platform.now()))
        val pdf = runCatching { platform.createPdf(pages, PdfExport.drawDensity) }.onFailure { it.printStackTrace() }.getOrNull()
        if (pdf == null) {
            platform.toast("Tavola PDF non disponibile su questa piattaforma")
            return
        }
        val name = (info?.name ?: "planimetria").replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "planimetria" }
        platform.saveFile("$name.pdf", "application/pdf", pdf) { ok ->
            platform.toast(if (ok) "Tavola PDF salvata" else "Esportazione non riuscita")
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tavola PDF in scala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Foglio (orizzontale)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in PdfExport.Paper.entries) FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(p.label) })
                }
                Text("Scala", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = scale == null, onClick = { scale = null }, label = { Text("Auto (1:$auto)") })
                    for (d in listOf(50, 100, 200)) FilterChip(selected = scale == d, onClick = { scale = d }, label = { Text("1:$d") })
                }
                val chosen = scale
                if (chosen != null && chosen < auto) Text(
                    "A 1:$chosen la pianta non entra tutta nel foglio $paper: scegli un foglio più grande o una scala più piccola.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.fullBuilding.floors.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allFloors, onCheckedChange = { allFloors = it })
                    Text("Tutti i piani (una tavola per piano)")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = takeoff, onCheckedChange = { takeoff = it })
                    Text("Aggiungi il foglio del computo")
                }
                Text(
                    "Il cartiglio usa nome, cliente, indirizzo e progettista del progetto (si cambiano dall'elenco dei progetti).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Salva PDF") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
