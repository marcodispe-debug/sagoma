package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * La vista 3D del computer disegna la stanza arredata con la luce dell'ora scelta: giorno, mattina, tramonto,
 * notte con le luci accese, e dentro camminando. Le immagini si salvano in build/ per controllarle a occhio.
 */
class DesktopRendererTest {
    private val assets = File("../app/src/pro/assets")
    private fun read(path: String): ByteArray? = File(assets, path).takeIf { it.isFile }?.readBytes()

    init {
        read("furniture/catalog.json")?.let { FurnitureCatalog.load(it.decodeToString()) }
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
    }

    private val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0)).copy(
        openings = listOf(Opening.default(1, OpeningKind.Window2, 1, 200.0), Opening.default(2, OpeningKind.Door, 2, 150.0)),
        fixtures = listOf(Fixture(1, FixtureKind.CeilingLight, point = Vec2(250.0, 200.0))),
        floorMaterial = if (assets.isDirectory) "woodfloor040" else null,
    )
    private val furniture = listOf(
        Furniture(1, "ph_sofa_01", Vec2(200.0, 60.0), rotation = 0.0, width = 157.0, depth = 66.0, height = 80.0),
        Furniture(2, "ph_armchair_01", Vec2(80.0, 200.0), rotation = 90.0, width = 85.0, depth = 77.0, height = 107.0),
        Furniture(3, "ph_ottoman_01", Vec2(220.0, 200.0), width = 88.0, depth = 62.0, height = 45.0),
    )

    /** Divano angolare normale (a sinistra) e specchiato (a destra): la penisola deve stare dall'altra parte. */
    @Test
    fun divanoSpecchiato() {
        val sofa = Furniture(10, "ob_5ec9697d85654005", Vec2(130.0, 150.0), width = 200.0, depth = 140.0, height = 78.0)
        shot("specchiato", 11f, walking = false, items = listOf(sofa, sofa.copy(id = 11, center = Vec2(370.0, 150.0), mirrored = true)))
    }

    /** Ripostiglio accostato al muro esterno di una stanza, visto da fuori (build/prova-3d-ripostiglio-*.png). */
    @Test
    fun ripostiglioAccostato() {
        val big = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 400.0))
        for ((name, thickness) in listOf("uguale" to big.wallThickness, "sottile" to 10.0)) {
            val small = Room(2, "Ripostiglio", RoomType.Altro, RoomFactory.rectangle(150.0, 120.0).map { it + com.sagoma.planimetria.model.Vec2(500.0, 140.0) })
                .copy(wallThickness = thickness)
            val scene = Scene3D.build(FloorPlan(listOf(big, small)), null, null, ceilings = false)
            val r = DesktopGlSceneRenderer(::read)
            r.setScene(scene)
            r.setDaylight(11f, "auto")
            r.setCamera(Vec3(1100.0, 350.0, 700.0), Vec3(550.0, 120.0, 200.0), Vec3.Up, 50.0, 5.0, walking = false)
            File("build/prova-3d-ripostiglio-$name.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
            r.release()
        }
    }

    /**
     * Ripostiglio ricavato DENTRO il soggiorno, contro il suo muro esterno: da fuori la facciata deve essere
     * continua, senza una striscia di altro colore (build/prova-3d-ripostiglio-interno.png).
     */
    @Test
    fun ripostiglioDentroLaStanza() {
        val big = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(725.0, 415.0))
            .copy(wallFinish = com.sagoma.planimetria.model.WallFinish(0xFFE8B89A))
        val small = Room(2, "ripostiglio", RoomType.Altro, RoomFactory.rectangle(213.0, 86.0).map { it + com.sagoma.planimetria.model.Vec2(92.0, 0.0) })
        val scene = Scene3D.build(FloorPlan(listOf(big, small)), null, null, ceilings = false)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        // Da fuori, davanti al muro in alto della pianta (quello contro cui sta il ripostiglio).
        r.setCamera(Vec3(360.0, 250.0, -700.0), Vec3(360.0, 120.0, 0.0), Vec3.Up, 50.0, 5.0, walking = false)
        val img = r.renderNow(960, 600)!!
        File("build/prova-3d-ripostiglio-interno.png").writeBytes(SkiaImages.encodePng(img)!!)
        r.release()
        // La facciata è tutta dello stesso colore: nessun pixel molto diverso lungo una riga a metà altezza.
        val px = IntArray(960 * 600)
        img.readPixels(px)
        val y = 380
        val row = (250 until 710).map { px[y * 960 + it] }
        fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        val spread = row.maxOf(::lum) - row.minOf(::lum)
        assertTrue(spread < 60, "facciata non uniforme (differenza $spread)")
    }

    /**
     * Scala con ringhiera vista dal primo piano: continua sopra il pavimento o tagliata a filo
     * (build/prova-3d-ringhiera-continua.png e prova-3d-ringhiera-tagliata.png).
     */
    @Test
    fun ringhieraScalaAlPianoDiSopra() {
        val ground = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))
        val stair = com.sagoma.planimetria.model.Stair(
            1, com.sagoma.planimetria.model.StairKind.Straight, com.sagoma.planimetria.model.Vec2(300.0, 225.0), rotation = 90.0,
            railing = com.sagoma.planimetria.model.StairRailing.Metal,
        )
        val upper = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(600.0, 450.0))
        for ((name, above) in listOf("continua" to true, "tagliata" to false)) {
            val below = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(ground), stairs = listOf(stair.copy(railingAboveFloor = above))))
            val scene = Scene3D.build(FloorPlan(listOf(upper)), null, null, ceilings = false, below = listOf(below))
            val r = DesktopGlSceneRenderer(::read)
            r.setScene(scene)
            r.setDaylight(11f, "auto")
            r.setCamera(Vec3(700.0, 900.0, 800.0), Vec3(300.0, 0.0, 225.0), Vec3.Up, 45.0, 5.0, walking = false)
            File("build/prova-3d-ringhiera-$name.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
            r.release()
        }
    }

    /** Muri eliminati: soggiorno e cucina in un unico ambiente, e un lato della cucina aperto (build/prova-3d-muri-eliminati.png). */
    @Test
    fun muriEliminati() {
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 300.0)).copy(removedWalls = setOf(1))
        val kitchen = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 300.0).map { it + com.sagoma.planimetria.model.Vec2(400.0, 0.0) })
            .copy(removedWalls = setOf(3, 1))
        val scene = Scene3D.build(FloorPlan(listOf(living, kitchen)), null, null, ceilings = false)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(350.0, 800.0, 900.0), Vec3(350.0, 0.0, 150.0), Vec3.Up, 50.0, 5.0, walking = false)
        File("build/prova-3d-muri-eliminati.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        r.release()
    }

    /** Scala a L con il pavimento di sopra fino al pianerottolo (build/prova-3d-vano-coperto.png). */
    @Test
    fun vanoScalaCoperto() {
        val ground = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))
        val stair = com.sagoma.planimetria.model.Stair(
            1, com.sagoma.planimetria.model.StairKind.LTurn, com.sagoma.planimetria.model.Vec2(300.0, 250.0),
            railing = com.sagoma.planimetria.model.StairRailing.Metal, wellRailing = com.sagoma.planimetria.model.StairRailing.Metal,
        )
        val l = com.sagoma.planimetria.geometry.Stairs.layout(stair, 300.0)
        val covered = stair.copy(coveredSteps = l.steps.indexOfFirst { it.landing } + 1, railingAboveFloor = false)
        val below = com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(ground), stairs = listOf(covered)), 300.0)
        val upper = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(600.0, 450.0))
        val scene = Scene3D.build(FloorPlan(listOf(upper)), null, null, ceilings = false, levelHeight = 300.0, below = listOf(below))
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(700.0, 900.0, 800.0), Vec3(300.0, 0.0, 225.0), Vec3.Up, 45.0, 5.0, walking = false)
        File("build/prova-3d-vano-coperto.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        r.release()
    }

    /** Camminando al piano terra si vede il vano scala nel soffitto e, oltre, il piano di sopra (build/prova-3d-vano-camminando.png). */
    @Test
    fun vanoScalaCamminando() {
        val ground = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))
        val stair = com.sagoma.planimetria.model.Stair(
            1, com.sagoma.planimetria.model.StairKind.Straight, com.sagoma.planimetria.model.Vec2(300.0, 150.0), rotation = 90.0,
            railing = com.sagoma.planimetria.model.StairRailing.Wood, wellRailing = com.sagoma.planimetria.model.StairRailing.Wood,
        )
        val upper = com.sagoma.planimetria.model.Floor(2, "Primo piano", FloorPlan(listOf(Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(600.0, 450.0)))), 300.0)
        val scene = Scene3D.build(FloorPlan(listOf(ground), stairs = listOf(stair)), null, null, ceilings = true, levelHeight = 300.0, above = upper)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(300.0, 160.0, 400.0), Vec3(300.0, 330.0, 150.0), Vec3.Up, 80.0, 5.0, walking = true)
        File("build/prova-3d-vano-camminando.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        r.release()
    }

    /**
     * Interpiano uguale all'altezza del soffitto (270) e un muro del piano di sopra che passa sopra la scala:
     * camminando il soffitto non deve sfarfallare né comparire un muro appeso (build/prova-3d-vano-270.png).
     */
    @Test
    fun vanoScalaInterpiano270() {
        val ground = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))
        val stair = com.sagoma.planimetria.model.Stair(
            1, com.sagoma.planimetria.model.StairKind.Straight, com.sagoma.planimetria.model.Vec2(300.0, 150.0), rotation = 90.0,
            railing = com.sagoma.planimetria.model.StairRailing.Wood,
        )
        // Di sopra due stanze divise da un muro a x = 300, proprio sopra la scala.
        val up1 = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(300.0, 450.0))
        val up2 = Room(3, "Studio", RoomType.Studio, RoomFactory.rectangle(300.0, 450.0).map { it + com.sagoma.planimetria.model.Vec2(300.0, 0.0) })
        val upper = com.sagoma.planimetria.model.Floor(2, "Primo piano", FloorPlan(listOf(up1, up2)), 270.0)
        val scene = Scene3D.build(FloorPlan(listOf(ground), stairs = listOf(stair)), null, null, ceilings = true, levelHeight = 270.0, above = upper)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(300.0, 160.0, 420.0), Vec3(300.0, 300.0, 150.0), Vec3.Up, 80.0, 5.0, walking = true)
        File("build/prova-3d-vano-270.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        r.release()
    }

    /** Passaggio ad arco tra due stanze: l'arco deve essere una curva continua (build/prova-3d-arco.png). */
    @Test
    fun passaggioAdArco() {
        val a = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(400.0, 400.0)).copy(
            openings = listOf(Opening.default(1, OpeningKind.Passage, 1, 200.0).copy(width = 160.0, height = 230.0, style = com.sagoma.planimetria.model.PassageStyle.Arched)),
        )
        val b = Room(2, "Cucina", RoomType.Cucina, RoomFactory.rectangle(300.0, 400.0).map { it + com.sagoma.planimetria.model.Vec2(400.0, 0.0) })
        val scene = Scene3D.build(FloorPlan(listOf(a, b)), null, null, ceilings = true)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(120.0, 150.0, 200.0), Vec3(400.0, 170.0, 200.0), Vec3.Up, 70.0, 5.0, walking = true)
        File("build/prova-3d-arco.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        r.release()
    }

    /** Guardando il primo piano si vede anche il piano terra sotto (il prato non lo copre). */
    @Test
    fun duePiani() {
        val upper = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(350.0, 300.0))
        val scene = Scene3D.build(
            FloorPlan(listOf(upper)), null, null, ceilings = false,
            below = listOf(com.sagoma.planimetria.model.Floor(1, "Piano terra", FloorPlan(listOf(room), furniture = furniture))),
        )
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(900.0, 500.0, 900.0), Vec3(250.0, -150.0, 200.0), Vec3.Up, 50.0, 5.0, walking = false)
        val img = r.renderNow(960, 600)!!
        File("build/prova-3d-due-piani.png").writeBytes(SkiaImages.encodePng(img)!!)
        r.release()
        // I muri del piano terra (bianchi) sotto il primo piano: in basso nell'immagine non c'è solo prato.
        val px = IntArray(960 * 600)
        img.readPixels(px)
        val light = (300 * 960 until 600 * 960).count { val p = px[it]; ((p shr 16) and 0xFF) > 200 && ((p shr 8) and 0xFF) > 200 && (p and 0xFF) > 200 }
        assertTrue(light > 5000, "pixel chiari del piano terra: $light")
    }

    /** Di notte una stanza senza lampade è quasi buia; con la plafoniera accesa si vede bene. */
    @Test
    fun notteSenzaLuci() {
        val dark = room.copy(fixtures = emptyList())
        val noLamps = shot("notte-senza-luci", 22f, walking = true, plan = FloorPlan(listOf(dark), furniture = furniture))
        val lit = shot("dentro-notte", 22f, walking = true)
        assertTrue(brightness(noLamps) < 25.0, "senza luci: ${brightness(noLamps)}")
        assertTrue(brightness(lit) > brightness(noLamps) * 3)
    }

    private fun shot(
        name: String, hour: Float, walking: Boolean, ao: Boolean = true, items: List<Furniture> = furniture,
        plan: FloorPlan = FloorPlan(listOf(room), furniture = items),
    ): IntArray {
        val scene = Scene3D.build(plan, null, null, ceilings = walking)
        val r = DesktopGlSceneRenderer(::read)
        r.ambientOcclusion = ao
        r.setScene(scene)
        r.setDaylight(hour, "auto")
        if (walking) r.setCamera(Vec3(440.0, 160.0, 250.0), Vec3(120.0, 70.0, 90.0), Vec3.Up, 80.0, 5.0, walking = true)
        else r.setCamera(if (items === furniture) Vec3(250.0, 650.0, 850.0) else Vec3(250.0, 800.0, 420.0), Vec3(250.0, 0.0, 200.0), Vec3.Up, 50.0, 5.0, walking = false)
        val img = r.renderNow(960, 600)
        assertNotNull(img)
        File("build/prova-3d-$name.png").writeBytes(SkiaImages.encodePng(img)!!)
        val pixels = IntArray(960 * 600)
        img.readPixels(pixels)
        r.release()
        return pixels
    }

    private fun brightness(p: IntArray) = p.sumOf { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) } / (p.size * 3.0)

    @Test
    fun luceSecondoLOra() {
        val day = shot("giorno", 11f, walking = false)
        shot("mattina", 8.5f, walking = false)
        val sunset = shot("tramonto", 17.5f, walking = false)
        val night = shot("notte", 22f, walking = false)
        shot("dentro-giorno", 11f, walking = true)
        shot("dentro-giorno-senza-ao", 11f, walking = true, ao = false)
        shot("dentro-notte", 22f, walking = true)
        assertTrue(day.distinct().size > 10)
        assertTrue(brightness(night) < brightness(sunset) && brightness(sunset) < brightness(day))
    }
}
