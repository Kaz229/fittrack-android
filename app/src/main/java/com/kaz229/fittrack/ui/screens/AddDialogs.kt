package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.ui.formatGrams
import kotlin.math.roundToInt
import com.kaz229.fittrack.data.Activities
import com.kaz229.fittrack.data.Exercise
import com.kaz229.fittrack.data.ExerciseSet
import com.kaz229.fittrack.data.FoodProduct
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.data.portion

/**
 * Saisie d'une série. Les champs sont pré-remplis avec la série précédente du même
 * exercice : en salle, on refait le plus souvent la même chose au même poids.
 */
@Composable
fun AddSetDialog(
    exercise: Exercise,
    lastSet: ExerciseSet?,
    onDismiss: () -> Unit,
    onConfirm: (reps: Int, weightKg: Double) -> Unit,
) {
    var reps by remember { mutableStateOf(lastSet?.reps?.toString() ?: "10") }
    var weight by remember { mutableStateOf(lastSet?.weightKg?.toString() ?: "") }

    val parsedReps = reps.toIntOrNull()
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val valid = parsedReps != null && parsedReps > 0 && parsedWeight != null && parsedWeight >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${exercise.targetLabel} · ${exercise.equipmentLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Répétitions") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Poids (kg) — 0 au poids du corps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsedReps ?: 0, parsedWeight ?: 0.0) },
                enabled = valid,
            ) { Text("Ajouter la série") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Clôture d'une séance salle : la durée sert à estimer les calories brûlées. */
@Composable
fun FinishSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (durationMinutes: Int, note: String) -> Unit,
) {
    var duration by remember { mutableStateOf("60") }
    var note by remember { mutableStateOf("") }
    val parsedDuration = duration.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminer la séance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Durée totale (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsedDuration ?: 0, note.trim()) },
                enabled = parsedDuration != null && parsedDuration > 0,
            ) { Text("Terminer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Séance cardio ou sport : une activité et une durée suffisent. */
@Composable
fun AddCardioDialog(
    onDismiss: () -> Unit,
    onConfirm: (activityId: String, durationMinutes: Int, note: String) -> Unit,
) {
    var activityId by remember { mutableStateOf(Activities.all.first().id) }
    var duration by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsedDuration = duration.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cardio / sport") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Activities.all.forEach { activity ->
                        FilterChip(
                            selected = activity.id == activityId,
                            onClick = { activityId = activity.id },
                            label = { Text(activity.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Durée (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(activityId, parsedDuration ?: 0, note.trim()) },
                enabled = parsedDuration != null && parsedDuration > 0,
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Quantité d'un produit scanné et type de repas, avec l'apport recalculé en direct.
 *
 * Le champ est pré-rempli avec la portion de l'emballage quand Open Food Facts la
 * connaît, sinon 100 g — la référence des valeurs nutritionnelles.
 */
@Composable
fun PortionForm(
    product: FoodProduct,
    onConfirm: (grams: Double, mealType: MealType) -> Unit,
) {
    var grams by remember(product.barcode) {
        mutableStateOf((product.servingSizeG ?: 100.0).let { formatInput(it) })
    }
    var mealType by remember { mutableStateOf(MealType.LUNCH) }
    val parsedGrams = grams.replace(',', '.').toDoubleOrNull()
    val valid = parsedGrams != null && parsedGrams > 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Pour 100 g : ${product.kcalPer100g.roundToInt()} kcal · " +
                "P ${formatGrams(product.proteinPer100g)} · " +
                "G ${formatGrams(product.carbsPer100g)} · " +
                "L ${formatGrams(product.fatPer100g)}",
            style = MaterialTheme.typography.bodySmall,
        )
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
        OutlinedTextField(
            value = grams,
            onValueChange = { grams = it },
            label = { Text("Quantité (g)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (valid) {
            val portion = product.portion(parsedGrams)
            HorizontalDivider()
            Text(
                text = "${portion.calories} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            MacroRow(
                proteinG = portion.proteinG,
                carbsG = portion.carbsG,
                fatG = portion.fatG,
            )
        }
        Button(
            onClick = { onConfirm(parsedGrams ?: 0.0, mealType) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ajouter au journal") }
    }
}

/**
 * Saisie libre d'un aliment : plat maison, ou produit absent d'Open Food Facts.
 * Les macros sont optionnelles — mieux vaut une ligne avec seulement des calories
 * qu'un repas non enregistré.
 */
@Composable
fun AddMealDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        calories: Int,
        mealType: MealType,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealType.LUNCH) }
    val parsedCalories = calories.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel aliment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Aliment / plat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Macros (optionnel, en grammes)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroField("Prot.", protein, { protein = it }, Modifier.weight(1f))
                    MacroField("Gluc.", carbs, { carbs = it }, Modifier.weight(1f))
                    MacroField("Lip.", fat, { fat = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        parsedCalories ?: 0,
                        mealType,
                        protein.toGramsOrZero(),
                        carbs.toGramsOrZero(),
                        fat.toGramsOrZero(),
                    )
                },
                enabled = name.isNotBlank() && parsedCalories != null && parsedCalories > 0,
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun String.toGramsOrZero(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0

/** « 30 » plutôt que « 30.0 » dans un champ de saisie. */
private fun formatInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
