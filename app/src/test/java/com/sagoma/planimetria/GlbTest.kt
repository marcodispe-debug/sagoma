package com.sagoma.planimetria

import com.sagoma.planimetria.geometry.Glb
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Rgba
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Versione pro: la scena esportata in glTF binario per Filament. */
class GlbTest {

    @Test
    fun `il GLB ha intestazione, JSON e dati binari coerenti, con vetri e terreno`() {
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
            .copy(openings = listOf(Opening(1, OpeningKind.Window2, wallIndex = 0, position = 250.0, width = 120.0, height = 140.0, sillHeight = 90.0)))
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val glb = Glb.fromScene(scene, ground = Rgba(0.8f, 0.8f, 0.8f))
        val b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, b.getInt(0))
        assertEquals(2, b.getInt(4))
        assertEquals(glb.size, b.getInt(8))
        val jsonLen = b.getInt(12)
        assertEquals(0x4E4F534A, b.getInt(16))
        assertEquals(0, jsonLen % 4)
        val json = String(glb, 20, jsonLen, Charsets.UTF_8)
        val binLen = b.getInt(20 + jsonLen)
        assertEquals(0x004E4942, b.getInt(24 + jsonLen))
        assertEquals(glb.size, 28 + jsonLen + binLen)
        assertTrue(json.contains("\"name\":\"lit\"") && json.contains("\"name\":\"glass\""))
        // Opachi + vetri + i 6 vertici del terreno, 10 float per vertice: 3 + 3 + 4 float nel binario.
        val vertices = (scene.opaque.size + scene.transparent.size) / Scene3D.FLOATS_PER_VERTEX + 6
        assertEquals(vertices * 10 * 4, Regex("\"byteLength\":(\\d+),\"target\"").findAll(json).sumOf { it.groupValues[1].toInt() })
        // Posizioni in metri: la stanza è larga 5 m (più lo spessore dei muri) e il terreno sporge di 40 m.
        val maxX = Regex("\"max\":\\[([-0-9.E]+)").find(json)!!.groupValues[1].toDouble()
        assertEquals(45.075, maxX, 0.01)
    }
}
