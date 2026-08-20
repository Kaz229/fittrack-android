package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.Activities
import com.kaz229.fittrack.data.Session
import com.kaz229.fittrack.data.SessionKind
import com.kaz229.fittrack.ui.formatDay

@Composable
fun SessionsScreen(
    sessions: List<Session>,
    onOpen: (Session) -> Unit,
    onDelete: (Session) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        EmptyState(
            message = "Aucune séance enregistrée.\n" +
                "Appuie sur + pour démarrer une séance de salle ou noter du cardio.",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                onOpen = { onOpen(session) },
                onDelete = { onDelete(session) },
            )
        }
    }
}

@Composable
private fun SessionCard(session: Session, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kind = SessionKind.byId(session.kind)
    val title = when (kind) {
        SessionKind.GYM -> "Séance salle"
        SessionKind.CARDIO -> session.activityId?.let { Activities.byId(it).label } ?: "Cardio"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = kind == SessionKind.GYM, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(formatDay(session.epochDay))
                        if (session.durationMinutes > 0) append(" · ${session.durationMinutes} min")
                        if (session.caloriesBurned > 0) append(" · ${session.caloriesBurned} kcal")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (session.note.isNotBlank()) {
                    Text(text = session.note, style = MaterialTheme.typography.bodySmall)
                }
                if (kind == SessionKind.GYM && !session.finished) {
                    AssistChip(
                        onClick = onOpen,
                        label = { Text("En cours") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer la séance")
            }
        }
    }
}
