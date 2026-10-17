package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.Vec2
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orientamento del fascio di un faretto a parete: matematica pura, senza interfaccia né renderer.
 *
 * Con `n` normale interna della parete (orizzontale, verso la stanza), `t = n.perp()` asse lungo il muro e `up` = Y:
 *  - `yaw` (gradi, [0, 360)): rotazione orizzontale; 0° = lungo `n`, 90° verso `t`;
 *  - `tilt` (gradi, [−90, 90]): inclinazione; 0° = orizzontale, positivo = verso il basso, negativo = verso l'alto.
 * Direzione = `(h.x·cos tilt, −sin tilt, h.y·cos tilt)` con `h = n·cos yaw + t·sin yaw` (x e z sono le coordinate della pianta).
 */
object SpotAim {
    /** Un faretto a parete nuovo: 45° verso il basso, nessuna rotazione laterale. */
    const val DEFAULT_TILT = Fixture.DEFAULT_AIM_TILT

    /** Lunghezza (cm) della freccia del gizmo: distanza della maniglia dal faretto. */
    const val HANDLE_LENGTH = 60.0

    /** Orientamento: rotazione orizzontale e inclinazione. */
    data class Aim(val yaw: Double, val tilt: Double)

    /** Rotazione orizzontale riportata in [0°, 360°). */
    fun normalizeYaw(deg: Double): Double {
        val r = deg % 360.0
        val v = if (r < 0) r + 360.0 else r
        return if (v >= 360.0) 0.0 else v
    }

    /** Inclinazione limitata a [−90°, 90°]. */
    fun clampTilt(deg: Double): Double = deg.coerceIn(-90.0, 90.0)

    /** Direzione del fascio (versore, coordinate della scena) per la parete di normale interna `n`. */
    fun direction(n: Vec2, yawDeg: Double, tiltDeg: Double): Vec3 {
        val yaw = toRadians(normalizeYaw(yawDeg))
        val tilt = toRadians(clampTilt(tiltDeg))
        val t = n.perp()
        val hx = n.x * cos(yaw) + t.x * sin(yaw)
        val hy = n.y * cos(yaw) + t.y * sin(yaw)
        return Vec3(hx * cos(tilt), -sin(tilt), hy * cos(tilt))
    }

    /**
     * Orientamento di una direzione `d` (anche non unitaria) per la parete di normale `n`. Se la direzione è quasi
     * verticale (±90°) la rotazione orizzontale non è definita: resta `previousYaw`.
     */
    fun fromDirection(n: Vec2, d: Vec3, previousYaw: Double = 0.0): Aim {
        val len = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
        if (len < 1e-12) return Aim(normalizeYaw(previousYaw), DEFAULT_TILT)
        val dy = (d.y / len).coerceIn(-1.0, 1.0)
        val tilt = clampTilt(toDegrees(asin(-dy)))
        val hx = d.x / len
        val hz = d.z / len
        if (sqrt(hx * hx + hz * hz) < 1e-6) return Aim(normalizeYaw(previousYaw), tilt)
        val t = n.perp()
        val yaw = toDegrees(atan2(hx * t.x + hz * t.y, hx * n.x + hz * n.y))
        return Aim(normalizeYaw(yaw), tilt)
    }

    /**
     * Direzione dal faretto in `spot` al punto della sfera di raggio `radius` (attorno al faretto) sotto il dito: il raggio della
     * visuale (`rayOrigin`, `rayDir`) interseca la sfera in due punti; si prende quello più vicino alla direzione `previous`
     * (la maniglia resta sotto il dito anche sull'emisfero lontano, senza salti; al bordo della sfera, dove i due punti si
     * toccano, vale la direzione `predicted` che prolunga il moto) o, senza `previous`, quello più vicino all'osservatore. Se il raggio non tocca la sfera, si prende il punto della sfera più vicino al raggio.
     * Null solo se la direzione è indefinita (raggio nullo).
     */
    fun sphereDirection(spot: Vec3, rayOrigin: Vec3, rayDir: Vec3, radius: Double, previous: Vec3? = null, predicted: Vec3? = previous): Vec3? {
        val dir = rayDir.normalized()
        val oc = rayOrigin - spot
        val b = oc dot dir
        val c = (oc dot oc) - radius * radius
        val disc = b * b - c
        val point = if (disc >= 0) {
            val s = sqrt(disc)
            val near = rayOrigin + dir * (-b - s)
            val far = rayOrigin + dir * (-b + s)
            val nearOk = (-b - s) > 0
            val farOk = (-b + s) > 0
            when {
                nearOk && farOk && previous != null -> {
                    val p = (predicted ?: previous).normalized()
                    if (((near - spot).normalized() dot p) >= ((far - spot).normalized() dot p)) near else far
                }
                nearOk -> near
                else -> far
            }
        } else {
            // Punto del raggio più vicino al centro, poi proiettato sulla sfera.
            val closest = rayOrigin + dir * (-b)
            val v = closest - spot
            if (v.length < 1e-9) return null
            spot + v.normalized() * radius
        }
        val d = point - spot
        return if (d.length < 1e-9) null else d.normalized()
    }

    /** Nuovo orientamento dal raggio del dito (maniglia trascinata sulla sfera di raggio `radius` attorno al faretto). */
    fun aimFromRay(
        n: Vec2, spot: Vec3, rayOrigin: Vec3, rayDir: Vec3, previousYaw: Double,
        previousTilt: Double = DEFAULT_TILT,
        predicted: Vec3? = null, radius: Double = HANDLE_LENGTH,
    ): Aim? = sphereDirection(spot, rayOrigin, rayDir, radius, direction(n, previousYaw, previousTilt), predicted)?.let { fromDirection(n, it, previousYaw) }

    /** True se il raggio della visuale tocca la sfera (altrimenti il dito è fuori dal suo profilo). */
    fun rayHitsSphere(spot: Vec3, rayOrigin: Vec3, rayDir: Vec3, radius: Double = HANDLE_LENGTH): Boolean {
        val dir = rayDir.normalized()
        val oc = rayOrigin - spot
        val b = oc dot dir
        return b * b - ((oc dot oc) - radius * radius) >= 0
    }

    /** Posizione 3D della maniglia: sulla sfera di raggio `length`, lungo la direzione del fascio. */
    fun handle(spot: Vec3, direction: Vec3, length: Double = HANDLE_LENGTH): Vec3 = spot + direction * length

    /** Normale interna della parete su cui sta il faretto. */
    fun normal(room: Room, f: Fixture): Vec2 = Fixtures.wallAnchor(room, f).second

    /** Posizione 3D della luce del faretto: appena davanti alla parete, alla sua quota (come la `SceneLight` della scena). */
    fun position(room: Room, f: Fixture): Vec3 {
        val (p, n) = Fixtures.wallAnchor(room, f)
        val d = Scene3D.WALL_SPOT_DEPTH + 1.0
        return Vec3(p.x + n.x * d, f.elevation, p.y + n.y * d)
    }

    /** Direzione del fascio del faretto (secondo i suoi `aimYaw` e `aimTilt`). */
    fun directionOf(room: Room, f: Fixture): Vec3 = direction(normal(room, f), f.aimYaw, f.aimTilt)

    /** Angolo (gradi) tra due direzioni: serve ai test. */
    fun angleBetween(a: Vec3, b: Vec3): Double = toDegrees(acos(((a dot b) / (a.length * b.length)).coerceIn(-1.0, 1.0)))
}
