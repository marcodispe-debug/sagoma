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
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Il catalogo scaricato a richiesta, dall'inizio alla fine e con i file veri: la cartella per il server preparata con
 * `tools/remote/BuildRemoteCatalog.java`, un server HTTP locale, il sistema remoto dell'app. La scena chiede i suoi file come fa
 * la vista 3D ([AssetPlanner.forScene] + [LateBoundAssetRequests]); arrivano tutti, uguali agli originali, e ogni arrivo fa
 * crescere la versione che la vista osserva per ridisegnare. Prima della richiesta nessuno è sul dispositivo.
 */
class CatalogoARichiestaTest {
    private val assets = File("../catalogo/src/main/assets")
    private val models = listOf("ph_sofa_01", "ph_sofa_02", "ph_sofa_03")

    private fun buildServerDir(root: File): File {
        // Solo una parte del catalogo vero: tre divani e il prato (bastano per la scena, lo strumento è veloce).
        val subset = File(root, "assets")
        val paths = models.flatMap { listOf(AssetPaths.furnitureModel(it), AssetPaths.furnitureThumbnail(it), AssetPaths.furnitureTop(it)) } +
            listOf("color", "normal", "orm").map { AssetPaths.materialMap(MaterialCatalog.GRASS, it) } +
            listOf(AssetPaths.FURNITURE_CATALOG, AssetPaths.MATERIAL_CATALOG)
        for (p in paths) File(assets, p).copyTo(File(subset, p))
        val out = File(root, "server")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val proc = ProcessBuilder(java, File("../tools/remote/BuildRemoteCatalog.java").path, subset.path, out.path, "1")
            .redirectErrorStream(true).start()
        val log = proc.inputStream.bufferedReader().readText()
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS), "lo strumento non ha finito")
        assertEquals(0, proc.exitValue(), log)
        return out
    }

    /** Server di file statici, come un hosting qualsiasi: GET di un percorso sotto `dir`. */
    private fun serve(dir: File): HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { ex ->
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
        val server = serve(buildServerDir(root))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val url = "http://127.0.0.1:${server.address.port}"
        val remote = createJvmRemoteAssets(File(root, "cache"), "$url/manifest.json", "$url/blobs", 1L shl 30)!!
        try {
            val requests = LateBoundAssetRequests()
            requests.bind(remote.prefetcher, scope)
            assertTrue(remote.refreshManifest() is ManifestRefreshResult.Updated)

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
        } finally {
            remote.close()
            scope.cancel()
            server.stop(0)
            root.deleteRecursively()
        }
    }
}
