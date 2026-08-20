package com.kaz229.fittrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: Long): Session?

    @Query("SELECT * FROM sessions ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM sessions WHERE epochDay = :epochDay")
    fun observeCaloriesForDay(epochDay: Long): Flow<Int>

    @Query(
        "SELECT epochDay, COALESCE(SUM(caloriesBurned), 0) AS total FROM sessions " +
            "WHERE epochDay >= :fromDay GROUP BY epochDay",
    )
    fun observeDailyTotals(fromDay: Long): Flow<List<DayTotal>>
}

@Dao
interface ExerciseSetDao {
    @Insert
    suspend fun insert(set: ExerciseSet): Long

    @Delete
    suspend fun delete(set: ExerciseSet)

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY position, id")
    fun observeForSession(sessionId: Long): Flow<List<ExerciseSet>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercise_sets WHERE sessionId = :sessionId")
    suspend fun lastPosition(sessionId: Long): Int

    /** Historique d'un exercice, séance la plus récente d'abord. */
    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId ORDER BY epochDay DESC, id DESC")
    fun observeForExercise(exerciseId: String): Flow<List<ExerciseSet>>

    /** La série la plus lourde jamais réalisée sur un exercice (record personnel). */
    @Query(
        "SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId " +
            "ORDER BY weightKg DESC, reps DESC, id DESC LIMIT 1",
    )
    fun observeBestSet(exerciseId: String): Flow<ExerciseSet?>

    /** Volume soulevé (répétitions × kilos) sur une journée. */
    @Query("SELECT COALESCE(SUM(reps * weightKg), 0.0) FROM exercise_sets WHERE epochDay = :epochDay")
    fun observeVolumeForDay(epochDay: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM exercise_sets WHERE epochDay = :epochDay")
    fun observeSetCountForDay(epochDay: Long): Flow<Int>
}

@Dao
interface MealDao {
    @Insert
    suspend fun insert(meal: Meal): Long

    @Delete
    suspend fun delete(meal: Meal)

    @Query("SELECT * FROM meals WHERE epochDay = :epochDay ORDER BY id DESC")
    fun observeForDay(epochDay: Long): Flow<List<Meal>>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM meals WHERE epochDay = :epochDay")
    fun observeCaloriesForDay(epochDay: Long): Flow<Int>

    @Query(
        "SELECT epochDay, COALESCE(SUM(calories), 0) AS total FROM meals " +
            "WHERE epochDay >= :fromDay GROUP BY epochDay",
    )
    fun observeDailyTotals(fromDay: Long): Flow<List<DayTotal>>
}
