package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.model.Building
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Archivio dei progetti su file (Android e computer): una cartella per progetto (`projects/<id>/`, con
 * la casa in `planimetria.json` e l'eventuale pianta di sfondo) e l'elenco in `projects/index.json`.
 * Alla prima apertura la casa della versione precedente (un solo file) diventa il primo progetto.
 */
class FileProjectRepository(private val root: File) : ProjectRepository {
    private val dir = File(root, "projects").also { it.mkdirs() }
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Index(val projects: List<ProjectInfo> = emptyList(), val last: Long? = null)

    private fun read(): Index = runCatching { json.decodeFromString(Index.serializer(), index.readText()) }.getOrDefault(Index())

    @Synchronized
    private fun write(i: Index) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(Index.serializer(), i))
        Files.move(tmp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    init {
        if (!index.exists()) {
            val old = File(root, "planimetria.json")
            val now = System.currentTimeMillis()
            val first = ProjectInfo(1, "La mia casa", created = now, modified = now)
            folder(1).mkdirs()
            if (old.exists()) old.copyTo(File(folder(1), "planimetria.json"), overwrite = true)
            write(Index(listOf(first), last = 1))
        }
    }

    override fun list(): List<ProjectInfo> = read().projects.sortedByDescending { it.modified }
    override fun info(id: Long): ProjectInfo? = read().projects.firstOrNull { it.id == id }
    override val lastOpened: Long? get() = read().last?.takeIf { id -> info(id) != null }

    /** Cartella del progetto. */
    fun folder(id: Long) = File(dir, id.toString())

    override fun planStore(id: Long): PlanStore {
        val file = FilePlanStore(folder(id).also { it.mkdirs() })
        return object : PlanStore {
            override fun load() = file.load()
            override fun save(building: Building) {
                file.save(building)
                touch(id)
            }
        }
    }

    override fun markOpened(id: Long) = write(read().copy(last = id))

    /** Il progetto è stato modificato adesso (per l'ordine dell'elenco). */
    fun touch(id: Long) {
        val i = read()
        write(i.copy(projects = i.projects.map { if (it.id == id) it.copy(modified = System.currentTimeMillis()) else it }))
    }

    private fun nextId(i: Index) = (i.projects.maxOfOrNull { it.id } ?: 0) + 1

    override fun create(name: String, client: String, address: String): ProjectInfo {
        val i = read()
        val now = System.currentTimeMillis()
        val p = ProjectInfo(nextId(i), name.ifBlank { "Progetto ${nextId(i)}" }, client, address, created = now, modified = now)
        folder(p.id).mkdirs()
        write(i.copy(projects = i.projects + p))
        return p
    }

    override fun update(p: ProjectInfo) {
        val i = read()
        write(i.copy(projects = i.projects.map { if (it.id == p.id) p else it }))
    }

    override fun duplicate(id: Long, name: String): ProjectInfo? {
        val src = info(id) ?: return null
        val i = read()
        val now = System.currentTimeMillis()
        val p = src.copy(id = nextId(i), name = name, created = now, modified = now)
        folder(id).copyRecursively(folder(p.id), overwrite = true)
        write(i.copy(projects = i.projects + p))
        return p
    }

    override fun delete(id: Long) {
        val i = read()
        if (i.projects.size <= 1) return
        folder(id).deleteRecursively()
        write(i.copy(projects = i.projects.filter { it.id != id }, last = i.last.takeIf { it != id }))
    }

    /** Solo nomi semplici: niente cartelle né "..". */
    private fun safe(name: String) = name.isNotBlank() && !name.contains('/') && !name.contains('\\') && !name.contains("..")

    override fun readFile(id: Long, name: String): ByteArray? =
        if (!safe(name)) null else File(folder(id), name).takeIf { it.isFile }?.readBytes()

    override fun writeFile(id: Long, name: String, bytes: ByteArray) {
        require(safe(name)) { "Nome di file non valido: $name" }
        folder(id).mkdirs()
        File(folder(id), name).writeBytes(bytes)
    }

    override fun export(id: Long): ByteArray? {
        val p = info(id) ?: return null
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("progetto.json"))
            zip.write(json.encodeToString(ProjectInfo.serializer(), p).toByteArray())
            zip.closeEntry()
            folder(id).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach { f ->
                zip.putNextEntry(ZipEntry("file/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    override fun import(bytes: ByteArray): ProjectInfo? {
        val i = read()
        val now = System.currentTimeMillis()
        val id = nextId(i)
        val target = folder(id).also { it.mkdirs() }
        var info: ProjectInfo? = null
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (isZip) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.removePrefix("file/")
                    when {
                        e.name == "progetto.json" -> info = runCatching { json.decodeFromString(ProjectInfo.serializer(), zip.readBytes().decodeToString()) }.getOrNull()
                        e.name.startsWith("file/") && safe(name) -> File(target, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } else {
            File(target, "planimetria.json").writeBytes(bytes)
        }
        // La casa deve leggersi, altrimenti non è un progetto.
        val plan = File(target, "planimetria.json")
        if (!plan.exists() || runCatching { PlanJson.decodeBuilding(plan.readText()) }.isFailure) {
            target.deleteRecursively()
            return null
        }
        val p = (info ?: ProjectInfo(id, "Progetto importato")).copy(id = id, created = now, modified = now)
        write(i.copy(projects = i.projects + p))
        return p
    }
}
