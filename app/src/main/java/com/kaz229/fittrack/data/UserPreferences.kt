package com.kaz229.fittrack.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "fittrack_prefs",
)

/**
 * Profil utilisateur : poids (pour l'estimation calorique), objectif calorique
 * journalier et objectifs de macros.
 */
data class UserProfile(
    val weightKg: Double = DEFAULT_WEIGHT_KG,
    val dailyCalorieGoal: Int = DEFAULT_GOAL,
    val macroGoals: MacroGoals = MacroGoals(),
) {
    companion object {
        const val DEFAULT_WEIGHT_KG = 75.0
        const val DEFAULT_GOAL = 2200
    }
}

/** Thème de l'application, choisi à la main ou laissé au système. */
enum class ThemeChoice(val id: String, val label: String) {
    SYSTEM("systeme", "Comme le système"),
    LIGHT("clair", "Clair"),
    DARK("sombre", "Sombre");

    companion object {
        fun byId(id: String?): ThemeChoice = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Coordonnées du serveur Kazis. Vides tant que l'utilisateur ne les a pas saisies. */
data class KazisSettings(val url: String = "", val token: String = "") {
    val configured: Boolean get() = url.isNotBlank() && token.isNotBlank()
}

class UserPreferences(private val context: Context) {

    private val weightKey = doublePreferencesKey("weight_kg")
    private val goalKey = intPreferencesKey("daily_calorie_goal")
    private val proteinGoalKey = intPreferencesKey("protein_goal_g")
    private val carbsGoalKey = intPreferencesKey("carbs_goal_g")
    private val fatGoalKey = intPreferencesKey("fat_goal_g")
    private val lastHealthSyncKey = longPreferencesKey("last_health_sync_epoch_second")
    private val kazisUrlKey = stringPreferencesKey("kazis_url")
    private val kazisTokenKey = stringPreferencesKey("kazis_token")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val themeKey = stringPreferencesKey("theme_choice")
    private val heightKey = intPreferencesKey("height_cm")
    private val ageKey = intPreferencesKey("age")
    private val sexKey = stringPreferencesKey("sex")
    private val stepsKey = intPreferencesKey("daily_steps")
    private val gymPerWeekKey = intPreferencesKey("gym_per_week")
    private val gymMinutesKey = intPreferencesKey("gym_minutes")
    private val gymIntensityKey = stringPreferencesKey("gym_intensity")
    private val runsPerWeekKey = intPreferencesKey("runs_per_week")
    private val runMinutesKey = intPreferencesKey("run_minutes")
    private val runIntensityKey = stringPreferencesKey("run_intensity")
    private val bodyGoalKey = stringPreferencesKey("body_goal")
    private val weeklyLossKey = doublePreferencesKey("weekly_loss_kg")
    private val lastBackupKey = longPreferencesKey("last_kazis_backup_epoch_second")

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            weightKg = prefs[weightKey] ?: UserProfile.DEFAULT_WEIGHT_KG,
            dailyCalorieGoal = prefs[goalKey] ?: UserProfile.DEFAULT_GOAL,
            macroGoals = MacroGoals(
                proteinG = prefs[proteinGoalKey] ?: MacroGoals.DEFAULT_PROTEIN_G,
                carbsG = prefs[carbsGoalKey] ?: MacroGoals.DEFAULT_CARBS_G,
                fatG = prefs[fatGoalKey] ?: MacroGoals.DEFAULT_FAT_G,
            ),
        )
    }

    suspend fun setWeight(weightKg: Double) {
        context.dataStore.edit { it[weightKey] = weightKg }
    }

    suspend fun setDailyCalorieGoal(goal: Int) {
        context.dataStore.edit { it[goalKey] = goal }
    }

    /**
     * Adresse et jeton du serveur Kazis, d'où viennent les repas dictés à l'assistant.
     * Le jeton n'ouvre qu'un serveur du réseau privé ; il est stocké comme le reste
     * des préférences, dans l'espace privé de l'application.
     */
    val kazis: Flow<KazisSettings> = context.dataStore.data.map { prefs ->
        KazisSettings(url = prefs[kazisUrlKey] ?: "", token = prefs[kazisTokenKey] ?: "")
    }

    suspend fun setKazis(settings: KazisSettings) {
        context.dataStore.edit {
            it[kazisUrlKey] = settings.url.trim()
            it[kazisTokenKey] = settings.token.trim()
        }
    }

    /**
     * Identifiant de cette installation, tiré au premier besoin.
     *
     * Les identifiants des séances repartent à 1 après une réinstallation : sans lui,
     * la sauvegarde écraserait celle de l'installation précédente.
     */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.map { it[deviceIdKey] }.first()
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[deviceIdKey] = fresh }
        return fresh
    }

    val theme: Flow<ThemeChoice> = context.dataStore.data.map { ThemeChoice.byId(it[themeKey]) }

    /** Les réponses du questionnaire, d'où sortent l'objectif et les macros. */
    val needsProfile: Flow<NeedsProfile> = context.dataStore.data.map { prefs ->
        NeedsProfile(
            heightCm = prefs[heightKey] ?: 0,
            age = prefs[ageKey] ?: 0,
            sex = Sex.byId(prefs[sexKey]),
            dailySteps = prefs[stepsKey] ?: 0,
            gymPerWeek = prefs[gymPerWeekKey] ?: 0,
            gymMinutes = prefs[gymMinutesKey] ?: 60,
            gymIntensity = Intensity.byId(prefs[gymIntensityKey]),
            runsPerWeek = prefs[runsPerWeekKey] ?: 0,
            runMinutes = prefs[runMinutesKey] ?: 30,
            runIntensity = Intensity.byId(prefs[runIntensityKey]),
            goal = Goal.byId(prefs[bodyGoalKey]),
            weeklyLossKg = prefs[weeklyLossKey] ?: 0.5,
        )
    }

    suspend fun setNeedsProfile(value: NeedsProfile) {
        context.dataStore.edit {
            it[heightKey] = value.heightCm
            it[ageKey] = value.age
            it[sexKey] = value.sex.id
            it[stepsKey] = value.dailySteps
            it[gymPerWeekKey] = value.gymPerWeek
            it[gymMinutesKey] = value.gymMinutes
            it[gymIntensityKey] = value.gymIntensity.id
            it[runsPerWeekKey] = value.runsPerWeek
            it[runMinutesKey] = value.runMinutes
            it[runIntensityKey] = value.runIntensity.id
            it[bodyGoalKey] = value.goal.id
            it[weeklyLossKey] = value.weeklyLossKg
        }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[themeKey] = choice.id }
    }

    /** Horodatage de la dernière sauvegarde réussie vers Kazis, en secondes. */
    val lastBackup: Flow<Long?> = context.dataStore.data.map { it[lastBackupKey] }

    suspend fun setLastBackup(epochSecond: Long) {
        context.dataStore.edit { it[lastBackupKey] = epochSecond }
    }

    /** Horodatage de la dernière synchronisation Health Connect réussie, en secondes. */
    val lastHealthSync: Flow<Long?> = context.dataStore.data.map { it[lastHealthSyncKey] }

    suspend fun setLastHealthSync(epochSecond: Long) {
        context.dataStore.edit { it[lastHealthSyncKey] = epochSecond }
    }

    suspend fun setMacroGoals(goals: MacroGoals) {
        context.dataStore.edit {
            it[proteinGoalKey] = goals.proteinG
            it[carbsGoalKey] = goals.carbsG
            it[fatGoalKey] = goals.fatG
        }
    }
}
