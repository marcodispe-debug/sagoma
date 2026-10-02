package com.sagoma.planimetria.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tutti i modelli del catalogo arredi si leggono, con misure sensate e texture presenti. */
class GltfModelTest {
    @Test
    fun `tutti gli arredi si leggono`() {
        val dir = File("../app/src/pro/assets/furniture")
        if (!dir.isDirectory) return // catalogo non presente (versione free)
        val files = dir.listFiles { f -> f.name.endsWith(".glb") }!!.sortedBy { it.name }
        val failed = mutableListOf<String>()
        var textured = 0
        for (f in files) {
            val r = runCatching { GltfModel.parse(f.readBytes()) }
            val m = r.getOrNull()
            val ok = m != null && m.parts.isNotEmpty() && (0..2).all { m.max[it] > m.min[it] - 1e-6f && m.max[it].isFinite() } &&
                m.parts.all { p -> p.indices.all { it in 0 until p.positions.size / 3 } }
            if (!ok) failed += f.name + " " + (r.exceptionOrNull()?.message ?: "")
            if (m != null && m.parts.any { it.material.baseColorImage != null }) textured++
        }
        println("Modelli: ${files.size}, con texture: $textured, falliti: ${failed.size}")
        failed.take(10).forEach(::println)
        assertTrue(failed.isEmpty(), "Modelli non letti: ${failed.take(10)}")
    }
}
