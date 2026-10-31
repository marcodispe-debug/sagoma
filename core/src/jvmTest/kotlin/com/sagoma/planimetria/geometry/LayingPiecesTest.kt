package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Laying
import com.sagoma.planimetria.model.LayingPattern
import com.sagoma.planimetria.model.LayingStart
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.MaterialLayout
import com.sagoma.planimetria.model.MaterialPieces
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Posa a pezzi: dove vanno i pezzi, che pezzo di foto mostrano, quanti ne servono. */
class LayingPiecesTest {
    private val saved = MaterialCatalog.items

    @AfterTest
    fun restore() = MaterialCatalog.set(saved)

    private val region = RoomFactory.rectangle(420.0, 310.0).map { it + Vec2(-37.0, 12.0) }

    private fun inside(p: LaidPiece, q: Vec2): Boolean {
        val l = p.local(q)
        return l.x > 0 && l.x < 1 && l.y > 0 && l.y < 1
    }

    /** Ogni punto a caso della zona sta in al più un pezzo; restituisce la parte dei punti coperta. */
    private fun coverage(laying: Laying, rotation: Double): Double {
        val pieces = LayingPieces.cover(laying, rotation, Vec2(100.0, 150.0), region)
        assertTrue(pieces.isNotEmpty())
        val rnd = Random(3)
        var covered = 0
        val n = 4000
        repeat(n) {
            val q = Vec2(-37.0 + rnd.nextDouble() * 420, 12.0 + rnd.nextDouble() * 310)
            val hits = pieces.count { inside(it, q) }
            assertTrue(hits <= 1, "punto $q in $hits pezzi (${laying.pattern})")
            covered += hits
        }
        return covered.toDouble() / n
    }

    @Test
    fun `senza fuga i pezzi coprono tutto senza sovrapporsi, con ogni schema e verso`() {
        for (pattern in LayingPattern.entries) for (rotation in listOf(0.0, 30.0, 90.0)) {
            val l = Laying(pattern, width = 12.0, length = 48.0, joint = 0.0)
            assertEquals(1.0, coverage(l, rotation), 1e-9, "$pattern a $rotation°")
        }
    }

    @Test
    fun `la fuga lascia scoperto quanto deve`() {
        for (pattern in listOf(LayingPattern.Dritta, LayingPattern.Sfalsata, LayingPattern.SpinaDiPesce)) {
            val l = Laying(pattern, width = 20.0, length = 40.0, joint = 1.0)
            val expected = 20.0 * 40.0 / (21.0 * 41.0)
            assertEquals(expected, coverage(l, 15.0), 0.02, "$pattern")
        }
    }

    @Test
    fun `file sfalsate di mezzo pezzo, di un terzo e a correre`() {
        fun offsets(pattern: LayingPattern): Map<Int, Double> =
            LayingPieces.cover(Laying(pattern, 20.0, 90.0, 0.0), 0.0, Vec2(0.0, 0.0), region)
                .groupBy { it.row }.mapValues { (_, row) -> ((row.minOf { it.corner.x } % 90.0) + 90.0) % 90.0 }
        val half = offsets(LayingPattern.Sfalsata)
        assertEquals(45.0, abs(half.getValue(1) - half.getValue(0)), 1e-6)
        val third = offsets(LayingPattern.Terzi)
        assertEquals(30.0, third.getValue(1), 1e-6)
        assertEquals(60.0, third.getValue(2), 1e-6)
        // A correre: le giunte di una fila sono sempre ad almeno un quarto di pezzo da quelle della fila accanto.
        val random = offsets(LayingPattern.ACorrere)
        for (j in random.keys) {
            val next = random[j + 1] ?: continue
            val d = abs(next - random.getValue(j))
            assertTrue(minOf(d, 90 - d) >= 22.5 - 1e-6, "file $j e ${j + 1}: $d")
        }
        assertTrue(random.values.distinct().size > 3)
    }

    @Test
    fun `la spina di pesce ha i pezzi a 45 gradi, ad angolo retto tra loro`() {
        val pieces = LayingPieces.cover(Laying(LayingPattern.SpinaDiPesce, 10.0, 60.0, 0.2), 0.0, Vec2(0.0, 0.0), region)
        val dirs = pieces.map { Math.toDegrees(kotlin.math.atan2(it.u.y, it.u.x)).let { a -> ((a % 180) + 180) % 180 } }.toSet()
        assertEquals(setOf(45.0, 135.0), dirs.map { Math.round(it).toDouble() }.toSet())
    }

    @Test
    fun `conta interi e tagliati, partendo da un angolo o dal centro`() {
        // Muri da 15: dentro 400 × 300.
        val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(415.0, 315.0)).copy(floorMaterial = "gres-prova")
        MaterialCatalog.set(listOf(MaterialCatalog.Item("gres-prova", "Gres", "Piastrelle", size = 100.0, use = "pavimento")))
        fun count(l: Laying) = assertNotNull(LayingPieces.count(room.copy(floorLayout = MaterialLayout(laying = l))))
        // 50 × 50 sta giusto: 8 × 6 interi, da qualsiasi parte si parta.
        assertEquals(LayingPieces.Count(48, 0, 12.0), count(Laying(width = 50.0, length = 50.0, joint = 0.0, start = LayingStart.Angolo)))
        assertEquals(LayingPieces.Count(48, 0, 12.0), count(Laying(width = 50.0, length = 50.0, joint = 0.0)))
        // 60 × 60 dall'angolo: 6 colonne intere e una tagliata, 5 file intere.
        assertEquals(LayingPieces.Count(30, 5, 12.0), count(Laying(width = 60.0, length = 60.0, joint = 0.0, start = LayingStart.Angolo)))
        // Dal centro: tagli da 20 cm sui due lati lunghi (6 colonne intere + 2 tagliate), e sui lati corti mezzi pezzi.
        val centered = count(Laying(width = 60.0, length = 60.0, joint = 0.0))
        assertEquals(24, centered.whole)
        assertEquals(24, centered.cut)
        // Senza posa a pezzi, niente conteggio.
        assertNull(LayingPieces.count(room))
    }

    @Test
    fun `pezzi trovati nelle foto del catalogo`() {
        // Gres beige: piastrelle in griglia, si posa dritto con la fuga della foto.
        val gres = MaterialCatalog.Item("tiles139", "Gres beige", "Piastrelle", size = 180.0, use = "entrambi")
        val photo = assertIs<MaterialPieces.Pieces>(MaterialPieces.of("tiles139"))
        assertTrue(photo.grid)
        val l = MaterialPieces.defaultLaying(gres)
        assertEquals(LayingPattern.Dritta, l.pattern)
        assertEquals(l.width, l.length, 2.0)
        assertTrue(l.width in 25.0..33.0, "piastrelle di ${l.width} cm")
        assertEquals(photo.groutArgb, l.jointArgb)
        // Listoni: doghe lunghe e strette, a correre, con una fuga sottile.
        val planks = MaterialPieces.defaultLaying(MaterialCatalog.Item("woodfloor051", "Listoni rovere", "Parquet e legno", size = 100.0, use = "pavimento"))
        assertEquals(LayingPattern.ACorrere, planks.pattern)
        assertTrue(planks.length > 4 * planks.width)
        assertEquals(0.1, planks.joint)
        // Spina di pesce già nella foto: non si posa a pezzi; materiale sconosciuto: foto senza pezzi.
        assertFalse(MaterialPieces.canLay("woodfloor052"))
        assertEquals(MaterialPieces.Plain, MaterialPieces.of("non-esiste"))
        assertTrue(MaterialPieces.canLay("marble021"))
    }

    @Test
    fun `cambiando materiale la posa resta o riparte`() {
        MaterialCatalog.set(
            listOf(
                MaterialCatalog.Item("woodfloor051", "Listoni rovere", "Parquet e legno", size = 100.0, use = "pavimento"),
                MaterialCatalog.Item("woodfloor040", "Parquet rovere miele", "Parquet e legno", size = 120.0, use = "pavimento"),
                MaterialCatalog.Item("tiles139", "Gres beige", "Piastrelle", size = 180.0, use = "entrambi"),
                MaterialCatalog.Item("woodfloor052", "Spina di pesce rovere", "Parquet e legno", size = 100.0, use = "pavimento"),
            ),
        )
        val mine = MaterialLayout(rotation = 90.0, laying = Laying(LayingPattern.SpinaDiPesce, 10.0, 70.0, 0.1))
        assertEquals(mine, MaterialPieces.relay(mine, "woodfloor051", "woodfloor040"))
        val tiles = MaterialPieces.relay(mine, "woodfloor051", "tiles139")
        assertEquals(LayingPattern.Dritta, tiles.laying?.pattern)
        assertEquals(90.0, tiles.rotation)
        assertNull(MaterialPieces.relay(mine, "woodfloor051", "woodfloor052").laying)
        assertEquals(MaterialLayout(), MaterialPieces.relay(MaterialLayout(), "woodfloor051", "tiles139"))
    }

    /** Coordinate della foto dei vertici dei triangoli con il materiale `id`. */
    private fun uvs(scene: Scene3D, id: String): List<Vec2> {
        val v = scene.textured.getValue(id)
        return (v.indices step 8).map { Vec2(v[it + 6].toDouble(), v[it + 7].toDouble()) }
    }

    private fun inRect(q: Vec2, r: MaterialPieces.Rect) =
        q.x >= r.x - 1e-4 && q.x <= r.x + r.w + 1e-4 && q.y >= r.y - 1e-4 && q.y <= r.y + r.h + 1e-4

    @Test
    fun `nel 3D ogni piastrella mostra una piastrella vera della foto, senza la fuga della foto`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("tiles139", "Gres beige", "Piastrelle", size = 180.0, use = "entrambi")))
        val photo = MaterialPieces.of("tiles139") as MaterialPieces.Pieces
        val laying = Laying(LayingPattern.Sfalsata, 30.0, 30.0, 0.4, 0xFF808080)
        val room = Room(1, "Bagno", RoomType.Bagno, RoomFactory.rectangle(300.0, 250.0))
            .copy(floorMaterial = "tiles139", floorLayout = MaterialLayout(rotation = 20.0, laying = laying))
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val tris = uvs(scene, "tiles139").chunked(3)
        assertTrue(tris.size > 100)
        // I tre vertici di un triangolo stanno nello stesso pezzo della foto.
        for (t in tris) assertTrue(photo.rects.any { r -> t.all { inRect(it, r) } }, "triangolo fuori dai pezzi: $t")
        // Sotto i pezzi il pavimento ha il colore della fuga.
        assertTrue(scene.opaque.toList().windowed(3, 1).any { (r, g, b) -> abs(r - 0.502f) < 0.003 && abs(g - 0.502f) < 0.003 && abs(b - 0.502f) < 0.003 })
    }

    @Test
    fun `anche le pareti si posano a pezzi, da terra con una fila intera`() {
        MaterialCatalog.set(listOf(MaterialCatalog.Item("tiles139", "Gres beige", "Piastrelle", size = 180.0, use = "entrambi")))
        val laying = Laying(LayingPattern.Dritta, 20.0, 20.0, 0.2, start = LayingStart.Angolo)
        val finish = WallFinish(0xFFFFFFFF, covering = Covering.Material, material = "tiles139", coveringHeight = 120.0, layout = MaterialLayout(laying = laying))
        val room = Room(1, "Bagno", RoomType.Bagno, RoomFactory.rectangle(300.0, 250.0)).copy(wallFinish = finish)
        val scene = Scene3D.build(FloorPlan(listOf(room)), null, null, ceilings = false)
        val v = scene.textured.getValue("tiles139")
        val heights = (v.indices step 8).map { v[it + 1].toDouble() }
        assertTrue(heights.isNotEmpty())
        assertTrue(heights.all { it >= -1e-6 && it <= 120.0 + 1e-6 })
        // Le fughe orizzontali stanno a 20,2 cm l'una dall'altra partendo da terra: la prima fila è intera.
        assertTrue(heights.any { abs(it - 20.0) < 1e-3 })
        assertTrue(heights.any { abs(it - 20.2) < 1e-3 })
    }

    @Test
    fun `la posa si salva e i progetti vecchi restano come prima`() {
        val plain = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(floorMaterial = "marble021")
        assertFalse("laying" in PlanJson.encode(Building.single(FloorPlan(listOf(plain)))))
        val laid = plain.copy(floorLayout = MaterialLayout(45.0, laying = Laying(LayingPattern.SpinaDiPesce, 9.0, 63.0, 0.2, 0xFF332211, LayingStart.Angolo)))
        val back = PlanJson.decodeBuilding(PlanJson.encode(Building.single(FloorPlan(listOf(laid)))))
        assertEquals(laid.floorLayout, back.floor.plan.rooms.single().floorLayout)
    }
}
