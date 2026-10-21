package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiFoto

object TestiFotoEs : TestiFoto() {
    override val impossibileLeggereFoto: String get() = "No se pudo leer la foto"
    override val nonSpazioSalvare: String get() = "No hay espacio para guardar la foto"
    override val fotoSopralluogoRiguardareMentre: String get() =
        "Fotos de la visita para consultar mientras dibujas: dónde está el enchufe, hacia dónde abre la puerta, el metro contra la pared. " +
            "Se quedan en el proyecto; no cambian el plano ni el 3D."
    override val scatta: String get() = "📷 Hacer foto"
    override val galleria: String get() = "🖼 Desde la galería"
    override val aggiungiFoto: String get() = "📷 Añadir foto"
    override val preparoFoto: String get() = "Preparando la foto…"
    override val fotoStanza: String get() = "Foto de la habitación"
    override val toccaAggiungereNota: String get() = "Toca para añadir una nota"
    override val aproFoto: String get() = "Abriendo la foto…"
    override val notaEsempioPresa30: String get() = "Nota (por ejemplo: enchufe a 30 cm del suelo)"
    override val togliFoto: String get() = "Quitar foto"
    override val senzaPermessoFotocamera: String get() = "Sin permiso de cámara puedes elegir una foto de la galería"
    override val impossibileAprireFotocamera: String get() = "No se puede abrir la cámara"
}
