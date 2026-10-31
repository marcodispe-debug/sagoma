package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Il GLB per il 3D del telefono, letto vertice per vertice: ogni vertice della scena finisce nel suo oggetto (illuminato, luce
 * emessa, vetro, rivolto in basso), nell'ordine, con posizione in metri, normale e colore lineare; le estensioni e il terreno
 * tornano. (Il GLB si scrive direttamente nel file finale, senza liste intermedie: qui si controlla che non si perda niente.)
 */
class GlbContentTest {
    private class Read(val name: String, val pos: List<FloatArray>, val nor: List<FloatArray>, val col: List<FloatArray>, val min: List<Float>, val max: List<Float>)

    private fun read(glb: ByteArray): List<Read> {
        val b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, b.getInt(0))
        assertEquals(glb.size, b.getInt(8))
        val jsonLen = b.getInt(12)
        assertEquals(0, jsonLen % 4)
        val json = Json.parseToJsonElement(String(glb, 20, jsonLen, Charsets.UTF_8).trimEnd()).jsonObject
        val binLen = b.getInt(20 + jsonLen)
        assertEquals(0x004E4942, b.getInt(24 + jsonLen))
        assertEquals(glb.size, 28 + jsonLen + binLen)
        val bin = 28 + jsonLen
        val views = json["bufferViews"]!!.jsonArray
        val accessors = json["accessors"]!!.jsonArray
        assertEquals(binLen, json["buffers"]!!.jsonArray[0].jsonObject["byteLength"]!!.jsonPrimitive.int)
        fun floats(acc: Int, size: Int): List<FloatArray> {
            val a = accessors[acc].jsonObject
            val v = views[a["bufferView"]!!.jsonPrimitive.int].jsonObject
            val offset = bin + v["byteOffset"]!!.jsonPrimitive.int
            val count = a["count"]!!.jsonPrimitive.int
            assertEquals(count * size * 4, v["byteLength"]!!.jsonPrimitive.int)
            return List(count) { i -> FloatArray(size) { c -> b.getFloat(offset + (i * size + c) * 4) } }
        }
        val meshes = json["meshes"]!!.jsonArray
        return json["nodes"]!!.jsonArray.map { node ->
            val attrs = meshes[node.jsonObject["mesh"]!!.jsonPrimitive.int].jsonObject["primitives"]!!.jsonArray[0].jsonObject["attributes"] as JsonObject
            val p = attrs["POSITION"]!!.jsonPrimitive.int
            val acc = accessors[p].jsonObject
            Read(
                node.jsonObject["name"]!!.jsonPrimitive.content,
                floats(p, 3), floats(attrs["NORMAL"]!!.jsonPrimitive.int, 3), floats(attrs["COLOR_0"]!!.jsonPrimitive.int, 4),
                acc["min"]!!.jsonArray.map { it.jsonPrimitive.content.toFloat() }, acc["max"]!!.jsonArray.map { it.jsonPrimitive.content.toFloat() },
            )
        }
    }

    private fun linear(c: Float): Float {
        val v = (c * 255f + 0.5f).toInt().coerceIn(0, 255) / 255.0
        return (if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)).toFloat()
    }

    /** Vertici attesi per oggetto, come li divide il GLB. */
    private fun expected(scene: Scene3D): Map<String, List<FloatArray>> {
        val out = linkedMapOf<String, MutableList<FloatArray>>()
        fun add(src: FloatArray, glassy: Boolean) {
            val n = Scene3D.FLOATS_PER_VERTEX
            var k = 0
            while (k + n <= src.size) {
                val emissive = src[k + 3] == 0f && src[k + 4] == 0f && src[k + 5] == 0f
                val name = when { glassy -> "glass"; emissive -> "unlit"; src[k + 4] < -0.9f -> "lit_down"; else -> "lit" }
                out.getOrPut(name) { ArrayList() } += src.copyOfRange(k, k + n)
                k += n
            }
        }
        add(scene.opaque, false)
        add(scene.transparent, true)
        return out
    }

    private fun check(scene: Scene3D, ground: Rgba?) {
        val parts = read(Glb.fromScene(scene, ground))
        val want = expected(scene)
        assertEquals(listOf("lit", "unlit", "glass", "lit_down").filter { it in want }, parts.map { it.name })
        for (p in parts) {
            val src = want.getValue(p.name)
            val extra = if (p.name == "lit" && ground != null) 6 else 0
            assertEquals(src.size + extra, p.pos.size, p.name)
            for ((i, v) in src.withIndex()) {
                assertEquals(listOf(v[0] * 0.01f, v[1] * 0.01f, v[2] * 0.01f), p.pos[i].toList(), "${p.name} posizione $i")
                val emissive = v[3] == 0f && v[4] == 0f && v[5] == 0f
                assertEquals(if (emissive) listOf(0f, 1f, 0f) else listOf(v[3], v[4], v[5]), p.nor[i].toList(), "${p.name} normale $i")
                assertEquals(listOf(linear(v[6]), linear(v[7]), linear(v[8]), v[9]), p.col[i].toList(), "${p.name} colore $i")
            }
            // Estensione delle posizioni (terreno compreso).
            for (c in 0..2) {
                assertEquals(p.pos.minOf { it[c] }, p.min[c], "${p.name} minimo $c")
                assertEquals(p.pos.maxOf { it[c] }, p.max[c], "${p.name} massimo $c")
            }
            if (extra > 0) {
                // Terreno: 40 m oltre la casa, appena sotto il punto più basso, rivolto in alto, del colore scelto.
                val house = p.pos.take(src.size)
                val g = p.pos.drop(src.size)
                assertEquals(house.minOf { it[0] } - 40f, g.minOf { it[0] })
                assertEquals(house.maxOf { it[2] } + 40f, g.maxOf { it[2] })
                assertEquals(setOf(house.minOf { it[1] } - 0.005f), g.map { it[1] }.toSet())
                assertEquals(setOf(listOf(0f, 1f, 0f)), p.nor.drop(src.size).map { it.toList() }.toSet())
                assertEquals(setOf(listOf(linear(ground!!.r), linear(ground.g), linear(ground.b), 1f)), p.col.drop(src.size).map { it.toList() }.toSet())
            }
        }
    }

    @Test
    fun ogniVerticeAlSuoPosto() {
        // Stanza con finestra (vetri), lampadario e faretti (luce emessa), soffitto (facce verso il basso).
        val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
            openings = listOf(Opening(1, OpeningKind.Window2, wallIndex = 0, position = 250.0, width = 120.0, height = 140.0, sillHeight = 90.0)),
            fixtures = listOf(Fixture(1, FixtureKind.Chandelier, point = Vec2(250.0, 200.0)), Fixture(2, FixtureKind.LedStrip, point = Vec2(100.0, 100.0), length = 120.0)),
        )
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = true)
        check(scene, null)
        check(scene, Rgba(0.45f, 0.62f, 0.31f))
    }

    @Test
    fun casiParticolari() {
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, a: Float = 1f) = floatArrayOf(x, y, z, nx, ny, nz, 0.2f, 0.5f, 0.9f, a)
        // Vetro senza normale (va in alto come le lampade), vertice a metà alla fine (si ignora), solo facce verso il basso.
        val opaque = v(0f, 300f, 0f, 0f, -1f, 0f) + v(100f, 300f, 0f, 0f, -1f, 0f) + v(0f, 300f, 100f, 0f, -1f, 0f) + floatArrayOf(1f, 2f, 3f)
        val transparent = v(0f, 0f, 0f, 0f, 0f, 0f, 0.3f) + v(-50f, 10f, 5f, 1f, 0f, 0f, 0.3f) + v(5f, -20f, 70f, 0f, 0f, 1f, 0.3f)
        val scene = Scene3D(opaque, transparent, emptyList(), emptyList(), null)
        check(scene, null)
        // Senza superfici illuminate non c'è terreno.
        check(scene, Rgba(1f, 1f, 1f))
        // Scena vuota: un file valido senza oggetti.
        assertEquals(emptyList(), read(Glb.fromScene(Scene3D(FloatArray(0), FloatArray(0), emptyList(), emptyList(), null), Rgba(1f, 1f, 1f))).map { it.name })
    }
}
