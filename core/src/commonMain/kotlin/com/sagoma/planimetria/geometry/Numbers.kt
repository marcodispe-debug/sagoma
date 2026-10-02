package com.sagoma.planimetria.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor

// Piccole funzioni di calcolo e formattazione in Kotlin puro (valgono su Android, web e iPhone).

fun toRadians(deg: Double): Double = deg * PI / 180.0
fun toDegrees(rad: Double): Double = rad * 180.0 / PI

/** Arrotondamento con le metà verso l'alto (come `Math.round`). */
fun roundHalfUp(v: Double): Double = floor(v + 0.5)

/** Numero con `decimals` cifre decimali (metà verso l'alto) e il separatore scelto, senza dipendere dalla lingua del sistema. */
fun formatDecimal(v: Double, decimals: Int, separator: Char = ','): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = floor(abs(v) * factor + 0.5).toLong()
    val sign = if (v < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    return "$sign$whole$separator" + (scaled % factor).toString().padStart(decimals, '0')
}

/** Colore come 6 cifre esadecimali maiuscole (RRGGBB). */
fun hex6(argb: Long): String = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** Scrittura di numeri little-endian in un array di byte che cresce da solo (formati binari come il GLB). */
class ByteWriter(capacity: Int = 1024) {
    private var data = ByteArray(capacity)
    var size = 0
        private set

    private fun ensure(n: Int) {
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
    }

    fun byte(b: Int): ByteWriter { ensure(1); data[size++] = b.toByte(); return this }

    fun int(v: Int): ByteWriter {
        ensure(4)
        data[size++] = v.toByte(); data[size++] = (v shr 8).toByte(); data[size++] = (v shr 16).toByte(); data[size++] = (v shr 24).toByte()
        return this
    }

    fun float(v: Float): ByteWriter = int(v.toRawBits())

    fun bytes(b: ByteArray): ByteWriter { ensure(b.size); b.copyInto(data, size); size += b.size; return this }

    fun toByteArray(): ByteArray = data.copyOf(size)
}
