package com.kaz229.fittrack

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType as HcMealType
import com.kaz229.fittrack.data.HealthConnectManager
import com.kaz229.fittrack.data.Meal
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.Session
import com.kaz229.fittrack.data.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Conversion des séances et repas de FitTrack vers les enregistrements Health Connect.
 *
 * Le point sensible est l'heure : l'app ne retient que le jour, Health Connect exige un
 * créneau. Deux séances du même jour ne doivent donc jamais se chevaucher.
 */
class HealthConnectMappingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val date = LocalDate.of(2026, 3, 14)
    private val day = date.toEpochDay()

    /** Un « maintenant » postérieur aux créneaux conventionnels : cas d'une journée passée. */
    private val lateEnough: Instant = date.atTime(23, 0).toInstant(zone.rules.getOffset(date.atTime(23, 0)))

    private fun gymSession(id: Long, minutes: Int = 60, calories: Int = 300) = Session(
        id = id,
        kind = SessionKind.GYM.id,
        epochDay = day,
        durationMinutes = minutes,
        caloriesBurned = calories,
        finished = true,
    )

    @Test
    fun `une seance salle donne la seance et ses calories`() {
        val records = HealthConnectManager.exerciseRecords(listOf(gymSession(1)), zone, lateEnough)

        assertEquals(2, records.size)
        val exercise = records.filterIsInstance<ExerciseSessionRecord>().single()
        val calories = records.filterIsInstance<ActiveCaloriesBurnedRecord>().single()
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            exercise.exerciseType,
        )
        assertEquals(300.0, calories.energy.inKilocalories, 0.001)
        assertEquals(60, Duration.between(exercise.startTime, exercise.endTime).toMinutes())
    }

    @Test
    fun `une seance sans calories ne produit pas d'enregistrement de depense`() {
        val records = HealthConnectManager.exerciseRecords(
            listOf(gymSession(1, calories = 0)),
            zone,
            lateEnough,
        )

        assertEquals(1, records.size)
        assertTrue(records.single() is ExerciseSessionRecord)
    }

    @Test
    fun `deux seances du meme jour ne se chevauchent pas`() {
        val records = HealthConnectManager.exerciseRecords(
            listOf(gymSession(1, minutes = 45), gymSession(2, minutes = 30)),
            zone,
            lateEnough,
        ).filterIsInstance<ExerciseSessionRecord>().sortedBy { it.startTime }

        assertEquals(2, records.size)
        // La seconde démarre exactement où la première s'arrête : jamais de recouvrement,
        // que Health Connect refuserait.
        assertTrue(records[0].endTime <= records[1].startTime)
        assertEquals(45, Duration.between(records[0].startTime, records[0].endTime).toMinutes())
        assertEquals(30, Duration.between(records[1].startTime, records[1].endTime).toMinutes())
    }

    @Test
    fun `une seance de duree nulle dure au moins une minute`() {
        // Health Connect rejette un créneau vide : une séance clôturée sans durée saisie
        // doit quand même produire un enregistrement valide.
        val records = HealthConnectManager.exerciseRecords(
            listOf(gymSession(1, minutes = 0)),
            zone,
            lateEnough,
        ).filterIsInstance<ExerciseSessionRecord>()

        val record = records.single()
        assertTrue(record.endTime > record.startTime)
    }

    @Test
    fun `les identifiants clients sont stables et distincts`() {
        val first = HealthConnectManager.exerciseRecords(listOf(gymSession(7)), zone, lateEnough)
        val second = HealthConnectManager.exerciseRecords(listOf(gymSession(7)), zone, lateEnough)

        val ids = first.map { it.metadata.clientRecordId }
        // Rejouer la synchronisation doit produire les mêmes identifiants, sinon Health
        // Connect créerait des doublons au lieu de mettre à jour.
        assertEquals(ids, second.map { it.metadata.clientRecordId })
        assertEquals(ids.toSet().size, ids.size)
        assertTrue(ids.all { it != null && it.startsWith("fittrack-session-") })
    }

    @Test
    fun `une activite cardio est traduite dans le bon type`() {
        val cardio = Session(
            id = 1,
            kind = SessionKind.CARDIO.id,
            epochDay = day,
            activityId = "course",
            durationMinutes = 40,
            caloriesBurned = 400,
            finished = true,
        )

        val record = HealthConnectManager.exerciseRecords(listOf(cardio), zone, lateEnough)
            .filterIsInstance<ExerciseSessionRecord>()
            .single()

        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, record.exerciseType)
        assertEquals("Course à pied (extérieur)", record.title)
    }

    @Test
    fun `une activite sans equivalent retombe sur autre entrainement`() {
        val cordeASauter = Session(
            id = 1,
            kind = SessionKind.CARDIO.id,
            epochDay = day,
            activityId = "corde",
            durationMinutes = 15,
            finished = true,
        )

        val record = HealthConnectManager.exerciseRecords(listOf(cordeASauter), zone, lateEnough)
            .filterIsInstance<ExerciseSessionRecord>()
            .single()

        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, record.exerciseType)
    }

    @Test
    fun `un repas porte ses calories et ses macros`() {
        val meal = Meal(
            id = 3,
            name = "Riz blanc cuit",
            calories = 350,
            mealType = MealType.LUNCH.id,
            epochDay = day,
            proteinG = 7.0,
            carbsG = 77.0,
            fatG = 0.6,
        )

        val record = HealthConnectManager.nutritionRecord(meal, zone, lateEnough)

        assertEquals("Riz blanc cuit", record.name)
        assertEquals(HcMealType.MEAL_TYPE_LUNCH, record.mealType)
        assertEquals(350.0, record.energy!!.inKilocalories, 0.001)
        assertEquals(7.0, record.protein!!.inGrams, 0.001)
        assertEquals(77.0, record.totalCarbohydrate!!.inGrams, 0.001)
        assertEquals(0.6, record.totalFat!!.inGrams, 0.001)
        assertEquals("fittrack-meal-3", record.metadata.clientRecordId)
    }

    @Test
    fun `un repas sans macros n'envoie pas de zeros`() {
        val meal = Meal(
            id = 4,
            name = "Poulet basquaise maison",
            calories = 620,
            mealType = MealType.DINNER.id,
            epochDay = day,
        )

        val record = HealthConnectManager.nutritionRecord(meal, zone, lateEnough)

        // Envoyer 0 g de protéines serait une affirmation fausse : l'information est
        // absente, pas nulle.
        assertNull(record.protein)
        assertNull(record.totalCarbohydrate)
        assertNull(record.totalFat)
        assertEquals(620.0, record.energy!!.inKilocalories, 0.001)
    }

    @Test
    fun `une seance du jour ne part jamais dans le futur`() {
        // Synchronisation lancée à 16 h : le créneau conventionnel de 18 h déborderait,
        // et Health Connect rejetterait tout l'envoi.
        val maintenant = date.atTime(16, 0).let { it.toInstant(zone.rules.getOffset(it)) }

        val record = HealthConnectManager.exerciseRecords(
            listOf(gymSession(1, minutes = 75)),
            zone,
            maintenant,
        ).filterIsInstance<ExerciseSessionRecord>().single()

        assertTrue("Début dans le futur : ${record.startTime}", record.startTime < maintenant)
        assertTrue("Fin dans le futur : ${record.endTime}", record.endTime <= maintenant)
        assertEquals(75, Duration.between(record.startTime, record.endTime).toMinutes())
    }

    @Test
    fun `plusieurs seances ramenees en arriere ne se chevauchent pas`() {
        val maintenant = date.atTime(16, 0).let { it.toInstant(zone.rules.getOffset(it)) }

        val records = HealthConnectManager.exerciseRecords(
            listOf(gymSession(1, minutes = 45), gymSession(2, minutes = 30)),
            zone,
            maintenant,
        ).filterIsInstance<ExerciseSessionRecord>().sortedBy { it.startTime }

        assertTrue(records[0].endTime <= records[1].startTime)
        assertTrue(records[1].endTime <= maintenant)
    }

    @Test
    fun `un repas du jour ne part jamais dans le futur`() {
        // Un dîner saisi à l'avance, ou une synchronisation en milieu d'après-midi.
        val maintenant = date.atTime(16, 0).let { it.toInstant(zone.rules.getOffset(it)) }
        val diner = Meal(
            id = 1,
            name = "Poulet",
            calories = 500,
            mealType = MealType.DINNER.id,
            epochDay = day,
        )

        val record = HealthConnectManager.nutritionRecord(diner, zone, maintenant)

        assertTrue(record.startTime < maintenant)
        assertTrue(record.endTime <= maintenant)
    }

    @Test
    fun `une journee passee garde ses heures conventionnelles`() {
        val hier = Session(
            id = 1,
            kind = SessionKind.GYM.id,
            epochDay = day - 1,
            durationMinutes = 60,
            caloriesBurned = 300,
            finished = true,
        )

        val record = HealthConnectManager.exerciseRecords(listOf(hier), zone, lateEnough)
            .filterIsInstance<ExerciseSessionRecord>()
            .single()

        assertEquals(18, record.startTime.atZone(zone).hour)
    }

    @Test
    fun `chaque type de repas tombe a son heure habituelle`() {
        fun heure(type: MealType): Int {
            val meal = Meal(id = 1, name = "x", calories = 1, mealType = type.id, epochDay = day)
            return HealthConnectManager.nutritionRecord(meal, zone, lateEnough)
                .startTime.atZone(zone).hour
        }

        assertEquals(8, heure(MealType.BREAKFAST))
        assertEquals(12, heure(MealType.LUNCH))
        assertEquals(19, heure(MealType.DINNER))
        assertEquals(16, heure(MealType.SNACK))
    }
}
