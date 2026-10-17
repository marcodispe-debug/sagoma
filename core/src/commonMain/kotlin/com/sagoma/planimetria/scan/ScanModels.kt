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
 * Parete rilevata dalla scansione, nel sistema di Sagoma (centimetri): il tratto `a`→`b` è la parte vista, non per forza
 * tutta la parete (dietro un mobile si ferma prima dell'angolo). `headingDeg` è la direzione in pianta, da 0 a 180 gradi.
 * `bottomCm` e `topCm`: altezza da terra della parte rilevata (null se la quota del pavimento non era nota).
 */
data class ScannedWall(
    val a: Vec2,
    val b: Vec2,
    val lengthCm: Double,
    val headingDeg: Double,
    val bottomCm: Double? = null,
    val topCm: Double? = null,
)

/**
 * Esito di una scansione: la stanza e, se è stata ricostruita dalle pareti, le pareti viste. Porte, finestre (`ScannedOpening`) e
 * oggetti (`ScannedObject`) si aggiungeranno qui nelle prossime tappe, senza cambiare chi consuma il risultato.
 */
data class ScanResult(val room: ScannedRoom, val walls: List<ScannedWall> = emptyList())
