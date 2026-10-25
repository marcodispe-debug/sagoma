package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiSfondo

object TestiSfondoEs : TestiSfondo() {
    override val impossibileLeggereFile: String get() = "No se pudo leer el archivo"
    override val nonSpazioSalvareSfondo: String get() = "No hay espacio para guardar el fondo: prueba con una imagen más pequeña"
    override val ricalcaPiantina: String get() = "Calcar un plano"
    override val piantaSfondo: String get() = "Plano de fondo"
    override fun guidaRicalcoPasso(number: Any?, total: Any?): String = "Guía de calcado · paso $number de $total"
    override val caricaPiantinaHaiFoto: String get() = "Carga el plano que tengas (foto, escaneo o PDF del catastro o del proyecto)."
    override val caricaPiantinaHaiImmagine: String get() = "Carga el plano que tengas como imagen (foto, escaneo, JPG o PNG). Si es un PDF, guarda su página como imagen."
    override val tocchiDueEstremiMisura: String get() =
        "Luego toca los dos extremos de una medida que conozcas y escribe cuánto mide: el plano queda a escala. " +
            "Por último elige cómo dibujar las habitaciones sobre el plano: con una forma o con «Pared a pared», esquina tras esquina."
    override val mettiSottoDisegnoPianta: String get() =
        "Pon un plano existente debajo del dibujo (foto, escaneo o PDF del catastro o del proyecto), " +
            "calíbralo con una medida que conozcas y calca las habitaciones encima."
    override val fotoImmagine: String get() = "Foto / imagen"
    override val altraImmagine: String get() = "Otra imagen"
    override val pdf: String get() = "PDF"
    override val caricamento: String get() = "Cargando…"
    override val visibile: String get() = "Visible"
    override fun trasparenza(p1: Any?): String = "Transparencia: $p1 %"
    override fun scalaAttuale1Px(p1: Any?, p2: Any?, p3: Any?): String = "Escala actual: 1 px = $p1 cm · imagen $p2 × $p3 m"
    override val tara: String get() = "📏 Calibrar"
    override val sposta: String get() = "✋ Mover"
    override val togliSfondo: String get() = "Quitar el fondo"
    override val trascinaDitoSpostarePianta: String get() = "Arrastra con un dedo para mover el plano de fondo debajo del dibujo."
    override val taraturaToccaInizioMisura: String get() = "Calibración: toca el inicio de una medida que conozcas (p. ej. una pared acotada)."
    override val oraToccaFineMisura: String get() = "Ahora toca el final de la medida. Amplía con dos dedos para ser preciso."
    override val scriviMisuraRealeB: String get() = "Escribe la longitud real entre A y B."
    override val misuraReale: String get() = "Longitud real"
    override val quantoLungaDavveroDistanza: String get() = "¿Cuánto mide de verdad la distancia entre A y B?"
    override val piuMisuraLungaPiu: String get() = "Cuanto más larga sea la medida, más precisa será la calibración."
    override val tara2: String get() = "Calibrar"
    override val rifai: String get() = "Rehacer"
    override val leggiDaSola: String get() = "Leer el plano por mí"
    override val leggiDaSolaSpiega: String get() = "Nuevo y gratis: carga el plano y Sagoma dibuja por ti las habitaciones, puertas y ventanas. Después revisa y corrige."
    override val letturaInCorso: String get() = "Leyendo el plano… puede tardar un minuto."
    override fun piantinaLetta(stanze: Int): String = "Plano leído: $stanze habitaciones. Revisa las medidas y corrige donde haga falta."
    override val senzaMisureScritte: String get() = "No hay medidas escritas en el plano: la escala es aproximada, revísala."
    override val letturaNonRiuscita: String get() = "No se pudo leer el plano: cálcalo a mano."
    override val letturaNonRaggiungibile: String get() = "El servicio de lectura no responde (hace falta internet): calca el plano a mano."
    override val troppeLetture: String get() = "Hoy ya has leído muchos planos: vuelve a intentarlo mañana o cálcalo a mano."
    override val nonUnaPiantina: String get() = "La imagen no parece un plano: cálcalo a mano o prueba otra imagen."
}
