package com.sagoma.planimetria.testi.fr

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

object NomiFr : Nomi() {
    override fun doorModel(v: DoorModel): String = when (v) {
        DoorModel.Flush -> "Lisse"
        DoorModel.Panels -> "À panneaux"
        DoorModel.Glazed -> "Vitrée"
        DoorModel.Planks -> "À lames"
        DoorModel.Modern -> "Moderne"
    }

    override fun finish(v: Finish): String = when (v) {
        Finish.Oak -> "Chêne"
        Finish.White -> "Blanc"
        Finish.LightOak -> "Chêne clair"
        Finish.Walnut -> "Noyer"
        Finish.Grey -> "Gris"
        Finish.Anthracite -> "Anthracite"
    }

    override fun windowModel(v: WindowModel): String = when (v) {
        WindowModel.Classic -> "Classique"
        WindowModel.Minimal -> "Minimaliste"
        WindowModel.English -> "Ouverture extérieure"
    }

    override fun shading(v: Shading): String = when (v) {
        Shading.None -> "Aucune"
        Shading.Shutters -> "Volets"
        Shading.RollerShutter -> "Volet roulant"
    }

    override fun stairStructure(v: StairStructure): String = when (v) {
        StairStructure.Masonry -> "Maçonnée"
        StairStructure.Floating -> "Suspendue"
        StairStructure.Stringers -> "À limons"
    }

    override fun stairMaterial(v: StairMaterial): String = when (v) {
        StairMaterial.Wood -> "Bois"
        StairMaterial.DarkWood -> "Bois foncé"
        StairMaterial.Marble -> "Marbre"
        StairMaterial.Stone -> "Pierre"
        StairMaterial.Concrete -> "Béton"
    }

    override fun stairRailing(v: StairRailing): String = when (v) {
        StairRailing.None -> "Aucun"
        StairRailing.Metal -> "Métal"
        StairRailing.Glass -> "Verre"
        StairRailing.Wood -> "Bois"
    }

    override fun radiatorModel(v: RadiatorModel): String = when (v) {
        RadiatorModel.Panel -> "Panneau"
        RadiatorModel.Fins -> "À colonnes"
        RadiatorModel.TowelRail -> "Sèche-serviettes"
    }

    override fun lampModel(v: LampModel): String = when (v) {
        LampModel.Modern -> "Moderne"
        LampModel.Classic -> "Classique"
        LampModel.Bell -> "Cloche"
        LampModel.Industrial -> "Industriel"
    }

    override fun floorFinish(v: FloorFinish): String = when (v) {
        FloorFinish.RoomColor -> "Couleur de la pièce"
        FloorFinish.OakParquet -> "Parquet chêne"
        FloorFinish.WalnutParquet -> "Parquet noyer"
        FloorFinish.LightTiles -> "Carrelage clair"
        FloorFinish.GreyTiles -> "Carrelage gris"
        FloorFinish.Marble -> "Marbre"
        FloorFinish.Terracotta -> "Tomettes"
    }

    override fun wallPaint(v: WallPaint): String = when (v) {
        WallPaint.White -> "Blanc"
        WallPaint.Ivory -> "Ivoire"
        WallPaint.PearlGrey -> "Gris perle"
        WallPaint.Sage -> "Sauge"
        WallPaint.Sky -> "Bleu ciel"
        WallPaint.Sand -> "Sable"
        WallPaint.Terracotta -> "Terre cuite"
        WallPaint.Yellow -> "Jaune"
        WallPaint.Salmon -> "Vieux rose"
        WallPaint.Brick -> "Brique"
        WallPaint.Stone -> "Pierre"
        WallPaint.Anthracite -> "Anthracite"
    }

    override fun covering(v: Covering): String = when (v) {
        Covering.None -> "Peinture seule"
        Covering.Wallpaper -> "Papier peint"
        Covering.Tiles -> "Carreaux 20×20"
        Covering.Metro -> "Carreaux métro"
        Covering.Brick -> "Brique apparente"
        Covering.Stone -> "Pierre"
        Covering.Wainscot -> "Soubassement"
        Covering.Boards -> "Lambris"
        Covering.Material -> "Matériau photographique"
    }

    override fun parapet(v: Parapet): String = when (v) {
        Parapet.Railing -> "Rambarde"
        Parapet.Wall -> "Muret"
        Parapet.Glass -> "Verre"
    }

    override fun lOrientation(v: LOrientation): String = when (v) {
        LOrientation.TopRight -> "En haut à droite"
        LOrientation.TopLeft -> "En haut à gauche"
        LOrientation.BottomRight -> "En bas à droite"
        LOrientation.BottomLeft -> "En bas à gauche"
    }

    override fun openingKind(v: OpeningKind): String = when (v) {
        OpeningKind.Door -> "Porte simple"
        OpeningKind.Door2 -> "Porte double"
        OpeningKind.Window1 -> "Fenêtre simple"
        OpeningKind.Window2 -> "Fenêtre double"
        OpeningKind.Balcony1 -> "Porte-fenêtre simple"
        OpeningKind.Balcony2 -> "Porte-fenêtre double"
        OpeningKind.FrenchDoor -> "Porte-fenêtre"
        OpeningKind.Passage -> "Baie sans porte"
    }

    override fun passageStyle(v: PassageStyle): String = when (v) {
        PassageStyle.Square -> "Carrée"
        PassageStyle.Arched -> "En arc"
    }

    override fun fixtureKind(v: FixtureKind): String = when (v) {
        FixtureKind.Radiator -> "Radiateur"
        FixtureKind.Outlet -> "Prise électrique"
        FixtureKind.Switch -> "Interrupteur"
        FixtureKind.Spotlight -> "Spot"
        FixtureKind.Neon -> "Néon long"
        FixtureKind.Chandelier -> "Lustre"
        FixtureKind.CeilingLight -> "Plafonnier"
        FixtureKind.LedStrip -> "Ruban LED"
        FixtureKind.WaterPoint -> "Point d'eau"
        FixtureKind.WallLight -> "Applique"
        FixtureKind.WallSpot -> "Spot mural"
        FixtureKind.WallLedStrip -> "Ruban LED mural"
    }

    override fun layer(v: Layer): String = when (v) {
        Layer.WallLengths -> "Longueurs des murs"
        Layer.Dimensions -> "Cotes"
        Layer.Texts -> "Textes"
        Layer.Furniture -> "Mobilier"
        Layer.Fixtures -> "Équipements (prises, lumières, radiateurs…)"
        Layer.Structure -> "Escaliers, poteaux, poutres, murs libres"
        Layer.Rulers -> "Mètres"
        Layer.Underlay -> "Plan de fond"
    }

    override fun phase(v: Phase): String = when (v) {
        Phase.Existing -> "Existant"
        Phase.Demolish -> "À démolir"
        Phase.New -> "Neuf"
    }

    override fun stairKind(v: StairKind): String = when (v) {
        StairKind.Straight -> "Volée droite"
        StairKind.LTurn -> "En L (avec palier)"
        StairKind.UTurn -> "En U (deux volées)"
        StairKind.Spiral -> "Colimaçon"
        StairKind.StraightLanding -> "Droit avec palier"
        StairKind.LWinder -> "En L balancé"
    }

    override fun columnShape(v: ColumnShape): String = when (v) {
        ColumnShape.Square -> "Carré"
        ColumnShape.Round -> "Rond"
    }

    override fun languageChoice(v: LanguageChoice): String = when (v) {
        LanguageChoice.Italian -> "Italiano"
        LanguageChoice.English -> "English"
        LanguageChoice.French -> "Français"
        LanguageChoice.Spanish -> "Español"
        LanguageChoice.System -> "Comme le système"
    }

    override fun themeChoice(v: ThemeChoice): String = when (v) {
        ThemeChoice.Light -> "Clair"
        ThemeChoice.Dark -> "Sombre"
        ThemeChoice.System -> "Comme le système"
    }

    override fun alignment(v: Alignment): String = when (v) {
        Alignment.Left -> "Gauche"
        Alignment.CenterX -> "Centre (horiz.)"
        Alignment.Right -> "Droite"
        Alignment.Top -> "Haut"
        Alignment.CenterY -> "Centre (vert.)"
        Alignment.Bottom -> "Bas"
    }

    override fun snapKind(v: SnapKind): String = when (v) {
        SnapKind.Endpoint -> "Extrémité"
        SnapKind.Midpoint -> "Milieu"
        SnapKind.Center -> "Centre"
        SnapKind.AlignVertical -> "Aligné"
        SnapKind.AlignHorizontal -> "Aligné"
        SnapKind.RightAngle -> "Angle droit"
        SnapKind.Horizontal -> "Horizontal"
        SnapKind.Vertical -> "Vertical"
        SnapKind.Diagonal -> "45°"
        SnapKind.Close -> "Fermer"
    }

    override fun roomType(v: RoomType): String = when (v) {
        RoomType.Altro -> "Autre"
        RoomType.Soggiorno -> "Séjour"
        RoomType.Camera -> "Chambre"
        RoomType.Cucina -> "Cuisine"
        RoomType.Bagno -> "Salle de bain"
        RoomType.Cameretta -> "Chambre d'enfant"
        RoomType.Studio -> "Bureau"
        RoomType.Corridoio -> "Couloir"
        RoomType.Balcone -> "Balcon"
        RoomType.Terrazza -> "Terrasse"
    }

    override fun roomDefaultName(v: RoomType): String = when (v) {
        RoomType.Altro -> "Pièce"
        RoomType.Soggiorno -> "Séjour"
        RoomType.Camera -> "Chambre"
        RoomType.Cucina -> "Cuisine"
        RoomType.Bagno -> "Salle de bain"
        RoomType.Cameretta -> "Chambre d'enfant"
        RoomType.Studio -> "Bureau"
        RoomType.Corridoio -> "Couloir"
        RoomType.Balcone -> "Balcon"
        RoomType.Terrazza -> "Terrasse"
    }

    override fun furnitureKind(v: ParametricFurniture.Kind): String = when (v) {
        ParametricFurniture.Kind.WARDROBE -> "Armoire sur mesure"
        ParametricFurniture.Kind.SLIDING_WARDROBE -> "Armoire coulissante sur mesure"
        ParametricFurniture.Kind.BED -> "Lit double sur mesure"
        ParametricFurniture.Kind.SINGLE_BED -> "Lit simple sur mesure"
        ParametricFurniture.Kind.NIGHTSTAND -> "Table de chevet sur mesure"
        ParametricFurniture.Kind.CHEST -> "Commode sur mesure"
        ParametricFurniture.Kind.BOOKCASE -> "Bibliothèque sur mesure"
        ParametricFurniture.Kind.SIDEBOARD -> "Buffet sur mesure"
        ParametricFurniture.Kind.TV_UNIT -> "Meuble TV sur mesure"
        ParametricFurniture.Kind.SHELF -> "Étagère sur mesure"
        ParametricFurniture.Kind.TABLE -> "Table sur mesure"
        ParametricFurniture.Kind.DESK -> "Bureau sur mesure"
    }

    override fun kitchenKind(v: KitchenModules.Kind): String = when (v) {
        KitchenModules.Kind.BASE_DOORS -> "Meuble bas à portes"
        KitchenModules.Kind.BASE_DRAWERS -> "Meuble bas à tiroirs"
        KitchenModules.Kind.SINK -> "Meuble sous-évier"
        KitchenModules.Kind.OVEN_HOB -> "Meuble four et plaque"
        KitchenModules.Kind.DISHWASHER -> "Lave-vaisselle encastré"
        KitchenModules.Kind.CORNER -> "Meuble bas d'angle"
        KitchenModules.Kind.WALL_DOORS -> "Meuble haut"
        KitchenModules.Kind.WALL_HOOD -> "Meuble haut avec hotte"
        KitchenModules.Kind.TALL_PANTRY -> "Colonne garde-manger"
        KitchenModules.Kind.TALL_OVEN -> "Colonne four et micro-ondes"
        KitchenModules.Kind.TALL_FRIDGE -> "Colonne réfrigérateur"
    }

    override fun categoria(chiave: String): String = when (chiave) {
        "Soggiorno" -> "Séjour"
        "Pranzo" -> "Salle à manger"
        "Cucina componibile" -> "Cuisine modulaire"
        "Cucina" -> "Cuisine"
        "Camera" -> "Chambre"
        "Bagno" -> "Salle de bain"
        "Studio" -> "Bureau"
        "Luci" -> "Luminaires"
        "Tappeti" -> "Tapis"
        "Elettrodomestici e TV" -> "Électroménager et TV"
        "Decori" -> "Décoration"
        "Piante" -> "Plantes"
        "Esterni" -> "Extérieur"
        "Sport e hobby" -> "Sport et loisirs"
        "Garage e cantina" -> "Garage et cave"
        "Intonaco e cemento" -> "Enduit et béton"
        "Marmo e pietra" -> "Marbre et pierre"
        "Mattoni e pietra" -> "Brique et pierre"
        "Moquette" -> "Moquette"
        "Parquet e legno" -> "Parquet et bois"
        "Piastrelle" -> "Carrelage"
        "Tessuti" -> "Tissus"
        else -> chiave
    }

    override fun arredo(nome: String): String = CatalogoFr.arredi[nome] ?: nome

    override fun materiale(nome: String): String = CatalogoFr.materiali[nome] ?: nome

    /** "Tapis en jute", "Tapis rond en jute" (le matériau est déjà en français). */
    override fun tappeto(materiale: String, tondo: Boolean): String =
        (if (tondo) "Tapis rond en " else "Tapis en ") + Francese.minuscola(materiale)

    override val pianoCucina: String get() = " · plan "

    override fun kitchenFront(v: KitchenModules.Front): String = when (v) {
        KitchenModules.Front.WHITE -> "Blanc mat"
        KitchenModules.Front.PEARL -> "Gris perle"
        KitchenModules.Front.ANTHRACITE -> "Gris anthracite"
        KitchenModules.Front.SAGE -> "Vert sauge"
        KitchenModules.Front.NAVY -> "Bleu nuit"
        KitchenModules.Front.OAK -> "Chêne clair"
        KitchenModules.Front.WALNUT -> "Noyer"
    }

    override fun kitchenTop(v: KitchenModules.Top): String = when (v) {
        KitchenModules.Top.LAMINATE_WHITE -> "Stratifié blanc"
        KitchenModules.Top.LAMINATE_GREY -> "Stratifié gris"
        KitchenModules.Top.LAMINATE_BLACK -> "Stratifié noir"
        KitchenModules.Top.WOOD -> "Bois de chêne"
        KitchenModules.Top.CARRARA -> "Marbre de Carrare"
        KitchenModules.Top.STONE -> "Pierre beige"
        KitchenModules.Top.CONCRETE -> "Béton"
        KitchenModules.Top.MARQUINIA -> "Marbre Nero Marquina"
    }

    override fun phaseView(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Jaunes et rouges"
        PhaseView.Existing -> "État existant"
        PhaseView.Project -> "État projeté"
    }

    override fun phaseViewTitle(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Plan comparatif"
        PhaseView.Existing -> "État existant"
        PhaseView.Project -> "État projeté"
    }

    override val tavolozza: Map<Long, String> = mapOf(
        0xFFFAFAF8 to "Blanc pur", 0xFFF5F1E8 to "Lait", 0xFFF1E6CF to "Crème", 0xFFE8DECB to "Lin",
        0xFFBFB2A3 to "Taupe", 0xFFCBC3B6 to "Grège", 0xFFA9A9A4 to "Béton", 0xFF6E7378 to "Ardoise",
        0xFF3F4246 to "Graphite", 0xFF26272A to "Noir", 0xFFEBCFC8 to "Poudre", 0xFFE8B4B8 to "Rose",
        0xFFD2CBE3 to "Lavande", 0xFFB9A6D1 to "Glycine", 0xFFCDE6D6 to "Menthe", 0xFFA9D6D0 to "Aigue-marine",
        0xFFB8D4EA to "Ciel", 0xFF9DB3C4 to "Bleu poudré", 0xFFD9A441 to "Ocre", 0xFFC89F3A to "Moutarde",
        0xFFCB8467 to "Brique clair", 0xFF9E4B2D to "Rouille", 0xFF6E2632 to "Bordeaux", 0xFFB5332E to "Rouge",
        0xFFE9806B to "Corail", 0xFFF2C0A0 to "Pêche", 0xFF8B8B55 to "Olive", 0xFF3E5E44 to "Forêt",
        0xFF2E7D63 to "Émeraude", 0xFF22565E to "Bleu pétrole", 0xFF243A5E to "Bleu nuit", 0xFF3C6FA8 to "Bleu",
        0xFF7E9CB9 to "Bleu aviateur", 0xFF7A5A3C to "Tabac", 0xFF4E3629 to "Chocolat", 0xFFB39377 to "Café au lait",
    )
}
