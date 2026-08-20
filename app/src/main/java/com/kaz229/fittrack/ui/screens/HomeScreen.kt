package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.DailySummary
import com.kaz229.fittrack.data.UserProfile
import com.kaz229.fittrack.ui.formatDay
import com.kaz229.fittrack.ui.formatVolume
import kotlin.math.abs

@Composable
fun HomeScreen(
    summary: DailySummary,
    profile: UserProfile,
    lastWeek: List<DailySummary>,
    volumeToday: Double,
    setCountToday: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TodayCard(summary = summary, goal = profile.dailyCalorieGoal)
        }
        item {
            TrainingCard(volumeToday = volumeToday, setCountToday = setCountToday)
        }
        item {
            Text(
                text = "7 derniers jours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(lastWeek.reversed()) { day ->
            WeekRow(day)
        }
        if (lastWeek.isEmpty()) {
            item {
                Text(
                    text = "Aucune donnée pour l'instant. Ajoute une séance ou un repas pour démarrer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TodayCard(summary: DailySummary, goal: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aujourd'hui", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${summary.net} kcal nettes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            val ratio = if (goal > 0) (summary.net.toFloat() / goal).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth(),
            )
            val remaining = goal - summary.net
            Text(
                text = if (remaining >= 0) {
                    "Il te reste $remaining kcal sur ton objectif de $goal kcal."
                } else {
                    "Tu as dépassé ton objectif de ${abs(remaining)} kcal."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatColumn("Ingéré", "${summary.caloriesIn} kcal")
                StatColumn("Brûlé", "${summary.caloriesOut} kcal")
                StatColumn("Objectif", "$goal kcal")
            }
        }
    }
}

@Composable
private fun TrainingCard(volumeToday: Double, setCountToday: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Entraînement du jour", style = MaterialTheme.typography.titleMedium)
            if (setCountToday == 0) {
                Text(
                    text = "Aucune série enregistrée aujourd'hui.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn("Séries", setCountToday.toString())
                    StatColumn("Volume soulevé", formatVolume(volumeToday))
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WeekRow(day: DailySummary) {
    val label = formatDay(day.epochDay)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "+${day.caloriesIn} / -${day.caloriesOut}  =  ${day.net} kcal",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
