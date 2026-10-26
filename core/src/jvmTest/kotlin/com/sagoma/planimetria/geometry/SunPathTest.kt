package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.DaylightSaving
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.HouseSite
import com.sagoma.planimetria.model.Places
import com.sagoma.planimetria.persistence.PlanJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Giro del sole: città, giorno, ora legale e nord della casa (la pianta non si gira, si sposta solo il sole). */
class SunPathTest {
    private val rome = HouseSite()

    @Test
    fun romaAMezzogiornoDEstateEDInverno() {
        // Mezzogiorno solare a Roma: verso le 13:10 d'estate (ora legale), verso le 12:10 d'inverno.
        val june = SunPath.at(13.17f, CalendarDay(2026, 6, 21), rome)
        assertEquals(71.5, june.elevation, 0.6)
        assertEquals(180.0, june.azimuth, 3.0)
        val december = SunPath.at(12.0f + 4f / 60f, CalendarDay(2026, 12, 21), rome)
        assertEquals(24.7, december.elevation, 0.6)
        assertEquals(180.0, december.azimuth, 3.0)
    }

    @Test
    fun alMattinoAEstLaSeraAOvestDiNotteSotto() {
        val day = CalendarDay(2026, 3, 21)
        val morning = SunPath.at(7.5f, day, rome)
        val evening = SunPath.at(18.5f, day, rome)
        assertTrue(morning.azimuth in 80.0..120.0, "mattina: ${morning.azimuth}")
        assertTrue(evening.azimuth in 240.0..280.0, "sera: ${evening.azimuth}")
        assertTrue(SunPath.at(1f, day, rome).elevation < -20)
    }

    @Test
    fun ilNordGiraSoloIlSole() {
        val day = CalendarDay(2026, 6, 21)
        val up = SunPath.at(10f, day, rome)
        // Nord a destra della pianta: il sole si sposta di 90° in senso orario, l'altezza resta la stessa.
        val right = SunPath.at(10f, day, HouseSite(northDeg = 90.0))
        assertEquals((up.azimuth + 90.0) % 360.0, right.azimuth, 1e-9)
        assertEquals(up.elevation, right.elevation, 1e-9)
    }

    @Test
    fun cittaDiversa() {
        val sydney = Places.search("Sydney").first()
        assertEquals(DaylightSaving.South, sydney.dst)
        // A Sydney a giugno è inverno e a mezzogiorno il sole sta a nord, basso.
        val noon = SunPath.at(12f, CalendarDay(2026, 6, 21), HouseSite(place = sydney))
        assertEquals(32.7, noon.elevation, 1.5)
        assertTrue(noon.azimuth < 15 || noon.azimuth > 345, "azimut ${noon.azimuth}")
        // Più a nord il sole d'estate è più basso.
        val oslo = SunPath.at(13.32f, CalendarDay(2026, 6, 21), HouseSite(place = Places.search("Oslo").first()))
        assertEquals(53.5, oslo.elevation, 1.0)
    }

    @Test
    fun oraLegale() {
        assertTrue(SunPath.isSummerTime(CalendarDay(2026, 3, 29), DaylightSaving.Europe))
        assertFalse(SunPath.isSummerTime(CalendarDay(2026, 3, 28), DaylightSaving.Europe))
        assertTrue(SunPath.isSummerTime(CalendarDay(2026, 10, 24), DaylightSaving.Europe))
        assertFalse(SunPath.isSummerTime(CalendarDay(2026, 10, 25), DaylightSaving.Europe))
        assertTrue(SunPath.isSummerTime(CalendarDay(2026, 3, 8), DaylightSaving.NorthAmerica))
        assertFalse(SunPath.isSummerTime(CalendarDay(2026, 11, 1), DaylightSaving.NorthAmerica))
        assertTrue(SunPath.isSummerTime(CalendarDay(2026, 1, 10), DaylightSaving.South))
        assertFalse(SunPath.isSummerTime(CalendarDay(2026, 7, 10), DaylightSaving.South))
    }

    @Test
    fun calendario() {
        assertEquals(CalendarDay(1970, 1, 1), CalendarDay.fromEpochDays(0))
        assertEquals(CalendarDay(2026, 10, 25), CalendarDay.fromEpochDays(CalendarDay.daysFromEpoch(2026, 10, 25)))
        assertEquals(6, CalendarDay(2026, 10, 25).dayOfWeek) // domenica
        assertEquals(1, CalendarDay(2026, 1, 1).dayOfYear)
        assertEquals(366, CalendarDay(2024, 12, 31).dayOfYear)
    }

    @Test
    fun cercaCitta() {
        assertEquals("Londra", Places.search("london").first().name)
        assertEquals("L'Aquila", Places.search("aquila").first().name)
        assertEquals("Forlì", Places.search("forli").first().name)
        assertTrue(Places.search("").size > 150)
    }

    @Test
    fun cittaNellaLinguaScelta() {
        val londra = Places.search("londres", "es").first()
        assertEquals("Londra", londra.name) // nel progetto resta il nome italiano
        assertEquals("London", Places.displayName(londra, "en"))
        assertEquals("Londres", Places.displayName(londra, "fr"))
        assertEquals("Londra", Places.displayName(londra, "it"))
        assertEquals("Múnich", Places.displayName(Places.search("monaco di baviera").first(), "es"))
        // Si cerca anche col nome in un'altra lingua, e l'elenco è in ordine alfabetico nella lingua scelta.
        assertEquals("Venezia", Places.search("venise", "en").first().name)
        val names = Places.search("", "en").map { Places.displayName(it, "en") }
        assertTrue(names.indexOf("London") < names.indexOf("Lucca") && names.indexOf("Munich") < names.indexOf("Naples"))
        // Ogni traduzione ha le tre lingue e si riferisce a una città dell'elenco.
        val all = Places.search("").map { it.name }.toSet()
        for ((it, tr) in Places.translated) {
            assertTrue(it in all, it)
            assertEquals(3, tr.split('|').count { n -> n.isNotBlank() }, it)
        }
    }

    @Test
    fun nordECittaSiSalvanoEIFileVecchiRestanoComeprima() {
        val site = HouseSite(northDeg = 37.0, place = Places.search("Torino").first())
        val b = Building.single(FloorPlan()).copy(site = site)
        assertEquals(site, PlanJson.decodeBuilding(PlanJson.encode(b)).site)
        val old = PlanJson.encode(Building.single(FloorPlan()))
        assertFalse(old.contains("site"))
        assertEquals(HouseSite(), PlanJson.decodeBuilding(old).site)
        assertNotNull(PlanJson.decodeBuilding(old).floors.firstOrNull())
    }
}
