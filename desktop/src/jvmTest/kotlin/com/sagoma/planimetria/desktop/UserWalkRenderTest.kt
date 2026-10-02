package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test

/** Solo per diagnosi (SAGOMA_PLAN): camminata al piano terra di un progetto salvato, vicino alla scala e fuori. */
class UserWalkRenderTest {
    @Test
    fun camminata() {
        val path = System.getenv("SAGOMA_PLAN") ?: return
        val assets = File("../app/src/pro/assets")
        fun read(p: String) = File(assets, p).takeIf { it.isFile }?.readBytes()
        read("furniture/catalog.json")?.let { FurnitureCatalog.load(it.decodeToString()) }
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        val b = PlanJson.decodeBuilding(File(path).readText())
        val ground = b.floors[0]
        val scene = Scene3D.build(ground.plan, null, null, ceilings = true, levelHeight = ground.levelHeight, above = b.floors.getOrNull(1))
        val st = ground.plan.stairs.first()
        val l = Stairs.layout(st, ground.levelHeight)
        println("Scala: ${l.bounds} gradini ${l.steps.size}")
        val p0 = l.path.first(); val p1 = l.path[1]
        val dir = (p1 - p0).normalized()
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(scene)
        r.setDaylight(11f, "auto")
        val views = listOf(
            "piede" to (Vec3(p0.x - dir.x * 150, 160.0, p0.y - dir.y * 150) to Vec3(p1.x, 200.0, p1.y)),
            "sopra" to (Vec3(p0.x - dir.x * 120, 160.0, p0.y - dir.y * 120) to Vec3(p0.x + dir.x * 150, 330.0, p0.y + dir.y * 150)),
            "pianerottolo" to (Vec3(p1.x, 160.0 + l.steps.first { it.landing }.top, p1.y) to Vec3(l.path.last().x, 330.0, l.path.last().y)),
            "fuori-balcone" to (Vec3(380.0, 160.0, 1650.0) to Vec3(380.0, 300.0, 1200.0)),
        )
        for ((name, v) in views) {
            r.setCamera(v.first, v.second, Vec3.Up, 80.0, 5.0, walking = true)
            File("build/prova-utente-cammina-$name.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
        }
        r.release()
    }
}
