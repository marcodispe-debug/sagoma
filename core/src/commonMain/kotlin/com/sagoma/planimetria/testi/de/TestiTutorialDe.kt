package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.testi.TestiTutorial

object TestiTutorialDe : TestiTutorial() {
    override fun titolo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro -> "Tutorial"
        TutorialStep.OpenInfo -> "Die Infos zum Raum"
        TutorialStep.WallLengths -> "Maße ändern"
        TutorialStep.ChangeType -> "Raumtyp"
        TutorialStep.MoveWall -> "Eine Wand verschieben"
        TutorialStep.MoveCorner -> "Eine Ecke verschieben"
        TutorialStep.AddDoor -> "Eine Tür hinzufügen"
        TutorialStep.MoveDoor -> "Die Tür verschieben"
        TutorialStep.AddFixture -> "Installationen"
        TutorialStep.AddRoom -> "Einen Raum hinzufügen"
        TutorialStep.MoveRoom -> "Räume aneinanderlegen"
        TutorialStep.Undo -> "Rückgängig"
        TutorialStep.Done -> "Geschafft!"
        TutorialStep.TraceLoad -> "Grundriss laden"
        TutorialStep.TraceCalibA -> "Kalibrieren: der erste Punkt"
        TutorialStep.TraceCalibB -> "Kalibrieren: der zweite Punkt"
        TutorialStep.TraceCalibValue -> "Kalibrieren: das echte Maß"
        TutorialStep.TracePick -> "Wähle, wie du zeichnest"
        TutorialStep.TraceFirstCorner -> "Die erste Ecke"
        TutorialStep.TraceCorners -> "Die anderen Ecken"
        TutorialStep.TraceClose -> "Raum schließen"
        TutorialStep.TraceCheck -> "Maße prüfen"
        TutorialStep.TraceFade -> "Grundriss und Zeichnung"
        TutorialStep.TraceNext -> "Der Raum daneben"
        TutorialStep.TraceDone -> "Geschafft!"
    }

    override fun testo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro ->
            "Ich führe dich Schritt für Schritt durch die wichtigsten Funktionen: bei jedem Schritt probierst du selbst. " +
                "Es sind echte Änderungen am Grundriss, aber am Ende kannst du sie mit ↶ rückgängig machen."
        TutorialStep.OpenInfo ->
            "Tippe auf einen Raum, um ihn auszuwählen: unten erscheint die Info-Leiste. Tippe auf Info, auf den Pfeil ▲ " +
                "oder wische die Leiste nach oben, um seine Eigenschaften zu öffnen."
        TutorialStep.WallLengths ->
            "Im Fenster steht unter „Wände“ die Länge jeder Seite: gib einen neuen Wert ein und bestätige. " +
                "Die gegenüberliegende Seite passt sich von selbst an. Ändere dann eine Seite in der anderen Richtung. Die Maße auf dem Grundriss werden aktualisiert."
        TutorialStep.ChangeType -> "Wähle im selben Fenster einen Raumtyp, zum Beispiel Küche: Farbe und Name ändern sich von selbst."
        TutorialStep.MoveWall ->
            "Schließe das Fenster (▼ oder nach unten wischen). Tippe dann auf eine Wand, um sie auszuwählen, und ziehe sie: " +
                "der Raum wird größer oder kleiner, und die Wand rastet an den Wänden der Nachbarräume ein."
        TutorialStep.MoveCorner -> "Ziehe einen der blauen Punkte an den Ecken des Raums: so kannst du die Wände auch schräg stellen."
        TutorialStep.AddDoor -> "Tippe auf „🧱 Bauelemente“ → Türen, wähle einen Typ und tippe dann auf die Wand, an die sie soll."
        TutorialStep.MoveDoor -> "Ziehe die Tür an der Wand entlang. Unter Info änderst du ihre Maße und die Öffnungsrichtung oder machst sie zur Schiebetür."
        TutorialStep.AddFixture -> "Tippe auf „🧱 Bauelemente“ → Installationen und wähle zum Beispiel Steckdose, dann tippe auf eine Wand. Lampen setzt du dagegen, indem du auf eine Stelle im Raum tippst."
        TutorialStep.AddRoom -> "Tippe auf „🧱 Bauelemente“ → Raum, wähle Form und Maße und bestätige."
        TutorialStep.MoveRoom -> "Ziehe den neuen Raum an seinem Namen und schiebe ihn an einen anderen heran: wenn die Wände nah sind, rasten sie von selbst ein."
        TutorialStep.Undo -> "Etwas falsch gemacht? Tippe oben auf ↶ Rückgängig, um einen Schritt zurückzugehen (↷ Wiederholen macht ihn erneut)."
        TutorialStep.Done -> "Jetzt kennst du die wichtigsten Funktionen. Unter „❔ Anleitungen“ oben rechts kannst du das Tutorial jederzeit wiederholen und findest eine Anleitung für jede Funktion."
        TutorialStep.TraceLoad -> "Tippe auf „Foto / Bild“ (oder „PDF“ am Computer) und wähle den Grundriss des Hauses: Kataster, Bauplan oder ein gerades Foto."
        TutorialStep.TraceCalibA ->
            "Suche auf dem Grundriss ein eingetragenes Maß, am besten ein langes (zum Beispiel eine Wand mit 4,72). " +
                "Vergrößere und tippe auf den Anfang dieses Maßes: Punkt A erscheint."
        TutorialStep.TraceCalibB -> "Tippe jetzt auf das Ende desselben Maßes: Punkt B erscheint. Bei einem Fehler beginnt ein weiteres Tippen wieder bei A."
        TutorialStep.TraceCalibValue ->
            "Gib im Fenster oben ein, wie lang das Maß zwischen A und B wirklich ist (in cm, wie auf dem Grundriss), und bestätige: " +
                "der Grundriss kommt in den richtigen Maßstab."
        TutorialStep.TracePick ->
            "Die Kalibrierung ist gespeichert: der Grundriss ist maßstäblich. Wähle jetzt, wie du die Räume zeichnest: mit einer Form (Quadrat, " +
                "Rechteck, L-Form…) erstellst du sie mit ihren Maßen und ziehst sie am Namen über den Grundriss; mit „Wand für Wand“ klickst du " +
                "ihre Ecken auf dem Grundriss nacheinander an."
        TutorialStep.TraceFirstCorner ->
            "Du bist jetzt in „Wand für Wand“. Wähle einen Raum und klicke auf eine seiner Ecken, an der Innenseite der Wand: " +
                "die Wandstärke kommt von selbst nach außen dazu."
        TutorialStep.TraceCorners -> "Klicke die anderen Ecken nacheinander an, einmal rund um den Raum. Bei einem Fehler entfernt „↶ Letzte“ die letzte Ecke."
        TutorialStep.TraceClose ->
            "Wenn du alle Ecken angeklickt hast, klicke noch einmal auf die erste (die eingekreiste) oder tippe auf „Raum schließen“; " +
                "wähle dann den Raumtyp und bestätige."
        TutorialStep.TraceCheck ->
            "Öffne die Info des Raums (Leiste unten, ▲ oder nach oben wischen): vergleiche m² und Wände mit dem Grundriss. " +
                "Wenn ein Teil nicht zu den m² zählt (niedrige Dachschräge, Schacht), nutze „▧ Nicht begehbare Fläche hinzufügen“ im Reiter Maße."
        TutorialStep.TraceFade ->
            "Schließe das Fenster und tippe unten rechts auf „◐ Grundriss und Zeichnung“: mach den Grundriss heller oder blende ihn aus, " +
                "um die Zeichnung gut zu sehen."
        TutorialStep.TraceNext -> "Tippe auf „🧱 Bauelemente“ → Raum und zeichne den Raum daneben nach, mit einer Form oder mit „Wand für Wand“, wie vorher."
        TutorialStep.TraceDone ->
            "Mach so mit den anderen Räumen weiter. Wenn das Einrasten deine Punkte verschiebt, schalte es mit 🧲 aus. " +
                "Mit „Fertig“ startet, falls noch nicht geschehen, das Tutorial der anderen Funktionen (Türen, Installationen, Maße)."
    }

    override fun latiCambiati(n: Int): String =
        if (n == 0) "Geänderte Seiten: 0 von 2" else "Geänderte Seiten: 1 von 2 · jetzt eine Seite in der anderen Richtung"
    override fun angoliServono(n: Int): String = "Angeklickte Ecken: $n · mindestens 3 nötig"
    override fun angoliCliccati(n: Int): String = "Angeklickte Ecken: $n"
}
