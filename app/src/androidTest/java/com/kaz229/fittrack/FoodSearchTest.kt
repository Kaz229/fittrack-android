package com.kaz229.fittrack

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.FitTrackDatabase
import com.kaz229.fittrack.data.FitTrackRepository
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

/** Recherche d'aliments : historique local d'abord, Open Food Facts ensuite. */
@RunWith(AndroidJUnit4::class)
class FoodSearchTest {

    private lateinit var db: FitTrackDatabase
    private lateinit var repository: FitTrackRepository
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FitTrackDatabase::class.java).build()
        repository = FitTrackRepository(
            sessionDao = db.sessionDao(),
            exerciseSetDao = db.exerciseSetDao(),
            mealDao = db.mealDao(),
            foodProductDao = db.foodProductDao(),
            userPreferences = UserPreferences(context),
            exercises = ExerciseCatalog.get(context),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun un_aliment_deja_saisi_se_retrouve_par_son_debut() = runTest {
        repository.addMeal("Skyr nature Lidl", 130, MealType.BREAKFAST, 20.0, 8.0, 0.4, today.minusDays(2))

        val found = repository.searchFoods("sky")

        assertEquals(listOf("Skyr nature Lidl"), found.history.map { it.name })
        assertEquals(130, found.history.single().calories)
    }

    @Test
    fun seule_la_derniere_version_d_un_aliment_est_proposee() = runTest {
        repository.addMeal("Skyr nature", 120, MealType.BREAKFAST, 19.0, 7.0, 0.3, today.minusDays(3))
        repository.addMeal("Skyr nature", 180, MealType.BREAKFAST, 28.0, 11.0, 0.5, today.minusDays(1))

        val found = repository.searchFoods("skyr")

        // Un même aliment revient souvent : la dernière version est la plus à jour
        assertEquals(1, found.history.size)
        assertEquals(180, found.history.single().calories)
    }

    @Test
    fun une_recherche_trop_courte_ne_cherche_rien() = runTest {
        repository.addMeal("Skyr", 130, MealType.BREAKFAST, date = today)

        assertTrue(repository.searchFoods("s").isEmpty)
    }

    @Test
    fun un_aliment_repete_revient_au_jour_et_au_repas_choisis() = runTest {
        repository.addMeal("Skyr nature", 130, MealType.BREAKFAST, 20.0, 8.0, 0.4, today.minusDays(2))
        val ancien = repository.searchFoods("skyr").history.single()

        repository.repeatMeal(ancien, MealType.SNACK, today)

        val aujourdhui = repository.mealsForDay(today).first()
        assertEquals(1, aujourdhui.size)
        assertEquals(MealType.SNACK.id, aujourdhui.single().mealType)
        assertEquals(130, aujourdhui.single().calories)
        assertEquals(20.0, aujourdhui.single().proteinG, 0.001)
    }

    @Test
    fun open_food_facts_complete_la_recherche() = runTest {
        val found = repository.searchFoods("skyr")

        if (found.products.isEmpty()) {
            Log.w(TAG, "IGNORÉ : Open Food Facts injoignable ou sans résultat")
            return@runTest
        }
        Log.i(TAG, "Trouvés : ${found.products.take(3).map { it.displayName }}")
        assertTrue("Les produits doivent porter leurs calories", found.products.all { it.kcalPer100g > 0 })
    }

    private companion object {
        const val TAG = "FoodSearchTest"
    }
}
