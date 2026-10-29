package com.sagoma.planimetria.testi

/** Scelta degli arredi, fonti e licenze dei modelli. */
open class TestiArredi {
    open val arredi: String get() = "🛋 Arredi"
    open fun arredi2(size: Any?): String = "Arredi ($size)"
    open val cercaDivanoLavelloLampada: String get() = "Cerca: divano, lavello, lampada…"
    open val nessunArredoQuestoNome: String get() = "Nessun arredo con questo nome."
    open val fontiLicenzeModelli: String get() = "Fonti e licenze dei modelli"
    open fun testoTesto(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3"
    open val fontiLicenze: String get() = "Fonti e licenze"
    open val modelli3dArrediVengono: String get() =
        "I modelli 3D degli arredi vengono da Poly Haven (polyhaven.com, licenza CC0: pubblico dominio), dalle " +
            "librerie gratuite di Sweet Home 3D (sweethome3d.com) e da Sketchfab (sketchfab.com) tramite la raccolta " +
            "Objaverse. Quelli con licenza CC-BY sono elencati uno per uno con autore e fonte. I materiali dei pavimenti e " +
            "delle pareti e le luci ambiente vengono da ambientCG e Poly Haven (CC0). Grazie a tutti gli autori."
    open val modelliLicenzaCcBy: String get() = "Modelli con licenza CC-BY (attribuzione)"
    open val libreriaScopiaSweetHome: String get() =
        "Libreria Scopia di Sweet Home 3D: © Space Mushrooms e Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
            "licenza Creative Commons Attribuzione 3.0. Modelli di Sketchfab: licenza Creative Commons Attribuzione 4.0."
    open fun di(label: Any?, p2: Any?, license: Any?, p4: Any?): String = "“$label” di $p2 · $license · $p4"
    open val modelliPubblicoDominioCc0: String get() = "Modelli di pubblico dominio (CC0)"
    open fun autoreLicenza(p1: Any?, p2: Any?): String = "Autore: $p1 · Licenza: $p2"
    open val autoreSconosciuto: String get() = "autore sconosciuto"
    open val puntoLuceSemplice: String get() = "Punto luce semplice"
}
