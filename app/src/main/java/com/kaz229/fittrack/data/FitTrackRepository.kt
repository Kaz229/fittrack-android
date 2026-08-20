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
    private val sessionDao: SessionDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val mealDao: MealDao,
    private val userPreferences: UserPreferences,
    val exercises: ExerciseCatalog,
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
    suspend fun startGymSession(date: LocalDate = LocalDate.now()): Long =
        sessionDao.insert(
            Session(
                kind = SessionKind.GYM.id,
                epochDay = date.toEpochDay(),
                finished = false,
            ),
        )

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

    suspend fun deleteSession(session: Session) = sessionDao.delete(session)

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

    suspend fun addMeal(
        name: String,
        calories: Int,
        mealType: MealType,
        date: LocalDate = LocalDate.now(),
    ) {
        mealDao.insert(
            Meal(
                name = name,
                calories = calories,
                mealType = mealType.id,
                epochDay = date.toEpochDay(),
            ),
        )
    }

    suspend fun deleteMeal(meal: Meal) = mealDao.delete(meal)

    // --- Bilans --------------------------------------------------------------

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

    // --- Profil --------------------------------------------------------------

    suspend fun setWeight(weightKg: Double) = userPreferences.setWeight(weightKg)

    suspend fun setDailyCalorieGoal(goal: Int) = userPreferences.setDailyCalorieGoal(goal)

    companion object {
        @Volatile
        private var instance: FitTrackRepository? = null

        fun get(context: Context): FitTrackRepository = instance ?: synchronized(this) {
            instance ?: run {
                val db = FitTrackDatabase.get(context)
                FitTrackRepository(
                    sessionDao = db.sessionDao(),
                    exerciseSetDao = db.exerciseSetDao(),
                    mealDao = db.mealDao(),
                    userPreferences = UserPreferences(context.applicationContext),
                    exercises = ExerciseCatalog.get(context),
                ).also { instance = it }
            }
        }
    }
}
