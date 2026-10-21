package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiArredi

object TestiArrediEs : TestiArredi() {
    override val arredi: String get() = "🛋 Muebles"
    override fun arredi2(size: Any?): String = "Muebles ($size)"
    override val cercaDivanoLavelloLampada: String get() = "Buscar: sofá, fregadero, lámpara…"
    override val nessunArredoQuestoNome: String get() = "Ningún mueble con este nombre."
    override val fontiLicenzeModelli: String get() = "Fuentes y licencias de los modelos"
    override fun testoTesto(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3"
    override val fontiLicenze: String get() = "Fuentes y licencias"
    override val modelli3dArrediVengono: String get() =
        "Los modelos 3D de los muebles vienen de Poly Haven (polyhaven.com, licencia CC0: dominio público), de las " +
            "bibliotecas libres de Sweet Home 3D (sweethome3d.com) y de Sketchfab (sketchfab.com) a través de la " +
            "colección Objaverse. Los que tienen licencia CC-BY aparecen uno por uno con autor y fuente. Los materiales " +
            "de suelos y paredes y las luces de ambiente vienen de ambientCG y Poly Haven (CC0). Gracias a todos los autores."
    override val modelliLicenzaCcBy: String get() = "Modelos con licencia CC-BY (atribución)"
    override val libreriaScopiaSweetHome: String get() =
        "Biblioteca Scopia de Sweet Home 3D: © Space Mushrooms y Scopia Visual Interfaces Systems, s.l. (scopia.es), " +
            "licencia Creative Commons Atribución 3.0. Modelos de Sketchfab: licencia Creative Commons Atribución 4.0."
    override fun di(label: Any?, p2: Any?, license: Any?, p4: Any?): String = "«$label» de $p2 · $license · $p4"
    override val modelliPubblicoDominioCc0: String get() = "Modelos de dominio público (CC0)"
    override fun autoreLicenza(p1: Any?, p2: Any?): String = "Autor: $p1 · Licencia: $p2"
    override val autoreSconosciuto: String get() = "autor desconocido"
}
