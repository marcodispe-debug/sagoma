package com.sagoma.planimetria.testi.es

import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiComputo

object TestiComputoEs : TestiComputo() {
    override val progetto: String get() = "Proyecto"
    override val computoEsportato: String get() = "Mediciones exportadas"
    override val esportazioneNonRiuscita: String get() = "No se pudo exportar"
    override val computoListaArredi: String get() = "Mediciones y lista de muebles"
    override val nonSonoAncoraStanze: String get() = "Todavía no hay habitaciones."
    override val stanze: String get() = "Habitaciones"
    override val stanza: String get() = "Habitación"
    override val muriSingoli: String get() = "Paredes sueltas"
    override val soffitti: String get() = "Techos"
    override val statoDiProgetto: String get() = "Cuenta el estado de proyecto: sin los muros a demoler ni las aberturas a cerrar."
    override val pavM2: String get() = "Suelo m²"
    override val paretiM2: String get() = "Paredes m²"
    override val battiscM: String get() = "Rodapié m"
    override val pavimento: String get() = "Suelo"
    override val totale: String get() = "Total"
    override val paretiSuperficieInternaNetto: String get() = "Paredes: superficie interior descontando puertas y ventanas. Rodapié: perímetro interior menos puertas y balcones."
    override val togliZoneNonCalpestabili: String get() = "Suelo sin las zonas no transitables"
    override val pavimentiOrdinare: String get() = "Suelos a pedir"
    override val finiturePareti: String get() = "Acabados de las paredes"
    override val arredi: String get() = "Muebles"
    override val nessunArredo: String get() = "Ningún mueble."
    override val impianti: String get() = "Instalaciones"
    override fun computo(title: Any?): String = "Mediciones – $title"
    override val condividi: String get() = "Compartir"
    override val esportaCsv: String get() = "Exportar CSV"
    override val definire: String get() = "Por definir"
    override fun pitturaColore(n: Any?): String = "Pintura color $n"
    override fun pittura(p1: Any?): String = "Pintura, $p1"
    override val rivestimento: String get() = "Revestimiento"
    override fun finoCm(cover: Any?, p2: Any?): String = "$cover hasta $p2 cm"
    override val arredo: String get() = "Mueble"
    override fun cm(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3 cm"
    override val stanze2: String get() = "HABITACIONES"
    override val pianoStanzaPavimentoM2: String get() = "Planta;Habitación;Superficie útil m²;Perímetro m;Paredes netas m²;Huecos m²;Rodapié m;Suelo;Paredes;Puertas;Ventanas"
    override fun totale2(p1: Any?, p2: Any?, p3: Any?): String = "Total;;$p1;;$p2;;$p3;;;;"
    override val pavimentiM2M210: String get() = "SUELOS;m²;m² con 10 % de desperdicio"
    override val paretiM22: String get() = "PAREDES;m²"
    override val arrediMisureQuantitaDove: String get() = "MUEBLES;Medidas;Cantidad;Dónde"
    override val luceAria: String get() = "Luz y aire (ventanas / suelo)"
    override val finM2: String get() = "Vent. m²"
    override val rapporto: String get() = "Relación"
    override fun almeno(n: HousingNorms): String = n.windowRatio?.let { "≥ ${n.ratioLabel(it)}" } ?: "Basta"
    override val si: String get() = "sí"
    override val no: String get() = "no"
    override fun notaLuce(n: HousingNorms): String {
        val ratio = n.windowRatio
        return "Estancias habitables. Ventanas ${if (n.openableOnly) "practicables " else ""}al exterior, medidas en el hueco de la pared. " + when {
            ratio == null -> "${T.controllo.paese(n.country)} (${n.source}): basta una ventana practicable al exterior."
            !n.official -> "Consejo (la app no tiene las normas del país de la vivienda): al menos ${n.ratioLabel(ratio)} del suelo."
            else -> "${T.controllo.paese(n.country)} (${n.source}): se necesita al menos ${n.ratioLabel(ratio)} del suelo" +
                (n.ventRatio?.let { ", y la parte practicable al menos ${n.ratioLabel(it)}" } ?: "") + "."
        }
    }
    override fun notaLucePdf(n: HousingNorms): String {
        val ratio = n.windowRatio
        return "Ventanas ${if (n.openableOnly) "practicables " else ""}al exterior, en el hueco; " + when {
            ratio == null -> "al menos una (${T.controllo.paese(n.country)})."
            !n.official -> "consejo: al menos ${n.ratioLabel(ratio)}."
            else -> "al menos ${n.ratioLabel(ratio)}${n.ventRatio?.let { ", practicables ${n.ratioLabel(it)}" } ?: ""} (${T.controllo.paese(n.country)})."
        }
    }
    override val opere: String get() = "Obras (demoliciones y construcciones)"
    override val muriDaDemolire: String get() = "Muros a demoler"
    override val muriNuovi: String get() = "Muros nuevos"
    override val voce: String get() = "Partida"
    override val numero: String get() = "N.º"
    override val pianoVoceNumeroM2M3: String get() = "Planta;Partida;N.º;m²;m³"
    override val vaniDaAprire: String get() = "Huecos a abrir en muros existentes"
    override val apertureDaChiudere: String get() = "Aberturas a cerrar"
    override val notaOpere: String get() = "Muros: una cara, a cinta corrida menos puertas y ventanas, altos como la pared; m³ con el espesor. Un muro compartido cuenta una vez. Indicativo: comprobar en obra."
    override val notaOperePdf: String get() = "Muros: una cara menos puertas y ventanas; m³ con el espesor. Indicativo."
    override val opere2: String get() = "OBRAS"
    override val superfici: String get() = "Superficies"
    override val piano: String get() = "Planta"
    override val utileM2: String get() = "Útil m²"
    override val lordaM2: String get() = "Constr. m²"
    override val balconiM2: String get() = "Balc. m²"
    override val commerciale: String get() = "Superficie comercial (orientativa)"
    override val notaSuperfici: String get() =
        "Útil: el suelo de las habitaciones. Construida: con las paredes. Comercial (DPR italiano 138/1998): paredes exteriores hasta 50 cm (las " +
            "medianeras con otras viviendas contarían la mitad), sin las zonas no transitables, sótanos al 50 %, balcones y terrazas al 30 % hasta 25 m² y al 10 % a partir de ahí."
    override val notaSuperficiPdf: String get() = "DPR 138/1998 (Italia): paredes ext. ≤ 50 cm, sótanos 50 %, balcones 30 % (10 % > 25 m²)."
    override val superfici2: String get() = "SUPERFICIES"
    override val pianoSuperfici: String get() = "Planta;Útil m²;Construida m²;Balcones y terrazas m²"
    override val commercialeCsv: String get() = "Superficie comercial orientativa"
    override val luceAria2: String get() = "LUZ Y AIRE"
    override fun pianoStanzaLuce(n: HousingNorms): String =
        "Planta;Habitación;Superficie útil m²;${if (n.openableOnly) "Ventanas practicables m²" else "Ventanas m²"};Relación;${n.windowRatio?.let { "Al menos ${n.ratioLabel(it)}" } ?: "Basta"}"
    override val impiantiQuantitaDove: String get() = "INSTALACIONES;Cantidad;Dónde"
    override val porteFinestre: String get() = "Puertas y ventanas"
    override val porteFinestreNota: String get() = "Medidas del hueco (ancho × alto), para que las compruebe quien haga el presupuesto. Los huecos que se cierran no aparecen."
    override fun misuraVano(w: Any?, h: Any?): String = "$w×$h cm"
    override val porteFinestreCsv: String get() = "PUERTAS Y VENTANAS;Aspecto;Medidas del hueco;Cantidad;Dónde"
    override val davanzaliSoglie: String get() = "Alféizares y umbrales"
    override val davanzaliSoglieNota: String get() = "Piezas a pedir: largo × fondo × grueso, con las partes que entran en los lados del hueco y las que sobresalen del muro."
    override val davanzaliSoglieCsv: String get() = "ALFÉIZARES Y UMBRALES;Medidas;Cantidad;Dónde"
}
