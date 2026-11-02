package com.kaz229.fittrack

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.kaz229.fittrack.data.ForeignSession
import com.kaz229.fittrack.data.HealthConnectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dédoublonnage des séances venues d'autres applications.
 *
 * Le cas réel : une séance faite dans Lyfta part sur Strava, qui l'écrit à son tour
 * dans Health Connect. L'historique affichait la même séance deux fois.
 */
class ForeignSessionTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val base: Instant = LocalDate.of(2026, 11, 2).atTime(18, 0).atZone(zone).toInstant()

    private fun session(
        id: String,
        source: String,
        startMinute: Long = 0,
        minutes: Int = 60,
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ): ForeignSession {
        val start = base.plusSeconds(startMinute * 60)
        return ForeignSession(
            id = id,
            title = null,
            exerciseType = type,
            source = source,
            start = start,
            minutes = minutes,
            day = LocalDate.of(2026, 11, 2),
            end = start.plusSeconds(minutes * 60L),
        )
    }

    @Test
    fun la_seance_relayee_par_strava_cede_la_place_a_lyfta() {
        val doublons = listOf(
            session("strava-1", "com.strava"),
            session("lyfta-1", "com.lyfta"),
        )

        val gardees = HealthConnectManager.dropDuplicates(doublons)

        assertEquals(listOf("lyfta-1"), gardees.map { it.id })
    }

    @Test
    fun l_ordre_d_arrivee_ne_change_rien() {
        val lyftaDabord = HealthConnectManager.dropDuplicates(
            listOf(session("lyfta-1", "com.lyfta"), session("strava-1", "com.strava")),
        )

        assertEquals(listOf("lyfta-1"), lyftaDabord.map { it.id })
    }

    @Test
    fun un_leger_decalage_entre_les_deux_reste_un_doublon() {
        // Strava recale souvent le créneau de quelques minutes
        val doublons = listOf(
            session("lyfta-1", "com.lyfta", startMinute = 0, minutes = 60),
            session("strava-1", "com.strava", startMinute = 3, minutes = 58),
        )

        assertEquals(listOf("lyfta-1"), HealthConnectManager.dropDuplicates(doublons).map { it.id })
    }

    @Test
    fun deux_seances_distinctes_sont_toutes_les_deux_gardees() {
        val journee = listOf(
            session("lyfta-1", "com.lyfta", startMinute = 0, minutes = 60),
            session("strava-course", "com.strava", startMinute = 120, minutes = 40),
        )

        // La course Strava n'a pas d'équivalent Lyfta : elle doit rester
        assertEquals(2, HealthConnectManager.dropDuplicates(journee).size)
    }

    @Test
    fun entre_deux_applications_inconnues_la_plus_longue_gagne() {
        val doublons = listOf(
            session("montre-1", "com.marque.montre", minutes = 45),
            session("autre-1", "com.autre.app", minutes = 62),
        )

        assertEquals(listOf("autre-1"), HealthConnectManager.dropDuplicates(doublons).map { it.id })
    }

    @Test
    fun une_application_connue_passe_devant_une_inconnue_plus_longue() {
        val doublons = listOf(
            session("montre-1", "com.marque.montre", minutes = 90),
            session("lyfta-1", "com.lyfta", minutes = 60),
        )

        assertEquals(listOf("lyfta-1"), HealthConnectManager.dropDuplicates(doublons).map { it.id })
    }

    @Test
    fun les_preferences_sont_ordonnees() {
        assertTrue(HealthConnectManager.rankOf("com.lyfta") < HealthConnectManager.rankOf("com.strava"))
        assertTrue(HealthConnectManager.rankOf("com.strava") < HealthConnectManager.rankOf("com.inconnu"))
    }

    @Test
    fun une_liste_vide_ne_pose_pas_de_probleme() {
        assertEquals(emptyList<ForeignSession>(), HealthConnectManager.dropDuplicates(emptyList()))
    }
}
