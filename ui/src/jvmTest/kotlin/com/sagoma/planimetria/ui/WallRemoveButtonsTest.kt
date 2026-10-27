package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.use
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.Selection
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.en.Inglese
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Muro in comune: «Togli il muro (restano 2 stanze)» e «Togli il muro e unisci in 1 stanza». Foto del pannello del muro in
 * build/pulsanti-muro-<lingua>.png; un muro verso l'esterno ha ancora «Elimina muro».
 */
class WallRemoveButtonsTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    private val soggiorno = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0))
    private val cucina = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + Vec2(400.0, 0.0) })
    private val plan = FloorPlan(listOf(soggiorno, cucina))
    private val vm = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(plan)
        override fun save(building: Building) {}
    })

    private fun photo(name: String, wall: Int) {
        ImageComposeScene(420, 1500).use { scene ->
            scene.setContent {
                MaterialTheme {
                    SelectionPanel(Selection.Wall(1, wall), plan, vm, Modifier.fillMaxSize().background(Color.White))
                }
            }
            File("build/$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test
    fun pannelloDelMuroInComune() {
        photo("pulsanti-muro-it", 1)
        photo("pulsanti-muro-esterno-it", 3)
        T.lingua = Inglese
        photo("pulsanti-muro-en", 1)
    }
}
