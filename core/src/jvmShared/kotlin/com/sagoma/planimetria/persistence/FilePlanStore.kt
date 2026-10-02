package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.model.Building
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Casa salvata in un file JSON nella memoria privata dell'app. La scrittura è atomica (file
 * temporaneo + rinomina), quindi un'interruzione a metà non corrompe l'ultimo salvataggio valido.
 */
class FilePlanStore(dir: File) : PlanStore {
    private val file = File(dir, "planimetria.json")
    private val tmp = File(dir, "planimetria.json.tmp")

    override fun load(): Building? {
        if (!file.exists()) return null
        return try {
            PlanJson.decodeBuilding(file.readText())
        } catch (e: Exception) {
            // File illeggibile: lo si mette da parte invece di sovrascriverlo al prossimo salvataggio.
            file.renameTo(File(file.parentFile, "planimetria.corrupt-${System.currentTimeMillis()}.json"))
            null
        }
    }

    @Synchronized
    override fun save(building: Building) {
        tmp.writeText(PlanJson.encode(building))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
