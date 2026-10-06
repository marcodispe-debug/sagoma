package com.sagoma.planimetria.scan.recording

import com.sagoma.planimetria.scan.WallScan
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanRecordingTest {
    private val header = RecordingHeader(
        createdAtMillis = 1_700_000_000_000,
        recordIntervalMs = 100,
        device = DeviceInfo("Google", "Pixel 8", 34),
        arcore = ArCoreInfo("1.44.0", "1.44.240000"),
        screen = ScreenInfo(1080, 2400, 0, 420, 640, 480),
        session = SessionInfo("HORIZONTAL_AND_VERTICAL", "LATEST_CAMERA_IMAGE", "AUTO", "DISABLED", "DISABLED", depthSupported = true),
    )

    private val origin = RecPose(0.0, 0.0, 0.0)

    /** Piano verticale 4 × 2,5 m sul muro z = −3: normale +z (verso la stanza). La posa ruota l'asse Y locale su +z (+90° attorno a x). */
    private val wallPose = RecPose(0.0, 1.0, -3.0, qx = kotlin.math.sqrt(0.5), qy = 0.0, qz = 0.0, qw = kotlin.math.sqrt(0.5))
    private val wallPolygon = listOf(-2.0, -1.25, 2.0, -1.25, 2.0, 1.25, -2.0, 1.25)

    private fun plane(
        key: Int, kind: String = "VERTICAL", tracking: String = "TRACKING", by: Int? = null,
        pose: RecPose = wallPose, polygon: List<Double> = wallPolygon,
    ) = RecordedPlane(key, kind, tracking, by, pose, listOf(0.0, 0.0, 1.0), 4.0, 2.5, polygon)

    private fun frame(
        i: Int, tracking: String = "TRACKING", reason: String? = null, camera: RecPose? = origin,
        planes: List<RecordedPlane> = emptyList(), points: RecordedPointCloud? = null, floorY: Double? = -1.2,
    ) = RecordedFrame(
        index = i, timestampNs = 1_000_000_000L + i * 100_000_000L, elapsedMs = i * 100L, tracking = tracking, failureReason = reason,
        camera = camera, cameraDisplay = camera, floorY = floorY, planes = planes, points = points,
    )

    private fun recording(vararg frames: RecordedFrame, end: Boolean = true) =
        ScanRecording(header, frames.toList(), if (end) RecordingEnd(frames = frames.size, durationMs = frames.size * 100L) else null)

    private fun roundTrip(r: ScanRecording) = ScanRecordingJson.decode(ScanRecordingJson.encode(r))

    // ---------- Serializzazione e formato ----------

    @Test
    fun `serializzazione e deserializzazione senza perdite`() {
        val points = RecordedPointCloud(5L, listOf(0.1, 0.2, 0.3, 1.0, 2.0, 3.0), listOf(0.9, 0.5), listOf(11, 12))
        val r = recording(frame(0), frame(1, planes = listOf(plane(1), plane(2, "HORIZONTAL_UPWARD_FACING")), points = points))
        assertEquals(r, roundTrip(r))
    }

    @Test
    fun `formato JSONL - una riga per elemento, intestazione per prima, riga finale`() {
        val text = ScanRecordingJson.encode(recording(frame(0), frame(1)))
        val lines = text.trimEnd('\n').split('\n')
        assertEquals(4, lines.size)
        assertTrue(lines[0].contains("\"type\":\"header\"") && lines[0].contains(RecordingFormat.NAME))
        assertTrue(lines[1].contains("\"type\":\"frame\"") && lines[2].contains("\"type\":\"frame\""))
        assertTrue(lines[3].contains("\"type\":\"end\""))
        assertTrue(lines.none { it.contains('\n') })
    }

    @Test
    fun `versione del formato - quella scritta, e le più nuove si rifiutano`() {
        val text = ScanRecordingJson.encode(recording(frame(0)))
        assertEquals(RecordingFormat.VERSION, ScanRecordingJson.decode(text).header.version)
        val future = text.replaceFirst("\"version\":${RecordingFormat.VERSION}", "\"version\":${RecordingFormat.VERSION + 1}")
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode(future) }
        // Altro formato, file vuoto, righe sconosciute.
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode("{\"type\":\"header\",\"format\":\"altro\",\"version\":1}") }
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode("") }
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode(text.replace("\"type\":\"frame\"", "\"type\":\"boh\"")) }
    }

    @Test
    fun `campi sconosciuti di versioni future compatibili si ignorano`() {
        val text = ScanRecordingJson.encode(recording(frame(0))).replace("\"type\":\"frame\"", "\"type\":\"frame\",\"nuovoCampo\":42")
        assertEquals(1, ScanRecordingJson.decode(text).frames.size)
    }

    @Test
    fun `un file interrotto a metà scrittura resta leggibile`() {
        val full = ScanRecordingJson.encode(recording(frame(0), frame(1), frame(2), end = false))
        val cut = full.substring(0, full.length - 40) // ultima riga tagliata
        val r = ScanRecordingJson.decode(cut)
        assertEquals(2, r.frames.size)
        assertNull(r.end)
        // Una riga rovinata in mezzo, invece, è un errore.
        val broken = full.lines().toMutableList().also { it[2] = "{rotto" }.joinToString("\n")
        assertFailsWith<RecordingFormatException> { ScanRecordingJson.decode(broken) }
    }

    // ---------- Coordinate ----------

    @Test
    fun `posa - rotazione e traslazione portano il locale nel mondo`() {
        // +90° attorno a x: l'asse Y locale (la normale) diventa +z, l'asse Z locale diventa −y.
        val n = wallPose.rotate(0.0, 1.0, 0.0)
        assertEquals(0.0, n.x, 1e-9); assertEquals(0.0, n.y, 1e-9); assertEquals(1.0, n.z, 1e-9)
        val z = wallPose.rotate(0.0, 0.0, 1.0)
        assertEquals(-1.0, z.y, 1e-9)
        // Punto locale (2, 0, 1,25) → mondo (2, 1 − 1,25, −3).
        val p = wallPose.transformPoint(2.0, 0.0, 1.25)
        assertEquals(2.0, p.x, 1e-9); assertEquals(-0.25, p.y, 1e-9); assertEquals(-3.0, p.z, 1e-9)
        // Posa identità e solo traslazione.
        assertEquals(1.0, RecPose(1.0, 2.0, 3.0).transformPoint(0.0, 0.0, 0.0).x, 0.0)
        // Rotazione di 90° attorno a y: x → −z.
        val h = sin(PI / 4); val c = cos(PI / 4)
        val r = RecPose(0.0, 0.0, 0.0, qy = h, qw = c).rotate(1.0, 0.0, 0.0)
        assertEquals(0.0, r.x, 1e-9); assertEquals(-1.0, r.z, 1e-9)
    }

    @Test
    fun `il piano nel mondo - poligono locale e normale restano distinti`() {
        val f = ScanReplay.frames(recording(frame(0, planes = listOf(plane(1))))).single()
        val p = f.planes.single()
        // Il locale è intatto, il mondo è derivato.
        assertEquals(4, p.polygonLocal.size)
        assertEquals(-2.0 to -1.25, p.polygonLocal.first())
        val ys = p.polygonWorld.map { it.y }
        assertEquals(-0.25, ys.min(), 1e-9); assertEquals(2.25, ys.max(), 1e-9)
        assertTrue(p.polygonWorld.all { kotlin.math.abs(it.z + 3.0) < 1e-9 }, "tutti sul muro z = −3")
        assertEquals(1.0, p.normal!!.z, 1e-9)
    }

    @Test
    fun `i valori numerici non vengono convertiti in coordinate di Sagoma`() {
        val r = roundTrip(recording(frame(0, camera = RecPose(1.5, 1.4, -2.25), planes = listOf(plane(1)))))
        val cam = r.frames.single().camera!!
        assertEquals(1.5, cam.x, 0.0); assertEquals(-2.25, cam.z, 0.0) // metri, non centimetri
        assertTrue(RecordingFormat.COORDINATES.contains("y verso l'ALTO"))
    }

    // ---------- Frame, tracking, piani ----------

    @Test
    fun `frame vuoti e registrazione senza frame`() {
        val r = roundTrip(recording(frame(0, camera = null, floorY = null), frame(1, camera = null, floorY = null)))
        val f = ScanReplay.frames(r)
        assertEquals(2, f.size)
        assertTrue(f.all { it.planes.isEmpty() && it.points == null && it.cameraPosition == null && it.speedMps == null })
        assertTrue(ScanReplay.frames(roundTrip(recording())).isEmpty())
    }

    @Test
    fun `il piano che appare, cambia poligono, viene assorbito e sparisce`() {
        val bigger = wallPolygon + listOf(2.5, 0.0)
        val frames = ScanReplay.frames(
            roundTrip(
                recording(
                    frame(0),
                    frame(1, planes = listOf(plane(7))),                              // appare
                    frame(2, planes = listOf(plane(7))),                              // uguale: nessun evento
                    frame(3, planes = listOf(plane(7, polygon = bigger))),            // poligono cambiato
                    frame(4, planes = listOf(plane(7, polygon = bigger), plane(8))),  // un secondo piano
                    frame(5, planes = listOf(plane(7, polygon = bigger, by = 8), plane(8))), // 7 assorbito da 8
                    frame(6, planes = listOf(plane(8))),                              // 7 sparisce
                ),
            ),
        )
        fun events(i: Int) = frames[i].events.filter { it !is ReplayEvent.TrackingChanged }
        assertEquals(emptyList(), events(0))
        assertEquals(listOf(ReplayEvent.PlaneAppeared(7, "VERTICAL")), events(1))
        assertEquals(emptyList(), events(2))
        assertEquals(listOf(ReplayEvent.PlaneChanged(7, polygonChanged = true, poseChanged = false)), events(3))
        assertEquals(listOf(ReplayEvent.PlaneAppeared(8, "VERTICAL")), events(4))
        assertEquals(listOf(ReplayEvent.PlaneSubsumed(7, 8)), events(5))
        assertEquals(listOf(ReplayEvent.PlaneDisappeared(7)), events(6))
        assertEquals(8, frames[5].planes.first { it.key == 7 }.subsumedBy)
    }

    @Test
    fun `il piano che si sposta (posa diversa) è un cambio di posa`() {
        val moved = wallPose.copy(z = -3.04)
        val f = ScanReplay.frames(recording(frame(0, planes = listOf(plane(1))), frame(1, planes = listOf(plane(1, pose = moved)))))
        assertTrue(f[1].events.contains(ReplayEvent.PlaneChanged(1, polygonChanged = false, poseChanged = true)))
    }

    @Test
    fun `tracking perso e recuperato, con il motivo`() {
        val f = ScanReplay.frames(
            roundTrip(
                recording(
                    frame(0), frame(1),
                    frame(2, tracking = "PAUSED", reason = "EXCESSIVE_MOTION", camera = null),
                    frame(3, tracking = "PAUSED", reason = "INSUFFICIENT_LIGHT", camera = null),
                    frame(4),
                ),
            ),
        )
        assertEquals(ReplayEvent.TrackingChanged(null, "TRACKING", null), f[0].events.first())
        assertFalse(f[1].events.any { it is ReplayEvent.TrackingChanged })
        assertEquals(ReplayEvent.TrackingChanged("TRACKING", "PAUSED", "EXCESSIVE_MOTION"), f[2].events.single())
        assertEquals("INSUFFICIENT_LIGHT", f[3].failureReason)
        assertFalse(f[3].events.any { it is ReplayEvent.TrackingChanged }) // sempre PAUSED
        assertEquals(ReplayEvent.TrackingChanged("PAUSED", "TRACKING", null), f[4].events.single())
        // Senza posa non c'è velocità.
        assertNull(f[2].speedMps); assertNull(f[4].speedMps)
    }

    @Test
    fun `velocità e spostamento dalla sequenza`() {
        // 0,1 s tra i frame: 5 cm → 0,5 m/s.
        val f = ScanReplay.frames(recording(frame(0, camera = RecPose(0.0, 1.5, 0.0)), frame(1, camera = RecPose(0.05, 1.5, 0.0)), frame(2, camera = RecPose(0.05, 1.5, 0.0))))
        assertNull(f[0].speedMps)
        assertEquals(0.5, f[1].speedMps!!, 1e-9)
        assertEquals(0.0, f[2].speedMps!!, 1e-9)
    }

    // ---------- Nuvola di punti ----------

    @Test
    fun `nuvola di punti assente, vuota o presente, con confidenza e identificatori`() {
        val cloud = RecordedPointCloud(42L, listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0), listOf(0.25, 0.75), listOf(100, 101))
        val f = ScanReplay.frames(
            roundTrip(recording(frame(0), frame(1, points = RecordedPointCloud(1L)), frame(2, points = cloud))),
        )
        assertNull(f[0].points)
        assertEquals(emptyList(), f[1].points)
        val pts = f[2].points!!
        assertEquals(2, pts.size)
        assertEquals(ReplayPoint(100, 0.0, 1.0, 2.0, 0.25), pts[0])
        assertEquals(ReplayPoint(101, 3.0, 4.0, 5.0, 0.75), pts[1])
        assertTrue(f[2].events.contains(ReplayEvent.PointCloudUpdated(2)))
        assertFalse(f[0].events.any { it is ReplayEvent.PointCloudUpdated })
        // Una nuvola incoerente (numeri di punti diversi) non si interpreta.
        assertFalse(RecordedPointCloud(1L, listOf(0.0, 0.0, 0.0), listOf(1.0, 1.0), listOf(1)).isConsistent)
        assertTrue(cloud.isConsistent)
    }

    // ---------- Registrazione intera e collegamento all'algoritmo ----------

    @Test
    fun `registrazione di molti frame resta ordinata e coerente`() {
        val frames = (0 until 200).map {
            frame(it, planes = if (it >= 10) listOf(plane(1)) else emptyList(), camera = RecPose(it * 0.01, 1.4, 0.0))
        }
        val r = roundTrip(ScanRecording(header, frames, RecordingEnd(frames = 200, durationMs = 20_000)))
        assertEquals(200, r.frames.size)
        assertEquals((0 until 200).toList(), r.frames.map { it.index })
        assertEquals(200, r.end!!.frames)
        val replay = ScanReplay.frames(r)
        assertEquals(1, replay.sumOf { f -> f.events.count { it is ReplayEvent.PlaneAppeared } })
        assertTrue(replay.drop(1).all { it.speedMps != null && it.speedMps!! > 0.0 })
        assertEquals(header, r.header)
    }

    @Test
    fun `dal recording all'algoritmo attuale senza telefono`() {
        // Un muro verticale visto in 5 frame diventa una parete di 4 m con le quote giuste.
        val r = recording(*(0 until 5).map { frame(it, planes = listOf(plane(1))) }.toTypedArray())
        var scan = WallScan()
        for (f in ScanReplay.frames(r)) scan = scan.update(ScanReplay.currentWallObservations(f))
        val wall = scan.walls().single()
        assertEquals(4.0, wall.length, 1e-6)
        assertEquals(-0.25, wall.bottomY, 1e-6)
        assertEquals(2.25, wall.topY, 1e-6)
        // Con il tracking perso non si producono osservazioni.
        assertEquals(emptyList(), ScanReplay.currentWallObservations(ScanReplay.frames(recording(frame(0, tracking = "PAUSED", planes = listOf(plane(1)))))[0]))
        // Un piano assorbito non conta.
        assertEquals(emptyList(), ScanReplay.currentWallObservations(ScanReplay.frames(recording(frame(0, planes = listOf(plane(1, by = 2)))))[0]))
        assertNotNull(ScanReplay.currentWallObservations(ScanReplay.frames(recording(frame(0, planes = listOf(plane(1)))))[0]).singleOrNull())
    }
}
