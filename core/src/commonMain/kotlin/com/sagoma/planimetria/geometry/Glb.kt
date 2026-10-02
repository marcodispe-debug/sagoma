package com.sagoma.planimetria.geometry

import kotlin.math.pow

/**
 * La [Scene3D] come file glTF binario (GLB) in memoria, per i motori grafici che leggono glTF (Filament
 * nella versione pro). Tre oggetti, ognuno con il suo materiale:
 * - "lit": superfici opache illuminate (ruvide, non metalliche), più il terreno attorno alla casa;
 * - "unlit": superfici che emettono luce (lampade), senza ombreggiatura;
 * - "glass": vetri trasparenti, visibili dai due lati.
 * Posizioni in metri (la scena è in cm), colori dei vertici convertiti da sRGB a lineari.
 */
object Glb {
    private const val SCALE = 0.01f

    private class Part(val name: String, val material: Int) {
        val pos = FloatList()
        val nor = FloatList()
        val col = FloatList()
        val count get() = pos.size / 3
    }

    private class FloatList {
        var data = FloatArray(1024)
        var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
    }

    /** sRGB → lineare, con una tabella per non ricalcolare la potenza a ogni vertice. */
    private val toLinear = FloatArray(256) { (it / 255.0).let { c -> if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }.toFloat() }
    private fun linear(c: Float) = toLinear[(c * 255f + 0.5f).toInt().coerceIn(0, 255)]

    /** `ground`: colore del terreno sotto e attorno alla casa (null = niente terreno). */
    fun fromScene(scene: Scene3D, ground: Rgba? = null): ByteArray {
        val lit = Part("lit", 0)
        val unlit = Part("unlit", 1)
        val glass = Part("glass", 2)
        // Facce rivolte in basso (soffitti): a due facce, così fanno ombra anche al sole che arriva dall'alto.
        val down = Part("lit_down", 3)
        val n = Scene3D.FLOATS_PER_VERTEX
        fun copy(src: FloatArray, glassy: Boolean) {
            var k = 0
            while (k + n <= src.size) {
                val emissive = src[k + 3] == 0f && src[k + 4] == 0f && src[k + 5] == 0f
                val p = when {
                    glassy -> glass
                    emissive -> unlit
                    src[k + 4] < -0.9f -> down
                    else -> lit
                }
                p.pos.add(src[k] * SCALE); p.pos.add(src[k + 1] * SCALE); p.pos.add(src[k + 2] * SCALE)
                if (emissive) { p.nor.add(0f); p.nor.add(1f); p.nor.add(0f) } else { p.nor.add(src[k + 3]); p.nor.add(src[k + 4]); p.nor.add(src[k + 5]) }
                p.col.add(linear(src[k + 6])); p.col.add(linear(src[k + 7])); p.col.add(linear(src[k + 8])); p.col.add(src[k + 9])
                k += n
            }
        }
        copy(scene.opaque, glassy = false)
        copy(scene.transparent, glassy = true)
        if (ground != null && lit.count > 0) addGround(lit, ground)
        return write(listOf(lit, unlit, glass, down).filter { it.count > 0 })
    }

    /** Terreno: un grande quadrato poco sotto il pavimento più basso, che riceve le ombre della casa. */
    private fun addGround(p: Part, color: Rgba) {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until p.count) {
            minX = minOf(minX, p.pos.data[3 * i]); maxX = maxOf(maxX, p.pos.data[3 * i])
            minY = minOf(minY, p.pos.data[3 * i + 1])
            minZ = minOf(minZ, p.pos.data[3 * i + 2]); maxZ = maxOf(maxZ, p.pos.data[3 * i + 2])
        }
        val m = 40f
        val y = minY - 0.005f
        val c = floatArrayOf(linear(color.r), linear(color.g), linear(color.b), 1f)
        // Due triangoli rivolti verso l'alto (antiorari visti da sopra).
        val corners = listOf(minX - m to minZ - m, minX - m to maxZ + m, maxX + m to maxZ + m, minX - m to minZ - m, maxX + m to maxZ + m, maxX + m to minZ - m)
        for ((x, z) in corners) {
            p.pos.add(x); p.pos.add(y); p.pos.add(z)
            p.nor.add(0f); p.nor.add(1f); p.nor.add(0f)
            for (v in c) p.col.add(v)
        }
    }

    private fun write(parts: List<Part>): ByteArray {
        val bin = ByteWriter(1 shl 16)
        val views = StringBuilder()
        val accessors = StringBuilder()
        val meshes = StringBuilder()
        val nodes = StringBuilder()
        var viewIndex = 0
        fun sep(sb: StringBuilder) { if (sb.isNotEmpty()) sb.append(',') }
        /** Scrive `count` elementi da `size` float e restituisce l'indice dell'accessor. */
        fun accessor(list: FloatList, size: Int, type: String, bounds: Boolean): Int {
            val offset = bin.size
            for (i in 0 until list.size) bin.float(list.data[i])
            sep(views); views.append("""{"buffer":0,"byteOffset":$offset,"byteLength":${list.size * 4},"target":34962}""")
            sep(accessors)
            accessors.append("""{"bufferView":$viewIndex,"componentType":5126,"count":${list.size / size},"type":"$type"""")
            if (bounds) {
                val min = FloatArray(size) { Float.MAX_VALUE }
                val max = FloatArray(size) { -Float.MAX_VALUE }
                for (i in 0 until list.size) {
                    val c = i % size
                    min[c] = minOf(min[c], list.data[i]); max[c] = maxOf(max[c], list.data[i])
                }
                accessors.append(""","min":[${min.joinToString(",")}],"max":[${max.joinToString(",")}]""")
            }
            accessors.append('}')
            return viewIndex++
        }
        parts.forEachIndexed { i, p ->
            val a = accessor(p.pos, 3, "VEC3", bounds = true)
            val b = accessor(p.nor, 3, "VEC3", bounds = false)
            val c = accessor(p.col, 4, "VEC4", bounds = false)
            sep(meshes); meshes.append("""{"primitives":[{"attributes":{"POSITION":$a,"NORMAL":$b,"COLOR_0":$c},"material":${p.material},"mode":4}]}""")
            sep(nodes); nodes.append("""{"name":"${p.name}","mesh":$i}""")
        }
        val materials = """[
            {"name":"lit","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.78}},
            {"name":"unlit","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1},"extensions":{"KHR_materials_unlit":{}}},
            {"name":"glass","alphaMode":"BLEND","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.05}},
            {"name":"lit_down","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.78}}
        ]""".replace(Regex("\\s+"), "")
        val json = """{"asset":{"version":"2.0","generator":"Sagoma"},"extensionsUsed":["KHR_materials_unlit"],""" +
            """"scene":0,"scenes":[{"nodes":[${parts.indices.joinToString(",")}]}],"nodes":[$nodes],"meshes":[$meshes],""" +
            """"materials":$materials,"accessors":[$accessors],"bufferViews":[$views],"buffers":[{"byteLength":${bin.size}}]}"""
        return pack(json.encodeToByteArray(), bin.toByteArray())
    }

    /** Intestazione GLB, blocco JSON (riempito di spazi) e blocco binario (riempito di zeri), allineati a 4. */
    private fun pack(json: ByteArray, bin: ByteArray): ByteArray {
        val jsonPad = (4 - json.size % 4) % 4
        val binPad = (4 - bin.size % 4) % 4
        val total = 12 + 8 + json.size + jsonPad + 8 + bin.size + binPad
        val out = ByteWriter(total)
        out.int(0x46546C67).int(2).int(total)
        out.int(json.size + jsonPad).int(0x4E4F534A).bytes(json)
        repeat(jsonPad) { out.byte(' '.code) }
        out.int(bin.size + binPad).int(0x004E4942).bytes(bin)
        repeat(binPad) { out.byte(0) }
        return out.toByteArray()
    }
}
