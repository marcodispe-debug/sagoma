package com.sagoma.planimetria.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.en.Inglese
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prima di esportare: quanti errori di disegno ha la pianta, e la finestra che lo dice (foto in build/prima-di-esportare-*.png). */
class ExportCheckTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    @Test
    fun `errori di disegno prima di esportare`() {
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
        val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })
        assertEquals(0, drawingErrors(Building.single(FloorPlan(listOf(living, kitchen)))))
        // La cucina spostata di un metro dentro il soggiorno: stanze sovrapposte.
        val over = kitchen.copy(points = kitchen.points.map { it - Vec2(100.0, 0.0) })
        val wrong = Building.single(FloorPlan(listOf(living, over)))
        assertTrue(drawingErrors(wrong) >= 1)
        // Tolti dalla pianta (già visti, voluti così): non si contano.
        val seen = wrong.copy(hiddenChecks = PlanCheck.of(wrong).filter { it.kind.error }.mapTo(HashSet()) { PlanCheck.hideKey(wrong, it) })
        assertEquals(0, drawingErrors(seen))
        assertEquals("La pianta ha un errore di disegno (⚠): misure, metri quadri e computo potrebbero essere sbagliati anche nel file esportato.", T.controllo.primaDiEsportare(1))
        for ((name, lingua) in listOf("it" to Italiano, "en" to Inglese)) {
            T.lingua = lingua
            ImageComposeScene(520, 420).use { scene ->
                scene.setContent { MaterialTheme { ExportCheckDialog(2, onCheck = {}, onExport = {}, onDismiss = {}) } }
                // La finestra entra con una dissolvenza: qualche fotogramma prima della foto.
                var img = scene.render(0)
                for (k in 1..20) img = scene.render(k * 50_000_000L)
                File("build/prima-di-esportare-$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
