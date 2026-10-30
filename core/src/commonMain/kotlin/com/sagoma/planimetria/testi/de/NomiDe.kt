package com.sagoma.planimetria.testi.de

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

/** Nomi tedeschi di tipi, modelli e finiture; i nomi del catalogo in [CatalogoDe]. */
object NomiDe : Nomi() {
    override fun doorModel(v: DoorModel): String = when (v) {
        DoorModel.Flush -> "Glatt"
        DoorModel.Panels -> "Kassetten"
        DoorModel.Glazed -> "Mit Glas"
        DoorModel.Planks -> "Mit Leisten"
        DoorModel.Modern -> "Modern"
        DoorModel.Armored -> "Sicherheitstür"
        DoorModel.AllGlass -> "Ganzglas"
    }

    override fun finish(v: Finish): String = when (v) {
        Finish.Oak -> "Eiche"
        Finish.White -> "Weiß"
        Finish.LightOak -> "Eiche hell"
        Finish.Walnut -> "Nussbaum"
        Finish.Grey -> "Grau"
        Finish.Anthracite -> "Anthrazit"
    }

    override fun windowShape(v: WindowShape): String = when (v) {
        WindowShape.Rectangle -> "Rechteckig"
        WindowShape.Arched -> "Rundbogen"
        WindowShape.Round -> "Rund"
    }

    override fun sashType(v: SashType): String = when (v) {
        SashType.Casement -> "Drehflügel"
        SashType.TiltTurn -> "Dreh-Kipp"
        SashType.Hopper -> "Kippflügel"
        SashType.Fixed -> "Fest"
        SashType.Pivot -> "Schwingflügel"
    }

    override fun windowModel(v: WindowModel): String = when (v) {
        WindowModel.Classic -> "Klassisch"
        WindowModel.Minimal -> "Minimal"
        WindowModel.English -> "Englisch (Sprossen)"
        WindowModel.Loft -> "Loft"
        WindowModel.Wood -> "Holz mit Glasleiste"
        WindowModel.GlassBlock -> "Glasbausteine"
    }

    override fun framePosition(v: FramePosition): String = when (v) {
        FramePosition.Center -> "Wandmitte"
        FramePosition.Outside -> "Außen"
        FramePosition.Inside -> "Innen"
    }

    override fun shading(v: Shading): String = when (v) {
        Shading.None -> "Keine"
        Shading.Shutters -> "Fensterläden"
        Shading.RollerShutter -> "Rollladen"
        Shading.Venetian -> "Außenjalousie"
        Shading.InteriorShutters -> "Innenläden"
        Shading.RomanShutter -> "Ausstellladen"
        Shading.SlidingShutters -> "Schiebeläden"
    }

    override fun stairStructure(v: StairStructure): String = when (v) {
        StairStructure.Masonry -> "Massiv"
        StairStructure.Floating -> "Kragstufen"
        StairStructure.Stringers -> "Offen (Wangen)"
    }

    override fun stairMaterial(v: StairMaterial): String = when (v) {
        StairMaterial.Wood -> "Holz"
        StairMaterial.DarkWood -> "Dunkles Holz"
        StairMaterial.Marble -> "Marmor"
        StairMaterial.Stone -> "Stein"
        StairMaterial.Concrete -> "Beton"
    }

    override fun stairRailing(v: StairRailing): String = when (v) {
        StairRailing.None -> "Keines"
        StairRailing.Metal -> "Metall"
        StairRailing.Glass -> "Glas"
        StairRailing.Wood -> "Holz"
    }

    override fun radiatorModel(v: RadiatorModel): String = when (v) {
        RadiatorModel.Panel -> "Flachheizkörper"
        RadiatorModel.Fins -> "Gliederheizkörper"
        RadiatorModel.TowelRail -> "Handtuchheizkörper"
    }

    override fun lampModel(v: LampModel): String = when (v) {
        LampModel.Modern -> "Modern"
        LampModel.Classic -> "Klassisch"
        LampModel.Bell -> "Glocke"
        LampModel.Industrial -> "Industrie"
    }

    override fun floorFinish(v: FloorFinish): String = when (v) {
        FloorFinish.RoomColor -> "Raumfarbe"
        FloorFinish.OakParquet -> "Parkett Eiche"
        FloorFinish.WalnutParquet -> "Parkett Nussbaum"
        FloorFinish.LightTiles -> "Fliesen hell"
        FloorFinish.GreyTiles -> "Fliesen grau"
        FloorFinish.Marble -> "Marmor"
        FloorFinish.Terracotta -> "Terrakotta"
    }

    override fun wallPaint(v: WallPaint): String = when (v) {
        WallPaint.White -> "Weiß"
        WallPaint.Ivory -> "Elfenbein"
        WallPaint.PearlGrey -> "Perlgrau"
        WallPaint.Sage -> "Salbei"
        WallPaint.Sky -> "Himmelblau"
        WallPaint.Sand -> "Sand"
        WallPaint.Terracotta -> "Terrakotta"
        WallPaint.Yellow -> "Gelb"
        WallPaint.Salmon -> "Altrosa"
        WallPaint.Brick -> "Ziegelrot"
        WallPaint.Stone -> "Stein"
        WallPaint.Anthracite -> "Anthrazit"
    }

    override fun covering(v: Covering): String = when (v) {
        Covering.None -> "Nur Anstrich"
        Covering.Wallpaper -> "Tapete"
        Covering.Tiles -> "Fliesen 20×20"
        Covering.Metro -> "Metrofliesen"
        Covering.Brick -> "Sichtziegel"
        Covering.Stone -> "Naturstein"
        Covering.Wainscot -> "Wandvertäfelung"
        Covering.Boards -> "Profilholz"
        Covering.Material -> "Fotomaterial"
    }

    override fun parapet(v: Parapet): String = when (v) {
        Parapet.Railing -> "Geländer"
        Parapet.Wall -> "Mäuerchen"
        Parapet.Glass -> "Glas"
    }

    override fun lOrientation(v: LOrientation): String = when (v) {
        LOrientation.TopRight -> "Oben rechts"
        LOrientation.TopLeft -> "Oben links"
        LOrientation.BottomRight -> "Unten rechts"
        LOrientation.BottomLeft -> "Unten links"
    }

    override fun openingKind(v: OpeningKind): String = when (v) {
        OpeningKind.Door -> "Einflügelige Tür"
        OpeningKind.Door2 -> "Zweiflügelige Tür"
        OpeningKind.Window1 -> "Einflügeliges Fenster"
        OpeningKind.Window2 -> "Zweiflügeliges Fenster"
        OpeningKind.Balcony1 -> "Einflügelige Fenstertür"
        OpeningKind.Balcony2 -> "Zweiflügelige Fenstertür"
        OpeningKind.FrenchDoor -> "Fenstertür"
        OpeningKind.Passage -> "Öffnung ohne Tür"
        OpeningKind.Niche -> "Nische"
    }

    override fun passageStyle(v: PassageStyle): String = when (v) {
        PassageStyle.Square -> "Rechteckig"
        PassageStyle.Arched -> "Rundbogen"
        PassageStyle.Segmental -> "Segmentbogen"
        PassageStyle.Pointed -> "Spitzbogen"
    }

    override fun fixtureKind(v: FixtureKind): String = when (v) {
        FixtureKind.Radiator -> "Heizkörper"
        FixtureKind.Outlet -> "Steckdose"
        FixtureKind.Switch -> "Schalter"
        FixtureKind.Spotlight -> "Spot"
        FixtureKind.Neon -> "Langes Leuchtstoffrohr"
        FixtureKind.Chandelier -> "Hängeleuchte"
        FixtureKind.CeilingLight -> "Deckenleuchte"
        FixtureKind.LedStrip -> "LED-Streifen"
        FixtureKind.WaterPoint -> "Wasseranschluss"
        FixtureKind.WallLight -> "Wandleuchte"
        FixtureKind.WallSpot -> "Wandspot"
        FixtureKind.WallLedStrip -> "LED-Streifen an der Wand"
    }

    override fun layer(v: Layer): String = when (v) {
        Layer.WallLengths -> "Wandmaße"
        Layer.Dimensions -> "Bemaßung"
        Layer.Texts -> "Texte"
        Layer.Furniture -> "Möbel"
        Layer.Fixtures -> "Installationen (Steckdosen, Lampen, Heizkörper…)"
        Layer.Structure -> "Treppen, Säulen, Balken, Einzelwände"
        Layer.Rulers -> "Maßbänder"
        Layer.Underlay -> "Grundriss im Hintergrund"
    }

    override fun phase(v: Phase): String = when (v) {
        Phase.Existing -> "Bestand"
        Phase.Demolish -> "Abbruch"
        Phase.New -> "Neu"
    }

    override fun stairKind(v: StairKind): String = when (v) {
        StairKind.Straight -> "Gerader Lauf"
        StairKind.LTurn -> "L-Form (mit Podest)"
        StairKind.UTurn -> "U-Form (zwei Läufe)"
        StairKind.Spiral -> "Wendeltreppe"
        StairKind.StraightLanding -> "Gerade mit Podest"
        StairKind.LWinder -> "L-Form gewendelt"
    }

    override fun columnShape(v: ColumnShape): String = when (v) {
        ColumnShape.Square -> "Eckig"
        ColumnShape.Round -> "Rund"
    }

    override fun languageChoice(v: LanguageChoice): String = when (v) {
        LanguageChoice.Italian -> "Italiano"
        LanguageChoice.English -> "English"
        LanguageChoice.French -> "Français"
        LanguageChoice.Spanish -> "Español"
        LanguageChoice.German -> "Deutsch"
        LanguageChoice.System -> "Wie das System"
    }

    override fun themeChoice(v: ThemeChoice): String = when (v) {
        ThemeChoice.Light -> "Hell"
        ThemeChoice.Dark -> "Dunkel"
        ThemeChoice.System -> "Wie das System"
    }

    override fun alignment(v: Alignment): String = when (v) {
        Alignment.Left -> "Links"
        Alignment.CenterX -> "Mitte (waagerecht)"
        Alignment.Right -> "Rechts"
        Alignment.Top -> "Oben"
        Alignment.CenterY -> "Mitte (senkrecht)"
        Alignment.Bottom -> "Unten"
    }

    override fun snapKind(v: SnapKind): String = when (v) {
        SnapKind.Endpoint -> "Endpunkt"
        SnapKind.Midpoint -> "Mittelpunkt"
        SnapKind.Center -> "Zentrum"
        SnapKind.AlignVertical -> "Ausgerichtet"
        SnapKind.AlignHorizontal -> "Ausgerichtet"
        SnapKind.RightAngle -> "Rechter Winkel"
        SnapKind.Horizontal -> "Waagerecht"
        SnapKind.Vertical -> "Senkrecht"
        SnapKind.Diagonal -> "45°"
        SnapKind.Close -> "Schließen"
    }

    override fun roomType(v: RoomType): String = when (v) {
        RoomType.Altro -> "Sonstiges"
        RoomType.Soggiorno -> "Wohnzimmer"
        RoomType.Camera -> "Schlafzimmer"
        RoomType.Cucina -> "Küche"
        RoomType.Bagno -> "Bad"
        RoomType.Cameretta -> "Kinderzimmer"
        RoomType.Studio -> "Arbeitszimmer"
        RoomType.Corridoio -> "Flur"
        RoomType.Balcone -> "Balkon"
        RoomType.Terrazza -> "Terrasse"
    }

    override fun roomDefaultName(v: RoomType): String = when (v) {
        RoomType.Altro -> "Raum"
        RoomType.Soggiorno -> "Wohnzimmer"
        RoomType.Camera -> "Schlafzimmer"
        RoomType.Cucina -> "Küche"
        RoomType.Bagno -> "Bad"
        RoomType.Cameretta -> "Kinderzimmer"
        RoomType.Studio -> "Arbeitszimmer"
        RoomType.Corridoio -> "Flur"
        RoomType.Balcone -> "Balkon"
        RoomType.Terrazza -> "Terrasse"
    }

    override val stanzeComuni: List<String>
        get() = listOf(
            "Diele", "Eingang", "Eingangshalle", "Salon", "Esszimmer", "Essbereich", "Kochnische", "Kleine Küche",
            "Speisekammer", "Elternschlafzimmer", "Doppelzimmer", "Einzelzimmer", "Gästezimmer", "Ankleide", "Garderobe",
            "Bad-Vorraum", "WC", "Gästebad", "Elternbad", "Duschbad", "Waschküche", "Bügelzimmer",
            "Fitnessbereich", "Fitnessraum", "Sauna", "Spielzimmer", "Partykeller", "Bibliothek", "Offener Wohnbereich",
            "Abstellraum", "Keller", "Garage", "Dachboden", "Spitzboden", "Dachzimmer", "Technikraum", "Heizraum", "Treppenhaus",
            "Treppe", "Treppenpodest", "Loggia", "Veranda", "Portikus", "Garten", "Hof", "Hauptschlafzimmer",
            "Wohnzimmer mit Küche",
        )

    override val sinonimiStanze: Map<String, String> get() = emptyMap()

    override fun furnitureKind(v: ParametricFurniture.Kind): String = when (v) {
        ParametricFurniture.Kind.WARDROBE -> "Kleiderschrank nach Maß"
        ParametricFurniture.Kind.SLIDING_WARDROBE -> "Schwebetürenschrank nach Maß"
        ParametricFurniture.Kind.BED -> "Doppelbett nach Maß"
        ParametricFurniture.Kind.SINGLE_BED -> "Einzelbett nach Maß"
        ParametricFurniture.Kind.NIGHTSTAND -> "Nachttisch nach Maß"
        ParametricFurniture.Kind.CHEST -> "Kommode nach Maß"
        ParametricFurniture.Kind.BOOKCASE -> "Bücherregal nach Maß"
        ParametricFurniture.Kind.SIDEBOARD -> "Sideboard nach Maß"
        ParametricFurniture.Kind.TV_UNIT -> "TV-Möbel nach Maß"
        ParametricFurniture.Kind.SHELF -> "Wandregal nach Maß"
        ParametricFurniture.Kind.TABLE -> "Tisch nach Maß"
        ParametricFurniture.Kind.DESK -> "Schreibtisch nach Maß"
    }

    override fun kitchenKind(v: KitchenModules.Kind): String = when (v) {
        KitchenModules.Kind.BASE_DOORS -> "Unterschrank mit Türen"
        KitchenModules.Kind.BASE_DRAWERS -> "Unterschrank mit Schubladen"
        KitchenModules.Kind.SINK -> "Spülenunterschrank"
        KitchenModules.Kind.OVEN_HOB -> "Herdunterschrank mit Kochfeld"
        KitchenModules.Kind.DISHWASHER -> "Einbau-Geschirrspüler"
        KitchenModules.Kind.CORNER -> "Eckunterschrank"
        KitchenModules.Kind.WALL_DOORS -> "Hängeschrank"
        KitchenModules.Kind.WALL_HOOD -> "Hängeschrank mit Dunstabzug"
        KitchenModules.Kind.TALL_PANTRY -> "Vorratsschrank"
        KitchenModules.Kind.TALL_OVEN -> "Backofen und Mikrowelle"
        KitchenModules.Kind.TALL_FRIDGE -> "Kühlschrank-Hochschrank"
    }

    override fun categoria(chiave: String): String = when (chiave) {
        "Soggiorno" -> "Wohnzimmer"
        "Pranzo" -> "Essbereich"
        "Cucina componibile" -> "Einbauküche"
        "Cucina" -> "Küche"
        "Camera" -> "Schlafzimmer"
        "Bagno" -> "Bad"
        "Studio" -> "Arbeitszimmer"
        "Luci" -> "Leuchten"
        "Tappeti" -> "Teppiche"
        "Elettrodomestici e TV" -> "Elektrogeräte und TV"
        "Decori" -> "Deko"
        "Piante" -> "Pflanzen"
        "Esterni" -> "Außenbereich"
        "Sport e hobby" -> "Sport und Hobby"
        "Garage e cantina" -> "Garage und Keller"
        "Intonaco e cemento" -> "Putz und Beton"
        "Marmo e pietra" -> "Marmor und Stein"
        "Mattoni e pietra" -> "Ziegel und Stein"
        "Moquette" -> "Teppichboden"
        "Parquet e legno" -> "Parkett und Holz"
        "Piastrelle" -> "Fliesen"
        "Tessuti" -> "Stoffe"
        else -> chiave
    }

    override fun arredo(nome: String): String = CatalogoDe.arredi[nome] ?: nome

    override fun materiale(nome: String): String = CatalogoDe.materiali[nome] ?: nome

    /** "Teppich aus Jute", "Runder Teppich aus Jute" (il materiale è già in tedesco, con la maiuscola). */
    override fun tappeto(materiale: String, tondo: Boolean): String =
        (if (tondo) "Runder Teppich aus " else "Teppich aus ") + materiale

    override val pianoCucina: String get() = " · Arbeitsplatte "

    override fun kitchenFront(v: KitchenModules.Front): String = when (v) {
        KitchenModules.Front.WHITE -> "Weiß matt"
        KitchenModules.Front.PEARL -> "Perlgrau"
        KitchenModules.Front.ANTHRACITE -> "Anthrazitgrau"
        KitchenModules.Front.SAGE -> "Salbeigrün"
        KitchenModules.Front.NAVY -> "Nachtblau"
        KitchenModules.Front.OAK -> "Eiche hell"
        KitchenModules.Front.WALNUT -> "Nussbaum"
    }

    override fun kitchenTop(v: KitchenModules.Top): String = when (v) {
        KitchenModules.Top.LAMINATE_WHITE -> "Laminat weiß"
        KitchenModules.Top.LAMINATE_GREY -> "Laminat grau"
        KitchenModules.Top.LAMINATE_BLACK -> "Laminat schwarz"
        KitchenModules.Top.WOOD -> "Eichenholz"
        KitchenModules.Top.CARRARA -> "Carrara-Marmor"
        KitchenModules.Top.STONE -> "Stein beige"
        KitchenModules.Top.CONCRETE -> "Beton"
        KitchenModules.Top.MARQUINIA -> "Marmor Nero Marquina"
    }

    override fun phaseView(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Gelb und Rot"
        PhaseView.Existing -> "Bestand"
        PhaseView.Project -> "Planung"
    }

    override fun phaseViewTitle(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Vergleichsplan"
        PhaseView.Existing -> "Bestandsplan"
        PhaseView.Project -> "Planung"
    }

    override val tavolozza: Map<Long, String> = mapOf(
        0xFFFAFAF8 to "Reinweiß", 0xFFF5F1E8 to "Milch", 0xFFF1E6CF to "Creme", 0xFFE8DECB to "Leinen",
        0xFFBFB2A3 to "Taupe", 0xFFCBC3B6 to "Greige", 0xFFA9A9A4 to "Beton", 0xFF6E7378 to "Schiefer",
        0xFF3F4246 to "Graphit", 0xFF26272A to "Schwarz", 0xFFEBCFC8 to "Puder", 0xFFE8B4B8 to "Rosa",
        0xFFD2CBE3 to "Lavendel", 0xFFB9A6D1 to "Flieder", 0xFFCDE6D6 to "Minze", 0xFFA9D6D0 to "Aquamarin",
        0xFFB8D4EA to "Himmel", 0xFF9DB3C4 to "Taubenblau", 0xFFD9A441 to "Ocker", 0xFFC89F3A to "Senf",
        0xFFCB8467 to "Helles Ziegelrot", 0xFF9E4B2D to "Rost", 0xFF6E2632 to "Bordeaux", 0xFFB5332E to "Rot",
        0xFFE9806B to "Koralle", 0xFFF2C0A0 to "Pfirsich", 0xFF8B8B55 to "Oliv", 0xFF3E5E44 to "Waldgrün",
        0xFF2E7D63 to "Smaragd", 0xFF22565E to "Petrol", 0xFF243A5E to "Nachtblau", 0xFF3C6FA8 to "Blau",
        0xFF7E9CB9 to "Fliegerblau", 0xFF7A5A3C to "Tabak", 0xFF4E3629 to "Schokolade", 0xFFB39377 to "Milchkaffee",
    )
}
