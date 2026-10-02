package com.sagoma.planimetria

/**
 * Versione in uso, impostata dall'app all'avvio: pro = colori e rivestimenti liberi, motore grafico avanzato,
 * libreria di arredi 3D e materiali fotografici.
 */
object Edition {
    var isPro: Boolean = false
    val label: String get() = if (isPro) "Sagoma Pro" else "Sagoma"
}
