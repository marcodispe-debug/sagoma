package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiNote

object TestiNoteEs : TestiNote() {
    override val piccolo: String get() = "Pequeño"
    override val medio: String get() = "Mediano"
    override val grande: String get() = "Grande"
    override val quotaToccaPrimoPunto: String get() = "↔ Cota: toca el primer punto (se ajusta a esquinas y caras de las paredes)"
    override val quotaToccaSecondoPunto: String get() = "↔ Cota: toca el segundo punto"
    override val tTestoToccaDove: String get() = "T Texto: toca donde escribirlo"
    override val nuovoTesto: String get() = "Texto nuevo"
    override val testoTesto: String get() = "Texto"
    override val dimensione: String get() = "Tamaño"
    override val metti: String get() = "Colocar"
    override fun misuraCmAggiornaSpostando(p1: Any?): String = "Medida: $p1 cm (se actualiza al mover los extremos)."
    override val scrittaPostoMisuraVuoto: String get() = "Texto en lugar de la medida (vacío = la medida)"
    override val distanzaLineaCm: String get() = "Distancia de la línea (cm)"
    override val dallAltraParte: String get() = "⇅ Al otro lado"
    override val trascinaLineaAllontanarlaEstremita: String get() = "Arrastra la línea para alejarla, los extremos para medir otra cosa: se ajustan a esquinas y caras de las paredes."
    override val verticale: String get() = "↺ Vertical"
    override val orizzontale: String get() = "↻ Horizontal"
    override val freccia: String get() = "↘ Flecha"
    override val togliFreccia: String get() = "Quitar flecha"
    override val trascinaTestoSpostarlo: String get() = "Arrastra el texto para moverlo"
    override val trascinaPuntaFrecciaIndicare: String get() = "; arrastra la punta de la flecha para señalar un punto."
    override val demolire: String get() = "A demoler"
    override val nuovo: String get() = "Nuevo"
    override val livelli: String get() = "Capas"
    override val nascostoNonVedePianta: String get() = "Oculto: no se ve en el plano, en el PDF ni en la imagen. Bloqueado: se ve pero no se puede tocar. Habitaciones, paredes, puertas y ventanas siempre están activas."
    override val visibile: String get() = "Visible"
    override val bloccato: String get() = "Bloqueado"
    override val fatto: String get() = "Hecho"
}
