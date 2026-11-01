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
import com.kaz229.fittrack.data.FoodLookup
import com.kaz229.fittrack.data.FoodProduct
import com.kaz229.fittrack.data.Labels
import com.kaz229.fittrack.data.MacroGoals
import com.kaz229.fittrack.data.MacroTotals
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

/** Pourquoi un code-barres scanné n'a rien donné. */
enum class ScanError { NOT_FOUND, NO_NUTRITION, NETWORK }

/**
 * État de l'écran de scan. Tant que [product] est null et qu'aucune [error] n'est
 * remontée, la caméra reste active et cherche un code-barres.
 */
data class ScanState(
    val searching: Boolean = false,
    val barcode: String? = null,
    val product: FoodProduct? = null,
    val fromCache: Boolean = false,
    val error: ScanError? = null,
) {
    /** La caméra doit s'arrêter dès qu'un code a été capté, succès ou échec. */
    val cameraActive: Boolean get() = !searching && product == null && error == null
}

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

    // --- Scan d'un code-barres alimentaire -----------------------------------

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan

    /**
     * Appelé par l'analyseur d'images à chaque code-barres reconnu. La caméra en
     * envoie plusieurs par seconde : on ignore tout ce qui arrive dès qu'une
     * recherche est lancée, sinon on épuiserait le quota d'Open Food Facts.
     */
    fun onBarcodeScanned(barcode: String) {
        if (!_scan.value.cameraActive) return
        _scan.value = ScanState(searching = true, barcode = barcode)
        viewModelScope.launch {
            _scan.value = when (val result = repository.lookupFood(barcode, today)) {
                is FoodLookup.Found -> ScanState(
                    barcode = barcode,
                    product = result.product,
                    fromCache = result.fromCache,
                )
                FoodLookup.NotFound -> ScanState(barcode = barcode, error = ScanError.NOT_FOUND)
                FoodLookup.NoNutrition ->
                    ScanState(barcode = barcode, error = ScanError.NO_NUTRITION)
                FoodLookup.NetworkError -> ScanState(barcode = barcode, error = ScanError.NETWORK)
            }
        }
    }

    /** Remet la caméra en recherche : nouveau scan, ou abandon de l'écran. */
    fun resetScan() { _scan.value = ScanState() }

    /** Enregistre la portion pesée du produit scanné, puis libère l'écran de scan. */
    fun addScannedFood(grams: Double, mealType: MealType) = viewModelScope.launch {
        val product = _scan.value.product ?: return@launch
        repository.addFoodMeal(product, grams, mealType, today)
        _scan.value = ScanState()
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

    val todayMacros: StateFlow<MacroTotals> = repository.macrosForDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MacroTotals())

    fun addCardioSession(activityId: String, durationMinutes: Int, note: String) =
        viewModelScope.launch {
            repository.addCardioSession(activityId, durationMinutes, note, today)
        }

    fun deleteSession(session: Session) = viewModelScope.launch {
        repository.deleteSession(session)
    }

    fun addMeal(
        name: String,
        calories: Int,
        mealType: MealType,
        proteinG: Double = 0.0,
        carbsG: Double = 0.0,
        fatG: Double = 0.0,
    ) = viewModelScope.launch {
        repository.addMeal(name, calories, mealType, proteinG, carbsG, fatG, today)
    }

    fun deleteMeal(meal: Meal) = viewModelScope.launch { repository.deleteMeal(meal) }

    fun setWeight(weightKg: Double) = viewModelScope.launch { repository.setWeight(weightKg) }

    fun setDailyCalorieGoal(goal: Int) = viewModelScope.launch {
        repository.setDailyCalorieGoal(goal)
    }

    fun setMacroGoals(goals: MacroGoals) = viewModelScope.launch {
        repository.setMacroGoals(goals)
    }
}
