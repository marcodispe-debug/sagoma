package com.sagoma.planimetria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3

/**
 * Chi disegna la [Scene3D] sullo schermo: OpenGL semplice o Filament (luci e ombre realistiche) su
 * Android, altri motori sulle altre piattaforme. Tocchi, selezione e telecamera restano uguali: il
 * renderer disegna soltanto. Tutte le chiamate arrivano dal thread dell'interfaccia.
 */
interface SceneRenderer {
    /** La superficie su cui disegna (una sola per renderer). */
    @Composable
    fun Surface(modifier: Modifier)

    fun setScene(scene: Scene3D)
    /** Telecamera: posizione, punto guardato, alto, apertura verticale (gradi), piano vicino (cm). */
    fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean)
    /**
     * Ora del giorno (0–24, decide sole, cielo e luce dalle finestre) e luci della casa ("auto": accese
     * quando fa buio, "on", "off"). Solo i renderer avanzati le usano.
     */
    fun setDaylight(hour: Float, lamps: String) {}
    fun onPause() {}
    fun onResume() {}
    /** La superficie non serve più: libera la memoria della scheda grafica. */
    fun release() {}
}

/** Piattaforme su cui la vista 3D non c'è ancora: un avviso al posto della scena. */
class UnavailableSceneRenderer(private val message: String) : SceneRenderer {
    @Composable
    override fun Surface(modifier: Modifier) {
        Box(modifier.background(Color(0xFF2B2F33)), contentAlignment = Alignment.Center) {
            Text(message, color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }

    override fun setScene(scene: Scene3D) {}
    override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {}
}
