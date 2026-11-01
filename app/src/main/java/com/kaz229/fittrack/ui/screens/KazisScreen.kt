package com.kaz229.fittrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kaz229.fittrack.data.KazisFetch
import com.kaz229.fittrack.data.KazisSettings
import com.kaz229.fittrack.ui.KazisState

/**
 * Réglages des repas dictés à Kazis.
 *
 * FitTrack va chercher ce qui a été déposé ; rien n'est poussé vers lui. La
 * récupération se fait au lancement de l'app, et à la demande depuis cet écran.
 */
@Composable
fun KazisScreen(
    state: KazisState,
    onSave: (KazisSettings) -> Unit,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember(state.settings.url) { mutableStateOf(state.settings.url) }
    var token by remember(state.settings.token) { mutableStateOf(state.settings.token) }
    val valid = url.isNotBlank() && token.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Repas dictés",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Dis à Kazis ce que tu as mangé, sur Telegram ou depuis un assistant, " +
                "et retrouve-le ici. FitTrack vient chercher les repas au lancement, " +
                "et quand tu appuies sur le bouton.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adresse du serveur") },
            placeholder = { Text("http://100.x.y.z:8765") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Jeton de synchro") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(KazisSettings(url, token)) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }

        if (state.settings.configured) {
            OutlinedButton(
                onClick = onFetch,
                enabled = !state.fetching,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.fetching) "Récupération…" else "Récupérer maintenant") }
        }
        if (state.fetching) CircularProgressIndicator()
        state.lastResult?.let { ResultCard(it) }

        HorizontalDivider()
        Text(
            text = "Comment ça marche",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Les repas attendent sur ton serveur tant que FitTrack ne les a pas pris : " +
                "rien ne se perd si le téléphone est éteint. Un repas donné par code-barres " +
                "arrive sans valeurs, et c'est FitTrack qui le retrouve chez Open Food Facts. " +
                "Un produit introuvable reste en attente plutôt que d'être enregistré faux.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "L'adresse est celle du serveur Kazis sur ton réseau privé, et le jeton " +
                "est le même que celui de l'app Kazis.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ResultCard(result: KazisFetch) {
    val (title, body) = when (result) {
        is KazisFetch.Success -> when {
            result.added == 0 && result.skipped == 0 ->
                "Rien de nouveau" to "Aucun repas en attente."
            result.skipped == 0 ->
                "${result.added} repas récupéré(s)" to "Ils sont dans le journal du jour concerné."
            else ->
                "${result.added} repas récupéré(s)" to
                    "${result.skipped} produit(s) introuvable(s) chez Open Food Facts : " +
                    "ils restent en attente, et tu peux les annuler depuis Telegram."
        }

        KazisFetch.NotConfigured ->
            "À configurer" to "Renseigne l'adresse du serveur et le jeton."

        KazisFetch.Unauthorized ->
            "Jeton refusé" to "Le serveur n'a pas accepté ce jeton. Vérifie-le côté Kazis."

        is KazisFetch.Failed ->
            "Serveur injoignable" to
                "${result.message}. Vérifie que Kazis tourne et que tu es bien sur le réseau privé."
    }
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
