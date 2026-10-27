package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Roof
import com.sagoma.planimetria.model.RoofCovering
import com.sagoma.planimetria.model.RoofKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test

/**
 * Tetto inclinato sopra l'ultimo piano, visto da fuori: due falde, una falda, padiglione, con il piano di sotto
 * (build/prova-3d-tetto-*.png, da controllare a occhio).
 */
class RoofRenderTest {
    private val assets = File("../catalogo/src/main/assets")
    private fun read(path: String): ByteArray? = File(assets, path).takeIf { it.isFile }?.readBytes()

    private fun house(): FloorPlan {
        val living = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0)).copy(
            openings = listOf(Opening.default(1, OpeningKind.Window2, 0, 300.0), Opening.default(2, OpeningKind.Door, 2, 150.0)),
        )
        val bed = Room(2, "Camera", RoomType.Camera, RoomFactory.rectangle(400.0, 450.0).map { it + Vec2(600.0, 0.0) }).copy(
            openings = listOf(Opening.default(3, OpeningKind.Window1, 0, 200.0)),
        )
        return FloorPlan(listOf(living, bed))
    }

    private fun shot(name: String, roof: Roof, eye: Vec3, twoFloors: Boolean = false) {
        val plan = house()
        val below = if (twoFloors) listOf(Floor(1, "Piano terra", plan, levelHeight = 300.0)) else emptyList()
        val scene = Scene3D.build(plan, null, null, ceilings = false, levelHeight = 300.0, below = below, roof = roof)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(10f, "auto")
        r.setCamera(eye, Vec3(500.0, if (twoFloors) 100.0 else 200.0, 225.0), Vec3.Up, 50.0, 5.0, walking = false)
        // Senza scheda grafica (server nel cloud) non si disegna niente: la foto si fa solo sul computer.
        val image = r.renderNow(1200, 750)
        if (image != null) File("build/prova-3d-tetto-$name.png").writeBytes(SkiaImages.encodePng(image)!!)
        r.release()
    }

    @Test
    fun dueFalde() = shot("due-falde", Roof(RoofKind.Gable), Vec3(-700.0, 900.0, -900.0))

    @Test
    fun dueFaldeGirato() = shot("due-falde-girato", Roof(RoofKind.Gable, turn = 1, covering = RoofCovering.Slate), Vec3(-700.0, 900.0, -900.0))

    @Test
    fun unaFalda() = shot("una-falda", Roof(RoofKind.Shed, pitchDeg = 15.0, overhang = 40.0, covering = RoofCovering.Metal), Vec3(-700.0, 900.0, -900.0))

    @Test
    fun padiglione() = shot("padiglione", Roof(RoofKind.Hip, pitchDeg = 30.0, overhang = 70.0), Vec3(1700.0, 1000.0, -900.0))

    @Test
    fun dueFaldeDuePiani() = shot("due-piani", Roof(RoofKind.Gable, pitchDeg = 35.0), Vec3(1800.0, 700.0, 1500.0), twoFloors = true)

    @Test
    fun gronda() = shot("gronda-da-sotto", Roof(RoofKind.Gable, overhang = 80.0), Vec3(300.0, 160.0, -500.0))
}
