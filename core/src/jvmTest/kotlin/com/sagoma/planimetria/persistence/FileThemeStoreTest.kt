package com.sagoma.planimetria.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileThemeStoreTest {
    @Test
    fun laSceltaRestaTraUnAvvioELAltro() {
        val dir = Files.createTempDirectory("sagoma-tema").toFile()
        try {
            assertEquals(ThemeChoice.Light, FileThemeStore(dir).load(), "senza scelta salvata l'app è chiara")
            FileThemeStore(dir).save(ThemeChoice.Dark)
            assertEquals(ThemeChoice.Dark, FileThemeStore(dir).load())
            FileThemeStore(dir).save(ThemeChoice.System)
            assertEquals(ThemeChoice.System, FileThemeStore(dir).load())
            dir.resolve("tema.txt").writeText("viola")
            assertEquals(ThemeChoice.Light, FileThemeStore(dir).load(), "un file rovinato non blocca l'avvio")
        } finally {
            dir.deleteRecursively()
        }
    }
}
