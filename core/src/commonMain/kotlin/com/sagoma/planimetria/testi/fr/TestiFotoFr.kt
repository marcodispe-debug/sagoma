package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiFoto

object TestiFotoFr : TestiFoto() {
    override val impossibileLeggereFoto: String get() = "Impossible de lire la photo"
    override val nonSpazioSalvare: String get() = "Pas assez d'espace pour enregistrer la photo"
    override val fotoSopralluogoRiguardareMentre: String get() =
        "Photos du relevé à revoir pendant que vous dessinez : où est la prise, dans quel sens s'ouvre la porte, le mètre contre le mur. " +
            "Elles restent dans le projet ; elles ne modifient ni le plan ni la 3D."
    override val scatta: String get() = "📷 Prendre une photo"
    override val galleria: String get() = "🖼 Depuis la galerie"
    override val aggiungiFoto: String get() = "📷 Ajouter une photo"
    override val preparoFoto: String get() = "Préparation de la photo…"
    override val fotoStanza: String get() = "Photo de la pièce"
    override val toccaAggiungereNota: String get() = "Touchez pour ajouter une note"
    override val aproFoto: String get() = "Ouverture de la photo…"
    override val notaEsempioPresa30: String get() = "Note (par exemple : prise à 30 cm du sol)"
    override val togliFoto: String get() = "Retirer la photo"
    override val senzaPermessoFotocamera: String get() = "Sans l'autorisation de l'appareil photo, vous pouvez choisir une photo dans la galerie"
    override val impossibileAprireFotocamera: String get() = "Impossible d'ouvrir l'appareil photo"
}
