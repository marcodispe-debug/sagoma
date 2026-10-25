package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoFr : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info
    private val ok get() = e.hoCapito

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "Forme en L"
        Lesson.Elements -> "Portes, fenêtres et le reste"
        Lesson.OverPlan -> "Sur votre plan"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guide · Leçon $n sur $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Étape $n sur $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Bienvenue dans Sagoma !"
        CourseStep.ElementsDone -> "Chaque élément, depuis « $infoTab »"
        CourseStep.Finished -> "Vous avez fini le guide !"
        else -> "C'est fait !"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "Je vous guide pas à pas pour dessiner un petit appartement d'essai, en 7 courtes leçons. C'est vous qui faites les actions : à chaque " +
                "étape un cadre orange vous montre où cliquer. C'est un projet juste pour s'entraîner : vos projets ne sont pas touchés."
        CourseStep.SquareDone -> "Le bureau carré de 3,30 × 3,30 m est sur le plan, avec les mesures écrites sur les murs. Dans la prochaine leçon vous ajoutez une pièce rectangulaire."
        CourseStep.RectDone -> "Voici la chambre de 4,00 × 3,30 m, à côté du bureau. Dans la prochaine leçon : un séjour en forme de L."
        CourseStep.LDone -> "Voici le séjour en L : 6,40 × 2,70 m, avec la partie de 3,00 × 1,90 m qui dépasse. Dans la prochaine leçon : une pièce hors d'équerre, d'après des mesures prises au mètre."
        CourseStep.SurveyDone -> "Voici la salle de bains avec sa vraie forme, un peu hors d'équerre comme en réalité. Dans la prochaine leçon vous dessinez une pièce mur par mur."
        CourseStep.WallsDone -> "Voici l'entrée, avec la forme que vous avez dessinée mur par mur. Vous avez vu les façons de créer une pièce. Dans la prochaine leçon : portes, fenêtres, balcon, équipements et escaliers."
        CourseStep.ElementsDone ->
            "Dans « $infoTab » vous changez les mesures, le type et tout le reste de l'élément sélectionné ; il se ferme avec « ▼ ». Pour chaque élément : " +
                "vous le choisissez dans « ${e.elementiArchitettonici} », vous le placez sur le plan, et le reste depuis « $infoTab ». Dernière leçon : " +
                "construire les pièces sur votre plan. Il faut un nouveau projet : je le prépare."
        CourseStep.Finished ->
            "Voici la salle de bains de travers, identique à celle du plan. Faites de même avec les autres pièces : une forme pour commencer, puis " +
                "murs et angles à leur place. Vous pouvez maintenant dessiner votre logement ! Le guide se refait quand vous voulez depuis « ${e.guide} »."
        else -> ""
    }

    override val avanti: String get() = "Suivant"
    override val apriProgettoNuovo: String get() = "Ouvrir le nouveau projet"
    override val bastaCosi: String get() = "Ça suffit"
    override val vaiAiProgetti: String get() = "Aller à vos projets"
    override val nienteDaIndicare: String get() = "Faites l'action de cette étape, ou cliquez sur « ${e.saltaPasso} »."
    override val tornaIndietro: String get() = "Ce n'est pas la forme de cette étape : cliquez sur « ${T.comuni.indietro} » et choisissez la bonne."
    override val vaiAllaLezione: String get() = "Ou allez à la leçon :"
    override val voceMenu: String get() = "Guide interactif (7 leçons)"
    override val progettoProva: String get() = "Essai du guide"
    override val progettoProvaPlanimetria: String get() = "Essai du guide · plan"
    override val usaPiantina: String get() = "🧭 Utiliser le plan du guide"
    override val ingresso: String get() = "Entrée"
    override val notaMisure: String get() = "mesures en cm, au nu intérieur des murs"

    override val apriElementi: String get() = "Cliquez sur « ${e.elementiArchitettonici} », ici en bas."
    override val apriElementiAltraStanza: String get() = "Pour ajouter une autre pièce cliquez sur « ${e.elementiArchitettonici} », ici en bas."
    override val poiStanza: String get() = "Cliquez sur « ${e.stanza} » : la fenêtre des formes revient."
    override fun tipo(nome: String): String = "Choisissez le type : « $nome »."
    override val creaStanza: String get() = "Cliquez sur « ${cr.creaStanza} »."

    override val scegliQuadrato: String get() =
        "Quand vous ouvrez un nouveau projet, Sagoma vous demande tout de suite la forme de la première pièce. Cliquez sur « ${cr.quadrato} » : c'est la forme d'une pièce aux quatre côtés égaux."
    override val tipoStudio: String get() = "Dans « ${cr.tipoStanza} » cliquez sur « ${RoomType.Studio.label} » : Sagoma donne à la pièce le bon nom et la bonne couleur."
    override fun scriviLato(v: Int): String = "Dans le champ « ${cr.lato2} » écrivez $v. C'est la mesure intérieure, d'un mur à l'autre : celle que vous prenez au mètre."
    override val soffittoECrea: String get() = "La hauteur sous plafond est déjà écrite (270 cm) : changez-la seulement si votre pièce est différente. Cliquez maintenant sur « ${cr.creaStanza} »."

    override val scegliRettangolo: String get() = "Cliquez sur « ${cr.rettangolo} » : deux côtés longs et deux courts."
    override fun scriviLarghezza(v: Int): String = "« ${T.comuni.larghezza} » est le côté horizontal de la pièce, de gauche à droite sur le plan : écrivez $v."
    override fun scriviProfondita(v: Int): String = "« ${T.comuni.profondita} » est l'autre côté, de haut en bas : écrivez $v."

    override val scegliL: String get() = "Cliquez sur « ${T.comuni.formaL} » : beaucoup de séjours et de cuisines sont ainsi, avec un angle rentrant."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma demande de quel côté dépasse la partie en plus du L. Regardez la pièce d'en haut, comme sur un plan : ici c'est « $lato »."
    override fun larghezzaPrincipale(v: Int): String = "Le L est fait de deux parties. La partie principale est le plus grand rectangle : dans « ${cr.larghezzaPrincipale} » écrivez $v."
    override fun profonditaPrincipale(v: Int): String = "Dans « ${cr.profonditaPrincipale} » écrivez $v."
    override fun larghezzaProtuberanza(v: Int): String = "L'autre partie est celle qui dépasse. Dans « ${cr.larghezzaProtuberanza} » écrivez $v : elle doit être inférieure à la largeur principale."
    override fun profonditaProtuberanza(v: Int): String = "Dans « ${cr.profonditaProtuberanza} » écrivez de combien elle dépasse : $v."

    override val scegliRilievo: String get() = "Cliquez sur « ${cr.rilievo} » : c'est pour les angles qui ne sont pas tout à fait à 90°, ou les pièces de 5 côtés ou plus."
    override val tipoBagnoQuattroLati: String get() = "Choisissez le type « ${RoomType.Bagno.label} ». « ${cr.numeroLati} » indique déjà 4, comme les côtés de notre salle de bains."
    override fun primoLatoRilievo(v: Int): String =
        "Appelez A un angle de la pièce, puis B, C et D dans le sens des aiguilles d'une montre. Mesurez les côtés sur le mur, à l'intérieur. Dans « ${cr.lato('A', 'B')} » écrivez $v."
    override fun lato(nome: String, v: Int): String = "Dans « ${cr.lato(nome[0], nome[1])} » écrivez $v."
    override fun diagonaleAC(v: Int): String =
        "Puis une diagonale : de A à l'angle opposé C (AC) ou de B à D (BD), celle que vous avez mesurée. Ici c'est AC, déjà choisie : écrivez $v. " +
            "C'est cette mesure qui dit à Sagoma à quel point les angles sont de travers."
    override val anteprimaECrea: String get() = "Plus bas il y a l'aperçu de la vraie forme. Cliquez sur « ${cr.creaStanza} »."

    override val scegliMuroPerMuro: String get() = "Cliquez sur « ${cr.muroMuro} » : vous dessinez la pièce un mur à la fois, avec les mesures intérieures, comme dans un logiciel d'architecte."
    override val primoAngolo: String get() = "Cliquez sur le plan là où commence le premier mur, par exemple ici : c'est le premier angle de la pièce, à l'intérieur."
    override fun verso(n: Int, a: WallArrow): String = "Mur $n : choisissez la direction, « ${freccia(a)} »."
    override fun lunghezzaMuro(v: Int): String = "Dans « ${T.muroPerMuro.lunghezzaCm} » écrivez $v."
    override val aggiungi: String get() = "Cliquez sur « ${T.muroPerMuro.aggiungi} »."
    override fun muriFatti(n: Int, tot: Int): String = "Murs faits : $n sur $tot"
    override val chiudiStanza: String get() = "Il ne manque que le dernier mur, qui revient au premier angle : cliquez sur « ${T.muroPerMuro.chiudiStanza} » et Sagoma le dessine pour vous."
    override fun tipoIngresso(nome: String): String = "Choisissez le type : pour l'entrée « $nome » convient. Plafond et épaisseur des murs sont déjà écrits."

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
    override val balconeCrea: String get() = "Cliquez sur « ${cr.crea} » : le balcon se pose déjà contre la maison (vous pourrez le déplacer en le faisant glisser par son nom)."
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
    override val cliccaStanzaLuce: String get() = "Cliquez dans la pièce, là où va la lumière : par exemple ici."
    override val cliccaStudio: String get() = "Pour finir, l'escalier. Cliquez d'abord dans la pièce où il va, par exemple le bureau, ici."
    override val scalaElementi: String get() = "Puis « ${e.elementiArchitettonici} »…"
    override val scale: String get() = "…« ${e.scale} »…"
    override val chiocciola: String get() = "…et choisissez la forme, par exemple « ${StairKind.Spiral.label} »."
    override val info: String get() =
        "L'escalier est sélectionné. Pour toutes les autres modifications cliquez sur « $infoTab » : c'est pareil pour chaque élément, portes, fenêtres, balcons, équipements et escaliers."

    override fun miniGuida(t: Tip): String = when (t) {
        Tip.Opening -> "La première fois que vous posez une porte, son mini guide s'ouvre : comment la faire glisser le long du mur, et mesures et sens depuis « $infoTab ». Lisez-le et cliquez sur « $ok »."
        Tip.Outdoor -> "Le balcon aussi a son mini guide : à la place des murs il y a le garde-corps, et dans « $infoTab » vous choisissez rambarde, muret ou verre. Cliquez sur « $ok »."
        Tip.Fixture -> "Mini guide des équipements : ceux au mur glissent le long du mur, et dans « $infoTab » vous réglez hauteur et dimensions. Cliquez sur « $ok »."
        Tip.Stair -> "Quand vous posez un escalier, son mini guide s'ouvre et explique tout : comment le déplacer et le coller à un mur, et ce que vous changez dans « $infoTab ». Lisez-le et cliquez sur « $ok »."
        else -> "Un mini guide s'est ouvert : lisez-le et cliquez sur « $ok »."
    }

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
    override val chiudiPannello: String get() =
        "Ici à droite il y a le panneau « ${e.piantinaDisegno} » : il rend le plan plus clair ou plus foncé, ou le cache. Pour l'instant fermez-le avec « ${e.chiudi} »."
    override val esciMuroPerMuro: String get() =
        "« ${cr.muroMuro} » a démarré sur le plan : vous pourriez cliquer les angles des pièces un par un. Ici nous utilisons plutôt les formes et " +
            "les ajustons sur le plan : cliquez sur « ${T.comuni.esci} »."
    override val cameraDaQuadrato: String get() = "Commençons par la chambre, qui est un rectangle : partons d'un carré, pour voir comment il s'allonge. Cliquez sur « ${cr.quadrato} »."
    override fun latoCortoCamera(v: Int): String = "Écrivez le petit côté de la chambre, lu sur le plan : $v."
    override val spostaCamera: String get() =
        "La chambre est là, mais pas encore à sa place. Pour déplacer une pièce appuyez sur son nom, maintenez et faites-la glisser : lâchez-la " +
            "quand l'angle en haut à gauche est sur celui de la chambre dessinée (le contour en pointillés)."
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
}
