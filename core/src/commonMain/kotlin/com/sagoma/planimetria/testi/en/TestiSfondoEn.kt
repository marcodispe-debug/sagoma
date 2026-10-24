package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiSfondo

object TestiSfondoEn : TestiSfondo() {
    override val impossibileLeggereFile: String get() = "Could not read the file"
    override val nonSpazioSalvareSfondo: String get() = "There is no room to save the background: try a smaller image"
    override val ricalcaPiantina: String get() = "Trace a floor plan"
    override val piantaSfondo: String get() = "Background plan"
    override fun guidaRicalcoPasso(number: Any?, total: Any?): String = "Tracing guide · step $number of $total"
    override val caricaPiantinaHaiFoto: String get() = "Load the floor plan you have (photo, scan or PDF from the land registry or the project)."
    override val caricaPiantinaHaiImmagine: String get() = "Load the floor plan you have as an image (photo, scan, JPG or PNG). For a PDF, save its page as an image."
    override val tocchiDueEstremiMisura: String get() =
        "Then tap the two ends of a measurement you know and type how long it is: the plan is scaled. " +
            "Finally trace over the walls with \"Wall by wall\", corner after corner."
    override val mettiSottoDisegnoPianta: String get() =
        "Put an existing plan under the drawing (photo, scan or PDF from the land registry or the project), " +
            "calibrate it on a measurement you know and trace the rooms over it."
    override val fotoImmagine: String get() = "Photo / image"
    override val altraImmagine: String get() = "Another image"
    override val pdf: String get() = "PDF"
    override val caricamento: String get() = "Loading…"
    override val visibile: String get() = "Visible"
    override fun trasparenza(p1: Any?): String = "Transparency: $p1%"
    override fun scalaAttuale1Px(p1: Any?, p2: Any?, p3: Any?): String = "Current scale: 1 px = $p1 cm · image $p2 × $p3 m"
    override val tara: String get() = "📏 Calibrate"
    override val sposta: String get() = "✋ Move"
    override val togliSfondo: String get() = "Remove the background"
    override val trascinaDitoSpostarePianta: String get() = "Drag with one finger to move the background plan under the drawing."
    override val taraturaToccaInizioMisura: String get() = "Calibration: tap the start of a measurement you know (e.g. a dimensioned wall)."
    override val oraToccaFineMisura: String get() = "Now tap the end of the measurement. Zoom in with two fingers to be precise."
    override val scriviMisuraRealeB: String get() = "Type the real length between A and B."
    override val misuraReale: String get() = "Real length"
    override val quantoLungaDavveroDistanza: String get() = "How long is the distance between A and B really?"
    override val piuMisuraLungaPiu: String get() = "The longer the measurement, the more precise the calibration."
    override val tara2: String get() = "Calibrate"
    override val rifai: String get() = "Redo"
    override val leggiDaSola: String get() = "Read the plan for me"
    override val leggiDaSolaSpiega: String get() = "New, free: load the plan and Sagoma draws the rooms, doors and windows for you. Then check and fix them."
    override val letturaInCorso: String get() = "Reading the plan… it may take a minute."
    override fun piantinaLetta(stanze: Int): String = "Plan read: $stanze rooms. Check the measurements and fix them where needed."
    override val senzaMisureScritte: String get() = "No written measurements found on the plan: the scale is only approximate, check it."
    override val letturaNonRiuscita: String get() = "Couldn't read the plan: trace it by hand."
    override val letturaNonRaggiungibile: String get() = "The reading service is not responding (internet needed): trace the plan by hand."
    override val troppeLetture: String get() = "You have already read many plans today: try again tomorrow or trace it by hand."
    override val nonUnaPiantina: String get() = "The image doesn't look like a floor plan: trace it by hand or try another image."
}
