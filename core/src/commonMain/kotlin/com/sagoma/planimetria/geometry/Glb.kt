package com.sagoma.planimetria.geometry

import kotlin.math.pow

/**
 * La [Scene3D] come file glTF binario (GLB) in memoria, per i motori grafici che leggono glTF (Filament).
 * Tre oggetti, ognuno con il suo materiale:
 * - "lit": superfici opache illuminate (ruvide, non metalliche), più il terreno attorno alla casa;
 * - "unlit": superfici che emettono luce (lampade), senza ombreggiatura;
 * - "glass": vetri trasparenti, visibili dai due lati.
 * Posizioni in metri (la scena è in cm), colori dei vertici convertiti da sRGB a lineari.
 */
object Glb {
    private const val SCALE = 0.01f

    /**
     * Un oggetto del file. Un primo giro sui vertici conta quanti sono e la loro estensione (serve nell'intestazione), un secondo
     * li scrive direttamente al loro posto nel file: niente liste che crescono e niente copie (con una casa grande il GLB
     * occupava 5-6 volte la memoria della scena e ci metteva quasi quanto a costruirla).
     */
    private class Part(val name: String, val material: Int) {
        var count = 0
        val min = FloatArray(3) { Float.MAX_VALUE }
        val max = FloatArray(3) { -Float.MAX_VALUE }

        /** Dove va il prossimo vertice nel file (byte): posizioni, normali e colori stanno in tre blocchi uno dopo l'altro. */
        var pos = 0
        var nor = 0
        var col = 0

        fun add(x: Float, y: Float, z: Float) {
            count++
            min[0] = minOf(min[0], x); max[0] = maxOf(max[0], x)
            min[1] = minOf(min[1], y); max[1] = maxOf(max[1], y)
            min[2] = minOf(min[2], z); max[2] = maxOf(max[2], z)
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
        fun partOf(src: FloatArray, k: Int, glassy: Boolean): Part = when {
            glassy -> glass
            src[k + 3] == 0f && src[k + 4] == 0f && src[k + 5] == 0f -> unlit // emette luce
            src[k + 4] < -0.9f -> down
            else -> lit
        }
        fun measure(src: FloatArray, glassy: Boolean) {
            var k = 0
            while (k + n <= src.size) {
                partOf(src, k, glassy).add(src[k] * SCALE, src[k + 1] * SCALE, src[k + 2] * SCALE)
                k += n
            }
        }
        measure(scene.opaque, glassy = false)
        measure(scene.transparent, glassy = true)
        val groundCorners = if (ground != null && lit.count > 0) groundCorners(lit).onEach { (x, y, z) -> lit.add(x, y, z) } else emptyList()
        val parts = listOf(lit, unlit, glass, down).filter { it.count > 0 }

        // Intestazione: dove sta ogni blocco (posizioni, normali, colori di ogni oggetto) nella parte binaria.
        val views = StringBuilder()
        val accessors = StringBuilder()
        val meshes = StringBuilder()
        val nodes = StringBuilder()
        var binSize = 0
        var viewIndex = 0
        fun sep(sb: StringBuilder) { if (sb.isNotEmpty()) sb.append(',') }
        /** Blocco di `count` elementi da `size` float; restituisce l'indice dell'accessor e dove inizia nella parte binaria. */
        fun accessor(p: Part, size: Int, type: String, bounds: Boolean): Int {
            val bytes = p.count * size * 4
            sep(views); views.append("""{"buffer":0,"byteOffset":$binSize,"byteLength":$bytes,"target":34962}""")
            sep(accessors)
            accessors.append("""{"bufferView":$viewIndex,"componentType":5126,"count":${p.count},"type":"$type"""")
            if (bounds) accessors.append(""","min":[${p.min.joinToString(",")}],"max":[${p.max.joinToString(",")}]""")
            accessors.append('}')
            val at = binSize
            binSize += bytes
            viewIndex++
            return at
        }
        parts.forEachIndexed { i, p ->
            val a = viewIndex
            p.pos = accessor(p, 3, "VEC3", bounds = true)
            p.nor = accessor(p, 3, "VEC3", bounds = false)
            p.col = accessor(p, 4, "VEC4", bounds = false)
            sep(meshes); meshes.append("""{"primitives":[{"attributes":{"POSITION":$a,"NORMAL":${a + 1},"COLOR_0":${a + 2}},"material":${p.material},"mode":4}]}""")
            sep(nodes); nodes.append("""{"name":"${p.name}","mesh":$i}""")
        }
        val materials = """[
            {"name":"lit","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.78}},
            {"name":"unlit","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1},"extensions":{"KHR_materials_unlit":{}}},
            {"name":"glass","alphaMode":"BLEND","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.05}},
            {"name":"lit_down","doubleSided":true,"pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.78}}
        ]""".replace(Regex("\\s+"), "")
        val json = (
            """{"asset":{"version":"2.0","generator":"Sagoma"},"extensionsUsed":["KHR_materials_unlit"],""" +
                """"scene":0,"scenes":[{"nodes":[${parts.indices.joinToString(",")}]}],"nodes":[$nodes],"meshes":[$meshes],""" +
                """"materials":$materials,"accessors":[$accessors],"bufferViews":[$views],"buffers":[{"byteLength":$binSize}]}"""
            ).encodeToByteArray()

        // Intestazione GLB, blocco JSON (riempito di spazi) e blocco binario (riempito di zeri), allineati a 4.
        val jsonPad = (4 - json.size % 4) % 4
        val binPad = (4 - binSize % 4) % 4
        val total = 12 + 8 + json.size + jsonPad + 8 + binSize + binPad
        val out = ByteArray(total)
        out.putInt(0, 0x46546C67); out.putInt(4, 2); out.putInt(8, total)
        out.putInt(12, json.size + jsonPad); out.putInt(16, 0x4E4F534A)
        json.copyInto(out, 20)
        for (k in 0 until jsonPad) out[20 + json.size + k] = ' '.code.toByte()
        val binHeader = 20 + json.size + jsonPad
        out.putInt(binHeader, binSize + binPad); out.putInt(binHeader + 4, 0x004E4942)
        val binStart = binHeader + 8
        for (p in parts) { p.pos += binStart; p.nor += binStart; p.col += binStart }

        // Secondo giro: i vertici al loro posto. Posizioni in metri, colori lineari; dove non c'è normale (luce emessa) va in alto.
        fun write(src: FloatArray, glassy: Boolean) {
            var k = 0
            while (k + n <= src.size) {
                val p = partOf(src, k, glassy)
                out.putFloat(p.pos, src[k] * SCALE); out.putFloat(p.pos + 4, src[k + 1] * SCALE); out.putFloat(p.pos + 8, src[k + 2] * SCALE)
                p.pos += 12
                if (src[k + 3] == 0f && src[k + 4] == 0f && src[k + 5] == 0f) { out.putFloat(p.nor, 0f); out.putFloat(p.nor + 4, 1f); out.putFloat(p.nor + 8, 0f) }
                else { out.putFloat(p.nor, src[k + 3]); out.putFloat(p.nor + 4, src[k + 4]); out.putFloat(p.nor + 8, src[k + 5]) }
                p.nor += 12
                out.putFloat(p.col, linear(src[k + 6])); out.putFloat(p.col + 4, linear(src[k + 7])); out.putFloat(p.col + 8, linear(src[k + 8]))
                out.putFloat(p.col + 12, src[k + 9])
                p.col += 16
                k += n
            }
        }
        write(scene.opaque, glassy = false)
        write(scene.transparent, glassy = true)
        if (ground != null && groundCorners.isNotEmpty()) {
            // Terreno: due triangoli rivolti verso l'alto, dopo i vertici della casa.
            val c = floatArrayOf(linear(ground.r), linear(ground.g), linear(ground.b), 1f)
            for ((x, y, z) in groundCorners) {
                out.putFloat(lit.pos, x); out.putFloat(lit.pos + 4, y); out.putFloat(lit.pos + 8, z)
                lit.pos += 12
                out.putFloat(lit.nor, 0f); out.putFloat(lit.nor + 4, 1f); out.putFloat(lit.nor + 8, 0f)
                lit.nor += 12
                for (v in c) { out.putFloat(lit.col, v); lit.col += 4 }
            }
        }
        return out
    }

    /**
     * Terreno: un grande quadrato poco sotto il pavimento più basso, che riceve le ombre della casa. I sei vertici dei due
     * triangoli (antiorari visti da sopra), dall'estensione delle superfici opache.
     */
    private fun groundCorners(p: Part): List<Triple<Float, Float, Float>> {
        val m = 40f
        val minX = p.min[0]; val maxX = p.max[0]
        val y = p.min[1] - 0.005f
        val minZ = p.min[2]; val maxZ = p.max[2]
        return listOf(minX - m to minZ - m, minX - m to maxZ + m, maxX + m to maxZ + m, minX - m to minZ - m, maxX + m to maxZ + m, maxX + m to minZ - m)
            .map { (x, z) -> Triple(x, y, z) }
    }

    private fun ByteArray.putInt(at: Int, v: Int) {
        this[at] = v.toByte(); this[at + 1] = (v shr 8).toByte(); this[at + 2] = (v shr 16).toByte(); this[at + 3] = (v shr 24).toByte()
    }

    private fun ByteArray.putFloat(at: Int, v: Float) = putInt(at, v.toRawBits())
}
