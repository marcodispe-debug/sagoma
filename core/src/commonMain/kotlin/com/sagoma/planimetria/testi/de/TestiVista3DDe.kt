package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DDe : TestiVista3D() {
    override val luciAccese: String get() = "💡 Licht an"
    override val luciSpente: String get() = "💡 Licht aus"
    override val luciAuto: String get() = "💡 Licht: automatisch"
    override fun luminositaLuci(pct: Int): String = "💡 Helligkeit der Lampen: $pct %"
    override val vistaDallAlto: String get() = "🏠 Ansicht von oben"
    override val cammina: String get() = "🚶 Gehen"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Tageszeit: $h:$p2"
    override val soleSegueBussola: String get() =
        "Die Sonne folgt der Stadt, dem Tag und der Nordrichtung, die du mit dem Kompass 🧭 auf dem Grundriss gewählt hast; das Licht fällt durch die Fenster. " +
            "Wenn es dunkel wird, gehen die Lampen im Haus an."
    override val nordCitta: String get() = "🧭 Norden und Stadt"
    override val bussola: String get() = "Kompass: ziehen, um das N nach dem echten Norden zu drehen, tippen, um Norden und Stadt zu wählen"
    override val spiegazioneNord: String get() =
        "Drehe den Pfeil, bis das N dorthin zeigt, wo im Verhältnis zu deinem Grundriss der echte Norden liegt. Der Grundriss bleibt stehen: im 3D bewegt sich nur die Sonne. Das ist freiwillig: ohne Angabe ist Norden oben und die Stadt ist Rom."
    override val nordGradi: String get() = "Norden (Grad im Uhrzeigersinn von oben)"
    override val nordInAlto: String get() = "Norden oben"
    override val citta: String get() = "Stadt"
    override val cercaCitta: String get() = "Stadt suchen"
    override val nessunaCitta: String get() = "Keine (gilt Rom)"
    override fun cittaScelta(nome: Any?): String = "Stadt: $nome"
    override val giorno: String get() = "Tag"
    override val oggi: String get() = "Heute"
    override val marzo21: String get() = "21. März"
    override val giugno21: String get() = "21. Juni"
    override val settembre21: String get() = "21. September"
    override val dicembre21: String get() = "21. Dezember"
    override val foto4k: String get() = "📷 4K-Foto"
    override val fotoInPreparazione: String get() = "📷 4K-Foto wird vorbereitet: die detaillierteren Materialien werden geladen, das kann eine Minute dauern…"
    override val fotoSalvata: String get() = "4K-Foto gespeichert"
    override val fotoSalvataMarchio: String get() = "4K-Foto gespeichert, mit dem Sagoma-Logo"
    override val fotoMaterialiMenoDefiniti: String get() = "Foto gespeichert. Einige Materialien gab es noch nicht in 4K: auf dem Foto sind sie etwas weniger detailliert"
    override val fotoNonRiuscita: String get() = "Das Foto ist nicht gelungen: der Grafikspeicher ist vielleicht voll, schließe andere Programme und versuche es erneut"
}
