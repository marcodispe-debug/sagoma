package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.CreationStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ScanDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Dalla scansione alla stanza di Sagoma: flusso di creazione e stanza creata. */
class ScanImportTest {
    private fun vm(): EditorViewModel {
        val b = Building.single(FloorPlan())
        return EditorViewModel(object : PlanStore {
            override fun load() = b
            override fun save(building: Building) {}
        })
    }

    private fun scanned() = listOf(0.0 to 0.0, 5.0 to 0.0, 5.0 to 3.5, 0.0 to 3.5)
        .fold(ScanDraft()) { d, (x, z) -> d.add(ArPoint(x, -1.2, z)) }.close().toScannedRoom()!!

    @Test
    fun `scansione - il flusso va a Scan e annulla riporta alla scelta della forma`() {
        val vm = vm()
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
        vm.pickScan()
        assertEquals(CreationStep.Scan(cancellable = false), vm.state.value.creation)
        vm.cancelScan()
        assertEquals(CreationStep.PickShape(cancellable = false), vm.state.value.creation)
    }

    @Test
    fun `la scansione diventa una stanza con le misure scansionate`() {
        val vm = vm()
        vm.pickScan()
        vm.createRoomFromScan(scanned())
        val s = vm.state.value
        assertEquals(null, s.creation)
        val room = s.plan.rooms.single()
        assertNotNull(room)
        // Misure interne: 5 × 3,5 m, spessore dei muri compreso nel contorno.
        val interiorArea = Polygon.interiorArea(room.points, room.wallThickness)
        assertEquals(500.0 * 350.0, interiorArea, 20.0)
        assertTrue(Polygon.signedArea(room.points) > 0, "oraria, come ogni stanza di Sagoma")
        assertEquals(4, room.points.size)
    }

    @Test
    fun `senza il passo di scansione non crea niente`() {
        val vm = vm()
        vm.createRoomFromScan(scanned())
        assertTrue(vm.state.value.plan.rooms.isEmpty())
    }
}
