package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.assisted.AssistedScene.camera
import com.sagoma.planimetria.scan.assisted.AssistedScene.plane
import com.sagoma.planimetria.scan.assisted.AssistedScene.scene
import com.sagoma.planimetria.scan.assisted.AssistedScene.sway
import com.sagoma.planimetria.scan.assisted.AssistedScene.wallA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pipeline: i frame costruiti uno alla volta (come sul telefono) sono gli stessi del calcolo in blocco. */
class AimFrameBuilderTest {
    private fun frames() = scene(14_000) {
        val ang = Math.toRadians(70.0)
        val b = plane(2, 4.0, 0.0, 4.0 + 3.0 * kotlin.math.cos(ang), 3.0 * kotlin.math.sin(ang))
        planes = { listOf(wallA(), b) }
        cam = { t ->
            when {
                t < 4_000 -> camera(2.0 + 0.02 * sway(t, 1), 1.4, 1.5, 2.0, 1.25, 0.0)
                t < 5_500 -> camera(2.0, 1.4, 1.5, -6.0, 1.25, 1.5)
                t < 10_000 -> camera(2.0, 1.4, 1.5, 4.0 + 1.5 * kotlin.math.cos(ang), 1.25, 1.5 * kotlin.math.sin(ang))
                else -> AssistedScene.standardCam
            }
        }
        points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.0, 0.02) else null }
    }

    @Test
    fun `frame uno alla volta uguali al calcolo in blocco`() {
        val rec = frames()
        val builder = AimFrameBuilder()
        val live = rec.sortedWith(compareBy({ it.elapsedMs }, { it.index })).map { builder.next(it) }
        assertEquals(AimFrames.from(rec), live)
    }

    @Test
    fun `la velocita riparte dopo una perdita di tracciamento`() {
        val rec = frames()
        val lost = rec.mapIndexed { i, f -> if (i == 10) f.copy(tracking = "PAUSED") else f }
        val out = AimFrameBuilder().let { b -> lost.map { b.next(it) } }
        assertNull(out[0].speedMps)        // primo frame: nessun precedente
        assertNull(out[10].speedMps)       // tracciamento perso
        assertNull(out[11].speedMps)       // il precedente valido non conta più
        assertTrue(out[12].speedMps != null)
    }
}
