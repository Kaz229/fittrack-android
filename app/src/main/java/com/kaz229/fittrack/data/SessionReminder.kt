package com.kaz229.fittrack.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kaz229.fittrack.R
import java.util.concurrent.TimeUnit

/**
 * Rappel quand une séance reste ouverte sans qu'on y ajoute rien.
 *
 * Une séance oubliée fausse tout : sa durée sert à estimer les calories, et elle
 * traîne dans la liste marquée « en cours ». Au bout de [IDLE_MINUTES] sans nouvelle
 * série, l'app demande si on est toujours là.
 *
 * Le rappel est reprogrammé à chaque série : seul le silence le déclenche. Il est
 * annulé à la clôture de la séance.
 */
object SessionReminder {

    const val IDLE_MINUTES = 10L
    private const val WORK_PREFIX = "seance-inactive-"
    private const val CHANNEL_ID = "seance_en_cours"

    fun schedule(context: Context, sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<IdleSessionWorker>()
            .setInitialDelay(IDLE_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(IdleSessionWorker.SESSION_ID to sessionId))
            .build()
        // REPLACE : une nouvelle série repousse le rappel au lieu d'en empiler un second
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_PREFIX + sessionId, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, sessionId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + sessionId)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Séance en cours",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Rappel quand une séance reste ouverte sans nouvelle série" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Affiche le rappel. Sans autorisation de notification, on n'insiste pas. */
    fun notifyIdle(context: Context, sessionId: Long) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = open?.let {
            android.app.PendingIntent.getActivity(
                context,
                sessionId.toInt(),
                it,
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Toujours à la salle ?")
            .setContentText(
                "Ta séance est ouverte depuis $IDLE_MINUTES minutes sans nouvelle série. " +
                    "Ajoute-la, ou termine la séance.",
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Ta séance est ouverte depuis $IDLE_MINUTES minutes sans nouvelle série. " +
                        "Ajoute-la, ou termine la séance pour que la durée et les calories " +
                        "restent justes.",
                ),
            )
            .setAutoCancel(true)
            .apply { pending?.let(::setContentIntent) }
            .build()
        NotificationManagerCompat.from(context).notify(sessionId.toInt(), notification)
    }
}

/** Vérifie, à l'heure dite, que la séance est encore ouverte avant de déranger. */
class IdleSessionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(SESSION_ID, -1)
        if (sessionId < 0) return Result.success()
        val session = FitTrackDatabase.get(applicationContext).sessionDao().findById(sessionId)
        // Séance terminée ou supprimée entre-temps : plus rien à rappeler
        if (session == null || session.finished) return Result.success()
        SessionReminder.notifyIdle(applicationContext, sessionId)
        return Result.success()
    }

    companion object {
        const val SESSION_ID = "sessionId"
    }
}
