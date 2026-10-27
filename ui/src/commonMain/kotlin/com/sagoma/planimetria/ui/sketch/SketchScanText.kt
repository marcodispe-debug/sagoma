package com.sagoma.planimetria.ui.sketch

import androidx.compose.runtime.staticCompositionLocalOf
import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.scan.TrackingIssue
import com.sagoma.planimetria.scan.assisted.Advisory
import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.assisted.Reason
import com.sagoma.planimetria.scan.sketch.Answer
import com.sagoma.planimetria.scan.sketch.GeometryIssue
import com.sagoma.planimetria.scan.sketch.IssueCode
import com.sagoma.planimetria.scan.sketch.MeasureSource
import com.sagoma.planimetria.scan.sketch.OpeningMark
import com.sagoma.planimetria.scan.sketch.RejectionReason
import com.sagoma.planimetria.scan.sketch.SessionRejection
import com.sagoma.planimetria.scan.sketch.StartReference

/*
 * UNICO confine di presentazione dei testi del percorso B (scansione guidata dallo schizzo): codici, valori e motivi del nucleo
 * diventano parole SOLO qui. Numeri e misure passano da [SketchScanText.meters] e simili, i plurali da funzioni con il
 * numero. Le schermate non scrivono testi propri.
 *
 * Scelta tecnica (documentata): oggi Sagoma non ha un sistema di risorse localizzabili in comune (Compose Multiplatform Resources
 * non è configurato; le risorse Android non arrivano al codice comune) e tutte le altre schermate hanno testi fissi in italiano.
 * Per non creare un secondo sistema, il percorso B usa un'interfaccia: c'è una sola implementazione, [SketchScanTextIt], e nessuna
 * traduzione (non ci sono lingue documentate). Quando l'app sceglierà il sistema di risorse, basterà un'implementazione di
 * [SketchScanText] che legge da lì: i `when` sui codici sono esaustivi, quindi ogni codice nuovo obbliga ad aggiungere il testo.
 */
/**
 * Come si scrivono le lunghezze. Oggi c'è solo il sistema metrico ([MetricLengthFormat]); il confine riceve il formato come
 * parametro, così un'altra unità si aggiunge con una nuova implementazione, senza toccare le schermate. Nessuna conversione qui.
 */
interface LengthFormat {
    fun meters(m: Double): String
    fun centimeters(cm: Double): String
    fun squareMeters(m2: Double): String
}

/** Metri e centimetri, con il separatore decimale della lingua. */
class MetricLengthFormat(private val decimalSeparator: Char) : LengthFormat {
    override fun meters(m: Double) = formatDecimal(m, 2, decimalSeparator) + " m"
    override fun centimeters(cm: Double) = formatDecimal(cm, 0, decimalSeparator) + " cm"
    override fun squareMeters(m2: Double) = formatDecimal(m2, 2, decimalSeparator) + " m²"
}

interface SketchScanText {
    // ---- Numeri e misure ----
    fun meters(m: Double): String
    fun centimeters(cm: Double): String
    fun degrees(deg: Double): String
    fun percent(rel: Double): String

    // ---- Nomi ----
    fun side(k: Int): String
    fun wall(id: Int): String
    fun opening(m: OpeningMark): String
    fun openingKind(k: OpeningKind): String
    fun trackingIssue(i: TrackingIssue): String
    fun source(s: MeasureSource): String
    fun startReference(r: StartReference): String

    // ---- Codici del nucleo ----
    /** Il problema in una frase, con i suoi valori (entrambi i valori quando c'è un confronto con lo schizzo). */
    fun issue(i: GeometryIssue): String
    fun answer(a: Answer): String
    fun rejection(r: RejectionReason): String
    fun sessionRejection(r: SessionRejection, side: Int?): String

    // ---- Motore (proposte) ----
    fun candidateState(s: CandidateState): String
    fun reason(r: Reason): String
    fun advisory(a: Advisory): String

    // ---- Plurali ----
    fun wallsConfirmed(count: Int): String
    fun alternatives(count: Int): String

    // ---- Schermata ----
    val chooseStartTitle: String
    val chooseStartHint: String
    val suggestedStart: String
    val aimHint: String
    val confirmWall: String
    val nextSideTitle: String
    val associationTitle: String
    val noWall: String
    val startMark: String
    val targetMark: String
    val inwardNotMeasured: String
    val remove: String
    val moveTo: String
    val shiftAllBack: String
    val shiftAllForward: String
    val questionsTitle: String
    /** Torna a una scelta già fatta (angoli della scansione o a 90°). */
    val changeChoice: String
    val blockingTitle: String
    val warningsTitle: String
    val declarationTitle: String
    val declarationNoteHint: String
    /** La dichiarazione mostrata come tale: è dell'utente, non una verifica del sistema. */
    fun declaredByUser(r: StartReference, note: String): String
    val manualLengthTitle: String
    val manualLengthLabel: String
    val useSketchLength: String
    val reviewTitle: String
    fun sideLine(k: Int, length: Double, sketch: Double, sources: Set<MeasureSource>, estimate: Boolean): String
    fun cornerLine(k: Int, angleDeg: Double, conventional: Boolean): String
    fun areaPerimeter(areaM2: Double, perimeterM: Double): String
    val openingsTitle: String
    fun openingLine(kind: String, side: Int, oldPos: Double, newPos: Double, width: Double, fits: Boolean): String
    val openingPositionLabel: String
    val confirmRoom: String
    val exit: String
    val record: String
    val stopRecording: String
    val export: String
    val trackingLost: String
    /** Conferma prima di uscire: tutto quello che si perde (non solo le pareti). */
    val exitConfirm: String
    val exitConfirmYes: String
    val stay: String
    val openingWidthLabel: String
    val recordingConsentTitle: String
    val recordingConsentText: String
    val recordingConsentYes: String
    val deleteRecording: String
    val recordingDeleted: String
    val legendStart: String
    val legendTarget: String
    val legendAssigned: String
    val arChecking: String
    val cameraTitle: String
    val cameraMessage: String
    val cameraDenied: String
    val unsupportedTitle: String
    val unsupportedMessage: String
    val checkFailedTitle: String
    val checkFailedMessage: String
    val allowCamera: String
    val openSettings: String
    val cancel: String
    val back: String
    val installingArcore: String
    val unavailableTitle: String
    val recordingExported: String
    val recordingExportFailed: String

    // ---- Creazione della stanza (percorso B) ----
    val knowMeasuresQuestion: String
    val knowMeasuresYes: String
    val knowMeasuresYesHint: String
    val knowMeasuresNo: String
    val knowMeasuresNoHint: String
    val approxTitle: String
    val approxOnly: String
    val approxOnlyHint: String
    val approxWithScan: String
    val approxWithScanHint: String
    val sketchShapeTitle: String
    val approximateMeasuresHint: String
    val sketchBanner: String
    val startScan: String
    val later: String

    // ---- Procedura guidata (SketchScanGuide) ----
    /** Nome di un lato per chi scansiona: "Lato 2 · finestra". */
    fun sideName(k: Int, openings: List<OpeningMark>): String
    /** "Parete 2 di 4". */
    fun wallOfTotal(k: Int, n: Int): String
    /** "Completate 1 di 4". */
    fun progress(done: Int, total: Int): String
    fun phaseTitle(p: GuidePhase): String
    val startQuestion: String
    val startExplain: String
    val suggestedTag: String
    val recordFirstHint: String
    fun aimInstruction(side: String): String
    fun aimStatus(s: AimStatus): String
    val changeStart: String
    val scanOtherWall: String
    val chooseNextTitle: String
    val undoLastWall: String
    val goToReview: String
    val backToScan: String
    val questionTitle: String
    val reviewAllGood: String
    val reviewTodoTitle: String
    val reviewQuestionsTitle: String
    fun wallsMissing(count: Int): String
    val angleTitle: String
    /** Spiegazione della scelta sugli angoli, con lo spostamento massimo della pianta e lo scarto massimo degli angoli. */
    fun angleExplain(shiftM: Double?, maxDeviationDeg: Double?): String
    val measuresTitle: String
    val openingsAdjustTitle: String
    val applyRoom: String
    val applyNeedsFixes: String
    val detailsShow: String
    val detailsHide: String
    val recordingOn: String

    // ---- Finestre passo passo (GuideWindow) ----
    /** I passi per inquadrare una parete, in ordine. */
    val introSteps: List<String>
    val begin: String
    val next: String
    val menu: String
    val menuTitle: String
    val readInstructions: String
    val close: String
    /** "Fatto: Lato 2 · finestra". */
    fun wallDone(side: String): String
    val allWallsDone: String
    val allWallsDoneExplain: String
    /** "Ora la parete 3 di 4". */
    fun nextWall(k: Int, n: Int): String
    /** "È alla tua destra: girati verso destra." */
    fun turnHint(t: Turn): String
    val turnUnknown: String
    /** "Rifai una parete": cancella la scansione di una parete e la si rifà. */
    val redoWall: String
    val redoTitle: String
    val redoExplain: String
    /** "Rifai Lato 2 · finestra". */
    fun redoSide(side: String): String
}

/** Italiano: l'unica lingua oggi. Il formato delle lunghezze è un parametro (oggi metrico). */
class SketchScanTextIt(private val lengths: LengthFormat = MetricLengthFormat(SEP)) : SketchScanText {
    private companion object { const val SEP = ',' }
    override fun meters(m: Double) = lengths.meters(m)
    override fun centimeters(cm: Double) = lengths.centimeters(cm)
    override fun degrees(deg: Double) = formatDecimal(deg, 1, SEP) + "°"
    override fun percent(rel: Double) = formatDecimal(rel * 100, 0, SEP) + "%"

    override fun side(k: Int) = "Lato ${k + 1}"
    override fun wall(id: Int) = "Parete $id"
    override fun opening(m: OpeningMark) = when (m) { OpeningMark.DOOR -> "porta"; OpeningMark.WINDOW -> "finestra"; OpeningMark.PASSAGE -> "passaggio" }
    override fun openingKind(k: OpeningKind) = when (k) {
        OpeningKind.Door -> "Porta a 1 anta"
        OpeningKind.Door2 -> "Porta a 2 ante"
        OpeningKind.Window1 -> "Finestra a 1 anta"
        OpeningKind.Window2 -> "Finestra a 2 ante"
        OpeningKind.Balcony1 -> "Balcone a 1 anta"
        OpeningKind.Balcony2 -> "Balcone a 2 ante"
        OpeningKind.FrenchDoor -> "Porta-finestra"
        OpeningKind.Passage -> "Apertura senza porta"
        OpeningKind.Niche -> "Nicchia"
    }
    override fun trackingIssue(i: TrackingIssue) = when (i) {
        TrackingIssue.LOW_LIGHT -> "C'è poca luce: accendi le luci della stanza."
        TrackingIssue.EXCESSIVE_MOTION -> "Muovi il telefono più lentamente."
        TrackingIssue.INSUFFICIENT_FEATURES -> "Inquadra superfici con più dettagli (non solo pareti bianche)."
        TrackingIssue.CAMERA_UNAVAILABLE -> "Fotocamera non disponibile."
    }
    override fun source(s: MeasureSource) = when (s) {
        MeasureSource.SKETCH_ESTIMATE -> "stima dello schizzo"
        MeasureSource.SCAN_CONFIRMED -> "scansionato e confermato"
        MeasureSource.MANUAL -> "misurato a mano"
    }
    override fun startReference(r: StartReference) = when (r) { StartReference.DOOR -> "la porta"; StartReference.WINDOW -> "la finestra"; StartReference.OTHER -> "altro" }

    private fun sides(i: GeometryIssue) = i.sides.joinToString(" e ") { side(it).lowercase() }
    private fun v(i: GeometryIssue, key: String) = i.values[key]

    /** Cosa fare per i blocchi di forma: quasi sempre una parete è finita sul lato sbagliato. */
    private val sidesHint get() = ": di solito una parete è sul lato sbagliato. Controlla la tabella dei lati (sposta le pareti, «$shiftAllBack» / «$shiftAllForward»), oppure togli e riconferma la parete"

    override fun issue(i: GeometryIssue): String = when (i.code) {
        IssueCode.INVALID_INPUT -> "Dati non validi: ripeti la conferma della parete"
        IssueCode.SKETCH_INVALID -> "Lo schizzo non è una stanza valida: correggilo"
        IssueCode.NO_CONFIRMED_WALL -> "Nessuna parete confermata: inquadra la parete di partenza e confermala"
        IssueCode.DUPLICATE_WALL -> "La stessa parete è confermata per ${sides(i)}: togline una con «$remove» e conferma la parete giusta"
        IssueCode.PARALLEL_NO_INTERSECTION -> "${sides(i).replaceFirstChar { it.uppercase() }} risultano paralleli: l'angolo tra loro non esiste. Una delle due pareti è sul lato sbagliato o è un mobile: controlla la tabella dei lati o riconferma una delle due"
        IssueCode.NEARLY_PARALLEL -> "${sides(i).replaceFirstChar { it.uppercase() }} sono quasi paralleli (${v(i, "angoloDeg")?.let(::degrees) ?: "?"}): l'angolo non si può calcolare. Una delle due pareti è sul lato sbagliato o è un mobile: controlla la tabella dei lati o riconferma una delle due"
        IssueCode.UNDERDETERMINED -> "Mancano misure per ${sides(i)}: scansiona un lato o inserisci una lunghezza"
        IssueCode.UNSTABLE_SOLUTION -> "Il calcolo di ${sides(i)} è instabile: scansiona un lato in più"
        IssueCode.INCOMPATIBLE_MEASURES -> "Le misure di ${sides(i)} sono in contraddizione" + pair(i, "calcolataM", "manoM", "calcolata", "a mano") + ": correggi la misura a mano o togli la stima dello schizzo"
        IssueCode.MANUAL_SCAN_CONFLICT -> "La misura a mano di ${sides(i)} non coincide con la scansione" + pair(i, "calcolataM", "manoM", "scansione", "a mano") + ": ricontrolla la misura col metro o riconferma la parete"
        IssueCode.NUMERIC -> "Risultato non valido: ripeti la scansione"
        IssueCode.VERTEX_OUT_OF_SCALE -> "Un angolo cade fuori scala: controlla le pareti confermate"
        IssueCode.VERTEX_UNSTABLE -> "L'angolo tra ${sides(i)} è troppo incerto (${v(i, "sigmaM")?.let { centimeters(it * 100) } ?: "?"}): riscansiona una parete vicino all'angolo"
        IssueCode.DEGENERATE_SIDE -> "${side(i.sides.first())} risulta quasi nullo$sidesHint"
        IssueCode.SIDE_REVERSED -> "${side(i.sides.first())} risulta rovesciato rispetto allo schizzo$sidesHint"
        IssueCode.SPIKE_ANGLE -> "L'angolo tra ${sides(i)} è una punta impossibile$sidesHint"
        IssueCode.SELF_INTERSECTION -> "Il perimetro si incrocia (${sides(i)})$sidesHint"
        IssueCode.WRONG_ORIENTATION -> "Il perimetro gira al contrario dello schizzo: schizzo speculare o pareti sui lati sbagliati"
        IssueCode.AREA_TOO_SMALL -> "La stanza risulta troppo piccola$sidesHint"
        IssueCode.INWARD_MISMATCH -> "La parete di ${sides(i)} guarda verso l'esterno: è il lato giusto? Se no, spostala sul lato giusto; se sì, togli e riconferma la parete stando dentro la stanza"
        IssueCode.OBSERVED_BEYOND_CORNER -> v(i, "altroLato")?.toInt()?.let { other ->
            val k = i.sides.first()
            "${side(k)}: la parete vista prosegue ${v(i, "oltreM")?.let { centimeters(it * 100) } ?: "?"} oltre l'angolo con ${side(other).lowercase()}" +
                (v(i, "angoloDeg")?.let { a -> " (angolo misurato ${degrees(a)}, nello schizzo ${v(i, "schizzoDeg")?.let(::degrees) ?: "?"})" } ?: "") +
                ": quell'angolo è calcolato male. Togli e riconferma la parete di ${side(other).lowercase()}, inquadrandola al centro e " +
                "senza mobili davanti; se non basta, riconferma anche quella di ${side(k).lowercase()}."
        } ?: "La parte vista di ${sides(i)} supera l'angolo di ${v(i, "oltreM")?.let(::meters) ?: "?"}: le pareti non si incontrano lì"
        IssueCode.INWARD_UNKNOWN_ALL -> "Nessuna parete con il lato interno misurato: riconferma una parete tenendo il telefono inquadrato"
        IssueCode.SKETCH_DIRECTION_CONFLICT -> "La parete di ${sides(i)} va al contrario dello schizzo: correggi lo schizzo o l'associazione"
        IssueCode.WALL_CONTINUES -> "${side(i.sides.first())}: l'angolo è ${v(i, "prolungamentoM")?.let(::meters) ?: "?"} oltre la parte vista. La parete continua dietro un mobile?"
        IssueCode.SKETCH_DIRECTION_MISMATCH -> "${side(i.sides.first())}: direzione diversa dallo schizzo di ${v(i, "scartoDeg")?.let(::degrees) ?: "?"}"
        IssueCode.SKETCH_LENGTH_MISMATCH -> "${side(i.sides.first())}: schizzo ${v(i, "schizzoM")?.let(::meters) ?: "?"}, scansione ${v(i, "misurataM")?.let(::meters) ?: "?"}"
        IssueCode.SKETCH_ANGLE_MISMATCH -> "Angolo tra ${sides(i)}: schizzo ${v(i, "schizzoDeg")?.let(::degrees) ?: "?"}, scansione ${v(i, "misuratoDeg")?.let(::degrees) ?: "?"}"
        IssueCode.SKETCH_VERTEX_FAR -> "Angolo tra ${sides(i)} a ${v(i, "distanzaM")?.let(::meters) ?: "?"} da dove lo prevede lo schizzo"
        IssueCode.VERTEX_UNCERTAIN -> "Angolo tra ${sides(i)} incerto (${v(i, "sigmaM")?.let { centimeters(it * 100) } ?: "?"})"
        IssueCode.SKETCH_ESTIMATE_USED -> "Stime dello schizzo, non misure: ${sides(i)}"
        IssueCode.INWARD_UNKNOWN -> "${side(i.sides.first())}: lato interno non misurato, verso preso dallo schizzo"
        IssueCode.START_NOT_CONFIRMED -> "Scegli il lato di partenza e conferma la sua parete mentre la inquadri"
        IssueCode.ASSOCIATION_AMBIGUOUS -> "Associazione ambigua: ${alternatives(v(i, "alternative")?.toInt() ?: 0)} compatibili con le misure. Verifica sul posto la parete di partenza"
        IssueCode.OPENING_CHECK -> "${side(i.sides.first())}: nello schizzo ha un'apertura. C'è davvero su questa parete?"
        IssueCode.ASSOCIATION_CONTRADICTED -> "${side(i.sides.first())}: l'apertura non è sulla parete associata. Riassegna le pareti o correggi lo schizzo"
        IssueCode.POSITION_BY_CONVENTION -> "Una sola parete confermata: la posizione lungo ${sides(i)} non è misurata"
        IssueCode.ANGLES_NEAR_SQUARE -> "Approssimando gli angoli a 90° la pianta cambia fino a " +
            "${v(i, "spostamentoMaxM")?.let { centimeters(it * 100) } ?: "?"} (angoli misurati: fino a ${v(i, "scartoMaxDeg")?.let(::degrees) ?: "?"} da 90°). " +
            "Se la stanza è in squadra conviene approssimare; se è davvero storta, tieni gli angoli della scansione."
    }

    private fun pair(i: GeometryIssue, a: String, b: String, la: String, lb: String): String {
        val x = i.values[a] ?: return ""
        val y = i.values[b] ?: return ""
        return " ($la ${meters(x)}, $lb ${meters(y)})"
    }

    override fun answer(a: Answer) = when (a) {
        Answer.WALL_CONTINUES_BEHIND -> "Sì, continua fino all'angolo"
        Answer.KEEP_MEASURED -> "La misura è giusta"
        Answer.START_WALL_VERIFIED -> "Ho verificato la parete di partenza"
        Answer.OPENING_ON_THIS_WALL -> "Sì, è su questa parete"
        Answer.OPENING_NOT_ON_THIS_WALL -> "No, non è qui"
        Answer.KEEP_SCAN_ANGLES -> "Tieni gli angoli della scansione"
        Answer.SQUARE_ANGLES -> "Approssima a 90°"
    }

    override fun rejection(r: RejectionReason) = when (r) {
        RejectionReason.NO_SUCH_ISSUE -> "risposta a una domanda che non c'è più"
        RejectionReason.NOT_ALLOWED -> "risposta non valida per questa domanda"
        RejectionReason.NEEDS_DATA_CHANGE -> "qui una risposta non basta: vanno corretti i dati"
        RejectionReason.MISSING_DECLARATION -> "indica prima con quale riferimento hai verificato la partenza"
    }

    override fun sessionRejection(r: SessionRejection, side: Int?) = when (r) {
        SessionRejection.INVALID_SIDE -> "Lato non valido"
        SessionRejection.START_NOT_CHOSEN -> "Scegli prima il lato di partenza"
        SessionRejection.ALL_SIDES_ASSIGNED -> "Tutti i lati hanno già una parete"
        SessionRejection.FIRST_MUST_BE_START -> "La prima parete da confermare è quella di partenza" + (side?.let { " (${side(it).lowercase()})" } ?: "")
        SessionRejection.SIDE_ALREADY_ASSIGNED -> (side?.let { side(it) } ?: "Il lato") + " ha già una parete: riassegnala o rimuovila"
        SessionRejection.NO_PROPOSAL -> "Nessuna parete proposta: inquadra la parete e tieni fermo il telefono"
        SessionRejection.DUPLICATE_WALL -> "Questa parete è già confermata"
        SessionRejection.CONFIRM_FAILED -> "Conferma non riuscita: riprova"
        SessionRejection.START_NOT_CONFIRMED -> "Conferma prima la parete di partenza"
        SessionRejection.WALLS_ALREADY_ASSIGNED -> "Ci sono già pareti: per cambiare la partenza riassegnale o rimuovile"
        SessionRejection.SIDE_HAS_NO_WALL -> (side?.let { side(it) } ?: "Il lato") + " non ha una parete"
        SessionRejection.NOTHING_TO_SHIFT -> "Niente da spostare"
        SessionRejection.OFF_SKETCH_DIRECTION -> "Questa superficie non corrisponde al lato giallo dello schizzo"
    }

    override fun candidateState(s: CandidateState) = when (s) {
        CandidateState.SEARCHING -> "Cerca una parete"
        CandidateState.TRACKING -> "Stabilizzo"
        CandidateState.PROPOSED -> "Parete proposta"
    }

    override fun reason(r: Reason) = when (r) {
        Reason.AIM_SHORT -> "tieni il telefono puntato ancora un attimo"
        Reason.PLANE_NEW -> "la parete è appena comparsa"
        Reason.TOO_FAST -> "il telefono si muove troppo"
        Reason.HEADING_UNSTABLE -> "direzione instabile"
        Reason.OFFSET_UNSTABLE -> "posizione instabile"
        Reason.TILTED -> "superficie inclinata"
        Reason.SHORT -> "parte vista troppo corta"
        Reason.LOW -> "parte vista troppo bassa"
        Reason.FEW_SAMPLES -> "pochi dati"
        Reason.NO_TRACKING -> "tracciamento perso"
        Reason.AIM_LOST -> "mira persa"
    }

    override fun advisory(a: Advisory) = when (a) {
        Advisory.NOT_REACHING_FLOOR -> "non arriva a terra: potrebbe essere un mobile"
        Advisory.PARALLEL_BEHIND -> "c'è già una parete confermata dietro: potrebbe essere un mobile"
        Advisory.POINTS_DISAGREE -> "i punti non concordano con la superficie"
        Advisory.FEW_POINTS -> "poca evidenza dai punti"
        Advisory.FAR -> "molto lontana"
    }

    override fun wallsConfirmed(count: Int) = if (count == 1) "1 parete confermata" else "$count pareti confermate"
    override fun alternatives(count: Int) = if (count == 1) "1 altra associazione" else "$count altre associazioni"

    override val chooseStartTitle = "Da quale lato parti?"
    override val chooseStartHint = "Tocca il lato dello schizzo da cui cominci. La porta lo suggerisce, ma sei tu a sceglierlo."
    override val suggestedStart = "consigliato: ha la porta"
    override val aimHint = "Punta il centro dello schermo sulla parete e tieni fermo il telefono"
    override val confirmWall = "Conferma parete"
    override val nextSideTitle = "Lato da scansionare"
    override val associationTitle = "Lati dello schizzo e pareti"
    override val noWall = "nessuna parete"
    override val startMark = "partenza"
    override val targetMark = "da scansionare"
    override val inwardNotMeasured = "lato interno non misurato"
    override val remove = "Rimuovi"
    override val moveTo = "Sposta su"
    override val shiftAllBack = "Tutte −1 lato"
    override val shiftAllForward = "Tutte +1 lato"
    override val questionsTitle = "Domande"
    override val changeChoice = "Cambia scelta"
    override val blockingTitle = "Da risolvere prima di confermare"
    override val warningsTitle = "Avvisi"
    override val declarationTitle = "Con che cosa hai verificato la parete di partenza?"
    override val declarationNoteHint = "Descrivi il riferimento (facoltativo)"
    override fun declaredByUser(r: StartReference, note: String) =
        "Hai dichiarato: verificata con ${startReference(r)}" + (if (note.isNotBlank()) " ($note)" else "") + ". È una tua dichiarazione, non un controllo dell'app."
    override val manualLengthTitle = "Lati senza misura"
    override val manualLengthLabel = "Lunghezza misurata a mano"
    override val useSketchLength = "Usa la stima dello schizzo"
    override val reviewTitle = "Revisione"
    override fun sideLine(k: Int, length: Double, sketch: Double, sources: Set<MeasureSource>, estimate: Boolean) =
        "${side(k)}: ${meters(length)} (schizzo ${meters(sketch)}) · " + sources.joinToString(", ") { source(it) } + if (estimate) " · STIMA" else ""
    override fun cornerLine(k: Int, angleDeg: Double, conventional: Boolean) =
        "Angolo ${k + 1}: ${degrees(angleDeg)}" + if (conventional) " · posizione per convenzione, non misurata" else ""
    override fun areaPerimeter(areaM2: Double, perimeterM: Double) = "Area ${lengths.squareMeters(areaM2)} · perimetro ${meters(perimeterM)}"
    override val openingsTitle = "Porte e finestre"
    override fun openingLine(kind: String, side: Int, oldPos: Double, newPos: Double, width: Double, fits: Boolean) =
        "$kind, ${side(side).lowercase()}: centro ${centimeters(oldPos)} → ${centimeters(newPos)}, larghezza ${centimeters(width)}" +
            if (fits) "" else " · NON entra nel muro"
    override val openingPositionLabel = "Centro dall'inizio del muro (cm)"
    override val confirmRoom = "Conferma stanza"
    override val exit = "Esci"
    override val record = "● Registra"
    override val stopRecording = "■ Ferma"
    override val export = "Esporta registrazione"
    override val trackingLost = "Muovi lentamente il telefono"
    override val exitConfirm = "Uscendo perderai la sessione corrente: pareti confermate, risposte e misure manuali. Vuoi uscire?"
    override val exitConfirmYes = "Esci e perdi la scansione"
    override val stay = "Continua la scansione"
    override val openingWidthLabel = "Larghezza (cm), al massimo quanto il muro"
    override val recordingConsentTitle = "Registrare la scansione?"
    override val recordingConsentText = "La registrazione salva su questo telefono immagini della stanza, profondità e posizioni del telefono, per analizzare la scansione. Non viene inviata a nessuno: la esporti tu, se vuoi, e puoi cancellarla."
    override val recordingConsentYes = "Sì, registra"
    override val deleteRecording = "Cancella registrazione"
    override val recordingDeleted = "Registrazione cancellata"
    override val legendStart = "● partenza"
    override val legendTarget = "┅ da scansionare"
    override val legendAssigned = "━ con parete"
    override val arChecking = "Controllo la realtà aumentata…"
    override val cameraTitle = "Serve la fotocamera"
    override val cameraMessage = "Per misurare la stanza Sagoma deve usare la fotocamera. Non salva né invia immagini, salvo la registrazione che avvii tu."
    override val cameraDenied = "Senza il permesso della fotocamera non posso misurare la stanza."
    override val unsupportedTitle = "Telefono non compatibile"
    override val unsupportedMessage = "Questo telefono non supporta ARCore: la scansione non è disponibile. Le misure si possono inserire a mano."
    override val checkFailedTitle = "Verifica non riuscita"
    override val checkFailedMessage = "Non riesco a verificare se questo telefono supporta ARCore. Controlla la connessione e riprova più tardi."
    override val allowCamera = "Consenti la fotocamera"
    override val openSettings = "Apri le impostazioni"
    override val cancel = "Annulla"
    override val back = "Indietro"
    override val installingArcore = "Sto installando ARCore: completa l'installazione e torna qui"
    override val unavailableTitle = "Scansione non disponibile"
    override val recordingExported = "Registrazione esportata"
    override val recordingExportFailed = "Esportazione non riuscita"

    override val knowMeasuresQuestion = "Conosci le misure della stanza?"
    override val knowMeasuresYes = "Sì"
    override val knowMeasuresYesHint = "Inserisci le misure o disegni la stanza come sempre"
    override val knowMeasuresNo = "No"
    override val knowMeasuresNoHint = "Disegni la forma con misure approssimative"
    override val approxTitle = "Come vuoi procedere?"
    override val approxOnly = "Forma e misure approssimative"
    override val approxOnlyHint = "Disegni uno schizzo della stanza; potrai misurarla con il telefono anche più tardi"
    override val approxWithScan = "Forma e misure approssimative + scansione"
    override val approxWithScanHint = "Disegni lo schizzo, aggiungi porte e finestre, poi avvii la scansione per misure più precise"
    override val sketchShapeTitle = "Schizzo della stanza: scegli la forma"
    override val approximateMeasuresHint = "Misure indicative: le preciserà la scansione con il telefono, che avvii dopo con il tasto apposito"
    override val sketchBanner = "Schizzo da misurare con il telefono"
    override val startScan = "Avvia scansione"
    override val later = "Chiudi"

    override fun sideName(k: Int, openings: List<OpeningMark>) = side(k) + if (openings.isEmpty()) "" else " · " + openings.distinct().joinToString(", ") { opening(it) }
    override fun wallOfTotal(k: Int, n: Int) = "Parete $k di $n"
    override fun progress(done: Int, total: Int) = "Completate $done di $total"
    override fun phaseTitle(p: GuidePhase) = when (p) {
        GuidePhase.START -> "Scegli la parete di partenza"
        GuidePhase.SCAN -> "Scansione delle pareti"
        GuidePhase.QUESTION -> "Una domanda"
        GuidePhase.REVIEW -> "Revisione"
    }
    override val startQuestion = "Da quale parete cominci?"
    override val startExplain = "Mettiti davanti a una parete e tocca il lato corrispondente dello schizzo. Di solito conviene la parete con la porta."
    override val suggestedTag = "consigliata"
    override val recordFirstHint = "Prova: avvia la registrazione prima di cominciare."
    override fun aimInstruction(side: String) = "Inquadra $side: punta il mirino al centro della parete"
    override fun aimStatus(s: AimStatus) = when (s) {
        AimStatus.NO_TRACKING -> "Muovi lentamente il telefono finché la scena viene riconosciuta"
        AimStatus.SEARCHING -> "Cerco la parete: avvicinati o muovi lentamente il telefono"
        AimStatus.STABILIZING -> "Parete trovata: tieni fermo il telefono"
        AimStatus.READY -> "Parete pronta: premi «Conferma parete»"
        AimStatus.ALREADY_TAKEN -> "Questa parete è già acquisita: inquadra quella indicata"
        AimStatus.OFF_SKETCH -> "Questa superficie non corrisponde al lato giallo dello schizzo: inquadra la parete giusta"
    }
    override val changeStart = "Cambia parete di partenza"
    override val scanOtherWall = "Scansiona un'altra parete"
    override val chooseNextTitle = "Quale parete vuoi scansionare ora?"
    override val undoLastWall = "Annulla l'ultima parete"
    override val goToReview = "Vai alla revisione"
    override val backToScan = "Torna alla scansione"
    override val questionTitle = "Una domanda prima di proseguire"
    override val reviewAllGood = "Tutto a posto: controlla le misure e applica la stanza."
    override val reviewTodoTitle = "Da sistemare prima di applicare"
    override val reviewQuestionsTitle = "Da confermare"
    override fun wallsMissing(count: Int) = if (count == 1) "Manca 1 parete: torna alla scansione oppure inserisci la misura a mano." else "Mancano $count pareti: torna alla scansione oppure inserisci le misure a mano."
    override val angleTitle = "Angoli della stanza"
    override fun angleExplain(shiftM: Double?, maxDeviationDeg: Double?) =
        "La scansione misura gli angoli con qualche grado di incertezza" + (maxDeviationDeg?.let { ": qui fino a ${degrees(it)} da 90°" } ?: "") + ". " +
            "Se la stanza è in squadra, approssimarli a 90° di solito rende la pianta più precisa; se è davvero storta, tieni gli angoli rilevati." +
            (shiftM?.let { " Approssimando, gli angoli della pianta si spostano al massimo di ${centimeters(it * 100)}." } ?: "")
    override val measuresTitle = "Misure risultanti"
    override val openingsAdjustTitle = "Porte e finestre (facoltativo)"
    override val applyRoom = "Applica alla stanza"
    override val applyNeedsFixes = "Per applicare, sistema prima i punti indicati sopra."
    override val detailsShow = "Dettagli e strumenti"
    override val detailsHide = "Nascondi dettagli"
    override val recordingOn = "● Registrazione in corso"

    override val introSteps = listOf(
        "Mettiti di fronte alla parete.",
        "Punta il mirino al centro della parete e muovi lentamente il telefono.",
        "Quando il mirino diventa giallo, premi «Conferma parete».",
    )
    override val begin = "Inizia"
    override val next = "Avanti"
    override val menu = "Menu"
    override val menuTitle = "Cosa vuoi fare?"
    override val readInstructions = "Rileggi le istruzioni"
    override val close = "Chiudi"
    override fun wallDone(side: String) = "✓ Fatto: $side"
    override val allWallsDone = "✓ Tutte le pareti sono fatte"
    override val allWallsDoneExplain = "Ora controlliamo insieme la stanza, un passo alla volta."
    override fun nextWall(k: Int, n: Int) = "Ora la parete $k di $n"
    override fun turnHint(t: Turn) = when (t) {
        Turn.RIGHT -> "È subito alla tua destra: girati verso destra."
        Turn.LEFT -> "È subito alla tua sinistra: girati verso sinistra."
    }
    override val turnUnknown = "Guarda lo schizzo: è il lato giallo."
    override val redoWall = "Rifai una parete"
    override val redoTitle = "Quale parete vuoi rifare?"
    override val redoExplain = "La scansione della parete scelta viene cancellata e la inquadri di nuovo subito."
    override fun redoSide(side: String) = "Rifai $side"
}

val LocalSketchScanText = staticCompositionLocalOf<SketchScanText> { SketchScanTextIt() }
