package com.sagoma.planimetria.testi.en

/**
 * Nomi inglesi degli arredi e dei materiali del catalogo. Il catalogo (scaricato da R2) ha i nomi in italiano: la chiave è
 * il nome italiano ([com.sagoma.planimetria.model.FurnitureCatalog.Item.label] e [com.sagoma.planimetria.model.MaterialCatalog.Item.label]),
 * così una voce con lo stesso nome ha la stessa traduzione. Un nome che manca resta in italiano.
 * Una riga per voce: "nome italiano|nome inglese".
 */
internal object CatalogoEn {
    val arredi: Map<String, String> by lazy { leggi(ARREDI) }
    val materiali: Map<String, String> by lazy { leggi(MATERIALI) }

    private fun leggi(testo: String): Map<String, String> =
        testo.lineSequence().filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }

    private const val ARREDI = """
Acquario|Aquarium
Albero natale|Christmas tree
Aloe in vaso|Potted aloe
Anfora|Amphora
Anfora in terracotta|Terracotta amphora
Appendiabiti|Coat rack
Apple iMac 2004|Apple iMac 2004
Applique|Wall light
Applique a gabbia|Cage wall light
Applique in ottone|Brass wall light
Armadietto alto|Tall cabinet
Armadio|Wardrobe
Armadio classico|Classic wardrobe
Armadio con specchio|Mirrored wardrobe
Armadio grigio|Grey wardrobe
Armadio in ciliegio|Cherry wardrobe
Armadio in noce|Walnut wardrobe
Armadio intagliato|Carved wardrobe
Armadio intagliato scuro|Dark carved wardrobe
Armadio rosso con vetri|Red glass-door wardrobe
Armadio scuro basso|Low dark wardrobe
Armadio stretto|Narrow wardrobe
Armadio verde salvia|Sage green wardrobe
Asciugatrice|Tumble dryer
Avvolgitubo da giardino|Garden hose reel
Baule a colonna|Tall trunk
Baule in legno|Wooden trunk
Bersaglio per freccette|Dartboard
Bidone della spazzatura|Rubbish bin
Bidè|Bidet
Bollitore|Kettle
Bonsai|Bonsai
Box doccia|Shower enclosure
Braciere in pietra|Stone brazier
Buddha|Buddha
Busto in marmo|Marble bust
Cabina doccia|Shower cabin
Cactus|Cactus
Cactus in vaso|Potted cactus
Caminetto|Small fireplace
Camino|Fireplace
Camino a legna|Wood-burning fireplace
Camino bianco|White fireplace
Camino classico|Classic fireplace
Camino in legno scuro|Dark wood fireplace
Camino in marmo|Marble fireplace
Candelabri in ottone|Brass candelabra
Candelabro in ferro|Iron candelabrum
Candele decorative|Decorative candles
Candeliere|Candlestick
Candeliere in ottone|Brass candlestick
Cappa|Cooker hood
Carrello porta attrezzi|Tool trolley
Cassa acustica a colonna|Tower speaker
Cassa acustica nera|Black speaker
Cassa bluetooth|Bluetooth speaker
Cassa hi-fi|Hi-fi speaker
Cassa su treppiede|Speaker on tripod
Cassapanca intagliata|Carved chest
Casse acustiche da terra|Floor-standing speakers
Casse compatte|Compact speakers
Casse da scaffale|Bookshelf speakers
Cassetta portaoggetti|Storage box
Cassettiera aperta|Open chest of drawers
Cassettiera bassa|Low chest of drawers
Cassettiera bianca|White chest of drawers
Cassettiera bianca moderna|Modern white chest of drawers
Cavalletto con tela|Easel with canvas
Cavalletto da pittore|Painter's easel
Cavallo a dondolo|Rocking horse
Cestino|Wastepaper basket
Cestino a rete|Mesh basket
Cesto|Basket
Cesto con manico|Basket with handle
Cesto del pane|Bread basket
Cesto in paglia|Straw basket
Cesto in vimini|Wicker basket
Cesto in vimini grande|Large wicker basket
Cesto in vimini ovale|Oval wicker basket
Chaise longue|Chaise longue
Chaise longue gialla|Yellow chaise longue
Colonna a giorno|Open shelving column
Colonna libreria|Bookcase column
Comodino|Bedside table
Comodino antico|Antique bedside table
Comodino classico|Classic bedside table
Comodino con cassetti|Bedside table with drawers
Comodino dipinto|Painted bedside table
Composizione verde|Green arrangement
Comò bianco|White dresser
Comò gotico|Gothic dresser
Comò orientale|Oriental dresser
Comò scuro|Dark dresser
Congelatore a pozzetto|Chest freezer
Console di gioco|Games console
Consolle classica|Classic console table
Consolle orientale|Oriental console table
Cornice decorata|Decorated frame
Cornice dorata|Gilded frame
Cornice ovale|Oval frame
Cornice scura|Dark frame
Credenza bianca|White sideboard
Credenza bianca aperta|Open white sideboard
Credenza con alzata|Sideboard with hutch
Credenza con piano in marmo|Marble-top sideboard
Credenza con vetrina|Sideboard with display cabinet
Credenza dipinta|Painted sideboard
Credenza grigia classica|Classic grey sideboard
Credenza in legno chiaro|Light wood sideboard
Credenza rustica|Rustic sideboard
Credenza vittoriana|Victorian sideboard
Cucina a gas bianca|White gas cooker
Cucina a quattro fuochi|Four-burner cooker
Cucina bianca|White cooker
Cucina componibile|Fitted kitchen
Cucina con doppio forno|Double-oven cooker
Cucina con forno|Cooker with oven
Cucina elettrica|Electric cooker
Cucina professionale|Professional range cooker
Cucina retrò azzurra|Retro light blue cooker
Culla|Cradle
Culla a casetta|House-shaped cradle
Culla con giostrina|Cradle with mobile
Culla in legno|Wooden cradle
Cuscini|Cushions
Cuscini decorativi|Decorative cushions
Cuscino avorio|Ivory cushion
Cuscino bianco|White cushion
Cuscino blu|Blue cushion
Cuscino lungo|Long cushion
Cuscino nero|Black cushion
Cuscino per schienale|Back cushion
Cuscino rosa antico|Dusty pink cushion
Dispenser acqua|Water dispenser
Divanetto Chesterfield bianco|White Chesterfield loveseat
Divanetto arancio|Orange loveseat
Divanetto bianco|White loveseat
Divanetto grigio|Grey loveseat
Divanetto in pelle scura|Dark leather loveseat
Divanetto rosso|Red loveseat
Divanetto rosso moderno|Modern red loveseat
Divano|Sofa
Divano 2 posti|2-seater sofa
Divano 3 posti|3-seater sofa
Divano Chesterfield|Chesterfield sofa
Divano Chesterfield grigio|Grey Chesterfield sofa
Divano Winchester|Winchester sofa
Divano ad angolo|Corner sofa
Divano angolare antracite|Charcoal corner sofa
Divano angolare blu|Blue corner sofa
Divano angolare fucsia|Fuchsia corner sofa
Divano angolare grigio|Grey corner sofa
Divano angolare grigio chiaro|Light grey corner sofa
Divano angolare rosa|Pink corner sofa
Divano antracite|Charcoal sofa
Divano antracite a tre posti|Charcoal three-seater sofa
Divano azzurro|Light blue sofa
Divano azzurro polvere|Powder blue sofa
Divano bianco classico|Classic white sofa
Divano chiaro|Light sofa
Divano classico bianco|White classic sofa
Divano classico chiaro|Light classic sofa
Divano componibile|Modular sofa
Divano con braccioli in legno|Sofa with wooden arms
Divano con penisola grigio|Grey chaise sofa
Divano con plaid|Sofa with throw
Divano con pouf|Sofa with pouf
Divano due posti|Two-seater sofa
Divano giallo|Yellow sofa
Divano grigio chiaro|Light grey sofa
Divano grigio con cuscini|Grey sofa with cushions
Divano imbottito|Upholstered sofa
Divano in legno|Wooden sofa
Divano in pelle marrone|Brown leather sofa
Divano letto|Sofa bed
Divano letto aperto|Open sofa bed
Divano letto bianco|White sofa bed
Divano letto grigio|Grey sofa bed
Divano letto rosa|Pink sofa bed
Divano moderno grigio|Modern grey sofa
Divano modulare bianco|White modular sofa
Divano nero con coperta|Black sofa with blanket
Divano orientale|Oriental sofa
Divano rosa capitonné|Pink tufted sofa
Divano scandinavo|Scandinavian sofa
Dormeuse|Daybed
Enciclopedia|Encyclopaedia
Estensione ad isola|Island extension
Faro da esterno|Outdoor floodlight
Fasciatoio|Changing table
Felce di Boston|Boston fern
Ficus|Ficus
Ficus in vaso|Potted ficus
Finestra con tende rosse|Window with red curtains
Fiori in vaso|Potted flowers
Fioriera|Planter
Fioriera alta|Tall planter
Fioriera lunga|Long planter
Fontana decorativa|Decorative fountain
Fornetto|Countertop oven
Forno|Oven
Forno bianco|White oven
Forno doppio|Double oven
Frigorifero|Fridge
Frigorifero americano|American-style fridge
Frigorifero antracite|Charcoal fridge
Frigorifero bianco|White fridge
Frigorifero bianco classico|Classic white fridge
Frigorifero con freezer|Fridge freezer
Frigorifero doppio|Double fridge
Frigorifero retrò|Retro fridge
Frigorifero side by side|Side-by-side fridge
Frigorifero vintage aperto|Open vintage fridge
Futon|Futon
Gatto in cemento|Concrete cat
Giradischi a valigetta|Suitcase record player
Grammofono|Gramophone
Griglia da esterno|Outdoor grill
Guardaroba|Wardrobe
Il bacio|The Kiss
Isola con lavello|Island with sink
Isola cucina moderna|Modern kitchen island
Lampada|Lamp
Lampada a muro|Wall lamp
Lampada a paralume grigio|Lamp with grey shade
Lampada a sospensione|Pendant lamp
Lampada a sospensione lunga|Long pendant lamp
Lampada classica|Classic lamp
Lampada classica bianca|Classic white lamp
Lampada con paralume|Lamp with shade
Lampada con paralume beige|Lamp with beige shade
Lampada con paralume plissé|Lamp with pleated shade
Lampada da banchiere|Banker's lamp
Lampada da banchiere verde|Green banker's lamp
Lampada da esterno|Outdoor lamp
Lampada da lettura|Reading lamp
Lampada da scrivania|Desk lamp
Lampada da soffitto|Ceiling lamp
Lampada da tavolo|Table lamp
Lampada da tavolo industriale|Industrial table lamp
Lampada da terra classica|Classic floor lamp
Lampada da terra sottile|Slim floor lamp
Lampada da terra vintage|Vintage floor lamp
Lampada in bronzo|Bronze lamp
Lampada in porcellana|Porcelain lamp
Lampada moderna|Modern lamp
Lampada sospesa|Hanging lamp
Lampada verde a fungo|Green mushroom lamp
Lampadario|Chandelier
Lampadario a bracci|Arm chandelier
Lampadario a candele in ferro|Iron candle chandelier
Lampadario a candele nero|Black candle chandelier
Lampadario a lanterna|Lantern chandelier
Lampadario classico|Classic chandelier
Lampadario con paralumi|Chandelier with shades
Lampadario con paralumi grigi|Chandelier with grey shades
Lampadario di cristallo|Crystal chandelier
Lampadario di cristallo azzurro|Light blue crystal chandelier
Lampadario in ferro|Iron chandelier
Lampadario in legno|Wooden chandelier
Lampadario in ottone|Brass chandelier
Lampadario in ottone sottile|Slim brass chandelier
Lampadario orientale|Oriental chandelier
Lampadario rustico|Rustic chandelier
Lampadario viola|Purple chandelier
Lampione da giardino|Garden lamp post
Lampione da giardino in legno|Wooden garden lamp post
Lanterna appesa|Hanging lantern
Lanterna da giardino|Garden lantern
Lanterna da terra|Floor lantern
Lanterna di carta|Paper lantern
Lanterna giapponese|Japanese lantern
Lanterna in legno|Wooden lantern
Lanterna in pietra|Stone lantern
Lavabo a colonna|Pedestal washbasin
Lavabo bianco|White washbasin
Lavabo da appoggio|Countertop washbasin
Lavabo ergonomico|Accessible washbasin
Lavabo in pietra|Stone washbasin
Lavabo piccolo|Small washbasin
Lavabo quadrato|Square washbasin
Lavabo quadrato in acciaio|Square steel washbasin
Lavabo sospeso|Wall-hung washbasin
Lavagna bianca|Whiteboard
Lavamani|Hand basin
Lavanda|Lavender
Lavandino|Washbasin
Lavandino con doppio specchio|Washbasin with double mirror
Lavandino con specchio|Washbasin with mirror
Lavastoviglie|Dishwasher
Lavastoviglie piccola|Compact dishwasher
Lavatrice|Washing machine
Lavatrice a carica dall'alto|Top-loading washing machine
Lavatrice frontale|Front-loading washing machine
Lavello bianco|White kitchen sink
Lavello da incasso|Inset kitchen sink
Lavello doppio in acciaio|Double steel kitchen sink
Lavello grigio|Grey kitchen sink
Lavello in acciaio|Steel kitchen sink
Lavello nero|Black kitchen sink
Lavello nero a una vasca|Black single-bowl sink
Lavello nero compatto|Compact black sink
Lettino|Small bed
Lettino prendisole|Sun lounger
Letto|Bed
Letto a castello bianco|White bunk bed
Letto a castello bianco classico|Classic white bunk bed
Letto a castello con scaletta|Bunk bed with ladder
Letto a castello curvo|Curved bunk bed
Letto a castello in legno|Wooden bunk bed
Letto a castello in metallo|Metal bunk bed
Letto a castello rosa|Pink bunk bed
Letto a giorno|Daybed
Letto bianco a spalliere|White bed with headboard and footboard
Letto con cassetti|Bed with drawers
Letto con testiera nera|Bed with black headboard
Letto gotico|Gothic bed
Letto imbottito rosa|Pink upholstered bed
Letto in ferro|Iron bed
Letto in ferro battuto|Wrought iron bed
Letto in ferro blu|Blue iron bed
Letto in legno|Wooden bed
Letto in legno chiaro|Light wood bed
Letto in legno scuro|Dark wood bed
Letto in metallo con coperta|Metal bed with blanket
Letto in ottone|Brass bed
Letto matrimoniale moderno|Modern double bed
Letto moderno bianco|Modern white bed
Letto per bambini|Children's bed
Letto singolo|Single bed
Letto singolo in ferro|Iron single bed
Letto singolo in legno|Wooden single bed
Letto soppalcato|Loft bed
Libreria|Bookcase
Libreria a vetri|Glass-door bookcase
Libreria antica|Antique bookcase
Libreria azzurra|Light blue bookcase
Libreria con libri|Bookcase with books
Libreria credenza|Bookcase sideboard
Libreria grande|Large bookcase
Libreria grigia|Grey bookcase
Libreria in legno|Wooden bookcase
Libreria rustica|Rustic bookcase
Libreria stretta|Narrow bookcase
Lume a petrolio|Oil lamp
Macchina da caffé|Coffee machine
Macchina del caffè|Coffee maker
Macchina del pane|Bread maker
Madia moderna|Modern sideboard
Madia scura|Dark sideboard
Maniglione pieghevole|Folding grab bar
Mappamondo antico|Antique globe
Mappamondo su piedistallo|Globe on pedestal
Materasso da pavimento|Floor mattress
Microonde|Microwave
Microonde bianco|White microwave
Microonde blu|Blue microwave
Microonde classico|Classic microwave
Microonde grigio|Grey microwave
Microonde in acciaio|Steel microwave
Microonde nero|Black microwave
Miscelatore per doccia|Shower mixer
Miscelatore per vasca|Bath mixer
Miscelatore per vasca e doccia|Bath and shower mixer
Mobile TV|TV unit
Mobile TV anni '50|1950s TV unit
Mobile TV vintage|Vintage TV unit
Mobile a cassetti|Drawer unit
Mobile ad angolo inferiore|Corner base unit
Mobile ad angolo superiore|Corner wall unit
Mobile ad angolo superiore piccolo|Small corner wall unit
Mobile alto|Tall unit
Mobile alto in legno|Tall wooden unit
Mobile bagno moderno|Modern bathroom vanity
Mobile con cassetti|Cabinet with drawers
Mobile con mensole|Cabinet with shelves
Mobile con vetri|Glass-door cabinet
Mobile inferiore|Base unit
Mobile lavabo|Vanity unit
Mobile lavandino|Sink vanity unit
Mobile lavello|Sink base unit
Mobile orientale|Oriental cabinet
Mobile soggiorno|Living room unit
Mobile superiore|Wall unit
Mobile vetrina chiaro|Light display cabinet
Mobile vetrina scuro|Dark display cabinet
Mobiletto blu|Small blue cabinet
Mobiletto gotico|Small Gothic cabinet
Mobiletto in legno|Small wooden cabinet
Monstera in vaso|Potted monstera
Ombrellone bianco|White parasol
Ombrellone nero|Black parasol
Ombrellone rosa|Pink parasol
Orinale|Urinal
Orologio a cucù|Cuckoo clock
Orologio a numeri romani|Roman numeral clock
Orologio a raggi|Sunburst clock
Orologio da mensola|Mantel clock
Orologio da parete|Wall clock
Orologio da parete a telefono|Telephone wall clock
Orologio da parete classico|Classic wall clock
Orologio da parete grigio|Grey wall clock
Orologio da parete tondo|Round wall clock
Pagoda in pietra|Stone pagoda
Palme in vaso|Potted palms
Panca capitonné|Tufted bench
Panca con schienale|Bench with backrest
Panca in legno|Wooden bench
Panca in legno massello|Solid wood bench
Panchetta imbottita|Upholstered footstool
Panchina classica|Classic bench
Panchina da giardino|Garden bench
Panchina da parco|Park bench
Panchina in ferro|Iron bench
Panchina moderna|Modern bench
Panchina verde|Green bench
Paravento|Folding screen
Pattumiera in acciaio|Steel pedal bin
Pendolo|Pendulum clock
Pendolo antico|Antique pendulum clock
Pendolo classico|Classic pendulum clock
Pendolo in mogano|Mahogany pendulum clock
Pendolo moderno|Modern pendulum clock
Piano cottura|Hob
Piano cottura a gas a due fuochi|Two-burner gas hob
Piano cottura a induzione|Induction hob
Pianoforte|Piano
Pianoforte a coda|Grand piano
Pianoforte verticale|Upright piano
Pianoforte verticale classico|Classic upright piano
Pianoforte verticale nero|Black upright piano
Pianoforte verticale scuro|Dark upright piano
Pianta|Plant
Pianta grande|Large plant
Pianta grassa|Succulent
Pianta grassa in vaso viola|Succulent in purple pot
Pianta in vaso|Potted plant
Pianta piccola|Small plant
Pianta tropicale|Tropical plant
Piccola lampada|Small lamp
PlayStation|PlayStation
Poltrona|Armchair
Poltrona Winchester|Winchester armchair
Poltrona a dondolo in legno|Wooden rocking armchair
Poltrona a dondolo moderna|Modern rocking armchair
Poltrona antica|Antique armchair
Poltrona beige|Beige armchair
Poltrona bergère bianca|White bergère armchair
Poltrona bergère verde|Green bergère armchair
Poltrona blu design|Blue designer armchair
Poltrona capitonné|Tufted armchair
Poltrona capitonné bordeaux|Burgundy tufted armchair
Poltrona classica|Classic armchair
Poltrona classica grigia|Classic grey armchair
Poltrona con cuscino|Armchair with cushion
Poltrona da doccia|Shower chair with arms
Poltrona da relax capitonné|Tufted recliner
Poltrona di design|Designer armchair
Poltrona girevole|Swivel armchair
Poltrona grigia|Grey armchair
Poltrona in pelle beige|Beige leather armchair
Poltrona in pelle cuoio|Tan leather armchair
Poltrona moderna|Modern armchair
Poltrona orientale|Oriental armchair
Poltrona reclinabile|Reclining armchair
Poltrona rosa|Pink armchair
Poltrona rosa cipria|Powder pink armchair
Poltrona rossa|Red armchair
Poltrona rossa moderna|Modern red armchair
Poltrona tortora|Taupe armchair
Poltrona viola|Purple armchair
Poltroncina|Small armchair
Poltroncina bianca|Small white armchair
Poltroncina koala|Koala armchair
Porta asciugamano|Towel holder
Porta doccia|Shower door
Portafoto|Photo frame
Portafoto grande|Large photo frame
Portafrutta|Fruit bowl
Portarotolo|Toilet roll holder
Portasciugamani|Towel rack
Portasciugamani a barra|Towel bar
Portasciugamani a muro|Wall towel rack
Portatile|Laptop
Pothos in vaso|Potted pothos
Pouf|Pouf
Pouf a sacco marrone|Brown beanbag
Pouf a sacco rosso|Red beanbag
Pouf a sacco viola|Purple beanbag
Pouf antracite|Charcoal pouf
Pouf bianco|White pouf
Pouf blu capitonné|Blue tufted pouf
Pouf capitonné|Tufted pouf
Pouf capitonné grigio|Grey tufted pouf
Pouf grigio|Grey pouf
Pouf in pelle nera|Black leather pouf
Pouf nero|Black pouf
Pouf rotondo|Round pouf
Pouf tondo bordeaux|Round burgundy pouf
Puff|Footstool
Quadro|Painting
Quadro grande|Large painting
Quadro grigio|Grey painting
Quadro in legno|Wooden picture
Quadro rosa|Pink painting
Radio giradischi vintage|Vintage radio record player
Ripiani superiori|Upper shelves
Rose|Roses
Salottino da esterno|Outdoor lounge set
Scaffale con cassetti|Shelving unit with drawers
Scaffale in metallo|Metal shelving
Scaffale metallico|Metal shelving unit
Scaffale metallico alto|Tall metal shelving unit
Scaffale metallico stretto|Narrow metal shelving unit
Scaffale verticale|Vertical shelving unit
Scaffaletto dipinto|Small painted shelf
Scaffali|Shelves
Schedario bianco|White filing cabinet
Schedario in metallo|Metal filing cabinet
Scrivania|Desk
Scrivania antica|Antique desk
Scrivania bianca|White desk
Scrivania classica|Classic desk
Scrivania con computer|Desk with computer
Scrivania con computer grigia|Grey desk with computer
Scrivania in legno|Wooden desk
Scrivania in metallo|Metal desk
Scrivania in mogano|Mahogany desk
Scrivania in noce|Walnut desk
Scrivania nera|Black desk
Scrivania regolabile|Adjustable desk
Scrivania semplice|Simple desk
Scrivania ufficio a L|L-shaped office desk
Scultura del pensatore|The Thinker sculpture
Scultura in bronzo|Bronze sculpture
Sedia|Chair
Sedia Osaka|Osaka chair
Sedia Savonarola|Savonarola chair
Sedia Tolix|Tolix chair
Sedia Windsor nera|Black Windsor chair
Sedia a dondolo|Rocking chair
Sedia a dondolo bianca|White rocking chair
Sedia a dondolo classica|Classic rocking chair
Sedia a dondolo gialla|Yellow rocking chair
Sedia a dondolo grigia|Grey rocking chair
Sedia a dondolo imbottita|Upholstered rocking chair
Sedia a dondolo in cuoio|Leather rocking chair
Sedia a dondolo in legno|Wooden rocking chair
Sedia a dondolo in legno scuro|Dark wood rocking chair
Sedia a dondolo rosa|Pink rocking chair
Sedia a dondolo scura|Dark rocking chair
Sedia alta scura|Dark high chair
Sedia anni '50|1950s chair
Sedia bianca|White chair
Sedia bianca classica|Classic white chair
Sedia bianca e grigia|White and grey chair
Sedia bistrot|Bistro chair
Sedia classica|Classic chair
Sedia con braccioli|Chair with arms
Sedia con cuscino|Chair with cushion
Sedia da bar|Bar chair
Sedia da doccia|Shower chair
Sedia da gaming|Gaming chair
Sedia da giardino|Garden chair
Sedia da pranzo|Dining chair
Sedia da ufficio|Office chair
Sedia dipinta|Painted chair
Sedia dipinta azzurra|Light blue painted chair
Sedia girevole|Swivel chair
Sedia gotica|Gothic chair
Sedia in Plastica|Plastic chair
Sedia in faggio|Beech chair
Sedia in metallo|Metal chair
Sedia intagliata|Carved chair
Sedia per bambini|Children's chair
Sedia quercia|Oak chair
Sedia rosa|Pink chair
Sedia rustica|Rustic chair
Sedia ryokan|Ryokan chair
Sedia scandinava|Scandinavian chair
Sedile ribaltabile a muro|Wall-mounted folding seat
Seggiolone|Baby high chair
Servizio da tè|Tea set
Set da giardino in metallo|Metal garden set
Set da patio con ombrellone|Patio set with parasol
Set da pranzo bianco|White dining set
Set da pranzo grigio|Grey dining set
Set da pranzo in ferro|Iron dining set
Set da pranzo rustico|Rustic dining set
Set da pranzo scuro|Dark dining set
Settimanale|Tall chest of drawers
Sfera armillare|Armillary sphere
Sgabello|Stool
Sgabello alto in legno|Tall wooden stool
Sgabello basso|Low stool
Sgabello bianco|White stool
Sgabello da bar|Bar stool
Sgabello da bar in metallo|Metal bar stool
Sgabello da officina|Workshop stool
Sgabello da pianoforte|Piano stool
Sgabello dipinto|Painted stool
Sgabello girevole|Swivel stool
Sgabello in legno|Wooden stool
Sgabello in legno chiaro|Light wood stool
Sgabello in metallo|Metal stool
Sgabello industriale|Industrial stool
Sgabello orientale|Oriental stool
Sgabello per pianoforte|Piano bench
Sgabello pieghevole|Folding stool
Sgabello rustico|Rustic stool
Soffione doccia e rubinetto|Shower head and tap
Sospensione industriale|Industrial pendant
Specchio ad arco|Arched mirror
Specchio barocco|Baroque mirror
Specchio capitonné|Tufted mirror
Specchio con cornice scura|Dark-framed mirror
Specchio da terra|Floor mirror
Specchio decorato|Decorated mirror
Specchio dorato|Gilded mirror
Specchio in stile impero|Empire-style mirror
Specchio ovale|Oval mirror
Stampante e scanner|Printer and scanner
Statua classica|Classical statue
Statua di cavallo|Horse statue
Statua di donna|Statue of a woman
Statua egizia|Egyptian statue
Statua in terracotta|Terracotta statue
Stereo portatile|Portable stereo
Stufa|Stove
Stufa a legna|Wood-burning stove
Sveglia|Alarm clock
Sveglia vintage|Vintage alarm clock
TV|TV
TV 16:9|16:9 TV
TV a muro|Wall-mounted TV
Tapis roulant|Treadmill
Tapis roulant pieghevole|Folding treadmill
Tappetino azzurro|Light blue mat
Tappetino da bagno bianco|White bath mat
Tappetino grigio|Grey mat
Tappetino in spugna|Terry towel mat
Tappetino rosa|Pink mat
Tappeto grigio|Grey rug
Tappeto orientale arancio|Orange oriental rug
Tappeto persiano rosso|Red Persian rug
Tappeto rotondo|Round rug
Tavolino|Side table
Tavolino a colonna|Pedestal side table
Tavolino alto|Tall side table
Tavolino alto in legno|Tall wooden side table
Tavolino basso|Coffee table
Tavolino bistrot|Bistro table
Tavolino con brocca|Side table with jug
Tavolino con intreccio|Woven side table
Tavolino con tovaglia|Side table with tablecloth
Tavolino da tè|Tea table
Tavolino gotico|Gothic side table
Tavolino in legno|Wooden side table
Tavolino industriale|Industrial side table
Tavolino intagliato|Carved side table
Tavolino laterale|End table
Tavolino moderno|Modern side table
Tavolino ovale|Oval side table
Tavolino per bambini|Children's table
Tavolino rotondo|Round side table
Tavolino rotondo in legno|Round wooden side table
Tavolino tondo design|Round designer side table
Tavolino tondo in legno|Round wooden table
Tavolino tondo in noce|Round walnut side table
Tavolino tondo scandinavo|Round Scandinavian side table
Tavolino verde intagliato|Green carved side table
Tavolo|Table
Tavolo bianco|White table
Tavolo bianco classico|Classic white table
Tavolo bianco da cucina|White kitchen table
Tavolo bianco moderno|Modern white table
Tavolo con panche|Table with benches
Tavolo con tovaglia|Table with tablecloth
Tavolo con tovaglia blu|Table with blue tablecloth
Tavolo da bar|Bar table
Tavolo da caffè|Café table
Tavolo da picnic|Picnic table
Tavolo design|Designer table
Tavolo dipinto|Painted table
Tavolo e sedie da giardino|Garden table and chairs
Tavolo grande con sedie|Large table with chairs
Tavolo grigio|Grey table
Tavolo grigio scuro|Dark grey table
Tavolo in legno|Wooden table
Tavolo in legno chiaro|Light wood table
Tavolo in legno di Tek|Teak table
Tavolo in legno di ontano|Alder wood table
Tavolo in legno massello|Solid wood table
Tavolo in legno rosso|Red wood table
Tavolo in vetro|Glass table
Tavolo lungo|Long table
Tavolo nero|Black table
Tavolo quadrato|Square table
Tavolo quercia|Oak table
Tavolo rotondo|Round table
Tavolo rotondo grande|Large round table
Tavolo scuro|Dark table
Tavolo semplice|Simple table
Tavolo su cavalletti|Trestle table
Tavolo tecnico|Drafting table
Tavolo tondo|Round dining table
Tavolo tulipano|Tulip table
Tecnigrafo|Drafting machine
Televisore a schermo piatto|Flat-screen TV
Televisore anni '60|1960s television
Televisore anni '80|1980s television
Televisore in legno|Wood-cased television
Televisore portatile|Portable television
Televisore portatile vintage|Vintage portable television
Televisore retrò|Retro television
Televisore su gambe|Television on legs
Televisore vintage|Vintage television
Telo per proiettore|Projector screen
Tenda a pannello|Panel curtain
Tenda su bastone|Curtain on pole
Tende bianche|White curtains
Tende bordeaux|Burgundy curtains
Tende con mantovana|Curtains with valance
Tende di lino|Linen curtains
Termoarredo|Designer radiator
Termosifone a colonne|Column radiator
Termosifone a colonne bianco|White column radiator
Termosifone a pannello|Panel radiator
Termosifone bianco|White radiator
Termosifone decorativo|Decorative radiator
Termosifone in ghisa|Cast iron radiator
Termosifone piatto|Flat radiator
Toilette giapponese|Japanese toilet
Toletta|Dressing table
Tostapane|Toaster
Turca|Squat toilet
Unità esterne del condizionatore|Air conditioner outdoor units
Urinale|Urinal
Valigie vintage|Vintage suitcases
Vasca ad angolo|Corner bath
Vasca ad isola|Island bath
Vasca con doccia|Bath with shower
Vasca con piedini|Clawfoot bath
Vasca con piedini bianca|White clawfoot bath
Vasca con rubinetto|Bath with tap
Vasca freestanding|Freestanding bath
Vasca in ottone|Brass bath
Vasca ovale|Oval bath
Vasca rettangolare|Rectangular bath
Vasca vittoriana|Victorian bath
Vaso antico|Antique vase
Vaso bianco|White vase
Vaso bianco e blu|Blue and white vase
Vaso bianco panciuto|Round white vase
Vaso celadon|Celadon vase
Vaso di terracotta|Terracotta pot
Vaso in ceramica|Ceramic vase
Vaso in ceramica alto|Tall ceramic vase
Vaso in ottone|Brass vase
Vaso in ottone alto|Tall brass vase
Vaso in terracotta|Terracotta vase
Vaso lilla|Lilac vase
Vaso rosa antico|Dusty pink vase
Vaso smaltato|Glazed vase
Vaso verde|Green vase
Venere di Milo|Venus de Milo
Ventilatore a soffitto|Ceiling fan
Ventilatore da tavolo|Desk fan
Ventilatore in ottone|Brass fan
Ventilatore vintage|Vintage fan
Vetrina bianca|White display cabinet
Vetrina con ante|Display cabinet with doors
Vetrinetta|Small display cabinet
Vetrinetta a giorno|Small open display unit
Vetrinetta antica|Antique display cabinet
Videoproiettore|Video projector
WC bianco|White toilet
WC classico|Classic toilet
WC compatto|Compact toilet
WC con cassetta|Toilet with cistern
WC moderno|Modern toilet
WC nero|Black toilet
Water|Toilet
Zerbino beige|Beige doormat
Zerbino marrone|Brown doormat
"""

    private const val MATERIALI = """
Ardesia a lastre|Slate slabs
Assi chiare|Light planks
Assi chiare nodose|Knotty light planks
Assi color miele|Honey planks
Assi da ponte|Scaffold boards
Assi scure|Dark planks
Assi vissute|Weathered planks
Autobloccanti|Interlocking pavers
Blocchi grigi|Grey blocks
Carta da parati jacquard|Jacquard wallpaper
Carta da parati marmorizzata|Marbled wallpaper
Cementine bianco e nero|Black and white cement tiles
Cementine decorate|Patterned cement tiles
Cementine geometriche|Geometric cement tiles
Cemento a lastre|Concrete slabs
Cemento chiaro|Light concrete
Cemento grigio|Grey concrete
Cemento scuro|Dark concrete
Cemento spatolato|Trowelled concrete
Ciottolato|Cobblestones
Cotto|Terracotta tiles
Cotto chiaro|Light terracotta tiles
Cotto scuro|Dark terracotta tiles
Doghe bianche|White boards
Doghe in legno|Wooden boards
Doghe rosse|Red boards
Doghe strette scure|Narrow dark boards
Esagonette bianche|White hexagon tiles
Esagonette nere|Black hexagon tiles
Esagoni in cemento|Concrete hexagons
Ghiaia|Gravel
Gres antracite a correre|Charcoal stoneware, running bond
Gres ardesia|Slate-effect stoneware
Gres beige|Beige stoneware
Gres bianco 60×60|White stoneware 60×60
Gres bianco grande formato|Large white stoneware
Gres grafite|Graphite stoneware
Gres nero|Black stoneware
Gres pietra chiara|Light stone stoneware
Intonaco bianco|White plaster
Intonaco fine|Fine plaster
Intonaco grigio|Grey plaster
Intonaco liscio|Smooth plaster
Intonaco rustico|Rough plaster
Juta|Jute
Juta fine|Fine jute
Laminato rovere|Oak laminate
Lana a righe|Striped wool
Lana grigia|Grey wool
Lana grigia melange|Grey melange wool
Lana intrecciata bianca|White woven wool
Lana rossa a coste|Red ribbed wool
Lastre a intreccio|Woven slabs
Lastre beige|Beige slabs
Lastre di marmo rosa|Pink marble slabs
Lastre di pietra beige|Beige stone slabs
Lastre di pietra chiara|Light stone slabs
Lastre grigie|Grey slabs
Lastre nere lucide|Glossy black slabs
Lino verde|Green linen
Listoni noce|Walnut planks
Listoni rovere|Oak planks
Listoni rovere naturale|Natural oak planks
Listoni teak|Teak planks
Maioliche blu|Blue majolica tiles
Marmo Emperador chiaro|Light Emperador marble
Marmo bianco venato|Veined white marble
Marmo blu notte|Midnight blue marble
Marmo crema|Cream marble
Marmo di Carrara|Carrara marble
Marmo grigio|Grey marble
Marmo grigio a lastre|Grey marble slabs
Marmo grigio perla|Pearl grey marble
Marmo nero Marquinia|Nero Marquina marble
Marmo nero lucido|Glossy black marble
Marmo nero venato|Veined black marble
Marmo oro|Gold marble
Marmo tortora|Taupe marble
Marmo verde|Green marble
Mattoncini bianchi|White brick tiles
Mattoncini verdi|Green brick tiles
Mattonelle chiare|Light tiles
Mattoni antichi|Old bricks
Mattoni arancio|Orange bricks
Mattoni arancio lisci|Smooth orange bricks
Mattoni bianchi|White bricks
Mattoni bruciati|Burnt bricks
Mattoni crema|Cream bricks
Mattoni da esterno|Outdoor bricks
Mattoni faccia a vista|Facing bricks
Mattoni gialli|Yellow bricks
Mattoni misti|Mixed bricks
Mattoni rossi|Red bricks
Mattoni rustici|Rustic bricks
Mattoni scuri|Dark bricks
Mattoni vissuti|Weathered bricks
Moquette a fiori|Floral carpet
Moquette a quadri grigia|Grey checked carpet
Moquette avorio|Ivory carpet
Moquette beige|Beige carpet
Moquette beige a riquadri|Beige panelled carpet
Moquette bianca|White carpet
Moquette blu|Blue carpet
Moquette blu a quadri|Blue checked carpet
Moquette rosa melange|Pink melange carpet
Moquette rossa|Red carpet
Moquette verde oliva|Olive green carpet
Mosaico acquamarina|Aquamarine mosaic
Mosaico azzurro|Light blue mosaic
Mosaico grigio|Grey mosaic
Muro in pietra|Stone wall
Muro in pietra grezza|Rough stone wall
Onice azzurro|Blue onyx
Onice bianco|White onyx
Onice miele|Honey onyx
Onice nero|Black onyx
Onice rosso|Red onyx
Onice verde acqua|Aqua green onyx
Opus incertum|Opus incertum
Paglia intrecciata|Woven straw
Parquet a cesto|Basket weave parquet
Parquet a intreccio chiaro|Light woven parquet
Parquet a intreccio scuro|Dark woven parquet
Parquet a quadri|Checked parquet
Parquet a riquadri|Panelled parquet
Parquet a riquadri classico|Classic panelled parquet
Parquet acero|Maple parquet
Parquet anticato|Antiqued parquet
Parquet ciliegio|Cherry parquet
Parquet in diagonale|Diagonal parquet
Parquet noce|Walnut parquet
Parquet rovere miele|Honey oak parquet
Pelliccia geometrica|Geometric faux fur
Perlinato scuro|Dark tongue and groove
Piastrelle a scacchi|Checkerboard tiles
Piastrelle beige|Beige tiles
Piastrelle bianche 10×10|White tiles 10×10
Piastrelle bianche lucide|Glossy white tiles
Piastrelle blu|Blue tiles
Piastrelle blu antiche|Old blue tiles
Piastrelle in diagonale|Diagonal tiles
Piastrelle in graniglia|Terrazzo tiles
Piastrelle marroni|Brown tiles
Pietra a opus|Opus stone
Pietra a secco|Dry stone
Pietra a strati|Layered stone
Pietra beige|Beige stone
Pietra gialla|Yellow stone
Pietre con erba|Stepping stones in grass
Porfido|Porphyry
Porfido a ventaglio|Fan-pattern porphyry
Prato|Lawn
Prato all'inglese|Manicured lawn
Quadri grigi|Grey checks
Quadrotte scure|Dark squares
Quadrotte vissute|Weathered squares
Resina bianca|White resin
Sampietrini a ventaglio|Fan-pattern setts
Scacchiera bianco e nero|Black and white checkerboard
Scacchiera da esterno|Outdoor checkerboard
Scacchiera in marmo|Marble checkerboard
Selciato|Paving stones
Spina di pesce ciliegio|Cherry herringbone
Spina di pesce classica|Classic herringbone
Spina di pesce naturale|Natural herringbone
Spina di pesce rovere|Oak herringbone
Tartan|Tartan
Tavolato bianco|White boarding
Tavolato rustico|Rustic boarding
Tavolato sbiancato|Whitewashed boarding
Terrazzo chiaro|Light terrazzo
Terrazzo colorato|Coloured terrazzo
Terrazzo grigio|Grey terrazzo
Terrazzo multicolore|Multicoloured terrazzo
Terrazzo nero|Black terrazzo
Terrazzo veneziano|Venetian terrazzo
Tessuto a motivi|Patterned fabric
Tessuto a motivi etnici|Ethnic-pattern fabric
Travertino|Travertine
Travertino azzurro|Blue travertine
Travertino classico|Classic travertine
Travertino grigio|Grey travertine
Travertino noce|Walnut travertine
Velluto azzurro|Light blue velvet
Velluto blu|Blue velvet
Velluto salvia|Sage velvet
Vimini intrecciato|Woven wicker
Wengè|Wenge
"""
}
