package com.sagoma.planimetria.persistence

import java.io.File

/** Tema scelto: il nome della scelta in un file, nella memoria privata dell'app. */
class FileThemeStore(dir: File) : ThemeStore {
    private val file = File(dir, "tema.txt")

    override fun load(): ThemeChoice = runCatching {
        val name = file.takeIf { it.exists() }?.readText()?.trim()
        ThemeChoice.entries.firstOrNull { it.name == name }
    }.getOrNull() ?: ThemeChoice.Light

    override fun save(choice: ThemeChoice) {
        runCatching { file.writeText(choice.name) }
    }
}
