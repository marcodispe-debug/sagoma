package com.sagoma.planimetria.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.persistence.ProjectInfo

/** Formato italiano con due decimali (12,35). */
internal fun fmt2(v: Double) = com.sagoma.planimetria.geometry.formatDecimal(v, 2)

/**
 * Computo metrico e lista degli arredi di tutta la casa: pavimenti, pareti al netto delle aperture,
 * battiscopa, totali per materiale; si esporta in CSV (Excel, LibreOffice) o si condivide come testo.
 */
@Composable
fun TakeoffDialog(state: EditorUiState, info: ProjectInfo?, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val res = remember(state.fullBuilding) { Takeoff.of(state.fullBuilding) }
    val title = info?.name ?: "Progetto"
    fun exportCsv() {
        // BOM: Excel riconosce così l'UTF-8 (accenti e m²).
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + Takeoff.csv(res).encodeToByteArray()
        val name = "computo-${title.replace(Regex("[^A-Za-z0-9 _-]"), "")}-${isoDate(platform.now())}.csv"
        platform.saveFile(name, "text/csv", bytes) { ok -> platform.toast(if (ok) "Computo esportato" else "Esportazione non riuscita") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Computo e lista arredi") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (res.rooms.isEmpty()) Text("Non ci sono ancora stanze.")
                Section("Stanze")
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    TableRow(listOf("Stanza", "Pav. m²", "Pareti m²", "Battisc. m", "Pavimento"), header = true)
                    val multi = state.fullBuilding.floors.size > 1
                    for (l in res.rooms) TableRow(listOf(if (multi) "${l.room} (${l.floor})" else l.room, fmt2(l.floorArea), fmt2(l.wallArea), fmt2(l.skirting), l.floorFinish))
                    TableRow(listOf("Totale", fmt2(res.totalFloor), fmt2(res.totalWalls), fmt2(res.totalSkirting), ""), header = true)
                }
                Text(
                    "Pareti: superficie interna al netto di porte e finestre. Battiscopa: perimetro interno meno porte e balconi.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Section("Pavimenti da ordinare")
                for (t in res.floorMaterials) Line(t.what, "${fmt2(t.quantity)} m²  (+10% ${fmt2(t.quantity * 1.1)})")
                Section("Finiture delle pareti")
                for (t in res.wallFinishes) Line(t.what, "${fmt2(t.quantity)} m²")
                Section("Arredi")
                if (res.furniture.isEmpty()) Text("Nessun arredo.", style = MaterialTheme.typography.bodySmall)
                for (i in res.furniture) Line("${i.count}× ${i.name}", "${i.size}\n${i.where}")
                if (res.fixtures.isNotEmpty()) {
                    Section("Impianti")
                    for (i in res.fixtures) Line("${i.count}× ${i.name}", i.where)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { platform.shareText("Computo – $title", Takeoff.csv(res).replace(';', '\t')) }) { Text("Condividi") }
                TextButton(onClick = { exportCsv() }) { Text("Esporta CSV") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Line(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(right, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

private val columnWidths: List<Dp> = listOf(120.dp, 64.dp, 72.dp, 72.dp, 130.dp)

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                Modifier.width(columnWidths[i]).padding(end = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.SemiBold else null,
                textAlign = if (i in 1..3) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
