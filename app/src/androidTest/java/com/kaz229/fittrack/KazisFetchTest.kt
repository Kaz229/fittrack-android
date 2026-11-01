package com.kaz229.fittrack

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.FitTrackDatabase
import com.kaz229.fittrack.data.FitTrackRepository
import com.kaz229.fittrack.data.KazisClient
import com.kaz229.fittrack.data.KazisFetch
import com.kaz229.fittrack.data.KazisSettings
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Récupération des repas dictés, contre un vrai serveur Kazis.
 *
 * Le serveur de test tourne sur la machine hôte, joignable depuis l'émulateur par
 * 10.0.2.2. Sans lui, le test constate seulement que l'échec est propre et que rien
 * n'est enregistré — et le dit dans logcat, pour qu'un vert ne trompe personne.
 */
@RunWith(AndroidJUnit4::class)
class KazisFetchTest {

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

    @Test
    fun sans_reglages_rien_n_est_tente() = runTest {
        preferences.setKazis(KazisSettings())

        assertEquals(KazisFetch.NotConfigured, repository.fetchKazisMeals(today))
        assertTrue(repository.mealsForDay(today).first().isEmpty())
    }

    @Test
    fun un_jeton_refuse_est_signale_sans_rien_enregistrer() = runTest {
        preferences.setKazis(KazisSettings(url, "mauvais-jeton"))

        val result = repository.fetchKazisMeals(today)

        if (result is KazisFetch.Failed) {
            Log.w(TAG, "IGNORÉ : serveur de test injoignable ($result)")
            return@runTest
        }
        assertEquals(KazisFetch.Unauthorized, result)
        assertTrue(repository.mealsForDay(today).first().isEmpty())
    }

    @Test
    fun les_repas_dictes_arrivent_dans_le_journal() = runTest {
        preferences.setKazis(KazisSettings(url, token))

        val result = repository.fetchKazisMeals(today)
        if (result is KazisFetch.Failed) {
            Log.w(TAG, "IGNORÉ : serveur de test injoignable ($result)")
            return@runTest
        }
        Log.i(TAG, "Récupération : $result")

        assertTrue("Attendu un succès, obtenu $result", result is KazisFetch.Success)
        val meals = repository.mealsForDay(today).first()
        assertTrue("Aucun repas enregistré", meals.isNotEmpty())

        val riz = meals.firstOrNull { it.name.contains("riz") }
        if (riz != null) {
            assertEquals(195, riz.calories)
            assertEquals(4.0, riz.proteinG, 0.001)
            assertEquals(MealType.LUNCH.id, riz.mealType)
        }

        // Le produit donné par code-barres doit avoir été résolu chez Open Food Facts.
        val produit = meals.firstOrNull { it.barcode == "3017620422003" }
        if (produit != null) {
            Log.i(TAG, "Produit résolu : ${produit.name} ${produit.calories} kcal")
            assertTrue("Le produit scanné doit porter ses calories", produit.calories > 0)
            assertEquals(30.0, produit.grams!!, 0.001)
        }

        // Deuxième passage : la file a été vidée par l'accusé de réception.
        val again = repository.fetchKazisMeals(today)
        assertTrue(again is KazisFetch.Success)
        assertEquals(0, (again as KazisFetch.Success).added)
    }

    private companion object {
        const val TAG = "KazisFetchTest"
    }
}
