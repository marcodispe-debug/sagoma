package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoDe : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "L-Form"
        Lesson.Elements -> "Türen, Fenster und mehr"
        Lesson.Check -> "Grundriss prüfen"
        Lesson.OverPlan -> "Auf deinem Grundriss"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Anleitung · Lektion $n von $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Schritt $n von $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Willkommen bei Sagoma!"
        CourseStep.ElementsDone -> "Jedes Element über „$infoTab“"
        CourseStep.Finished -> "Du hast die Anleitung geschafft!"
        CourseStep.Snap -> "Räume verschieben und der Magnet 🧲"
        CourseStep.InnerFace -> "Die Innenseite der Wände"
        else -> "Geschafft!"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Ich führe dich Schritt für Schritt durch das Zeichnen des Hauses auf diesem erfundenen Grundriss, in ${Lesson.entries.size} kurzen Lektionen: ein Raum nach dem " +
                "anderen, jeder an seinem Platz wie auf der Zeichnung. Die Aktionen machst du selbst: bei jedem Schritt zeige ich dir mit einem orangefarbenen Rahmen, was " +
                "du anklicken sollst. Es ist ein Projekt nur zum Üben: deine Projekte bleiben unberührt."
        CourseStep.SquareDone ->
            "Das Arbeitszimmer ist fertig: 3,30 × 3,30 m. Du hast alle Wege gesehen, die Form eines Raums zu ändern: die Maße beim Erstellen, " +
                "gezogene Wände und Ecken, die genauen Maße unter „$infoTab“ und „${e.annulla}“, um zurückzugehen. " +
                "In der nächsten Lektion: das Wohnzimmer in L-Form, über dem Arbeitszimmer."
        CourseStep.Snap ->
            "Das Wohnzimmer ist getrennt vom Haus entstanden. Einen Raum verschiebst du, indem du ihn an seinem Namen ziehst. Der Magnet „${T.schedaStanza.si}“ " +
                "hier unten ist an: wenn du den Raum nahe an einen anderen bringst, springt er und hängt sich von selbst an, Ecke an Ecke, und die " +
                "Wand zwischen beiden wird zu einer einzigen. Ausgeschaltet („${T.schedaStanza.no}“) bleibt der Raum genau dort, wo du ihn loslässt. Lass ihn an."
        CourseStep.LDone ->
            "Das Wohnzimmer hängt über dem Arbeitszimmer, wie auf dem Grundriss: die Wand zwischen beiden Räumen ist jetzt eine gemeinsame. " +
                "In der nächsten Lektion: das Schlafzimmer, ein Rechteck rechts vom Wohnzimmer."
        CourseStep.RectDone ->
            "Das Schlafzimmer hängt rechts am Wohnzimmer. In der nächsten Lektion: das Bad unter dem Schlafzimmer, das etwas schiefwinklig ist."
        CourseStep.SurveyDone ->
            "Das Bad liegt unter dem Schlafzimmer, mit seiner echten Form: etwas schiefwinklig, wie in Wirklichkeit. In der nächsten Lektion: " +
                "der Eingang, Wand für Wand im Raum gezeichnet, der zwischen den Räumen bleibt."
        CourseStep.WallsDone ->
            "Der Eingang schließt das Haus: jetzt sind alle Räume verbunden, wie auf dem Grundriss. Du hast alle Wege gesehen, einen Raum " +
                "zu erstellen. In der nächsten Lektion: die Einzelwand, eine Wand für sich, die keinen Raum schließt."
        CourseStep.FreeWallDone ->
            "Das Schlafzimmer hat jetzt seine Ankleide, abgetrennt durch eine Trennwand mit dem Durchgang am Ende. Im Reiter „$infoTab“ " +
                "der Einzelwand wählst du auch die Höhe (bis zur Decke oder niedriger, wie ein Mäuerchen), die Farbe und die Krümmung; und auch " +
                "in Einzelwände kannst du Türen und Fenster setzen. In der nächsten Lektion: Türen, Fenster, Balkon, Installationen und Treppen."
        CourseStep.ElementsDone ->
            "Im Reiter „$infoTab“ änderst du Maße, Typ und alles andere des ausgewählten Elements; er schließt sich mit „▼“. " +
                "Für jedes Element gilt: du wählst es unter „${e.elementiArchitettonici}“, setzt es auf den Grundriss, und den Rest erledigst du unter „$infoTab“. " +
                "In der nächsten Lektion: die Prüfung des Grundrisses, die Zeichenfehler von selbst findet und viele davon behebt."
        CourseStep.CheckDone ->
            "Jetzt kannst du die Grundrissprüfung nutzen: die vollständige Liste im Menü „⋮“, „✓ ${T.controllo.correggi}“ und „✓ Alle beheben“, die " +
                "von selbst korrigieren („${e.annulla}“ geht zurück), die „!“ auf dem Grundriss, um zu sehen, was an einer Stelle ist, und „${T.controllo.togliDallaPianta}“ " +
                "für die, die so in Ordnung sind (in der Liste kommen sie zurück). Vor dem Exportieren warnt dich Sagoma, wenn noch Zeichenfehler da sind. " +
                "Letzte Lektion: die Räume auf deinem Grundriss aufbauen. Dafür braucht es ein neues Projekt: ich bereite es vor."
        CourseStep.Finished ->
            "Hier sind Schlafzimmer, Bad und Wohnzimmer auf dem Grundriss, mit den richtigen Maßen. Mit den anderen Räumen machst du es genauso: eine Form " +
                "zum Start, an die Nachbarräume gehängt, dann Wände und Ecken an ihren Platz und die genauen Maße unter „$infoTab“. " +
                "Jetzt kannst du dein Zuhause zeichnen! Die Anleitung kannst du jederzeit unter „${e.guide}“ wiederholen."
        CourseStep.InnerFace ->
            "Bevor du das Schlafzimmer verschiebst, etwas Wichtiges. Sagoma misst die Räume an der Innenseite der Wände, wie die Maße " +
                "auf dem Grundriss: die Wandstärke geht nach außen. Wenn du einen Raum auf den Grundriss legst, bring die Innenseite der " +
                "gezeichneten Wände möglichst genau auf die Innenseite der Wände im Grundriss: achte auf die innere Linie der Wand, " +
                "nicht auf die Mitte oder die Außenseite. So stimmen Maße, Quadratmeter und Nachbarräume."
        else -> ""
    }

    override val avanti: String get() = "Weiter"
    override val apriProgettoNuovo: String get() = "Neues Projekt öffnen"
    override val bastaCosi: String get() = "Genug für jetzt"
    override val vaiAiProgetti: String get() = "Zu deinen Projekten"
    override val nienteDaIndicare: String get() = "Führe die Aktion dieses Schritts aus oder klicke auf „${e.saltaPasso}“."
    override val tornaIndietro: String get() = "Das ist nicht die Form dieses Schritts: klicke auf „${T.comuni.indietro}“ und wähle die richtige."
    override val vaiAllaLezione: String get() = "Oder geh direkt zur Lektion:"
    override val voceMenu: String get() = "Interaktive Anleitung (${Lesson.entries.size} Lektionen)"
    override val progettoProva: String get() = "Übung der Anleitung"
    override val progettoProvaPlanimetria: String get() = "Übung der Anleitung · Grundriss"
    override val usaPiantina: String get() = "🧭 Grundriss der Anleitung verwenden"
    override val ingresso: String get() = "Eingang"
    override val cabinaArmadio: String get() = "Ankleide"
    override val notaMisure: String get() = "Maße in cm, an der Innenkante der Wände"

    override val apriElementi: String get() = "Klicke unten auf „${e.elementiArchitettonici}“."
    override val apriElementiAltraStanza: String get() = "Um einen weiteren Raum hinzuzufügen, klicke unten auf „${e.elementiArchitettonici}“."
    override val poiStanza: String get() = "Klicke auf „${e.stanza}“: das Fenster mit den Formen erscheint wieder."
    override fun tipo(nome: String): String = "Wähle den Typ: „$nome“."
    override val creaStanza: String get() = "Klicke auf „${cr.creaStanza}“."
    override fun selezionaStanza(nome: String): String = "Klicke auf den Namen des Raums, „$nome“, um ihn auszuwählen."
    override val accendiAgganci: String get() = "Der Magnet ist aus: klicke unten auf „${T.schedaStanza.no}“, um ihn einzuschalten."
    override val schedaMisure: String get() = "Öffne den Reiter „${T.schedaStanza.misure}“."

    override fun latoMuro(a: WallArrow): String = when (a) {
        WallArrow.UP -> "oben"
        WallArrow.RIGHT -> "rechts"
        WallArrow.DOWN -> "unten"
        WallArrow.LEFT -> "links"
    }

    override val scegliQuadrato: String get() =
        "Wenn du ein neues Projekt öffnest, fragt Sagoma sofort, welche Form der erste Raum hat. Fangen wir mit dem Arbeitszimmer an, dem " +
            "quadratischen Raum unten links im Grundriss: klicke auf „${cr.quadrato}“."
    override val tipoStudio: String get() = "Klicke unter „${cr.tipoStanza}“ auf „${RoomType.Studio.label}“: Sagoma gibt dem Raum den richtigen Namen und die richtige Farbe."
    override fun scriviLato(v: Int): String =
        "Gib im Feld „${cr.lato2}“ $v ein: das ist der erste Weg, einem Raum seine Form zu geben, mit den Maßen beim Erstellen. Es ist das " +
            "lichte Maß, von Wand zu Wand: das, was du mit dem Zollstock misst."
    override val soffittoECrea: String get() = "Die Raumhöhe ist schon eingetragen (270 cm): ändere sie nur, wenn dein Raum anders ist. Klicke jetzt auf „${cr.creaStanza}“."
    override val trascinaMuroStudio: String get() =
        "Zweiter Weg: die Wände ziehen. Drücke auf die rechte Wand des Arbeitszimmers, halte gedrückt und ziehe sie bis zur Markierung: der Raum " +
            "wird größer, und das Maß auf der Wand ändert sich, während du sie verschiebst."
    override fun misureEsatte(v: Int): String =
        "Von Hand gezogen wird das Maß nie genau. Dritter Weg, der genaueste: die Maße eingeben. Bringen wir das Arbeitszimmer zurück auf " +
            "$v cm: klicke auf „$infoTab“."
    override fun muroStudio(numero: Int, lato: WallArrow, v: Int): String =
        "Unter „${T.schedaStanza.muri}“ steht die lichte Länge jeder Wand. Gib bei „${T.schedaStanza.muro(numero)}“, der Wand " +
            "${latoMuro(lato)}, $v ein und drücke Enter: die nächste Wand verschiebt sich und das Arbeitszimmer ist wieder quadratisch."
    override val trascinaAngoloStudio: String get() =
        "Vierter Weg, für schiefwinklige Räume: eine Ecke ziehen. Drücke auf den blauen Punkt der Ecke unten rechts und " +
            "ziehe ihn bis zur Markierung."
    override val annullaAngolo: String get() =
        "Das Arbeitszimmer hat aber rechte Winkel. Wenn du dich vertust, nimmt „${e.annulla}“ hier oben die letzte Änderung zurück: klicke darauf."

    override val scegliRettangolo: String get() = "Klicke auf „${cr.rettangolo}“: das ist das Schlafzimmer rechts vom Wohnzimmer, mit zwei langen und zwei kurzen Seiten."
    override val spostaCameraAccanto: String get() =
        "Auch das Schlafzimmer entsteht getrennt: ziehe es am Namen bis zum gestrichelten Umriss rechts vom Wohnzimmer. Mit eingeschaltetem " +
            "Magnet hängt es sich von selbst an."
    override fun scriviLarghezza(v: Int): String = "„${T.comuni.larghezza}“ ist die waagerechte Seite des Raums, von links nach rechts auf dem Grundriss: gib $v ein."
    override fun scriviProfondita(v: Int): String = "„${T.comuni.profondita}“ ist die andere Seite, von oben nach unten: gib $v ein."

    override val scegliL: String get() =
        "Klicke auf „${T.comuni.formaL}“: das ist das Wohnzimmer, das im Grundriss über dem Arbeitszimmer liegt. Viele Wohnzimmer und Küchen sind " +
            "so geformt, mit einer einspringenden Ecke."
    override val spostaSoggiorno: String get() =
        "Ziehe das Wohnzimmer an seinem Namen bis zum gestrichelten Umriss über dem Arbeitszimmer: wenn es nahe genug ist, springt es und hängt sich von selbst an."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma fragt, auf welcher Seite das zusätzliche Stück des L vorsteht. Sieh dir den Raum von oben an, wie auf einem Grundriss: hier steht es „$lato“ vor."
    override fun larghezzaPrincipale(v: Int): String = "Das L besteht aus zwei Stücken. Das Hauptstück ist das größere Rechteck: gib bei „${cr.larghezzaPrincipale}“ $v ein."
    override fun profonditaPrincipale(v: Int): String = "Gib bei „${cr.profonditaPrincipale}“ $v ein."
    override fun larghezzaProtuberanza(v: Int): String =
        "Der „Vorsprung“ ist das Stück, das vorsteht. Gib bei „${cr.larghezzaProtuberanza}“ $v ein: es muss weniger sein als die Hauptbreite."
    override fun profonditaProtuberanza(v: Int): String = "Gib bei „${cr.profonditaProtuberanza}“ ein, wie weit es vorsteht: $v."

    override val scegliRilievo: String get() =
        "Klicke auf „${cr.rilievo}“: das Bad unter dem Schlafzimmer hat Winkel, die nicht ganz 90° sind. Das hilft auch bei Räumen mit 5 und mehr Seiten."
    override val spostaBagnoSotto: String get() = "Ziehe das Bad am Namen bis zum gestrichelten Umriss unter dem Schlafzimmer: es hängt sich von selbst an."
    override val tipoBagnoQuattroLati: String get() =
        "Wähle den Typ „${RoomType.Bagno.label}“. Bei „${cr.numeroLati}“ steht schon 4, wie die Seiten unseres Bads."
    override fun primoLatoRilievo(v: Int): String =
        "Nenne eine Ecke des Raums A, dann B, C und D im Uhrzeigersinn. Miss die Seiten an der Wand, innen. Gib bei „${cr.lato('A', 'B')}“ $v ein."
    override fun lato(nome: String, v: Int): String = "Gib bei „${cr.lato(nome[0], nome[1])}“ $v ein."
    override fun diagonaleAC(v: Int): String =
        "Dann eine Diagonale: von A zur gegenüberliegenden Ecke C (AC) oder von B nach D (BD), je nachdem, welche du gemessen hast. Hier ist es AC, schon gewählt: gib $v ein. " +
            "Dieses Maß sagt Sagoma, wie schief die Winkel sind."
    override val anteprimaECrea: String get() = "Weiter unten ist die Vorschau der echten Form. Klicke auf „${cr.creaStanza}“."

    override val scegliMuroPerMuro: String get() =
        "Klicke auf „${cr.muroMuro}“: der Eingang füllt den Raum zwischen den Räumen, und du zeichnest ihn Wand für Wand, mit den " +
            "lichten Maßen, wie in einem Architekturprogramm."
    override val primoAngolo: String get() =
        "Klicke hier, unter dem Schlafzimmer und neben dem Wohnzimmer: das ist die erste Ecke des Eingangs, innen. Sagoma rastet sie " +
            "an der richtigen Ecke zwischen den Wänden ein."
    override fun verso(n: Int, a: WallArrow): String = "Wand $n: wähle die Richtung „${freccia(a)}“."
    override fun lunghezzaMuro(v: Int): String = "Gib bei „${T.muroPerMuro.lunghezzaCm}“ $v ein."
    override val aggiungi: String get() = "Klicke auf „${T.muroPerMuro.aggiungi}“."
    override fun muriFatti(n: Int, tot: Int): String = "Fertige Wände: $n von $tot"
    override val chiudiStanza: String get() =
        "Es fehlt nur die letzte Wand, die zur ersten Ecke zurückführt: klicke auf „${T.muroPerMuro.chiudiStanza}“ und Sagoma zeichnet sie selbst."
    override fun tipoIngresso(nome: String): String = "Wähle den Typ: für den Eingang passt „$nome“. Raumhöhe und Wandstärke sind schon eingetragen."

    override fun selezionaCameraTramezzo(nome: String): String =
        "Eine Einzelwand ist eine Wand für sich, die keinen Raum schließt: zum Beispiel die Trennwand der Ankleide im Schlafzimmer, " +
            "wie auf dem Grundriss. Die Wand entsteht im ausgewählten Raum: klicke auf den Namen des Schlafzimmers, „$nome“."
    override val apriElementiMuroSingolo: String get() = "Klicke jetzt unten auf „${e.elementiArchitettonici}“."
    override val scegliMuroSingolo: String get() =
        "Ganz unten im Fenster ist „${cr.muroSingolo}“: es erscheint nur, wenn es schon mindestens einen Raum gibt. Klicke darauf."
    override val spostaTramezzo: String get() =
        "Die Wand entsteht gerade quer durch das Schlafzimmer, von einer Wand zur anderen, und ist schon ausgewählt. Du verschiebst sie durch Ziehen: drücke auf die " +
            "Wand, halte gedrückt und bring sie bis zur gestrichelten Linie, wo die Trennwand hin soll. Die beiden Enden bleiben an den Wänden."
    override val selezionaMuroSingolo: String get() = "Klicke auf die Einzelwand, um sie auszuwählen: an beiden Enden erscheinen Punkte."
    override val accorciaTramezzo: String get() =
        "Die Trennwand reicht nicht bis ganz hinten: sie lässt den Durchgang in die Ankleide frei. Drücke auf den blauen Punkt unten, " +
            "am Ende der Wand, und ziehe ihn nach oben bis zur Markierung: die Wand wird kürzer."
    override val infoTramezzo: String get() = "Wie bei den Räumen gibst du die genauen Maße unter „$infoTab“ ein: klicke darauf."
    override fun lunghezzaTramezzo(v: Int): String =
        "Gib bei „${T.comuni.lunghezza}“ $v ein: die Wand wird am Ende länger oder kürzer, der Anfang oben bleibt, wo er ist."
    override fun spessoreTramezzo(v: Int): String = "Eine Trennwand aus Gipskarton ist dünn: gib bei „${T.comuni.spessore}“ $v ein."

    override val portaElementi: String get() = "Türen, Fenster und alles andere fügst du unter „${e.elementiArchitettonici}“ hinzu. Klicke darauf."
    override val porte: String get() = "Klicke auf „${e.porte}“."
    override val portaUnaAnta: String get() = "Wähle den Typ, zum Beispiel „${e.portaAd1Anta}“."
    override val cliccaMuroPorta: String get() = "Klicke jetzt auf die Wand, an die Stelle, wo die Tür hin soll: zum Beispiel hier."
    override val finestraElementi: String get() = "Ein Fenster setzt du genauso: „${e.elementiArchitettonici}“…"
    override val finestre: String get() = "…„${e.finestre}“…"
    override val finestraDueAnte: String get() = "…„${e.finestra2Ante}“."
    override val cliccaMuroFinestra: String get() = "Klicke auf eine Außenwand, wo das Fenster hin soll: zum Beispiel hier."
    override val balconeElementi: String get() = "Jetzt ein Balkon: „${e.elementiArchitettonici}“…"
    override val balconiTerrazze: String get() = "…„${e.balconiTerrazze}“…"
    override val balcone: String get() = "…„${RoomType.Balcone.label}“."
    override val balconeRettangolo: String get() = "Die Form wählst du wie bei den Räumen: der Balkon ist ein „${cr.rettangolo}“."
    override fun balconeLarghezza(v: Int): String = "Breite $v…"
    override fun balconeProfondita(v: Int): String = "…und Tiefe $v."
    override val balconeCrea: String get() =
        "Klicke auf „${cr.crea}“: der Balkon entsteht an seinem Platz, über dem Schlafzimmer, schon am Haus anliegend (danach kannst du ihn am Namen verschieben)."
    override val portaFinestraElementi: String get() = "Um auf den Balkon zu gehen, braucht es eine Fenstertür: „${e.elementiArchitettonici}“…"
    override val porteFinestre: String get() = "…„${e.porteFinestre}“…"
    override val balconeDueAnte: String get() = "…„${e.balcone2Ante}“."
    override val cliccaMuroBalcone: String get() = "Klicke auf die Hauswand, an der der Balkon anliegt, hier."
    override val impiantiElementi: String get() = "Jetzt die Installationen: Heizkörper, Steckdosen, Schalter, Wasseranschlüsse und Lampen. „${e.elementiArchitettonici}“…"
    override val impianti: String get() = "…„${e.impianti}“…"
    override val calorifero: String get() = "…„${e.calorifero}“."
    override val cliccaMuroCalorifero: String get() = "Heizkörper, Steckdosen, Schalter und Wasseranschlüsse setzt du durch Klicken auf die Wand: zum Beispiel hier."
    override val luciElementi: String get() = "Lampen kommen dagegen in den Raum. „${e.elementiArchitettonici}“…"
    override val luci: String get() = "…„${e.luci}“…"
    override val lampadario: String get() = "…zum Beispiel „${FixtureKind.Chandelier.label}“."
    override val luceSemplice: String get() = "…und hier „${T.arredi.puntoLuceSemplice}“: du setzt ihn durch Klicken in den Raum."
    override val cliccaStanzaLuce: String get() = "Klicke in den Raum, wo die Lampe hin soll: zum Beispiel hier."
    override val cliccaStudio: String get() = "Zuletzt die Treppe. Klicke zuerst in den Raum, wo sie hin soll, zum Beispiel das Arbeitszimmer, hier."
    override val scalaElementi: String get() = "Dann „${e.elementiArchitettonici}“…"
    override val scale: String get() = "…„${e.scale}“…"
    override val chiocciola: String get() = "…und wähle die Form, zum Beispiel „${StairKind.Spiral.label}“."
    override val info: String get() =
        "Die Treppe ist ausgewählt. Für alle weiteren Änderungen klicke auf „$infoTab“: das gilt für jedes Element, Türen, Fenster, Balkone, Installationen und Treppen."

    override val controlloMenu: String get() = "Eine Hilfe für jeden Tag: die Grundrissprüfung, die Zeichenfehler findet, die sonst leicht übersehen werden. Klicke hier oben auf das Menü „⋮“."
    override val controlloVoce: String get() = "…und auf „${T.controllo.voceMenu}“."
    override val controlloRiapri: String get() = "Öffne die Prüfung erneut: das Menü „⋮“ hier oben…"
    override val controlloCorreggiTutti: String get() =
        "Hier ist die Liste: ⚠ was zu beheben ist, ? was zu prüfen ist, 📏 die Mindestmaße der Bauvorschriften. Viele Probleme lösen sich von selbst: " +
            "„✓ Alle beheben“ korrigiert eine ganze Gruppe auf einmal (setzt zum Beispiel fehlende Türen) und ein einziges „${e.annulla}“ macht es rückgängig. Klicke darauf."
    override val controlloCorreggi: String get() =
        "Hier ist die Liste: ⚠ was zu beheben ist, ? was zu prüfen ist, 📏 die Mindestmaße der Bauvorschriften. Viele Probleme lösen sich von selbst: " +
            "klicke auf „✓ ${T.controllo.correggi}“ (mit „${e.annulla}“ geht es zurück)."
    override val controlloChiudi: String get() =
        "Erledigt: die Liste aktualisiert sich von selbst. „${T.controllo.mostra}“ führt zur richtigen Stelle und wählt sie aus, „${T.controllo.condividi}“ schickt " +
            "die Liste an jemanden, der dir hilft. Klicke jetzt auf „${T.controllo.chiudi}“."
    override val controlloSegno: String get() =
        "Die Prüfung arbeitet auch von selbst, während du zeichnest: das Zeichen oben links auf dem Grundriss zeigt, wie viele Probleme es gibt (tippe darauf, " +
            "um die Liste wieder zu öffnen), und ein „!“ markiert die Stelle jedes Problems, rot für Fehler, gelb für Dinge zum Prüfen, blau für Vorschriften. Klicke auf dieses „!“."
    override val controlloTogli: String get() =
        "Das Fenster zeigt, was an dieser Stelle ist, mit „✓ ${T.controllo.correggi}“ (wo möglich) und „${T.controllo.mostra}“. Wenn es für dich so in Ordnung ist, " +
            "entfernt „${T.controllo.togliDallaPianta}“ das „!“: das Problem bleibt in der Liste und kann von dort zurückgeholt werden. Klicke auf „${T.controllo.togliDallaPianta}“."
    override val controlloSegnoDiNuovo: String get() = "Klicke auf ein „!“ auf dem Grundriss: das Fenster dieser Stelle öffnet sich."

    override val ricalca: String get() =
        "Hast du schon einen Grundriss, aus dem Kataster, vom Bauplan oder auch nur ein Foto? Klicke bei der Wahl des ersten Raums eines Projekts auf „${cr.ricalcaPiantina}“."
    override val usaPiantinaGuida: String get() =
        "Normalerweise klickst du hier auf „${T.sfondo.fotoImmagine}“ (oder „${T.sfondo.pdf}“) und wählst die Datei deines Grundrisses. Zum Üben nimm den Grundriss der Anleitung: klicke auf „$usaPiantina“."
    override fun taraturaA(cm: Int): String =
        "Der Grundriss liegt unter der Zeichnung, aber Sagoma weiß noch nicht, wie groß er ist: er muss mit einem eingetragenen Maß in den Maßstab gebracht werden. " +
            "Nehmen wir die obere Wand des Wohnzimmers, $cm cm: klicke hier, auf die Innenecke oben links des Wohnzimmers."
    override val taraturaB: String get() = "Klicke jetzt dort, wo das Maß endet: die Innenecke oben rechts des Wohnzimmers, hier."
    override fun misuraReale(cm: Int): String = "Gib das echte Maß zwischen den beiden Punkten ein, das auf dem Grundriss steht: $cm."
    override val tara: String get() = "Klicke auf „${T.sfondo.tara2}“: jetzt ist der Grundriss maßstäblich, jeder Zentimeter stimmt."
    override val sceltaDopoTaratura: String get() =
        "Die Kalibrierung ist gespeichert: der Grundriss ist jetzt maßstäblich. Wähle nun, wie du den ersten Raum zeichnest: mit „${cr.muroMuro}“ " +
            "klickst du seine Ecken auf dem Grundriss an, mit einer Form erstellst du ihn und bringst ihn dann auf den Grundriss. Hier nehmen wir die Formen. " +
            "Fangen wir mit dem Schlafzimmer an, einem Rechteck: wir beginnen mit einem Quadrat, um zu sehen, wie man es verlängert. Klicke auf „${cr.quadrato}“."
    override val cameraDaQuadrato: String get() =
        "Fangen wir mit dem Schlafzimmer an, einem Rechteck: wir beginnen mit einem Quadrat, um zu sehen, wie man es verlängert. Klicke auf „${cr.quadrato}“."
    override fun latoCortoCamera(v: Int): String = "Gib die kurze Seite des Schlafzimmers ein, abgelesen auf dem Grundriss: $v."
    override val spostaCamera: String get() =
        "Das Schlafzimmer ist da, aber noch nicht an seinem Platz. Um einen Raum zu verschieben, drücke auf seinen Namen, halte gedrückt und ziehe ihn: lass ihn " +
            "los, wenn die Innenecke oben links über der Innenecke des gezeichneten Schlafzimmers liegt (der gestrichelte Umriss)."
    override fun allungaCamera(v: Int): String =
        "Das Schlafzimmer im Grundriss ist breiter: $v. Es reicht, die rechte Wand zu verschieben: drücke auf die Wand, halte gedrückt und ziehe sie bis zur Markierung. " +
            "Beim Verschieben ändert sich das Maß: lass sie bei $v los."
    override val bagnoElementi: String get() = "Jetzt das Bad, das schief ist: auch hier beginnen wir mit einem Quadrat. „${e.elementiArchitettonici}“…"
    override val bagnoQuadrato: String get() = "…„${cr.quadrato}“…"
    override fun latoBagno(v: Int): String = "…Seite $v, die obere Wand des Bads…"
    override val spostaBagno: String get() = "Ziehe das Bad am Namen über das Bad im Grundriss, mit der Ecke oben links an ihrem Platz."
    override val angoloC: String get() =
        "Die Ecken des Bads haben keine 90°. Drücke auf den blauen Punkt der Ecke unten rechts und ziehe ihn bis zur Markierung, wo er mit dem Grundriss übereinstimmt."
    override val angoloD: String get() = "Dann die Ecke unten links: nur ein wenig tiefer. Die Wandmaße aktualisieren sich, während du sie verschiebst."

    override val soggiornoElementi: String get() = "Letzter Raum: das Wohnzimmer in L-Form. „${e.elementiArchitettonici}“…"
    override val soggiornoL: String get() = "…„${T.comuni.formaL}“."
    override val soggiornoMisureDopo: String get() =
        "Wähle den Typ „${RoomType.Soggiorno.label}“. Lass die Maße, wie sie sind: das Wohnzimmer bringst du danach auf dem Grundriss auf Maß."
    override val attaccaSoggiorno: String get() =
        "Im Grundriss hängt das Wohnzimmer rechts am Schlafzimmer. Ziehe das Wohnzimmer am Namen bis zum gestrichelten Umriss: " +
            "mit eingeschaltetem Magnet „${T.schedaStanza.si}“ springt es und hängt sich von selbst an, wenn seine Ecke oben rechts nahe an die des Schlafzimmers kommt."
    override fun muroSullaPiantina(muro: Int): String =
        "Bring jetzt die Wände auf den Grundriss. Drücke auf ${nomeMuroSoggiorno(muro)} des Wohnzimmers und ziehe sie bis zur Markierung, auf die " +
            "gezeichnete Wand. Ungefähr reicht: das genaue Maß gibst du danach ein."
    override val misureInInfo: String get() =
        "Wenn du die Wände von Hand verschiebst, werden die Maße nie ganz gleich wie im Grundriss: sieh dir die Maße auf den Wänden des " +
            "Wohnzimmers an. Die richtigen gibst du unter „$infoTab“ ein: klicke darauf."
    override fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Unter „${T.schedaStanza.muri}“ steht die lichte Länge jeder Wand. " else "") +
            "Gib bei „${T.schedaStanza.muro(numero)}“, ${nomeMuroSoggiorno(muro)}, $v ein und drücke Enter."

    override val apriPannello: String get() =
        "Zum Schluss: um die Zeichnung auf dem Grundriss besser zu sehen, klicke hier unten rechts auf „${e.piantinaDisegno}“."
    override val trasparenzaPiantina: String get() =
        "Hier stellst du den Grundriss ein: mit dem Schieberegler wird er heller oder dunkler, mit dem Schalter blendest du ihn aus. Darunter gibt es " +
            "dasselbe für die Zeichnung. Wenn du fertig bist, schließe das Fenster mit „${T.comuni.chiudi}“. Probier es: ziehe den Schieberegler des Grundrisses."

    override fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "die Wand oben"
        1 -> "die Wand rechts"
        2 -> "die Wand unten rechts"
        3 -> "die kurze Wand in der Mitte"
        4 -> "die Wand unten links"
        else -> "die Wand links"
    }
}
