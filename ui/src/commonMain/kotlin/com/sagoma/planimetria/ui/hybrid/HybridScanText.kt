package com.sagoma.planimetria.ui.hybrid

import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.scan.TrackingIssue
import com.sagoma.planimetria.scan.hybrid.BoxState
import com.sagoma.planimetria.scan.hybrid.CandidateStatus
import com.sagoma.planimetria.scan.hybrid.Discrepancy
import com.sagoma.planimetria.scan.hybrid.DiscrepancyKind
import com.sagoma.planimetria.scan.hybrid.FragmentStatus
import com.sagoma.planimetria.scan.hybrid.HybridPhase
import com.sagoma.planimetria.scan.hybrid.ReferenceCandidate

/*
 * Testi del PROTOTIPO di scansione ibrida (pavimento, soffitto e pareti dalla profondità). Un solo posto per le parole, come per la
 * scansione guidata. Solo italiano.
 */
object HybridScanText {
    private fun cm(m: Double) = formatDecimal(m * 100, 0, ',') + " cm"
    private fun m(v: Double) = formatDecimal(v, 2, ',') + " m"
    private fun deg(d: Double) = formatDecimal(d, 1, ',') + "°"

    const val chooseTitle = "Che scansione vuoi fare?"
    const val chooseGuided = "Scansione guidata (attuale)"
    const val chooseGuidedHint = "Le pareti proposte da ARCore, una alla volta"
    const val chooseHybrid = "Prototipo: scansione ibrida"
    const val chooseHybridHint = "Pavimento, soffitto e pareti ricostruiti dalla profondità del telefono. È una PROVA: risultati non ancora verificati."

    const val title = "Prototipo: scansione ibrida"
    const val intro = "Ricostruisce la stanza come pavimento, soffitto e pareti usando la profondità del telefono e lo schizzo. " +
        "È una prova: le tolleranze sono provvisorie e i risultati non sono ancora verificati con il metro."
    const val qStairs = "Ci sono scale nella stanza?"
    const val qSlopedFloor = "Il pavimento è inclinato?"
    const val qSlopedCeiling = "Il soffitto è inclinato (mansarda)?"
    const val yes = "Sì"
    const val no = "No"
    const val next = "Avanti"
    const val begin = "Inizia"
    const val close = "Chiudi"
    const val exit = "Esci"
    const val menu = "Menu"
    const val back = "Indietro"
    const val unsupportedTitle = "Caso non gestito dal prototipo"
    fun unsupported(stairs: Boolean, floor: Boolean, ceiling: Boolean) =
        "Il prototipo non gestisce: " + listOfNotNull("scale".takeIf { stairs }, "pavimento inclinato".takeIf { floor }, "soffitto inclinato".takeIf { ceiling }).joinToString(", ") +
            ". Puoi uscire o provare lo stesso: i risultati NON saranno affidabili."
    const val tryAnyway = "Prova lo stesso"
    const val unsupportedFlag = "Attenzione: stanza con casi non gestiti (scale o superfici inclinate). Risultati non affidabili."
    const val noDepthPhone = "Questo telefono non fornisce la profondità: il prototipo non può funzionare (non usa i piani di ARCore al suo posto)."

    const val startTitle = "Da quale parete cominci?"
    const val startExplain = "Sceglierai le pareti nell'ordine dello schizzo. Di solito conviene partire dalla porta."

    fun phaseTitle(p: HybridPhase) = when (p) {
        HybridPhase.START_SIDE -> "Parete di partenza"
        HybridPhase.FLOOR -> "1 · Pavimento"
        HybridPhase.WALLS -> "2 · Pareti"
        HybridPhase.CEILING -> "3 · Soffitto"
        HybridPhase.REVIEW -> "Riepilogo"
    }

    /** Dopo il riferimento si usa l'immagine intera, ma si registrano solo i punti buoni: visti quasi di fronte, entro 2 m. */
    private const val colors = "Le parti registrate come buone si colorano di VERDE; in bianco quelle viste ma ancora da completare, in arancione quelle dubbie."

    val floorSteps = listOf(
        "Inquadra un tratto di pavimento LIBERO e SENZA RIFLESSI a circa 1 m da te: su marmo o piastrelle lucide il riflesso di luci, porte e mobili falsa la misura di alcuni centimetri.",
        "Aspetta \"Pavimento pronto\" e premi «Conferma pavimento».",
        "Poi cammina piano per la stanza inquadrando il pavimento dall'alto (si registrano solo i punti visti quasi di fronte, entro 2 m); quando hai finito premi «Avanti».",
        colors,
    )
    val ceilingSteps = listOf(
        "Per ultimo il soffitto: serve solo per l'altezza, le pareti sono già al sicuro. Puoi anche saltarlo: l'altezza non sarà stimata.",
        "Inquadra un tratto di soffitto LIBERO (senza lampade), tenendo nell'inquadratura anche un pezzo di parete: su un soffitto tutto uguale il telefono perde la posizione.",
        "Aspetta \"Soffitto pronto\" e premi «Conferma soffitto».",
        "Poi cammina tenendo il telefono quasi in verticale sotto il soffitto (si registrano solo i punti visti quasi di fronte); quando hai finito premi «Avanti: riepilogo».",
        colors,
    )
    val wallSteps = listOf(
        "Mettiti a 1–2 m dalla parete gialla e inquadra un tratto LIBERO (niente mobili, vetri, quadri).",
        "Aspetta \"Parete pronta\" e premi «Conferma parete».",
        "Poi spostati di lato lungo tutta la parete, a 1–1,5 m e con il telefono sempre di fronte (di sbieco i punti non si registrano), anche in alto.",
        "Quando giri verso la parete successiva, il cambio di direzione viene riconosciuto e ti viene proposta: confermala.",
        "Confermate e percorse tutte le pareti, premi «Avanti: soffitto».",
        colors,
    )

    const val confirmFloor = "Conferma pavimento"
    const val confirmCeiling = "Conferma soffitto"
    fun confirmWall(side: String, first: Boolean) = if (first) "Conferma parete: $side" else "Nuova parete trovata: $side — Conferma"
    const val nextCeiling = "Avanti: soffitto"
    const val nextWalls = "Avanti: pareti"
    const val nextReview = "Avanti: riepilogo"
    const val allWallsDone = "Tutte le pareti confermate: percorrile ancora dove sono bianche, poi premi «Avanti: soffitto»."
    const val realignMismatch = "Le due pareti non tornano tra loro: forse una non era quella gialla. Ricominciamo il riallineamento dalla prima parete gialla."
    const val skipCeiling = "Salta il soffitto (altezza non stimata)"
    fun surfaceDone(kind: String, seenM2: Double, acceptedM2: Double) =
        "$kind confermato · visti ${formatDecimal(seenM2, 1, ',')} m², accettati ${formatDecimal(acceptedM2, 1, ',')} m² (estensione, non precisione del bordo). Cammina inquadrandolo."
    fun surfaceDoubtful(offsetM: Double, angleDeg: Double?) =
        "La parte più grande è dubbia: in media ${formatDecimal(offsetM * 100, 1, ',')} cm dal riferimento" + (angleDeg?.let { ", inclinata di ${deg(it)}" } ?: "") + " (limite provvisorio 3 cm e 3°). Non entra nei m² accettati. " +
            "Se è quasi tutto arancione, il riferimento può essere caduto su un riflesso: rifallo su un tratto senza riflessi."
    const val redoFloorRef = "Rifai il riferimento del pavimento"
    const val redoCeilingRef = "Rifai il riferimento del soffitto"
    fun tiltLine(wall: Boolean, tiltDeg: Double) = if (wall) "Inclinazione dalla verticale ${deg(tiltDeg)}" else "Inclinazione dall'orizzontale ${deg(tiltDeg)}"
    fun adjacentLine(side: String, angleDeg: Double, sketchDeg: Double) = "Angolo con ${side.lowercase()}: ${deg(angleDeg)} (schizzo ${deg(sketchDeg)})"

    fun wallOf(side: String) = "Parete da acquisire: $side"

    /** Tracciamento perso: il motivo di ARCore e cosa fare (terza prova: posizione persa per "pochi dettagli" fino al 15% del tempo). */
    fun trackingLost(issue: TrackingIssue?) = "Posizione persa" + when (issue) {
        TrackingIssue.INSUFFICIENT_FEATURES -> ": pochi dettagli nell'inquadratura. Inquadra anche mobili, cornici, prese o lo spigolo con il soffitto, e muoviti piano"
        TrackingIssue.EXCESSIVE_MOTION -> ": movimento troppo veloce. Muovi il telefono più lentamente"
        TrackingIssue.LOW_LIGHT -> ": poca luce. Accendi le luci della stanza"
        TrackingIssue.CAMERA_UNAVAILABLE -> ": fotocamera non disponibile"
        null -> ": muovi lentamente il telefono inquadrando mobili e oggetti"
    }
    /** Conferma non riuscita, con il motivo attuale (di solito "tieni fermo": il pallino deve essere verde). */
    fun notConfirmed(reason: String) = "Non confermato. $reason"

    const val realignTitle = "Riallineamento"
    fun realignExplain(side: String, done: Int, needed: Int) =
        "Il telefono ha perso la posizione e potrebbe aver girato la stanza. Inquadra di nuovo ${side.lowercase()} (già acquisita), un tratto libero, " +
            "e premi «Riallinea»." + (if (needed > 1) " Parete ${done + 1} di $needed." else "")
    fun realignButton(side: String) = "Riallinea con: $side"
    const val realignFloor = "Allinea anche la quota con il pavimento"
    const val realignFloorDone = "Quota allineata con il pavimento."
    const val realignOther = "Usa un'altra parete già acquisita"
    const val realignChoose = "Con quale parete riallineare?"
    const val realignSkip = "Salta (se sei sicuro che non si è spostato niente)"
    const val realignDoneMsg = "Riallineato: le superfici acquisite sono riportate nella posizione attuale."

    fun candidate(c: ReferenceCandidate, phase: HybridPhase, sideName: (Int) -> String, realign: Boolean = false): String {
        val base = when (c.status) {
            CandidateStatus.NO_DEPTH -> "Nessun dato di profondità valido: muovi lentamente il telefono (non uso i piani di ARCore al suo posto)"
            CandidateStatus.TOO_FEW_POINTS -> "Pochi punti al centro: stai a 0,5–3 m e muovi piano il telefono"
            CandidateStatus.NOT_PLANAR -> "Non è una superficie piana (dispersione ${c.plane?.let { cm(it.sigmaM) } ?: "-"}): inquadra un tratto libero"
            CandidateStatus.UNSTABLE -> "Tieni fermo il telefono un istante"
            CandidateStatus.WRONG_ORIENTATION -> when (phase) {
                HybridPhase.WALLS, HybridPhase.REVIEW ->"Inclinata di ${c.tiltDeg?.let { deg(it) } ?: "-"} dalla verticale: non sembra una parete"
                HybridPhase.CEILING -> "Inclinata di ${c.tiltDeg?.let { deg(it) } ?: "-"}: non sembra il soffitto"
                else -> "Inclinata di ${c.tiltDeg?.let { deg(it) } ?: "-"}: non sembra il pavimento"
            }
            CandidateStatus.WRONG_SIDE -> if (phase == HybridPhase.CEILING) "È sotto di te: inquadra il soffitto" else "È sopra di te: inquadra il pavimento"
            CandidateStatus.ALREADY_ACQUIRED -> c.otherSide?.let { "È ${sideName(it).lowercase()}, già acquisita: percorrila; quando giri verso la parete gialla te la propongo" }
                ?: "Parete già acquisita"
            CandidateStatus.PARALLEL_OFFSET -> "Parallela a ${c.otherSide?.let { sideName(it).lowercase() } ?: "una parete"} ma spostata di ${c.offsetM?.let { cm(kotlin.math.abs(it)) } ?: "-"}: probabilmente un mobile. Inquadra il muro libero"
            CandidateStatus.OFF_SKETCH -> "Cerco la parete gialla: inquadra un tratto libero (le superfici con direzione diversa dallo schizzo non sono considerate). Se non la trova mai, la parete precedente potrebbe essere sbagliata: Menu → Rifai"
            CandidateStatus.SURFACE_BEHIND -> "C'è un'altra superficie parallela ${c.offsetM?.let { cm(it) } ?: ""} più indietro: forse questa è un mobile, " +
                "o nell'inquadratura c'è una finestra o una porta. Non si può confermare: inquadra un tratto di muro libero, senza mobili, finestre o porte"
            CandidateStatus.READY -> when {
                phase == HybridPhase.FLOOR -> "Pavimento pronto"
                phase == HybridPhase.CEILING -> "Soffitto pronto"
                realign && c.side == null -> "Pavimento pronto: puoi allineare la quota"
                realign -> "Pronta per riallineare con " + (c.side?.let { sideName(it).lowercase() } ?: "la parete")
                else -> "Parete pronta" + (c.side?.let { ": " + sideName(it) } ?: "")
            }
        }
        val opposite = if (c.oppositeDistanceM != null && c.sketchOppositeDistanceM != null)
            " · distanza dalla parete opposta ${m(c.oppositeDistanceM!!)} (schizzo ${m(c.sketchOppositeDistanceM!!)})" else ""
        return base + opposite
    }

    fun depthLine(validPoints: Int, pixels: Int, msSince: Long?) = when {
        msSince == null -> "Profondità: nessun dato ancora"
        msSince > 3000 -> "Profondità: NESSUN DATO VALIDO da ${msSince / 1000} s"
        else -> "Profondità: ${if (pixels > 0) validPoints * 100 / pixels else 0}% dei punti validi"
    }

    fun wallProgress(side: String, accepted: Int, doubtful: Int, insufficient: Int) =
        "$side: $accepted tratti accettati" + (if (doubtful > 0) ", $doubtful dubbi" else "") + (if (insufficient > 0) ", $insufficient da osservare ancora" else "")
    const val doubtfulHint = "Zone dubbie (forse un mobile o un rivestimento): inquadrale di nuovo da un'altra posizione. Non entrano nella pianta."
    const val goToReview = "Vai al riepilogo"
    const val backToScan = "Torna alla scansione"
    const val redoWall = "Rifai una parete"
    const val redoTitle = "Quale parete vuoi rifare?"
    fun redoSide(side: String) = "Rifai $side"
    const val chooseTarget = "Scegli la parete da acquisire"
    const val details = "Dettagli"
    fun jumpLine(jumps: Int, unverified: Boolean, displacementM: Double?, discarded: Int) =
        "Salti di posizione: $jumps" + (if (unverified) " · posizione da verificare: ogni tanto inquadra una superficie già confermata" else " · posizione verificata") +
            (displacementM?.let { " · l'ultima superficie confermata vista era spostata di ${cm(kotlin.math.abs(it))}: quei punti non si usano" } ?: "") +
            (if (discarded > 0) " · immagini scartate $discarded" else "")
    fun pointsLine(points: Int) = "punti buoni raccolti " + formatDecimal(points.toDouble(), 0, ',')
    const val record = "● Registra"
    const val stopRecording = "■ Ferma registrazione"
    const val recordingOn = "● Registrazione in corso"
    const val export = "Esporta registrazione"
    const val exitConfirm = "Uscire dal prototipo? Le superfici acquisite andranno perse."
    const val stay = "Resta"

    fun box(b: BoxState) = when (b) {
        BoxState.INCOMPLETE -> "Scatola incompleta: mancano pareti misurate"
        BoxState.UNCERTAIN -> "Scatola completa ma con associazioni incerte (avvisi superati): rifai quelle pareti per poter applicare"
        BoxState.UNDEFINED_CORNER -> "Angolo non definito: due pareti adiacenti sono quasi parallele"
        BoxState.COMPLETE -> "Scatola chiusa: tutte le pareti misurate"
    }

    fun sideLine(side: String, measured: Double?, sketch: Double) =
        "$side: " + (measured?.let { m(it) } ?: "non misurato") + " (schizzo ${m(sketch)})"
    fun angleLine(k: Int, measured: Double?, sketch: Double) = "Angolo ${k + 1}: " + (measured?.let { deg(it) } ?: "-") + " (schizzo ${deg(sketch)})"
    fun height(measured: Double?, sketch: Double?, sketchIsDefault: Boolean, skipped: Boolean) = "Altezza: " +
        (measured?.let { m(it) } ?: if (skipped) "non stimata (soffitto saltato)" else "non stimata") +
        (sketch?.let { " · schizzo ${m(it)}" + if (sketchIsDefault) " (valore predefinito dell'app: forse non dichiarato)" else "" } ?: "")
    fun floorSummary(area: Double, ceilingArea: Double) = "Superfici ricostruite: pavimento ${formatDecimal(area, 1, ',')} m², soffitto ${formatDecimal(ceilingArea, 1, ',')} m² (estensione, non precisione del bordo)"

    fun discrepancy(d: Discrepancy, sideName: (Int) -> String) = when (d.kind) {
        DiscrepancyKind.LENGTH -> "${sideName(d.sides[0])}: misurato ${d.measured?.let { m(it) }}, schizzo ${d.expected?.let { m(it) }}"
        DiscrepancyKind.ANGLE -> "Angolo tra ${d.sides.joinToString(" e ") { sideName(it).lowercase() }}: misurato ${d.measured?.let { deg(it) }}, schizzo ${d.expected?.let { deg(it) }}"
        DiscrepancyKind.DIRECTION -> "${sideName(d.sides[0])}: direzione diversa di ${d.measured?.let { deg(it) }} dallo schizzo"
        DiscrepancyKind.MISSING_WALL -> "${sideName(d.sides[0])}: non acquisita"
        DiscrepancyKind.UNCERTAIN_ASSOCIATION -> "${sideName(d.sides[0])}: confermata nonostante un avviso (associazione incerta)"
        DiscrepancyKind.HEIGHT -> "Altezza: misurata ${d.measured?.let { m(it) }}, schizzo ${d.expected?.let { m(it) }}"
    }
    const val discrepanciesTitle = "Confronto con lo schizzo (si mostra, non si corregge)"
    const val noDiscrepancies = "Nessuna differenza oltre le soglie con lo schizzo."
    const val apply = "Applica alla stanza"
    const val applyNeedsComplete = "Per applicare servono tutte le pareti misurate, senza associazioni incerte."
    const val applyOpeningsDontFit = "Porte o finestre non entrano nei muri misurati: controlla lo schizzo."
    const val toleranceNote = "Tolleranze provvisorie (protocollo P0): ogni quadretto di 10 cm è buono se sta entro 3 cm dal riferimento (giudicato con almeno 20 punti); i tratti grandi entro 3° di inclinazione. Non sono precisioni garantite."

    fun fragmentStatus(s: FragmentStatus) = when (s) {
        FragmentStatus.ACCEPTED -> "accettato"
        FragmentStatus.DOUBTFUL -> "dubbio"
        FragmentStatus.INSUFFICIENT -> "da osservare"
    }
    fun fragmentLine(status: FragmentStatus, points: Int, frames: Int, medianM: Double, angleDeg: Double?, a: Double, b: Double) =
        "${fragmentStatus(status)}: $points punti, $frames fotogrammi, scarto ${formatDecimal(medianM * 100, 1, ',')} cm" +
            (angleDeg?.let { ", inclinazione ${deg(it)}" } ?: "") + ", ${formatDecimal(a, 1, ',')} × ${formatDecimal(b, 1, ',')} m"
}
