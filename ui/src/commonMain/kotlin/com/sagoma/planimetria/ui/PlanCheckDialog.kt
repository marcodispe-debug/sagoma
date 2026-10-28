package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.testi.T

/**
 * «Controlla la pianta»: l'elenco dei problemi di tutta la casa, prima quelli da correggere, poi quelli da controllare e infine
 * i minimi delle norme per la casa. «Mostra» porta al piano giusto, seleziona e inquadra (la finestra si chiude). «Condividi»
 * manda l'elenco come testo (`projectName` nell'oggetto).
 */
@Composable
fun PlanCheckDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit, projectName: String? = null) {
    val platform = LocalPlatform.current
    val building = state.fullBuilding
    // Pochi millesimi di secondo anche per una casa di 100 stanze (al massimo [PlanCheck.MAX_ISSUES] problemi).
    val issues = remember(building) { runCatching { PlanCheck.of(building) }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.controllo.titolo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val list = issues
                when {
                    list.isEmpty() -> {
                        Text("✓ " + T.controllo.nessunProblema, style = MaterialTheme.typography.titleSmall)
                        Text(T.controllo.cosaControlla, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text(T.controllo.spiega, style = MaterialTheme.typography.bodySmall)
                        val floors = building.floors.size > 1
                        for ((title, group) in PlanCheck.groups(list)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                            if (group.first().kind.rule) Text(T.controllo.spiegaNorme, style = MaterialTheme.typography.bodySmall)
                            for (i in group) Row(verticalAlignment = Alignment.CenterVertically) {
                                val where = if (floors) building.floors.getOrNull(i.floor)?.name?.let { "$it · " } ?: "" else ""
                                Text(
                                    PlanCheck.mark(i.kind) + " " + where + PlanCheck.describe(i, building),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
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

/** Errori di disegno (⚠) della casa: prima di esportare si avvisa se ce ne sono. */
fun drawingErrors(b: Building): Int = runCatching { PlanCheck.of(b).count { it.kind.error } }.getOrDefault(0)

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
