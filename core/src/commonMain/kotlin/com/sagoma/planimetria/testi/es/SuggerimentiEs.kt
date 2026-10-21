package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Suggerimenti

object SuggerimentiEs : Suggerimenti() {
    override fun titolo(v: Tip): String = when (v) {
        Tip.Welcome -> "Bienvenido"
        Tip.Room -> "Habitación seleccionada"
        Tip.Wall -> "Pared"
        Tip.NewRoom -> "Nueva habitación"
        Tip.Opening -> "Puertas y ventanas"
        Tip.Fixture -> "Instalaciones"
        Tip.Ruler -> "Metro"
        Tip.WallCut -> "Pared bajo cubierta"
        Tip.View3D -> "Vista 3D"
        Tip.Stair -> "Escaleras"
        Tip.Outdoor -> "Balcón y terraza"
        Tip.Structure -> "Pilares y vigas"
        Tip.Measure -> "Distancia"
        Tip.FreeWall -> "Pared suelta"
        Tip.Floors -> "Plantas"
        Tip.Tracing -> "Calcar un plano"
        Tip.Calibrate -> "Calibración"
        Tip.WallDraw -> "Pared a pared"
        Tip.Underlay -> "Plano y dibujo"
        Tip.ExcludedZone -> "Zona no transitable"
        Tip.RoomInfo -> "Panel Info"
        Tip.Snap -> "Ajuste"
    }

    override fun testo(v: Tip): String = when (v) {
        Tip.Welcome ->
            "Toca una habitación para seleccionarla. Pellizca con dos dedos para ampliar y arrastra el fondo para mover el plano. " +
                "Si te equivocas, ↶ Deshacer arriba lo deja todo como estaba."
        Tip.Room ->
            "Arrastra los puntos azules para mover las esquinas y las paredes para estirarla; arrastra el nombre para mover toda la habitación. " +
                "Para el nombre, el tipo y las medidas exactas de los lados pulsa Info o desliza hacia arriba la barra de abajo. " +
                "Si el ajuste lleva el punto donde no quieres, desactívalo con 🧲 en la barra."
        Tip.Wall ->
            "Arrástrala para moverla: se ajusta a las paredes de las habitaciones cercanas. En Info cambias su altura y, si es compartida " +
                "con otra habitación, puedes eliminarla para unir las dos habitaciones en un único espacio."
        Tip.NewRoom ->
            "La nueva habitación se pega a la que está abierta, con una pared en común. Si va en otro sitio, arrástrala por el nombre: " +
                "se ajusta a las paredes de las habitaciones cercanas."
        Tip.Opening -> "Arrástrala a lo largo de la pared para moverla. En Info cambias las medidas, abatible o corredera, el lado de las bisagras y el sentido de apertura, o la quitas."
        Tip.Fixture ->
            "Las instalaciones de pared se deslizan a lo largo de la pared; las luces se arrastran a cualquier punto de la habitación. " +
                "En Info ajustas la altura desde el suelo, las medidas y la rotación, o la quitas."
        Tip.Ruler -> "Arrastra los extremos para estirarlo y el centro para moverlo. El tirador redondo lo gira (se ajusta cada 45°), × lo elimina."
        Tip.WallCut ->
            "La pared baja en diagonal hasta la altura del murete. Arrastra el punto sobre la pared, o escribe la distancia, " +
                "para elegir dónde empieza a bajar. La línea discontinua muestra dónde empieza la pendiente del techo; en 3D lo ves todo."
        Tip.View3D ->
            "Arrastra para girar alrededor de la casa; amplía con dos dedos (o con la rueda del ratón). Toca una pared o un objeto para seleccionarlo. " +
                "Con 🚶 Caminar entras en las habitaciones. Todos los controles están en ❔ Guías → Vista 3D."
        Tip.Stair ->
            "Arrástrala para moverla: cerca de una pared se alinea sola. En Info elige tipo, ancho, huella y hacia dónde gira, " +
                "y gírala 90°. El número y la altura de los peldaños salen de la altura entre plantas; en la planta de arriba aparece el hueco."
        Tip.Outdoor ->
            "Es un espacio exterior: en lugar de paredes hay un antepecho y contra la casa queda la pared de la casa. Ponlo contra una pared " +
                "y añade una puerta balconera (Elementos constructivos → Puertas balconeras) en la pared de la habitación. En Info elige barandilla, murete o vidrio y la altura."
        Tip.Structure ->
            "Arrastra un pilar para moverlo: cerca de una pared se alinea y se convierte en un pilar que sobresale. Una viga se mueve " +
                "arrastrándola y se alarga desde los puntos de los extremos, que se apoyan en las paredes. En Info cambias la forma y las medidas."
        Tip.Measure ->
            "Toca el primer objeto y luego el segundo: paredes, puertas y ventanas, radiadores, enchufes, luces, pilares, vigas, escaleras. " +
                "Aparece la distancia entre sus bordes más cercanos, con una cota en el plano; si mueves los objetos se actualiza."
        Tip.FreeWall ->
            "Un tabique que no cierra una habitación. Arrástralo para moverlo y arrastra los puntos de los extremos para alargarlo o girarlo: " +
                "los extremos se ajustan a las caras de otras paredes y a los extremos de otras paredes sueltas, y la pared se endereza a 90°. " +
                "En Info cambias el grosor, la altura y el color."
        Tip.Floors ->
            "Con el botón de la planta, arriba a la izquierda, cambias de planta y añades otras nuevas. La planta inferior se ve " +
                "en gris para alinear las paredes, y sus escaleras aparecen como un hueco en el suelo. En 3D ves la planta elegida sobre las de abajo."
        Tip.Tracing ->
            "Carga la foto o el PDF del plano y calíbralo con una medida que conozcas: luego haces clic en las esquinas de las habitaciones " +
                "y las medidas salen solas, como las escritas en el plano."
        Tip.Calibrate ->
            "Toca los dos extremos de una medida escrita en el plano (mejor la más larga, por ejemplo una pared entera) y escribe " +
                "cuántos cm mide. Si luego las medidas no cuadran, «📏 Rehacer calibración» lo corrige incluso a mitad de una habitación."
        Tip.WallDraw ->
            "Haz clic en las esquinas una tras otra; para terminar haz clic en la primera (con círculo) o en «Cerrar habitación». Sobre el plano " +
                "haz clic en las esquinas interiores de las paredes: el grosor se añade hacia fuera. ↶ Última quita la última esquina, " +
                "«Escribir medidas» te deja elegir la dirección y la longitud de cada pared."
        Tip.Underlay ->
            "Con el panel «◐ Plano y dibujo», abajo a la derecha, muestras u ocultas el plano y tu dibujo y ajustas " +
                "su transparencia, para ver bien lo que calcas. El dibujo vuelve a ser opaco al reabrir el proyecto."
        Tip.ExcludedZone ->
            "Haz clic en las esquinas de la parte que no cuenta en los m², como la parte baja de un ático o un patinillo: las esquinas " +
                "se ajustan a las esquinas interiores de las paredes. Al final elige el nombre; los m² de la habitación bajan, el suelo sigue entero."
        Tip.RoomInfo ->
            "Arriba están Duplicar, Eliminar, el nombre, el tipo y las medidas principales. Debajo, las pestañas: Medidas (alturas, grosores, paredes y " +
                "zonas no transitables), Suelo, paredes (acabados), Puertas, inst. (puertas, ventanas e instalaciones), Obra (paredes existentes, a demoler o nuevas). " +
                "La barra de la derecha desplaza el contenido."
        Tip.Snap ->
            "Con 🧲 Sí, esquinas, paredes y objetos se ajustan a esquinas, alineaciones, direcciones rectas y paredes cercanas. Con 🧲 No van " +
                "exactamente donde los pones; la habitación se cierra igualmente al hacer clic en la primera esquina."
    }
}
