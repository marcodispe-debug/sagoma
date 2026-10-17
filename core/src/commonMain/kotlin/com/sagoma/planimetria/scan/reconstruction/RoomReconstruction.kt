package com.sagoma.planimetria.scan.reconstruction

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.recording.CaptureIssue
import com.sagoma.planimetria.scan.recording.CaptureValidation
import com.sagoma.planimetria.scan.recording.ScanRecording

/**
 * Room Reconstruction Engine — fase 1 (R1 + R2): dataset M0.2 → mappa 3D globale → superfici candidate con metriche.
 * Non ricostruisce ancora pareti, perimetro, aperture né `Room` (R3–R8).
 */
class ReconResult(
    val issues: List<CaptureIssue>,
    val map: GlobalMap,
    val surfaces: SurfaceResult,
    val residuals: List<ResidualStats>,
    val floorCheck: FloorCheck,
    val viewSpread: List<ViewSpread>,
    val stability: List<Pair<String, List<StabilityRow>>>,
    val signature: String,
    /** R3: pareti candidate (null se non calcolate). */
    val walls: WallEstimationResult? = null,
    /** R3: coerenza delle σ tra le ricostruzioni con i soli keyframe pari e dispari. */
    val wallConsistency: List<Pair<String, List<WallConsistency>>> = emptyList(),
    /** R4: perimetro calcolato sulle pareti R3 così come sono (null se non calcolato). */
    val perimeter: PerimeterResult? = null,
)

object RoomReconstruction {
    /**
     * [stabilityRuns]: rifà la ricostruzione con i soli depth keyframe pari e con i soli dispari e confronta le superfici (test di
     * stabilità a metà dati) e le pareti R3 (σ contro differenze reali). [files]: i percorsi presenti nel dataset, per la validazione
     * (null = non controllare i file). R3 usa le superfici di R1/R2 così come sono.
     */
    fun run(
        r: ScanRecording, blobs: DatasetBlobs, files: Set<String>? = null,
        p: ReconParams = ReconParams(), sp: SurfaceParams = SurfaceParams(), stabilityRuns: Boolean = true, wp: WallParams = WallParams(),
        pp: PerimeterParams = PerimeterParams(),
    ): ReconResult {
        val issues = CaptureValidation.validate(r, files)
        val map = GlobalMap.build(r, blobs, p)
        val surfaces = SurfaceExtractor.extract(map, r, sp)
        val walls = WallEstimator.estimate(WallEstimator.inputFrom(map, surfaces, r), wp)
        val perimeter = PerimeterSolver.solve(PerimeterSolver.inputFrom(map, surfaces, walls), pp)
        val halves = if (!stabilityRuns) emptyList() else listOf(0, 1).map { parity ->
            val halfMap = GlobalMap.build(r, blobs, p.copy(frameParity = parity))
            val half = SurfaceExtractor.extract(halfMap, r, sp)
            Triple(parity, half, WallEstimator.estimate(WallEstimator.inputFrom(halfMap, half, r), wp))
        }
        val stability = halves.map { (parity, half, _) -> (if (parity == 0) "solo keyframe pari" else "solo keyframe dispari") to ReconDiagnostics.stability(surfaces, half) }
        val consistency = if (halves.size == 2) listOf("pari ↔ dispari" to WallReport.consistency(halves[0].third, halves[1].third)) else emptyList()
        return ReconResult(
            issues, map, surfaces, ReconDiagnostics.arcoreVsDepth(r, blobs, p), ReconDiagnostics.floor(map, surfaces, r),
            ReconDiagnostics.viewCoherence(map, surfaces), stability, ReconDiagnostics.signature(map, surfaces), walls, consistency, perimeter,
        )
    }
}

/** Report testuale e CSV di R1/R2. Deterministici. */
object ReconReport {
    private fun f(v: Double, d: Int = 2) = formatDecimal(v, d, '.')
    private fun cm(v: Double) = f(v * 100, 1) + " cm"
    private fun pct(n: Long, of: Long) = if (of == 0L) "0%" else f(n * 100.0 / of, 1) + "%"

    fun text(res: ReconResult, title: String): String = buildString {
        fun line(s: String = "") = append(s).append('\n')
        val m = res.map
        val st = m.stats
        val s = res.surfaces
        line("=== Room Reconstruction R1/R2 — $title ===")
        line("Firma deterministica: ${res.signature}")
        line()
        line("-- Dataset")
        val errors = res.issues.count { it.severity == "error" }
        line("Validazione: $errors errori, ${res.issues.size - errors} avvisi" + if (errors > 0) " (ATTENZIONE: dataset con errori)" else "")
        for (i in res.issues.take(10)) line("  $i")
        line()
        line("-- R1 · Depth usate")
        line("Depth keyframe: ${st.depthKeyframes} · raw usate ${st.framesRaw} · depth filtrata di ripiego ${st.framesFallback} · saltati ${st.framesSkipped}")
        for ((reason, n) in st.skipReasons) line("  saltati: $n × $reason")
        val poseKinds = m.frames.groupBy { it.use }.map { "${it.key} ${it.value.size}" }
        line("Fonti: ${poseKinds.joinToString()} · gruppi di vista (osservazioni indipendenti): ${st.viewGroups}")
        line("Pixel: ${st.pixels} · accettati ${st.accepted} (${pct(st.accepted, st.pixels)}) · scartati: depth 0 ${st.rejectedZero}, fuori range ${st.rejectedRange}, confidenza bassa ${st.rejectedConfidence}, bordo/salto ${st.rejectedEdge}")
        line("Controllo somma: accettati + scartati = ${st.accepted + st.rejectedZero + st.rejectedRange + st.rejectedConfidence + st.rejectedEdge} su ${st.pixels} pixel")
        line()
        line("-- R1 · Mappa")
        line("Punti nel mondo (originali, non quantizzati): ${m.points.size}")
        line("Voxel di indicizzazione (${f(m.voxels.sizeM * 100, 0)} cm): ${st.voxels} · punti per voxel (mediana) ${f(st.pointsPerVoxelMedian, 0)}")
        line("Limiti: x ${f(st.boundsMin[0])}..${f(st.boundsMax[0])} · y ${f(st.boundsMin[1])}..${f(st.boundsMax[1])} · z ${f(st.boundsMin[2])}..${f(st.boundsMax[2])} m")
        line("Raggi percorsi per lo spazio libero: ${st.raysTraced} · celle 3D (${f(m.freeSpace.grid.cellM * 100, 0)} cm): libere ${st.freeCells3d}, occupate ${st.occupiedCells3d}")
        line("Pianta (${f(m.plan.grid.cellM * 100, 0)} cm): celle con punti ${st.planHitCells}, solo spazio libero ${st.planFreeOnlyCells}, area esplorata ${f(st.planExploredM2)} m²")
        line("Lo spazio libero è solo evidenza: in R1/R2 non viene interpretato come apertura.")
        line()
        line("-- Diagnosi: punti ARCore contro depth (NON sono la stessa osservazione fisica: test di coerenza, non di precisione)")
        for (r in res.residuals) line("  ${r.label}: ${r.samples} confronti in ${r.frames} frame · |Δ| mediana ${cm(r.medianAbsM)}, p90 ${cm(r.p90AbsM)} · entro 5 cm ${f(r.within5cm * 100, 1)}% · Δ mediano con segno ${cm(r.medianSignedM)}")
        line()
        line("-- R2 · Pavimento e soffitto")
        val fc = res.floorCheck
        line("Pavimento: ${s.floor?.let { "y = ${f(it.y, 3)} m (${it.source})" } ?: "non trovato"}" +
            (fc.tiltDeg?.let { " · inclinazione ${f(it)}° · RMS ${cm(fc.rmsM ?: 0.0)}" } ?: ""))
        line("Rispetto a floorY di ARCore (${fc.arcoreFloorY?.let { f(it, 3) } ?: "?"} m): ${fc.deltaVsArcore?.let { cm(it) } ?: "?"}")
        line("Deriva verticale (primo terzo → ultimo terzo del tempo): ${fc.drift?.let { cm(it) } ?: "non calcolabile"}" +
            (if (fc.earlyY != null && fc.lateY != null) " (${f(fc.earlyY, 3)} → ${f(fc.lateY, 3)} m)" else ""))
        line("Soffitto: ${s.ceilingY?.let { "y = ${f(it, 3)} m · altezza ${s.floor?.let { fl -> f(it - fl.y) } ?: "?"} m" } ?: "non visto"} · quota mediana delle camere ${f(s.cameraMedianY, 3)} m")
        line()
        val th = s.thresholds
        line("-- R2 · Rumore e soglie")
        line("Rumore stimato della depth (σ̂, mediana dello spessore dei vicinati di ~20 cm): ${cm(s.noiseEstimateM)}")
        line("Soglie usate: distanza di crescita ${cm(th.growDistM)} · angolo ${f(th.growAngleDeg, 0)}° · fusione ${cm(th.mergeDistM)} / ${f(th.mergeAngleDeg, 0)}° · variazione semi ≤ ${f(th.seedMaxVariation, 3)}, crescita ≤ ${f(th.growMaxVariation, 3)}")
        line()
        line("-- R2 · Superfici (${s.surfaces.size})")
        val byKind = s.surfaces.groupBy { it.kind }
        line("Per classe: " + SurfaceKind.entries.joinToString(" · ") { "$it ${byKind[it]?.size ?: 0}" })
        line("Voxel con normale ${s.voxelsWithNormal} su ${m.voxels.size} · assegnati a superfici ${s.voxelsAssigned} · punti assegnati ${pct(s.pointsAssigned, s.pointsTotal)}")
        line()
        line("id  classe               conf  orient.        area m²  lungh. m  quote dal pav. m   RMS mm  outl.%  cop.%  campioni  viste  ang.vista°  dist. m  dietro libero  esterna  ARCore")
        for (sf in s.surfaces) {
            line(
                "S${sf.id}".padEnd(4) + sf.kind.name.padEnd(21) + f(sf.kindConfidence).padEnd(6) + sf.orientation.name.padEnd(15) +
                    f(sf.areaM2).padStart(7) + f(sf.lengthM).padStart(10) + "  " + "${sf.bottomAboveFloor?.let { f(it) } ?: "?"}..${sf.topAboveFloor?.let { f(it) } ?: "?"}".padEnd(17) +
                    f(sf.rmsM * 1000, 1).padStart(6) + f(sf.outlierFraction * 100, 1).padStart(8) + f(sf.coverage * 100, 0).padStart(7) +
                    sf.samples.toString().padStart(10) + sf.effectiveFrames.toString().padStart(7) + f(sf.viewAngleSpanDeg, 0).padStart(12) +
                    f(sf.rangeMedianM).padStart(9) + (sf.freeBehind?.let { f(it * 100, 0) + "%" } ?: "—").padStart(15) +
                    (sf.outermost?.let { if (it) "sì" else "no" } ?: "—").padStart(9) + (sf.arcorePlane?.let { "  A$it" } ?: "  —"),
            )
        }
        line()
        line("Motivi della classificazione:")
        for (sf in s.surfaces) line("  S${sf.id} ${sf.kind}: " + sf.reasons.joinToString(" · "))
        line()
        line("-- Coerenza tra viste (superfici ≥ 0,5 m²: scarto medio dal piano di ogni gruppo di vista)")
        if (res.viewSpread.isEmpty()) line("  nessuna superficie con almeno 2 gruppi da 150 punti")
        for (v in res.viewSpread) line("  S${v.surfaceId}: ${v.groups} gruppi · deviazione standard ${cm(v.stdM)} · escursione ${cm(v.rangeM)}")
        line()
        line("-- Stabilità a metà dati (superfici ≥ 0,3 m² non ignote ritrovate nella ricostruzione con metà dei keyframe)")
        for ((label, rows) in res.stability) {
            val found = rows.count { it.otherId != null }
            val sameKind = rows.count { it.otherKind == it.kind }
            line("  $label: ritrovate $found/${rows.size} · stessa classe $sameKind/${rows.size}" +
                if (found > 0) " · angolo max ${f(rows.mapNotNull { it.angleDeg }.max())}° · scarto max ${cm(rows.mapNotNull { it.offsetM }.max())}" else "")
            for (r in rows.filter { it.otherId == null || it.otherKind != it.kind }) line("    S${r.surfaceId} ${r.kind}: " + (r.otherId?.let { "ritrovata come ${r.otherKind}" } ?: "non ritrovata"))
        }
        line()
        line("-- Confronto con i piani verticali ARCore (ultimo stato; NON sono verità, solo un riferimento)")
        line("Piani verticali ARCore con geometria: ${s.arcore.size} · con una superficie compatibile ${s.arcore.count { it.surface != null }}")
        for (a in s.arcore) line(
            "  A${a.key}: ${f(a.lengthM)} m, visto ${f(a.persistenceMs / 1000.0, 1)} s" + (if (a.subsumed) ", assorbito" else "") + " → " +
                (a.surface?.let { "S$it ${a.surfaceKind} (angolo ${f(a.angleDeg ?: 0.0, 1)}°, distanza ${cm(a.offsetM ?: 0.0)})" } ?: "nessuna superficie compatibile"),
        )
        val verticalWithoutAr = s.surfaces.filter { it.orientation == Orientation.VERTICAL && it.arcorePlane == null }
        line("Superfici verticali senza un piano ARCore compatibile: ${verticalWithoutAr.size}" + if (verticalWithoutAr.isNotEmpty()) " (" + verticalWithoutAr.joinToString { "S${it.id} ${it.kind}" } + ")" else "")
        res.walls?.let { w -> line(); append(WallReport.text(w, res.wallConsistency, s.arcore, s)) }
        res.perimeter?.let { pr -> line(); append(PerimeterReport.text(pr)) }
    }

    fun csv(s: SurfaceResult): String = buildString {
        append("id;kind;confidence;orientation;tiltDeg;nx;ny;nz;d;cx;cy;cz;areaM2;lengthM;heightM;uMin;uMax;vMin;vMax;yMin;yMax;bottomAboveFloor;topAboveFloor;")
        append("rmsM;outlierFraction;coverage;voxels;samples;frames;effectiveFrames;viewAngleSpanDeg;rangeMedianM;incidenceMedianDeg;freeBehind;outermost;arcorePlane;structuralScore\n")
        for (sf in s.surfaces) {
            val p = sf.plane
            append(
                listOf(
                    sf.id, sf.kind, f(sf.kindConfidence, 3), sf.orientation, f(sf.tiltDeg, 2), f(p.nx, 5), f(p.ny, 5), f(p.nz, 5), f(p.d, 5),
                    f(p.centroid[0], 4), f(p.centroid[1], 4), f(p.centroid[2], 4), f(sf.areaM2, 3), f(sf.lengthM, 3), f(sf.heightM, 3),
                    f(sf.uMin, 3), f(sf.uMax, 3), f(sf.vMin, 3), f(sf.vMax, 3), f(sf.yMin, 3), f(sf.yMax, 3),
                    sf.bottomAboveFloor?.let { f(it, 3) } ?: "", sf.topAboveFloor?.let { f(it, 3) } ?: "", f(sf.rmsM, 5), f(sf.outlierFraction, 4),
                    f(sf.coverage, 3), sf.voxels, sf.samples, sf.frames, sf.effectiveFrames, f(sf.viewAngleSpanDeg, 1), f(sf.rangeMedianM, 3),
                    f(sf.incidenceMedianDeg, 1), sf.freeBehind?.let { f(it, 3) } ?: "", sf.outermost ?: "", sf.arcorePlane ?: "", sf.structuralScore?.let { f(it, 3) } ?: "",
                ).joinToString(";"),
            ).append('\n')
        }
    }
}
