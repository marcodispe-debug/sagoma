package com.sagoma.planimetria.assets.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs

/** TEMPORANEO (non va nel repository): il manifest degli asset veri, scritto da BuildRemoteCatalog, si legge. */
class TmpRealManifestTest {
    @Test
    fun manifestVero() {
        val path = System.getenv("SAGOMA_REMOTE_MANIFEST") ?: return
        val r = ManifestParser.parse(File(path).readText(), clientVersion = 1)
        assertIs<ManifestParseResult.Valid>(r, r.toString().take(2000))
        val m = r.manifest
        val kinds = m.assets.groupingBy { it.kind }.eachCount()
        File("build/tmp-manifest.txt").writeText("asset ${m.assets.size} file ${m.assets.sumOf { it.files.size }} tipi $kinds\n" +
            "arredi senza modello: ${m.assets.filter { it.kind == "furniture" && it.files.none { f -> f.role == "model" } }.map { it.id }}\n" +
            "arredi senza vista dall'alto: ${m.assets.filter { it.kind == "furniture" && it.files.none { f -> f.role == "top" } }.map { it.id }}\n" +
            "altri file: ${m.assets.filter { it.kind == "file" }.map { it.id }}\n")
    }
}
