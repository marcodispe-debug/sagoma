package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.scan.recording.ArCameraProjection
import com.sagoma.planimetria.scan.recording.RgbKeyframe
import com.sagoma.planimetria.scan.recording.analysis.PlaneTrack
import kotlin.io.encoding.Base64
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Viste di R1/R2 per guardare cosa il motore ha ricostruito: pianta dall'alto, prospetti delle superfici verticali, overlay sulle
 * immagini RGB e nuvola PLY. Solo testo e byte (nessuna libreria grafica): SVG apribili nel browser, PLY in MeshLab/CloudCompare.
 */
object ReconVisuals {
    fun color(kind: SurfaceKind?): String = when (kind) {
        SurfaceKind.FLOOR -> "#43A047"
        SurfaceKind.CEILING -> "#8E24AA"
        SurfaceKind.VERTICAL_STRUCTURAL -> "#1565C0"
        SurfaceKind.VERTICAL_OBJECT -> "#FB8C00"
        SurfaceKind.OBJECT -> "#E53935"
        SurfaceKind.UNKNOWN -> "#757575"
        null -> "#BDBDBD"
    }

    private fun rgb(kind: SurfaceKind?): IntArray = color(kind).let { intArrayOf(it.substring(1, 3).toInt(16), it.substring(3, 5).toInt(16), it.substring(5, 7).toInt(16)) }
    private fun f(v: Double) = (kotlin.math.round(v * 10) / 10).toString()

    /** Pianta dall'alto: spazio libero visto, densità dei punti, superfici per classe, traiettoria, piani ARCore (tratteggiati). */
    fun topDown(map: GlobalMap, s: SurfaceResult, trajectory: List<DoubleArray>, arcore: List<PlaneTrack>, title: String, widthPx: Int = 1300): String {
        val g = map.plan.grid
        val scale = (widthPx - 40) / (g.nx * g.cellM)
        val heightPx = (g.nz * g.cellM * scale + 140).roundToInt()
        fun px(x: Double) = (x - g.minX) * scale + 20
        fun pz(z: Double) = (z - g.minZ) * scale + 60
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$widthPx" height="$heightPx" font-family="sans-serif" font-size="12">""")
        sb.append("""<rect width="100%" height="100%" fill="#FAFAFA"/><text x="20" y="22" font-size="15" font-weight="bold">${esc(title)}</text>""")
        sb.append("""<text x="20" y="42" fill="#555">Pianta dall'alto (mondo ARCore, x → destra, z → basso). Azzurro = spazio libero visto (evidenza, NON aperture) · grigio chiaro = solo pavimento · grigio scuro = punti in elevazione (più scuro = più denso) · colori = superfici.</text>""")
        val cell = g.cellM * scale
        // Punti "in elevazione" (oltre 15 cm sopra il pavimento): pareti e mobili; quelli a terra si disegnano chiari.
        val floorY = s.floor?.y
        val elevated = IntArray(g.cells)
        for (i in 0 until map.points.size) {
            if (floorY != null && map.points.y[i] <= floorY + 0.15) continue
            val ci = g.cx(map.points.x[i].toDouble()); val ck = g.cz(map.points.z[i].toDouble())
            if (g.inside(ci, 0, ck)) elevated[g.index(ci, 0, ck)]++
        }
        val maxHits = elevated.maxOrNull()?.coerceAtLeast(1) ?: 1
        fun level(c: Int): Int = when {
            elevated[c] > 0 -> 2 + min(3, (ln(elevated[c].toDouble()) / ln(maxHits.toDouble() + 1) * 4).toInt())
            map.plan.hits[c] > 0 -> 1
            map.plan.free[c] > 0 -> 0
            else -> -1
        }
        // Spazio libero, pavimento e densità in elevazione, a righe (run-length) per tenere il file piccolo.
        for (k in 0 until g.nz) {
            var i = 0
            while (i < g.nx) {
                val c = g.index(i, 0, k)
                val level = level(c)
                if (level < 0) { i++; continue }
                var j = i
                while (j + 1 < g.nx && level(g.index(j + 1, 0, k)) == level) j++
                val fill = when (level) {
                    0 -> "#DCEEFB"
                    1 -> "#E6E6E6"
                    else -> "rgb(${230 - (level - 1) * 45},${230 - (level - 1) * 45},${230 - (level - 1) * 45})"
                }
                sb.append("""<rect x="${f(px(g.minX + i * g.cellM))}" y="${f(pz(g.minZ + k * g.cellM))}" width="${f((j - i + 1) * cell + 0.3)}" height="${f(cell + 0.3)}" fill="$fill"/>""")
                i = j + 1
            }
        }
        // Superfici orizzontali e oggetti: impronta dei voxel; verticali: tratto sul piano.
        for (sf in s.surfaces.sortedBy { if (it.kind == SurfaceKind.CEILING) 0 else 1 }) {
            if (sf.orientation == Orientation.VERTICAL) continue
            val cells = HashSet<Long>()
            for (v in sf.memberVoxels) {
                val c = map.voxels.moments[v].centroid()
                cells.add((floor((c[0] - g.minX) / g.cellM).toLong() shl 32) or (floor((c[2] - g.minZ) / g.cellM).toLong() and 0xFFFFFFFFL))
            }
            val op = if (sf.kind == SurfaceKind.CEILING) 0.12 else 0.45
            sb.append("""<g fill="${color(sf.kind)}" fill-opacity="$op">""")
            for (key in cells.sorted()) {
                val ci = (key ushr 32).toInt(); val ck = (key and 0xFFFFFFFFL).toInt()
                sb.append("""<rect x="${f(px(g.minX + ci * g.cellM))}" y="${f(pz(g.minZ + ck * g.cellM))}" width="${f(cell + 0.3)}" height="${f(cell + 0.3)}"/>""")
            }
            sb.append("</g>")
        }
        for (t in arcore) {
            val geo = t.last ?: continue
            sb.append("""<line x1="${f(px(geo.a.x))}" y1="${f(pz(geo.a.z))}" x2="${f(px(geo.b.x))}" y2="${f(pz(geo.b.z))}" stroke="#212121" stroke-width="1.2" stroke-dasharray="5,4"/>""")
            sb.append("""<text x="${f(px(geo.centerX) + 4)}" y="${f(pz(geo.centerZ) - 4)}" fill="#212121" font-size="10">A${t.key}</text>""")
        }
        for (sf in s.surfaces.filter { it.orientation == Orientation.VERTICAL }) {
            val c = sf.plane.centroid
            val ax = c[0] + sf.u[0] * sf.uMin; val az = c[2] + sf.u[2] * sf.uMin
            val bx = c[0] + sf.u[0] * sf.uMax; val bz = c[2] + sf.u[2] * sf.uMax
            sb.append("""<line x1="${f(px(ax))}" y1="${f(pz(az))}" x2="${f(px(bx))}" y2="${f(pz(bz))}" stroke="${color(sf.kind)}" stroke-width="4" stroke-linecap="round"/>""")
            // Normale (verso le camere).
            val mx = (ax + bx) / 2; val mz = (az + bz) / 2
            sb.append("""<line x1="${f(px(mx))}" y1="${f(pz(mz))}" x2="${f(px(mx + sf.plane.nx * 0.2))}" y2="${f(pz(mz + sf.plane.nz * 0.2))}" stroke="${color(sf.kind)}" stroke-width="1.5"/>""")
            sb.append("""<text x="${f(px(mx) + 5)}" y="${f(pz(mz) + 12)}" fill="${color(sf.kind)}" font-weight="bold">S${sf.id}</text>""")
        }
        if (trajectory.size >= 2) {
            sb.append("""<polyline fill="none" stroke="#C62828" stroke-width="1.5" points="""")
            for (t in trajectory) sb.append("${f(px(t[0]))},${f(pz(t[2]))} ")
            sb.append(""""/>""")
            sb.append("""<circle cx="${f(px(trajectory.first()[0]))}" cy="${f(pz(trajectory.first()[2]))}" r="4" fill="#C62828"/>""")
        }
        // Scala e legenda.
        val y0 = heightPx - 50
        sb.append("""<line x1="20" y1="$y0" x2="${f(20 + scale)}" y2="$y0" stroke="#000" stroke-width="3"/><text x="20" y="${y0 + 16}">1 m</text>""")
        var lx = 120
        for (k in SurfaceKind.entries) {
            sb.append("""<rect x="$lx" y="${y0 - 10}" width="14" height="14" fill="${color(k)}"/><text x="${lx + 18}" y="${y0 + 2}">${k.name}</text>""")
            lx += 175
        }
        sb.append("""<text x="120" y="${y0 + 26}">Rosso = traiettoria della camera (pallino = inizio) · tratteggio nero = piani verticali ARCore (solo confronto) · S# = superficie, trattino = normale verso le camere</text>""")
        sb.append("</svg>")
        return sb.toString()
    }

    /** Prospetto di una superficie verticale: densità dei punti sul piano (celle da 5 cm), pavimento e soffitto, metriche e motivi. */
    fun elevation(map: GlobalMap, sf: Surface, floorY: Double?, ceilingY: Double?, cellM: Double = 0.05): String {
        val idx = SurfaceExtractor.pointsOf(map, sf.memberVoxels)
        val c = sf.plane.centroid
        val counts = HashMap<Long, Int>()
        for (i in idx) {
            val dx = map.points.x[i] - c[0]; val dy = map.points.y[i] - c[1]; val dz = map.points.z[i] - c[2]
            val uu = dx * sf.u[0] + dy * sf.u[1] + dz * sf.u[2]
            val yy = map.points.y[i].toDouble()
            val key = (floor(uu / cellM).toLong() shl 32) or (floor(yy / cellM).toLong() and 0xFFFFFFFFL)
            counts[key] = (counts[key] ?: 0) + 1
        }
        val y0 = floorY ?: sf.yMin
        val y1 = max(ceilingY ?: sf.yMax, sf.yMax)
        val u0 = sf.uMin - 0.3; val u1 = sf.uMax + 0.3
        val scale = min(900.0 / (u1 - u0), 500.0 / (y1 - y0 + 0.2))
        val w = ((u1 - u0) * scale + 60).roundToInt()
        val h = ((y1 - y0 + 0.2) * scale + 210).roundToInt()
        fun px(u: Double) = (u - u0) * scale + 30
        fun py(y: Double) = (y1 + 0.1 - y) * scale + 150
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" font-family="sans-serif" font-size="12"><rect width="100%" height="100%" fill="#FAFAFA"/>""")
        sb.append("""<text x="10" y="20" font-size="15" font-weight="bold">S${sf.id} · ${sf.kind} (confidenza ${f(sf.kindConfidence)})</text>""")
        val lines = listOf(
            "lunghezza ${f(sf.lengthM)} m · quote ${f(sf.yMin)}..${f(sf.yMax)} m · dal pavimento ${sf.bottomAboveFloor?.let { f(it) } ?: "?"}..${sf.topAboveFloor?.let { f(it) } ?: "?"} m",
            "RMS ${f(sf.rmsM * 1000)} mm · area ${f(sf.areaM2)} m² · copertura ${f(sf.coverage * 100)}% · campioni ${sf.samples} · viste ${sf.effectiveFrames} · angoli di vista ${f(sf.viewAngleSpanDeg)}°",
        ) + sf.reasons.take(4)
        for ((i, l) in lines.withIndex()) sb.append("""<text x="10" y="${40 + i * 16}">${esc(l)}</text>""")
        val maxC = counts.values.maxOrNull() ?: 1
        for ((key, n) in counts.entries.sortedBy { it.key }) {
            val cu = (key shr 32).toInt(); val cy = key.toInt()
            val shade = (230 - 200 * ln(n.toDouble() + 1) / ln(maxC.toDouble() + 1)).roundToInt()
            sb.append("""<rect x="${f(px(cu * cellM))}" y="${f(py((cy + 1) * cellM))}" width="${f(cellM * scale + 0.3)}" height="${f(cellM * scale + 0.3)}" fill="rgb($shade,$shade,${min(255, shade + 25)})"/>""")
        }
        sb.append("""<rect x="${f(px(sf.uMin))}" y="${f(py(sf.yMax))}" width="${f((sf.uMax - sf.uMin) * scale)}" height="${f((sf.yMax - sf.yMin) * scale)}" fill="none" stroke="${color(sf.kind)}" stroke-width="2"/>""")
        floorY?.let { sb.append("""<line x1="0" y1="${f(py(it))}" x2="$w" y2="${f(py(it))}" stroke="#43A047" stroke-width="2"/><text x="4" y="${f(py(it) - 4)}" fill="#43A047">pavimento</text>""") }
        ceilingY?.let { sb.append("""<line x1="0" y1="${f(py(it))}" x2="$w" y2="${f(py(it))}" stroke="#8E24AA" stroke-width="2"/><text x="4" y="${f(py(it) + 14)}" fill="#8E24AA">soffitto</text>""") }
        sb.append("""<line x1="30" y1="${h - 15}" x2="${f(30 + scale)}" y2="${h - 15}" stroke="#000" stroke-width="3"/><text x="${f(36 + scale)}" y="${h - 11}">1 m</text>""")
        sb.append("</svg>")
        return sb.toString()
    }

    /**
     * Overlay: il JPEG originale con sopra i voxel della mappa proiettati con la posa e le intrinseche dell'immagine RGB, colorati per
     * classe (il più vicino per blocco di 4 px). Ruotato per la visione come lo schermo (orientamento del sensore e dello schermo).
     */
    fun overlay(map: GlobalMap, s: SurfaceResult, rgb: RgbKeyframe, jpeg: ByteArray): String {
        val w = rgb.width; val h = rgb.height
        val block = 4
        val bw = (w + block - 1) / block; val bh = (h + block - 1) / block
        val depth = DoubleArray(bw * bh) { Double.MAX_VALUE }
        val owner = IntArray(bw * bh) { -1 }
        val us = DoubleArray(map.voxels.size); val vs = DoubleArray(map.voxels.size)
        for (v in 0 until map.voxels.size) {
            val c = map.voxels.moments[v].centroid()
            val hit = ArCameraProjection.project(rgb.camera, rgb.intrinsics, c[0], c[1], c[2]) ?: continue
            if (hit.depthM < 0.2 || hit.depthM > 6) continue
            if (hit.u < 0 || hit.v < 0 || hit.u >= w || hit.v >= h) continue
            val b = (hit.v / block).toInt() * bw + (hit.u / block).toInt()
            if (hit.depthM < depth[b]) { depth[b] = hit.depthM; owner[b] = v; us[v] = hit.u; vs[v] = hit.v }
        }
        val rot = (((rgb.sensorOrientationDeg ?: 0) - rgb.displayRotation * 90) % 360 + 360) % 360
        val (ow, oh) = if (rot == 90 || rot == 270) h to w else w to h
        val transform = when (rot) {
            90 -> "translate($h,0) rotate(90)"
            180 -> "translate($w,$h) rotate(180)"
            270 -> "translate(0,$w) rotate(270)"
            else -> ""
        }
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$ow" height="${oh + 44}" font-family="sans-serif" font-size="11">""")
        sb.append("""<rect width="100%" height="100%" fill="#FFF"/><g transform="$transform">""")
        sb.append("""<image href="data:image/jpeg;base64,${Base64.encode(jpeg)}" x="0" y="0" width="$w" height="$h"/>""")
        for (b in owner.indices) {
            val v = owner[b]
            if (v < 0) continue
            val kind = s.voxelSurface[v].let { if (it >= 0) s.surfaces[it].kind else null }
            sb.append("""<circle cx="${f(us[v])}" cy="${f(vs[v])}" r="1.7" fill="${color(kind)}" fill-opacity="0.75"/>""")
        }
        sb.append("</g>")
        sb.append("""<text x="4" y="${oh + 16}">RGB #${rgb.seq} (frame ${rgb.frameSeq}) · voxel della mappa proiettati con posa e intrinseche dell'immagine CPU</text>""")
        sb.append("""<text x="4" y="${oh + 32}">verde pavimento · viola soffitto · blu parete strutturale · arancio fronte di oggetto · rosso oggetto · grigio ignoto/non assegnato</text>""")
        sb.append("</svg>")
        return sb.toString()
    }

    /**
     * Nuvola PLY binaria (little-endian): tutti i punti accettati, nel mondo ARCore (metri, y in alto), colorati per classe; proprietà
     * `kind` (indice di [SurfaceKind], 255 = non assegnato), `source` (0 raw, 1 depth filtrata di ripiego), `frame`.
     */
    fun ply(map: GlobalMap, s: SurfaceResult): ByteArray {
        val n = map.points.size
        val kindOf = ByteArray(n) { 255.toByte() }
        for (sf in s.surfaces) for (i in SurfaceExtractor.pointsOf(map, sf.memberVoxels)) kindOf[i] = sf.kind.ordinal.toByte()
        val header = "ply\nformat binary_little_endian 1.0\ncomment Sagoma R1/R2: mondo ARCore, metri, y in alto\n" +
            "element vertex $n\nproperty float x\nproperty float y\nproperty float z\nproperty uchar red\nproperty uchar green\nproperty uchar blue\n" +
            "property uchar kind\nproperty uchar source\nproperty int frame\nend_header\n"
        val head = header.encodeToByteArray()
        val rec = 12 + 3 + 1 + 1 + 4
        val out = ByteArray(head.size + n * rec)
        head.copyInto(out)
        var o = head.size
        fun putInt(v: Int) { out[o++] = v.toByte(); out[o++] = (v shr 8).toByte(); out[o++] = (v shr 16).toByte(); out[o++] = (v shr 24).toByte() }
        val palette = Array(SurfaceKind.entries.size + 1) { if (it < SurfaceKind.entries.size) rgb(SurfaceKind.entries[it]) else rgb(null) }
        for (i in 0 until n) {
            putInt(map.points.x[i].toRawBits()); putInt(map.points.y[i].toRawBits()); putInt(map.points.z[i].toRawBits())
            val k = kindOf[i].toInt() and 0xFF
            val c = palette[if (k == 255) SurfaceKind.entries.size else k]
            out[o++] = c[0].toByte(); out[o++] = c[1].toByte(); out[o++] = c[2].toByte()
            out[o++] = kindOf[i]
            out[o++] = (if (map.frames[map.points.frame[i]].use == DepthUse.RAW) 0 else 1).toByte()
            putInt(map.frames[map.points.frame[i]].depthSeq)
        }
        return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
