package com.sagoma.planimetria.scan.assisted

import com.sagoma.planimetria.scan.assisted.AssistedScene.camera
import com.sagoma.planimetria.scan.assisted.AssistedScene.plane
import com.sagoma.planimetria.scan.assisted.AssistedScene.scene
import com.sagoma.planimetria.scan.assisted.AssistedScene.wallA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Punti di sostegno salvati alla conferma e avviso "parete confermata dietro" (solo dietro la candidata, non alle spalle). */
class AssistedSupportTest {
    private fun run(frames: List<com.sagoma.planimetria.scan.recording.RecordedFrame>, g: (AimFrame, AssistedState) -> Run.Gesture? = { _, _ -> null }) = Run(aimFrames(frames), AssistedParams(), g)

    private val confirmFirst: (AimFrame, AssistedState) -> Run.Gesture? = { _, s -> if (s.candidate?.state == CandidateState.PROPOSED && s.walls.isEmpty()) Run.Gesture.CONFIRM else null }

    @Test
    fun `la conferma conserva dove stanno i punti lungo la parete`() {
        val r = run(scene(4_000) { planes = { listOf(wallA()) }; points = { _, i -> if (i == 0) AssistedScene.wallPoints(0.0, 0.02) else null } }, confirmFirst)
        val w = r.last.walls.single()
        val along = assertNotNull(w.supportAlongM)
        assertEquals(60, along.size)
        assertEquals(along.sorted(), along)
        assertTrue(along.all { it in 0.0..4.0 }, "$along")
        assertEquals(w.pointSupport, along.size)
    }

    @Test
    fun `senza nuvola di punti nessun dato di sostegno`() {
        val r = run(scene(4_000) { planes = { listOf(wallA()) } }, confirmFirst)
        assertNull(r.last.walls.single().supportAlongM)
    }

    @Test
    fun `la parete opposta alle spalle del telefono non e una parete dietro`() {
        // Stanza larga 1,5 m: muro A a z = 0 confermato, poi si inquadra il muro opposto a z = 1,5 dal centro (z = 1).
        val opposite = plane(2, 4.0, 1.5, 0.0, 1.5)
        val r = run(scene(9_000) {
            planes = { t -> if (t < 4_000) listOf(wallA()) else listOf(wallA(), opposite) }
            cam = { t -> if (t < 4_000) camera(2.0, 1.4, 1.0, 2.0, 1.25, 0.0) else camera(2.0, 1.4, 1.0, 2.0, 1.25, 1.5) }
        }, confirmFirst)
        assertEquals(1, r.last.walls.size)
        val c = assertNotNull(r.last.candidate)
        assertEquals(2, c.aimedPlaneKey)
        assertTrue(Advisory.PARALLEL_BEHIND !in c.advisories, "avvisi: ${c.advisories}")
    }
}
