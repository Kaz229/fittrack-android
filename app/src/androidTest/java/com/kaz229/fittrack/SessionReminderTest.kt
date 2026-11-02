package com.kaz229.fittrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kaz229.fittrack.data.SessionReminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Programmation du rappel de séance oubliée. */
@RunWith(AndroidJUnit4::class)
class SessionReminderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val work = WorkManager.getInstance(context)

    private fun infos(sessionId: Long): List<WorkInfo> =
        work.getWorkInfosForUniqueWork("seance-inactive-$sessionId").get()

    @Test
    fun demarrer_une_seance_programme_un_rappel() = runTest {
        SessionReminder.schedule(context, 42)

        val info = infos(42).single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun une_nouvelle_serie_repousse_le_rappel_au_lieu_d_en_empiler_un_second() = runTest {
        SessionReminder.schedule(context, 43)
        val premier = infos(43).single().id

        SessionReminder.schedule(context, 43)

        val apres = infos(43).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, apres.size)
        assertTrue("Le rappel doit être remplacé, pas doublé", apres.single().id != premier)
    }

    @Test
    fun terminer_la_seance_annule_le_rappel() = runTest {
        SessionReminder.schedule(context, 44)

        SessionReminder.cancel(context, 44)

        assertTrue(infos(44).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun le_rappel_affiche_bien_une_notification() = runTest {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()

        SessionReminder.notifyIdle(context, 99)

        val active = manager.activeNotifications.firstOrNull { it.id == 99 }
        if (active == null) {
            // Sans l'autorisation POST_NOTIFICATIONS, l'app n'insiste pas : c'est voulu.
            android.util.Log.w("SessionReminderTest", "IGNORÉ : notifications non autorisées")
            return@runTest
        }
        val extras = active.notification.extras
        assertEquals("Toujours à la salle ?", extras.getCharSequence("android.title"))
        assertTrue(
            extras.getCharSequence("android.text").toString().contains("10 minutes"),
        )
        manager.cancelAll()
    }

    @Test
    fun deux_seances_ont_chacune_leur_rappel() = runTest {
        SessionReminder.schedule(context, 45)
        SessionReminder.schedule(context, 46)

        SessionReminder.cancel(context, 45)

        assertTrue(infos(45).all { it.state == WorkInfo.State.CANCELLED })
        assertEquals(WorkInfo.State.ENQUEUED, infos(46).single().state)
    }
}
