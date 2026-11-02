package com.kaz229.fittrack.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Point d'entrée unique vers les données. Les écrans et le ViewModel ne connaissent
 * ni Room ni DataStore : le jour où le stockage change, seul ce fichier bouge.
 */
class FitTrackRepository(
    private val context: Context? = null,
    private val sessionDao: SessionDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val mealDao: MealDao,
    private val foodProductDao: FoodProductDao,
    private val userPreferences: UserPreferences,
    val exercises: ExerciseCatalog,
    private val openFoodFacts: OpenFoodFactsClient = OpenFoodFactsClient(),
    private val healthConnect: HealthConnectManager? = null,
    private val kazis: KazisClient = KazisClient(),
) {

    val profile: Flow<UserProfile> = userPreferences.profile

    // --- Séances -------------------------------------------------------------

    fun allSessions(): Flow<List<Session>> = sessionDao.observeAll()

    fun session(id: Long): Flow<Session?> = sessionDao.observeById(id)

    /** Les séries d'une séance, regroupées par exercice dans l'ordre où ils ont été ajoutés. */
    fun sessionBlocks(sessionId: Long): Flow<List<ExerciseBlock>> =
        exerciseSetDao.observeForSession(sessionId).map { sets ->
            sets.groupBy { it.exerciseId }
                .map { (exerciseId, exerciseSets) -> ExerciseBlock(exerciseId, exerciseSets) }
                .sortedBy { block -> block.sets.minOf { it.position } }
        }

    /** Crée une séance salle vide et renvoie son identifiant. */
    suspend fun startGymSession(date: LocalDate = LocalDate.now()): Long {
        val id = sessionDao.insert(
            Session(
                kind = SessionKind.GYM.id,
                epochDay = date.toEpochDay(),
                finished = false,
            ),
        )
        context?.let { SessionReminder.schedule(it, id) }
        return id
    }

    /**
     * Clôture une séance salle : la durée saisie sert à estimer les calories brûlées
     * (MET de la musculation × poids de l'utilisateur).
     */
    suspend fun finishGymSession(sessionId: Long, durationMinutes: Int, note: String) {
        val session = sessionDao.findById(sessionId) ?: return
        val weight = userPreferences.profile.first().weightKg
        val calories = Activities.caloriesBurned(Activities.strengthTraining, durationMinutes, weight)
        sessionDao.update(
            session.copy(
                durationMinutes = durationMinutes,
                caloriesBurned = calories,
                note = note,
                finished = true,
            ),
        )
        context?.let { SessionReminder.cancel(it, sessionId) }
    }

    /** Enregistre une séance cardio ou un sport, calories estimées immédiatement. */
    suspend fun addCardioSession(
        activityId: String,
        durationMinutes: Int,
        note: String,
        date: LocalDate = LocalDate.now(),
    ) {
        val activity = Activities.byId(activityId)
        val weight = userPreferences.profile.first().weightKg
        sessionDao.insert(
            Session(
                kind = SessionKind.CARDIO.id,
                epochDay = date.toEpochDay(),
                activityId = activity.id,
                durationMinutes = durationMinutes,
                caloriesBurned = Activities.caloriesBurned(activity, durationMinutes, weight),
                note = note,
                finished = true,
            ),
        )
    }

    suspend fun deleteSession(session: Session) {
        context?.let { SessionReminder.cancel(it, session.id) }
        sessionDao.delete(session)
    }

    // --- Séries --------------------------------------------------------------

    suspend fun addSet(sessionId: Long, exerciseId: String, reps: Int, weightKg: Double) {
        val session = sessionDao.findById(sessionId) ?: return
        exerciseSetDao.insert(
            ExerciseSet(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = exerciseSetDao.lastPosition(sessionId) + 1,
                reps = reps,
                weightKg = weightKg,
                epochDay = session.epochDay,
            ),
        )
        // Une série repousse le rappel : seul le silence doit le déclencher
        context?.let { SessionReminder.schedule(it, sessionId) }
    }

    suspend fun deleteSet(set: ExerciseSet) = exerciseSetDao.delete(set)

    fun exerciseHistory(exerciseId: String): Flow<List<ExerciseSet>> =
        exerciseSetDao.observeForExercise(exerciseId)

    fun exerciseBestSet(exerciseId: String): Flow<ExerciseSet?> =
        exerciseSetDao.observeBestSet(exerciseId)

    fun volumeForDay(date: LocalDate): Flow<Double> =
        exerciseSetDao.observeVolumeForDay(date.toEpochDay())

    fun setCountForDay(date: LocalDate): Flow<Int> =
        exerciseSetDao.observeSetCountForDay(date.toEpochDay())

    // --- Repas ---------------------------------------------------------------

    fun mealsForDay(date: LocalDate): Flow<List<Meal>> = mealDao.observeForDay(date.toEpochDay())

    /** Saisie libre : plat maison ou produit absent d'Open Food Facts. Macros optionnelles. */
    suspend fun addMeal(
        name: String,
        calories: Int,
        mealType: MealType,
        proteinG: Double = 0.0,
        carbsG: Double = 0.0,
        fatG: Double = 0.0,
        date: LocalDate = LocalDate.now(),
    ) {
        mealDao.insert(
            Meal(
                name = name,
                calories = calories,
                mealType = mealType.id,
                epochDay = date.toEpochDay(),
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
            ),
        )
    }

    suspend fun deleteMeal(meal: Meal) = mealDao.delete(meal)

    /** Réinscrit un aliment déjà saisi, à la date du jour et au repas choisi. */
    suspend fun repeatMeal(meal: Meal, mealType: MealType, date: LocalDate = LocalDate.now()) {
        mealDao.insert(
            meal.copy(id = 0, mealType = mealType.id, epochDay = date.toEpochDay()),
        )
    }

    // --- Recherche d'aliments ------------------------------------------------

    /**
     * Ce que la recherche propose : d'abord ce qui est déjà connu du téléphone,
     * ensuite seulement ce qu'Open Food Facts renvoie.
     *
     * L'historique passe devant parce qu'on remange surtout les mêmes choses, et qu'il
     * répond sans réseau. Les deux listes sont rendues séparément : recopier un repas
     * déjà saisi et peser un produit ne sont pas le même geste.
     */
    suspend fun searchFoods(query: String): FoodSearch {
        val trimmed = query.trim()
        if (trimmed.length < 2) return FoodSearch()
        val history = mealDao.searchHistory(trimmed)
        val cached = foodProductDao.search(trimmed)
        val remote = openFoodFacts.search(trimmed)
            .filter { product -> cached.none { it.barcode == product.barcode } }
        return FoodSearch(history = history, products = cached + remote)
    }

    // --- Aliments scannés ----------------------------------------------------

    /**
     * Cherche un code-barres : cache local d'abord, réseau ensuite.
     *
     * Le cache évite de retomber sur la limite d'Open Food Facts (15 requêtes par
     * minute) et rend un produit déjà scanné utilisable sans réseau.
     */
    suspend fun lookupFood(barcode: String, today: LocalDate = LocalDate.now()): FoodLookup {
        foodProductDao.findByBarcode(barcode)?.let {
            return FoodLookup.Found(it, fromCache = true)
        }
        val result = openFoodFacts.fetch(barcode)
        if (result is FoodLookup.Found) {
            val product = result.product.copy(fetchedAtEpochDay = today.toEpochDay())
            foodProductDao.upsert(product)
            return FoodLookup.Found(product, fromCache = false)
        }
        return result
    }

    /** Enregistre une portion pesée d'un produit scanné, macros calculées au prorata. */
    suspend fun addFoodMeal(
        product: FoodProduct,
        grams: Double,
        mealType: MealType,
        date: LocalDate = LocalDate.now(),
    ) {
        val portion = product.portion(grams)
        mealDao.insert(
            Meal(
                name = product.displayName,
                calories = portion.calories,
                mealType = mealType.id,
                epochDay = date.toEpochDay(),
                proteinG = portion.proteinG,
                carbsG = portion.carbsG,
                fatG = portion.fatG,
                barcode = product.barcode,
                grams = grams,
            ),
        )
    }

    // --- Bilans --------------------------------------------------------------

    fun macrosForDay(date: LocalDate): Flow<MacroTotals> =
        mealDao.observeMacrosForDay(date.toEpochDay())

    fun summaryForDay(date: LocalDate): Flow<DailySummary> {
        val day = date.toEpochDay()
        return combine(
            mealDao.observeCaloriesForDay(day),
            sessionDao.observeCaloriesForDay(day),
        ) { caloriesIn, caloriesOut ->
            DailySummary(epochDay = day, caloriesIn = caloriesIn, caloriesOut = caloriesOut)
        }
    }

    /** Bilan des [days] derniers jours, du plus ancien au plus récent. */
    fun recentSummaries(today: LocalDate, days: Int): Flow<List<DailySummary>> {
        val fromDay = today.minusDays((days - 1).toLong()).toEpochDay()
        return combine(
            mealDao.observeDailyTotals(fromDay),
            sessionDao.observeDailyTotals(fromDay),
        ) { intake, burned ->
            val intakeByDay = intake.associate { it.epochDay to it.total }
            val burnedByDay = burned.associate { it.epochDay to it.total }
            (0 until days).map { offset ->
                val day = fromDay + offset
                DailySummary(
                    epochDay = day,
                    caloriesIn = intakeByDay[day] ?: 0,
                    caloriesOut = burnedByDay[day] ?: 0,
                )
            }
        }
    }

    // --- Repas dictés à Kazis ------------------------------------------------

    val kazisSettings: Flow<KazisSettings> = userPreferences.kazis

    suspend fun setKazisSettings(settings: KazisSettings) = userPreferences.setKazis(settings)

    /**
     * Récupère les repas déposés par un assistant, les enregistre, puis confirme.
     *
     * Un repas donné par code-barres arrive sans valeurs : c'est ici qu'il est résolu
     * chez Open Food Facts, en passant par le cache. Un produit introuvable est laissé
     * dans la file plutôt que d'être enregistré faux — il sera retenté, et reste
     * annulable depuis Telegram.
     */
    suspend fun fetchKazisMeals(today: LocalDate = LocalDate.now()): KazisFetch {
        val settings = userPreferences.kazis.first()
        if (!settings.configured) return KazisFetch.NotConfigured
        return try {
            val pending = kazis.pendingMeals(settings.url, settings.token)
            val saved = mutableListOf<Long>()
            var skipped = 0
            for (meal in pending) {
                if (saveKazisMeal(meal, today)) saved += meal.id else skipped++
            }
            kazis.acknowledge(settings.url, settings.token, saved)
            KazisFetch.Success(added = saved.size, skipped = skipped)
        } catch (_: KazisUnauthorized) {
            KazisFetch.Unauthorized
        } catch (e: Exception) {
            KazisFetch.Failed(e.message ?: "serveur injoignable")
        }
    }

    /** Enregistre un repas venu de Kazis. Faux si le produit n'a pas pu être résolu. */
    private suspend fun saveKazisMeal(meal: KazisMeal, today: LocalDate): Boolean {
        if (!meal.needsLookup) {
            mealDao.insert(
                Meal(
                    name = meal.name,
                    calories = meal.calories,
                    mealType = meal.mealType.id,
                    epochDay = meal.day.toEpochDay(),
                    proteinG = meal.proteinG,
                    carbsG = meal.carbsG,
                    fatG = meal.fatG,
                    barcode = meal.barcode,
                    grams = meal.grams,
                ),
            )
            return true
        }
        val lookup = lookupFood(meal.barcode!!, today)
        if (lookup !is FoodLookup.Found) return false
        addFoodMeal(lookup.product, meal.grams!!, meal.mealType, meal.day)
        return true
    }

    val lastKazisBackup: Flow<Long?> = userPreferences.lastBackup

    /**
     * Envoie tout le contenu de l'app à Kazis, qui le conserve.
     *
     * C'est la seule copie hors du téléphone des séries et des charges : Health Connect
     * ne les porte pas.
     */
    suspend fun backupToKazis(): KazisBackup {
        val settings = userPreferences.kazis.first()
        if (!settings.configured) return KazisBackup.NotConfigured
        return try {
            val result = kazis.backup(
                baseUrl = settings.url,
                token = settings.token,
                device = userPreferences.deviceId(),
                sessions = sessionDao.all(),
                sets = exerciseSetDao.all(),
                meals = mealDao.all(),
            )
            userPreferences.setLastBackup(System.currentTimeMillis() / 1000)
            result
        } catch (_: KazisUnauthorized) {
            KazisBackup.Unauthorized
        } catch (e: Exception) {
            KazisBackup.Failed(e.message ?: "serveur injoignable")
        }
    }

    /**
     * Réinjecte la sauvegarde conservée par Kazis.
     *
     * Seulement dans une app vide : les identifiants d'origine sont restaurés tels
     * quels, et ils écraseraient des séances créées depuis. C'est le cas d'un
     * téléphone neuf, le seul où la restauration a un sens.
     */
    suspend fun restoreFromKazis(): KazisRestore {
        val settings = userPreferences.kazis.first()
        if (!settings.configured) return KazisRestore.NotConfigured
        if (sessionDao.all().isNotEmpty() || mealDao.all().isNotEmpty()) {
            return KazisRestore.Failed("cette application contient déjà des données")
        }
        return try {
            val archive = kazis.archive(settings.url, settings.token)
            if (archive.isEmpty) return KazisRestore.Empty
            sessionDao.insertAll(archive.sessions)
            exerciseSetDao.insertAll(archive.sets)
            mealDao.insertAll(archive.meals)
            KazisRestore.Success(archive.sessions.size, archive.sets.size, archive.meals.size)
        } catch (_: KazisUnauthorized) {
            KazisRestore.Unauthorized
        } catch (e: Exception) {
            KazisRestore.Failed(e.message ?: "serveur injoignable")
        }
    }

    // --- Health Connect ------------------------------------------------------

    val lastHealthSync: Flow<Long?> = userPreferences.lastHealthSync

    fun healthConnectAvailability(): HealthConnectAvailability =
        healthConnect?.availability() ?: HealthConnectAvailability.UNSUPPORTED

    suspend fun healthConnectPermissionsGranted(): Boolean =
        healthConnect?.hasAllPermissions() ?: false

    /**
     * Reprend les séances enregistrées par d'autres applications (Lyfta, montre…).
     *
     * Les calories ne sont pas recalculées : celles de l'application d'origine valent
     * mieux qu'une estimation MET faite sur une durée seule. Quand elle n'en donne pas,
     * on retombe sur la formule MET, comme pour une séance saisie ici.
     */
    suspend fun importHealthSessions(today: LocalDate = LocalDate.now()): Int {
        val manager = healthConnect ?: return 0
        val foreign = manager.foreignSessions(today, SYNC_DAYS)

        // Une séance importée qui n'est plus dans la liste n'a plus lieu d'être : soit
        // elle a été supprimée à la source, soit c'était le doublon relayé par une autre
        // application. Les séances saisies ici n'ont pas d'identifiant externe et ne sont
        // jamais touchées.
        val fromDay = today.minusDays((SYNC_DAYS - 1).toLong()).toEpochDay()
        val stillThere = foreign.map { it.id }.toSet()
        for (session in sessionDao.importedSince(fromDay)) {
            if (session.externalId !in stillThere) sessionDao.delete(session)
        }

        val known = sessionDao.importedExternalIds().toSet()
        val weight = userPreferences.profile.first().weightKg
        var imported = 0
        for (session in foreign) {
            if (session.id in known) continue
            val activity = if (session.isStrength) {
                Activities.strengthTraining
            } else {
                Activities.byId("autre")
            }
            sessionDao.insert(
                Session(
                    kind = if (session.isStrength) SessionKind.GYM.id else SessionKind.CARDIO.id,
                    epochDay = session.day.toEpochDay(),
                    activityId = if (session.isStrength) null else activity.id,
                    durationMinutes = session.minutes,
                    caloriesBurned = Activities.caloriesBurned(activity, session.minutes, weight),
                    note = session.title ?: "Importé de ${session.source.substringAfterLast('.')}",
                    finished = true,
                    externalId = session.id,
                ),
            )
            imported++
        }
        return imported
    }

    /**
     * Exporte les séances et repas du dernier mois, et relit le poids le plus récent.
     * Le poids n'est pas appliqué d'office : il est renvoyé pour que l'utilisateur
     * décide de remplacer, ou non, celui de son profil.
     */
    suspend fun syncHealthConnect(today: LocalDate = LocalDate.now()): SyncResult {
        val manager = healthConnect ?: return SyncResult.Unavailable
        val fromDay = today.minusDays((SYNC_DAYS - 1).toLong()).toEpochDay()
        val result = manager.sync(
            sessions = sessionDao.since(fromDay),
            meals = mealDao.since(fromDay),
            today = today,
            days = SYNC_DAYS,
        )
        if (result is SyncResult.Success) {
            userPreferences.setLastHealthSync(System.currentTimeMillis() / 1000)
            // Dans la foulée : ce que les autres applications ont écrit de leur côté
            return result.copy(imported = importHealthSessions(today))
        }
        return result
    }

    // --- Profil --------------------------------------------------------------

    suspend fun setWeight(weightKg: Double) = userPreferences.setWeight(weightKg)

    suspend fun setDailyCalorieGoal(goal: Int) = userPreferences.setDailyCalorieGoal(goal)

    suspend fun setMacroGoals(goals: MacroGoals) = userPreferences.setMacroGoals(goals)

    companion object {
        /** Fenêtre d'export : réécrire tout l'historique à chaque fois serait inutile. */
        const val SYNC_DAYS = 30

        @Volatile
        private var instance: FitTrackRepository? = null

        fun get(context: Context): FitTrackRepository = instance ?: synchronized(this) {
            instance ?: run {
                val db = FitTrackDatabase.get(context)
                FitTrackRepository(
                    context = context.applicationContext,
                    sessionDao = db.sessionDao(),
                    exerciseSetDao = db.exerciseSetDao(),
                    mealDao = db.mealDao(),
                    foodProductDao = db.foodProductDao(),
                    userPreferences = UserPreferences(context.applicationContext),
                    exercises = ExerciseCatalog.get(context),
                    healthConnect = HealthConnectManager(context.applicationContext),
                    kazis = KazisClient(),
                ).also { instance = it }
            }
        }
    }
}
