package com.sagoma.planimetria.testi.de

import com.sagoma.planimetria.testi.TestiArredi

object TestiArrediDe : TestiArredi() {
    override val arredi: String get() = "🛋 Möbel"
    override fun arredi2(size: Any?): String = "Möbel ($size)"
    override val cercaDivanoLavelloLampada: String get() = "Suchen: Sofa, Spüle, Lampe…"
    override val nessunArredoQuestoNome: String get() = "Kein Möbelstück mit diesem Namen."
    override val fontiLicenzeModelli: String get() = "Quellen und Lizenzen der Modelle"
    override fun testoTesto(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3"
    override val fontiLicenze: String get() = "Quellen und Lizenzen"
    override val modelli3dArrediVengono: String get() =
        "Die 3D-Modelle der Möbel stammen von Poly Haven (polyhaven.com, Lizenz CC0: gemeinfrei), aus den " +
            "kostenlosen Bibliotheken von Sweet Home 3D (sweethome3d.com) und von Sketchfab (sketchfab.com) über die " +
            "Sammlung Objaverse. Modelle mit CC-BY-Lizenz sind einzeln mit Autor und Quelle aufgeführt. Die Materialien " +
            "für Böden und Wände und das Umgebungslicht stammen von ambientCG und Poly Haven (CC0). Danke an alle Autoren."
    override val modelliLicenzaCcBy: String get() = "Modelle mit CC-BY-Lizenz (Namensnennung)"
    override val libreriaScopiaSweetHome: String get() =
        "Scopia-Bibliothek von Sweet Home 3D: © Space Mushrooms und Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
            "Lizenz Creative Commons Namensnennung 3.0. Sketchfab-Modelle: Lizenz Creative Commons Namensnennung 4.0."
    override fun di(label: Any?, p2: Any?, license: Any?, p4: Any?): String = "„$label“ von $p2 · $license · $p4"
    override val modelliPubblicoDominioCc0: String get() = "Gemeinfreie Modelle (CC0)"
    override fun autoreLicenza(p1: Any?, p2: Any?): String = "Autor: $p1 · Lizenz: $p2"
    override val autoreSconosciuto: String get() = "Autor unbekannt"
    override val puntoLuceSemplice: String get() = "Einfacher Lichtpunkt"
}
