package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.MacroGoals
import com.kaz229.fittrack.data.MacroTotals
import com.kaz229.fittrack.data.Meal
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.ui.formatGrams
import com.kaz229.fittrack.ui.theme.LocalMacroColors
import kotlin.math.roundToInt

/**
 * Journal alimentaire présenté comme une ligne de temps.
 *
 * Les repas sont groupés par moment de la journée, dans l'ordre où on les prend :
 * un trou se voit alors tout de suite, là qu'une liste à plat le noyait.
 */
@Composable
fun MealsScreen(
    meals: List<Meal>,
    macros: MacroTotals,
    macroGoals: MacroGoals,
    onDelete: (Meal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byType = MealType.entries.associateWith { type ->
        meals.filter { it.mealType == type.id }
    }
    val total = meals.sumOf { it.calories }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = if (meals.isEmpty()) {
                        "Rien d'enregistré"
                    } else {
                        "$total kcal · P ${macros.proteinG.roundToInt()}" +
                            " · G ${macros.carbsG.roundToInt()}" +
                            " · L ${macros.fatG.roundToInt()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = "Repas", style = MaterialTheme.typography.headlineMedium)
            }
        }

        MealType.entries.forEach { type ->
            item(key = type.id) {
                MealBlock(
                    type = type,
                    meals = byType[type].orEmpty(),
                    onDelete = onDelete,
                )
            }
        }
    }
}

/** Un moment de la journée : son point sur la ligne, et ce qu'on y a mangé. */
@Composable
private fun MealBlock(type: MealType, meals: List<Meal>, onDelete: (Meal) -> Unit) {
    val filled = meals.isNotEmpty()
    Row(modifier = Modifier.fillMaxWidth()) {
        TimelineMarker(filled = filled)
        Column(
            modifier = Modifier.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!filled) {
                EmptyMeal(type)
                return@Column
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(text = type.label, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = meals.sumOf { it.calories }.toString(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = " kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    meals.forEach { meal -> MealLine(meal, onDelete = { onDelete(meal) }) }
                }
            }
        }
    }
}

@Composable
private fun TimelineMarker(filled: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                )
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(2.dp)
                .height(56.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun MealLine(meal: Meal, onDelete: () -> Unit) {
    val colors = LocalMacroColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = meal.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    meal.grams?.let { append("${formatGrams(it)} · ") }
                    append("${meal.calories} kcal")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (meal.hasMacros) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MacroTag("P ${meal.proteinG.roundToInt()} g", colors.protein)
                    MacroTag("G ${meal.carbsG.roundToInt()} g", colors.carbs)
                    MacroTag("L ${meal.fatG.roundToInt()} g", colors.fat)
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Supprimer ${meal.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroTag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Un repas vide reste visible : c'est ce qui rappelle de le remplir. */
@Composable
private fun EmptyMeal(type: MealType) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(18.dp),
    ) {
        Text(
            text = "Ajouter ${type.label.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
