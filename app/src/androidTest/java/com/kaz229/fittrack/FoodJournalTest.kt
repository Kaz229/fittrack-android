package com.kaz229.fittrack

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.FitTrackDatabase
import com.kaz229.fittrack.data.FitTrackRepository
import com.kaz229.fittrack.data.FoodLookup
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.OpenFoodFactsClient
import com.kaz229.fittrack.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Parcours complet du journal alimentaire, sur appareil : recherche d'un code-barres
 * chez Open Food Facts, mise en cache, enregistrement de la portion, agrégation des
 * macros du jour.
 *
 * Le test touche le vrai réseau : c'est délibéré, c'est le seul moyen de vérifier que
 * le contrat de l'API tient toujours. Une coupure de connexion ne fait pas échouer le
 * build, mais l'abandon est tracé dans logcat (voir [skipOffline]) — sans quoi un test
 * vert pourrait masquer une API cassée.
 */
@RunWith(AndroidJUnit4::class)
class FoodJournalTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var repository: FitTrackRepository
    private val today = LocalDate.of(2026, 8, 20)

    /** Nutella : produit stable et très complété, bon témoin pour l'API. */
    private val nutellaBarcode = "3017620422003"

    /**
     * Signale dans logcat qu'un test réseau a été ignoré. Un test vert qui a en réalité
     * sauté sa vérification ne prouve rien : il faut pouvoir le constater.
     */
    private fun skipOffline(test: String) {
        Log.w(TAG, "IGNORÉ ($test) : Open Food Facts injoignable depuis cet appareil.")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, FitTrackDatabase::class.java).build()
        repository = FitTrackRepository(
            sessionDao = db.sessionDao(),
            exerciseSetDao = db.exerciseSetDao(),
            mealDao = db.mealDao(),
            foodProductDao = db.foodProductDao(),
            userPreferences = UserPreferences(context),
            exercises = ExerciseCatalog.get(context),
            openFoodFacts = OpenFoodFactsClient(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun un_code_barres_donne_un_produit_puis_vient_du_cache() = runTest {
        val first = repository.lookupFood(nutellaBarcode, today)
        if (first is FoodLookup.NetworkError) {
            skipOffline("lookup + cache")
            return@runTest
        }
        Log.i(TAG, "Réseau OK : $first")

        assertTrue("Attendu un produit, obtenu $first", first is FoodLookup.Found)
        val found = first as FoodLookup.Found
        assertFalse("Premier appel : doit venir du réseau", found.fromCache)
        assertTrue("Les kcal doivent être renseignées", found.product.kcalPer100g > 0)
        assertTrue(
            "Les protéines doivent être renseignées",
            found.product.proteinPer100g > 0,
        )

        // Deuxième appel : le produit doit sortir du cache local, sans requête réseau.
        val second = repository.lookupFood(nutellaBarcode, today)
        assertTrue(second is FoodLookup.Found)
        assertTrue("Second appel : doit venir du cache", (second as FoodLookup.Found).fromCache)
        assertEquals(found.product.barcode, second.product.barcode)
    }

    @Test
    fun un_produit_scanne_alimente_le_journal_et_les_macros_du_jour() = runTest {
        val lookup = repository.lookupFood(nutellaBarcode, today)
        if (lookup !is FoodLookup.Found) {
            skipOffline("journal + macros")
            return@runTest
        }
        val product = lookup.product

        repository.addFoodMeal(product, grams = 30.0, mealType = MealType.BREAKFAST, date = today)

        val meals = repository.mealsForDay(today).first()
        assertEquals(1, meals.size)
        val meal = meals.single()
        assertEquals(nutellaBarcode, meal.barcode)
        assertEquals(30.0, meal.grams!!, 0.001)
        assertTrue("La ligne doit porter des macros", meal.hasMacros)

        // Les macros enregistrées valent bien 30 % des valeurs pour 100 g.
        val expected = product.proteinPer100g * 0.3
        assertEquals(expected, meal.proteinG, 0.15)

        val macros = repository.macrosForDay(today).first()
        assertEquals(meal.proteinG, macros.proteinG, 0.001)
        assertEquals(meal.carbsG, macros.carbsG, 0.001)
        assertEquals(meal.fatG, macros.fatG, 0.001)

        // Les calories du jour tiennent compte de la ligne ajoutée.
        val summary = repository.summaryForDay(today).first()
        assertEquals(meal.calories, summary.caloriesIn)
    }

    @Test
    fun un_code_barres_inexistant_ne_casse_rien() = runTest {
        val result = repository.lookupFood("0000000000000", today)
        // Selon l'humeur d'OFF : produit absent, ou réponse sans nutriments. Jamais un plantage.
        assertTrue(
            "Attendu NotFound / NoNutrition / NetworkError, obtenu $result",
            result is FoodLookup.NotFound ||
                result is FoodLookup.NoNutrition ||
                result is FoodLookup.NetworkError,
        )
    }

    @Test
    fun une_saisie_libre_sans_macros_reste_enregistrable() = runTest {
        repository.addMeal(
            name = "Poulet basquaise maison",
            calories = 620,
            mealType = MealType.DINNER,
            date = today,
        )

        val meal = repository.mealsForDay(today).first().single()
        assertEquals(620, meal.calories)
        assertFalse("Sans macros saisies, hasMacros doit être faux", meal.hasMacros)
        assertNotNull(meal.name)

        val macros = repository.macrosForDay(today).first()
        assertEquals(0.0, macros.proteinG, 0.001)
    }

    @Test
    fun les_macros_de_plusieurs_lignes_s_additionnent() = runTest {
        repository.addMeal("Riz", 350, MealType.LUNCH, 7.0, 77.0, 0.6, today)
        repository.addMeal("Blanc de poulet", 165, MealType.LUNCH, 31.0, 0.0, 3.6, today)

        val macros = repository.macrosForDay(today).first()
        assertEquals(38.0, macros.proteinG, 0.001)
        assertEquals(77.0, macros.carbsG, 0.001)
        assertEquals(4.2, macros.fatG, 0.001)
    }

    private companion object {
        const val TAG = "FoodJournalTest"
    }
}
