package com.sagoma.planimetria.web

import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.persistence.PlanJson
import com.sagoma.planimetria.persistence.PlanStore
import com.sagoma.planimetria.persistence.ProjectInfo
import com.sagoma.planimetria.persistence.ProjectRepository
import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Archivio dei progetti nella memoria del browser (localStorage): elenco in `sagoma/index`, casa in
 * `sagoma/<id>/plan`, file del progetto (sfondi) in `sagoma/<id>/file/<nome>` in base64. Il browser tiene
 * pochi MB: è una soluzione di partenza, finché i progetti non passano nel cloud.
 */
@OptIn(ExperimentalEncodingApi::class)
class BrowserProjectRepository(private val now: () -> Long) : ProjectRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Index(val projects: List<ProjectInfo> = emptyList(), val last: Long? = null)

    private fun read(): Index = localStorage.getItem(INDEX)?.let { runCatching { json.decodeFromString(Index.serializer(), it) }.getOrNull() } ?: Index()
    private fun write(i: Index) = localStorage.setItem(INDEX, json.encodeToString(Index.serializer(), i))

    init {
        if (localStorage.getItem(INDEX) == null) {
            val t = now()
            write(Index(listOf(ProjectInfo(1, "Il mio progetto", created = t, modified = t)), last = 1))
        }
    }

    override fun list(): List<ProjectInfo> = read().projects.sortedByDescending { it.modified }
    override fun info(id: Long): ProjectInfo? = read().projects.firstOrNull { it.id == id }
    override val lastOpened: Long? get() = read().last?.takeIf { info(it) != null }
    override fun markOpened(id: Long) = write(read().copy(last = id))

    private fun touch(id: Long) {
        val i = read()
        write(i.copy(projects = i.projects.map { if (it.id == id) it.copy(modified = now()) else it }))
    }

    override fun planStore(id: Long): PlanStore = object : PlanStore {
        override fun load(): Building? = localStorage.getItem(planKey(id))?.let { runCatching { PlanJson.decodeBuilding(it) }.getOrNull() }
        override fun save(building: Building) {
            localStorage.setItem(planKey(id), PlanJson.encode(building))
            touch(id)
        }
    }

    private fun nextId(i: Index) = (i.projects.maxOfOrNull { it.id } ?: 0) + 1

    override fun create(name: String, client: String, address: String): ProjectInfo {
        val i = read()
        val t = now()
        val p = ProjectInfo(nextId(i), name.ifBlank { "Progetto ${nextId(i)}" }, client, address, created = t, modified = t)
        write(i.copy(projects = i.projects + p))
        return p
    }

    override fun update(p: ProjectInfo) {
        val i = read()
        write(i.copy(projects = i.projects.map { if (it.id == p.id) p else it }))
    }

    /** Chiavi del progetto (casa e file). */
    private fun keysOf(id: Long): List<String> = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith("sagoma/$id/") }

    override fun duplicate(id: Long, name: String): ProjectInfo? {
        val src = info(id) ?: return null
        val i = read()
        val t = now()
        val p = src.copy(id = nextId(i), name = name, created = t, modified = t)
        for (k in keysOf(id)) localStorage.getItem(k)?.let { localStorage.setItem("sagoma/${p.id}/" + k.removePrefix("sagoma/$id/"), it) }
        write(i.copy(projects = i.projects + p))
        return p
    }

    override fun delete(id: Long) {
        val i = read()
        if (i.projects.size <= 1) return
        keysOf(id).forEach { localStorage.removeItem(it) }
        write(i.copy(projects = i.projects.filter { it.id != id }, last = i.last.takeIf { it != id }))
    }

    override fun readFile(id: Long, name: String): ByteArray? =
        localStorage.getItem(fileKey(id, name))?.let { runCatching { Base64.decode(it) }.getOrNull() }

    override fun writeFile(id: Long, name: String, bytes: ByteArray) = localStorage.setItem(fileKey(id, name), Base64.encode(bytes))

    /** Sul web per ora si esporta la sola casa (planimetria.json), che le altre versioni sanno importare. */
    override fun export(id: Long): ByteArray? = localStorage.getItem(planKey(id))?.encodeToByteArray()

    /** Sul web per ora si importa un planimetria.json (i file .sagoma compressi arriveranno col cloud). */
    override fun import(bytes: ByteArray): ProjectInfo? {
        val text = bytes.decodeToString()
        if (runCatching { PlanJson.decodeBuilding(text) }.isFailure) return null
        val p = create("Progetto importato")
        localStorage.setItem(planKey(p.id), text)
        return p
    }

    private fun planKey(id: Long) = "sagoma/$id/plan"
    private fun fileKey(id: Long, name: String) = "sagoma/$id/file/$name"

    private companion object {
        const val INDEX = "sagoma/index"
    }
}

/** Suggerimenti già visti, nella memoria del browser. */
class BrowserTipStore : TipStore {
    override fun seen(): Set<String> = localStorage.getItem("sagoma/suggerimenti")?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    override fun save(seen: Set<String>) = localStorage.setItem("sagoma/suggerimenti", seen.joinToString("\n"))
}
