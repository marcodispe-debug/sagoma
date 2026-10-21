package com.sagoma.planimetria.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileLanguageStoreTest {
    @Test
    fun laSceltaRestaTraUnAvvioELAltro() {
        val dir = Files.createTempDirectory("sagoma-lingua").toFile()
        try {
            assertEquals(LanguageChoice.System, FileLanguageStore(dir).load(), "senza scelta salvata segue il sistema")
            FileLanguageStore(dir).save(LanguageChoice.English)
            assertEquals(LanguageChoice.English, FileLanguageStore(dir).load())
            FileLanguageStore(dir).save(LanguageChoice.Italian)
            assertEquals(LanguageChoice.Italian, FileLanguageStore(dir).load())
            dir.resolve("lingua.txt").writeText("klingon")
            assertEquals(LanguageChoice.System, FileLanguageStore(dir).load(), "un file rovinato non blocca l'avvio")
        } finally {
            dir.deleteRecursively()
        }
    }
}
