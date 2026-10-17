package com.sagoma.planimetria.scan.reconstruction.rescan.session

import com.sagoma.planimetria.scan.reconstruction.EndReason
import com.sagoma.planimetria.scan.reconstruction.EndState
import com.sagoma.planimetria.scan.reconstruction.EstimatedWall
import com.sagoma.planimetria.scan.reconstruction.EvidenceQuality
import com.sagoma.planimetria.scan.reconstruction.GapReason
import com.sagoma.planimetria.scan.reconstruction.PerimeterState
import com.sagoma.planimetria.scan.reconstruction.ThicknessState
import com.sagoma.planimetria.scan.reconstruction.WallEnd
import com.sagoma.planimetria.scan.reconstruction.WallEvidence
import com.sagoma.planimetria.scan.reconstruction.WallGap
import com.sagoma.planimetria.scan.reconstruction.WallGeometry
import com.sagoma.planimetria.scan.reconstruction.WallSpan
import com.sagoma.planimetria.scan.reconstruction.WallThickness
import com.sagoma.planimetria.scan.reconstruction.WallUncertainty
import com.sagoma.planimetria.scan.reconstruction.quality.PerimeterFacts
import com.sagoma.planimetria.scan.reconstruction.quality.QualityEngine
import com.sagoma.planimetria.scan.reconstruction.rescan.PerimeterGeometry
import com.sagoma.planimetria.scan.reconstruction.rescan.RescanDirector
import com.sagoma.planimetria.scan.recording.PoseSample
import com.sagoma.planimetria.scan.recording.RecPose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M5.1.2 — identità persistente (D1–D7): split, merge, nuova separazione, riapertura con evidenze H1–H4, pareti non ritrovate,
 * rinumerazione e ordine degli id R3, alternative ambigue persistenti, collegamenti di incertezza, retrocompatibilità JSON.
 * Pareti orizzontali (normale verso −z); ogni registrazione contiene la precedente come prefisso (stessa sessione).
 */
class RescanSessionIdentityTest {
    private val P = EndState.PARTIAL
    private val O = EndState.OBSERVED

    private fun wall(id: Int, z: Double = 3.0, spans: List<Pair<Double, Double>> = listOf(0.0 to 4.0), start: EndState = O, end: EndState = O, views: Int = 4): EstimatedWall {
        val s = spans.first().first; val e = spans.last().second
        val geom = WallGeometry(0.0, -1.0, -z, 0.0, z, 1.0, 0.0, s, e, 0.0, 2.6, 0.0, 2.6, 0.0, spans.map { WallSpan(it.first, it.second) })
        val gaps = spans.zipWithNext().map { (a, b) -> WallGap(a.second, b.first, GapReason.NOT_SEEN) }
        val ev = WallEvidence(5000, 4800, 40, views, 60.0, 2.0, 0.004, 0.003, 0.95, 0.02, if (views >= 2) EvidenceQuality.HIGH else EvidenceQuality.MEDIUM, emptyList())
        return EstimatedWall(id, listOf(id), geom, WallUncertainty(0.005, 0.3, 0.01, 0.01, 0.014, 0.0, 0.0, 0.005, 0.005), ev,
            WallEnd(s, start, if (start == O) EndReason.CORNER else EndReason.NOT_SEEN, 0.01),
            WallEnd(e, end, if (end == O) EndReason.CORNER else EndReason.NOT_SEEN, 0.01), gaps, WallThickness(ThicknessState.UNKNOWN, note = ""), 0.9, emptyList(), emptyList())
    }

    private fun scan(poses: Int, walls: List<EstimatedWall>): ScanInput {
        val room = walls.map { it.id }
        val facts = PerimeterFacts(PerimeterState.CLOSED, room, room.associateWith { "x" }, emptyList(), null, null, null, null, null)
        val q = QualityEngine.evaluate(walls, facts, emptyMap(), walls.associate { it.id to 4.0 }, 12.0)
        val geo = PerimeterGeometry(PerimeterState.CLOSED, null, emptyList(), emptyList())
        val ps = (0 until poses).map { PoseSample(seq = it, timestampNs = it * 1_000_000L, tracking = "TRACKING", camera = RecPose(it * 0.01, 1.4, 0.0)) }
        return ScanInput("S$poses", 1000L, ps, emptyList(), walls, q, RescanDirector.plan(q, walls, geo, emptyMap(), emptyMap()), geo)
    }

    /** Catena di scansioni: a ogni passo invarianti (prev, next), ripresa da JSON = esecuzione diretta, ricodifica idempotente. */
    private fun run(vararg steps: List<EstimatedWall>): List<RescanSession> {
        val out = mutableListOf<RescanSession>(); var s: RescanSession? = null; var r: RescanSession? = null
        for ((i, w) in steps.withIndex()) {
            val n = RescanSessionEngine.update(s, scan(10 * (i + 1), w))
            assertEquals(emptyList(), RescanSessionEngine.validate(s, n), "invarianti al passo $i")
            r = RescanSessionEngine.update(r?.let { RescanSessionJson.decode(RescanSessionJson.encode(it)) }, scan(10 * (i + 1), w))
            val text = RescanSessionJson.encode(n)
            assertEquals(text, RescanSessionJson.encode(r), "ripresa da JSON = esecuzione diretta al passo $i")
            assertEquals(text, RescanSessionJson.encode(RescanSessionJson.decode(text)), "ricodifica idempotente al passo $i")
            out.add(n); s = n
        }
        return out
    }

    private fun RescanSession.t(key: String) = assertNotNull(targets.firstOrNull { it.key == key }, "bersaglio $key; presenti ${targets.map { it.key }}")
    private fun RescanSession.openEndsAt(x: Double, z: Double) = targets.filter { it.kind == TargetKind.WALL_ENDPOINT && it.state != TargetState.RESOLVED && it.last.pointX == x && it.last.pointZ == z }
    private fun RescanSession.lastMatches() = wallMatches.filter { it.scanIndex == scans.last().index }
    private fun SessionTarget.ev() = history.last()
    private fun renum(ws: List<EstimatedWall>, f: (Int) -> Int) = ws.map { it.copy(id = f(it.id), sourceSurfaceIds = listOf(f(it.id))) }

    /** Rappresentazione senza id R3: per confrontare sessioni equivalenti con id rinumerati. */
    private fun norm(s: RescanSession): List<String> =
        s.targets.map { t -> "${t.key}|${t.wallKey}|${t.state}|${t.attempts}|${t.reopenCount}|${t.history.joinToString(",") { "${it.to}:${it.reason}:${it.relatedTargetKey}:${it.matchStatus}:${it.candidates.map { c -> "${c.wallKey}/${c.end}/${c.verdict}/${c.followedBy}/${c.alternativeKey}" }}" }}|${t.last.pointX}|${t.last.pointZ}" } +
            s.walls.map { "W ${it.key}|${it.state}|${it.cz}|${it.startU}|${it.endU}|${it.lastScan}|${it.fragments.map { f -> "${f.role}:${f.startU}:${f.endU}" }}" } +
            s.wallAliases.map { "A $it" } + s.wallRelations.map { "R $it" } + s.targetLinks.map { "L $it" } +
            s.targetAlternatives.map { "ALT ${it.targetKey}/${it.altKey}/${it.wallKey}/${it.end}/${it.lastVerdict}/${it.status}" } +
            s.wallMatches.map { "M ${it.scanIndex}|${it.previousKey}|${it.status}|${it.relations}|${it.candidates.map { c -> c.role }}|${it.mergedWith}|${it.ambiguousWith}|${it.dormantSinceScan}" }

    // ------------------------------------------------------------------------------------------------ split (D1)

    @Test
    fun `1 - split e poi ulteriore split - una sola parete, SW1-END continua`() {
        val (_, b, c) = run(listOf(wall(0, spans = listOf(0.0 to 6.0), end = P)),
            listOf(wall(0, spans = listOf(0.0 to 2.0)), wall(1, spans = listOf(3.0 to 6.0), end = P)),
            listOf(wall(0, spans = listOf(0.0 to 2.0)), wall(1, spans = listOf(3.0 to 4.0)), wall(2, spans = listOf(4.5 to 6.0), end = P)))
        for (s in listOf(b, c)) {
            assertEquals(listOf("SW1"), s.walls.map { it.key }, "lo split non crea identità")
            val t = s.t("SW1-END")
            assertEquals(TargetState.PERSISTENT, t.state); assertEquals(TransitionReason.UNOBSERVED_NOT_MEASURABLE, t.ev().reason)
            assertEquals(1, s.openEndsAt(6.0, 3.0).size)
            assertTrue(MatchStatus.SPLIT in s.lastMatches().single().relations)
        }
        assertEquals(listOf(FragmentRole.PRIMARY, FragmentRole.SPLIT_PART, FragmentRole.SPLIT_PART).sorted(), c.lastMatches().single().candidates.map { it.role }.sorted())
        assertEquals(3, c.walls.single().fragments.size)
    }

    @Test
    fun `2 - split e poi merge della stessa parete - nessun alias, nessuna parete nuova`() {
        val (_, b, c) = run(listOf(wall(0, end = P)), listOf(wall(0, spans = listOf(0.0 to 1.5)), wall(1, spans = listOf(2.5 to 4.0), end = P)), listOf(wall(0, end = P)))
        assertEquals(MatchStatus.SPLIT, b.lastMatches().single().status)
        assertEquals(MatchStatus.MATCH_FOUND, c.lastMatches().single().status)
        assertTrue(c.wallAliases.isEmpty()); assertEquals(listOf("SW1"), c.walls.map { it.key })
        assertEquals(1, c.openEndsAt(4.0, 3.0).size); assertEquals(2, c.t("SW1-END").attempts)
    }

    // ------------------------------------------------------------------------------ merge e nuova separazione (D2)

    private val mA = listOf(wall(0, spans = listOf(0.0 to 1.5), end = P), wall(1, spans = listOf(2.5 to 4.0), start = P))

    @Test
    fun `3 - merge e poi nuova separazione nello stesso punto - riapertura con H1-H4, nessun bersaglio nuovo`() {
        val (_, b, c) = run(mA, listOf(wall(0)), mA)
        assertEquals(listOf(WallAlias("SW2", "SW1", 1, MatchStatus.MERGE)), b.wallAliases)
        for (k in listOf("SW1-END", "SW2-START")) assertEquals(TransitionReason.INTERNAL_COVERAGE, b.t(k).ev().reason)
        for (k in listOf("SW1-END", "SW2-START")) {
            val t = c.t(k)
            assertEquals(TargetState.PROPOSED, t.state, k); assertEquals(1, t.reopenCount, k)
            assertEquals(TransitionReason.REAPPEARED, t.history[t.history.size - 2].reason, k)
            assertTrue(!t.internal, "interna ricalcolata a ogni scansione")
        }
        assertTrue(c.targets.none { it.firstScan == 2 }, "nessun NEW: ${c.targets.filter { it.firstScan == 2 }.map { it.key }}")
        assertTrue(c.walls.none { it.key == "SW3" }); assertEquals(WallState.ABSORBED, c.walls.first { it.key == "SW2" }.state)
        assertTrue(c.targetLinks.isEmpty())
    }

    @Test
    fun `4a - bersaglio risolto il cui difetto ricompare nello stesso punto - riaperto`() {
        val (_, _, c) = run(listOf(wall(0, end = P)), listOf(wall(0)), listOf(wall(0, end = P)))
        val t = c.t("SW1-END")
        assertEquals(1, t.reopenCount); assertEquals(TargetState.PROPOSED, t.state)
        assertEquals("OBSERVED", t.history.first { it.reason == TransitionReason.REAPPEARED }.previous!!.endpointState)
    }

    @Test
    fun `4b - difetto ricomparso fuori finestra - lo storico RESOLVED resta tale, nuovo bersaglio con collegamento di incertezza`() {
        val (_, b, c) = run(listOf(wall(0, end = P)), listOf(wall(0)), listOf(wall(0, spans = listOf(0.0 to 4.5), end = P)))
        val h = c.t("SW1-END")
        assertEquals(TargetState.RESOLVED, h.state); assertEquals(b.t("SW1-END").history, h.history, "lo storico non viene toccato")
        val n = c.t("SW1-END-2"); assertEquals(TargetState.PROPOSED, n.state); assertEquals(4.5, n.last.pointX)
        assertEquals(listOf(TargetLink(2, TargetLinkKind.POSSIBLE_CONTINUATION, "SW1-END-2", "SW1-END", listOf(ContinuityCondition.H1_GEOMETRIC_WINDOW))), c.targetLinks)
    }

    @Test
    fun `4c - difetto simile su una parete distinta - nessuna riapertura e nessun collegamento`() {
        val (_, _, c) = run(listOf(wall(0, end = P)), listOf(wall(0)), listOf(wall(0), wall(1, z = 3.10, end = P)))
        assertEquals(TargetState.RESOLVED, c.t("SW1-END").state)
        assertEquals(TargetState.PROPOSED, c.t("SW2-END").state); assertTrue(c.targetLinks.isEmpty())
    }

    // ------------------------------------------------------------------------------ pareti non ritrovate (D4)

    @Test
    fun `5 - parete assente in una scansione e poi ricomparsa - ritrovata con la propria chiave`() {
        val keep = wall(1, z = 0.0)
        val (_, b, c) = run(listOf(wall(0, end = P), keep), listOf(keep), listOf(wall(0, end = P), keep))
        assertEquals(TransitionReason.NOT_REFOUND, b.t("SW1-END").ev().reason)
        val m = c.lastMatches().first { it.previousKey == "SW1" }
        assertEquals(MatchStatus.REFOUND, m.status); assertEquals(0, m.dormantSinceScan)
        assertEquals(TargetState.PERSISTENT, c.t("SW1-END").state); assertEquals(1, c.openEndsAt(4.0, 3.0).size)
        assertEquals(listOf("SW1", "SW2"), c.walls.map { it.key })
    }

    @Test
    fun `5b - quattro scansioni, due assenze consecutive e ritorno`() {
        val keep = wall(1, z = 0.0)
        val s = run(listOf(wall(0, end = P), keep), listOf(keep), listOf(keep), listOf(wall(0, end = P), keep))
        assertEquals(listOf(TransitionReason.NOT_REFOUND, TransitionReason.NOT_REFOUND), listOf(s[1], s[2]).map { it.t("SW1-END").ev().reason })
        val d = s[3]
        assertEquals(3, d.t("SW1-END").attempts); assertEquals(TransitionReason.UNOBSERVED_NOT_MEASURABLE, d.t("SW1-END").ev().reason)
        assertEquals(0, d.lastMatches().first { it.previousKey == "SW1" }.dormantSinceScan)
        assertEquals(listOf("SW1", "SW2"), d.walls.map { it.key }); assertTrue(d.targets.none { it.firstScan == 3 })
    }

    @Test
    fun `5c - una parete diversa al posto di quella persa - nessuna riassociazione forzata`() {
        val keep = wall(1, z = 0.0)
        val (_, _, c) = run(listOf(wall(0, end = P), keep), listOf(keep), listOf(wall(0, z = 3.10, end = P), keep))
        assertEquals(TransitionReason.NOT_REFOUND, c.t("SW1-END").ev().reason)
        assertEquals(MatchStatus.NO_GEOMETRIC_MATCH, c.lastMatches().first { it.previousKey == "SW1" }.status)
        assertEquals(TargetState.PROPOSED, c.t("SW3-END").state); assertTrue(c.targetLinks.isEmpty())
    }

    // ------------------------------------------------------------------------------ ambiguità (D3, D7)

    @Test
    fun `6 - pareti parallele distinte - lontane separate, vicine ambigue ma mai fuse`() {
        val far = run(listOf(wall(0, end = P), wall(1, z = 3.10, end = P)), listOf(wall(0, end = P), wall(1, z = 3.10, end = P)))[1]
        assertTrue(far.lastMatches().all { it.status == MatchStatus.MATCH_FOUND }); assertTrue(far.wallRelations.isEmpty())
        val near = run(listOf(wall(0, end = P), wall(1, z = 3.04, end = P)), listOf(wall(0, end = P), wall(1, z = 3.04, end = P)))[1]
        assertTrue(near.wallAliases.isEmpty(), "co-osservate: mai fuse")
        assertTrue(near.wallRelations.all { it.basis == RelationBasis.CO_OBSERVED_OVERLAP && it.a == "SW1" && it.b == "SW2" } && near.wallRelations.size == 2)
        assertEquals(TargetState.PERSISTENT, near.t("SW1-END").state); assertEquals(TargetState.PERSISTENT, near.t("SW2-END").state)
        for (k in listOf("SW1", "SW2")) assertEquals(near.walls.first { it.key == k }.cz, near.t("$k-END").last.pointZ!!, 1e-12, "ognuna segue la propria estremità")
    }

    @Test
    fun `7 - parete e pannello quasi sovrapposti - nessun RESOLVED, esito indipendente dall'id R3`() {
        val outs = listOf(0 to 1, 1 to 0).map { (idWall, idPanel) ->
            run(listOf(wall(0, end = P)), listOf(wall(idWall, end = P), wall(idPanel, z = 3.01)).sortedBy { it.id })[1]
        }
        for (s in outs) {
            val t = s.t("SW1-END")
            assertEquals(TargetState.PERSISTENT, t.state); assertEquals(TransitionReason.AMBIGUOUS_CANDIDATES, t.ev().reason)
            assertEquals(setOf(Verdict.OPEN, Verdict.OBSERVED), t.ev().candidates.map { it.verdict }.toSet())
            assertEquals(1, s.targetAlternatives.count { it.targetKey == "SW1-END" && it.lastVerdict == Verdict.OBSERVED && it.status == AlternativeStatus.ACTIVE })
            assertTrue(MatchStatus.AMBIGUOUS_MATCH in s.lastMatches().single().relations)
        }
        assertEquals(norm(outs[0]), norm(outs[1]))
    }

    @Test
    fun `8 e 9 - frammenti disgiunti SPLIT, sovrapposti AMBIGUOUS con parete alternativa propria`() {
        val split = run(listOf(wall(0, end = P)), listOf(wall(0, spans = listOf(0.0 to 2.0)), wall(1, spans = listOf(2.0 to 4.0), end = P)))[1]
        assertEquals(listOf(MatchStatus.MATCH_FOUND, MatchStatus.SPLIT), split.lastMatches().single().relations); assertEquals(1, split.walls.size)
        val over = run(listOf(wall(0, end = P)), listOf(wall(0, spans = listOf(0.0 to 2.2)), wall(1, spans = listOf(2.0 to 4.0), end = P)))[1]
        val m = over.lastMatches().single()
        assertTrue(MatchStatus.AMBIGUOUS_MATCH in m.relations); assertEquals(listOf("SW2"), m.ambiguousWith)
        assertTrue(FragmentRole.AMBIGUOUS_ALTERNATIVE in m.candidates.map { it.role }, "l'alternativa resta registrata")
        assertTrue(over.targets.none { it.state == TargetState.RESOLVED })
        assertEquals(TargetState.PERSISTENT, over.t("SW1-END").state); assertEquals(4.0, over.t("SW1-END").last.pointX)
    }

    @Test
    fun `10 - due candidati aperti per lo stesso bersaglio - si segue il migliore, l'altro resta alternativa riservata`() {
        val outs = listOf(0 to 1, 1 to 0).map { (a, b) -> run(listOf(wall(0, end = P)), listOf(wall(a, end = P), wall(b, z = 3.01, end = P)).sortedBy { it.id })[1] }
        for (s in outs) {
            val t = s.t("SW1-END")
            assertEquals(3.0, t.last.pointZ); assertEquals(TransitionReason.UNOBSERVED_NOT_MEASURABLE, t.ev().reason)
            assertEquals(1, s.openEndsAt(4.0, 3.0).size); assertTrue(s.openEndsAt(4.0, 3.01).isEmpty(), "l'alternativa non diventa NEW")
            assertEquals(Verdict.OPEN, s.targetAlternatives.single().lastVerdict)
        }
        assertEquals(norm(outs[0]), norm(outs[1]))
    }

    @Test
    fun `11 - due bersagli per lo stesso frammento - pareti co-osservate non fuse, il secondo COVERED`() {
        val b = run(listOf(wall(0, end = P), wall(1, z = 3.01, end = P)), listOf(wall(0, z = 3.005, end = P)))[1]
        assertTrue(b.wallAliases.isEmpty(), "distinzione dimostrata (co-osservate sovrapposte): nessun merge")
        assertEquals(TargetState.PERSISTENT, b.t("SW1-END").state)
        assertEquals(TransitionReason.COVERED_BY_OTHER_TARGET, b.t("SW2-END").ev().reason); assertEquals("SW1-END", b.t("SW2-END").ev().relatedTargetKey)
    }

    @Test
    fun `11b - merge non dimostrato (il frammento non copre entrambe) - nessun alias, relazione di evidenza insufficiente`() {
        val b = run(listOf(wall(0, spans = listOf(0.0 to 2.0), end = P), wall(1, spans = listOf(2.1 to 4.0), start = P)), listOf(wall(0, spans = listOf(0.0 to 2.05), end = P)))[1]
        assertTrue(b.wallAliases.isEmpty())
        assertEquals(listOf(RelationBasis.INSUFFICIENT_MERGE_EVIDENCE), b.wallRelations.map { it.basis })
    }

    // ------------------------------------------------------------------------------ merge multipli e priorità (D5)

    @Test
    fun `12 - merge di tre pareti con lo stesso difetto - una segue, due COVERED, storie separate`() {
        val a = listOf(wall(0, spans = listOf(0.0 to 1.0), views = 1), wall(1, spans = listOf(1.5 to 2.5), views = 1), wall(2, spans = listOf(3.0 to 4.0), views = 1))
        val b = run(a, listOf(wall(0, views = 1)))[1]
        assertEquals(listOf(WallAlias("SW2", "SW1", 1, MatchStatus.MERGE), WallAlias("SW3", "SW1", 1, MatchStatus.MERGE)), b.wallAliases)
        assertEquals(TransitionReason.NO_MEASURABLE_IMPROVEMENT, b.t("SW1-VIEWS").ev().reason)
        for (k in listOf("SW2-VIEWS", "SW3-VIEWS")) { assertEquals(TransitionReason.COVERED_BY_OTHER_TARGET, b.t(k).ev().reason); assertEquals("SW1-VIEWS", b.t(k).ev().relatedTargetKey) }
        assertEquals(4, b.t("SW2-VIEWS").history.size, "storie non fuse")
    }

    @Test
    fun `12b - priorita numerica, non di stringa - con chiavi a due cifre segue la canonica`() {
        val others = (0..9).filter { it != 1 && it != 9 }.map { wall(it, z = 20.0 + it) }
        val a = (others + wall(1, spans = listOf(0.0 to 1.5), views = 1) + wall(9, spans = listOf(2.5 to 4.0), views = 1)).sortedBy { it.id }
        val b = run(a, others + wall(1, views = 1))[1]
        val alias = b.wallAliases.single()
        assertTrue(alias.absorbedKey.length > alias.canonicalKey.length, "il caso deve confrontare una chiave a due cifre: $alias")
        assertEquals(TransitionReason.NO_MEASURABLE_IMPROVEMENT, b.t("${alias.canonicalKey}-VIEWS").ev().reason)
        assertEquals("${alias.canonicalKey}-VIEWS", b.t("${alias.absorbedKey}-VIEWS").ev().relatedTargetKey)
    }

    private val m13 = listOf(
        listOf(wall(0, spans = listOf(0.0 to 1.0), end = P), wall(1, spans = listOf(1.5 to 2.5), start = P, end = P), wall(2, spans = listOf(3.0 to 4.0), start = P)),
        listOf(wall(0, spans = listOf(0.0 to 2.5), end = P), wall(1, spans = listOf(3.0 to 4.0), start = P)),
        listOf(wall(0)),
        listOf(wall(0, spans = listOf(0.0 to 2.5), end = P), wall(1, spans = listOf(3.0 to 4.0), start = P)),
    )

    @Test
    fun `13 - quattro scansioni - merge, secondo merge, poi separazione - riaperture solo dove il difetto ricompare`() {
        val (_, b, c, d) = run(*m13.toTypedArray())
        assertEquals(listOf(WallAlias("SW2", "SW1", 1, MatchStatus.MERGE)), b.wallAliases)
        assertEquals(TransitionReason.INTERNAL_COVERAGE, b.t("SW1-END").ev().reason); assertEquals(TransitionReason.INTERNAL_COVERAGE, b.t("SW2-START").ev().reason)
        assertEquals(listOf(WallAlias("SW2", "SW1", 1, MatchStatus.MERGE), WallAlias("SW3", "SW1", 2, MatchStatus.MERGE)), c.wallAliases)
        assertEquals(TransitionReason.INTERNAL_COVERAGE, c.t("SW2-END").ev().reason); assertEquals(TransitionReason.INTERNAL_COVERAGE, c.t("SW3-START").ev().reason)
        assertEquals(c.wallAliases, d.wallAliases, "nessun alias nuovo nella separazione")
        assertTrue(MatchStatus.SPLIT in d.lastMatches().first { it.previousKey == "SW1" }.relations)
        for (k in listOf("SW2-END", "SW3-START")) { assertEquals(TargetState.PROPOSED, d.t(k).state, k); assertEquals(1, d.t(k).reopenCount, k) }
        assertEquals(TargetState.RESOLVED, d.t("SW1-END").state, "il punto 1,0 è coperto: nessuna riapertura")
        assertTrue(d.targets.none { it.firstScan == 3 }, "nessun NEW: ${d.targets.filter { it.firstScan == 3 }.map { it.key }}")
    }

    // ------------------------------------------------------------------------------ id R3 e ordine (D6)

    @Test
    fun `14 e 15 - id R3 rinumerati e ordine invertito - stessa sessione`() {
        val perm = { i: Int -> 7 - i }
        val cases = listOf(listOf(mA, listOf(wall(0)), mA), m13,
            listOf(listOf(wall(0, end = P)), listOf(wall(0, end = P), wall(1, z = 3.01)), listOf(wall(0))))
        for (steps in cases) {
            val base = run(*steps.toTypedArray()).last()
            assertEquals(norm(base), norm(run(*steps.map { renum(it, perm) }.toTypedArray()).last()), "rinumerazione")
            assertEquals(RescanSessionJson.encode(base), RescanSessionJson.encode(run(*steps.map { it.reversed() }.toTypedArray()).last()), "ordine invertito")
        }
        // Prima scansione: chiavi da ordine geometrico, non dall'id.
        val w = listOf(wall(0, z = 3.0, end = P), wall(1, z = 0.0, end = P), wall(2, z = 6.0))
        assertEquals(norm(run(w)[0]), norm(run(renum(w) { 9 - it })[0]))
    }

    // ------------------------------------------------------------------------------ alternative persistenti (Q2)

    @Test
    fun `18 - alternativa che diventa distinguibile - risolto solo allora`() {
        val (_, b, c) = run(listOf(wall(0, end = P)), listOf(wall(0, end = P), wall(1, z = 3.01)), listOf(wall(0), wall(1, z = 3.01, spans = listOf(0.0 to 3.5))))
        assertEquals(TransitionReason.AMBIGUOUS_CANDIDATES, b.t("SW1-END").ev().reason)
        assertEquals(TargetState.RESOLVED, c.t("SW1-END").state)
        assertTrue(c.targetAlternatives.filter { it.targetKey == "SW1-END" }.all { it.lastVerdict in setOf(Verdict.OBSERVED, Verdict.BELOW_MIN, Verdict.NOT_REACHABLE) })
    }

    @Test
    fun `18b - quattro scansioni - alternativa non osservata non e prova, la tiene aperto finche un'evidenza positiva la esclude`() {
        val s = run(listOf(wall(0, end = P)), listOf(wall(0, end = P), wall(1, z = 3.04)), listOf(wall(0, z = 2.97)),
            listOf(wall(0, z = 2.97), wall(1, z = 3.04, spans = listOf(0.0 to 3.5))))
        assertEquals(TransitionReason.AMBIGUOUS_CANDIDATES, s[1].t("SW1-END").ev().reason)
        val alt2 = s[2].targetAlternatives.single { it.targetKey == "SW1-END" }
        assertEquals(Verdict.NOT_OBSERVED, alt2.lastVerdict); assertEquals(AlternativeStatus.ACTIVE, alt2.status, "assenza ≠ evidenza contraria")
        assertEquals(TargetState.PERSISTENT, s[2].t("SW1-END").state); assertEquals(TransitionReason.AMBIGUOUS_CANDIDATES, s[2].t("SW1-END").ev().reason)
        val alt3 = s[3].targetAlternatives.single { it.targetKey == "SW1-END" }
        assertEquals(AlternativeStatus.DISAMBIGUATED, alt3.status); assertEquals(3, alt3.disambiguatedScan)
        assertEquals(TargetState.RESOLVED, s[3].t("SW1-END").state)
    }

    // ------------------------------------------------------------------------------ retrocompatibilità (16, 19)

    private val newFields = setOf("fragments", "outX", "outZ", "outEvidence", "candidates", "relations", "primary", "mergedWith",
        "ambiguousWith", "dormantSinceScan", "wallRelations", "targetAlternatives", "targetLinks")
    private val newWallFields = setOf("state", "firstScan")   // solo negli oggetti parete ("inRoom"): "state" esiste anche nei bersagli
    private fun strip(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.filterKeys { it !in newFields && !("inRoom" in e && it in newWallFields) }.mapValues { strip(it.value) })
        is JsonArray -> JsonArray(e.map { strip(it) })
        else -> e
    }
    /** Sessione nel formato M5.1.1 (senza alcun campo M5.1.2). */
    private fun legacy(s: RescanSession): RescanSession {
        val text = Json.encodeToString(JsonElement.serializer(), strip(Json.parseToJsonElement(RescanSessionJson.encode(s))))
        assertTrue(newFields.none { "\"$it\"" in text })
        return RescanSessionJson.decode(text)
    }

    @Test
    fun `16 e 17 - sessione nel formato precedente caricata, aggiornata, risalvata`() {
        for (steps in listOf(listOf(mA, listOf(wall(0)), mA), m13)) {
            val s = run(*steps.dropLast(1).toTypedArray()).last()
            val old = legacy(s)
            val n = RescanSessionEngine.update(old, scan(10 * steps.size, steps.last()))
            assertEquals(emptyList(), RescanSessionEngine.validate(old, n))
            val text = RescanSessionJson.encode(n)
            assertEquals(text, RescanSessionJson.encode(RescanSessionJson.decode(text)))
            assertEquals(text, RescanSessionJson.encode(RescanSessionEngine.update(legacy(s), scan(10 * steps.size, steps.last()))), "deterministico")
        }
    }

    @Test
    fun `19 - direzione legacy non confermata - nessun RESOLVED finche un frammento non la conferma`() {
        val a = legacy(run(listOf(wall(0, end = P)))[0])
        val b = RescanSessionEngine.update(a, scan(20, listOf(wall(0, spans = listOf(0.0 to 4.15)))))
        assertEquals(TransitionReason.ENDPOINT_OBSERVED, b.t("SW1-END").ev().reason, "il punto cade sull'estremità di un frammento: direzione confermata")
        assertEquals(OutwardSource.FOLLOWED_FRAGMENT_END, b.t("SW1-END").last.outEvidence!!.source)
        val c = RescanSessionEngine.update(legacy(run(listOf(wall(0, end = P)))[0]), scan(20, listOf(wall(0, spans = listOf(0.0 to 6.0)))))
        assertEquals(TransitionReason.DIRECTION_UNDETERMINED, c.t("SW1-END").ev().reason, "nessun RESOLVED con direzione solo legacy")
        assertTrue(c.t("SW1-END").state != TargetState.RESOLVED)
    }
}
