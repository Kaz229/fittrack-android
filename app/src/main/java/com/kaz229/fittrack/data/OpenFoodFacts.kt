package com.kaz229.fittrack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Résultat d'une recherche de code-barres, pour que l'écran sache quoi afficher. */
sealed interface FoodLookup {
    /** Produit trouvé. [fromCache] distingue un scan hors ligne d'un appel réseau. */
    data class Found(val product: FoodProduct, val fromCache: Boolean) : FoodLookup

    /** Code-barres absent de la base : à l'utilisateur de saisir les valeurs. */
    data object NotFound : FoodLookup

    /** Pas de réseau, serveur en erreur, ou quota de 15 requêtes/min dépassé. */
    data object NetworkError : FoodLookup

    /** Produit connu d'OFF mais sans valeurs nutritionnelles exploitables. */
    data object NoNutrition : FoodLookup
}

/**
 * Client de lecture d'Open Food Facts.
 *
 * L'API est publique et sans authentification, mais impose un User-Agent identifiant
 * l'application et limite les lectures à 15 requêtes par minute et par IP — d'où le
 * cache local en amont (voir [FitTrackRepository.lookupFood]). Un seul appel HTTP,
 * donc `HttpURLConnection` suffit : pas de client HTTP tiers à embarquer.
 *
 * Seul le code-barres quitte le téléphone. Aucune donnée de séance, de repas ou de
 * profil n'est transmise.
 */
class OpenFoodFactsClient(private val baseUrl: String = BASE_URL) {

    suspend fun fetch(barcode: String): FoodLookup = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v2/product/$barcode.json?fields=$FIELDS"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseProduct(barcode, body)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> FoodLookup.NotFound
                else -> FoodLookup.NetworkError
            }
        } catch (_: Exception) {
            // Pas de réseau, DNS muet, timeout, JSON illisible : même conséquence pour
            // l'utilisateur, il saisira le produit à la main.
            FoodLookup.NetworkError
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://world.openfoodfacts.org"
        private const val TIMEOUT_MS = 8_000
        private const val FIELDS = "code,product_name,product_name_fr,brands,serving_size,nutriments"

        /** Format imposé par la politique d'usage d'OFF : nom, version, contact. */
        private const val USER_AGENT = "FitTrack/0.1.0 (github.com/Kaz229/fittrack-android)"

        /**
         * Transforme une réponse `/api/v2/product` en [FoodProduct].
         *
         * Fonction pure, testable sans réseau. Le nom français est préféré quand il
         * existe ; les macros sont lues dans les champs `*_100g`, seuls comparables
         * entre produits.
         */
        fun parseProduct(barcode: String, json: String): FoodLookup {
            val root = JSONObject(json)
            if (root.optInt("status", 1) == 0) return FoodLookup.NotFound
            val product = root.optJSONObject("product") ?: return FoodLookup.NotFound
            val nutriments = product.optJSONObject("nutriments") ?: return FoodLookup.NoNutrition

            val kcal = nutriments.optDoubleOrNull("energy-kcal_100g")
                // Certains produits ne renseignent que les kilojoules.
                ?: nutriments.optDoubleOrNull("energy-kj_100g")?.let { it / 4.184 }
                ?: return FoodLookup.NoNutrition

            val name = product.optStringOrNull("product_name_fr")
                ?: product.optStringOrNull("product_name")
                ?: return FoodLookup.NoNutrition

            return FoodLookup.Found(
                product = FoodProduct(
                    barcode = barcode,
                    name = name,
                    brands = product.optStringOrNull("brands")?.substringBefore(',')?.trim() ?: "",
                    kcalPer100g = kcal,
                    proteinPer100g = nutriments.optDoubleOrNull("proteins_100g") ?: 0.0,
                    carbsPer100g = nutriments.optDoubleOrNull("carbohydrates_100g") ?: 0.0,
                    fatPer100g = nutriments.optDoubleOrNull("fat_100g") ?: 0.0,
                    servingSizeG = parseServingGrams(product.optStringOrNull("serving_size")),
                ),
                fromCache = false,
            )
        }

        /** `optDouble` renvoie NaN pour un champ absent : on préfère un null explicite. */
        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            optDouble(key).takeIf { !it.isNaN() }

        private fun JSONObject.optStringOrNull(key: String): String? =
            optString(key).takeIf { it.isNotBlank() }
    }
}
