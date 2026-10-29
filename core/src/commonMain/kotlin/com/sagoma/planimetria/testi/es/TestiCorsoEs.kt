package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoEs : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "Forma de L"
        Lesson.Elements -> "Puertas, ventanas y más"
        Lesson.Check -> "Revisar el plano"
        Lesson.OverPlan -> "Sobre tu plano"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guía · Lección $n de $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Paso $n de $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "¡Bienvenido a Sagoma!"
        CourseStep.ElementsDone -> "Cada elemento, desde «$infoTab»"
        CourseStep.Finished -> "¡Has terminado la guía!"
        CourseStep.Snap -> "Mover las habitaciones y el ajuste 🧲"
        CourseStep.InnerFace -> "El lado interior de las paredes"
        else -> "¡Hecho!"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Te guío paso a paso para dibujar la casa de este plano inventado, en ${Lesson.entries.size} lecciones cortas: una habitación cada vez, cada " +
                "una en su sitio como en el dibujo. Las acciones las haces tú: en cada paso un recuadro naranja te indica dónde hacer " +
                "clic. Es un proyecto solo para practicar: tus proyectos no se tocan."
        CourseStep.SquareDone ->
            "El estudio está listo: 3,30 × 3,30 m. Has visto todas las formas de cambiar la forma de una habitación: las medidas al " +
                "crearla, las paredes y las esquinas arrastradas, las medidas exactas en «$infoTab» y «${e.annulla}» para volver " +
                "atrás. En la próxima lección: el salón en L, encima del estudio."
        CourseStep.Snap ->
            "El salón ha nacido separado de la casa. Una habitación se mueve arrastrándola por su nombre. El ajuste " +
                "«${T.schedaStanza.si}», aquí abajo, está activado: cuando acercas la habitación a otra, se engancha sola, esquina con " +
                "esquina, y la pared entre las dos pasa a ser una sola. Desactivado («${T.schedaStanza.no}»), la habitación se queda " +
                "exactamente donde la sueltas. Déjalo activado."
        CourseStep.LDone ->
            "El salón está pegado encima del estudio, como en el plano: la pared entre las dos habitaciones ahora es una sola, " +
                "común. En la próxima lección: el dormitorio, un rectángulo a la derecha del salón."
        CourseStep.RectDone ->
            "El dormitorio está pegado a la derecha del salón. En la próxima lección: el baño, debajo del dormitorio, un poco descuadrado."
        CourseStep.SurveyDone ->
            "El baño está debajo del dormitorio, con su forma real: un poco descuadrado, como en la realidad. En la próxima lección: " +
                "la entrada, dibujada pared a pared en el espacio que queda entre las habitaciones."
        CourseStep.WallsDone ->
            "La entrada cierra la casa: ahora todas las habitaciones están conectadas, como en el plano. Has visto todas las formas " +
                "de crear una habitación. En la próxima lección: la pared suelta, una pared sola que no cierra una habitación."
        CourseStep.FreeWallDone ->
            "El dormitorio ya tiene su vestidor, separado por un tabique con el paso al fondo. En el panel «${T.schedaStanza.info}» de la " +
                "pared suelta eliges también la altura (hasta el techo o más baja, como un murete), el color y la curva; y a las paredes " +
                "sueltas también puedes añadirles puertas y ventanas. En la próxima lección: puertas, ventanas, balcón, instalaciones y escaleras."
        CourseStep.ElementsDone ->
            "En «$infoTab» cambias medidas, tipo y todo lo demás del elemento seleccionado; se cierra con «▼». Para cada elemento: lo eliges en " +
                "«${e.elementiArchitettonici}», lo colocas en el plano y el resto desde «$infoTab». " +
                "Próxima lección: la revisión del plano, que encuentra sola los errores de dibujo y arregla muchos."
        CourseStep.CheckDone ->
            "Ya sabes usar la revisión del plano: la lista completa desde el menú «⋮», «✓ ${T.controllo.correggi}» y «✓ Corregir todos» que " +
                "arreglan solos («${e.annulla}» vuelve atrás), los «!» en el plano para ver qué hay en un punto y «${T.controllo.togliDallaPianta}» " +
                "para los que te parecen bien así (se vuelven a poner desde la lista). Antes de exportar, Sagoma avisa si aún hay errores de dibujo. " +
                "Última lección: construir las habitaciones sobre tu plano. Hace falta un proyecto nuevo: lo preparo yo."
        CourseStep.Finished ->
            "Aquí están el dormitorio, el baño y el salón sobre el plano, con las medidas correctas. Haz lo mismo con las demás habitaciones: " +
                "una forma para empezar, pegada a las habitaciones de al lado, luego paredes y esquinas en su sitio y las medidas exactas en " +
                "«$infoTab». ¡Ya puedes dibujar tu casa! La guía se repite cuando quieras desde «${e.guide}»."
        CourseStep.InnerFace ->
            "Antes de mover el dormitorio, una cosa importante. Sagoma mide las habitaciones desde el lado interior de las paredes, " +
                "como las medidas escritas en el plano: el grosor de la pared va hacia fuera. Cuando pongas una habitación sobre el " +
                "plano, haz coincidir lo más posible el lado interior de las paredes dibujadas con el lado interior de las paredes del " +
                "plano: mira la línea interior de la pared, no el centro ni el lado exterior. Así las medidas, los metros cuadrados y " +
                "las habitaciones vecinas salen bien."
        else -> ""
    }

    override val avanti: String get() = "Siguiente"
    override val apriProgettoNuovo: String get() = "Abrir el proyecto nuevo"
    override val bastaCosi: String get() = "Ya basta"
    override val vaiAiProgetti: String get() = "Ir a tus proyectos"
    override val nienteDaIndicare: String get() = "Haz la acción de este paso, o haz clic en «${e.saltaPasso}»."
    override val tornaIndietro: String get() = "Esta no es la forma de este paso: haz clic en «${T.comuni.indietro}» y elige la correcta."
    override val vaiAllaLezione: String get() = "O ve a la lección:"
    override val voceMenu: String get() = "Guía interactiva (${Lesson.entries.size} lecciones)"
    override val progettoProva: String get() = "Prueba de la guía"
    override val progettoProvaPlanimetria: String get() = "Prueba de la guía · plano"
    override val usaPiantina: String get() = "🧭 Usar el plano de la guía"
    override val ingresso: String get() = "Entrada"
    override val cabinaArmadio: String get() = "vestidor"
    override val notaMisure: String get() = "medidas en cm, por la cara interior de las paredes"

    override val apriElementi: String get() = "Haz clic en «${e.elementiArchitettonici}», aquí abajo."
    override val apriElementiAltraStanza: String get() = "Para añadir otra habitación haz clic en «${e.elementiArchitettonici}», aquí abajo."
    override val poiStanza: String get() = "Haz clic en «${e.stanza}»: vuelve la ventana con las formas."
    override fun tipo(nome: String): String = "Elige el tipo: «$nome»."
    override val creaStanza: String get() = "Haz clic en «${cr.creaStanza}»."
    override fun selezionaStanza(nome: String): String = "Haz clic en el nombre de la habitación, «$nome», para seleccionarla."
    override val accendiAgganci: String get() = "El ajuste está desactivado: haz clic en «${T.schedaStanza.no}», aquí abajo, para activarlo."
    override val schedaMisure: String get() = "Abre la pestaña «${T.schedaStanza.misure}»."
    override fun latoMuro(a: WallArrow): String = when (a) {
        WallArrow.UP -> "de arriba"
        WallArrow.RIGHT -> "de la derecha"
        WallArrow.DOWN -> "de abajo"
        WallArrow.LEFT -> "de la izquierda"
    }

    override val scegliQuadrato: String get() =
        "Cuando abres un proyecto nuevo, Sagoma te pregunta enseguida qué forma tiene la primera habitación. Empecemos por el " +
            "estudio, la habitación cuadrada abajo a la izquierda en el plano: haz clic en «${cr.quadrato}»."
    override val tipoStudio: String get() = "En «${cr.tipoStanza}» haz clic en «${RoomType.Studio.label}»: Sagoma da a la habitación el nombre y el color adecuados."
    override fun scriviLato(v: Int): String =
        "En el campo «${cr.lato2}» escribe $v: es la primera forma de dar forma a una habitación, con las medidas al crearla. Es la " +
            "medida interior, de pared a pared: la que tomas con el metro."
    override val soffittoECrea: String get() = "La altura del techo ya está escrita (270 cm): cámbiala solo si tu habitación es distinta. Ahora haz clic en «${cr.creaStanza}»."
    override val trascinaMuroStudio: String get() =
        "Segunda forma: arrastrar las paredes. Pulsa en la pared derecha del estudio, mantén pulsado y arrástrala hasta la marca: la " +
            "habitación se ensancha y la medida escrita en la pared cambia mientras la mueves."
    override fun misureEsatte(v: Int): String =
        "Arrastrando a mano la medida nunca sale exacta. Tercera forma, la más precisa: escribir las medidas. Devolvamos el estudio " +
            "a $v cm: haz clic en «$infoTab»."
    override fun muroStudio(numero: Int, lato: WallArrow, v: Int): String =
        "En «${T.schedaStanza.muri}» está la longitud interior de cada pared. En «${T.schedaStanza.muro(numero)}», la pared " +
            "${latoMuro(lato)}, escribe $v y pulsa Intro: la pared siguiente se mueve y el estudio vuelve a ser cuadrado."
    override val trascinaAngoloStudio: String get() =
        "Cuarta forma, para las habitaciones fuera de escuadra: arrastrar una esquina. Pulsa en el punto azul de la esquina de " +
            "abajo a la derecha y arrástralo hasta la marca."
    override val annullaAngolo: String get() =
        "Pero el estudio tiene las esquinas rectas. Cuando te equivocas, «${e.annulla}», aquí arriba, quita el último cambio: haz clic en él."

    override val scegliRettangolo: String get() =
        "Haz clic en «${cr.rettangolo}»: es el dormitorio, a la derecha del salón, con dos lados largos y dos cortos."
    override val spostaCameraAccanto: String get() =
        "También el dormitorio nace separado: arrástralo por su nombre hasta el contorno discontinuo, a la derecha del salón. Con " +
            "el ajuste activado se engancha solo."
    override fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» es el lado horizontal de la habitación, de izquierda a derecha en el plano: escribe $v."
    override fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» es el otro lado, de arriba abajo: escribe $v."

    override val scegliL: String get() =
        "Haz clic en «${T.comuni.formaL}»: es el salón, que en el plano está encima del estudio. Muchos salones y cocinas son así, " +
            "con una esquina que entra."
    override val spostaSoggiorno: String get() =
        "Arrastra el salón por su nombre hasta el contorno discontinuo, encima del estudio: cuando llega cerca se engancha solo."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma pregunta hacia qué lado sobresale la parte de más de la L. Mira la habitación desde arriba, como en un plano: aquí es «$lato»."
    override fun larghezzaPrincipale(v: Int): String = "La L está hecha de dos partes. La parte principal es el rectángulo más grande: en «${cr.larghezzaPrincipale}» escribe $v."
    override fun profonditaPrincipale(v: Int): String = "En «${cr.profonditaPrincipale}» escribe $v."
    override fun larghezzaProtuberanza(v: Int): String = "La otra parte es la que sobresale. En «${cr.larghezzaProtuberanza}» escribe $v: debe ser menor que el ancho principal."
    override fun profonditaProtuberanza(v: Int): String = "En «${cr.profonditaProtuberanza}» escribe cuánto sobresale: $v."

    override val scegliRilievo: String get() =
        "Haz clic en «${cr.rilievo}»: el baño, debajo del dormitorio, tiene esquinas que no son exactamente de 90°. Sirve también " +
            "para habitaciones de 5 lados o más."
    override val spostaBagnoSotto: String get() =
        "Arrastra el baño por su nombre hasta el contorno discontinuo, debajo del dormitorio: se engancha solo."
    override val tipoBagnoQuattroLati: String get() = "Elige el tipo «${RoomType.Bagno.label}». En «${cr.numeroLati}» ya pone 4, como los lados de nuestro baño."
    override fun primoLatoRilievo(v: Int): String =
        "Llama A a una esquina de la habitación, luego B, C y D en el sentido de las agujas del reloj. Mide los lados en la pared, por dentro. En «${cr.lato('A', 'B')}» escribe $v."
    override fun lato(nome: String, v: Int): String = "En «${cr.lato(nome[0], nome[1])}» escribe $v."
    override fun diagonaleAC(v: Int): String =
        "Luego una diagonal: de A a la esquina opuesta C (AC) o de B a D (BD), la que hayas medido. Aquí es AC, ya elegida: escribe $v. " +
            "Esta medida le dice a Sagoma cuánto están torcidas las esquinas."
    override val anteprimaECrea: String get() = "Más abajo está la vista previa de la forma real. Haz clic en «${cr.creaStanza}»."

    override val scegliMuroPerMuro: String get() =
        "Haz clic en «${cr.muroMuro}»: la entrada llena el espacio que queda entre las habitaciones, y la dibujas pared a pared, con " +
            "las medidas interiores, como en un programa de arquitecto."
    override val primoAngolo: String get() =
        "Haz clic aquí, debajo del dormitorio y junto al salón: es la primera esquina de la entrada, por dentro. Sagoma la engancha " +
            "a la esquina correcta entre las paredes."
    override fun verso(n: Int, a: WallArrow): String = "Pared $n: elige la dirección, «${freccia(a)}»."
    override fun lunghezzaMuro(v: Int): String = "En «${T.muroPerMuro.lunghezzaCm}» escribe $v."
    override val aggiungi: String get() = "Haz clic en «${T.muroPerMuro.aggiungi}»."
    override fun muriFatti(n: Int, tot: Int): String = "Paredes hechas: $n de $tot"
    override val chiudiStanza: String get() = "Solo falta la última pared, la que vuelve a la primera esquina: haz clic en «${T.muroPerMuro.chiudiStanza}» y Sagoma la dibuja sola."
    override fun tipoIngresso(nome: String): String = "Elige el tipo: para la entrada vale «$nome». Techo y grosor de las paredes ya están escritos."

    override fun selezionaCameraTramezzo(nome: String): String =
        "Una pared suelta es una pared sola, que no cierra una habitación: por ejemplo el tabique del vestidor en el dormitorio, " +
            "como en el plano. La pared nace en la habitación seleccionada: haz clic en el nombre del dormitorio, «$nome»."
    override val apriElementiMuroSingolo: String get() = "Ahora haz clic en «${e.elementiArchitettonici}», aquí abajo."
    override val scegliMuroSingolo: String get() =
        "Al final de la ventana está «${cr.muroSingolo}»: solo aparece cuando ya hay al menos una habitación. Haz clic."
    override val spostaTramezzo: String get() =
        "La pared nace recta a través del dormitorio, de una pared a la otra, y ya está seleccionada. Se mueve arrastrándola: pulsa " +
            "sobre la pared, mantén pulsado y llévala hasta la línea discontinua, donde va el tabique. Los dos extremos siguen unidos a las paredes."
    override val selezionaMuroSingolo: String get() = "Haz clic en la pared suelta para seleccionarla: aparecen los puntos en sus dos extremos."
    override val accorciaTramezzo: String get() =
        "El tabique no llega hasta el fondo: deja el paso para entrar en el vestidor. Pulsa el punto azul de abajo, al final de la " +
            "pared, y arrástralo hacia arriba hasta la marca: la pared se acorta."
    override val infoTramezzo: String get() = "Como en las habitaciones, las medidas exactas se escriben en «${T.schedaStanza.info}»: haz clic."
    override fun lunghezzaTramezzo(v: Int): String =
        "En «${T.comuni.lunghezza}» escribe $v: la pared se alarga o se acorta por el final, el inicio de arriba no se mueve."
    override fun spessoreTramezzo(v: Int): String = "Un tabique de pladur es delgado: en «${T.comuni.spessore}» escribe $v."

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
    override val balconeCrea: String get() =
        "Haz clic en «${cr.crea}»: el balcón nace en su sitio, encima del dormitorio, ya apoyado en la casa (luego puedes moverlo arrastrándolo por el nombre)."
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
    override val luceSemplice: String get() = "…y aquí «${T.arredi.puntoLuceSemplice}»: se coloca haciendo clic en la habitación."
    override val cliccaStanzaLuce: String get() = "Haz clic dentro de la habitación, donde va la luz: por ejemplo aquí."
    override val cliccaStudio: String get() = "Por último, la escalera. Primero haz clic dentro de la habitación donde va, por ejemplo el estudio, aquí."
    override val scalaElementi: String get() = "Luego «${e.elementiArchitettonici}»…"
    override val scale: String get() = "…«${e.scale}»…"
    override val chiocciola: String get() = "…y elige la forma, por ejemplo «${StairKind.Spiral.label}»."
    override val info: String get() =
        "La escalera está seleccionada. Para todos los demás cambios haz clic en «$infoTab»: vale para cada elemento, puertas, ventanas, balcones, instalaciones y escaleras."

    override val controlloMenu: String get() = "Una ayuda para usar a menudo: la revisión del plano, que busca los errores de dibujo que suelen pasar desapercibidos. Haz clic en el menú «⋮» aquí arriba."
    override val controlloVoce: String get() = "…y «${T.controllo.voceMenu}»."
    override val controlloRiapri: String get() = "Vuelve a abrir la revisión: el menú «⋮» aquí arriba…"
    override val controlloCorreggiTutti: String get() =
        "Aquí está la lista: ⚠ lo que hay que corregir, ? lo que hay que revisar, 📏 los mínimos de las normas de vivienda. Muchos problemas " +
            "se arreglan solos: «✓ Corregir todos» corrige todo un grupo a la vez (por ejemplo pone las puertas que faltan) y un solo «${e.annulla}» lo deshace. Haz clic."
    override val controlloCorreggi: String get() =
        "Aquí está la lista: ⚠ lo que hay que corregir, ? lo que hay que revisar, 📏 los mínimos de las normas de vivienda. Muchos problemas " +
            "se arreglan solos: haz clic en «✓ ${T.controllo.correggi}» (con «${e.annulla}» vuelves atrás)."
    override val controlloChiudi: String get() =
        "Hecho: la lista se actualiza sola. «${T.controllo.mostra}» lleva al punto correcto y lo selecciona, «${T.controllo.condividi}» envía " +
            "la lista a quien te ayuda. Ahora haz clic en «${T.controllo.chiudi}»."
    override val controlloSegno: String get() =
        "La revisión también trabaja sola mientras dibujas: la señal arriba a la izquierda del plano dice cuántos problemas hay (tócala para " +
            "volver a abrir la lista) y un «!» marca el lugar de cada uno: rojo para los errores, amarillo para lo que hay que revisar, azul para las normas. Haz clic en este «!»."
    override val controlloTogli: String get() =
        "La ficha dice qué hay en este punto, con «✓ ${T.controllo.correggi}» (donde se puede) y «${T.controllo.mostra}». Si para ti está bien " +
            "así, «${T.controllo.togliDallaPianta}» quita el «!»: el problema sigue en la lista y desde allí se vuelve a poner. Haz clic en «${T.controllo.togliDallaPianta}»."
    override val controlloSegnoDiNuovo: String get() = "Haz clic en un «!» del plano: se abre la ficha de ese punto."
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
    override val sceltaDopoTaratura: String get() =
        "La calibración está guardada: el plano ya está a escala. Ahora elige cómo dibujar la primera habitación: con «${cr.muroMuro}» " +
            "haces clic en sus esquinas sobre el plano, con una forma la creas y luego la llevas sobre el plano. Aquí usamos las formas. " +
            "Empecemos por el dormitorio, que es un rectángulo: partimos de un cuadrado, para ver cómo se alarga. Haz clic en «${cr.quadrato}»."
    override val cameraDaQuadrato: String get() = "Empecemos por el dormitorio, que es un rectángulo: partimos de un cuadrado, para ver cómo se alarga. Haz clic en «${cr.quadrato}»."
    override fun latoCortoCamera(v: Int): String = "Escribe el lado corto del dormitorio, leído en el plano: $v."
    override val spostaCamera: String get() =
        "El dormitorio está, pero aún no en su sitio. Para mover una habitación pulsa sobre su nombre, mantén y arrástrala: suéltala " +
            "cuando la esquina interior de arriba a la izquierda esté sobre la esquina interior del dormitorio dibujado (el contorno discontinuo)."
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

    override val soggiornoElementi: String get() = "Última habitación: el salón, que tiene forma de L. «${e.elementiArchitettonici}»…"
    override val soggiornoL: String get() = "…«${T.comuni.formaL}»."
    override val soggiornoMisureDopo: String get() =
        "Elige el tipo «${RoomType.Soggiorno.label}». Deja las medidas como están: el salón lo pones a medida después, sobre el plano."
    override val attaccaSoggiorno: String get() =
        "En el plano el salón está pegado al dormitorio, a su derecha. Arrastra el salón por su nombre hasta el contorno discontinuo: " +
            "con el ajuste «${T.schedaStanza.si}» activado, cuando su esquina de arriba a la derecha llega cerca de la del dormitorio, se engancha solo."
    override fun muroSullaPiantina(muro: Int): String =
        "Ahora lleva las paredes sobre el plano. Pulsa en la pared ${nomeMuroSoggiorno(muro)} del salón y arrástrala hasta la marca, sobre la " +
            "pared dibujada. Basta más o menos: la medida exacta la escribes después."
    override val misureInInfo: String get() =
        "Moviendo las paredes a mano las medidas nunca salen exactamente como las del plano: mira las escritas en las paredes del salón. " +
            "Las correctas se escriben en «${T.schedaStanza.info}»: haz clic."
    override fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "En «${T.schedaStanza.muri}» está la longitud interior de cada pared. " else "") +
            "En «${T.schedaStanza.muro(numero)}», la pared ${nomeMuroSoggiorno(muro)}, escribe $v y pulsa Intro."

    override val apriPannello: String get() =
        "Una última cosa: para ver mejor el dibujo sobre el plano, haz clic en «${e.piantinaDisegno}», aquí abajo a la derecha."
    override val trasparenzaPiantina: String get() =
        "Aquí ajustas el plano: arrastrando el cursor se aclara o se oscurece, con el interruptor lo ocultas. Debajo está lo mismo " +
            "para el dibujo. Cuando termines, cierra el panel con «${T.comuni.chiudi}». Prueba: arrastra el cursor del plano."

    override fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "de arriba"
        1 -> "de la derecha"
        2 -> "de abajo a la derecha"
        3 -> "corta del medio"
        4 -> "de abajo a la izquierda"
        else -> "de la izquierda"
    }
}
