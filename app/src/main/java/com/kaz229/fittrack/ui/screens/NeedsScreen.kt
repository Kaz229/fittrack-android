package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.Goal
import com.kaz229.fittrack.data.Intensity
import com.kaz229.fittrack.data.Needs
import com.kaz229.fittrack.data.NeedsProfile
import com.kaz229.fittrack.data.Sex
import com.kaz229.fittrack.ui.formatWeight

/**
 * Questionnaire d'où sortent l'objectif calorique et les macros.
 *
 * Les chiffres se recalculent à chaque réponse et restent affichés : on voit tout de
 * suite ce que coûte une séance de plus ou un rythme de perte plus ambitieux. Rien
 * n'est appliqué au profil tant qu'on n'a pas validé.
 */
@Composable
fun NeedsScreen(
    answers: NeedsProfile,
    needs: Needs?,
    weightKg: Double,
    onChange: (NeedsProfile) -> Unit,
    onApply: () -> Unit,
    onRefreshWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                text = "Quelques questions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Mes besoins", style = MaterialTheme.typography.headlineMedium)
        }

        needs?.let { ResultCard(it, answers) }

        Section("Toi")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Taille (cm)",
                value = answers.heightCm,
                onValue = { onChange(answers.copy(heightCm = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Âge",
                value = answers.age,
                onValue = { onChange(answers.copy(age = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        ChipRow(
            values = Sex.entries.toList(),
            selected = answers.sex,
            labelOf = { it.label },
            onSelect = { onChange(answers.copy(sex = it)) },
        )
        WeightLine(weightKg = weightKg, onRefresh = onRefreshWeight)

        Section("Au quotidien")
        NumberField(
            label = "Pas par jour, en moyenne",
            value = answers.dailySteps,
            onValue = { onChange(answers.copy(dailySteps = it)) },
        )

        Section("Salle")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Séances / semaine",
                value = answers.gymPerWeek,
                onValue = { onChange(answers.copy(gymPerWeek = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Minutes",
                value = answers.gymMinutes,
                onValue = { onChange(answers.copy(gymMinutes = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        ChipRow(
            values = Intensity.entries.toList(),
            selected = answers.gymIntensity,
            labelOf = { it.label },
            onSelect = { onChange(answers.copy(gymIntensity = it)) },
        )

        Section("Course et cardio")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Sorties / semaine",
                value = answers.runsPerWeek,
                onValue = { onChange(answers.copy(runsPerWeek = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Minutes",
                value = answers.runMinutes,
                onValue = { onChange(answers.copy(runMinutes = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        ChipRow(
            values = Intensity.entries.toList(),
            selected = answers.runIntensity,
            labelOf = { it.label },
            onSelect = { onChange(answers.copy(runIntensity = it)) },
        )

        Section("Objectif")
        ChipRow(
            values = Goal.entries.toList(),
            selected = answers.goal,
            labelOf = { it.label },
            onSelect = { onChange(answers.copy(goal = it)) },
        )
        if (answers.goal == Goal.LOSE) {
            ChipRow(
                values = listOf(0.25, 0.5, 0.75, 1.0),
                selected = answers.weeklyLossKg,
                labelOf = { "$it kg / semaine" },
                onSelect = { onChange(answers.copy(weeklyLossKg = it)) },
            )
        }

        Button(
            onClick = onApply,
            enabled = needs != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Utiliser ces objectifs") }

        Text(
            text = "Ces formules sont des estimations de population, pas une prescription. " +
                "Les calories brûlées pendant une séance sont déjà comptées ici : l'objectif " +
                "est celui d'une journée, sport compris.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCard(needs: Needs, answers: NeedsProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Objectif proposé",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = needs.target.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = " kcal / jour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = "P ${needs.macros.proteinG} g · G ${needs.macros.carbsG} g · " +
                    "L ${needs.macros.fatG} g",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            Detail("Métabolisme de base", "${needs.bmr} kcal")
            Detail("Marche", "+ ${needs.stepsKcal} kcal")
            Detail("Entraînements", "+ ${needs.trainingKcal} kcal")
            Detail("Maintien", "${needs.maintenance} kcal")
            if (answers.goal == Goal.LOSE && needs.weeklyDeficit > 0) {
                Detail(
                    "Perte attendue",
                    "${"%.2f".format(needs.achievableWeeklyLossKg)} kg / semaine",
                )
            }
            if (needs.cappedByFloor) {
                Text(
                    text = "Ce rythme demanderait de descendre sous le minimum vital. " +
                        "L'objectif a été ramené au plancher : tu perdras environ " +
                        "${"%.2f".format(needs.achievableWeeklyLossKg)} kg par semaine, " +
                        "pas ${answers.weeklyLossKg}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (needs.tooAggressive) {
                Text(
                    text = "Un déficit de cette taille est difficile à tenir sans carences. " +
                        "Un rythme plus lent se garde mieux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeightLine(weightKg: Double, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Poids", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${formatWeight(weightKg)} · repris de Health Connect",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRefresh) { Text("Actualiser") }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Champ numérique qui garde son texte à part.
 *
 * Reconstruire la chaîne depuis l'entier à chaque frappe renvoie le curseur au début :
 * « 180 » se tapait « 801 ». Le texte saisi reste donc local, et seule la valeur
 * remonte.
 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Clé stable : une clé liée à la valeur recréerait le champ à la première frappe,
    // et l'affichage divergerait de ce qui a été saisi.
    var text by rememberSaveable { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { entry ->
            text = entry.filter { it.isDigit() }.take(6)
            onValue(text.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> ChipRow(
    values: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(labelOf(value)) },
            )
        }
    }
}
