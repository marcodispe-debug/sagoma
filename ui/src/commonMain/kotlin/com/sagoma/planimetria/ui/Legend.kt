package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import kotlin.math.min
import kotlin.math.sqrt

/** Pulsante tondeggiante sulla pianta, in basso a destra ("Legenda", "Vista 3D", ...). */
@Composable
fun LegendButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Una voce della legenda: il simbolo (disegnato con lo stesso codice della pianta) e cosa significa. */
private class LegendItem(val title: String, val text: String, val draw: DrawScope.(TextMeasurer) -> Unit)

private class LegendSection(val title: String, val items: List<LegendItem>)

/** Spiegazione di tutti i simboli della pianta, divisi per gruppo. */
@Composable
fun LegendDialog(onDismiss: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val maxHeight = windowHeight() * 0.85f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Legenda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
                HorizontalDivider()
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    for (section in legendSections) {
                        item {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(section.items) { LegendRow(it, measurer) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(item: LegendItem, measurer: TextMeasurer) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(8.dp))) {
            drawRect(PlanBackgroundColor)
            item.draw(this, measurer)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Campioni dei simboli ----------

/** Stanza di prova 200×160 cm: il muro 0 è quello in alto, da sinistra a destra. */
private fun sampleRoom(
    openings: List<Opening> = emptyList(),
    fixtures: List<Fixture> = emptyList(),
    wallHeights: Map<Int, Double> = emptyMap(),
) = Room(
    id = 1,
    name = "",
    type = RoomType.Altro,
    points = listOf(Vec2(0.0, 0.0), Vec2(200.0, 0.0), Vec2(200.0, 160.0), Vec2(0.0, 160.0)),
    wallHeights = wallHeights,
    openings = openings,
    fixtures = fixtures,
)

/** Vista che inquadra il rettangolo di pianta indicato (in cm) al centro del riquadro. */
private fun DrawScope.view(minX: Double, minY: Double, maxX: Double, maxY: Double): Camera {
    val s = min(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
    return Camera(
        s,
        ((size.width - (maxX - minX) * s) / 2 - minX * s).toFloat(),
        ((size.height - (maxY - minY) * s) / 2 - minY * s).toFloat(),
    )
}

private fun DrawScope.drawSample(
    room: Room,
    measurer: TextMeasurer,
    window: DoubleArray,
    focused: Boolean = false,
) {
    val cam = view(window[0], window[1], window[2], window[3])
    val state = EditorUiState(plan = FloorPlan(listOf(room)), camera = cam, focusedRoomId = if (focused) room.id else null)
    drawPlan(state, measurer)
    drawAllFixtures(state)
}

/** Parte alta della stanza di prova, dove stanno aperture e impianti a muro. */
private val TopWall = doubleArrayOf(-10.0, -45.0, 210.0, 105.0)
private val WholeRoom = doubleArrayOf(-15.0, -15.0, 215.0, 175.0)

/** Stessi colori della pianta, per il campione della quota disegnato a mano. */
private val WallSampleColor = androidx.compose.ui.graphics.Color(0xFF3A3F44)
private val DimensionSampleColor = androidx.compose.ui.graphics.Color(0xFF495057)

/** Apertura al centro del muro in alto della stanza di prova. */
private fun opening(
    kind: OpeningKind,
    sliding: Boolean = false,
    style: PassageStyle = PassageStyle.Square,
): DrawScope.(TextMeasurer) -> Unit = { m ->
    val o = Opening.default(1, kind, 0, 100.0).copy(sliding = sliding, style = style)
    drawSample(sampleRoom(openings = listOf(o)), m, TopWall)
}

/** Impianto a muro al centro del muro in alto, oppure luce al centro della stanza di prova. */
private fun fixture(kind: FixtureKind): DrawScope.(TextMeasurer) -> Unit = { m ->
    val f = Fixture.default(1, kind).let {
        if (kind.mount == Mount.Ceiling) it.copy(point = Vec2(100.0, 80.0), length = if (kind.linear) 140.0 else it.length) else it.copy(position = 100.0)
    }
    drawSample(sampleRoom(fixtures = listOf(f)), m, if (kind.mount == Mount.Ceiling) WholeRoom else TopWall)
}

/** Quota come sulla pianta: filo interno del muro, linee di richiamo, trattini obliqui e valore. */
private fun DrawScope.drawDimensionSample(measurer: TextMeasurer) {
    val wall = 6.dp.toPx()
    val left = 10.dp.toPx()
    val right = size.width - 10.dp.toPx()
    val wallY = 10.dp.toPx()
    drawRect(WallSampleColor, topLeft = Offset(0f, wallY - wall), size = androidx.compose.ui.geometry.Size(size.width, wall))
    val lineY = wallY + 12.dp.toPx()
    val stroke = 1.dp.toPx()
    val gap = 2.dp.toPx()
    drawLine(DimensionSampleColor, Offset(left, wallY + gap), Offset(left, lineY + gap), strokeWidth = stroke * 0.7f)
    drawLine(DimensionSampleColor, Offset(right, wallY + gap), Offset(right, lineY + gap), strokeWidth = stroke * 0.7f)
    drawLine(DimensionSampleColor, Offset(left, lineY), Offset(right, lineY), strokeWidth = stroke)
    val slash = Offset(1f, 1f) * (4.dp.toPx() / sqrt(2f))
    drawLine(DimensionSampleColor, Offset(left, lineY) - slash, Offset(left, lineY) + slash, strokeWidth = stroke * 1.6f)
    drawLine(DimensionSampleColor, Offset(right, lineY) - slash, Offset(right, lineY) + slash, strokeWidth = stroke * 1.6f)
    val t = measurer.measure("385", TextStyle(fontSize = 11.sp, color = DimensionSampleColor))
    drawText(t, topLeft = Offset((size.width - t.size.width) / 2f, lineY + gap))
}

private val legendSections: List<LegendSection> by lazy {
    listOf(
        LegendSection(
            "Stanze e muri",
            listOf(
                LegendItem("Muro", "Muri spessi 15 cm. Il colore dell'interno indica il tipo di stanza.") { m ->
                    drawSample(sampleRoom(), m, WholeRoom)
                },
                LegendItem("Stanza selezionata", "Colore più intenso e pallini blu sugli angoli: trascinali per cambiare la forma.") { m ->
                    drawSample(sampleRoom(), m, WholeRoom, focused = true)
                },
                LegendItem("Altezza muro diversa", "Il muro ha un'altezza diversa da quella del soffitto (es. mansarda).") { m ->
                    drawSample(sampleRoom(wallHeights = mapOf(0 to 220.0)), m, WholeRoom)
                },
                LegendItem("Balcone o terrazza", "Spazio all'aperto: la doppia linea sottile è la ringhiera (pieno = muretto, azzurro = vetro).") { m ->
                    drawSample(sampleRoom().copy(type = RoomType.Balcone), m, WholeRoom)
                },
                LegendItem("Quota","Lunghezza interna del muro in cm, misurata sul filo interno. Si cambia da Info della stanza.") { m ->
                    drawDimensionSample(m)
                },
                LegendItem(
                    "Parete sotto il tetto",
                    "Il tratteggio segna dove il soffitto inizia a scendere verso il muro basso: da lì le pareti sono tagliate in diagonale.",
                ) { m ->
                    // Muro di destra basso; i muri in alto e in basso scendono verso di lui dagli ultimi 80 cm.
                    val cuts = mapOf(
                        0 to com.sagoma.planimetria.model.WallCut(towardEnd = true, start = 80.0),
                        2 to com.sagoma.planimetria.model.WallCut(towardEnd = false, start = 80.0),
                    )
                    val room = sampleRoom().copy(wallCuts = cuts, wallHeights = mapOf(1 to 200.0))
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    val state = EditorUiState(plan = FloorPlan(listOf(room)), camera = cam)
                    drawPlan(state, m)
                    drawWallCuts(state)
                },
            ),
        ),
        LegendSection(
            "Porte",
            listOf(
                LegendItem("Porta ad 1 anta", "L'arco indica il verso di apertura e il lato del cardine.", opening(OpeningKind.Door)),
                LegendItem("Porta a 2 ante", "Due ante che si aprono dal centro.", opening(OpeningKind.Door2)),
                LegendItem("Porta scorrevole", "Anta nello spessore del muro; la freccia indica dove scorre.", opening(OpeningKind.Door, sliding = true)),
                LegendItem("Apertura squadrata", "Varco nel muro senza porta.", opening(OpeningKind.Passage)),
                LegendItem("Apertura ad arco", "Varco senza porta con l'arco in alto.", opening(OpeningKind.Passage, style = PassageStyle.Arched)),
            ),
        ),
        LegendSection(
            "Infissi",
            listOf(
                LegendItem("Finestra ad 1 anta", "Vetro nel muro con l'anta che si apre.", opening(OpeningKind.Window1)),
                LegendItem("Finestra a 2 ante", "Due ante che si aprono dal centro.", opening(OpeningKind.Window2)),
                LegendItem("Finestra scorrevole", "Ante sovrapposte che scorrono.", opening(OpeningKind.Window2, sliding = true)),
                LegendItem("Balcone ad 1 anta", "Porta-finestra fino a terra: ha la soglia disegnata.", opening(OpeningKind.Balcony1)),
                LegendItem("Balcone a 2 ante", "Porta-finestra a due ante fino a terra.", opening(OpeningKind.Balcony2)),
                LegendItem("Balcone scorrevole", "Porta-finestra con ante scorrevoli.", opening(OpeningKind.Balcony2, sliding = true)),
            ),
        ),
        LegendSection(
            "Impianti",
            listOf(
                LegendItem("Calorifero", "Sul muro, con la sua larghezza reale.", fixture(FixtureKind.Radiator)),
                LegendItem("Presa di corrente", "Sul muro, con l'altezza da terra.", fixture(FixtureKind.Outlet)),
                LegendItem("Interruttore", "Sul muro, con l'altezza da terra.", fixture(FixtureKind.Switch)),
                LegendItem("Punto acqua", "Attacco dell'acqua sul muro; la goccia punta verso il muro.", fixture(FixtureKind.WaterPoint)),
            ),
        ),
        LegendSection(
            "Luci",
            listOf(
                LegendItem("Faretto", "Luce puntiforme a soffitto.", fixture(FixtureKind.Spotlight)),
                LegendItem("Neon lungo", "Luce lineare a soffitto, con lunghezza e rotazione.", fixture(FixtureKind.Neon)),
                LegendItem("Lampadario", "Lampadario a soffitto.", fixture(FixtureKind.Chandelier)),
                LegendItem("Plafoniera", "Plafoniera a soffitto.", fixture(FixtureKind.CeilingLight)),
                LegendItem("Striscia LED", "Striscia luminosa a soffitto, con lunghezza e rotazione.", fixture(FixtureKind.LedStrip)),
                LegendItem("Plafoniera a parete", "Fissata al muro, con l'altezza da terra (di solito 220 cm).", fixture(FixtureKind.WallLight)),
                LegendItem("Faretto a parete", "Fissato al muro e rivolto verso la stanza, con l'altezza da terra.", fixture(FixtureKind.WallSpot)),
            ),
        ),
        LegendSection(
            "Scale e piani",
            listOf(
                LegendItem("Scala", "Pedate, linea di salita con il pallino alla partenza e la freccia all'arrivo (\"sale\").") { m ->
                    val cam = view(-10.0, -10.0, 250.0, 130.0)
                    val s = com.sagoma.planimetria.model.Stair(1, com.sagoma.planimetria.model.StairKind.Straight, Vec2(120.0, 60.0), rotation = 90.0)
                    drawStair(s, 150.0, cam, m, selected = false)
                },
                LegendItem("Vuoto scala", "Al piano di sopra: il buco nel pavimento dove arriva la scala, tratteggiato, con la freccia che scende.") { m ->
                    val cam = view(-10.0, -10.0, 250.0, 130.0)
                    val s = com.sagoma.planimetria.model.Stair(1, com.sagoma.planimetria.model.StairKind.Straight, Vec2(120.0, 60.0), rotation = 90.0)
                    val below = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(stairs = listOf(s)), levelHeight = 150.0)
                    val building = com.sagoma.planimetria.model.Building(listOf(below, com.sagoma.planimetria.model.Floor(2, "Primo piano")), current = 1)
                    drawStairs(EditorUiState(building = building, camera = cam), m)
                },
                LegendItem("Piano di sotto", "Tratteggio grigio: i muri del piano di sotto, per allineare quelli del piano che stai disegnando.") { m ->
                    drawFloorBelow(FloorPlan(listOf(sampleRoom())), view(-15.0, -15.0, 215.0, 175.0))
                },
            ),
        ),
        LegendSection(
            "Colonne e travi",
            listOf(
                LegendItem("Colonna", "Piena, come i muri: quadrata o rotonda, in mezzo alla stanza o accostata a un muro (pilastro).") { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawColumn(com.sagoma.planimetria.model.Column(1, Vec2(70.0, 80.0)), cam, false)
                    drawColumn(com.sagoma.planimetria.model.Column(2, Vec2(170.0, 80.0), com.sagoma.planimetria.model.ColumnShape.Round), cam, false)
                },
                LegendItem("Muro singolo", "Tramezzo che non chiude una stanza, pieno come i muri, con la sua lunghezza accanto.") { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawFreeWall(com.sagoma.planimetria.model.FreeWall(1, Vec2(100.0, 7.5), Vec2(100.0, 110.0)), cam, m, false)
                },
                LegendItem("Trave","Tratteggiata perché sta in alto, a soffitto: larghezza × quanto sporge sotto il soffitto.") { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawBeam(com.sagoma.planimetria.model.Beam(1, Vec2(100.0, 7.5), Vec2(100.0, 152.5)), cam, m, false)
                },
            ),
        ),
        LegendSection(
            "Strumenti",
            listOf(
                LegendItem("Metro", "Righello libero con tacche ogni 10 cm e la misura in cm.") { m ->
                    val cam = view(0.0, -10.0, 200.0, 110.0)
                    // In basso nel riquadro: la misura si scrive sopra il nastro.
                    val r = Ruler(1, Vec2(20.0, 75.0), Vec2(180.0, 75.0))
                    drawRuler(RulerGeom(r, cam, this), cam, rotating = false, measurer = m, showHandles = false)
                },
            ),
        ),
    )
}
