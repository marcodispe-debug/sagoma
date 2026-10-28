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
import com.sagoma.planimetria.testi.T

/**
 * «Controlla la pianta»: l'elenco dei problemi di tutta la casa, prima quelli da correggere e poi quelli da controllare.
 * «Mostra» porta al piano giusto, seleziona e inquadra (la finestra si chiude).
 */
@Composable
fun PlanCheckDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit) {
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
                        for ((title, group) in listOf(T.controllo.daCorreggere to list.filter { it.kind.error }, T.controllo.daControllare to list.filter { !it.kind.error })) {
                            if (group.isEmpty()) continue
                            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                            for (i in group) Row(verticalAlignment = Alignment.CenterVertically) {
                                val where = if (floors) building.floors.getOrNull(i.floor)?.name?.let { "$it · " } ?: "" else ""
                                Text(
                                    (if (i.kind.error) "⚠ " else "? ") + where + PlanCheck.describe(i, building),
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
    )
}
