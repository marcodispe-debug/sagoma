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
import com.sagoma.planimetria.editor.OpeningPreset
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
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
                    Text(T.legenda.legenda, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) }
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
    preset: OpeningPreset = OpeningPreset.None,
): DrawScope.(TextMeasurer) -> Unit = { m ->
    val o = preset.apply(Opening.default(1, kind, 0, 100.0).copy(sliding = sliding, style = style)).copy(position = 100.0)
    drawSample(sampleRoom(openings = listOf(o)), m, TopWall)
}

/** Impianto a muro al centro del muro in alto, oppure luce al centro della stanza di prova. */
private fun fixture(kind: FixtureKind): DrawScope.(TextMeasurer) -> Unit = { m ->
    val f = Fixture.default(1, kind).let {
        if (kind.mount == Mount.Ceiling) it.copy(point = Vec2(100.0, 80.0), length = if (kind.linear) 140.0 else it.length)
        else if (kind == FixtureKind.WallLedStrip) it.copy(position = 100.0, length = 120.0)
        else it.copy(position = 100.0)
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
            T.legenda.stanzeMuri,
            listOf(
                LegendItem(T.legenda.muro, T.legenda.muriSpessi15Cm) { m ->
                    drawSample(sampleRoom(), m, WholeRoom)
                },
                LegendItem(T.legenda.stanzaSelezionata, T.legenda.colorePiuIntensoPallini) { m ->
                    drawSample(sampleRoom(), m, WholeRoom, focused = true)
                },
                LegendItem(T.legenda.altezzaMuroDiversa, T.legenda.muroHaAltezzaDiversa) { m ->
                    drawSample(sampleRoom(wallHeights = mapOf(0 to 220.0)), m, WholeRoom)
                },
                LegendItem(T.legenda.balconeTerrazza, T.legenda.spazioAllApertoDoppia) { m ->
                    drawSample(sampleRoom().copy(type = RoomType.Balcone), m, WholeRoom)
                },
                LegendItem(T.legenda.quota,T.legenda.lunghezzaInternaMuroCm) { m ->
                    drawDimensionSample(m)
                },
                LegendItem(
                    T.legenda.pareteSottoTetto,
                    T.legenda.tratteggioSegnaDoveSoffitto,
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
            T.legenda.porte,
            listOf(
                LegendItem(T.legenda.portaAd1Anta, T.legenda.arcoIndicaVersoApertura, opening(OpeningKind.Door)),
                LegendItem(T.legenda.porta2Ante, T.legenda.dueAnteApronoCentro, opening(OpeningKind.Door2)),
                LegendItem(T.legenda.portaScorrevole, T.legenda.antaNelloSpessoreMuro, opening(OpeningKind.Door, sliding = true)),
                LegendItem(T.editor.portaScorrevoleEsterna, T.legenda.antaSulMuro, opening(OpeningKind.Door, preset = OpeningPreset.WallSlide)),
                LegendItem(T.editor.portaALibro, T.legenda.pannelliPiegati, opening(OpeningKind.Door, preset = OpeningPreset.Folding)),
                LegendItem(T.editor.portaABilico, T.legenda.pernoAnta, opening(OpeningKind.Door, preset = OpeningPreset.Pivot)),
                LegendItem(T.legenda.aperturaSquadrata, T.legenda.varcoMuroSenzaPorta, opening(OpeningKind.Passage)),
                LegendItem(T.legenda.aperturaAdArco, T.legenda.varcoSenzaPortaArco, opening(OpeningKind.Passage, style = PassageStyle.Arched)),
                LegendItem(OpeningKind.Niche.label, T.legenda.nicchiaMezzoMuro, opening(OpeningKind.Niche)),
            ),
        ),
        LegendSection(
            T.legenda.infissi,
            listOf(
                LegendItem(T.legenda.finestraAd1Anta, T.legenda.vetroMuroAntaApre, opening(OpeningKind.Window1)),
                LegendItem(T.legenda.finestra2Ante, T.legenda.dueAnteApronoCentro, opening(OpeningKind.Window2)),
                LegendItem(T.legenda.finestraScorrevole, T.legenda.anteSovrapposteScorrono, opening(OpeningKind.Window2, sliding = true)),
                LegendItem(T.editor.finestraFissa, T.legenda.vetroFisso, opening(OpeningKind.Window1, preset = OpeningPreset.Fixed)),
                LegendItem(T.editor.finestraVasistas, T.legenda.vasistasRibalta, opening(OpeningKind.Window1, preset = OpeningPreset.Hopper)),
                LegendItem(T.editor.finestraBilico, T.legenda.perniOrizzontale, opening(OpeningKind.Window1, preset = OpeningPreset.PivotSash)),
                LegendItem(T.legenda.balconeAd1Anta, T.legenda.portaFinestraFinoTerra, opening(OpeningKind.Balcony1)),
                LegendItem(T.legenda.balcone2Ante, T.legenda.portaFinestraDueAnte, opening(OpeningKind.Balcony2)),
                LegendItem(T.legenda.balconeScorrevole, T.legenda.portaFinestraAnteScorrevoli, opening(OpeningKind.Balcony2, sliding = true)),
            ),
        ),
        LegendSection(
            T.legenda.impianti,
            listOf(
                LegendItem(T.legenda.calorifero, T.legenda.muroSuaLarghezzaReale, fixture(FixtureKind.Radiator)),
                LegendItem(T.legenda.presaCorrente, T.legenda.muroAltezzaTerra, fixture(FixtureKind.Outlet)),
                LegendItem(T.legenda.interruttore, T.legenda.muroAltezzaTerra, fixture(FixtureKind.Switch)),
                LegendItem(T.legenda.puntoAcqua, T.legenda.attaccoDellAcquaMuro, fixture(FixtureKind.WaterPoint)),
            ),
        ),
        LegendSection(
            T.legenda.luci,
            listOf(
                LegendItem(T.legenda.faretto, T.legenda.lucePuntiformeSoffitto, fixture(FixtureKind.Spotlight)),
                LegendItem(T.legenda.neonLungo, T.legenda.luceLineareSoffittoLunghezza, fixture(FixtureKind.Neon)),
                LegendItem(T.legenda.lampadario, T.legenda.lampadarioSoffitto, fixture(FixtureKind.Chandelier)),
                LegendItem(T.legenda.plafoniera, T.legenda.plafonieraSoffitto, fixture(FixtureKind.CeilingLight)),
                LegendItem(T.legenda.strisciaLed, T.legenda.strisciaLuminosaSoffittoLunghezza, fixture(FixtureKind.LedStrip)),
                LegendItem(T.legenda.plafonieraParete, T.legenda.fissataMuroAltezzaTerra, fixture(FixtureKind.WallLight)),
                LegendItem(T.legenda.farettoParete, T.legenda.fissatoMuroRivoltoVerso, fixture(FixtureKind.WallSpot)),
                LegendItem(T.legenda.strisciaLedParete, T.legenda.strisciaLuminosaMuroSegue, fixture(FixtureKind.WallLedStrip)),
            ),
        ),
        LegendSection(
            T.legenda.scalePiani,
            listOf(
                LegendItem(T.legenda.scala, T.legenda.pedateLineaSalitaPallino) { m ->
                    val cam = view(-10.0, -10.0, 250.0, 130.0)
                    val s = com.sagoma.planimetria.model.Stair(1, com.sagoma.planimetria.model.StairKind.Straight, Vec2(120.0, 60.0), rotation = 90.0)
                    drawStair(s, 150.0, cam, m, selected = false)
                },
                LegendItem(T.legenda.vuotoScala, T.legenda.pianoSopraBucoPavimento) { m ->
                    val cam = view(-10.0, -10.0, 250.0, 130.0)
                    val s = com.sagoma.planimetria.model.Stair(1, com.sagoma.planimetria.model.StairKind.Straight, Vec2(120.0, 60.0), rotation = 90.0)
                    val below = com.sagoma.planimetria.model.Floor(1, T.legenda.pianoTerra, FloorPlan(stairs = listOf(s)), levelHeight = 150.0)
                    val building = com.sagoma.planimetria.model.Building(listOf(below, com.sagoma.planimetria.model.Floor(2, T.legenda.primoPiano)), current = 1)
                    drawStairs(EditorUiState(building = building, camera = cam), m)
                },
                LegendItem(T.legenda.pianoSotto, T.legenda.tratteggioGrigioMuriPiano) { m ->
                    drawFloorBelow(FloorPlan(listOf(sampleRoom())), view(-15.0, -15.0, 215.0, 175.0))
                },
            ),
        ),
        LegendSection(
            T.legenda.colonneTravi,
            listOf(
                LegendItem(T.legenda.colonna, T.legenda.pienaMuriQuadrataRotonda) { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawColumn(com.sagoma.planimetria.model.Column(1, Vec2(70.0, 80.0)), cam, false)
                    drawColumn(com.sagoma.planimetria.model.Column(2, Vec2(170.0, 80.0), com.sagoma.planimetria.model.ColumnShape.Round), cam, false)
                },
                LegendItem(T.legenda.muroSingolo, T.legenda.tramezzoNonChiudeStanza) { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawFreeWall(com.sagoma.planimetria.model.FreeWall(1, Vec2(100.0, 7.5), Vec2(100.0, 110.0)), cam, m, false)
                },
                LegendItem(T.legenda.trave,T.legenda.tratteggiataPercheStaAlto) { m ->
                    val cam = view(-15.0, -15.0, 215.0, 175.0)
                    drawSample(sampleRoom(), m, WholeRoom)
                    drawBeam(com.sagoma.planimetria.model.Beam(1, Vec2(100.0, 7.5), Vec2(100.0, 152.5)), cam, m, false)
                },
            ),
        ),
        LegendSection(
            T.legenda.strumenti,
            listOf(
                LegendItem(T.legenda.metro, T.legenda.righelloLiberoTaccheOgni) { m ->
                    val cam = view(0.0, -10.0, 200.0, 110.0)
                    // In basso nel riquadro: la misura si scrive sopra il nastro.
                    val r = Ruler(1, Vec2(20.0, 75.0), Vec2(180.0, 75.0))
                    drawRuler(RulerGeom(r, cam, this), cam, rotating = false, measurer = m, showHandles = false)
                },
            ),
        ),
    )
}
