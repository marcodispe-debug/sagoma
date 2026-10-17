package com.sagoma.planimetria.assets.remote

/** Versione dello schema del manifest che questa versione dell'app sa leggere. */
const val SUPPORTED_MANIFEST_SCHEMA: Int = 1

/** Ciclo di vita di un asset nel catalogo: un asset non si cancella, si ritira (e magari si rimpiazza). */
enum class AssetStatus { Active, Deprecated, Withdrawn }

/** Chi può scaricarlo: `Public` senza credenziali, `Protected` serve un accesso (per esempio la versione pro). */
enum class AssetAccess { Public, Protected }

/**
 * Come si dispone una cartella di blob: dove sta il contenuto con una certa impronta. È l'UNICA definizione di
 * che cosa sia una chiave di blob valida ([isValidKey], [isValidBlobKey]): il resolver, la richiesta di
 * trasporto e i fetcher usano questa, così una chiave non può diventare un percorso o un indirizzo arbitrario
 * (`..`, `/`, `\`, `?`, `#`, `%2e`, schemi, maiuscole: tutto ciò che non è esadecimale minuscolo è rifiutato).
 */
enum class BlobLayout {
    /** `<sha256>` */
    Flat,

    /** `<aa>/<sha256>`, con `aa` i primi due caratteri dell'impronta. */
    Fanout2;

    /** Chiave del blob con questa impronta; l'impronta deve essere SHA-256 esadecimale minuscolo (altrimenti `IllegalArgumentException`). */
    fun keyOf(sha256: String): String {
        require(isBlobSha256(sha256)) { "impronta del blob non valida" }
        return when (this) {
            Flat -> sha256
            Fanout2 -> sha256.substring(0, 2) + "/" + sha256
        }
    }

    /** `key` è una chiave ben formata di questa disposizione. */
    fun isValidKey(key: String): Boolean = when (this) {
        Flat -> isBlobSha256(key)
        Fanout2 -> key.length == 2 + 1 + SHA256_HEX_LENGTH && key[2] == '/' && isBlobSha256(key.substring(3)) && key.startsWith(key.substring(3, 5))
    }

    companion object {
        private const val SHA256_HEX_LENGTH = 64

        /** SHA-256 in esadecimale minuscolo, 64 caratteri e nient'altro. */
        fun isBlobSha256(s: String): Boolean = s.length == SHA256_HEX_LENGTH && s.all { it in '0'..'9' || it in 'a'..'f' }

        /** `key` è una chiave valida per almeno una disposizione. */
        fun isValidBlobKey(key: String): Boolean = entries.any { it.isValidKey(key) }
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
