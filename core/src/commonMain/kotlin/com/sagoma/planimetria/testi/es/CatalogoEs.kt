package com.sagoma.planimetria.testi.es

/**
 * Nomi spagnoli degli arredi e dei materiali del catalogo. Il catalogo (scaricato da R2) ha i nomi in italiano: la chiave è
 * il nome italiano ([com.sagoma.planimetria.model.FurnitureCatalog.Item.label] e [com.sagoma.planimetria.model.MaterialCatalog.Item.label]),
 * così una voce con lo stesso nome ha la stessa traduzione. Un nome che manca resta in italiano.
 * Una riga per voce: "nome italiano|nome spagnolo".
 */
internal object CatalogoEs {
    val arredi: Map<String, String> by lazy { leggi(ARREDI) }
    val materiali: Map<String, String> by lazy { leggi(MATERIALI) }

    private fun leggi(testo: String): Map<String, String> =
        testo.lineSequence().filter { '|' in it }.associate { it.substringBefore('|') to it.substringAfter('|') }

    private const val ARREDI = """
Acquario|Acuario
Albero natale|Árbol de Navidad
Aloe in vaso|Aloe en maceta
Anfora|Ánfora
Anfora in terracotta|Ánfora de terracota
Appendiabiti|Perchero
Apple iMac 2004|Apple iMac 2004
Applique|Aplique
Applique a gabbia|Aplique de jaula
Applique in ottone|Aplique de latón
Armadietto alto|Armario alto
Armadio|Armario
Armadio classico|Armario clásico
Armadio con specchio|Armario con espejo
Armadio grigio|Armario gris
Armadio in ciliegio|Armario de cerezo
Armadio in noce|Armario de nogal
Armadio intagliato|Armario tallado
Armadio intagliato scuro|Armario tallado oscuro
Armadio rosso con vetri|Armario rojo con cristales
Armadio scuro basso|Armario bajo oscuro
Armadio stretto|Armario estrecho
Armadio verde salvia|Armario verde salvia
Asciugatrice|Secadora
Avvolgitubo da giardino|Enrollador de manguera
Baule a colonna|Baúl alto
Baule in legno|Baúl de madera
Bersaglio per freccette|Diana de dardos
Bidone della spazzatura|Cubo de basura
Bidè|Bidé
Bollitore|Hervidor
Bonsai|Bonsái
Box doccia|Mampara de ducha
Braciere in pietra|Brasero de piedra
Buddha|Buda
Busto in marmo|Busto de mármol
Cabina doccia|Cabina de ducha
Cactus|Cactus
Cactus in vaso|Cactus en maceta
Caminetto|Chimenea pequeña
Camino|Chimenea
Camino a legna|Chimenea de leña
Camino bianco|Chimenea blanca
Camino classico|Chimenea clásica
Camino in legno scuro|Chimenea de madera oscura
Camino in marmo|Chimenea de mármol
Candelabri in ottone|Candelabros de latón
Candelabro in ferro|Candelabro de hierro
Candele decorative|Velas decorativas
Candeliere|Candelero
Candeliere in ottone|Candelero de latón
Cappa|Campana extractora
Carrello porta attrezzi|Carro de herramientas
Cassa acustica a colonna|Altavoz de columna
Cassa acustica nera|Altavoz negro
Cassa bluetooth|Altavoz Bluetooth
Cassa hi-fi|Altavoz hi-fi
Cassa su treppiede|Altavoz con trípode
Cassapanca intagliata|Arcón tallado
Casse acustiche da terra|Altavoces de suelo
Casse compatte|Altavoces compactos
Casse da scaffale|Altavoces de estantería
Cassetta portaoggetti|Caja organizadora
Cassettiera aperta|Cajonera abierta
Cassettiera bassa|Cajonera baja
Cassettiera bianca|Cajonera blanca
Cassettiera bianca moderna|Cajonera blanca moderna
Cavalletto con tela|Caballete con lienzo
Cavalletto da pittore|Caballete de pintor
Cavallo a dondolo|Caballito balancín
Cestino|Papelera
Cestino a rete|Papelera de rejilla
Cesto|Cesta
Cesto con manico|Cesta con asa
Cesto del pane|Panera
Cesto in paglia|Cesta de paja
Cesto in vimini|Cesta de mimbre
Cesto in vimini grande|Cesta de mimbre grande
Cesto in vimini ovale|Cesta de mimbre ovalada
Chaise longue|Chaise longue
Chaise longue gialla|Chaise longue amarilla
Colonna a giorno|Columna abierta
Colonna libreria|Columna librería
Comodino|Mesita de noche
Comodino antico|Mesita de noche antigua
Comodino classico|Mesita de noche clásica
Comodino con cassetti|Mesita de noche con cajones
Comodino dipinto|Mesita de noche pintada
Composizione verde|Composición verde
Comò bianco|Cómoda blanca
Comò gotico|Cómoda gótica
Comò orientale|Cómoda oriental
Comò scuro|Cómoda oscura
Congelatore a pozzetto|Arcón congelador
Console di gioco|Videoconsola
Consolle classica|Consola clásica
Consolle orientale|Consola oriental
Cornice decorata|Marco decorado
Cornice dorata|Marco dorado
Cornice ovale|Marco ovalado
Cornice scura|Marco oscuro
Credenza bianca|Aparador blanco
Credenza bianca aperta|Aparador blanco abierto
Credenza con alzata|Aparador con vitrina alta
Credenza con piano in marmo|Aparador con encimera de mármol
Credenza con vetrina|Aparador con vitrina
Credenza dipinta|Aparador pintado
Credenza grigia classica|Aparador gris clásico
Credenza in legno chiaro|Aparador de madera clara
Credenza rustica|Aparador rústico
Credenza vittoriana|Aparador victoriano
Cucina a gas bianca|Cocina de gas blanca
Cucina a quattro fuochi|Cocina de cuatro fuegos
Cucina bianca|Cocina blanca
Cucina componibile|Cocina modular
Cucina con doppio forno|Cocina con doble horno
Cucina con forno|Cocina con horno
Cucina elettrica|Cocina eléctrica
Cucina professionale|Cocina profesional
Cucina retrò azzurra|Cocina retro azul claro
Culla|Cuna
Culla a casetta|Cuna en forma de casita
Culla con giostrina|Cuna con móvil
Culla in legno|Cuna de madera
Cuscini|Cojines
Cuscini decorativi|Cojines decorativos
Cuscino avorio|Cojín marfil
Cuscino bianco|Cojín blanco
Cuscino blu|Cojín azul
Cuscino lungo|Cojín alargado
Cuscino nero|Cojín negro
Cuscino per schienale|Cojín de respaldo
Cuscino rosa antico|Cojín rosa empolvado
Dispenser acqua|Dispensador de agua
Divanetto Chesterfield bianco|Sofá Chesterfield blanco de dos plazas
Divanetto arancio|Sofá pequeño naranja
Divanetto bianco|Sofá pequeño blanco
Divanetto grigio|Sofá pequeño gris
Divanetto in pelle scura|Sofá pequeño de piel oscura
Divanetto rosso|Sofá pequeño rojo
Divanetto rosso moderno|Sofá pequeño rojo moderno
Divano|Sofá
Divano 2 posti|Sofá de 2 plazas
Divano 3 posti|Sofá de 3 plazas
Divano Chesterfield|Sofá Chesterfield
Divano Chesterfield grigio|Sofá Chesterfield gris
Divano Winchester|Sofá Winchester
Divano ad angolo|Sofá rinconero
Divano angolare antracite|Sofá rinconero antracita
Divano angolare blu|Sofá rinconero azul
Divano angolare fucsia|Sofá rinconero fucsia
Divano angolare grigio|Sofá rinconero gris
Divano angolare grigio chiaro|Sofá rinconero gris claro
Divano angolare rosa|Sofá rinconero rosa
Divano antracite|Sofá antracita
Divano antracite a tre posti|Sofá antracita de tres plazas
Divano azzurro|Sofá azul claro
Divano azzurro polvere|Sofá azul empolvado
Divano bianco classico|Sofá blanco clásico
Divano chiaro|Sofá claro
Divano classico bianco|Sofá clásico blanco
Divano classico chiaro|Sofá clásico claro
Divano componibile|Sofá modular
Divano con braccioli in legno|Sofá con brazos de madera
Divano con penisola grigio|Sofá gris con chaise longue
Divano con plaid|Sofá con manta
Divano con pouf|Sofá con puf
Divano due posti|Sofá de dos plazas
Divano giallo|Sofá amarillo
Divano grigio chiaro|Sofá gris claro
Divano grigio con cuscini|Sofá gris con cojines
Divano imbottito|Sofá tapizado
Divano in legno|Sofá de madera
Divano in pelle marrone|Sofá de piel marrón
Divano letto|Sofá cama
Divano letto aperto|Sofá cama abierto
Divano letto bianco|Sofá cama blanco
Divano letto grigio|Sofá cama gris
Divano letto rosa|Sofá cama rosa
Divano moderno grigio|Sofá moderno gris
Divano modulare bianco|Sofá modular blanco
Divano nero con coperta|Sofá negro con manta
Divano orientale|Sofá oriental
Divano rosa capitonné|Sofá capitoné rosa
Divano scandinavo|Sofá escandinavo
Dormeuse|Diván
Enciclopedia|Enciclopedia
Estensione ad isola|Extensión en isla
Faro da esterno|Foco de exterior
Fasciatoio|Cambiador
Felce di Boston|Helecho de Boston
Ficus|Ficus
Ficus in vaso|Ficus en maceta
Finestra con tende rosse|Ventana con cortinas rojas
Fiori in vaso|Flores en maceta
Fioriera|Jardinera
Fioriera alta|Jardinera alta
Fioriera lunga|Jardinera larga
Fontana decorativa|Fuente decorativa
Fornetto|Horno de sobremesa
Forno|Horno
Forno bianco|Horno blanco
Forno doppio|Horno doble
Frigorifero|Frigorífico
Frigorifero americano|Frigorífico americano
Frigorifero antracite|Frigorífico antracita
Frigorifero bianco|Frigorífico blanco
Frigorifero bianco classico|Frigorífico blanco clásico
Frigorifero con freezer|Frigorífico combi
Frigorifero doppio|Frigorífico doble
Frigorifero retrò|Frigorífico retro
Frigorifero side by side|Frigorífico side by side
Frigorifero vintage aperto|Frigorífico vintage abierto
Futon|Futón
Gatto in cemento|Gato de cemento
Giradischi a valigetta|Tocadiscos de maleta
Grammofono|Gramófono
Griglia da esterno|Barbacoa
Guardaroba|Ropero
Il bacio|El beso
Isola con lavello|Isla con fregadero
Isola cucina moderna|Isla de cocina moderna
Lampada|Lámpara
Lampada a muro|Lámpara de pared
Lampada a paralume grigio|Lámpara con pantalla gris
Lampada a sospensione|Lámpara colgante
Lampada a sospensione lunga|Lámpara colgante larga
Lampada classica|Lámpara clásica
Lampada classica bianca|Lámpara clásica blanca
Lampada con paralume|Lámpara con pantalla
Lampada con paralume beige|Lámpara con pantalla beige
Lampada con paralume plissé|Lámpara con pantalla plisada
Lampada da banchiere|Lámpara de banquero
Lampada da banchiere verde|Lámpara de banquero verde
Lampada da esterno|Lámpara de exterior
Lampada da lettura|Lámpara de lectura
Lampada da scrivania|Lámpara de escritorio
Lampada da soffitto|Lámpara de techo
Lampada da tavolo|Lámpara de mesa
Lampada da tavolo industriale|Lámpara de mesa industrial
Lampada da terra classica|Lámpara de pie clásica
Lampada da terra sottile|Lámpara de pie fina
Lampada da terra vintage|Lámpara de pie vintage
Lampada in bronzo|Lámpara de bronce
Lampada in porcellana|Lámpara de porcelana
Lampada moderna|Lámpara moderna
Lampada sospesa|Lámpara suspendida
Lampada verde a fungo|Lámpara seta verde
Lampadario|Araña
Lampadario a bracci|Araña de brazos
Lampadario a candele in ferro|Araña de velas de hierro
Lampadario a candele nero|Araña de velas negra
Lampadario a lanterna|Araña farol
Lampadario classico|Araña clásica
Lampadario con paralumi|Araña con pantallas
Lampadario con paralumi grigi|Araña con pantallas grises
Lampadario di cristallo|Araña de cristal
Lampadario di cristallo azzurro|Araña de cristal azul claro
Lampadario in ferro|Araña de hierro
Lampadario in legno|Araña de madera
Lampadario in ottone|Araña de latón
Lampadario in ottone sottile|Araña fina de latón
Lampadario orientale|Araña oriental
Lampadario rustico|Araña rústica
Lampadario viola|Araña morada
Lampione da giardino|Farola de jardín
Lampione da giardino in legno|Farola de jardín de madera
Lanterna appesa|Farol colgante
Lanterna da giardino|Farol de jardín
Lanterna da terra|Farol de suelo
Lanterna di carta|Farolillo de papel
Lanterna giapponese|Farol japonés
Lanterna in legno|Farol de madera
Lanterna in pietra|Farol de piedra
Lavabo a colonna|Lavabo de pedestal
Lavabo bianco|Lavabo blanco
Lavabo da appoggio|Lavabo sobre encimera
Lavabo ergonomico|Lavabo accesible
Lavabo in pietra|Lavabo de piedra
Lavabo piccolo|Lavabo pequeño
Lavabo quadrato|Lavabo cuadrado
Lavabo quadrato in acciaio|Lavabo cuadrado de acero
Lavabo sospeso|Lavabo suspendido
Lavagna bianca|Pizarra blanca
Lavamani|Lavamanos
Lavanda|Lavanda
Lavandino|Lavabo
Lavandino con doppio specchio|Lavabo con doble espejo
Lavandino con specchio|Lavabo con espejo
Lavastoviglie|Lavavajillas
Lavastoviglie piccola|Lavavajillas compacto
Lavatrice|Lavadora
Lavatrice a carica dall'alto|Lavadora de carga superior
Lavatrice frontale|Lavadora de carga frontal
Lavello bianco|Fregadero blanco
Lavello da incasso|Fregadero encastrado
Lavello doppio in acciaio|Fregadero doble de acero
Lavello grigio|Fregadero gris
Lavello in acciaio|Fregadero de acero
Lavello nero|Fregadero negro
Lavello nero a una vasca|Fregadero negro de un seno
Lavello nero compatto|Fregadero negro compacto
Lettino|Camita
Lettino prendisole|Tumbona
Letto|Cama
Letto a castello bianco|Litera blanca
Letto a castello bianco classico|Litera blanca clásica
Letto a castello con scaletta|Litera con escalera
Letto a castello curvo|Litera curva
Letto a castello in legno|Litera de madera
Letto a castello in metallo|Litera de metal
Letto a castello rosa|Litera rosa
Letto a giorno|Cama de día
Letto bianco a spalliere|Cama blanca con cabecero y piecero
Letto con cassetti|Cama con cajones
Letto con testiera nera|Cama con cabecero negro
Letto gotico|Cama gótica
Letto imbottito rosa|Cama tapizada rosa
Letto in ferro|Cama de hierro
Letto in ferro battuto|Cama de hierro forjado
Letto in ferro blu|Cama de hierro azul
Letto in legno|Cama de madera
Letto in legno chiaro|Cama de madera clara
Letto in legno scuro|Cama de madera oscura
Letto in metallo con coperta|Cama de metal con manta
Letto in ottone|Cama de latón
Letto matrimoniale moderno|Cama de matrimonio moderna
Letto moderno bianco|Cama moderna blanca
Letto per bambini|Cama infantil
Letto singolo|Cama individual
Letto singolo in ferro|Cama individual de hierro
Letto singolo in legno|Cama individual de madera
Letto soppalcato|Cama alta
Libreria|Librería
Libreria a vetri|Librería con cristales
Libreria antica|Librería antigua
Libreria azzurra|Librería azul claro
Libreria con libri|Librería con libros
Libreria credenza|Librería aparador
Libreria grande|Librería grande
Libreria grigia|Librería gris
Libreria in legno|Librería de madera
Libreria rustica|Librería rústica
Libreria stretta|Librería estrecha
Lume a petrolio|Quinqué
Macchina da caffé|Máquina de café
Macchina del caffè|Cafetera
Macchina del pane|Panificadora
Madia moderna|Aparador moderno bajo
Madia scura|Aparador oscuro bajo
Maniglione pieghevole|Barra de apoyo abatible
Mappamondo antico|Globo terráqueo antiguo
Mappamondo su piedistallo|Globo terráqueo con pie
Materasso da pavimento|Colchón de suelo
Microonde|Microondas
Microonde bianco|Microondas blanco
Microonde blu|Microondas azul
Microonde classico|Microondas clásico
Microonde grigio|Microondas gris
Microonde in acciaio|Microondas de acero
Microonde nero|Microondas negro
Miscelatore per doccia|Grifo de ducha
Miscelatore per vasca|Grifo de bañera
Miscelatore per vasca e doccia|Grifo de bañera y ducha
Mobile TV|Mueble de TV
Mobile TV anni '50|Mueble de TV de los años 50
Mobile TV vintage|Mueble de TV vintage
Mobile a cassetti|Mueble de cajones
Mobile ad angolo inferiore|Mueble bajo de esquina
Mobile ad angolo superiore|Mueble alto de esquina
Mobile ad angolo superiore piccolo|Mueble alto de esquina pequeño
Mobile alto|Mueble alto
Mobile alto in legno|Mueble alto de madera
Mobile bagno moderno|Mueble de baño moderno
Mobile con cassetti|Mueble con cajones
Mobile con mensole|Mueble con estantes
Mobile con vetri|Mueble con cristales
Mobile inferiore|Mueble bajo
Mobile lavabo|Mueble de lavabo
Mobile lavandino|Mueble bajo lavabo
Mobile lavello|Mueble bajo fregadero
Mobile orientale|Mueble oriental
Mobile soggiorno|Mueble de salón
Mobile superiore|Mueble alto de pared
Mobile vetrina chiaro|Vitrina clara
Mobile vetrina scuro|Vitrina oscura
Mobiletto blu|Mueblecito azul
Mobiletto gotico|Mueblecito gótico
Mobiletto in legno|Mueblecito de madera
Monstera in vaso|Monstera en maceta
Ombrellone bianco|Sombrilla blanca
Ombrellone nero|Sombrilla negra
Ombrellone rosa|Sombrilla rosa
Orinale|Urinario
Orologio a cucù|Reloj de cuco
Orologio a numeri romani|Reloj con números romanos
Orologio a raggi|Reloj sol
Orologio da mensola|Reloj de repisa
Orologio da parete|Reloj de pared
Orologio da parete a telefono|Reloj de pared con forma de teléfono
Orologio da parete classico|Reloj de pared clásico
Orologio da parete grigio|Reloj de pared gris
Orologio da parete tondo|Reloj de pared redondo
Pagoda in pietra|Pagoda de piedra
Palme in vaso|Palmeras en maceta
Panca capitonné|Banco capitoné
Panca con schienale|Banco con respaldo
Panca in legno|Banco de madera
Panca in legno massello|Banco de madera maciza
Panchetta imbottita|Reposapiés tapizado
Panchina classica|Banco clásico
Panchina da giardino|Banco de jardín
Panchina da parco|Banco de parque
Panchina in ferro|Banco de hierro
Panchina moderna|Banco moderno
Panchina verde|Banco verde
Paravento|Biombo
Pattumiera in acciaio|Cubo de pedal de acero
Pendolo|Reloj de péndulo
Pendolo antico|Reloj de péndulo antiguo
Pendolo classico|Reloj de péndulo clásico
Pendolo in mogano|Reloj de péndulo de caoba
Pendolo moderno|Reloj de péndulo moderno
Piano cottura|Placa de cocina
Piano cottura a gas a due fuochi|Placa de gas de dos fuegos
Piano cottura a induzione|Placa de inducción
Pianoforte|Piano
Pianoforte a coda|Piano de cola
Pianoforte verticale|Piano vertical
Pianoforte verticale classico|Piano vertical clásico
Pianoforte verticale nero|Piano vertical negro
Pianoforte verticale scuro|Piano vertical oscuro
Pianta|Planta
Pianta grande|Planta grande
Pianta grassa|Planta crasa
Pianta grassa in vaso viola|Planta crasa en maceta morada
Pianta in vaso|Planta en maceta
Pianta piccola|Planta pequeña
Pianta tropicale|Planta tropical
Piccola lampada|Lámpara pequeña
PlayStation|PlayStation
Poltrona|Sillón
Poltrona Winchester|Sillón Winchester
Poltrona a dondolo in legno|Sillón balancín de madera
Poltrona a dondolo moderna|Sillón balancín moderno
Poltrona antica|Sillón antiguo
Poltrona beige|Sillón beige
Poltrona bergère bianca|Butaca bergère blanca
Poltrona bergère verde|Butaca bergère verde
Poltrona blu design|Sillón de diseño azul
Poltrona capitonné|Sillón capitoné
Poltrona capitonné bordeaux|Sillón capitoné burdeos
Poltrona classica|Sillón clásico
Poltrona classica grigia|Sillón clásico gris
Poltrona con cuscino|Sillón con cojín
Poltrona da doccia|Silla de ducha con brazos
Poltrona da relax capitonné|Sillón relax capitoné
Poltrona di design|Sillón de diseño
Poltrona girevole|Sillón giratorio
Poltrona grigia|Sillón gris
Poltrona in pelle beige|Sillón de piel beige
Poltrona in pelle cuoio|Sillón de piel color cuero
Poltrona moderna|Sillón moderno
Poltrona orientale|Sillón oriental
Poltrona reclinabile|Sillón reclinable
Poltrona rosa|Sillón rosa
Poltrona rosa cipria|Sillón rosa empolvado
Poltrona rossa|Sillón rojo
Poltrona rossa moderna|Sillón rojo moderno
Poltrona tortora|Sillón gris topo
Poltrona viola|Sillón morado
Poltroncina|Butaca
Poltroncina bianca|Butaca blanca
Poltroncina koala|Butaca koala
Porta asciugamano|Toallero
Porta doccia|Puerta de ducha
Portafoto|Portarretratos
Portafoto grande|Portarretratos grande
Portafrutta|Frutero
Portarotolo|Portarrollos
Portasciugamani|Toallero de pie
Portasciugamani a barra|Toallero de barra
Portasciugamani a muro|Toallero de pared
Portatile|Portátil
Pothos in vaso|Potus en maceta
Pouf|Puf
Pouf a sacco marrone|Puf pera marrón
Pouf a sacco rosso|Puf pera rojo
Pouf a sacco viola|Puf pera morado
Pouf antracite|Puf antracita
Pouf bianco|Puf blanco
Pouf blu capitonné|Puf capitoné azul
Pouf capitonné|Puf capitoné
Pouf capitonné grigio|Puf capitoné gris
Pouf grigio|Puf gris
Pouf in pelle nera|Puf de piel negra
Pouf nero|Puf negro
Pouf rotondo|Puf redondo
Pouf tondo bordeaux|Puf redondo burdeos
Puff|Reposapiés
Quadro|Cuadro
Quadro grande|Cuadro grande
Quadro grigio|Cuadro gris
Quadro in legno|Cuadro de madera
Quadro rosa|Cuadro rosa
Radio giradischi vintage|Radio tocadiscos vintage
Ripiani superiori|Estantes superiores
Rose|Rosas
Salottino da esterno|Conjunto de exterior
Scaffale con cassetti|Estantería con cajones
Scaffale in metallo|Estantería de metal
Scaffale metallico|Estantería metálica
Scaffale metallico alto|Estantería metálica alta
Scaffale metallico stretto|Estantería metálica estrecha
Scaffale verticale|Estantería vertical
Scaffaletto dipinto|Estantería pequeña pintada
Scaffali|Estantes
Schedario bianco|Archivador blanco
Schedario in metallo|Archivador de metal
Scrivania|Escritorio
Scrivania antica|Escritorio antiguo
Scrivania bianca|Escritorio blanco
Scrivania classica|Escritorio clásico
Scrivania con computer|Escritorio con ordenador
Scrivania con computer grigia|Escritorio gris con ordenador
Scrivania in legno|Escritorio de madera
Scrivania in metallo|Escritorio de metal
Scrivania in mogano|Escritorio de caoba
Scrivania in noce|Escritorio de nogal
Scrivania nera|Escritorio negro
Scrivania regolabile|Escritorio regulable
Scrivania semplice|Escritorio sencillo
Scrivania ufficio a L|Escritorio de oficina en L
Scultura del pensatore|Escultura del Pensador
Scultura in bronzo|Escultura de bronce
Sedia|Silla
Sedia Osaka|Silla Osaka
Sedia Savonarola|Silla Savonarola
Sedia Tolix|Silla Tolix
Sedia Windsor nera|Silla Windsor negra
Sedia a dondolo|Mecedora
Sedia a dondolo bianca|Mecedora blanca
Sedia a dondolo classica|Mecedora clásica
Sedia a dondolo gialla|Mecedora amarilla
Sedia a dondolo grigia|Mecedora gris
Sedia a dondolo imbottita|Mecedora tapizada
Sedia a dondolo in cuoio|Mecedora de cuero
Sedia a dondolo in legno|Mecedora de madera
Sedia a dondolo in legno scuro|Mecedora de madera oscura
Sedia a dondolo rosa|Mecedora rosa
Sedia a dondolo scura|Mecedora oscura
Sedia alta scura|Silla alta oscura
Sedia anni '50|Silla de los años 50
Sedia bianca|Silla blanca
Sedia bianca classica|Silla blanca clásica
Sedia bianca e grigia|Silla blanca y gris
Sedia bistrot|Silla bistró
Sedia classica|Silla clásica
Sedia con braccioli|Silla con brazos
Sedia con cuscino|Silla con cojín
Sedia da bar|Silla de bar
Sedia da doccia|Silla de ducha
Sedia da gaming|Silla gaming
Sedia da giardino|Silla de jardín
Sedia da pranzo|Silla de comedor
Sedia da ufficio|Silla de oficina
Sedia dipinta|Silla pintada
Sedia dipinta azzurra|Silla pintada azul claro
Sedia girevole|Silla giratoria
Sedia gotica|Silla gótica
Sedia in Plastica|Silla de plástico
Sedia in faggio|Silla de haya
Sedia in metallo|Silla de metal
Sedia intagliata|Silla tallada
Sedia per bambini|Silla infantil
Sedia quercia|Silla de roble
Sedia rosa|Silla rosa
Sedia rustica|Silla rústica
Sedia ryokan|Silla ryokan
Sedia scandinava|Silla escandinava
Sedile ribaltabile a muro|Asiento abatible de pared
Seggiolone|Trona
Servizio da tè|Juego de té
Set da giardino in metallo|Conjunto de jardín de metal
Set da patio con ombrellone|Conjunto de patio con sombrilla
Set da pranzo bianco|Conjunto de comedor blanco
Set da pranzo grigio|Conjunto de comedor gris
Set da pranzo in ferro|Conjunto de comedor de hierro
Set da pranzo rustico|Conjunto de comedor rústico
Set da pranzo scuro|Conjunto de comedor oscuro
Settimanale|Sinfonier
Sfera armillare|Esfera armilar
Sgabello|Taburete
Sgabello alto in legno|Taburete alto de madera
Sgabello basso|Taburete bajo
Sgabello bianco|Taburete blanco
Sgabello da bar|Taburete de bar
Sgabello da bar in metallo|Taburete de bar de metal
Sgabello da officina|Taburete de taller
Sgabello da pianoforte|Banqueta de piano
Sgabello dipinto|Taburete pintado
Sgabello girevole|Taburete giratorio
Sgabello in legno|Taburete de madera
Sgabello in legno chiaro|Taburete de madera clara
Sgabello in metallo|Taburete de metal
Sgabello industriale|Taburete industrial
Sgabello orientale|Taburete oriental
Sgabello per pianoforte|Banco de piano
Sgabello pieghevole|Taburete plegable
Sgabello rustico|Taburete rústico
Soffione doccia e rubinetto|Rociador de ducha y grifo
Sospensione industriale|Colgante industrial
Specchio ad arco|Espejo en arco
Specchio barocco|Espejo barroco
Specchio capitonné|Espejo capitoné
Specchio con cornice scura|Espejo con marco oscuro
Specchio da terra|Espejo de pie
Specchio decorato|Espejo decorado
Specchio dorato|Espejo dorado
Specchio in stile impero|Espejo estilo imperio
Specchio ovale|Espejo ovalado
Stampante e scanner|Impresora y escáner
Statua classica|Estatua clásica
Statua di cavallo|Estatua de caballo
Statua di donna|Estatua de mujer
Statua egizia|Estatua egipcia
Statua in terracotta|Estatua de terracota
Stereo portatile|Equipo de música portátil
Stufa|Estufa
Stufa a legna|Estufa de leña
Sveglia|Despertador
Sveglia vintage|Despertador vintage
TV|TV
TV 16:9|TV 16:9
TV a muro|TV de pared
Tapis roulant|Cinta de correr
Tapis roulant pieghevole|Cinta de correr plegable
Tappetino azzurro|Alfombrilla azul claro
Tappetino da bagno bianco|Alfombrilla de baño blanca
Tappetino grigio|Alfombrilla gris
Tappetino in spugna|Alfombrilla de rizo
Tappetino rosa|Alfombrilla rosa
Tappeto grigio|Alfombra gris
Tappeto orientale arancio|Alfombra oriental naranja
Tappeto persiano rosso|Alfombra persa roja
Tappeto rotondo|Alfombra redonda
Tavolino|Mesita
Tavolino a colonna|Mesita de pedestal
Tavolino alto|Mesita alta
Tavolino alto in legno|Mesita alta de madera
Tavolino basso|Mesa de centro
Tavolino bistrot|Mesa bistró
Tavolino con brocca|Mesita con jarra
Tavolino con intreccio|Mesita trenzada
Tavolino con tovaglia|Mesita con mantel
Tavolino da tè|Mesa de té
Tavolino gotico|Mesita gótica
Tavolino in legno|Mesita de madera
Tavolino industriale|Mesita industrial
Tavolino intagliato|Mesita tallada
Tavolino laterale|Mesa auxiliar
Tavolino moderno|Mesita moderna
Tavolino ovale|Mesita ovalada
Tavolino per bambini|Mesa infantil
Tavolino rotondo|Mesita redonda
Tavolino rotondo in legno|Mesita redonda de madera
Tavolino tondo design|Mesita redonda de diseño
Tavolino tondo in legno|Mesa redonda de madera
Tavolino tondo in noce|Mesita redonda de nogal
Tavolino tondo scandinavo|Mesita redonda escandinava
Tavolino verde intagliato|Mesita verde tallada
Tavolo|Mesa
Tavolo bianco|Mesa blanca
Tavolo bianco classico|Mesa blanca clásica
Tavolo bianco da cucina|Mesa de cocina blanca
Tavolo bianco moderno|Mesa blanca moderna
Tavolo con panche|Mesa con bancos
Tavolo con tovaglia|Mesa con mantel
Tavolo con tovaglia blu|Mesa con mantel azul
Tavolo da bar|Mesa de bar
Tavolo da caffè|Mesa de cafetería
Tavolo da picnic|Mesa de pícnic
Tavolo design|Mesa de diseño
Tavolo dipinto|Mesa pintada
Tavolo e sedie da giardino|Mesa y sillas de jardín
Tavolo grande con sedie|Mesa grande con sillas
Tavolo grigio|Mesa gris
Tavolo grigio scuro|Mesa gris oscuro
Tavolo in legno|Mesa de madera
Tavolo in legno chiaro|Mesa de madera clara
Tavolo in legno di Tek|Mesa de teca
Tavolo in legno di ontano|Mesa de aliso
Tavolo in legno massello|Mesa de madera maciza
Tavolo in legno rosso|Mesa de madera roja
Tavolo in vetro|Mesa de cristal
Tavolo lungo|Mesa larga
Tavolo nero|Mesa negra
Tavolo quadrato|Mesa cuadrada
Tavolo quercia|Mesa de roble
Tavolo rotondo|Mesa redonda
Tavolo rotondo grande|Mesa redonda grande
Tavolo scuro|Mesa oscura
Tavolo semplice|Mesa sencilla
Tavolo su cavalletti|Mesa de caballetes
Tavolo tecnico|Mesa de dibujo
Tavolo tondo|Mesa redonda de comedor
Tavolo tulipano|Mesa tulipán
Tecnigrafo|Tecnígrafo
Televisore a schermo piatto|Televisor de pantalla plana
Televisore anni '60|Televisor de los años 60
Televisore anni '80|Televisor de los años 80
Televisore in legno|Televisor de madera
Televisore portatile|Televisor portátil
Televisore portatile vintage|Televisor portátil vintage
Televisore retrò|Televisor retro
Televisore su gambe|Televisor con patas
Televisore vintage|Televisor vintage
Telo per proiettore|Pantalla de proyección
Tenda a pannello|Cortina de panel
Tenda su bastone|Cortina en barra
Tende bianche|Cortinas blancas
Tende bordeaux|Cortinas burdeos
Tende con mantovana|Cortinas con bandó
Tende di lino|Cortinas de lino
Termoarredo|Radiador de diseño
Termosifone a colonne|Radiador de columnas
Termosifone a colonne bianco|Radiador de columnas blanco
Termosifone a pannello|Radiador de panel
Termosifone bianco|Radiador blanco
Termosifone decorativo|Radiador decorativo
Termosifone in ghisa|Radiador de hierro fundido
Termosifone piatto|Radiador plano
Toilette giapponese|Inodoro japonés
Toletta|Tocador
Tostapane|Tostadora
Turca|Inodoro turco
Unità esterne del condizionatore|Unidades exteriores de aire acondicionado
Urinale|Urinario de pared
Valigie vintage|Maletas vintage
Vasca ad angolo|Bañera de esquina
Vasca ad isola|Bañera exenta en isla
Vasca con doccia|Bañera con ducha
Vasca con piedini|Bañera con patas
Vasca con piedini bianca|Bañera con patas blanca
Vasca con rubinetto|Bañera con grifo
Vasca freestanding|Bañera exenta
Vasca in ottone|Bañera de latón
Vasca ovale|Bañera ovalada
Vasca rettangolare|Bañera rectangular
Vasca vittoriana|Bañera victoriana
Vaso antico|Jarrón antiguo
Vaso bianco|Jarrón blanco
Vaso bianco e blu|Jarrón azul y blanco
Vaso bianco panciuto|Jarrón blanco panzudo
Vaso celadon|Jarrón celadón
Vaso di terracotta|Maceta de terracota
Vaso in ceramica|Jarrón de cerámica
Vaso in ceramica alto|Jarrón alto de cerámica
Vaso in ottone|Jarrón de latón
Vaso in ottone alto|Jarrón alto de latón
Vaso in terracotta|Jarrón de terracota
Vaso lilla|Jarrón lila
Vaso rosa antico|Jarrón rosa empolvado
Vaso smaltato|Jarrón esmaltado
Vaso verde|Jarrón verde
Venere di Milo|Venus de Milo
Ventilatore a soffitto|Ventilador de techo
Ventilatore da tavolo|Ventilador de mesa
Ventilatore in ottone|Ventilador de latón
Ventilatore vintage|Ventilador vintage
Vetrina bianca|Vitrina blanca
Vetrina con ante|Vitrina con puertas
Vetrinetta|Vitrina pequeña
Vetrinetta a giorno|Vitrina pequeña abierta
Vetrinetta antica|Vitrina antigua
Videoproiettore|Proyector
WC bianco|Inodoro blanco
WC classico|Inodoro clásico
WC compatto|Inodoro compacto
WC con cassetta|Inodoro con cisterna
WC moderno|Inodoro moderno
WC nero|Inodoro negro
Water|Váter
Zerbino beige|Felpudo beige
Zerbino marrone|Felpudo marrón
"""

    private const val MATERIALI = """
Ardesia a lastre|Pizarra en losas
Assi chiare|Tablas claras
Assi chiare nodose|Tablas claras con nudos
Assi color miele|Tablas color miel
Assi da ponte|Tablones de andamio
Assi scure|Tablas oscuras
Assi vissute|Tablas envejecidas
Autobloccanti|Adoquines entrelazados
Blocchi grigi|Bloques grises
Carta da parati jacquard|Papel pintado jacquard
Carta da parati marmorizzata|Papel pintado marmoleado
Cementine bianco e nero|Baldosas hidráulicas blanco y negro
Cementine decorate|Baldosas hidráulicas decoradas
Cementine geometriche|Baldosas hidráulicas geométricas
Cemento a lastre|Losas de hormigón
Cemento chiaro|Hormigón claro
Cemento grigio|Hormigón gris
Cemento scuro|Hormigón oscuro
Cemento spatolato|Hormigón alisado
Ciottolato|Empedrado de cantos
Cotto|Barro cocido
Cotto chiaro|Barro cocido claro
Cotto scuro|Barro cocido oscuro
Doghe bianche|Lamas blancas
Doghe in legno|Lamas de madera
Doghe rosse|Lamas rojas
Doghe strette scure|Lamas estrechas oscuras
Esagonette bianche|Hexágonos blancos
Esagonette nere|Hexágonos negros
Esagoni in cemento|Hexágonos de hormigón
Ghiaia|Grava
Gres antracite a correre|Gres antracita a matajunta
Gres ardesia|Gres efecto pizarra
Gres beige|Gres beige
Gres bianco 60×60|Gres blanco 60×60
Gres bianco grande formato|Gres blanco gran formato
Gres grafite|Gres grafito
Gres nero|Gres negro
Gres pietra chiara|Gres piedra clara
Intonaco bianco|Enlucido blanco
Intonaco fine|Enlucido fino
Intonaco grigio|Enlucido gris
Intonaco liscio|Enlucido liso
Intonaco rustico|Enlucido rústico
Juta|Yute
Juta fine|Yute fino
Laminato rovere|Laminado de roble
Lana a righe|Lana a rayas
Lana grigia|Lana gris
Lana grigia melange|Lana gris jaspeado
Lana intrecciata bianca|Lana trenzada blanca
Lana rossa a coste|Lana roja acanalada
Lastre a intreccio|Losas entrelazadas
Lastre beige|Losas beige
Lastre di marmo rosa|Losas de mármol rosa
Lastre di pietra beige|Losas de piedra beige
Lastre di pietra chiara|Losas de piedra clara
Lastre grigie|Losas grises
Lastre nere lucide|Losas negras brillantes
Lino verde|Lino verde
Listoni noce|Tablones de nogal
Listoni rovere|Tablones de roble
Listoni rovere naturale|Tablones de roble natural
Listoni teak|Tablones de teca
Maioliche blu|Azulejos azules
Marmo Emperador chiaro|Mármol Emperador claro
Marmo bianco venato|Mármol blanco veteado
Marmo blu notte|Mármol azul noche
Marmo crema|Mármol crema
Marmo di Carrara|Mármol de Carrara
Marmo grigio|Mármol gris
Marmo grigio a lastre|Losas de mármol gris
Marmo grigio perla|Mármol gris perla
Marmo nero Marquinia|Mármol Negro Marquina
Marmo nero lucido|Mármol negro brillante
Marmo nero venato|Mármol negro veteado
Marmo oro|Mármol dorado
Marmo tortora|Mármol gris topo
Marmo verde|Mármol verde
Mattoncini bianchi|Plaquetas blancas
Mattoncini verdi|Plaquetas verdes
Mattonelle chiare|Baldosas claras
Mattoni antichi|Ladrillos antiguos
Mattoni arancio|Ladrillos naranja
Mattoni arancio lisci|Ladrillos naranja lisos
Mattoni bianchi|Ladrillos blancos
Mattoni bruciati|Ladrillos quemados
Mattoni crema|Ladrillos crema
Mattoni da esterno|Ladrillos de exterior
Mattoni faccia a vista|Ladrillos caravista
Mattoni gialli|Ladrillos amarillos
Mattoni misti|Ladrillos mixtos
Mattoni rossi|Ladrillos rojos
Mattoni rustici|Ladrillos rústicos
Mattoni scuri|Ladrillos oscuros
Mattoni vissuti|Ladrillos envejecidos
Moquette a fiori|Moqueta de flores
Moquette a quadri grigia|Moqueta gris a cuadros
Moquette avorio|Moqueta marfil
Moquette beige|Moqueta beige
Moquette beige a riquadri|Moqueta beige con recuadros
Moquette bianca|Moqueta blanca
Moquette blu|Moqueta azul
Moquette blu a quadri|Moqueta azul a cuadros
Moquette rosa melange|Moqueta rosa jaspeado
Moquette rossa|Moqueta roja
Moquette verde oliva|Moqueta verde oliva
Mosaico acquamarina|Mosaico aguamarina
Mosaico azzurro|Mosaico azul claro
Mosaico grigio|Mosaico gris
Muro in pietra|Muro de piedra
Muro in pietra grezza|Muro de piedra en bruto
Onice azzurro|Ónice azul
Onice bianco|Ónice blanco
Onice miele|Ónice miel
Onice nero|Ónice negro
Onice rosso|Ónice rojo
Onice verde acqua|Ónice verde agua
Opus incertum|Opus incertum
Paglia intrecciata|Paja trenzada
Parquet a cesto|Parquet de cestería
Parquet a intreccio chiaro|Parquet entrelazado claro
Parquet a intreccio scuro|Parquet entrelazado oscuro
Parquet a quadri|Parquet a cuadros
Parquet a riquadri|Parquet de paneles
Parquet a riquadri classico|Parquet de paneles clásico
Parquet acero|Parquet de arce
Parquet anticato|Parquet envejecido
Parquet ciliegio|Parquet de cerezo
Parquet in diagonale|Parquet en diagonal
Parquet noce|Parquet de nogal
Parquet rovere miele|Parquet de roble miel
Pelliccia geometrica|Pelo sintético geométrico
Perlinato scuro|Friso oscuro
Piastrelle a scacchi|Baldosas en damero
Piastrelle beige|Baldosas beige
Piastrelle bianche 10×10|Baldosas blancas 10×10
Piastrelle bianche lucide|Baldosas blancas brillantes
Piastrelle blu|Baldosas azules
Piastrelle blu antiche|Baldosas azules antiguas
Piastrelle in diagonale|Baldosas en diagonal
Piastrelle in graniglia|Baldosas de terrazo
Piastrelle marroni|Baldosas marrones
Pietra a opus|Piedra en opus
Pietra a secco|Piedra seca
Pietra a strati|Piedra en capas
Pietra beige|Piedra beige
Pietra gialla|Piedra amarilla
Pietre con erba|Piedras en el césped
Porfido|Pórfido
Porfido a ventaglio|Pórfido en abanico
Prato|Césped
Prato all'inglese|Césped inglés
Quadri grigi|Cuadros grises
Quadrotte scure|Losetas oscuras
Quadrotte vissute|Losetas envejecidas
Resina bianca|Resina blanca
Sampietrini a ventaglio|Adoquines en abanico
Scacchiera bianco e nero|Damero blanco y negro
Scacchiera da esterno|Damero de exterior
Scacchiera in marmo|Damero de mármol
Selciato|Empedrado
Spina di pesce ciliegio|Espiga de cerezo
Spina di pesce classica|Espiga clásica
Spina di pesce naturale|Espiga natural
Spina di pesce rovere|Espiga de roble
Tartan|Tartán
Tavolato bianco|Entarimado blanco
Tavolato rustico|Entarimado rústico
Tavolato sbiancato|Entarimado blanqueado
Terrazzo chiaro|Terrazo claro
Terrazzo colorato|Terrazo de colores
Terrazzo grigio|Terrazo gris
Terrazzo multicolore|Terrazo multicolor
Terrazzo nero|Terrazo negro
Terrazzo veneziano|Terrazo veneciano
Tessuto a motivi|Tejido estampado
Tessuto a motivi etnici|Tejido de motivos étnicos
Travertino|Travertino
Travertino azzurro|Travertino azul
Travertino classico|Travertino clásico
Travertino grigio|Travertino gris
Travertino noce|Travertino nogal
Velluto azzurro|Terciopelo azul claro
Velluto blu|Terciopelo azul
Velluto salvia|Terciopelo salvia
Vimini intrecciato|Mimbre trenzado
Wengè|Wengué
Moquette grigia a quadretti|Moqueta gris a cuadritos
Moquette petrolio a riquadri|Moqueta azul petróleo a recuadros
Moquette sabbia a riquadri|Moqueta arena a recuadros
Cemento liscio|Hormigón liso
Cemento a vista con fori|Hormigón visto con agujeros
Cemento a riquadri|Hormigón a recuadros
Microcemento grigio|Microcemento gris
Microcemento perla|Microcemento perla
Microcemento antracite|Microcemento antracita
Resina grigia|Resina gris
Cemento a casseri|Hormigón encofrado
Resina verde muschio|Resina verde musgo
Resina sabbia|Resina arena
Pannelli in cemento a righe|Paneles de hormigón acanalados
Sughero fine|Corcho fino
Sughero a granuli|Corcho granulado
Granito rosa|Granito rosa
Granito bianco sardo|Granito blanco sardo
Granito grigio rosato|Granito gris rosado
Granito bruno|Granito marrón
Granito lilla|Granito lila
Granito rosso|Granito rojo
Granito giallo|Granito amarillo
Marmo bianco perla|Mármol blanco perla
Marmo nero assoluto|Mármol negro absoluto
Marmo avorio|Mármol marfil
Marmo rosa|Mármol rosa
Marmo Emperador scuro|Mármol Emperador oscuro
Granito blu Labrador|Granito azul Labrador
Marmo crema Marfil|Mármol Crema Marfil
Marmo nero Portoro|Mármol negro Portoro
Marmo Botticino|Mármol Botticino
Marmo rosso Alicante|Mármol rojo Alicante
Marmo bianco Thassos|Mármol blanco Thassos
Pietra di Trani|Piedra de Trani
Onice grigio|Ónice gris
Onice arancio|Ónice naranja
Onice avorio|Ónice marfil
Onice multicolore|Ónice multicolor
Onice nero venato|Ónice negro veteado
Intonaco rosso|Enlucido rojo
Intonaco blu|Enlucido azul
Intonaco verde|Enlucido verde
Intonaco giallo|Enlucido amarillo
Intonaco verde salvia|Enlucido verde salvia
Intonaco grigio chiaro|Enlucido gris claro
Stucco a spatola|Estuco a espátula
Stucco veneziano grigio|Estuco veneciano gris
Graniglia bianca e blu|Terrazo blanco y azul
Terrazzo antracite|Terrazo antracita
Terrazzo bianco e grigio|Terrazo blanco y gris
Terrazzo avorio|Terrazo marfil
Terrazzo azzurro multicolore|Terrazo celeste multicolor
Terrazzo sabbia|Terrazo arena
Terrazzo grigio multicolore|Terrazo gris multicolor
Terrazzo verde|Terrazo verde
Graniglia fine bianca|Terrazo fino blanco
Terrazzo beige|Terrazo beige
Piastrelle tortora 30×30|Baldosas topo 30×30
Gres bianco opaco|Gres blanco mate
Mosaico di vetro azzurro|Mosaico de vidrio celeste
Mosaico a bottoni pastello|Mosaico de botones pastel
Mattoncini rosa|Azulejos metro rosas
Mattoncini in marmo avorio|Azulejos metro de mármol marfil
Scacchiera marmo beige|Damero de mármol beige
Scacchiera marmo bianco e nero grande|Damero grande de mármol blanco y negro
Scacchiera marmo grigio scuro|Damero de mármol gris oscuro
Mosaico di vetro blu|Mosaico de vidrio azul
Piastrelle a griglia verde scuro|Baldosas en cuadrícula verde oscuro
Mosaico verde petrolio|Mosaico verde petróleo
Mosaico verde salvia|Mosaico verde salvia
Cotto arancio 20×20|Barro cocido naranja 20×20
Gres pesca grande formato|Gres melocotón gran formato
Mattoncini petrolio lucidi|Azulejos metro petróleo brillantes
Mattoncini blu polvere|Azulejos metro azul empolvado
Mattoncini verde smeraldo|Azulejos metro verde esmeralda
Mattoncini gialli|Azulejos metro amarillos
Zellige verde|Zellige verde
Piastrelle bianche 10×10 lucide|Azulejos blancos brillantes 10×10
Mattoncini rossi lucidi|Azulejos metro rojos brillantes
Lastre di cemento|Losas de hormigón
Graniglia arancio|Terrazo naranja
Graniglia verde|Terrazo verde en baldosas
Graniglia bianca|Terrazo blanco
Graniglia blu|Terrazo azul
Marmo azzurro a correre|Mármol celeste a junta corrida
Pietra beige a formati misti|Piedra beige de formatos mixtos
Pietra grigia a formati misti|Piedra gris de formatos mixtos
Mosaico di ardesia|Mosaico de pizarra
Mosaico verde marmorizzato|Mosaico verde marmolado
Marmo bianco con tozzetti neri|Mármol blanco con tacos negros
Marmo nero e bianco sfalsato|Mármol negro y blanco desplazado
Marmo nero con tozzetti bianchi|Mármol negro con tacos blancos
Losanghe verde, nero e grigio|Rombos verde, negro y gris
Esagonette cotto e beige a righe|Hexágonos barro y beige a rayas
Esagonette cotto e beige|Hexágonos barro y beige
Esagonette in cotto|Hexágonos de barro cocido
Esagonette azzurre e bianche|Hexágonos celestes y blancos
Esagonette celesti e petrolio|Hexágonos celestes y petróleo
Esagonette in pietra nera|Hexágonos de piedra negra
Piastrelle prugna 10×10|Azulejos ciruela 10×10
Marmo a onde verde e crema|Mármol en ondas verde y crema
Marmo a rombi verde e crema|Mármol en rombos verde y crema
Marmo a zig-zag verde e crema|Mármol en zigzag verde y crema
Lastricato in pietra vecchia|Enlosado de piedra vieja
Pietra lavica a opus|Piedra volcánica en opus
Cotto antico a quadri|Barro cocido antiguo cuadrado
Cotto a correre|Barro cocido a junta corrida
Azulejos blu fitti|Azulejos azules tupidos
Maioliche blu intense|Mayólicas azul intenso
Maioliche a decori blu|Mayólicas con decoración azul
Maioliche a stelle blu|Mayólicas con estrellas azules
Maioliche bianche con angoli blu|Mayólicas blancas con esquinas azules
Piastrelle nere lucide|Azulejos negros brillantes
Pietra graniglia beige|Piedra terrazo beige
Cotto in diagonale con fascia|Barro cocido en diagonal con cenefa
Bianco in diagonale con fascia blu|Blanco en diagonal con cenefa azul
Giallo in diagonale con fascia|Amarillo en diagonal con cenefa
Nero in diagonale con fascia bianca|Negro en diagonal con cenefa blanca
Lastre di pietra a spacco|Losas de piedra partida
Mosaico a bottoni azzurro|Mosaico de botones celeste
Mosaico a bottoni bianco e verde|Mosaico de botones blanco y verde
Mosaico a bottoni tortora|Mosaico de botones topo
Mosaico a bottoni bianco e blu|Mosaico de botones blanco y azul
Mosaico a bottoni ocra|Mosaico de botones ocre
Mosaico a bottoni grigio|Mosaico de botones gris
Mosaico a bottoni rosa e bordeaux|Mosaico de botones rosa y burdeos
Mosaico a bottoni bianco|Mosaico de botones blanco
Mosaico a bottoni nero|Mosaico de botones negro
Piastrelle bianche 10×10 opache|Azulejos blancos mate 10×10
Piastrelle blu notte|Azulejos azul noche
Mattoncini arancio|Azulejos metro naranja
Travertino venato|Travertino veteado
Travertino tortora|Travertino topo
Travertino oro|Travertino oro
Travertino silver|Travertino silver
Travertino giallo|Travertino amarillo
Quarzite grigia|Cuarcita gris
Listoni grigio sbiancato|Tablas gris blanqueado
Listoni mogano|Tablas de caoba
Listoni rovere fumé|Tablas de roble ahumado
Listoni frassino|Tablas de fresno
Listoni noce scuro|Tablas de nogal oscuro
Listoncini a correre|Tablillas a junta corrida
Listoni rovere sbiancato|Tablas de roble blanqueado
Listoni wengè|Tablas de wengué
Spina di pesce faggio|Espiga de haya
Spina di pesce mogano|Espiga de caoba
Spina di pesce noce|Espiga de nogal
Spina di pesce sbiancata|Espiga blanqueada
Spina di pesce scura|Espiga oscura
Parquet a cesto chiaro|Parquet de cestería claro
Parquet a cesto bicolore|Parquet de cestería bicolor
Parquet a cesto ciliegio|Parquet de cestería cerezo
Parquet a gradini bicolore|Parquet escalonado bicolor
Parquet intarsiato chiaro|Parquet taraceado claro
Parquet intarsiato scuro|Parquet taraceado oscuro
Parquet a dama|Parquet en damero
Parquet a cesto wengè|Parquet de cestería wengué
Parquet a cesto acero|Parquet de cestería arce
Industriale scuro|Parquet industrial oscuro
Parquet a cesto sbiancato|Parquet de cestería blanqueado
Spina di pesce vecchia grigia|Espiga vieja gris
Spina di pesce larice|Espiga de alerce
Industriale miele|Parquet industrial miel
Industriale chiaro|Parquet industrial claro
Industriale rovere|Parquet industrial de roble
Parquet Versailles|Parquet Versalles
Listoni noce lunghi|Tablas largas de nogal
Spina ungherese chiara|Espiga húngara clara
Parquet a intarsio con cornice|Parquet taraceado con marco
Listoncini rovere vissuto|Tablillas de roble envejecido
Spina di pesce in marmo fiammato|Espiga de mármol flameado
Mattoni a spina di pesce|Ladrillos en espiga
Laminato miele|Laminado miel
Laminato pino|Laminado pino
Linoleum nocciola|Linóleo avellana
Listelli bianchi verticali|Listones blancos verticales
Cementine con scaglie di marmo|Baldosas hidráulicas con lascas de mármol
Linoleum a ottagoni|Linóleo de octógonos
Cemento verniciato|Hormigón pintado
Cotto con tozzetti neri|Barro cocido con tacos negros
Parquet a mosaico rustico|Parquet mosaico rústico
Assi di pino nodose|Tablas de pino con nudos
Piastre in gomma nera|Losetas de caucho negro
Ardesia a opus|Pizarra en opus
Mosaico di pietra a disegni|Mosaico de piedra con dibujos
Cotto rosso|Barro cocido rojo
Stucco bianco|Estuco blanco
Tavolato rovere|Tablado de roble
Tavolato di pino vissuto|Tablado de pino envejecido
Assi grezze|Tablas en bruto
"""
}
