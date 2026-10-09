package com.sagoma.planimetria.scan.reconstruction.bench

import com.sagoma.planimetria.scan.reconstruction.PerimeterReport
import com.sagoma.planimetria.scan.reconstruction.ReconReport
import com.sagoma.planimetria.scan.reconstruction.WallReport
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REGRESSIONE R2.1: gli output geometrici di R2, R3/R3.1 e R4/R4.1 (surfaces.csv, walls.csv, wall-ends.csv, perimeter.csv,
 * perimeter.json) devono restare IDENTICI a quelli prodotti dal codice PRIMA di R2.1. Le impronte sono state catturate con la
 * produzione originale (hash dei sorgenti verificati uguali a quelli registrati negli audit precedenti), prima di aggiungere
 * l'evidenza di superficie alternativa. Con SAGOMA_R21_GOLDEN_PRINT=1 stampa le impronte invece di confrontarle.
 */
class R21RegressionTest {
    private val occ = OcclusionAuditTest()

    private fun scenarios(): List<Scenario> = listOf(Scenarios.byId("S1"), Scenarios.byId("S5"), Scenarios.byId("S9"), Scenarios.byId("S11")) +
        occ.partC().filter { it.sc.id == "O5" || it.sc.id == "C2" }.map { it.sc }

    private fun fingerprint(sc: Scenario): String {
        val run = BenchPipeline.run(sc)
        val text = ReconReport.csv(run.surfaces) + "\u0000" + WallReport.csv(run.walls) + "\u0000" + WallReport.endsCsv(run.walls) + "\u0000" +
            PerimeterReport.csv(run.r4) + "\u0000" + PerimeterReport.json(run.r4)
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Impronte della produzione PRIMA di R2.1 (seme 1, parametri predefiniti). */
    private val golden = mapOf(
        "S1" to "48a201d4ca7cb81943fb5e1252a58ed8129fccb123f571e185c0b46e7417c0f4",
        "S5" to "5313f22ed964b6dcb259ab684ecb54764e89f7efea4d1e3d808b17620b4792b8",
        "S9" to "650e26e5ba2aedfbf07714d28040382955bb6c429aa2952e550f2893bbdec90f",
        "S11" to "43a862550ba5b470b479b54ded1436eac16ea637f23ae5555cb71cd0a4b5922a",
        "O5" to "85be5ffe8222dd34ba22f4cfb654aa38e0d032c1eb8fd5e2c5816e64f3592bee",
        "C2" to "7ea9dbb00d2d938808169cb194205e640fedf7b58a30013d1d8134f41cfa70e9",
    )

    @Test
    fun `R2 R3 R4 producono gli stessi output geometrici di prima di R2_1`() {
        val print = System.getenv("SAGOMA_R21_GOLDEN_PRINT") != null
        for (sc in scenarios()) {
            val fp = fingerprint(sc)
            if (print) println("GOLDEN \"${sc.id}\" to \"$fp\",") else assertEquals(golden.getValue(sc.id), fp, "${sc.id}: output geometrici cambiati")
        }
    }
}
