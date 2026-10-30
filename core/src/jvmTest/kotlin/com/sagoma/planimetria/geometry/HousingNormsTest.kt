package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.HouseSite
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Places
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Norme della casa per paese: da dove viene il paese e cosa cambia nel controllo della pianta e nel computo. */
class HousingNormsTest {
    private fun rect(id: Long, type: RoomType, w: Double, h: Double, at: Vec2 = Vec2.Zero, openings: List<Opening> = emptyList(), ceiling: Double = 270.0) =
        Room(id, type.name, type, RoomFactory.rectangle(w, h).map { it + at }, openings = openings, ceilingHeight = ceiling)

    private fun city(name: String) = (Places.italy + Places.world).first { it.place.name == name }.place

    /** La casa nella città `name` (null: senza città). */
    private fun Building.inCity(name: String?) = copy(site = HouseSite(place = name?.let(::city)))

    /** I problemi delle norme (tipo) della casa nella città `name`. */
    private fun rules(b: Building, name: String?) = PlanCheck.of(b.inCity(name)).filter { it.kind.rule }.map { it.kind }.toSet()

    @Test
    fun `il paese dalla città o dalla lingua`() {
        val b = Building.single(FloorPlan(emptyList()))
        for ((name, code) in listOf("Berlino" to "DE", "Monaco di Baviera" to "DE", "Parigi" to "FR", "Madrid" to "ES", "Londra" to "GB", "New York" to "US", "Roma" to "IT")) {
            assertEquals(code, HousingNorms.of(b.inCity(name), "it").country, name)
        }
        // San Marino, il Vaticano e la Scozia hanno regole loro: consigli generali.
        for (name in listOf("San Marino", "Città del Vaticano", "Edimburgo", "Tokyo")) assertEquals(HousingNorms.General, HousingNorms.of(b.inCity(name), "it"), name)
        // Senza città, la lingua dell'app; l'inglese è di troppi paesi.
        assertEquals(HousingNorms.Italy, HousingNorms.of(b, "it"))
        assertEquals(HousingNorms.Germany, HousingNorms.of(b, "de"))
        assertEquals(HousingNorms.France, HousingNorms.of(b, "fr"))
        assertEquals(HousingNorms.Spain, HousingNorms.of(b, "es"))
        assertEquals(HousingNorms.General, HousingNorms.of(b, "en"))
        // La città vince sulla lingua.
        assertEquals(HousingNorms.Italy, HousingNorms.of(b.inCity("Milano"), "en"))
        // Ogni città del mondo che si può scegliere ha il suo paese.
        for (e in Places.world) assertTrue(HousingNorms.country(e.place) != null, e.place.name)
        assertNull(HousingNorms.country(null))
    }

    @Test
    fun `tabelle e minimi scritti`() {
        assertEquals("1/8", HousingNorms.Italy.ratioLabel(1.0 / 8))
        assertEquals("1/20", HousingNorms.England.ratioLabel(1.0 / 20))
        assertEquals("1/6", HousingNorms.Spain.ratioLabel(1.0 / 6))
        assertEquals("8 %", HousingNorms.UnitedStates.ratioLabel(0.08))
        assertEquals("4 %", HousingNorms.UnitedStates.ratioLabel(0.04))
        // Le stesse tabelle si potranno scaricare: tornano uguali dal JSON.
        val json = Json { encodeDefaults = false }
        for (n in HousingNorms.countries.values + HousingNorms.General) {
            assertEquals(n, json.decodeFromString(HousingNorms.serializer(), json.encodeToString(HousingNorms.serializer(), n)), n.country)
        }
        // L'Italia di sempre: i numeri del DM 5 luglio 1975.
        assertEquals(HousingRules.MIN_RATIO, HousingNorms.Italy.windowRatio)
        assertEquals(HousingRules.MIN_HEIGHT_LIVING_CM, HousingNorms.Italy.livingHeightCm)
    }

    @Test
    fun `altezze e camere secondo il paese`() {
        // Camera di 8,12 m² con il soffitto a 2,50 m e una finestra da 1,44 m² (più di 1/6 del pavimento).
        val bedroom = rect(1, RoomType.Camera, 300.0, 300.0, openings = listOf(Opening.default(1, OpeningKind.Window2, 2, 150.0)), ceiling = 250.0)
        val b = Building.single(FloorPlan(listOf(bedroom)))
        assertEquals(setOf(PlanCheck.Kind.LowCeiling, PlanCheck.Kind.SmallBedroom), rules(b, "Roma"))
        for (name in listOf("Berlino", "Parigi", "Madrid", "Londra", "New York", "Tokyo")) assertEquals(emptySet(), rules(b, name), name)
        // Più bassa di 2,40 m: anche in Germania e nei consigli generali, con il minimo del paese nel testo.
        val low = Building.single(FloorPlan(listOf(bedroom.copy(ceilingHeight = 235.0))))
        assertEquals(setOf(PlanCheck.Kind.LowCeiling), rules(low, "Berlino"))
        val issue = PlanCheck.of(low.inCity("Berlino")).first { it.kind == PlanCheck.Kind.LowCeiling }
        assertEquals("«Camera»: soffitto a 2,35 m; nelle stanze da abitare deve essere almeno a 2,40 m.", PlanCheck.describe(issue, low.inCity("Berlino")))
        assertEquals(setOf(PlanCheck.Kind.LowCeiling), rules(low, "Tokyo"))
        assertEquals(emptySet(), rules(low, "Londra"))
        // Camera di 6,72 m² (6,7 al centesimo): piccola in Francia (7 m²), non in Spagna (6 m²).
        val small = Building.single(FloorPlan(listOf(rect(1, RoomType.Camera, 274.0, 260.0, openings = listOf(Opening.default(1, OpeningKind.Window2, 2, 130.0)), ceiling = 270.0))))
        assertEquals(setOf(PlanCheck.Kind.SmallBedroom), rules(small, "Parigi"))
        assertEquals(emptySet(), rules(small, "Madrid"))
        val text = PlanCheck.describe(PlanCheck.of(small.inCity("Parigi")).first { it.kind == PlanCheck.Kind.SmallBedroom }, small.inCity("Parigi"))
        assertTrue(text.endsWith("una camera da letto deve averne almeno 7."), text)
    }

    @Test
    fun `finestre fisse dove contano`() {
        // Soggiorno di 14,82 m² con una vetrata fissa da 2,40 m²: luce sì, aria no.
        val fixed = Opening.default(1, OpeningKind.Window2, 2, 200.0).copy(width = 200.0, sash = SashType.Fixed)
        val b = Building.single(FloorPlan(listOf(rect(1, RoomType.Soggiorno, 400.0, 400.0, openings = listOf(fixed)))))
        // Italia, Francia, Spagna, Inghilterra: contano solo le finestre che si aprono.
        for (name in listOf("Roma", "Parigi", "Madrid", "Londra")) assertEquals(setOf(PlanCheck.Kind.NoWindows), rules(b, name), name)
        // Germania e consigli generali: conta la luce anche dalle fisse.
        assertEquals(emptySet(), rules(b, "Berlino"))
        assertEquals(emptySet(), rules(b, "Tokyo"))
        // Stati Uniti: la luce basta (8 %), la parte che si apre no (4 %).
        val us = b.inCity("New York")
        val air = PlanCheck.of(us).single { it.kind.rule }
        assertEquals(PlanCheck.Kind.FewWindows, air.kind)
        assertEquals(1, air.count)
        assertEquals(
            "«Soggiorno»: la parte delle finestre che si apre è di 0,00 m² per 14,82 m² di pavimento. Per l'aria ne serve almeno 4 %, cioè 0,59 m².",
            PlanCheck.describe(air, us),
        )
        // Il computo: in Germania la vetrata basta, negli Stati Uniti no; le intestazioni dicono quali finestre contano.
        val de = Takeoff.of(b.inCity("Berlino"))
        assertTrue(de.light.single().enough)
        assertTrue(Takeoff.csv(de).contains("Piano;Stanza;Pavimento m²;Finestre m²;Rapporto;Almeno 1/8\n"), Takeoff.csv(de))
        assertFalse(Takeoff.of(us).light.single().enough)
        assertTrue(Takeoff.csv(Takeoff.of(us)).contains(";Almeno 8 %\n"))
        // «Correggi» mette una finestra che si apre, abbastanza per l'aria.
        val fixedUs = PlanCheck.fixed(us, air)
        assertTrue(fixedUs != null && PlanCheck.of(fixedUs).none { it.kind.rule })
        // In Francia basta una finestra apribile, di qualsiasi misura.
        val fr = b.inCity("Parigi")
        val none = PlanCheck.of(fr).single { it.kind.rule }
        assertEquals("«Soggiorno» non ha finestre apribili che danno fuori: una stanza da abitare ha bisogno di luce e aria dirette.", PlanCheck.describe(none, fr))
        val small = Building.single(FloorPlan(listOf(rect(1, RoomType.Soggiorno, 400.0, 400.0, openings = listOf(Opening.default(1, OpeningKind.Window1, 2, 200.0).copy(width = 40.0, height = 40.0)))))).inCity("Parigi")
        assertEquals(emptySet(), PlanCheck.of(small).filter { it.kind.rule }.map { it.kind }.toSet())
    }

    @Test
    fun `bagno e gruppi secondo il paese`() {
        val saved = FurnitureCatalog.items
        try {
            fun item(model: String, label: String) = FurnitureCatalog.Item(model = model, label = label, category = "Bagno", width = 40.0, depth = 40.0, height = 40.0)
            FurnitureCatalog.set(listOf(item("wc", "WC sospeso"), item("doccia", "Porta doccia")))
            val living = rect(1, RoomType.Soggiorno, 500.0, 400.0, openings = listOf(Opening.default(1, OpeningKind.Window2, 2, 150.0), Opening.default(2, OpeningKind.Window2, 2, 350.0)))
            val bath = rect(2, RoomType.Bagno, 250.0, 250.0, Vec2(500.0, 0.0), openings = listOf(Opening.default(3, OpeningKind.Window1, 1, 120.0)))
            fun f(id: Long, model: String, x: Double) = Furniture(id, model, Vec2(x, 100.0), width = 40.0, depth = 40.0, height = 40.0)
            val b = Building.single(FloorPlan(listOf(living, bath), furniture = listOf(f(1, "wc", 560.0), f(2, "doccia", 680.0))))
            // Italia: vaso, bidè, vasca o doccia e lavabo. Germania: vaso e vasca o doccia. Spagna: il vaso.
            assertTrue(PlanCheck.Kind.BathIncomplete in rules(b, "Roma"))
            assertFalse(PlanCheck.Kind.BathIncomplete in rules(b, "Berlino"))
            assertFalse(PlanCheck.Kind.BathIncomplete in rules(b, "Madrid"))
            val us = b.inCity("New York")
            val issue = PlanCheck.of(us).first { it.kind == PlanCheck.Kind.BathIncomplete }
            assertEquals("«Bagno»: senza lavabo. Almeno un bagno della casa deve avere vaso, vasca o doccia e lavabo.", PlanCheck.describe(issue, us))
            // Il gruppo e la spiegazione dicono di che paese sono le norme, o che sono consigli.
            val report = PlanCheck.report(us)
            assertTrue(report.contains("\nNorme per la casa (Stati Uniti)\nMinimi di International Residential Code per "), report)
            assertTrue(report.contains(T.controllo.dovePaese), report)
            assertEquals("Consigli per la casa", T.controllo.daNorme(HousingNorms.General))
        } finally {
            FurnitureCatalog.set(saved)
        }
    }
}
