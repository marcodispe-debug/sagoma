package com.sagoma.planimetria.testi.en

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
import com.sagoma.planimetria.model.WindowModel
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.persistence.ThemeChoice
import com.sagoma.planimetria.testi.Nomi

object NomiEn : Nomi() {
    override fun doorModel(v: DoorModel): String = when (v) {
        DoorModel.Flush -> "Flush"
        DoorModel.Panels -> "Panelled"
        DoorModel.Glazed -> "Glazed"
        DoorModel.Planks -> "Planked"
        DoorModel.Modern -> "Modern"
    }

    override fun finish(v: Finish): String = when (v) {
        Finish.Oak -> "Oak"
        Finish.White -> "White"
        Finish.LightOak -> "Light oak"
        Finish.Walnut -> "Walnut"
        Finish.Grey -> "Grey"
        Finish.Anthracite -> "Anthracite"
    }

    override fun windowModel(v: WindowModel): String = when (v) {
        WindowModel.Classic -> "Classic"
        WindowModel.Minimal -> "Minimal"
        WindowModel.English -> "Outward opening"
    }

    override fun framePosition(v: FramePosition): String = when (v) {
        FramePosition.Center -> "Mid-wall"
        FramePosition.Outside -> "Outside"
        FramePosition.Inside -> "Inside"
    }

    override fun shading(v: Shading): String = when (v) {
        Shading.None -> "None"
        Shading.Shutters -> "Shutters"
        Shading.RollerShutter -> "Roller shutter"
    }

    override fun stairStructure(v: StairStructure): String = when (v) {
        StairStructure.Masonry -> "Masonry"
        StairStructure.Floating -> "Floating"
        StairStructure.Stringers -> "Open stringers"
    }

    override fun stairMaterial(v: StairMaterial): String = when (v) {
        StairMaterial.Wood -> "Wood"
        StairMaterial.DarkWood -> "Dark wood"
        StairMaterial.Marble -> "Marble"
        StairMaterial.Stone -> "Stone"
        StairMaterial.Concrete -> "Concrete"
    }

    override fun stairRailing(v: StairRailing): String = when (v) {
        StairRailing.None -> "None"
        StairRailing.Metal -> "Metal"
        StairRailing.Glass -> "Glass"
        StairRailing.Wood -> "Wood"
    }

    override fun radiatorModel(v: RadiatorModel): String = when (v) {
        RadiatorModel.Panel -> "Panel"
        RadiatorModel.Fins -> "Column"
        RadiatorModel.TowelRail -> "Towel rail"
    }

    override fun lampModel(v: LampModel): String = when (v) {
        LampModel.Modern -> "Modern"
        LampModel.Classic -> "Classic"
        LampModel.Bell -> "Bell"
        LampModel.Industrial -> "Industrial"
    }

    override fun floorFinish(v: FloorFinish): String = when (v) {
        FloorFinish.RoomColor -> "Room colour"
        FloorFinish.OakParquet -> "Oak parquet"
        FloorFinish.WalnutParquet -> "Walnut parquet"
        FloorFinish.LightTiles -> "Light tiles"
        FloorFinish.GreyTiles -> "Grey tiles"
        FloorFinish.Marble -> "Marble"
        FloorFinish.Terracotta -> "Terracotta"
    }

    override fun wallPaint(v: WallPaint): String = when (v) {
        WallPaint.White -> "White"
        WallPaint.Ivory -> "Ivory"
        WallPaint.PearlGrey -> "Pearl grey"
        WallPaint.Sage -> "Sage"
        WallPaint.Sky -> "Sky blue"
        WallPaint.Sand -> "Sand"
        WallPaint.Terracotta -> "Terracotta"
        WallPaint.Yellow -> "Yellow"
        WallPaint.Salmon -> "Dusty pink"
        WallPaint.Brick -> "Brick"
        WallPaint.Stone -> "Stone"
        WallPaint.Anthracite -> "Anthracite"
    }

    override fun covering(v: Covering): String = when (v) {
        Covering.None -> "Paint only"
        Covering.Wallpaper -> "Wallpaper"
        Covering.Tiles -> "Tiles 20×20"
        Covering.Metro -> "Metro tiles"
        Covering.Brick -> "Exposed brick"
        Covering.Stone -> "Stone"
        Covering.Wainscot -> "Wainscoting"
        Covering.Boards -> "Wood panelling"
        Covering.Material -> "Photographic material"
    }

    override fun parapet(v: Parapet): String = when (v) {
        Parapet.Railing -> "Railing"
        Parapet.Wall -> "Low wall"
        Parapet.Glass -> "Glass"
    }

    override fun lOrientation(v: LOrientation): String = when (v) {
        LOrientation.TopRight -> "Top right"
        LOrientation.TopLeft -> "Top left"
        LOrientation.BottomRight -> "Bottom right"
        LOrientation.BottomLeft -> "Bottom left"
    }

    override fun openingKind(v: OpeningKind): String = when (v) {
        OpeningKind.Door -> "Single door"
        OpeningKind.Door2 -> "Double door"
        OpeningKind.Window1 -> "Single window"
        OpeningKind.Window2 -> "Double window"
        OpeningKind.Balcony1 -> "Single French window"
        OpeningKind.Balcony2 -> "Double French window"
        OpeningKind.FrenchDoor -> "French window"
        OpeningKind.Passage -> "Opening without door"
    }

    override fun passageStyle(v: PassageStyle): String = when (v) {
        PassageStyle.Square -> "Square"
        PassageStyle.Arched -> "Arched"
    }

    override fun fixtureKind(v: FixtureKind): String = when (v) {
        FixtureKind.Radiator -> "Radiator"
        FixtureKind.Outlet -> "Power socket"
        FixtureKind.Switch -> "Switch"
        FixtureKind.Spotlight -> "Spotlight"
        FixtureKind.Neon -> "Long neon"
        FixtureKind.Chandelier -> "Chandelier"
        FixtureKind.CeilingLight -> "Ceiling light"
        FixtureKind.LedStrip -> "LED strip"
        FixtureKind.WaterPoint -> "Water point"
        FixtureKind.WallLight -> "Wall light"
        FixtureKind.WallSpot -> "Wall spotlight"
        FixtureKind.WallLedStrip -> "Wall LED strip"
    }

    override fun layer(v: Layer): String = when (v) {
        Layer.WallLengths -> "Wall lengths"
        Layer.Dimensions -> "Dimensions"
        Layer.Texts -> "Texts"
        Layer.Furniture -> "Furniture"
        Layer.Fixtures -> "Fixtures (sockets, lights, radiators…)"
        Layer.Structure -> "Stairs, columns, beams, free walls"
        Layer.Rulers -> "Rulers"
        Layer.Underlay -> "Background plan"
    }

    override fun phase(v: Phase): String = when (v) {
        Phase.Existing -> "Existing"
        Phase.Demolish -> "To demolish"
        Phase.New -> "New"
    }

    override fun stairKind(v: StairKind): String = when (v) {
        StairKind.Straight -> "Straight flight"
        StairKind.LTurn -> "L-shaped (with landing)"
        StairKind.UTurn -> "U-shaped (two flights)"
        StairKind.Spiral -> "Spiral"
        StairKind.StraightLanding -> "Straight with landing"
        StairKind.LWinder -> "L-shaped with winders"
    }

    override fun columnShape(v: ColumnShape): String = when (v) {
        ColumnShape.Square -> "Square"
        ColumnShape.Round -> "Round"
    }

    override fun languageChoice(v: LanguageChoice): String = when (v) {
        LanguageChoice.Italian -> "Italiano"
        LanguageChoice.English -> "English"
        LanguageChoice.French -> "Français"
        LanguageChoice.Spanish -> "Español"
        LanguageChoice.System -> "Same as system"
    }

    override fun themeChoice(v: ThemeChoice): String = when (v) {
        ThemeChoice.Light -> "Light"
        ThemeChoice.Dark -> "Dark"
        ThemeChoice.System -> "Same as system"
    }

    override fun alignment(v: Alignment): String = when (v) {
        Alignment.Left -> "Left"
        Alignment.CenterX -> "Centre (horiz.)"
        Alignment.Right -> "Right"
        Alignment.Top -> "Top"
        Alignment.CenterY -> "Centre (vert.)"
        Alignment.Bottom -> "Bottom"
    }

    override fun snapKind(v: SnapKind): String = when (v) {
        SnapKind.Endpoint -> "Endpoint"
        SnapKind.Midpoint -> "Midpoint"
        SnapKind.Center -> "Centre"
        SnapKind.AlignVertical -> "Aligned"
        SnapKind.AlignHorizontal -> "Aligned"
        SnapKind.RightAngle -> "Right angle"
        SnapKind.Horizontal -> "Horizontal"
        SnapKind.Vertical -> "Vertical"
        SnapKind.Diagonal -> "45°"
        SnapKind.Close -> "Close"
    }

    override fun roomType(v: RoomType): String = when (v) {
        RoomType.Altro -> "Other"
        RoomType.Soggiorno -> "Living room"
        RoomType.Camera -> "Bedroom"
        RoomType.Cucina -> "Kitchen"
        RoomType.Bagno -> "Bathroom"
        RoomType.Cameretta -> "Kids' room"
        RoomType.Studio -> "Study"
        RoomType.Corridoio -> "Hallway"
        RoomType.Balcone -> "Balcony"
        RoomType.Terrazza -> "Terrace"
    }

    override fun roomDefaultName(v: RoomType): String = when (v) {
        RoomType.Altro -> "Room"
        RoomType.Soggiorno -> "Living room"
        RoomType.Camera -> "Bedroom"
        RoomType.Cucina -> "Kitchen"
        RoomType.Bagno -> "Bathroom"
        RoomType.Cameretta -> "Kids' room"
        RoomType.Studio -> "Study"
        RoomType.Corridoio -> "Hallway"
        RoomType.Balcone -> "Balcony"
        RoomType.Terrazza -> "Terrace"
    }

    override val stanzeComuni: List<String>
        get() = listOf(
            "Hall", "Entrance", "Lobby", "Lounge", "Dining room", "Dining area", "Kitchenette", "Small kitchen", "Pantry",
            "Master bedroom", "Double bedroom", "Single bedroom", "Guest room", "Walk-in closet", "Cloakroom",
            "Bathroom anteroom", "WC", "Guest bathroom", "En-suite bathroom", "Shower room", "Laundry", "Ironing room",
            "Fitness area", "Gym", "Sauna", "Playroom", "Rec room", "Library", "Open space", "Storage room", "Cellar",
            "Garage", "Attic", "Loft", "Attic room", "Utility room", "Boiler room", "Stairwell", "Stairs", "Landing", "Loggia",
            "Veranda", "Porch", "Garden", "Courtyard", "Primary bedroom", "Living room with kitchen",
        )

    override val sinonimiStanze: Map<String, String> get() = emptyMap()

    override fun furnitureKind(v: ParametricFurniture.Kind): String = when (v) {
        ParametricFurniture.Kind.WARDROBE -> "Custom wardrobe"
        ParametricFurniture.Kind.SLIDING_WARDROBE -> "Custom sliding wardrobe"
        ParametricFurniture.Kind.BED -> "Custom double bed"
        ParametricFurniture.Kind.SINGLE_BED -> "Custom single bed"
        ParametricFurniture.Kind.NIGHTSTAND -> "Custom nightstand"
        ParametricFurniture.Kind.CHEST -> "Custom chest of drawers"
        ParametricFurniture.Kind.BOOKCASE -> "Custom bookcase"
        ParametricFurniture.Kind.SIDEBOARD -> "Custom sideboard"
        ParametricFurniture.Kind.TV_UNIT -> "Custom TV unit"
        ParametricFurniture.Kind.SHELF -> "Custom shelf"
        ParametricFurniture.Kind.TABLE -> "Custom table"
        ParametricFurniture.Kind.DESK -> "Custom desk"
    }

    override fun kitchenKind(v: KitchenModules.Kind): String = when (v) {
        KitchenModules.Kind.BASE_DOORS -> "Base unit with doors"
        KitchenModules.Kind.BASE_DRAWERS -> "Base unit with drawers"
        KitchenModules.Kind.SINK -> "Sink base unit"
        KitchenModules.Kind.OVEN_HOB -> "Oven and hob base unit"
        KitchenModules.Kind.DISHWASHER -> "Built-in dishwasher"
        KitchenModules.Kind.CORNER -> "Corner base unit"
        KitchenModules.Kind.WALL_DOORS -> "Wall cabinet"
        KitchenModules.Kind.WALL_HOOD -> "Wall cabinet with hood"
        KitchenModules.Kind.TALL_PANTRY -> "Tall pantry unit"
        KitchenModules.Kind.TALL_OVEN -> "Tall oven and microwave unit"
        KitchenModules.Kind.TALL_FRIDGE -> "Tall fridge unit"
    }

    override fun categoria(chiave: String): String = when (chiave) {
        "Soggiorno" -> "Living room"
        "Pranzo" -> "Dining"
        "Cucina componibile" -> "Modular kitchen"
        "Cucina" -> "Kitchen"
        "Camera" -> "Bedroom"
        "Bagno" -> "Bathroom"
        "Studio" -> "Study"
        "Luci" -> "Lights"
        "Tappeti" -> "Rugs"
        "Elettrodomestici e TV" -> "Appliances and TV"
        "Decori" -> "Decor"
        "Piante" -> "Plants"
        "Esterni" -> "Outdoor"
        "Sport e hobby" -> "Sport and hobbies"
        "Garage e cantina" -> "Garage and cellar"
        "Intonaco e cemento" -> "Plaster and concrete"
        "Marmo e pietra" -> "Marble and stone"
        "Mattoni e pietra" -> "Brick and stone"
        "Moquette" -> "Carpet"
        "Parquet e legno" -> "Parquet and wood"
        "Piastrelle" -> "Tiles"
        "Tessuti" -> "Fabrics"
        else -> chiave
    }

    override fun arredo(nome: String): String = CatalogoEn.arredi[nome] ?: nome

    override fun materiale(nome: String): String = CatalogoEn.materiali[nome] ?: nome

    /** "Jute rug", "Round jute rug" (il materiale è già in inglese). */
    override fun tappeto(materiale: String, tondo: Boolean): String =
        if (tondo) "Round " + Inglese.minuscola(materiale) + " rug" else "$materiale rug"

    override val pianoCucina: String get() = " · worktop "

    override fun kitchenFront(v: KitchenModules.Front): String = when (v) {
        KitchenModules.Front.WHITE -> "Matt white"
        KitchenModules.Front.PEARL -> "Pearl grey"
        KitchenModules.Front.ANTHRACITE -> "Anthracite grey"
        KitchenModules.Front.SAGE -> "Sage green"
        KitchenModules.Front.NAVY -> "Navy blue"
        KitchenModules.Front.OAK -> "Light oak"
        KitchenModules.Front.WALNUT -> "Walnut"
    }

    override fun kitchenTop(v: KitchenModules.Top): String = when (v) {
        KitchenModules.Top.LAMINATE_WHITE -> "White laminate"
        KitchenModules.Top.LAMINATE_GREY -> "Grey laminate"
        KitchenModules.Top.LAMINATE_BLACK -> "Black laminate"
        KitchenModules.Top.WOOD -> "Oak wood"
        KitchenModules.Top.CARRARA -> "Carrara marble"
        KitchenModules.Top.STONE -> "Beige stone"
        KitchenModules.Top.CONCRETE -> "Concrete"
        KitchenModules.Top.MARQUINIA -> "Nero Marquina marble"
    }

    override fun phaseView(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Yellow and red"
        PhaseView.Existing -> "Existing state"
        PhaseView.Project -> "Proposed state"
    }

    override fun phaseViewTitle(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Comparison drawing"
        PhaseView.Existing -> "Existing state"
        PhaseView.Project -> "Proposed state"
    }

    override val tavolozza: Map<Long, String> = mapOf(
        0xFFFAFAF8 to "Pure white", 0xFFF5F1E8 to "Milk", 0xFFF1E6CF to "Cream", 0xFFE8DECB to "Linen",
        0xFFBFB2A3 to "Taupe", 0xFFCBC3B6 to "Greige", 0xFFA9A9A4 to "Concrete", 0xFF6E7378 to "Slate",
        0xFF3F4246 to "Graphite", 0xFF26272A to "Black", 0xFFEBCFC8 to "Powder pink", 0xFFE8B4B8 to "Pink",
        0xFFD2CBE3 to "Lavender", 0xFFB9A6D1 to "Wisteria", 0xFFCDE6D6 to "Mint", 0xFFA9D6D0 to "Aquamarine",
        0xFFB8D4EA to "Sky", 0xFF9DB3C4 to "Dusty blue", 0xFFD9A441 to "Ochre", 0xFFC89F3A to "Mustard",
        0xFFCB8467 to "Light brick", 0xFF9E4B2D to "Rust", 0xFF6E2632 to "Burgundy", 0xFFB5332E to "Red",
        0xFFE9806B to "Coral", 0xFFF2C0A0 to "Peach", 0xFF8B8B55 to "Olive", 0xFF3E5E44 to "Forest",
        0xFF2E7D63 to "Emerald", 0xFF22565E to "Petrol blue", 0xFF243A5E to "Navy blue", 0xFF3C6FA8 to "Blue",
        0xFF7E9CB9 to "Air force blue", 0xFF7A5A3C to "Tobacco", 0xFF4E3629 to "Chocolate", 0xFFB39377 to "Latte",
    )
}
