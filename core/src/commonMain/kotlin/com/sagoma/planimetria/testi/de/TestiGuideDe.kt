package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Passo
import com.sagoma.planimetria.testi.TestiGuide

object TestiGuideDe : TestiGuide() {
    override val titoloMansarda: String get() = "Dachgeschoss"
    override val titoloRicalco: String get() = "Grundriss nachzeichnen"
    override val titoloStanze: String get() = "Räume und Maße"
    override val titoloZone: String get() = "Nicht begehbare Flächen"
    override val titoloMuri: String get() = "Wände und Ecken"
    override val titoloAgganci: String get() = "Einrasten"
    override val titoloPorte: String get() = "Türen und Fenster"
    override val titoloImpianti: String get() = "Installationen"
    override val titoloPiuStanze: String get() = "Mehrere Räume"
    override val titoloBalconi: String get() = "Balkone und Terrassen"
    override val titoloScale: String get() = "Treppen und Geschosse"
    override val titoloVista3D: String get() = "3D-Ansicht"
    override val titoloQuote: String get() = "Maße und Texte"

    override fun passoDi(numero: Int, totale: Int): String = "Schritt $numero von $totale"
    override val apriVista3D: String get() = "3D-Ansicht öffnen"

    override val mansardaIntro: Passo get() = Passo(
        "So ist ein Dachgeschoss aufgebaut",
        "In einem Dachgeschoss fällt die Decke zu einer niedrigeren Wand hin ab (dem „Kniestock“ unter der Dachfläche). " +
            "Die Seitenwände sind deshalb nicht rechteckig: sie sind bis zu einem gewissen Punkt hoch und fallen dann " +
            "schräg ab. In dieser App geht das in zwei Schritten: du machst den Kniestock niedriger und schneidest die Wände daneben schräg ab.",
    )

    override val mansardaStanza: Passo get() = Passo(
        "1 · Zeichne den Raum",
        "Erstelle den Raum mit „🧱 Bauelemente“ → Raum und passe die Maße im Info-Fenster an, wie bei jedem anderen Raum. " +
            "Die Raumhöhe ist die Höhe des höchsten Teils des Dachgeschosses.",
    )

    override val mansardaMuretto: Passo get() = Passo(
        "2 · Mach den Kniestock niedriger",
        "Tippe auf die Wand, zu der das Dach abfällt, öffne Info und gib ihre echte Höhe unter „Wandhöhe“ ein " +
            "(zum Beispiel 120 cm). Auf dem Grundriss erscheint die Höhe in Violett neben der Wand.",
    )

    override val mansardaTaglio: Passo get() = Passo(
        "3 · Schneide die erste Seitenwand ab",
        "Tippe auf eine der beiden Wände, die senkrecht zum Kniestock stehen, und schalte im Info-Fenster „Schräger Schnitt (Dachschräge)“ ein. " +
            "Ist der Kniestock niedriger, fällt die Wand jetzt bis zu seiner Höhe ab. Sind beide Nachbarwände niedrig, " +
            "wähle unter „Fällt ab nach“ die Seite.",
    )

    override val mansardaInizio: Passo get() = Passo(
        "4 · Wähle, wo die Schräge beginnt",
        "Miss im Haus, in welchem Abstand vom Kniestock die Decke schräg zu werden beginnt, und gib ihn unter „Schräge beginnt bei“ ein. " +
            "Oder ziehe den orangefarbenen Punkt auf dem Grundriss: die gestrichelte Linie markiert den Beginn der Schräge.",
    )

    override val mansardaRipeti: Passo get() = Passo(
        "5 · Wiederhole es an der anderen Wand",
        "Mach dasselbe an der gegenüberliegenden Wand, damit die Decke im ganzen Raum gleich abfällt. " +
            "Grenzt die Wand an einen anderen Raum, wird nur die Seite des Dachgeschosses abgeschnitten: der Nachbarraum bleibt ganz.",
    )

    override val mansardaControllo: Passo get() = Passo(
        "6 · Prüfe Türen, Fenster und Installationen",
        "Liegt ein Fenster, eine Tür, ein Heizkörper oder eine Steckdose höher als die schräge Wand, erscheint ein " +
            "roter Punkt mit „!“ auf dem Grundriss und ein Hinweis im Info-Fenster: setze das Objekt tiefer oder verschiebe es an der Wand " +
            "zum hohen Teil hin.",
    )

    override val mansarda3D: Passo get() = Passo(
        "7 · Sieh dir das Ergebnis in 3D an",
        "Öffne die 3D-Ansicht: Wandoberkanten und Decke fallen schräg ab, und du kannst durch das Dachgeschoss gehen, " +
            "um zu prüfen, ob alles stimmt. Das Raumvolumen im Info-Fenster berücksichtigt die schräge Decke bereits.",
    )

    override val caricaPiantina: Passo get() = Passo(
        "Grundriss laden",
        "Wähle in einem neuen Projekt „Grundriss nachzeichnen“; in einem begonnenen Projekt nutze ⋮ → „Grundriss im Hintergrund…“. " +
            "Dann „Foto / Bild“ (oder „PDF“ am Computer): Kataster, Bauplan oder ein gerades Foto.",
    )

    override val ricalcaAngoli: Passo get() = Passo(
        "Ecken nachzeichnen",
        "„Wand für Wand“ öffnet sich. " + Tip.WallDraw.text + " Für den nächsten Raum: „🧱 Bauelemente“ → Raum → „Wand für Wand“.",
    )

    override val controllaMisure: Passo get() = Passo(
        "Maße prüfen",
        "Öffne die Info des Raums und vergleiche m² und Wände mit den Angaben auf dem Grundriss. Zählt ein Teil nicht zu den m² " +
            "(niedrige Dachschräge, Schacht), füge eine nicht begehbare Fläche hinzu.",
    )

    override val muoversi: Passo get() = Passo(
        "Im Grundriss bewegen",
        Tip.Welcome.text,
    )

    override val aggiungiZona: Passo get() = Passo(
        "Eine Fläche hinzufügen",
        "Wähle den Raum aus, öffne Info und tippe im Reiter Maße auf „▧ Nicht begehbare Fläche hinzufügen“.",
    )

    override val agganci: Passo get() = Passo(
        "Einrasten ein- und ausschalten",
        "Die Taste 🧲 ist auf der Info-Leiste des Raums und im Fenster von „Wand für Wand“. " + Tip.Snap.text,
    )

    override val aggiungiBalcone: Passo get() = Passo(
        "Einen Balkon oder eine Terrasse hinzufügen",
        "Tippe auf „🧱 Bauelemente“ → Balkone und Terrassen und wähle den Typ, dann Form und Maße wie bei einem Raum. Er liegt schon am Haus an: " +
            "ziehe ihn am Namen an der Wand entlang an die richtige Stelle, er rastet von selbst ein. An den Außenseiten ist die Brüstung, " +
            "am Haus bleibt die Wand.",
    )

    override val parapetto: Passo get() = Passo(
        "Brüstung und Fenstertür",
        Tip.Outdoor.text,
    )

    override val aggiungiScala: Passo get() = Passo(
        "Eine Treppe hinzufügen",
        "Tippe auf „🧱 Bauelemente“ → Treppen und wähle den Typ: gerader Lauf, L-Form mit Podest, U-Form mit zwei Läufen oder Wendeltreppe. " +
            "Die Treppe erscheint in der Mitte der Ansicht (oder des ausgewählten Raums): ziehe sie an ihren Platz, nahe einer Wand legt sie sich von selbst an.",
    )

    override val opzioniScala: Passo get() = Passo(
        "Typ, Maße und Richtung",
        "Tippe auf die Treppe und öffne Info: du änderst Typ, Breite, Auftritt (oder Durchmesser der Wendeltreppe), Drehrichtung und Drehung " +
            "oder drehst sie mit ↻ um 90°. Anzahl und Höhe der Stufen ergeben sich aus der Geschosshöhe; sind die Stufen unbequem, erscheint ein Hinweis.",
    )

    override val aggiungiPiano: Passo get() = Passo(
        "Ein Geschoss hinzufügen",
        "Tippe oben links auf die Geschoss-Schaltfläche und wähle „Geschoss darüber hinzufügen“. Du kannst mit den Wänden des Geschosses darunter " +
            "oder mit einem leeren Geschoss beginnen. Das Geschoss darunter bleibt grau sichtbar und seine Treppen erscheinen als Öffnung im Boden.",
    )

    override val piani3D: Passo get() = Passo(
        "Die Geschosse in 3D",
        "In 3D siehst du das gewählte Geschoss über den unteren, verbunden durch die Treppen; höhere Geschosse werden ausgeblendet, als " +
            "würde man das Dach abnehmen. Im selben Geschoss-Menü änderst du Name und Geschosshöhe oder löschst es.",
    )

    override val comandi3D: Passo get() = Passo(
        "Alle Befehle",
        "Telefon: ein Finger dreht, zwei Finger zoomen und verschieben; eine ausgewählte Wand, Tür oder Installation lässt sich ziehen, um " +
            "sie zu verschieben. In 🚶 Gehen bewegst du dich mit dem Joystick unten links. Computer: ↑ ↓ (oder W S) vor und zurück, ← → zum " +
            "Drehen, A D zur Seite, Bild↑/Bild↓, um nach oben oder unten zu schauen, Umschalt, um schneller zu gehen; " +
            "Mausrad zum Zoomen, rechte Maustaste ziehen, um die Ansicht zu verschieben.",
    )

    override val quota: Passo get() = Passo(
        "Ein Maß hinzufügen",
        "Tippe auf „🛠 Werkzeuge“ → Maß und tippe auf die beiden Enden: die Punkte rasten an Ecken und Wandflächen ein. Das Maß bleibt " +
            "auf dem Grundriss und erscheint in den Plänen; tippe darauf, um es zu verschieben oder zu löschen.",
    )

    override val testo: Passo get() = Passo(
        "Einen Text schreiben",
        "Tippe auf „🛠 Werkzeuge“ → Text, tippe dorthin, wo er stehen soll, dann schreibe und wähle die Größe.",
    )
}
