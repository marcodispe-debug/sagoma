package com.sagoma.planimetria.model

/**
 * Città che si possono scegliere per la casa (posizione approssimata: basta per il sole). Tutti i capoluoghi di
 * provincia italiani e le principali città estere, sempre disponibili anche senza internet. [aliases] sono altri nomi
 * con cui cercarle (in inglese, francese, spagnolo, tedesco o nella lingua del posto), non si salvano nel progetto.
 */
object Places {
    class Entry(val place: Place, val aliases: String = "")

    /** Capoluogo italiano (fuso di Roma, ora legale europea). */
    private fun p(name: String, lat: Double, lon: Double, aliases: String = "") =
        Entry(Place(name, lat, lon, 1.0, DaylightSaving.Europe), aliases)

    /** Città estera: fuso in ore rispetto a UTC (senza ora legale) e regola dell'ora legale. */
    private fun w(name: String, lat: Double, lon: Double, utc: Double, dst: DaylightSaving, aliases: String = "") =
        Entry(Place(name, lat, lon, utc, dst), aliases)

    val italy: List<Entry> = listOf(
        p("Agrigento", 37.31, 13.58),
        p("Alessandria", 44.91, 8.61),
        p("Ancona", 43.62, 13.52),
        p("Aosta", 45.74, 7.32),
        p("Arezzo", 43.46, 11.88),
        p("Ascoli Piceno", 42.85, 13.58),
        p("Asti", 44.90, 8.21),
        p("Avellino", 40.91, 14.79),
        p("Bari", 41.12, 16.87),
        p("Barletta", 41.32, 16.28),
        p("Belluno", 46.14, 12.22),
        p("Benevento", 41.13, 14.78),
        p("Bergamo", 45.70, 9.67),
        p("Biella", 45.56, 8.05),
        p("Bologna", 44.49, 11.34),
        p("Bolzano", 46.50, 11.35, "Bozen"),
        p("Brescia", 45.54, 10.22),
        p("Brindisi", 40.64, 17.94),
        p("Cagliari", 39.22, 9.11),
        p("Caltanissetta", 37.49, 14.06),
        p("Campobasso", 41.56, 14.66),
        p("Caserta", 41.07, 14.33),
        p("Catania", 37.50, 15.09),
        p("Catanzaro", 38.91, 16.59),
        p("Chieti", 42.35, 14.17),
        p("Como", 45.81, 9.09),
        p("Cosenza", 39.30, 16.25),
        p("Cremona", 45.13, 10.02),
        p("Crotone", 39.08, 17.13),
        p("Cuneo", 44.39, 7.55),
        p("Enna", 37.57, 14.28),
        p("Fermo", 43.16, 13.72),
        p("Ferrara", 44.84, 11.62),
        p("Firenze", 43.77, 11.26, "Florence Florencia"),
        p("Foggia", 41.46, 15.55),
        p("Forlì", 44.22, 12.04),
        p("Frosinone", 41.64, 13.35),
        p("Genova", 44.41, 8.93, "Genoa Gênes Génova"),
        p("Gorizia", 45.94, 13.62),
        p("Grosseto", 42.76, 11.11),
        p("Imperia", 43.89, 8.03),
        p("Isernia", 41.59, 14.23),
        p("L'Aquila", 42.35, 13.40),
        p("La Spezia", 44.10, 9.82),
        p("Latina", 41.47, 12.90),
        p("Lecce", 40.35, 18.17),
        p("Lecco", 45.86, 9.40),
        p("Livorno", 43.55, 10.31),
        p("Lodi", 45.31, 9.50),
        p("Lucca", 43.84, 10.50),
        p("Macerata", 43.30, 13.45),
        p("Mantova", 45.16, 10.79, "Mantua"),
        p("Massa", 44.04, 10.14),
        p("Matera", 40.67, 16.60),
        p("Messina", 38.19, 15.55),
        p("Milano", 45.46, 9.19, "Milan Milán"),
        p("Modena", 44.65, 10.93),
        p("Monza", 45.58, 9.27),
        p("Napoli", 40.85, 14.27, "Naples Nápoles"),
        p("Novara", 45.45, 8.62),
        p("Nuoro", 40.32, 9.33),
        p("Oristano", 39.90, 8.59),
        p("Padova", 45.41, 11.88, "Padua"),
        p("Palermo", 38.12, 13.36),
        p("Parma", 44.80, 10.33),
        p("Pavia", 45.19, 9.16),
        p("Perugia", 43.11, 12.39),
        p("Pesaro", 43.91, 12.91),
        p("Pescara", 42.46, 14.21),
        p("Piacenza", 45.05, 9.69),
        p("Pisa", 43.72, 10.40),
        p("Pistoia", 43.93, 10.91),
        p("Pordenone", 45.96, 12.66),
        p("Potenza", 40.64, 15.80),
        p("Prato", 43.88, 11.10),
        p("Ragusa", 36.93, 14.73),
        p("Ravenna", 44.42, 12.20),
        p("Reggio Calabria", 38.11, 15.65),
        p("Reggio Emilia", 44.70, 10.63),
        p("Rieti", 42.40, 12.86),
        p("Rimini", 44.06, 12.57),
        p("Roma", 41.90, 12.50, "Rome"),
        p("Rovigo", 45.07, 11.79),
        p("Salerno", 40.68, 14.77),
        p("Sassari", 40.73, 8.56),
        p("Savona", 44.31, 8.48),
        p("Siena", 43.32, 11.33),
        p("Siracusa", 37.08, 15.29, "Syracuse"),
        p("Sondrio", 46.17, 9.87),
        p("Taranto", 40.47, 17.24),
        p("Teramo", 42.66, 13.70),
        p("Terni", 42.56, 12.65),
        p("Torino", 45.07, 7.69, "Turin"),
        p("Trapani", 38.02, 12.51),
        p("Trento", 46.07, 11.12),
        p("Treviso", 45.67, 12.24),
        p("Trieste", 45.65, 13.78, "Triest"),
        p("Udine", 46.06, 13.24),
        p("Varese", 45.82, 8.83),
        p("Venezia", 45.44, 12.33, "Venice Venise Venecia"),
        p("Verbania", 45.92, 8.55),
        p("Vercelli", 45.32, 8.42),
        p("Verona", 45.44, 10.99),
        p("Vibo Valentia", 38.68, 16.10),
        p("Vicenza", 45.55, 11.55),
        p("Viterbo", 42.42, 12.11),
        p("San Marino", 43.94, 12.45),
        p("Città del Vaticano", 41.90, 12.45, "Vatican"),
    )

    val world: List<Entry> = listOf(
        w("Amsterdam", 52.37, 4.90, 1.0, DaylightSaving.Europe),
        w("Atene", 37.98, 23.73, 2.0, DaylightSaving.Europe, aliases = "Athens"),
        w("Barcellona", 41.39, 2.17, 1.0, DaylightSaving.Europe, aliases = "Barcelona"),
        w("Belgrado", 44.79, 20.45, 1.0, DaylightSaving.Europe, aliases = "Belgrade Beograd"),
        w("Berlino", 52.52, 13.40, 1.0, DaylightSaving.Europe, aliases = "Berlin"),
        w("Berna", 46.95, 7.45, 1.0, DaylightSaving.Europe, aliases = "Bern Berne"),
        w("Bratislava", 48.15, 17.11, 1.0, DaylightSaving.Europe),
        w("Bruxelles", 50.85, 4.35, 1.0, DaylightSaving.Europe, aliases = "Brussels Brussel"),
        w("Bucarest", 44.43, 26.10, 2.0, DaylightSaving.Europe, aliases = "Bucharest"),
        w("Budapest", 47.50, 19.04, 1.0, DaylightSaving.Europe),
        w("Copenaghen", 55.68, 12.57, 1.0, DaylightSaving.Europe, aliases = "Copenhagen København"),
        w("Dublino", 53.35, -6.26, 0.0, DaylightSaving.Europe, aliases = "Dublin"),
        w("Edimburgo", 55.95, -3.19, 0.0, DaylightSaving.Europe, aliases = "Edinburgh"),
        w("Francoforte", 50.11, 8.68, 1.0, DaylightSaving.Europe, aliases = "Frankfurt"),
        w("Ginevra", 46.20, 6.14, 1.0, DaylightSaving.Europe, aliases = "Geneva Genève"),
        w("Helsinki", 60.17, 24.94, 2.0, DaylightSaving.Europe),
        w("Lione", 45.76, 4.84, 1.0, DaylightSaving.Europe, aliases = "Lyon"),
        w("Lisbona", 38.72, -9.14, 0.0, DaylightSaving.Europe, aliases = "Lisbon Lisboa"),
        w("Londra", 51.51, -0.13, 0.0, DaylightSaving.Europe, aliases = "London Londres"),
        w("Lubiana", 46.06, 14.51, 1.0, DaylightSaving.Europe, aliases = "Ljubljana"),
        w("Lugano", 46.00, 8.95, 1.0, DaylightSaving.Europe),
        w("Lussemburgo", 49.61, 6.13, 1.0, DaylightSaving.Europe, aliases = "Luxembourg"),
        w("Madrid", 40.42, -3.70, 1.0, DaylightSaving.Europe),
        w("Malta", 35.90, 14.51, 1.0, DaylightSaving.Europe, aliases = "Valletta"),
        w("Marsiglia", 43.30, 5.37, 1.0, DaylightSaving.Europe, aliases = "Marseille"),
        w("Monaco di Baviera", 48.14, 11.58, 1.0, DaylightSaving.Europe, aliases = "Munich München"),
        w("Nizza", 43.70, 7.27, 1.0, DaylightSaving.Europe, aliases = "Nice"),
        w("Oslo", 59.91, 10.75, 1.0, DaylightSaving.Europe),
        w("Parigi", 48.86, 2.35, 1.0, DaylightSaving.Europe, aliases = "Paris"),
        w("Porto", 41.15, -8.61, 0.0, DaylightSaving.Europe),
        w("Praga", 50.08, 14.44, 1.0, DaylightSaving.Europe, aliases = "Prague Praha"),
        w("Siviglia", 37.39, -5.98, 1.0, DaylightSaving.Europe, aliases = "Seville Sevilla"),
        w("Spalato", 43.51, 16.44, 1.0, DaylightSaving.Europe, aliases = "Split"),
        w("Stoccolma", 59.33, 18.07, 1.0, DaylightSaving.Europe, aliases = "Stockholm"),
        w("Tirana", 41.33, 19.82, 1.0, DaylightSaving.Europe),
        w("Valencia", 39.47, -0.38, 1.0, DaylightSaving.Europe),
        w("Varsavia", 52.23, 21.01, 1.0, DaylightSaving.Europe, aliases = "Warsaw Warszawa"),
        w("Vienna", 48.21, 16.37, 1.0, DaylightSaving.Europe, aliases = "Wien"),
        w("Zagabria", 45.82, 15.98, 1.0, DaylightSaving.Europe, aliases = "Zagreb"),
        w("Zurigo", 47.38, 8.54, 1.0, DaylightSaving.Europe, aliases = "Zurich Zürich"),
        w("Istanbul", 41.01, 28.98, 3.0, DaylightSaving.None),
        w("Mosca", 55.76, 37.62, 3.0, DaylightSaving.None, aliases = "Moscow"),
        w("Il Cairo", 30.04, 31.24, 2.0, DaylightSaving.None, aliases = "Cairo"),
        w("Casablanca", 33.57, -7.59, 1.0, DaylightSaving.None),
        w("Tunisi", 36.81, 10.18, 1.0, DaylightSaving.None, aliases = "Tunis"),
        w("Tel Aviv", 32.09, 34.78, 2.0, DaylightSaving.None),
        w("Dubai", 25.20, 55.27, 4.0, DaylightSaving.None),
        w("New York", 40.71, -74.01, -5.0, DaylightSaving.NorthAmerica),
        w("Boston", 42.36, -71.06, -5.0, DaylightSaving.NorthAmerica),
        w("Washington", 38.91, -77.04, -5.0, DaylightSaving.NorthAmerica),
        w("Miami", 25.76, -80.19, -5.0, DaylightSaving.NorthAmerica),
        w("Chicago", 41.88, -87.63, -6.0, DaylightSaving.NorthAmerica),
        w("Los Angeles", 34.05, -118.24, -8.0, DaylightSaving.NorthAmerica),
        w("San Francisco", 37.77, -122.42, -8.0, DaylightSaving.NorthAmerica),
        w("Toronto", 43.65, -79.38, -5.0, DaylightSaving.NorthAmerica),
        w("Montréal", 45.50, -73.57, -5.0, DaylightSaving.NorthAmerica),
        w("Città del Messico", 19.43, -99.13, -6.0, DaylightSaving.None, aliases = "Mexico City Ciudad de México"),
        w("Buenos Aires", -34.60, -58.38, -3.0, DaylightSaving.None),
        w("San Paolo", -23.55, -46.63, -3.0, DaylightSaving.None, aliases = "São Paulo Sao Paulo"),
        w("Rio de Janeiro", -22.91, -43.17, -3.0, DaylightSaving.None),
        w("Santiago del Cile", -33.45, -70.67, -4.0, DaylightSaving.None, aliases = "Santiago"),
        w("Città del Capo", -33.92, 18.42, 2.0, DaylightSaving.None, aliases = "Cape Town"),
        w("Sydney", -33.87, 151.21, 10.0, DaylightSaving.South),
        w("Melbourne", -37.81, 144.96, 10.0, DaylightSaving.South),
        w("Auckland", -36.85, 174.76, 12.0, DaylightSaving.South),
        w("Tokyo", 35.68, 139.69, 9.0, DaylightSaving.None),
        w("Pechino", 39.90, 116.40, 8.0, DaylightSaving.None, aliases = "Beijing Pékin"),
        w("Singapore", 1.35, 103.82, 8.0, DaylightSaving.None),
        w("Mumbai", 19.08, 72.88, 5.5, DaylightSaving.None),
    )

    /**
     * Nomi in inglese, francese, spagnolo e tedesco ("en|fr|es|de") delle città che non si chiamano come in italiano. Nel progetto
     * si salva sempre il nome italiano; questi servono solo per mostrarle nella lingua scelta.
     */
    internal val translated: Map<String, String> = mapOf(
        "Aosta" to "Aosta|Aoste|Aosta|Aosta",
        "Bergamo" to "Bergamo|Bergame|Bérgamo|Bergamo",
        "Bologna" to "Bologna|Bologne|Bolonia|Bologna",
        "Catania" to "Catania|Catane|Catania|Catania",
        "Bolzano" to "Bolzano|Bolzano|Bolzano|Bozen",
        "Città del Vaticano" to "Vatican City|Cité du Vatican|Ciudad del Vaticano|Vatikanstadt",
        "Ferrara" to "Ferrara|Ferrare|Ferrara|Ferrara",
        "Firenze" to "Florence|Florence|Florencia|Florenz",
        "Genova" to "Genoa|Gênes|Génova|Genua",
        "Livorno" to "Livorno|Livourne|Livorno|Livorno",
        "Lucca" to "Lucca|Lucques|Luca|Lucca",
        "Mantova" to "Mantua|Mantoue|Mantua|Mantua",
        "Messina" to "Messina|Messine|Mesina|Messina",
        "Milano" to "Milan|Milan|Milán|Mailand",
        "Modena" to "Modena|Modène|Módena|Modena",
        "Napoli" to "Naples|Naples|Nápoles|Neapel",
        "Padova" to "Padua|Padoue|Padua|Padua",
        "Palermo" to "Palermo|Palerme|Palermo|Palermo",
        "Parma" to "Parma|Parme|Parma|Parma",
        "Pavia" to "Pavia|Pavie|Pavía|Pavia",
        "Perugia" to "Perugia|Pérouse|Perusa|Perugia",
        "Pisa" to "Pisa|Pise|Pisa|Pisa",
        "Ravenna" to "Ravenna|Ravenne|Rávena|Ravenna",
        "Reggio Calabria" to "Reggio Calabria|Reggio de Calabre|Regio de Calabria|Reggio Calabria",
        "Roma" to "Rome|Rome|Roma|Rom",
        "San Marino" to "San Marino|Saint-Marin|San Marino|San Marino",
        "Siena" to "Siena|Sienne|Siena|Siena",
        "Siracusa" to "Syracuse|Syracuse|Siracusa|Syrakus",
        "Trieste" to "Trieste|Trieste|Trieste|Triest",
        "Torino" to "Turin|Turin|Turín|Turin",
        "Trento" to "Trento|Trente|Trento|Trient",
        "Venezia" to "Venice|Venise|Venecia|Venedig",
        "Verona" to "Verona|Vérone|Verona|Verona",
        "Vicenza" to "Vicenza|Vicence|Vicenza|Vicenza",
        "Amsterdam" to "Amsterdam|Amsterdam|Ámsterdam|Amsterdam",
        "Atene" to "Athens|Athènes|Atenas|Athen",
        "Barcellona" to "Barcelona|Barcelone|Barcelona|Barcelona",
        "Belgrado" to "Belgrade|Belgrade|Belgrado|Belgrad",
        "Berlino" to "Berlin|Berlin|Berlín|Berlin",
        "Berna" to "Bern|Berne|Berna|Bern",
        "Bruxelles" to "Brussels|Bruxelles|Bruselas|Brüssel",
        "Bucarest" to "Bucharest|Bucarest|Bucarest|Bukarest",
        "Copenaghen" to "Copenhagen|Copenhague|Copenhague|Kopenhagen",
        "Dublino" to "Dublin|Dublin|Dublín|Dublin",
        "Edimburgo" to "Edinburgh|Édimbourg|Edimburgo|Edinburgh",
        "Francoforte" to "Frankfurt|Francfort|Fráncfort|Frankfurt",
        "Ginevra" to "Geneva|Genève|Ginebra|Genf",
        "Lione" to "Lyon|Lyon|Lyon|Lyon",
        "Lisbona" to "Lisbon|Lisbonne|Lisboa|Lissabon",
        "Londra" to "London|Londres|Londres|London",
        "Lubiana" to "Ljubljana|Ljubljana|Liubliana|Ljubljana",
        "Lussemburgo" to "Luxembourg|Luxembourg|Luxemburgo|Luxemburg",
        "Malta" to "Malta|Malte|Malta|Malta",
        "Marsiglia" to "Marseille|Marseille|Marsella|Marseille",
        "Monaco di Baviera" to "Munich|Munich|Múnich|München",
        "Nizza" to "Nice|Nice|Niza|Nizza",
        "Parigi" to "Paris|Paris|París|Paris",
        "Porto" to "Porto|Porto|Oporto|Porto",
        "Praga" to "Prague|Prague|Praga|Prag",
        "Siviglia" to "Seville|Séville|Sevilla|Sevilla",
        "Spalato" to "Split|Split|Split|Split",
        "Stoccolma" to "Stockholm|Stockholm|Estocolmo|Stockholm",
        "Valencia" to "Valencia|Valence|Valencia|Valencia",
        "Varsavia" to "Warsaw|Varsovie|Varsovia|Warschau",
        "Vienna" to "Vienna|Vienne|Viena|Wien",
        "Zagabria" to "Zagreb|Zagreb|Zagreb|Zagreb",
        "Zurigo" to "Zurich|Zurich|Zúrich|Zürich",
        "Istanbul" to "Istanbul|Istanbul|Estambul|Istanbul",
        "Mosca" to "Moscow|Moscou|Moscú|Moskau",
        "Il Cairo" to "Cairo|Le Caire|El Cairo|Kairo",
        "Tunisi" to "Tunis|Tunis|Túnez|Tunis",
        "Dubai" to "Dubai|Dubaï|Dubái|Dubai",
        "New York" to "New York|New York|Nueva York|New York",
        "Montréal" to "Montreal|Montréal|Montreal|Montreal",
        "Città del Messico" to "Mexico City|Mexico|Ciudad de México|Mexiko-Stadt",
        "San Paolo" to "São Paulo|São Paulo|São Paulo|São Paulo",
        "Santiago del Cile" to "Santiago|Santiago du Chili|Santiago de Chile|Santiago de Chile",
        "Città del Capo" to "Cape Town|Le Cap|Ciudad del Cabo|Kapstadt",
        "Tokyo" to "Tokyo|Tokyo|Tokio|Tokio",
        "Pechino" to "Beijing|Pékin|Pekín|Peking",
        "Singapore" to "Singapore|Singapour|Singapur|Singapur",
        "Mumbai" to "Mumbai|Bombay|Bombay|Mumbai",
    )

    private val languages = listOf("en", "fr", "es", "de")

    /** Nome della città nella lingua [lang] ("it", "en", "fr", "es", "de"); se non c'è, il nome italiano salvato. */
    fun displayName(place: Place, lang: String): String {
        val i = languages.indexOf(lang)
        if (i < 0) return place.name
        return translated[place.name]?.split('|')?.getOrNull(i) ?: place.name
    }

    val all: List<Entry> = (italy + world).sortedBy { it.place.name.lowercase() }

    /**
     * Città il cui nome (in una qualsiasi lingua, o un altro nome) contiene [query], senza badare a maiuscole e accenti,
     * in ordine alfabetico nella lingua [lang]; prima quelle il cui nome in quella lingua inizia così.
     */
    fun search(query: String, lang: String = "it"): List<Place> {
        val q = fold(query.trim())
        val shown = all.sortedBy { fold(displayName(it.place, lang)) }
        if (q.isEmpty()) return shown.map { it.place }
        val found = shown.filter { e -> fold(e.place.name + " " + e.aliases + " " + translated[e.place.name].orEmpty().replace('|', ' ')).contains(q) }
        return found.sortedBy { e -> if (fold(displayName(e.place, lang)).startsWith(q)) 0 else 1 }.map { it.place }
    }

    private fun fold(s: String): String = buildString {
        for (c in s.lowercase()) append(
            when (c) {
                'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a'
                'è', 'é', 'ê', 'ë' -> 'e'
                'ì', 'í', 'î', 'ï' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö' -> 'o'
                'ù', 'ú', 'û', 'ü' -> 'u'
                'ç' -> 'c'
                'ñ' -> 'n'
                '\'', '’' -> ' '
                else -> c
            },
        )
    }
}
