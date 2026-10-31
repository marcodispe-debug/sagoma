package com.sagoma.planimetria.testi

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
import com.sagoma.planimetria.model.Parapet
import com.sagoma.planimetria.model.ParametricFurniture
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

/** Nomi di tipi, modelli e finiture, come li vede l'utente (le costanti nel codice e nei file restano in inglese). */
open class Nomi {
    open fun doorModel(v: DoorModel): String = when (v) {
        DoorModel.Flush -> "Liscia"
        DoorModel.Panels -> "A pannelli"
        DoorModel.Glazed -> "Con vetro"
        DoorModel.Planks -> "A doghe"
        DoorModel.Modern -> "Moderna"
        DoorModel.Armored -> "Blindata"
        DoorModel.AllGlass -> "Tutto vetro"
    }

    open fun finish(v: Finish): String = when (v) {
        Finish.Oak -> "Rovere"
        Finish.White -> "Bianco"
        Finish.LightOak -> "Rovere chiaro"
        Finish.Walnut -> "Noce"
        Finish.Grey -> "Grigio"
        Finish.Anthracite -> "Antracite"
    }

    open fun windowShape(v: WindowShape): String = when (v) {
        WindowShape.Rectangle -> "Rettangolare"
        WindowShape.Arched -> "Ad arco"
        WindowShape.Round -> "Tonda"
    }

    open fun sashType(v: SashType): String = when (v) {
        SashType.Casement -> "Battente"
        SashType.TiltTurn -> "Anta-ribalta"
        SashType.Hopper -> "Vasistas"
        SashType.Fixed -> "Fissa"
        SashType.Pivot -> "A bilico"
    }

    open fun windowModel(v: WindowModel): String = when (v) {
        WindowModel.Classic -> "Classica"
        WindowModel.Minimal -> "Minimal"
        WindowModel.English -> "All'inglese"
        WindowModel.Loft -> "Loft"
        WindowModel.Wood -> "Legno con fermavetro"
        WindowModel.GlassBlock -> "Vetromattone"
    }

    open fun framePosition(v: FramePosition): String = when (v) {
        FramePosition.Center -> "A metà muro"
        FramePosition.Outside -> "Verso l'esterno"
        FramePosition.Inside -> "Verso l'interno"
    }

    open fun shading(v: Shading): String = when (v) {
        Shading.None -> "Nessuno"
        Shading.Shutters -> "Persiane"
        Shading.RollerShutter -> "Tapparella"
        Shading.Venetian -> "Veneziana esterna"
        Shading.InteriorShutters -> "Scuri interni"
        Shading.RomanShutter -> "Persiana alla romana"
        Shading.SlidingShutters -> "Persiane scorrevoli"
    }

    open fun stairStructure(v: StairStructure): String = when (v) {
        StairStructure.Masonry -> "Muratura"
        StairStructure.Floating -> "A sbalzo"
        StairStructure.Stringers -> "A giorno"
    }

    open fun stairMaterial(v: StairMaterial): String = when (v) {
        StairMaterial.Wood -> "Legno"
        StairMaterial.DarkWood -> "Legno scuro"
        StairMaterial.Marble -> "Marmo"
        StairMaterial.Stone -> "Pietra"
        StairMaterial.Concrete -> "Cemento"
    }

    open fun stairRailing(v: StairRailing): String = when (v) {
        StairRailing.None -> "Nessuna"
        StairRailing.Metal -> "Metallo"
        StairRailing.Glass -> "Vetro"
        StairRailing.Wood -> "Legno"
    }

    open fun radiatorModel(v: RadiatorModel): String = when (v) {
        RadiatorModel.Panel -> "A piastra"
        RadiatorModel.Fins -> "A elementi"
        RadiatorModel.TowelRail -> "Scaldasalviette"
    }

    open fun lampModel(v: LampModel): String = when (v) {
        LampModel.Modern -> "Moderno"
        LampModel.Classic -> "Classico"
        LampModel.Bell -> "A campana"
        LampModel.Industrial -> "Industriale"
    }

    open fun floorFinish(v: FloorFinish): String = when (v) {
        FloorFinish.RoomColor -> "Colore stanza"
        FloorFinish.OakParquet -> "Parquet rovere"
        FloorFinish.WalnutParquet -> "Parquet noce"
        FloorFinish.LightTiles -> "Gres chiaro"
        FloorFinish.GreyTiles -> "Gres grigio"
        FloorFinish.Marble -> "Marmo"
        FloorFinish.Terracotta -> "Cotto"
    }

    open fun wallPaint(v: WallPaint): String = when (v) {
        WallPaint.White -> "Bianco"
        WallPaint.Ivory -> "Avorio"
        WallPaint.PearlGrey -> "Grigio perla"
        WallPaint.Sage -> "Salvia"
        WallPaint.Sky -> "Azzurro"
        WallPaint.Sand -> "Sabbia"
        WallPaint.Terracotta -> "Terracotta"
        WallPaint.Yellow -> "Giallo"
        WallPaint.Salmon -> "Rosa antico"
        WallPaint.Brick -> "Mattone"
        WallPaint.Stone -> "Pietra"
        WallPaint.Anthracite -> "Antracite"
    }

    open fun covering(v: Covering): String = when (v) {
        Covering.None -> "Solo pittura"
        Covering.Wallpaper -> "Carta da parati"
        Covering.Tiles -> "Piastrelle 20×20"
        Covering.Metro -> "Piastrelle diamantate"
        Covering.Brick -> "Mattoni a vista"
        Covering.Stone -> "Pietra"
        Covering.Wainscot -> "Boiserie"
        Covering.Boards -> "Perlinato"
        Covering.Material -> "Materiale fotografico"
    }

    open fun parapet(v: Parapet): String = when (v) {
        Parapet.Railing -> "Ringhiera"
        Parapet.Wall -> "Muretto"
        Parapet.Glass -> "Vetro"
    }

    open fun lOrientation(v: LOrientation): String = when (v) {
        LOrientation.TopRight -> "In alto a destra"
        LOrientation.TopLeft -> "In alto a sinistra"
        LOrientation.BottomRight -> "In basso a destra"
        LOrientation.BottomLeft -> "In basso a sinistra"
    }

    open fun openingKind(v: OpeningKind): String = when (v) {
        OpeningKind.Door -> "Porta ad 1 anta"
        OpeningKind.Door2 -> "Porta a 2 ante"
        OpeningKind.Window1 -> "Finestra 1 anta"
        OpeningKind.Window2 -> "Finestra 2 ante"
        OpeningKind.Balcony1 -> "Balcone 1 anta"
        OpeningKind.Balcony2 -> "Balcone 2 ante"
        OpeningKind.FrenchDoor -> "Porta-finestra"
        OpeningKind.Passage -> "Apertura senza porta"
        OpeningKind.Niche -> "Nicchia"
    }

    open fun passageStyle(v: PassageStyle): String = when (v) {
        PassageStyle.Square -> "Squadrata"
        PassageStyle.Arched -> "Ad arco"
        PassageStyle.Segmental -> "Ad arco ribassato"
        PassageStyle.Pointed -> "A sesto acuto"
    }

    open fun fixtureKind(v: FixtureKind): String = when (v) {
        FixtureKind.Radiator -> "Calorifero"
        FixtureKind.Outlet -> "Presa di corrente"
        FixtureKind.Switch -> "Interruttore"
        FixtureKind.Spotlight -> "Faretto"
        FixtureKind.Neon -> "Neon lungo"
        FixtureKind.Chandelier -> "Lampadario"
        FixtureKind.CeilingLight -> "Plafoniera"
        FixtureKind.LedStrip -> "Striscia LED"
        FixtureKind.WaterPoint -> "Punto acqua"
        FixtureKind.WallLight -> "Plafoniera a parete"
        FixtureKind.WallSpot -> "Faretto a parete"
        FixtureKind.WallLedStrip -> "Striscia LED a parete"
    }

    open fun layer(v: Layer): String = when (v) {
        Layer.WallLengths -> "Misure dei muri"
        Layer.Dimensions -> "Quote"
        Layer.Texts -> "Testi"
        Layer.Furniture -> "Arredi"
        Layer.Fixtures -> "Impianti (prese, luci, caloriferi…)"
        Layer.Structure -> "Scale, colonne, travi, muri singoli"
        Layer.Rulers -> "Metri"
        Layer.Underlay -> "Pianta di sfondo"
    }

    open fun phase(v: Phase): String = when (v) {
        Phase.Existing -> "Esistente"
        Phase.Demolish -> "Da demolire"
        Phase.New -> "Nuovo"
    }

    open fun stairKind(v: StairKind): String = when (v) {
        StairKind.Straight -> "Rampa dritta"
        StairKind.LTurn -> "A L (con pianerottolo)"
        StairKind.UTurn -> "A U (due rampe)"
        StairKind.Spiral -> "A chiocciola"
        StairKind.StraightLanding -> "Dritta con pianerottolo"
        StairKind.LWinder -> "A L con ventaglio"
    }

    open fun columnShape(v: ColumnShape): String = when (v) {
        ColumnShape.Square -> "Quadrata"
        ColumnShape.Round -> "Rotonda"
    }

    /** Le lingue si scrivono sempre col loro nome, così le riconosce anche chi non legge la lingua attuale. */
    open fun languageChoice(v: LanguageChoice): String = when (v) {
        LanguageChoice.Italian -> "Italiano"
        LanguageChoice.English -> "English"
        LanguageChoice.French -> "Français"
        LanguageChoice.Spanish -> "Español"
        LanguageChoice.System -> "Come il sistema"
    }

    open fun themeChoice(v: ThemeChoice): String = when (v) {
        ThemeChoice.Light -> "Chiaro"
        ThemeChoice.Dark -> "Scuro"
        ThemeChoice.System -> "Come il sistema"
    }

    open fun alignment(v: Alignment): String = when (v) {
        Alignment.Left -> "A sinistra"
        Alignment.CenterX -> "Al centro (orizz.)"
        Alignment.Right -> "A destra"
        Alignment.Top -> "In alto"
        Alignment.CenterY -> "Al centro (vert.)"
        Alignment.Bottom -> "In basso"
    }

    open fun snapKind(v: SnapKind): String = when (v) {
        SnapKind.Endpoint -> "Estremità"
        SnapKind.Midpoint -> "Punto medio"
        SnapKind.Center -> "Centro"
        SnapKind.AlignVertical -> "Allineato"
        SnapKind.AlignHorizontal -> "Allineato"
        SnapKind.RightAngle -> "Angolo retto"
        SnapKind.Horizontal -> "Orizzontale"
        SnapKind.Vertical -> "Verticale"
        SnapKind.Diagonal -> "45°"
        SnapKind.Close -> "Chiudi"
    }

    open fun roomType(v: RoomType): String = when (v) {
        RoomType.Altro -> "Altro"
        RoomType.Soggiorno -> "Soggiorno"
        RoomType.Camera -> "Camera da letto"
        RoomType.Cucina -> "Cucina"
        RoomType.Bagno -> "Bagno"
        RoomType.Cameretta -> "Cameretta"
        RoomType.Studio -> "Studio"
        RoomType.Corridoio -> "Corridoio"
        RoomType.Balcone -> "Balcone"
        RoomType.Terrazza -> "Terrazza"
    }

    open fun roomDefaultName(v: RoomType): String = when (v) {
        RoomType.Altro -> "Stanza"
        RoomType.Soggiorno -> "Soggiorno"
        RoomType.Camera -> "Camera da letto"
        RoomType.Cucina -> "Cucina"
        RoomType.Bagno -> "Bagno"
        RoomType.Cameretta -> "Cameretta"
        RoomType.Studio -> "Studio"
        RoomType.Corridoio -> "Corridoio"
        RoomType.Balcone -> "Balcone"
        RoomType.Terrazza -> "Terrazza"
    }

    /**
     * Nomi di stanza comuni oltre a quelli automatici, come si scrivono sulle piantine o a mano ("Camera doppia", "Disimpegno"):
     * cambiando lingua si traducono anche questi ([com.sagoma.planimetria.model.AutoNames]). Stesso ordine in tutte le lingue: il
     * nome n di una lingua è la traduzione del nome n delle altre.
     */
    open val stanzeComuni: List<String>
        get() = listOf(
            "Disimpegno", "Ingresso", "Atrio", "Salotto", "Sala da pranzo", "Pranzo", "Angolo cottura", "Cucinotto",
            "Dispensa", "Camera matrimoniale", "Camera doppia", "Camera singola", "Camera ospiti", "Cabina armadio",
            "Guardaroba", "Antibagno", "WC", "Bagno di servizio", "Bagno padronale", "Doccia", "Lavanderia", "Stireria",
            "Area fitness", "Palestra", "Sauna", "Sala giochi", "Taverna", "Biblioteca", "Open space", "Ripostiglio",
            "Cantina", "Garage", "Soffitta", "Sottotetto", "Mansarda", "Locale tecnico", "Centrale termica", "Vano scala",
            "Scala", "Pianerottolo", "Loggia", "Veranda", "Portico", "Giardino", "Cortile", "Camera padronale",
            "Soggiorno con cucina",
        )

    /**
     * Altri modi di scrivere un nome di stanza in questa lingua (abbreviazioni delle catastali, sinonimi): si traducono come il
     * nome a cui portano, che è un nome automatico o uno di [stanzeComuni]. Solo dell'italiano: le altre lingue non ne hanno.
     */
    open val sinonimiStanze: Map<String, String>
        get() = mapOf(
            "Camera" to "Camera da letto",
            "Letto" to "Camera da letto",
            "Matrimoniale" to "Camera matrimoniale",
            "Salone" to "Soggiorno",
            "Living" to "Soggiorno",
            "Sala" to "Soggiorno",
            "Poggiolo" to "Balcone",
            "Terrazzo" to "Terrazza",
            "Ufficio" to "Studio",
            "Disimp." to "Disimpegno",
            "Dis." to "Disimpegno",
            "Ing." to "Ingresso",
            "Rip." to "Ripostiglio",
            "Lavatoio" to "Lavanderia",
            "Vano tecnico" to "Locale tecnico",
            "Locale caldaia" to "Centrale termica",
            "Box" to "Garage",
            "Autorimessa" to "Garage",
            "Bagno ospiti" to "Bagno di servizio",
            "Cameretta bambini" to "Cameretta",
        )

    open fun furnitureKind(v: ParametricFurniture.Kind): String = when (v) {
        ParametricFurniture.Kind.WARDROBE -> "Armadio su misura"
        ParametricFurniture.Kind.SLIDING_WARDROBE -> "Armadio scorrevole su misura"
        ParametricFurniture.Kind.BED -> "Letto matrimoniale su misura"
        ParametricFurniture.Kind.SINGLE_BED -> "Letto singolo su misura"
        ParametricFurniture.Kind.NIGHTSTAND -> "Comodino su misura"
        ParametricFurniture.Kind.CHEST -> "Cassettiera su misura"
        ParametricFurniture.Kind.BOOKCASE -> "Libreria su misura"
        ParametricFurniture.Kind.SIDEBOARD -> "Madia su misura"
        ParametricFurniture.Kind.TV_UNIT -> "Mobile TV su misura"
        ParametricFurniture.Kind.SHELF -> "Mensola su misura"
        ParametricFurniture.Kind.TABLE -> "Tavolo su misura"
        ParametricFurniture.Kind.DESK -> "Scrivania su misura"
    }

    open fun kitchenKind(v: KitchenModules.Kind): String = when (v) {
        KitchenModules.Kind.BASE_DOORS -> "Base con ante"
        KitchenModules.Kind.BASE_DRAWERS -> "Base con cassetti"
        KitchenModules.Kind.SINK -> "Base con lavello"
        KitchenModules.Kind.OVEN_HOB -> "Base forno e piano cottura"
        KitchenModules.Kind.DISHWASHER -> "Lavastoviglie a incasso"
        KitchenModules.Kind.CORNER -> "Base d'angolo"
        KitchenModules.Kind.WALL_DOORS -> "Pensile"
        KitchenModules.Kind.WALL_HOOD -> "Pensile con cappa"
        KitchenModules.Kind.TALL_PANTRY -> "Colonna dispensa"
        KitchenModules.Kind.TALL_OVEN -> "Colonna forno e microonde"
        KitchenModules.Kind.TALL_FRIDGE -> "Colonna frigorifero"
    }

    /** Tra il nome del modulo e il piano di lavoro: "Base con ante · Bianco opaco · piano laminato bianco". */
    open val pianoCucina: String get() = " · piano "

    /** Nome a schermo di una categoria del catalogo di arredi o materiali (nei dati la categoria è in italiano). */
    open fun categoria(chiave: String): String = chiave

    /** Nome a schermo di un arredo del catalogo (nei dati il nome è in italiano). */
    open fun arredo(nome: String): String = nome

    /** Nome a schermo di un materiale del catalogo (nei dati il nome è in italiano). */
    open fun materiale(nome: String): String = nome

    /** Tappeto fatto con un materiale del catalogo: "Tappeto in juta", "Tappeto tondo in juta". */
    open fun tappeto(materiale: String, tondo: Boolean): String =
        (if (tondo) "Tappeto tondo in " else "Tappeto in ") + materiale.replaceFirstChar { it.lowercase() }

    open fun kitchenFront(v: KitchenModules.Front): String = when (v) {
        KitchenModules.Front.WHITE -> "Bianco opaco"
        KitchenModules.Front.PEARL -> "Grigio perla"
        KitchenModules.Front.ANTHRACITE -> "Grigio antracite"
        KitchenModules.Front.SAGE -> "Verde salvia"
        KitchenModules.Front.NAVY -> "Blu notte"
        KitchenModules.Front.OAK -> "Rovere chiaro"
        KitchenModules.Front.WALNUT -> "Noce"
    }

    open fun kitchenTop(v: KitchenModules.Top): String = when (v) {
        KitchenModules.Top.LAMINATE_WHITE -> "Laminato bianco"
        KitchenModules.Top.LAMINATE_GREY -> "Laminato grigio"
        KitchenModules.Top.LAMINATE_BLACK -> "Laminato nero"
        KitchenModules.Top.WOOD -> "Legno rovere"
        KitchenModules.Top.CARRARA -> "Marmo di Carrara"
        KitchenModules.Top.STONE -> "Pietra beige"
        KitchenModules.Top.CONCRETE -> "Cemento"
        KitchenModules.Top.MARQUINIA -> "Marmo nero Marquinia"
    }

    open fun phaseView(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Gialli e rossi"
        PhaseView.Existing -> "Stato di fatto"
        PhaseView.Project -> "Stato di progetto"
    }

    open fun phaseViewTitle(v: PhaseView): String = when (v) {
        PhaseView.Compare -> "Tavola comparativa"
        PhaseView.Existing -> "Stato di fatto"
        PhaseView.Project -> "Stato di progetto"
    }

    /** Nomi dei colori della tavolozza estesa, oltre a quelli delle pitture ([com.sagoma.planimetria.model.ProPalette]). */
    open val tavolozza: Map<Long, String> = mapOf(
        0xFFFAFAF8 to "Bianco puro", 0xFFF5F1E8 to "Latte", 0xFFF1E6CF to "Panna", 0xFFE8DECB to "Lino",
        0xFFBFB2A3 to "Tortora", 0xFFCBC3B6 to "Greige", 0xFFA9A9A4 to "Cemento", 0xFF6E7378 to "Ardesia",
        0xFF3F4246 to "Grafite", 0xFF26272A to "Nero", 0xFFEBCFC8 to "Cipria", 0xFFE8B4B8 to "Rosa",
        0xFFD2CBE3 to "Lavanda", 0xFFB9A6D1 to "Glicine", 0xFFCDE6D6 to "Menta", 0xFFA9D6D0 to "Acquamarina",
        0xFFB8D4EA to "Cielo", 0xFF9DB3C4 to "Polvere", 0xFFD9A441 to "Ocra", 0xFFC89F3A to "Senape",
        0xFFCB8467 to "Mattone chiaro", 0xFF9E4B2D to "Ruggine", 0xFF6E2632 to "Bordeaux", 0xFFB5332E to "Rosso",
        0xFFE9806B to "Corallo", 0xFFF2C0A0 to "Pesca", 0xFF8B8B55 to "Oliva", 0xFF3E5E44 to "Bosco",
        0xFF2E7D63 to "Smeraldo", 0xFF22565E to "Petrolio", 0xFF243A5E to "Blu notte", 0xFF3C6FA8 to "Blu",
        0xFF7E9CB9 to "Avio", 0xFF7A5A3C to "Tabacco", 0xFF4E3629 to "Cioccolato", 0xFFB39377 to "Caffelatte",
    )
}
