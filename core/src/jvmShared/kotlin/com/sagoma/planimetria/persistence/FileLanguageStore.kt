package com.sagoma.planimetria.persistence

import java.io.File

/** Lingua scelta: il nome della scelta in un file, nella memoria privata dell'app. */
class FileLanguageStore(dir: File) : LanguageStore {
    private val file = File(dir, "lingua.txt")

    override fun load(): LanguageChoice = runCatching {
        val name = file.takeIf { it.exists() }?.readText()?.trim()
        LanguageChoice.entries.firstOrNull { it.name == name }
    }.getOrNull() ?: LanguageChoice.System

    override fun save(choice: LanguageChoice) {
        runCatching { file.writeText(choice.name) }
    }
}
