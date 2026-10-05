package com.sagoma.planimetria.scan

import com.sagoma.planimetria.model.Vec2

/**
 * Stanza scansionata, nel sistema di Sagoma: angoli del perimetro **interno** (calpestabile), in centimetri, con gli assi della
 * pianta (x a destra, y verso il basso). L'orientamento rispetto al mondo AR è arbitrario e l'ordine degli angoli può essere
 * in un senso o nell'altro: [ScanGeometry.alignedInterior] la mette in forma per Sagoma.
 * Nessun tipo di ARCore o di Android: la scansione arriva già convertita da [ScanCoordinates].
 */
data class ScannedRoom(
    val corners: List<Vec2>,
    /** Altezza del soffitto (cm) se misurata; null = quella solita. */
    val ceilingHeight: Double? = null,
)

/**
 * Esito di una scansione. Per ora c'è solo la stanza; porte, finestre (`ScannedOpening`) e oggetti (`ScannedObject`) si
 * aggiungeranno qui nelle prossime tappe, senza cambiare chi consuma il risultato.
 */
data class ScanResult(val room: ScannedRoom)
