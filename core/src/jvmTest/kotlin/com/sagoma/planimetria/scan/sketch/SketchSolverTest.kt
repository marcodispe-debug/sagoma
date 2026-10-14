package com.sagoma.planimetria.scan.sketch

import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArXZ
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nucleo geometrico del percorso B su dati SINTETICI: verificano il comportamento del codice (regole, blocchi, origini), NON
 * l'accuratezza della scansione reale (per quella: `:core:realDataQuality`).
 */
class SketchSolverTest {

    // ---------- Scena sintetica ----------

    /** Stanza vera nel mondo AR: angoli del poligono (antiorario), ruotati di [ROT] e spostati. */
    private fun world(vararg pts: Pair<Double, Double>): List<ArXZ> = pts.map { (x, z) ->
        val r = Math.toRadians(ROT)
        ArXZ(x * cos(r) - z * sin(r) + 3.1, x * sin(r) + z * cos(r) - 1.7)
    }

    private val rect = world(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)

    /** La parete `k` della stanza vera vista dal `from` al `to` (frazioni della lunghezza), con il lato interno corretto. */
    private fun seen(room: List<ArXZ>, k: Int, from: Double = 0.15, to: Double = 0.85, sigmaOff: Double = 0.005, sigmaDir: Double = 0.5, flipInward: Boolean = false): ConfirmedLine {
        val a = room[k]; val b = room[(k + 1) % room.size]
        val d = (b - a).normalized()
        val inward = ArXZ(-d.z, d.x) * (if (flipInward) -1.0 else 1.0) // stanza antioraria: l'interno è a sinistra
        return ConfirmedLine(a + (b - a) * from, a + (b - a) * to, inward, sigmaOff, sigmaDir)
    }

    private fun sketchCm(vararg pts: Pair<Double, Double>) = RoomSketch.fromPlanInterior(pts.map { (x, y) -> Vec2(x, y) })

    /** Schizzo "a occhio": 5 × 2,4 m invece di 4 × 3 (lati sbagliati del 25 e del 20%). */
    private val roughSketch = sketchCm(0.0 to 0.0, 500.0 to 0.0, 500.0 to 240.0, 0.0 to 240.0)
    private val goodSketch = sketchCm(0.0 to 0.0, 410.0 to 0.0, 410.0 to 290.0, 0.0 to 290.0)

    private fun all(room: List<ArXZ>) = room.indices.associateWith { seen(room, it) }

    private fun codes(g: SketchGeometry) = g.issues.map { it.code }.toSet()

    /** Risponde a ogni domanda aperta con la prima risposta che il suo codice ammette (come farebbe un utente che conferma tutto). */
    private fun ackAll(input: SketchGeometryInput): SketchGeometryInput {
        val first = SketchSolver.solve(input)
        return input.copy(answers = input.answers + first.openQuestions.associate { it.key to it.code.allowedAnswers.first() })
    }

    // ---------- Casi validi ----------

    @Test
    fun `quattro pareti confermate danno la stanza vera anche con uno schizzo sbagliato`() {
        val g = SketchSolver.solve(SketchGeometryInput(roughSketch, all(rect)))
        val c = assertNotNull(g.corners)
        for (k in 0 until 4) assertEquals(0.0, c[k].distanceTo(rect[k]), 1e-9)
        assertEquals(listOf(4.0, 3.0, 4.0, 3.0), g.sides.map { Math.round(it.lengthM * 1e9) / 1e9 })
        assertTrue(g.sides.all { it.lengthSources == setOf(MeasureSource.SCAN_CONFIRMED) && !it.isEstimate })
        // Lo schizzo sbagliato fa DOMANDE (con entrambi i valori), non correzioni.
        val q = g.openQuestions.filter { it.code == IssueCode.SKETCH_LENGTH_MISMATCH }
        assertEquals(4, q.size)
        assertEquals(5.0, q.first { it.sides == listOf(0) }.values.getValue("schizzoM"), 1e-9)
        assertEquals(4.0, q.first { it.sides == listOf(0) }.values.getValue("misurataM"), 1e-9)
        assertFalse(g.canConfirm)
        val ok = SketchSolver.solve(ackAll(SketchGeometryInput(roughSketch, all(rect))))
        assertTrue(ok.canConfirm, "dopo le risposte: ${ok.issues}")
        assertEquals(ok.corners, g.corners) // rispondere non cambia la geometria
    }

    @Test
    fun `schizzo vicino al vero nessuna domanda e conferma possibile`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect)))
        assertTrue(g.canConfirm, "${g.issues}")
        assertEquals(ROT, g.rotationDeg!!, 1e-9)
        for (k in 0 until 4) assertEquals(90.0, g.cornerResults[k].interiorAngleDeg, 1e-9)
        assertEquals(12.0, g.areaM2!!, 1e-9)
    }

    @Test
    fun `le rette confermate e le misure a mano non vengono mai spostate`() {
        val input = SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(1 to 3.0))
        val before = input.copy()
        val g = SketchSolver.solve(input)
        assertEquals(before, input)
        val c = assertNotNull(g.corners)
        for ((k, l) in input.confirmed) {
            val d = (l.b - l.a).normalized()
            for (v in listOf(c[k], c[(k + 1) % 4])) assertEquals(0.0, kotlin.math.abs((v - l.a).cross(d)), 1e-9, "angolo fuori dalla retta del lato $k")
        }
        assertEquals(3.0, g.sides[1].lengthM, 1e-9) // la misura a mano è rispettata esattamente
    }

    @Test
    fun `stanza a L con sei pareti confermate`() {
        val l = world(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 3.0 to 2.0, 3.0 to 4.0, 0.0 to 4.0)
        val sk = sketchCm(0.0 to 0.0, 480.0 to 0.0, 480.0 to 220.0, 290.0 to 220.0, 290.0 to 410.0, 0.0 to 410.0)
        val g = SketchSolver.solve(SketchGeometryInput(sk, all(l)))
        assertTrue(g.canConfirm, "${g.issues}")
        for (k in 0 until 6) assertEquals(0.0, g.corners!![k].distanceTo(l[k]), 1e-9)
        assertEquals(270.0, g.cornerResults[3].interiorAngleDeg, 1e-9) // angolo rientrante
    }

    @Test
    fun `lato non scansionato fissato dalla lunghezza a mano di un lato vicino`() {
        val g = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(1 to 3.0))))
        assertTrue(g.canConfirm, "${g.issues}")
        for (k in 0 until 4) assertEquals(0.0, g.corners!![k].distanceTo(rect[k]), 1e-6)
        val s2 = g.sides[2]
        assertEquals(MeasureSource.SKETCH_ESTIMATE, s2.directionSource) // la direzione resta una stima
        assertTrue(MeasureSource.MANUAL in s2.lengthSources)
        assertTrue(s2.isEstimate)
        assertTrue(IssueCode.SKETCH_ESTIMATE_USED in codes(g))
    }

    @Test
    fun `la sola lunghezza del lato mancante non basta se i lati vicini sono paralleli`() {
        // Rettangolo, lato 2 non visto: sapere che è lungo 4 m non dice DOVE sta (può scorrere lungo i lati 1 e 3).
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(2 to 4.0)))
        assertEquals(IssueCode.UNDERDETERMINED, g.blocking.single().code, "${g.issues}")
        assertNull(g.corners)
    }

    @Test
    fun `lato non scansionato senza misure blocca e con la stima accettata diventa stima dichiarata`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) - 2))
        assertEquals(IssueCode.UNDERDETERMINED, g.blocking.single().code)
        assertNull(g.corners)
        val est = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) - 2, acceptedSketchLengths = setOf(1))))
        assertTrue(est.canConfirm, "${est.issues}")
        assertEquals(2.9, est.sides[1].lengthM, 1e-9) // la stima dello schizzo (2,90 m), dichiarata come tale
        assertTrue(MeasureSource.SKETCH_ESTIMATE in est.sides[1].lengthSources)
        // Tutti i lati il cui calcolo dipende dalla stima sono stime; il lato 0 (tra due rette confermate) no.
        assertTrue(est.sides[1].isEstimate && est.sides[2].isEstimate && est.sides[3].isEstimate)
        assertFalse(est.sides[0].isEstimate)
    }

    @Test
    fun `due lati non scansionati adiacenti servono due misure`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) - 1 - 2, manualLengthsM = mapOf(1 to 3.0)))
        assertEquals(1.0, g.blocking.single { it.code == IssueCode.UNDERDETERMINED }.values.getValue("misureMancanti"))
        val ok = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) - 1 - 2, manualLengthsM = mapOf(1 to 3.0, 2 to 4.0))))
        assertTrue(ok.canConfirm, "${ok.issues}")
        for (k in 0 until 4) assertEquals(0.0, ok.corners!![k].distanceTo(rect[k]), 1e-6)
    }

    @Test
    fun `una sola parete confermata e due misure a mano danno la forma, con la posizione per convenzione`() {
        val ok = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, mapOf(0 to seen(rect, 0)), manualLengthsM = mapOf(0 to 4.0, 1 to 3.0))))
        assertTrue(ok.canConfirm, "${ok.issues}")
        // La parete vista (dal 15 all'85%) è centrata sul lato: con lunghezza 4 m la stanza coincide con quella vera.
        for (k in 0 until 4) assertEquals(0.0, ok.corners!![k].distanceTo(rect[k]), 1e-6)
        // Una terza misura incoerente non viene mediata: blocca.
        val bad = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, mapOf(0 to seen(rect, 0)), manualLengthsM = mapOf(0 to 4.0, 1 to 3.0, 2 to 4.3))))
        assertTrue(bad.blocking.any { it.code == IssueCode.INCOMPATIBLE_MEASURES }, "${bad.issues}") // quale lato venga indicato dipende dalla priorità delle misure: conta che blocchi
        assertFalse(bad.canConfirm)
    }

    @Test
    fun `misura a mano coerente con le pareti confermate viene registrata come controllo`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect), manualLengthsM = mapOf(0 to 4.02)))
        assertTrue(g.canConfirm, "${g.issues}")
        assertEquals(4.0, g.sides[0].lengthM, 1e-9) // la retta comanda: nessuna media
        assertEquals(setOf(MeasureSource.SCAN_CONFIRMED, MeasureSource.MANUAL), g.sides[0].lengthSources)
    }

    // ---------- Casi patologici ----------

    @Test
    fun `misura a mano in contrasto con le pareti confermate blocca senza scegliere`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect), manualLengthsM = mapOf(0 to 3.9)))
        val i = g.blocking.single()
        assertEquals(IssueCode.MANUAL_SCAN_CONFLICT, i.code)
        assertEquals(4.0, i.values.getValue("calcolataM"), 1e-9); assertEquals(3.9, i.values.getValue("manoM"), 1e-9)
        assertEquals(4.0, g.sides[0].lengthM, 1e-9) // la geometria non è stata "aggiustata"
        assertFalse(g.canConfirm)
    }

    @Test
    fun `misure incompatibili su un lato non scansionato bloccano`() {
        // Lato 2 non visto: il lato 1 misurato a mano 3,5 m e il lato 3 misurato a mano 3,0 m non possono valere insieme.
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(1 to 3.5, 3 to 3.0)))
        val i = g.blocking.single { it.code == IssueCode.INCOMPATIBLE_MEASURES }
        assertEquals(listOf(3), i.sides) // la misura usata per il calcolo è la prima per priorità (lato 1); l'altra la contraddice
        assertEquals(0.5, kotlin.math.abs(i.values.getValue("calcolataM") - i.values.getValue("manoM")), 1e-9)
        assertFalse(g.canConfirm)
    }

    @Test
    fun `muro diagonale confermato al posto di un lato viene segnalato e la stanza non si conferma`() {
        // Una "parete" a 16° di traverso (come il falso piano di scan4) confermata come lato 1.
        val fakeDir = (rect[2] - rect[1]).normalized().rotatedDeg(-74.0)
        val fakeStart = rect[1] + (rect[2] - rect[1]) * 0.2
        val fake = ConfirmedLine(fakeStart, fakeStart + fakeDir * 2.3, ArXZ(-fakeDir.z, fakeDir.x), 0.005, 0.5)
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (1 to fake)))
        assertTrue(IssueCode.SKETCH_DIRECTION_MISMATCH in codes(g), "${g.issues}")
        assertFalse(g.canConfirm)
        // Rispondere alle domande non basta: la geometria che ne viene è comunque incoerente.
        val again = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) + (1 to fake))))
        assertFalse(again.canConfirm, "${again.issues}")
    }

    @Test
    fun `lati consecutivi paralleli o quasi bloccano`() {
        val para = ConfirmedLine(rect[1] + (rect[0] - rect[1]) * -0.1, rect[1] + (rect[0] - rect[1]) * -0.6, (rect[3] - rect[0]).normalized(), 0.005, 0.5)
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (1 to para)))
        assertTrue(g.blocking.any { it.code == IssueCode.PARALLEL_NO_INTERSECTION || it.code == IssueCode.INVALID_INPUT }, "${g.issues}")
        assertNull(g.corners)
        // Quasi parallelo: 12° tra lato 0 e lato 1.
        val tilt = seen(rect, 1).let { c -> val d = (rect[1] - rect[0]).normalized().rotatedDeg(12.0); ConfirmedLine(rect[1], rect[1] + d * 2.0, ArXZ(-d.z, d.x), 0.005, 0.5) }
        val q = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (1 to tilt)))
        assertTrue(q.blocking.any { it.code == IssueCode.NEARLY_PARALLEL }, "${q.issues}")
        assertNull(q.corners)
    }

    @Test
    fun `angolo instabile per pareti viste lontano dall'angolo con direzione incerta`() {
        // Pareti viste solo in un tratto corto lontano dall'angolo, con incertezza di direzione alta.
        val walls = all(rect) + (0 to seen(rect, 0, 0.45, 0.55, sigmaDir = 5.0)) + (1 to seen(rect, 1, 0.45, 0.55, sigmaDir = 5.0))
        val g = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, walls)))
        assertTrue(g.blocking.any { it.code == IssueCode.VERTEX_UNSTABLE }, "${g.issues}")
        assertFalse(g.canConfirm)
        // Incertezza moderata: solo avviso.
        val warn = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) + (0 to seen(rect, 0, 0.3, 0.7, sigmaDir = 4.0)))))
        assertTrue(IssueCode.VERTEX_UNCERTAIN in codes(warn), "${warn.issues}")
        assertTrue(warn.canConfirm, "${warn.issues}")
    }

    @Test
    fun `parte vista oltre l'angolo blocca`() {
        // La parete 0 vista fino a 30 cm oltre l'angolo 1 (non può essere: le pareti non si incontrano lì).
        val over = seen(rect, 0, 0.1, 1.075)
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (0 to over)))
        assertTrue(g.blocking.any { it.code == IssueCode.OBSERVED_BEYOND_CORNER && it.sides == listOf(0) }, "${g.issues}")
    }

    @Test
    fun `prolungamento lungo chiede se la parete continua e la risposta lo risolve`() {
        val short = all(rect) + (0 to seen(rect, 0, 0.05, 0.40)) // l'angolo 1 cade 2,4 m oltre la parte vista
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, short))
        val q = g.openQuestions.single { it.code == IssueCode.WALL_CONTINUES }
        assertEquals(2.4, q.values.getValue("prolungamentoM"), 1e-9)
        val ok = SketchSolver.solve(SketchGeometryInput(goodSketch, short, answers = mapOf(q.key to Answer.WALL_CONTINUES_BEHIND)))
        assertTrue(ok.canConfirm, "${ok.issues}")
        assertEquals(g.corners, ok.corners)
    }

    @Test
    fun `lato interno sbagliato su una parete confermata blocca`() {
        // Lato interno rovesciato = parete che va al contrario dello schizzo (180°): conflitto bloccante, nessuna pianta.
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (2 to seen(rect, 2, flipInward = true))))
        assertEquals(180.0, g.blocking.single { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT && it.sides == listOf(2) }.values.getValue("scartoDeg"), 1e-9, "${g.issues}")
        assertNull(g.corners)
        assertFalse(g.canConfirm)
    }

    @Test
    fun `parete messa dal lato sbagliato rovescia un lato e incrocia il perimetro`() {
        // Lato 3 confermato 5 m più in là (oltre il lato 1): il poligono si rovescia.
        val shift = (rect[1] - rect[0]).normalized() * 5.0
        val w = seen(rect, 3).let { ConfirmedLine(it.a + shift, it.b + shift, it.inward, 0.005, 0.5) }
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (3 to w)))
        val c = codes(g)
        assertTrue(IssueCode.SIDE_REVERSED in c || IssueCode.SELF_INTERSECTION in c || IssueCode.WRONG_ORIENTATION in c, "${g.issues}")
        assertFalse(g.canConfirm)
    }

    @Test
    fun `vertice fuori scala e lato degenere bloccano`() {
        val far = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(1 to 80.0))))
        assertTrue(far.blocking.any { it.code == IssueCode.VERTEX_OUT_OF_SCALE }, "${far.issues}")
        val tiny = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, all(rect) - 2, manualLengthsM = mapOf(1 to 0.05))))
        assertTrue(tiny.blocking.any { it.code == IssueCode.DEGENERATE_SIDE || it.code == IssueCode.OBSERVED_BEYOND_CORNER }, "${tiny.issues}")
        assertFalse(tiny.canConfirm)
    }

    @Test
    fun `la stessa parete confermata per due lati blocca`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (2 to seen(rect, 0).let { ConfirmedLine(it.b, it.a, it.inward, 0.005, 0.5) })))
        assertEquals(listOf(0, 2), g.blocking.single { it.code == IssueCode.DUPLICATE_WALL }.sides, "${g.issues}")
        assertNull(g.corners)
        assertFalse(g.canConfirm)
    }

    @Test
    fun `schizzo speculare blocca finche non si corregge lo schizzo`() {
        val l = world(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 3.0 to 2.0, 3.0 to 4.0, 0.0 to 4.0)
        // Lo stesso schizzo a L ma specchiato (la rientranza dall'altra parte), con i lati nello stesso ordine del giro.
        val mirrored = sketchCm(0.0 to 0.0, 500.0 to 0.0, 500.0 to 400.0, 200.0 to 400.0, 200.0 to 200.0, 0.0 to 200.0)
        val g = SketchSolver.solve(SketchGeometryInput(mirrored, all(l)))
        val conflict = g.blocking.filter { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT }
        assertTrue(conflict.isNotEmpty(), "${g.issues}")
        assertTrue(conflict.all { it.values.getValue("scartoDeg") > 90.0 })
        assertNull(g.corners) // nessuna pianta "corretta" in automatico
        // Rispondere non basta: il blocco non ammette risposte, che vengono riportate come rifiutate.
        val answered = SketchSolver.solve(SketchGeometryInput(mirrored, all(l), answers = conflict.associate { it.key to Answer.KEEP_MEASURED }))
        assertFalse(answered.canConfirm)
        assertEquals(conflict.map { it.key }.toSet(), answered.rejectedAnswers.map { it.key }.toSet())
        // Correggere lo schizzo risolve; le pareti confermate sono le stesse e la pianta è quella delle loro rette.
        val fixed = sketchCm(0.0 to 0.0, 490.0 to 0.0, 490.0 to 200.0, 300.0 to 200.0, 300.0 to 390.0, 0.0 to 390.0)
        val ok = SketchSolver.solve(SketchGeometryInput(fixed, all(l)))
        assertTrue(ok.canConfirm, "${ok.issues}")
        for (k in 0 until 6) assertEquals(0.0, ok.corners!![k].distanceTo(l[k]), 1e-9)
    }

    @Test
    fun `associazione sbagliata di una sola parete blocca e correggerla risolve`() {
        // Parete 1 confermata sul lato 3 e viceversa: direzioni opposte a quelle dello schizzo → conflitto bloccante.
        val swapped = all(rect) + (1 to seen(rect, 3)) + (3 to seen(rect, 1))
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, swapped))
        assertTrue(g.blocking.any { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT }, "${g.issues}")
        assertFalse(SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, swapped))).canConfirm)
        assertTrue(SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect))).canConfirm)
    }

    @Test
    fun `LIMITE NOTO - pareti associate tutte spostate di un lato in un rettangolo danno solo domande di lunghezza`() {
        // Lato k ← parete k+1 per TUTTE le pareti: equivale a uno schizzo ruotato di 90°. Le rette si incrociano nella stanza vera,
        // le direzioni relative sono coerenti: il nucleo NON può accorgersene dalla geometria. Lo segnalano solo le lunghezze
        // diverse dallo schizzo (domande); se l'utente risponde "la misura è giusta", la stanza si conferma con i lati
        // numerati diversamente dallo schizzo (porte e finestre finirebbero sul muro sbagliato). Da gestire nella sessione
        // (parete di partenza confermata dall'utente, proposta di riassegnazione) — qui lo si documenta, non lo si nasconde.
        val shifted = (0 until 4).associate { k -> k to seen(rect, (k + 1) % 4) }
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, shifted))
        assertTrue(g.blocking.isEmpty(), "${g.issues}")
        assertEquals(4, g.openQuestions.count { it.code == IssueCode.SKETCH_LENGTH_MISMATCH })
        assertTrue(SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, shifted))).canConfirm)
    }

    @Test
    fun `nessuna parete confermata, dati non validi, schizzo non valido`() {
        assertEquals(IssueCode.NO_CONFIRMED_WALL, SketchSolver.solve(SketchGeometryInput(goodSketch)).blocking.single().code)
        val nan = seen(rect, 0).copy(sigmaOffsetM = Double.NaN)
        assertEquals(IssueCode.INVALID_INPUT, SketchSolver.solve(SketchGeometryInput(goodSketch, mapOf(0 to nan))).blocking.single().code)
        assertEquals(IssueCode.INVALID_INPUT, SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect), manualLengthsM = mapOf(1 to -2.0))).blocking.single().code)
        assertEquals(IssueCode.INVALID_INPUT, SketchSolver.solve(SketchGeometryInput(goodSketch, mapOf(7 to seen(rect, 0)))).blocking.single().code)
        val bow = sketchCm(0.0 to 0.0, 400.0 to 300.0, 400.0 to 0.0, 0.0 to 300.0)
        assertEquals(IssueCode.SKETCH_INVALID, SketchSolver.solve(SketchGeometryInput(bow, all(rect))).blocking.single().code)
    }

    @Test
    fun `sistema lineare singolare non da una soluzione`() {
        assertNull(SketchSolver.solveLinear(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)), doubleArrayOf(1.0, 2.0), 1e-9))
        val x = assertNotNull(SketchSolver.solveLinear(arrayOf(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, 3.0)), doubleArrayOf(3.0, 5.0), 1e-9))
        assertEquals(0.8, x[0], 1e-12); assertEquals(1.4, x[1], 1e-12)
    }

    // ---------- Differenze di angolo ----------

    @Test
    fun `differenze di angolo senza salti a 0 180 e 360 gradi`() {
        assertEquals(2.0, SketchAngles.lineDeltaDeg(179.0, 1.0), 1e-12)
        assertEquals(0.0, SketchAngles.lineDeltaDeg(0.0, 180.0), 1e-12)
        assertEquals(90.0, SketchAngles.lineDeltaDeg(45.0, 135.0), 1e-12)
        assertEquals(2.0, SketchAngles.angleDeltaDeg(359.0, 1.0), 1e-12)
        assertEquals(0.0, SketchAngles.angleDeltaDeg(0.0, 360.0), 1e-12)
        assertEquals(180.0, SketchAngles.angleDeltaDeg(90.0, 270.0), 1e-12)
        assertEquals(10.0, SketchAngles.angleDeltaDeg(-175.0, 175.0), 1e-12)
        assertEquals(180.0, SketchAngles.wrap180(-180.0), 1e-12)
    }

    // ---------- Limiti noti (documentano, non nascondono) ----------

    @Test
    fun `LIMITE NOTO - un errore di direzione su una parete confermata si propaga alla pianta senza correzione`() {
        // 2° di errore sulla parete 1 (come su scan4 per una parete vista per poco): il nucleo NON lo corregge, per scelta.
        val bad = seen(rect, 1).let { c -> val m = (c.a + c.b) * 0.5; val d = (c.b - c.a).normalized().rotatedDeg(2.0); ConfirmedLine(m - d * 1.0, m + d * 1.0, c.inward, 0.005, 0.5) }
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (1 to bad)))
        assertTrue(g.canConfirm, "${g.issues}") // 2° stanno sotto le soglie: la pianta passa con l'errore dentro
        val worst = (0 until 4).maxOf { kotlin.math.abs(g.cornerResults[it].interiorAngleDeg - 90.0) }
        assertEquals(2.0, worst, 1e-6)
    }

    // ---------- Risposte tipizzate ----------

    @Test
    fun `risposta valida risolve la domanda e resta registrata`() {
        val short = all(rect) + (0 to seen(rect, 0, 0.05, 0.40))
        val q = SketchSolver.solve(SketchGeometryInput(goodSketch, short)).openQuestions.single { it.code == IssueCode.WALL_CONTINUES }
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, short, answers = mapOf(q.key to Answer.WALL_CONTINUES_BEHIND)))
        val issue = g.issues.single { it.key == q.key }
        assertEquals(Answer.WALL_CONTINUES_BEHIND, issue.answer) // la decisione è nel risultato, non cancellata
        assertTrue(g.canConfirm)
        assertTrue(g.rejectedAnswers.isEmpty())
    }

    @Test
    fun `risposta mancante lascia la domanda aperta`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (0 to seen(rect, 0, 0.05, 0.40))))
        assertTrue(g.openQuestions.any { it.code == IssueCode.WALL_CONTINUES && it.answer == null })
        assertFalse(g.canConfirm)
    }

    @Test
    fun `risposta non applicabile non risolve e viene riportata`() {
        val short = all(rect) + (0 to seen(rect, 0, 0.05, 0.40))
        val q = SketchSolver.solve(SketchGeometryInput(goodSketch, short)).openQuestions.single { it.code == IssueCode.WALL_CONTINUES }
        // "La misura è giusta" non è una risposta a "la parete continua dietro il mobile?".
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, short, answers = mapOf(q.key to Answer.KEEP_MEASURED)))
        assertTrue(g.issues.single { it.key == q.key }.isOpenQuestion)
        assertFalse(g.canConfirm)
        assertEquals(RejectionReason.NOT_ALLOWED, g.rejectedAnswers.single { it.key == q.key }.reason)
    }

    @Test
    fun `una risposta non elimina un blocco geometrico`() {
        val input = SketchGeometryInput(goodSketch, all(rect), manualLengthsM = mapOf(0 to 3.9))
        val block = SketchSolver.solve(input).blocking.single { it.code == IssueCode.MANUAL_SCAN_CONFLICT }
        val g = SketchSolver.solve(input.copy(answers = mapOf(block.key to Answer.KEEP_MEASURED)))
        assertTrue(g.blocking.any { it.key == block.key && it.answer == null })
        assertFalse(g.canConfirm)
        assertEquals(RejectionReason.NEEDS_DATA_CHANGE, g.rejectedAnswers.single().reason)
        // Lo risolve solo togliere o correggere la misura a mano.
        assertTrue(SketchSolver.solve(input.copy(manualLengthsM = mapOf(0 to 4.0))).canConfirm)
    }

    @Test
    fun `risposta a un problema che non c'e viene riportata`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect), answers = mapOf("WALL_CONTINUES[3]" to Answer.WALL_CONTINUES_BEHIND)))
        assertTrue(g.canConfirm)
        assertEquals(RejectionReason.NO_SUCH_ISSUE, g.rejectedAnswers.single().reason)
    }

    @Test
    fun `angolo lontano dallo schizzo mostra entrambe le posizioni e la risposta non sposta niente`() {
        // Schizzo 12 × 3 m per una stanza 4 × 3: gli angoli previsti stanno a 4 m da quelli calcolati (limite 30% di 12,4 m = 3,7 m).
        val wide = sketchCm(0.0 to 0.0, 1200.0 to 0.0, 1200.0 to 300.0, 0.0 to 300.0)
        val g = SketchSolver.solve(SketchGeometryInput(wide, all(rect)))
        val far = g.openQuestions.filter { it.code == IssueCode.SKETCH_VERTEX_FAR }
        assertEquals(4, far.size, "${g.issues}")
        for (q in far) {
            for (k in listOf("schizzoX", "schizzoZ", "calcolatoX", "calcolatoZ", "distanzaM", "limiteM")) assertTrue(k in q.values, "manca $k")
            val d = ArXZ(q.values.getValue("schizzoX"), q.values.getValue("schizzoZ")).distanceTo(ArXZ(q.values.getValue("calcolatoX"), q.values.getValue("calcolatoZ")))
            assertEquals(q.values.getValue("distanzaM"), d, 1e-9)
            assertTrue(d > q.values.getValue("limiteM"))
        }
        assertEquals(kotlin.math.max(0.5, 0.3 * kotlin.math.hypot(12.0, 3.0)), far.first().values.getValue("limiteM"), 1e-9)
        val answered = SketchSolver.solve(ackAll(SketchGeometryInput(wide, all(rect))))
        assertTrue(answered.canConfirm, "${answered.issues}")
        assertEquals(g.corners, answered.corners)
        assertTrue(answered.issues.filter { it.code == IssueCode.SKETCH_VERTEX_FAR }.all { it.answer == Answer.KEEP_MEASURED })
    }

    // ---------- Posizione per convenzione ----------

    @Test
    fun `una sola parete confermata dichiara la posizione per convenzione e non da un prolungamento`() {
        val g = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, mapOf(0 to seen(rect, 0)), manualLengthsM = mapOf(0 to 4.0, 1 to 3.0))))
        assertTrue(g.canConfirm, "${g.issues}")
        assertEquals(listOf(0), g.warnings.single { it.code == IssueCode.POSITION_BY_CONVENTION }.sides)
        assertEquals(listOf(true, true, false, false), g.cornerResults.map { it.positionByConvention })
        assertNull(g.sides[0].prolongationM)
        assertTrue(g.issues.none { it.code == IssueCode.WALL_CONTINUES && it.sides == listOf(0) })
        // Con due pareti confermate non c'è nessuna convenzione.
        val two = SketchSolver.solve(ackAll(SketchGeometryInput(goodSketch, mapOf(0 to seen(rect, 0), 1 to seen(rect, 1)), manualLengthsM = mapOf(2 to 4.0, 3 to 3.0))))
        assertTrue(two.issues.none { it.code == IssueCode.POSITION_BY_CONVENTION }, "${two.issues}")
        assertTrue(two.cornerResults.none { it.positionByConvention })
        assertNotNull(two.sides[0].prolongationM)
    }

    // ---------- Verso delle pareti: dato fisico, non convenzione ----------

    @Test
    fun `parete rappresentata nel verso opposto da lo stesso risultato`() {
        // Gli estremi a e b scambiati (la convenzione del tratto) non cambiano niente: il verso viene dal lato interno.
        val flipped = all(rect).mapValues { (_, c) -> ConfirmedLine(c.b, c.a, c.inward, c.sigmaOffsetM, c.sigmaDirectionDeg) }
        val g1 = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect)))
        val g2 = SketchSolver.solve(SketchGeometryInput(goodSketch, flipped))
        assertTrue(g2.canConfirm, "${g2.issues}")
        for (k in 0 until 4) assertEquals(g1.sides[k].lengthM, g2.sides[k].lengthM, 1e-12)
        for (k in 0 until 4) assertEquals(0.0, g1.corners!![k].distanceTo(g2.corners!![k]), 1e-12)
        assertTrue(g2.issues.none { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT })
    }

    @Test
    fun `stanza ruotata in qualunque direzione non crea conflitti di verso`() {
        for (deg in listOf(0.0, 89.0, 90.0, 179.0, 180.0, 181.0, 270.0, -135.0)) {
            val r = Math.toRadians(deg)
            val room = listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0).map { (x, z) -> ArXZ(x * cos(r) - z * sin(r) - 2.0, x * sin(r) + z * cos(r) + 5.0) }
            val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(room)))
            assertTrue(g.canConfirm, "rotazione $deg: ${g.issues}")
            assertEquals(listOf(4.0, 3.0, 4.0, 3.0), g.sides.map { Math.round(it.lengthM * 1e9) / 1e9 }, "rotazione $deg")
        }
    }

    @Test
    fun `lato interno di convenzione non genera un falso conflitto`() {
        // Parete 2 con il lato interno NON noto e la normale di convenzione puntata fuori: nessun conflitto, solo un avviso.
        val unknown = seen(rect, 2, flipInward = true).copy(inwardKnown = false)
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (2 to unknown)))
        assertTrue(g.canConfirm, "${g.issues}")
        assertTrue(g.issues.none { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT || it.code == IssueCode.INWARD_MISMATCH })
        assertEquals(listOf(2), g.warnings.single { it.code == IssueCode.INWARD_UNKNOWN }.sides)
        for (k in 0 until 4) assertEquals(0.0, g.corners!![k].distanceTo(rect[k]), 1e-9)
        // Con il lato interno NOTO la stessa normale rovesciata è un vero conflitto.
        val known = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect) + (2 to seen(rect, 2, flipInward = true))))
        assertTrue(known.blocking.any { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT })
    }

    @Test
    fun `nessuna parete con lato interno noto blocca invece di inventare il verso`() {
        val g = SketchSolver.solve(SketchGeometryInput(goodSketch, all(rect).mapValues { it.value.copy(inwardKnown = false) }))
        assertEquals(IssueCode.INWARD_UNKNOWN_ALL, g.blocking.single().code)
        assertNull(g.corners)
    }

    @Test
    fun `schizzo speculare resta un conflitto anche con la stanza ruotata`() {
        val l = world(0.0 to 0.0, 5.0 to 0.0, 5.0 to 2.0, 3.0 to 2.0, 3.0 to 4.0, 0.0 to 4.0)
        val mirrored = sketchCm(0.0 to 0.0, 500.0 to 0.0, 500.0 to 400.0, 200.0 to 400.0, 200.0 to 200.0, 0.0 to 200.0)
        // Associazione originale: verso opposto su alcune pareti → conflitto bloccante.
        assertTrue(SketchSolver.solve(SketchGeometryInput(mirrored, all(l))).blocking.any { it.code == IssueCode.SKETCH_DIRECTION_CONFLICT })
        for (shift in 1 until 6) {
            // Pareti riassociate (spostate di `shift` lati): o blocca, o 'è una L con proporzioni diverse dallo schizzo e lo dicono
            // SOLO le domande di lunghezza. In nessun caso si conferma senza una risposta dell'utente.
            val moved = all(l).entries.associate { (k, v) -> ((k + shift) % 6) to v }
            val g = SketchSolver.solve(SketchGeometryInput(mirrored, moved))
            assertFalse(g.canConfirm, "spostamento $shift: ${g.issues}")
            assertTrue(g.blocking.isNotEmpty() || g.openQuestions.any { it.code == IssueCode.SKETCH_LENGTH_MISMATCH }, "spostamento $shift: ${g.issues}")
        }
    }

    private companion object {
        const val ROT = 37.0
    }
}
