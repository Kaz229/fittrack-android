package com.kaz229.fittrack.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Nature d'une séance : salle de musculation ou activité cardio / sport. */
enum class SessionKind(val id: String, val label: String) {
    GYM("gym", "Salle"),
    CARDIO("cardio", "Cardio / sport");

    companion object {
        fun byId(id: String): SessionKind = entries.firstOrNull { it.id == id } ?: GYM
    }
}

/**
 * Une séance d'entraînement.
 *
 * Pour une séance salle, le détail vit dans [ExerciseSet] ; pour une séance cardio,
 * [activityId] renvoie au catalogue [Activities]. Les calories brûlées sont figées
 * à l'enregistrement (formule MET) pour que l'historique reste stable si le poids change.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val epochDay: Long,
    val activityId: String? = null,
    val durationMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    val note: String = "",
    val finished: Boolean = false,
)

/**
 * Une série réalisée pendant une séance salle : tant de répétitions à tant de kilos.
 * [exerciseId] est l'identifiant du catalogue d'exercices (`assets/exercises.json`).
 */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val epochDay: Long,
) {
    /** Volume soulevé par la série, en kilos. */
    val volume: Double get() = reps * weightKg
}

/** Un repas ou une collation, avec les calories ingérées. */
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Int,
    val mealType: String,
    val epochDay: Long,
)

/** Bilan d'une journée : ingéré, brûlé, et solde net. */
data class DailySummary(
    val epochDay: Long,
    val caloriesIn: Int,
    val caloriesOut: Int,
) {
    val net: Int get() = caloriesIn - caloriesOut
}

/** Ligne agrégée « un jour, un total » renvoyée par les requêtes GROUP BY. */
data class DayTotal(
    val epochDay: Long,
    val total: Int,
)

/** Les séries d'un même exercice au sein d'une séance, regroupées pour l'affichage. */
data class ExerciseBlock(
    val exerciseId: String,
    val sets: List<ExerciseSet>,
) {
    val volume: Double get() = sets.sumOf { it.volume }
    val bestWeight: Double get() = sets.maxOfOrNull { it.weightKg } ?: 0.0
}
