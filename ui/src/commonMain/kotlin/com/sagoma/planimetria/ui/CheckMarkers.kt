package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Un «!» sulla pianta: il punto (cm), se è quello del nome di una stanza, e i problemi lì (prima i più gravi). */
internal class CheckMark(val at: Vec2, val room: Boolean, val issues: List<PlanCheck.Issue>)

/**
 * I «!» dei problemi del piano `floor` sulla pianta `plan` (com'è adesso), senza quelli tolti dalla pianta: i problemi nello
 * stesso punto (le norme di una stanza) fanno un segno solo. `issues` già in ordine, prima gli errori ([PlanCheck.of]).
 */
internal fun checkMarks(issues: List<PlanCheck.Issue>, building: Building, floor: Int, plan: FloorPlan): List<CheckMark> =
    issues.asSequence()
        .filter { it.floor == floor && !PlanCheck.hidden(building, it) }
        .mapNotNull { i -> PlanCheck.spot(i, plan)?.let { it to i } }
        .groupBy { (s, _) -> Triple(s.at.x.roundToLong(), s.at.y.roundToLong(), s.room) }
        .map { (_, list) -> CheckMark(list.first().first.at, list.first().first.room, list.map { it.second }) }

/** Colori del «!»: rosso per gli errori di disegno (⚠), giallo per le cose da controllare (?), blu per le norme (📏). */
@Composable
private fun markColors(kind: PlanCheck.Kind): Pair<Color, Color> = when {
    kind.error -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    kind.rule -> Color(0xFF1E6FD9) to Color.White
    else -> Color(0xFFF2B01E) to Color(0xFF2B2100)
}

/**
 * Lato del tocco del «!» (il cerchio è un po' più piccolo): non di più, perché sotto ci sono la pianta e le sue cose da toccare
 * (la plafoniera al centro della stanza).
 */
private val MARK_TOUCH = 28.dp
private val MARK_SIZE = 22.dp

/**
 * Il «!» del problema di una stanza sta tanto sopra il punto del nome: non copre il nome (due righe) né la plafoniera che spesso
 * sta proprio lì, al centro.
 */
private val ROOM_MARK_LIFT = 38.dp

/** Gli altri «!» stanno tanto in alto a destra del punto: la porta, l'impianto o il mobile si toccano ancora nel mezzo. */
private val MARK_NUDGE = 20.dp

/**
 * I «!» sulla pianta, sopra il disegno ([Camera] della pianta 2D); il tocco su un segno chiama `onOpen`. Fuori dallo schermo
 * non si mettono; fuori dai segni il tocco va alla pianta.
 */
@Composable
internal fun CheckMarkers(marks: List<CheckMark>, camera: Camera, onOpen: (CheckMark) -> Unit, modifier: Modifier = Modifier) {
    if (marks.isEmpty()) return
    val density = LocalDensity.current
    val touch = with(density) { MARK_TOUCH.toPx() }
    val lift = with(density) { ROOM_MARK_LIFT.toPx() }
    val nudge = with(density) { MARK_NUDGE.toPx() }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        for (m in marks) {
            val x = camera.toScreenX(m.at.x) + if (m.room) 0f else nudge
            val y = camera.toScreenY(m.at.y) - if (m.room) lift else nudge
            if (!x.isFinite() || !y.isFinite() || x < -touch || y < -touch || x > w + touch || y > h + touch) continue
            key(m.at, m.room) {
                CheckMarker(m, onClick = { onOpen(m) }, modifier = Modifier.offset { IntOffset((x - touch / 2).roundToInt(), (y - touch / 2).roundToInt()) })
            }
        }
    }
}

@Composable
private fun CheckMarker(m: CheckMark, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (color, content) = markColors(m.issues.first().kind)
    val label = T.controllo.problemiQui(m.issues.size)
    Box(
        modifier.size(MARK_TOUCH).clip(CircleShape).clickable(onClick = onClick).semantics {
            contentDescription = label
            role = Role.Button
        },
    ) {
        Box(Modifier.align(Alignment.Center).size(MARK_SIZE).shadow(2.dp, CircleShape).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text("!", color = content, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        // Più problemi nello stesso punto: quanti, in un cerchietto in alto a destra.
        if (m.issues.size > 1) Surface(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp).size(16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) { Text("${m.issues.size}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * Scheda dei problemi di un «!» toccato sulla pianta: per ognuno cosa non va, «Correggi» dove si può (per le ante che urtano
 * appena si sa che girarle basta), «Mostra» e «Togli dalla pianta»; in fondo «Chiudi».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckSpotCard(
    issues: List<PlanCheck.Issue>,
    building: Building,
    onFix: (PlanCheck.Issue) -> Unit,
    onShow: (PlanCheck.Issue) -> Unit,
    onHide: (PlanCheck.Issue) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turnable by produceState(emptySet<PlanCheck.Issue>(), building, issues) {
        value = withContext(Dispatchers.Default) {
            issues.filterTo(HashSet()) { PlanCheck.triesFirst(it) && runCatching { PlanCheck.canFix(building, it) }.getOrDefault(false) }
        }
    }
    val error = issues.firstOrNull()?.kind?.error == true
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val buttons = ButtonDefaults.textButtonColors(contentColor = if (error) content else MaterialTheme.colorScheme.primary)
    val where = { i: PlanCheck.Issue -> if (building.floors.size > 1) building.floors.getOrNull(i.floor)?.name?.let { "$it · " } ?: "" else "" }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        contentColor = content,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 10.dp, end = 4.dp)) {
            for (i in issues) {
                Text(
                    PlanCheck.mark(i.kind) + " " + where(i) + PlanCheck.describe(i, building),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
                // Sul telefono i tre pulsanti possono non starci in una riga: vanno a capo, a destra.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val fixable = if (PlanCheck.triesFirst(i)) i in turnable else PlanCheck.canFix(i)
                    if (fixable) TextButton(onClick = { onFix(i) }, colors = buttons) { Text("✓ " + T.controllo.correggi) }
                    TextButton(onClick = { onShow(i) }, colors = buttons) { Text(T.controllo.mostra) }
                    TextButton(onClick = { onHide(i) }, colors = buttons) { Text(T.controllo.togliDallaPianta) }
                }
            }
            TextButton(onClick = onClose, colors = buttons, modifier = Modifier.align(Alignment.End)) { Text(T.controllo.chiudi) }
        }
    }
}
