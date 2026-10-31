package com.sagoma.planimetria.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Scritta e icona del marchio su fondo chiaro e scuro (immagine di controllo in build/prova-marchio.png). */
class MarchioImageTest {
    @Test
    fun `scritta e icona si disegnano con l'arco terracotta`() {
        ImageComposeScene(900, 420).use { scene ->
            scene.setContent {
                Column(Modifier.fillMaxSize().background(Color.White)) {
                    ScrittaSagoma(Modifier.padding(16.dp).height(80.dp), lettere = Marchio.Inchiostro)
                    Row(Modifier.background(Marchio.Inchiostro)) {
                        ScrittaSagoma(Modifier.padding(16.dp).height(80.dp), lettere = Color.White)
                    }
                    Row(Modifier.padding(16.dp)) {
                        for (lato in listOf(128, 48, 16)) Image(rememberVectorPainter(Marchio.icona), null, Modifier.padding(end = 16.dp).size(lato.dp))
                    }
                }
            }
            val img = scene.render()
            File("build").mkdirs()
            File("build/prova-marchio.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val px = org.jetbrains.skia.Bitmap.makeFromImage(img)
            var terracotta = 0
            var inchiostro = 0
            for (x in 0 until px.width) for (y in 0 until px.height) {
                val c = px.getColor(x, y)
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                if (abs(r - 0xC8) < 20 && abs(g - 0x55) < 20 && abs(b - 0x3D) < 20) terracotta++
                if (abs(r - 0x16) < 10 && abs(g - 0x20) < 10 && abs(b - 0x2A) < 10) inchiostro++
            }
            assertTrue(terracotta > 200, "manca l'arco della porta ($terracotta pixel terracotta)")
            assertTrue(inchiostro > 20000, "mancano le lettere o il fondo dell'icona ($inchiostro pixel inchiostro)")
        }
    }
}
