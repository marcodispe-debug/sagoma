package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.CaptureDataset
import com.sagoma.planimetria.scan.recording.DepthRaw
import com.sagoma.planimetria.scan.recording.PoseMatch
import com.sagoma.planimetria.scan.recording.rotate
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** R1/R2 su una stanza sintetica (4 × 3 × 2,6 m, un mobile basso, depth esatta o con rumore noto). */
class ReconstructionTest {
    private val room = SyntheticRoom()

    private fun build(r: SyntheticRoom = room, rec: Pair<com.sagoma.planimetria.scan.recording.ScanRecording, Map<String, ByteArray>> = r.recording()): Triple<GlobalMap, SurfaceResult, com.sagoma.planimetria.scan.recording.ScanRecording> {
        val map = GlobalMap.build(rec.first, { rec.second[it] })
        return Triple(map, SurfaceExtractor.extract(map, rec.first), rec.first)
    }

    /** Distanza di un punto dalla superficie più vicina della scena (pareti, pavimento, soffitto, facce del mobile). */
    private fun sceneDistance(x: Double, y: Double, z: Double, r: SyntheticRoom = room): Double {
        var d = minOf(abs(x), abs(x - 4), abs(y), abs(y - 2.6), abs(z), abs(z - 3))
        if (z < -1.0) d = min(d, abs(z + 2))
        for (b in r.boxes) {
            val inside = x in b.x0 - 0.01..b.x1 + 0.01 && y in b.y0 - 0.01..b.y1 + 0.01 && z in b.z0 - 0.01..b.z1 + 0.01
            if (inside) d = min(d, minOf(abs(x - b.x0), abs(x - b.x1), abs(y - b.y1), abs(z - b.z0), abs(z - b.z1)))
        }
        return d
    }

    @Test
    fun `1 - camera nel mondo - ogni pixel cade sulla superficie della scena da cui viene`() {
        val pose = room.pose(2.0, 1.4, 2.0, 30.0, -20.0)
        val mm = room.render(pose)
        val f = DepthFrame(0, DepthUse.RAW, 0, pose, room.k, mm, null, 0, PoseMatch.FRAME)
        var n = 0
        DepthToWorld.convert(f, ReconParams(edgeJumpRatio = 10.0)) { p ->
            assertTrue(sceneDistance(p.x, p.y, p.z) < 0.004, "punto (${p.x}, ${p.y}, ${p.z}) fuori dalla scena")
            n++
        }
        assertTrue(n > room.width * room.height * 0.9)
        // Lo stesso pixel con la formula esplicita: p = o + R·((u−cx)/fx·z, (cy−v)/fy·z, −z).
        val u = 10; val v = 20; val z = mm[v * room.width + u] / 1000.0
        val w = pose.rotate((u - room.k.cx) / room.k.fx * z, (room.k.cy - v) / room.k.fy * z, -z)
        val p = ArCameraProjection.unproject(pose, room.k, u.toDouble(), v.toDouble(), z)
        assertEquals(pose.x + w.x, p[0], 1e-12); assertEquals(pose.y + w.y, p[1], 1e-12); assertEquals(pose.z + w.z, p[2], 1e-12)
    }

    @Test
    fun `2 - la raw usa la posa del proprio frame, non quella della depth filtrata`() {
        val poses = room.cameras()
        val (rec, blobs0) = room.recording(poses)
        val blobs = blobs0.toMutableMap()
        // Ogni raw (tranne la prima) viene dall'immagine del frame PRECEDENTE: altra posa, altro timestamp.
        val depth = rec.depth.map { d ->
            if (d.seq == 0) d else {
                blobs[CaptureDataset.rawDepthPath(d.seq)] = DepthRaw.encode(room.render(poses[d.seq - 1]))
                d.copy(rawTimestampNs = rec.poses[d.seq - 1].timestampNs, rawPoseFrameSeq = d.seq - 1, rawCamera = poses[d.seq - 1])
            }
        }
        val r = rec.copy(depth = depth)
        val map = GlobalMap.build(r, { blobs[it] }, ReconParams(edgeJumpRatio = 10.0))
        assertEquals(poses.size, map.frames.size)
        for (f in map.frames.drop(1)) assertEquals(f.depthSeq - 1, f.poseFrameSeq)
        var worst = 0.0
        for (i in 0 until map.points.size) worst = maxOf(worst, sceneDistance(map.points.x[i].toDouble(), map.points.y[i].toDouble(), map.points.z[i].toDouble()))
        assertTrue(worst < 0.005, "punto più lontano dalla scena: $worst m")
        // Controllo: con la posa della depth filtrata i punti si staccano dalla scena.
        val wrong = DepthFrame(5, DepthUse.RAW, 0, poses[5], room.k, DepthRaw.decode(blobs.getValue(CaptureDataset.rawDepthPath(5)), room.width, room.height), null, 5, PoseMatch.FRAME)
        var far = 0
        DepthToWorld.convert(wrong, ReconParams(edgeJumpRatio = 10.0)) { p -> if (sceneDistance(p.x, p.y, p.z) > 0.05) far++ }
        assertTrue(far > 100, "con la posa sbagliata solo $far punti fuori scena")
    }

    @Test
    fun `3 - voxel - indice dei punti originali, momenti esatti, chiavi ordinate`() {
        val (map, _, _) = build()
        val vx = map.voxels
        assertEquals(map.points.size, vx.count.sum())
        for (i in 1 until vx.size) assertTrue(vx.keys[i] > vx.keys[i - 1])
        for (v in 0 until vx.size step 97) {
            val k = vx.keys[v]
            var sx = 0.0; var sw = 0.0
            for (t in vx.start[v] until vx.start[v] + vx.count[v]) {
                val i = vx.order[t]
                assertEquals(VoxelIndex.ix(k), kotlin.math.floor(map.points.x[i] / vx.sizeM).toInt())
                assertEquals(VoxelIndex.iy(k), kotlin.math.floor(map.points.y[i] / vx.sizeM).toInt())
                assertEquals(VoxelIndex.iz(k), kotlin.math.floor(map.points.z[i] / vx.sizeM).toInt())
                sx += map.points.x[i] * map.points.weight[i].toDouble(); sw += map.points.weight[i]
            }
            assertEquals(vx.count[v], vx.moments[v].n)
            assertEquals(sx / sw, vx.moments[v].centroid()[0], 1e-6) // i momenti sono dei punti originali, non del centro del voxel
        }
    }

    @Test
    fun `4 - planarita - piano perfetto contro nuvola sparsa`() {
        val plane = Moments()
        for (i in 0 until 20) for (j in 0 until 20) plane.add(i * 0.01, 0.5, j * 0.01, 1.0)
        val p = assertNotNull(plane.plane())
        assertTrue(p.surfaceVariation < 1e-9)
        assertEquals(1.0, abs(p.ny), 1e-9)
        assertEquals(0.0, p.rms, 1e-9)
        val cloud = Moments()
        var s = 7L
        repeat(500) { s = (s * 6364136223846793005L + 1442695040888963407L); val a = ((s ushr 40) % 1000) / 1000.0; s = s * 31 + 17; val b = ((s ushr 40) % 1000) / 1000.0; s = s * 31 + 17; val c = ((s ushr 40) % 1000) / 1000.0; cloud.add(a, b, c, 1.0) }
        assertTrue(assertNotNull(cloud.plane()).surfaceVariation > 0.2)
    }

    @Test
    fun `5 e 9 - pavimento orizzontale, alla quota giusta, classificato FLOOR`() {
        val (_, s, _) = build()
        val floor = s.surfaces.filter { it.kind == SurfaceKind.FLOOR }.maxByOrNull { it.areaM2 }
        assertNotNull(floor)
        assertEquals(Orientation.HORIZONTAL_UP, floor.orientation)
        assertTrue(floor.plane.ny > 0.999)
        assertEquals(0.0, floor.plane.centroid[1], 0.003)
        assertEquals(0.0, assertNotNull(s.floor).y, 0.003)
        assertTrue(floor.tiltDeg < 0.5)
    }

    @Test
    fun `6 - parete verticale con normale e posizione giuste`() {
        val (_, s, _) = build()
        val wall = s.surfaces.filter { it.orientation == Orientation.VERTICAL }.firstOrNull { abs(it.plane.nx) > 0.99 && abs(it.plane.centroid[0] - 4.0) < 0.05 }
        assertNotNull(wall, "parete x = 4 non trovata")
        assertEquals(0.0, wall.plane.distance(4.0, 1.0, 1.5), 0.003)
        assertTrue(wall.plane.nx < 0, "la normale deve puntare verso le camere (dentro la stanza)")
        assertTrue(wall.tiltDeg < 0.5)
        assertTrue(wall.heightM > 1.2 && wall.lengthM > 2.5, "altezza ${wall.heightM}, lunghezza ${wall.lengthM}") // le camere sintetiche guardano 20° in basso: vedono fino a ~1,45 m
    }

    @Test
    fun `7 - region growing - un piano libero cresce in una sola regione, un'ombra lo divide senza perdere punti`() {
        // Senza mobile: il pavimento visto è un'unica regione. Restano fuori solo i punti nella fascia lungo le pareti (il vicinato di
        // 20 cm della normale lì prende pavimento e parete insieme): limite noto, da gestire in R3 con i punti vicini agli spigoli.
        val (map, s, _) = build(SyntheticRoom(boxes = emptyList()))
        val floorIdx = (0 until map.points.size).filter { abs(map.points.y[it]) < 0.003 }
        val floors = s.surfaces.filter { it.kind == SurfaceKind.FLOOR && it.areaM2 > 0.5 }
        assertEquals(1, floors.size)
        assertTrue(floors[0].samples > 0.7 * floorIdx.size, "pavimento: ${floors[0].samples} punti su ${floorIdx.size}")
        val inFloor = SurfaceExtractor.pointsOf(map, floors[0].memberVoxels).toHashSet()
        val leftOut = floorIdx.filter { it !in inFloor }.map { i -> minOf(abs(map.points.x[i].toDouble()), abs(map.points.x[i] - 4.0), abs(map.points.z[i].toDouble()), abs(map.points.z[i] - 3.0)) }.sorted()
        assertTrue(leftOut.isEmpty() || leftOut[leftOut.size * 9 / 10] < 0.15, "punti del pavimento esclusi lontani dalle pareti: p90 ${leftOut.getOrNull(leftOut.size * 9 / 10)} m")
        // Con il mobile l'ombra divide il pavimento in parti che non si toccano: R2 non le unisce (lo farà R3), ma sono tutte FLOOR
        // e insieme tengono i punti del pavimento.
        val (map2, s2, _) = build()
        val floorPoints2 = (0 until map2.points.size).count { abs(map2.points.y[it]) < 0.003 }
        val parts = s2.surfaces.filter { it.kind == SurfaceKind.FLOOR }
        assertTrue(parts.size >= 2)
        assertTrue(parts.sumOf { it.samples } > 0.7 * floorPoints2, "pavimento: ${parts.sumOf { it.samples }} punti su $floorPoints2")
    }

    @Test
    fun `8 - due superfici distinte - piano del mobile e pavimento, fronte del mobile e parete dietro`() {
        val (_, s, _) = build()
        val top = s.surfaces.firstOrNull { it.orientation == Orientation.HORIZONTAL_UP && abs(it.plane.centroid[1] - 0.8) < 0.02 }
        val floor = s.surfaces.first { it.kind == SurfaceKind.FLOOR }
        assertNotNull(top, "piano del mobile non trovato")
        assertTrue(top.id != floor.id)
        val front = s.surfaces.firstOrNull { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.99 && abs(it.plane.centroid[2] - 1.1) < 0.03 }
        val back = s.surfaces.firstOrNull { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.99 && abs(it.plane.centroid[2]) < 0.03 }
        assertNotNull(front, "fronte del mobile non trovato"); assertNotNull(back, "parete z = 0 non trovata")
        assertTrue(front.id != back.id)
        assertTrue(front.memberVoxels.none { it in back.memberVoxels })
    }

    @Test
    fun `10 - pareti strutturali e fronte del mobile oggetto`() {
        val (_, s, _) = build()
        val walls = s.surfaces.filter { it.kind == SurfaceKind.VERTICAL_STRUCTURAL }
        val sides = walls.map { w -> if (abs(w.plane.nx) > 0.99) "x" + (if (w.plane.centroid[0] < 2) 0 else 4) else "z" + (if (w.plane.centroid[2] < 1.5) 0 else 3) }.toSet()
        assertTrue(sides.size >= 3, "pareti strutturali trovate: $sides")
        val front = s.surfaces.first { it.orientation == Orientation.VERTICAL && abs(it.plane.nz) > 0.99 && abs(it.plane.centroid[2] - 1.1) < 0.03 }
        assertEquals(SurfaceKind.VERTICAL_OBJECT, front.kind, front.reasons.joinToString())
        assertFalse(assertNotNull(front.outermost))
        val top = s.surfaces.first { it.orientation == Orientation.HORIZONTAL_UP && abs(it.plane.centroid[1] - 0.8) < 0.02 }
        assertEquals(SurfaceKind.OBJECT, top.kind)
    }

    @Test
    fun `11 - determinismo - stessi dati, stesso risultato`() {
        val a = build(); val b = build()
        assertEquals(ReconDiagnostics.signature(a.first, a.second), ReconDiagnostics.signature(b.first, b.second))
        assertEquals(a.second.surfaces.map { Triple(it.kind, it.samples, it.plane.d) }, b.second.surfaces.map { Triple(it.kind, it.samples, it.plane.d) })
        assertEquals(ReconReport.csv(a.second), ReconReport.csv(b.second))
    }

    @Test
    fun `12 - depth non valida - scartata e contata, nessun valore inventato`() {
        val rec = room.recording(
            confidence = { _, _, v -> if (v < 3) 10 else 255 },
            invalid = { _, u, _ -> u < 4 },
        )
        val map = GlobalMap.build(rec.first, { rec.second[it] })
        val st = map.stats
        val pixels = room.width.toLong() * room.height * rec.first.depth.size
        assertEquals(pixels, st.pixels)
        assertEquals(st.pixels, st.accepted + st.rejectedZero + st.rejectedRange + st.rejectedConfidence + st.rejectedEdge)
        assertTrue(st.rejectedZero >= 4L * room.height * rec.first.depth.size)
        assertTrue(st.rejectedConfidence > 0)
        assertTrue(st.rejectedEdge > 0, "i bordi del mobile devono dare salti di profondità")
        assertEquals(st.accepted, map.points.size.toLong())
        // Nessun punto dalle colonne senza depth: proiettando i punti nella loro camera, nessuno cade in u < 4.
        for (i in 0 until map.points.size step 13) {
            val f = map.frames[map.points.frame[i]]
            val hit = assertNotNull(ArCameraProjection.project(f.pose, room.k, map.points.x[i].toDouble(), map.points.y[i].toDouble(), map.points.z[i].toDouble()))
            assertTrue(hit.u > 3.5 && hit.v > 2.5, "punto da un pixel non valido (${hit.u}, ${hit.v})")
        }
    }

    @Test
    fun `13 - spazio libero - evidenza attraverso la finestra, mai un'apertura`() {
        val r = SyntheticRoom(boxes = emptyList(), windows = listOf(doubleArrayOf(1.5, 1.0, 2.5, 2.0)))
        val rec = r.recording()
        val map = GlobalMap.build(rec.first, { rec.second[it] }, ReconParams(maxRangeM = 6.0))
        val fs = map.freeSpace
        assertEquals(1, fs.state(2.0, 1.5, 1.0), "davanti alla parete: libero")
        assertEquals(1, fs.state(2.0, 1.5, -0.6), "oltre la finestra: libero (i raggi ci sono passati)")
        assertEquals(0, fs.state(0.7, 1.5, -0.6), "dietro il muro pieno: ignoto")
        assertTrue(map.stats.raysTraced > 0 && map.stats.freeCells3d > 0)
        val s = SurfaceExtractor.extract(map, rec.first)
        assertTrue(s.surfaces.all { it.kind in SurfaceKind.entries })
        val res = ReconResult(emptyList(), map, s, emptyList(), ReconDiagnostics.floor(map, s, rec.first), emptyList(), emptyList(), ReconDiagnostics.signature(map, s))
        val text = ReconReport.text(res, "sintetica")
        assertTrue("non viene interpretato come apertura" in text)
        assertFalse(Regex("(?i)\\b(opening|apertura rilevata|finestra rilevata|porta rilevata)\\b").containsMatchIn(text))
    }

    @Test
    fun `rumore - con depth rumorosa le soglie si adattano e il pavimento resta una superficie`() {
        val noisy = SyntheticRoom(noiseM = 0.04)
        val (_, s, _) = build(noisy)
        assertTrue(s.noiseEstimateM > 0.005, "rumore stimato ${s.noiseEstimateM}")
        assertTrue(s.thresholds.growDistM > 0.03)
        val floors = s.surfaces.filter { it.kind == SurfaceKind.FLOOR }
        assertTrue(floors.isNotEmpty())
        for (f in floors) assertEquals(0.0, f.plane.centroid[1], 0.02)
        // Stessa area del caso senza rumore (pavimento diviso dall'ombra del mobile: ~2,4 + ~1,7 m²).
        assertTrue(floors.sumOf { it.areaM2 } > 3.5, "pavimento ${floors.sumOf { it.areaM2 }} m²")
        assertTrue(s.surfaces.count { it.kind == SurfaceKind.VERTICAL_STRUCTURAL } >= 4)
    }
}
