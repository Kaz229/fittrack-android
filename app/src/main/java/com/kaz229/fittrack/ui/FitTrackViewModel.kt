package com.kaz229.fittrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaz229.fittrack.data.DailySummary
import com.kaz229.fittrack.data.Exercise
import com.kaz229.fittrack.data.ExerciseBlock
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.ExerciseSet
import com.kaz229.fittrack.data.FitTrackRepository
import com.kaz229.fittrack.data.Labels
import com.kaz229.fittrack.data.Meal
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.Session
import com.kaz229.fittrack.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ce que l'écran « Exercices » affiche : la liste filtrée et les filtres disponibles. */
data class CatalogState(
    val exercises: List<Exercise> = emptyList(),
    val bodyParts: List<String> = emptyList(),
    val equipments: List<String> = emptyList(),
    val query: String = "",
    val bodyPart: String? = null,
    val equipment: String? = null,
    val loading: Boolean = true,
)

/** La fiche d'un exercice : sa description et ce que l'utilisateur y a déjà fait. */
data class ExerciseDetail(
    val exercise: Exercise,
    val history: List<ExerciseSet>,
    val bestSet: ExerciseSet?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FitTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FitTrackRepository.get(application)
    private val today: LocalDate = LocalDate.now()

    // --- Catalogue d'exercices ----------------------------------------------

    private val allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    private val query = MutableStateFlow("")
    private val bodyPartFilter = MutableStateFlow<String?>(null)
    private val equipmentFilter = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { allExercises.value = repository.exercises.all() }
    }

    val catalog: StateFlow<CatalogState> = combine(
        allExercises,
        query,
        bodyPartFilter,
        equipmentFilter,
    ) { exercises, text, bodyPart, equipment ->
        CatalogState(
            exercises = ExerciseCatalog.filter(exercises, text, bodyPart, equipment),
            bodyParts = exercises.map { it.bodyPart }.distinct().sortedBy(Labels::bodyPart),
            equipments = exercises.map { it.equipment }.distinct().sortedBy(Labels::equipment),
            query = text,
            bodyPart = bodyPart,
            equipment = equipment,
            loading = exercises.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogState())

    /** Index des exercices par identifiant, pour afficher les séries sans reparcourir la liste. */
    val exercisesById: StateFlow<Map<String, Exercise>> = allExercises
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onQueryChange(value: String) { query.value = value }

    fun onBodyPartFilter(value: String?) { bodyPartFilter.value = value }

    fun onEquipmentFilter(value: String?) { equipmentFilter.value = value }

    // --- Fiche exercice ------------------------------------------------------

    private val selectedExerciseId = MutableStateFlow<String?>(null)

    val exerciseDetail: StateFlow<ExerciseDetail?> = combine(
        selectedExerciseId,
        allExercises,
    ) { id, exercises -> exercises.firstOrNull { it.id == id } }
        .flatMapLatest { exercise ->
            if (exercise == null) {
                flowOf(null)
            } else {
                combine(
                    repository.exerciseHistory(exercise.id),
                    repository.exerciseBestSet(exercise.id),
                ) { history, best -> ExerciseDetail(exercise, history, best) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectExercise(exerciseId: String) { selectedExerciseId.value = exerciseId }

    // --- Séance ouverte ------------------------------------------------------

    private val openSessionId = MutableStateFlow<Long?>(null)

    val openSession: StateFlow<Session?> = openSessionId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.session(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val openSessionBlocks: StateFlow<List<ExerciseBlock>> = openSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.sessionBlocks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openSession(sessionId: Long) { openSessionId.value = sessionId }

    /** Crée une séance salle puis rappelle [onCreated] avec son identifiant, pour naviguer. */
    fun startGymSession(onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = repository.startGymSession(today)
        openSessionId.value = id
        onCreated(id)
    }

    fun addSet(exerciseId: String, reps: Int, weightKg: Double) = viewModelScope.launch {
        openSessionId.value?.let { repository.addSet(it, exerciseId, reps, weightKg) }
    }

    fun deleteSet(set: ExerciseSet) = viewModelScope.launch { repository.deleteSet(set) }

    fun finishGymSession(durationMinutes: Int, note: String) = viewModelScope.launch {
        openSessionId.value?.let { repository.finishGymSession(it, durationMinutes, note) }
    }

    // --- Séances, repas, bilans ---------------------------------------------

    val profile: StateFlow<UserProfile> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val todaySummary: StateFlow<DailySummary> = repository.summaryForDay(today)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DailySummary(today.toEpochDay(), 0, 0),
        )

    val lastWeek: StateFlow<List<DailySummary>> = repository.recentSummaries(today, days = 7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayVolume: StateFlow<Double> = repository.volumeForDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val todaySetCount: StateFlow<Int> = repository.setCountForDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sessions: StateFlow<List<Session>> = repository.allSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayMeals: StateFlow<List<Meal>> = repository.mealsForDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCardioSession(activityId: String, durationMinutes: Int, note: String) =
        viewModelScope.launch {
            repository.addCardioSession(activityId, durationMinutes, note, today)
        }

    fun deleteSession(session: Session) = viewModelScope.launch {
        repository.deleteSession(session)
    }

    fun addMeal(name: String, calories: Int, mealType: MealType) = viewModelScope.launch {
        repository.addMeal(name, calories, mealType, today)
    }

    fun deleteMeal(meal: Meal) = viewModelScope.launch { repository.deleteMeal(meal) }

    fun setWeight(weightKg: Double) = viewModelScope.launch { repository.setWeight(weightKg) }

    fun setDailyCalorieGoal(goal: Int) = viewModelScope.launch {
        repository.setDailyCalorieGoal(goal)
    }
}
