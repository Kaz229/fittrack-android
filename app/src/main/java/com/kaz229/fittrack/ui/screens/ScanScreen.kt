package com.kaz229.fittrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kaz229.fittrack.data.FoodProduct
import com.kaz229.fittrack.data.MealType
import com.kaz229.fittrack.ui.ScanError
import com.kaz229.fittrack.ui.ScanState

/**
 * Écran de scan d'un code-barres alimentaire.
 *
 * Trois états successifs : la caméra cherche, la recherche est en cours, puis soit le
 * produit est présenté pour saisir la quantité, soit l'échec est expliqué avec une
 * porte de sortie vers la saisie manuelle.
 */
@Composable
fun ScanScreen(
    state: ScanState,
    onBarcode: (String) -> Unit,
    onConfirm: (grams: Double, mealType: MealType) -> Unit,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRefused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRefused = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        CenteredMessage(
            title = if (permissionRefused) "Caméra refusée" else "Accès à la caméra",
            body = if (permissionRefused) {
                "Sans la caméra, impossible de lire un code-barres. Tu peux autoriser " +
                    "l'accès dans les réglages Android, ou saisir l'aliment à la main."
            } else {
                "FitTrack a besoin de la caméra pour lire le code-barres des produits."
            },
            actionLabel = "Saisir à la main",
            onAction = onManualEntry,
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.cameraActive) {
            CameraPreview(onBarcode = onBarcode, modifier = Modifier.fillMaxSize())
            Text(
                text = "Cadre le code-barres du produit",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        when {
            state.searching -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text("Recherche du produit…")
            }

            state.product != null -> ScannedProductPanel(
                product = state.product,
                fromCache = state.fromCache,
                onConfirm = onConfirm,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )

            state.error != null -> CenteredMessage(
                title = when (state.error) {
                    ScanError.NOT_FOUND -> "Produit inconnu"
                    ScanError.NO_NUTRITION -> "Valeurs nutritionnelles absentes"
                    ScanError.NETWORK -> "Pas de connexion"
                },
                body = when (state.error) {
                    ScanError.NOT_FOUND ->
                        "Le code-barres ${state.barcode} n'est pas dans Open Food Facts. " +
                            "Tu peux saisir l'aliment à la main."
                    ScanError.NO_NUTRITION ->
                        "Ce produit est référencé mais personne n'a encore renseigné ses " +
                            "valeurs nutritionnelles. Saisis-les à la main."
                    ScanError.NETWORK ->
                        "Impossible d'interroger Open Food Facts. Vérifie ta connexion, " +
                            "ou saisis l'aliment à la main."
                },
                actionLabel = "Saisir à la main",
                onAction = onManualEntry,
                secondaryLabel = "Réessayer",
                onSecondary = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Aperçu caméra branché sur l'analyseur de codes-barres de ML Kit.
 *
 * Le binding est fait une fois pour toute la vie de l'écran : le rejouer à chaque
 * recomposition rouvre et referme la caméra en boucle, et l'aperçu reste noir.
 */
@AndroidXOptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    // Les seuls formats présents sur un emballage alimentaire : restreindre accélère
    // la détection et évite de lire un QR code au passage.
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build(),
        )
    }
    // L'analyse tourne hors du thread principal : décoder 30 images par seconde
    // saccaderait l'aperçu.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        imageProxy.scanBarcode(scanner, onBarcode)
                    }
                }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            provider = cameraProvider
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            // La navigation ne détruit pas l'activité : sans libération explicite, la
            // caméra resterait ouverte après la sortie de l'écran.
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Passe une image de la caméra à ML Kit et remonte le premier code-barres lisible. */
@AndroidXOptIn(ExperimentalGetImage::class)
private fun ImageProxy.scanBarcode(scanner: BarcodeScanner, onBarcode: (String) -> Unit) {
    val mediaImage = image
    if (mediaImage == null) {
        close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcode)
        }
        .addOnCompleteListener { close() }
}

/** Produit reconnu : on demande la quantité et on montre l'apport correspondant. */
@Composable
private fun ScannedProductPanel(
    product: FoodProduct,
    fromCache: Boolean,
    onConfirm: (grams: Double, mealType: MealType) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.padding(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = product.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (fromCache) {
                Text(
                    text = "Déjà scanné : valeurs reprises du cache local.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PortionForm(product = product, onConfirm = onConfirm)
            TextButton(onClick = onRetry) { Text("Scanner un autre produit") }
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAction) { Text(actionLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}
