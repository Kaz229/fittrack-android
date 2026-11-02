package com.kaz229.fittrack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Un repas déposé par un assistant sur Kazis, en attente de récupération.
 *
 * Deux formes : soit tout est renseigné (nom, calories, macros), soit seuls
 * [barcode] et [grams] le sont — c'est alors à FitTrack de retrouver le produit
 * chez Open Food Facts, avec le cache qu'il possède déjà.
 */
data class KazisMeal(
    val id: Long,
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val mealType: MealType,
    val day: LocalDate,
    val barcode: String?,
    val grams: Double?,
) {
    /** Un produit sans valeurs : il faut interroger Open Food Facts avant d'enregistrer. */
    val needsLookup: Boolean get() = calories <= 0 && barcode != null && grams != null
}

/** Ce que la restauration a donné. */
sealed interface KazisRestore {
    data class Success(val sessions: Int, val sets: Int, val meals: Int) : KazisRestore
    data object Empty : KazisRestore
    data object NotConfigured : KazisRestore
    data object Unauthorized : KazisRestore
    data class Failed(val message: String) : KazisRestore
}

/** Le contenu rendu par Kazis, prêt à être réécrit dans la base. */
data class KazisArchive(
    val sessions: List<Session>,
    val sets: List<ExerciseSet>,
    val meals: List<Meal>,
) {
    val isEmpty: Boolean get() = sessions.isEmpty() && sets.isEmpty() && meals.isEmpty()
}

/** Ce que la sauvegarde a donné. */
sealed interface KazisBackup {
    data class Success(val sessions: Int, val sets: Int, val meals: Int) : KazisBackup
    data object NotConfigured : KazisBackup
    data object Unauthorized : KazisBackup
    data class Failed(val message: String) : KazisBackup
}

/** Ce que la récupération a donné, pour l'afficher sans interpréter une exception. */
sealed interface KazisFetch {
    data class Success(val added: Int, val skipped: Int) : KazisFetch
    data object NotConfigured : KazisFetch
    data object Unauthorized : KazisFetch
    data class Failed(val message: String) : KazisFetch
}

/**
 * Client du serveur de synchro Kazis.
 *
 * Le serveur n'écoute que sur le réseau privé (Tailscale) et exige le même jeton que
 * l'app Kazis. Un seul appel HTTP à la fois, donc `HttpURLConnection` suffit.
 */
class KazisClient {

    /** Les repas en attente. Lève [KazisUnauthorized] si le jeton est refusé. */
    suspend fun pendingMeals(baseUrl: String, token: String): List<KazisMeal> =
        withContext(Dispatchers.IO) {
            val body = request(baseUrl, token, "/v1/meals", "GET", null)
            parseMeals(body)
        }

    /**
     * Envoie tout le contenu de l'app : séances, séries et journal.
     *
     * Rien n'est filtré sur la date. L'historique complet est petit (quelques milliers
     * de lignes), et le serveur remplace ce qu'il a déjà : une sauvegarde partielle
     * laisserait des trous après une restauration.
     */
    suspend fun backup(
        baseUrl: String,
        token: String,
        device: String,
        sessions: List<Session>,
        sets: List<ExerciseSet>,
        meals: List<Meal>,
    ): KazisBackup.Success = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("device", device)
            .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
            .put("sets", JSONArray().apply { sets.forEach { put(it.toJson()) } })
            .put("meals", JSONArray().apply { meals.forEach { put(it.toJson()) } })
        request(baseUrl, token, "/v1/fittrack", "POST", payload.toString())
        KazisBackup.Success(sessions.size, sets.size, meals.size)
    }

    /** Récupère la sauvegarde conservée par Kazis. */
    suspend fun archive(baseUrl: String, token: String): KazisArchive =
        withContext(Dispatchers.IO) { parseArchive(request(baseUrl, token, "/v1/fittrack", "GET", null)) }

    /** Confirme l'enregistrement : les repas sortent de la file côté Kazis. */
    suspend fun acknowledge(baseUrl: String, token: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("ids", JSONArray().apply { ids.forEach { put(it) } })
            request(baseUrl, token, "/v1/meals/ack", "POST", payload.toString())
        }
    }

    private fun request(
        baseUrl: String,
        token: String,
        path: String,
        method: String,
        body: String?,
    ): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { connection.outputStream.use { stream -> stream.write(it.toByteArray()) } }
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw KazisUnauthorized()
                else -> throw KazisError("le serveur a répondu ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000

        /** Lit la réponse de `/v1/meals`. Fonction pure, testable sans réseau. */
        fun parseMeals(json: String): List<KazisMeal> {
            val array = JSONObject(json).optJSONArray("meals") ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val day = runCatching { LocalDate.parse(item.optString("day")) }.getOrNull()
                    ?: return@mapNotNull null
                KazisMeal(
                    id = item.optLong("id"),
                    name = name,
                    calories = item.optInt("calories"),
                    proteinG = item.optDouble("protein_g", 0.0).orZero(),
                    carbsG = item.optDouble("carbs_g", 0.0).orZero(),
                    fatG = item.optDouble("fat_g", 0.0).orZero(),
                    mealType = MealType.byId(item.optString("meal_type")),
                    day = day,
                    barcode = item.optString("barcode").takeIf { it.isNotBlank() && it != "null" },
                    grams = item.optDouble("grams").takeIf { !it.isNaN() && it > 0 },
                )
            }
        }

        private fun Double.orZero(): Double = if (isNaN()) 0.0 else this

        /** Relit ce que `GET /v1/fittrack` a rendu. Fonction pure, testable sans réseau. */
        fun parseArchive(json: String): KazisArchive {
            val root = JSONObject(json)
            return KazisArchive(
                sessions = root.optJSONArray("sessions").map { item ->
                    Session(
                        id = item.optLong("id"),
                        kind = item.optString("kind"),
                        epochDay = LocalDate.parse(item.optString("day")).toEpochDay(),
                        activityId = item.optString("activity").takeIf { it.isNotBlank() && it != "null" },
                        durationMinutes = item.optInt("duration_min"),
                        caloriesBurned = item.optInt("kcal"),
                        note = item.optString("note"),
                        finished = true,
                    )
                },
                sets = root.optJSONArray("sets").map { item ->
                    ExerciseSet(
                        id = item.optLong("id"),
                        sessionId = item.optLong("session_id"),
                        exerciseId = item.optString("exercise_id"),
                        position = item.optInt("position"),
                        reps = item.optInt("reps"),
                        weightKg = item.optDouble("weight_kg", 0.0).orZero(),
                        epochDay = LocalDate.parse(item.optString("day")).toEpochDay(),
                    )
                },
                meals = root.optJSONArray("meals").map { item ->
                    Meal(
                        id = item.optLong("id"),
                        name = item.optString("name"),
                        calories = item.optInt("calories"),
                        mealType = item.optString("meal_type"),
                        epochDay = LocalDate.parse(item.optString("day")).toEpochDay(),
                        proteinG = item.optDouble("protein_g", 0.0).orZero(),
                        carbsG = item.optDouble("carbs_g", 0.0).orZero(),
                        fatG = item.optDouble("fat_g", 0.0).orZero(),
                    )
                },
            )
        }

        /** Une ligne illisible est ignorée : mieux vaut restaurer le reste que rien. */
        private fun <T> JSONArray?.map(build: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                optJSONObject(index)?.let { runCatching { build(it) }.getOrNull() }
            }
        }
    }
}

private fun Session.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("kind", kind)
    .put("day", LocalDate.ofEpochDay(epochDay).toString())
    .put("activity", activityId ?: JSONObject.NULL)
    .put("duration_min", durationMinutes)
    .put("kcal", caloriesBurned)
    .put("note", note)

private fun ExerciseSet.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("session_id", sessionId)
    .put("exercise_id", exerciseId)
    .put("position", position)
    .put("reps", reps)
    .put("weight_kg", weightKg)
    .put("day", LocalDate.ofEpochDay(epochDay).toString())

private fun Meal.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("calories", calories)
    .put("protein_g", proteinG)
    .put("carbs_g", carbsG)
    .put("fat_g", fatG)
    .put("meal_type", mealType)
    .put("day", LocalDate.ofEpochDay(epochDay).toString())

/** Jeton refusé : inutile de réessayer sans le corriger. */
class KazisUnauthorized : Exception("jeton refusé par Kazis")

/** Serveur injoignable ou réponse inattendue. */
class KazisError(message: String) : Exception(message)
