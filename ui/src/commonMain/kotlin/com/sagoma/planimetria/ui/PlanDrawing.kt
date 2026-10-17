package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.exterior
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.geometry.interiorArea
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.FloorPlan
import kotlin.math.atan2
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.roundToInt
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.geometry.WallCollision

private val WallColor = Color(0xFF3A3F44)
/** Tavola comparativa delle pratiche edilizie: demolizioni in giallo, nuove costruzioni in rosso. */
internal val PhaseDemolishColor = Color(0xFFF5C518)
internal val PhaseNewColor = Color(0xFFD62828)
private val WallFocusedColor = Color(0xFF1C1F22)
private val SelectedColor = Color(0xFF1971C2)
private val DoorColor = Color(0xFF8B5E3C)
private val WindowColor = Color(0xFF0B7285)
private val PassageColor = Color(0xFF495057)
private val WallHeightColor = Color(0xFF862E9C)
private val LabelTextColor = Color(0xFF212529)
private val DimensionColor = Color(0xFF495057)
private val RadiatorColor = Color(0xFFD9480F)
private val ElectricColor = Color(0xFF1C7ED6)
private val WaterColor = Color(0xFF0C8599)
private val LightColor = Color(0xFFE8A200)
private val CutColor = Color(0xFFB5501D)
private val WarningColor = Color(0xFFE03131)

private fun Camera.o(v: Vec2) = Offset(toScreenX(v.x), toScreenY(v.y))

private fun roomPath(room: Room, cam: Camera) = Path().apply {
    room.points.forEachIndexed { i, p -> val o = cam.o(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

/** Muri della stanza come area piena: contorno esterno meno contorno interno. */
internal fun wallRing(room: Room, cam: Camera) = Path().apply {
    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    for (contour in listOf(room.exterior(), room.interior())) {
        contour.forEachIndexed { i, p -> val o = cam.o(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        close()
    }
}

/**
 * Disegna l'intera pianta a strati: riempimenti, muri, varchi (propri e condivisi), simboli delle
 * aperture, evidenziazioni, etichette altezza e angoli della stanza a fuoco.
 */
internal fun DrawScope.drawPlan(state: EditorUiState, measurer: TextMeasurer, background: Color = PlanBackgroundColor) {
    val cam = state.camera
    val rooms = state.plan.rooms.sortedBy { it.id == state.focusedRoomId }
    val paths = rooms.associate { it.id to roomPath(it, cam) }
    val wallPx = (Room.WALL_THICKNESS * cam.scale).toFloat().coerceAtLeast(2f)
    // Riempimento tinto dal tipo, più intenso se la stanza è attiva (§3).
    fun fill(r: Room) = Color(r.type.argb).copy(alpha = if (r.id == state.focusedRoomId) 0.30f else 0.13f)

    for (r in rooms) drawPath(paths.getValue(r.id), fill(r))
    // Balconi e terrazze: parapetto sottile, disegnato prima dei muri così contro la casa resta il muro pieno.
    // Solo sui tratti che danno sul vuoto: niente parapetto dove un balcone tocca un altro balcone o terrazza.
    // Due passate (prima il tratto pieno di tutti, poi il riempimento): dove due parapetti si incontrano non
    // resta una tacca.
    val parapets = rooms.filter { it.outdoor }.map { r ->
        r to com.sagoma.planimetria.geometry.Parapets.segments(state.plan, r).map { (a, b) ->
            Offset(cam.toScreenX(a.x), cam.toScreenY(a.y)) to Offset(cam.toScreenX(b.x), cam.toScreenY(b.y))
        }
    }
    for (pass in 0..1) for ((r, segments) in parapets) {
        val line = if (r.id == state.focusedRoomId) WallFocusedColor else WallColor
        fun stroke(color: Color, width: Float) {
            for ((a, b) in segments) drawLine(color, a, b, width, cap = StrokeCap.Square)
        }
        when (r.parapet) {
            Parapet.Wall -> if (pass == 0) stroke(line, wallPx * 0.6f)
            Parapet.Railing -> if (pass == 0) stroke(line, wallPx * 0.5f) else stroke(background, wallPx * 0.22f)
            Parapet.Glass -> if (pass == 0) stroke(WindowColor, wallPx * 0.4f) else stroke(line, 1.dp.toPx())
        }
    }
    // Muri: anello tra il filo esterno e quello interno, ogni muro con il suo spessore (angoli a spigolo vivo).
    for (r in rooms) {
        if (r.outdoor) continue
        drawPath(wallRing(r, cam), if (r.id == state.focusedRoomId) WallFocusedColor else WallColor)
    }
    // Tavola comparativa: muri da demolire in giallo, nuovi in rosso (con un filo scuro ai bordi).
    for (r in rooms) for ((i, ph) in r.wallPhases) {
        if (i >= r.wallCount || r.isRemoved(i) || ph == Phase.Existing) continue
        val px = (r.thicknessOf(i) * cam.scale).toFloat().coerceAtLeast(2f)
        val a = cam.o(r.wallStart(i)); val b = cam.o(r.wallEnd(i))
        drawLine(WallColor, a, b, strokeWidth = px, cap = StrokeCap.Butt)
        drawLine(if (ph == Phase.Demolish) PhaseDemolishColor else PhaseNewColor, a, b, strokeWidth = (px - 2f).coerceAtLeast(1f), cap = StrokeCap.Butt)
    }

    // Varchi: si "cancella" il muro e si ripristina il riempimento delle stanze su entrambi i lati.
    val maxWallPx = ((state.plan.rooms.flatMap { it.thicknesses }.maxOrNull() ?: Room.WALL_THICKNESS) * cam.scale).toFloat().coerceAtLeast(2f)
    val gaps = rooms.flatMap { r -> r.openings.filter { it.wallIndex < r.wallCount }.map { Openings.span(r, it) to (r.thicknessOf(it.wallIndex) * cam.scale).toFloat().coerceAtLeast(2f) } } +
        Openings.sharedGaps(state.plan).map { (it.a to it.b) to maxWallPx } +
        // Muri eliminati: tutto il muro diventa un varco.
        rooms.flatMap { r -> r.removedWalls.filter { it < r.wallCount }.map { i -> (r.wallStart(i) to r.wallEnd(i)) to (r.thicknessOf(i) * cam.scale).toFloat().coerceAtLeast(2f) } }
    for ((ab, px) in gaps) {
        val (a, b) = ab
        drawLine(background, cam.o(a), cam.o(b), strokeWidth = px + 2f, cap = StrokeCap.Butt)
        for (r in rooms) clipPath(paths.getValue(r.id)) {
            drawLine(fill(r), cam.o(a), cam.o(b), strokeWidth = px + 2f, cap = StrokeCap.Butt)
        }
    }

    // Dove c'era il muro resta una linea tratteggiata sottile: il confine della stanza (si tocca per rimetterlo).
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    for (r in rooms) for (i in r.removedWalls) if (i < r.wallCount) {
        drawLine(WallColor.copy(alpha = 0.55f), cam.o(r.wallStart(i)), cam.o(r.wallEnd(i)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
    }

    val selWall = state.selection as? Selection.Wall
    val selOpening = state.selection as? Selection.Opening
    for (r in rooms) {
        if (selWall != null && selWall.roomId == r.id && selWall.index < r.wallCount) {
            drawLine(SelectedColor, cam.o(r.wallStart(selWall.index)), cam.o(r.wallEnd(selWall.index)), strokeWidth = (r.thicknessOf(selWall.index) * cam.scale).toFloat().coerceAtLeast(2f) + 4.dp.toPx())
        }
        for (o in r.openings) {
            if (o.wallIndex >= r.wallCount) continue
            if (selOpening?.roomId == r.id && selOpening.openingId == o.id) {
                val (a, b) = Openings.span(r, o)
                drawLine(SelectedColor.copy(alpha = 0.35f), cam.o(a), cam.o(b), strokeWidth = wallPx + 8.dp.toPx(), cap = StrokeCap.Round)
            }
            // Comparativa: apertura nuova = muro da demolire (giallo) sotto il simbolo; da chiudere = muratura
            // nuova (rossa) al posto dell'apertura.
            if (o.phase != Phase.Existing) {
                val (a, b) = Openings.span(r, o)
                val px = (r.thicknessOf(o.wallIndex) * cam.scale).toFloat().coerceAtLeast(2f)
                drawLine(WallColor, cam.o(a), cam.o(b), strokeWidth = px, cap = StrokeCap.Butt)
                drawLine(if (o.phase == Phase.New) PhaseDemolishColor else PhaseNewColor, cam.o(a), cam.o(b), strokeWidth = (px - 2f).coerceAtLeast(1f), cap = StrokeCap.Butt)
                if (o.phase == Phase.Demolish) continue
            }
            drawOpening(r, o, cam)
        }
        drawWallHeights(r, cam, measurer)
    }

    state.plan.room(state.focusedRoomId)?.let { r ->
        for (p in r.points) {
            drawCircle(Color.White, radius = 7.dp.toPx(), center = cam.o(p))
            drawCircle(SelectedColor, radius = 5.dp.toPx(), center = cam.o(p))
        }
    }
}

/** Etichetta di una stanza, già misurata: serve sia per disegnarla sia per il test di tocco. */
internal class RoomLabel(val room: Room, val anchor: Vec2, val text: TextLayoutResult) {
    fun rect(cam: Camera, pad: Float): Rect {
        val c = cam.o(anchor)
        val w = text.size.width / 2f + pad
        val h = text.size.height / 2f + pad
        return Rect(c.x - w, c.y - h, c.x + w, c.y + h)
    }
}

/**
 * Area calpestabile in m² (sul filo interno dei muri), con due decimali: stesso valore e formato su
 * pianta, schede, caratteristiche ed esportazione.
 */
/** Lunghezze dei muri sul lato interno della stanza, come vengono mostrate e modificate ovunque. */
internal fun interiorLengths(room: Room): List<Double> = room.interiorLengths()

internal fun formatArea(room: Room): String =
    com.sagoma.planimetria.geometry.formatDecimal(room.interiorArea() / 10_000.0, 2) + " m²"

/** Misura le etichette; `detailed` decide per quali stanze mostrare anche l'area. */
internal fun measureRoomLabels(plan: FloorPlan, measurer: TextMeasurer, detailed: (Room) -> Boolean): List<RoomLabel> =
    plan.rooms.map { room ->
        val full = detailed(room)
        val text = if (full) "${room.name}\n${formatArea(room)}" else room.name
        RoomLabel(
            room,
            Polygon.labelPoint(room.points),
            measurer.measure(
                text,
                TextStyle(fontSize = 13.sp, fontWeight = if (full) FontWeight.SemiBold else FontWeight.Normal, color = LabelTextColor),
            ),
        )
    }

internal fun DrawScope.drawRoomLabels(labels: List<RoomLabel>, cam: Camera, pad: Float, emphasized: (Room) -> Boolean) {
    for (l in labels) {
        val r = l.rect(cam, pad)
        drawRoundRect(
            color = Color.White.copy(alpha = if (emphasized(l.room)) 0.95f else 0.8f),
            topLeft = r.topLeft,
            size = r.size,
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        drawText(l.text, topLeft = Offset(r.left + pad, r.top + pad))
    }
}

/**
 * Quote come nelle planimetrie: per ogni muro di ogni stanza, dentro la stanza, una linea di quota
 * parallela al filo interno del muro, con le linee di richiamo agli angoli interni, i trattini obliqui
 * alle estremità e il valore in cm sopra la linea. La misura è quella interna (calpestabile), quindi
 * ogni stanza quota i propri muri al proprio interno: anche i muri in comune non si sovrappongono.
 * Il valore evita porte, finestre e impianti sul muro; se non ci sta sulla linea non viene scritto.
 */
internal fun DrawScope.drawWallDimensions(plan: FloorPlan, cam: Camera, measurer: TextMeasurer, avoid: List<Rect> = emptyList()) {
    val style = TextStyle(fontSize = 11.sp, color = DimensionColor)
    val stroke = 1.dp.toPx()
    val offset = 12.dp.toPx() // distanza della linea di quota dal filo interno del muro
    val tick = 4.dp.toPx()
    val gap = 2.dp.toPx()
    for (room in plan.rooms) {
        if (room.wallCount < 3) continue
        val inner = room.interior()
        val lengths = room.interiorLengths()
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        for (i in 0 until room.wallCount) {
            val len = lengths[i]
            val p = inner[i]
            val q = inner[(i + 1) % inner.size]
            val lenPx = (len * cam.scale).toFloat()
            if (len <= 0 || lenPx < 3 * offset) continue
            val u = (q - p).normalized()
            val n = u.perp() * sign // verso l'interno della stanza
            val uo = Offset(u.x.toFloat(), u.y.toFloat())
            val no = Offset(n.x.toFloat(), n.y.toFloat())
            val a = cam.o(p) + no * offset
            val b = cam.o(q) + no * offset
            // Linee di richiamo dal filo interno del muro, un poco oltre la linea di quota.
            drawLine(DimensionColor, cam.o(p) + no * gap, a + no * gap, strokeWidth = stroke * 0.7f)
            drawLine(DimensionColor, cam.o(q) + no * gap, b + no * gap, strokeWidth = stroke * 0.7f)
            // Linea di quota e trattini obliqui (a 45°) alle estremità.
            drawLine(DimensionColor, a, b, strokeWidth = stroke)
            val slash = (uo + no) * (tick / sqrt(2f))
            drawLine(DimensionColor, a - slash, a + slash, strokeWidth = stroke * 1.6f)
            drawLine(DimensionColor, b - slash, b + slash, strokeWidth = stroke * 1.6f)

            val t = measurer.measure(formatCm(len), style)
            val textLenCm = ((t.size.width + 4 * gap) / cam.scale).toDouble()
            if (t.size.width + 4 * gap > lenPx) continue
            // Posizione lungo il muro: il centro se libero, altrimenti il tratto libero più lungo.
            val free = Dimensions.freeAnchor(plan, room.wallStart(i), room.wallEnd(i), textLenCm)
            val preferred = ((free - p) dot u).coerceIn(textLenCm / 2, len - textLenCm / 2)
            // Ingombro sullo schermo del testo ruotato, per non finire sotto il nome della stanza.
            val w = t.size.width.toFloat()
            val h = t.size.height.toFloat()
            val boxW = abs(w * u.x.toFloat()) + abs(h * u.y.toFloat())
            val boxH = abs(w * u.y.toFloat()) + abs(h * u.x.toFloat())
            fun center(along: Double) = cam.o(p + u * along) + no * (offset + gap + h / 2f)
            fun clear(along: Double): Boolean {
                val c = center(along)
                val box = Rect(c.x - boxW / 2, c.y - boxH / 2, c.x + boxW / 2, c.y + boxH / 2)
                return avoid.none { it.overlaps(box) }
            }
            // Se il punto preferito è coperto dal nome, si prova più verso le estremità della linea.
            val candidates = listOf(preferred) + listOf(0.25, 0.75, 0.15, 0.85).map { (len * it).coerceIn(textLenCm / 2, len - textLenCm / 2) }
            val along = candidates.firstOrNull(::clear) ?: continue
            val c = center(along)
            // Testo parallelo al muro, mai capovolto.
            var deg = com.sagoma.planimetria.geometry.toDegrees(atan2(u.y, u.x)).toFloat()
            if (deg > 90f) deg -= 180f
            if (deg < -90f) deg += 180f
            rotate(deg, pivot = c) {
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
            }
        }
    }
}

/**
 * Sottotetti sulla pianta: per ogni stanza con pareti tagliate, una linea tratteggiata dove il soffitto
 * inizia a scendere verso il muro basso; sul muro selezionato con il taglio, il pallino da trascinare
 * (il punto da cui la parete inizia a scendere).
 */
internal fun DrawScope.drawWallCuts(state: EditorUiState) {
    val cam = state.camera
    val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
    for (room in state.plan.rooms) for ((a, b) in Ceilings.slopeStartLines(room)) {
        drawLine(CutColor, cam.o(a), cam.o(b), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
    }
    cutHandle(state)?.let { p ->
        drawCircle(Color.White, 9.dp.toPx(), cam.o(p))
        drawCircle(CutColor, 7.dp.toPx(), cam.o(p))
    }
}

/**
 * Avvisi di collisione: un segnale rosso con "!" sugli oggetti che arrivano più in alto della parete
 * (di solito sotto un taglio diagonale), un poco dentro la stanza così non copre il simbolo dell'oggetto.
 */
internal fun DrawScope.drawCollisionWarnings(state: EditorUiState, collisions: List<WallCollision>, measurer: TextMeasurer) {
    val cam = state.camera
    val text = measurer.measure("!", TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold))
    for (c in collisions) {
        val room = state.plan.room(c.roomId) ?: continue
        if (c.wallIndex >= room.wallCount) continue
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        val n = Openings.inwardNormal(room, c.wallIndex) * sign
        val center = cam.o(c.point) + Offset(n.x.toFloat(), n.y.toFloat()) * (Room.WALL_THICKNESS / 2 * cam.scale + 14.dp.toPx()).toFloat()
        drawCircle(Color.White, 10.dp.toPx(), center)
        drawCircle(WarningColor, 8.5.dp.toPx(), center)
        drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
    }
}

/** Pallino del punto di inizio discesa, solo per il muro selezionato che ha il taglio. */
internal fun cutHandle(state: EditorUiState): Vec2? {
    val sel = state.selection as? Selection.Wall ?: return null
    val room = state.plan.room(sel.roomId) ?: return null
    return Ceilings.startPoint(room, sel.index)
}

/**
 * Impianti di tutte le stanze. Si disegnano per ultimi, sopra le etichette delle stanze: le luci
 * stanno spesso proprio al centro della stanza, dove c'è l'etichetta.
 */
internal fun DrawScope.drawAllFixtures(state: EditorUiState) {
    val selFixture = state.selection as? Selection.Fixture
    for (r in state.plan.rooms) for (f in r.fixtures) {
        if (f.kind.mount == Mount.Wall && f.wallIndex >= r.wallCount) continue
        drawFixture(r, f, state.camera, selected = selFixture?.roomId == r.id && selFixture.fixtureId == f.id)
    }
}

/** Estremi del calorifero sul filo interno del muro (per disegno e tocco). */
internal fun radiatorSpan(room: Room, f: Fixture): Pair<Vec2, Vec2> {
    val (p, n) = Fixtures.wallAnchor(room, f)
    val u = n.perp() * -1.0 // lungo il muro (n = perp(u) ⇒ u = -perp(n))
    return (p - u * (f.length / 2)) to (p + u * (f.length / 2))
}

/**
 * Simboli degli impianti. Caloriferi, neon e strisce LED hanno misure reali (cm); prese, interruttori, punti acqua e
 * luci puntiformi hanno una dimensione fissa sullo schermo, così restano leggibili a ogni zoom.
 */
private fun DrawScope.drawFixture(room: Room, f: Fixture, cam: Camera, selected: Boolean) {
    val px = { dp: Float -> dp * density } // dp → px
    val toCm = { p: Float -> (p / cam.scale).toDouble() } // px → cm
    val thin = px(1.5f)
    val center = Fixtures.center(room, f)
    if (selected) {
        when {
            f.kind.linear -> Fixtures.linearEnds(f).let { (a, b) ->
                drawLine(SelectedColor.copy(alpha = 0.3f), cam.o(a), cam.o(b), strokeWidth = px(18f), cap = StrokeCap.Round)
            }
            f.kind == FixtureKind.Radiator || f.kind == FixtureKind.WallLedStrip -> radiatorSpan(room, f).let { (a, b) ->
                drawLine(SelectedColor.copy(alpha = 0.3f), cam.o(a), cam.o(b), strokeWidth = px(18f), cap = StrokeCap.Round)
            }
            else -> drawCircle(SelectedColor.copy(alpha = 0.3f), px(16f), cam.o(center))
        }
    }
    when (f.kind) {
        FixtureKind.Radiator -> {
            val (a, b) = radiatorSpan(room, f)
            val (_, n) = Fixtures.wallAnchor(room, f)
            val d = n * Fixtures.RADIATOR_DEPTH
            val path = Path().apply {
                listOf(a, b, b + d, a + d).forEachIndexed { i, p -> val o = cam.o(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                close()
            }
            drawPath(path, RadiatorColor.copy(alpha = 0.2f))
            drawPath(path, RadiatorColor, style = Stroke(thin))
            // Alette ogni 10 cm.
            val u = (b - a).normalized()
            var t = 10.0
            while (t < f.length) {
                val p = a + u * t
                drawLine(RadiatorColor, cam.o(p), cam.o(p + d), strokeWidth = thin / 2)
                t += 10.0
            }
        }
        FixtureKind.Outlet -> {
            val (p, n) = Fixtures.wallAnchor(room, f)
            val r = px(6f)
            val c = cam.o(p + n * toCm(r))
            drawCircle(Color.White, r, c)
            drawCircle(ElectricColor, r, c, style = Stroke(thin))
            // Due fori della presa, allineati al muro.
            val u = n.perp()
            val hole = Offset(u.x.toFloat(), u.y.toFloat()) * (r * 0.4f)
            drawCircle(ElectricColor, px(1.3f), c + hole)
            drawCircle(ElectricColor, px(1.3f), c - hole)
        }
        FixtureKind.Switch -> {
            val (p, n) = Fixtures.wallAnchor(room, f)
            val r = px(4f)
            val c = cam.o(p + n * toCm(r))
            drawCircle(ElectricColor, r, c)
            // Levetta dell'interruttore, inclinata di 45° verso l'interno.
            val lever = (n + n.perp()).normalized()
            drawLine(ElectricColor, c, c + Offset(lever.x.toFloat(), lever.y.toFloat()) * px(11f), strokeWidth = thin, cap = StrokeCap.Round)
        }
        FixtureKind.WaterPoint -> {
            val (p, n) = Fixtures.wallAnchor(room, f)
            val r = px(7f)
            val c = cam.o(p + n * toCm(r))
            drawCircle(Color.White, r, c)
            drawCircle(WaterColor, r, c, style = Stroke(thin))
            // Goccia d'acqua con la punta verso il muro.
            val toWall = Offset(-n.x.toFloat(), -n.y.toFloat())
            val side = Offset(-toWall.y, toWall.x)
            val body = c - toWall * px(1.2f)
            val drop = Path().apply {
                val tip = c + toWall * px(4.5f)
                moveTo(tip.x, tip.y)
                (body + side * px(2.6f)).let { lineTo(it.x, it.y) }
                (body - side * px(2.6f)).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(drop, WaterColor)
            drawCircle(WaterColor, px(2.6f), body)
        }
        // Luci a parete: sul filo interno del muro, con la sporgenza verso la stanza.
        FixtureKind.WallLight -> {
            // Plafoniera: rettangolo largo lungo il muro, con una linea al centro.
            val (p, n) = Fixtures.wallAnchor(room, f)
            val u = n.perp()
            val half = toCm(px(11f))
            val deep = toCm(px(7f))
            val path = Path().apply {
                listOf(p - u * half, p + u * half, p + u * half + n * deep, p - u * half + n * deep).forEachIndexed { i, q ->
                    val o = cam.o(q); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
            drawPath(path, LightColor.copy(alpha = 0.35f))
            drawPath(path, LightColor, style = Stroke(thin))
            drawLine(LightColor, cam.o(p + n * (deep / 2) - u * (half * 0.6)), cam.o(p + n * (deep / 2) + u * (half * 0.6)), strokeWidth = thin / 2)
        }
        FixtureKind.WallSpot -> {
            // Faretto: cerchietto sul muro con due raggi che si aprono verso la stanza.
            val (p, n) = Fixtures.wallAnchor(room, f)
            val u = n.perp()
            val r = px(4f)
            val c = cam.o(p + n * toCm(r))
            drawCircle(LightColor.copy(alpha = 0.35f), r, c)
            drawCircle(LightColor, r, c, style = Stroke(thin))
            val base = p + n * toCm(r * 2)
            for (side in listOf(-1.0, 1.0)) {
                drawLine(LightColor, cam.o(base + u * (side * toCm(r * 0.5f))), cam.o(base + n * toCm(px(9f)) + u * (side * toCm(px(5f)))), strokeWidth = thin / 2, cap = StrokeCap.Round)
            }
        }
        FixtureKind.WallLedStrip -> {
            // Striscia LED a parete: tratteggio lungo il muro (come quella a soffitto), appena dentro la stanza; segue il muro.
            val (a, b) = radiatorSpan(room, f)
            val (_, n) = Fixtures.wallAnchor(room, f)
            val off = n * toCm(px(3f))
            drawLine(LightColor.copy(alpha = 0.3f), cam.o(a + off), cam.o(b + off), strokeWidth = px(6f), cap = StrokeCap.Butt)
            drawLine(
                LightColor, cam.o(a + off), cam.o(b + off), strokeWidth = px(3f), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(2f), px(3f))),
            )
        }
        FixtureKind.Spotlight -> {
            val c = cam.o(center)
            drawCircle(LightColor.copy(alpha = 0.25f), px(6f), c)
            drawCircle(LightColor, px(6f), c, style = Stroke(thin))
            drawCircle(LightColor, px(2f), c)
        }
        FixtureKind.CeilingLight -> {
            val c = cam.o(center)
            drawCircle(LightColor.copy(alpha = 0.35f), px(10f), c)
            drawCircle(LightColor, px(10f), c, style = Stroke(thin))
            drawCircle(LightColor, px(5f), c, style = Stroke(thin))
        }
        FixtureKind.Chandelier -> {
            val c = cam.o(center)
            val r = px(9f)
            drawCircle(Color.White, r, c)
            drawCircle(LightColor, r, c, style = Stroke(thin))
            val k = r * 0.7f
            drawLine(LightColor, c + Offset(-k, -k), c + Offset(k, k), strokeWidth = thin)
            drawLine(LightColor, c + Offset(-k, k), c + Offset(k, -k), strokeWidth = thin)
            for (i in 0 until 8) {
                val ang = PI / 4 * i
                val dir = Offset(cos(ang).toFloat(), sin(ang).toFloat())
                drawLine(LightColor, c + dir * (r + px(2f)), c + dir * (r + px(6f)), strokeWidth = thin, cap = StrokeCap.Round)
            }
        }
        FixtureKind.Neon -> {
            val (a, b) = Fixtures.linearEnds(f)
            val w = maxOf((6.0 * cam.scale).toFloat(), px(4f))
            drawLine(LightColor.copy(alpha = 0.35f), cam.o(a), cam.o(b), strokeWidth = w, cap = StrokeCap.Butt)
            drawLine(LightColor, cam.o(a), cam.o(b), strokeWidth = thin)
            // Attacchi alle estremità.
            val u = (b - a).normalized().perp()
            val tick = u * toCm(w / 2 + px(2f))
            for (e in listOf(a, b)) drawLine(LightColor, cam.o(e - tick), cam.o(e + tick), strokeWidth = thin)
        }
        FixtureKind.LedStrip -> {
            val (a, b) = Fixtures.linearEnds(f)
            drawLine(
                LightColor, cam.o(a), cam.o(b), strokeWidth = px(3f), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(2f), px(3f))),
            )
        }
    }
}

/** Simboli architettonici standard delle aperture (§5). */
private fun DrawScope.drawOpening(room: Room, o: Opening, cam: Camera) {
    val (a, b) = Openings.span(room, o)
    val n = Openings.inwardNormal(room, o.wallIndex)
    val half = room.thicknessOf(o.wallIndex) / 2
    val line = 2.dp.toPx()
    val thin = 1.2.dp.toPx()
    val color = when {
        o.kind.glazed -> WindowColor // infissi: finestre e balconi
        o.kind == OpeningKind.Passage -> PassageColor
        else -> DoorColor
    }
    // Stipiti: tacche sottili attraverso lo spessore del muro ai due bordi del varco.
    for (p in listOf(a, b)) drawLine(color, cam.o(p - n * half), cam.o(p + n * half), strokeWidth = thin)
    // Infissi a terra (balconi): soglia lungo il muro, per distinguerli dalle finestre.
    if (o.kind.glazed && o.sillHeight == 0.0) drawLine(color.copy(alpha = 0.6f), cam.o(a), cam.o(b), strokeWidth = thin)

    if (o.sliding && o.kind.hasLeaves) {
        drawSliding(o, a, b, n, color, line, cam, room.thicknessOf(o.wallIndex))
        return
    }

    val swing = if (o.opensInward) n else n * -1.0
    when (o.kind.leaves) {
        1 -> {
            val hinge = if (o.hingeLeft) a else b
            val free = if (o.hingeLeft) b else a
            drawLeaf(hinge + swing * half, free + swing * half, swing, color, line, cam)
        }
        2 -> {
            // Due ante, ciascuna incernierata sul proprio lato esterno, che si aprono verso il centro.
            val mid = (a + b) / 2.0
            drawLeaf(a + swing * half, mid + swing * half, swing, color, line, cam)
            drawLeaf(b + swing * half, mid + swing * half, swing, color, line, cam)
        }
        else -> if (o.style == PassageStyle.Arched) {
            val mid = (a + b) / 2.0
            val ctrl = mid + n * (a.distanceTo(b) * 0.35)
            val path = Path().apply {
                val s = cam.o(a); val c = cam.o(ctrl); val e = cam.o(b)
                moveTo(s.x, s.y)
                quadraticTo(c.x, c.y, e.x, e.y)
            }
            drawPath(path, color, style = Stroke(width = thin))
        }
    }
}

/**
 * Ante scorrevoli: linee parallele al muro dentro il suo spessore, con una freccia per il verso.
 * - 1 anta: l'anta chiude tutto il varco e scorre verso il lato scelto; il tratteggio oltre lo
 *   stipite mostra dove va a finire da aperta.
 * - 2 ante: due ante sfalsate nello spessore, sovrapposte al centro, che scorrono in versi opposti.
 */
private fun DrawScope.drawSliding(o: Opening, a: Vec2, b: Vec2, n: Vec2, color: Color, width: Float, cam: Camera, thickness: Double) {
    val w = a.distanceTo(b)
    if (w < 1e-6) return
    val u = (b - a) / w
    val off = n * (thickness / 4)
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
    if (o.kind.leaves == 1) {
        val towardA = o.hingeLeft // "sinistra" guardando dall'interno = estremo a
        drawLine(color, cam.o(a + off), cam.o(b + off), strokeWidth = width, cap = StrokeCap.Round)
        val (s, e) = if (towardA) (a - u * w) to a else b to (b + u * w)
        drawLine(color.copy(alpha = 0.5f), cam.o(s + off), cam.o(e + off), strokeWidth = width / 2, pathEffect = dash)
        drawArrow((a + b) / 2.0 + off, if (towardA) u * -1.0 else u, color, cam)
    } else {
        val mid = (a + b) / 2.0
        val overlap = u * (w * 0.08)
        drawLine(color, cam.o(a + off), cam.o(mid + overlap + off), strokeWidth = width, cap = StrokeCap.Round)
        drawLine(color, cam.o(mid - overlap - off), cam.o(b - off), strokeWidth = width, cap = StrokeCap.Round)
        drawArrow((a + mid) / 2.0 + off, u, color, cam)
        drawArrow((mid + b) / 2.0 - off, u * -1.0, color, cam)
    }
}

/** Piccola freccia (punta a V) in `p` rivolta verso `dir`, di dimensione fissa sullo schermo. */
private fun DrawScope.drawArrow(p: Vec2, dir: Vec2, color: Color, cam: Camera) {
    val s = (5.dp.toPx() / cam.scale).toDouble() // lunghezza dei due tratti, in cm
    val back = p - dir * s
    val side = dir.perp() * (s * 0.6)
    val stroke = 1.2.dp.toPx()
    drawLine(color, cam.o(p), cam.o(back + side), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, cam.o(p), cam.o(back - side), strokeWidth = stroke, cap = StrokeCap.Round)
}

/** Anta aperta a 90° dal cardine + arco tratteggiato che ne mostra il raggio di apertura. */
private fun DrawScope.drawLeaf(hinge: Vec2, closedTip: Vec2, swing: Vec2, color: Color, width: Float, cam: Camera) {
    val r = hinge.distanceTo(closedTip)
    if (r < 1e-6) return
    val e1 = (closedTip - hinge) / r
    val openTip = hinge + swing * r
    drawLine(color, cam.o(hinge), cam.o(openTip), strokeWidth = width, cap = StrokeCap.Round)
    val arc = Path()
    val steps = 24
    for (k in 0..steps) {
        val t = PI / 2 * k / steps
        val p = cam.o(hinge + e1 * (cos(t) * r) + swing * (sin(t) * r))
        if (k == 0) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
    }
    drawPath(arc, color, style = Stroke(width = width / 2, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/** Etichetta "h.___cm" sui muri con altezza personalizzata (§2). */
private fun DrawScope.drawWallHeights(room: Room, cam: Camera, measurer: TextMeasurer) {
    for ((i, h) in room.wallHeights) {
        if (i >= room.wallCount) continue
        val mid = (room.wallStart(i) + room.wallEnd(i)) / 2.0
        // Dentro la stanza (anche se salvata in senso antiorario), oltre la linea di quota e il suo valore.
        val sign = if (Polygon.signedArea(room.points) >= 0) 1.0 else -1.0
        val pos = mid + Openings.inwardNormal(room, i) * (sign * (Room.WALL_THICKNESS / 2 + 48 * density / cam.scale))
        val t = measurer.measure("h.${h.toInt()}cm", TextStyle(fontSize = 11.sp, color = WallHeightColor))
        val o = cam.o(pos)
        drawText(t, topLeft = Offset(o.x - t.size.width / 2f, o.y - t.size.height / 2f))
    }
}
