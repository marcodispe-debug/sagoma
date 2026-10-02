package com.sagoma.planimetria.editor

import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2

/** Vista: schermo(px) = mondo(cm) × scale + offset. */
data class Camera(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    fun toScreenX(x: Double) = (x * scale + offsetX).toFloat()
    fun toScreenY(y: Double) = (y * scale + offsetY).toFloat()
    fun toWorld(px: Float, py: Float) = Vec2(((px - offsetX) / scale).toDouble(), ((py - offsetY) / scale).toDouble())

    companion object {
        const val MIN_SCALE = 0.02f
        const val MAX_SCALE = 40f
    }
}

/** Oggetto selezionato che apre il riquadro caratteristiche (§10). */
sealed interface Selection {
    val roomId: Long

    data class Wall(override val roomId: Long, val index: Int) : Selection
    data class Opening(override val roomId: Long, val openingId: Long) : Selection
    data class Fixture(override val roomId: Long, val fixtureId: Long) : Selection
    /** Una scala non appartiene a una stanza: `roomId` non corrisponde a nessuna. */
    data class Stair(val stairId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }

    data class Column(val columnId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }
    data class Beam(val beamId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }
    data class FreeWall(val wallId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }
    /** Arredo (versione pro). */
    data class Furniture(val furnitureId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }

    /** Vano di una scala che sale dal piano di sotto (`stairId` è una scala del piano di sotto). */
    data class StairWell(val stairId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }

    /** Quota manuale e testo del disegno. */
    data class Dimension(val dimensionId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }
    data class Annotation(val annotationId: Long) : Selection {
        override val roomId: Long get() = NO_ROOM
    }

    companion object {
        const val NO_ROOM = -1L
    }
}

/** Apertura scelta dai pulsanti, in attesa del tocco su un muro della stanza attiva (§5). */
data class PendingOpening(
    val kind: OpeningKind,
    val style: PassageStyle = PassageStyle.Square,
    /** Porte, finestre e balconi scorrevoli invece che a battente. */
    val sliding: Boolean = false,
)

/** Passi del flusso di creazione stanza (§1). */
sealed interface CreationStep {
    val cancellable: Boolean

    data class PickShape(override val cancellable: Boolean) : CreationStep
    data class PickLOrientation(override val cancellable: Boolean) : CreationStep
    data class Measures(
        val shape: RoomShape,
        val orientation: LOrientation?,
        override val cancellable: Boolean,
    ) : CreationStep

    /** Stanza da rilievo: lati e diagonali misurati sul posto. */
    data class Survey(override val cancellable: Boolean) : CreationStep
}

/** Elemento afferrato con un trascinamento sulla pianta. */
sealed interface DragTarget {
    data class Corner(val roomId: Long, val index: Int) : DragTarget
    data class Wall(val roomId: Long, val index: Int) : DragTarget
    /** Linea di un'apertura: si trascina lungo il proprio muro. */
    data class Opening(val roomId: Long, val openingId: Long) : DragTarget
    /** Impianto: a muro scorre lungo il muro, al soffitto si sposta liberamente. */
    data class Fixture(val roomId: Long, val fixtureId: Long) : DragTarget
    /** Pallino sul muro selezionato con il taglio diagonale: il punto da cui la parete inizia a scendere. */
    data class CutStart(val roomId: Long, val index: Int) : DragTarget
    /** Etichetta del nome: sposta la stanza intera. */
    data class RoomLabel(val roomId: Long) : DragTarget
    /** Interno della stanza: un tocco la mette a fuoco, un trascinamento sposta la vista. */
    data class RoomInterior(val roomId: Long) : DragTarget
    /** Metro (§9): estremità (0 = inizio, 1 = fine), corpo, maniglia di rotazione, pulsante ×. */
    data class RulerEnd(val rulerId: Long, val endIndex: Int) : DragTarget
    data class RulerBody(val rulerId: Long) : DragTarget
    data class RulerRotate(val rulerId: Long) : DragTarget
    data class RulerDelete(val rulerId: Long) : DragTarget
    /** Scala: si sposta intera (e si accosta ai muri). */
    data class Stair(val stairId: Long) : DragTarget
    /** Colonna: si sposta intera (e si accosta ai muri). */
    data class Column(val columnId: Long) : DragTarget
    /** Trave: il corpo la sposta intera, le estremità (0 = inizio, 1 = fine) la allungano. */
    data class BeamBody(val beamId: Long) : DragTarget
    data class BeamEnd(val beamId: Long, val endIndex: Int) : DragTarget
    /** Muro singolo: il corpo lo sposta, le estremità (0 = inizio, 1 = fine) lo allungano e lo ruotano. */
    data class FreeWallBody(val wallId: Long) : DragTarget
    data class FreeWallEnd(val wallId: Long, val endIndex: Int) : DragTarget
    /** Arredo: il corpo lo sposta (accostandolo ai muri), la maniglia lo ruota. */
    data class FurnitureBody(val furnitureId: Long) : DragTarget
    data class FurnitureRotate(val furnitureId: Long) : DragTarget
    /** Vano di una scala del piano di sotto: si tocca (per la ringhiera), non si sposta. */
    data class StairWell(val stairId: Long) : DragTarget
    /** Quota manuale: la linea la allontana o avvicina, le estremità (0 = a, 1 = b) si spostano con l'aggancio. */
    data class DimensionLine(val dimensionId: Long) : DragTarget
    data class DimensionEnd(val dimensionId: Long, val endIndex: Int) : DragTarget
    /** Testo: si sposta intero; la punta della freccia di richiamo si sposta da sola. */
    data class AnnotationBody(val annotationId: Long) : DragTarget
    data class AnnotationArrow(val annotationId: Long) : DragTarget
    data object Background : DragTarget
}

/** Eliminazione del muro `index` di `roomId` in comune con `otherRoomId`: le due stanze diventano una. */
data class MergeRequest(val roomId: Long, val index: Int, val otherRoomId: Long)

/** Misura di una distanza in corso: il primo e il secondo oggetto toccati (null = ancora da scegliere). */
data class MeasureState(val first: MeasureTarget? = null, val second: MeasureTarget? = null)

/**
 * Strumenti della pianta di sfondo. [Move]: un dito trascina l'immagine. [Calibrate]: si toccano i due
 * estremi di una misura nota dell'immagine (`first`, `second` in cm della pianta), poi se ne scrive la lunghezza.
 */
sealed interface UnderlayTool {
    data object Move : UnderlayTool
    data class Calibrate(val first: Vec2? = null, val second: Vec2? = null) : UnderlayTool
}

/**
 * Strumento "Disegna muri": angoli già messi (in mezzeria dei muri), punto sotto il puntatore (anteprima del
 * prossimo muro) e, a forma chiusa, la richiesta del tipo di stanza.
 */
data class WallDraw(val points: List<Vec2> = emptyList(), val cursor: Vec2? = null, val closing: Boolean = false)

/**
 * Appunti di "Copia": un impianto o un'apertura (si incollano toccando un muro, le luci dentro una stanza)
 * oppure oggetti della pianta (si incollano col centro nel punto toccato). Restano anche cambiando piano.
 */
sealed interface Clip {
    data class FixtureClip(val fixture: com.sagoma.planimetria.model.Fixture) : Clip
    data class OpeningClip(val opening: com.sagoma.planimetria.model.Opening) : Clip
    data class GroupClip(val source: FloorPlan, val items: Set<com.sagoma.planimetria.geometry.GroupItem>, val center: Vec2) : Clip
}

/**
 * Strumento "Quota": primo punto già messo (null = ancora da mettere) e punto sotto il puntatore (anteprima,
 * già agganciato).
 */
data class DimensionDraw(val first: Vec2? = null, val cursor: Vec2? = null)

/** Richiesta di un nuovo piano (finestra "Aggiungi piano"), o di modifica di un piano esistente (`index`). */
data class FloorDialog(val index: Int?)

data class EditorUiState(
    /** Pianta del piano che si sta modificando. */
    val plan: FloorPlan = FloorPlan(),
    /**
     * Tutti i piani. La pianta del piano corrente qui può essere vecchia: quella giusta è [plan]
     * (si aggiorna a ogni trascinamento); la casa completa è [fullBuilding].
     */
    val building: Building = Building.single(FloorPlan()),
    /** Finestra per aggiungere o modificare un piano. */
    val floorDialog: FloorDialog? = null,
    /** Strumento "Distanza" attivo: i tocchi scelgono gli oggetti da misurare. */
    val measure: MeasureState? = null,
    /** Stanza attiva e a fuoco (§4); null = nulla selezionato. */
    val focusedRoomId: Long? = null,
    val selection: Selection? = null,
    val creation: CreationStep? = null,
    /** Tipo proposto nella creazione (es. balcone dal pulsante "+ Balcone o terrazza"); null = scelta libera. */
    val creationType: RoomType? = null,
    /** Trascinamento in corso sulla pianta: la vista non va ricentrata mentre il dito si muove. */
    val dragging: Boolean = false,
    val pendingOpening: PendingOpening? = null,
    /**
     * Tendina delle informazioni aperta. Selezionando qualcosa compare solo la striscia "Info" in basso:
     * la tendina si apre e si chiude a richiesta, con Info o con la freccia.
     */
    val infoExpanded: Boolean = false,
    /** Suggerimento al primo utilizzo da mostrare (null = nessuno). */
    val tip: Tip? = null,
    /** Tutorial guidato in corso (null = nessuno). */
    val tutorial: TutorialUi? = null,
    /** Vista 3D al posto della pianta 2D. */
    val view3d: Boolean = false,
    /** Impianto scelto dal menu, in attesa del tocco su un muro (a muro) o dentro una stanza (luci). */
    val pendingFixture: FixtureKind? = null,
    /** Stanza per cui è aperta la richiesta di conferma eliminazione. */
    val confirmDeleteRoomId: Long? = null,
    /** Muro in comune per cui è aperta la richiesta di conferma "elimina muro e unisci le stanze". */
    val confirmMergeWall: MergeRequest? = null,
    /** Metro in rotazione: i gradi si mostrano solo durante il trascinamento della maniglia. */
    val rotatingRulerId: Long? = null,
    val camera: Camera = Camera(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Selezione multipla: oggetti selezionati insieme (vuota = nessuna). */
    val multi: Set<com.sagoma.planimetria.geometry.GroupItem> = emptySet(),
    /** Modalità "Seleziona": i trascinamenti sul vuoto disegnano il rettangolo, i tocchi aggiungono o tolgono. */
    val selectMode: Boolean = false,
    /** Rettangolo di selezione in corso (angolo di partenza e angolo opposto, in cm). */
    val selectRect: Pair<Vec2, Vec2>? = null,
    /** L'ultimo salvataggio su file non è riuscito (il motivo); null = tutto salvato. */
    val saveError: String? = null,
    /** Ultimo oggetto copiato (null = niente). */
    val clipboard: Clip? = null,
    /** Modalità "Incolla": ogni tocco mette una copia, finché non si preme Fine. */
    val pasting: Boolean = false,
    /** Punto sotto il puntatore durante "Incolla" (anteprima sul computer). */
    val pasteCursor: Vec2? = null,
    /** Stato di fatto / progetto: come si guarda la pianta (e il 3D). */
    val phaseView: com.sagoma.planimetria.geometry.PhaseView = com.sagoma.planimetria.geometry.PhaseView.Compare,
    /** Strumento "Quota" attivo (null = no). */
    val dimensionDraw: DimensionDraw? = null,
    /** Strumento "Testo": il prossimo tocco sceglie dove scrivere. */
    val textPlacing: Boolean = false,
    /** Punto scelto per un nuovo testo: si apre la finestra per scriverlo. */
    val textDialogAt: Vec2? = null,
    /** Strumento "Disegna muri" attivo (null = no). */
    val wallDraw: WallDraw? = null,
    /** Guide dell'aggancio durante un trascinamento (linee tratteggiate e nome dell'aggancio). */
    val snapGuides: List<com.sagoma.planimetria.geometry.SnapGuide> = emptyList(),
    /** Pianta di sfondo: si sta spostando o tarando (i tocchi vanno a lei invece che al disegno). */
    val underlayTool: UnderlayTool? = null,
) {
    val fullBuilding: Building get() = building.withPlan(plan)
    /** Altezza che devono superare le scale di questo piano (interpiano). */
    val levelHeight: Double get() = building.floor.levelHeight
    /** Pianta del piano di sotto (null al piano più basso). */
    val planBelow: FloorPlan? get() = building.below?.plan
    /** Interpiano del piano di sotto: l'altezza delle scale che arrivano qui. */
    val levelHeightBelow: Double get() = building.below?.levelHeight ?: levelHeight
}
