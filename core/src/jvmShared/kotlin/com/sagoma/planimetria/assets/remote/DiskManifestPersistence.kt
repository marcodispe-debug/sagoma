package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.moveReplacing
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * [ManifestPersistence] su un file (Android e computer): `dir/manifest.json`. Il salvataggio scrive il testo in
 * `manifest.json.tmp` nella stessa cartella, lo porta su disco e lo sposta al posto del precedente in modo atomico (stessa
 * primitiva dell'indice della cache): un arresto a metà lascia al più un `.tmp`, e `manifest.json` resta il vecchio file intero.
 * Un `.tmp` rimasto non si legge mai e il salvataggio successivo lo riscrive.
 */
class DiskManifestPersistence(private val dir: File, fileName: String = "manifest.json") : ManifestPersistence {
    private val file = File(dir, fileName)
    private val tmp = File(dir, "$fileName.tmp")

    /** Solo per i test: chiamato dopo aver scritto il `.tmp` e prima dello spostamento (può lanciare per simulare un errore). */
    internal var beforeReplace: () -> Unit = {}

    override fun load(): String? = try {
        if (file.isFile) file.readText() else null
    } catch (e: IOException) {
        null
    }

    @Synchronized
    override fun save(text: String): Boolean = try {
        dir.mkdirs()
        FileOutputStream(tmp).use { out ->
            out.write(text.encodeToByteArray())
            out.flush()
            out.fd.sync()
        }
        beforeReplace()
        moveReplacing(tmp, file)
        true
    } catch (e: IOException) {
        tmp.delete()
        false
    }
}
