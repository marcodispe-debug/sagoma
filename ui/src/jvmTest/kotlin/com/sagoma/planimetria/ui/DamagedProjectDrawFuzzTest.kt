package com.sagoma.planimetria.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sagoma.planimetria.editor.Camera
import com.sagoma.planimetria.editor.EditorUiState
import com.sagoma.planimetria.geometry.ExteriorChains
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.expandCurves
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.persistence.PlanJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random
import kotlin.test.Test

/**
 * Prova lunga, spenta di solito: copie di un progetto con numeri cambiati a caso (negativi, zero, enormi, piccolissimi),
 * disegnate come all'apertura nell'editor (pianta, quote, misure di porte e finestre, quote e testi), esportate in PNG e nella
 * tavola PDF (tutti i piani e il computo). Un progetto rovinato non deve far cadere né bloccare l'app appena lo si apre.
 * SAGOMA_FUZZ_UI=progetto.json, SAGOMA_FUZZ_N=quante copie, SAGOMA_FUZZ_FROM=prima copia, SAGOMA_FUZZ_ONLY=1,5,9,
 * SAGOMA_FUZZ_OUT=cartella per esito.txt e le copie che falliscono.
 */
class DamagedProjectDrawFuzzTest {
    private fun mutate(e: JsonElement, rnd: Random, rate: Double): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { mutate(it.value, rnd, rate) })
        is JsonArray -> JsonArray(e.map { mutate(it, rnd, rate) })
        is JsonPrimitive -> if (!e.isString && e.doubleOrNull != null && e.booleanOrNull == null && rnd.nextDouble() < rate) {
            val v = e.double
            val isInt = e.content.none { it == '.' || it == 'e' || it == 'E' }
            val n = when (rnd.nextInt(8)) {
                0 -> -v
                1 -> 0.0
                2 -> v * 1000
                3 -> v / 1000
                4 -> -1.0
                5 -> 1e7
                6 -> v + rnd.nextDouble(-50.0, 50.0)
                else -> 1e-4
            }
            if (isInt) JsonPrimitive(n.toLong()) else JsonPrimitive(n)
        } else e
        else -> e
    }

    /** La pianta come nell'editor appena aperto: tutta nella finestra, con quote e testi. */
    private fun drawEditor(b: Building, measurer: TextMeasurer) {
        val w = 1200
        val h = 800
        val plan = b.floor.plan
        val box = Openings.planBounds(plan)
        val k = if (box == null) 1f else minOf(w / maxOf(box.width, 1.0), h / maxOf(box.height, 1.0)).toFloat().coerceIn(Camera.MIN_SCALE, 50f)
        val cam = if (box == null) Camera() else Camera(k, (w / 2 - box.center.x * k).toFloat(), (h / 2 - box.center.y * k).toFloat())
        val state = EditorUiState(plan = plan, camera = cam, building = b)
        val density = Density(1.5f)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(ImageBitmap(w, h)), Size(w.toFloat(), h.toFloat())) {
            drawPlan(state, measurer)
            val outside = ExteriorChains.of(plan)
            drawWallDimensions(plan, cam, measurer, skip = { id, i -> (id to i) in outside.covered })
            drawOpeningSizes(plan.expandCurves().plan, outside, cam, measurer, androidx.compose.ui.graphics.Color.White)
            drawExteriorChains(outside, cam, measurer)
            drawNotes(plan, cam, measurer, androidx.compose.ui.graphics.Color.White, showDimensions = true, showTexts = true)
        }
    }

    @Test
    fun fuzz() {
        val src = System.getenv("SAGOMA_FUZZ_UI") ?: return
        val n = (System.getenv("SAGOMA_FUZZ_N") ?: "100").toInt()
        val outDir = File(System.getenv("SAGOMA_FUZZ_OUT") ?: "build/prova-disegni-rovinati").apply { mkdirs() }
        val base = Json.parseToJsonElement(File(src).readText())
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        var pool = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
        val log = StringBuilder()
        fun note(line: String) { log.appendLine(line); File(outDir, "esito.txt").writeText(log.toString()) }
        val only = System.getenv("SAGOMA_FUZZ_ONLY")?.split(',')?.map { it.toInt() }?.toSet()
        val from = (System.getenv("SAGOMA_FUZZ_FROM") ?: "0").toInt()
        var done = 0
        for (k in from until from + n) {
            if (only != null && k !in only) continue
            val rnd = Random(k)
            val text = mutate(base, rnd, listOf(0.002, 0.01, 0.05, 0.2)[k % 4]).toString()
            val b = runCatching { PlanJson.decodeBuilding(text) }.getOrNull() ?: continue
            var stage = "?"
            val task = pool.submit<Unit> {
                stage = "editor"; drawEditor(b, measurer)
                for ((i, f) in b.floors.withIndex()) {
                    stage = "png$i"; PlanExport.render(f.plan, measurer, Density(1f), f.levelHeight, b.layers)
                }
                stage = "pdf"
                val pages = PdfExport.pages(b, null, PdfExport.Options(PdfExport.Paper.A4, null, allFloors = true, takeoff = true), measurer, "28/10/2026")
                for ((i, p) in pages.withIndex()) {
                    stage = "pdf$i"
                    CanvasDrawScope().draw(PdfExport.drawDensity, LayoutDirection.Ltr, Canvas(ImageBitmap(p.width, p.height)), Size(p.width.toFloat(), p.height.toFloat())) { p.draw(this) }
                }
            }
            try {
                task.get(60, TimeUnit.SECONDS)
                done++
            } catch (t: TimeoutException) {
                note("$k TIMEOUT $stage"); File(outDir, "caso-$k.json").writeText(text)
                task.cancel(true); pool = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
            } catch (t: ExecutionException) {
                val c = t.cause!!
                note("$k ERR $stage ${c::class.simpleName}: ${c.message?.take(120)} @ ${c.stackTrace.filter { it.className.startsWith("com.sagoma") }.take(10).joinToString(" < ")}")
                File(outDir, "caso-$k.json").writeText(text)
            }
        }
        note("fine: $done copie disegnate senza problemi")
    }
}
