package com.sagoma.planimetria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.FixSummary
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.LiveCheck
import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.testi.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Controlla la pianta»: l'elenco dei problemi di tutta la casa, prima quelli da correggere, poi quelli da controllare e infine
 * i minimi delle norme per la casa. «Mostra» porta al piano giusto, seleziona e inquadra (la finestra si chiude). «Correggi», dove
 * c'è ([PlanCheck.canFix]; per le ante che urtano compare appena si sa che girarle basta), sistema il problema da solo e la
 * finestra resta aperta con l'elenco nuovo e in cima cosa è cambiato ([FixSummary]; si annulla con ↶); «Correggi tutti» lo fa
 * per tutto un gruppo, con un solo ↶ ([PlanCheck.fixedAll]). «Togli dalla pianta» toglie il «!» del problema dalla pianta (e
 * dal conto del segnale): il problema resta qui, e si rimette. «Condividi» manda l'elenco come testo (`projectName` nell'oggetto).
 * Ogni gruppo ha il numero dei problemi e si apre e chiude toccando il titolo; all'inizio sono aperti il primo e quelli corti (al
 * massimo [OPEN_GROUP_MAX]).
 */
@Composable
fun PlanCheckDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit, projectName: String? = null) {
    val platform = LocalPlatform.current
    val building = state.fullBuilding
    // Pochi millesimi di secondo anche per una casa di 100 stanze (al massimo [PlanCheck.MAX_ISSUES] problemi).
    val issues = remember(building) { runCatching { PlanCheck.of(building) }.getOrDefault(emptyList()) }
    // Le ante che urtano: girarle si prova fuori dal disegno, e «Correggi» compare solo se basta.
    val turnable by produceState(emptySet<PlanCheck.Issue>(), building, issues) {
        value = withContext(Dispatchers.Default) {
            issues.filterTo(HashSet()) { PlanCheck.triesFirst(it) && runCatching { PlanCheck.canFix(building, it) }.getOrDefault(false) }
        }
    }
    // «Correggi tutti» lavora fuori dal disegno: intanto i pulsanti aspettano.
    val scope = rememberCoroutineScope()
    var fixing by remember { mutableStateOf(false) }
    // Cosa ha cambiato l'ultimo «Correggi» (o «Correggi tutti»): si vede in cima finché la casa resta quella corretta.
    var done by remember { mutableStateOf<Pair<Building, String>?>(null) }
    fun fixedSaying(message: String) {
        done = vm.state.value.fullBuilding to message
    }
    // Per la guida interattiva, che la fa provare.
    LaunchedEffect(Unit) { vm.planCheckOpened() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.controllo.titolo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                done?.takeIf { it.first == building }?.let { (_, message) ->
                    Text("✓ $message", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                val list = issues
                when {
                    list.isEmpty() -> {
                        Text("✓ " + T.controllo.nessunProblema, style = MaterialTheme.typography.titleSmall)
                        Text(T.controllo.cosaControlla, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text(T.controllo.spiega, style = MaterialTheme.typography.bodySmall)
                        val floors = building.floors.size > 1
                        val norms = HousingNorms.of(building)
                        val groups = PlanCheck.groups(list, norms)
                        // Gruppi aperti e chiusi a mano restano così anche dopo «Correggi» (l'elenco cambia).
                        val open = remember { mutableStateMapOf<String, Boolean>() }
                        val atStart = openAtStart(groups)
                        // La guida indica il primo «Correggi tutti» e il primo «Correggi» dell'elenco.
                        var guideFixAll: GuideTarget? = GuideTarget.CheckFixAll
                        var guideFix: GuideTarget? = GuideTarget.CheckFix
                        for ((title, group) in groups) {
                            val expanded = open[title] ?: atStart[title] ?: true
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (expanded) "▾ " else "▸ ") + "$title (${group.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f).clickable { open[title] = !expanded },
                                )
                                // Tutto il gruppo via dalla pianta (le norme che non interessano), o di nuovo tutto sulla pianta.
                                if (group.size > 1) {
                                    val allHidden = group.all { PlanCheck.hidden(building, it) }
                                    TextButton(onClick = { vm.setChecksHidden(group, !allHidden) }) {
                                        Text(if (allHidden) T.controllo.rimettiTutti else T.controllo.togliTutti, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (!expanded) continue
                            if (group.first().kind.rule) Text(T.controllo.spiegaNorme(norms), style = MaterialTheme.typography.bodySmall)
                            // Tutti quelli del gruppo che si correggono da soli (non quelli tolti dalla pianta), con un solo ↶.
                            val fixable = group.filter { !PlanCheck.hidden(building, it) && fixable(it, turnable) }
                            if (fixable.size > 1) {
                                TextButton(
                                    onClick = {
                                        fixing = true
                                        scope.launch {
                                            val (out, n) = withContext(Dispatchers.Default) {
                                                runCatching { PlanCheck.fixedAll(building, fixable) }.getOrDefault(building to 0)
                                            }
                                            fixing = false
                                            if (n > 0 && vm.applyFixes(building, out)) fixedSaying(FixSummary.message(n, building, out))
                                            else platform.toast(T.controllo.nonSiCorregge)
                                        }
                                    },
                                    enabled = !fixing,
                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                    modifier = Modifier.guide(guideFixAll),
                                ) { Text("✓ " + T.controllo.correggiTutti(fixable.size)) }
                                guideFixAll = null
                            }
                            for (i in group) Row(verticalAlignment = Alignment.CenterVertically) {
                                val where = if (floors) building.floors.getOrNull(i.floor)?.name?.let { "$it · " } ?: "" else ""
                                val hidden = PlanCheck.hidden(building, i)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        PlanCheck.mark(i.kind) + " " + where + PlanCheck.describe(i, building) + if (hidden) " (${T.controllo.toltoDallaPianta})" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                                    )
                                    // Sotto la frase, allineati al testo: a destra c'è già «Mostra».
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (fixable(i, turnable)) {
                                            TextButton(
                                                onClick = { vm.fixIssueMessage(i)?.let(::fixedSaying) ?: platform.toast(T.controllo.nonSiCorregge) },
                                                enabled = !fixing,
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                                modifier = Modifier.guide(guideFix),
                                            ) { Text("✓ " + T.controllo.correggi) }
                                            guideFix = null
                                        }
                                        // Il «!» sulla pianta: si toglie (il problema resta qui) e si rimette.
                                        TextButton(
                                            onClick = { vm.setCheckHidden(i, !hidden) },
                                            contentPadding = PaddingValues(horizontal = 0.dp),
                                        ) { Text(if (hidden) T.controllo.rimettiSullaPianta else T.controllo.togliDallaPianta, style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                                TextButton(onClick = { vm.showIssue(i); onDismiss() }) { Text(T.controllo.mostra) }
                            }
                        }
                        if (list.size >= PlanCheck.MAX_ISSUES) Text(T.controllo.troppi(PlanCheck.MAX_ISSUES), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.guide(GuideTarget.CheckClose)) { Text(T.controllo.chiudi) } },
        dismissButton = {
            if (issues.isNotEmpty()) {
                TextButton(onClick = { platform.shareText(T.controllo.oggetto(projectName), PlanCheck.report(building, issues)) }) { Text(T.controllo.condividi) }
            }
        },
    )
}

/**
 * «Correggi» per il problema `i`: si sa subito ([PlanCheck.canFix]), o, per quelli che si provano prima ([PlanCheck.triesFirst]),
 * quando la prova è andata bene (`turnable`).
 */
private fun fixable(i: PlanCheck.Issue, turnable: Set<PlanCheck.Issue>): Boolean =
    if (PlanCheck.triesFirst(i)) i in turnable else PlanCheck.canFix(i)

/** Gruppi di «Controlla la pianta» con al massimo tanti problemi: all'inizio aperti; quelli più lunghi chiusi (tranne il primo). */
internal const val OPEN_GROUP_MAX = 8

/**
 * Quali gruppi ([PlanCheck.groups], per titolo) sono aperti quando si apre la finestra: il primo (i problemi più importanti) e
 * quelli corti; un gruppo lungo resta chiuso, così si vedono subito anche i gruppi sotto.
 */
internal fun openAtStart(groups: List<Pair<String, List<PlanCheck.Issue>>>): Map<String, Boolean> =
    groups.withIndex().associate { (k, g) -> g.first to (k == 0 || g.second.size <= OPEN_GROUP_MAX) }

/** Errori di disegno (⚠) della casa: prima di esportare si avvisa se ce ne sono. Non quelli tolti dalla pianta (già visti). */
fun drawingErrors(b: Building): Int = runCatching { PlanCheck.of(b).count { it.kind.error && !PlanCheck.hidden(b, it) } }.getOrDefault(0)

/**
 * Prima di esportare (tavola PDF, DXF) con degli errori di disegno nella pianta: «Controlla» apre «Controlla la pianta», «Esporta lo
 * stesso» va avanti.
 */
@Composable
fun ExportCheckDialog(errors: Int, onCheck: () -> Unit, onExport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.controllo.primaDiEsportareTitolo) },
        text = { Text(T.controllo.primaDiEsportare(errors)) },
        confirmButton = { TextButton(onClick = onCheck) { Text(T.controllo.controlla) } },
        dismissButton = { TextButton(onClick = onExport) { Text(T.controllo.esportaLoStesso) } },
    )
}

/** Testo del segnale sulla pianta: «⚠ 2  ? 5  📏 3», senza i gruppi vuoti. */
internal fun liveCheckLabel(c: LiveCheck.Counts): String = listOfNotNull(
    "⚠ ${c.errors}".takeIf { c.errors > 0 },
    "? ${c.checks}".takeIf { c.checks > 0 },
    "📏 ${c.rules}".takeIf { c.rules > 0 },
).joinToString("  ")

/**
 * Segnale di «Controlla la pianta» sulla pianta, aggiornato mentre si disegna: quanti problemi ci sono (rosso con un errore di
 * disegno); il tocco apre l'elenco. Senza problemi non c'è.
 */
@Composable
fun LiveCheckChip(counts: LiveCheck.Counts, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (counts.total == 0) return
    val error = counts.errors > 0
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Text(liveCheckLabel(counts), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * Avviso breve quando una modifica crea un errore di disegno (⚠): cosa c'è che non va, «Correggi» dove si può
 * ([PlanCheck.canFix]), «Mostra» (porta lì e lo seleziona) e «Chiudi». `floors`: con più piani il nome del piano davanti.
 */
@Composable
fun LiveCheckNotice(
    issue: PlanCheck.Issue,
    building: Building,
    onShow: () -> Unit,
    onFix: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val where = if (building.floors.size > 1) building.floors.getOrNull(issue.floor)?.name?.let { "$it · " } ?: "" else ""
    val buttons = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 4.dp)) {
            Text(
                PlanCheck.mark(issue.kind) + " " + where + PlanCheck.describe(issue, building),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(Modifier.align(Alignment.End)) {
                if (onFix != null) TextButton(onClick = onFix, colors = buttons) { Text("✓ " + T.controllo.correggi) }
                TextButton(onClick = onShow, colors = buttons) { Text(T.controllo.mostra) }
                TextButton(onClick = onClose, colors = buttons) { Text(T.controllo.chiudi) }
            }
        }
    }
}
