package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Suggerimenti

object SuggerimentiFr : Suggerimenti() {
    override fun titolo(v: Tip): String = when (v) {
        Tip.Welcome -> "Bienvenue"
        Tip.Room -> "Pièce sélectionnée"
        Tip.Wall -> "Mur"
        Tip.NewRoom -> "Nouvelle pièce"
        Tip.Opening -> "Portes et fenêtres"
        Tip.Fixture -> "Équipements"
        Tip.Ruler -> "Mètre"
        Tip.WallCut -> "Mur sous le toit"
        Tip.View3D -> "Vue 3D"
        Tip.Stair -> "Escaliers"
        Tip.Outdoor -> "Balcon et terrasse"
        Tip.Structure -> "Poteaux et poutres"
        Tip.Measure -> "Distance"
        Tip.FreeWall -> "Mur libre"
        Tip.Floors -> "Étages"
        Tip.Tracing -> "Décalquer un plan"
        Tip.Calibrate -> "Étalonnage"
        Tip.WallDraw -> "Mur par mur"
        Tip.Underlay -> "Plan et dessin"
        Tip.ExcludedZone -> "Zone non praticable"
        Tip.RoomInfo -> "Panneau Infos"
        Tip.Snap -> "Accroche"
    }

    override fun testo(v: Tip): String = when (v) {
        Tip.Welcome ->
            "Touchez une pièce pour la sélectionner. Pincez à deux doigts pour zoomer, faites glisser le fond pour déplacer le plan. " +
                "En cas d'erreur, ↶ Annuler en haut remet tout comme avant."
        Tip.Room ->
            "Faites glisser les points bleus pour déplacer les angles et les murs pour l'étirer ; faites glisser le nom pour déplacer toute la pièce. " +
                "Pour le nom, le type et les longueurs exactes des côtés, appuyez sur Infos ou faites remonter la barre du bas. " +
                "Si l'accroche attire le point où vous ne voulez pas, désactivez-la avec 🧲 sur la barre."
        Tip.Wall ->
            "Faites-le glisser pour le déplacer : il s'accroche aux murs des pièces voisines. Dans Infos vous changez sa hauteur et, s'il est commun " +
                "avec une autre pièce, vous pouvez le supprimer pour réunir les deux pièces en un seul espace."
        Tip.NewRoom ->
            "La nouvelle pièce est accolée à celle qui est ouverte, avec un mur en commun. Si elle va ailleurs, faites-la glisser par son nom : " +
                "elle s'accroche aux murs des pièces voisines."
        Tip.Opening -> "Faites-la glisser le long du mur pour la déplacer. Dans Infos vous changez les dimensions, battante ou coulissante, le côté des charnières et le sens d'ouverture, ou vous la retirez."
        Tip.Fixture ->
            "Les équipements muraux glissent le long du mur, les lumières se déplacent n'importe où dans la pièce. " +
                "Dans Infos vous réglez la hauteur depuis le sol, les dimensions et la rotation, ou vous le retirez."
        Tip.Ruler -> "Faites glisser les extrémités pour l'étirer et le milieu pour le déplacer. La poignée ronde le fait tourner (accroche tous les 45°), × le supprime."
        Tip.WallCut ->
            "Le mur descend en biais jusqu'à la hauteur du muret. Faites glisser le point sur le mur, ou tapez la distance, " +
                "pour choisir où il commence à descendre. Les pointillés montrent où commence la pente du plafond ; en 3D vous voyez tout."
        Tip.View3D ->
            "Faites glisser pour tourner autour de la maison ; zoomez à deux doigts (ou avec la molette). Touchez un mur ou un objet pour le sélectionner. " +
                "Avec 🚶 Marcher vous entrez dans les pièces. Toutes les commandes sont dans ❔ Guides → Vue 3D."
        Tip.Stair ->
            "Faites-le glisser pour le déplacer : près d'un mur il s'aligne tout seul. Dans Infos choisissez le type, la largeur, le giron et le sens de rotation, " +
                "et tournez-le de 90°. Le nombre et la hauteur des marches viennent de la hauteur d'étage ; à l'étage supérieur apparaît la trémie."
        Tip.Outdoor ->
            "C'est un espace extérieur : à la place des murs il y a un garde-corps, contre la maison le mur de la maison reste. Placez-le contre un mur " +
                "et ajoutez une porte-fenêtre (Éléments de construction → Portes-fenêtres) sur le mur de la pièce. Dans Infos choisissez rambarde, muret ou verre et la hauteur."
        Tip.Structure ->
            "Faites glisser un poteau pour le déplacer : près d'un mur il s'aligne et devient un pilier en saillie. Une poutre se déplace en " +
                "la faisant glisser et s'allonge par les points aux extrémités, qui s'appuient sur les murs. Dans Infos vous changez la forme et les dimensions."
        Tip.Measure ->
            "Touchez le premier objet puis le second : murs, portes et fenêtres, radiateurs, prises, lumières, poteaux, poutres, escaliers. " +
                "La distance entre leurs bords les plus proches apparaît, avec une cote sur le plan ; si vous déplacez les objets elle se met à jour."
        Tip.FreeWall ->
            "Une cloison qui ne ferme pas une pièce. Faites-la glisser pour la déplacer et faites glisser les points aux extrémités pour l'allonger ou la tourner : " +
                "les extrémités s'accrochent aux faces des autres murs et aux extrémités des autres murs libres, et le mur se redresse à 90°. " +
                "Dans Infos vous changez l'épaisseur, la hauteur et la couleur."
        Tip.Floors ->
            "Avec le bouton de l'étage, en haut à gauche, vous passez d'un étage à l'autre et en ajoutez de nouveaux. L'étage inférieur apparaît " +
                "en gris pour aligner les murs, et ses escaliers apparaissent comme un trou dans le sol. En 3D vous voyez l'étage choisi sur ceux du dessous."
        Tip.Tracing ->
            "Chargez la photo ou le PDF du plan, puis étalonnez-le sur une mesure connue : ensuite vous cliquez les angles des pièces " +
                "et les mesures viennent toutes seules, comme celles écrites sur le plan."
        Tip.Calibrate ->
            "Touchez les deux extrémités d'une mesure écrite sur le plan (de préférence la plus longue, par exemple un mur entier) et tapez " +
                "combien de cm elle fait. Si ensuite les mesures ne correspondent pas, « 📏 Refaire l'étalonnage » corrige même au milieu d'une pièce."
        Tip.WallDraw ->
            "Cliquez les angles l'un après l'autre ; pour finir cliquez le premier angle (entouré) ou « Fermer la pièce ». Sur le plan, " +
                "cliquez les angles intérieurs des murs : l'épaisseur s'ajoute vers l'extérieur. ↶ Dernier retire le dernier angle, " +
                "« Taper les mesures » vous laisse choisir la direction et la longueur de chaque mur."
        Tip.Underlay ->
            "Avec le panneau « ◐ Plan et dessin », en bas à droite, vous affichez ou masquez le plan et votre dessin et réglez " +
                "leur transparence, pour bien voir ce que vous décalquez. Le dessin redevient plein quand vous rouvrez le projet."
        Tip.ExcludedZone ->
            "Cliquez les angles de la partie qui ne compte pas dans les m², comme la partie basse d'un comble ou une gaine : les angles " +
                "s'accrochent aux angles intérieurs des murs. À la fin choisissez le nom ; les m² de la pièce diminuent, le sol reste entier."
        Tip.RoomInfo ->
            "En haut il y a Dupliquer, Supprimer, le nom, le type et les mesures principales. En dessous, les onglets : Mesures (hauteurs, épaisseurs, murs et " +
                "zones non praticables), Finitions (sol et murs), Portes, équip. (portes, fenêtres et équipements), Projet (murs existants, à démolir ou neufs). " +
                "La barre à droite fait défiler le contenu."
        Tip.Snap ->
            "Avec 🧲 Oui, les angles, les murs et les objets s'accrochent aux angles, aux alignements, aux directions droites et aux murs proches. Avec 🧲 Non ils vont " +
                "exactement là où vous les mettez ; la pièce se ferme quand même quand vous cliquez le premier angle."
    }
}
