package com.sagoma.planimetria.assets.remote

/** Versione dello schema del manifest che questa versione dell'app sa leggere. */
const val SUPPORTED_MANIFEST_SCHEMA: Int = 1

/** Ciclo di vita di un asset nel catalogo: un asset non si cancella, si ritira (e magari si rimpiazza). */
enum class AssetStatus { Active, Deprecated, Withdrawn }

/** Chi può scaricarlo: `Public` senza credenziali, `Protected` serve un accesso (per esempio la versione pro). */
enum class AssetAccess { Public, Protected }

/** Come si dispone una cartella di blob: dove sta il contenuto con una certa impronta. */
enum class BlobLayout {
    /** `<sha256>` */
    Flat,

    /** `<aa>/<sha256>`, con `aa` i primi due caratteri dell'impronta. */
    Fanout2;

    /** Chiave del blob con questa impronta (minuscola): indipendente da dove e come lo si serve. */
    fun keyOf(sha256: String): String = when (this) {
        Flat -> sha256
        Fanout2 -> sha256.substring(0, 2) + "/" + sha256
    }
}

/**
 * Un file di un asset. [path] è il percorso logico (quello di [com.sagoma.planimetria.assets.AssetStore]);
 * [sha256] è l'identità del CONTENUTO (esadecimale minuscolo) e non va confusa con l'id dell'asset, che resta
 * stabile quando il contenuto cambia.
 */
data class ManifestFile(
    val role: String,
    val path: String,
    val sha256: String,
    val size: Long,
    val mime: String? = null,
)

/** Un asset: [id] stabile (lo usano i progetti), [kind] il tipo (`furniture`, `environment`…), più i suoi file. */
data class ManifestAsset(
    val id: String,
    val kind: String,
    val status: AssetStatus,
    val replacedBy: String?,
    val tier: String,
    val requires: List<String>,
    val access: AssetAccess,
    val files: List<ManifestFile>,
)

/** Un pezzo del catalogo, se questo è diviso in più file (solo struttura: per ora non si scarica). */
data class ManifestShard(val id: String, val path: String, val sha256: String, val size: Long)

/** Firma del manifest (solo struttura: per ora NON si verifica). */
data class ManifestSignature(val algorithm: String, val keyId: String, val value: String)

/** Un manifest letto e validato. Non cambia più: si sostituisce con un altro. */
data class Manifest(
    val schema: Int,
    val catalog: String,
    val catalogVersion: Long,
    val releaseId: String,
    val publishedAt: String?,
    val minClient: Int,
    val blobLayout: BlobLayout,
    val assets: List<ManifestAsset>,
    val aliases: Map<String, String>,
    val shards: List<ManifestShard>,
    val signature: ManifestSignature?,
)

/** Esito della lettura di un manifest. */
sealed class ManifestParseResult {
    data class Valid(val manifest: Manifest) : ManifestParseResult()

    /** Malformato o incoerente: [issues] elenca i problemi trovati (frasi per i log, senza dati riservati). */
    data class Invalid(val issues: List<String>) : ManifestParseResult()

    /** Ben formato ma non utilizzabile da questa app (schema o layout più nuovi, `minClient` troppo alto, catalogo più vecchio di uno già visto). */
    data class Incompatible(val reason: String) : ManifestParseResult()
}

/** Lo stesso esito come errore remoto, per chi deve propagarlo; `null` se valido. */
fun ManifestParseResult.toRemoteError(): RemoteError? = when (this) {
    is ManifestParseResult.Valid -> null
    is ManifestParseResult.Invalid -> RemoteError.ManifestInvalid(issues)
    is ManifestParseResult.Incompatible -> RemoteError.ManifestIncompatible(reason)
}
