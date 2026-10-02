package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.sagoma.planimetria.model.FloorPlan

/**
 * Restituisce l'azione "Esporta PNG": la pianta viene disegnata in un'immagine, poi l'utente sceglie dove
 * salvarla con il selettore della piattaforma.
 */
@Composable
fun rememberPngExporter(plan: FloorPlan, layers: com.sagoma.planimetria.model.LayerSettings = com.sagoma.planimetria.model.LayerSettings()): () -> Unit {
    val platform = LocalPlatform.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return {
        val png = PlanExport.render(plan, measurer, density, layers = layers)?.let(platform::encodePng)
        if (png == null) platform.toast("Non c'è niente da esportare")
        else platform.saveFile("planimetria-${isoDate(platform.now())}.png", "image/png", png) { ok ->
            platform.toast(if (ok) "Pianta esportata" else "Esportazione non riuscita")
        }
    }
}

/** Data AAAA-MM-GG (UTC) per i nomi dei file. */
internal fun isoDate(epochMillis: Long): String {
    // Giorni dal 1/1/1970 → data del calendario gregoriano (algoritmo di H. Hinnant).
    val z = epochMillis.floorDiv(86_400_000L) + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
