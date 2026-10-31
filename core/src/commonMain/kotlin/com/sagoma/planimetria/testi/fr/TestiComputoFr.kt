package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.geometry.HousingNorms
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiComputo

object TestiComputoFr : TestiComputo() {
    override val progetto: String get() = "Projet"
    override val computoEsportato: String get() = "Métré exporté"
    override val esportazioneNonRiuscita: String get() = "Échec de l'export"
    override val computoListaArredi: String get() = "Métré et liste du mobilier"
    override val nonSonoAncoraStanze: String get() = "Il n'y a pas encore de pièces."
    override val stanze: String get() = "Pièces"
    override val stanza: String get() = "Pièce"
    override val muriSingoli: String get() = "Murs libres"
    override val soffitti: String get() = "Plafonds"
    override val statoDiProgetto: String get() = "Compte l'état projeté : sans les murs à démolir ni les ouvertures à fermer."
    override val pavM2: String get() = "Sol m²"
    override val paretiM2: String get() = "Murs m²"
    override val battiscM: String get() = "Plinthes m"
    override val pavimento: String get() = "Sol"
    override val totale: String get() = "Total"
    override val paretiSuperficieInternaNetto: String get() = "Murs : surface intérieure hors portes et fenêtres. Plinthes : périmètre intérieur moins les portes et les balcons."
    override val togliZoneNonCalpestabili: String get() = "Sol sans les zones non praticables"
    override val pavimentiOrdinare: String get() = "Revêtements de sol à commander"
    override val finiturePareti: String get() = "Finitions des murs"
    override val arredi: String get() = "Mobilier"
    override val nessunArredo: String get() = "Aucun meuble."
    override val impianti: String get() = "Équipements"
    override fun computo(title: Any?): String = "Métré – $title"
    override val condividi: String get() = "Partager"
    override val esportaCsv: String get() = "Exporter en CSV"
    override val definire: String get() = "À définir"
    override fun pitturaColore(n: Any?): String = "Peinture couleur $n"
    override fun pittura(p1: Any?): String = "Peinture, $p1"
    override val rivestimento: String get() = "Revêtement"
    override fun finoCm(cover: Any?, p2: Any?): String = "$cover jusqu'à $p2 cm"
    override val arredo: String get() = "Meuble"
    override fun cm(p1: Any?, p2: Any?, p3: Any?): String = "$p1×$p2×$p3 cm"
    override val stanze2: String get() = "PIÈCES"
    override val pianoStanzaPavimentoM2: String get() = "Étage;Pièce;Surface au sol m²;Périmètre m;Murs nets m²;Ouvertures m²;Plinthes m;Sol;Murs;Portes;Fenêtres"
    override fun totale2(p1: Any?, p2: Any?, p3: Any?): String = "Total;;$p1;;$p2;;$p3;;;;"
    override val pavimentiM2M210: String get() = "SOLS;m²;m² avec 10 % de chutes"
    override val paretiM22: String get() = "MURS;m²"
    override val arrediMisureQuantitaDove: String get() = "MOBILIER;Dimensions;Quantité;Où"
    override val luceAria: String get() = "Lumière et air (fenêtres / sol)"
    override val finM2: String get() = "Fen. m²"
    override val rapporto: String get() = "Rapport"
    override fun almeno(n: HousingNorms): String = n.windowRatio?.let { "≥ ${n.ratioLabel(it)}" } ?: "Suffit"
    override val si: String get() = "oui"
    override val no: String get() = "non"
    override fun notaLuce(n: HousingNorms): String {
        val ratio = n.windowRatio
        return "Pièces à vivre. Fenêtres ${if (n.openableOnly) "ouvrantes " else ""}donnant dehors, mesurées sur la baie du mur. " + when {
            ratio == null -> "${T.controllo.paese(n.country)} (${n.source}) : une fenêtre ouvrante donnant dehors suffit."
            !n.official -> "Conseil (l'application n'a pas les règles du pays du logement) : au moins ${n.ratioLabel(ratio)} du sol."
            else -> "${T.controllo.paese(n.country)} (${n.source}) : il en faut au moins ${n.ratioLabel(ratio)} du sol" +
                (n.ventRatio?.let { ", et la partie ouvrante au moins ${n.ratioLabel(it)}" } ?: "") + "."
        }
    }
    override fun notaLucePdf(n: HousingNorms): String {
        val ratio = n.windowRatio
        return "Fenêtres ${if (n.openableOnly) "ouvrantes " else ""}donnant dehors, sur la baie ; " + when {
            ratio == null -> "au moins une (${T.controllo.paese(n.country)})."
            !n.official -> "conseil : au moins ${n.ratioLabel(ratio)}."
            else -> "au moins ${n.ratioLabel(ratio)}${n.ventRatio?.let { ", ouvrantes ${n.ratioLabel(it)}" } ?: ""} (${T.controllo.paese(n.country)})."
        }
    }
    override val opere: String get() = "Travaux (démolitions et constructions)"
    override val muriDaDemolire: String get() = "Murs à démolir"
    override val muriNuovi: String get() = "Murs neufs"
    override val voce: String get() = "Poste"
    override val numero: String get() = "N°"
    override val pianoVoceNumeroM2M3: String get() = "Niveau;Poste;N°;m²;m³"
    override val vaniDaAprire: String get() = "Baies à ouvrir dans les murs existants"
    override val apertureDaChiudere: String get() = "Ouvertures à fermer"
    override val notaOpere: String get() = "Murs : une face, plein pour vide moins portes et fenêtres, hauts comme la paroi ; m³ avec l'épaisseur. Un mur mitoyen compte une fois. Indicatif : à vérifier sur place."
    override val notaOperePdf: String get() = "Murs : une face moins portes et fenêtres ; m³ avec l'épaisseur. Indicatif."
    override val opere2: String get() = "TRAVAUX"
    override val superfici: String get() = "Surfaces"
    override val piano: String get() = "Étage"
    override val utileM2: String get() = "Utile m²"
    override val lordaM2: String get() = "Brute m²"
    override val balconiM2: String get() = "Balc. m²"
    override val commerciale: String get() = "Surface commerciale (indicative)"
    override val notaSuperfici: String get() =
        "Utile : le sol des pièces. Brute : avec les murs. Commerciale (DPR italien 138/1998) : murs extérieurs jusqu'à 50 cm (ceux mitoyens " +
            "avec d'autres logements compteraient à moitié), sans les zones non praticables, sous-sols à 50 %, balcons et terrasses à 30 % jusqu'à 25 m² et 10 % au-delà."
    override val notaSuperficiPdf: String get() = "DPR 138/1998 (Italie) : murs ext. ≤ 50 cm, sous-sols 50 %, balcons 30 % (10 % > 25 m²)."
    override val superfici2: String get() = "SURFACES"
    override val pianoSuperfici: String get() = "Étage;Utile m²;Brute m²;Balcons et terrasses m²"
    override val commercialeCsv: String get() = "Surface commerciale indicative"
    override val luceAria2: String get() = "LUMIÈRE ET AIR"
    override fun pianoStanzaLuce(n: HousingNorms): String =
        "Étage;Pièce;Surface au sol m²;${if (n.openableOnly) "Fenêtres ouvrantes m²" else "Fenêtres m²"};Rapport;${n.windowRatio?.let { "Au moins ${n.ratioLabel(it)}" } ?: "Suffit"}"
    override val impiantiQuantitaDove: String get() = "ÉQUIPEMENTS;Quantité;Où"
    override val porteFinestre: String get() = "Portes et fenêtres"
    override val porteFinestreNota: String get() = "Dimensions de la baie (largeur × hauteur), à faire vérifier par qui établit le devis. Les baies à murer n'y figurent pas."
    override fun misuraVano(w: Any?, h: Any?): String = "$w×$h cm"
    override val porteFinestreCsv: String get() = "PORTES ET FENÊTRES;Aspect;Dimensions de la baie;Quantité;Où"
    override val davanzaliSoglie: String get() = "Appuis et seuils"
    override val davanzaliSoglieNota: String get() = "Pièces à commander : longueur × profondeur × épaisseur, avec les parties encastrées dans les côtés de la baie et celles qui débordent du mur."
    override val davanzaliSoglieCsv: String get() = "APPUIS ET SEUILS;Dimensions;Quantité;Où"
}
