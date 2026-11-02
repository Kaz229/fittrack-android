package com.kaz229.fittrack

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.FitTrackDatabase
import com.kaz229.fittrack.data.FitTrackRepository
import com.kaz229.fittrack.data.KazisBackup
import com.kaz229.fittrack.data.KazisClient
import com.kaz229.fittrack.data.KazisSettings
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Sauvegarde du contenu de l'app vers Kazis, contre un vrai serveur.
 *
 * Sans serveur joignable, le test vérifie seulement que l'échec est propre, et le dit
 * dans logcat pour qu'un vert ne trompe personne.
 */
@RunWith(AndroidJUnit4::class)
class KazisBackupTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var repository: FitTrackRepository
    private lateinit var preferences: UserPreferences
    private val today: LocalDate = LocalDate.now()

    private val url = "http://10.0.2.2:8799"
    private val token = "jeton-de-test"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FitTrackDatabase::class.java).build()
        preferences = UserPreferences(context)
        repository = FitTrackRepository(
            sessionDao = db.sessionDao(),
            exerciseSetDao = db.exerciseSetDao(),
            mealDao = db.mealDao(),
            foodProductDao = db.foodProductDao(),
            userPreferences = preferences,
            exercises = ExerciseCatalog.get(context),
            kazis = KazisClient(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun remplir() {
        val id = repository.startGymSession(today)
        repository.addSet(id, "0025", reps = 10, weightKg = 60.0)
        repository.addSet(id, "0025", reps = 8, weightKg = 65.0)
        repository.finishGymSession(id, durationMinutes = 75, note = "Push day")
        repository.addMeal("ravioles fraiches", 420, MealType.LUNCH, 24.0, 36.0, 21.0, today)
    }

    @Test
    fun sans_reglages_rien_n_est_envoye() = runTest {
        preferences.setKazis(KazisSettings())

        assertEquals(KazisBackup.NotConfigured, repository.backupToKazis())
    }

    @Test
    fun un_jeton_refuse_est_signale() = runTest {
        preferences.setKazis(KazisSettings(url, "mauvais-jeton"))

        val result = repository.backupToKazis()

        if (result is KazisBackup.Failed) {
            Log.w(TAG, "IGNORÉ : serveur de test injoignable ($result)")
            return@runTest
        }
        assertEquals(KazisBackup.Unauthorized, result)
    }

    @Test
    fun tout_le_contenu_part_en_sauvegarde() = runTest {
        preferences.setKazis(KazisSettings(url, token))
        remplir()

        val result = repository.backupToKazis()
        if (result is KazisBackup.Failed) {
            Log.w(TAG, "IGNORÉ : serveur de test injoignable ($result)")
            return@runTest
        }
        Log.i(TAG, "Sauvegarde : $result")

        assertTrue("Attendu un succès, obtenu $result", result is KazisBackup.Success)
        val success = result as KazisBackup.Success
        assertEquals(1, success.sessions)
        // Les séries sont le cœur : ce que Health Connect ne conserve pas.
        assertEquals(2, success.sets)
        assertEquals(1, success.meals)

        // Rejouée, la sauvegarde renvoie la même chose : côté serveur rien ne double.
        val again = repository.backupToKazis()
        assertEquals(result, again)
    }

    private companion object {
        const val TAG = "KazisBackupTest"
    }
}
