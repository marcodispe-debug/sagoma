package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiArredi

object TestiArrediFr : TestiArredi() {
    override val arredi: String get() = "🛋 Mobilier"
    override fun arredi2(size: Any?): String = "Mobilier ($size)"
    override val cercaDivanoLavelloLampada: String get() = "Rechercher : canapé, évier, lampe…"
    override val nessunArredoQuestoNome: String get() = "Aucun meuble avec ce nom."
    override val fontiLicenzeModelli: String get() = "Sources et licences des modèles"
    override fun testoTesto(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3"
    override val fontiLicenze: String get() = "Sources et licences"
    override val modelli3dArrediVengono: String get() =
        "Les modèles 3D des meubles viennent de Poly Haven (polyhaven.com, licence CC0 : domaine public), des " +
            "bibliothèques libres de Sweet Home 3D (sweethome3d.com) et de Sketchfab (sketchfab.com) via la " +
            "collection Objaverse. Ceux sous licence CC-BY sont listés un par un avec auteur et source. Les matériaux " +
            "des sols et des murs et les lumières d'ambiance viennent d'ambientCG et de Poly Haven (CC0). Merci à tous les auteurs."
    override val modelliLicenzaCcBy: String get() = "Modèles sous licence CC-BY (attribution)"
    override val libreriaScopiaSweetHome: String get() =
        "Bibliothèque Scopia de Sweet Home 3D : © Space Mushrooms et Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
            "licence Creative Commons Attribution 3.0. Modèles Sketchfab : licence Creative Commons Attribution 4.0."
    override fun di(label: Any?, p2: Any?, license: Any?, p4: Any?): String = "« $label » de $p2 · $license · $p4"
    override val modelliPubblicoDominioCc0: String get() = "Modèles du domaine public (CC0)"
    override fun autoreLicenza(p1: Any?, p2: Any?): String = "Auteur : $p1 · Licence : $p2"
    override val autoreSconosciuto: String get() = "auteur inconnu"
    override fun modelliLuce(nome: Any?, n: Any?): String = "$nome ($n modèles)"
    override val puntoLuceSemplice: String get() = "Point lumineux simple"
    override val sceltaLuce: String get() =
        "Le point lumineux simple est celui de toujours : il se place en touchant le plan. Les autres sont les modèles 3D du mobilier."
}
