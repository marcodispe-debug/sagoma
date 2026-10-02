package com.sagoma.planimetria.persistence

import kotlinx.serialization.Serializable

/** Dati di un progetto per l'elenco e per il cartiglio delle tavole. */
@Serializable
data class ProjectInfo(
    val id: Long,
    val name: String,
    val client: String = "",
    val address: String = "",
    val author: String = "",
    val created: Long = 0,
    val modified: Long = 0,
)

/**
 * Archivio dei progetti, uguale su tutte le piattaforme: ognuna lo realizza a modo suo (file su Android e
 * computer, memoria del browser sul web, cloud in futuro). Ogni progetto ha la sua casa e i suoi file
 * (immagini di sfondo).
 */
interface ProjectRepository {
    fun list(): List<ProjectInfo>
    fun info(id: Long): ProjectInfo?
    /** Ultimo progetto aperto (null se non esiste più). */
    val lastOpened: Long?
    fun markOpened(id: Long)

    /** La casa del progetto; ogni salvataggio aggiorna anche la data di modifica. */
    fun planStore(id: Long): PlanStore

    fun create(name: String, client: String = "", address: String = ""): ProjectInfo
    fun update(p: ProjectInfo)
    /** Copia completa (varianti: stato di fatto e progetto, proposta A e B). */
    fun duplicate(id: Long, name: String): ProjectInfo?
    /** Elimina il progetto; almeno uno resta sempre. */
    fun delete(id: Long)

    /** File del progetto (immagini di sfondo…), per nome. */
    fun readFile(id: Long, name: String): ByteArray?
    fun writeFile(id: Long, name: String, bytes: ByteArray)

    /** Progetto in un unico file .sagoma (zip: scheda del progetto, casa e file). */
    fun export(id: Long): ByteArray?
    /** Nuovo progetto da un file .sagoma (o da un semplice planimetria.json). Null se il file non è valido. */
    fun import(bytes: ByteArray): ProjectInfo?
}
