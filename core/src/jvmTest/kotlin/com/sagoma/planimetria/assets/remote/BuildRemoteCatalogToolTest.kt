package com.sagoma.planimetria.assets.remote

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `tools/remote/BuildRemoteCatalog.java` (la cartella da caricare sul server): il manifest che scrive lo legge l'app, ogni file
 * ha il suo blob con il contenuto giusto, gli asset sono raggruppati come li cerca l'app, e rilanciarlo non ricopia niente.
 */
class BuildRemoteCatalogToolTest {
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun run(vararg args: String): String {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val p = ProcessBuilder(listOf(java, File("../tools/remote/BuildRemoteCatalog.java").path) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "lo strumento non ha finito")
        assertEquals(0, p.exitValue(), out)
        return out
    }

    @Test
    fun `il manifest scritto dallo strumento e valido e ogni file ha il suo blob`() {
        val root = createTempDirectory("sagoma-remote").toFile()
        try {
            val assets = File(root, "assets")
            val files = mapOf(
                "furniture/catalog.json" to "[]",
                "furniture/ph_sofa_01.glb" to "glb del divano",
                "furniture/ph_sofa_01.png" to "miniatura",
                "furniture/ph_sofa_01_top.png" to "vista dall'alto",
                "furniture/ob_uguale.png" to "miniatura", // stesso contenuto di un altro file: un solo blob
                "materials/materials.json" to "[]",
                "materials/marble_01_color.jpg" to "colore",
                "materials/marble_01_normal.jpg" to "normali",
                "materials/marble_01_orm.jpg" to "orm",
                "materials/marble_01_thumb.jpg" to "campione",
                "env/giorno.ibl" to "luce",
            )
            for ((path, text) in files) File(assets, path).apply { parentFile.mkdirs(); writeText(text) }
            val out = File(root, "server")
            run(assets.path, out.path, "3", "2026-10-14.1")

            val r = ManifestParser.parse(File(out, "manifest.json").readText(), clientVersion = 1)
            assertIs<ManifestParseResult.Valid>(r, r.toString())
            val m = r.manifest
            assertEquals(3, m.catalogVersion)
            assertEquals("2026-10-14.1", m.releaseId)
            assertEquals(BlobLayout.Fanout2, m.blobLayout)
            val resolver = BlobResolver(m)
            for ((path, text) in files) {
                val e = assertNotNull(resolver.entryForPath(path), path)
                assertEquals(sha(text.toByteArray()), e.file.sha256, path)
                val blob = File(out, "blobs/" + m.blobLayout.keyOf(e.file.sha256))
                assertEquals(text, blob.readText(), path)
            }
            // Raggruppati come li cerca l'app: un arredo con modello, miniatura e vista dall'alto; un materiale con le sue mappe.
            assertEquals(setOf("model", "thumbnail", "top"), resolver.entries("furniture:ph_sofa_01").map { it.file.role }.toSet())
            assertEquals(setOf("color", "normal", "orm", "thumbnail"), resolver.entries("material:marble_01").map { it.file.role }.toSet())
            assertEquals("environment", resolver.asset("environment:giorno")?.kind)
            assertEquals("catalog", resolver.asset("catalog:furniture")?.kind)
            assertEquals(files.values.toSet().size, File(out, "blobs").walk().count { it.isFile })

            // Stessi file e stessa versione: lo stesso manifest byte per byte (è quello che l'app porta con sé).
            val first = File(out, "manifest.json").readText()
            val app = File(root, "app")
            run("--solo-manifest", assets.path, app.path, "3", "2026-10-14.1")
            assertEquals(first, File(app, "manifest.json").readText())
            assertTrue(!File(app, "blobs").exists())

            // Di nuovo con gli stessi file: niente da copiare.
            assertTrue("copiati ora: 0" in run(assets.path, out.path, "4"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `i materiali 2K e 4K vanno sul server, e con il loro elenco l'app ha lo stesso manifest senza averli`() {
        val root = createTempDirectory("sagoma-remote-hd").toFile()
        try {
            val assets = File(root, "assets")
            for ((path, text) in mapOf("materials/materials.json" to "[]", "materials/marble_01_color.jpg" to "colore 1K")) {
                File(assets, path).apply { parentFile.mkdirs(); writeText(text) }
            }
            val hd = File(root, "hd")
            val hdFiles = mapOf(
                "materials/2k/marble_01_color.jpg" to "colore 2K",
                "materials/2k/marble_01_normal.jpg" to "normali 2K",
                "materials/2k/marble_01_orm.jpg" to "orm 2K",
                "materials/4k/marble_01_color.jpg" to "colore 4K",
            )
            for ((path, text) in hdFiles) File(hd, path).apply { parentFile.mkdirs(); writeText(text) }
            val index = File(root, "materiali-hd.tsv")
            val out = File(root, "server")
            run("--hd=${hd.path}", "--indice-hd=${index.path}", assets.path, out.path, "3")

            val r = ManifestParser.parse(File(out, "manifest.json").readText(), clientVersion = 1)
            assertIs<ManifestParseResult.Valid>(r, r.toString())
            val resolver = BlobResolver(r.manifest)
            for ((path, text) in hdFiles) {
                val e = assertNotNull(resolver.entryForPath(path), path)
                assertEquals(text, File(out, "blobs/" + r.manifest.blobLayout.keyOf(e.file.sha256)).readText(), path)
            }
            assertEquals(setOf("color", "normal", "orm"), resolver.entries("material:marble_01:2k").map { it.file.role }.toSet())
            assertEquals("material-4k", resolver.asset("material:marble_01:4k")?.kind)
            // L'1K resta il materiale di sempre.
            assertEquals(setOf("color"), resolver.entries("material:marble_01").map { it.file.role }.toSet())
            assertEquals(hdFiles.size, index.readLines().count { !it.startsWith("#") })

            // L'app: niente cartella HD, solo l'elenco nel repository. Stesso manifest byte per byte.
            val app = File(root, "app")
            run("--solo-manifest", "--indice-hd=${index.path}", assets.path, app.path, "3")
            assertEquals(File(out, "manifest.json").readText(), File(app, "manifest.json").readText())

            // Rilanciato con gli stessi file, l'elenco non cambia.
            val before = index.readText()
            assertTrue("copiati ora: 0" in run("--hd=${hd.path}", "--indice-hd=${index.path}", assets.path, out.path, "3"))
            assertEquals(before, index.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
