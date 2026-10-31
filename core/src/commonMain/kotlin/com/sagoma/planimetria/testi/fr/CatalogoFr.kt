package com.sagoma.planimetria.testi.fr

/**
 * Nomi francesi degli arredi e dei materiali del catalogo. Il catalogo (scaricato da R2) ha i nomi in italiano: la chiave è
 * il nome italiano ([com.sagoma.planimetria.model.FurnitureCatalog.Item.label] e [com.sagoma.planimetria.model.MaterialCatalog.Item.label]),
 * così una voce con lo stesso nome ha la stessa traduzione. Un nome che manca resta in italiano.
 * Una riga per voce: "nome italiano|nome francese".
 */
internal object CatalogoFr {
    val arredi: Map<String, String> by lazy { leggi(ARREDI) }
    val materiali: Map<String, String> by lazy { leggi(MATERIALI) }

    private fun leggi(testo: String): Map<String, String> =
        testo.lineSequence().filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }

    private const val ARREDI = """
Acquario|Aquarium
Albero natale|Sapin de Noël
Aloe in vaso|Aloès en pot
Anfora|Amphore
Anfora in terracotta|Amphore en terre cuite
Appendiabiti|Portemanteau
Apple iMac 2004|Apple iMac 2004
Applique|Applique
Applique a gabbia|Applique cage
Applique in ottone|Applique en laiton
Armadietto alto|Armoire haute
Armadio|Armoire
Armadio classico|Armoire classique
Armadio con specchio|Armoire à miroir
Armadio grigio|Armoire grise
Armadio in ciliegio|Armoire en merisier
Armadio in noce|Armoire en noyer
Armadio intagliato|Armoire sculptée
Armadio intagliato scuro|Armoire sculptée foncée
Armadio rosso con vetri|Armoire rouge vitrée
Armadio scuro basso|Armoire basse foncée
Armadio stretto|Armoire étroite
Armadio verde salvia|Armoire vert sauge
Asciugatrice|Sèche-linge
Avvolgitubo da giardino|Dévidoir de jardin
Baule a colonna|Coffre haut
Baule in legno|Coffre en bois
Bersaglio per freccette|Cible de fléchettes
Bidone della spazzatura|Poubelle
Bidè|Bidet
Bollitore|Bouilloire
Bonsai|Bonsaï
Box doccia|Cabine de douche vitrée
Braciere in pietra|Brasero en pierre
Buddha|Bouddha
Busto in marmo|Buste en marbre
Cabina doccia|Cabine de douche
Cactus|Cactus
Cactus in vaso|Cactus en pot
Caminetto|Petite cheminée
Camino|Cheminée
Camino a legna|Cheminée à bois
Camino bianco|Cheminée blanche
Camino classico|Cheminée classique
Camino in legno scuro|Cheminée en bois foncé
Camino in marmo|Cheminée en marbre
Candelabri in ottone|Candélabres en laiton
Candelabro in ferro|Candélabre en fer
Candele decorative|Bougies décoratives
Candeliere|Bougeoir
Candeliere in ottone|Bougeoir en laiton
Cappa|Hotte
Carrello porta attrezzi|Servante à outils
Cassa acustica a colonna|Enceinte colonne
Cassa acustica nera|Enceinte noire
Cassa bluetooth|Enceinte Bluetooth
Cassa hi-fi|Enceinte hi-fi
Cassa su treppiede|Enceinte sur trépied
Cassapanca intagliata|Coffre sculpté
Casse acustiche da terra|Enceintes colonnes
Casse compatte|Enceintes compactes
Casse da scaffale|Enceintes d'étagère
Cassetta portaoggetti|Boîte de rangement
Cassettiera aperta|Commode ouverte
Cassettiera bassa|Commode basse
Cassettiera bianca|Commode blanche
Cassettiera bianca moderna|Commode blanche moderne
Cavalletto con tela|Chevalet avec toile
Cavalletto da pittore|Chevalet de peintre
Cavallo a dondolo|Cheval à bascule
Cestino|Corbeille à papier
Cestino a rete|Corbeille grillagée
Cesto|Panier
Cesto con manico|Panier à anse
Cesto del pane|Corbeille à pain
Cesto in paglia|Panier en paille
Cesto in vimini|Panier en osier
Cesto in vimini grande|Grand panier en osier
Cesto in vimini ovale|Panier en osier ovale
Chaise longue|Chaise longue
Chaise longue gialla|Chaise longue jaune
Colonna a giorno|Colonne ouverte
Colonna libreria|Colonne bibliothèque
Comodino|Table de chevet
Comodino antico|Table de chevet ancienne
Comodino classico|Table de chevet classique
Comodino con cassetti|Table de chevet à tiroirs
Comodino dipinto|Table de chevet peinte
Composizione verde|Composition végétale
Comò bianco|Commode blanche classique
Comò gotico|Commode gothique
Comò orientale|Commode orientale
Comò scuro|Commode foncée
Congelatore a pozzetto|Congélateur coffre
Console di gioco|Console de jeux
Consolle classica|Console classique
Consolle orientale|Console orientale
Cornice decorata|Cadre décoré
Cornice dorata|Cadre doré
Cornice ovale|Cadre ovale
Cornice scura|Cadre foncé
Credenza bianca|Buffet blanc
Credenza bianca aperta|Buffet blanc ouvert
Credenza con alzata|Buffet avec vaisselier
Credenza con piano in marmo|Buffet dessus marbre
Credenza con vetrina|Buffet vitré
Credenza dipinta|Buffet peint
Credenza grigia classica|Buffet gris classique
Credenza in legno chiaro|Buffet en bois clair
Credenza rustica|Buffet rustique
Credenza vittoriana|Buffet victorien
Cucina a gas bianca|Cuisinière à gaz blanche
Cucina a quattro fuochi|Cuisinière quatre feux
Cucina bianca|Cuisinière blanche
Cucina componibile|Cuisine modulable
Cucina con doppio forno|Cuisinière double four
Cucina con forno|Cuisinière avec four
Cucina elettrica|Cuisinière électrique
Cucina professionale|Piano de cuisson professionnel
Cucina retrò azzurra|Cuisinière rétro bleu clair
Culla|Berceau
Culla a casetta|Berceau cabane
Culla con giostrina|Berceau avec mobile
Culla in legno|Berceau en bois
Cuscini|Coussins
Cuscini decorativi|Coussins décoratifs
Cuscino avorio|Coussin ivoire
Cuscino bianco|Coussin blanc
Cuscino blu|Coussin bleu
Cuscino lungo|Coussin long
Cuscino nero|Coussin noir
Cuscino per schienale|Coussin de dossier
Cuscino rosa antico|Coussin vieux rose
Dispenser acqua|Fontaine à eau
Divanetto Chesterfield bianco|Canapé Chesterfield blanc deux places
Divanetto arancio|Petit canapé orange
Divanetto bianco|Petit canapé blanc
Divanetto grigio|Petit canapé gris
Divanetto in pelle scura|Petit canapé en cuir foncé
Divanetto rosso|Petit canapé rouge
Divanetto rosso moderno|Petit canapé rouge moderne
Divano|Canapé
Divano 2 posti|Canapé 2 places
Divano 3 posti|Canapé 3 places
Divano Chesterfield|Canapé Chesterfield
Divano Chesterfield grigio|Canapé Chesterfield gris
Divano Winchester|Canapé Winchester
Divano ad angolo|Canapé d'angle
Divano angolare antracite|Canapé d'angle anthracite
Divano angolare blu|Canapé d'angle bleu
Divano angolare fucsia|Canapé d'angle fuchsia
Divano angolare grigio|Canapé d'angle gris
Divano angolare grigio chiaro|Canapé d'angle gris clair
Divano angolare rosa|Canapé d'angle rose
Divano antracite|Canapé anthracite
Divano antracite a tre posti|Canapé anthracite trois places
Divano azzurro|Canapé bleu clair
Divano azzurro polvere|Canapé bleu poudré
Divano bianco classico|Canapé blanc classique
Divano chiaro|Canapé clair
Divano classico bianco|Canapé classique blanc
Divano classico chiaro|Canapé classique clair
Divano componibile|Canapé modulable
Divano con braccioli in legno|Canapé à accoudoirs en bois
Divano con penisola grigio|Canapé gris avec méridienne
Divano con plaid|Canapé avec plaid
Divano con pouf|Canapé avec pouf
Divano due posti|Canapé deux places
Divano giallo|Canapé jaune
Divano grigio chiaro|Canapé gris clair
Divano grigio con cuscini|Canapé gris avec coussins
Divano imbottito|Canapé rembourré
Divano in legno|Canapé en bois
Divano in pelle marrone|Canapé en cuir marron
Divano letto|Canapé-lit
Divano letto aperto|Canapé-lit ouvert
Divano letto bianco|Canapé-lit blanc
Divano letto grigio|Canapé-lit gris
Divano letto rosa|Canapé-lit rose
Divano moderno grigio|Canapé moderne gris
Divano modulare bianco|Canapé modulaire blanc
Divano nero con coperta|Canapé noir avec couverture
Divano orientale|Canapé oriental
Divano rosa capitonné|Canapé capitonné rose
Divano scandinavo|Canapé scandinave
Dormeuse|Méridienne
Enciclopedia|Encyclopédie
Estensione ad isola|Extension en îlot
Faro da esterno|Projecteur extérieur
Fasciatoio|Table à langer
Felce di Boston|Fougère de Boston
Ficus|Ficus
Ficus in vaso|Ficus en pot
Finestra con tende rosse|Fenêtre aux rideaux rouges
Fiori in vaso|Fleurs en pot
Fioriera|Jardinière
Fioriera alta|Jardinière haute
Fioriera lunga|Jardinière longue
Fontana decorativa|Fontaine décorative
Fornetto|Mini-four
Forno|Four
Forno bianco|Four blanc
Forno doppio|Double four
Frigorifero|Réfrigérateur
Frigorifero americano|Réfrigérateur américain
Frigorifero antracite|Réfrigérateur anthracite
Frigorifero bianco|Réfrigérateur blanc
Frigorifero bianco classico|Réfrigérateur blanc classique
Frigorifero con freezer|Réfrigérateur-congélateur
Frigorifero doppio|Double réfrigérateur
Frigorifero retrò|Réfrigérateur rétro
Frigorifero side by side|Réfrigérateur side by side
Frigorifero vintage aperto|Réfrigérateur vintage ouvert
Futon|Futon
Gatto in cemento|Chat en béton
Giradischi a valigetta|Tourne-disque valise
Grammofono|Gramophone
Griglia da esterno|Barbecue
Guardaroba|Penderie
Il bacio|Le Baiser
Isola con lavello|Îlot avec évier
Isola cucina moderna|Îlot de cuisine moderne
Lampada|Lampe
Lampada a muro|Lampe murale
Lampada a paralume grigio|Lampe à abat-jour gris
Lampada a sospensione|Suspension
Lampada a sospensione lunga|Suspension longue
Lampada classica|Lampe classique
Lampada classica bianca|Lampe classique blanche
Lampada con paralume|Lampe avec abat-jour
Lampada con paralume beige|Lampe à abat-jour beige
Lampada con paralume plissé|Lampe à abat-jour plissé
Lampada da banchiere|Lampe de banquier
Lampada da banchiere verde|Lampe de banquier verte
Lampada da esterno|Lampe d'extérieur
Lampada da lettura|Liseuse
Lampada da scrivania|Lampe de bureau
Lampada da soffitto|Plafonnier
Lampada da tavolo|Lampe de table
Lampada da tavolo industriale|Lampe de table industrielle
Lampada da terra classica|Lampadaire classique
Lampada da terra sottile|Lampadaire fin
Lampada da terra vintage|Lampadaire vintage
Lampada in bronzo|Lampe en bronze
Lampada in porcellana|Lampe en porcelaine
Lampada moderna|Lampe moderne
Lampada sospesa|Lampe suspendue
Lampada verde a fungo|Lampe champignon verte
Lampadario|Lustre
Lampadario a bracci|Lustre à bras
Lampadario a candele in ferro|Lustre à bougies en fer
Lampadario a candele nero|Lustre à bougies noir
Lampadario a lanterna|Lustre lanterne
Lampadario classico|Lustre classique
Lampadario con paralumi|Lustre avec abat-jour
Lampadario con paralumi grigi|Lustre avec abat-jour gris
Lampadario di cristallo|Lustre en cristal
Lampadario di cristallo azzurro|Lustre en cristal bleu clair
Lampadario in ferro|Lustre en fer
Lampadario in legno|Lustre en bois
Lampadario in ottone|Lustre en laiton
Lampadario in ottone sottile|Lustre fin en laiton
Lampadario orientale|Lustre oriental
Lampadario rustico|Lustre rustique
Lampadario viola|Lustre violet
Lampione da giardino|Lampadaire de jardin
Lampione da giardino in legno|Lampadaire de jardin en bois
Lanterna appesa|Lanterne suspendue
Lanterna da giardino|Lanterne de jardin
Lanterna da terra|Lanterne à poser
Lanterna di carta|Lanterne en papier
Lanterna giapponese|Lanterne japonaise
Lanterna in legno|Lanterne en bois
Lanterna in pietra|Lanterne en pierre
Lavabo a colonna|Lavabo sur colonne
Lavabo bianco|Lavabo blanc
Lavabo da appoggio|Vasque à poser
Lavabo ergonomico|Lavabo PMR
Lavabo in pietra|Lavabo en pierre
Lavabo piccolo|Petit lavabo
Lavabo quadrato|Lavabo carré
Lavabo quadrato in acciaio|Lavabo carré en acier
Lavabo sospeso|Lavabo suspendu
Lavagna bianca|Tableau blanc
Lavamani|Lave-mains
Lavanda|Lavande
Lavandino|Évier-lavabo
Lavandino con doppio specchio|Lavabo avec double miroir
Lavandino con specchio|Lavabo avec miroir
Lavastoviglie|Lave-vaisselle
Lavastoviglie piccola|Lave-vaisselle compact
Lavatrice|Lave-linge
Lavatrice a carica dall'alto|Lave-linge top
Lavatrice frontale|Lave-linge frontal
Lavello bianco|Évier blanc
Lavello da incasso|Évier à encastrer
Lavello doppio in acciaio|Évier double en inox
Lavello grigio|Évier gris
Lavello in acciaio|Évier en inox
Lavello nero|Évier noir
Lavello nero a una vasca|Évier noir un bac
Lavello nero compatto|Évier noir compact
Lettino|Petit lit
Lettino prendisole|Bain de soleil
Letto|Lit
Letto a castello bianco|Lits superposés blancs
Letto a castello bianco classico|Lits superposés blancs classiques
Letto a castello con scaletta|Lits superposés avec échelle
Letto a castello curvo|Lits superposés arrondis
Letto a castello in legno|Lits superposés en bois
Letto a castello in metallo|Lits superposés en métal
Letto a castello rosa|Lits superposés roses
Letto a giorno|Lit de repos
Letto bianco a spalliere|Lit blanc à tête et pied de lit
Letto con cassetti|Lit à tiroirs
Letto con testiera nera|Lit à tête de lit noire
Letto gotico|Lit gothique
Letto imbottito rosa|Lit rembourré rose
Letto in ferro|Lit en fer
Letto in ferro battuto|Lit en fer forgé
Letto in ferro blu|Lit en fer bleu
Letto in legno|Lit en bois
Letto in legno chiaro|Lit en bois clair
Letto in legno scuro|Lit en bois foncé
Letto in metallo con coperta|Lit en métal avec couverture
Letto in ottone|Lit en laiton
Letto matrimoniale moderno|Lit double moderne
Letto moderno bianco|Lit moderne blanc
Letto per bambini|Lit enfant
Letto singolo|Lit simple
Letto singolo in ferro|Lit simple en fer
Letto singolo in legno|Lit simple en bois
Letto soppalcato|Lit mezzanine
Libreria|Bibliothèque
Libreria a vetri|Bibliothèque vitrée
Libreria antica|Bibliothèque ancienne
Libreria azzurra|Bibliothèque bleu clair
Libreria con libri|Bibliothèque avec livres
Libreria credenza|Bibliothèque buffet
Libreria grande|Grande bibliothèque
Libreria grigia|Bibliothèque grise
Libreria in legno|Bibliothèque en bois
Libreria rustica|Bibliothèque rustique
Libreria stretta|Bibliothèque étroite
Lume a petrolio|Lampe à pétrole
Macchina da caffé|Machine à café
Macchina del caffè|Cafetière
Macchina del pane|Machine à pain
Madia moderna|Bahut moderne
Madia scura|Bahut foncé
Maniglione pieghevole|Barre d'appui rabattable
Mappamondo antico|Globe ancien
Mappamondo su piedistallo|Globe sur pied
Materasso da pavimento|Matelas au sol
Microonde|Micro-ondes
Microonde bianco|Micro-ondes blanc
Microonde blu|Micro-ondes bleu
Microonde classico|Micro-ondes classique
Microonde grigio|Micro-ondes gris
Microonde in acciaio|Micro-ondes en inox
Microonde nero|Micro-ondes noir
Miscelatore per doccia|Mitigeur de douche
Miscelatore per vasca|Mitigeur de baignoire
Miscelatore per vasca e doccia|Mitigeur bain-douche
Mobile TV|Meuble TV
Mobile TV anni '50|Meuble TV années 50
Mobile TV vintage|Meuble TV vintage
Mobile a cassetti|Meuble à tiroirs
Mobile ad angolo inferiore|Meuble bas d'angle
Mobile ad angolo superiore|Meuble haut d'angle
Mobile ad angolo superiore piccolo|Petit meuble haut d'angle
Mobile alto|Colonne
Mobile alto in legno|Colonne en bois
Mobile bagno moderno|Meuble de salle de bain moderne
Mobile con cassetti|Meuble avec tiroirs
Mobile con mensole|Meuble à étagères
Mobile con vetri|Meuble vitré
Mobile inferiore|Meuble bas
Mobile lavabo|Meuble vasque
Mobile lavandino|Meuble sous-lavabo
Mobile lavello|Meuble sous-évier
Mobile orientale|Meuble oriental
Mobile soggiorno|Meuble de séjour
Mobile superiore|Meuble haut
Mobile vetrina chiaro|Vitrine claire
Mobile vetrina scuro|Vitrine foncée
Mobiletto blu|Petit meuble bleu
Mobiletto gotico|Petit meuble gothique
Mobiletto in legno|Petit meuble en bois
Monstera in vaso|Monstera en pot
Ombrellone bianco|Parasol blanc
Ombrellone nero|Parasol noir
Ombrellone rosa|Parasol rose
Orinale|Urinoir
Orologio a cucù|Coucou
Orologio a numeri romani|Horloge à chiffres romains
Orologio a raggi|Horloge soleil
Orologio da mensola|Pendulette de cheminée
Orologio da parete|Horloge murale
Orologio da parete a telefono|Horloge murale téléphone
Orologio da parete classico|Horloge murale classique
Orologio da parete grigio|Horloge murale grise
Orologio da parete tondo|Horloge murale ronde
Pagoda in pietra|Pagode en pierre
Palme in vaso|Palmiers en pot
Panca capitonné|Banc capitonné
Panca con schienale|Banc avec dossier
Panca in legno|Banc en bois
Panca in legno massello|Banc en bois massif
Panchetta imbottita|Repose-pieds rembourré
Panchina classica|Banc classique
Panchina da giardino|Banc de jardin
Panchina da parco|Banc public
Panchina in ferro|Banc en fer
Panchina moderna|Banc moderne
Panchina verde|Banc vert
Paravento|Paravent
Pattumiera in acciaio|Poubelle à pédale en inox
Pendolo|Horloge comtoise
Pendolo antico|Horloge comtoise ancienne
Pendolo classico|Horloge comtoise classique
Pendolo in mogano|Horloge comtoise en acajou
Pendolo moderno|Horloge comtoise moderne
Piano cottura|Plaque de cuisson
Piano cottura a gas a due fuochi|Plaque gaz deux feux
Piano cottura a induzione|Plaque à induction
Pianoforte|Piano
Pianoforte a coda|Piano à queue
Pianoforte verticale|Piano droit
Pianoforte verticale classico|Piano droit classique
Pianoforte verticale nero|Piano droit noir
Pianoforte verticale scuro|Piano droit foncé
Pianta|Plante
Pianta grande|Grande plante
Pianta grassa|Plante grasse
Pianta grassa in vaso viola|Plante grasse en pot violet
Pianta in vaso|Plante en pot
Pianta piccola|Petite plante
Pianta tropicale|Plante tropicale
Piccola lampada|Petite lampe
PlayStation|PlayStation
Poltrona|Fauteuil
Poltrona Winchester|Fauteuil Winchester
Poltrona a dondolo in legno|Fauteuil à bascule en bois
Poltrona a dondolo moderna|Fauteuil à bascule moderne
Poltrona antica|Fauteuil ancien
Poltrona beige|Fauteuil beige
Poltrona bergère bianca|Bergère blanche
Poltrona bergère verde|Bergère verte
Poltrona blu design|Fauteuil design bleu
Poltrona capitonné|Fauteuil capitonné
Poltrona capitonné bordeaux|Fauteuil capitonné bordeaux
Poltrona classica|Fauteuil classique
Poltrona classica grigia|Fauteuil classique gris
Poltrona con cuscino|Fauteuil avec coussin
Poltrona da doccia|Fauteuil de douche
Poltrona da relax capitonné|Fauteuil relax capitonné
Poltrona di design|Fauteuil design
Poltrona girevole|Fauteuil pivotant
Poltrona grigia|Fauteuil gris
Poltrona in pelle beige|Fauteuil en cuir beige
Poltrona in pelle cuoio|Fauteuil en cuir naturel
Poltrona moderna|Fauteuil moderne
Poltrona orientale|Fauteuil oriental
Poltrona reclinabile|Fauteuil inclinable
Poltrona rosa|Fauteuil rose
Poltrona rosa cipria|Fauteuil rose poudré
Poltrona rossa|Fauteuil rouge
Poltrona rossa moderna|Fauteuil rouge moderne
Poltrona tortora|Fauteuil taupe
Poltrona viola|Fauteuil violet
Poltroncina|Petit fauteuil
Poltroncina bianca|Petit fauteuil blanc
Poltroncina koala|Petit fauteuil koala
Porta asciugamano|Porte-serviette
Porta doccia|Porte de douche
Portafoto|Cadre photo
Portafoto grande|Grand cadre photo
Portafrutta|Coupe à fruits
Portarotolo|Dérouleur papier toilette
Portasciugamani|Porte-serviettes
Portasciugamani a barra|Barre porte-serviettes
Portasciugamani a muro|Porte-serviettes mural
Portatile|Ordinateur portable
Pothos in vaso|Pothos en pot
Pouf|Pouf
Pouf a sacco marrone|Pouf poire marron
Pouf a sacco rosso|Pouf poire rouge
Pouf a sacco viola|Pouf poire violet
Pouf antracite|Pouf anthracite
Pouf bianco|Pouf blanc
Pouf blu capitonné|Pouf capitonné bleu
Pouf capitonné|Pouf capitonné
Pouf capitonné grigio|Pouf capitonné gris
Pouf grigio|Pouf gris
Pouf in pelle nera|Pouf en cuir noir
Pouf nero|Pouf noir
Pouf rotondo|Pouf rond
Pouf tondo bordeaux|Pouf rond bordeaux
Puff|Repose-pieds
Quadro|Tableau
Quadro grande|Grand tableau
Quadro grigio|Tableau gris
Quadro in legno|Tableau en bois
Quadro rosa|Tableau rose
Radio giradischi vintage|Radio tourne-disque vintage
Ripiani superiori|Étagères hautes
Rose|Roses
Salottino da esterno|Salon de jardin
Scaffale con cassetti|Étagère à tiroirs
Scaffale in metallo|Étagère en métal
Scaffale metallico|Étagère métallique
Scaffale metallico alto|Étagère métallique haute
Scaffale metallico stretto|Étagère métallique étroite
Scaffale verticale|Étagère verticale
Scaffaletto dipinto|Petite étagère peinte
Scaffali|Étagères
Schedario bianco|Classeur à tiroirs blanc
Schedario in metallo|Classeur à tiroirs en métal
Scrivania|Bureau
Scrivania antica|Bureau ancien
Scrivania bianca|Bureau blanc
Scrivania classica|Bureau classique
Scrivania con computer|Bureau avec ordinateur
Scrivania con computer grigia|Bureau gris avec ordinateur
Scrivania in legno|Bureau en bois
Scrivania in metallo|Bureau en métal
Scrivania in mogano|Bureau en acajou
Scrivania in noce|Bureau en noyer
Scrivania nera|Bureau noir
Scrivania regolabile|Bureau réglable
Scrivania semplice|Bureau simple
Scrivania ufficio a L|Bureau d'angle en L
Scultura del pensatore|Sculpture du Penseur
Scultura in bronzo|Sculpture en bronze
Sedia|Chaise
Sedia Osaka|Chaise Osaka
Sedia Savonarola|Chaise Savonarole
Sedia Tolix|Chaise Tolix
Sedia Windsor nera|Chaise Windsor noire
Sedia a dondolo|Chaise à bascule
Sedia a dondolo bianca|Chaise à bascule blanche
Sedia a dondolo classica|Chaise à bascule classique
Sedia a dondolo gialla|Chaise à bascule jaune
Sedia a dondolo grigia|Chaise à bascule grise
Sedia a dondolo imbottita|Chaise à bascule rembourrée
Sedia a dondolo in cuoio|Chaise à bascule en cuir
Sedia a dondolo in legno|Chaise à bascule en bois
Sedia a dondolo in legno scuro|Chaise à bascule en bois foncé
Sedia a dondolo rosa|Chaise à bascule rose
Sedia a dondolo scura|Chaise à bascule foncée
Sedia alta scura|Chaise haute foncée
Sedia anni '50|Chaise années 50
Sedia bianca|Chaise blanche
Sedia bianca classica|Chaise blanche classique
Sedia bianca e grigia|Chaise blanche et grise
Sedia bistrot|Chaise bistrot
Sedia classica|Chaise classique
Sedia con braccioli|Chaise à accoudoirs
Sedia con cuscino|Chaise avec coussin
Sedia da bar|Chaise de bar
Sedia da doccia|Chaise de douche
Sedia da gaming|Chaise gaming
Sedia da giardino|Chaise de jardin
Sedia da pranzo|Chaise de salle à manger
Sedia da ufficio|Chaise de bureau
Sedia dipinta|Chaise peinte
Sedia dipinta azzurra|Chaise peinte bleu clair
Sedia girevole|Chaise pivotante
Sedia gotica|Chaise gothique
Sedia in Plastica|Chaise en plastique
Sedia in faggio|Chaise en hêtre
Sedia in metallo|Chaise en métal
Sedia intagliata|Chaise sculptée
Sedia per bambini|Chaise enfant
Sedia quercia|Chaise en chêne
Sedia rosa|Chaise rose
Sedia rustica|Chaise rustique
Sedia ryokan|Chaise ryokan
Sedia scandinava|Chaise scandinave
Sedile ribaltabile a muro|Siège rabattable mural
Seggiolone|Chaise haute bébé
Servizio da tè|Service à thé
Set da giardino in metallo|Salon de jardin en métal
Set da patio con ombrellone|Salon de jardin avec parasol
Set da pranzo bianco|Ensemble salle à manger blanc
Set da pranzo grigio|Ensemble salle à manger gris
Set da pranzo in ferro|Ensemble salle à manger en fer
Set da pranzo rustico|Ensemble salle à manger rustique
Set da pranzo scuro|Ensemble salle à manger foncé
Settimanale|Semainier
Sfera armillare|Sphère armillaire
Sgabello|Tabouret
Sgabello alto in legno|Tabouret haut en bois
Sgabello basso|Tabouret bas
Sgabello bianco|Tabouret blanc
Sgabello da bar|Tabouret de bar
Sgabello da bar in metallo|Tabouret de bar en métal
Sgabello da officina|Tabouret d'atelier
Sgabello da pianoforte|Tabouret de piano
Sgabello dipinto|Tabouret peint
Sgabello girevole|Tabouret pivotant
Sgabello in legno|Tabouret en bois
Sgabello in legno chiaro|Tabouret en bois clair
Sgabello in metallo|Tabouret en métal
Sgabello industriale|Tabouret industriel
Sgabello orientale|Tabouret oriental
Sgabello per pianoforte|Banquette de piano
Sgabello pieghevole|Tabouret pliant
Sgabello rustico|Tabouret rustique
Soffione doccia e rubinetto|Pomme de douche et robinet
Sospensione industriale|Suspension industrielle
Specchio ad arco|Miroir en arche
Specchio barocco|Miroir baroque
Specchio capitonné|Miroir capitonné
Specchio con cornice scura|Miroir à cadre foncé
Specchio da terra|Miroir sur pied
Specchio decorato|Miroir décoré
Specchio dorato|Miroir doré
Specchio in stile impero|Miroir style Empire
Specchio ovale|Miroir ovale
Stampante e scanner|Imprimante et scanner
Statua classica|Statue classique
Statua di cavallo|Statue de cheval
Statua di donna|Statue de femme
Statua egizia|Statue égyptienne
Statua in terracotta|Statue en terre cuite
Stereo portatile|Radio-cassette portable
Stufa|Poêle
Stufa a legna|Poêle à bois
Sveglia|Réveil
Sveglia vintage|Réveil vintage
TV|TV
TV 16:9|TV 16:9
TV a muro|TV murale
Tapis roulant|Tapis de course
Tapis roulant pieghevole|Tapis de course pliant
Tappetino azzurro|Petit tapis bleu clair
Tappetino da bagno bianco|Tapis de bain blanc
Tappetino grigio|Petit tapis gris
Tappetino in spugna|Tapis éponge
Tappetino rosa|Petit tapis rose
Tappeto grigio|Tapis gris
Tappeto orientale arancio|Tapis oriental orange
Tappeto persiano rosso|Tapis persan rouge
Tappeto rotondo|Tapis rond
Tavolino|Guéridon
Tavolino a colonna|Guéridon sur colonne
Tavolino alto|Guéridon haut
Tavolino alto in legno|Guéridon haut en bois
Tavolino basso|Table basse
Tavolino bistrot|Table bistrot
Tavolino con brocca|Guéridon avec carafe
Tavolino con intreccio|Guéridon tressé
Tavolino con tovaglia|Guéridon avec nappe
Tavolino da tè|Table à thé
Tavolino gotico|Guéridon gothique
Tavolino in legno|Guéridon en bois
Tavolino industriale|Guéridon industriel
Tavolino intagliato|Guéridon sculpté
Tavolino laterale|Bout de canapé
Tavolino moderno|Guéridon moderne
Tavolino ovale|Guéridon ovale
Tavolino per bambini|Table enfant
Tavolino rotondo|Guéridon rond
Tavolino rotondo in legno|Guéridon rond en bois
Tavolino tondo design|Guéridon rond design
Tavolino tondo in legno|Petite table ronde en bois
Tavolino tondo in noce|Guéridon rond en noyer
Tavolino tondo scandinavo|Guéridon rond scandinave
Tavolino verde intagliato|Guéridon vert sculpté
Tavolo|Table
Tavolo bianco|Table blanche
Tavolo bianco classico|Table blanche classique
Tavolo bianco da cucina|Table de cuisine blanche
Tavolo bianco moderno|Table blanche moderne
Tavolo con panche|Table avec bancs
Tavolo con tovaglia|Table avec nappe
Tavolo con tovaglia blu|Table avec nappe bleue
Tavolo da bar|Table de bar
Tavolo da caffè|Table de café
Tavolo da picnic|Table de pique-nique
Tavolo design|Table design
Tavolo dipinto|Table peinte
Tavolo e sedie da giardino|Table et chaises de jardin
Tavolo grande con sedie|Grande table avec chaises
Tavolo grigio|Table grise
Tavolo grigio scuro|Table gris foncé
Tavolo in legno|Table en bois
Tavolo in legno chiaro|Table en bois clair
Tavolo in legno di Tek|Table en teck
Tavolo in legno di ontano|Table en aulne
Tavolo in legno massello|Table en bois massif
Tavolo in legno rosso|Table en bois rouge
Tavolo in vetro|Table en verre
Tavolo lungo|Table longue
Tavolo nero|Table noire
Tavolo quadrato|Table carrée
Tavolo quercia|Table en chêne
Tavolo rotondo|Table ronde
Tavolo rotondo grande|Grande table ronde
Tavolo scuro|Table foncée
Tavolo semplice|Table simple
Tavolo su cavalletti|Table sur tréteaux
Tavolo tecnico|Table à dessin
Tavolo tondo|Table ronde de salle à manger
Tavolo tulipano|Table tulipe
Tecnigrafo|Table à dessin avec règle
Televisore a schermo piatto|Téléviseur à écran plat
Televisore anni '60|Téléviseur années 60
Televisore anni '80|Téléviseur années 80
Televisore in legno|Téléviseur en bois
Televisore portatile|Téléviseur portable
Televisore portatile vintage|Téléviseur portable vintage
Televisore retrò|Téléviseur rétro
Televisore su gambe|Téléviseur sur pieds
Televisore vintage|Téléviseur vintage
Telo per proiettore|Écran de projection
Tenda a pannello|Panneau japonais
Tenda su bastone|Rideau sur tringle
Tende bianche|Rideaux blancs
Tende bordeaux|Rideaux bordeaux
Tende con mantovana|Rideaux avec cantonnière
Tende di lino|Rideaux en lin
Termoarredo|Radiateur design
Termosifone a colonne|Radiateur à colonnes
Termosifone a colonne bianco|Radiateur à colonnes blanc
Termosifone a pannello|Radiateur panneau
Termosifone bianco|Radiateur blanc
Termosifone decorativo|Radiateur décoratif
Termosifone in ghisa|Radiateur en fonte
Termosifone piatto|Radiateur plat
Toilette giapponese|Toilettes japonaises
Toletta|Coiffeuse
Tostapane|Grille-pain
Turca|Toilettes à la turque
Unità esterne del condizionatore|Unités extérieures de climatisation
Urinale|Urinoir mural
Valigie vintage|Valises vintage
Vasca ad angolo|Baignoire d'angle
Vasca ad isola|Baignoire îlot
Vasca con doccia|Baignoire avec douche
Vasca con piedini|Baignoire sur pieds
Vasca con piedini bianca|Baignoire sur pieds blanche
Vasca con rubinetto|Baignoire avec robinet
Vasca freestanding|Baignoire îlot autoportante
Vasca in ottone|Baignoire en laiton
Vasca ovale|Baignoire ovale
Vasca rettangolare|Baignoire rectangulaire
Vasca vittoriana|Baignoire victorienne
Vaso antico|Vase ancien
Vaso bianco|Vase blanc
Vaso bianco e blu|Vase bleu et blanc
Vaso bianco panciuto|Vase blanc pansu
Vaso celadon|Vase céladon
Vaso di terracotta|Pot en terre cuite
Vaso in ceramica|Vase en céramique
Vaso in ceramica alto|Vase haut en céramique
Vaso in ottone|Vase en laiton
Vaso in ottone alto|Vase haut en laiton
Vaso in terracotta|Vase en terre cuite
Vaso lilla|Vase lilas
Vaso rosa antico|Vase vieux rose
Vaso smaltato|Vase émaillé
Vaso verde|Vase vert
Venere di Milo|Vénus de Milo
Ventilatore a soffitto|Ventilateur de plafond
Ventilatore da tavolo|Ventilateur de table
Ventilatore in ottone|Ventilateur en laiton
Ventilatore vintage|Ventilateur vintage
Vetrina bianca|Vitrine blanche
Vetrina con ante|Vitrine à portes
Vetrinetta|Petite vitrine
Vetrinetta a giorno|Petite vitrine ouverte
Vetrinetta antica|Vitrine ancienne
Videoproiettore|Vidéoprojecteur
WC bianco|WC blanc
WC classico|WC classique
WC compatto|WC compact
WC con cassetta|WC avec réservoir
WC moderno|WC moderne
WC nero|WC noir
Water|Toilettes
Zerbino beige|Paillasson beige
Zerbino marrone|Paillasson marron
"""

    private const val MATERIALI = """
Ardesia a lastre|Ardoise en dalles
Assi chiare|Planches claires
Assi chiare nodose|Planches claires noueuses
Assi color miele|Planches couleur miel
Assi da ponte|Planches d'échafaudage
Assi scure|Planches foncées
Assi vissute|Planches patinées
Autobloccanti|Pavés autobloquants
Blocchi grigi|Blocs gris
Carta da parati jacquard|Papier peint jacquard
Carta da parati marmorizzata|Papier peint marbré
Cementine bianco e nero|Carreaux de ciment noir et blanc
Cementine decorate|Carreaux de ciment décorés
Cementine geometriche|Carreaux de ciment géométriques
Cemento a lastre|Dalles de béton
Cemento chiaro|Béton clair
Cemento grigio|Béton gris
Cemento scuro|Béton foncé
Cemento spatolato|Béton ciré
Ciottolato|Galets
Cotto|Tomettes
Cotto chiaro|Tomettes claires
Cotto scuro|Tomettes foncées
Doghe bianche|Lames blanches
Doghe in legno|Lames de bois
Doghe rosse|Lames rouges
Doghe strette scure|Lames étroites foncées
Esagonette bianche|Tomettes hexagonales blanches
Esagonette nere|Tomettes hexagonales noires
Esagoni in cemento|Hexagones en béton
Ghiaia|Gravier
Gres antracite a correre|Grès cérame anthracite en coupe de pierre
Gres ardesia|Grès cérame effet ardoise
Gres beige|Grès cérame beige
Gres bianco 60×60|Grès cérame blanc 60×60
Gres bianco grande formato|Grès cérame blanc grand format
Gres grafite|Grès cérame graphite
Gres nero|Grès cérame noir
Gres pietra chiara|Grès cérame pierre claire
Intonaco bianco|Enduit blanc
Intonaco fine|Enduit fin
Intonaco grigio|Enduit gris
Intonaco liscio|Enduit lisse
Intonaco rustico|Enduit rustique
Juta|Jute
Juta fine|Jute fin
Laminato rovere|Stratifié chêne
Lana a righe|Laine rayée
Lana grigia|Laine grise
Lana grigia melange|Laine gris chiné
Lana intrecciata bianca|Laine tressée blanche
Lana rossa a coste|Laine rouge côtelée
Lastre a intreccio|Dalles tressées
Lastre beige|Dalles beiges
Lastre di marmo rosa|Dalles de marbre rose
Lastre di pietra beige|Dalles de pierre beige
Lastre di pietra chiara|Dalles de pierre claire
Lastre grigie|Dalles grises
Lastre nere lucide|Dalles noires brillantes
Lino verde|Lin vert
Listoni noce|Lames de noyer
Listoni rovere|Lames de chêne
Listoni rovere naturale|Lames de chêne naturel
Listoni teak|Lames de teck
Maioliche blu|Faïences bleues
Marmo Emperador chiaro|Marbre Emperador clair
Marmo bianco venato|Marbre blanc veiné
Marmo blu notte|Marbre bleu nuit
Marmo crema|Marbre crème
Marmo di Carrara|Marbre de Carrare
Marmo grigio|Marbre gris
Marmo grigio a lastre|Dalles de marbre gris
Marmo grigio perla|Marbre gris perle
Marmo nero Marquinia|Marbre Nero Marquina
Marmo nero lucido|Marbre noir brillant
Marmo nero venato|Marbre noir veiné
Marmo oro|Marbre doré
Marmo tortora|Marbre taupe
Marmo verde|Marbre vert
Mattoncini bianchi|Briquettes blanches
Mattoncini verdi|Briquettes vertes
Mattonelle chiare|Carreaux clairs
Mattoni antichi|Briques anciennes
Mattoni arancio|Briques orange
Mattoni arancio lisci|Briques orange lisses
Mattoni bianchi|Briques blanches
Mattoni bruciati|Briques brûlées
Mattoni crema|Briques crème
Mattoni da esterno|Briques d'extérieur
Mattoni faccia a vista|Briques apparentes
Mattoni gialli|Briques jaunes
Mattoni misti|Briques mélangées
Mattoni rossi|Briques rouges
Mattoni rustici|Briques rustiques
Mattoni scuri|Briques foncées
Mattoni vissuti|Briques patinées
Moquette a fiori|Moquette fleurie
Moquette a quadri grigia|Moquette grise à carreaux
Moquette avorio|Moquette ivoire
Moquette beige|Moquette beige
Moquette beige a riquadri|Moquette beige à panneaux
Moquette bianca|Moquette blanche
Moquette blu|Moquette bleue
Moquette blu a quadri|Moquette bleue à carreaux
Moquette rosa melange|Moquette rose chiné
Moquette rossa|Moquette rouge
Moquette verde oliva|Moquette vert olive
Mosaico acquamarina|Mosaïque aigue-marine
Mosaico azzurro|Mosaïque bleu clair
Mosaico grigio|Mosaïque grise
Muro in pietra|Mur en pierre
Muro in pietra grezza|Mur en pierre brute
Onice azzurro|Onyx bleu
Onice bianco|Onyx blanc
Onice miele|Onyx miel
Onice nero|Onyx noir
Onice rosso|Onyx rouge
Onice verde acqua|Onyx vert d'eau
Opus incertum|Opus incertum
Paglia intrecciata|Paille tressée
Parquet a cesto|Parquet en vannerie
Parquet a intreccio chiaro|Parquet tressé clair
Parquet a intreccio scuro|Parquet tressé foncé
Parquet a quadri|Parquet à damier
Parquet a riquadri|Parquet à panneaux
Parquet a riquadri classico|Parquet à panneaux classique
Parquet acero|Parquet érable
Parquet anticato|Parquet vieilli
Parquet ciliegio|Parquet merisier
Parquet in diagonale|Parquet en diagonale
Parquet noce|Parquet noyer
Parquet rovere miele|Parquet chêne miel
Pelliccia geometrica|Fausse fourrure géométrique
Perlinato scuro|Lambris foncé
Piastrelle a scacchi|Carrelage en damier
Piastrelle beige|Carrelage beige
Piastrelle bianche 10×10|Carrelage blanc 10×10
Piastrelle bianche lucide|Carrelage blanc brillant
Piastrelle blu|Carrelage bleu
Piastrelle blu antiche|Carrelage bleu ancien
Piastrelle in diagonale|Carrelage en diagonale
Piastrelle in graniglia|Carreaux de granito
Piastrelle marroni|Carrelage marron
Pietra a opus|Pierre en opus
Pietra a secco|Pierre sèche
Pietra a strati|Pierre en strates
Pietra beige|Pierre beige
Pietra gialla|Pierre jaune
Pietre con erba|Pas japonais dans l'herbe
Porfido|Porphyre
Porfido a ventaglio|Porphyre en éventail
Prato|Pelouse
Prato all'inglese|Gazon anglais
Quadri grigi|Carreaux gris
Quadrotte scure|Dalles carrées foncées
Quadrotte vissute|Dalles carrées patinées
Resina bianca|Résine blanche
Sampietrini a ventaglio|Pavés en éventail
Scacchiera bianco e nero|Damier noir et blanc
Scacchiera da esterno|Damier d'extérieur
Scacchiera in marmo|Damier en marbre
Selciato|Pavage
Spina di pesce ciliegio|Point de Hongrie merisier
Spina di pesce classica|Point de Hongrie classique
Spina di pesce naturale|Point de Hongrie naturel
Spina di pesce rovere|Point de Hongrie chêne
Tartan|Tartan
Tavolato bianco|Plancher blanc
Tavolato rustico|Plancher rustique
Tavolato sbiancato|Plancher blanchi
Terrazzo chiaro|Terrazzo clair
Terrazzo colorato|Terrazzo coloré
Terrazzo grigio|Terrazzo gris
Terrazzo multicolore|Terrazzo multicolore
Terrazzo nero|Terrazzo noir
Terrazzo veneziano|Terrazzo vénitien
Tessuto a motivi|Tissu à motifs
Tessuto a motivi etnici|Tissu à motifs ethniques
Travertino|Travertin
Travertino azzurro|Travertin bleu
Travertino classico|Travertin classique
Travertino grigio|Travertin gris
Travertino noce|Travertin noyer
Velluto azzurro|Velours bleu clair
Velluto blu|Velours bleu
Velluto salvia|Velours sauge
Vimini intrecciato|Osier tressé
Wengè|Wengé
Moquette grigia a quadretti|Moquette grise à petits carrés
Moquette petrolio a riquadri|Moquette bleu pétrole à carrés
Moquette sabbia a riquadri|Moquette sable à carrés
Cemento liscio|Béton lisse
Cemento a vista con fori|Béton apparent avec trous
Cemento a riquadri|Béton à carrés
Microcemento grigio|Béton ciré gris
Microcemento perla|Béton ciré perle
Microcemento antracite|Béton ciré anthracite
Resina grigia|Résine grise
Cemento a casseri|Béton banché
Resina verde muschio|Résine vert mousse
Resina sabbia|Résine sable
Pannelli in cemento a righe|Panneaux de béton rainurés
Sughero fine|Liège fin
Sughero a granuli|Liège aggloméré
Granito rosa|Granit rose
Granito bianco sardo|Granit blanc de Sardaigne
Granito grigio rosato|Granit gris rosé
Granito bruno|Granit brun
Granito lilla|Granit lilas
Granito rosso|Granit rouge
Granito giallo|Granit jaune
Marmo bianco perla|Marbre blanc perle
Marmo nero assoluto|Marbre noir absolu
Marmo avorio|Marbre ivoire
Marmo rosa|Marbre rose
Marmo Emperador scuro|Marbre Emperador foncé
Granito blu Labrador|Granit bleu Labrador
Marmo crema Marfil|Marbre Crema Marfil
Marmo nero Portoro|Marbre noir Portor
Marmo Botticino|Marbre Botticino
Marmo rosso Alicante|Marbre rouge Alicante
Marmo bianco Thassos|Marbre blanc Thassos
Pietra di Trani|Pierre de Trani
Onice grigio|Onyx gris
Onice arancio|Onyx orange
Onice avorio|Onyx ivoire
Onice multicolore|Onyx multicolore
Onice nero venato|Onyx noir veiné
Intonaco rosso|Enduit rouge
Intonaco blu|Enduit bleu
Intonaco verde|Enduit vert
Intonaco giallo|Enduit jaune
Intonaco verde salvia|Enduit vert sauge
Intonaco grigio chiaro|Enduit gris clair
Stucco a spatola|Stuc à la spatule
Stucco veneziano grigio|Stuc vénitien gris
Graniglia bianca e blu|Granito blanc et bleu
Terrazzo antracite|Terrazzo anthracite
Terrazzo bianco e grigio|Terrazzo blanc et gris
Terrazzo avorio|Terrazzo ivoire
Terrazzo azzurro multicolore|Terrazzo bleu clair multicolore
Terrazzo sabbia|Terrazzo sable
Terrazzo grigio multicolore|Terrazzo gris multicolore
Terrazzo verde|Terrazzo vert
Graniglia fine bianca|Granito blanc fin
Terrazzo beige|Granito beige
Piastrelle tortora 30×30|Carrelage taupe 30×30
Gres bianco opaco|Grès blanc mat
Mosaico di vetro azzurro|Mosaïque de verre bleu clair
Mosaico a bottoni pastello|Mosaïque ronde pastel
Mattoncini rosa|Carreaux métro roses
Mattoncini in marmo avorio|Briquettes en marbre ivoire
Scacchiera marmo beige|Damier en marbre beige
Scacchiera marmo bianco e nero grande|Grand damier en marbre noir et blanc
Scacchiera marmo grigio scuro|Damier en marbre gris foncé
Mosaico di vetro blu|Mosaïque de verre bleue
Piastrelle a griglia verde scuro|Carrelage quadrillé vert foncé
Mosaico verde petrolio|Mosaïque vert pétrole
Mosaico verde salvia|Mosaïque vert sauge
Cotto arancio 20×20|Terre cuite orange 20×20
Gres pesca grande formato|Grès pêche grand format
Mattoncini petrolio lucidi|Carreaux métro bleu pétrole brillants
Mattoncini blu polvere|Carreaux métro bleu poudré
Mattoncini verde smeraldo|Carreaux métro vert émeraude
Mattoncini gialli|Carreaux métro jaunes
Zellige verde|Zellige vert
Piastrelle bianche 10×10 lucide|Carrelage blanc brillant 10×10
Mattoncini rossi lucidi|Carreaux métro rouges brillants
Lastre di cemento|Dalles de béton
Graniglia arancio|Granito orange
Graniglia verde|Granito vert
Graniglia bianca|Granito blanc
Graniglia blu|Granito bleu
Marmo azzurro a correre|Marbre bleu clair à joints décalés
Pietra beige a formati misti|Pierre beige formats mixtes
Pietra grigia a formati misti|Pierre grise formats mixtes
Mosaico di ardesia|Mosaïque d'ardoise
Mosaico verde marmorizzato|Mosaïque verte marbrée
Marmo bianco con tozzetti neri|Marbre blanc avec cabochons noirs
Marmo nero e bianco sfalsato|Marbre noir et blanc décalé
Marmo nero con tozzetti bianchi|Marbre noir avec cabochons blancs
Losanghe verde, nero e grigio|Losanges vert, noir et gris
Esagonette cotto e beige a righe|Tomettes terre cuite et beige à rayures
Esagonette cotto e beige|Tomettes terre cuite et beige
Esagonette in cotto|Tomettes en terre cuite
Esagonette azzurre e bianche|Hexagones bleu clair et blancs
Esagonette celesti e petrolio|Hexagones bleu ciel et pétrole
Esagonette in pietra nera|Hexagones en pierre noire
Piastrelle prugna 10×10|Carrelage prune 10×10
Marmo a onde verde e crema|Marbre à vagues vert et crème
Marmo a rombi verde e crema|Marbre à losanges vert et crème
Marmo a zig-zag verde e crema|Marbre en zigzag vert et crème
Lastricato in pietra vecchia|Dallage en vieille pierre
Pietra lavica a opus|Pierre de lave en opus
Cotto antico a quadri|Terre cuite ancienne carrée
Cotto a correre|Terre cuite à joints décalés
Azulejos blu fitti|Azulejos bleus serrés
Maioliche blu intense|Majoliques bleu intense
Maioliche a decori blu|Majoliques à décors bleus
Maioliche a stelle blu|Majoliques à étoiles bleues
Maioliche bianche con angoli blu|Majoliques blanches à coins bleus
Piastrelle nere lucide|Carrelage noir brillant
Pietra graniglia beige|Pierre granito beige
Cotto in diagonale con fascia|Terre cuite en diagonale avec frise
Bianco in diagonale con fascia blu|Blanc en diagonale avec frise bleue
Giallo in diagonale con fascia|Jaune en diagonale avec frise
Nero in diagonale con fascia bianca|Noir en diagonale avec frise blanche
Lastre di pietra a spacco|Dalles de pierre clivée
Mosaico a bottoni azzurro|Mosaïque ronde bleu clair
Mosaico a bottoni bianco e verde|Mosaïque ronde blanche et verte
Mosaico a bottoni tortora|Mosaïque ronde taupe
Mosaico a bottoni bianco e blu|Mosaïque ronde blanche et bleue
Mosaico a bottoni ocra|Mosaïque ronde ocre
Mosaico a bottoni grigio|Mosaïque ronde grise
Mosaico a bottoni rosa e bordeaux|Mosaïque ronde rose et bordeaux
Mosaico a bottoni bianco|Mosaïque ronde blanche
Mosaico a bottoni nero|Mosaïque ronde noire
Piastrelle bianche 10×10 opache|Carrelage blanc mat 10×10
Piastrelle blu notte|Carrelage bleu nuit
Mattoncini arancio|Carreaux métro orange
Travertino venato|Travertin veiné
Travertino tortora|Travertin taupe
Travertino oro|Travertin doré
Travertino silver|Travertin silver
Travertino giallo|Travertin jaune
Quarzite grigia|Quartzite grise
Listoni grigio sbiancato|Lames gris blanchi
Listoni mogano|Lames acajou
Listoni rovere fumé|Lames chêne fumé
Listoni frassino|Lames frêne
Listoni noce scuro|Lames noyer foncé
Listoncini a correre|Lamelles à joints décalés
Listoni rovere sbiancato|Lames chêne blanchi
Listoni wengè|Lames wengé
Spina di pesce faggio|Point de Hongrie hêtre
Spina di pesce mogano|Point de Hongrie acajou
Spina di pesce noce|Point de Hongrie noyer
Spina di pesce sbiancata|Point de Hongrie blanchi
Spina di pesce scura|Point de Hongrie foncé
Parquet a cesto chiaro|Parquet en vannerie clair
Parquet a cesto bicolore|Parquet en vannerie bicolore
Parquet a cesto ciliegio|Parquet en vannerie merisier
Parquet a gradini bicolore|Parquet en escalier bicolore
Parquet intarsiato chiaro|Parquet marqueté clair
Parquet intarsiato scuro|Parquet marqueté foncé
Parquet a dama|Parquet en damier
Parquet a cesto wengè|Parquet en vannerie wengé
Parquet a cesto acero|Parquet en vannerie érable
Industriale scuro|Parquet industriel foncé
Parquet a cesto sbiancato|Parquet en vannerie blanchi
Spina di pesce vecchia grigia|Point de Hongrie vieux gris
Spina di pesce larice|Point de Hongrie mélèze
Industriale miele|Parquet industriel miel
Industriale chiaro|Parquet industriel clair
Industriale rovere|Parquet industriel chêne
Parquet Versailles|Parquet de Versailles
Listoni noce lunghi|Longues lames noyer
Spina ungherese chiara|Point de Hongrie à 45° clair
Parquet a intarsio con cornice|Parquet marqueté avec cadre
Listoncini rovere vissuto|Lamelles chêne vieilli
Spina di pesce in marmo fiammato|Chevrons en marbre flammé
Mattoni a spina di pesce|Briques en chevrons
Laminato miele|Stratifié miel
Laminato pino|Stratifié pin
Linoleum nocciola|Linoléum noisette
Listelli bianchi verticali|Baguettes blanches verticales
Cementine con scaglie di marmo|Carreaux de ciment à éclats de marbre
Linoleum a ottagoni|Linoléum à octogones
Cemento verniciato|Béton peint
Cotto con tozzetti neri|Terre cuite avec cabochons noirs
Parquet a mosaico rustico|Parquet mosaïque rustique
Assi di pino nodose|Planches de pin noueux
Piastre in gomma nera|Dalles en caoutchouc noir
Ardesia a opus|Ardoise en opus
Mosaico di pietra a disegni|Mosaïque de pierre à motifs
Cotto rosso|Terre cuite rouge
Stucco bianco|Stuc blanc
Tavolato rovere|Plancher chêne
Tavolato di pino vissuto|Plancher pin vieilli
Assi grezze|Planches brutes
"""
}
