package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoEs : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info
    private val ok get() = e.hoCapito

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "Forma de L"
        Lesson.Elements -> "Puertas, ventanas y más"
        Lesson.OverPlan -> "Sobre tu plano"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guía · Lección $n de $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Paso $n de $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "¡Bienvenido a Sagoma!"
        CourseStep.ElementsDone -> "Cada elemento, desde «$infoTab»"
        CourseStep.Finished -> "¡Has terminado la guía!"
        else -> "¡Hecho!"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Te guío paso a paso para dibujar un pequeño piso de prueba, en 7 lecciones cortas. Las acciones las haces tú: en cada paso un " +
                "recuadro naranja te indica dónde hacer clic. Es un proyecto solo para practicar: tus proyectos no se tocan."
        CourseStep.SquareDone -> "El estudio cuadrado de 3,30 × 3,30 m está en el plano, con las medidas escritas en las paredes. En la próxima lección añades una habitación rectangular."
        CourseStep.RectDone -> "Aquí está el dormitorio de 4,00 × 3,30 m, junto al estudio. En la próxima lección: un salón en forma de L."
        CourseStep.LDone -> "Aquí está el salón en L: 6,40 × 2,70 m, con la parte de 3,00 × 1,90 m que sobresale. En la próxima lección: una habitación descuadrada, con medidas tomadas con el metro."
        CourseStep.SurveyDone -> "Aquí está el baño con su forma real, un poco descuadrado como en la realidad. En la próxima lección dibujas una habitación pared a pared."
        CourseStep.WallsDone -> "Aquí está la entrada, con la forma que has dibujado pared a pared. Has visto las formas de crear una habitación. En la próxima lección: puertas, ventanas, balcón, instalaciones y escaleras."
        CourseStep.ElementsDone ->
            "En «$infoTab» cambias medidas, tipo y todo lo demás del elemento seleccionado; se cierra con «▼». Para cada elemento: lo eliges en " +
                "«${e.elementiArchitettonici}», lo colocas en el plano y el resto desde «$infoTab». Última lección: construir las habitaciones " +
                "sobre tu plano. Hace falta un proyecto nuevo: lo preparo yo."
        CourseStep.Finished ->
            "Aquí está el baño torcido, igual que el del plano. Haz lo mismo con las demás habitaciones: una forma para empezar, luego paredes y " +
                "esquinas en su sitio. ¡Ya puedes dibujar tu casa! La guía se repite cuando quieras desde «${e.guide}»."
        else -> ""
    }

    override val avanti: String get() = "Siguiente"
    override val apriProgettoNuovo: String get() = "Abrir el proyecto nuevo"
    override val bastaCosi: String get() = "Ya basta"
    override val vaiAiProgetti: String get() = "Ir a tus proyectos"
    override val nienteDaIndicare: String get() = "Haz la acción de este paso, o haz clic en «${e.saltaPasso}»."
    override val tornaIndietro: String get() = "Esta no es la forma de este paso: haz clic en «${T.comuni.indietro}» y elige la correcta."
    override val vaiAllaLezione: String get() = "O ve a la lección:"
    override val voceMenu: String get() = "Guía interactiva (7 lecciones)"
    override val progettoProva: String get() = "Prueba de la guía"
    override val progettoProvaPlanimetria: String get() = "Prueba de la guía · plano"
    override val usaPiantina: String get() = "🧭 Usar el plano de la guía"
    override val ingresso: String get() = "Entrada"
    override val notaMisure: String get() = "medidas en cm, por la cara interior de las paredes"

    override val apriElementi: String get() = "Haz clic en «${e.elementiArchitettonici}», aquí abajo."
    override val apriElementiAltraStanza: String get() = "Para añadir otra habitación haz clic en «${e.elementiArchitettonici}», aquí abajo."
    override val poiStanza: String get() = "Haz clic en «${e.stanza}»: vuelve la ventana con las formas."
    override fun tipo(nome: String): String = "Elige el tipo: «$nome»."
    override val creaStanza: String get() = "Haz clic en «${cr.creaStanza}»."

    override val scegliQuadrato: String get() =
        "Cuando abres un proyecto nuevo, Sagoma te pregunta enseguida qué forma tiene la primera habitación. Haz clic en «${cr.quadrato}»: es la forma de una habitación con los cuatro lados iguales."
    override val tipoStudio: String get() = "En «${cr.tipoStanza}» haz clic en «${RoomType.Studio.label}»: Sagoma da a la habitación el nombre y el color adecuados."
    override fun scriviLato(v: Int): String = "En el campo «${cr.lato2}» escribe $v. Es la medida interior, de pared a pared: la que tomas con el metro."
    override val soffittoECrea: String get() = "La altura del techo ya está escrita (270 cm): cámbiala solo si tu habitación es distinta. Ahora haz clic en «${cr.creaStanza}»."

    override val scegliRettangolo: String get() = "Haz clic en «${cr.rettangolo}»: dos lados largos y dos cortos."
    override fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» es el lado horizontal de la habitación, de izquierda a derecha en el plano: escribe $v."
    override fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» es el otro lado, de arriba abajo: escribe $v."

    override val scegliL: String get() = "Haz clic en «${T.comuni.formaL}»: muchos salones y muchas cocinas son así, con una esquina que entra."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma pregunta hacia qué lado sobresale la parte de más de la L. Mira la habitación desde arriba, como en un plano: aquí es «$lato»."
    override fun larghezzaPrincipale(v: Int): String = "La L está hecha de dos partes. La parte principal es el rectángulo más grande: en «${cr.larghezzaPrincipale}» escribe $v."
    override fun profonditaPrincipale(v: Int): String = "En «${cr.profonditaPrincipale}» escribe $v."
    override fun larghezzaProtuberanza(v: Int): String = "La otra parte es la que sobresale. En «${cr.larghezzaProtuberanza}» escribe $v: debe ser menor que el ancho principal."
    override fun profonditaProtuberanza(v: Int): String = "En «${cr.profonditaProtuberanza}» escribe cuánto sobresale: $v."

    override val scegliRilievo: String get() = "Haz clic en «${cr.rilievo}»: sirve cuando las esquinas no están justo a 90°, o la habitación tiene 5 lados o más."
    override val tipoBagnoQuattroLati: String get() = "Elige el tipo «${RoomType.Bagno.label}». En «${cr.numeroLati}» ya pone 4, como los lados de nuestro baño."
    override fun primoLatoRilievo(v: Int): String =
        "Llama A a una esquina de la habitación, luego B, C y D en el sentido de las agujas del reloj. Mide los lados en la pared, por dentro. En «${cr.lato('A', 'B')}» escribe $v."
    override fun lato(nome: String, v: Int): String = "En «${cr.lato(nome[0], nome[1])}» escribe $v."
    override fun diagonaleAC(v: Int): String =
        "Luego una diagonal: de A a la esquina opuesta C (AC) o de B a D (BD), la que hayas medido. Aquí es AC, ya elegida: escribe $v. " +
            "Esta medida le dice a Sagoma cuánto están torcidas las esquinas."
    override val anteprimaECrea: String get() = "Más abajo está la vista previa de la forma real. Haz clic en «${cr.creaStanza}»."

    override val scegliMuroPerMuro: String get() = "Haz clic en «${cr.muroMuro}»: dibujas la habitación una pared cada vez, con las medidas interiores, como en un programa de arquitecto."
    override val primoAngolo: String get() = "Haz clic en el plano donde empieza la primera pared, por ejemplo aquí: es la primera esquina de la habitación, por dentro."
    override fun verso(n: Int, a: WallArrow): String = "Pared $n: elige la dirección, «${freccia(a)}»."
    override fun lunghezzaMuro(v: Int): String = "En «${T.muroPerMuro.lunghezzaCm}» escribe $v."
    override val aggiungi: String get() = "Haz clic en «${T.muroPerMuro.aggiungi}»."
    override fun muriFatti(n: Int, tot: Int): String = "Paredes hechas: $n de $tot"
    override val chiudiStanza: String get() = "Solo falta la última pared, la que vuelve a la primera esquina: haz clic en «${T.muroPerMuro.chiudiStanza}» y Sagoma la dibuja sola."
    override fun tipoIngresso(nome: String): String = "Elige el tipo: para la entrada vale «$nome». Techo y grosor de las paredes ya están escritos."

    override val portaElementi: String get() = "Las puertas, las ventanas y todo lo demás se añaden desde «${e.elementiArchitettonici}». Haz clic."
    override val porte: String get() = "Haz clic en «${e.porte}»."
    override val portaUnaAnta: String get() = "Elige el tipo, por ejemplo «${e.portaAd1Anta}»."
    override val cliccaMuroPorta: String get() = "Ahora haz clic en la pared, donde va la puerta: por ejemplo aquí."
    override val finestraElementi: String get() = "Una ventana se pone igual: «${e.elementiArchitettonici}»…"
    override val finestre: String get() = "…«${e.finestre}»…"
    override val finestraDueAnte: String get() = "…«${e.finestra2Ante}»."
    override val cliccaMuroFinestra: String get() = "Haz clic en una pared exterior, donde va la ventana: por ejemplo aquí."
    override val balconeElementi: String get() = "Ahora un balcón: «${e.elementiArchitettonici}»…"
    override val balconiTerrazze: String get() = "…«${e.balconiTerrazze}»…"
    override val balcone: String get() = "…«${RoomType.Balcone.label}»."
    override val balconeRettangolo: String get() = "La forma se elige como para las habitaciones: el balcón es un «${cr.rettangolo}»."
    override fun balconeLarghezza(v: Int): String = "Ancho $v…"
    override fun balconeProfondita(v: Int): String = "…y fondo $v."
    override val balconeCrea: String get() = "Haz clic en «${cr.crea}»: el balcón nace ya apoyado en la casa (luego lo puedes mover arrastrándolo por el nombre)."
    override val portaFinestraElementi: String get() = "Para salir al balcón hace falta una puerta-ventana: «${e.elementiArchitettonici}»…"
    override val porteFinestre: String get() = "…«${e.porteFinestre}»…"
    override val balconeDueAnte: String get() = "…«${e.balcone2Ante}»."
    override val cliccaMuroBalcone: String get() = "Haz clic en la pared de la casa donde se apoya el balcón, aquí."
    override val impiantiElementi: String get() = "Ahora las instalaciones: radiadores, enchufes, interruptores, tomas de agua y luces. «${e.elementiArchitettonici}»…"
    override val impianti: String get() = "…«${e.impianti}»…"
    override val calorifero: String get() = "…«${e.calorifero}»."
    override val cliccaMuroCalorifero: String get() = "Radiadores, enchufes, interruptores y tomas de agua se ponen haciendo clic en la pared: por ejemplo aquí."
    override val luciElementi: String get() = "Las luces, en cambio, van dentro de la habitación. «${e.elementiArchitettonici}»…"
    override val luci: String get() = "…«${e.luci}»…"
    override val lampadario: String get() = "…por ejemplo «${FixtureKind.Chandelier.label}»."
    override val cliccaStanzaLuce: String get() = "Haz clic dentro de la habitación, donde va la luz: por ejemplo aquí."
    override val cliccaStudio: String get() = "Por último, la escalera. Primero haz clic dentro de la habitación donde va, por ejemplo el estudio, aquí."
    override val scalaElementi: String get() = "Luego «${e.elementiArchitettonici}»…"
    override val scale: String get() = "…«${e.scale}»…"
    override val chiocciola: String get() = "…y elige la forma, por ejemplo «${StairKind.Spiral.label}»."
    override val info: String get() =
        "La escalera está seleccionada. Para todos los demás cambios haz clic en «$infoTab»: vale para cada elemento, puertas, ventanas, balcones, instalaciones y escaleras."

    override fun miniGuida(t: Tip): String = when (t) {
        Tip.Opening -> "La primera vez que pones una puerta se abre su mini guía: cómo deslizarla por la pared, y medidas y sentido desde «$infoTab». Léela y haz clic en «$ok»."
        Tip.Outdoor -> "El balcón también tiene su mini guía: en lugar de paredes hay un antepecho, y en «$infoTab» eliges barandilla, murete o vidrio. Haz clic en «$ok»."
        Tip.Fixture -> "Mini guía de las instalaciones: las de pared se deslizan por la pared, y en «$infoTab» eliges altura desde el suelo y medidas. Haz clic en «$ok»."
        Tip.Stair -> "Cuando pones una escalera se abre su mini guía, que lo explica todo: cómo moverla y arrimarla a una pared, y qué cambias en «$infoTab». Léela y haz clic en «$ok»."
        else -> "Se ha abierto una mini guía: léela y haz clic en «$ok»."
    }

    override val ricalca: String get() =
        "¿Ya tienes un plano, del catastro, del proyecto o solo una foto? Al elegir la primera habitación de un proyecto haz clic en «${cr.ricalcaPiantina}»."
    override val usaPiantinaGuida: String get() =
        "Normalmente aquí haces clic en «${T.sfondo.fotoImmagine}» (o «${T.sfondo.pdf}») y eliges el archivo de tu plano. Para probar usa el plano de la guía: haz clic en «$usaPiantina»."
    override fun taraturaA(cm: Int): String =
        "El plano está debajo del dibujo, pero Sagoma aún no sabe lo grande que es: hay que ponerlo a escala con una medida escrita en él. " +
            "Usemos la pared de arriba del salón, $cm cm: haz clic aquí, en la esquina interior de arriba a la izquierda del salón."
    override val taraturaB: String get() = "Ahora haz clic donde termina la medida: la esquina interior de arriba a la derecha del salón, aquí."
    override fun misuraReale(cm: Int): String = "Escribe la distancia real entre los dos puntos, la escrita en el plano: $cm."
    override val tara: String get() = "Haz clic en «${T.sfondo.tara2}»: ahora el plano está a escala, cada centímetro es correcto."
    override val chiudiPannello: String get() =
        "Aquí a la derecha está el panel «${e.piantinaDisegno}»: aclara u oscurece el plano, o lo oculta. Por ahora ciérralo con «${e.chiudi}»."
    override val esciMuroPerMuro: String get() =
        "Ha empezado «${cr.muroMuro}» sobre el plano: podrías hacer clic en las esquinas de las habitaciones una a una. Aquí en cambio usamos las formas y " +
            "las ajustamos sobre el plano: haz clic en «${T.comuni.esci}»."
    override val cameraDaQuadrato: String get() = "Empecemos por el dormitorio, que es un rectángulo: partimos de un cuadrado, para ver cómo se alarga. Haz clic en «${cr.quadrato}»."
    override fun latoCortoCamera(v: Int): String = "Escribe el lado corto del dormitorio, leído en el plano: $v."
    override val spostaCamera: String get() =
        "El dormitorio está, pero aún no en su sitio. Para mover una habitación pulsa sobre su nombre, mantén y arrástrala: suéltala " +
            "cuando la esquina de arriba a la izquierda esté sobre la del dormitorio dibujado (el contorno discontinuo)."
    override fun allungaCamera(v: Int): String =
        "El dormitorio del plano es más ancho: $v. Basta con mover la pared de la derecha: pulsa sobre la pared, mantén y arrástrala hasta la marca. " +
            "Mientras la mueves la medida cambia: déjala en $v."
    override val bagnoElementi: String get() = "Ahora el baño, que está torcido: aquí también partimos de un cuadrado. «${e.elementiArchitettonici}»…"
    override val bagnoQuadrato: String get() = "…«${cr.quadrato}»…"
    override fun latoBagno(v: Int): String = "…lado $v, la pared de arriba del baño…"
    override val spostaBagno: String get() = "Arrastra el baño por su nombre sobre el baño del plano, con la esquina de arriba a la izquierda en su sitio."
    override val angoloC: String get() =
        "Las esquinas del baño no están a 90°. Pulsa sobre el punto azul de la esquina de abajo a la derecha y arrástralo hasta la marca, donde coincide con el plano."
    override val angoloD: String get() = "Luego la esquina de abajo a la izquierda: solo un poco más abajo. Las medidas de las paredes se actualizan mientras la mueves."
}
