package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.editor.FixSummary
import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloDe : TestiControllo() {
    override val voceMenu: String get() = "🔍 Grundriss prüfen…"
    override val titolo: String get() = "Grundriss prüfen"
    override val nessunProblema: String get() = "Keine Probleme gefunden."
    override val cosaControlla: String get() =
        "Sucht überlappende oder fast aneinanderliegende Räume, sich kreuzende Wände, doppelte Türen und Fenster, solche, die breiter oder höher als die Wand sind, " +
            "Räume ohne Tür, Flügel, die beim Öffnen anstoßen, die Haustür, die Treppen, die Installationen und die gesetzlichen Mindestwerte der Räume (Fenster, Höhen, Quadratmeter)."
    override val spiega: String get() =
        "Sie können Quadratmeter, Mengen und 3D verfälschen. Auf dem Grundriss markiert ein „!“, wo jedes Problem liegt. „Zeigen“ führt dorthin und wählt es aus; " +
            "„Beheben“ korrigiert es von selbst (rückgängig mit ↶); „Ausblenden“ nimmt sein „!“ vom Grundriss."
    override val daCorreggere: String get() = "Zu beheben"
    override val daControllare: String get() = "Zu prüfen"
    override fun daNorme(n: HousingNorms): String = if (n.official) "Normen fürs Wohnen (${paese(n.country)})" else "Empfehlungen fürs Wohnen"
    override fun paese(codice: String): String = when (codice) {
        "IT" -> "Italien"
        "DE" -> "Deutschland"
        "FR" -> "Frankreich"
        "ES" -> "Spanien"
        "GB" -> "England"
        "US" -> "USA"
        else -> codice
    }
    override fun spiegaNorme(n: HousingNorms): String = when {
        n.country == "IT" ->
            "Mindestwerte des italienischen DM vom 5. Juli 1975 für Wohnräume (Schlafzimmer, Wohnzimmer, Küche, Arbeitszimmer) und Bäder; die Bauordnung der Gemeinde " +
                "kann mehr verlangen. Fenster zählen, wenn sie sich öffnen lassen und nach außen gehen, gemessen an der Wandöffnung. Wenn du Steckdosen setzt, auch die Steckdosen " +
                "und Lichtauslässe der elektrischen Grundausstattung (CEI 64-8, Stufe 1). "
        !n.official ->
            "Die App hat die Normen des Landes, in dem das Haus steht, noch nicht: das sind Empfehlungen, die weltweit häufigsten Mindestwerte. " +
                "Vor Ort können andere Regeln gelten. "
        else ->
            "Mindestwerte nach ${n.source} für Wohnräume (Schlafzimmer, Wohnzimmer, Küche, Arbeitszimmer) und Bäder; vor Ort kann mehr verlangt werden. " +
                "Fenster zählen, wenn sie ${if (n.openableOnly) "sich öffnen lassen und " else ""}nach außen gehen, gemessen an der Wandöffnung. "
    } + dovePaese
    override val dovePaese: String get() = "Das Land ist das der Stadt des Hauses (🧭 Norden und Stadt); ohne Stadt das der Sprache der App."
    override fun nome(n: String): String = "„$n“"
    override val mostra: String get() = "Zeigen"
    override val chiudi: String get() = "Schließen"
    override fun troppi(n: Int): String = "Nur die ersten $n: behebe diese und prüfe erneut."
    override val primaDiEsportareTitolo: String get() = "Vor dem Exportieren"
    override fun primaDiEsportare(n: Int): String =
        "Der Grundriss hat ${if (n == 1) "einen Zeichenfehler" else "$n Zeichenfehler"} (⚠): Maße, Quadratmeter und Mengen könnten auch in der exportierten Datei falsch sein."
    override val controlla: String get() = "Prüfen"
    override val esportaLoStesso: String get() = "Trotzdem exportieren"
    override val correggi: String get() = "Beheben"
    override val nonSiCorregge: String get() = "Das lässt sich nicht automatisch beheben: bitte von Hand korrigieren."
    override fun correggiTutti(n: Int): String = "Alle beheben ($n)"
    override fun corretti(n: Int): String =
        if (n == 1) "Ein Problem behoben: rückgängig mit ↶." else "$n Probleme behoben: ein einziges ↶ macht alle rückgängig."
    override fun corretti(n: Int, fatto: String): String =
        if (n == 1) "Ein Problem behoben ($fatto): rückgängig mit ↶." else "$n Probleme behoben ($fatto): ein einziges ↶ macht alle rückgängig."

    override fun quante(cosa: FixSummary.Thing, n: Int): String = "$n " + when (cosa) {
        FixSummary.Thing.Door -> if (n == 1) "Tür" else "Türen"
        FixSummary.Thing.Window -> "Fenster"
        FixSummary.Thing.BalconyDoor -> if (n == 1) "Fenstertür" else "Fenstertüren"
        FixSummary.Thing.Passage -> if (n == 1) "Durchgang" else "Durchgänge"
        FixSummary.Thing.Niche -> if (n == 1) "Nische" else "Nischen"
        FixSummary.Thing.Radiator -> "Heizkörper"
        FixSummary.Thing.WaterPoint -> if (n == 1) "Wasseranschluss" else "Wasseranschlüsse"
        FixSummary.Thing.Outlet -> if (n == 1) "Steckdose" else "Steckdosen"
        FixSummary.Thing.Switch -> "Schalter"
        FixSummary.Thing.Light -> if (n == 1) "Lampe" else "Lampen"
        FixSummary.Thing.Furniture -> if (n == 1) "Möbelstück" else "Möbel"
        FixSummary.Thing.Room -> if (n == 1) "Raum" else "Räume"
    }

    override fun elenco(parti: List<String>): String =
        if (parti.size < 2) parti.joinToString() else parti.dropLast(1).joinToString(", ") + " und " + parti.last()

    override fun fatto(aggiunto: String?, tolto: String?, spostato: String?, cambiato: String?): String =
        listOfNotNull(aggiunto?.let { "hinzugefügt: $it" }, tolto?.let { "entfernt: $it" }, spostato?.let { "verschoben: $it" }, cambiato?.let { "geändert: $it" })
            .joinToString("; ")
    override val togliDallaPianta: String get() = "Ausblenden"
    override val rimettiSullaPianta: String get() = "Wieder anzeigen"
    override val togliTutti: String get() = "Alle ausblenden"
    override val rimettiTutti: String get() = "Alle wieder anzeigen"
    override val toltoDallaPianta: String get() = "ausgeblendet"
    override fun problemiQui(n: Int): String = if (n == 1) "Ein Problem hier" else "$n Probleme hier"
    override val condividi: String get() = "Teilen"
    override fun oggetto(progetto: String?): String = "Grundrissprüfung" + (progetto?.let { " · $it" } ?: "")
    override fun muriIncrociati(a: String): String = "$a: die Wände kreuzen sich und der Umriss schneidet sich selbst, dadurch werden Fläche und 3D falsch. Verschiebe die Ecken."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a und $b überlappen sich um $m2 m²: dieser Teil zählt doppelt. Verschiebe eine Wand oder eine Ecke."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a und $b liegen fast aneinander: zwischen den Wänden bleibt ein Spalt von $cm cm. Meist ist die Wand gemeinsam: ziehe diese Wand auf die des anderen Raums."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a und $b haben zwei Wände, die sich um $cm cm überlappen, statt einer gemeinsamen Wand: ziehe diese Wand auf die des anderen Raums."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Zwei Öffnungen an derselben Stelle ($cosa, $stanze): vielleicht doppelt gesetzt. Entferne eine."
    override fun muroZero(a: String, unisci: String): String = "$a: zwei Ecken übereinander (eine Wand mit 0 cm Länge). Bei ausgewählter Wand entfernt „$unisci“ sie."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa mit $cm cm an einer Wand mit $muro cm, das passt nicht."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa reicht bis $cm cm über dem Boden, aber dort ist die Wand nur $muro cm hoch."
    override fun suMuroTolto(a: String, cosa: String): String = "$a: $cosa an einer entfernten Wand (offene Seite), die es nicht gibt."
    override fun senzaPorta(a: String): String = "$a hat weder Türen noch Durchgänge: wo kommt man hinein?"
    override val nessunaPorta: String get() = "Im Geschoss gibt es keine einzige Tür: füge Türen und Durchgänge zwischen den Räumen hinzu."
    override val senzaIngresso: String get() = "Keine Tür führt nach draußen: fehlt die Haustür?"
    override fun finestraInterna(a: String, b: String): String = "$a: das Fenster geht auf $b, nicht nach draußen. Ist das gewollt?"
    override fun piccolissima(a: String, m2: String): String = "$a ist winzig ($m2 m²): ist das gewollt oder ein übrig gebliebenes Stück?"
    override val scalaSenzaPiano: String get() = "Die Treppe führt nach oben, aber darüber gibt es kein Geschoss: füge das Geschoss darüber hinzu oder entferne die Treppe."
    override fun scalaFuori(piano: String): String = "Die Treppe endet außerhalb der Räume des Geschosses darüber ($piano)."
    override fun senzaCalorifero(a: String): String = "$a: kein Heizkörper, während andere Räume einen haben. Wenn anders geheizt wird, ist das in Ordnung."
    override fun senzaAcqua(a: String): String = "$a: kein Wasseranschluss, während andere Räume einen haben."
    override fun scalaRipida(alzata: String, pedata: String?): String =
        "Steile Treppe: Steigungen von $alzata cm" + (pedata?.let { " und Auftritte von $it cm" } ?: "") +
            ". Bequem ist meist eine Steigung von höchstens 18-20 cm, und zwei Steigungen plus ein Auftritt ergeben 62-64 cm: füge Steigungen hinzu oder wähle eine flachere Neigung (Info der Treppe → Stufen)."
    override fun scalaStretta(larga: String): String =
        "Treppe $larga cm breit: im Haus braucht es meist mindestens 80 (das sagt die Bauordnung der Gemeinde)."
    override fun scalaTestaBassaTetto(h: String): String =
        "Über der Treppe bleiben $h cm bis zur Decke: darüber liegt kein Raum des Geschosses darüber, sondern das Dach, und man stößt sich den Kopf (mindestens 200 nötig). Vergrößere den Raum darüber bis über die Treppe oder verschiebe die Treppe."
    override fun scalaTestaBassa(h: String): String =
        "Über der Treppe bleiben $h cm bis zur Decke des Geschosses darüber: man stößt sich den Kopf (mindestens 200 nötig). Lass mehr Stufen offen (Info der Treppe → Darüber)."
    override fun anteSiUrtano(stanze: String, cosa: String, cosa2: String): String =
        "$stanze: zwei Flügel stoßen beim Öffnen aneinander ($cosa und $cosa2). Ändere die Anschlagseite oder die Öffnungsrichtung."
    override fun mobileNelMuro(cosa: String, cm: String): String = "$cosa ragt $cm cm in eine Wand: verschiebe es oder mach es kleiner."
    override fun mobileAttraversaMuro(cosa: String): String = "$cosa geht durch eine Wand: verschiebe es in den Raum oder mach es kleiner."
    override fun antaUrta(a: String, cosa: String, cosa2: String): String =
        "$a: beim Öffnen stößt der Flügel an $cosa2 ($cosa). Verschiebe $cosa2 oder ändere die Anschlagseite oder die Öffnungsrichtung."
    override fun antaUrtaMuro(a: String, cosa: String): String =
        "$a: der Flügel öffnet sich nicht ganz, er stößt an eine Wand oder einen Pfeiler ($cosa). Verschiebe die Öffnung oder ändere die Anschlagseite oder die Öffnungsrichtung."
    override fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String, minimo: String, apribili: Boolean): String =
        "$a: ${if (apribili) "öffenbare " else ""}Fenster mit $fin m² für $pav m² Boden ($frazione). Ein Wohnraum braucht mindestens $minimo, also $serve m²."
    override fun pocaAria(a: String, fin: String, pav: String, minimo: String, serve: String): String =
        "$a: der öffenbare Teil der Fenster hat $fin m² für $pav m² Boden. Für die Luft braucht es mindestens $minimo, also $serve m²."
    override fun senzaFinestre(a: String, apribili: Boolean, serve: String?, minimo: String?): String =
        "$a hat keine ${if (apribili) "öffenbaren " else ""}Fenster nach außen: ein Wohnraum braucht direktes Licht und frische Luft" +
            if (serve != null) ", mit mindestens $serve m² Fensterfläche ($minimo der Bodenfläche)." else "."
    override val nessunaFinestra: String get() = "Im Geschoss gibt es kein einziges Fenster: füge welche hinzu, dann werden hier Licht und Luft der Räume geprüft."
    override fun soffittoBasso(a: String, h: String, min: String): String = "$a: Raumhöhe $h m; in Wohnräumen muss sie mindestens $min m betragen."
    override fun soffittoBassoServizi(a: String, h: String, min: String): String = "$a: Raumhöhe $h m; in Bädern und Fluren muss sie mindestens $min m betragen."
    override fun mansardaBassa(a: String, h: String): String =
        "$a: schräge Decke, im Mittel $h m hoch. Zum Wohnen verlangen die regionalen Gesetze für Dachgeschosse meist mindestens 2,40 m im Mittel (außerhalb von Dachgeschossen 2,70 m)."
    override fun cameraPiccola(a: String, m2: String, min: String, perDue: String?): String =
        "$a: $m2 m²; ein Schlafzimmer muss mindestens $min haben${perDue?.let { " ($it für zwei Personen)" } ?: ""}."
    override fun cameraPerDuePiccola(a: String, m2: String, min: String): String = "$a: $m2 m² mit Betten für zwei Personen; ein Zimmer für zwei muss mindestens $min haben."
    override fun soggiornoPiccolo(a: String, m2: String, min: String): String = "$a: $m2 m²; das Wohnzimmer muss mindestens $min haben."
    override fun bagnoSenzaFinestra(a: String): String =
        "$a: kein öffenbares Fenster nach außen; das Bad braucht eine mechanische Lüftung (und keine Geräte mit offener Flamme)."
    override fun bagnoSuCucina(bagno: String, stanza: String): String =
        "$bagno öffnet sich direkt zu $stanza: viele Hygienevorschriften der Gemeinden verlangen einen Vorraum oder eine Diele zwischen Bad und Küche oder Wohnzimmer."
    override fun bagnoIncompleto(a: String, manca: String, servono: String): String =
        "$a: ohne $manca. Mindestens ein Bad im Haus muss $servono haben."
    override val vaso: String get() = "WC"
    override val bide: String get() = "Bidet"
    override val vascaODoccia: String get() = "Badewanne oder Dusche"
    override val lavabo: String get() = "Waschbecken"
    override fun casaPiccola(m2: String, persone: Int, min: String): String =
        "Das Haus hat $m2 m² für $persone Personen (nach den Betten gezählt); nötig sind mindestens $min (14 m² pro Kopf für die ersten 4, 10 für die weiteren)."
    override fun monolocalePiccolo(m2: String, min: String, perDue: Boolean): String =
        "Einzimmerwohnung mit $m2 m²: nötig sind mindestens $min für ${if (perDue) "zwei Personen" else "eine Person"}, Bad inklusive."
    override fun impiantoScarso(a: String, prese: String, luci: String, minPrese: String, minLuci: String): String =
        "$a: $prese und $luci. Für die elektrische Grundausstattung (CEI 64-8, Stufe 1) braucht es mindestens $minPrese und $minLuci."
    override fun puntiPresa(n: Int): String = when (n) {
        0 -> "keine Steckdose"
        1 -> "1 Steckdose"
        else -> "$n Steckdosen"
    }
    override fun puntiLuce(n: Int): String = when (n) {
        0 -> "kein Lichtauslass"
        1 -> "1 Lichtauslass"
        else -> "$n Lichtauslässe"
    }
}
