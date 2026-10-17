package com.sagoma.planimetria.ui.sketch

import com.sagoma.planimetria.scan.assisted.CandidateState
import com.sagoma.planimetria.scan.sketch.GeometryIssue
import com.sagoma.planimetria.scan.sketch.IssueCode
import com.sagoma.planimetria.scan.sketch.RoomSketch
import com.sagoma.planimetria.scan.sketch.SessionResult
import com.sagoma.planimetria.scan.sketch.Severity
import com.sagoma.planimetria.scan.sketch.SketchSession

/*
 * La scansione guidata come PROCEDURA A PASSI: questo file decide cosa mostrare (fase, parete attiva, progresso, prossima azione,
 * domande da fare adesso), la schermata lo disegna. Nessuna geometria qui: tutto viene dalla sessione e dal suo risultato, che
 * non cambiano. Puro, quindi provabile sul computer.
 */

/** Le fasi, nell'ordine in cui l'utente le attraversa. */
enum class GuidePhase {
    /** Scegliere (o confermare il suggerimento de) la parete da cui partire. */
    START,
    /** Inquadrare e confermare la parete attiva, una alla volta. */
    SCAN,
    /** Una domanda su una parete appena acquisita (aperture, partenza ambigua): si risponde e si prosegue. */
    QUESTION,
    /** Tutte le pareti (o la scelta di fermarsi): problemi da sistemare, scelta degli angoli, misure, "Applica". */
    REVIEW,
}

/** Cosa sta succedendo con la parete inquadrata, in parole semplici (il dettaglio tecnico resta nei dettagli). */
enum class AimStatus {
    /** Tracciamento perso: muovere lentamente il telefono. */
    NO_TRACKING,
    /** Nessuna parete inquadrata. */
    SEARCHING,
    /** Parete inquadrata, si sta stabilizzando: tenere fermo. */
    STABILIZING,
    /** Parete pronta: si può confermare. */
    READY,
    /** La superficie inquadrata non ha la direzione che lo schizzo prevede per la parete attiva: non si mostra né si conferma. */
    OFF_SKETCH,
    /** È una parete già acquisita: inquadrare quella indicata. */
    ALREADY_TAKEN,
}

/** Lo stato della procedura per la schermata. */
data class GuideState(
    val phase: GuidePhase,
    /** Il lato dello schizzo da acquisire ora (null se non c'è). */
    val activeSide: Int?,
    /** Numero della parete in corso, da 1 (in [GuidePhase.SCAN]: "Parete [wallNumber] di [total]"). */
    val wallNumber: Int,
    val total: Int,
    val done: Int,
    val remaining: Int,
    /** Lati con la parete acquisita. */
    val doneSides: Set<Int>,
    val startSide: Int?,
    val suggestedStart: Int?,
    val aim: AimStatus,
    /** "Conferma parete" possibile adesso. */
    val canConfirmWall: Boolean,
    /** Domande da fare adesso (in [GuidePhase.QUESTION]) o da fare in revisione. */
    val questions: List<GeometryIssue>,
    /** Problemi che impediscono di applicare (in revisione). */
    val blocking: List<GeometryIssue>,
    /** La scelta tra angoli rilevati e 90° (in revisione, se proposta). */
    val angleChoice: GeometryIssue?,
    /** "Applica alla stanza" possibile: stanza confermabile (criteri della sessione) e aperture applicabili. */
    val canApply: Boolean,
    /** Si può tornare dalla revisione alla scansione (mancano pareti). */
    val canResumeScan: Boolean,
    /** L'ultima parete confermata (per "Fatto: lato 2"), null se nessuna. */
    val lastDoneSide: Int? = null,
    /** Da che parte girarsi per la parete attiva, partendo dall'ultima confermata; null se non si può dire in modo semplice. */
    val nextTurn: Turn? = null,
)

/** Da che parte si trova la parete successiva per chi sta dentro la stanza, rivolto verso quella appena fatta. */
enum class Turn { LEFT, RIGHT }

/**
 * Le finestre della procedura a passi, una alla volta. Nessuna finestra (null) = la fotocamera è libera e si inquadra la parete,
 * con solo la barra in alto e "Conferma parete" in basso.
 */
enum class GuideWindow {
    /** Da quale parete cominci. */
    START,
    /** Istruzioni per la parete attiva (la prima, o una scelta/ripresa a mano). */
    WALL_INTRO,
    /** "Fatto" dopo una parete confermata, con la prossima e da che parte girarsi (o "tutte fatte"). */
    WALL_DONE,
    /** Una domanda da fare adesso. */
    QUESTION,
    /** Angoli rilevati o approssimati a 90°. */
    ANGLES,
    /** Problemi che impediscono di applicare la stanza. */
    FIX,
    /** Misure risultanti e "Applica alla stanza". */
    SUMMARY,
}

/** Quello che la schermata ricorda delle finestre già chiuse con "Inizia"/"Avanti". */
data class WindowMemory(
    /** Lato per cui l'utente ha già letto le istruzioni. */
    val introSeenFor: Int? = null,
    /** Pareti completate quando l'utente ha chiuso l'ultimo "Fatto". */
    val doneSeen: Int = 0,
) {
    /** Dopo aver tolto pareti il conteggio scende: la prossima conferma deve di nuovo mostrare "Fatto". */
    fun normalized(done: Int) = if (doneSeen > done) copy(doneSeen = done) else this

    /** Chiuso "Fatto": visto il conteggio attuale e le istruzioni della parete successiva (sono nella stessa finestra). */
    fun afterDone(g: GuideState) = WindowMemory(introSeenFor = g.activeSide, doneSeen = g.done)
}

object SketchScanGuide {
    /** Domande che riguardano una parete appena acquisita: si fanno subito, durante la scansione. */
    val CONTEXTUAL = setOf(IssueCode.OPENING_CHECK, IssueCode.ASSOCIATION_AMBIGUOUS)

    /**
     * [reviewRequested]: l'utente ha scelto di passare alla revisione prima di acquisire tutte le pareti. [tracking]: stato del
     * tracciamento di ARCore. [openingsApplicable]: le aperture entrano nei muri nuovi (vedi `SketchApply`). [choosingStart]: l'utente
     * ha chiesto di cambiare la parete di partenza (possibile solo prima di aver acquisito pareti). [candidate]: la parete proposta dal
     * motore (di norma quella della sessione; i test la passano direttamente).
     */
    fun of(
        session: SketchSession,
        result: SessionResult,
        tracking: Boolean,
        reviewRequested: Boolean,
        openingsApplicable: Boolean,
        choosingStart: Boolean = false,
        candidate: com.sagoma.planimetria.scan.assisted.CandidateWall? = session.candidate,
    ): GuideState {
        val n = session.n
        val done = session.assigned.keys
        val g = result.geometry
        val openQuestions = g.issues.filter { it.severity == Severity.QUESTION && it.answer == null }
        val contextual = openQuestions.filter { it.code in CONTEXTUAL }
        val angle = g.issues.firstOrNull { it.code == IssueCode.ANGLES_NEAR_SQUARE }
        val allDone = done.size == n
        val lastDone = session.assigned.maxByOrNull { it.value.confirmedAtMs }?.key
        val phase = when {
            session.startSide == null || (choosingStart && session.assigned.isEmpty()) -> GuidePhase.START
            contextual.isNotEmpty() && done.isNotEmpty() -> GuidePhase.QUESTION
            allDone || reviewRequested -> GuidePhase.REVIEW
            else -> GuidePhase.SCAN
        }
        val active = when (phase) {
            GuidePhase.START, GuidePhase.REVIEW, GuidePhase.QUESTION -> null
            GuidePhase.SCAN -> session.target // prima la partenza (la sessione la impone), poi il lato successivo libero
        }
        val c = candidate
        val aim = when {
            !tracking -> AimStatus.NO_TRACKING
            c == null -> AimStatus.SEARCHING
            c.duplicateOf != null -> AimStatus.ALREADY_TAKEN
            !session.fitsSketch(c) -> AimStatus.OFF_SKETCH
            c.state == CandidateState.PROPOSED -> AimStatus.READY
            c.state == CandidateState.TRACKING -> AimStatus.STABILIZING
            else -> AimStatus.SEARCHING
        }
        return GuideState(
            phase = phase,
            activeSide = active,
            wallNumber = (done.size + 1).coerceAtMost(n),
            total = n,
            done = done.size,
            remaining = n - done.size,
            doneSides = done,
            startSide = session.startSide,
            suggestedStart = session.suggestedStartSide,
            aim = aim,
            canConfirmWall = phase == GuidePhase.SCAN && active != null && aim == AimStatus.READY,
            questions = if (phase == GuidePhase.QUESTION) contextual else openQuestions.filter { it.code != IssueCode.ANGLES_NEAR_SQUARE },
            blocking = g.blocking,
            angleChoice = angle,
            canApply = phase == GuidePhase.REVIEW && result.canConfirm && openingsApplicable,
            canResumeScan = phase == GuidePhase.REVIEW && !allDone,
            lastDoneSide = lastDone,
            nextTurn = if (lastDone != null && active != null) turn(session.sketch, lastDone, active) else null,
        )
    }

    /**
     * Quale finestra mostrare adesso. Ordine: la partenza; le domande sulla parete appena presa; il "Fatto"; le istruzioni della
     * parete attiva; poi la fotocamera libera. In revisione, una cosa alla volta: domande, angoli, problemi, riepilogo.
     */
    fun window(g: GuideState, memory: WindowMemory): GuideWindow? {
        val m = memory.normalized(g.done)
        return when (g.phase) {
            GuidePhase.START -> GuideWindow.START
            GuidePhase.QUESTION -> GuideWindow.QUESTION
            GuidePhase.SCAN -> when {
                g.done > m.doneSeen -> GuideWindow.WALL_DONE
                g.activeSide != m.introSeenFor -> GuideWindow.WALL_INTRO
                else -> null
            }
            GuidePhase.REVIEW -> when {
                g.remaining == 0 && g.done > m.doneSeen -> GuideWindow.WALL_DONE
                g.questions.isNotEmpty() -> GuideWindow.QUESTION
                g.angleChoice != null && g.angleChoice.answer == null -> GuideWindow.ANGLES
                g.blocking.isNotEmpty() -> GuideWindow.FIX
                else -> GuideWindow.SUMMARY
            }
        }
    }

    /**
     * Da che parte è il lato [to] per chi sta nella stanza rivolto verso il lato [from]. Solo per lati vicini e solo se l'angolo
     * tra i due sporge verso l'esterno (angolo "normale"): in un angolo rientrante (stanze a L) "girati a destra" confonderebbe,
     * e allora null (resta lo schizzo). Lo schizzo in pianta è la vista dall'alto (x a destra, z in basso).
     */
    fun turn(sketch: RoomSketch, from: Int, to: Int): Turn? {
        val n = sketch.sideCount
        val area = sketch.signedArea
        if (n < 3 || area == 0.0 || from == to) return null
        fun m(k: Int) = ((k % n) + n) % n
        fun dir(k: Int) = sketch.end(k) - sketch.start(k)
        /** L'angolo all'inizio del lato k sporge verso l'esterno. */
        fun convex(k: Int) = dir(m(k - 1)).cross(dir(m(k))) * area > 0
        // Area con segno positiva: i lati girano in senso orario sullo schizzo, il successivo è alla destra di chi guarda la parete.
        val forward = if (area > 0) Turn.RIGHT else Turn.LEFT
        val backward = if (area > 0) Turn.LEFT else Turn.RIGHT
        return when {
            m(to) == m(from + 1) && convex(m(to)) -> forward
            m(to) == m(from - 1) && convex(m(from)) -> backward
            else -> null
        }
    }
}
