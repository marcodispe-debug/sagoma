package com.sagoma.planimetria.scan.reconstruction

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Matematica del motore di ricostruzione: momenti pesati di punti 3D, autovalori di matrici simmetriche 3×3 (Jacobi) e fit di
 * piani con i minimi quadrati totali. Tutto in metri, nel mondo ARCore (y verso l'alto). Deterministico.
 */

/**
 * Momenti pesati di un insieme di punti: peso totale, somme e somme dei prodotti. Bastano per centroide, covarianza e piano
 * senza tenere i punti. I valori sono quelli ORIGINALI dei punti (non quantizzati): il voxel serve solo a raggrupparli.
 * Le somme dei prodotti sono relative a un punto di riferimento [ox], [oy], [oz] per non perdere precisione lontano dall'origine.
 */
class Moments(val ox: Double = 0.0, val oy: Double = 0.0, val oz: Double = 0.0) {
    var n = 0
    var w = 0.0
    var sx = 0.0; var sy = 0.0; var sz = 0.0
    var sxx = 0.0; var sxy = 0.0; var sxz = 0.0; var syy = 0.0; var syz = 0.0; var szz = 0.0

    fun add(x: Double, y: Double, z: Double, weight: Double) {
        val dx = x - ox; val dy = y - oy; val dz = z - oz
        n++
        w += weight
        sx += weight * dx; sy += weight * dy; sz += weight * dz
        sxx += weight * dx * dx; sxy += weight * dx * dy; sxz += weight * dx * dz
        syy += weight * dy * dy; syz += weight * dy * dz; szz += weight * dz * dz
    }

    /** Somma un altro insieme (con lo stesso riferimento). */
    fun addAll(o: Moments) {
        require(o.ox == ox && o.oy == oy && o.oz == oz) { "Momenti con riferimenti diversi" }
        n += o.n; w += o.w
        sx += o.sx; sy += o.sy; sz += o.sz
        sxx += o.sxx; sxy += o.sxy; sxz += o.sxz; syy += o.syy; syz += o.syz; szz += o.szz
    }

    fun centroid(): DoubleArray = doubleArrayOf(ox + sx / w, oy + sy / w, oz + sz / w)

    /** Covarianza pesata [xx, xy, xz, yy, yz, zz]. */
    fun covariance(): DoubleArray {
        val mx = sx / w; val my = sy / w; val mz = sz / w
        return doubleArrayOf(
            sxx / w - mx * mx, sxy / w - mx * my, sxz / w - mx * mz,
            syy / w - my * my, syz / w - my * mz, szz / w - mz * mz,
        )
    }

    /** Piano dei minimi quadrati totali (normale = autovettore del più piccolo autovalore). Null con meno di 3 punti. */
    fun plane(): PlaneFit? {
        if (n < 3 || w <= 0.0) return null
        val c = centroid()
        val e = Eigen3.symmetric(covariance())
        val nx = e.vectors[0]; val ny = e.vectors[1]; val nz = e.vectors[2]
        val sum = e.values[0] + e.values[1] + e.values[2]
        return PlaneFit(nx, ny, nz, nx * c[0] + ny * c[1] + nz * c[2], c, e.values, if (sum > 0) e.values[0] / sum else 0.0, n, w)
    }
}

/**
 * Piano n·p = d (n unitario) con il centroide dei punti, gli autovalori della covarianza (crescenti), la variazione di superficie
 * λ0/(λ0+λ1+λ2) (0 = perfettamente piano, 1/3 = nessuna struttura) e quanti punti e che peso lo sostengono.
 */
data class PlaneFit(
    val nx: Double, val ny: Double, val nz: Double, val d: Double,
    val centroid: DoubleArray,
    val eigenvalues: DoubleArray,
    val surfaceVariation: Double,
    val count: Int,
    val weight: Double,
) {
    fun distance(x: Double, y: Double, z: Double) = nx * x + ny * y + nz * z - d

    /** Scarto quadratico medio pesato dei punti dal piano: √λ0. */
    val rms: Double get() = sqrt(max(0.0, eigenvalues[0]))

    /** Lo stesso piano con la normale girata. */
    fun flipped() = copy(nx = -nx, ny = -ny, nz = -nz, d = -d)

    /** Angolo (gradi, 0..90) tra le normali, senza verso. */
    fun angleTo(o: PlaneFit): Double = Geo.angleDeg(abs(nx * o.nx + ny * o.ny + nz * o.nz))

    override fun equals(other: Any?) = other is PlaneFit && nx == other.nx && ny == other.ny && nz == other.nz && d == other.d && count == other.count
    override fun hashCode() = (nx * 1e6).toInt() * 31 + count
}

/** Autovalori e autovettori di una matrice simmetrica 3×3 (metodo di Jacobi): valori crescenti, vettori nello stesso ordine. */
class Eigen3(val values: DoubleArray, private val v: Array<DoubleArray>) {
    /** Primo autovettore (quello del più piccolo autovalore), come [x, y, z]; [vector] per gli altri. */
    val vectors: DoubleArray get() = vector(0)
    fun vector(i: Int): DoubleArray = doubleArrayOf(v[0][i], v[1][i], v[2][i])

    companion object {
        /** [m] = [xx, xy, xz, yy, yz, zz]. */
        fun symmetric(m: DoubleArray): Eigen3 {
            val a = arrayOf(doubleArrayOf(m[0], m[1], m[2]), doubleArrayOf(m[1], m[3], m[4]), doubleArrayOf(m[2], m[4], m[5]))
            val v = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))
            for (sweep in 0 until 50) {
                val off = abs(a[0][1]) + abs(a[0][2]) + abs(a[1][2])
                if (off < 1e-15) break
                for (p in 0 until 2) for (q in p + 1 until 3) {
                    if (abs(a[p][q]) < 1e-300) continue
                    val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                    val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                    val c = 1 / sqrt(t * t + 1)
                    val s = t * c
                    for (k in 0 until 3) {
                        val akp = a[k][p]; val akq = a[k][q]
                        a[k][p] = c * akp - s * akq
                        a[k][q] = s * akp + c * akq
                    }
                    for (k in 0 until 3) {
                        val apk = a[p][k]; val aqk = a[q][k]
                        a[p][k] = c * apk - s * aqk
                        a[q][k] = s * apk + c * aqk
                    }
                    for (k in 0 until 3) {
                        val vkp = v[k][p]; val vkq = v[k][q]
                        v[k][p] = c * vkp - s * vkq
                        v[k][q] = s * vkp + c * vkq
                    }
                }
            }
            val order = (0 until 3).sortedWith(compareBy({ a[it][it] }, { it }))
            val values = DoubleArray(3) { a[order[it]][order[it]] }
            val vecs = Array(3) { r -> DoubleArray(3) { c -> v[r][order[c]] } }
            // Verso deterministico: la componente più grande in valore assoluto è positiva.
            for (c in 0 until 3) {
                var big = 0
                for (r in 1 until 3) if (abs(vecs[r][c]) > abs(vecs[big][c]) + 1e-12) big = r
                if (vecs[big][c] < 0) for (r in 0 until 3) vecs[r][c] = -vecs[r][c]
            }
            return Eigen3(values, vecs)
        }
    }
}

object Geo {
    fun angleDeg(cosine: Double): Double = acos(min(1.0, max(-1.0, cosine))) * 180.0 / kotlin.math.PI

    /** Percentile (0..1, rango più vicino) di valori già ordinati. */
    fun percentile(sorted: DoubleArray, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val i = min(sorted.size - 1, max(0, kotlin.math.ceil(p * sorted.size).toInt() - 1))
        return sorted[i]
    }

    fun median(values: DoubleArray): Double = percentile(values.sortedArray(), 0.5)
}

/** Lista di interi crescente senza boxing. */
class IntList(capacity: Int = 16) {
    private var a = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(max(16, a.size * 2))
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]
    fun toArray(): IntArray = a.copyOf(size)
}

/** Lista di float senza boxing. */
class FloatList(capacity: Int = 1024) {
    private var a = FloatArray(capacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == a.size) a = a.copyOf(max(1024, a.size * 2))
        a[size++] = v
    }

    operator fun get(i: Int) = a[i]
    fun toArray(): FloatArray = a.copyOf(size)
}
