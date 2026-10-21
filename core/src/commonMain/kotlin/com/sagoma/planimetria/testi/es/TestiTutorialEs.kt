package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.testi.TestiTutorial

object TestiTutorialEs : TestiTutorial() {
    override fun titolo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro -> "Tutorial"
        TutorialStep.OpenInfo -> "Información de la habitación"
        TutorialStep.WallLengths -> "Cambiar las medidas"
        TutorialStep.ChangeType -> "Tipo de habitación"
        TutorialStep.MoveWall -> "Mover una pared"
        TutorialStep.MoveCorner -> "Mover una esquina"
        TutorialStep.AddDoor -> "Añadir una puerta"
        TutorialStep.MoveDoor -> "Mover la puerta"
        TutorialStep.AddFixture -> "Instalaciones"
        TutorialStep.AddRoom -> "Añadir una habitación"
        TutorialStep.MoveRoom -> "Juntar las habitaciones"
        TutorialStep.Undo -> "Deshacer"
        TutorialStep.Done -> "¡Hecho!"
        TutorialStep.TraceLoad -> "Cargar el plano"
        TutorialStep.TraceCalibA -> "Calibración: el primer punto"
        TutorialStep.TraceCalibB -> "Calibración: el segundo punto"
        TutorialStep.TraceCalibValue -> "Calibración: la longitud real"
        TutorialStep.TraceFirstCorner -> "La primera esquina"
        TutorialStep.TraceCorners -> "Las demás esquinas"
        TutorialStep.TraceClose -> "Cerrar la habitación"
        TutorialStep.TraceCheck -> "Revisar las medidas"
        TutorialStep.TraceFade -> "Plano y dibujo"
        TutorialStep.TraceNext -> "La siguiente habitación"
        TutorialStep.TraceDone -> "¡Hecho!"
    }

    override fun testo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro ->
            "Te guío paso a paso por las funciones principales: en cada paso lo pruebas tú. " +
                "Son cambios reales en el plano, pero al final puedes deshacerlos con ↶."
        TutorialStep.OpenInfo ->
            "Toca una habitación para seleccionarla: abajo aparece la barra Info. Pulsa Info, la flecha ▲ " +
                "o desliza la barra hacia arriba para abrir sus propiedades."
        TutorialStep.WallLengths ->
            "En el panel, en «Paredes», está la longitud de cada lado: escribe un valor nuevo y confirma. " +
                "El lado opuesto se ajusta solo. Luego cambia un lado en la otra dirección. Las cotas del plano se actualizan."
        TutorialStep.ChangeType -> "Todavía en el panel, elige un tipo de habitación, por ejemplo Cocina: el color y el nombre cambian solos."
        TutorialStep.MoveWall ->
            "Cierra el panel (▼ o deslízalo hacia abajo). Luego toca una pared para seleccionarla y arrástrala: " +
                "la habitación crece o se reduce, y la pared se ajusta a las paredes de las habitaciones cercanas."
        TutorialStep.MoveCorner -> "Arrastra uno de los puntos azules de las esquinas de la habitación: puedes hacer incluso paredes inclinadas."
        TutorialStep.AddDoor -> "Pulsa «🧱 Elementos constructivos» → Puertas, elige un tipo y luego toca la pared donde ponerla."
        TutorialStep.MoveDoor -> "Arrastra la puerta a lo largo de la pared. En Info cambias sus medidas y el sentido de apertura, o la haces corredera."
        TutorialStep.AddFixture -> "Pulsa «🧱 Elementos constructivos» → Instalaciones y elige, por ejemplo, Enchufe; luego toca una pared. Las luces, en cambio, se ponen tocando un punto dentro de la habitación."
        TutorialStep.AddRoom -> "Pulsa «🧱 Elementos constructivos» → Habitación, elige la forma y las medidas y confirma."
        TutorialStep.MoveRoom -> "Arrastra la nueva habitación por su nombre y acércala a otra: cuando las paredes están cerca, se ajustan."
        TutorialStep.Undo -> "¿Te has equivocado? Pulsa ↶ Deshacer arriba para volver un paso atrás (↷ Rehacer lo vuelve a hacer)."
        TutorialStep.Done -> "Ya conoces las funciones principales. Desde «❔ Guías», arriba a la derecha, puedes repetir el tutorial cuando quieras y encontrar una guía para cada función."
        TutorialStep.TraceLoad -> "Pulsa «Foto / imagen» (o «PDF» en el ordenador) y elige el plano de la casa: catastro, plano del arquitecto o una foto bien recta."
        TutorialStep.TraceCalibA ->
            "Busca en el plano una medida escrita, mejor si es larga (por ejemplo una pared acotada 4,72). " +
                "Amplía y toca el inicio de esa medida: aparece el punto A."
        TutorialStep.TraceCalibB -> "Ahora toca el final de la misma medida: aparece el punto B. Si te equivocas, otro toque vuelve a empezar desde A."
        TutorialStep.TraceCalibValue ->
            "En la casilla de arriba escribe cuánto mide de verdad la distancia entre A y B (en cm, como en el plano) y confirma: " +
                "el plano ya está a escala."
        TutorialStep.TraceFirstCorner ->
            "Ahora estás en «Pared a pared». Elige una habitación y haz clic en una de sus esquinas, por el lado interior de la pared: " +
                "el grosor de la pared se añade solo hacia fuera."
        TutorialStep.TraceCorners -> "Haz clic en las demás esquinas una tras otra, dando la vuelta a la habitación. Si te equivocas, «↶ Última» quita la última esquina."
        TutorialStep.TraceClose ->
            "Cuando hayas marcado todas las esquinas, vuelve a hacer clic en la primera (la del círculo) o pulsa «Cerrar habitación»; " +
                "luego elige el tipo de habitación y confirma."
        TutorialStep.TraceCheck ->
            "Abre la Info de la habitación (barra de abajo, ▲ o deslízala hacia arriba): compara los m² y las paredes con el plano. " +
                "Si una parte no cuenta en los m² (buhardilla baja, patinillo) usa «▧ Añadir zona no transitable» en la pestaña Medidas."
        TutorialStep.TraceFade ->
            "Cierra el panel y pulsa «◐ Plano y dibujo» abajo a la derecha: aclara el plano u ocúltalo " +
                "para ver bien el dibujo."
        TutorialStep.TraceNext -> "Pulsa «🧱 Elementos constructivos» → Habitación, elige «Pared a pared» y calca la siguiente habitación, esquina a esquina como antes."
        TutorialStep.TraceDone ->
            "Sigue así con las demás habitaciones. Si el ajuste mueve tus puntos, desactívalo con 🧲. " +
                "Con «Hecho», si aún no lo has hecho, empieza el tutorial de las demás funciones (puertas, instalaciones, medidas)."
    }

    override fun latiCambiati(n: Int): String =
        if (n == 0) "Lados cambiados: 0 de 2" else "Lados cambiados: 1 de 2 · ahora un lado en la otra dirección"
    override fun angoliServono(n: Int): String = "Esquinas marcadas: $n · hacen falta al menos 3"
    override fun angoliCliccati(n: Int): String = "Esquinas marcadas: $n"
}
