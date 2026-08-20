package com.kaz229.fittrack

import com.kaz229.fittrack.data.Exercise
import com.kaz229.fittrack.data.ExerciseCatalog
import com.kaz229.fittrack.data.ExerciseSet
import com.kaz229.fittrack.data.Labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {

    private val benchPress = exercise(
        id = "0001",
        name = "barbell bench press",
        bodyPart = "chest",
        equipment = "barbell",
        target = "pectorals",
    )
    private val lateralRaise = exercise(
        id = "0002",
        name = "dumbbell lateral raise",
        bodyPart = "shoulders",
        equipment = "dumbbell",
        target = "delts",
    )
    private val pullUp = exercise(
        id = "0003",
        name = "pull-up",
        bodyPart = "back",
        equipment = "body weight",
        target = "lats",
    )
    private val catalog = listOf(benchPress, lateralRaise, pullUp)

    @Test
    fun `search matches the english name`() {
        val results = ExerciseCatalog.filter(catalog, query = "bench")
        assertEquals(listOf(benchPress), results)
    }

    @Test
    fun `search ignores case and accents`() {
        // « épaules » est le libellé français de "shoulders"
        val results = ExerciseCatalog.filter(catalog, query = "EPAULES")
        assertEquals(listOf(lateralRaise), results)
    }

    @Test
    fun `filters combine with the query`() {
        val results = ExerciseCatalog.filter(catalog, query = "", equipment = "dumbbell")
        assertEquals(listOf(lateralRaise), results)

        val none = ExerciseCatalog.filter(catalog, query = "bench", equipment = "dumbbell")
        assertTrue(none.isEmpty())
    }

    @Test
    fun `empty query returns everything`() {
        assertEquals(catalog, ExerciseCatalog.filter(catalog))
    }

    @Test
    fun `labels are translated to french`() {
        assertEquals("Pectoraux", Labels.bodyPart("chest"))
        assertEquals("Poids du corps", Labels.equipment("body weight"))
        assertEquals("grand dorsal", Labels.muscle("lats"))
    }

    @Test
    fun `unknown label falls back to the raw value`() {
        assertEquals("Trampoline", Labels.equipment("trampoline"))
    }

    @Test
    fun `set volume is reps times weight`() {
        val set = ExerciseSet(
            sessionId = 1,
            exerciseId = "0001",
            position = 0,
            reps = 10,
            weightKg = 60.0,
            epochDay = 20_000,
        )
        assertEquals(600.0, set.volume, 0.001)
    }

    private fun exercise(
        id: String,
        name: String,
        bodyPart: String,
        equipment: String,
        target: String,
    ) = Exercise(
        id = id,
        name = name,
        bodyPart = bodyPart,
        equipment = equipment,
        target = target,
        secondaryMuscles = emptyList(),
        steps = emptyList(),
    )
}
