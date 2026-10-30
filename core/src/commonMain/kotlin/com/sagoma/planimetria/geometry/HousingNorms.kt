package com.sagoma.planimetria.geometry

import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.Place
import com.sagoma.planimetria.model.Places
import com.sagoma.planimetria.testi.T
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Norme della casa di un paese, o i consigli generali dove il paese non c'è: i numeri con cui «Controlla la pianta», la scheda della
 * stanza e il computo guardano luce e aria, altezze e metri quadri ([HousingRules]). Superfici in cm², altezze in cm; null = quella
 * regola nel paese non c'è (non si controlla). Le tabelle stanno nell'app ([countries]); lo stesso formato si potrà scaricare dal server
 * (JSON) per aggiungere o correggere un paese senza una versione nuova.
 */
@Serializable
data class HousingNorms(
    /** Codice del paese (ISO 3166, «GB» è l'Inghilterra); "" = consigli generali. */
    val country: String,
    /** La legge o la regola da cui vengono i numeri, come la si cita nel paese. */
    val source: String = "",
    /** Norme del paese; false = consigli generali (minimi comuni nel mondo, non una legge). */
    val official: Boolean = true,
    /** Finestre / pavimento nelle stanze da abitare (per ambiente); null = basta una finestra che dà fuori. */
    val windowRatio: Double? = null,
    /** Contano solo le finestre che si aprono (altrimenti anche quelle fisse: è la luce). */
    val openableOnly: Boolean = true,
    /** In più: parte che si apre / pavimento (l'aria), quando il paese la chiede a parte dalla luce. */
    val ventRatio: Double? = null,
    /** Altezza minima del soffitto nelle stanze da abitare e nei servizi (bagni, corridoi). */
    val livingHeightCm: Double? = null,
    val serviceHeightCm: Double? = null,
    /** Mansarda abitabile: altezza media minima (leggi regionali italiane sui sottotetti). */
    val atticAverageCm: Double? = null,
    /** Camera da letto per una persona e per due (null: come per una). */
    val bedroomCm2: Double? = null,
    val doubleBedroomCm2: Double? = null,
    /** Soggiorno (con la cucina a vista: tutto l'ambiente). */
    val livingCm2: Double? = null,
    /** Sanitari che almeno un bagno della casa deve avere. */
    val bathroom: List<HousingRules.Sanitary>? = null,
    /** Il bagno non si apre direttamente su cucina o soggiorno (antibagno). */
    val bathLobby: Boolean = false,
    /** Metri quadri per le persone che ci dormono e monolocale (14 m² a testa per le prime 4, 10 per le altre; 28/38 m²). */
    val perPerson: Boolean = false,
    /** Punti presa e luce dell'impianto elettrico di base (CEI 64-8). */
    val electric: Boolean = false,
) {
    /** Il minimo delle finestre come si scrive: «1/8», «1/20» (frazioni fino a 1/20), altrimenti in percento («8 %», «4 %»). */
    fun ratioLabel(ratio: Double): String {
        val n = 1 / ratio
        return if (n <= 20.01 && abs(n - n.roundToInt()) < 0.01) "1/${n.roundToInt()}" else "${formatDecimal(ratio * 100, 0)} %"
    }

    companion object {
        private val all = HousingRules.Sanitary.entries.toList()
        private val basic = listOf(HousingRules.Sanitary.Toilet, HousingRules.Sanitary.BathOrShower, HousingRules.Sanitary.Basin)

        /** Italia: DM 5 luglio 1975 (e le leggi regionali sui sottotetti, il regolamento d'igiene dei comuni, la CEI 64-8). */
        val Italy = HousingNorms(
            "IT", "DM 5 luglio 1975", windowRatio = HousingRules.MIN_RATIO, openableOnly = true,
            livingHeightCm = HousingRules.MIN_HEIGHT_LIVING_CM, serviceHeightCm = HousingRules.MIN_HEIGHT_SERVICE_CM,
            atticAverageCm = HousingRules.MIN_ATTIC_AVERAGE_CM, bedroomCm2 = HousingRules.MIN_BEDROOM_CM2,
            doubleBedroomCm2 = HousingRules.MIN_DOUBLE_BEDROOM_CM2, livingCm2 = HousingRules.MIN_LIVING_CM2,
            bathroom = all, bathLobby = true, perPerson = true, electric = true,
        )

        /**
         * Germania: Musterbauordnung § 47-48, ripresa dalle Landesbauordnungen: stanze da abitare alte almeno 2,40 m, finestre (vano
         * grezzo, anche fisse) di almeno 1/8 del pavimento, un bagno con vasca o doccia e un WC. Niente minimi di superficie.
         */
        val Germany = HousingNorms(
            "DE", "Musterbauordnung § 47–48", windowRatio = 1.0 / 8, openableOnly = false, livingHeightCm = 240.0,
            bathroom = listOf(HousingRules.Sanitary.Toilet, HousingRules.Sanitary.BathOrShower),
        )

        /**
         * Francia: decreto 2002-120 sull'alloggio decente e regolamenti sanitari dei dipartimenti: stanze principali con una finestra
         * apribile verso fuori, soffitto ad almeno 2,20 m, una stanza principale di almeno 9 m² e le altre di 7, WC e vasca o doccia.
         */
        val France = HousingNorms(
            "FR", "Décret 2002-120 et RSD", windowRatio = null, livingHeightCm = 220.0, bedroomCm2 = 70_000.0, livingCm2 = 90_000.0,
            bathroom = listOf(HousingRules.Sanitary.Toilet, HousingRules.Sanitary.BathOrShower),
        )

        /**
         * Spagna: Orden del 29 febbraio 1944 (minimi nazionali; le comunità autonome hanno regole loro): finestra di almeno 1/6 del
         * pavimento in ogni stanza da abitare, soffitti a 2,50 m, camera a un letto 6 m², a due letti 10 m², soggiorno 10 m², un WC.
         */
        val Spain = HousingNorms(
            "ES", "Orden de 29 de febrero de 1944", windowRatio = 1.0 / 6, openableOnly = true, livingHeightCm = 250.0,
            serviceHeightCm = 250.0, bedroomCm2 = 60_000.0, doubleBedroomCm2 = 100_000.0, livingCm2 = 100_000.0,
            bathroom = listOf(HousingRules.Sanitary.Toilet),
        )

        /**
         * Inghilterra: Approved Document F (finestre apribili per l'aria di almeno 1/20 del pavimento) e Nationally Described Space
         * Standard (soffitti a 2,30 m, camera singola 7,5 m², doppia 11,5 m²), Approved Document G (WC, lavabo, vasca o doccia).
         */
        val England = HousingNorms(
            "GB", "Approved Document F, NDSS", windowRatio = 1.0 / 20, openableOnly = true, livingHeightCm = 230.0,
            serviceHeightCm = 230.0, bedroomCm2 = 75_000.0, doubleBedroomCm2 = 115_000.0, bathroom = basic,
        )

        /**
         * Stati Uniti: International Residential Code (R303–R306), adottato da quasi tutti gli stati: vetri di almeno l'8 % del
         * pavimento e parte apribile del 4 %, stanze da abitare (non la cucina) di almeno 70 piedi² (6,5 m²), soffitti a 7 piedi
         * (2,13 m; 6 piedi e 8 pollici nei bagni), WC, lavabo e vasca o doccia.
         */
        val UnitedStates = HousingNorms(
            "US", "International Residential Code", windowRatio = 0.08, openableOnly = false, ventRatio = 0.04,
            livingHeightCm = 213.0, serviceHeightCm = 203.0, bedroomCm2 = 65_000.0, livingCm2 = 65_000.0, bathroom = basic,
        )

        /**
         * Consigli generali per i paesi che non sono nella tabella: i minimi più comuni nel mondo (finestre di 1/10 del pavimento,
         * soffitti a 2,40 m, 2,20 nei servizi, camera singola 7 m² e doppia 11), da controllare con le regole del posto.
         */
        val General = HousingNorms(
            "", official = false, windowRatio = 1.0 / 10, openableOnly = false, livingHeightCm = 240.0, serviceHeightCm = 220.0,
            bedroomCm2 = 70_000.0, doubleBedroomCm2 = 110_000.0, bathroom = basic,
        )

        /** Le norme che ci sono, per codice del paese. */
        val countries: Map<String, HousingNorms> = listOf(Italy, Germany, France, Spain, England, UnitedStates).associateBy { it.country }

        /** Paese delle norme quando la casa non ha una città: dalla lingua dell'app (l'inglese è di troppi paesi). */
        private val byLanguage = mapOf("it" to "IT", "de" to "DE", "fr" to "FR", "es" to "ES")

        /**
         * Le norme della casa: del paese della sua città ([Building.site]); senza città, quelle della lingua dell'app; i consigli
         * generali se il paese non è nella tabella.
         */
        fun of(building: Building, language: String = T.lingua.codice): HousingNorms =
            forCountry(country(building.site.place) ?: byLanguage[language])

        fun forCountry(code: String?): HousingNorms = code?.let { countries[it] } ?: General

        /**
         * Paese della città scelta per la casa (codice ISO), null senza città. I capoluoghi italiani sono in Italia (San Marino e il
         * Vaticano no); Edimburgo è in Scozia, che ha regole sue.
         */
        fun country(place: Place?): String? {
            if (place == null) return null
            worldCountries[place.name]?.let { return it }
            return if (Places.italy.any { it.place.name == place.name }) "IT" else null
        }

        private val worldCountries: Map<String, String> = mapOf(
            "San Marino" to "SM", "Città del Vaticano" to "VA",
            "Amsterdam" to "NL", "Atene" to "GR", "Barcellona" to "ES", "Belgrado" to "RS", "Berlino" to "DE", "Berna" to "CH",
            "Bratislava" to "SK", "Bruxelles" to "BE", "Bucarest" to "RO", "Budapest" to "HU", "Copenaghen" to "DK", "Dublino" to "IE",
            "Edimburgo" to "GB-SCT", "Francoforte" to "DE", "Ginevra" to "CH", "Helsinki" to "FI", "Lione" to "FR", "Lisbona" to "PT",
            "Londra" to "GB", "Lubiana" to "SI", "Lugano" to "CH", "Lussemburgo" to "LU", "Madrid" to "ES", "Malta" to "MT",
            "Marsiglia" to "FR", "Monaco di Baviera" to "DE", "Nizza" to "FR", "Oslo" to "NO", "Parigi" to "FR", "Porto" to "PT",
            "Praga" to "CZ", "Siviglia" to "ES", "Spalato" to "HR", "Stoccolma" to "SE", "Tirana" to "AL", "Valencia" to "ES",
            "Varsavia" to "PL", "Vienna" to "AT", "Zagabria" to "HR", "Zurigo" to "CH", "Istanbul" to "TR", "Mosca" to "RU",
            "Il Cairo" to "EG", "Casablanca" to "MA", "Tunisi" to "TN", "Tel Aviv" to "IL", "Dubai" to "AE", "New York" to "US",
            "Boston" to "US", "Washington" to "US", "Miami" to "US", "Chicago" to "US", "Los Angeles" to "US", "San Francisco" to "US",
            "Toronto" to "CA", "Montréal" to "CA", "Città del Messico" to "MX", "Buenos Aires" to "AR", "San Paolo" to "BR",
            "Rio de Janeiro" to "BR", "Santiago del Cile" to "CL", "Città del Capo" to "ZA", "Sydney" to "AU", "Melbourne" to "AU",
            "Auckland" to "NZ", "Tokyo" to "JP", "Pechino" to "CN", "Singapore" to "SG", "Mumbai" to "IN",
        )
    }
}
