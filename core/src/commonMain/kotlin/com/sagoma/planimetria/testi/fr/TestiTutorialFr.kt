package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.testi.TestiTutorial

object TestiTutorialFr : TestiTutorial() {
    override fun titolo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro -> "Tutoriel"
        TutorialStep.OpenInfo -> "Informations de la pièce"
        TutorialStep.WallLengths -> "Changer les mesures"
        TutorialStep.ChangeType -> "Type de pièce"
        TutorialStep.MoveWall -> "Déplacer un mur"
        TutorialStep.MoveCorner -> "Déplacer un angle"
        TutorialStep.AddDoor -> "Ajouter une porte"
        TutorialStep.MoveDoor -> "Déplacer la porte"
        TutorialStep.AddFixture -> "Équipements"
        TutorialStep.AddRoom -> "Ajouter une pièce"
        TutorialStep.MoveRoom -> "Assembler les pièces"
        TutorialStep.Undo -> "Annuler"
        TutorialStep.Done -> "Terminé !"
        TutorialStep.TraceLoad -> "Charger le plan"
        TutorialStep.TraceCalibA -> "Étalonnage : le premier point"
        TutorialStep.TraceCalibB -> "Étalonnage : le second point"
        TutorialStep.TraceCalibValue -> "Étalonnage : la longueur réelle"
        TutorialStep.TraceFirstCorner -> "Le premier angle"
        TutorialStep.TraceCorners -> "Les autres angles"
        TutorialStep.TraceClose -> "Fermer la pièce"
        TutorialStep.TraceCheck -> "Vérifier les mesures"
        TutorialStep.TraceFade -> "Plan et dessin"
        TutorialStep.TraceNext -> "La pièce suivante"
        TutorialStep.TraceDone -> "Terminé !"
    }

    override fun testo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro ->
            "Je vous guide pas à pas dans les fonctions principales : à chaque étape, vous essayez vous-même. " +
                "Ce sont de vraies modifications du plan, mais à la fin vous pouvez les annuler avec ↶."
        TutorialStep.OpenInfo ->
            "Touchez une pièce pour la sélectionner : la barre Infos apparaît en bas. Appuyez sur Infos, sur la flèche ▲ " +
                "ou faites remonter la barre pour ouvrir ses propriétés."
        TutorialStep.WallLengths ->
            "Dans le panneau, sous « Murs », il y a la longueur de chaque côté : tapez une nouvelle valeur et confirmez. " +
                "Le côté opposé s'adapte tout seul. Changez ensuite un côté dans l'autre direction. Les cotes du plan se mettent à jour."
        TutorialStep.ChangeType -> "Toujours dans le panneau, choisissez un type de pièce, par exemple Cuisine : la couleur et le nom changent tout seuls."
        TutorialStep.MoveWall ->
            "Fermez le panneau (▼ ou faites-le glisser vers le bas). Touchez ensuite un mur pour le sélectionner et faites-le glisser : " +
                "la pièce s'agrandit ou rétrécit, et le mur s'accroche aux murs des pièces voisines."
        TutorialStep.MoveCorner -> "Faites glisser l'un des points bleus aux angles de la pièce : vous pouvez même faire des murs en biais."
        TutorialStep.AddDoor -> "Appuyez sur « 🧱 Éléments de construction » → Portes, choisissez un type puis touchez le mur où la mettre."
        TutorialStep.MoveDoor -> "Faites glisser la porte le long du mur. Dans Infos vous changez ses dimensions et son sens d'ouverture, ou la rendez coulissante."
        TutorialStep.AddFixture -> "Appuyez sur « 🧱 Éléments de construction » → Équipements et choisissez par exemple Prise électrique, puis touchez un mur. Les lumières, elles, se placent en touchant un endroit dans la pièce."
        TutorialStep.AddRoom -> "Appuyez sur « 🧱 Éléments de construction » → Pièce, choisissez la forme et les dimensions et confirmez."
        TutorialStep.MoveRoom -> "Faites glisser la nouvelle pièce par son nom et approchez-la d'une autre : quand les murs sont proches, ils s'accrochent."
        TutorialStep.Undo -> "Une erreur ? Appuyez sur ↶ Annuler en haut pour revenir d'un pas (↷ Rétablir le refait)."
        TutorialStep.Done -> "Vous connaissez maintenant les fonctions principales. Depuis « ❔ Guides », en haut à droite, vous pouvez refaire le tutoriel quand vous voulez et trouver un guide pour chaque fonction."
        TutorialStep.TraceLoad -> "Appuyez sur « Photo / image » (ou « PDF » sur ordinateur) et choisissez le plan de la maison : cadastre, plan de l'architecte ou une photo bien droite."
        TutorialStep.TraceCalibA ->
            "Cherchez sur le plan une mesure écrite, de préférence longue (par exemple un mur coté 4,72). " +
                "Zoomez et touchez le début de cette mesure : le point A apparaît."
        TutorialStep.TraceCalibB -> "Touchez maintenant la fin de la même mesure : le point B apparaît. En cas d'erreur, un autre toucher recommence depuis A."
        TutorialStep.TraceCalibValue ->
            "Dans la case en haut, tapez la longueur réelle de la mesure entre A et B (en cm, comme sur le plan) et confirmez : " +
                "le plan est maintenant à l'échelle."
        TutorialStep.TraceFirstCorner ->
            "Vous êtes maintenant dans « Mur par mur ». Choisissez une pièce et cliquez l'un de ses angles, du côté intérieur du mur : " +
                "l'épaisseur du mur s'ajoute toute seule vers l'extérieur."
        TutorialStep.TraceCorners -> "Cliquez les autres angles l'un après l'autre, en faisant le tour de la pièce. En cas d'erreur, « ↶ Dernier » retire le dernier angle."
        TutorialStep.TraceClose ->
            "Quand vous avez cliqué tous les angles, cliquez à nouveau le premier (celui entouré) ou appuyez sur « Fermer la pièce » ; " +
                "choisissez ensuite le type de pièce et confirmez."
        TutorialStep.TraceCheck ->
            "Ouvrez les Infos de la pièce (barre en bas, ▲ ou faites-la remonter) : comparez les m² et les murs avec le plan. " +
                "Si une partie ne compte pas dans les m² (comble bas, gaine), utilisez « ▧ Ajouter une zone non praticable » dans l'onglet Mesures."
        TutorialStep.TraceFade ->
            "Fermez le panneau et appuyez sur « ◐ Plan et dessin » en bas à droite : éclaircissez le plan ou masquez-le " +
                "pour bien voir le dessin."
        TutorialStep.TraceNext -> "Appuyez sur « 🧱 Éléments de construction » → Pièce, choisissez « Mur par mur » et décalquez la pièce suivante, angle par angle comme avant."
        TutorialStep.TraceDone ->
            "Continuez ainsi avec les autres pièces. Si l'accroche déplace vos points, désactivez-la avec 🧲. " +
                "Avec « Terminé », si vous ne l'avez pas encore fait, le tutoriel des autres fonctions commence (portes, équipements, mesures)."
    }

    override fun latiCambiati(n: Int): String =
        if (n == 0) "Côtés modifiés : 0 sur 2" else "Côtés modifiés : 1 sur 2 · maintenant un côté dans l'autre direction"
    override fun angoliServono(n: Int): String = "Angles cliqués : $n · il en faut au moins 3"
    override fun angoliCliccati(n: Int): String = "Angles cliqués : $n"
}
