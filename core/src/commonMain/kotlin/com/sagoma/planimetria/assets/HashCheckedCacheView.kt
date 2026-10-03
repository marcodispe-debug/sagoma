package com.sagoma.planimetria.assets

/**
 * La cache vista come negozio, ma senza fidarsi di una voce la cui impronta non è quella attesa per quel
 * percorso: per quel file la cache "non ce l'ha", e chi sta dietro (la sorgente o il negozio remoto) lo rimette
 * al suo posto.
 *
 * [expectedSha256] dà l'impronta attesa di un percorso, oppure `null`. Che cosa vuol dire `null` lo sceglie
 * [requireExpected]:
 *  - `false` (default, cache degli asset impacchettati): "nessuna aspettativa", la voce in cache vale com'è;
 *  - `true` (cache di blob remoti, dove l'aspettativa viene dal manifest): `null` vuol dire che il percorso non è nel
 *    manifest, quindi NON è disponibile da qui anche se il file c'è ancora in cache (un asset ritirato non diventa
 *    disponibile "per caso"). Una cache remota deve sempre stare dietro a una vista così, mai davanti a un negozio come file nudi.
 */
class HashCheckedCacheView(
    private val cache: AssetCache,
    private val expectedSha256: (String) -> String?,
    private val requireExpected: Boolean = false,
) : AssetStore {
    private fun usable(path: String): Boolean {
        val want = expectedSha256(path) ?: return !requireExpected
        val info = cache.info(path) ?: return true // senza voce non c'è niente da respingere: lo dirà la cache
        return info.matches(want)
    }

    override fun availability(path: String): AssetAvailability =
        if (usable(path)) cache.availability(path) else AssetAvailability.Unavailable

    override fun peek(path: String): ByteArray? = if (usable(path)) cache.peek(path) else null
}
