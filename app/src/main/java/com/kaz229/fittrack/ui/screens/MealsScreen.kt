package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.MacroGoals
import com.kaz229.fittrack.data.MacroTotals
import com.kaz229.fittrack.data.Meal
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.ui.formatGrams

@Composable
fun MealsScreen(
    meals: List<Meal>,
    macros: MacroTotals,
    macroGoals: MacroGoals,
    onDelete: (Meal) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (meals.isEmpty()) {
        EmptyState(
            message = "Aucun repas aujourd'hui.\n" +
                "Scanne un code-barres, ou saisis l'aliment à la main.",
            modifier = modifier,
        )
        return
    }
    val total = meals.sumOf { it.calories }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        // Marge basse dégagée : les deux boutons flottants (saisie et scan) recouvrent
        // sinon la dernière ligne du journal.
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Total du jour : $total kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    MacroGoalsBlock(totals = macros, goals = macroGoals)
                }
            }
        }
        items(meals, key = { it.id }) { meal ->
            MealCard(meal = meal, onDelete = { onDelete(meal) })
        }
    }
}

@Composable
private fun MealCard(meal: Meal, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(MealType.byId(meal.mealType).label)
                        meal.grams?.let { append(" · ${formatGrams(it)}") }
                        append(" · ${meal.calories} kcal")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (meal.hasMacros) {
                    Text(
                        text = "P ${formatGrams(meal.proteinG)} · " +
                            "G ${formatGrams(meal.carbsG)} · " +
                            "L ${formatGrams(meal.fatG)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer le repas")
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
