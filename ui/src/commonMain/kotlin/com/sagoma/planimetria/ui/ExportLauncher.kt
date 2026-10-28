package com.sagoma.planimetria.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.sagoma.planimetria.access.OutputFormat
import com.sagoma.planimetria.access.Rights
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.testi.T

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
        val watermark = !Rights.cleanOutput(platform.account, OutputFormat.Plan, plan.rooms.map { it.id })
        val png = PlanExport.render(plan, measurer, density, layers = layers, watermark = watermark)?.let(platform::encodePng)
        if (png == null) platform.toast(T.vari.nonNienteEsportare)
        else platform.saveFile("planimetria-${isoDate(platform.now())}.png", "image/png", png) { ok ->
            platform.toast(if (!ok) T.vari.esportazioneNonRiuscita else if (watermark) T.vari.piantaEsportataMarchioSagoma else T.vari.piantaEsportata)
        }
    }
}

/**
 * Restituisce l'azione "Esporta pianta DXF": il piano che si sta guardando (nello stato di fatto o di progetto, se è quella la
 * vista), in centimetri, per AutoCAD e gli altri programmi CAD. Solo la planimetria: gli arredi non escono.
 */
@Composable
fun rememberDxfExporter(state: com.sagoma.planimetria.editor.EditorUiState): () -> Unit {
    val platform = LocalPlatform.current
    return {
        val view = state.phaseView
        val plan = if (view == com.sagoma.planimetria.geometry.PhaseView.Compare) state.plan else com.sagoma.planimetria.geometry.Phases.view(state.plan, view)
        if (plan.rooms.isEmpty() && plan.freeWalls.isEmpty()) platform.toast(T.vari.nonNienteEsportare)
        else {
            val dxf = com.sagoma.planimetria.dxf.PlanDxf.bytes(plan, state.levelHeight)
            val floor = if (state.building.floors.size > 1) "-" + fileNameOf(state.building.floor.name, "piano").replace(' ', '-') else ""
            platform.saveFile("planimetria$floor-${isoDate(platform.now())}.dxf", "application/dxf", dxf) { ok ->
                platform.toast(if (ok) T.vari.piantaEsportata else T.vari.esportazioneNonRiuscita)
            }
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
