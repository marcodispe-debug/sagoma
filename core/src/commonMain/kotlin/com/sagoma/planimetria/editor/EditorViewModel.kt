package com.sagoma.planimetria.editor

import androidx.lifecycle.ViewModel
import com.sagoma.planimetria.geometry.Dimensions
import com.sagoma.planimetria.geometry.Furnishings
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.model.Furniture
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.geometry.Fixtures
import com.sagoma.planimetria.model.Fixture
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.Mount
import com.sagoma.planimetria.geometry.Openings
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.RoomFactory
import com.sagoma.planimetria.geometry.RoomMerge
import com.sagoma.planimetria.geometry.Ceilings
import com.sagoma.planimetria.model.WallCut
import com.sagoma.planimetria.geometry.Rulers
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.geometry.Snapping
import com.sagoma.planimetria.geometry.SnapEngine
import com.sagoma.planimetria.geometry.SnapGuide
import com.sagoma.planimetria.geometry.SnapKind
import com.sagoma.planimetria.geometry.GroupItem
import com.sagoma.planimetria.geometry.GroupOps
import com.sagoma.planimetria.geometry.roundHalfUp
import com.sagoma.planimetria.geometry.toDegrees
import com.sagoma.planimetria.geometry.toRadians
import com.sagoma.planimetria.geometry.Stairs
import com.sagoma.planimetria.geometry.Structure
import com.sagoma.planimetria.geometry.MeasureTarget
import com.sagoma.planimetria.model.Beam
import com.sagoma.planimetria.model.Column
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.FreeWall
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Floor
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Stair
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.WallFinish
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Ruler
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.model.Underlay
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.TextNote
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.geometry.PhaseView
import androidx.lifecycle.viewModelScope
import com.sagoma.planimetria.persistence.PlanStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class EditorViewModel(private val store: PlanStore, tipStore: TipStore = NoTipStore) : ViewModel() {

    /** Cronologia della casa intera: annullare riporta anche al piano su cui si era fatta la modifica. */
    private val history = History<Building>()
    private val tips = TipQueue(tipStore)
    // Prima del blocco init, che può avviare il tutorial.
    private val tutorial = Tutorial()

    // Il file è piccolo (pochi KB): lo si legge subito, così la prima schermata è già quella giusta.
    private val loaded = store.load() ?: Building.single(FloorPlan())
    private val loadedPlan = loaded.floor.plan
    private val _state = MutableStateFlow(
        EditorUiState(
            plan = loadedPlan,
            building = loaded,
            // Finestra di creazione obbligatoria solo se non c'è ancora nessuna stanza (§1).
            creation = if (loaded.floors.all { it.plan.rooms.isEmpty() }) CreationStep.PickShape(cancellable = false) else null,
        ),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    // ---------- Salvataggio automatico ----------

    /** Scritture una alla volta, nell'ordine in cui arrivano. */
    private val saveDispatcher = ioDispatcher.limitedParallelism(1)
    @kotlin.concurrent.Volatile private var lastSaved: Building = loaded

    init {
        // Salva poco dopo l'ultima modifica: durante un trascinamento si scrive una volta sola, alla fine.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _state.map { it.fullBuilding }.distinctUntilChanged().debounce(AUTOSAVE_DELAY_MS).collect { save(it) }
        }
        // Al primo avvio con la creazione obbligatoria, tutorial (o benvenuto) arrivano dopo aver creato la prima stanza.
        if (_state.value.creation == null) welcome()
    }

    /** Primo contatto: il tutorial guidato se non è mai stato fatto, altrimenti il suggerimento di benvenuto. */
    private fun welcome() {
        if (!tips.tutorialDone) restartTutorial() else showTip(Tip.Welcome)
    }

    // ---------- Tutorial guidato ----------

    private fun publishTutorial() = _state.update { it.copy(tutorial = tutorial.ui) }

    /** Menu ⋮ "Rifai il tutorial" (e primo avvio). */
    fun restartTutorial() {
        tutorial.start()
        _state.update { it.copy(tip = null) }
        publishTutorial()
    }

    /** "Inizia" / "Salta passo": passo successivo; dopo l'ultimo il tutorial finisce. */
    fun nextTutorialStep() {
        tutorial.next()
        if (!tutorial.active) return finishTutorial()
        // Primo passo: la striscia Info compare solo con una stanza selezionata.
        if (tutorial.step == TutorialStep.OpenInfo && _state.value.focusedRoomId == null) {
            _state.value.plan.rooms.firstOrNull()?.let { r -> _state.update { it.copy(focusedRoomId = r.id, selection = null) } }
        }
        publishTutorial()
    }

    /**
     * Tutorial finito ("Fine"): non ricompare, e nemmeno i suggerimenti delle funzioni che ha già spiegato.
     * `completed = false` ("Esci"): non ricompare, ma i suggerimenti al primo utilizzo restano attivi.
     */
    fun finishTutorial(completed: Boolean = true) {
        tutorial.stop()
        tips.tutorialDone = true
        if (completed) tips.markSeen(TUTORIAL_TIPS)
        _state.update { it.copy(tip = tips.current) }
        publishTutorial()
    }

    private fun tutorialEvent(e: TutorialEvent) {
        if (tutorial.on(e) || e is TutorialEvent.WallLength) publishTutorial()
    }

    // ---------- Suggerimenti al primo utilizzo ----------

    private fun showTip(tip: Tip) {
        if (tutorial.active) return // durante il tutorial parla solo lui
        tips.request(tip)
        _state.update { it.copy(tip = tips.current) }
    }

    fun dismissTip() {
        tips.dismiss()
        _state.update { it.copy(tip = tips.current) }
    }

    fun skipAllTips() {
        tips.skipAll()
        _state.update { it.copy(tip = null) }
    }

    /** Menu ⋮ "Rivedi i suggerimenti": ricompaiono tutti, a partire dal benvenuto. */
    fun resetTips() {
        tips.reset()
        if (tutorial.active) finishTutorial(completed = false)
        showTip(Tip.Welcome)
    }

    /** Suggerimento legato a ciò che è appena stato selezionato (stanza, muro, apertura, impianto). */
    private fun showTipForSelection() {
        val s = _state.value
        when (s.selection) {
            is Selection.Wall -> showTip(Tip.Wall)
            is Selection.Opening -> showTip(Tip.Opening)
            is Selection.Fixture -> showTip(Tip.Fixture)
            is Selection.Stair -> showTip(Tip.Stair)
            is Selection.StairWell -> Unit
            is Selection.Column, is Selection.Beam -> showTip(Tip.Structure)
            is Selection.FreeWall -> showTip(Tip.FreeWall)
            is Selection.Furniture, is Selection.Dimension, is Selection.Annotation -> Unit
            null -> if (s.focusedRoomId != null) showTip(Tip.Room)
        }
    }

    /** Salvataggio immediato, per quando l'app va in background (il processo potrebbe essere chiuso). */
    fun saveNow() {
        viewModelScope.launch { save(_state.value.fullBuilding) }
    }

    private suspend fun save(building: Building) = withContext(saveDispatcher) {
        if (building == lastSaved) return@withContext
        runCatching { store.save(building) }
            .onSuccess { lastSaved = building; _state.update { if (it.saveError != null) it.copy(saveError = null) else it } }
            // Mai in silenzio: se il file non si scrive l'utente lo deve sapere (le modifiche sono solo in memoria).
            .onFailure { e -> _state.update { it.copy(saveError = e.toString()) } }
    }

    /**
     * Salvataggio subito, senza aspettare (chiusura della finestra sul computer): altrimenti le ultime
     * modifiche dei 400 ms prima della chiusura andrebbero perse.
     */
    fun saveBlocking() {
        val b = _state.value.fullBuilding
        if (b == lastSaved) return
        runCatching { store.save(b) }.onSuccess { lastSaved = b }
    }

    private var viewportW = 0f
    private var viewportH = 0f
    private var fitMarginPx = 0f
    /** L'utente ha zoomato o spostato la vista a mano: da quel momento non la si riadatta più da soli. */
    private var userAdjustedView = false
    private var dragStartPlan: FloorPlan? = null
    private var dragStartPoint = Vec2.Zero
    private var dragTarget: DragTarget? = null

    // ---------- Modifiche con cronologia ----------

    private fun commit(transform: (FloorPlan) -> FloorPlan) {
        val before = _state.value.plan
        val after = transform(before)
        if (after == before) return
        history.record(_state.value.fullBuilding)
        setPlan(after)
    }

    private fun setPlan(plan: FloorPlan) = _state.update { s ->
        s.copy(
            plan = plan,
            building = s.building.withPlan(plan),
            focusedRoomId = s.focusedRoomId?.takeIf { plan.room(it) != null },
            selection = s.selection?.takeIf { sel -> selectionExists(plan, sel) },
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
    }

    /** Modifica di tutta la casa (piani aggiunti, tolti, rinominati), con cronologia. */
    private fun commitBuilding(transform: (Building) -> Building) {
        val before = _state.value.fullBuilding
        val after = transform(before)
        if (after == before) return
        history.record(before)
        setBuilding(after)
    }

    /** Mostra la casa `b` sul suo piano corrente; se il piano cambia, selezione e posizionamenti si azzerano. */
    private fun setBuilding(b: Building) = _state.update { s ->
        val sameFloor = b.floor.id == s.building.floor.id
        val plan = b.floor.plan
        s.copy(
            plan = plan,
            building = b,
            focusedRoomId = s.focusedRoomId?.takeIf { sameFloor && plan.room(it) != null },
            selection = s.selection?.takeIf { sel -> sameFloor && selectionExists(plan, sel) },
            pendingOpening = s.pendingOpening.takeIf { sameFloor },
            pendingFixture = s.pendingFixture.takeIf { sameFloor },
            canUndo = history.canUndo,
            canRedo = history.canRedo,
        )
    }

    fun undo() {
        // I livelli (nascosti, bloccati) sono un modo di guardare il disegno, non una modifica: restano come sono.
        val layers = _state.value.building.layers
        history.undo(_state.value.fullBuilding)?.let {
            setBuilding(it.copy(layers = layers))
            tutorialEvent(TutorialEvent.Undo)
        }
    }

    fun redo() {
        val layers = _state.value.building.layers
        history.redo(_state.value.fullBuilding)?.let { setBuilding(it.copy(layers = layers)) }
    }

    // ---------- Livelli ----------

    /** Mostra o nasconde un livello; nascondendolo si toglie la selezione di ciò che vi appartiene. */
    fun setLayerVisible(layer: Layer, visible: Boolean) = _state.update { s ->
        val l = s.building.layers
        val nl = l.copy(hidden = if (visible) l.hidden - layer else l.hidden + layer)
        val sel = s.selection?.takeIf { visible || layerOf(it) != layer }
        s.copy(building = s.building.copy(layers = nl), selection = sel)
    }

    /** Blocca o sblocca un livello (bloccato: si vede ma non si tocca). */
    fun setLayerLocked(layer: Layer, locked: Boolean) = _state.update { s ->
        val l = s.building.layers
        val nl = l.copy(locked = if (locked) l.locked + layer else l.locked - layer)
        val sel = s.selection?.takeIf { !locked || layerOf(it) != layer }
        s.copy(building = s.building.copy(layers = nl), selection = sel)
    }

    /** Livello di un oggetto selezionato (null = stanze e muri, sempre attivi). */
    private fun layerOf(sel: Selection): Layer? = when (sel) {
        is Selection.Fixture -> Layer.Fixtures
        is Selection.Furniture -> Layer.Furniture
        is Selection.Stair, is Selection.Column, is Selection.Beam, is Selection.FreeWall, is Selection.StairWell -> Layer.Structure
        is Selection.Dimension -> Layer.Dimensions
        is Selection.Annotation -> Layer.Texts
        else -> null
    }

    // ---------- Piani ----------

    /** Passa a un altro piano (non è una modifica: non entra nella cronologia). */
    fun selectFloor(index: Int) {
        val b = _state.value.fullBuilding
        if (index !in b.floors.indices || index == b.current) return
        setBuilding(b.copy(current = index))
        _state.update { it.copy(infoExpanded = false) }
        if (!userAdjustedView) fitToView()
    }

    fun openFloorDialog(index: Int?) = _state.update { it.copy(floorDialog = FloorDialog(index)) }

    fun closeFloorDialog() = _state.update { it.copy(floorDialog = null) }

    /**
     * Nuovo piano in cima alla casa, che diventa quello corrente. Con `copyRooms` parte con le stanze
     * del piano più alto (solo i muri, senza aperture né impianti), da modificare; altrimenti è vuoto
     * e si apre subito la creazione della prima stanza.
     */
    fun addFloor(name: String, levelHeight: Double, copyRooms: Boolean) {
        closeFloorDialog()
        commitBuilding { b ->
            val top = b.floors.last()
            val rooms = if (copyRooms) top.plan.rooms.map {
                Room(it.id, it.name, it.type, it.points, ceilingHeight = it.ceilingHeight)
            } else emptyList()
            val floor = Floor(b.nextFloorId, name.trim().ifBlank { Building.floorName(b.floors.size) }, FloorPlan(rooms), levelHeight)
            b.copy(floors = b.floors + floor, current = b.floors.size)
        }
        if (!userAdjustedView) fitToView()
        if (_state.value.plan.rooms.isEmpty()) _state.update { it.copy(creation = CreationStep.PickShape(cancellable = true)) }
        showTip(Tip.Floors)
    }

    /** Nome e interpiano di un piano. */
    fun updateFloor(index: Int, name: String, levelHeight: Double) {
        closeFloorDialog()
        commitBuilding { b ->
            val f = b.floors.getOrNull(index) ?: return@commitBuilding b
            b.copy(floors = b.floors.mapIndexed { i, x -> if (i == index) f.copy(name = name.trim().ifBlank { f.name }, levelHeight = levelHeight) else x })
        }
    }

    /** Elimina un piano (deve restarne almeno uno); si passa a quello di sotto. Si può annullare. */
    fun deleteFloor(index: Int) {
        closeFloorDialog()
        commitBuilding { b ->
            if (b.floors.size < 2 || index !in b.floors.indices) return@commitBuilding b
            val floors = b.floors.filterIndexed { i, _ -> i != index }
            b.copy(floors = floors, current = (if (b.current >= index) b.current - 1 else b.current).coerceIn(floors.indices))
        }
        if (!userAdjustedView) fitToView()
    }

    // ---------- Scale ----------

    /** Nuova scala al centro della vista (o della stanza selezionata), accostata ai muri vicini. */
    fun addStair(kind: StairKind) {
        val s = _state.value
        // Dentro una stanza: quella selezionata, altrimenti quella al centro della vista (o la più grande).
        val viewCenter = s.camera.toWorld(viewportW / 2, viewportH / 2)
        val room = s.plan.room(s.focusedRoomId)
            ?: s.plan.rooms.lastOrNull { Polygon.contains(it.points, viewCenter) }
            ?: s.plan.rooms.maxByOrNull { Polygon.area(it.points) }
        val center = room?.let { Polygon.labelPoint(it.points) } ?: viewCenter
        val stair = Stairs.snapped(s.plan, Stair(s.plan.nextStairId, kind, center), s.levelHeight)
        commit { it.copy(stairs = it.stairs + stair) }
        _state.update { it.copy(focusedRoomId = null, selection = Selection.Stair(stair.id), pendingOpening = null, pendingFixture = null, infoExpanded = false) }
        showTip(Tip.Stair)
    }

    fun selectStair(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Stair(id)) }

    // ---------- Colonne e travi ----------

    /** Nuova colonna nella stanza selezionata (o al centro della vista): al centro, oppure accostata a un muro. */
    fun addColumn(shape: ColumnShape) {
        val s = _state.value
        val center = placementCenter(s)
        val column = Structure.snapped(s.plan, Column(s.plan.nextColumnId, center, shape))
        commit { it.copy(columns = it.columns + column) }
        _state.update { it.copy(focusedRoomId = null, selection = Selection.Column(column.id), pendingOpening = null, pendingFixture = null, infoExpanded = false) }
        showTip(Tip.Structure)
    }

    /** Nuova trave che attraversa la stanza selezionata (o quella al centro della vista) nel verso più corto. */
    fun addBeam() {
        val s = _state.value
        val center = placementCenter(s)
        val room = s.plan.room(s.focusedRoomId) ?: s.plan.rooms.lastOrNull { Polygon.contains(it.points, center) }
        val beam = Structure.defaultBeam(s.plan.nextBeamId, room, center, s.plan)
        commit { it.copy(beams = it.beams + beam) }
        _state.update { it.copy(focusedRoomId = null, selection = Selection.Beam(beam.id), pendingOpening = null, pendingFixture = null, infoExpanded = false) }
        showTip(Tip.Structure)
    }

    /** Dove mettere un elemento nuovo: la stanza selezionata, quella al centro della vista o la più grande. */
    private fun placementCenter(s: EditorUiState): Vec2 {
        val viewCenter = s.camera.toWorld(viewportW / 2, viewportH / 2)
        val room = s.plan.room(s.focusedRoomId)
            ?: s.plan.rooms.lastOrNull { Polygon.contains(it.points, viewCenter) }
            ?: s.plan.rooms.maxByOrNull { Polygon.area(it.points) }
        return room?.let { Polygon.labelPoint(it.points) } ?: viewCenter
    }

    /**
     * "Muro singolo" dalla finestra "Aggiungi stanza": un tramezzo che attraversa la stanza selezionata (o
     * quella al centro della vista) da un muro all'altro, da spostare e allungare.
     */
    fun addFreeWall() {
        _state.update { it.copy(creation = null) }
        val s = _state.value
        val center = placementCenter(s)
        val room = s.plan.room(s.focusedRoomId) ?: s.plan.rooms.lastOrNull { Polygon.contains(it.points, center) }
        val wall = Structure.defaultFreeWall(s.plan.nextFreeWallId, room, center, s.plan)
        commit { it.copy(freeWalls = it.freeWalls + wall) }
        _state.update { it.copy(focusedRoomId = null, selection = Selection.FreeWall(wall.id), pendingOpening = null, pendingFixture = null, infoExpanded = false) }
        showTip(Tip.FreeWall)
    }

    fun selectFreeWall(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.FreeWall(id)) }
    fun updateFreeWall(w: FreeWall) = commit { it.replace(w) }

    fun removeFreeWall(id: Long) {
        commit { it.copy(freeWalls = it.freeWalls.filter { w -> w.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    fun selectColumn(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Column(id)) }
    fun selectBeam(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Beam(id)) }
    fun updateColumn(c: Column) = commit { it.replace(c) }
    fun updateBeam(b: Beam) = commit { it.replace(b) }

    fun removeColumn(id: Long) {
        commit { it.copy(columns = it.columns.filter { c -> c.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    fun removeBeam(id: Long) {
        commit { it.copy(beams = it.beams.filter { b -> b.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    // ---------- Arredi (versione pro) ----------

    /** Nuovo arredo del catalogo nella stanza selezionata (o al centro della vista), accostato a muri e mobili. */
    fun addFurniture(item: FurnitureCatalog.Item) {
        val s = _state.value
        val f = Furnishings.snapped(s.plan, Furnishings.create(s.plan, item, placementCenter(s), s.levelHeight))
        commit { it.copy(furniture = it.furniture + f) }
        _state.update { it.copy(focusedRoomId = null, selection = Selection.Furniture(f.id), pendingOpening = null, pendingFixture = null, infoExpanded = false) }
    }

    fun selectFurniture(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Furniture(id)) }
    fun updateFurniture(f: Furniture) = commit { it.replace(f) }

    fun rotateFurniture(id: Long) = commit { plan ->
        val f = plan.furniture(id) ?: return@commit plan
        plan.replace(Furnishings.snapped(plan, f.copy(rotation = (com.sagoma.planimetria.geometry.roundHalfUp(f.rotation / 90.0) * 90.0 + 90.0) % 360)))
    }

    /** Copia accanto all'originale (per mettere in fila sedie, basi della cucina…), subito selezionata. */
    fun duplicateFurniture(id: Long) {
        val plan = _state.value.plan
        val f = plan.furniture(id) ?: return
        val copy = f.copy(id = plan.nextFurnitureId, center = f.center + Furnishings.axes(f).first * f.width)
        commit { it.copy(furniture = it.furniture + Furnishings.snapped(it, copy)) }
        selectFurniture(copy.id)
    }

    /**
     * "⧉ Duplica" (Ctrl+D) per qualsiasi oggetto selezionato: una copia con le stesse impostazioni, accanto
     * all'originale, subito selezionata (così si trascina dove serve). Impianti e aperture restano sullo stesso
     * muro, un poco più in là; una stanza (o un balcone) si copia intera, con porte e impianti, a destra della casa.
     * Restituisce false se non c'è niente da duplicare.
     */
    fun duplicateSelected(): Boolean {
        val s = _state.value
        val plan = s.plan
        if (s.multi.isNotEmpty()) {
            val b = GroupOps.bounds(plan, s.multi) ?: return false
            groupDuplicate(b.width + 50.0, 0.0)
            return true
        }
        /** Copia di un oggetto della pianta spostata di `offset`; restituisce il nuovo oggetto. */
        fun copyItem(item: GroupItem, offset: Vec2): GroupItem? {
            val (p, created) = GroupOps.duplicate(plan, setOf(item), offset)
            commit { p }
            return created.singleOrNull()
        }
        /** Accanto, a destra (lo spazio della sua larghezza più `gap`). */
        fun beside(item: GroupItem, gap: Double) = Vec2((GroupOps.boundsOf(plan, item)?.width ?: 0.0) + gap, 0.0)
        when (val sel = s.selection) {
            is Selection.Furniture -> duplicateFurniture(sel.furnitureId)
            // Quota: una uguale più in là, parallela; testo: un poco sotto.
            is Selection.Dimension -> plan.dimension(sel.dimensionId)?.let { d ->
                (copyItem(GroupItem.DimensionItem(d.id), d.normal * (if (d.offset >= 0) 40.0 else -40.0)) as? GroupItem.DimensionItem)?.let { selectDimension(it.id) }
            }
            is Selection.Annotation -> plan.annotation(sel.annotationId)?.let { t ->
                (copyItem(GroupItem.AnnotationItem(t.id), Vec2(0.0, t.size * 1.6)) as? GroupItem.AnnotationItem)?.let { selectAnnotation(it.id) }
            }
            is Selection.Column -> (copyItem(GroupItem.ColumnItem(sel.columnId), beside(GroupItem.ColumnItem(sel.columnId), 30.0)) as? GroupItem.ColumnItem)?.let { selectColumn(it.id) }
            is Selection.Stair -> (copyItem(GroupItem.StairItem(sel.stairId), beside(GroupItem.StairItem(sel.stairId), 50.0)) as? GroupItem.StairItem)?.let { selectStair(it.id) }
            is Selection.Beam, is Selection.FreeWall -> {
                // Travi e muri singoli: copia parallela, spostata di lato rispetto alla loro direzione.
                val (item, a, b, w) = when (sel) {
                    is Selection.Beam -> plan.beam(sel.beamId)?.let { Quad(GroupItem.BeamItem(it.id), it.start, it.end, it.width) }
                    is Selection.FreeWall -> plan.freeWall(sel.wallId)?.let { Quad(GroupItem.FreeWallItem(it.id), it.start, it.end, it.thickness) }
                    else -> null
                } ?: return false
                val dir = (b - a).normalized()
                val created = copyItem(item, Vec2(-dir.y, dir.x) * (w + 60.0))
                when (created) {
                    is GroupItem.BeamItem -> selectBeam(created.id)
                    is GroupItem.FreeWallItem -> selectFreeWall(created.id)
                    else -> Unit
                }
            }
            is Selection.Fixture -> {
                val room = plan.room(sel.roomId) ?: return false
                val f = room.fixture(sel.fixtureId) ?: return false
                val copy = if (f.kind.mount == Mount.Wall) {
                    // Stesso muro, più in là (o più in qua se non c'è posto).
                    val len = room.wallLength(f.wallIndex)
                    val step = maxOf(f.length, 10.0) + 20.0
                    val pos = if (f.position + step + f.length / 2 <= len) f.position + step else f.position - step
                    f.copy(id = plan.nextFixtureId, position = Openings.clampPosition(len, f.length, pos))
                } else {
                    val right = f.point + Vec2(60.0, 0.0)
                    f.copy(id = plan.nextFixtureId, point = if (Polygon.contains(room.points, right)) right else f.point - Vec2(60.0, 0.0))
                }
                commit { it.replace(room.copy(fixtures = room.fixtures + copy)) }
                selectFixture(room.id, copy.id)
            }
            is Selection.Opening -> {
                val room = plan.room(sel.roomId) ?: return false
                val o = room.opening(sel.openingId) ?: return false
                val len = room.wallLength(o.wallIndex)
                val step = o.width + 40.0
                val pos = if (o.position + step + o.width / 2 <= len) o.position + step else o.position - step
                val copy = o.copy(id = plan.nextOpeningId, position = Openings.clampPosition(len, o.width, pos))
                commit { it.replace(room.copy(openings = room.openings + copy)) }
                selectOpening(room.id, copy.id)
            }
            is Selection.Wall, null -> {
                // Stanza (dal suo muro selezionato o dalla stanza attiva): a destra di tutta la casa.
                val roomId = (sel as? Selection.Wall)?.roomId ?: s.focusedRoomId ?: return false
                val room = plan.room(roomId) ?: return false
                val houseRight = plan.rooms.maxOf { r -> r.points.maxOf { it.x } }
                val offset = Vec2(houseRight - room.points.minOf { it.x } + 100.0, 0.0)
                (copyItem(GroupItem.RoomItem(roomId), offset) as? GroupItem.RoomItem)?.let { focusRoom(it.id) }
            }
            is Selection.StairWell -> return false
        }
        return true
    }

    private data class Quad(val item: GroupItem, val a: Vec2, val b: Vec2, val width: Double)

    fun removeFurniture(id: Long) {
        commit { it.copy(furniture = it.furniture.filter { f -> f.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    fun selectStairWell(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.StairWell(id)) }

    /** Modifica una scala del piano di sotto (dal suo vano al piano corrente): la ringhiera del vano. */
    fun updateStairBelow(stair: Stair) = commitBuilding { b ->
        val i = b.current - 1
        if (i < 0) return@commitBuilding b
        val f = b.floors[i]
        b.copy(floors = b.floors.mapIndexed { k, x -> if (k == i) f.copy(plan = f.plan.replace(stair)) else x })
    }

    fun updateStair(stair: Stair) = commit { it.replace(stair) }

    fun rotateStair(id: Long) = commit { plan ->
        val st = plan.stair(id) ?: return@commit plan
        plan.replace(st.copy(rotation = (st.rotation + 90.0) % 360.0))
    }

    fun removeStair(id: Long) {
        commit { it.copy(stairs = it.stairs.filter { s -> s.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    // ---------- Creazione stanza (§1) ----------

    fun startAddRoom() = _state.update { it.copy(creation = CreationStep.PickShape(cancellable = true), creationType = null) }

    /** "+ Balcone o terrazza": la creazione parte con il tipo già scelto. */
    fun startAddOutdoor(type: RoomType) = _state.update { it.copy(creation = CreationStep.PickShape(cancellable = true), creationType = type) }

    /** Tipo e altezza del parapetto di un balcone o di una terrazza. */
    fun setParapet(roomId: Long, parapet: Parapet? = null, height: Double? = null) = commit { plan ->
        val room = plan.room(roomId) ?: return@commit plan
        plan.replace(room.copy(parapet = parapet ?: room.parapet, parapetHeight = height ?: room.parapetHeight))
    }

    fun cancelCreation() = _state.update { s ->
        if (s.creation?.cancellable == true) s.copy(creation = null) else s
    }

    fun pickShape(shape: RoomShape) = _state.update { s ->
        val c = s.creation ?: return@update s
        s.copy(
            creation = if (shape == RoomShape.L) CreationStep.PickLOrientation(c.cancellable)
            else CreationStep.Measures(shape, null, c.cancellable),
        )
    }

    fun pickLOrientation(o: LOrientation) = _state.update { s ->
        val c = s.creation ?: return@update s
        s.copy(creation = CreationStep.Measures(RoomShape.L, o, c.cancellable))
    }

    fun backToShapePicker() = _state.update { s ->
        val c = s.creation ?: return@update s
        s.copy(creation = CreationStep.PickShape(c.cancellable))
    }

    fun createRoom(type: RoomType, dims: ShapeDimensions, ceilingHeight: Double) {
        val step = _state.value.creation as? CreationStep.Measures ?: return
        val plan = _state.value.plan
        // Le misure inserite sono interne (calpestabili): il contorno della stanza, che passa per la
        // mezzeria dei muri, si ottiene allargandole di metà spessore su ogni lato.
        val interior = when (step.shape) {
            RoomShape.Square -> RoomFactory.rectangle(dims.width, dims.width)
            RoomShape.Rectangle -> RoomFactory.rectangle(dims.width, dims.depth)
            RoomShape.L -> RoomFactory.lShape(dims, step.orientation ?: LOrientation.TopRight)
        }
        addRoomFromInterior(type, interior, ceilingHeight, Room.WALL_THICKNESS)
    }

    /** "Da rilievo": si apre la finestra dei lati e delle diagonali misurati. */
    fun pickSurvey() = _state.update { s ->
        val c = s.creation ?: return@update s
        s.copy(creation = CreationStep.Survey(c.cancellable))
    }

    /** Stanza dal rilievo: angoli sul filo interno calcolati da [com.sagoma.planimetria.geometry.Survey]. */
    fun createRoomFromSurvey(type: RoomType, interior: List<Vec2>, ceilingHeight: Double, wallThickness: Double) {
        if (_state.value.creation !is CreationStep.Survey || interior.size < 3) return
        addRoomFromInterior(type, interior, ceilingHeight, wallThickness)
    }

    private fun addRoomFromInterior(type: RoomType, interior: List<Vec2>, ceilingHeight: Double, wallThickness: Double) {
        val plan = _state.value.plan
        val shape = Polygon.fromInterior(interior, wallThickness)
        // Prima stanza di un piano superiore: nell'angolo del piano di sotto, così ci si allinea subito.
        val below = _state.value.planBelow?.let { Openings.planBounds(it) }
        val points = if (plan.rooms.isEmpty() && below != null) {
            val sb = Polygon.bounds(shape)
            shape.map { it - Vec2(sb.minX, sb.minY) + Vec2(below.minX, below.minY) }
        // Balconi e terrazze nascono già appoggiati alla casa (muro in comune), le stanze un po' distanti.
        } else if (type.outdoor) RoomFactory.placeAgainstHouse(plan, shape)
        else RoomFactory.placeBeside(plan, shape)
        val room = Room(
            id = plan.nextId,
            name = RoomFactory.autoName(plan, type),
            type = type,
            points = points,
            ceilingHeight = ceilingHeight,
            wallThickness = wallThickness,
        )
        val firstEver = _state.value.building.floors.size == 1 && plan.rooms.isEmpty()
        commit { it.copy(rooms = it.rooms + room) }
        _state.update { it.copy(creation = null, focusedRoomId = room.id, selection = null) }
        fitToView()
        if (firstEver) welcome() else {
            tutorialEvent(TutorialEvent.RoomAdded)
            showTip(if (type.outdoor) Tip.Outdoor else Tip.NewRoom)
        }
    }

    // ---------- Selezione multipla ----------

    /** "☐ Seleziona": entra o esce dalla modalità; uscendo la selezione si svuota. */
    fun toggleSelectMode() = _state.update {
        if (it.selectMode) it.copy(selectMode = false, multi = emptySet(), selectRect = null)
        else it.copy(selectMode = true, pasting = false, selection = null, focusedRoomId = null, pendingOpening = null, pendingFixture = null, measure = null, wallDraw = null, infoExpanded = false)
    }

    fun clearMulti() = _state.update { it.copy(multi = emptySet(), selectRect = null) }

    /** Tutti gli oggetti del piano (Ctrl+A). */
    fun selectAll() = _state.update { s ->
        val p = s.plan
        val all = GroupOps.inRect(p, Vec2(-1e9, -1e9), Vec2(1e9, 1e9), crossing = false)
        s.copy(selectMode = true, selection = null, focusedRoomId = null, multi = all.filter { editable(s, it) }.toSet())
    }

    /** Livello di un oggetto della pianta (null = stanze, sempre attive). */
    private fun layerOf(item: GroupItem): Layer? = when (item) {
        is GroupItem.RoomItem -> null
        is GroupItem.FurnitureItem -> Layer.Furniture
        is GroupItem.ColumnItem, is GroupItem.BeamItem, is GroupItem.FreeWallItem, is GroupItem.StairItem -> Layer.Structure
        is GroupItem.RulerItem -> Layer.Rulers
        is GroupItem.DimensionItem -> Layer.Dimensions
        is GroupItem.AnnotationItem -> Layer.Texts
    }

    /** Si può selezionare e modificare (il suo livello non è nascosto né bloccato). */
    private fun editable(s: EditorUiState, item: GroupItem) = layerOf(item)?.let { s.building.layers.editable(it) } ?: true

    /** Oggetto della pianta sotto un tocco (stanze da muri, angoli, etichetta o pavimento). */
    fun groupItemOf(target: DragTarget): GroupItem? = when (target) {
        is DragTarget.RoomInterior -> GroupItem.RoomItem(target.roomId)
        is DragTarget.RoomLabel -> GroupItem.RoomItem(target.roomId)
        is DragTarget.Wall -> GroupItem.RoomItem(target.roomId)
        is DragTarget.Corner -> GroupItem.RoomItem(target.roomId)
        is DragTarget.Opening -> GroupItem.RoomItem(target.roomId)
        is DragTarget.Fixture -> GroupItem.RoomItem(target.roomId)
        is DragTarget.FurnitureBody -> GroupItem.FurnitureItem(target.furnitureId)
        is DragTarget.FurnitureRotate -> GroupItem.FurnitureItem(target.furnitureId)
        is DragTarget.Column -> GroupItem.ColumnItem(target.columnId)
        is DragTarget.BeamBody -> GroupItem.BeamItem(target.beamId)
        is DragTarget.BeamEnd -> GroupItem.BeamItem(target.beamId)
        is DragTarget.FreeWallBody -> GroupItem.FreeWallItem(target.wallId)
        is DragTarget.FreeWallEnd -> GroupItem.FreeWallItem(target.wallId)
        is DragTarget.Stair -> GroupItem.StairItem(target.stairId)
        is DragTarget.RulerBody -> GroupItem.RulerItem(target.rulerId)
        is DragTarget.RulerEnd -> GroupItem.RulerItem(target.rulerId)
        is DragTarget.DimensionLine -> GroupItem.DimensionItem(target.dimensionId)
        is DragTarget.DimensionEnd -> GroupItem.DimensionItem(target.dimensionId)
        is DragTarget.AnnotationBody -> GroupItem.AnnotationItem(target.annotationId)
        is DragTarget.AnnotationArrow -> GroupItem.AnnotationItem(target.annotationId)
        else -> null
    }

    /** Aggiunge o toglie un oggetto dalla selezione (tocco in modalità Seleziona, Ctrl+clic sul computer). */
    fun toggleInMulti(target: DragTarget) = _state.update { s ->
        val item = groupItemOf(target) ?: return@update s
        val m = if (item in s.multi) s.multi - item else s.multi + item
        s.copy(multi = m, selectMode = true, selection = null, focusedRoomId = null, infoExpanded = false)
    }

    fun selectRectUpdate(a: Vec2, b: Vec2) = _state.update { it.copy(selectRect = a to b) }

    /**
     * Fine del rettangolo: da sinistra a destra gli oggetti tutti dentro, da destra a sinistra anche quelli
     * toccati (come in AutoCAD). `add`: si aggiungono alla selezione invece di sostituirla.
     */
    fun selectRectEnd(add: Boolean) = _state.update { s ->
        val (a, b) = s.selectRect ?: return@update s
        val found = GroupOps.inRect(s.plan, a, b, crossing = b.x < a.x).filter { editable(s, it) }.toSet()
        s.copy(selectRect = null, multi = if (add) s.multi + found else found)
    }

    private var groupStart: FloorPlan? = null

    fun beginGroupMove() {
        if (_state.value.multi.isEmpty()) return
        groupStart = _state.value.plan
        history.record(_state.value.fullBuilding)
        _state.update { it.copy(dragging = true) }
    }

    fun groupMoveBy(total: Vec2) {
        val start = groupStart ?: return
        setPlan(GroupOps.translate(start, _state.value.multi, total))
    }

    fun endGroupMove() {
        val start = groupStart ?: return
        groupStart = null
        if (_state.value.plan == start) history.discardLast()
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo, dragging = false) }
    }

    private fun groupCenter(): Vec2? = GroupOps.bounds(_state.value.plan, _state.value.multi)?.center

    fun groupTranslate(dx: Double, dy: Double) = commit { GroupOps.translate(it, _state.value.multi, Vec2(dx, dy)) }

    fun groupRotate(clockwise: Boolean) {
        val c = groupCenter() ?: return
        commit { GroupOps.rotate90(it, _state.value.multi, c, clockwise) }
    }

    fun groupMirror(horizontal: Boolean) {
        val c = groupCenter() ?: return
        commit { GroupOps.mirror(it, _state.value.multi, c, horizontal) }
    }

    /** Copia (count = 1) o serie: le copie diventano la nuova selezione. */
    fun groupDuplicate(dx: Double, dy: Double, count: Int = 1) {
        if (_state.value.multi.isEmpty() || count < 1) return
        val (plan, created) = GroupOps.duplicate(_state.value.plan, _state.value.multi, Vec2(dx, dy), count)
        commit { plan }
        _state.update { it.copy(multi = created) }
    }

    fun groupAlign(a: com.sagoma.planimetria.geometry.Alignment) = commit { GroupOps.align(it, _state.value.multi, a) }

    fun groupDistribute(horizontal: Boolean) = commit { GroupOps.distribute(it, _state.value.multi, horizontal) }

    fun groupDelete() {
        if (_state.value.multi.isEmpty()) return
        commit { GroupOps.delete(it, _state.value.multi) }
        _state.update { it.copy(multi = emptySet()) }
    }

    // ---------- Copia e incolla ----------

    /** Cosa copierebbe "Copia" adesso (selezione multipla, oggetto selezionato o stanza attiva); null = niente. */
    fun clipOf(s: EditorUiState = _state.value): Clip? {
        val plan = s.plan
        fun group(items: Set<GroupItem>) =
            GroupOps.bounds(plan, items)?.let { Clip.GroupClip(plan, items, it.center) }
        if (s.multi.isNotEmpty()) return group(s.multi)
        return when (val sel = s.selection) {
            is Selection.Fixture -> plan.room(sel.roomId)?.fixture(sel.fixtureId)?.let { Clip.FixtureClip(it) }
            is Selection.Opening -> plan.room(sel.roomId)?.opening(sel.openingId)?.let { Clip.OpeningClip(it) }
            is Selection.Furniture -> group(setOf(GroupItem.FurnitureItem(sel.furnitureId)))
            is Selection.Column -> group(setOf(GroupItem.ColumnItem(sel.columnId)))
            is Selection.Beam -> group(setOf(GroupItem.BeamItem(sel.beamId)))
            is Selection.FreeWall -> group(setOf(GroupItem.FreeWallItem(sel.wallId)))
            is Selection.Stair -> group(setOf(GroupItem.StairItem(sel.stairId)))
            is Selection.Wall -> group(setOf(GroupItem.RoomItem(sel.roomId)))
            is Selection.Dimension -> group(setOf(GroupItem.DimensionItem(sel.dimensionId)))
            is Selection.Annotation -> group(setOf(GroupItem.AnnotationItem(sel.annotationId)))
            is Selection.StairWell -> null
            null -> s.focusedRoomId?.let { group(setOf(GroupItem.RoomItem(it))) }
        }
    }

    /** "Copia" (Ctrl+C): l'oggetto con tutte le sue impostazioni va negli appunti. */
    fun copy(): Boolean {
        val clip = clipOf() ?: return false
        _state.update { it.copy(clipboard = clip) }
        return true
    }

    /** "Incolla" (Ctrl+V): ogni tocco mette una copia (sul muro toccato per impianti e aperture). */
    fun startPaste() {
        if (_state.value.clipboard == null) return
        _state.update {
            it.copy(
                pasting = true, selectMode = false, multi = emptySet(), selectRect = null, selection = null,
                pendingOpening = null, pendingFixture = null, measure = null, wallDraw = null, view3d = false, infoExpanded = false,
            )
        }
    }

    fun stopPaste() = _state.update { it.copy(pasting = false, pasteCursor = null) }

    fun pasteCursor(p: Vec2) = _state.update { if (it.pasting) it.copy(pasteCursor = p) else it }

    /** Muro indicato da un tocco (sul muro, su un'apertura o su un impianto a muro). */
    private fun wallOf(plan: FloorPlan, target: DragTarget): Pair<Long, Int>? = when (target) {
        is DragTarget.Wall -> target.roomId to target.index
        is DragTarget.Opening -> plan.room(target.roomId)?.opening(target.openingId)?.let { target.roomId to it.wallIndex }
        is DragTarget.Fixture -> plan.room(target.roomId)?.fixture(target.fixtureId)
            ?.takeIf { it.kind.mount == Mount.Wall }?.let { target.roomId to it.wallIndex }
        else -> null
    }

    /** Un tocco in modalità "Incolla". Restituisce false se lì non si può incollare (es. non su un muro). */
    private fun pasteAt(target: DragTarget, at: Vec2): Boolean = pasteOn(target, at, nearestWall = false) != null

    /**
     * Mette una copia degli appunti: impianti a muro e aperture sul muro indicato da `target` (o, con
     * `nearestWall`, sul muro più vicino al punto `at`), luci nel punto, oggetti della pianta centrati sul punto.
     * Restituisce come selezionare la copia, oppure null se lì non si può incollare.
     */
    private fun pasteOn(target: DragTarget, at: Vec2, nearestWall: Boolean): (() -> Unit)? {
        val plan = _state.value.plan
        fun wall(): Pair<Long, Int>? = (wallOf(plan, target) ?: if (nearestWall) nearestWallTo(plan, at) else null)
            ?.takeIf { !isRemovedWall(plan, it) }
        when (val clip = _state.value.clipboard ?: return null) {
            is Clip.FixtureClip -> {
                val f = clip.fixture
                if (f.kind.mount == Mount.Wall) {
                    val (roomId, w) = wall() ?: return null
                    val room = plan.room(roomId) ?: return null
                    val pos = Openings.clampPosition(room.wallLength(w), f.length, Openings.projectOnWall(room, w, at))
                    val copy = f.copy(id = plan.nextFixtureId, wallIndex = w, position = pos)
                    commit { it.replace(room.copy(fixtures = room.fixtures + copy)) }
                    return { selectFixture(room.id, copy.id) }
                } else {
                    val room = plan.rooms.lastOrNull { Polygon.contains(it.points, at) } ?: return null
                    val copy = f.copy(id = plan.nextFixtureId, point = at)
                    commit { it.replace(room.copy(fixtures = room.fixtures + copy)) }
                    return { selectFixture(room.id, copy.id) }
                }
            }
            is Clip.OpeningClip -> {
                val (roomId, w) = wall() ?: return null
                val room = plan.room(roomId) ?: return null
                val o = clip.opening
                val pos = Openings.clampPosition(room.wallLength(w), o.width, Openings.projectOnWall(room, w, at))
                val copy = o.copy(id = plan.nextOpeningId, wallIndex = w, position = pos)
                commit { it.replace(room.copy(openings = room.openings + copy)) }
                return { selectOpening(room.id, copy.id) }
            }
            is Clip.GroupClip -> {
                val d = at - clip.center
                val offset = Vec2(roundHalfUp(d.x), roundHalfUp(d.y))
                val (p, created) = GroupOps.duplicate(clip.source, clip.items, offset, into = plan)
                commit { p }
                return { selectCreated(created) }
            }
        }
    }

    /** Il muro (stanza, indice) è stato eliminato: è un lato aperto, lì non si mettono aperture né impianti. */
    private fun isRemovedWall(plan: FloorPlan, wall: Pair<Long, Int>) = plan.room(wall.first)?.isRemoved(wall.second) == true

    /** Muro (stanza, indice) più vicino al punto, fra tutte le stanze del piano (esclusi i muri eliminati). */
    private fun nearestWallTo(plan: FloorPlan, p: Vec2): Pair<Long, Int>? =
        plan.rooms.flatMap { r -> (0 until r.wallCount).filter { !r.isRemoved(it) }.map { Triple(r.id, it, Polygon.distanceToSegment(p, r.wallStart(it), r.wallEnd(it))) } }
            .minByOrNull { it.third }?.let { it.first to it.second }

    /** Seleziona le copie appena create: un oggetto solo come selezione normale, più oggetti come selezione multipla. */
    private fun selectCreated(created: Set<GroupItem>) {
        when (val one = created.singleOrNull()) {
            is GroupItem.RoomItem -> focusRoom(one.id)
            is GroupItem.FurnitureItem -> selectFurniture(one.id)
            is GroupItem.ColumnItem -> selectColumn(one.id)
            is GroupItem.BeamItem -> selectBeam(one.id)
            is GroupItem.FreeWallItem -> selectFreeWall(one.id)
            is GroupItem.StairItem -> selectStair(one.id)
            is GroupItem.RulerItem -> Unit
            is GroupItem.DimensionItem -> selectDimension(one.id)
            is GroupItem.AnnotationItem -> selectAnnotation(one.id)
            null -> if (created.isNotEmpty()) _state.update { it.copy(selectMode = true, multi = created, selection = null, focusedRoomId = null) }
        }
    }

    /** Punto della pianta sotto il mouse e cosa c'è lì (sul computer), per incollare con Ctrl+V dove si punta. */
    private var hover: Pair<Vec2, DragTarget>? = null

    fun setHover(at: Vec2?, target: DragTarget?) {
        hover = if (at != null && target != null) at to target else null
    }

    /**
     * Ctrl+V: incolla subito, come nei programmi del computer. Dove c'è il mouse (sul muro più vicino per
     * impianti e aperture); se il mouse non è sulla pianta, accanto all'originale. La copia resta selezionata.
     */
    fun pasteNow(): Boolean {
        val clip = _state.value.clipboard ?: return false
        _state.update { it.copy(pasting = false, pasteCursor = null) }
        val h = hover
        val select = if (h != null) pasteOn(h.second, h.first, nearestWall = true) else null
        (select ?: pasteBesideOriginal(clip))?.invoke() ?: return false
        return true
    }

    /** Incolla senza il mouse sulla pianta: accanto all'originale (stesso muro un po' più in là, o spostata). */
    private fun pasteBesideOriginal(clip: Clip): (() -> Unit)? {
        val plan = _state.value.plan
        return when (clip) {
            is Clip.GroupClip -> {
                val b = GroupOps.bounds(clip.source, clip.items)
                pasteOn(DragTarget.Background, clip.center + Vec2((b?.width ?: 0.0) + 50.0, 0.0), nearestWall = false)
            }
            is Clip.FixtureClip, is Clip.OpeningClip -> {
                // Il muro dell'originale (se c'è ancora): la copia va un po' più in là lungo lo stesso muro.
                val (wallIndex, pos, width) = when (clip) {
                    is Clip.FixtureClip -> Triple(clip.fixture.wallIndex, clip.fixture.position, maxOf(clip.fixture.length, 10.0))
                    is Clip.OpeningClip -> Triple(clip.opening.wallIndex, clip.opening.position, clip.opening.width)
                    else -> return null
                }
                val room = plan.rooms.firstOrNull { r ->
                    when (clip) {
                        is Clip.FixtureClip -> r.fixtures.any { it.id == clip.fixture.id }
                        is Clip.OpeningClip -> r.openings.any { it.id == clip.opening.id }
                        else -> false
                    }
                } ?: return null
                if (clip is Clip.FixtureClip && clip.fixture.kind.mount != Mount.Wall) {
                    return pasteOn(DragTarget.Background, clip.fixture.point + Vec2(60.0, 0.0), nearestWall = false)
                }
                val s = room.wallStart(wallIndex)
                val u = (room.wallEnd(wallIndex) - s).normalized()
                val len = room.wallLength(wallIndex)
                val step = width + 30.0
                val t = if (pos + step + width / 2 <= len) pos + step else pos - step
                pasteOn(DragTarget.Wall(room.id, wallIndex), s + u * t, nearestWall = false)
            }
        }
    }

    // ---------- Disegna muri (muro per muro) ----------

    /** "Muro per muro": si chiude la scelta della forma e si disegnano gli angoli sulla pianta. */
    fun startDrawWalls() = _state.update {
        it.copy(creation = null, wallDraw = WallDraw(), selection = null, focusedRoomId = null, pendingOpening = null, pendingFixture = null, measure = null, view3d = false, infoExpanded = false)
    }

    fun cancelDrawWalls() = _state.update { it.copy(wallDraw = null, snapGuides = emptyList()) }

    private fun drawSnap(p: Vec2, wd: WallDraw) =
        SnapEngine.snapDrawing(p, wd.points.lastOrNull(), wd.points.firstOrNull(), SnapEngine.targets(_state.value.plan), wd.points, snapTolerance())

    /** Il puntatore si muove (mouse sul computer): anteprima del prossimo muro, già agganciata. */
    fun drawWallsCursor(p: Vec2) = _state.update { s ->
        val wd = s.wallDraw ?: return@update s
        if (wd.closing) return@update s
        val r = drawSnap(p, wd)
        s.copy(wallDraw = wd.copy(cursor = r.point), snapGuides = r.guides)
    }

    /** Tocco o clic: nuovo angolo (agganciato); sul primo angolo la forma si chiude. */
    fun drawWallsTap(p: Vec2) {
        val wd = _state.value.wallDraw ?: return
        if (wd.closing) return
        val r = drawSnap(p, wd)
        if (r.guides.any { it.kind == SnapKind.Close }) { drawWallsClose(); return }
        if (wd.points.lastOrNull()?.let { it.distanceTo(r.point) < 1.0 } == true) return
        _state.update { it.copy(wallDraw = wd.copy(points = wd.points + r.point, cursor = r.point), snapGuides = emptyList()) }
    }

    /**
     * Muro di lunghezza esatta (cm, in mezzeria) dall'ultimo angolo: nella direzione dell'angolo scritto (gradi,
     * 0 = verso destra, 90 = verso l'alto) oppure verso il puntatore, bloccata a 0°/45°/90° se vicina.
     */
    fun drawWallsTyped(length: Double, angleDeg: Double?) {
        val wd = _state.value.wallDraw ?: return
        val last = wd.points.lastOrNull() ?: return
        if (length <= 0) return
        val dir = when {
            angleDeg != null -> toRadians(angleDeg).let { Vec2(kotlin.math.cos(it), -kotlin.math.sin(it)) }
            wd.cursor != null && wd.cursor.distanceTo(last) > 1e-6 -> {
                val d = (wd.cursor - last).normalized()
                val deg = toDegrees(kotlin.math.atan2(d.y, d.x))
                val locked = roundHalfUp(deg / 45.0) * 45.0
                if (kotlin.math.abs(deg - locked) <= 10.0) toRadians(locked).let { Vec2(kotlin.math.cos(it), kotlin.math.sin(it)) } else d
            }
            wd.points.size >= 2 -> (last - wd.points[wd.points.size - 2]).normalized().perp()
            else -> Vec2(1.0, 0.0)
        }
        val p = last + dir * length
        _state.update { it.copy(wallDraw = wd.copy(points = wd.points + p, cursor = p), snapGuides = emptyList()) }
    }

    fun drawWallsUndo() = _state.update { s ->
        val wd = s.wallDraw ?: return@update s
        s.copy(wallDraw = wd.copy(points = wd.points.dropLast(1), closing = false))
    }

    /** "Chiudi stanza": con almeno 3 angoli si chiede il tipo di stanza. */
    fun drawWallsClose() = _state.update { s ->
        val wd = s.wallDraw ?: return@update s
        if (wd.points.size < 3) s else s.copy(wallDraw = wd.copy(closing = true, cursor = null), snapGuides = emptyList())
    }

    /** Torna al disegno dalla richiesta del tipo di stanza. */
    fun drawWallsResume() = _state.update { s -> s.wallDraw?.let { s.copy(wallDraw = it.copy(closing = false)) } ?: s }

    /** Stanza con gli angoli disegnati (mezzeria dei muri), in ordine orario sullo schermo come tutte le stanze. */
    fun finishDrawWalls(type: RoomType, ceilingHeight: Double, wallThickness: Double) {
        val wd = _state.value.wallDraw ?: return
        if (wd.points.size < 3) return
        var pts = wd.points
        val signed = pts.indices.sumOf { i -> val a = pts[i]; val b = pts[(i + 1) % pts.size]; a.x * b.y - b.x * a.y }
        if (signed < 0) pts = pts.reversed()
        val plan = _state.value.plan
        val room = Room(
            id = plan.nextId,
            name = RoomFactory.autoName(plan, type),
            type = type,
            points = pts,
            ceilingHeight = ceilingHeight,
            wallThickness = wallThickness,
        )
        commit { it.copy(rooms = it.rooms + room) }
        _state.update { it.copy(wallDraw = null, snapGuides = emptyList(), focusedRoomId = room.id, selection = null) }
        tutorialEvent(TutorialEvent.RoomAdded)
    }

    // ---------- Fuoco e selezione (§4, §10) ----------

    fun focusRoom(id: Long) = _state.update { s ->
        val keepSel = s.selection?.roomId == id
        s.copy(focusedRoomId = id, selection = if (keepSel) s.selection else null)
    }

    fun clearFocus() =
        _state.update { it.copy(focusedRoomId = null, selection = null, pendingOpening = null, pendingFixture = null) }

    fun selectWall(roomId: Long, index: Int) =
        _state.update { it.copy(focusedRoomId = roomId, selection = Selection.Wall(roomId, index)) }

    fun closeSelection() = _state.update { it.copy(selection = null) }

    /** Punto da tenere visibile: l'oggetto selezionato o, senza selezione, l'etichetta della stanza a fuoco. */
    private fun focusAnchor(s: EditorUiState): Vec2? = when (val sel = s.selection) {
        is Selection.Wall -> s.plan.room(sel.roomId)?.takeIf { sel.index < it.wallCount }
            ?.let { (it.wallStart(sel.index) + it.wallEnd(sel.index)) / 2.0 }
        is Selection.Opening -> s.plan.room(sel.roomId)?.let { r ->
            r.opening(sel.openingId)?.let { o -> Openings.span(r, o).let { (a, b) -> (a + b) / 2.0 } }
        }
        is Selection.Fixture -> s.plan.room(sel.roomId)?.let { r -> r.fixture(sel.fixtureId)?.let { Fixtures.center(r, it) } }
        is Selection.Stair -> s.plan.stair(sel.stairId)?.center
        is Selection.StairWell -> s.planBelow?.stair(sel.stairId)?.center
        is Selection.Column -> s.plan.column(sel.columnId)?.center
        is Selection.Beam -> s.plan.beam(sel.beamId)?.mid
        is Selection.FreeWall -> s.plan.freeWall(sel.wallId)?.mid
        is Selection.Furniture -> s.plan.furniture(sel.furnitureId)?.center
        is Selection.Dimension -> s.plan.dimension(sel.dimensionId)?.let { (it.lineA + it.lineB) / 2.0 }
        is Selection.Annotation -> s.plan.annotation(sel.annotationId)?.at
        null -> s.plan.room(s.focusedRoomId)?.let { Polygon.labelPoint(it.points) }
    }

    /**
     * Vista centrata nello spazio libero dal riquadro caratteristiche (da 0 a `visibleRightPx` in
     * orizzontale, da 0 a `visibleBottomPx` in verticale), senza cambiare lo zoom: la stanza
     * selezionata se ne è aperta la tendina "Info", altrimenti l'intera pianta. In verticale il
     * riquadro sta in basso e la pianta sale; in orizzontale sta a destra e la pianta va a sinistra.
     * Se la pianta non ci sta, scorre solo quanto serve e l'oggetto selezionato (muro, apertura)
     * resta visibile. Null se non c'è niente da centrare.
     */
    fun centeredCamera(visibleRightPx: Float, visibleBottomPx: Float, marginPx: Float, visibleTopPx: Float = 0f): Camera? {
        val s = _state.value
        if (visibleRightPx <= 0f || visibleBottomPx - visibleTopPx <= 0f) return null
        val midY = (visibleTopPx + visibleBottomPx) / 2
        // Tendina della stanza aperta: è la stanza ad andare al centro dello spazio libero, anche in una
        // pianta grande in cui il resto esce dallo schermo. Con la sola striscia "Info" la pianta resta com'è.
        if (s.selection == null && s.infoExpanded) s.plan.room(s.focusedRoomId)?.let { room ->
            val rb = Polygon.bounds(room.points)
            val k = s.camera.scale
            return s.camera.copy(
                offsetX = (visibleRightPx / 2 - rb.center.x * k).toFloat(),
                offsetY = (midY - rb.center.y * k).toFloat(),
            )
        }
        val b = viewBounds(s) ?: return null
        val cam = s.camera
        val k = cam.scale
        /**
         * Asse in cui la pianta non ci sta: la si fa scorrere solo quanto serve a eliminare lo spazio
         * vuoto da un lato mentre dall'altro esce dallo schermo (mostra quanta più pianta possibile).
         * Lo spazio visibile su quell'asse va da `start` a `end`.
         */
        fun fill(offset: Float, min: Double, max: Double, start: Float, end: Float): Float {
            val before = (min * k + offset) - (start + marginPx)
            val after = (max * k + offset) - (end - marginPx)
            return when {
                before > 0 && after > 0 -> offset - minOf(before, after).toFloat()
                before < 0 && after < 0 -> offset + minOf(-before, -after).toFloat()
                else -> offset
            }
        }
        /** Tiene il punto selezionato dentro [start + margin, end - margin] su un asse. */
        fun keepVisible(offset: Float, coord: Double, start: Float, end: Float): Float {
            val p = (coord * k + offset).toFloat()
            return when {
                p > end - marginPx -> offset - (p - (end - marginPx))
                p < start + marginPx -> offset + (start + marginPx - p)
                else -> offset
            }
        }
        val anchor = focusAnchor(s)
        val ox = if (b.width * k <= visibleRightPx - 2 * marginPx) (visibleRightPx / 2 - b.center.x * k).toFloat()
        else fill(cam.offsetX, b.minX, b.maxX, 0f, visibleRightPx).let { o -> anchor?.let { keepVisible(o, it.x, 0f, visibleRightPx) } ?: o }
        val oy = if (b.height * k <= visibleBottomPx - visibleTopPx - 2 * marginPx) (midY - b.center.y * k).toFloat()
        else fill(cam.offsetY, b.minY, b.maxY, visibleTopPx, visibleBottomPx)
            .let { o -> anchor?.let { keepVisible(o, it.y, visibleTopPx, visibleBottomPx) } ?: o }
        return Camera(k, ox, oy)
    }

    /**
     * Usato dall'animazione di ricentratura: sposta la vista senza toccare lo zoom, così un'animazione
     * in corso non può riportare uno zoom vecchio sopra un adattamento appena fatto (es. rotazione).
     */
    fun setCameraOffset(offsetX: Float, offsetY: Float) =
        _state.update { it.copy(camera = it.camera.copy(offsetX = offsetX, offsetY = offsetY)) }

    fun selectOpening(roomId: Long, openingId: Long) =
        _state.update { it.copy(focusedRoomId = roomId, selection = Selection.Opening(roomId, openingId)) }

    fun selectFixture(roomId: Long, fixtureId: Long) =
        _state.update { it.copy(focusedRoomId = roomId, selection = Selection.Fixture(roomId, fixtureId)) }

    // ---------- Vista 3D ----------

    /** Passa dalla pianta 2D alla vista 3D e viceversa; selezione e tendina restano come sono. */
    fun setView3d(on: Boolean) {
        _state.update { it.copy(view3d = on) }
        if (on) showTip(Tip.View3D)
    }

    // ---------- Tendina informazioni ----------

    fun toggleInfo() = setInfoExpanded(!_state.value.infoExpanded)

    fun setInfoExpanded(expanded: Boolean) {
        _state.update { it.copy(infoExpanded = expanded) }
        if (expanded) tutorialEvent(TutorialEvent.InfoOpened)
    }

    /** Esegue `action` e, se ha cambiato stanza o oggetto selezionato, richiude la tendina (resta la striscia). */
    private inline fun collapsingInfoOnNewSelection(action: () -> Unit) {
        val before = _state.value.let { it.focusedRoomId to it.selection }
        action()
        if ((_state.value.focusedRoomId to _state.value.selection) == before) return
        _state.update { it.copy(infoExpanded = false) }
        showTipForSelection()
    }

    /** Tocco senza trascinamento; `at` è il punto toccato in cm. */
    fun onTap(target: DragTarget, at: Vec2) = collapsingInfoOnNewSelection { tap(target, at) }

    // ---------- Distanza tra due oggetti ----------

    /** "📐 Distanza": si torna alla pianta e i prossimi due tocchi scelgono gli oggetti. */
    fun startMeasure() {
        _state.update {
            it.copy(measure = MeasureState(), selection = null, focusedRoomId = null, pendingOpening = null, pendingFixture = null, view3d = false, infoExpanded = false)
        }
        showTip(Tip.Measure)
    }

    fun closeMeasure() = _state.update { it.copy(measure = null) }

    /** Oggetto da misurare sotto il dito (una stanza, il vuoto, il metro non si misurano). */
    private fun measureTarget(target: DragTarget, s: EditorUiState): MeasureTarget? = when (target) {
        is DragTarget.Wall -> MeasureTarget.Wall(target.roomId, target.index)
        is DragTarget.CutStart -> MeasureTarget.Wall(target.roomId, target.index)
        is DragTarget.Opening -> MeasureTarget.Opening(target.roomId, target.openingId)
        is DragTarget.Fixture -> MeasureTarget.Fixture(target.roomId, target.fixtureId)
        is DragTarget.Column -> MeasureTarget.Column(target.columnId)
        is DragTarget.BeamBody -> MeasureTarget.Beam(target.beamId)
        is DragTarget.BeamEnd -> MeasureTarget.Beam(target.beamId)
        is DragTarget.Stair -> MeasureTarget.Stair(target.stairId)
        is DragTarget.FreeWallBody -> MeasureTarget.FreeWall(target.wallId)
        is DragTarget.FreeWallEnd -> MeasureTarget.FreeWall(target.wallId)
        is DragTarget.FurnitureBody -> MeasureTarget.Furniture(target.furnitureId)
        is DragTarget.FurnitureRotate -> MeasureTarget.Furniture(target.furnitureId)
        // Un angolo: il muro che parte da quell'angolo.
        is DragTarget.Corner -> s.plan.room(target.roomId)?.let { MeasureTarget.Wall(it.id, target.index) }
        else -> null
    }

    private fun measureTap(target: DragTarget) {
        val s = _state.value
        val m = s.measure ?: return
        val t = measureTarget(target, s) ?: return
        _state.update {
            it.copy(
                measure = when {
                    m.first == null -> MeasureState(first = t)
                    m.second == null -> m.copy(second = t)
                    else -> MeasureState(first = t) // misura già fatta: si ricomincia da questo oggetto
                },
            )
        }
    }

    private fun tap(target: DragTarget, at: Vec2) {
        val s = _state.value
        if (s.measure != null) return measureTap(target)
        // Incolla: si resta in modalità per mettere altre copie; un tocco fuori posto non fa nulla.
        if (s.pasting) { pasteAt(target, at); return }
        // Impianto in attesa: a muro va sul muro toccato, le luci nel punto toccato dentro una stanza.
        s.pendingFixture?.let { kind ->
            if (kind.mount == Mount.Wall) {
                val wall = when (target) {
                    is DragTarget.Wall -> target.roomId to target.index
                    is DragTarget.Opening -> target.roomId to (s.plan.room(target.roomId)?.opening(target.openingId)?.wallIndex ?: -1)
                    else -> null
                }
                // Su un muro eliminato (lato aperto) non si mette niente.
                if (wall != null && wall.second >= 0 && !isRemovedWall(s.plan, wall)) { placeWallFixture(wall.first, wall.second, at); return }
            } else {
                val room = s.plan.rooms.lastOrNull { Polygon.contains(it.points, at) }
                if (room != null) { placeCeilingFixture(room.id, at); return }
            }
        }
        // Apertura in attesa di posizionamento: va sul muro toccato, di qualunque stanza (non serve
        // averla selezionata prima); quella stanza diventa la stanza attiva.
        if (s.pendingOpening != null) {
            val wall = when (target) {
                is DragTarget.Wall -> target.roomId to target.index
                is DragTarget.Opening -> target.roomId to (s.plan.room(target.roomId)?.opening(target.openingId)?.wallIndex ?: -1)
                else -> null
            }
            if (wall != null && wall.second >= 0 && !isRemovedWall(s.plan, wall)) {
                placeOpening(wall.first, wall.second, at)
                return
            }
        }
        when (target) {
            is DragTarget.Wall -> selectWall(target.roomId, target.index)
            is DragTarget.Opening -> selectOpening(target.roomId, target.openingId)
            is DragTarget.Fixture -> selectFixture(target.roomId, target.fixtureId)
            is DragTarget.CutStart -> selectWall(target.roomId, target.index)
            is DragTarget.Corner -> focusRoom(target.roomId)
            is DragTarget.RoomLabel -> focusRoom(target.roomId)
            is DragTarget.RoomInterior -> focusRoom(target.roomId)
            is DragTarget.RulerDelete -> deleteRuler(target.rulerId)
            is DragTarget.Stair -> selectStair(target.stairId)
            is DragTarget.StairWell -> selectStairWell(target.stairId)
            is DragTarget.Column -> selectColumn(target.columnId)
            is DragTarget.BeamBody -> selectBeam(target.beamId)
            is DragTarget.BeamEnd -> selectBeam(target.beamId)
            is DragTarget.FreeWallBody -> selectFreeWall(target.wallId)
            is DragTarget.FreeWallEnd -> selectFreeWall(target.wallId)
            is DragTarget.FurnitureBody -> selectFurniture(target.furnitureId)
            is DragTarget.FurnitureRotate -> selectFurniture(target.furnitureId)
            is DragTarget.RulerEnd, is DragTarget.RulerBody, is DragTarget.RulerRotate -> Unit
            is DragTarget.DimensionLine -> selectDimension(target.dimensionId)
            is DragTarget.DimensionEnd -> selectDimension(target.dimensionId)
            is DragTarget.AnnotationBody -> selectAnnotation(target.annotationId)
            is DragTarget.AnnotationArrow -> selectAnnotation(target.annotationId)
            DragTarget.Background -> clearFocus()
        }
    }

    // ---------- Metro (§9) ----------

    /** Nuovo metro orizzontale da 100 cm al centro della vista. */
    fun addRuler() {
        val cam = _state.value.camera
        val c = cam.toWorld(viewportW / 2, viewportH / 2)
        val half = Vec2(Ruler.DEFAULT_LENGTH / 2, 0.0)
        commit { it.copy(rulers = it.rulers + Ruler(it.nextRulerId, c - half, c + half)) }
        tutorialEvent(TutorialEvent.RulerAdded)
        showTip(Tip.Ruler)
    }

    fun deleteRuler(id: Long) = commit { it.copy(rulers = it.rulers.filter { r -> r.id != id }) }

    // ---------- Quote manuali e testi ----------

    /** Tutte le modalità "in attesa di un tocco" si chiudono quando ne parte un'altra. */
    private fun EditorUiState.withoutTools() = copy(
        pendingOpening = null, pendingFixture = null, measure = null, wallDraw = null, pasting = false,
        selectMode = false, multi = emptySet(), selectRect = null, dimensionDraw = null, textPlacing = false,
        selection = null, focusedRoomId = null, view3d = false, infoExpanded = false,
    )

    /** "📏 Quota": i prossimi due tocchi sono gli estremi da misurare (agganciati a angoli e facce dei muri). */
    fun startDimension() = _state.update { it.withoutTools().copy(dimensionDraw = DimensionDraw()) }

    fun cancelDimension() = _state.update { it.copy(dimensionDraw = null, snapGuides = emptyList()) }

    /** Punto agganciato per le quote: angoli, punti medi, facce dei muri; allineato al primo punto. */
    private fun dimensionSnap(p: Vec2, first: Vec2?) =
        SnapEngine.snap(p, SnapEngine.targets(_state.value.plan, faces = true), snapTolerance(), alignOnly = listOfNotNull(first))

    /** Il puntatore si muove (mouse): anteprima della quota, già agganciata. */
    fun dimensionCursor(p: Vec2) = _state.update { s ->
        val d = s.dimensionDraw ?: return@update s
        val r = dimensionSnap(p, d.first)
        s.copy(dimensionDraw = d.copy(cursor = r.point), snapGuides = r.guides)
    }

    /**
     * Tocco con lo strumento "Quota": il primo mette l'inizio, il secondo crea la quota (la linea 40 cm di lato,
     * dalla parte opposta al centro della stanza più vicina, così non copre il disegno). Si resta nello strumento.
     */
    fun dimensionTap(p: Vec2) {
        val d = _state.value.dimensionDraw ?: return
        val pt = dimensionSnap(p, d.first).point
        val first = d.first
        if (first == null) {
            _state.update { it.copy(dimensionDraw = DimensionDraw(first = pt, cursor = pt), snapGuides = emptyList()) }
            return
        }
        if (first.distanceTo(pt) < 1.0) return
        val plan = _state.value.plan
        val base = Dimension(plan.nextDimensionId, first, pt)
        val mid = (first + pt) / 2.0
        val room = plan.rooms.lastOrNull { Polygon.contains(it.points, mid + base.normal * 5.0) || Polygon.contains(it.points, mid - base.normal * 5.0) }
        // Lato della linea: verso l'esterno della stanza su cui si misura (o a sinistra se non c'è).
        val side = room?.let { r -> if (Polygon.contains(r.points, mid + base.normal * 5.0)) -1.0 else 1.0 } ?: 1.0
        val dim = base.copy(offset = 40.0 * side)
        commit { it.copy(dimensions = it.dimensions + dim) }
        _state.update { it.copy(dimensionDraw = DimensionDraw(), snapGuides = emptyList()) }
    }

    fun selectDimension(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Dimension(id)) }
    fun updateDimension(d: Dimension) = commit { it.replace(d) }
    fun removeDimension(id: Long) {
        commit { it.copy(dimensions = it.dimensions.filter { d -> d.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    /** "T Testo": il prossimo tocco sceglie il punto, poi si scrive il testo. */
    fun startText() = _state.update { it.withoutTools().copy(textPlacing = true) }
    fun cancelText() = _state.update { it.copy(textPlacing = false, textDialogAt = null) }

    /** Tocco con lo strumento "Testo": lì si apre la finestra per scrivere. */
    fun textTap(at: Vec2) = _state.update { if (it.textPlacing) it.copy(textDialogAt = at) else it }

    /** Testo nuovo nel punto scelto (vuoto: niente); resta selezionato per spostarlo o cambiarlo. */
    fun addAnnotation(at: Vec2, text: String, size: Double) {
        _state.update { it.copy(textPlacing = false, textDialogAt = null) }
        if (text.isBlank()) return
        val t = TextNote(_state.value.plan.nextAnnotationId, at, text.trim(), size)
        commit { it.copy(annotations = it.annotations + t) }
        selectAnnotation(t.id)
    }

    fun selectAnnotation(id: Long) = _state.update { it.copy(focusedRoomId = null, selection = Selection.Annotation(id)) }
    fun updateAnnotation(t: TextNote) = commit { it.replace(t) }
    fun removeAnnotation(id: Long) {
        commit { it.copy(annotations = it.annotations.filter { t -> t.id != id }) }
        _state.update { it.copy(selection = null) }
    }

    private fun selectionExists(plan: FloorPlan, sel: Selection): Boolean {
        if (sel is Selection.Stair) return plan.stair(sel.stairId) != null
        // Il vano è di una scala del piano di sotto: resta finché si è su questo piano.
        if (sel is Selection.StairWell) return true
        if (sel is Selection.Column) return plan.column(sel.columnId) != null
        if (sel is Selection.Beam) return plan.beam(sel.beamId) != null
        if (sel is Selection.FreeWall) return plan.freeWall(sel.wallId) != null
        if (sel is Selection.Furniture) return plan.furniture(sel.furnitureId) != null
        if (sel is Selection.Dimension) return plan.dimension(sel.dimensionId) != null
        if (sel is Selection.Annotation) return plan.annotation(sel.annotationId) != null
        val room = plan.room(sel.roomId) ?: return false
        return when (sel) {
            is Selection.Wall -> sel.index < room.wallCount
            is Selection.Opening -> room.opening(sel.openingId) != null
            is Selection.Fixture -> room.fixture(sel.fixtureId) != null
            is Selection.Stair, is Selection.StairWell, is Selection.Column, is Selection.Beam, is Selection.FreeWall, is Selection.Furniture,
            is Selection.Dimension, is Selection.Annotation -> true
        }
    }

    // ---------- Pareti sotto il tetto (taglio diagonale) ----------

    /**
     * Attiva o toglie il taglio diagonale del muro: la parete scende verso il muro perpendicolare più basso
     * (quello indicato da `towardEnd`, altrimenti il più basso dei due), partendo a metà muro.
     */
    fun setWallCut(roomId: Long, index: Int, on: Boolean, towardEnd: Boolean? = null) {
        applyWallCut(roomId, index, on, towardEnd)
        if (on) showTip(Tip.WallCut)
    }

    private fun applyWallCut(roomId: Long, index: Int, on: Boolean, towardEnd: Boolean?) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
        if (!on) return@commit plan.replace(room.copy(wallCuts = room.wallCuts - index))
        val options = Ceilings.cutOptions(room, index)
        val toward = towardEnd?.takeIf { it in options }
            ?: options.minByOrNull { room.wallHeight(Ceilings.lowWall(room, index, WallCut(it, 0.0))) }
            ?: return@commit plan
        val start = room.wallCuts[index]?.start ?: (room.wallLength(index) / 2)
        plan.replace(room.copy(wallCuts = room.wallCuts + (index to WallCut(toward, Ceilings.clampStart(room, index, start)))))
    }

    /** Distanza (cm, dal muro basso) da cui la parete inizia a scendere. */
    fun setWallCutStart(roomId: Long, index: Int, start: Double) = commit { plan ->
        val room = plan.room(roomId) ?: return@commit plan
        val cut = room.wallCuts[index] ?: return@commit plan
        plan.replace(room.copy(wallCuts = room.wallCuts + (index to cut.copy(start = Ceilings.clampStart(room, index, start)))))
    }

    // ---------- Impianti ----------

    /** Sempre disponibile: l'impianto andrà sul muro (o, per le luci, nel punto della stanza) che si tocca. */
    fun startPlacingFixture(kind: FixtureKind) =
        _state.update { it.copy(pendingFixture = kind, pendingOpening = null, selection = null, pasting = false) }

    private fun placeWallFixture(roomId: Long, wallIndex: Int, at: Vec2) {
        val kind = _state.value.pendingFixture ?: return
        val plan = _state.value.plan
        val room = plan.room(roomId) ?: return
        val base = Fixture.default(plan.nextFixtureId, kind).copy(wallIndex = wallIndex)
        val pos = Openings.clampPosition(room.wallLength(wallIndex), base.length, Openings.projectOnWall(room, wallIndex, at))
        addFixture(room, base.copy(position = pos))
    }

    private fun placeCeilingFixture(roomId: Long, at: Vec2) {
        val kind = _state.value.pendingFixture ?: return
        val plan = _state.value.plan
        val room = plan.room(roomId) ?: return
        addFixture(room, Fixture.default(plan.nextFixtureId, kind).copy(point = at))
    }

    private fun addFixture(room: Room, fixture: Fixture) {
        commit { it.replace(room.copy(fixtures = room.fixtures + fixture)) }
        _state.update {
            it.copy(pendingFixture = null, focusedRoomId = room.id, selection = Selection.Fixture(room.id, fixture.id))
        }
        tutorialEvent(TutorialEvent.FixtureAdded)
    }

    fun updateFixture(roomId: Long, fixture: Fixture) = commit { plan ->
        val room = plan.room(roomId) ?: return@commit plan
        plan.replace(room.copy(fixtures = room.fixtures.map { if (it.id == fixture.id) fixture else it }))
    }

    fun removeFixture(roomId: Long, fixtureId: Long) {
        commit { plan ->
            val room = plan.room(roomId) ?: return@commit plan
            plan.replace(room.copy(fixtures = room.fixtures.filter { it.id != fixtureId }))
        }
        _state.update { it.copy(selection = null) }
    }

    // ---------- Aperture (§5) ----------

    /** Disponibile sempre, anche senza stanza selezionata: l'apertura andrà sul muro che si tocca. */
    fun startPlacingOpening(pending: PendingOpening) =
        _state.update { it.copy(pendingOpening = pending, pendingFixture = null, selection = null, pasting = false) }

    /** Annulla il posizionamento in corso (apertura o impianto). */
    fun cancelPlacingOpening() = _state.update { it.copy(pendingOpening = null, pendingFixture = null) }

    private fun placeOpening(roomId: Long, wallIndex: Int, at: Vec2) {
        val pending = _state.value.pendingOpening ?: return
        val plan = _state.value.plan
        val room = plan.room(roomId) ?: return
        val base = Opening.default(plan.nextOpeningId, pending.kind, wallIndex, 0.0)
            .copy(style = pending.style, sliding = pending.sliding)
        val pos = Openings.clampPosition(room.wallLength(wallIndex), base.width, Openings.projectOnWall(room, wallIndex, at))
        val opening = base.copy(position = pos)
        commit { it.replace(room.copy(openings = room.openings + opening)) }
        _state.update {
            it.copy(pendingOpening = null, focusedRoomId = roomId, selection = Selection.Opening(roomId, opening.id))
        }
        tutorialEvent(TutorialEvent.OpeningAdded)
    }

    fun updateOpening(roomId: Long, opening: Opening) = commit { plan ->
        val room = plan.room(roomId) ?: return@commit plan
        plan.replace(room.copy(openings = room.openings.map { if (it.id == opening.id) opening else it }))
    }

    fun removeOpening(roomId: Long, openingId: Long) {
        commit { plan ->
            val room = plan.room(roomId) ?: return@commit plan
            plan.replace(room.copy(openings = room.openings.filter { it.id != openingId }))
        }
        _state.update { it.copy(selection = null) }
    }

    // ---------- Trascinamenti (§2) ----------

    /** `at` è il punto (cm) in cui è iniziato il trascinamento. */
    fun beginDrag(target: DragTarget, at: Vec2) = collapsingInfoOnNewSelection { startDrag(target, at) }

    private fun startDrag(target: DragTarget, at: Vec2) {
        when (target) {
            is DragTarget.Corner -> focusRoom(target.roomId)
            is DragTarget.Wall -> focusRoom(target.roomId)
            is DragTarget.RoomLabel -> focusRoom(target.roomId)
            is DragTarget.Opening -> selectOpening(target.roomId, target.openingId)
            is DragTarget.Fixture -> selectFixture(target.roomId, target.fixtureId)
            is DragTarget.RulerRotate -> _state.update { it.copy(rotatingRulerId = target.rulerId) }
            is DragTarget.RulerEnd, is DragTarget.RulerBody -> Unit
            is DragTarget.CutStart -> selectWall(target.roomId, target.index)
            is DragTarget.Stair -> selectStair(target.stairId)
            is DragTarget.Column -> selectColumn(target.columnId)
            is DragTarget.BeamBody -> selectBeam(target.beamId)
            is DragTarget.BeamEnd -> selectBeam(target.beamId)
            is DragTarget.FreeWallBody -> selectFreeWall(target.wallId)
            is DragTarget.FreeWallEnd -> selectFreeWall(target.wallId)
            is DragTarget.FurnitureBody -> selectFurniture(target.furnitureId)
            is DragTarget.FurnitureRotate -> selectFurniture(target.furnitureId)
            is DragTarget.DimensionLine -> selectDimension(target.dimensionId)
            is DragTarget.DimensionEnd -> selectDimension(target.dimensionId)
            is DragTarget.AnnotationBody -> selectAnnotation(target.annotationId)
            is DragTarget.AnnotationArrow -> selectAnnotation(target.annotationId)
            else -> return
        }
        dragStartPoint = at
        dragTarget = target
        dragStartPlan = _state.value.plan
        history.record(_state.value.fullBuilding)
        _state.update { it.copy(dragging = true) }
    }

    /** `totalDelta` è lo spostamento complessivo in cm dall'inizio del trascinamento. */
    fun dragBy(target: DragTarget, totalDelta: Vec2) {
        val start = dragStartPlan ?: return
        snapGuides = emptyList()
        val plan = when (target) {
            is DragTarget.Corner -> {
                val room = start.room(target.roomId) ?: return
                val i = target.index
                val n = room.points.size
                // Aggancio CAD: estremità e punti medi delle altre stanze, allineamenti con tutti gli angoli.
                val neighbors = listOf(room.points[(i - 1 + n) % n], room.points[(i + 1) % n])
                val own = room.points.filterIndexed { k, _ -> k != i }
                val r = SnapEngine.snap(room.points[i] + totalDelta, SnapEngine.targets(start, excludeRoom = room.id), snapTolerance(), neighbors, own)
                val moved = room.points.toMutableList().also { it[i] = r.point }
                snapGuides = r.guides
                start.replace(room.copy(points = if (r.snapped) Snapping.straighten(moved, i) else moved))
            }
            // Punto da cui la parete inizia a scendere: scorre lungo il muro.
            is DragTarget.CutStart -> {
                val room = start.room(target.roomId) ?: return
                val cut = room.wallCuts[target.index] ?: return
                val corner = Ceilings.lowCorner(room, target.index, cut)
                val other = if (cut.towardEnd) room.wallStart(target.index) else room.wallEnd(target.index)
                val away = (other - corner).normalized()
                val moved = Ceilings.clampStart(room, target.index, cut.start + (totalDelta dot away))
                start.replace(room.copy(wallCuts = room.wallCuts + (target.index to cut.copy(start = moved))))
            }
            is DragTarget.Wall -> {
                val room = start.room(target.roomId) ?: return
                // Vicino a un muro parallelo di un'altra stanza scatta sulla sua linea: i due muri si sovrappongono.
                val otherWalls = start.rooms.filter { it.id != room.id }.flatMap { Snapping.wallsOf(it) }
                start.replace(room.copy(points = Snapping.moveWallSnapped(room.points, target.index, totalDelta, otherWalls)))
            }
            is DragTarget.RoomLabel -> {
                val room = start.room(target.roomId) ?: return
                val others = start.rooms.filter { it.id != room.id }
                val d = Snapping.snapRoomTranslation(room, others, totalDelta)
                // Con la stanza si spostano anche le luci al soffitto (aperture e impianti a muro seguono i muri).
                start.replace(Fixtures.movedRoom(room, d))
            }
            is DragTarget.Fixture -> {
                val room = start.room(target.roomId) ?: return
                val f = room.fixture(target.fixtureId) ?: return
                val moved = when (f.kind.mount) {
                    // A muro: scorre lungo il perimetro della stanza, passando da un muro all'altro.
                    Mount.Wall -> {
                        val len = room.wallLength(f.wallIndex)
                        val u = (room.wallEnd(f.wallIndex) - room.wallStart(f.wallIndex)).normalized()
                        val from = room.wallStart(f.wallIndex) + u * Openings.clampPosition(len, f.length, f.position)
                        val (j, pos) = alongPerimeter(room, from + totalDelta, f.length) ?: return
                        f.copy(wallIndex = j, position = pos)
                    }
                    // Al soffitto: spostamento libero.
                    Mount.Ceiling -> f.copy(point = f.point + totalDelta)
                }
                start.replace(room.copy(fixtures = room.fixtures.map { if (it.id == f.id) moved else it }))
            }
            is DragTarget.Opening -> {
                val room = start.room(target.roomId) ?: return
                val o = room.opening(target.openingId) ?: return
                val len = room.wallLength(o.wallIndex)
                val u = (room.wallEnd(o.wallIndex) - room.wallStart(o.wallIndex)).normalized()
                // Si parte dal centro effettivo (già limitato al muro) e si segue il dito lungo tutto il
                // perimetro della stanza: vicino a un altro muro l'apertura ci passa sopra.
                val from = room.wallStart(o.wallIndex) + u * Openings.clampPosition(len, o.width, o.position)
                val (j, pos) = alongPerimeter(room, from + totalDelta, o.width) ?: return
                start.replace(room.copy(openings = room.openings.map { if (it.id == o.id) o.copy(wallIndex = j, position = pos) else it }))
            }
            is DragTarget.Stair -> {
                val st = start.stair(target.stairId) ?: return
                start.replace(Stairs.snapped(start, st.copy(center = st.center + totalDelta), _state.value.levelHeight))
            }
            is DragTarget.Column -> {
                val c = start.column(target.columnId) ?: return
                start.replace(Structure.snapped(start, c.copy(center = c.center + totalDelta)))
            }
            is DragTarget.FreeWallBody -> {
                val w = start.freeWall(target.wallId) ?: return
                start.replace(Structure.snapped(start, w.copy(start = w.start + totalDelta, end = w.end + totalDelta)))
            }
            is DragTarget.FreeWallEnd -> {
                val w = start.freeWall(target.wallId) ?: return
                // Estremità trascinata: raddrizzata a 90° se ci è vicina, poi agganciata agli altri muri.
                if (target.endIndex == 0) {
                    val p = Structure.snapAngle(w.start + totalDelta, w.end)
                    start.replace(w.copy(start = Structure.snapWallEnd(start, p, w.end, w.id)))
                } else {
                    val p = Structure.snapAngle(w.end + totalDelta, w.start)
                    start.replace(w.copy(end = Structure.snapWallEnd(start, p, w.start, w.id)))
                }
            }
            is DragTarget.FurnitureBody -> {
                val f = start.furniture(target.furnitureId) ?: return
                // Prima l'allineamento con gli altri arredi (centri), poi l'accostamento ai muri, che vince.
                val others = start.furniture.filter { it.id != f.id }.map { it.center }
                val aligned = SnapEngine.snap(f.center + totalDelta, SnapEngine.Targets(emptyList(), emptyList(), emptyList()), snapTolerance(), alignOnly = others)
                val placed = Furnishings.snapped(start, f.copy(center = aligned.point))
                snapGuides = aligned.guides.filter { g ->
                    // Guida solo sugli assi rimasti allineati dopo l'accostamento al muro.
                    val from = g.from ?: return@filter false
                    if (g.kind == SnapKind.AlignVertical) kotlin.math.abs(placed.center.x - from.x) < 0.01 else kotlin.math.abs(placed.center.y - from.y) < 0.01
                }.map { it.copy(point = placed.center) }
                start.replace(placed)
            }
            is DragTarget.FurnitureRotate -> {
                val f = start.furniture(target.furnitureId) ?: return
                start.replace(f.copy(rotation = Furnishings.rotationToward(f, dragStartPoint + totalDelta)))
            }
            is DragTarget.BeamBody -> {
                val b = start.beam(target.beamId) ?: return
                start.replace(Structure.snapped(start, b.copy(start = b.start + totalDelta, end = b.end + totalDelta)))
            }
            is DragTarget.BeamEnd -> {
                val b = start.beam(target.beamId) ?: return
                // L'estremità trascinata: raddrizzata se la trave è quasi orizzontale o verticale, poi appoggiata al muro.
                if (target.endIndex == 0) {
                    val p = Structure.snapAngle(b.start + totalDelta, b.end)
                    start.replace(b.copy(start = Structure.snapBeamEnd(start, p, b.end, b.id)))
                } else {
                    val p = Structure.snapAngle(b.end + totalDelta, b.start)
                    start.replace(b.copy(end = Structure.snapBeamEnd(start, p, b.start, b.id)))
                }
            }
            is DragTarget.RulerBody -> {
                val r = start.ruler(target.rulerId) ?: return
                start.replace(r.copy(start = r.start + totalDelta, end = r.end + totalDelta))
            }
            is DragTarget.RulerEnd -> {
                val r = start.ruler(target.rulerId) ?: return
                // Estremità libera che si aggancia agli spigoli (anche delle facce dei muri) e, vicino
                // all'orizzontale o alla verticale, si allinea all'altra estremità.
                val moving = if (target.endIndex == 0) r.start else r.end
                val other = if (target.endIndex == 0) r.end else r.start
                val snap = SnapEngine.snap(moving + totalDelta, SnapEngine.targets(start, faces = true), snapTolerance(), alignOnly = listOf(other))
                snapGuides = snap.guides
                val p = snap.point.takeIf { it.distanceTo(other) >= Ruler.MIN_LENGTH } ?: moving
                start.replace(if (target.endIndex == 0) r.copy(start = p) else r.copy(end = p))
            }
            is DragTarget.RulerRotate -> {
                val r = start.ruler(target.rulerId) ?: return
                start.replace(Rulers.rotateToward(r, dragStartPoint + totalDelta))
            }
            // Linea di quota: si allontana o si avvicina (a passi di 5 cm), restando parallela.
            is DragTarget.DimensionLine -> {
                val d = start.dimension(target.dimensionId) ?: return
                val off = roundHalfUp((d.offset + (totalDelta dot d.normal)) / 5.0) * 5.0
                start.replace(d.copy(offset = off))
            }
            // Estremità della quota: agganciata a angoli e facce dei muri, allineata all'altra estremità.
            is DragTarget.DimensionEnd -> {
                val d = start.dimension(target.dimensionId) ?: return
                val moving = if (target.endIndex == 0) d.a else d.b
                val other = if (target.endIndex == 0) d.b else d.a
                val snap = dimensionSnap(moving + totalDelta, other)
                snapGuides = snap.guides
                val p = snap.point.takeIf { it.distanceTo(other) >= 1.0 } ?: moving
                start.replace(if (target.endIndex == 0) d.copy(a = p) else d.copy(b = p))
            }
            is DragTarget.AnnotationBody -> {
                val t = start.annotation(target.annotationId) ?: return
                start.replace(t.copy(at = t.at + totalDelta))
            }
            is DragTarget.AnnotationArrow -> {
                val t = start.annotation(target.annotationId) ?: return
                val tip = t.arrowTo ?: return
                start.replace(t.copy(arrowTo = SnapEngine.snap(tip + totalDelta, SnapEngine.targets(start, faces = true), snapTolerance()).point))
            }
            else -> return
        }
        val guides = snapGuides
        _state.update { it.copy(plan = plan, snapGuides = guides) }
    }

    /** Guide dell'aggancio del trascinamento in corso (le imposta [dragBy], le toglie [endDrag]). */
    private var snapGuides: List<SnapGuide> = emptyList()

    /** Tolleranza dell'aggancio in cm: circa 10 dp sullo schermo, qualunque sia lo zoom. */
    private fun snapTolerance(): Double {
        val dp = if (fitMarginPx > 0f) fitMarginPx / 24f else 2.5f
        return (10f * dp / _state.value.camera.scale).toDouble().coerceIn(2.0, 60.0)
    }

    /**
     * Muro della stanza più vicino al punto `p` su cui ci sta un oggetto largo `width`, e la posizione
     * (distanza del centro dall'inizio del muro) più vicina a `p`. Null se nessun muro è abbastanza lungo.
     */
    private fun alongPerimeter(room: Room, p: Vec2, width: Double) = Openings.alongPerimeter(room, p, width)

    fun endDrag() {
        val start = dragStartPlan ?: return
        dragStartPlan = null
        val moved = _state.value.plan != start
        if (!moved) history.discardLast()
        snapGuides = emptyList()
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo, rotatingRulerId = null, dragging = false, snapGuides = emptyList()) }
        if (moved) when (dragTarget) {
            is DragTarget.Wall -> tutorialEvent(TutorialEvent.WallDragged)
            is DragTarget.Corner -> tutorialEvent(TutorialEvent.CornerDragged)
            is DragTarget.Opening -> tutorialEvent(TutorialEvent.OpeningDragged)
            is DragTarget.RoomLabel -> tutorialEvent(TutorialEvent.RoomMoved)
            else -> Unit
        }
        dragTarget = null
    }

    // ---------- Campi numerici ----------

    /**
     * Imposta la lunghezza del muro misurata sul lato interno. La differenza tra mezzeria e lato
     * interno dipende solo dagli angoli alle due estremità, che [Snapping.setWallLength] non cambia:
     * basta aggiungerla al valore richiesto per ottenere la lunghezza in mezzeria.
     */
    fun setWallLength(roomId: Long, index: Int, interiorLength: Double) {
        if (interiorLength <= 0) return
        val before = _state.value.plan
        commit { plan ->
            val room = plan.room(roomId) ?: return@commit plan
            val current = room.interiorLengths()[index]
            val center = interiorLength + (room.wallLength(index) - current)
            if (center <= 0) return@commit plan
            plan.replace(room.copy(points = Snapping.setWallLength(room.points, index, center)))
        }
        val room = _state.value.plan.room(roomId)
        if (_state.value.plan != before && room != null) tutorialEvent(TutorialEvent.WallLength(roomId, index, room.wallCount))
    }

    /**
     * Altezza personalizzata del muro; `height == null` ripristina l'altezza soffitto. Un muro in comune
     * con un'altra stanza è lo stesso muro fisico: la modifica vale per entrambe (in un solo passo di
     * cronologia). Ogni stanza torna alla propria altezza soffitto quando si ripristina lo standard.
     */
    fun setWallHeight(roomId: Long, index: Int, height: Double?) =
        commit { plan -> Dimensions.withWallHeight(plan, roomId, index, height) }

    // ---------- Spessore dei muri e forma libera ----------

    /** Spessore di tutti i muri della stanza (quelli con uno spessore proprio restano come sono). */
    fun setRoomWallThickness(roomId: Long, thickness: Double) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { thickness in 3.0..120.0 } ?: return@commit plan
        plan.replace(room.copy(wallThickness = thickness))
    }

    /**
     * Spessore di un solo muro (null = come gli altri della stanza). Un muro in comune con un'altra stanza
     * è lo stesso muro fisico: cambia anche per lei.
     */
    fun setWallThickness(roomId: Long, index: Int, thickness: Double?) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
        if (thickness != null && thickness !in 3.0..120.0) return@commit plan
        fun apply(r: Room, i: Int) = r.copy(wallThicknesses = if (thickness == null || thickness == r.wallThickness) r.wallThicknesses - i else r.wallThicknesses + (i to thickness))
        var result = plan.replace(apply(room, index))
        val a = room.wallStart(index)
        val b = room.wallEnd(index)
        for (other in plan.rooms) {
            if (other.id == roomId) continue
            for (j in 0 until other.wallCount) {
                if (Dimensions.sharesWall(a, b, other.wallStart(j), other.wallEnd(j))) result = result.replace(apply(result.room(other.id)!!, j))
            }
        }
        result
    }

    /**
     * Elimina il muro (o lo rimette, con `removed = false`): la stanza resta, ma da quel lato è aperta. Porte,
     * finestre e impianti su quel muro spariscono con lui. Un muro in comune con un'altra stanza è lo stesso
     * muro fisico: si toglie (o si rimette) anche per lei, altrimenti resterebbe in piedi il suo.
     */
    fun setWallRemoved(roomId: Long, index: Int, removed: Boolean) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
        fun apply(r: Room, i: Int) = if (removed) r.copy(
            removedWalls = r.removedWalls + i,
            openings = r.openings.filter { it.wallIndex != i },
            fixtures = r.fixtures.filter { it.kind.mount != Mount.Wall || it.wallIndex != i },
        ) else r.copy(removedWalls = r.removedWalls - i)
        var result = plan.replace(apply(room, index))
        val a = room.wallStart(index)
        val b = room.wallEnd(index)
        for (other in plan.rooms) {
            if (other.id == roomId) continue
            for (j in 0 until other.wallCount) {
                if (Dimensions.sharesWall(a, b, other.wallStart(j), other.wallEnd(j))) result = result.replace(apply(result.room(other.id)!!, j))
            }
        }
        result
    }

    // ---------- Stato di fatto / progetto ----------

    /** Vista: comparativa (gialli e rossi), stato di fatto o di progetto. */
    fun setPhaseView(v: PhaseView) = _state.update { it.copy(phaseView = v) }

    /** Dando uno stato a qualcosa si passa alla comparativa, dove si vede (nelle altre viste sparirebbe). */
    private fun showPhases(p: Phase) { if (p != Phase.Existing) _state.update { it.copy(phaseView = PhaseView.Compare) } }

    /** Stato del muro (anche del muro in comune con un'altra stanza, che è lo stesso muro fisico). */
    fun setWallPhase(roomId: Long, index: Int, phase: Phase) {
        commit { plan ->
            val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
            fun apply(r: Room, i: Int) = r.copy(wallPhases = if (phase == Phase.Existing) r.wallPhases - i else r.wallPhases + (i to phase))
            var result = plan.replace(apply(room, index))
            val a = room.wallStart(index)
            val b = room.wallEnd(index)
            for (other in plan.rooms) {
                if (other.id == roomId) continue
                for (j in 0 until other.wallCount) {
                    if (Dimensions.sharesWall(a, b, other.wallStart(j), other.wallEnd(j))) result = result.replace(apply(result.room(other.id)!!, j))
                }
            }
            result
        }
        showPhases(phase)
    }

    /** Tutti i muri della stanza insieme (es. una stanza nuova, o da demolire tutta). */
    fun setRoomPhase(roomId: Long, phase: Phase) {
        val room = _state.value.plan.room(roomId) ?: return
        commit { plan ->
            var result = plan
            for (i in 0 until room.wallCount) {
                val r = result.room(roomId) ?: break
                result = result.replace(r.copy(wallPhases = if (phase == Phase.Existing) r.wallPhases - i else r.wallPhases + (i to phase)))
            }
            result
        }
        showPhases(phase)
    }

    fun setOpeningPhase(roomId: Long, openingId: Long, phase: Phase) {
        val o = _state.value.plan.room(roomId)?.opening(openingId) ?: return
        updateOpening(roomId, o.copy(phase = phase))
        showPhases(phase)
    }

    fun setFreeWallPhase(id: Long, phase: Phase) {
        val w = _state.value.plan.freeWall(id) ?: return
        updateFreeWall(w.copy(phase = phase))
        showPhases(phase)
    }

    /**
     * Tasto Canc (computer): elimina ciò che è selezionato. Un muro diventa un lato aperto; la stanza intera si
     * elimina con la sua conferma. Restituisce false se non c'era niente da eliminare.
     */
    fun deleteSelected(): Boolean {
        val s = _state.value
        if (s.multi.isNotEmpty()) { groupDelete(); return true }
        when (val sel = s.selection) {
            is Selection.Wall -> if (s.plan.room(sel.roomId)?.isRemoved(sel.index) == false) setWallRemoved(sel.roomId, sel.index, removed = true) else return false
            is Selection.Opening -> removeOpening(sel.roomId, sel.openingId)
            is Selection.Fixture -> removeFixture(sel.roomId, sel.fixtureId)
            is Selection.Furniture -> removeFurniture(sel.furnitureId)
            is Selection.Column -> removeColumn(sel.columnId)
            is Selection.Beam -> removeBeam(sel.beamId)
            is Selection.FreeWall -> removeFreeWall(sel.wallId)
            is Selection.Stair -> removeStair(sel.stairId)
            is Selection.Dimension -> removeDimension(sel.dimensionId)
            is Selection.Annotation -> removeAnnotation(sel.annotationId)
            is Selection.StairWell -> return false
            null -> s.focusedRoomId?.let { if (s.plan.rooms.size > 1) requestDeleteRoom(it) else return false } ?: return false
        }
        return true
    }

    /** Aggiunge un angolo a metà del muro: il muro diventa due, da spostare liberamente (forme libere). */
    fun splitWall(roomId: Long, index: Int) {
        commit { plan ->
            val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
            plan.replace(room.splitWall(index))
        }
        _state.update { it.copy(selection = Selection.Wall(roomId, index), focusedRoomId = roomId) }
    }

    /** Toglie l'angolo alla fine del muro selezionato: il muro si unisce al successivo. */
    fun mergeWithNext(roomId: Long, index: Int) {
        commit { plan ->
            val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
            room.removeCorner((index + 1) % room.wallCount)?.let(plan::replace) ?: plan
        }
        _state.update { it.copy(selection = null, focusedRoomId = roomId) }
    }

    // ---------- Tendina caratteristiche stanza ----------

    /** Seleziona la stanza (es. dalla scheda in alto): compare la striscia "Info", la tendina resta chiusa. */
    fun openRoomProperties(id: Long) =
        collapsingInfoOnNewSelection { _state.update { it.copy(focusedRoomId = id, selection = null) } }

    /** Seleziona la stanza e ne apre subito la tendina (menu ⋮ "Proprietà di …"). */
    fun showRoomInfo(id: Long) = _state.update { it.copy(focusedRoomId = id, selection = null, infoExpanded = true) }

    /** Chiudere le caratteristiche della stanza la deseleziona: la pianta torna al centro. */
    fun closeRoomProperties() = clearFocus()

    fun renameRoom(id: Long, name: String) = commit { plan ->
        val room = plan.room(id) ?: return@commit plan
        plan.replace(room.copy(name = name.trim().ifBlank { room.name }))
    }

    /** Cambiare tipo rinomina automaticamente la stanza, es. "Cucina 2" (§3). */
    fun changeRoomType(id: Long, type: RoomType) {
        val before = _state.value.plan
        commit { plan ->
            val room = plan.room(id)?.takeIf { it.type != type } ?: return@commit plan
            plan.replace(room.copy(type = type, name = RoomFactory.autoName(plan, type, excludingRoomId = id)))
        }
        if (_state.value.plan != before) tutorialEvent(TutorialEvent.RoomTypeChanged)
    }

    /** Colore di una sola parete (lato interno); null = torna al colore delle pareti della stanza. */
    fun setWallPaint(roomId: Long, index: Int, paint: WallPaint?) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
        plan.replace(
            room.copy(
                wallPaints = if (paint == null || (paint == room.wallPaint && room.wallFinish == null)) room.wallPaints - index else room.wallPaints + (index to paint),
                wallFinishes = room.wallFinishes - index,
            ),
        )
    }

    /** Colore delle pareti esterne (facciata) del piano. */
    fun setFacade(paint: WallPaint) = commit { it.copy(facade = paint, facadeFinish = null) }

    /** Pavimento e colore delle pareti della stanza (null = lascia com'è). */
    fun setRoomLook(id: Long, floor: FloorFinish? = null, paint: WallPaint? = null) = commit { plan ->
        val room = plan.room(id) ?: return@commit plan
        plan.replace(
            room.copy(
                floorFinish = floor ?: room.floorFinish,
                wallPaint = paint ?: room.wallPaint,
                wallFinish = if (paint != null) null else room.wallFinish,
            ),
        )
    }

    // ---------- Versione pro: colori liberi e rivestimenti ----------

    /** Finitura (colore libero e rivestimento) delle pareti della stanza. */
    fun setRoomFinish(id: Long, finish: WallFinish) = commit { plan ->
        val room = plan.room(id) ?: return@commit plan
        plan.replace(room.copy(wallFinish = finish))
    }

    /** Finitura di una sola parete; null = torna a quella della stanza. */
    fun setWallFinish(roomId: Long, index: Int, finish: WallFinish?) = commit { plan ->
        val room = plan.room(roomId)?.takeIf { index < it.wallCount } ?: return@commit plan
        plan.replace(
            room.copy(
                wallFinishes = if (finish == null || finish == room.roomFinish) room.wallFinishes - index else room.wallFinishes + (index to finish),
                wallPaints = room.wallPaints - index,
            ),
        )
    }

    /** Pavimento con un materiale fotografico (null = la finitura normale). */
    fun setFloorMaterial(id: Long, material: String?) = commit { plan ->
        val room = plan.room(id) ?: return@commit plan
        plan.replace(room.copy(floorMaterial = material))
    }

    // ---------- Pianta di sfondo ----------

    /** Nuova immagine di sfondo (o null per toglierla). */
    fun setUnderlay(u: Underlay?) {
        commit { it.copy(underlay = u) }
        if (u == null) _state.update { it.copy(underlayTool = null) }
    }

    fun setUnderlayOpacity(opacity: Double) = commit { p -> p.underlay?.let { p.copy(underlay = it.copy(opacity = opacity.coerceIn(0.05, 1.0))) } ?: p }

    fun setUnderlayVisible(visible: Boolean) = commit { p -> p.underlay?.let { p.copy(underlay = it.copy(visible = visible)) } ?: p }

    /** Sceglie lo strumento (spostare, tarare) o lo chiude (null). */
    fun setUnderlayTool(tool: UnderlayTool?) = _state.update {
        it.copy(underlayTool = tool, selection = null, focusedRoomId = null, pendingOpening = null, pendingFixture = null, measure = null, view3d = false, infoExpanded = false)
    }

    private var underlayMoveStart: Underlay? = null

    fun beginUnderlayMove() {
        underlayMoveStart = _state.value.plan.underlay ?: return
        history.record(_state.value.fullBuilding)
    }

    fun moveUnderlay(totalDelta: Vec2) {
        val start = underlayMoveStart ?: return
        setPlan(_state.value.plan.copy(underlay = start.copy(origin = start.origin + totalDelta)))
    }

    fun endUnderlayMove() {
        val start = underlayMoveStart ?: return
        underlayMoveStart = null
        if (_state.value.plan.underlay == start) history.discardLast()
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    /** Taratura: tocco di uno dei due estremi della misura nota (il terzo tocco ricomincia). */
    fun underlayTap(at: Vec2) = _state.update {
        val t = it.underlayTool as? UnderlayTool.Calibrate ?: return@update it
        it.copy(underlayTool = if (t.first == null || t.second != null) UnderlayTool.Calibrate(at) else t.copy(second = at))
    }

    /**
     * Fine taratura: i due punti toccati distano davvero `realCm`. L'immagine si ingrandisce o rimpicciolisce
     * attorno al primo punto, che resta dov'è.
     */
    fun calibrateUnderlay(realCm: Double) {
        val t = _state.value.underlayTool as? UnderlayTool.Calibrate ?: return
        val a = t.first ?: return
        val b = t.second ?: return
        val measured = a.distanceTo(b)
        if (measured < 1e-6 || realCm <= 0) return
        val f = realCm / measured
        commit { p ->
            val u = p.underlay ?: return@commit p
            p.copy(underlay = u.copy(cmPerPx = u.cmPerPx * f, origin = a + (u.origin - a) * f))
        }
        _state.update { it.copy(underlayTool = null) }
    }

    /** Finitura della facciata del piano. */
    fun setFacadeFinish(finish: WallFinish) = commit { it.copy(facadeFinish = finish) }

    /** Finitura di un muro singolo (i due lati). */
    fun setFreeWallFinish(id: Long, finish: WallFinish) = commit { plan ->
        val w = plan.freeWall(id) ?: return@commit plan
        plan.replace(w.copy(finish = finish))
    }

    fun setCeilingHeight(id: Long, height: Double) = commit { plan ->
        val room = plan.room(id) ?: return@commit plan
        plan.replace(room.copy(ceilingHeight = height))
    }

    /** Una stanza si può eliminare solo se ne resta almeno un'altra (§1: deve sempre esistere una stanza). */
    val canDeleteRoom: Boolean get() = _state.value.plan.rooms.size > 1

    /** Chiede conferma prima di eliminare (resta comunque annullabile). */
    fun requestDeleteRoom(id: Long) {
        if (!canDeleteRoom || _state.value.plan.room(id) == null) return
        _state.update { it.copy(confirmDeleteRoomId = id) }
    }

    fun cancelDeleteRoom() = _state.update { it.copy(confirmDeleteRoomId = null) }

    fun deleteRoom(id: Long) {
        _state.update { it.copy(confirmDeleteRoomId = null) }
        if (!canDeleteRoom) return
        commit { it.copy(rooms = it.rooms.filter { r -> r.id != id }) }
        _state.update { it.copy(focusedRoomId = null, selection = null, pendingOpening = null) }
    }

    // ---------- Eliminazione muro in comune: un unico ambiente ----------

    /** Chiede conferma prima di eliminare il muro in comune (resta comunque annullabile). */
    fun requestMergeWall(req: MergeRequest) {
        if (RoomMerge.merge(_state.value.plan, req.roomId, req.index, req.otherRoomId) == null) return
        _state.update { it.copy(confirmMergeWall = req) }
    }

    fun cancelMergeWall() = _state.update { it.copy(confirmMergeWall = null) }

    /** Elimina il muro: la stanza resta (con nome e tipo), quella confinante vi confluisce. */
    fun mergeWall(req: MergeRequest) {
        _state.update { it.copy(confirmMergeWall = null) }
        val result = RoomMerge.merge(_state.value.plan, req.roomId, req.index, req.otherRoomId) ?: return
        commit { result.plan }
        openRoomProperties(req.roomId)
    }

    // ---------- Vista (§11) ----------

    /** `fitMarginPx`: margine attorno alla pianta quando la si adatta alla vista. */
    fun setViewport(w: Float, h: Float, fitMarginPx: Float) {
        this.fitMarginPx = fitMarginPx
        val first = viewportW == 0f
        // Rotazione dello schermo: lo zoom adatto all'altro orientamento non va più bene, si riadatta.
        val rotated = !first && (w > h) != (viewportW > viewportH)
        viewportW = w
        viewportH = h
        // Finché l'utente non ha zoomato o spostato la vista a mano, la pianta resta adattata all'area
        // anche quando questa cambia (barre di sistema che compaiono/spariscono, tastiera, rotazione).
        if (first || rotated || !userAdjustedView) fitToView()
    }

    fun pan(dx: Float, dy: Float) {
        userAdjustedView = true
        _state.update {
            it.copy(camera = it.camera.copy(offsetX = it.camera.offsetX + dx, offsetY = it.camera.offsetY + dy))
        }
    }

    /** Zoom attorno a un punto dello schermo (dito, cursore o centro). */
    fun zoomBy(factor: Float, pivotX: Float = viewportW / 2, pivotY: Float = viewportH / 2) = _state.update {
        userAdjustedView = true
        val c = it.camera
        val newScale = (c.scale * factor).coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
        val k = newScale / c.scale
        it.copy(
            camera = Camera(
                scale = newScale,
                offsetX = pivotX - (pivotX - c.offsetX) * k,
                offsetY = pivotY - (pivotY - c.offsetY) * k,
            ),
        )
    }

    /** Parte alta della pianta coperta dalle schede (tutorial, suggerimenti) e parte bassa coperta dalla striscia Info. */
    private var coveredTop = 0f
    private var coveredBottom = 0f

    /**
     * Aggiornata dall'interfaccia. Quando compare o sparisce la scheda del tutorial, la vista adattata
     * automaticamente si riadatta allo spazio che resta, così la pianta non finisce sotto la scheda.
     */
    fun setCoveredEdges(topPx: Float, bottomPx: Float) {
        val topChanged = topPx != coveredTop
        coveredTop = topPx
        coveredBottom = bottomPx
        if (topChanged && !userAdjustedView) fitToView()
    }

    /** Ciò che la vista deve inquadrare: la pianta del piano, oppure (piano ancora vuoto) quella di sotto. */
    private fun viewBounds(s: EditorUiState) =
        Openings.planBounds(s.plan, s.levelHeight) ?: s.planBelow?.let { Openings.planBounds(it, s.levelHeightBelow) }

    fun fitToView() {
        if (viewportW <= 0f || viewportH <= 0f) return
        userAdjustedView = false
        val b = viewBounds(_state.value) ?: return
        // Spazio libero: tolte le schede in alto e la striscia in basso (se lasciano abbastanza posto).
        val top = coveredTop.takeIf { viewportH - it - coveredBottom > viewportH / 3 } ?: 0f
        val bottom = coveredBottom.takeIf { viewportH - top - it > viewportH / 3 } ?: 0f
        val h = viewportH - top - bottom
        // Margine fisso (quanto basta per etichette e campi sui muri), non in percentuale: la pianta
        // usa quasi tutto lo spazio, importante soprattutto in orizzontale dove l'altezza è poca.
        val m = fitMarginPx.coerceAtMost(min(viewportW, h) / 4)
        val scale = min(
            (viewportW - 2 * m) / b.width.coerceAtLeast(1.0).toFloat(),
            (h - 2 * m) / b.height.coerceAtLeast(1.0).toFloat(),
        ).coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
        val c = b.center
        _state.update {
            it.copy(camera = Camera(scale, viewportW / 2 - (c.x * scale).toFloat(), top + h / 2 - (c.y * scale).toFloat()))
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 400L
        /** Funzioni già spiegate dal tutorial: finito il tutorial, i loro suggerimenti non servono più. */
        val TUTORIAL_TIPS = listOf(Tip.Welcome, Tip.Room, Tip.Wall, Tip.NewRoom, Tip.Opening, Tip.Fixture, Tip.Ruler)
    }
}
