package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiFoto

object TestiFotoDe : TestiFoto() {
    override val impossibileLeggereFoto: String get() = "Das Foto kann nicht gelesen werden"
    override val nonSpazioSalvare: String get() = "Nicht genug Speicherplatz für das Foto"
    override val fotoSopralluogoRiguardareMentre: String get() =
        "Fotos vom Ortstermin zum Nachsehen beim Zeichnen: wo die Steckdose ist, wohin die Tür aufgeht, der Zollstock an der Wand. " +
            "Sie bleiben im Projekt; Grundriss und 3D ändern sie nicht."
    override val scatta: String get() = "📷 Foto aufnehmen"
    override val galleria: String get() = "🖼 Aus der Galerie"
    override val aggiungiFoto: String get() = "📷 Foto hinzufügen"
    override val preparoFoto: String get() = "Foto wird vorbereitet…"
    override val fotoStanza: String get() = "Fotos des Raums"
    override val toccaAggiungereNota: String get() = "Tippen, um eine Notiz hinzuzufügen"
    override val aproFoto: String get() = "Foto wird geöffnet…"
    override val notaEsempioPresa30: String get() = "Notiz (zum Beispiel: Steckdose 30 cm über dem Boden)"
    override val togliFoto: String get() = "Foto entfernen"
    override val senzaPermessoFotocamera: String get() = "Ohne Kamerazugriff kannst du ein Foto aus der Galerie wählen"
    override val impossibileAprireFotocamera: String get() = "Die Kamera kann nicht geöffnet werden"
}
