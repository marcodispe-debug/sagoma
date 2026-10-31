package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.LiveCheck
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

/** Segnale e avviso di «Controlla la pianta» in tempo reale (foto in build/controllo-dal-vivo-*.png, larghe come un telefono). */
class LiveCheckUiTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    @Test
    fun `testo del segnale`() {
        assertEquals("⚠ 2  ? 5  📏 3", liveCheckLabel(LiveCheck.Counts(2, 5, 3)))
        assertEquals("? 1", liveCheckLabel(LiveCheck.Counts(0, 1, 0)))
        assertEquals("⚠ 1  📏 4", liveCheckLabel(LiveCheck.Counts(1, 0, 4)))
    }

    @Test
    fun `segnale e avviso`() {
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
        val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(300.0, 0.0) })
        val b = Building.single(FloorPlan(listOf(living, kitchen)))
        for ((name, lingua) in listOf("it" to Italiano, "en" to Inglese)) {
            T.lingua = lingua
            val issues = PlanCheck.of(b)
            val overlap = issues.first { it.kind == PlanCheck.Kind.Overlap }
            ImageComposeScene(400, 300).use { scene ->
                scene.setContent {
                    MaterialTheme {
                        Column(Modifier.padding(8.dp)) {
                            LiveCheckChip(LiveCheck.counts(issues), onClick = {})
                            LiveCheckNotice(overlap, b, onShow = {}, onFix = null, onClose = {}, modifier = Modifier.padding(top = 8.dp).width(380.dp))
                        }
                    }
                }
                var img = scene.render(0)
                for (k in 1..5) img = scene.render(k * 50_000_000L)
                File("build/controllo-dal-vivo-$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
