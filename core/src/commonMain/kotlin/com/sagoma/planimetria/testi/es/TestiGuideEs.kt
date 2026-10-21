package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Passo
import com.sagoma.planimetria.testi.TestiGuide

object TestiGuideEs : TestiGuide() {
    override val titoloMansarda: String get() = "Buhardilla"
    override val titoloRicalco: String get() = "Calcar un plano"
    override val titoloStanze: String get() = "Habitaciones y medidas"
    override val titoloZone: String get() = "Zonas no transitables"
    override val titoloMuri: String get() = "Paredes y esquinas"
    override val titoloAgganci: String get() = "Ajuste automático"
    override val titoloPorte: String get() = "Puertas y ventanas"
    override val titoloImpianti: String get() = "Instalaciones"
    override val titoloPiuStanze: String get() = "Varias habitaciones"
    override val titoloBalconi: String get() = "Balcones y terrazas"
    override val titoloScale: String get() = "Escaleras y plantas"
    override val titoloVista3D: String get() = "Vista 3D"
    override val titoloQuote: String get() = "Cotas y textos"

    override fun passoDi(numero: Int, totale: Int): String = "Paso $numero de $totale"
    override val apriVista3D: String get() = "Abrir la vista 3D"

    override val mansardaIntro: Passo get() = Passo(
        "Cómo es una buhardilla",
        "En una buhardilla el techo baja hacia una pared más baja (el «murete» bajo la pendiente del tejado). " +
            "Por eso las paredes laterales no son rectangulares: tienen altura completa hasta un punto y luego bajan " +
            "en diagonal. En la app se consigue con dos gestos: se baja el murete y se cortan en diagonal las paredes de al lado.",
    )

    override val mansardaStanza: Passo get() = Passo(
        "1 · Dibuja la habitación",
        "Crea la habitación con «🧱 Elementos constructivos» → Habitación y ajusta las medidas desde el panel Info, como cualquier otra habitación. " +
            "La altura de la habitación es la de la parte más alta de la buhardilla.",
    )

    override val mansardaMuretto: Passo get() = Passo(
        "2 · Baja el murete",
        "Toca la pared hacia la que baja el tejado, abre Info y escribe su altura real en «Altura de la pared» " +
            "(por ejemplo 120 cm). En el plano la altura aparece en morado junto a la pared.",
    )

    override val mansardaTaglio: Passo get() = Passo(
        "3 · Corta la primera pared lateral",
        "Toca una de las dos paredes perpendiculares al murete y, en el panel Info, activa «Corte diagonal (bajo cubierta)». " +
            "Si el murete es más bajo, la pared ahora baja hasta su altura. Si las dos paredes de al lado son bajas, " +
            "elige el lado en «Baja hacia».",
    )

    override val mansardaInizio: Passo get() = Passo(
        "4 · Elige dónde empieza a bajar",
        "Mide en casa a qué distancia del murete empieza a bajar el techo y escríbela en «Empieza a bajar a». " +
            "O arrastra el punto naranja en el plano: la línea discontinua marca dónde empieza la pendiente.",
    )

    override val mansardaRipeti: Passo get() = Passo(
        "5 · Repite en la otra pared",
        "Haz lo mismo en la pared de enfrente, para que el techo baje de forma uniforme en toda la habitación. " +
            "Si la pared se comparte con otra habitación, solo se corta el lado de la buhardilla: la habitación de al lado queda entera.",
    )

    override val mansardaControllo: Passo get() = Passo(
        "6 · Revisa puertas, ventanas e instalaciones",
        "Si una ventana, una puerta, un radiador o un enchufe queda más alto que la pared inclinada, aparece en el plano un " +
            "punto rojo con «!» y un aviso en el panel Info: baja el objeto o muévelo a lo largo de la pared " +
            "hacia la parte alta.",
    )

    override val mansarda3D: Passo get() = Passo(
        "7 · Mira el resultado en 3D",
        "Abre la vista 3D: la parte de arriba de las paredes y el techo bajan en diagonal y puedes caminar dentro de la buhardilla " +
            "para comprobar que todo cuadra. El volumen de la habitación en el panel Info ya tiene en cuenta el techo inclinado.",
    )

    override val caricaPiantina: Passo get() = Passo(
        "Carga el plano",
        "En un proyecto nuevo elige «Calcar un plano»; en un proyecto ya empezado usa ⋮ → «Plano de fondo…». " +
            "Luego «Foto / imagen» (o «PDF» en el ordenador): plano catastral, plano del arquitecto o una foto bien recta.",
    )

    override val ricalcaAngoli: Passo get() = Passo(
        "Calca las esquinas",
        "Se abre «Pared a pared». " + Tip.WallDraw.text + " Para la habitación siguiente: «🧱 Elementos constructivos» → Habitación → «Pared a pared».",
    )

    override val controllaMisure: Passo get() = Passo(
        "Comprueba las medidas",
        "Abre la Info de la habitación y compara m² y paredes con los escritos en el plano. Si una parte no cuenta en los m² " +
            "(buhardilla baja, patinillo) añade una zona no transitable.",
    )

    override val muoversi: Passo get() = Passo(
        "Moverse por el plano",
        Tip.Welcome.text,
    )

    override val aggiungiZona: Passo get() = Passo(
        "Añade una zona",
        "Selecciona la habitación, abre Info y, en la pestaña Medidas, pulsa «▧ Añadir zona no transitable».",
    )

    override val agganci: Passo get() = Passo(
        "Activar y desactivar el ajuste",
        "El botón 🧲 está en la barra Info de la habitación y en el panel «Pared a pared». " + Tip.Snap.text,
    )

    override val aggiungiBalcone: Passo get() = Passo(
        "Añade un balcón o una terraza",
        "Pulsa «🧱 Elementos constructivos» → Balcones y terrazas y elige el tipo, luego forma y medidas como para una habitación. Sale ya pegado a la casa: " +
            "arrástralo por su nombre a lo largo de la pared hasta el sitio correcto, se ajusta solo. En los lados hacia fuera está la barandilla, " +
            "contra la casa se queda la pared.",
    )

    override val parapetto: Passo get() = Passo(
        "Barandilla y puerta balconera",
        Tip.Outdoor.text,
    )

    override val aggiungiScala: Passo get() = Passo(
        "Añade una escalera",
        "Pulsa «🧱 Elementos constructivos» → Escaleras y elige el tipo: recta, en L con rellano, en U con dos tramos o de caracol. " +
            "La escalera aparece en el centro de la vista (o de la habitación seleccionada): arrástrala donde haga falta, cerca de una pared se alinea sola.",
    )

    override val opzioniScala: Passo get() = Passo(
        "Tipo, medidas y sentido",
        "Toca la escalera y abre Info: cambias tipo, ancho, huella (o diámetro de la de caracol), hacia dónde gira y la orientación, " +
            "o la giras 90° con ↻. Número y altura de los peldaños salen de la altura entre plantas; si los peldaños son incómodos aparece un aviso.",
    )

    override val aggiungiPiano: Passo get() = Passo(
        "Añade una planta",
        "Toca el botón de la planta arriba a la izquierda y elige «Añadir una planta encima». Puedes partir de las paredes de la planta de abajo " +
            "o de una planta vacía. La planta de abajo sigue visible en gris y sus escaleras aparecen como un hueco en el suelo.",
    )

    override val piani3D: Passo get() = Passo(
        "Las plantas en 3D",
        "En 3D ves la planta elegida encima de las de abajo, unidas por las escaleras; las plantas más altas se ocultan, como si " +
            "se hubiera quitado el tejado. Desde el mismo menú de la planta cambias su nombre y su altura entre plantas, o la eliminas.",
    )

    override val comandi3D: Passo get() = Passo(
        "Todos los controles",
        "Teléfono: un dedo gira, dos dedos hacen zoom y desplazan; una pared, puerta o instalación seleccionada se puede arrastrar para " +
            "moverla. En 🚶 Caminar te mueves con el joystick abajo a la izquierda. Ordenador: ↑ ↓ (o W S) avanzar y retroceder, ← → para " +
            "girar, A D para un paso lateral, RePág/AvPág para mirar arriba o abajo, Mayús para ir más rápido; " +
            "rueda del ratón para el zoom, arrastrar con el botón derecho para desplazar la vista.",
    )

    override val quota: Passo get() = Passo(
        "Añade una cota",
        "Pulsa «🛠 Herramientas» → Cota y toca los dos extremos: los puntos se ajustan a las esquinas y a las caras de las paredes. La cota se queda " +
            "en el plano y aparece en las láminas; tócala para moverla o eliminarla.",
    )

    override val testo: Passo get() = Passo(
        "Escribe un texto",
        "Pulsa «🛠 Herramientas» → Texto, toca donde escribirlo, luego escríbelo y elige el tamaño.",
    )
}
