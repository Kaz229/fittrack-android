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
