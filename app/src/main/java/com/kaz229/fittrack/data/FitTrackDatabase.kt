package com.kaz229.fittrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Session::class, ExerciseSet::class, Meal::class, FoodProduct::class],
    version = 2,
    exportSchema = false,
)
abstract class FitTrackDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun mealDao(): MealDao
    abstract fun foodProductDao(): FoodProductDao

    companion object {
        @Volatile
        private var instance: FitTrackDatabase? = null

        /**
         * v1 → v2 : arrivée des macronutriments et du cache Open Food Facts.
         *
         * Migration explicite et non destructive : les repas déjà saisis gardent leurs
         * calories et se retrouvent simplement avec des macros à zéro.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meals ADD COLUMN proteinG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE meals ADD COLUMN carbsG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE meals ADD COLUMN fatG REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE meals ADD COLUMN barcode TEXT")
                db.execSQL("ALTER TABLE meals ADD COLUMN grams REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS food_products (" +
                        "barcode TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "brands TEXT NOT NULL, " +
                        "kcalPer100g REAL NOT NULL, " +
                        "proteinPer100g REAL NOT NULL, " +
                        "carbsPer100g REAL NOT NULL, " +
                        "fatPer100g REAL NOT NULL, " +
                        "servingSizeG REAL, " +
                        "fetchedAtEpochDay INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): FitTrackDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FitTrackDatabase::class.java,
                "fittrack.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
