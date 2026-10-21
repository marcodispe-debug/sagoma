package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiSelezione

object TestiSelezioneFr : TestiSelezione() {
    override val selezionaTrascinaVuotoRettangolo: String get() = "☐ Sélection : faites glisser dans le vide pour un rectangle (→ seulement ce qui est dedans, ← aussi ce qu'il touche), touchez pour ajouter ou retirer."
    override fun trascinaneSpostarliTutti(n: Any?, p2: Any?): String = "$n $p2 · faites-en glisser un pour tous les déplacer"
    override val specchia: String get() = "⇆ Miroir"
    override val specchia2: String get() = "⇅ Miroir"
    override val sposta: String get() = "Déplacer de…"
    override val duplica: String get() = "Dupliquer…"
    override val copia: String get() = "⧉ Copier"
    override val serie: String get() = "Série…"
    override val tutto: String get() = "Tout"
    override val sposta2: String get() = "Déplacer de"
    override val duplicaSpostando: String get() = "Dupliquer en déplaçant de"
    override val serieCopieFila: String get() = "Série (copies alignées)"
    override val allinea: String get() = "Aligner ▾"
    override val distribuisciOrizzontale: String get() = "Répartir horizontalement"
    override val distribuisciVerticale: String get() = "Répartir verticalement"
    override val destraCm: String get() = "→ droite cm"
    override val giuCm: String get() = "↓ bas cm"
    override val numeroCopie: String get() = "Nombre de copies"
    override val valoriNegativiVersoSinistra: String get() = "Valeurs négatives : vers la gauche ou vers le haut."
    override val oggettoSelezionato: String get() = "objet sélectionné"
    override val oggettiSelezionati: String get() = "objets sélectionnés"
}
