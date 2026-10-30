package com.sagoma.planimetria.testi.de

/**
 * Nomi tedeschi degli arredi e dei materiali del catalogo. Il catalogo (scaricato da R2) ha i nomi in italiano: la chiave è
 * il nome italiano ([com.sagoma.planimetria.model.FurnitureCatalog.Item.label] e [com.sagoma.planimetria.model.MaterialCatalog.Item.label]),
 * così una voce con lo stesso nome ha la stessa traduzione. Un nome che manca resta in italiano.
 * Una riga per voce: "nome italiano|nome tedesco".
 */
internal object CatalogoDe {
    val arredi: Map<String, String> by lazy { leggi(ARREDI) }
    val materiali: Map<String, String> by lazy { leggi(MATERIALI) }

    private fun leggi(testo: String): Map<String, String> =
        testo.lineSequence().filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }

    private const val ARREDI = """
Acquario|Aquarium
Albero natale|Weihnachtsbaum
Aloe in vaso|Aloe im Topf
Anfora|Amphore
Anfora in terracotta|Terrakotta-Amphore
Appendiabiti|Kleiderständer
Apple iMac 2004|Apple iMac 2004
Applique|Wandleuchte
Applique a gabbia|Käfig-Wandleuchte
Applique in ottone|Wandleuchte aus Messing
Armadietto alto|Hoher Schrank
Armadio|Kleiderschrank
Armadio classico|Klassischer Kleiderschrank
Armadio con specchio|Kleiderschrank mit Spiegel
Armadio grigio|Grauer Kleiderschrank
Armadio in ciliegio|Kleiderschrank aus Kirschbaum
Armadio in noce|Kleiderschrank aus Nussbaum
Armadio intagliato|Geschnitzter Kleiderschrank
Armadio intagliato scuro|Dunkler geschnitzter Kleiderschrank
Armadio rosso con vetri|Roter Schrank mit Glastüren
Armadio scuro basso|Niedriger dunkler Schrank
Armadio stretto|Schmaler Kleiderschrank
Armadio verde salvia|Salbeigrüner Kleiderschrank
Asciugatrice|Wäschetrockner
Avvolgitubo da giardino|Schlauchtrommel
Baule a colonna|Hohe Truhe
Baule in legno|Holztruhe
Bersaglio per freccette|Dartscheibe
Bidone della spazzatura|Mülltonne
Bidè|Bidet
Bollitore|Wasserkocher
Bonsai|Bonsai
Box doccia|Duschkabine
Braciere in pietra|Feuerschale aus Stein
Buddha|Buddha-Figur
Busto in marmo|Marmorbüste
Cabina doccia|Duschkabine mit Dach
Cactus|Kaktus
Cactus in vaso|Kaktus im Topf
Caminetto|Kleiner Kamin
Camino|Kamin
Camino a legna|Holzkamin
Camino bianco|Weißer Kamin
Camino classico|Klassischer Kamin
Camino in legno scuro|Kamin aus dunklem Holz
Camino in marmo|Marmorkamin
Candelabri in ottone|Kandelaber aus Messing
Candelabro in ferro|Kandelaber aus Eisen
Candele decorative|Dekokerzen
Candeliere|Kerzenständer
Candeliere in ottone|Kerzenständer aus Messing
Cappa|Abzugshaube
Carrello porta attrezzi|Werkzeugwagen
Cassa acustica a colonna|Säulen-Lautsprecher
Cassa acustica nera|Schwarzer Lautsprecher
Cassa bluetooth|Bluetooth-Lautsprecher
Cassa hi-fi|HiFi-Lautsprecher
Cassa su treppiede|Lautsprecher auf Stativ
Cassapanca intagliata|Geschnitzte Truhenbank
Casse acustiche da terra|Boden-Lautsprecher
Casse compatte|Kompakt-Lautsprecher
Casse da scaffale|Regal-Lautsprecher
Cassetta portaoggetti|Ablagebox
Cassettiera aperta|Offene Kommode
Cassettiera bassa|Niedrige Kommode
Cassettiera bianca|Weiße Kommode
Cassettiera bianca moderna|Moderne weiße Kommode
Cavalletto con tela|Staffelei mit Leinwand
Cavalletto da pittore|Malerstaffelei
Cavallo a dondolo|Schaukelpferd
Cestino|Papierkorb
Cestino a rete|Gitterkorb
Cesto|Korb
Cesto con manico|Henkelkorb
Cesto del pane|Brotkorb
Cesto in paglia|Strohkorb
Cesto in vimini|Weidenkorb
Cesto in vimini grande|Großer Weidenkorb
Cesto in vimini ovale|Ovaler Weidenkorb
Chaise longue|Chaiselongue
Chaise longue gialla|Gelbe Chaiselongue
Colonna a giorno|Offenes Hochregal
Colonna libreria|Bücherregal-Säule
Comodino|Nachttisch
Comodino antico|Antiker Nachttisch
Comodino classico|Klassischer Nachttisch
Comodino con cassetti|Nachttisch mit Schubladen
Comodino dipinto|Bemalter Nachttisch
Composizione verde|Grünes Arrangement
Comò bianco|Weiße Kommode mit Aufsatz
Comò gotico|Gotische Kommode
Comò orientale|Orientalische Kommode
Comò scuro|Dunkle Kommode
Congelatore a pozzetto|Gefriertruhe
Console di gioco|Spielkonsole
Consolle classica|Klassischer Konsolentisch
Consolle orientale|Orientalischer Konsolentisch
Cornice decorata|Verzierter Bilderrahmen
Cornice dorata|Vergoldeter Bilderrahmen
Cornice ovale|Ovaler Bilderrahmen
Cornice scura|Dunkler Bilderrahmen
Credenza bianca|Weißes Sideboard
Credenza bianca aperta|Offenes weißes Sideboard
Credenza con alzata|Buffetschrank mit Aufsatz
Credenza con piano in marmo|Sideboard mit Marmorplatte
Credenza con vetrina|Sideboard mit Vitrine
Credenza dipinta|Bemaltes Sideboard
Credenza grigia classica|Klassisches graues Sideboard
Credenza in legno chiaro|Sideboard aus hellem Holz
Credenza rustica|Rustikales Sideboard
Credenza vittoriana|Viktorianisches Sideboard
Cucina a gas bianca|Weißer Gasherd
Cucina a quattro fuochi|Herd mit vier Kochstellen
Cucina bianca|Weißer Herd
Cucina componibile|Einbauküche
Cucina con doppio forno|Herd mit Doppelbackofen
Cucina con forno|Herd mit Backofen
Cucina elettrica|Elektroherd
Cucina professionale|Profi-Standherd
Cucina retrò azzurra|Hellblauer Retro-Herd
Culla|Wiege
Culla a casetta|Hausbettchen
Culla con giostrina|Wiege mit Mobile
Culla in legno|Holzwiege
Cuscini|Kissen
Cuscini decorativi|Dekokissen
Cuscino avorio|Cremeweißes Kissen
Cuscino bianco|Weißes Kissen
Cuscino blu|Blaues Kissen
Cuscino lungo|Langes Kissen
Cuscino nero|Schwarzes Kissen
Cuscino per schienale|Rückenkissen
Cuscino rosa antico|Altrosa Kissen
Dispenser acqua|Wasserspender
Divanetto Chesterfield bianco|Weißes Chesterfield-Zweiersofa
Divanetto arancio|Oranges Zweiersofa
Divanetto bianco|Weißes Zweiersofa
Divanetto grigio|Graues Zweiersofa
Divanetto in pelle scura|Zweiersofa aus dunklem Leder
Divanetto rosso|Rotes Zweiersofa
Divanetto rosso moderno|Modernes rotes Zweiersofa
Divano|Sofa
Divano 2 posti|2-Sitzer-Sofa
Divano 3 posti|3-Sitzer-Sofa
Divano Chesterfield|Chesterfield-Sofa
Divano Chesterfield grigio|Graues Chesterfield-Sofa
Divano Winchester|Winchester-Sofa
Divano ad angolo|Ecksofa
Divano angolare antracite|Ecksofa anthrazit
Divano angolare blu|Blaues Ecksofa
Divano angolare fucsia|Pinkes Ecksofa
Divano angolare grigio|Graues Ecksofa
Divano angolare grigio chiaro|Hellgraues Ecksofa
Divano angolare rosa|Rosa Ecksofa
Divano antracite|Sofa anthrazit
Divano antracite a tre posti|Dreisitzer-Sofa anthrazit
Divano azzurro|Hellblaues Sofa
Divano azzurro polvere|Taubenblaues Sofa
Divano bianco classico|Klassisches weißes Sofa
Divano chiaro|Helles Sofa
Divano classico bianco|Weißes klassisches Sofa
Divano classico chiaro|Helles klassisches Sofa
Divano componibile|Modulsofa
Divano con braccioli in legno|Sofa mit Holzarmlehnen
Divano con penisola grigio|Graues Sofa mit Recamiere
Divano con plaid|Sofa mit Plaid
Divano con pouf|Sofa mit Hocker
Divano due posti|Zweisitzer-Sofa
Divano giallo|Gelbes Sofa
Divano grigio chiaro|Hellgraues Sofa
Divano grigio con cuscini|Graues Sofa mit Kissen
Divano imbottito|Gepolstertes Sofa
Divano in legno|Holzsofa
Divano in pelle marrone|Braunes Ledersofa
Divano letto|Schlafsofa
Divano letto aperto|Ausgeklapptes Schlafsofa
Divano letto bianco|Weißes Schlafsofa
Divano letto grigio|Graues Schlafsofa
Divano letto rosa|Rosa Schlafsofa
Divano moderno grigio|Modernes graues Sofa
Divano modulare bianco|Weißes Modulsofa
Divano nero con coperta|Schwarzes Sofa mit Decke
Divano orientale|Orientalisches Sofa
Divano rosa capitonné|Rosa Chesterfield-Sofa
Divano scandinavo|Skandinavisches Sofa
Dormeuse|Tagesbett
Enciclopedia|Enzyklopädie
Estensione ad isola|Inselerweiterung
Faro da esterno|Außenstrahler
Fasciatoio|Wickeltisch
Felce di Boston|Schwertfarn
Ficus|Ficus
Ficus in vaso|Ficus im Topf
Finestra con tende rosse|Fenster mit roten Vorhängen
Fiori in vaso|Blumen im Topf
Fioriera|Pflanzkasten
Fioriera alta|Hoher Pflanzkübel
Fioriera lunga|Langer Pflanzkasten
Fontana decorativa|Zierbrunnen
Fornetto|Minibackofen
Forno|Backofen
Forno bianco|Weißer Backofen
Forno doppio|Doppelbackofen
Frigorifero|Kühlschrank
Frigorifero americano|Amerikanischer Kühlschrank
Frigorifero antracite|Anthrazitfarbener Kühlschrank
Frigorifero bianco|Weißer Kühlschrank
Frigorifero bianco classico|Klassischer weißer Kühlschrank
Frigorifero con freezer|Kühl-Gefrier-Kombination
Frigorifero doppio|Doppelter Kühlschrank
Frigorifero retrò|Retro-Kühlschrank
Frigorifero side by side|Side-by-Side-Kühlschrank
Frigorifero vintage aperto|Offener Vintage-Kühlschrank
Futon|Futon
Gatto in cemento|Katze aus Beton
Giradischi a valigetta|Koffer-Plattenspieler
Grammofono|Grammophon
Griglia da esterno|Gartengrill
Guardaroba|Dielenschrank
Il bacio|Der Kuss
Isola con lavello|Kochinsel mit Spüle
Isola cucina moderna|Moderne Kochinsel
Lampada|Lampe
Lampada a muro|Wandlampe
Lampada a paralume grigio|Lampe mit grauem Schirm
Lampada a sospensione|Pendelleuchte
Lampada a sospensione lunga|Lange Pendelleuchte
Lampada classica|Klassische Lampe
Lampada classica bianca|Klassische weiße Lampe
Lampada con paralume|Lampe mit Schirm
Lampada con paralume beige|Lampe mit beigem Schirm
Lampada con paralume plissé|Lampe mit Plisseeschirm
Lampada da banchiere|Bankerlampe
Lampada da banchiere verde|Grüne Bankerlampe
Lampada da esterno|Außenleuchte
Lampada da lettura|Leselampe
Lampada da scrivania|Schreibtisch-Lampe
Lampada da soffitto|Deckenlampe
Lampada da tavolo|Tischlampe
Lampada da tavolo industriale|Tischlampe im Industriestil
Lampada da terra classica|Klassische Stehlampe
Lampada da terra sottile|Schmale Stehlampe
Lampada da terra vintage|Vintage-Stehlampe
Lampada in bronzo|Bronzelampe
Lampada in porcellana|Porzellanlampe
Lampada moderna|Moderne Lampe
Lampada sospesa|Hängelampe
Lampada verde a fungo|Grüne Pilzlampe
Lampadario|Kronleuchter
Lampadario a bracci|Armleuchter
Lampadario a candele in ferro|Kerzen-Kronleuchter aus Eisen
Lampadario a candele nero|Schwarzer Kerzen-Kronleuchter
Lampadario a lanterna|Laternen-Hängeleuchte
Lampadario classico|Klassischer Kronleuchter
Lampadario con paralumi|Kronleuchter mit Schirmen
Lampadario con paralumi grigi|Kronleuchter mit grauen Schirmen
Lampadario di cristallo|Kronleuchter aus Kristall
Lampadario di cristallo azzurro|Hellblauer Kristall-Kronleuchter
Lampadario in ferro|Kronleuchter aus Eisen
Lampadario in legno|Kronleuchter aus Holz
Lampadario in ottone|Kronleuchter aus Messing
Lampadario in ottone sottile|Schmaler Kronleuchter aus Messing
Lampadario orientale|Orientalischer Kronleuchter
Lampadario rustico|Rustikaler Kronleuchter
Lampadario viola|Violetter Kronleuchter
Lampione da giardino|Gartenlaterne
Lampione da giardino in legno|Gartenlaterne aus Holz
Lanterna appesa|Hängelaterne
Lanterna da giardino|Gartenleuchte
Lanterna da terra|Bodenlaterne
Lanterna di carta|Papierlaterne
Lanterna giapponese|Japanische Laterne
Lanterna in legno|Holzlaterne
Lanterna in pietra|Steinlaterne
Lavabo a colonna|Stand-Waschbecken
Lavabo bianco|Weißes Waschbecken
Lavabo da appoggio|Aufsatz-Waschbecken
Lavabo ergonomico|Barrierefreies Waschbecken
Lavabo in pietra|Waschbecken aus Stein
Lavabo piccolo|Kleines Waschbecken
Lavabo quadrato|Eckiges Waschbecken
Lavabo quadrato in acciaio|Eckiges Waschbecken aus Stahl
Lavabo sospeso|Wand-Waschbecken
Lavagna bianca|Whiteboard
Lavamani|Hand-Waschbecken
Lavanda|Lavendel
Lavandino|Waschbecken
Lavandino con doppio specchio|Waschbecken mit Doppelspiegel
Lavandino con specchio|Waschbecken mit Spiegel
Lavastoviglie|Geschirrspüler
Lavastoviglie piccola|Kompakt-Geschirrspüler
Lavatrice|Waschmaschine
Lavatrice a carica dall'alto|Toplader-Waschmaschine
Lavatrice frontale|Frontlader-Waschmaschine
Lavello bianco|Weiße Küchenspüle
Lavello da incasso|Einbauspüle
Lavello doppio in acciaio|Doppelspüle aus Edelstahl
Lavello grigio|Graue Küchenspüle
Lavello in acciaio|Edelstahlspüle
Lavello nero|Schwarze Küchenspüle
Lavello nero a una vasca|Schwarze Einzelspüle
Lavello nero compatto|Kompakte schwarze Spüle
Lettino|Kleines Bett
Lettino prendisole|Sonnenliege
Letto|Bett
Letto a castello bianco|Weißes Etagenbett
Letto a castello bianco classico|Klassisches weißes Etagenbett
Letto a castello con scaletta|Etagenbett mit Leiter
Letto a castello curvo|Geschwungenes Etagenbett
Letto a castello in legno|Etagenbett aus Holz
Letto a castello in metallo|Etagenbett aus Metall
Letto a castello rosa|Rosa Etagenbett
Letto a giorno|Tagesliege
Letto bianco a spalliere|Weißes Bett mit Kopf- und Fußteil
Letto con cassetti|Bett mit Schubladen
Letto con testiera nera|Bett mit schwarzem Kopfteil
Letto gotico|Gotisches Bett
Letto imbottito rosa|Rosa Polsterbett
Letto in ferro|Eisenbett
Letto in ferro battuto|Bett aus Schmiedeeisen
Letto in ferro blu|Blaues Eisenbett
Letto in legno|Holzbett
Letto in legno chiaro|Bett aus hellem Holz
Letto in legno scuro|Bett aus dunklem Holz
Letto in metallo con coperta|Metallbett mit Decke
Letto in ottone|Messingbett
Letto matrimoniale moderno|Modernes Doppelbett
Letto moderno bianco|Modernes weißes Bett
Letto per bambini|Kinderbett
Letto singolo|Einzelbett
Letto singolo in ferro|Einzelbett aus Eisen
Letto singolo in legno|Einzelbett aus Holz
Letto soppalcato|Hochbett
Libreria|Bücherregal
Libreria a vetri|Bücherschrank mit Glastüren
Libreria antica|Antiker Bücherschrank
Libreria azzurra|Hellblaues Bücherregal
Libreria con libri|Bücherregal mit Büchern
Libreria credenza|Bücherregal mit Unterschrank
Libreria grande|Großes Bücherregal
Libreria grigia|Graues Bücherregal
Libreria in legno|Bücherregal aus Holz
Libreria rustica|Rustikales Bücherregal
Libreria stretta|Schmales Bücherregal
Lume a petrolio|Petroleumlampe
Macchina da caffé|Kaffeemaschine
Macchina del caffè|Kaffeemaschine mit Kanne
Macchina del pane|Brotbackautomat
Madia moderna|Modernes Sideboard
Madia scura|Dunkles Sideboard
Maniglione pieghevole|Klappbarer Haltegriff
Mappamondo antico|Antiker Globus
Mappamondo su piedistallo|Globus auf Standfuß
Materasso da pavimento|Bodenmatratze
Microonde|Mikrowelle
Microonde bianco|Weiße Mikrowelle
Microonde blu|Blaue Mikrowelle
Microonde classico|Klassische Mikrowelle
Microonde grigio|Graue Mikrowelle
Microonde in acciaio|Edelstahl-Mikrowelle
Microonde nero|Schwarze Mikrowelle
Miscelatore per doccia|Duscharmatur
Miscelatore per vasca|Wannenarmatur
Miscelatore per vasca e doccia|Wannen- und Duscharmatur
Mobile TV|TV-Möbel
Mobile TV anni '50|TV-Möbel im 50er-Stil
Mobile TV vintage|Vintage-TV-Möbel
Mobile a cassetti|Schrank mit Schubladen
Mobile ad angolo inferiore|Eckunterschrank
Mobile ad angolo superiore|Eckhängeschrank
Mobile ad angolo superiore piccolo|Kleiner Eckhängeschrank
Mobile alto|Hochschrank
Mobile alto in legno|Hochschrank aus Holz
Mobile bagno moderno|Modernes Badmöbel
Mobile con cassetti|Schrank mit Schubladen
Mobile con mensole|Schrank mit Fächern
Mobile con vetri|Schrank mit Glastüren
Mobile inferiore|Unterschrank
Mobile lavabo|Waschtisch mit Unterschrank
Mobile lavandino|Waschbecken-Unterschrank
Mobile lavello|Spülenschrank
Mobile orientale|Orientalischer Schrank
Mobile soggiorno|Wohnwand
Mobile superiore|Hängeschrank
Mobile vetrina chiaro|Helle Vitrine
Mobile vetrina scuro|Dunkle Vitrine
Mobiletto blu|Kleiner blauer Schrank
Mobiletto gotico|Kleiner gotischer Schrank
Mobiletto in legno|Kleiner Holzschrank
Monstera in vaso|Monstera im Topf
Ombrellone bianco|Weißer Sonnenschirm
Ombrellone nero|Schwarzer Sonnenschirm
Ombrellone rosa|Rosa Sonnenschirm
Orinale|Urinal
Orologio a cucù|Kuckucksuhr
Orologio a numeri romani|Uhr mit römischen Ziffern
Orologio a raggi|Wanduhr im Strahlendesign
Orologio da mensola|Kaminuhr
Orologio da parete|Wanduhr
Orologio da parete a telefono|Telefon-Wanduhr
Orologio da parete classico|Klassische Wanduhr
Orologio da parete grigio|Graue Wanduhr
Orologio da parete tondo|Runde Wanduhr
Pagoda in pietra|Steinpagode
Palme in vaso|Palmen im Topf
Panca capitonné|Gesteppte Polsterbank
Panca con schienale|Bank mit Lehne
Panca in legno|Holzbank
Panca in legno massello|Bank aus Massivholz
Panchetta imbottita|Gepolsterte Fußbank
Panchina classica|Klassische Bank
Panchina da giardino|Gartenbank
Panchina da parco|Parkbank
Panchina in ferro|Eisenbank
Panchina moderna|Moderne Bank
Panchina verde|Grüne Bank
Paravento|Paravent
Pattumiera in acciaio|Treteimer aus Edelstahl
Pendolo|Pendeluhr
Pendolo antico|Antike Pendeluhr
Pendolo classico|Klassische Pendeluhr
Pendolo in mogano|Pendeluhr aus Mahagoni
Pendolo moderno|Moderne Pendeluhr
Piano cottura|Kochfeld
Piano cottura a gas a due fuochi|Gaskochfeld mit zwei Flammen
Piano cottura a induzione|Induktions-Kochfeld
Pianoforte|Klavier
Pianoforte a coda|Flügel
Pianoforte verticale|Klavier (Pianino)
Pianoforte verticale classico|Klassisches Klavier
Pianoforte verticale nero|Schwarzes Klavier
Pianoforte verticale scuro|Dunkles Klavier
Pianta|Pflanze
Pianta grande|Große Pflanze
Pianta grassa|Sukkulente
Pianta grassa in vaso viola|Sukkulente im violetten Topf
Pianta in vaso|Topfpflanze
Pianta piccola|Kleine Pflanze
Pianta tropicale|Tropische Pflanze
Piccola lampada|Kleine Lampe
PlayStation|PlayStation
Poltrona|Sessel
Poltrona Winchester|Winchester-Sessel
Poltrona a dondolo in legno|Schaukelsessel aus Holz
Poltrona a dondolo moderna|Moderner Schaukelsessel
Poltrona antica|Antiker Sessel
Poltrona beige|Beiger Sessel
Poltrona bergère bianca|Weißer Bergère-Sessel
Poltrona bergère verde|Grüner Bergère-Sessel
Poltrona blu design|Blauer Designsessel
Poltrona capitonné|Chesterfield-Sessel
Poltrona capitonné bordeaux|Bordeauxroter Chesterfield-Sessel
Poltrona classica|Klassischer Sessel
Poltrona classica grigia|Klassischer grauer Sessel
Poltrona con cuscino|Sessel mit Kissen
Poltrona da doccia|Duschsessel
Poltrona da relax capitonné|Gesteppter Relaxsessel
Poltrona di design|Designsessel
Poltrona girevole|Drehsessel
Poltrona grigia|Grauer Sessel
Poltrona in pelle beige|Beiger Ledersessel
Poltrona in pelle cuoio|Cognacfarbener Ledersessel
Poltrona moderna|Moderner Sessel
Poltrona orientale|Orientalischer Sessel
Poltrona reclinabile|Liegesessel
Poltrona rosa|Rosa Sessel
Poltrona rosa cipria|Puderrosa Sessel
Poltrona rossa|Roter Sessel
Poltrona rossa moderna|Moderner roter Sessel
Poltrona tortora|Taupefarbener Sessel
Poltrona viola|Violetter Sessel
Poltroncina|Kleiner Sessel
Poltroncina bianca|Kleiner weißer Sessel
Poltroncina koala|Koala-Sessel
Porta asciugamano|Handtuchhalter
Porta doccia|Duschtür
Portafoto|Bilderrahmen zum Aufstellen
Portafoto grande|Großer Bilderrahmen zum Aufstellen
Portafrutta|Obstschale
Portarotolo|WC-Rollenhalter
Portasciugamani|Handtuchständer
Portasciugamani a barra|Handtuchstange
Portasciugamani a muro|Wand-Handtuchhalter
Portatile|Laptop
Pothos in vaso|Efeutute im Topf
Pouf|Pouf
Pouf a sacco marrone|Brauner Sitzsack
Pouf a sacco rosso|Roter Sitzsack
Pouf a sacco viola|Violetter Sitzsack
Pouf antracite|Anthrazitfarbener Pouf
Pouf bianco|Weißer Pouf
Pouf blu capitonné|Blauer gesteppter Pouf
Pouf capitonné|Gesteppter Pouf
Pouf capitonné grigio|Grauer gesteppter Pouf
Pouf grigio|Grauer Pouf
Pouf in pelle nera|Pouf aus schwarzem Leder
Pouf nero|Schwarzer Pouf
Pouf rotondo|Runder Pouf
Pouf tondo bordeaux|Runder bordeauxroter Pouf
Puff|Fußhocker
Quadro|Gemälde
Quadro grande|Großes Gemälde
Quadro grigio|Graues Gemälde
Quadro in legno|Holzbild
Quadro rosa|Rosa Gemälde
Radio giradischi vintage|Vintage-Radio mit Plattenspieler
Ripiani superiori|Obere Regalböden
Rose|Rosen
Salottino da esterno|Lounge-Set für draußen
Scaffale con cassetti|Regal mit Schubladen
Scaffale in metallo|Metallregal
Scaffale metallico|Regal aus Metall
Scaffale metallico alto|Hohes Metallregal
Scaffale metallico stretto|Schmales Metallregal
Scaffale verticale|Hochregal
Scaffaletto dipinto|Kleines bemaltes Regal
Scaffali|Regale
Schedario bianco|Weißer Aktenschrank
Schedario in metallo|Aktenschrank aus Metall
Scrivania|Schreibtisch
Scrivania antica|Antiker Schreibtisch
Scrivania bianca|Weißer Schreibtisch
Scrivania classica|Klassischer Schreibtisch
Scrivania con computer|Schreibtisch mit Computer
Scrivania con computer grigia|Grauer Schreibtisch mit Computer
Scrivania in legno|Holzschreibtisch
Scrivania in metallo|Schreibtisch aus Metall
Scrivania in mogano|Schreibtisch aus Mahagoni
Scrivania in noce|Schreibtisch aus Nussbaum
Scrivania nera|Schwarzer Schreibtisch
Scrivania regolabile|Sitz-Steh-Schreibtisch
Scrivania semplice|Einfacher Schreibtisch
Scrivania ufficio a L|Büro-Eckschreibtisch
Scultura del pensatore|Skulptur Der Denker
Scultura in bronzo|Bronzeskulptur
Sedia|Stuhl
Sedia Osaka|Osaka-Stuhl
Sedia Savonarola|Savonarola-Stuhl
Sedia Tolix|Tolix-Stuhl
Sedia Windsor nera|Schwarzer Windsor-Stuhl
Sedia a dondolo|Schaukelstuhl
Sedia a dondolo bianca|Weißer Schaukelstuhl
Sedia a dondolo classica|Klassischer Schaukelstuhl
Sedia a dondolo gialla|Gelber Schaukelstuhl
Sedia a dondolo grigia|Grauer Schaukelstuhl
Sedia a dondolo imbottita|Gepolsterter Schaukelstuhl
Sedia a dondolo in cuoio|Schaukelstuhl aus Leder
Sedia a dondolo in legno|Schaukelstuhl aus Holz
Sedia a dondolo in legno scuro|Schaukelstuhl aus dunklem Holz
Sedia a dondolo rosa|Rosa Schaukelstuhl
Sedia a dondolo scura|Dunkler Schaukelstuhl
Sedia alta scura|Dunkler hoher Stuhl
Sedia anni '50|Stuhl im 50er-Stil
Sedia bianca|Weißer Stuhl
Sedia bianca classica|Klassischer weißer Stuhl
Sedia bianca e grigia|Weiß-grauer Stuhl
Sedia bistrot|Bistrostuhl
Sedia classica|Klassischer Stuhl
Sedia con braccioli|Armlehnstuhl
Sedia con cuscino|Stuhl mit Kissen
Sedia da bar|Barstuhl mit Lehne
Sedia da doccia|Duschstuhl
Sedia da gaming|Gaming-Stuhl
Sedia da giardino|Gartenstuhl
Sedia da pranzo|Esszimmerstuhl
Sedia da ufficio|Bürostuhl
Sedia dipinta|Bemalter Stuhl
Sedia dipinta azzurra|Hellblau bemalter Stuhl
Sedia girevole|Drehstuhl
Sedia gotica|Gotischer Stuhl
Sedia in Plastica|Kunststoffstuhl
Sedia in faggio|Buchenstuhl
Sedia in metallo|Metallstuhl
Sedia intagliata|Geschnitzter Stuhl
Sedia per bambini|Kinderstuhl
Sedia quercia|Eichenstuhl
Sedia rosa|Rosa Stuhl
Sedia rustica|Rustikaler Stuhl
Sedia ryokan|Ryokan-Stuhl
Sedia scandinava|Skandinavischer Stuhl
Sedile ribaltabile a muro|Wand-Klappsitz
Seggiolone|Hochstuhl für Kinder
Servizio da tè|Teeservice
Set da giardino in metallo|Gartenset aus Metall
Set da patio con ombrellone|Terrassenset mit Sonnenschirm
Set da pranzo bianco|Weiße Essgruppe
Set da pranzo grigio|Graue Essgruppe
Set da pranzo in ferro|Essgruppe aus Eisen
Set da pranzo rustico|Rustikale Essgruppe
Set da pranzo scuro|Dunkle Essgruppe
Settimanale|Hohe Kommode
Sfera armillare|Armillarsphäre
Sgabello|Hocker
Sgabello alto in legno|Hoher Holzhocker
Sgabello basso|Niedriger Hocker
Sgabello bianco|Weißer Hocker
Sgabello da bar|Barhocker
Sgabello da bar in metallo|Barhocker aus Metall
Sgabello da officina|Werkstatthocker
Sgabello da pianoforte|Klavierhocker
Sgabello dipinto|Bemalter Hocker
Sgabello girevole|Drehhocker
Sgabello in legno|Holzhocker
Sgabello in legno chiaro|Hocker aus hellem Holz
Sgabello in metallo|Metallhocker
Sgabello industriale|Hocker im Industriestil
Sgabello orientale|Orientalischer Hocker
Sgabello per pianoforte|Klavierbank
Sgabello pieghevole|Klapphocker
Sgabello rustico|Rustikaler Hocker
Soffione doccia e rubinetto|Duschkopf und Armatur
Sospensione industriale|Pendelleuchte im Industriestil
Specchio ad arco|Bogenspiegel
Specchio barocco|Barockspiegel
Specchio capitonné|Gesteppter Spiegel
Specchio con cornice scura|Spiegel mit dunklem Rahmen
Specchio da terra|Standspiegel
Specchio decorato|Verzierter Spiegel
Specchio dorato|Goldener Spiegel
Specchio in stile impero|Spiegel im Empirestil
Specchio ovale|Ovaler Spiegel
Stampante e scanner|Drucker und Scanner
Statua classica|Klassische Statue
Statua di cavallo|Pferdestatue
Statua di donna|Frauenstatue
Statua egizia|Ägyptische Statue
Statua in terracotta|Terrakotta-Statue
Stereo portatile|Tragbare Stereoanlage
Stufa|Ofen
Stufa a legna|Holzofen
Sveglia|Wecker
Sveglia vintage|Vintage-Wecker
TV|Fernseher
TV 16:9|Fernseher 16:9
TV a muro|Wandfernseher
Tapis roulant|Laufband
Tapis roulant pieghevole|Klappbares Laufband
Tappetino azzurro|Hellblaue Matte
Tappetino da bagno bianco|Weißer Badvorleger
Tappetino grigio|Graue Matte
Tappetino in spugna|Frottee-Vorleger
Tappetino rosa|Rosa Matte
Tappeto grigio|Grauer Teppich
Tappeto orientale arancio|Oranger Orientteppich
Tappeto persiano rosso|Roter Perserteppich
Tappeto rotondo|Runder Teppich
Tavolino|Beistelltisch
Tavolino a colonna|Säulen-Beistelltisch
Tavolino alto|Hoher Beistelltisch
Tavolino alto in legno|Hoher Beistelltisch aus Holz
Tavolino basso|Couchtisch
Tavolino bistrot|Bistrotisch
Tavolino con brocca|Beistelltisch mit Krug
Tavolino con intreccio|Geflochtener Beistelltisch
Tavolino con tovaglia|Beistelltisch mit Tischdecke
Tavolino da tè|Teetisch
Tavolino gotico|Gotischer Beistelltisch
Tavolino in legno|Beistelltisch aus Holz
Tavolino industriale|Beistelltisch im Industriestil
Tavolino intagliato|Geschnitzter Beistelltisch
Tavolino laterale|Sofatisch
Tavolino moderno|Moderner Beistelltisch
Tavolino ovale|Ovaler Beistelltisch
Tavolino per bambini|Kindertisch
Tavolino rotondo|Runder Beistelltisch
Tavolino rotondo in legno|Runder Beistelltisch aus Holz
Tavolino tondo design|Runder Design-Beistelltisch
Tavolino tondo in legno|Runder Holztisch
Tavolino tondo in noce|Runder Beistelltisch aus Nussbaum
Tavolino tondo scandinavo|Runder skandinavischer Beistelltisch
Tavolino verde intagliato|Grüner geschnitzter Beistelltisch
Tavolo|Tisch
Tavolo bianco|Weißer Tisch
Tavolo bianco classico|Klassischer weißer Tisch
Tavolo bianco da cucina|Weißer Küchentisch
Tavolo bianco moderno|Moderner weißer Tisch
Tavolo con panche|Tisch mit Bänken
Tavolo con tovaglia|Tisch mit Tischdecke
Tavolo con tovaglia blu|Tisch mit blauer Tischdecke
Tavolo da bar|Bartisch
Tavolo da caffè|Kaffeehaustisch
Tavolo da picnic|Picknicktisch
Tavolo design|Designtisch
Tavolo dipinto|Bemalter Tisch
Tavolo e sedie da giardino|Gartentisch mit Stühlen
Tavolo grande con sedie|Großer Tisch mit Stühlen
Tavolo grigio|Grauer Tisch
Tavolo grigio scuro|Dunkelgrauer Tisch
Tavolo in legno|Holztisch
Tavolo in legno chiaro|Tisch aus hellem Holz
Tavolo in legno di Tek|Teakholztisch
Tavolo in legno di ontano|Erlenholztisch
Tavolo in legno massello|Massivholztisch
Tavolo in legno rosso|Tisch aus rotem Holz
Tavolo in vetro|Glastisch
Tavolo lungo|Langer Tisch
Tavolo nero|Schwarzer Tisch
Tavolo quadrato|Quadratischer Tisch
Tavolo quercia|Eichentisch
Tavolo rotondo|Runder Tisch
Tavolo rotondo grande|Großer runder Tisch
Tavolo scuro|Dunkler Tisch
Tavolo semplice|Einfacher Tisch
Tavolo su cavalletti|Tisch auf Böcken
Tavolo tecnico|Zeichentisch
Tavolo tondo|Runder Esstisch
Tavolo tulipano|Tulpentisch
Tecnigrafo|Zeichenmaschine
Televisore a schermo piatto|Flachbild-Fernseher
Televisore anni '60|Fernseher aus den 60ern
Televisore anni '80|Fernseher aus den 80ern
Televisore in legno|Fernseher mit Holzgehäuse
Televisore portatile|Tragbarer Fernseher
Televisore portatile vintage|Tragbarer Vintage-Fernseher
Televisore retrò|Retro-Fernseher
Televisore su gambe|Fernseher auf Beinen
Televisore vintage|Vintage-Fernseher
Telo per proiettore|Beamer-Leinwand
Tenda a pannello|Flächenvorhang
Tenda su bastone|Vorhang an der Stange
Tende bianche|Weiße Vorhänge
Tende bordeaux|Bordeauxrote Vorhänge
Tende con mantovana|Vorhänge mit Querbehang
Tende di lino|Leinenvorhänge
Termoarredo|Design-Heizkörper
Termosifone a colonne|Röhrenheizkörper
Termosifone a colonne bianco|Weißer Röhrenheizkörper
Termosifone a pannello|Plattenheizkörper
Termosifone bianco|Weißer Heizkörper
Termosifone decorativo|Dekorativer Heizkörper
Termosifone in ghisa|Gusseisen-Heizkörper
Termosifone piatto|Flacher Heizkörper
Toilette giapponese|Japanische Toilette
Toletta|Schminktisch
Tostapane|Toaster
Turca|Hocktoilette
Unità esterne del condizionatore|Klimaanlagen-Außengeräte
Urinale|Pissoir
Valigie vintage|Vintage-Koffer
Vasca ad angolo|Eckbadewanne
Vasca ad isola|Freistehende Inselbadewanne
Vasca con doccia|Badewanne mit Dusche
Vasca con piedini|Badewanne mit Füßen
Vasca con piedini bianca|Weiße Badewanne mit Füßen
Vasca con rubinetto|Badewanne mit Armatur
Vasca freestanding|Freistehende Badewanne
Vasca in ottone|Badewanne aus Messing
Vasca ovale|Ovale Badewanne
Vasca rettangolare|Rechteckige Badewanne
Vasca vittoriana|Viktorianische Badewanne
Vaso antico|Antike Vase
Vaso bianco|Weiße Vase
Vaso bianco e blu|Blau-weiße Vase
Vaso bianco panciuto|Bauchige weiße Vase
Vaso celadon|Seladon-Vase
Vaso di terracotta|Terrakottatopf
Vaso in ceramica|Keramikvase
Vaso in ceramica alto|Hohe Keramikvase
Vaso in ottone|Messingvase
Vaso in ottone alto|Hohe Messingvase
Vaso in terracotta|Terrakottavase
Vaso lilla|Lila Vase
Vaso rosa antico|Altrosa Vase
Vaso smaltato|Glasierte Vase
Vaso verde|Grüne Vase
Venere di Milo|Venus von Milo
Ventilatore a soffitto|Deckenventilator
Ventilatore da tavolo|Tischventilator
Ventilatore in ottone|Messingventilator
Ventilatore vintage|Vintage-Ventilator
Vetrina bianca|Weiße Vitrine
Vetrina con ante|Vitrine mit Türen
Vetrinetta|Kleine Vitrine
Vetrinetta a giorno|Kleines offenes Regal
Vetrinetta antica|Antike Vitrine
Videoproiettore|Beamer
WC bianco|Weiße Toilette
WC classico|Klassische Toilette
WC compatto|Kompakte Toilette
WC con cassetta|Toilette mit Spülkasten
WC moderno|Moderne Toilette
WC nero|Schwarze Toilette
Water|Toilette
Zerbino beige|Beige Fußmatte
Zerbino marrone|Braune Fußmatte
"""

    private const val MATERIALI = """
Ardesia a lastre|Schiefer in Platten
Assi chiare|Helle Dielen
Assi chiare nodose|Helle astige Dielen
Assi color miele|Honigfarbene Dielen
Assi da ponte|Gerüstbohlen
Assi scure|Dunkle Dielen
Assi vissute|Gebrauchte Dielen
Autobloccanti|Verbund-Pflaster
Blocchi grigi|Graue Blöcke
Carta da parati jacquard|Jacquard-Tapete
Carta da parati marmorizzata|Tapete in Marmoroptik
Cementine bianco e nero|Zementfliesen schwarz-weiß
Cementine decorate|Gemusterte Zementfliesen
Cementine geometriche|Geometrische Zementfliesen
Cemento a lastre|Betonplatten
Cemento chiaro|Heller Beton
Cemento grigio|Grauer Beton
Cemento scuro|Dunkler Beton
Cemento spatolato|Gespachtelter Beton
Ciottolato|Kopfstein-Pflaster
Cotto|Cotto-Fliesen
Cotto chiaro|Helle Cotto-Fliesen
Cotto scuro|Dunkle Cotto-Fliesen
Doghe bianche|Weiße Paneele
Doghe in legno|Holzpaneele
Doghe rosse|Rote Paneele
Doghe strette scure|Schmale dunkle Paneele
Esagonette bianche|Weiße Sechseck-Fliesen
Esagonette nere|Schwarze Sechseck-Fliesen
Esagoni in cemento|Sechsecke aus Beton
Ghiaia|Kies
Gres antracite a correre|Feinsteinzeug anthrazit, wilder Verband
Gres ardesia|Feinsteinzeug in Schieferoptik
Gres beige|Feinsteinzeug beige
Gres bianco 60×60|Feinsteinzeug weiß 60×60
Gres bianco grande formato|Feinsteinzeug weiß, Großformat
Gres grafite|Feinsteinzeug graphit
Gres nero|Feinsteinzeug schwarz
Gres pietra chiara|Feinsteinzeug in heller Steinoptik
Intonaco bianco|Weißer Putz
Intonaco fine|Feinputz
Intonaco grigio|Grauer Putz
Intonaco liscio|Glattputz
Intonaco rustico|Rauputz
Juta|Jute
Juta fine|Jute fein
Laminato rovere|Laminat Eiche
Lana a righe|Wolle gestreift
Lana grigia|Wolle grau
Lana grigia melange|Wolle grau meliert
Lana intrecciata bianca|Wolle weiß geflochten
Lana rossa a coste|Wolle rot gerippt
Lastre a intreccio|Platten im Flechtmuster
Lastre beige|Beige Platten
Lastre di marmo rosa|Platten aus rosa Marmor
Lastre di pietra beige|Beige Steinplatten
Lastre di pietra chiara|Helle Steinplatten
Lastre grigie|Graue Platten
Lastre nere lucide|Glänzende schwarze Platten
Lino verde|Leinen grün
Listoni noce|Dielen Nussbaum
Listoni rovere|Dielen Eiche
Listoni rovere naturale|Dielen Eiche natur
Listoni teak|Dielen Teak
Maioliche blu|Majolika blau
Marmo Emperador chiaro|Marmor Emperador hell
Marmo bianco venato|Weißer geäderter Marmor
Marmo blu notte|Nachtblauer Marmor
Marmo crema|Marmor creme
Marmo di Carrara|Carrara-Marmor
Marmo grigio|Grauer Marmor
Marmo grigio a lastre|Grauer Marmor in Platten
Marmo grigio perla|Perlgrauer Marmor
Marmo nero Marquinia|Marmor Nero Marquina
Marmo nero lucido|Glänzender schwarzer Marmor
Marmo nero venato|Schwarzer geäderter Marmor
Marmo oro|Goldener Marmor
Marmo tortora|Taupefarbener Marmor
Marmo verde|Grüner Marmor
Mattoncini bianchi|Weiße Riemchen
Mattoncini verdi|Grüne Riemchen
Mattonelle chiare|Helle Fliesen
Mattoni antichi|Alte Ziegel
Mattoni arancio|Orange Ziegel
Mattoni arancio lisci|Glatte orange Ziegel
Mattoni bianchi|Weiße Ziegel
Mattoni bruciati|Gebrannte dunkle Ziegel
Mattoni crema|Cremefarbene Ziegel
Mattoni da esterno|Ziegel für außen
Mattoni faccia a vista|Sichtziegel
Mattoni gialli|Gelbe Ziegel
Mattoni misti|Gemischte Ziegel
Mattoni rossi|Rote Ziegel
Mattoni rustici|Rustikale Ziegel
Mattoni scuri|Dunkle Ziegel
Mattoni vissuti|Gebrauchte Ziegel
Moquette a fiori|Teppichboden geblümt
Moquette a quadri grigia|Teppichboden grau kariert
Moquette avorio|Teppichboden elfenbein
Moquette beige|Teppichboden beige
Moquette beige a riquadri|Teppichboden beige mit Feldern
Moquette bianca|Teppichboden weiß
Moquette blu|Teppichboden blau
Moquette blu a quadri|Teppichboden blau kariert
Moquette rosa melange|Teppichboden rosa meliert
Moquette rossa|Teppichboden rot
Moquette verde oliva|Teppichboden olivgrün
Mosaico acquamarina|Mosaik aquamarin
Mosaico azzurro|Mosaik hellblau
Mosaico grigio|Mosaik grau
Muro in pietra|Steinmauer
Muro in pietra grezza|Mauer aus Bruchstein
Onice azzurro|Blauer Onyx
Onice bianco|Weißer Onyx
Onice miele|Honigfarbener Onyx
Onice nero|Schwarzer Onyx
Onice rosso|Roter Onyx
Onice verde acqua|Wassergrüner Onyx
Opus incertum|Opus incertum
Paglia intrecciata|Stroh geflochten
Parquet a cesto|Parkett im Flechtverband
Parquet a intreccio chiaro|Helles Flechtparkett
Parquet a intreccio scuro|Dunkles Flechtparkett
Parquet a quadri|Würfelparkett
Parquet a riquadri|Tafelparkett
Parquet a riquadri classico|Klassisches Tafelparkett
Parquet acero|Ahornparkett
Parquet anticato|Antik-Parkett
Parquet ciliegio|Parkett Kirschbaum
Parquet in diagonale|Parkett diagonal
Parquet noce|Parkett Nussbaum
Parquet rovere miele|Parkett Eiche honig
Pelliccia geometrica|Kunstfell geometrisch
Perlinato scuro|Dunkles Profilholz
Piastrelle a scacchi|Schachbrett-Fliesen
Piastrelle beige|Beige Fliesen
Piastrelle bianche 10×10|Weiße Fliesen 10×10
Piastrelle bianche lucide|Glänzende weiße Fliesen
Piastrelle blu|Blaue Fliesen
Piastrelle blu antiche|Alte blaue Fliesen
Piastrelle in diagonale|Diagonal verlegte Fliesen
Piastrelle in graniglia|Terrazzo-Fliesen
Piastrelle marroni|Braune Fliesen
Pietra a opus|Stein polygonal
Pietra a secco|Trockenmauer
Pietra a strati|Schichtstein
Pietra beige|Beiger Stein
Pietra gialla|Gelber Stein
Pietre con erba|Trittsteine im Rasen
Porfido|Porphyr
Porfido a ventaglio|Porphyr im Bogenpflaster
Prato|Rasen
Prato all'inglese|Englischer Rasen
Quadri grigi|Graue Karos
Quadrotte scure|Dunkle Tafeln
Quadrotte vissute|Gebrauchte Tafeln
Resina bianca|Weißes Kunstharz
Sampietrini a ventaglio|Pflastersteine im Bogenmuster
Scacchiera bianco e nero|Schachbrett schwarz-weiß
Scacchiera da esterno|Schachbrett für außen
Scacchiera in marmo|Schachbrett aus Marmor
Selciato|Pflaster
Spina di pesce ciliegio|Fischgrät Kirschbaum
Spina di pesce classica|Fischgrät klassisch
Spina di pesce naturale|Fischgrät natur
Spina di pesce rovere|Fischgrät Eiche
Tartan|Schottenkaro
Tavolato bianco|Weiße Bretterwand
Tavolato rustico|Rustikale Bretterwand
Tavolato sbiancato|Gekalkte Bretterwand
Terrazzo chiaro|Heller Terrazzo
Terrazzo colorato|Bunter Terrazzo
Terrazzo grigio|Grauer Terrazzo
Terrazzo multicolore|Mehrfarbiger Terrazzo
Terrazzo nero|Schwarzer Terrazzo
Terrazzo veneziano|Venezianer Terrazzo
Tessuto a motivi|Stoff gemustert
Tessuto a motivi etnici|Stoff mit Ethno-Muster
Travertino|Travertin
Travertino azzurro|Travertin blau
Travertino classico|Travertin klassisch
Travertino grigio|Travertin grau
Travertino noce|Travertin nussbraun
Velluto azzurro|Samt hellblau
Velluto blu|Samt blau
Velluto salvia|Samt salbei
Vimini intrecciato|Korbgeflecht
Wengè|Wenge
Moquette grigia a quadretti|Teppichboden grau mit kleinen Karos
Moquette petrolio a riquadri|Teppichboden petrol mit Feldern
Moquette sabbia a riquadri|Teppichboden sand mit Feldern
Cemento liscio|Glatter Beton
Cemento a vista con fori|Sichtbeton mit Ankerlöchern
Cemento a riquadri|Beton mit Feldern
Microcemento grigio|Mikrozement grau
Microcemento perla|Mikrozement perlgrau
Microcemento antracite|Mikrozement anthrazit
Resina grigia|Graues Kunstharz
Cemento a casseri|Sichtbeton mit Brettschalung
Resina verde muschio|Moosgrünes Kunstharz
Resina sabbia|Sandfarbenes Kunstharz
Pannelli in cemento a righe|Gerippte Betonplatten
Sughero fine|Kork fein
Sughero a granuli|Kork gekörnt
Granito rosa|Rosa Granit
Granito bianco sardo|Weißer sardischer Granit
Granito grigio rosato|Rötlich grauer Granit
Granito bruno|Brauner Granit
Granito lilla|Lila Granit
Granito rosso|Roter Granit
Granito giallo|Gelber Granit
Marmo bianco perla|Perlweißer Marmor
Marmo nero assoluto|Marmor Nero Assoluto
Marmo avorio|Marmor elfenbein
Marmo rosa|Rosa Marmor
Marmo Emperador scuro|Marmor Emperador dunkel
Granito blu Labrador|Blauer Labrador-Granit
Marmo crema Marfil|Marmor Crema Marfil
Marmo nero Portoro|Marmor Nero Portoro
Marmo Botticino|Botticino-Marmor
Marmo rosso Alicante|Marmor Rojo Alicante
Marmo bianco Thassos|Weißer Thassos-Marmor
Pietra di Trani|Trani-Stein
Onice grigio|Grauer Onyx
Onice arancio|Oranger Onyx
Onice avorio|Onyx elfenbein
Onice multicolore|Mehrfarbiger Onyx
Onice nero venato|Schwarzer geäderter Onyx
Intonaco rosso|Roter Putz
Intonaco blu|Blauer Putz
Intonaco verde|Grüner Putz
Intonaco giallo|Gelber Putz
Intonaco verde salvia|Salbeigrüner Putz
Intonaco grigio chiaro|Hellgrauer Putz
Stucco a spatola|Spachtelputz
Stucco veneziano grigio|Stucco Veneziano grau
Graniglia bianca e blu|Terrazzo weiß und blau
Terrazzo antracite|Terrazzo anthrazit
Terrazzo bianco e grigio|Terrazzo weiß und grau
Terrazzo avorio|Terrazzo elfenbein
Terrazzo azzurro multicolore|Terrazzo hellblau mehrfarbig
Terrazzo sabbia|Terrazzo sand
Terrazzo grigio multicolore|Terrazzo grau mehrfarbig
Terrazzo verde|Terrazzo grün
Graniglia fine bianca|Feiner weißer Terrazzo
Terrazzo beige|Terrazzo beige
Piastrelle tortora 30×30|Fliesen taupe 30×30
Gres bianco opaco|Feinsteinzeug weiß matt
Mosaico di vetro azzurro|Glasmosaik hellblau
Mosaico a bottoni pastello|Knopfmosaik pastell
Mattoncini rosa|Metrofliesen rosa
Mattoncini in marmo avorio|Marmor-Riemchen elfenbein
Scacchiera marmo beige|Schachbrett aus beigem Marmor
Scacchiera marmo bianco e nero grande|Großes Schachbrett aus Marmor schwarz-weiß
Scacchiera marmo grigio scuro|Schachbrett aus dunkelgrauem Marmor
Mosaico di vetro blu|Glasmosaik blau
Piastrelle a griglia verde scuro|Gitterfliesen dunkelgrün
Mosaico verde petrolio|Mosaik petrolgrün
Mosaico verde salvia|Mosaik salbeigrün
Cotto arancio 20×20|Cotto orange 20×20
Gres pesca grande formato|Feinsteinzeug pfirsich, Großformat
Mattoncini petrolio lucidi|Metrofliesen petrol glänzend
Mattoncini blu polvere|Metrofliesen taubenblau
Mattoncini verde smeraldo|Metrofliesen smaragdgrün
Mattoncini gialli|Metrofliesen gelb
Zellige verde|Zellige grün
Piastrelle bianche 10×10 lucide|Fliesen weiß 10×10 glänzend
Mattoncini rossi lucidi|Metrofliesen rot glänzend
Lastre di cemento|Platten aus Zement
Graniglia arancio|Terrazzo-Fliesen orange
Graniglia verde|Terrazzo-Fliesen grün
Graniglia bianca|Terrazzo-Fliesen weiß
Graniglia blu|Terrazzo-Fliesen blau
Marmo azzurro a correre|Hellblauer Marmor im wilden Verband
Pietra beige a formati misti|Beiger Stein in gemischten Formaten
Pietra grigia a formati misti|Grauer Stein in gemischten Formaten
Mosaico di ardesia|Schiefer-Mosaik
Mosaico verde marmorizzato|Mosaik grün marmoriert
Marmo bianco con tozzetti neri|Weißer Marmor mit schwarzen Einlegern
Marmo nero e bianco sfalsato|Marmor schwarz-weiß versetzt
Marmo nero con tozzetti bianchi|Schwarzer Marmor mit weißen Einlegern
Losanghe verde, nero e grigio|Rauten grün, schwarz und grau
Esagonette cotto e beige a righe|Sechseck-Fliesen Cotto und beige gestreift
Esagonette cotto e beige|Sechseck-Fliesen Cotto und beige
Esagonette in cotto|Sechseck-Fliesen aus Cotto
Esagonette azzurre e bianche|Sechseck-Fliesen hellblau und weiß
Esagonette celesti e petrolio|Sechseck-Fliesen himmelblau und petrol
Esagonette in pietra nera|Sechseck-Fliesen aus schwarzem Stein
Piastrelle prugna 10×10|Fliesen pflaume 10×10
Marmo a onde verde e crema|Marmor mit Wellen grün und creme
Marmo a rombi verde e crema|Marmor mit Rauten grün und creme
Marmo a zig-zag verde e crema|Marmor im Zickzack grün und creme
Lastricato in pietra vecchia|Altes Steinpflaster
Pietra lavica a opus|Lavastein polygonal
Cotto antico a quadri|Antike quadratische Cotto-Fliesen
Cotto a correre|Cotto im wilden Verband
Azulejos blu fitti|Dichte blaue Azulejos
Maioliche blu intense|Majolika tiefblau
Maioliche a decori blu|Majolika mit blauem Dekor
Maioliche a stelle blu|Majolika mit blauen Sternen
Maioliche bianche con angoli blu|Weiße Majolika mit blauen Ecken
Piastrelle nere lucide|Glänzende schwarze Fliesen
Pietra graniglia beige|Beiger Terrazzostein
Cotto in diagonale con fascia|Cotto diagonal mit Bordüre
Bianco in diagonale con fascia blu|Weiß diagonal mit blauer Bordüre
Giallo in diagonale con fascia|Gelb diagonal mit Bordüre
Nero in diagonale con fascia bianca|Schwarz diagonal mit weißer Bordüre
Lastre di pietra a spacco|Spaltplatten aus Stein
Mosaico a bottoni azzurro|Knopfmosaik hellblau
Mosaico a bottoni bianco e verde|Knopfmosaik weiß und grün
Mosaico a bottoni tortora|Knopfmosaik taupe
Mosaico a bottoni bianco e blu|Knopfmosaik weiß und blau
Mosaico a bottoni ocra|Knopfmosaik ocker
Mosaico a bottoni grigio|Knopfmosaik grau
Mosaico a bottoni rosa e bordeaux|Knopfmosaik rosa und bordeaux
Mosaico a bottoni bianco|Knopfmosaik weiß
Mosaico a bottoni nero|Knopfmosaik schwarz
Piastrelle bianche 10×10 opache|Fliesen weiß 10×10 matt
Piastrelle blu notte|Fliesen nachtblau
Mattoncini arancio|Metrofliesen orange
Travertino venato|Travertin geädert
Travertino tortora|Travertin taupe
Travertino oro|Travertin gold
Travertino silver|Travertin silber
Travertino giallo|Travertin gelb
Quarzite grigia|Grauer Quarzit
Listoni grigio sbiancato|Dielen grau gekalkt
Listoni mogano|Dielen Mahagoni
Listoni rovere fumé|Dielen Räuchereiche
Listoni frassino|Dielen Esche
Listoni noce scuro|Dielen Nussbaum dunkel
Listoncini a correre|Schmale Stäbe im wilden Verband
Listoni rovere sbiancato|Dielen Eiche gekalkt
Listoni wengè|Dielen Wenge
Spina di pesce faggio|Fischgrät Buche
Spina di pesce mogano|Fischgrät Mahagoni
Spina di pesce noce|Fischgrät Nussbaum
Spina di pesce sbiancata|Fischgrät gekalkt
Spina di pesce scura|Fischgrät dunkel
Parquet a cesto chiaro|Flechtverband hell
Parquet a cesto bicolore|Flechtverband zweifarbig
Parquet a cesto ciliegio|Flechtverband Kirschbaum
Parquet a gradini bicolore|Stufenmuster zweifarbig
Parquet intarsiato chiaro|Helles Intarsien-Parkett
Parquet intarsiato scuro|Dunkles Intarsien-Parkett
Parquet a dama|Schachbrett-Parkett
Parquet a cesto wengè|Flechtverband Wenge
Parquet a cesto acero|Flechtverband Ahorn
Industriale scuro|Industrie-Parkett dunkel
Parquet a cesto sbiancato|Flechtverband gekalkt
Spina di pesce vecchia grigia|Alter Fischgrät grau
Spina di pesce larice|Fischgrät Lärche
Industriale miele|Industrie-Parkett honig
Industriale chiaro|Industrie-Parkett hell
Industriale rovere|Industrie-Parkett Eiche
Parquet Versailles|Versailles-Parkett
Listoni noce lunghi|Lange Dielen Nussbaum
Spina ungherese chiara|Heller Fischgrät im Chevron
Parquet a intarsio con cornice|Intarsien-Parkett mit Fries
Listoncini rovere vissuto|Eichenstäbe gebraucht
Spina di pesce in marmo fiammato|Fischgrät aus geflammtem Marmor
Mattoni a spina di pesce|Ziegel in Fischgrät
Laminato miele|Laminat honig
Laminato pino|Laminat Kiefer
Linoleum nocciola|Linoleum haselnuss
Listelli bianchi verticali|Weiße Stäbchen-Fliesen senkrecht
Cementine con scaglie di marmo|Zementfliesen mit Marmorsplitt
Linoleum a ottagoni|Linoleum mit Achtecken
Cemento verniciato|Lackierter Beton
Cotto con tozzetti neri|Cotto mit schwarzen Einlegern
Parquet a mosaico rustico|Rustikales Mosaikparkett
Assi di pino nodose|Astige Kiefernbretter
Piastre in gomma nera|Schwarze Gummiplatten
Ardesia a opus|Schiefer polygonal
Mosaico di pietra a disegni|Steinmosaik mit Motiven
Cotto rosso|Cotto rot
Stucco bianco|Weißer Stuck
Tavolato rovere|Eichenbretter
Tavolato di pino vissuto|Gebrauchte Kiefernbretter
Assi grezze|Raue Bretter
"""
}
