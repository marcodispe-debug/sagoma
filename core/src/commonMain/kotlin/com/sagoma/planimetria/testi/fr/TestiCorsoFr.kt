package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoFr : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "Forme en L"
        Lesson.Elements -> "Portes, fenêtres et le reste"
        Lesson.Check -> "Vérifier le plan"
        Lesson.OverPlan -> "Sur votre plan"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guide · Leçon $n sur $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Étape $n sur $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Bienvenue dans Sagoma !"
        CourseStep.ElementsDone -> "Chaque élément, depuis « $infoTab »"
        CourseStep.Finished -> "Vous avez fini le guide !"
        CourseStep.Snap -> "Déplacer les pièces et l'aimant 🧲"
        CourseStep.InnerFace -> "Le côté intérieur des murs"
        else -> "C'est fait !"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Je vous guide pas à pas pour dessiner le logement de ce plan inventé, en ${Lesson.entries.size} courtes leçons : une pièce à la fois, chacune " +
                "à sa place comme sur le dessin. C'est vous qui faites les actions : à chaque étape un cadre orange vous montre où " +
                "cliquer. C'est un projet juste pour s'entraîner : vos projets ne sont pas touchés."
        CourseStep.SquareDone ->
            "Le bureau est prêt : 3,30 × 3,30 m. Vous avez vu toutes les façons de changer la forme d'une pièce : les mesures à la " +
                "création, les murs et les angles déplacés à la main, les mesures exactes dans « $infoTab » et « ${e.annulla} » pour " +
                "revenir en arrière. Dans la prochaine leçon : le séjour en L, au-dessus du bureau."
        CourseStep.Snap ->
            "Le séjour est né à l'écart de la maison. Une pièce se déplace en la faisant glisser par son nom. L'aimant " +
                "« ${T.schedaStanza.si} », ici en bas, est activé : quand vous approchez la pièce d'une autre, elle s'accroche toute " +
                "seule, angle sur angle, et le mur entre les deux n'en fait plus qu'un. Désactivé (« ${T.schedaStanza.no} »), la pièce " +
                "reste exactement où vous la lâchez. Laissez-le activé."
        CourseStep.LDone ->
            "Le séjour est accolé au-dessus du bureau, comme sur le plan : le mur entre les deux pièces est maintenant un seul mur " +
                "commun. Dans la prochaine leçon : la chambre, un rectangle à droite du séjour."
        CourseStep.RectDone ->
            "La chambre est accolée à droite du séjour. Dans la prochaine leçon : la salle de bains, sous la chambre, un peu hors d'équerre."
        CourseStep.SurveyDone ->
            "La salle de bains est sous la chambre, avec sa vraie forme : un peu hors d'équerre, comme en réalité. Dans la prochaine " +
                "leçon : l'entrée, dessinée mur par mur dans l'espace qui reste entre les pièces."
        CourseStep.WallsDone ->
            "L'entrée ferme le logement : maintenant toutes les pièces sont reliées, comme sur le plan. Vous avez vu toutes les " +
                "façons de créer une pièce. Dans la prochaine leçon : le mur libre, un mur tout seul qui ne ferme pas une pièce."
        CourseStep.FreeWallDone ->
            "La chambre a maintenant son dressing, séparé par une cloison avec le passage au fond. Dans le panneau « ${T.schedaStanza.info} » " +
                "du mur libre vous choisissez aussi la hauteur (jusqu'au plafond ou plus basse, comme un muret), la couleur et la courbe ; " +
                "et vous pouvez aussi ajouter des portes et des fenêtres aux murs libres. Dans la prochaine leçon : portes, fenêtres, " +
                "balcon, équipements et escaliers."
        CourseStep.ElementsDone ->
            "Dans « $infoTab » vous changez les mesures, le type et tout le reste de l'élément sélectionné ; il se ferme avec « ▼ ». Pour chaque élément : " +
                "vous le choisissez dans « ${e.elementiArchitettonici} », vous le placez sur le plan, et le reste depuis « $infoTab ». " +
                "Prochaine leçon : la vérification du plan, qui trouve toute seule les erreurs de dessin et en corrige beaucoup."
        CourseStep.CheckDone ->
            "Vous savez maintenant utiliser la vérification du plan : la liste complète depuis le menu « ⋮ », « ✓ ${T.controllo.correggi} » et " +
                "« ✓ Tout corriger » qui règlent tout seuls (« ${e.annulla} » revient en arrière), les « ! » sur le plan pour voir ce qu'il y a à un endroit " +
                "et « ${T.controllo.togliDallaPianta} » pour ceux qui vous vont comme ça (ils se remettent depuis la liste). Avant d'exporter, Sagoma vous " +
                "prévient s'il reste des erreurs de dessin. Dernière leçon : construire les pièces sur votre plan. Il faut un nouveau projet : je le prépare."
        CourseStep.Finished ->
            "Voici la chambre, la salle de bains et le séjour sur le plan, avec les bonnes mesures. Faites de même avec les autres pièces : une " +
                "forme pour commencer, accolée aux pièces voisines, puis murs et angles à leur place et les mesures exactes dans « $infoTab ». " +
                "Vous pouvez maintenant dessiner votre logement ! Le guide se refait quand vous voulez depuis « ${e.guide} »."
        CourseStep.InnerFace ->
            "Avant de déplacer la chambre, une chose importante. Sagoma mesure les pièces depuis le côté intérieur des murs, comme les " +
                "cotes écrites sur le plan : l'épaisseur du mur va vers l'extérieur. Quand vous placez une pièce sur le plan, faites " +
                "coïncider le plus possible le côté intérieur des murs dessinés avec le côté intérieur des murs du plan : regardez la " +
                "ligne intérieure du mur, ni le milieu ni le côté extérieur. Ainsi les mesures, les mètres carrés et les pièces voisines sont justes."
        else -> ""
    }

    override val avanti: String get() = "Suivant"
    override val apriProgettoNuovo: String get() = "Ouvrir le nouveau projet"
    override val bastaCosi: String get() = "Ça suffit"
    override val vaiAiProgetti: String get() = "Aller à vos projets"
    override val nienteDaIndicare: String get() = "Faites l'action de cette étape, ou cliquez sur « ${e.saltaPasso} »."
    override val tornaIndietro: String get() = "Ce n'est pas la forme de cette étape : cliquez sur « ${T.comuni.indietro} » et choisissez la bonne."
    override val vaiAllaLezione: String get() = "Ou allez à la leçon :"
    override val voceMenu: String get() = "Guide interactif (${Lesson.entries.size} leçons)"
    override val progettoProva: String get() = "Essai du guide"
    override val progettoProvaPlanimetria: String get() = "Essai du guide · plan"
    override val usaPiantina: String get() = "🧭 Utiliser le plan du guide"
    override val ingresso: String get() = "Entrée"
    override val cabinaArmadio: String get() = "dressing"
    override val notaMisure: String get() = "mesures en cm, au nu intérieur des murs"

    override val apriElementi: String get() = "Cliquez sur « ${e.elementiArchitettonici} », ici en bas."
    override val apriElementiAltraStanza: String get() = "Pour ajouter une autre pièce cliquez sur « ${e.elementiArchitettonici} », ici en bas."
    override val poiStanza: String get() = "Cliquez sur « ${e.stanza} » : la fenêtre des formes revient."
    override fun tipo(nome: String): String = "Choisissez le type : « $nome »."
    override val creaStanza: String get() = "Cliquez sur « ${cr.creaStanza} »."
    override fun selezionaStanza(nome: String): String = "Cliquez sur le nom de la pièce, « $nome », pour la sélectionner."
    override val accendiAgganci: String get() = "L'aimant est désactivé : cliquez sur « ${T.schedaStanza.no} », ici en bas, pour l'activer."
    override val schedaMisure: String get() = "Ouvrez l'onglet « ${T.schedaStanza.misure} »."
    override fun latoMuro(a: WallArrow): String = when (a) {
        WallArrow.UP -> "du haut"
        WallArrow.RIGHT -> "de droite"
        WallArrow.DOWN -> "du bas"
        WallArrow.LEFT -> "de gauche"
    }

    override val scegliQuadrato: String get() =
        "Quand vous ouvrez un nouveau projet, Sagoma vous demande tout de suite la forme de la première pièce. Commençons par le " +
            "bureau, la pièce carrée en bas à gauche du plan : cliquez sur « ${cr.quadrato} »."
    override val tipoStudio: String get() = "Dans « ${cr.tipoStanza} » cliquez sur « ${RoomType.Studio.label} » : Sagoma donne à la pièce le bon nom et la bonne couleur."
    override fun scriviLato(v: Int): String =
        "Dans le champ « ${cr.lato2} » écrivez $v : c'est la première façon de donner sa forme à une pièce, avec les mesures à la " +
            "création. C'est la mesure intérieure, d'un mur à l'autre : celle que vous prenez au mètre."
    override val soffittoECrea: String get() = "La hauteur sous plafond est déjà écrite (270 cm) : changez-la seulement si votre pièce est différente. Cliquez maintenant sur « ${cr.creaStanza} »."
    override val trascinaMuroStudio: String get() =
        "Deuxième façon : déplacer les murs. Appuyez sur le mur de droite du bureau, maintenez et faites-le glisser jusqu'au " +
            "repère : la pièce s'élargit et la mesure écrite sur le mur change pendant que vous le déplacez."
    override fun misureEsatte(v: Int): String =
        "À la main, la mesure n'est jamais précise. Troisième façon, la plus précise : écrire les mesures. Remettons le bureau à " +
            "$v cm : cliquez sur « $infoTab »."
    override fun muroStudio(numero: Int, lato: WallArrow, v: Int): String =
        "Sous « ${T.schedaStanza.muri} » il y a la longueur intérieure de chaque mur. Dans « ${T.schedaStanza.muro(numero)} », le " +
            "mur ${latoMuro(lato)}, écrivez $v et appuyez sur Entrée : le mur suivant se déplace et le bureau redevient carré."
    override val trascinaAngoloStudio: String get() =
        "Quatrième façon, pour les pièces hors d'équerre : déplacer un angle. Appuyez sur le point bleu de l'angle en bas à " +
            "droite et faites-le glisser jusqu'au repère."
    override val annullaAngolo: String get() =
        "Mais le bureau a des angles droits. Quand vous vous trompez, « ${e.annulla} », ici en haut, enlève la dernière modification : cliquez dessus."

    override val scegliRettangolo: String get() =
        "Cliquez sur « ${cr.rettangolo} » : c'est la chambre, à droite du séjour, avec deux côtés longs et deux courts."
    override val spostaCameraAccanto: String get() =
        "La chambre aussi naît à l'écart : faites-la glisser par son nom jusqu'au contour en pointillés, à droite du séjour. Avec " +
            "l'aimant activé, elle s'accroche toute seule."
    override fun scriviLarghezza(v: Int): String = "« ${T.comuni.larghezza} » est le côté horizontal de la pièce, de gauche à droite sur le plan : écrivez $v."
    override fun scriviProfondita(v: Int): String = "« ${T.comuni.profondita} » est l'autre côté, de haut en bas : écrivez $v."

    override val scegliL: String get() =
        "Cliquez sur « ${T.comuni.formaL} » : c'est le séjour, qui sur le plan est au-dessus du bureau. Beaucoup de séjours et de " +
            "cuisines sont ainsi, avec un angle rentrant."
    override val spostaSoggiorno: String get() =
        "Faites glisser le séjour par son nom jusqu'au contour en pointillés, au-dessus du bureau : quand il arrive près, il s'accroche tout seul."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma demande de quel côté dépasse la partie en plus du L. Regardez la pièce d'en haut, comme sur un plan : ici c'est « $lato »."
    override fun larghezzaPrincipale(v: Int): String = "Le L est fait de deux parties. La partie principale est le plus grand rectangle : dans « ${cr.larghezzaPrincipale} » écrivez $v."
    override fun profonditaPrincipale(v: Int): String = "Dans « ${cr.profonditaPrincipale} » écrivez $v."
    override fun larghezzaProtuberanza(v: Int): String = "L'autre partie est celle qui dépasse. Dans « ${cr.larghezzaProtuberanza} » écrivez $v : elle doit être inférieure à la largeur principale."
    override fun profonditaProtuberanza(v: Int): String = "Dans « ${cr.profonditaProtuberanza} » écrivez de combien elle dépasse : $v."

    override val scegliRilievo: String get() =
        "Cliquez sur « ${cr.rilievo} » : la salle de bains, sous la chambre, a des angles qui ne sont pas tout à fait à 90°. C'est " +
            "aussi pour les pièces de 5 côtés ou plus."
    override val spostaBagnoSotto: String get() =
        "Faites glisser la salle de bains par son nom jusqu'au contour en pointillés, sous la chambre : elle s'accroche toute seule."
    override val tipoBagnoQuattroLati: String get() = "Choisissez le type « ${RoomType.Bagno.label} ». « ${cr.numeroLati} » indique déjà 4, comme les côtés de notre salle de bains."
    override fun primoLatoRilievo(v: Int): String =
        "Appelez A un angle de la pièce, puis B, C et D dans le sens des aiguilles d'une montre. Mesurez les côtés sur le mur, à l'intérieur. Dans « ${cr.lato('A', 'B')} » écrivez $v."
    override fun lato(nome: String, v: Int): String = "Dans « ${cr.lato(nome[0], nome[1])} » écrivez $v."
    override fun diagonaleAC(v: Int): String =
        "Puis une diagonale : de A à l'angle opposé C (AC) ou de B à D (BD), celle que vous avez mesurée. Ici c'est AC, déjà choisie : écrivez $v. " +
            "C'est cette mesure qui dit à Sagoma à quel point les angles sont de travers."
    override val anteprimaECrea: String get() = "Plus bas il y a l'aperçu de la vraie forme. Cliquez sur « ${cr.creaStanza} »."

    override val scegliMuroPerMuro: String get() =
        "Cliquez sur « ${cr.muroMuro} » : l'entrée remplit l'espace qui reste entre les pièces, et vous la dessinez un mur à la " +
            "fois, avec les mesures intérieures, comme dans un logiciel d'architecte."
    override val primoAngolo: String get() =
        "Cliquez ici, sous la chambre et à côté du séjour : c'est le premier angle de l'entrée, à l'intérieur. Sagoma l'accroche " +
            "au bon angle entre les murs."
    override fun verso(n: Int, a: WallArrow): String = "Mur $n : choisissez la direction, « ${freccia(a)} »."
    override fun lunghezzaMuro(v: Int): String = "Dans « ${T.muroPerMuro.lunghezzaCm} » écrivez $v."
    override val aggiungi: String get() = "Cliquez sur « ${T.muroPerMuro.aggiungi} »."
    override fun muriFatti(n: Int, tot: Int): String = "Murs faits : $n sur $tot"
    override val chiudiStanza: String get() = "Il ne manque que le dernier mur, qui revient au premier angle : cliquez sur « ${T.muroPerMuro.chiudiStanza} » et Sagoma le dessine pour vous."
    override fun tipoIngresso(nome: String): String = "Choisissez le type : pour l'entrée « $nome » convient. Plafond et épaisseur des murs sont déjà écrits."

    override fun selezionaCameraTramezzo(nome: String): String =
        "Un mur libre est un mur tout seul, qui ne ferme pas une pièce : par exemple la cloison du dressing dans la chambre, comme " +
            "sur le plan. Le mur naît dans la pièce sélectionnée : cliquez sur le nom de la chambre, « $nome »."
    override val apriElementiMuroSingolo: String get() = "Cliquez maintenant sur « ${e.elementiArchitettonici} », ici en bas."
    override val scegliMuroSingolo: String get() =
        "En bas de la fenêtre il y a « ${cr.muroSingolo} » : il n'apparaît que s'il y a déjà au moins une pièce. Cliquez dessus."
    override val spostaTramezzo: String get() =
        "Le mur naît droit à travers la chambre, d'un mur à l'autre, et il est déjà sélectionné. On le déplace en le faisant " +
            "glisser : appuyez sur le mur, maintenez et amenez-le jusqu'à la ligne pointillée, où va la cloison. Les deux extrémités " +
            "restent accrochées aux murs."
    override val selezionaMuroSingolo: String get() = "Cliquez sur le mur libre pour le sélectionner : les points apparaissent à ses deux extrémités."
    override val accorciaTramezzo: String get() =
        "La cloison ne va pas jusqu'au bout : elle laisse le passage pour entrer dans le dressing. Appuyez sur le point bleu en bas, " +
            "au bout du mur, et faites-le glisser vers le haut jusqu'au repère : le mur raccourcit."
    override val infoTramezzo: String get() = "Comme pour les pièces, les mesures exactes s'écrivent dans « ${T.schedaStanza.info} » : cliquez dessus."
    override fun lunghezzaTramezzo(v: Int): String =
        "Dans « ${T.comuni.lunghezza} » écrivez $v : le mur s'allonge ou raccourcit par sa fin, le départ en haut ne bouge pas."
    override fun spessoreTramezzo(v: Int): String = "Une cloison en plaque de plâtre est fine : dans « ${T.comuni.spessore} » écrivez $v."

    override val portaElementi: String get() = "Les portes, les fenêtres et tout le reste s'ajoutent depuis « ${e.elementiArchitettonici} ». Cliquez dessus."
    override val porte: String get() = "Cliquez sur « ${e.porte} »."
    override val portaUnaAnta: String get() = "Choisissez le type, par exemple « ${e.portaAd1Anta} »."
    override val cliccaMuroPorta: String get() = "Cliquez maintenant sur le mur, là où va la porte : par exemple ici."
    override val finestraElementi: String get() = "Une fenêtre se pose de la même façon : « ${e.elementiArchitettonici} »…"
    override val finestre: String get() = "…« ${e.finestre} »…"
    override val finestraDueAnte: String get() = "…« ${e.finestra2Ante} »."
    override val cliccaMuroFinestra: String get() = "Cliquez sur un mur extérieur, là où va la fenêtre : par exemple ici."
    override val balconeElementi: String get() = "Maintenant un balcon : « ${e.elementiArchitettonici} »…"
    override val balconiTerrazze: String get() = "…« ${e.balconiTerrazze} »…"
    override val balcone: String get() = "…« ${RoomType.Balcone.label} »."
    override val balconeRettangolo: String get() = "La forme se choisit comme pour les pièces : le balcon est un « ${cr.rettangolo} »."
    override fun balconeLarghezza(v: Int): String = "Largeur $v…"
    override fun balconeProfondita(v: Int): String = "…et profondeur $v."
    override val balconeCrea: String get() =
        "Cliquez sur « ${cr.crea} » : le balcon naît à sa place, au-dessus de la chambre, déjà contre la maison (vous pourrez le déplacer en le faisant glisser par son nom)."
    override val portaFinestraElementi: String get() = "Pour sortir sur le balcon il faut une porte-fenêtre : « ${e.elementiArchitettonici} »…"
    override val porteFinestre: String get() = "…« ${e.porteFinestre} »…"
    override val balconeDueAnte: String get() = "…« ${e.balcone2Ante} »."
    override val cliccaMuroBalcone: String get() = "Cliquez sur le mur de la maison où s'appuie le balcon, ici."
    override val impiantiElementi: String get() = "Maintenant les équipements : radiateurs, prises, interrupteurs, points d'eau et luminaires. « ${e.elementiArchitettonici} »…"
    override val impianti: String get() = "…« ${e.impianti} »…"
    override val calorifero: String get() = "…« ${e.calorifero} »."
    override val cliccaMuroCalorifero: String get() = "Radiateurs, prises, interrupteurs et points d'eau se posent en cliquant sur le mur : par exemple ici."
    override val luciElementi: String get() = "Les luminaires, eux, vont à l'intérieur de la pièce. « ${e.elementiArchitettonici} »…"
    override val luci: String get() = "…« ${e.luci} »…"
    override val lampadario: String get() = "…par exemple « ${FixtureKind.Chandelier.label} »."
    override val luceSemplice: String get() = "…et ici « ${T.arredi.puntoLuceSemplice} » : il se place en cliquant dans la pièce."
    override val cliccaStanzaLuce: String get() = "Cliquez dans la pièce, là où va la lumière : par exemple ici."
    override val cliccaStudio: String get() = "Pour finir, l'escalier. Cliquez d'abord dans la pièce où il va, par exemple le bureau, ici."
    override val scalaElementi: String get() = "Puis « ${e.elementiArchitettonici} »…"
    override val scale: String get() = "…« ${e.scale} »…"
    override val chiocciola: String get() = "…et choisissez la forme, par exemple « ${StairKind.Spiral.label} »."
    override val info: String get() =
        "L'escalier est sélectionné. Pour toutes les autres modifications cliquez sur « $infoTab » : c'est pareil pour chaque élément, portes, fenêtres, balcons, équipements et escaliers."

    override val controlloMenu: String get() = "Une aide à utiliser souvent : la vérification du plan, qui cherche les erreurs de dessin qui passent souvent inaperçues. Cliquez sur le menu « ⋮ » ici en haut."
    override val controlloVoce: String get() = "…et « ${T.controllo.voceMenu} »."
    override val controlloRiapri: String get() = "Rouvrez la vérification : le menu « ⋮ » ici en haut…"
    override val controlloCorreggiTutti: String get() =
        "Voici la liste : ⚠ ce qu'il faut corriger, ? ce qu'il faut vérifier, 📏 les minimums des normes du logement. Beaucoup de problèmes " +
            "se règlent tout seuls : « ✓ Tout corriger » corrige tout un groupe d'un coup (par exemple il ajoute les portes qui manquent) et un seul « ${e.annulla} » l'annule. Cliquez dessus."
    override val controlloCorreggi: String get() =
        "Voici la liste : ⚠ ce qu'il faut corriger, ? ce qu'il faut vérifier, 📏 les minimums des normes du logement. Beaucoup de problèmes " +
            "se règlent tout seuls : cliquez sur « ✓ ${T.controllo.correggi} » (« ${e.annulla} » revient en arrière)."
    override val controlloChiudi: String get() =
        "C'est fait : la liste se met à jour toute seule. « ${T.controllo.mostra} » amène au bon endroit et le sélectionne, « ${T.controllo.condividi} » " +
            "envoie la liste à qui vous aide. Cliquez maintenant sur « ${T.controllo.chiudi} »."
    override val controlloSegno: String get() =
        "La vérification travaille aussi toute seule pendant que vous dessinez : le signal en haut à gauche du plan indique combien de problèmes " +
            "il y a (touchez-le pour rouvrir la liste) et un « ! » marque l'endroit de chacun : rouge pour les erreurs, jaune pour ce qu'il faut vérifier, bleu pour les normes. Cliquez sur ce « ! »."
    override val controlloTogli: String get() =
        "La fiche dit ce qu'il y a à cet endroit, avec « ✓ ${T.controllo.correggi} » (quand c'est possible) et « ${T.controllo.mostra} ». Si cela vous " +
            "va comme ça, « ${T.controllo.togliDallaPianta} » enlève le « ! » : le problème reste dans la liste et se remet depuis là. Cliquez sur « ${T.controllo.togliDallaPianta} »."
    override val controlloSegnoDiNuovo: String get() = "Cliquez sur un « ! » du plan : la fiche de cet endroit s'ouvre."
    override val ricalca: String get() =
        "Vous avez déjà un plan, du cadastre, du projet ou même une simple photo ? Au choix de la première pièce d'un projet cliquez sur « ${cr.ricalcaPiantina} »."
    override val usaPiantinaGuida: String get() =
        "D'habitude ici vous cliquez sur « ${T.sfondo.fotoImmagine} » (ou « ${T.sfondo.pdf} ») et vous choisissez le fichier de votre plan. Pour essayer, utilisez le plan du guide : cliquez sur « $usaPiantina »."
    override fun taraturaA(cm: Int): String =
        "Le plan est sous le dessin, mais Sagoma ne connaît pas encore sa taille : il faut le mettre à l'échelle avec une mesure écrite dessus. " +
            "Prenons le mur du haut du séjour, $cm cm : cliquez ici, sur l'angle intérieur en haut à gauche du séjour."
    override val taraturaB: String get() = "Cliquez maintenant là où finit la mesure : l'angle intérieur en haut à droite du séjour, ici."
    override fun misuraReale(cm: Int): String = "Écrivez la vraie distance entre les deux points, celle écrite sur le plan : $cm."
    override val tara: String get() = "Cliquez sur « ${T.sfondo.tara2} » : le plan est maintenant à l'échelle, chaque centimètre est juste."
    override val sceltaDopoTaratura: String get() =
        "L'étalonnage est enregistré : le plan est maintenant à l'échelle. Choisissez maintenant comment dessiner la première pièce : " +
            "avec « ${cr.muroMuro} » vous cliquez ses angles sur le plan, avec une forme vous la créez puis l'amenez sur le plan. Ici nous " +
            "utilisons les formes. Commençons par la chambre, qui est un rectangle : partons d'un carré, pour voir comment il s'allonge. " +
            "Cliquez sur « ${cr.quadrato} »."
    override val cameraDaQuadrato: String get() = "Commençons par la chambre, qui est un rectangle : partons d'un carré, pour voir comment il s'allonge. Cliquez sur « ${cr.quadrato} »."
    override fun latoCortoCamera(v: Int): String = "Écrivez le petit côté de la chambre, lu sur le plan : $v."
    override val spostaCamera: String get() =
        "La chambre est là, mais pas encore à sa place. Pour déplacer une pièce appuyez sur son nom, maintenez et faites-la glisser : lâchez-la " +
            "quand l'angle intérieur en haut à gauche est sur l'angle intérieur de la chambre dessinée (le contour en pointillés)."
    override fun allungaCamera(v: Int): String =
        "La chambre du plan est plus large : $v. Il suffit de déplacer le mur de droite : appuyez sur le mur, maintenez et faites-le glisser jusqu'au repère. " +
            "Pendant le déplacement la mesure change : laissez-la à $v."
    override val bagnoElementi: String get() = "Maintenant la salle de bains, qui est de travers : là aussi on part d'un carré. « ${e.elementiArchitettonici} »…"
    override val bagnoQuadrato: String get() = "…« ${cr.quadrato} »…"
    override fun latoBagno(v: Int): String = "…côté $v, le mur du haut de la salle de bains…"
    override val spostaBagno: String get() = "Faites glisser la salle de bains par son nom sur celle du plan, avec l'angle en haut à gauche à sa place."
    override val angoloC: String get() =
        "Les angles de la salle de bains ne sont pas à 90°. Appuyez sur le point bleu de l'angle en bas à droite et faites-le glisser jusqu'au repère, là où il coïncide avec le plan."
    override val angoloD: String get() = "Puis l'angle en bas à gauche : juste un peu plus bas. Les mesures des murs se mettent à jour pendant le déplacement."

    override val soggiornoElementi: String get() = "Dernière pièce : le séjour, qui est en L. « ${e.elementiArchitettonici} »…"
    override val soggiornoL: String get() = "…« ${T.comuni.formaL} »."
    override val soggiornoMisureDopo: String get() =
        "Choisissez le type « ${RoomType.Soggiorno.label} ». Laissez les mesures telles quelles : vous mettrez le séjour à la bonne taille après, sur le plan."
    override val attaccaSoggiorno: String get() =
        "Sur le plan, le séjour est accolé à la chambre, à sa droite. Faites glisser le séjour par son nom jusqu'au contour en pointillés : " +
            "avec l'aimant « ${T.schedaStanza.si} » activé, quand son angle en haut à droite arrive près de celui de la chambre, il s'accroche tout seul."
    override fun muroSullaPiantina(muro: Int): String =
        "Amenez maintenant les murs sur le plan. Appuyez sur le mur ${nomeMuroSoggiorno(muro)} du séjour et faites-le glisser jusqu'au repère, " +
            "sur le mur dessiné. À peu près suffit : la mesure exacte, vous l'écrirez après."
    override val misureInInfo: String get() =
        "En déplaçant les murs à la main, les mesures ne sont jamais tout à fait celles du plan : regardez celles écrites sur les murs du " +
            "séjour. Les bonnes s'écrivent dans « ${T.schedaStanza.info} » : cliquez dessus."
    override fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Sous « ${T.schedaStanza.muri} » il y a la longueur intérieure de chaque mur. " else "") +
            "Dans « ${T.schedaStanza.muro(numero)} », le mur ${nomeMuroSoggiorno(muro)}, écrivez $v et appuyez sur Entrée."

    override val apriPannello: String get() =
        "Dernière chose : pour mieux voir le dessin sur le plan, cliquez sur « ${e.piantinaDisegno} », ici en bas à droite."
    override val trasparenzaPiantina: String get() =
        "Ici vous réglez le plan : en faisant glisser le curseur il devient plus clair ou plus foncé, l'interrupteur le cache. En " +
            "dessous, la même chose pour le dessin. Quand vous avez fini, fermez le panneau avec « ${T.comuni.chiudi} ». Essayez : faites glisser le curseur du plan."

    override fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "du haut"
        1 -> "de droite"
        2 -> "en bas à droite"
        3 -> "court du milieu"
        4 -> "en bas à gauche"
        else -> "de gauche"
    }
}
