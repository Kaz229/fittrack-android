package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.Exercise
import com.kaz229.fittrack.data.Labels
import com.kaz229.fittrack.ui.CatalogState

/**
 * Le catalogue : barre de recherche, filtres par groupe musculaire et matériel,
 * puis la liste des exercices. Réutilisé tel quel dans la feuille de sélection
 * pendant une séance.
 */
@Composable
fun ExercisesScreen(
    state: CatalogState,
    onQueryChange: (String) -> Unit,
    onBodyPartFilter: (String?) -> Unit,
    onEquipmentFilter: (String?) -> Unit,
    onSelect: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text("Rechercher un exercice") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FilterRow(
            values = state.bodyParts,
            selected = state.bodyPart,
            labelOf = Labels::bodyPart,
            onSelect = onBodyPartFilter,
        )
        FilterRow(
            values = state.equipments,
            selected = state.equipment,
            labelOf = Labels::equipment,
            onSelect = onEquipmentFilter,
        )
        Text(
            text = when {
                state.loading -> "Chargement du catalogue…"
                else -> "${state.exercises.size} exercice(s)"
            },
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        LazyColumn(contentPadding = contentPadding) {
            items(state.exercises, key = { it.id }) { exercise ->
                ExerciseRow(exercise = exercise, onClick = { onSelect(exercise) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FilterRow(
    values: List<String>,
    selected: String?,
    labelOf: (String) -> String,
    onSelect: (String?) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else value) },
                label = { Text(labelOf(value)) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${exercise.bodyPartLabel} · ${exercise.targetLabel} · ${exercise.equipmentLabel}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
