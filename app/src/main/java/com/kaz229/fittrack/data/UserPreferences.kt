package com.kaz229.fittrack.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class UserPreferences(private val context: Context) {

    private val weightKey = doublePreferencesKey("weight_kg")
    private val goalKey = intPreferencesKey("daily_calorie_goal")
    private val proteinGoalKey = intPreferencesKey("protein_goal_g")
    private val carbsGoalKey = intPreferencesKey("carbs_goal_g")
    private val fatGoalKey = intPreferencesKey("fat_goal_g")

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

    suspend fun setMacroGoals(goals: MacroGoals) {
        context.dataStore.edit {
            it[proteinGoalKey] = goals.proteinG
            it[carbsGoalKey] = goals.carbsG
            it[fatGoalKey] = goals.fatG
        }
    }
}
