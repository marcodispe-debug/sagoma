package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiSfondo

object TestiSfondoDe : TestiSfondo() {
    override val impossibileLeggereFile: String get() = "Die Datei kann nicht gelesen werden"
    override val nonSpazioSalvareSfondo: String get() = "Nicht genug Speicherplatz für den Hintergrund: versuche es mit einem kleineren Bild"
    override val ricalcaPiantina: String get() = "Grundriss nachzeichnen"
    override val piantaSfondo: String get() = "Grundriss im Hintergrund"
    override fun guidaRicalcoPasso(number: Any?, total: Any?): String = "Anleitung zum Nachzeichnen · Schritt $number von $total"
    override val caricaPiantinaHaiFoto: String get() = "Lade den Grundriss, den du hast (Foto, Scan oder PDF aus dem Kataster oder vom Projekt)."
    override val caricaPiantinaHaiImmagine: String get() = "Lade deinen Grundriss als Bild (Foto, Scan, JPG oder PNG). Bei einem PDF speichere die Seite als Bild."
    override val tocchiDueEstremiMisura: String get() =
        "Dann tippst du auf die beiden Enden eines Maßes, das du kennst, und gibst seine Länge ein: der Grundriss kommt in den richtigen Maßstab. " +
            "Zum Schluss wählst du, wie du die Räume auf dem Grundriss zeichnest: mit einer Form oder mit „Wand für Wand“, Ecke für Ecke."
    override val mettiSottoDisegnoPianta: String get() =
        "Lege einen vorhandenen Grundriss unter die Zeichnung (Foto, Scan oder PDF aus dem Kataster oder vom Projekt), " +
            "kalibriere ihn mit einem bekannten Maß und zeichne die Räume darauf nach."
    override val fotoImmagine: String get() = "Foto / Bild"
    override val altraImmagine: String get() = "Anderes Bild"
    override val pdf: String get() = "PDF"
    override val caricamento: String get() = "Wird geladen…"
    override val visibile: String get() = "Sichtbar"
    override fun trasparenza(p1: Any?): String = "Transparenz: $p1 %"
    override fun scalaAttuale1Px(p1: Any?, p2: Any?, p3: Any?): String = "Aktueller Maßstab: 1 px = $p1 cm · Bild $p2 × $p3 m"
    override val tara: String get() = "📏 Kalibrieren"
    override val sposta: String get() = "✋ Verschieben"
    override val togliSfondo: String get() = "Hintergrund entfernen"
    override val trascinaDitoSpostarePianta: String get() = "Mit einem Finger ziehen, um den Hintergrund-Grundriss unter der Zeichnung zu verschieben."
    override val taraturaToccaInizioMisura: String get() = "Kalibrieren: tippe auf den Anfang eines Maßes, das du kennst (z. B. eine bemaßte Wand)."
    override val oraToccaFineMisura: String get() = "Tippe jetzt auf das Ende des Maßes. Vergrößere mit zwei Fingern, um genau zu sein."
    override val scriviMisuraRealeB: String get() = "Gib das echte Maß zwischen A und B ein."
    override val misuraReale: String get() = "Echtes Maß"
    override val quantoLungaDavveroDistanza: String get() = "Wie lang ist der Abstand zwischen A und B wirklich?"
    override val piuMisuraLungaPiu: String get() = "Je länger das Maß, desto genauer die Kalibrierung."
    override val tara2: String get() = "Kalibrieren"
    override val rifai: String get() = "Wiederholen"
    override val leggiDaSola: String get() = "Grundriss automatisch lesen"
    override val leggiDaSolaSpiega: String get() = "Neu, kostenlos: lade den Grundriss und Sagoma zeichnet Räume, Türen und Fenster selbst. Danach prüfst und korrigierst du."
    override val letturaInCorso: String get() = "Ich lese den Grundriss… das kann eine Minute dauern."
    override fun piantinaLetta(stanze: Int): String = "Grundriss gelesen: $stanze Räume. Prüfe die Maße und korrigiere, wo nötig."
    override val senzaMisureScritte: String get() = "Auf dem Grundriss habe ich keine eingetragenen Maße gefunden: der Maßstab ist nur ungefähr, prüfe ihn."
    override val letturaNonRiuscita: String get() = "Ich konnte den Grundriss nicht lesen: zeichne ihn von Hand nach."
    override val letturaNonRaggiungibile: String get() = "Der Lesedienst antwortet nicht (Internet nötig): zeichne den Grundriss von Hand nach."
    override val troppeLetture: String get() = "Du hast heute schon viele Grundrisse lesen lassen: versuche es morgen wieder oder zeichne von Hand nach."
    override val nonUnaPiantina: String get() = "Das Bild scheint kein Grundriss zu sein: zeichne von Hand nach oder versuche ein anderes Bild."
    override val dxf: String get() = "📐 DXF (CAD)"
    override val dxfDwg: String get() = "DWG-Dateien lassen sich nicht öffnen: speichere den Grundriss im CAD-Programm als DXF."
    override val dxfBinario: String get() = "Diese DXF-Datei ist binär: speichere sie im CAD-Programm als Text-DXF (ASCII)."
    override val dxfVuoto: String get() = "In der DXF-Datei gibt es nichts zu zeichnen (oder alle Layer sind ausgeschaltet)."
    override fun dxfInScala(larghezza: String, altezza: String): String = "DXF-Grundriss bereits maßstäblich geladen ($larghezza × $altezza m): keine Kalibrierung nötig."
    override fun dxfUnita(cmPerUnita: Double): String =
        "Die Datei nennt keine Einheit: ich habe sie in " + when (cmPerUnita) { 100.0 -> "Metern"; 0.1 -> "Millimetern"; else -> "Zentimetern" } +
            " gelesen. Wenn die Maße nicht stimmen, nutze „Kalibrieren“."
    override val dxfSpiega: String get() = "DXF aus AutoCAD oder einem anderen CAD-Programm (zum Beispiel der Grundriss vom Architekten): bereits maßstäblich, ohne Kalibrierung."
    override val dxfParziale: String get() = "Sehr große Zeichnung: ich habe nur einen Teil der Linien geladen."
}
