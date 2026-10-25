package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.DaylightSaving
import com.sagoma.planimetria.model.HouseSite
import com.sagoma.planimetria.model.Place
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Posizione del sole vista dalla casa: [elevation] in gradi sopra l'orizzonte (negativa di notte) e [azimuth]
 * sulla pianta, in gradi in senso orario dall'alto dello schermo (con il nord in alto: 90 = est, 180 = sud).
 */
data class SunAngles(val elevation: Double, val azimuth: Double)

/** Giorno del calendario (anno, mese 1–12, giorno 1–31). */
data class CalendarDay(val year: Int, val month: Int, val day: Int) {
    /** Giorno dell'anno, 1 = 1° gennaio. */
    val dayOfYear: Int get() = (daysFromEpoch(year, month, day) - daysFromEpoch(year, 1, 1) + 1).toInt()

    /** 0 = lunedì … 6 = domenica. */
    val dayOfWeek: Int get() = (((daysFromEpoch(year, month, day) + 3) % 7 + 7) % 7).toInt()

    companion object {
        /** Giorni dal 1° gennaio 1970 (calendario gregoriano). */
        fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
            val y = if (month <= 2) year - 1 else year
            val era = floor(y / 400.0).toLong()
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097 + doe - 719468
        }

        /** Giorno del calendario a partire dai giorni dal 1° gennaio 1970. */
        fun fromEpochDays(days: Long): CalendarDay {
            val z = days + 719468
            val era = floor(z / 146097.0).toLong()
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400).toInt() + if (m <= 2) 1 else 0
            return CalendarDay(y, m, d)
        }

        /** Oggi (secondo l'orologio del dispositivo, in UTC: per il sole basta). */
        @OptIn(ExperimentalTime::class)
        fun today(): CalendarDay = fromEpochDays(Clock.System.now().toEpochMilliseconds().floorDiv(86_400_000L))
    }
}

/**
 * Giro del sole calcolato con le formule astronomiche (quelle del NOAA, precise a pochi decimi di grado): dipende
 * dalla città, dal giorno e dall'ora dell'orologio (con l'ora legale). Senza città vale Roma; la pianta non si gira
 * mai: il nord scelto sposta solo il sole.
 */
object SunPath {
    val Rome = Place("Roma", 41.90, 12.50, 1.0, DaylightSaving.Europe)

    /** Il giro di prima, uguale tutti i giorni: est alle 6, sud a mezzogiorno, ovest alle 18, al massimo a 55°. */
    fun simple(hour: Float): SunAngles =
        SunAngles(55.0 * sin(PI * (hour - 6.0) / 12.0), 90.0 + (hour - 6.0) * 15.0)

    /** Sole all'ora dell'orologio [hour] (0–24) del giorno [day], per la casa [site]. */
    fun at(hour: Float, day: CalendarDay, site: HouseSite): SunAngles {
        val place = site.place ?: Rome
        val zone = place.utcOffset + if (isSummerTime(day, place.dst)) 1.0 else 0.0
        val utcHour = hour - zone
        val g = 2 * PI / 365.0 * (day.dayOfYear - 1 + (utcHour - 12) / 24.0)
        // Equazione del tempo (minuti) e declinazione del sole (radianti).
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        // Ora solare vera (minuti) e angolo orario (0 a mezzogiorno solare, negativo al mattino).
        val solarMinutes = hour * 60.0 + eqTime + 4.0 * place.longitude - 60.0 * zone
        val ha = toRadians(solarMinutes / 4.0 - 180.0)
        val lat = toRadians(place.latitude)
        val cosZen = (sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(ha)).coerceIn(-1.0, 1.0)
        val elevation = 90.0 - acos(cosZen) * 180.0 / PI
        // Azimut dal nord in senso orario (90 = est).
        val az = atan2(sin(ha), cos(ha) * sin(lat) - tan(decl) * cos(lat)) * 180.0 / PI + 180.0
        return SunAngles(elevation, normalize(az + site.northDeg))
    }

    /** Ora legale in vigore quel giorno (si cambia alle 2 di notte: per il sole conta solo il giorno). */
    fun isSummerTime(day: CalendarDay, rule: DaylightSaving): Boolean {
        val n = day.dayOfYear
        return when (rule) {
            DaylightSaving.None -> false
            DaylightSaving.Europe -> n >= lastSunday(day.year, 3).dayOfYear && n < lastSunday(day.year, 10).dayOfYear
            DaylightSaving.NorthAmerica -> n >= nthSunday(day.year, 3, 2).dayOfYear && n < nthSunday(day.year, 11, 1).dayOfYear
            DaylightSaving.South -> n >= nthSunday(day.year, 10, 1).dayOfYear || n < nthSunday(day.year, 4, 1).dayOfYear
        }
    }

    private fun nthSunday(year: Int, month: Int, n: Int): CalendarDay {
        val first = CalendarDay(year, month, 1)
        val toSunday = (6 - first.dayOfWeek + 7) % 7
        return CalendarDay(year, month, 1 + toSunday + 7 * (n - 1))
    }

    private fun lastSunday(year: Int, month: Int): CalendarDay {
        val days = if (month == 12) 31 else (CalendarDay.daysFromEpoch(year, month + 1, 1) - CalendarDay.daysFromEpoch(year, month, 1)).toInt()
        val last = CalendarDay(year, month, days)
        return CalendarDay(year, month, days - (last.dayOfWeek + 1) % 7)
    }

    private fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Direzione verso il sole nel 3D (x a destra, y in alto, z verso il basso della pianta), con il sole almeno a [minElevation]°. */
    fun toSun(sun: SunAngles, minElevation: Double = 2.0): Vec3 {
        val az = toRadians(sun.azimuth)
        val el = toRadians(maxOf(sun.elevation, minElevation))
        return Vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el))
    }
}
