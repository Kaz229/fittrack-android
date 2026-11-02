package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    // La séance ouverte remonte en tête : c'est la seule sur laquelle on peut agir
    val live = sessions.firstOrNull {
        SessionKind.byId(it.kind) == SessionKind.GYM && !it.finished
    }
    val past = sessions.filter { it.id != live?.id }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "${sessions.count { it.finished }} terminées",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = "Séances", style = MaterialTheme.typography.headlineMedium)
            }
        }

        live?.let { session ->
            item(key = "live") { LiveCard(session, onOpen = { onOpen(session) }) }
        }

        if (past.isEmpty() && live == null) {
            item {
                Text(
                    text = "Aucune séance enregistrée. Démarre une séance de salle, " +
                        "ou note du cardio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (past.isNotEmpty()) {
            item {
                Text(
                    text = "Historique",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(past, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    onOpen = { onOpen(session) },
                    onDelete = { onDelete(session) },
                )
            }
        }
    }
}

/** Séance ouverte : encadrée de l'accent, avec ce qu'on y a déjà fait. */
@Composable
private fun LiveCard(session: Session, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        // Sans contentColor explicite, Material déduit onSurfaceVariant du conteneur
        // et le titre ressort grisé.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "EN COURS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(text = "Séance salle", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Démarrée ${formatDay(session.epochDay).lowercase()} · " +
                    "appuie pour reprendre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(session: Session, onOpen: () -> Unit, onDelete: () -> Unit) {
    val kind = SessionKind.byId(session.kind)
    val title = when (kind) {
        SessionKind.GYM -> "Séance salle"
        SessionKind.CARDIO -> session.activityId?.let { Activities.byId(it).label } ?: "Cardio"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = kind == SessionKind.GYM, onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatDay(session.epochDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = buildString {
                        if (session.durationMinutes > 0) append("${session.durationMinutes} min")
                        if (session.caloriesBurned > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${session.caloriesBurned} kcal")
                        }
                        if (session.note.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(session.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Supprimer la séance",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
