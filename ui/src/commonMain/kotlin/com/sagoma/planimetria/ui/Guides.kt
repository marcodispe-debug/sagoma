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

/** Un passo di una miniguida: titolo, spiegazione e animazione. */
class GuideStep(val title: String, val text: String, val sketch: Sketch)

/** Miniguida passo passo su un argomento; `endsIn3d` mette "Apri la vista 3D" all'ultimo passo. */
class Guide(val icon: String, val title: String, val steps: List<GuideStep>, val endsIn3d: Boolean = false)

private fun step(s: TutorialStep) = GuideStep(s.title, s.text, s.sketch)

val AtticGuide = Guide(
    "⌂", "Mansarda",
    listOf(
        GuideStep(
            "Com'è fatta una mansarda",
            "In una mansarda il soffitto scende verso un muro più basso (il \"muretto\" sotto la falda del tetto). " +
                "Le pareti laterali quindi non sono rettangolari: sono alte fino a un certo punto e poi si abbassano in " +
                "diagonale. In questa app si ottiene in due mosse: abbassi il muretto e tagli in diagonale le pareti accanto.",
            Sketch.AtticIntro,
        ),
        GuideStep(
            "1 · Disegna la stanza",
            "Crea la stanza con \"+ Aggiungi stanza\" e sistema le misure dal pannello Info, come per qualsiasi altra stanza. " +
                "L'altezza del soffitto della stanza è quella della parte più alta della mansarda.",
            Sketch.AtticRoom,
        ),
        GuideStep(
            "2 · Abbassa il muretto",
            "Tocca il muro verso cui scende il tetto, apri Info e scrivi la sua altezza reale in \"Altezza muro\" " +
                "(per esempio 120 cm). Sulla pianta compare l'altezza in viola accanto al muro.",
            Sketch.AtticLow,
        ),
        GuideStep(
            "3 · Taglia la prima parete laterale",
            "Tocca una delle due pareti perpendicolari al muretto e, nel pannello Info, attiva \"Taglio diagonale (sottotetto)\". " +
                "Se il muretto è più basso, la parete ora scende fino alla sua altezza. Se entrambi i muri accanto sono bassi, " +
                "scegli in \"Scende verso\" da che parte.",
            Sketch.AtticCut,
        ),
        GuideStep(
            "4 · Scegli dove inizia a scendere",
            "Misura in casa a che distanza dal muretto il soffitto comincia a inclinarsi e scrivila in \"Inizia a scendere a\". " +
                "In alternativa trascina il pallino arancione sulla pianta: la linea tratteggiata segna l'inizio della discesa.",
            Sketch.AtticStart,
        ),
        GuideStep(
            "5 · Ripeti sull'altra parete",
            "Fai lo stesso sulla parete di fronte, così il soffitto scende in modo uguale su tutta la stanza. " +
                "Se la parete è in comune con un'altra stanza, viene tagliato solo il lato della mansarda: la stanza accanto resta intera.",
            Sketch.AtticRepeat,
        ),
        GuideStep(
            "6 · Controlla porte, finestre e impianti",
            "Se una finestra, una porta, un termosifone o una presa finisce più in alto della parete inclinata, compare un " +
                "pallino rosso con \"!\" sulla pianta e un avviso nel pannello Info: abbassa l'oggetto o spostalo lungo il muro " +
                "verso la parte alta.",
            Sketch.AtticCheck,
        ),
        GuideStep(
            "7 · Guarda il risultato in 3D",
            "Apri la vista 3D: il bordo delle pareti e il soffitto scendono in diagonale e puoi camminare dentro la mansarda " +
                "per controllare che tutto torni. Il volume della stanza nel pannello Info tiene già conto del soffitto inclinato.",
            Sketch.Attic3D,
        ),
    ),
    endsIn3d = true,
)

/** Tutte le miniguide del pulsante "Guide", nell'ordine in cui conviene leggerle. */
val Guides: List<Guide> = listOf(
    Guide(
        "▭", "Stanze e misure",
        listOf(
            GuideStep("Muoversi nella pianta", Tip.Welcome.text, Sketch.Welcome),
            step(TutorialStep.OpenInfo),
            step(TutorialStep.WallLengths),
            step(TutorialStep.ChangeType),
        ),
    ),
    Guide("✥", "Muri e angoli", listOf(step(TutorialStep.MoveWall), step(TutorialStep.MoveCorner), step(TutorialStep.Undo))),
    Guide(
        "🚪", "Porte e finestre",
        listOf(step(TutorialStep.AddDoor), GuideStep(TutorialStep.MoveDoor.title, Tip.Opening.text, Sketch.MoveDoor)),
    ),
    Guide("🔌", "Impianti", listOf(GuideStep(TutorialStep.AddFixture.title, TutorialStep.AddFixture.text + " " + Tip.Fixture.text, Sketch.AddFixture))),
    Guide("＋", "Più stanze", listOf(step(TutorialStep.AddRoom), step(TutorialStep.MoveRoom))),
    Guide(
        "🌿", "Balconi e terrazze",
        listOf(
            GuideStep(
                "Aggiungi un balcone o una terrazza",
                "Premi + Balconi e terrazze e scegli il tipo, poi forma e misure come per una stanza. Nasce già appoggiato alla casa: " +
                    "trascinalo dal nome lungo il muro fino al punto giusto, si aggancia da solo. Sui lati verso l'esterno c'è il parapetto, " +
                    "contro la casa resta il muro.",
                Sketch.Outdoor,
            ),
            GuideStep(
                "Parapetto e porta-finestra",
                Tip.Outdoor.text,
                Sketch.Outdoor,
            ),
        ),
        endsIn3d = true,
    ),
    Guide(
        "🪜", "Scale e piani",
        listOf(
            GuideStep(
                "Aggiungi una scala",
                "Premi + Scale e scegli il tipo: rampa dritta, a L con pianerottolo, a U con due rampe o a chiocciola. " +
                    "La scala compare al centro della vista (o della stanza selezionata): trascinala dove serve, vicino a un muro ci si accosta da sola.",
                Sketch.AddStair,
            ),
            GuideStep(
                "Tipo, misure e verso",
                "Tocca la scala e apri Info: cambi tipo, larghezza, pedata (o diametro della chiocciola), da che parte gira e la rotazione, " +
                    "oppure la giri di 90° con ↻. Numero e altezza dei gradini si calcolano dall'interpiano; se i gradini sono scomodi compare un avviso.",
                Sketch.StairOptions,
            ),
            GuideStep(
                "Aggiungi un piano",
                "Tocca il pulsante del piano in alto a sinistra e scegli «Aggiungi un piano sopra». Puoi partire dai muri del piano di sotto " +
                    "o da un piano vuoto. Il piano di sotto resta visibile in grigio e le sue scale compaiono come vuoto nel pavimento.",
                Sketch.AddFloor,
            ),
            GuideStep(
                "I piani in 3D",
                "In 3D vedi il piano scelto sopra quelli di sotto, collegati dalle scale; i piani più alti si nascondono, come se si " +
                    "togliesse il tetto. Dallo stesso menu del piano cambi nome e interpiano, o lo elimini.",
                Sketch.Floors3D,
            ),
        ),
        endsIn3d = true,
    ),
    Guide("🧊", "Vista 3D", listOf(GuideStep(Tip.View3D.title, Tip.View3D.text, Sketch.View3D)), endsIn3d = true),
    AtticGuide,
    Guide("📏", "Metro", listOf(step(TutorialStep.Ruler))),
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
                                "Passo ${index + 1} di ${guide.steps.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
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
                    TextButton(onClick = { index-- }, enabled = index > 0) { Text("‹ Indietro") }
                    Spacer(Modifier.weight(1f))
                    when {
                        index < guide.steps.lastIndex -> Button(onClick = { index++ }) { Text("Avanti ›") }
                        guide.endsIn3d -> Button(onClick = { onOpen3d(); onDismiss() }) { Text("Apri la vista 3D") }
                        else -> Button(onClick = onDismiss) { Text("Fine") }
                    }
                }
            }
        }
    }
}
