package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.Exercise
import com.kaz229.fittrack.data.ExerciseBlock
import com.kaz229.fittrack.data.ExerciseSet
import com.kaz229.fittrack.data.Session
import com.kaz229.fittrack.ui.CatalogState
import com.kaz229.fittrack.ui.formatDay
import com.kaz229.fittrack.ui.formatVolume
import com.kaz229.fittrack.ui.formatWeight

/**
 * Le détail d'une séance salle : les exercices déjà faits, leurs séries,
 * l'ajout d'un exercice via le catalogue, et la clôture de la séance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: Session?,
    blocks: List<ExerciseBlock>,
    exercisesById: Map<String, Exercise>,
    catalog: CatalogState,
    onQueryChange: (String) -> Unit,
    onBodyPartFilter: (String?) -> Unit,
    onEquipmentFilter: (String?) -> Unit,
    onAddSet: (exerciseId: String, reps: Int, weightKg: Double) -> Unit,
    onDeleteSet: (ExerciseSet) -> Unit,
    onFinish: (durationMinutes: Int, note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        EmptyState(message = "Séance introuvable.", modifier = modifier)
        return
    }

    var pickerOpen by remember { mutableStateOf(false) }
    var setDialogFor by remember { mutableStateOf<Exercise?>(null) }
    var finishDialogOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalVolume = blocks.sumOf { it.volume }
    val totalSets = blocks.sumOf { it.sets.size }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SessionStat("Date", formatDay(session.epochDay))
                    SessionStat("Séries", totalSets.toString())
                    SessionStat("Volume", formatVolume(totalVolume))
                }
            }
        }

        items(blocks, key = { it.exerciseId }) { block ->
            ExerciseBlockCard(
                block = block,
                exercise = exercisesById[block.exerciseId],
                onAddSet = { exercisesById[block.exerciseId]?.let { setDialogFor = it } },
                onDeleteSet = onDeleteSet,
            )
        }

        item {
            OutlinedButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Ajouter un exercice")
            }
        }

        if (!session.finished) {
            item {
                Button(
                    onClick = { finishDialogOpen = true },
                    enabled = totalSets > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Terminer la séance")
                }
            }
        } else {
            item {
                Text(
                    text = "Séance terminée : ${session.durationMinutes} min · " +
                        "${session.caloriesBurned} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.9f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Choisir un exercice",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { pickerOpen = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer")
                    }
                }
                ExercisesScreen(
                    state = catalog,
                    onQueryChange = onQueryChange,
                    onBodyPartFilter = onBodyPartFilter,
                    onEquipmentFilter = onEquipmentFilter,
                    onSelect = { exercise ->
                        pickerOpen = false
                        setDialogFor = exercise
                    },
                )
            }
        }
    }

    setDialogFor?.let { exercise ->
        val lastSet = blocks.firstOrNull { it.exerciseId == exercise.id }?.sets?.lastOrNull()
        AddSetDialog(
            exercise = exercise,
            lastSet = lastSet,
            onDismiss = { setDialogFor = null },
            onConfirm = { reps, weight ->
                onAddSet(exercise.id, reps, weight)
                setDialogFor = null
            },
        )
    }

    if (finishDialogOpen) {
        FinishSessionDialog(
            onDismiss = { finishDialogOpen = false },
            onConfirm = { duration, note ->
                onFinish(duration, note)
                finishDialogOpen = false
            },
        )
    }
}

@Composable
private fun SessionStat(label: String, value: String) {
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
private fun ExerciseBlockCard(
    block: ExerciseBlock,
    exercise: Exercise?,
    onAddSet: () -> Unit,
    onDeleteSet: (ExerciseSet) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exercise?.name ?: "Exercice ${block.exerciseId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            exercise?.let {
                Text(
                    text = "${it.targetLabel} · ${it.equipmentLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            block.sets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Série ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${set.reps} reps × ${formatWeight(set.weightKg)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { onDeleteSet(set) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Supprimer la série")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Volume : ${formatVolume(block.volume)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onAddSet) { Text("+ Série") }
            }
        }
    }
}
