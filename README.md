# FitTrack

Application Android pour **suivre ses séances de salle** et **tracker ses calories et ses macros**.
Pas de compte, pas de serveur, pas de publicité : tout est stocké sur le téléphone.

Une seule exception au « tout local » : quand tu scannes un code-barres, ce code-barres est envoyé à
[Open Food Facts](https://world.openfoodfacts.org) pour récupérer les valeurs nutritionnelles du
produit. Rien d'autre ne sort du téléphone — ni séances, ni repas, ni poids — et le produit reçu est
mis en cache localement pour rester consultable hors ligne.

## Fonctionnalités

- **Séance de salle** : on démarre une séance, on ajoute des exercices depuis un catalogue de
  **1324 exercices**, et pour chacun ses séries (répétitions × kilos). Volume total calculé en direct.
  À la clôture, la durée saisie donne l'estimation des calories brûlées.
- **Catalogue d'exercices** : recherche insensible aux accents, filtres par groupe musculaire et
  par matériel, consignes détaillées **en français**, muscles ciblés et secondaires.
- **Progression** : chaque fiche d'exercice affiche ton **record personnel** et tout l'historique
  de tes séries sur cet exercice.
- **Cardio / sport** : tapis, rameur, foot… une activité et une durée suffisent.
- **Repas au code-barres** : on scanne le produit, l'app récupère ses valeurs nutritionnelles chez
  Open Food Facts, on saisit la quantité en grammes et l'apport est calculé au prorata —
  calories, protéines, glucides, lipides.
- **Saisie libre** : pour la cuisine maison ou un produit absent de la base, avec macros optionnelles.
- **Accueil** : bilan calorique du jour (ingéré / brûlé / net), macros face aux objectifs, volume
  soulevé, progression vers l'objectif, historique des 7 derniers jours.
- **Profil** : poids, objectif calorique et objectifs de macros, utilisés pour les calculs.

### Estimation des calories brûlées

Formule MET (Compendium of Physical Activities), la même que celle des trackers grand public :

```
kcal = MET × 3,5 × poids(kg) ÷ 200 × durée(min)
```

La musculation compte pour un MET de 5,0 ; chaque activité cardio a le sien (course 9,8 ·
corde à sauter 11,0 · rameur 7,0…). C'est une estimation, pas une mesure médicale.

## Stack technique

| | |
|---|---|
| Langage | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 (thème dynamique Android 12+) |
| Navigation | Navigation Compose (4 onglets + écrans de détail) |
| Base de données | Room (SQLite) |
| Préférences | DataStore |
| Catalogue | JSON embarqué dans les assets, chargé en mémoire au lancement |
| Scan | CameraX + ML Kit (barcode-scanning groupé, fonctionne sans Play Services) |
| Nutrition | API Open Food Facts v2 en lecture, `HttpURLConnection`, cache Room |
| Build | Gradle 8.11 + AGP 8.7, `minSdk 26`, `targetSdk 35` |

Architecture : `data` (entités Room, DAO, catalogue, repository) → `ui` (ViewModel exposant des
`StateFlow`) → écrans Compose sans état. Le `FitTrackRepository` est la seule porte d'entrée vers
les données : c'est le seul fichier à changer le jour où le stockage évolue.

## Démarrer

Prérequis : [Android Studio](https://developer.android.com/studio) (version récente) et un JDK 17.

```bash
git clone https://github.com/Kaz229/fittrack-android.git
cd fittrack-android
./gradlew assembleDebug              # génère app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # tests unitaires (calculs, parsing des réponses OFF)
./gradlew connectedDebugAndroidTest  # tests sur appareil : journal alimentaire de bout en bout
```

Les tests instrumentés interrogent réellement Open Food Facts. Sans connexion, ils n'échouent pas
mais tracent leur abandon dans logcat (`FoodJournalTest`).

Dans Android Studio : `File > Open`, sélectionner le dossier, puis **Run** sur un émulateur ou un
téléphone en mode développeur.

L'APK de debug est aussi produit par la CI GitHub Actions à chaque push
(onglet *Actions* → artefact `fittrack-debug-apk`).

## Structure

```
app/src/main/
├── assets/exercises.json          # catalogue des 1324 exercices (FR)
└── java/com/kaz229/fittrack/
    ├── MainActivity.kt
    ├── data/
    │   ├── Model.kt               # Session, ExerciseSet, Meal, bilans
    │   ├── Nutrition.kt           # FoodProduct, portions, macros et objectifs
    │   ├── OpenFoodFacts.kt       # client de lecture de l'API + parsing
    │   ├── Exercise.kt            # exercice du catalogue + libellés FR
    │   ├── ExerciseCatalog.kt     # chargement, recherche, filtres
    │   ├── Activities.kt          # activités cardio (MET) et calcul des calories
    │   ├── Daos.kt                # requêtes Room
    │   ├── FitTrackDatabase.kt
    │   ├── UserPreferences.kt     # poids + objectif (DataStore)
    │   └── FitTrackRepository.kt  # toute la logique métier
    └── ui/
        ├── FitTrackApp.kt         # navigation et onglets
        ├── FitTrackViewModel.kt
        ├── Format.kt              # affichage des poids, volumes et dates
        ├── theme/Theme.kt
        └── screens/               # Accueil, Séances, Séance, Exercices, Fiche, Repas, Scan, Profil
```

## Données des exercices

Le catalogue provient de [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset),
dont **les données** (noms, groupes musculaires, matériel, consignes multilingues) sont publiées
sous licence MIT. `assets/exercises.json` en est un extrait : identifiant, nom, groupe musculaire,
matériel, muscles ciblés et consignes en français.

Les **images et GIFs** de ce jeu de données appartiennent à [Gym visual](https://gymvisual.com/) et
ne sont pas redistribuables sans licence propre : ils ne sont donc **pas** embarqués ici. Pour
illustrer les exercices, il faudra soit obtenir une licence auprès de Gym visual, soit utiliser des
médias libres.

## Données nutritionnelles

Les produits scannés viennent d'[Open Food Facts](https://world.openfoodfacts.org), base
alimentaire ouverte et collaborative. Ses données sont publiées sous licence
[ODbL](https://opendatacommons.org/licenses/odbl/) ; elles sont renseignées par des contributeurs
et peuvent être incomplètes ou erronées.

L'API est utilisée en lecture seule, sans authentification, avec un User-Agent identifiant l'app
comme sa politique d'usage l'exige. Elle limite les lectures à **15 requêtes par minute et par IP** :
le cache local des produits déjà scannés sert autant à respecter cette limite qu'à fonctionner
hors ligne.

## Idées pour la suite

- Modèles de séances récurrentes (Push / Pull / Legs) à charger en un tap.
- Chronomètre de repos entre les séries.
- Graphiques de progression par exercice et courbe de poids.
- Réutiliser un produit déjà scanné sans rescanner (liste des aliments récents).
- Typage des séries (échauffement, échec, drop-set) pour un volume plus juste.
- Export / import CSV et intégration Health Connect.
