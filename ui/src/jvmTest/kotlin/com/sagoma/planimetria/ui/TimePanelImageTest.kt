package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Immagine di prova del cursore dell'ora in verticale (computer, telefono girato): build/prova-ora-verticale.png. */
class TimePanelImageTest {
    @Test
    fun cursoreVerticale() {
        val view = View3DState().apply { timePanel = true; hour = 17.5f }
        ImageComposeScene(900, 500).use { scene ->
            scene.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF8FA98A))) {
                        TimeOfDayPanel(view, Modifier.align(Alignment.TopStart).padding(12.dp), vertical = true, length = 220.dp)
                    }
                }
            }
            val img = scene.render()
            File("build/prova-ora-verticale.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
