package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Suggerimenti

object SuggerimentiDe : Suggerimenti() {
    override fun titolo(v: Tip): String = when (v) {
        Tip.Welcome -> "Willkommen"
        Tip.Room -> "Ausgewählter Raum"
        Tip.Wall -> "Wand"
        Tip.NewRoom -> "Neuer Raum"
        Tip.Opening -> "Türen und Fenster"
        Tip.Fixture -> "Installationen"
        Tip.Ruler -> "Maßband"
        Tip.WallCut -> "Wand unter dem Dach"
        Tip.View3D -> "3D-Ansicht"
        Tip.Stair -> "Treppe"
        Tip.Outdoor -> "Balkon und Terrasse"
        Tip.Structure -> "Säulen und Balken"
        Tip.Measure -> "Abstand"
        Tip.FreeWall -> "Einzelwand"
        Tip.Floors -> "Geschosse"
        Tip.Tracing -> "Grundriss nachzeichnen"
        Tip.Calibrate -> "Kalibrieren"
        Tip.WallDraw -> "Wand für Wand"
        Tip.Underlay -> "Grundriss und Zeichnung"
        Tip.ExcludedZone -> "Nicht begehbare Fläche"
        Tip.RoomInfo -> "Info-Fenster"
        Tip.Snap -> "Einrasten"
    }

    override fun testo(v: Tip): String = when (v) {
        Tip.Welcome ->
            "Tippe auf einen Raum, um ihn auszuwählen. Mit zwei Fingern zoomst du, durch Ziehen auf dem Hintergrund verschiebst du den Grundriss. " +
                "Bei einem Fehler stellt ↶ Rückgängig oben alles wieder her."
        Tip.Room ->
            "Ziehe die blauen Punkte, um die Ecken zu verschieben, und die Wände, um den Raum zu verlängern; am Namen ziehst du den ganzen Raum. " +
                "Für Name, Typ und genaue Seitenmaße tippe auf Info oder wische die Leiste unten nach oben. " +
                "Wenn das Einrasten den Punkt dorthin zieht, wo du ihn nicht willst, schalte es mit 🧲 auf der Leiste aus."
        Tip.Wall ->
            "Ziehe sie, um sie zu verschieben: sie rastet an den Wänden der Nachbarräume ein. Unter Info änderst du die Höhe und, wenn sie " +
                "an einen anderen Raum grenzt, kannst du sie löschen, um beide Räume zu einem zu verbinden."
        Tip.NewRoom ->
            "Der neue Raum entsteht direkt am offenen Raum, mit gemeinsamer Wand. Soll er woanders hin, ziehe ihn am Namen: " +
                "er rastet an den Wänden der Nachbarräume ein."
        Tip.Opening -> "Ziehe sie an der Wand entlang, um sie zu verschieben. Unter Info änderst du Maße, Dreh- oder Schiebeflügel, Anschlagseite und Öffnungsrichtung oder entfernst sie."
        Tip.Fixture ->
            "Wandinstallationen gleiten an der Wand entlang, Lampen ziehst du an jede Stelle im Raum. " +
                "Unter Info stellst du Höhe ab Boden, Maße und Drehung ein oder entfernst sie."
        Tip.Ruler -> "Ziehe die Enden, um es zu verlängern, und die Mitte, um es zu verschieben. Mit dem runden Griff drehst du es (rastet alle 45° ein), mit × löschst du es."
        Tip.WallCut ->
            "Die Wand fällt schräg bis zur Höhe der niedrigen Wand ab. Ziehe den Punkt auf der Wand oder gib den Abstand ein, " +
                "um zu wählen, wo sie abzufallen beginnt. Die Schraffur zeigt, wo die Dachschräge beginnt; im 3D siehst du alles."
        Tip.View3D ->
            "Ziehe, um das Haus zu umkreisen; mit zwei Fingern (oder dem Mausrad) zoomst du. Tippe auf eine Wand oder ein Objekt, um es auszuwählen. " +
                "Mit 🚶 Gehen betrittst du die Räume. Alle Befehle findest du unter ❔ Anleitungen → 3D-Ansicht."
        Tip.Stair ->
            "Ziehe sie, um sie zu verschieben: nahe einer Wand legt sie sich von selbst an. Unter Info wählst du Typ, Breite, Auftritt und Drehrichtung " +
                "und drehst sie um 90°. Anzahl und Höhe der Stufen ergeben sich aus der Geschosshöhe; im Geschoss darüber erscheint das Treppenloch."
        Tip.Outdoor ->
            "Das ist ein Außenbereich: statt der Wände gibt es eine Brüstung, am Haus bleibt die Hauswand. Lege ihn an eine Wand " +
                "und setze eine Fenstertür (Bauelemente → Fenstertüren) in die Wand des Raums. Unter Info wählst du Geländer, Mäuerchen oder Glas und die Höhe."
        Tip.Structure ->
            "Ziehe eine Säule, um sie zu verschieben: nahe einer Wand legt sie sich an und wird zum vorstehenden Pfeiler. Einen Balken verschiebst du " +
                "durch Ziehen und verlängerst ihn an den Punkten an den Enden, die sich auf die Wände legen. Unter Info änderst du Form und Maße."
        Tip.Measure ->
            "Tippe auf das erste Objekt und dann auf das zweite: Wände, Türen und Fenster, Heizkörper, Steckdosen, Lampen, Säulen, Balken, Treppen. " +
                "Der Abstand zwischen ihren nächsten Kanten erscheint, mit Maß auf dem Grundriss; wenn du die Objekte verschiebst, wird er aktualisiert."
        Tip.FreeWall ->
            "Eine Trennwand, die keinen Raum schließt. Ziehe sie, um sie zu verschieben, und ziehe die Punkte an den Enden, um sie zu verlängern oder zu drehen: " +
                "die Enden rasten an den Flächen anderer Wände und an den Enden anderer Einzelwände ein, und die Wand richtet sich auf 90° aus. " +
                "Unter Info änderst du Stärke, Höhe und Farbe."
        Tip.Floors ->
            "Mit der Geschoss-Schaltfläche oben links wechselst du zwischen den Geschossen und fügst neue hinzu. Das Geschoss darunter erscheint " +
                "grau, damit du die Wände ausrichten kannst, und seine Treppen erscheinen als Öffnung im Boden. Im 3D siehst du das gewählte Geschoss über den unteren."
        Tip.Tracing ->
            "Lade Foto oder PDF des Grundrisses und kalibriere ihn mit einem bekannten Maß: dann wählst du, wie du die Räume zeichnest, " +
                "mit einer Form oder durch Anklicken der Ecken mit „Wand für Wand“, und die Maße ergeben sich von selbst, wie die auf dem Grundriss."
        Tip.Calibrate ->
            "Tippe auf die beiden Enden eines Maßes, das auf dem Grundriss steht (am besten das längste, zum Beispiel eine ganze Wand), und gib " +
                "ein, wie viele cm es sind. Wenn die Maße danach nicht stimmen, korrigiert „📏 Neu kalibrieren“ das auch mitten im Raum."
        Tip.WallDraw ->
            "Klicke die Ecken nacheinander an; zum Abschließen klicke auf die erste Ecke (eingekreist) oder auf „Raum schließen“. Auf dem Grundriss " +
                "klickst du die Innenecken der Wände an: die Wandstärke kommt nach außen dazu. ↶ Letzte entfernt die letzte Ecke, " +
                "mit „Maße eingeben“ wählst du Richtung und Länge jeder Wand."
        Tip.Underlay ->
            "Mit dem Fenster „◐ Grundriss und Zeichnung“ unten rechts blendest du Grundriss und Zeichnung ein oder aus und stellst " +
                "ihre Transparenz ein, um gut zu sehen, was du nachzeichnest. Die Zeichnung ist wieder voll sichtbar, wenn du das Projekt neu öffnest."
        Tip.ExcludedZone ->
            "Klicke die Ecken des Bereichs an, der nicht zu den m² zählt, etwa der niedrige Teil eines Dachgeschosses oder ein Schacht: die Ecken " +
                "rasten an den Innenecken der Wände ein. Zum Schluss wählst du den Namen; die m² des Raums sinken, der Boden bleibt vollständig."
        Tip.RoomInfo ->
            "Oben stehen Duplizieren, Löschen, Name, Typ und die wichtigsten Maße. Darunter die Reiter: Maße (Höhen, Stärken, Wände und nicht " +
                "begehbare Flächen), Material (Boden und Wände), Türen, Technik (Türen, Fenster und Installationen), Planung (bestehende, abzubrechende oder neue Wände). " +
                "Mit der Leiste rechts scrollst du den Inhalt."
        Tip.Snap ->
            "Mit 🧲 Ja springen Ecken, Wände und Objekte auf Ecken, Fluchten, gerade Richtungen und nahe Wände. Mit 🧲 Nein landen sie " +
                "genau dort, wo du sie hinziehst; der Raum schließt sich trotzdem, wenn du auf die erste Ecke klickst."
    }
}
