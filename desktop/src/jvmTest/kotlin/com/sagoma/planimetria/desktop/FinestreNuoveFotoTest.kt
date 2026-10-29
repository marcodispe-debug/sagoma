package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.editor.OpeningPreset
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test

/**
 * Foto 3D delle finestre nuove (inferriata, sopraluce, legno con fermavetro, persiana alla romana e bovindo) e delle porte
 * da garage (basculante e sezionale), zanzariere, tende da sole, vetromattone, persiane scorrevoli, porta tutto vetro, persiane accanto agli angoli e vetro satinato, viste da fuori e da dentro. Non controlla niente: serve a guardarle. Parte solo con SAGOMA_FOTO_FINESTRE=true; immagini in build/finestre/.
 */
class FinestreNuoveFotoTest {
    @Test
    fun foto() {
        if (System.getenv("SAGOMA_FOTO_FINESTRE") != "true") return
        val out = File("build/finestre").also { it.mkdirs() }
        fun o(id: Long, kind: OpeningKind, wall: Int, pos: Double, preset: OpeningPreset) =
            preset.apply(Opening.default(id, kind, wall, pos)).copy(position = pos)
        // Muro 0 (davanti, y = 0): inferriata, sopraluce, legno, romana. Muro 2 (dietro, y = 450): bovindo.
        val room = Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(600.0, 450.0)).copy(
            openings = listOf(
                o(1, OpeningKind.Window1, 0, 90.0, OpeningPreset.Grille).copy(sillMaterial = StairMaterial.Marble),
                // Con il vetro satinato, come in bagno.
                o(2, OpeningKind.Window1, 0, 220.0, OpeningPreset.TopLight).copy(sillMaterial = StairMaterial.Stone, frosted = true),
                o(3, OpeningKind.Window2, 0, 370.0, OpeningPreset.WoodWindow).copy(sillMaterial = StairMaterial.Wood, splay = 15.0),
                o(4, OpeningKind.Window1, 0, 520.0, OpeningPreset.RomanShutter).copy(sillMaterial = StairMaterial.Marble),
                o(5, OpeningKind.Window2, 2, 300.0, OpeningPreset.BayWindow),
                // Porta d'ingresso sul muro a sinistra, con la soglia di marmo.
                o(6, OpeningKind.Door, 3, 225.0, OpeningPreset.Armored).copy(sillMaterial = StairMaterial.Marble),
                // Persiane scorrevoli marroni sul muro a destra (un'anta).
                o(7, OpeningKind.Window1, 1, 225.0, OpeningPreset.None).copy(shading = Shading.SlidingShutters, shadingColor = 0xFF6B4A33, sillMaterial = StairMaterial.Stone),
            ),
        )
        // Con il catalogo (se c'è) il prato e i materiali sono quelli fotografici, come nell'app.
        val assets = File("../catalogo/src/main/assets")
        fun read(path: String): ByteArray? = File(assets, path).takeIf { it.isFile }?.readBytes()
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        // Garage accanto, a destra: basculante e sezionale sul muro davanti.
        val garage = Room(2, "Garage", RoomType.Altro, RoomFactory.rectangle(600.0, 450.0).map { it + com.sagoma.planimetria.model.Vec2(800.0, 0.0) }).copy(
            openings = listOf(
                o(1, OpeningKind.Door, 0, 150.0, OpeningPreset.GarageUpAndOver),
                o(2, OpeningKind.Door, 0, 450.0, OpeningPreset.GarageSectional),
            ),
        )
        // Soggiorno dietro: zanzariere e tende da sole (a righe sul balcone, rossa sulla finestra).
        val living = Room(3, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0).map { it + com.sagoma.planimetria.model.Vec2(0.0, 700.0) }).copy(
            openings = listOf(
                o(1, OpeningKind.Window2, 2, 150.0, OpeningPreset.None).copy(insectScreen = true, awning = true, awningColor = 0xFF9C2F2F, sillMaterial = StairMaterial.Marble),
                o(2, OpeningKind.Balcony2, 2, 420.0, OpeningPreset.None).copy(insectScreen = true, awning = true, sillMaterial = StairMaterial.Marble),
                // Vetromattone sul muro davanti, con il davanzale di marmo.
                o(3, OpeningKind.Window1, 0, 150.0, OpeningPreset.GlassBlock).copy(sillMaterial = StairMaterial.Marble),
                // Balcone con le persiane scorrevoli (antracite) sul muro a destra.
                o(4, OpeningKind.Balcony2, 1, 225.0, OpeningPreset.None).copy(shading = Shading.SlidingShutters, sillMaterial = StairMaterial.Marble),
                // Porta tutto vetro sul muro a sinistra.
                o(5, OpeningKind.Door, 3, 225.0, OpeningPreset.AllGlass),
            ),
        )
        // Studio dietro il garage: persiane vicino agli angoli (piegate in due e aperte a 90°), due finestre vicine che si dividono lo
        // spazio in mezzo, e scuri interni contro il muro di fianco.
        val studio = Room(4, "Studio", RoomType.Altro, RoomFactory.rectangle(500.0, 300.0).map { it + com.sagoma.planimetria.model.Vec2(800.0, 700.0) }).copy(
            openings = listOf(
                o(1, OpeningKind.Window2, 2, 90.0, OpeningPreset.None).copy(shading = Shading.Shutters),
                o(2, OpeningKind.Window2, 2, 290.0, OpeningPreset.None).copy(shading = Shading.Shutters),
                o(3, OpeningKind.Window1, 2, 460.0, OpeningPreset.None).copy(shading = Shading.Shutters, hingeLeft = false),
                o(4, OpeningKind.Window2, 1, 90.0, OpeningPreset.None).copy(shading = Shading.InteriorShutters, color = com.sagoma.planimetria.model.Finish.Walnut),
            ),
        )
        // Ingresso a destra del garage: porte con la finitura in foto (noce dritto e girato, listoni, assi) e il garage in legno.
        val hall = Room(5, "Ingresso", RoomType.Altro, RoomFactory.rectangle(700.0, 400.0).map { it + com.sagoma.planimetria.model.Vec2(1600.0, 0.0) }).copy(
            openings = listOf(
                o(1, OpeningKind.Door, 0, 100.0, OpeningPreset.None).copy(doorMaterial = "woodfloor064"),
                o(2, OpeningKind.Door, 0, 230.0, OpeningPreset.None).copy(doorMaterial = "woodfloor064", doorMaterialLayout = com.sagoma.planimetria.model.MaterialLayout(rotation = 90.0)),
                o(3, OpeningKind.Door, 0, 360.0, OpeningPreset.None).copy(doorModel = com.sagoma.planimetria.model.DoorModel.Panels, doorMaterial = "woodfloor046", doorMaterialLayout = com.sagoma.planimetria.model.MaterialLayout(rotation = 90.0, scale = 0.6)),
                o(4, OpeningKind.Door, 0, 560.0, OpeningPreset.GarageUpAndOver).copy(doorMaterial = "ph_wood_planks", doorMaterialLayout = com.sagoma.planimetria.model.MaterialLayout(rotation = 90.0)),
                o(5, OpeningKind.Door, 3, 200.0, OpeningPreset.Armored).copy(doorMaterial = "planks023a", doorMaterialLayout = com.sagoma.planimetria.model.MaterialLayout(rotation = 90.0)),
            ),
        )
        val scene = Scene3D.build(FloorPlan(listOf(room, garage, living, studio, hall)), null, null, ceilings = false)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        fun shot(name: String, eye: Vec3, at: Vec3, fov: Double, walking: Boolean) {
            r.setCamera(eye, at, Vec3.Up, fov, 5.0, walking = walking)
            File(out, "$name.png").writeBytes(SkiaImages.encodePng(r.renderNow(1280, 720)!!)!!)
        }
        shot("1-fuori-davanti", Vec3(300.0, 170.0, -520.0), Vec3(300.0, 140.0, 0.0), 55.0, walking = false)
        shot("2-fuori-romana-legno", Vec3(560.0, 150.0, -230.0), Vec3(440.0, 150.0, 0.0), 55.0, walking = false)
        shot("3-fuori-bovindo", Vec3(520.0, 200.0, 820.0), Vec3(300.0, 120.0, 450.0), 55.0, walking = false)
        shot("4-dentro-davanti", Vec3(300.0, 160.0, 380.0), Vec3(300.0, 150.0, 0.0), 80.0, walking = true)
        shot("5-dentro-bovindo", Vec3(300.0, 150.0, 120.0), Vec3(300.0, 120.0, 450.0), 75.0, walking = true)
        shot("6-dentro-sopraluce-legno", Vec3(300.0, 160.0, 140.0), Vec3(300.0, 170.0, 0.0), 75.0, walking = true)
        shot("7-garage-fuori", Vec3(1100.0, 180.0, -560.0), Vec3(1100.0, 120.0, 0.0), 55.0, walking = false)
        shot("8-garage-dentro", Vec3(1100.0, 170.0, 430.0), Vec3(1100.0, 150.0, 0.0), 80.0, walking = true)
        shot("9-tende-fuori", Vec3(120.0, 260.0, 1750.0), Vec3(300.0, 150.0, 1150.0), 55.0, walking = false)
        shot("10-zanzariera-dentro", Vec3(300.0, 160.0, 800.0), Vec3(300.0, 140.0, 1150.0), 80.0, walking = true)
        // Davanzali: marmo, pietra e legno (con lo sguincio) visti da fuori da vicino, e da dentro.
        shot("11-davanzali-fuori", Vec3(260.0, 150.0, -170.0), Vec3(220.0, 95.0, 0.0), 60.0, walking = false)
        shot("13-soglia-ingresso", Vec3(-230.0, 140.0, 140.0), Vec3(0.0, 40.0, 225.0), 60.0, walking = false)
        shot("12-davanzale-dentro", Vec3(370.0, 165.0, 110.0), Vec3(370.0, 90.0, 0.0), 75.0, walking = true)
        shot("14-vetromattone-fuori", Vec3(60.0, 165.0, 540.0), Vec3(150.0, 150.0, 700.0), 60.0, walking = false)
        shot("15-vetromattone-dentro", Vec3(230.0, 160.0, 960.0), Vec3(150.0, 155.0, 700.0), 70.0, walking = true)
        shot("16-scorrevoli-balcone", Vec3(1000.0, 180.0, 1150.0), Vec3(600.0, 120.0, 925.0), 55.0, walking = false)
        shot("17-scorrevoli-finestra", Vec3(780.0, 160.0, 380.0), Vec3(600.0, 140.0, 225.0), 70.0, walking = false)
        shot("18-porta-tutto-vetro", Vec3(200.0, 150.0, 1110.0), Vec3(20.0, 110.0, 900.0), 70.0, walking = true)
        shot("19-persiane-angolo-fuori", Vec3(1150.0, 190.0, 1560.0), Vec3(1050.0, 140.0, 1000.0), 55.0, walking = false)
        shot("20-scuri-angolo-dentro", Vec3(1000.0, 160.0, 760.0), Vec3(1300.0, 150.0, 930.0), 75.0, walking = true)
        shot("21-porte-legno-fuori", Vec3(1900.0, 160.0, -420.0), Vec3(1900.0, 120.0, 0.0), 60.0, walking = false)
        shot("22-porte-legno-dentro", Vec3(1880.0, 160.0, 330.0), Vec3(1880.0, 120.0, 0.0), 80.0, walking = true)
        shot("23-porta-legno-vicino", Vec3(1830.0, 150.0, -150.0), Vec3(1830.0, 110.0, 0.0), 60.0, walking = false)
    }
}
