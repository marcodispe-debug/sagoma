package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.dxf.PlanDxf
import com.sagoma.planimetria.geometry.Collisions
import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.Glb
import com.sagoma.planimetria.geometry.Phases
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Takeoff
import com.sagoma.planimetria.editor.PlanCheck
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random
import kotlin.test.Test

/**
 * Prova lunga, spenta di solito: copie di un progetto salvato con numeri cambiati a caso (negativi, zero, enormi,
 * piccolissimi). Aprirle non deve far cadere né bloccare 3D, quote, computo, pianta in DXF e salvataggio.
 * SAGOMA_FUZZ=progetto.json, SAGOMA_FUZZ_N=quante copie, SAGOMA_FUZZ_FROM=prima copia, SAGOMA_FUZZ_ONLY=1,5,9 (solo quelle),
 * SAGOMA_FUZZ_OUT=cartella dove finiscono esito.txt e le copie che falliscono, SAGOMA_FUZZ_GLB=1 prova anche l'esportazione 3D.
 */
class DamagedProjectFuzzTest {
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

    @Test
    fun fuzz() {
        val src = System.getenv("SAGOMA_FUZZ") ?: return
        val n = (System.getenv("SAGOMA_FUZZ_N") ?: "200").toInt()
        val outDir = File(System.getenv("SAGOMA_FUZZ_OUT") ?: "build/prova-progetti-rovinati").apply { mkdirs() }
        val base = Json.parseToJsonElement(File(src).readText())
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
        val log = StringBuilder()
        // Scritto a ogni caso che fallisce: se la prova si ferma a metà, quello che ha trovato resta.
        fun note(line: String) { log.appendLine(line); File(outDir, "esito.txt").writeText(log.toString()) }
        var pool2 = pool
        val only = System.getenv("SAGOMA_FUZZ_ONLY")?.split(',')?.map { it.toInt() }?.toSet()
        val from = (System.getenv("SAGOMA_FUZZ_FROM") ?: "0").toInt()
        for (k in from until from + n) {
            if (only != null && k !in only) continue
            val rnd = Random(k)
            val text = mutate(base, rnd, listOf(0.002, 0.01, 0.05, 0.2)[k % 4]).toString()
            val b = runCatching { PlanJson.decodeBuilding(text) }.getOrNull() ?: continue
            var stage = "?"
            val task = pool2.submit<Unit> {
                for ((i, f) in b.floors.withIndex()) {
                    stage = "scene$i"; val s = Scene3D.build(f.plan, null, null, ceilings = true, levelHeight = f.levelHeight, below = b.floors.take(i), above = b.floors.getOrNull(i + 1))
                    if (System.getenv("SAGOMA_FUZZ_GLB") != null) { stage = "glb$i"; Glb.fromScene(s) }
                    stage = "dims$i"; Dimensions.of(f.plan)
                    stage = "coll$i"; Collisions.of(f.plan)
                    stage = "phases$i"; Phases.works(f.plan)
                    stage = "dxf$i"; PlanDxf.of(f.plan, f.levelHeight)
                }
                stage = "takeoff"; Takeoff.csv(Takeoff.of(b))
                stage = "check"; PlanCheck.of(b).forEach { PlanCheck.describe(it, b) }
                stage = "json"; PlanJson.decodeBuilding(PlanJson.encode(b))
            }
            try {
                task.get(20, TimeUnit.SECONDS)
            } catch (t: TimeoutException) {
                note("$k TIMEOUT $stage"); File(outDir, "caso-$k.json").writeText(text)
                task.cancel(true); pool2 = Executors.newSingleThreadExecutor { r -> Thread(r).apply { isDaemon = true } }
            } catch (t: java.util.concurrent.ExecutionException) {
                val c = t.cause!!
                note("$k ERR $stage ${c::class.simpleName}: ${c.message?.take(120)} @ ${c.stackTrace.filter { it.className.startsWith("com.sagoma") }.take(10).joinToString(" < ")}")
                File(outDir, "caso-$k.json").writeText(text)
            }
        }
        File(outDir, "esito.txt").writeText(log.toString())
    }
}
