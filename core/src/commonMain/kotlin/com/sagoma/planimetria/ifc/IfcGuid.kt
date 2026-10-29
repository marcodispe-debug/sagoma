package com.sagoma.planimetria.ifc

/**
 * GlobalId IFC (22 caratteri) ricavato sempre uguale dallo stesso testo: la stessa stanza del progetto esce ogni volta con lo
 * stesso id, così al ritorno dal professionista la si riconosce anche se il programma ha perso le proprietà di sagoma.
 */
object IfcGuid {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$"

    fun of(key: String): String {
        val hi = mix(fnv(key, -0x340d631b7bdddcdbL))
        val lo = mix(fnv(key, 0x6c62272e07bb0142L) xor hi)
        return encode(hi, lo)
    }

    /** 128 bit → 22 caratteri: il primo porta 2 bit, gli altri 6 ciascuno (come fanno tutti i programmi IFC). */
    fun encode(hi: Long, lo: Long): String {
        val sb = StringBuilder()
        sb.append(CHARS[((hi ushr 62) and 3).toInt()])
        // I 126 bit restanti, 6 alla volta, dal più alto.
        for (k in 0 until 21) {
            val bit = 125 - k * 6 // posizione del bit più alto del gruppo, contando da 0 (il più basso di lo)
            var v = 0
            for (b in 0 until 6) {
                val pos = bit - b
                val word = if (pos >= 64) hi else lo
                val shift = if (pos >= 64) pos - 64 else pos
                v = (v shl 1) or ((word ushr shift) and 1L).toInt()
            }
            sb.append(CHARS[v])
        }
        return sb.toString()
    }

    fun isValid(id: String): Boolean = id.length == 22 && id[0] in "0123" && id.all { it in CHARS }

    private fun fnv(s: String, seed: Long): Long {
        var h = seed
        for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
        return h
    }

    private fun mix(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
