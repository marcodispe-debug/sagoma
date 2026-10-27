package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WallFinish
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Sopra un varco ad arco il materiale della parete segue la curva, senza gradini. */
class ArchCoveringTest {
    private val saved = MaterialCatalog.items

    @AfterTest
    fun restore() = MaterialCatalog.set(saved)

    @Test
    fun `il materiale della parete segue l'arco senza gradini`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("prova", "Prova", "Intonaco", use = "parete")))
        for (style in listOf(PassageStyle.Arched, PassageStyle.Segmental, PassageStyle.Pointed)) {
            val o = Opening(1, OpeningKind.Passage, 0, 200.0, width = 120.0, height = 220.0, style = style)
            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(
                openings = listOf(o),
                wallFinish = WallFinish(0xFFFFFFFF, covering = Covering.Material, material = "prova", coveringHeight = WallFinish.FULL_HEIGHT),
            )
            val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
            val v = scene.textured.getValue("prova")
            val (a, b) = Openings.span(room, o)
            val u = (b - a).normalized()
            val w = a.distanceTo(b)
            var checked = 0
            for (k in v.indices step 8) {
                val p = com.sagoma.planimetria.model.Vec2(v[k].toDouble(), v[k + 2].toDouble())
                val y = v[k + 1].toDouble()
                val t = (p - a) dot u
                val off = abs((p - a).cross(u))
                if (off > 30 || t < 1 || t > w - 1 || y > 300) continue
                val arch = Openings.archTop(style, 0.0, w, 0.0, 220.0, t)
                // Niente materiale dentro il varco: ogni punto sta sull'arco o più in alto.
                assertTrue(y >= arch - 0.05, "$style: materiale a ${"%.1f".format(y)} cm sotto l'arco (${"%.1f".format(arch)}) a t = ${"%.1f".format(t)}")
                if (abs(y - arch) < 0.05) checked++
            }
            assertTrue(checked > 10, "$style: il bordo del materiale dovrebbe stare sull'arco ($checked punti)")
        }
    }
}
