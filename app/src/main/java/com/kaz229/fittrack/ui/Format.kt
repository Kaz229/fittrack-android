package com.kaz229.fittrack.ui

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** « 62,5 kg » mais « 60 kg » : on n'affiche la décimale que si elle existe. */
fun formatWeight(weightKg: Double): String =
    if (weightKg % 1.0 == 0.0) {
        "${weightKg.toInt()} kg"
    } else {
        String.format(Locale.FRENCH, "%.1f kg", weightKg)
    }

/** Le volume se compte vite en tonnes : au-delà de 1000 kg on bascule. */
fun formatVolume(volumeKg: Double): String = when {
    volumeKg >= 1000 -> String.format(Locale.FRENCH, "%.1f t", volumeKg / 1000)
    else -> "${volumeKg.roundToInt()} kg"
}

/** « Aujourd'hui », « Hier », sinon « lun. 12/05 ». */
fun formatDay(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> {
            val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH)
            "$weekday ${date.dayOfMonth}/${date.monthValue}"
        }
    }
}
