package com.sagoma.planimetria.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Modello 3D di un arredo letto da un file glTF binario (.glb), in Kotlin puro: le parti (primitive) con le
 * posizioni già trasformate dai nodi del file, normali, coordinate delle texture, indici e materiale PBR
 * (colore, metallo/ruvidità, rilievo, emissione) con le immagini ancora codificate (JPEG/PNG). Legge anche i
 * dati compressi di KHR_mesh_quantization (interi normalizzati).
 */
class GltfModel(val parts: List<Part>, val min: FloatArray, val max: FloatArray) {

    class Material(
        val baseColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
        val baseColorImage: Int? = null,
        val metallic: Float = 1f,
        val roughness: Float = 1f,
        val metallicRoughnessImage: Int? = null,
        val normalImage: Int? = null,
        val emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
        val emissiveImage: Int? = null,
        /** Trasparente ("BLEND") o con ritagli ("MASK", es. foglie). */
        val blend: Boolean = false,
        val mask: Boolean = false,
        val doubleSided: Boolean = false,
    )

    /** Parte del modello: 3 float per posizione e normale, 2 per le coordinate della texture. */
    class Part(val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray, val material: Material)

    /** Immagini del file (JPEG/PNG), per indice di immagine. */
    var images: List<ByteArray> = emptyList()
        private set

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(glb: ByteArray): GltfModel {
            fun int(o: Int) = (glb[o].toInt() and 0xFF) or ((glb[o + 1].toInt() and 0xFF) shl 8) or
                ((glb[o + 2].toInt() and 0xFF) shl 16) or ((glb[o + 3].toInt() and 0xFF) shl 24)
            require(int(0) == 0x46546C67) { "Non è un file GLB" }
            val jsonLen = int(12)
            val root = json.parseToJsonElement(glb.decodeToString(20, 20 + jsonLen)).jsonObject
            val binStart = 20 + jsonLen + 8
            val g = Reader(root, glb, binStart)
            val parts = mutableListOf<Part>()
            val scene = root["scenes"]?.jsonArray?.getOrNull(root["scene"]?.jsonPrimitive?.intOrNull ?: 0)?.jsonObject
            val rootNodes = scene?.get("nodes")?.jsonArray?.map { it.jsonPrimitive.intOrNull ?: 0 }
                ?: (root["nodes"]?.jsonArray?.indices?.toList() ?: emptyList())
            for (n in rootNodes) g.node(n, identity(), parts)
            val mn = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val mx = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (p in parts) for (i in 0 until p.positions.size / 3) for (k in 0..2) {
                mn[k] = min(mn[k], p.positions[i * 3 + k]); mx[k] = max(mx[k], p.positions[i * 3 + k])
            }
            return GltfModel(parts, mn, mx).also { it.images = g.images() }
        }

        private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        /** a × b, matrici 4×4 in colonna. */
        private fun mul(a: FloatArray, b: FloatArray): FloatArray {
            val r = FloatArray(16)
            for (c in 0 until 4) for (row in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
                r[c * 4 + row] = s
            }
            return r
        }
    }

    private class Reader(val root: JsonObject, val glb: ByteArray, val binStart: Int) {
        val accessors = root["accessors"]?.jsonArray ?: JsonArray(emptyList())
        val views = root["bufferViews"]?.jsonArray ?: JsonArray(emptyList())
        val meshes = root["meshes"]?.jsonArray ?: JsonArray(emptyList())
        val nodes = root["nodes"]?.jsonArray ?: JsonArray(emptyList())
        val materials = root["materials"]?.jsonArray ?: JsonArray(emptyList())
        val textures = root["textures"]?.jsonArray ?: JsonArray(emptyList())

        fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
        fun JsonObject.float(k: String) = this[k]?.jsonPrimitive?.doubleOrNull?.toFloat()
        fun JsonObject.floats(k: String) = this[k]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull?.toFloat() ?: 0f }?.toFloatArray()

        fun images(): List<ByteArray> = root["images"]?.jsonArray?.map { im ->
            val v = im.jsonObject.int("bufferView")
            if (v == null) ByteArray(0) else {
                val bv = views[v].jsonObject
                val off = binStart + (bv.int("byteOffset") ?: 0)
                glb.copyOfRange(off, off + (bv.int("byteLength") ?: 0))
            }
        } ?: emptyList()

        /** Immagine usata da una texture (indice nell'elenco delle immagini). */
        fun image(textureRef: JsonObject?): Int? {
            val t = textureRef?.int("index") ?: return null
            return textures.getOrNull(t)?.jsonObject?.int("source")
        }

        fun material(i: Int?): Material {
            val m = i?.let { materials.getOrNull(it)?.jsonObject } ?: return Material(metallic = 0f, roughness = 0.8f)
            val pbr = m["pbrMetallicRoughness"]?.jsonObject
            return Material(
                baseColor = pbr?.floats("baseColorFactor") ?: floatArrayOf(1f, 1f, 1f, 1f),
                baseColorImage = image(pbr?.get("baseColorTexture")?.jsonObject),
                metallic = pbr?.float("metallicFactor") ?: 1f,
                roughness = pbr?.float("roughnessFactor") ?: 1f,
                metallicRoughnessImage = image(pbr?.get("metallicRoughnessTexture")?.jsonObject),
                normalImage = image(m["normalTexture"]?.jsonObject),
                emissive = m.floats("emissiveFactor") ?: floatArrayOf(0f, 0f, 0f),
                emissiveImage = image(m["emissiveTexture"]?.jsonObject),
                blend = m["alphaMode"]?.jsonPrimitive?.content == "BLEND",
                mask = m["alphaMode"]?.jsonPrimitive?.content == "MASK",
                doubleSided = m["doubleSided"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

        /** Valori di un accessor come float (interi normalizzati convertiti tra −1/0 e 1). */
        fun floats(a: Int): FloatArray {
            val acc = accessors[a].jsonObject
            val count = acc.int("count") ?: 0
            val comps = when (acc["type"]?.jsonPrimitive?.content) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1 }
            val type = acc.int("componentType") ?: 5126
            val normalized = acc["normalized"]?.jsonPrimitive?.booleanOrNull ?: false
            val out = FloatArray(count * comps)
            val v = acc.int("bufferView") ?: return out
            val bv = views[v].jsonObject
            val size = when (type) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            val stride = bv.int("byteStride") ?: (size * comps)
            val base = binStart + (bv.int("byteOffset") ?: 0) + (acc.int("byteOffset") ?: 0)
            for (i in 0 until count) for (c in 0 until comps) {
                val o = base + i * stride + c * size
                out[i * comps + c] = when (type) {
                    5126 -> Float.fromBits(i32(o))
                    5120 -> glb[o].toFloat().let { if (normalized) max(it / 127f, -1f) else it }
                    5121 -> (glb[o].toInt() and 0xFF).toFloat().let { if (normalized) it / 255f else it }
                    5122 -> i16(o).toFloat().let { if (normalized) max(it / 32767f, -1f) else it }
                    5123 -> (i16(o) and 0xFFFF).toFloat().let { if (normalized) it / 65535f else it }
                    else -> i32(o).toFloat()
                }
            }
            return out
        }

        fun ints(a: Int): IntArray {
            val acc = accessors[a].jsonObject
            val count = acc.int("count") ?: 0
            val type = acc.int("componentType") ?: 5125
            val v = acc.int("bufferView") ?: return IntArray(0)
            val bv = views[v].jsonObject
            val base = binStart + (bv.int("byteOffset") ?: 0) + (acc.int("byteOffset") ?: 0)
            return IntArray(count) { i ->
                when (type) {
                    5121 -> glb[base + i].toInt() and 0xFF
                    5123 -> i16(base + i * 2) and 0xFFFF
                    else -> i32(base + i * 4)
                }
            }
        }

        fun i16(o: Int) = ((glb[o].toInt() and 0xFF) or (glb[o + 1].toInt() shl 8)).toShort().toInt()
        fun i32(o: Int) = (glb[o].toInt() and 0xFF) or ((glb[o + 1].toInt() and 0xFF) shl 8) or ((glb[o + 2].toInt() and 0xFF) shl 16) or ((glb[o + 3].toInt() and 0xFF) shl 24)

        /** Matrice locale del nodo: `matrix`, oppure traslazione × rotazione × scala. */
        fun local(n: JsonObject): FloatArray {
            n.floats("matrix")?.let { if (it.size == 16) return it }
            val t = n.floats("translation") ?: floatArrayOf(0f, 0f, 0f)
            val q = n.floats("rotation") ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = n.floats("scale") ?: floatArrayOf(1f, 1f, 1f)
            val (x, y, z, w) = listOf(q[0], q[1], q[2], q[3])
            val r = floatArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
                2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
                0f, 0f, 0f, 1f,
            )
            for (c in 0..2) for (row in 0..2) r[c * 4 + row] *= s[c]
            r[12] = t[0]; r[13] = t[1]; r[14] = t[2]
            return r
        }

        fun node(i: Int, parent: FloatArray, out: MutableList<Part>) {
            val n = nodes.getOrNull(i)?.jsonObject ?: return
            val world = mul(parent, local(n))
            n.int("mesh")?.let { mi ->
                val mesh = meshes.getOrNull(mi)?.jsonObject
                mesh?.get("primitives")?.jsonArray?.forEach { pe ->
                    val p = pe.jsonObject
                    if ((p.int("mode") ?: 4) != 4) return@forEach // solo triangoli
                    val attrs = p["attributes"]?.jsonObject ?: return@forEach
                    val pos = floats(attrs.int("POSITION") ?: return@forEach)
                    val vc = pos.size / 3
                    val nor = attrs.int("NORMAL")?.let { floats(it) } ?: FloatArray(vc * 3)
                    val uv = attrs.int("TEXCOORD_0")?.let { floats(it) } ?: FloatArray(vc * 2)
                    val idx = p.int("indices")?.let { ints(it) } ?: IntArray(vc) { it }
                    transform(pos, nor, world)
                    out += Part(pos, nor, uv, idx, material(p.int("material")))
                }
            }
            n["children"]?.jsonArray?.forEach { c -> c.jsonPrimitive.intOrNull?.let { node(it, world, out) } }
        }

        /** Posizioni per la matrice del nodo; normali per la sua parte di rotazione/scala, poi normalizzate. */
        fun transform(pos: FloatArray, nor: FloatArray, m: FloatArray) {
            for (i in 0 until pos.size / 3) {
                val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
                pos[i * 3] = m[0] * x + m[4] * y + m[8] * z + m[12]
                pos[i * 3 + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
                pos[i * 3 + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
                val a = nor[i * 3]; val b = nor[i * 3 + 1]; val c = nor[i * 3 + 2]
                var nx = m[0] * a + m[4] * b + m[8] * c
                var ny = m[1] * a + m[5] * b + m[9] * c
                var nz = m[2] * a + m[6] * b + m[10] * c
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (l > 1e-6f) { nx /= l; ny /= l; nz /= l }
                nor[i * 3] = nx; nor[i * 3 + 1] = ny; nor[i * 3 + 2] = nz
            }
        }
    }
}
