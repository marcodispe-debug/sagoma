package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Passo
import com.sagoma.planimetria.testi.TestiGuide

object TestiGuideFr : TestiGuide() {
    override val titoloMansarda: String get() = "Combles"
    override val titoloRicalco: String get() = "Décalquer un plan"
    override val titoloStanze: String get() = "Pièces et dimensions"
    override val titoloZone: String get() = "Zones non praticables"
    override val titoloMuri: String get() = "Murs et angles"
    override val titoloAgganci: String get() = "Aimantation"
    override val titoloPorte: String get() = "Portes et fenêtres"
    override val titoloImpianti: String get() = "Équipements"
    override val titoloPiuStanze: String get() = "Plusieurs pièces"
    override val titoloBalconi: String get() = "Balcons et terrasses"
    override val titoloScale: String get() = "Escaliers et étages"
    override val titoloVista3D: String get() = "Vue 3D"
    override val titoloQuote: String get() = "Cotes et textes"

    override fun passoDi(numero: Int, totale: Int): String = "Étape $numero sur $totale"
    override val apriVista3D: String get() = "Ouvrir la vue 3D"

    override val mansardaIntro: Passo get() = Passo(
        "À quoi ressemblent des combles",
        "Dans des combles le plafond descend vers un mur plus bas (le « muret » sous la pente du toit). " +
            "Les murs latéraux ne sont donc pas rectangulaires : ils sont pleine hauteur jusqu'à un point puis descendent " +
            "en biais. Dans l'app on l'obtient en deux gestes : on abaisse le muret et on coupe en biais les murs à côté.",
    )

    override val mansardaStanza: Passo get() = Passo(
        "1 · Dessiner la pièce",
        "Créez la pièce avec « 🧱 Éléments de construction » → Pièce et ajustez les dimensions dans le panneau Infos, comme pour toute autre pièce. " +
            "La hauteur sous plafond de la pièce est celle de la partie la plus haute des combles.",
    )

    override val mansardaMuretto: Passo get() = Passo(
        "2 · Abaisser le muret",
        "Touchez le mur vers lequel descend le toit, ouvrez Infos et tapez sa vraie hauteur dans « Hauteur du mur » " +
            "(par exemple 120 cm). Sur le plan la hauteur apparaît en violet à côté du mur.",
    )

    override val mansardaTaglio: Passo get() = Passo(
        "3 · Couper le premier mur latéral",
        "Touchez l'un des deux murs perpendiculaires au muret et, dans le panneau Infos, activez « Coupe en biais (sous le toit) ». " +
            "Si le muret est plus bas, le mur descend maintenant jusqu'à sa hauteur. Si les deux murs voisins sont bas, " +
            "choisissez le côté dans « Descend vers ».",
    )

    override val mansardaInizio: Passo get() = Passo(
        "4 · Choisir où la pente commence",
        "Mesurez dans la maison à quelle distance du muret le plafond commence à descendre et tapez-la dans « Commence à descendre à ». " +
            "Ou faites glisser le point orange sur le plan : la ligne pointillée indique où la pente commence.",
    )

    override val mansardaRipeti: Passo get() = Passo(
        "5 · Répéter sur l'autre mur",
        "Faites de même sur le mur d'en face, pour que le plafond descende de façon régulière dans toute la pièce. " +
            "Si le mur est partagé avec une autre pièce, seul le côté des combles est coupé : la pièce voisine reste entière.",
    )

    override val mansardaControllo: Passo get() = Passo(
        "6 · Vérifier portes, fenêtres et équipements",
        "Si une fenêtre, une porte, un radiateur ou une prise dépasse le mur en pente, un " +
            "point rouge avec « ! » apparaît sur le plan et un avertissement dans le panneau Infos : abaissez l'objet ou déplacez-le le long du mur " +
            "vers la partie haute.",
    )

    override val mansarda3D: Passo get() = Passo(
        "7 · Voir le résultat en 3D",
        "Ouvrez la vue 3D : le haut des murs et le plafond descendent en biais et vous pouvez marcher dans les combles " +
            "pour vérifier que tout est juste. Le volume de la pièce dans le panneau Infos tient déjà compte du plafond en pente.",
    )

    override val caricaPiantina: Passo get() = Passo(
        "Charger le plan",
        "Dans un nouveau projet choisissez « Décalquer un plan » ; dans un projet déjà commencé utilisez ⋮ → « Plan de fond… ». " +
            "Puis « Photo / image » (ou « PDF » sur l'ordinateur) : plan cadastral, plan de l'architecte ou une photo bien droite.",
    )

    override val ricalcaAngoli: Passo get() = Passo(
        "Décalquer les angles",
        "« Mur par mur » s'ouvre. " + Tip.WallDraw.text + " Pour la pièce suivante : « 🧱 Éléments de construction » → Pièce → « Mur par mur ».",
    )

    override val controllaMisure: Passo get() = Passo(
        "Vérifier les dimensions",
        "Ouvrez les Infos de la pièce et comparez m² et murs avec ceux écrits sur le plan. Si une partie ne compte pas dans les m² " +
            "(combles bas, gaine) ajoutez une zone non praticable.",
    )

    override val muoversi: Passo get() = Passo(
        "Se déplacer sur le plan",
        Tip.Welcome.text,
    )

    override val aggiungiZona: Passo get() = Passo(
        "Ajouter une zone",
        "Sélectionnez la pièce, ouvrez Infos et, dans l'onglet Mesures, appuyez sur « ▧ Ajouter une zone non praticable ».",
    )

    override val agganci: Passo get() = Passo(
        "Activer et désactiver l'aimantation",
        "Le bouton 🧲 se trouve sur la barre Infos de la pièce et dans le panneau « Mur par mur ». " + Tip.Snap.text,
    )

    override val aggiungiBalcone: Passo get() = Passo(
        "Ajouter un balcon ou une terrasse",
        "Appuyez sur « 🧱 Éléments de construction » → Balcons et terrasses et choisissez le type, puis la forme et les dimensions comme pour une pièce. Il part déjà accolé à la maison : " +
            "faites-le glisser par son nom le long du mur jusqu'au bon endroit, il s'aimante tout seul. Sur les côtés vers l'extérieur il y a le garde-corps, " +
            "contre la maison le mur reste.",
    )

    override val parapetto: Passo get() = Passo(
        "Garde-corps et porte-fenêtre",
        Tip.Outdoor.text,
    )

    override val aggiungiScala: Passo get() = Passo(
        "Ajouter un escalier",
        "Appuyez sur « 🧱 Éléments de construction » → Escaliers et choisissez le type : droit, en L avec palier, en U à deux volées ou hélicoïdal. " +
            "L'escalier apparaît au milieu de la vue (ou de la pièce sélectionnée) : faites-le glisser où il faut, près d'un mur il s'aligne tout seul.",
    )

    override val opzioniScala: Passo get() = Passo(
        "Type, dimensions et sens",
        "Touchez l'escalier et ouvrez Infos : vous changez le type, la largeur, le giron (ou le diamètre de l'hélicoïdal), le sens de rotation et l'orientation, " +
            "ou tournez-le de 90° avec ↻. Le nombre et la hauteur des marches viennent de la hauteur d'étage ; si les marches sont inconfortables un avertissement apparaît.",
    )

    override val aggiungiPiano: Passo get() = Passo(
        "Ajouter un étage",
        "Touchez le bouton de l'étage en haut à gauche et choisissez « Ajouter un étage au-dessus ». Vous pouvez partir des murs de l'étage du dessous " +
            "ou d'un étage vide. L'étage du dessous reste visible en gris et ses escaliers apparaissent comme une trémie dans le sol.",
    )

    override val piani3D: Passo get() = Passo(
        "Les étages en 3D",
        "En 3D vous voyez l'étage choisi au-dessus de ceux du dessous, reliés par les escaliers ; les étages plus hauts sont cachés, comme si " +
            "on avait enlevé le toit. Dans le même menu de l'étage vous changez son nom et sa hauteur d'étage, ou le supprimez.",
    )

    override val comandi3D: Passo get() = Passo(
        "Toutes les commandes",
        "Téléphone : un doigt fait tourner, deux doigts zooment et déplacent ; un mur, une porte ou un équipement sélectionné se fait glisser pour " +
            "le déplacer. En 🚶 Marcher on avance avec le joystick en bas à gauche. Ordinateur : ↑ ↓ (ou W S) avancer et reculer, ← → pour " +
            "tourner, A D pour un pas de côté, PgUp/PgDn pour regarder en haut ou en bas, Maj pour aller plus vite ; " +
            "molette pour zoomer, glisser avec le bouton droit pour déplacer la vue.",
    )

    override val quota: Passo get() = Passo(
        "Ajouter une cote",
        "Appuyez sur « 🛠 Outils » → Cote et touchez les deux extrémités : les points s'aimantent aux angles et aux faces des murs. La cote reste " +
            "sur le plan et apparaît dans les planches ; touchez-la pour la déplacer ou la supprimer.",
    )

    override val testo: Passo get() = Passo(
        "Écrire un texte",
        "Appuyez sur « 🛠 Outils » → Texte, touchez où l'écrire, puis tapez-le et choisissez la taille.",
    )
}
