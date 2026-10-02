package com.sagoma.planimetria.geometry

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Telecamera della vista 3D, in due modalità:
 * - [Mode.Orbit]: "casa delle bambole" vista dall'esterno, che ruota attorno a un punto della pianta.
 * - [Mode.Walk]: in prima persona, ad altezza occhi, dentro le stanze.
 * Angoli in gradi: `yaw` attorno alla verticale, `pitch` verso l'alto (+) o il basso (−).
 */
class Camera3D {
    enum class Mode { Orbit, Walk }

    var mode = Mode.Orbit

    // Orbita.
    var target = Vec3.Zero
    var distance = 1500.0
    var yaw = 30.0
    var pitch = 55.0

    // Camminata.
    var eye = Vec3(0.0, Scene3D.EYE_HEIGHT, 0.0)
    var walkYaw = 0.0
    var walkPitch = 0.0

    /** Apertura verticale dell'inquadratura: più ampia camminando, per vedere la stanza anche da vicino. */
    val fovY: Double get() = if (mode == Mode.Walk) 80.0 else 60.0

    /**
     * Inquadra tutta la pianta dall'alto, di tre quarti. `aspect` = larghezza / altezza dello schermo:
     * su un telefono in verticale serve più distanza perché la pianta ci stia anche in larghezza.
     */
    fun fit(bounds: Bounds, aspect: Double = 1.0) {
        target = bounds.center.at(0.0)
        yaw = 20.0
        pitch = 58.0
        // Margine abbondante: in prospettiva il lato vicino della pianta appare più grande, e i muri sono alti.
        val size = max(max(bounds.width, bounds.height), 300.0) * 1.7
        val halfFov = toRadians(fovY / 2)
        val byHeight = size / 2 / tan(halfFov)
        val byWidth = size / 2 / (tan(halfFov) * aspect.coerceAtLeast(0.2))
        distance = max(byHeight, byWidth)
    }

    /** Posizione dell'occhio. */
    val position: Vec3
        get() = when (mode) {
            Mode.Orbit -> {
                val p = toRadians(pitch)
                val y = toRadians(yaw)
                target + Vec3(cos(p) * sin(y), sin(p), cos(p) * cos(y)) * distance
            }
            Mode.Walk -> eye
        }

    /** Direzione in cui si guarda (unitaria). */
    val forward: Vec3
        get() = when (mode) {
            Mode.Orbit -> (target - position).normalized()
            Mode.Walk -> {
                val p = toRadians(walkPitch)
                val y = toRadians(walkYaw)
                Vec3(-cos(p) * sin(y), sin(p), -cos(p) * cos(y))
            }
        }

    /** Destra e alto dello schermo, nello spazio. */
    val right: Vec3 get() = (forward cross Vec3.Up).normalized()
    val up: Vec3 get() = (right cross forward).normalized()

    /** Direzione "avanti" di chi cammina, sul pavimento. */
    val walkForward: Vec3 get() = Vec3(-sin(toRadians(walkYaw)), 0.0, -cos(toRadians(walkYaw)))

    fun viewMatrix(): FloatArray = Mat4.lookAt(position, position + forward, up)

    /** Piano di taglio vicino: piccolo quando si cammina, proporzionato alla distanza in orbita. */
    val near: Double get() = if (mode == Mode.Walk) 5.0 else max(5.0, distance / 200)

    /** Raggio (origine, direzione) che passa per il punto (x, y) di uno schermo largo w e alto h (px). */
    fun ray(x: Float, y: Float, w: Float, h: Float): Pair<Vec3, Vec3> {
        val ndcX = 2.0 * x / w - 1
        val ndcY = 1 - 2.0 * y / h
        val t = tan(toRadians(fovY / 2))
        val aspect = w.toDouble() / h
        val dir = (forward + right * (ndcX * t * aspect) + up * (ndcY * t)).normalized()
        return position to dir
    }

    /** Intersezione di un raggio con il piano che passa per `point` con normale `normal` (null se parallelo o dietro). */
    fun rayPlane(origin: Vec3, dir: Vec3, point: Vec3, normal: Vec3): Vec3? {
        val den = dir dot normal
        if (kotlin.math.abs(den) < 1e-6) return null
        val t = ((point - origin) dot normal) / den
        return if (t > 0) origin + dir * t else null
    }
}

/** Matrici 4×4 in colonna (come le vuole OpenGL). */
object Mat4 {
    fun perspective(fovYDeg: Double, aspect: Double, near: Double, far: Double): FloatArray {
        val f = 1.0 / tan(toRadians(fovYDeg) / 2)
        val m = FloatArray(16)
        m[0] = (f / aspect).toFloat()
        m[5] = f.toFloat()
        m[10] = ((far + near) / (near - far)).toFloat()
        m[11] = -1f
        m[14] = (2 * far * near / (near - far)).toFloat()
        return m
    }

    fun lookAt(eye: Vec3, center: Vec3, up: Vec3): FloatArray {
        val f = (center - eye).normalized()
        val s = (f cross up).normalized()
        val u = s cross f
        val m = FloatArray(16)
        m[0] = s.x.toFloat(); m[4] = s.y.toFloat(); m[8] = s.z.toFloat()
        m[1] = u.x.toFloat(); m[5] = u.y.toFloat(); m[9] = u.z.toFloat()
        m[2] = -f.x.toFloat(); m[6] = -f.y.toFloat(); m[10] = -f.z.toFloat()
        m[12] = -(s dot eye).toFloat()
        m[13] = -(u dot eye).toFloat()
        m[14] = (f dot eye).toFloat()
        m[15] = 1f
        return m
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[col * 4 + k]
            r[col * 4 + row] = s
        }
        return r
    }
}
