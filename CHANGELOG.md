# Journal des versions

Numérotation `MAJEUR.MINEUR.CORRECTIF`. Le numéro vit dans `app/build.gradle.kts`
(`versionName`), et `versionCode` s'incrémente de un à chaque version publiée.

## 1.1.1 (2026-11-02)

### Corrections
- Les macros proposées étaient intenables en cas de surpoids : rapportées au poids total, protéines et lipides mangeaient presque tout l'objectif et ne laissaient qu'une cinquantaine de grammes de glucides. Les protéines suivent maintenant un poids de référence plafonné — la masse grasse n'a pas de besoin protéique — et les lipides sont une part des calories, non un montant par kilo. Pour 126 kg à 2 353 kcal : 186 g de protéines, 237 g de glucides et 73 g de lipides, contre 277 / 57 / 113 avant.

## 1.1.0 (2026-11-02)

### Nouveautés
- Une ligne du journal se **déplace d'un repas à l'autre** sans la ressaisir.
- **Calcul des besoins** à partir d'un questionnaire : taille, âge, sexe, pas quotidiens, séances de salle et sorties cardio avec leurs intensités, objectif. L'objectif calorique et les macros s'en déduisent et s'appliquent d'un bouton.
- Pour une perte, le rythme hebdomadaire visé se choisit. L'app dit la perte **réellement atteignable** : quand le rythme demandé passerait sous le minimum vital, la cible est bornée et l'écart annoncé.
- Le **poids est repris de Health Connect** à chaque lancement — celui qu'écrit la balance.

### Corrections
- Plus de bandeau en haut des onglets : la barre épouse le fond.
- Les champs numériques du questionnaire gardaient mal le curseur ; « 180 » pouvait s'afficher « 801 ».

## 1.0.0 (2026-11-02)

Première version complète : l'application couvre désormais la salle et la nutrition
de bout en bout, et ses données ne vivent plus uniquement sur le téléphone.

### Salle
- Séances de musculation : exercices pris dans un catalogue de 1324 entrées, séries en
  répétitions × kilos, volume calculé en direct, record personnel et historique par exercice.
- Cardio et sport : une activité, une durée, calories estimées par la formule MET.
- Rappel au bout de dix minutes sans nouvelle série : une séance laissée ouverte fausse sa
  durée et ses calories.
- Les séances enregistrées dans une autre application (Lyfta, une montre) sont reprises via
  Health Connect. Quand la même séance arrive deux fois — Lyfta la relaie sur Strava, qui
  l'écrit à son tour — seule celle de l'application où elle a été saisie est gardée.

### Nutrition
- Calories **et macros** : protéines, glucides, lipides, avec leurs objectifs journaliers.
- Scan du code-barres (CameraX et ML Kit), valeurs nutritionnelles reprises d'Open Food Facts
  et mises en cache pour rester consultables hors ligne.
- Saisie du code au clavier quand la caméra ne peut pas lire l'étiquette, avec vérification de
  la clé de contrôle avant toute requête.
- Recherche par nom : les aliments déjà enregistrés passent devant — « sky » retrouve le skyr
  d'avant-hier, qui se réinscrit en un tap — puis viennent les produits d'Open Food Facts.
- Saisie libre pour la cuisine maison, macros optionnelles.

### Échanges
- **Health Connect** : écriture des séances, des calories brûlées et de la nutrition ; lecture
  du poids et des séances venues d'ailleurs.
- **Kazis** : les repas dictés à l'assistant (Telegram ou MCP) arrivent dans le journal, et
  séances, séries et repas sont sauvegardés sur le serveur personnel — la seule copie des
  charges hors du téléphone. Restauration sur une installation neuve.

### Apparence
- Direction « bento » : un seul accent vif pour les calories, trois teintes sourdes pour les
  macros, contrastes au-dessus du seuil AA dans les deux thèmes.
- Thème clair ou sombre au choix, ou celui du système.

### Ce que la version ne fait pas encore
- Pas de modèles de séances récurrentes, pas de chronomètre de repos, pas de graphiques de
  progression par exercice.
- Pas d'illustrations d'exercices : les médias du jeu de données d'origine ne sont pas
  redistribuables.
- L'APK est signé avec la clé de debug ; une version release signée reste à produire.
