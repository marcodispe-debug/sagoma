package com.sagoma.planimetria.assets.remote

/** Un file del catalogo con ciò che serve per scaricarlo e verificarlo: a quale asset appartiene e dove sta il blob. */
class BlobEntry(
    val assetId: String,
    val file: ManifestFile,
    /** Chiave del blob nel deposito (derivata dall'impronta e dalla disposizione del manifest), senza nessun URL. */
    val blobKey: String,
) {
    init {
        // Difesa in profondità: anche con un manifest costruito a mano (senza passare dal parser) la chiave resta un'impronta.
        require(BlobLayout.isValidBlobKey(blobKey) && blobKey.endsWith(file.sha256)) { "chiave del blob non valida" }
    }

    val path: String get() = file.path
    val role: String get() = file.role
    val sha256: String get() = file.sha256
    val size: Long get() = file.size
}

/**
 * Istantanea immutabile di un manifest, con gli indici per rispondere in tempo costante: percorso → file,
 * id → asset, (id, ruolo) → file. Si costruisce una volta e non cambia mai: un manifest nuovo dà un'altra istantanea,
 * quindi chi la tiene in mano (un download in corso) vede sempre dati coerenti.
 */
class BlobResolver(val manifest: Manifest) {
    private val assetsById: Map<String, ManifestAsset>
    private val entriesByPath: Map<String, BlobEntry>
    private val entriesByAssetRole: Map<String, Map<String, BlobEntry>>

    init {
        val a = HashMap<String, ManifestAsset>(manifest.assets.size * 2)
        val p = HashMap<String, BlobEntry>()
        val r = HashMap<String, Map<String, BlobEntry>>(manifest.assets.size * 2)
        for (asset in manifest.assets) {
            a[asset.id] = asset
            val roles = LinkedHashMap<String, BlobEntry>()
            for (f in asset.files) {
                val key = try {
                    manifest.blobLayout.keyOf(f.sha256)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("asset '${asset.id}': impronta del file non valida")
                }
                val e = BlobEntry(asset.id, f, key)
                if (f.role !in roles) roles[f.role] = e
                if (f.path !in p) p[f.path] = e // un percorso ripetuto ha lo stesso contenuto (lo garantisce la validazione)
            }
            r[asset.id] = roles
        }
        assetsById = a
        entriesByPath = p
        entriesByAssetRole = r
    }

    companion object {
        /**
         * Il resolver di "nessun manifest ancora": nessun percorso, nessun asset, nessun errore. Serve a costruire un
         * `RemoteAssetStore` prima che esista un manifest valido (tutto risulta non disponibile) senza nullable da gestire.
         */
        val EMPTY: BlobResolver = BlobResolver(
            Manifest(SUPPORTED_MANIFEST_SCHEMA, "", 0, "empty", null, 0, BlobLayout.Flat, emptyList(), emptyMap(), emptyList(), null),
        )
    }

    val assetCount: Int get() = assetsById.size

    /** Tutti gli asset, nell'ordine del manifest. */
    val assets: List<ManifestAsset> get() = manifest.assets

    fun asset(id: String): ManifestAsset? = assetsById[id] ?: manifest.aliases[id]?.let { assetsById[it] }

    fun entryForPath(path: String): BlobEntry? = entriesByPath[path]

    fun entry(assetId: String, role: String): BlobEntry? =
        entriesByAssetRole[assetsById[assetId]?.id ?: manifest.aliases[assetId] ?: return null]?.get(role)

    fun entries(assetId: String): Collection<BlobEntry> =
        entriesByAssetRole[assetsById[assetId]?.id ?: manifest.aliases[assetId] ?: return emptyList()]?.values ?: emptyList()

    /** L'asset che ha sostituito `id` seguendo `replacedBy` fino in fondo; `id` stesso se non è stato rimpiazzato; `null` se `id` non esiste. */
    fun currentAsset(id: String): ManifestAsset? {
        var cur = asset(id) ?: return null
        var steps = 0
        while (cur.replacedBy != null && steps++ <= assetsById.size) cur = assetsById[cur.replacedBy!!] ?: return cur
        return cur
    }
}
