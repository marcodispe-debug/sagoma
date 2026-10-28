package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloEs : TestiControllo() {
    override val voceMenu: String get() = "🔍 Revisar el plano…"
    override val titolo: String get() = "Revisar el plano"
    override val nessunProblema: String get() = "No se encontraron problemas."
    override val cosaControlla: String get() =
        "Busca habitaciones superpuestas o casi pegadas, paredes que se cruzan, puertas y ventanas duplicadas, más anchas o más altas que la pared, " +
            "habitaciones sin puerta, hojas que chocan al abrirse, la puerta de entrada, las escaleras, las instalaciones y los mínimos legales de las habitaciones (ventanas, alturas, metros cuadrados)."
    override val spiega: String get() = "Pueden estropear superficies, mediciones y 3D. «Mostrar» lleva allí y lo selecciona."
    override val daCorreggere: String get() = "Para corregir"
    override val daControllare: String get() = "Para revisar"
    override val daNorme: String get() = "Normas para la vivienda (Italia)"
    override val spiegaNorme: String get() =
        "Mínimos del decreto italiano del 5 de julio de 1975 para las estancias habitables (dormitorios, salón, cocina, estudio) y los baños; la normativa " +
            "del municipio puede pedir más. Las ventanas cuentan si se abren y dan al exterior, medidas en el hueco de la pared. Si colocas enchufes, " +
            "también los puntos de enchufe y de luz de una instalación eléctrica básica (CEI 64-8, nivel 1)."
    override fun nome(n: String): String = "«$n»"
    override val mostra: String get() = "Mostrar"
    override val chiudi: String get() = "Cerrar"
    override fun troppi(n: Int): String = "Solo los primeros $n: corrígelos y revisa de nuevo."
    override val primaDiEsportareTitolo: String get() = "Antes de exportar"
    override fun primaDiEsportare(n: Int): String =
        "El plano tiene ${if (n == 1) "un error" else "$n errores"} de dibujo (⚠): medidas, superficies y mediciones podrían estar mal también en el archivo exportado."
    override val controlla: String get() = "Revisar"
    override val esportaLoStesso: String get() = "Exportar igualmente"
    override val condividi: String get() = "Compartir"
    override fun oggetto(progetto: String?): String = "Revisión del plano" + (progetto?.let { " · $it" } ?: "")
    override fun muriIncrociati(a: String): String = "$a: las paredes se cruzan y el contorno se corta a sí mismo, así que la superficie y el 3D salen mal. Mueve las esquinas."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a y $b se superponen $m2 m²: esa parte cuenta dos veces. Mueve una pared o una esquina."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a y $b están casi pegadas: entre las paredes queda una rendija de $cm cm. Normalmente la pared es compartida: arrastra esta pared sobre la de la otra habitación."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a y $b tienen dos paredes que se solapan $cm cm en lugar de una pared compartida: arrastra esta pared sobre la de la otra habitación."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Dos aberturas en el mismo punto ($cosa, $stanze): quizá puesta dos veces. Quita una."
    override fun muroZero(a: String, unisci: String): String = "$a: dos esquinas una encima de otra (una pared de 0 cm). Con la pared seleccionada, «$unisci» la quita."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa de $cm cm en una pared de $muro cm, no cabe."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa llega a $cm cm del suelo, pero allí la pared mide $muro cm."
    override fun suMuroTolto(a: String, cosa: String): String = "$a: $cosa en una pared quitada (lado abierto), que no existe."
    override fun senzaPorta(a: String): String = "$a no tiene puertas ni pasos: ¿por dónde se entra?"
    override val nessunaPorta: String get() = "No hay ninguna puerta en esta planta: añade puertas y pasos entre las habitaciones."
    override val senzaIngresso: String get() = "Ninguna puerta da al exterior: ¿falta la puerta de entrada?"
    override fun finestraInterna(a: String, b: String): String = "$a: la ventana da a $b, no al exterior. ¿Es intencionado?"
    override fun piccolissima(a: String, m2: String): String = "$a es diminuta ($m2 m²): ¿es intencionado o un trozo olvidado?"
    override val scalaSenzaPiano: String get() = "La escalera sube, pero arriba no hay ninguna planta: añade la planta de arriba o quita la escalera."
    override fun scalaFuori(piano: String): String = "La escalera llega fuera de las habitaciones de la planta de arriba ($piano)."
    override fun senzaCalorifero(a: String): String = "$a: ningún radiador, mientras otras habitaciones lo tienen. Si se calienta de otra forma, está bien."
    override fun senzaAcqua(a: String): String = "$a: ningún punto de agua, mientras otras habitaciones lo tienen."
    override fun scalaRipida(alzata: String, pedata: String?): String =
        "Escalera empinada: contrahuellas de $alzata cm" + (pedata?.let { " y huellas de $it cm" } ?: "") +
            ". Para subir cómodos la contrahuella suele ser de 18-20 cm como mucho y dos contrahuellas más una huella suman 62-64 cm: añade peldaños o elige una pendiente más suave (Info de la escalera → Peldaños)."
    override fun scalaStretta(larga: String): String =
        "Escalera de $larga cm de ancho: dentro de una vivienda suelen hacer falta al menos 80 (lo fija la normativa urbanística local)."
    override fun scalaTestaBassa(h: String): String =
        "Encima de la escalera quedan $h cm hasta el forjado de la planta de arriba: te das con la cabeza (hacen falta al menos 200). Deja más peldaños descubiertos (Info de la escalera → Piso sup.)."
    override fun anteSiUrtano(stanze: String, cosa: String, cosa2: String): String =
        "$stanze: dos hojas chocan al abrirse ($cosa y $cosa2). Cambia el lado de las bisagras o el sentido de apertura."
    override fun mobileNelMuro(cosa: String, cm: String): String = "$cosa entra $cm cm en una pared: muévelo o hazlo más pequeño."
    override fun mobileAttraversaMuro(cosa: String): String = "$cosa atraviesa una pared: muévelo dentro de la habitación o hazlo más pequeño."
    override fun antaUrta(a: String, cosa: String, cosa2: String): String =
        "$a: al abrirse, la hoja choca con $cosa2 ($cosa). Mueve $cosa2 o cambia el lado de las bisagras o el sentido de apertura."
    override fun antaUrtaMuro(a: String, cosa: String): String =
        "$a: la hoja no se abre del todo, choca con una pared o un pilar ($cosa). Mueve la abertura o cambia el lado de las bisagras o el sentido de apertura."
    override fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String): String =
        "$a: ventanas practicables de $fin m² para $pav m² de suelo ($frazione). Una estancia habitable necesita al menos 1/8, es decir $serve m²."
    override fun senzaFinestre(a: String, serve: String): String =
        "$a no tiene ventanas practicables al exterior: una estancia habitable necesita luz y aire directos, con al menos $serve m² de ventanas (1/8 del suelo)."
    override val nessunaFinestra: String get() = "No hay ninguna ventana en esta planta: añádelas y aquí se comprobarán la luz y el aire de las habitaciones."
    override fun soffittoBasso(a: String, h: String): String = "$a: techo a $h m; las estancias habitables necesitan al menos 2,70 m."
    override fun soffittoBassoServizi(a: String, h: String): String = "$a: techo a $h m; baños y pasillos necesitan al menos 2,40 m."
    override fun mansardaBassa(a: String, h: String): String =
        "$a: techo inclinado con una altura media de $h m. Para vivir en una buhardilla, las normas regionales suelen pedir una media de al menos 2,40 m (2,70 m fuera de las buhardillas)."
    override fun cameraPiccola(a: String, m2: String): String = "$a: $m2 m²; un dormitorio necesita al menos 9 (14 para dos personas)."
    override fun cameraPerDuePiccola(a: String, m2: String): String = "$a: $m2 m² con camas para dos personas; un dormitorio para dos necesita al menos 14."
    override fun soggiornoPiccolo(a: String, m2: String): String = "$a: $m2 m²; el salón necesita al menos 14."
    override fun bagnoSenzaFinestra(a: String): String =
        "$a: ninguna ventana practicable al exterior; el baño necesita extracción mecánica (y nada de aparatos de llama abierta)."
    override fun bagnoSuCucina(bagno: String, stanza: String): String =
        "$bagno se abre directamente a $stanza: muchos reglamentos de higiene municipales piden un antebaño o un distribuidor entre el baño y la cocina o el salón."
    override fun bagnoIncompleto(a: String, manca: String): String =
        "$a: sin $manca. Al menos un baño de la vivienda debe tener inodoro, bidé, bañera o ducha y lavabo."
    override val vaso: String get() = "inodoro"
    override val bide: String get() = "bidé"
    override val vascaODoccia: String get() = "bañera o ducha"
    override val lavabo: String get() = "lavabo"
    override fun casaPiccola(m2: String, persone: Int, min: String): String =
        "La vivienda tiene $m2 m² para $persone personas (contadas por las camas); necesita al menos $min (14 m² por persona para las 4 primeras, 10 para las demás)."
    override fun monolocalePiccolo(m2: String, min: String, perDue: Boolean): String =
        "Estudio de $m2 m²: necesita al menos $min para ${if (perDue) "dos personas" else "una persona"}, baño incluido."
    override fun impiantoScarso(a: String, prese: String, luci: String, minPrese: String, minLuci: String): String =
        "$a: $prese y $luci. Para una instalación eléctrica básica (norma italiana CEI 64-8, nivel 1) hacen falta al menos $minPrese y $minLuci."
    override fun puntiPresa(n: Int): String = when (n) {
        0 -> "ningún punto de enchufe"
        1 -> "1 punto de enchufe"
        else -> "$n puntos de enchufe"
    }
    override fun puntiLuce(n: Int): String = when (n) {
        0 -> "ningún punto de luz"
        1 -> "1 punto de luz"
        else -> "$n puntos de luz"
    }
}
