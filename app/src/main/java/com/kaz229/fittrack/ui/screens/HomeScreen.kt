package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.kaz229.fittrack.data.DailySummary
import com.kaz229.fittrack.data.MacroTotals
import com.kaz229.fittrack.data.UserProfile
import com.kaz229.fittrack.ui.formatDay
import com.kaz229.fittrack.ui.formatVolume
import com.kaz229.fittrack.ui.theme.LocalMacroColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Coins et espacements communs : la grille doit respirer de la même façon partout. */
private val CardShape = RoundedCornerShape(28.dp)
private val CardPadding = 20.dp

@Composable
fun HomeScreen(
    summary: DailySummary,
    profile: UserProfile,
    macros: MacroTotals,
    lastWeek: List<DailySummary>,
    volumeToday: Double,
    setCountToday: Int,
    today: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(today) }
        item { CaloriesCard(summary, profile.dailyCalorieGoal) }
        item { MacrosCard(macros, profile) }
        item { TrendCard(lastWeek, profile.dailyCalorieGoal) }
        item { TrainingCard(volumeToday, setCountToday) }
    }
}

@Composable
private fun Header(today: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = today,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "Aujourd'hui", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun BentoCard(modifier: Modifier = Modifier, content: @Composable ColumnScopeAlias.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/** Le seul chiffre qu'on doit pouvoir lire à bout de bras. */
@Composable
private fun CaloriesCard(summary: DailySummary, goal: Int) {
    val remaining = goal - summary.net
    BentoCard {
        Text(
            text = if (remaining >= 0) "Il te reste" else "Tu as dépassé de",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = abs(remaining).toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "sur $goal kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        ThickBar(
            ratio = if (goal > 0) summary.net.toFloat() / goal else 0f,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${summary.caloriesIn} ingérées",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${summary.caloriesOut} brûlées",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacrosCard(macros: MacroTotals, profile: UserProfile) {
    val colors = LocalMacroColors.current
    BentoCard {
        MacroLine("Protéines", macros.proteinG, profile.macroGoals.proteinG, colors.protein)
        MacroLine("Glucides", macros.carbsG, profile.macroGoals.carbsG, colors.carbs)
        MacroLine("Lipides", macros.fatG, profile.macroGoals.fatG, colors.fat)
    }
}

@Composable
private fun MacroLine(label: String, value: Double, goal: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${value.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " / $goal g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ThinBar(ratio = if (goal > 0) (value / goal).toFloat() else 0f, color = color)
    }
}

/**
 * Tendance des sept derniers jours, à l'échelle de l'objectif.
 *
 * Sans repère, des barres ne disent rien : la hauteur se lit par rapport à l'objectif,
 * et une journée qui le dépasse prend la couleur d'alerte.
 */
@Composable
private fun TrendCard(lastWeek: List<DailySummary>, goal: Int) {
    BentoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calories nettes", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "objectif $goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lastWeek.isEmpty()) {
            Text(
                text = "Pas encore d'historique.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@BentoCard
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            lastWeek.forEach { day ->
                DayColumn(day = day, goal = goal, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayColumn(day: DailySummary, goal: Int, modifier: Modifier = Modifier) {
    val ratio = if (goal > 0) (day.net.toFloat() / goal).coerceIn(0f, 1.2f) else 0f
    val over = goal > 0 && day.net > goal
    val label = formatDay(day.epochDay).take(3).lowercase()
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(ratio.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        over -> MaterialTheme.colorScheme.error
                        ratio > 0f -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrainingCard(volumeToday: Double, setCountToday: Int) {
    BentoCard {
        Text("Entraînement", style = MaterialTheme.typography.titleMedium)
        if (setCountToday == 0) {
            Text(
                text = "Aucune série aujourd'hui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat("Séries", setCountToday.toString())
                Stat("Volume", formatVolume(volumeToday))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ThickBar(ratio: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(color),
        )
    }
}

@Composable
private fun ThinBar(ratio: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(color),
        )
    }
}
