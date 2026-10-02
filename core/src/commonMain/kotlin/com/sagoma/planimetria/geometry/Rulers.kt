package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object Rulers {

    /** Entro questi gradi da un multiplo di 45° la rotazione si aggancia (§9). */
    const val ANGLE_SNAP_DEG = 4.0

    /** Angolo mostrato all'utente, 0..360, antiorario come si legge a vista (lo schermo ha y verso il basso). */
    fun angleDeg(r: Ruler): Double {
        val d = r.end - r.start
        val a = toDegrees(atan2(-d.y, d.x))
        return (a + 360) % 360
    }

    /** Aggancia l'angolo al multiplo di 45° più vicino se ci si avvicina abbastanza, altrimenti lo lascia libero. */
    fun snapAngle(deg: Double): Double {
        val nearest = (deg / 45).roundToInt() * 45.0
        return (if (abs(deg - nearest) <= ANGLE_SNAP_DEG) nearest else deg).let { (it + 360) % 360 }
    }

    /** Ruota attorno al centro con la direzione indicata (in gradi), mantenendo la lunghezza. */
    fun withAngle(r: Ruler, deg: Double): Ruler {
        val rad = toRadians(deg)
        val half = Vec2(cos(rad), sin(rad)) * (r.length / 2)
        return r.copy(start = r.mid - half, end = r.mid + half)
    }

    /**
     * Rotazione con la maniglia: la maniglia sta sulla perpendicolare `perp()` del metro, quindi la
     * direzione del metro è quella centro→dito meno 90°.
     */
    fun rotateToward(r: Ruler, pointer: Vec2): Ruler {
        val h = pointer - r.mid
        if (h.length < 1e-6) return r
        val deg = toDegrees(atan2(h.y, h.x)) - 90
        return withAngle(r, snapAngle((deg + 360) % 360))
    }

    /** Allunga/accorcia trascinando un'estremità: si muove solo lungo la direzione del metro. */
    fun extend(r: Ruler, endIndex: Int, delta: Vec2): Ruler {
        val u = r.direction
        return if (endIndex == 1) {
            val len = max(Ruler.MIN_LENGTH, r.length + (delta dot u))
            r.copy(end = r.start + u * len)
        } else {
            val len = max(Ruler.MIN_LENGTH, r.length - (delta dot u))
            r.copy(start = r.end - u * len)
        }
    }
}
