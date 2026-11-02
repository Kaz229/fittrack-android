package com.kaz229.fittrack

import com.kaz229.fittrack.data.KazisClient
import com.kaz229.fittrack.data.KazisSettings
import com.kaz229.fittrack.data.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Lecture de la file de repas servie par Kazis. */
class KazisTest {

    @Test
    fun `un repas complet est lu avec ses macros`() {
        val json = """
            {"meals": [{"id": 3, "name": "150 g de riz", "calories": 195,
              "protein_g": 4.0, "carbs_g": 42.0, "fat_g": 0.5,
              "meal_type": "dejeuner", "day": "2026-11-02", "barcode": null, "grams": null}]}
        """.trimIndent()

        val meal = KazisClient.parseMeals(json).single()

        assertEquals(3L, meal.id)
        assertEquals("150 g de riz", meal.name)
        assertEquals(195, meal.calories)
        assertEquals(4.0, meal.proteinG, 0.001)
        assertEquals(MealType.LUNCH, meal.mealType)
        assertEquals(LocalDate.of(2026, 11, 2), meal.day)
        assertNull(meal.barcode)
        assertFalse(meal.needsLookup)
    }

    @Test
    fun `un produit a code-barres attend d'etre resolu`() {
        val json = """
            {"meals": [{"id": 4, "name": "Produit 3017620422003", "calories": 0,
              "protein_g": 0, "carbs_g": 0, "fat_g": 0, "meal_type": "petit_dejeuner",
              "day": "2026-11-02", "barcode": "3017620422003", "grams": 30.0}]}
        """.trimIndent()

        val meal = KazisClient.parseMeals(json).single()

        // Sans valeurs mais avec un code : c'est à FitTrack d'interroger Open Food Facts.
        assertTrue(meal.needsLookup)
        assertEquals("3017620422003", meal.barcode)
        assertEquals(30.0, meal.grams!!, 0.001)
        assertEquals(MealType.BREAKFAST, meal.mealType)
    }

    @Test
    fun `une file vide ne donne rien`() {
        assertEquals(emptyList<Any>(), KazisClient.parseMeals("""{"meals": []}"""))
        assertEquals(emptyList<Any>(), KazisClient.parseMeals("""{}"""))
    }

    @Test
    fun `une ligne inexploitable est ignoree sans faire tomber les autres`() {
        val json = """
            {"meals": [
              {"id": 1, "name": "", "calories": 100, "meal_type": "dejeuner", "day": "2026-11-02"},
              {"id": 2, "name": "Sans date", "calories": 100, "meal_type": "dejeuner", "day": "jamais"},
              {"id": 3, "name": "Pomme", "calories": 72, "meal_type": "collation", "day": "2026-11-02"}
            ]}
        """.trimIndent()

        val meals = KazisClient.parseMeals(json)

        assertEquals(listOf("Pomme"), meals.map { it.name })
    }

    @Test
    fun `un type de repas inconnu retombe sur collation`() {
        val json = """
            {"meals": [{"id": 1, "name": "Truc", "calories": 100,
              "meal_type": "quatre_heures", "day": "2026-11-02"}]}
        """.trimIndent()

        assertEquals(MealType.SNACK, KazisClient.parseMeals(json).single().mealType)
    }

    @Test
    fun `les macros absentes valent zero`() {
        val json = """{"meals": [{"id": 1, "name": "Pomme", "calories": 72,
            "meal_type": "collation", "day": "2026-11-02"}]}"""

        val meal = KazisClient.parseMeals(json).single()

        assertEquals(0.0, meal.proteinG, 0.001)
        assertEquals(0.0, meal.carbsG, 0.001)
        assertNull(meal.grams)
    }

    @Test
    fun `une archive est relue avec ses charges`() {
        val json = """
            {"device": "pixel", "devices": ["pixel"],
             "sessions": [{"id": 4, "kind": "gym", "day": "2026-11-02", "activity": null,
                           "duration_min": 75, "kcal": 492, "note": "Push day"}],
             "sets": [{"id": 1, "session_id": 4, "exercise_id": "0025", "position": 0,
                       "reps": 10, "weight_kg": 60.0, "day": "2026-11-02"},
                      {"id": 2, "session_id": 4, "exercise_id": "0025", "position": 1,
                       "reps": 8, "weight_kg": 65.0, "day": "2026-11-02"}],
             "meals": [{"id": 1, "name": "ravioles fraiches", "calories": 420, "protein_g": 24.0,
                        "carbs_g": 36.0, "fat_g": 21.0, "meal_type": "dejeuner", "day": "2026-11-02"}]}
        """.trimIndent()

        val archive = KazisClient.parseArchive(json)

        // Les identifiants d'origine sont conservés : les séries pointent vers leur séance
        assertEquals(4L, archive.sessions.single().id)
        assertEquals("Push day", archive.sessions.single().note)
        assertEquals(listOf(10 to 60.0, 8 to 65.0), archive.sets.map { it.reps to it.weightKg })
        assertTrue(archive.sets.all { it.sessionId == 4L })
        assertEquals(420, archive.meals.single().calories)
        assertFalse(archive.isEmpty)
    }

    @Test
    fun `une archive vide est reconnue`() {
        val vide = KazisClient.parseArchive("""{"device": "", "sessions": [], "sets": [], "meals": []}""")

        assertTrue(vide.isEmpty)
        assertTrue(KazisClient.parseArchive("{}").isEmpty)
    }

    @Test
    fun `une ligne illisible n'empeche pas de restaurer le reste`() {
        val json = """
            {"sessions": [{"id": 1, "kind": "gym", "day": "jamais"},
                          {"id": 2, "kind": "gym", "day": "2026-11-02"}],
             "sets": [], "meals": []}
        """.trimIndent()

        // Mieux vaut récupérer une séance que d'abandonner toute la restauration
        assertEquals(listOf(2L), KazisClient.parseArchive(json).sessions.map { it.id })
    }

    @Test
    fun `les reglages ne sont complets qu'avec l'adresse et le jeton`() {
        assertFalse(KazisSettings().configured)
        assertFalse(KazisSettings(url = "http://100.1.2.3:8765").configured)
        assertFalse(KazisSettings(token = "secret").configured)
        assertTrue(KazisSettings("http://100.1.2.3:8765", "secret").configured)
    }
}
