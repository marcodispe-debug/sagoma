package com.sagoma.planimetria.model

import kotlinx.serialization.Serializable

/**
 * Oggetto 3D FORNITO da un professionista per questo progetto: per esempio la cucina che il negozio ha progettato con il suo
 * programma (Winner esporta un IFC con i soli mobili). Sta nel piano ([FloorPlan.supplied]) e un arredo lo mette in casa con il
 * modello "fornito:<id>": si sposta, si gira e si specchia come gli altri arredi.
 *
 * Non entra mai nel catalogo e non esce mai da sagoma: nessuna esportazione (IFC, immagini a parte) ne contiene la forma.
 *
 * La forma è in coordinate locali, in mm, come i mobili generati ([com.sagoma.planimetria.geometry.KitchenGeometry]):
 * x lungo la larghezza (dal centro), y in alto dalla base, z dal retro (−depth/2) al davanti (+depth/2).
 */
@Serializable
data class SuppliedObject(
    val id: Long,
    val name: String,
    /** Chi l'ha fornito (il negozio, lo studio). */
    val supplier: String = "",
    /** Misure vere (cm): l'arredo che lo usa parte da queste. */
    val width: Double,
    val depth: Double,
    val height: Double,
    /** Triangoli, raggruppati per colore. */
    val parts: List<SuppliedPart> = emptyList(),
    /** I mobili che lo compongono, per il disegno in pianta. */
    val elements: List<SuppliedElement> = emptyList(),
)

/** Triangoli di un colore: `vertices` x, y, z (mm) di seguito, `triangles` tre indici per triangolo. */
@Serializable
data class SuppliedPart(val argb: Long, val vertices: List<Int>, val triangles: List<Int>)

/** Ingombro di un mobile dell'oggetto (mm, coordinate locali): in pianta da x0,z0 a x1,z1, in altezza da y0 a y1. */
@Serializable
data class SuppliedElement(val name: String, val x0: Int, val z0: Int, val x1: Int, val z1: Int, val y0: Int, val y1: Int)

object SuppliedObjects {
    const val PREFIX = "fornito:"

    fun isModel(model: String): Boolean = model.startsWith(PREFIX)
    fun model(id: Long): String = "$PREFIX$id"
    fun idOf(model: String): Long? = if (isModel(model)) model.removePrefix(PREFIX).toLongOrNull() else null
}
