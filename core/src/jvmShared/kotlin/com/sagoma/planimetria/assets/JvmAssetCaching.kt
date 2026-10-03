package com.sagoma.planimetria.assets

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Mette una cache persistente davanti a `source`, o restituisce `source` così com'è se la cache non si può o
 * non si deve usare: l'app funziona sempre, con o senza cache. La piattaforma decide dove sta la cache e quanto
 * è grande; qui si fa il resto.
 *
 * - `cacheBaseDir == null` o `sourceStamp == null`: niente cache.
 * - `protectedDirs`: le cartelle sorgente degli asset. La cache non può stare dentro una di esse, né contenerne una:
 *   gli asset sorgente non vengono mai toccati.
 * - `sourceStamp`: identifica la versione della sorgente (su Android l'installazione dell'app, sul computer
 *   l'impronta della cartella). Ogni stamp ha la sua cartella di cache: se la sorgente cambia, la cache vecchia
 *   non si usa e si cancella (altrimenti, con la cache per prima, si servirebbe per sempre il contenuto vecchio).
 * - `execute`: dove girano scritture in cache e pulizia delle cache vecchie; di default un solo thread di sfondo,
 *   così chi legge non aspetta.
 */
fun buildCachedAssetStore(
    source: AssetStore,
    cacheBaseDir: File?,
    sourceStamp: String?,
    maxBytes: Long,
    protectedDirs: List<File> = emptyList(),
    expectedSha256: ((String) -> String?)? = null,
    execute: ((() -> Unit) -> Unit)? = null,
): AssetStore {
    if (cacheBaseDir == null || sourceStamp == null) return source
    if (protectedDirs.any { overlaps(cacheBaseDir, it) }) return source
    val run = execute ?: backgroundExecutor()
    val cache = openVersionedAssetCache(cacheBaseDir, sourceStamp, maxBytes, run) ?: return source
    return cachedAssetStore(source, cache, expectedSha256, run)
}

private const val VERSION_DIR_PREFIX = "v-"
private val versionDirName = Regex("v-[0-9a-f]{16}")

/**
 * Apre la cache di questa versione della sorgente (`baseDir/v-<impronta dello stamp>`) e toglie quelle di altre
 * versioni. `null` se non si riesce ad aprire: chi chiama prosegue senza cache.
 */
fun openVersionedAssetCache(
    baseDir: File,
    sourceStamp: String,
    maxBytes: Long,
    execute: (() -> Unit) -> Unit = { it() },
): AssetCache? {
    val name = VERSION_DIR_PREFIX + sha256Hex(sourceStamp.encodeToByteArray()).take(16)
    val cache = try {
        DiskAssetCache(File(baseDir, name), maxBytes)
    } catch (e: Exception) {
        return null
    }
    try {
        execute { removeOtherVersions(baseDir, keep = name) }
    } catch (e: RuntimeException) {
        // la pulizia si farà alla prossima apertura
    }
    return cache
}

/** Cancella le cartelle di cache di altre versioni: solo quelle che portano il nome e il contenuto di una nostra cache. */
private fun removeOtherVersions(baseDir: File, keep: String) {
    try {
        baseDir.listFiles()?.forEach { d ->
            val ours = d.isDirectory && versionDirName.matches(d.name) && (File(d, "index.json").isFile || File(d, "data").isDirectory)
            if (ours && d.name != keep) d.deleteRecursively()
        }
    } catch (e: Exception) {
        // best effort
    }
}

/** Impronta di una cartella (percorsi, dimensioni e date di tutti i file): cambia se un file cambia, si aggiunge o sparisce. */
fun directoryFingerprint(root: File): String {
    if (!root.isDirectory) return "none"
    val lines = ArrayList<String>()
    root.walkTopDown().filter { it.isFile }.forEach { lines += "${it.relativeTo(root).invariantSeparatorsPath}|${it.length()}|${it.lastModified()}" }
    lines.sort()
    return sha256Hex(lines.joinToString("\n").encodeToByteArray())
}

/** Identifica il programma (il jar o la cartella delle classi) da cui parte `anchor`, per le risorse del classpath. */
fun codeSourceStamp(anchor: Class<*>): String = try {
    val f = anchor.protectionDomain?.codeSource?.location?.let { File(it.toURI()) }
    if (f == null) "code:unknown" else "code:${f.path}|${f.length()}|${f.lastModified()}"
} catch (e: Exception) {
    "code:unknown"
}

/** Una cartella contiene l'altra (o sono la stessa); nel dubbio, sì. */
private fun overlaps(a: File, b: File): Boolean = try {
    val pa = a.canonicalFile.toPath()
    val pb = b.canonicalFile.toPath()
    pa.startsWith(pb) || pb.startsWith(pa)
} catch (e: IOException) {
    true
}

private fun backgroundExecutor(): (() -> Unit) -> Unit {
    val pool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sagoma-asset-cache").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    return { task -> pool.execute(task) }
}
