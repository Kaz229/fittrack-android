package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.MacroGoals
import com.kaz229.fittrack.data.MacroTotals
import com.kaz229.fittrack.ui.formatGrams

/** Les trois macros côte à côte, sans objectif : pour une ligne de repas ou un aperçu. */
@Composable
fun MacroRow(
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MacroCell("Protéines", proteinG)
        MacroCell("Glucides", carbsG)
        MacroCell("Lipides", fatG)
    }
}

/** Les macros du jour face aux objectifs, chacune avec sa barre de progression. */
@Composable
fun MacroGoalsBlock(
    totals: MacroTotals,
    goals: MacroGoals,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MacroGoalBar("Protéines", totals.proteinG, goals.proteinG)
        MacroGoalBar("Glucides", totals.carbsG, goals.carbsG)
        MacroGoalBar("Lipides", totals.fatG, goals.fatG)
    }
}

@Composable
private fun MacroGoalBar(label: String, valueG: Double, goalG: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = "${formatGrams(valueG)} / $goalG g",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { if (goalG > 0) (valueG / goalG).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MacroCell(label: String, valueG: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = formatGrams(valueG),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
