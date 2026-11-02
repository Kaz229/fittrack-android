package com.kaz229.fittrack.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.kaz229.fittrack.data.HealthConnectAvailability
import com.kaz229.fittrack.data.HealthConnectManager
import com.kaz229.fittrack.data.SyncResult
import com.kaz229.fittrack.ui.HealthState
import com.kaz229.fittrack.ui.formatWeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Réglages de la synchronisation Health Connect.
 *
 * Tout y est explicite et déclenché à la main : rien ne part vers d'autres applications
 * sans que l'utilisateur appuie sur le bouton.
 */
@Composable
fun HealthConnectScreen(
    state: HealthState,
    onSync: () -> Unit,
    onPermissionsChanged: () -> Unit,
    onUseWeight: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { onPermissionsChanged() }

    // Les autorisations peuvent avoir changé dans les réglages de Health Connect,
    // hors de l'app : on les revérifie à chaque affichage de l'écran.
    LaunchedEffect(Unit) { onPermissionsChanged() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Health Connect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Partage tes séances et ton journal alimentaire avec les autres " +
                "applications santé du téléphone, et récupère ton poids si une autre " +
                "application le suit déjà.",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (state.availability) {
            HealthConnectAvailability.UNSUPPORTED -> InfoCard(
                title = "Indisponible sur cet appareil",
                body = "Health Connect demande une version d'Android plus récente. " +
                    "FitTrack fonctionne normalement sans.",
            )

            HealthConnectAvailability.UPDATE_REQUIRED -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(
                    title = "Health Connect à installer",
                    body = "L'application Health Connect est absente ou doit être mise à jour.",
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(HealthConnectManager.PLAY_STORE_URI)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ouvrir le Play Store") }
            }

            HealthConnectAvailability.AVAILABLE -> {
                if (!state.permissionsGranted) {
                    InfoCard(
                        title = "Autorisations à accorder",
                        body = "FitTrack écrit les séances, les calories brûlées et la " +
                            "nutrition, et lit uniquement le poids. Chaque autorisation " +
                            "reste révocable depuis Health Connect.",
                    )
                    Button(
                        onClick = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Accorder les autorisations") }
                } else {
                    Button(
                        onClick = onSync,
                        enabled = !state.syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.syncing) "Synchronisation…" else "Synchroniser maintenant") }
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Revoir les autorisations") }
                }

                if (state.syncing) CircularProgressIndicator()
                state.lastSyncEpochSecond?.let { LastSyncLine(it) }
                state.lastResult?.let { ResultCard(result = it, onUseWeight = onUseWeight) }
            }
        }

        HorizontalDivider()
        Text(
            text = "Ce qui sort du téléphone",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Les 30 derniers jours de séances et de repas sont envoyés à Health " +
                "Connect, qui les garde sur l'appareil et ne les communique qu'aux " +
                "applications que tu autorises. Chaque enregistrement porte un identifiant " +
                "stable : relancer la synchronisation met à jour l'existant au lieu de créer " +
                "des doublons.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Les séances enregistrées par une autre application (Lyfta, une montre…) " +
                "sont reprises dans ton historique, sans doublon : chacune garde son " +
                "identifiant d'origine. Quand la même séance arrive deux fois — Lyfta la " +
                "relaie sur Strava, qui l'écrit à son tour — seule celle de l'application " +
                "où elle a été saisie est gardée.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "FitTrack ne connaît que le jour d'une séance, jamais son heure. Les " +
                "séances sont donc placées en fin d'après-midi et les repas à l'heure " +
                "habituelle de leur catégorie — en reculant ce qu'il faut pour qu'un " +
                "enregistrement du jour ne tombe jamais dans le futur, ce que Health " +
                "Connect refuse.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LastSyncLine(epochSecond: Long) {
    val formatted = remember(epochSecond) {
        Instant.ofEpochSecond(epochSecond)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"))
    }
    Text(
        text = "Dernière synchronisation : $formatted",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ResultCard(result: SyncResult, onUseWeight: (Double) -> Unit) {
    when (result) {
        is SyncResult.Success -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${result.sessions} séance(s) et ${result.meals} ligne(s) " +
                        "de repas envoyées.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.imported > 0) {
                    Text(
                        text = "${result.imported} séance(s) reprise(s) d'une autre application.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                result.weightKg?.let { weight ->
                    Text(
                        text = "Poids trouvé dans Health Connect : ${formatWeight(weight)}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { onUseWeight(weight) }) {
                        Text("Utiliser ce poids dans mon profil")
                    }
                }
            }
        }

        SyncResult.PermissionsMissing -> InfoCard(
            title = "Autorisations manquantes",
            body = "Health Connect a refusé l'accès. Vérifie les autorisations accordées à FitTrack.",
        )

        SyncResult.Unavailable -> InfoCard(
            title = "Health Connect indisponible",
            body = "L'application n'a pas pu être jointe.",
        )

        is SyncResult.Failed -> InfoCard(
            title = "Échec de la synchronisation",
            body = result.message,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
