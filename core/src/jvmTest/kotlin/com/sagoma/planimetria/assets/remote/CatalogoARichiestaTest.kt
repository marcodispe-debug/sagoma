package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.AssetPlanner
import com.sagoma.planimetria.assets.isAvailable
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Il catalogo scaricato a richiesta, dall'inizio alla fine e con i file veri: la cartella per il server preparata con
 * `tools/remote/BuildRemoteCatalog.java`, un server HTTP locale, il sistema remoto dell'app. La scena chiede i suoi file come fa
 * la vista 3D ([AssetPlanner.forScene] + [LateBoundAssetRequests]); arrivano tutti, uguali agli originali, e ogni arrivo fa
 * crescere la versione che la vista osserva per ridisegnare. Prima della richiesta nessuno è sul dispositivo.
 *
 * Il manifest che l'app porta con sé (`--solo-manifest`) coincide con quello del server; si scaricano solo i file della scena;
 * riaperta l'app senza rete, quei file si usano dalla cache senza nessuna richiesta.
 */
class CatalogoARichiestaTest {
    private val assets = File("../catalogo/src/main/assets")
    private val models = listOf("ph_sofa_01", "ph_sofa_02", "ph_sofa_03")

    private fun tool(vararg args: String) {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val proc = ProcessBuilder(listOf(java, File("../tools/remote/BuildRemoteCatalog.java").path) + args)
            .redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS), "lo strumento non ha finito")
        assertEquals(0, proc.exitValue(), log)
    }

    /** Prepara la cartella per il server e il manifest che porterebbe l'app; dà (cartella del server, manifest dell'app). */
    private fun buildServerDir(root: File): Pair<File, String> {
        // Solo una parte del catalogo vero: tre divani e il prato (bastano per la scena, lo strumento è veloce).
        val subset = File(root, "assets")
        val paths = models.flatMap { listOf(AssetPaths.furnitureModel(it), AssetPaths.furnitureThumbnail(it), AssetPaths.furnitureTop(it)) } +
            listOf("color", "normal", "orm").map { AssetPaths.materialMap(MaterialCatalog.GRASS, it) } +
            listOf(AssetPaths.FURNITURE_CATALOG, AssetPaths.MATERIAL_CATALOG)
        for (p in paths) File(assets, p).copyTo(File(subset, p))
        val out = File(root, "server")
        tool(subset.path, out.path, "1")
        val app = File(root, "app")
        tool("--solo-manifest", subset.path, app.path, "1")
        assertTrue(!File(app, "blobs").exists(), "solo il manifest, nessun blob")
        return out to File(app, "manifest.json").readText()
    }

    /** Percorsi chiesti al server, in ordine. */
    private val served: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Server di file statici, come un hosting qualsiasi: GET di un percorso sotto `dir`. */
    private fun serve(dir: File): HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { ex ->
            served += ex.requestURI.path
            val f = File(dir, ex.requestURI.path.removePrefix("/"))
            if (f.isFile && f.canonicalPath.startsWith(dir.canonicalPath)) {
                ex.sendResponseHeaders(200, f.length())
                ex.responseBody.use { out -> f.inputStream().use { it.copyTo(out) } }
            } else {
                ex.sendResponseHeaders(404, -1)
                ex.close()
            }
        }
        start()
    }

    @Test
    fun `i file della scena arrivano dal server quando la vista li chiede`() = runBlocking {
        assertTrue(assets.isDirectory, "Cartella del catalogo non trovata: ${assets.absolutePath}")
        val root = createTempDirectory("sagoma-catalogo-remoto").toFile()
        val (serverDir, bundled) = buildServerDir(root)
        assertEquals(File(serverDir, "manifest.json").readText(), bundled, "il manifest dell'app è quello del server")
        val server = serve(serverDir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val url = "http://127.0.0.1:${server.address.port}"
        val cacheDir = File(root, "cache")
        var remote = createJvmRemoteAssets(cacheDir, "$url/manifest.json", "$url/blobs", 1L shl 30, bundledManifest = bundled)!!
        try {
            val requests = LateBoundAssetRequests()
            requests.bind(remote.prefetcher, scope)
            assertIs<ManifestRefreshResult.Unchanged>(remote.refreshManifest())

            val room = Room(1, "Soggiorno", RoomType.Soggiorno, RoomFactory.rectangle(600.0, 500.0))
            val furniture = models.mapIndexed { i, m -> Furniture(i + 1L, m, Vec2(150.0 + i * 150, 250.0), width = 140.0, depth = 80.0, height = 80.0) }
            val scene = Scene3D.build(FloorPlan(listOf(room), furniture = furniture), null, null, ceilings = false)
            val needed = AssetPlanner.forScene(scene).map { it.path }
            assertEquals(models.map(AssetPaths::furnitureModel).toSet() + listOf("color", "normal", "orm").map { AssetPaths.materialMap(MaterialCatalog.GRASS, it) }, needed.toSet())
            assertTrue(needed.none { remote.store.isAvailable(it) }, "prima della richiesta niente è sul dispositivo")

            val before = requests.version.value
            for (r in AssetPlanner.forScene(scene)) requests.request(r.path, r.priority)
            withTimeout(60_000) {
                requests.version.first { needed.all { p -> remote.store.isAvailable(p) } }
            }
            assertTrue(requests.version.value > before, "gli arrivi fanno crescere la versione che la vista osserva")
            for (p in needed) assertContentEquals(File(assets, p).readBytes(), remote.store.peek(p), p)
            // Solo i file della scena, ognuno una volta (più la richiesta del manifest).
            val blobRequests = served.filter { it.startsWith("/blobs/") }
            assertEquals(needed.size, blobRequests.size, blobRequests.toString())
            assertEquals(blobRequests.size, blobRequests.toSet().size)

            // L'app si riapre senza rete: il manifest dell'app e la cache bastano, nessuna richiesta.
            remote.close()
            server.stop(0)
            served.clear()
            remote = createJvmRemoteAssets(cacheDir, "$url/manifest.json", "$url/blobs", 1L shl 30, bundledManifest = bundled)!!
            val offline = LateBoundAssetRequests()
            offline.bind(remote.prefetcher, scope)
            assertIs<ManifestRefreshResult.Failed>(remote.refreshManifest())
            for (r in AssetPlanner.forScene(scene)) offline.request(r.path, r.priority)
            for (p in needed) {
                assertTrue(remote.store.isAvailable(p), p)
                assertContentEquals(File(assets, p).readBytes(), remote.store.peek(p), p)
            }
            assertEquals(emptyList(), served.toList())
        } finally {
            remote.close()
            scope.cancel()
            runCatching { server.stop(0) }
            root.deleteRecursively()
        }
    }
}
