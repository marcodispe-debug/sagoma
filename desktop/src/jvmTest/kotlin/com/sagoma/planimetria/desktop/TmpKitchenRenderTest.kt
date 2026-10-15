package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.KitchenModules.Front
import com.sagoma.planimetria.model.KitchenModules.Kind
import com.sagoma.planimetria.model.KitchenModules.Spec
import com.sagoma.planimetria.model.KitchenModules.Top
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.ParametricFurniture.Kind as PKind
import com.sagoma.planimetria.model.ParametricFurniture.Spec as PSpec
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.ui.SkiaImages
import java.io.File
import kotlin.test.Test

/** TEMPORANEO (non va nel repository): la cucina di prova in 3D, con il renderer del computer. */
class TmpKitchenRenderTest {
    @Test
    fun cucina3d() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        val assets = File("../app/src/pro/assets")
        fun read(p: String) = File(assets, p).takeIf { it.isFile }?.readBytes()
        read("furniture/catalog.json")?.let { FurnitureCatalog.load(it.decodeToString()) }
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        for ((name, front, top) in listOf(Triple("salvia-carrara", Front.SAGE, Top.CARRARA), Triple("noce-marquinia", Front.WALNUT, Top.MARQUINIA), Triple("bianco-legno", Front.WHITE, Top.WOOD))) {
            val room = Room(1, "Cucina", RoomType.Cucina, RoomFactory.rectangle(395.0, 315.0))
            var x = 7.5
            val mods = ArrayList<Furniture>()
            fun add(kind: Kind, w: Double = kind.width) {
                mods += Furniture(mods.size + 1L, KitchenModules.model(Spec(kind, front, top)), Vec2(x + w / 2, 7.5 + kind.depth / 2), 0.0, w, kind.depth, kind.height, kind.elevation)
            }
            add(Kind.TALL_FRIDGE); x += 60
            add(Kind.TALL_OVEN); x += 60
            val basesStart = x
            add(Kind.BASE_DRAWERS); x += 60
            add(Kind.SINK); x += 80
            add(Kind.DISHWASHER); x += 60
            add(Kind.OVEN_HOB); x += 60
            x = basesStart
            add(Kind.WALL_DOORS); x += 60
            add(Kind.WALL_DOORS, 80.0); x += 80
            add(Kind.WALL_DOORS); x += 60
            add(Kind.WALL_HOOD); x += 60
            val plan = FloorPlan(listOf(room), furniture = mods)
            val scene = Scene3D.build(plan, null, null, ceilings = true)
            val r = DesktopGlSceneRenderer(::read)
            r.setScene(scene)
            r.setDaylight(11f, "auto")
            val views = listOf(
                "insieme" to (Vec3(200.0, 165.0, 290.0) to Vec3(200.0, 120.0, 0.0)),
                "lavello" to (Vec3(230.0, 150.0, 150.0) to Vec3(205.0, 80.0, 20.0)),
            )
            for ((v, cam) in views) {
                r.setCamera(cam.first, cam.second, Vec3.Up, 70.0, 5.0, walking = true)
                File("build/prova-cucina-3d-$name-$v.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
            }
            r.release()
        }
    }

    @Test
    fun mobili3d() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        val assets = File("../app/src/pro/assets")
        fun read(p: String) = File(assets, p).takeIf { it.isFile }?.readBytes()
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        for ((name, plan) in stanzeMobili()) {
            val scene = Scene3D.build(plan, null, null, ceilings = true)
            val r = DesktopGlSceneRenderer(::read)
            r.setScene(scene)
            r.setDaylight(11f, "auto")
            val views = listOf(
                "nord" to (Vec3(250.0, 160.0, 335.0) to Vec3(240.0, 70.0, 0.0)),
                "sud" to (Vec3(450.0, 160.0, 150.0) to Vec3(180.0, 90.0, 420.0)),
            )
            for ((v, cam) in views) {
                r.setCamera(cam.first, cam.second, Vec3.Up, 75.0, 5.0, walking = true)
                File("build/prova-mobili-3d-$name-$v.png").writeBytes(SkiaImages.encodePng(r.renderNow(960, 600)!!)!!)
            }
            r.release()
        }
    }

    /** Alcuni modelli riscalati, accanto a una libreria su misura alta 200 cm come riferimento. */
    @Test
    fun riscalati3d() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        val assets = File("../app/src/pro/assets")
        fun read(p: String) = File(assets, p).takeIf { it.isFile }?.readBytes()
        read("furniture/catalog.json")?.let { FurnitureCatalog.load(it.decodeToString()) }
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        var id = 0L
        fun f(model: String, x: Double, y: Double, rot: Double = 0.0): Furniture {
            val i = FurnitureCatalog.item(model)!!
            return Furniture(++id, model, Vec2(x, y), rot, i.width, i.depth, i.height, i.elevation)
        }
        val items = listOf(
            Furniture(++id, ParametricFurniture.model(PSpec(PKind.BOOKCASE, Front.OAK)), Vec2(60.0, 25.0), 0.0, 100.0, 35.0, 200.0),
            f("ph_steel_frame_shelves_01", 180.0, 32.5),
            f("ob_81d13143d53145bd", 360.0, 45.0),
            f("ph_mid_century_lounge_chair", 520.0, 60.0),
            f("ob_9e19bc36beef4093", 200.0, 260.0, 90.0),
            f("ob_d601c2907f114376", 110.0, 230.0, 90.0),
            f("ob_dfb7c3b51e264b50", 110.0, 300.0, 90.0),
            f("ph_round_wooden_table_01", 430.0, 260.0),
            f("ph_painted_wooden_chair_02", 430.0, 340.0, 180.0),
        )
        val plan = FloorPlan(listOf(Room(1, "Prova", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 450.0))), furniture = items)
        val r = DesktopGlSceneRenderer(::read)
        r.setScene(Scene3D.build(plan, null, null, ceilings = true))
        r.setDaylight(11f, "auto")
        r.setCamera(Vec3(300.0, 170.0, 440.0), Vec3(300.0, 70.0, 0.0), Vec3.Up, 80.0, 5.0, walking = true)
        File("build/prova-riscalati-3d.png").writeBytes(SkiaImages.encodePng(r.renderNow(1200, 700)!!)!!)
        r.release()
    }

    /** I modelli nuovi (Poly Haven e Objaverse), in due stanze, con la libreria su misura alta 200 cm come riferimento. */
    @Test
    fun nuovi3d() {
        System.getenv("SAGOMA_KITCHEN_IMAGES") ?: return
        val assets = File("../app/src/pro/assets")
        fun read(p: String) = File(assets, p).takeIf { it.isFile }?.readBytes()
        read("furniture/catalog.json")?.let { FurnitureCatalog.load(it.decodeToString()) }
        read("materials/materials.json")?.let { MaterialCatalog.load(it.decodeToString()) }
        var id = 0L
        fun f(model: String, x: Double, y: Double, rot: Double = 0.0): Furniture {
            val i = FurnitureCatalog.item(model)!!
            return Furniture(++id, model, Vec2(x, y), rot, i.width, i.depth, i.height, i.elevation)
        }
        val ref = Furniture(++id, ParametricFurniture.model(PSpec(PKind.BOOKCASE, Front.OAK)), Vec2(60.0, 25.0), 0.0, 100.0, 35.0, 200.0)
        val stanze = listOf(
            "interno" to listOf(
                ref,
                f("ob_c91f9ac4de274b5a", 230.0, 10.0),
                f("ob_1ab9bf841df04c07", 350.0, 55.0),
                f("ob_410e3e70d7a64cf6", 450.0, 50.0),
                f("ph_electric_stove", 545.0, 40.0),
                f("ob_e5cf10304ffb4c11", 120.0, 230.0, 90.0),
                f("ob_03e4ff99ecc24e5f", 300.0, 250.0),
                f("ph_tool_cart", 470.0, 250.0),
                f("ph_projector_screen", 120.0, 380.0, 180.0),
            ),
            "esterno" to listOf(
                ref,
                f("ob_6a6c0f882e0e444c", 300.0, 110.0),
                f("ob_37c3f6ee451f495b", 140.0, 300.0),
                f("ob_3059668094e847e2", 420.0, 310.0),
                f("ph_stone_fire_pit", 540.0, 120.0),
                f("ph_exterior_aircon_unit", 500.0, 25.0),
            ),
        )
        for ((name, items) in stanze) {
            val plan = FloorPlan(listOf(Room(1, "Prova", RoomType.Soggiorno, RoomFactory.rectangle(620.0, 450.0))), furniture = items)
            val r = DesktopGlSceneRenderer(::read)
            r.setScene(Scene3D.build(plan, null, null, ceilings = false))
            r.setDaylight(11f, "auto")
            r.setCamera(Vec3(310.0, 230.0, 440.0), Vec3(310.0, 60.0, 60.0), Vec3.Up, 80.0, 5.0, walking = true)
            File("build/prova-nuovi-3d-$name.png").writeBytes(SkiaImages.encodePng(r.renderNow(1200, 700)!!)!!)
            r.release()
        }
    }

    /** Camera da letto e soggiorno-studio di prova, con tutti i mobili su misura (muri di 15 cm in mezzeria). */
    private fun stanzeMobili(): List<Pair<String, FloorPlan>> {
        fun p(id: Long, k: PKind, f: Front, x: Double, y: Double, rot: Double = 0.0, w: Double = k.width, h: Double = k.height) =
            Furniture(id, ParametricFurniture.model(PSpec(k, f)), Vec2(x, y), rot, w, k.depth, h, k.elevation)
        val inTop = 7.5
        val inBottom = 420.0 - 7.5
        val letto = listOf(
            p(1, PKind.BED, Front.OAK, 230.0, inTop + 102.5),
            p(2, PKind.NIGHTSTAND, Front.OAK, 120.0, inTop + 20.0),
            p(3, PKind.NIGHTSTAND, Front.OAK, 340.0, inTop + 20.0),
            p(4, PKind.CHEST, Front.NAVY, 435.0, inTop + 25.0),
            p(5, PKind.WARDROBE, Front.WHITE, 95.0, inBottom - 30.0, rot = 180.0),
            p(6, PKind.SLIDING_WARDROBE, Front.SAGE, 330.0, inBottom - 32.5, rot = 180.0, w = 270.0),
        )
        val giorno = listOf(
            p(1, PKind.BOOKCASE, Front.OAK, 65.0, inTop + 17.5),
            p(2, PKind.TV_UNIT, Front.ANTHRACITE, 235.0, inTop + 21.0),
            p(3, PKind.SHELF, Front.WALNUT, 235.0, inTop + 11.0),
            p(4, PKind.SIDEBOARD, Front.WALNUT, 410.0, inTop + 22.5),
            p(5, PKind.TABLE, Front.OAK, 250.0, 230.0),
            p(6, PKind.DESK, Front.WHITE, 90.0, inBottom - 30.0, rot = 180.0),
            p(7, PKind.BOOKCASE, Front.PEARL, 360.0, inBottom - 17.5, rot = 180.0, w = 250.0, h = 240.0),
        )
        return listOf(
            "camera" to FloorPlan(listOf(Room(1, "Camera", RoomType.Camera, RoomFactory.rectangle(500.0, 420.0))), furniture = letto),
            "soggiorno" to FloorPlan(listOf(Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(500.0, 420.0))), furniture = giorno),
        )
    }
}
