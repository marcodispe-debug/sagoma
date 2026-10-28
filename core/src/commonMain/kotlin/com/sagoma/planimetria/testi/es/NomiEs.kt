package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.geometry.Alignment
import com.sagoma.planimetria.geometry.PhaseView
import com.sagoma.planimetria.geometry.SnapKind
import com.sagoma.planimetria.model.ColumnShape
import com.sagoma.planimetria.model.Covering
import com.sagoma.planimetria.model.DoorModel
import com.sagoma.planimetria.model.Finish
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.FloorFinish
import com.sagoma.planimetria.model.KitchenModules
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.LampModel
import com.sagoma.planimetria.model.Layer
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.ParametricFurniture
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.PassageStyle
import com.sagoma.planimetria.model.Phase
import com.sagoma.planimetria.model.RadiatorModel
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.FramePosition
import com.sagoma.planimetria.model.Shading
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.model.StairMaterial
import com.sagoma.planimetria.model.StairRailing
import com.sagoma.planimetria.model.StairStructure
import com.sagoma.planimetria.model.WallPaint
import com.sagoma.planimetria.model.SashType
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.model.WindowShape
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.persistence.ThemeChoice
import com.sagoma.planimetria.testi.Nomi

object NomiEs : Nomi() {
    override fun doorModel(v: DoorModel): String = when (v) {
        DoorModel.Flush -> "Lisa"
        DoorModel.Panels -> "De cuarterones"
        DoorModel.Glazed -> "Acristalada"
        DoorModel.Planks -> "De tablas"
        DoorModel.Modern -> "Moderna"
        DoorModel.Armored -> "Acorazada"
    }

    override fun finish(v: Finish): String = when (v) {
        Finish.Oak -> "Roble"
        Finish.White -> "Blanco"
        Finish.LightOak -> "Roble claro"
        Finish.Walnut -> "Nogal"
        Finish.Grey -> "Gris"
        Finish.Anthracite -> "Antracita"
    }

    override fun windowShape(v: WindowShape): String = when (v) {
        WindowShape.Rectangle -> "Rectangular"
        WindowShape.Arched -> "En arco"
        WindowShape.Round -> "Redonda"
    }

    override fun sashType(v: SashType): String = when (v) {
        SashType.Casement -> "Abatible"
        SashType.TiltTurn -> "Oscilobatiente"
        SashType.Hopper -> "Basculante"
        SashType.Fixed -> "Fija"
        SashType.Pivot -> "Pivotante"
    }

    override fun windowModel(v: WindowModel): String = when (v) {
        WindowModel.Classic -> "Clásica"
        WindowModel.Minimal -> "Minimalista"
        WindowModel.English -> "Apertura exterior"
        WindowModel.Loft -> "Loft"
        WindowModel.Wood -> "Madera con junquillo"
        WindowModel.GlassBlock -> "Pavés de vidrio"
    }

    override fun framePosition(v: FramePosition): String = when (v) {
        FramePosition.Center -> "Centro del muro"
        FramePosition.Outside -> "Hacia fuera"
        FramePosition.Inside -> "Hacia dentro"
    }

    override fun shading(v: Shading): String = when (v) {
        Shading.None -> "Ninguno"
        Shading.Shutters -> "Contraventanas"
        Shading.RollerShutter -> "Persiana enrollable"
        Shading.Venetian -> "Veneciana exterior"
        Shading.InteriorShutters -> "Contraventanas interiores"
        Shading.RomanShutter -> "Persiana a la romana"
    }

    override fun stairStructure(v: StairStructure): String = when (v) {
        StairStructure.Masonry -> "De obra"
        StairStructure.Floating -> "Volada"
        StairStructure.Stringers -> "Con zancas"
    }

    override fun stairMaterial(v: StairMaterial): String = when (v) {
        StairMaterial.Wood -> "Madera"
        StairMaterial.DarkWood -> "Madera oscura"
        StairMaterial.Marble -> "Mármol"
        StairMaterial.Stone -> "Piedra"
        StairMaterial.Concrete -> "Hormigón"
    }

    override fun stairRailing(v: StairRailing): String = when (v) {
        StairRailing.None -> "Ninguna"
        StairRailing.Metal -> "Metal"
        StairRailing.Glass -> "Vidrio"
        StairRailing.Wood -> "Madera"
    }

    override fun radiatorModel(v: RadiatorModel): String = when (v) {
        RadiatorModel.Panel -> "Panel"
        RadiatorModel.Fins -> "De elementos"
        RadiatorModel.TowelRail -> "Toallero"
    }

    override fun lampModel(v: LampModel): String = when (v) {
        LampModel.Modern -> "Moderna"
        LampModel.Classic -> "Clásica"
        LampModel.Bell -> "Campana"
        LampModel.Industrial -> "Industrial"
    }

    override fun floorFinish(v: FloorFinish): String = when (v) {
        FloorFinish.RoomColor -> "Color de la habitación"
        FloorFinish.OakParquet -> "Parquet de roble"
        FloorFinish.WalnutParquet -> "Parquet de nogal"
        FloorFinish.LightTiles -> "Baldosas claras"
        FloorFinish.GreyTiles -> "Baldosas grises"
        FloorFinish.Marble -> "Mármol"
        FloorFinish.Terracotta -> "Barro cocido"
    }

    override fun wallPaint(v: WallPaint): String = when (v) {
        WallPaint.White -> "Blanco"
        WallPaint.Ivory -> "Marfil"
        WallPaint.PearlGrey -> "Gris perla"
        WallPaint.Sage -> "Salvia"
        WallPaint.Sky -> "Celeste"
        WallPaint.Sand -> "Arena"
        WallPaint.Terracotta -> "Terracota"
        WallPaint.Yellow -> "Amarillo"
        WallPaint.Salmon -> "Rosa empolvado"
        WallPaint.Brick -> "Ladrillo"
        WallPaint.Stone -> "Piedra"
        WallPaint.Anthracite -> "Antracita"
    }

    override fun covering(v: Covering): String = when (v) {
        Covering.None -> "Solo pintura"
        Covering.Wallpaper -> "Papel pintado"
        Covering.Tiles -> "Azulejos 20×20"
        Covering.Metro -> "Azulejos metro"
        Covering.Brick -> "Ladrillo visto"
        Covering.Stone -> "Piedra"
        Covering.Wainscot -> "Zócalo"
        Covering.Boards -> "Friso de madera"
        Covering.Material -> "Material fotográfico"
    }

    override fun parapet(v: Parapet): String = when (v) {
        Parapet.Railing -> "Barandilla"
        Parapet.Wall -> "Murete"
        Parapet.Glass -> "Vidrio"
    }

    override fun lOrientation(v: LOrientation): String = when (v) {
        LOrientation.TopRight -> "Arriba a la derecha"
        LOrientation.TopLeft -> "Arriba a la izquierda"
        LOrientation.BottomRight -> "Abajo a la derecha"
        LOrientation.BottomLeft -> "Abajo a la izquierda"
    }

    override fun openingKind(v: OpeningKind): String = when (v) {
        OpeningKind.Door -> "Puerta de una hoja"
        OpeningKind.Door2 -> "Puerta de dos hojas"
        OpeningKind.Window1 -> "Ventana de una hoja"
        OpeningKind.Window2 -> "Ventana de dos hojas"
        OpeningKind.Balcony1 -> "Puerta balconera de una hoja"
        OpeningKind.Balcony2 -> "Puerta balconera de dos hojas"
        OpeningKind.FrenchDoor -> "Puerta balconera"
        OpeningKind.Passage -> "Hueco sin puerta"
        OpeningKind.Niche -> "Hornacina"
    }

    override fun passageStyle(v: PassageStyle): String = when (v) {
        PassageStyle.Square -> "Recto"
        PassageStyle.Arched -> "En arco"
        PassageStyle.Segmental -> "En arco rebajado"
        PassageStyle.Pointed -> "En arco apuntado"
    }

    override fun fixtureKind(v: FixtureKind): String = when (v) {
        FixtureKind.Radiator -> "Radiador"
        FixtureKind.Outlet -> "Enchufe"
        FixtureKind.Switch -> "Interruptor"
        FixtureKind.Spotlight -> "Foco"
        FixtureKind.Neon -> "Fluorescente largo"
        FixtureKind.Chandelier -> "Lámpara de araña"
        FixtureKind.CeilingLight -> "Plafón"
        FixtureKind.LedStrip -> "Tira LED"
        FixtureKind.WaterPoint -> "Toma de agua"
        FixtureKind.WallLight -> "Aplique"
        FixtureKind.WallSpot -> "Foco de pared"
        FixtureKind.WallLedStrip -> "Tira LED de pared"
    }

    override fun layer(v: Layer): String = when (v) {
        Layer.WallLengths -> "Longitudes de pared"
        Layer.Dimensions -> "Cotas"
        Layer.Texts -> "Textos"
        Layer.Furniture -> "Muebles"
        Layer.Fixtures -> "Instalaciones (enchufes, luces, radiadores…)"
        Layer.Structure -> "Escaleras, pilares, vigas, paredes sueltas"
        Layer.Rulers -> "Metros"
        Layer.Underlay -> "Plano de fondo"
    }

    override fun phase(v: Phase): String = when (v) {
        Phase.Existing -> "Existente"
        Phase.Demolish -> "A demoler"
        Phase.New -> "Nuevo"
    }

    override fun stairKind(v: StairKind): String = when (v) {
        StairKind.Straight -> "Tramo recto"
        StairKind.LTurn -> "En L (con rellano)"
        StairKind.UTurn -> "En U (dos tramos)"
        StairKind.Spiral -> "Caracol"
        StairKind.StraightLanding -> "Recta con rellano"
        StairKind.LWinder -> "En L compensada"
    }

    override fun columnShape(v: ColumnShape): String = when (v) {
        ColumnShape.Square -> "Cuadrado"
        ColumnShape.Round -> "Redondo"
    }

    override fun languageChoice(v: LanguageChoice): String = when (v) {
        LanguageChoice.Italian -> "Italiano"
        LanguageChoice.English -> "English"
        LanguageChoice.French -> "Français"
        LanguageChoice.Spanish -> "Español"
        LanguageChoice.System -> "Como el sistema"
    }

    override fun themeChoice(v: ThemeChoice): String = when (v) {
        ThemeChoice.Light -> "Claro"
        ThemeChoice.Dark -> "Oscuro"
        ThemeChoice.System -> "Como el sistema"
    }

    override fun alignment(v: Alignment): String = when (v) {
        Alignment.Left -> "Izquierda"
        Alignment.CenterX -> "Centro (horiz.)"
        Alignment.Right -> "Derecha"
        Alignment.Top -> "Arriba"
        Alignment.CenterY -> "Centro (vert.)"
        Alignment.Bottom -> "Abajo"
    }

    override fun snapKind(v: SnapKind): String = when (v) {
        SnapKind.Endpoint -> "Extremo"
        SnapKind.Midpoint -> "Punto medio"
        SnapKind.Center -> "Centro"
        SnapKind.AlignVertical -> "Alineado"
        SnapKind.AlignHorizontal -> "Alineado"
        SnapKind.RightAngle -> "Ángulo recto"
        SnapKind.Horizontal -> "Horizontal"
        SnapKind.Vertical -> "Vertical"
        SnapKind.Diagonal -> "45°"
        SnapKind.Close -> "Cerrar"
    }

    override fun roomType(v: RoomType): String = when (v) {
        RoomType.Altro -> "Otro"
        RoomType.Soggiorno -> "Salón"
        RoomType.Camera -> "Dormitorio"
        RoomType.Cucina -> "Cocina"
        RoomType.Bagno -> "Baño"
        RoomType.Cameretta -> "Habitación infantil"
        RoomType.Studio -> "Estudio"
        RoomType.Corridoio -> "Pasillo"
        RoomType.Balcone -> "Balcón"
        RoomType.Terrazza -> "Terraza"
    }

    override fun roomDefaultName(v: RoomType): String = when (v) {
        RoomType.Altro -> "Habitación"
        RoomType.Soggiorno -> "Salón"
        RoomType.Camera -> "Dormitorio"
        RoomType.Cucina -> "Cocina"
        RoomType.Bagno -> "Baño"
        RoomType.Cameretta -> "Habitación infantil"
        RoomType.Studio -> "Estudio"
        RoomType.Corridoio -> "Pasillo"
        RoomType.Balcone -> "Balcón"
        RoomType.Terrazza -> "Terraza"
    }

    override val stanzeComuni: List<String>
        get() = listOf(
            "Distribuidor", "Entrada", "Vestíbulo", "Sala de estar", "Comedor", "Zona de comedor", "Cocina americana",
            "Cocina pequeña", "Despensa", "Dormitorio principal", "Dormitorio doble", "Dormitorio individual",
            "Habitación de invitados", "Vestidor", "Ropero", "Antebaño", "Aseo", "Baño de servicio", "Baño principal", "Ducha",
            "Lavadero", "Cuarto de plancha", "Zona fitness", "Gimnasio", "Sauna", "Sala de juegos", "Sala de ocio",
            "Biblioteca", "Espacio abierto", "Trastero", "Bodega", "Garaje", "Desván", "Bajo cubierta", "Mansarda",
            "Cuarto técnico", "Sala de calderas", "Hueco de escalera", "Escalera", "Rellano", "Logia", "Galería", "Porche",
            "Jardín", "Patio", "Suite principal", "Salón con cocina",
        )

    override val sinonimiStanze: Map<String, String> get() = emptyMap()

    override fun furnitureKind(v: ParametricFurniture.Kind): String = when (v) {
        ParametricFurniture.Kind.WARDROBE -> "Armario a medida"
        ParametricFurniture.Kind.SLIDING_WARDROBE -> "Armario corredero a medida"
        ParametricFurniture.Kind.BED -> "Cama de matrimonio a medida"
        ParametricFurniture.Kind.SINGLE_BED -> "Cama individual a medida"
        ParametricFurniture.Kind.NIGHTSTAND -> "Mesita de noche a medida"
        ParametricFurniture.Kind.CHEST -> "Cómoda a medida"
        ParametricFurniture.Kind.BOOKCASE -> "Librería a medida"
        ParametricFurniture.Kind.SIDEBOARD -> "Aparador a medida"
        ParametricFurniture.Kind.TV_UNIT -> "Mueble de TV a medida"
        ParametricFurniture.Kind.SHELF -> "Estante a medida"
        ParametricFurniture.Kind.TABLE -> "Mesa a medida"
        ParametricFurniture.Kind.DESK -> "Escritorio a medida"
    }

    override fun kitchenKind(v: KitchenModules.Kind): String = when (v) {
        KitchenModules.Kind.BASE_DOORS -> "Mueble bajo con puertas"
        KitchenModules.Kind.BASE_DRAWERS -> "Mueble bajo con cajones"
        KitchenModules.Kind.SINK -> "Mueble bajo fregadero"
        KitchenModules.Kind.OVEN_HOB -> "Mueble bajo horno y placa"
        KitchenModules.Kind.DISHWASHER -> "Lavavajillas integrado"
        KitchenModules.Kind.CORNER -> "Mueble bajo esquinero"
        KitchenModules.Kind.WALL_DOORS -> "Mueble alto"
        KitchenModules.Kind.WALL_HOOD -> "Mueble alto con campana"
        KitchenModules.Kind.TALL_PANTRY -> "Columna despensa"
        KitchenModules.Kind.TALL_OVEN -> "Columna horno y microondas"
        KitchenModules.Kind.TALL_FRIDGE -> "Columna frigorífico"
    }

    override fun categoria(chiave: String): String = when (chiave) {
        "Soggiorno" -> "Salón"
        "Pranzo" -> "Comedor"
        "Cucina componibile" -> "Cocina modular"
        "Cucina" -> "Cocina"
        "Camera" -> "Dormitorio"
        "Bagno" -> "Baño"
        "Studio" -> "Estudio"
        "Luci" -> "Iluminación"
        "Tappeti" -> "Alfombras"
        "Elettrodomestici e TV" -> "Electrodomésticos y TV"
        "Decori" -> "Decoración"
        "Piante" -> "Plantas"
        "Esterni" -> "Exterior"
        "Sport e hobby" -> "Deporte y ocio"
        "Garage e cantina" -> "Garaje y bodega"
        "Intonaco e cemento" -> "Enlucido y hormigón"
        "Marmo e pietra" -> "Mármol y piedra"
        "Mattoni e pietra" -> "Ladrillo y piedra"
        "Moquette" -> "Moqueta"
        "Parquet e legno" -> "Parquet y madera"
        "Piastrelle" -> "Baldosas"
        "Tessuti" -> "Tejidos"
        else -> chiave
    }

    override fun arredo(nome: String): String = CatalogoEs.arredi[nome] ?: nome

    override fun materiale(nome: String): String = CatalogoEs.materiali[nome] ?: nome

    /** "Alfombra de yute", "Alfombra redonda de yute" (el material ya está en español). */
    override fun tappeto(materiale: String, tondo: Boolean): String =
        (if (tondo) "Alfombra redonda de " else "Alfombra de ") + Spagnolo.minuscola(materiale)

    override val pianoCucina: String get() = " · encimera "

    override fun kitchenFront(v: KitchenModules.Front): String = when (v) {
        KitchenModules.Front.WHITE -> "Blanco mate"
        KitchenModules.Front.PEARL -> "Gris perla"
        KitchenModules.Front.ANTHRACITE -> "Gris antracita"
        KitchenModules.Front.SAGE -> "Verde salvia"
        KitchenModules.Front.NAVY -> "Azul marino"
        KitchenModules.Front.OAK -> "Roble claro"
        KitchenModules.Front.WALNUT -> "Nogal"
    }

    override fun kitchenTop(v: KitchenModules.Top): String = when (v) {
        KitchenModules.Top.LAMINATE_WHITE -> "Laminado blanco"
        KitchenModules.Top.LAMINATE_GREY -> "Laminado gris"
        KitchenModules.Top.LAMINATE_BLACK -> "Laminado negro"
        KitchenModules.Top.WOOD -> "Madera de roble"
        KitchenModules.Top.CARRARA -> "Mármol de Carrara"
        KitchenModules.Top.STONE -> "Piedra beige"
        KitchenModules.Top.CONCRETE -> "Hormigón"
        KitchenModules.Top.MARQUINIA -> "Mármol Negro Marquina"
    }

    override fun phaseView(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Amarillos y rojos"
        PhaseView.Existing -> "Estado actual"
        PhaseView.Project -> "Estado reformado"
    }

    override fun phaseViewTitle(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Plano comparativo"
        PhaseView.Existing -> "Estado actual"
        PhaseView.Project -> "Estado reformado"
    }

    override val tavolozza: Map<Long, String> = mapOf(
        0xFFFAFAF8 to "Blanco puro", 0xFFF5F1E8 to "Leche", 0xFFF1E6CF to "Crema", 0xFFE8DECB to "Lino",
        0xFFBFB2A3 to "Gris topo", 0xFFCBC3B6 to "Greige", 0xFFA9A9A4 to "Cemento", 0xFF6E7378 to "Pizarra",
        0xFF3F4246 to "Grafito", 0xFF26272A to "Negro", 0xFFEBCFC8 to "Rosa polvo", 0xFFE8B4B8 to "Rosa",
        0xFFD2CBE3 to "Lavanda", 0xFFB9A6D1 to "Glicina", 0xFFCDE6D6 to "Menta", 0xFFA9D6D0 to "Aguamarina",
        0xFFB8D4EA to "Cielo", 0xFF9DB3C4 to "Azul polvo", 0xFFD9A441 to "Ocre", 0xFFC89F3A to "Mostaza",
        0xFFCB8467 to "Ladrillo claro", 0xFF9E4B2D to "Óxido", 0xFF6E2632 to "Burdeos", 0xFFB5332E to "Rojo",
        0xFFE9806B to "Coral", 0xFFF2C0A0 to "Melocotón", 0xFF8B8B55 to "Oliva", 0xFF3E5E44 to "Bosque",
        0xFF2E7D63 to "Esmeralda", 0xFF22565E to "Azul petróleo", 0xFF243A5E to "Azul marino", 0xFF3C6FA8 to "Azul",
        0xFF7E9CB9 to "Azul aviador", 0xFF7A5A3C to "Tabaco", 0xFF4E3629 to "Chocolate", 0xFFB39377 to "Café con leche",
    )
}
