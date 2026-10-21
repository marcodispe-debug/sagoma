package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiFoto

object TestiFotoEn : TestiFoto() {
    override val impossibileLeggereFoto: String get() = "Could not read the photo"
    override val nonSpazioSalvare: String get() = "There is no room to save the photo"
    override val fotoSopralluogoRiguardareMentre: String get() =
        "Site photos to look back at while you draw: where the socket is, which way the door opens, the tape measure against the wall. " +
            "They stay in the project; they do not change the plan or the 3D."
    override val scatta: String get() = "📷 Take photo"
    override val galleria: String get() = "🖼 From gallery"
    override val aggiungiFoto: String get() = "📷 Add photo"
    override val preparoFoto: String get() = "Preparing the photo…"
    override val fotoStanza: String get() = "Room photo"
    override val toccaAggiungereNota: String get() = "Tap to add a note"
    override val aproFoto: String get() = "Opening the photo…"
    override val notaEsempioPresa30: String get() = "Note (for example: socket 30 cm from the floor)"
    override val togliFoto: String get() = "Remove photo"
    override val senzaPermessoFotocamera: String get() = "Without camera permission you can pick a photo from the gallery"
    override val impossibileAprireFotocamera: String get() = "Unable to open the camera"
}
