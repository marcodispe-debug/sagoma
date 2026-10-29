package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiArredi

object TestiArrediEn : TestiArredi() {
    override val arredi: String get() = "🛋 Furniture"
    override fun arredi2(size: Any?): String = "Furniture ($size)"
    override val cercaDivanoLavelloLampada: String get() = "Search: sofa, sink, lamp…"
    override val nessunArredoQuestoNome: String get() = "No furniture with this name."
    override val fontiLicenzeModelli: String get() = "Model sources and licences"
    override fun testoTesto(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3"
    override val fontiLicenze: String get() = "Sources and licences"
    override val modelli3dArrediVengono: String get() =
        "The 3D furniture models come from Poly Haven (polyhaven.com, CC0 licence: public domain), from the " +
            "free Sweet Home 3D libraries (sweethome3d.com) and from Sketchfab (sketchfab.com) through the " +
            "Objaverse collection. Those under a CC-BY licence are listed one by one with author and source. The floor and " +
            "wall materials and the ambient lights come from ambientCG and Poly Haven (CC0). Thanks to all the authors."
    override val modelliLicenzaCcBy: String get() = "Models under a CC-BY licence (attribution)"
    override val libreriaScopiaSweetHome: String get() =
        "Scopia library of Sweet Home 3D: © Space Mushrooms and Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
            "Creative Commons Attribution 3.0 licence. Sketchfab models: Creative Commons Attribution 4.0 licence."
    override fun di(label: Any?, p2: Any?, license: Any?, p4: Any?): String = "“$label” by $p2 · $license · $p4"
    override val modelliPubblicoDominioCc0: String get() = "Public domain models (CC0)"
    override fun autoreLicenza(p1: Any?, p2: Any?): String = "Author: $p1 · Licence: $p2"
    override val autoreSconosciuto: String get() = "unknown author"
    override fun modelliLuce(nome: Any?, n: Any?): String = "$nome ($n models)"
    override val puntoLuceSemplice: String get() = "Simple light point"
    override val sceltaLuce: String get() =
        "The simple light point is the usual one: you place it by tapping the plan. The others are 3D furniture models."
}
