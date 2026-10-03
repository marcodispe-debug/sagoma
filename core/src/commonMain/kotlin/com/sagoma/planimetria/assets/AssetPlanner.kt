package com.sagoma.planimetria.assets

import com.sagoma.planimetria.assets.remote.AssetPriority
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog

/** Un asset da preparare (percorso logico, vedi [AssetPaths]) e quanto serve. */
data class AssetRequest(val path: String, val priority: AssetPriority)

/**
 * Calcola QUALI asset servono e con che [AssetPriority]: solo conti sui dati che già ci sono (scena, piani), nessun I/O, nessun
 * negozio, nessuna rete, nessuna dipendenza da chi poi li scarica o li disegna. Il risultato è deterministico, senza
 * duplicati: l'ordine è quello della prima volta in cui un percorso compare e, se lo stesso percorso arriva con priorità
 * diverse, resta la più alta (`Scene` > `Visible` > `Soon` > `Background`).
 *
 * I percorsi si ricavano dagli stessi dati che usano i renderer: i modelli da [Scene3D.furniture] e dai
 * [com.sagoma.planimetria.model.Furniture] dei piani, i materiali dalle chiavi di [Scene3D.textured] (la scena le crea solo per i
 * materiali della libreria), le miniature dei tappeti da [FurnitureCatalog.rugOf]. Non serve che i cataloghi siano già caricati.
 * Un nome vuoto o non valido come percorso non produce una richiesta (non si inventa nessun percorso).
 *
 * Non include le miniature del catalogo di arredi né dei materiali per scegliere (`Visible`, le gestisce la UI).
 */
object AssetPlanner {
    private val MAP_KINDS = listOf("color", "normal", "orm")

    /**
     * Gli asset che servono a disegnare `scene`, tutti `Scene`, nell'ordine in cui li legge il renderer:
     *  - il modello di ogni arredo ([AssetPaths.furnitureModel]);
     *  - per ogni materiale fotografico con almeno un triangolo, le tre mappe (`color`, `normal`, `orm`), in ordine alfabetico;
     *  - l'erba ([MaterialCatalog.GRASS], tre mappe) se la scena ha un'estensione ([Scene3D.bounds]): è quando i renderer stendono il prato;
     *  - le luci ambiente in `environments` ([AssetPaths.environment]). Quale serva (giorno, tramonto…) lo decide il renderer in base all'ora,
     *    quindi i nomi li passa chi chiama.
     */
    fun forScene(scene: Scene3D, environments: List<String> = emptyList()): List<AssetRequest> {
        val plan = Plan()
        for (f in scene.furniture) plan.model(f.model, AssetPriority.Scene)
        for (id in scene.textured.keys.sorted()) {
            // Come i renderer: una superficie con meno di un triangolo (8 float per vertice) non si disegna.
            if ((scene.textured[id]?.size ?: 0) / 8 >= 3) plan.materialMaps(id, AssetPriority.Scene)
        }
        if (scene.bounds != null) plan.materialMaps(MaterialCatalog.GRASS, AssetPriority.Scene)
        for (name in environments) plan.environment(name, AssetPriority.Scene)
        return plan.result()
    }

    /**
     * Gli asset da preparare in anticipo per un progetto: di ogni arredo di ogni piano il modello e la vista dall'alto
     * ([AssetPaths.furnitureTop]); per i tappeti di materiale, che non hanno file 3D, il campione del materiale (quello che la pianta
     * disegna al posto della vista dall'alto). Di default `Soon`.
     */
    fun forProject(building: Building, priority: AssetPriority = AssetPriority.Soon): List<AssetRequest> =
        forPlans(building.floors.map { it.plan }, priority)

    /** Come [forProject] per un elenco di piani. */
    fun forPlans(plans: List<FloorPlan>, priority: AssetPriority = AssetPriority.Soon): List<AssetRequest> {
        val plan = Plan()
        for (p in plans) for (f in p.furniture) {
            val rug = FurnitureCatalog.rugOf(f.model)
            if (rug != null) {
                plan.add(AssetPaths.materialThumbnail(rug.first), rug.first, priority)
            } else {
                plan.model(f.model, priority)
                plan.add(AssetPaths.furnitureTop(f.model), f.model, priority)
            }
        }
        return plan.result()
    }

    /** Unisce più elenchi (per esempio scena e progetto): ordine della prima comparsa, priorità più alta per ogni percorso. */
    fun combine(vararg groups: List<AssetRequest>): List<AssetRequest> {
        val plan = Plan()
        for (g in groups) for (r in g) plan.addPath(r.path, r.priority)
        return plan.result()
    }

    /** L'accumulatore: percorso → priorità più alta, nell'ordine della prima comparsa. */
    private class Plan {
        private val map = LinkedHashMap<String, AssetPriority>()

        fun addPath(path: String, priority: AssetPriority) {
            if (!isPortableAssetPath(path)) return
            val current = map[path]
            if (current == null || priority.ordinal < current.ordinal) map[path] = priority
        }

        /** `id` è il nome da cui nasce il percorso: deve essere un nome semplice (niente vuoto, niente cartelle). */
        fun add(path: String, id: String, priority: AssetPriority) {
            if (isPlainName(id)) addPath(path, priority)
        }

        fun model(model: String, priority: AssetPriority) = add(AssetPaths.furnitureModel(model), model, priority)

        fun environment(name: String, priority: AssetPriority) = add(AssetPaths.environment(name), name, priority)

        /** Le tre mappe di un materiale, tutte o nessuna (una sola non basta a disegnarlo). */
        fun materialMaps(id: String, priority: AssetPriority) {
            if (!isPlainName(id)) return
            val paths = MAP_KINDS.map { AssetPaths.materialMap(id, it) }
            if (paths.all { isPortableAssetPath(it) }) for (p in paths) addPath(p, priority)
        }

        fun result(): List<AssetRequest> = map.map { (p, prio) -> AssetRequest(p, prio) }

        private fun isPlainName(s: String) = s.isNotBlank() && s != "." && s != ".." && '/' !in s && '\\' !in s
    }
}
