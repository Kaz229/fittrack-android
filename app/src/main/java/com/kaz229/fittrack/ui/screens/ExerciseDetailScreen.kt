package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.ui.ExerciseDetail
import com.kaz229.fittrack.ui.formatDay
import com.kaz229.fittrack.ui.formatWeight

/** Fiche d'un exercice : consignes en français, et ce que l'utilisateur y a déjà fait. */
@Composable
fun ExerciseDetailScreen(detail: ExerciseDetail?, modifier: Modifier = Modifier) {
    if (detail == null) {
        EmptyState(message = "Exercice introuvable.", modifier = modifier)
        return
    }
    val exercise = detail.exercise
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${exercise.bodyPartLabel} · ${exercise.equipmentLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Muscle ciblé : ${exercise.targetLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    Text(
                        text = "Muscles secondaires : ${exercise.secondaryLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        detail.bestSet?.let { best ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Record personnel", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${formatWeight(best.weightKg)} × ${best.reps} reps",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatDay(best.epochDay),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Consignes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        itemsIndexed(exercise.steps) { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = step, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Text(
                text = "Historique",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (detail.history.isEmpty()) {
            item {
                Text(
                    text = "Tu n'as pas encore fait cet exercice.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(detail.history, key = { it.id }) { set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = formatDay(set.epochDay), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${set.reps} reps × ${formatWeight(set.weightKg)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
