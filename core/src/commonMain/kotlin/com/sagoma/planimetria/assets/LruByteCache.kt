package com.sagoma.planimetria.assets

/**
 * Cache che tiene al massimo `maxBytes` (secondo `sizeOf`) e, quando è piena, butta via per primi i
 * valori usati meno di recente. Serve per le immagini già decodificate (miniature, viste dall'alto): senza
 * limite crescerebbero con ogni arredo sfogliato. Un valore buttato via si rilegge e si decodifica di nuovo.
 *
 * Non è sicura tra thread: va usata da un solo thread (quello dell'interfaccia). Un valore più grande
 * del limite non viene tenuto.
 */
class LruByteCache<V : Any>(private val maxBytes: Long, private val sizeOf: (V) -> Long) {
    // LinkedHashMap mantiene l'ordine di inserimento: il primo è il meno recente.
    private val map = LinkedHashMap<String, V>()
    private var bytes = 0L

    val size: Int get() = map.size
    val totalBytes: Long get() = bytes

    /** Il valore, se c'è; lo segna come usato di recente. */
    operator fun get(key: String): V? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }

    operator fun set(key: String, value: V) {
        map.remove(key)?.let { bytes -= sizeOf(it) }
        val s = sizeOf(value)
        if (s > maxBytes) return
        map[key] = value
        bytes += s
        while (bytes > maxBytes) {
            val oldest = map.keys.first()
            bytes -= sizeOf(map.remove(oldest)!!)
        }
    }

    /** Il valore in cache o quello che dà `load` (che si tiene); `null` da `load` non si tiene. */
    inline fun getOrLoad(key: String, load: () -> V?): V? = get(key) ?: load()?.also { set(key, it) }

    fun clear() {
        map.clear()
        bytes = 0
    }
}
