package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.FoodProduct
import com.kaz229.fittrack.data.Meal
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.ui.FoodSearchState
import com.kaz229.fittrack.ui.formatGrams
import kotlin.math.roundToInt

/**
 * Recherche d'un aliment par son nom.
 *
 * Ce qu'on a déjà mangé passe devant : on remange surtout les mêmes choses, et
 * l'historique répond sans réseau. Les produits d'Open Food Facts viennent ensuite.
 */
@Composable
fun FoodSearchScreen(
    state: FoodSearchState,
    onQueryChange: (String) -> Unit,
    onRepeat: (Meal, MealType) -> Unit,
    onPickProduct: (FoodProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mealType by remember { mutableStateOf(MealType.LUNCH) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Chercher un aliment") },
                placeholder = { Text("skyr, poulet, riz…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = type == mealType,
                        onClick = { mealType = type },
                        label = { Text(type.label) },
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item { SectionTitle("Déjà mangé") }
            items(state.history, key = { "h${it.id}" }) { meal ->
                HistoryRow(meal = meal, onClick = { onRepeat(meal, mealType) })
            }
        }

        if (state.products.isNotEmpty()) {
            item { SectionTitle("Open Food Facts") }
            items(state.products, key = { "p${it.barcode}" }) { product ->
                ProductRow(product = product, onClick = { onPickProduct(product) })
            }
        }

        if (state.searching) {
            item { CircularProgressIndicator() }
        }
        if (!state.searching && state.query.length >= 2 && state.isEmpty) {
            item {
                Text(
                    text = "Rien trouvé pour « ${state.query} ». Scanne le code-barres, " +
                        "ou saisis l'aliment à la main.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.query.length < 2) {
            item {
                Text(
                    text = "Tape au moins deux lettres. Tes aliments déjà enregistrés " +
                        "apparaissent en premier, et se réinscrivent en un tap.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HistoryRow(meal: Meal, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    meal.grams?.let { append("${formatGrams(it)} · ") }
                    append("${meal.calories} kcal")
                    if (meal.hasMacros) {
                        append(" · P ${formatGrams(meal.proteinG)}")
                        append(" · G ${formatGrams(meal.carbsG)}")
                        append(" · L ${formatGrams(meal.fatG)}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProductRow(product: FoodProduct, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = product.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pour 100 g : ${product.kcalPer100g.roundToInt()} kcal · " +
                    "P ${formatGrams(product.proteinPer100g)} · " +
                    "G ${formatGrams(product.carbsPer100g)} · " +
                    "L ${formatGrams(product.fatPer100g)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
