package com.sagoma.planimetria.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * [AssetCache] su una cartella del disco (Android e computer). Disposizione:
 *
 * ```
 * root/index.json     elenco dei file (dal meno al più recente), le loro impronte e i file fissati
 * root/data/<percorso>   i file, con lo stesso percorso logico degli asset (furniture/ph_sofa_01.glb…)
 * root/tmp/           file in scrittura: si svuota a ogni apertura
 * ```
 *
 * Come resta coerente anche se il programma si ferma a metà:
 *  - un file entra solo dopo essere stato scritto per intero in `tmp/` e verificato, poi con uno spostamento
 *    atomico; l'indice si riscrive allo stesso modo (file temporaneo e spostamento);
 *  - all'apertura si tengono solo i file dell'indice che esistono con la dimensione registrata; quelli che
 *    l'indice non conosce (orfani) e i file in `tmp/` si cancellano; un indice illeggibile, o di una versione
 *    diversa, equivale a una cache vuota (i file si potranno riscaricare: mai fidarsi di ciò che non si può verificare).
 *
 * Leggere un file lo segna come usato di recente; quell'ordine si salva nell'indice con [flush] o alla prima
 * scrittura successiva (un arresto brusco può farlo tornare a quello precedente: non conta per la correttezza).
 *
 * Sicura tra thread: [peek] si può chiamare dal thread di rendering mentre un altro thread fa [put]. Una
 * lettura non blocca le altre (il file si legge fuori dal blocco). Non è pensata per due processi sulla
 * stessa cartella. Su dischi che non distinguono maiuscole e minuscole due percorsi che differiscono solo per
 * quelle sono rifiutati ([AssetPutResult.InvalidPath]).
 */
class DiskAssetCache(
    private val root: File,
    override val maxBytes: Long,
    private val now: () -> Long = { System.currentTimeMillis() },
) : AssetCache {
    private class Entry(val size: Long, val sha256: String, var lastAccess: Long)

    @Serializable
    private class IndexEntry(val path: String, val size: Long, val sha256: String, val lastAccess: Long)

    @Serializable
    private class Index(val version: Int, val entries: List<IndexEntry>, val pins: Map<String, List<String>> = emptyMap())

    private val dataDir = File(root, "data")
    private val tmpDir = File(root, "tmp")
    private val indexFile = File(root, "index.json")

    private val lock = Any()

    // Dal meno al più recente: leggere un file lo sposta in fondo (come LruByteCache).
    private val entries = LinkedHashMap<String, Entry>()
    private val pins = HashMap<String, Set<String>>()
    private var used = 0L

    // Le letture hanno cambiato l'ordine d'uso e l'indice su disco non lo sa ancora.
    private var orderDirty = false

    init {
        require(maxBytes > 0) { "maxBytes deve essere positivo" }
        reconcileWithDisk()
    }

    override val usedBytes: Long get() = synchronized(lock) { used }

    // ---- lettura ----

    override fun availability(path: String): AssetAvailability {
        val f = fileFor(path) ?: return AssetAvailability.Unavailable
        return if (synchronized(lock) { path in entries } && f.isFile) AssetAvailability.Available else AssetAvailability.Unavailable
    }

    override fun peek(path: String): ByteArray? {
        val f = fileFor(path) ?: return null
        val entry = synchronized(lock) {
            val e = entries.remove(path) ?: return null
            e.lastAccess = now()
            entries[path] = e // ora è il più recente
            orderDirty = true
            e
        }
        val bytes = try {
            f.readBytes()
        } catch (e: IOException) {
            null
        }
        if (bytes == null || bytes.size.toLong() != entry.size) {
            // Il file non c'è più o non è completo: non è più affidabile. Se nel frattempo è stato sostituito, non si tocca.
            dropIf(path, entry)
            return null
        }
        return bytes
    }

    override fun list(): List<CachedAssetInfo> = synchronized(lock) { entries.map { (p, e) -> info(p, e) } }

    override fun info(path: String): CachedAssetInfo? = synchronized(lock) { entries[path]?.let { info(path, it) } }

    private fun info(path: String, e: Entry) = CachedAssetInfo(path, e.size, e.sha256, e.lastAccess, isPinned(path))

    // ---- scrittura ----

    override fun put(path: String, bytes: ByteArray, sha256: String?): AssetPutResult {
        if (!isPortableAssetPath(path)) return AssetPutResult.InvalidPath
        if (bytes.size.toLong() > maxBytes) return AssetPutResult.TooLarge
        val actual = sha256Hex(bytes)
        if (sha256 != null && !sha256.equals(actual, ignoreCase = true)) return AssetPutResult.HashMismatch
        if (synchronized(lock) { entries.keys.any { it != path && it.equals(path, ignoreCase = true) } }) return AssetPutResult.InvalidPath

        val tmp = try {
            tmpDir.mkdirs()
            File.createTempFile("asset-", ".part", tmpDir)
        } catch (e: IOException) {
            return AssetPutResult.IoError
        }
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            val target = File(dataDir, path)
            synchronized(lock) {
                target.parentFile?.mkdirs()
                moveReplacing(tmp, target)
                entries.remove(path)?.let { used -= it.size }
                entries[path] = Entry(bytes.size.toLong(), actual, now())
                used += bytes.size
                evict(keep = path)
                persist()
            }
        } catch (e: IOException) {
            tmp.delete() // se lo spostamento è riuscito il file non c'è più e delete non fa niente
            return AssetPutResult.IoError
        }
        return AssetPutResult.Stored
    }

    override fun remove(path: String): Boolean {
        val f = fileFor(path) ?: return false
        synchronized(lock) {
            val e = entries.remove(path) ?: return false
            used -= e.size
            f.delete()
            persist()
            return true
        }
    }

    override fun setPinned(owner: String, paths: Collection<String>) {
        val valid = paths.filter { isPortableAssetPath(it) }.toSet()
        synchronized(lock) {
            if (valid.isEmpty()) pins.remove(owner) else pins[owner] = valid
            persist()
        }
    }

    override fun flush() {
        synchronized(lock) { if (orderDirty) persist() }
    }

    override fun clear() {
        synchronized(lock) {
            entries.clear()
            pins.clear()
            used = 0
            dataDir.deleteRecursively()
            dataDir.mkdirs()
            persist()
        }
    }

    // ---- integrità ----

    override fun verify(path: String): Boolean {
        val f = fileFor(path) ?: return false
        val entry = synchronized(lock) { entries[path] } ?: return false
        val actual = try {
            sha256Hex(f.readBytes())
        } catch (e: IOException) {
            null
        }
        if (actual != entry.sha256) {
            dropIf(path, entry)
            return false
        }
        return true
    }

    override fun verifyAll(): List<String> {
        val paths = synchronized(lock) { entries.keys.toList() }
        return paths.filterNot { verify(it) }
    }

    // ---- interno ----

    private fun fileFor(path: String): File? = if (isPortableAssetPath(path)) File(dataDir, path) else null

    private fun isPinned(path: String) = pins.values.any { path in it }

    /** Toglie `path` solo se in cache c'è ancora proprio quella voce (un `put` concorrente potrebbe averla sostituita). */
    private fun dropIf(path: String, entry: Entry) {
        synchronized(lock) {
            if (entries[path] !== entry) return
            entries.remove(path)
            used -= entry.size
            File(dataDir, path).delete()
            persist()
        }
    }

    /** Butta via i file meno usati, non fissati e diversi da `keep`, finché si rientra nel limite. Con il blocco preso. */
    private fun evict(keep: String?) {
        if (used <= maxBytes) return
        val it = entries.entries.iterator()
        while (used > maxBytes && it.hasNext()) {
            val (p, e) = it.next()
            if (p == keep || isPinned(p)) continue
            File(dataDir, p).delete() // se non si riesce (file aperto) resta un orfano, cancellato alla prossima apertura
            used -= e.size
            it.remove()
        }
    }

    private fun moveReplacing(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: FileAlreadyExistsException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Riscrive l'indice (file temporaneo e spostamento). Con il blocco preso. Se non riesce, all'apertura successiva si riallinea con il disco. */
    private fun persist() {
        orderDirty = false
        try {
            val index = Index(
                VERSION,
                entries.map { (p, e) -> IndexEntry(p, e.size, e.sha256, e.lastAccess) },
                pins.mapValues { (_, v) -> v.sorted() },
            )
            val tmp = File(root, "index.json.tmp")
            tmp.writeText(json.encodeToString(Index.serializer(), index))
            moveReplacing(tmp, indexFile)
        } catch (e: IOException) {
            // L'indice sarà riallineato alla prossima apertura: i file senza voce si cancellano, le voci senza file si tolgono.
        }
    }

    private fun readIndex(): Index? {
        if (!indexFile.isFile) return null
        val index = try {
            json.decodeFromString(Index.serializer(), indexFile.readText())
        } catch (e: Exception) {
            return null
        }
        return index.takeIf { it.version == VERSION }
    }

    private fun reconcileWithDisk() {
        root.mkdirs()
        dataDir.mkdirs()
        tmpDir.mkdirs()
        tmpDir.listFiles()?.forEach { it.deleteRecursively() }
        File(root, "index.json.tmp").delete()

        val index = readIndex()
        var changed = index == null
        if (index != null) {
            for (e in index.entries) {
                val f = fileFor(e.path)
                val valid = f != null && f.isFile && f.length() == e.size && e.size >= 0 && e.sha256.length == 64 && e.path !in entries
                if (valid) {
                    entries[e.path] = Entry(e.size, e.sha256, e.lastAccess)
                    used += e.size
                } else {
                    if (e.path !in entries) f?.delete()
                    changed = true
                }
            }
            for ((owner, paths) in index.pins) {
                val valid = paths.filter { isPortableAssetPath(it) }.toSet()
                if (valid.isNotEmpty()) pins[owner] = valid
            }
        }
        // File che l'indice non conosce: non si sa se sono completi, si cancellano.
        dataDir.walkTopDown().filter { it.isFile }.forEach { f ->
            if (f.relativeTo(dataDir).invariantSeparatorsPath !in entries) {
                f.delete()
                changed = true
            }
        }
        if (used > maxBytes) {
            evict(keep = null)
            changed = true
        }
        if (changed) persist()
    }

    private companion object {
        const val VERSION = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}

private val windowsReservedNames = setOf("CON", "PRN", "AUX", "NUL") + (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

/**
 * Percorso adatto a una cache su disco, anche su Windows: relativo, senza `..`, senza segmenti vuoti né
 * caratteri che Windows non ammette, né nomi riservati (`CON`, `NUL`…).
 */
internal fun isPortableAssetPath(path: String): Boolean {
    if (!isSafeAssetPath(path)) return false
    for (segment in path.split('/')) {
        if (segment.isEmpty() || segment.endsWith('.') || segment.endsWith(' ')) return false
        if (segment.any { it < ' ' || it in "<>:\"|?*\\" }) return false
        if (segment.substringBefore('.').uppercase() in windowsReservedNames) return false
    }
    return true
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
