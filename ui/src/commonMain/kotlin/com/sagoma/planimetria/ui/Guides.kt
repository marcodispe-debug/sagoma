package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.testi.Passo
import com.sagoma.planimetria.testi.T

/** Un passo di una miniguida: titolo, spiegazione e animazione. */
class GuideStep(val title: String, val text: String, val sketch: Sketch)

/** Miniguida passo passo su un argomento; `endsIn3d` mette "Apri la vista 3D" all'ultimo passo. */
class Guide(val icon: String, val title: String, val steps: List<GuideStep>, val endsIn3d: Boolean = false)

private fun step(s: TutorialStep) = GuideStep(s.title, s.text, s.sketch)

private fun passo(p: Passo, sketch: Sketch) = GuideStep(p.titolo, p.testo, sketch)

val AtticGuide: Guide get() = Guide(
    "⌂", T.guide.titoloMansarda,
    listOf(
        passo(T.guide.mansardaIntro, Sketch.AtticIntro),
        passo(T.guide.mansardaStanza, Sketch.AtticRoom),
        passo(T.guide.mansardaMuretto, Sketch.AtticLow),
        passo(T.guide.mansardaTaglio, Sketch.AtticCut),
        passo(T.guide.mansardaInizio, Sketch.AtticStart),
        passo(T.guide.mansardaRipeti, Sketch.AtticRepeat),
        passo(T.guide.mansardaControllo, Sketch.AtticCheck),
        passo(T.guide.mansarda3D, Sketch.Attic3D),
    ),
    endsIn3d = true,
)

private fun tip(t: Tip) = GuideStep(t.title, t.text, t.sketch)

/** Ricalcare una piantina: dal caricamento alle stanze ricalcate, con taratura e trasparenze. */
val TracingGuide: Guide get() = Guide(
    "🗺", T.guide.titoloRicalco,
    listOf(
        passo(T.guide.caricaPiantina, Sketch.Trace),
        tip(Tip.Calibrate),
        passo(T.guide.ricalcaAngoli, Sketch.Trace),
        tip(Tip.Underlay),
        passo(T.guide.controllaMisure, Sketch.OpenInfo),
    ),
)

/** Tutte le miniguide del pulsante "Guide", nell'ordine in cui conviene leggerle. */
val Guides: List<Guide> get() = listOf(
    TracingGuide,
    Guide(
        "▭", T.guide.titoloStanze,
        listOf(
            passo(T.guide.muoversi, Sketch.Welcome),
            step(TutorialStep.OpenInfo),
            tip(Tip.RoomInfo),
            step(TutorialStep.WallLengths),
            step(TutorialStep.ChangeType),
        ),
    ),
    Guide(
        "▧", T.guide.titoloZone,
        listOf(
            passo(T.guide.aggiungiZona, Sketch.OpenInfo),
            tip(Tip.ExcludedZone),
        ),
    ),
    Guide("✥", T.guide.titoloMuri, listOf(step(TutorialStep.MoveWall), step(TutorialStep.MoveCorner), step(TutorialStep.Undo))),
    Guide(
        "🧲", T.guide.titoloAgganci,
        listOf(
            passo(T.guide.agganci, Sketch.Snap),
        ),
    ),
    Guide(
        "🚪", T.guide.titoloPorte,
        listOf(step(TutorialStep.AddDoor), GuideStep(TutorialStep.MoveDoor.title, Tip.Opening.text, Sketch.MoveDoor)),
    ),
    Guide("🔌", T.guide.titoloImpianti, listOf(GuideStep(TutorialStep.AddFixture.title, TutorialStep.AddFixture.text + " " + Tip.Fixture.text, Sketch.AddFixture))),
    Guide("＋", T.guide.titoloPiuStanze, listOf(step(TutorialStep.AddRoom), step(TutorialStep.MoveRoom))),
    Guide(
        "🌿", T.guide.titoloBalconi,
        listOf(
            passo(T.guide.aggiungiBalcone, Sketch.Outdoor),
            passo(T.guide.parapetto, Sketch.Outdoor),
        ),
        endsIn3d = true,
    ),
    Guide(
        "🪜", T.guide.titoloScale,
        listOf(
            passo(T.guide.aggiungiScala, Sketch.AddStair),
            passo(T.guide.opzioniScala, Sketch.StairOptions),
            passo(T.guide.aggiungiPiano, Sketch.AddFloor),
            passo(T.guide.piani3D, Sketch.Floors3D),
        ),
        endsIn3d = true,
    ),
    Guide(
        "🧊", T.guide.titoloVista3D,
        listOf(
            GuideStep(Tip.View3D.title, Tip.View3D.text, Sketch.View3D),
            // Il suggerimento al primo uso resta corto: i comandi completi stanno qui.
            passo(T.guide.comandi3D, Sketch.View3D),
        ),
        endsIn3d = true,
    ),
    AtticGuide,
    Guide(
        "↔", T.guide.titoloQuote,
        listOf(
            passo(T.guide.quota, Sketch.Ruler),
            passo(T.guide.testo, Sketch.Ruler),
        ),
    ),
)

/** Miniguida passo passo: animazione, titolo e testo di ogni passo, con Avanti e Indietro. */
@Composable
fun GuideDialog(guide: Guide, onDismiss: () -> Unit, onOpen3d: () -> Unit) {
    var index by remember(guide) { mutableIntStateOf(0) }
    val step = guide.steps[index]
    val maxHeight = windowHeight() * 0.88f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${guide.icon} ${guide.title}", style = MaterialTheme.typography.titleLarge)
                        if (guide.steps.size > 1) {
                            Text(
                                T.guide.passoDi(index + 1, guide.steps.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) }
                }
                HorizontalDivider()
                // Altezza minima uguale per tutti i passi, così "Avanti" non si sposta cambiando pagina.
                Column(Modifier.weight(1f, fill = false).heightIn(min = 390.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8F9FA)) {
                        AnimatedSketch(step.sketch, Modifier.fillMaxWidth().height(180.dp).padding(6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(step.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(step.text, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index-- }, enabled = index > 0) { Text(T.comuni.indietroFreccia) }
                    Spacer(Modifier.weight(1f))
                    when {
                        index < guide.steps.lastIndex -> Button(onClick = { index++ }) { Text(T.comuni.avantiFreccia) }
                        guide.endsIn3d -> Button(onClick = { onOpen3d(); onDismiss() }) { Text(T.guide.apriVista3D) }
                        else -> Button(onClick = onDismiss) { Text(T.comuni.fine) }
                    }
                }
            }
        }
    }
}
