package com.sagoma.planimetria.persistence

import com.sagoma.planimetria.editor.TipStore
import java.io.File

/** Suggerimenti già visti: un nome per riga, nella memoria privata dell'app. */
class FileTipStore(dir: File) : TipStore {
    private val file = File(dir, "suggerimenti-visti.txt")

    override fun seen(): Set<String> = runCatching {
        if (!file.exists()) return emptySet()
        file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    override fun save(seen: Set<String>) {
        runCatching { file.writeText(seen.joinToString("\n")) }
    }
}
