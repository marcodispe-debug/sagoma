package com.sagoma.planimetria.testi

/** Scheda Foto della stanza. */
open class TestiFoto {
    open val impossibileLeggereFoto: String get() = "Impossibile leggere la foto"
    open val nonSpazioSalvare: String get() = "Non c'è spazio per salvare la foto"
    open val fotoSopralluogoRiguardareMentre: String get() =
        "Foto del sopralluogo da riguardare mentre disegni: dov'è la presa, da che parte apre la porta, il metro appoggiato al muro. " +
            "Restano nel progetto; non cambiano la pianta né il 3D."
    open val scatta: String get() = "📷 Scatta"
    open val galleria: String get() = "🖼 Dalla galleria"
    open val aggiungiFoto: String get() = "📷 Aggiungi foto"
    open val preparoFoto: String get() = "Preparo la foto…"
    open val fotoStanza: String get() = "Foto della stanza"
    open val toccaAggiungereNota: String get() = "Tocca per aggiungere una nota"
    open val aproFoto: String get() = "Apro la foto…"
    open val notaEsempioPresa30: String get() = "Nota (per esempio: presa a 30 cm da terra)"
    open val togliFoto: String get() = "Togli foto"
}
