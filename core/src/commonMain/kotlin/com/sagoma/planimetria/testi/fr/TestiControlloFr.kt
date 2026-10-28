package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloFr : TestiControllo() {
    override val voceMenu: String get() = "🔍 Vérifier le plan…"
    override val titolo: String get() = "Vérifier le plan"
    override val nessunProblema: String get() = "Aucun problème trouvé."
    override val cosaControlla: String get() =
        "Cherche les pièces superposées ou presque collées, les murs qui se croisent, les portes et fenêtres en double, plus larges ou plus hautes que le mur, " +
            "les pièces sans porte, la porte d'entrée et les escaliers."
    override val spiega: String get() = "Ils peuvent fausser surfaces, métré et 3D. « Afficher » y amène et le sélectionne."
    override val daCorreggere: String get() = "À corriger"
    override val daControllare: String get() = "À vérifier"
    override val mostra: String get() = "Afficher"
    override val chiudi: String get() = "Fermer"
    override fun troppi(n: Int): String = "Seulement les $n premiers : corrigez-les et vérifiez à nouveau."
    override fun muriIncrociati(a: String): String = "$a : les murs se croisent et le contour se coupe lui-même, la surface et la 3D sont donc fausses. Déplacez les angles."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a et $b se superposent sur $m2 m² : cette partie compte deux fois. Déplacez un mur ou un angle."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a et $b sont presque collées : il reste une fente de $cm cm entre les murs. D'habitude le mur est commun : faites glisser ce mur sur celui de l'autre pièce."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a et $b ont deux murs qui se chevauchent de $cm cm au lieu d'un mur commun : faites glisser ce mur sur celui de l'autre pièce."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Deux ouvertures au même endroit ($cosa, $stanze) : peut-être posée deux fois. Supprimez-en une."
    override fun muroZero(a: String, unisci: String): String = "$a : deux angles l'un sur l'autre (un mur de 0 cm). Avec le mur sélectionné, « $unisci » le supprime."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a : $cosa de $cm cm sur un mur de $muro cm, elle n'y tient pas."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a : $cosa monte à $cm cm du sol, mais le mur y est haut de $muro cm."
    override fun suMuroTolto(a: String, cosa: String): String = "$a : $cosa sur un mur supprimé (côté ouvert), qui n'existe pas."
    override fun senzaPorta(a: String): String = "$a n'a ni porte ni passage : par où entre-t-on ?"
    override val nessunaPorta: String get() = "Il n'y a aucune porte à cet étage : ajoutez des portes et des passages entre les pièces."
    override val senzaIngresso: String get() = "Aucune porte ne donne dehors : la porte d'entrée manque-t-elle ?"
    override fun finestraInterna(a: String, b: String): String = "$a : la fenêtre donne sur $b, pas dehors. Est-ce voulu ?"
    override fun piccolissima(a: String, m2: String): String = "$a est minuscule ($m2 m²) : est-ce voulu ou un morceau oublié ?"
    override val scalaSenzaPiano: String get() = "L'escalier monte, mais il n'y a pas d'étage au-dessus : ajoutez l'étage supérieur ou supprimez l'escalier."
    override fun scalaFuori(piano: String): String = "L'escalier arrive hors des pièces de l'étage supérieur ($piano)."
}
