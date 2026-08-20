package com.kaz229.fittrack.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer

/**
 * Catalogue d'exercices chargé une fois depuis `assets/exercises.json`.
 *
 * Le fichier fait ~870 Ko : on le lit sur le dispatcher IO au premier accès,
 * puis tout tient en mémoire (1324 entrées, quelques Mo).
 */
class ExerciseCatalog private constructor(private val context: Context) {

    @Volatile
    private var cache: List<Exercise>? = null

    suspend fun all(): List<Exercise> = cache ?: withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    suspend fun byId(id: String): Exercise? = all().firstOrNull { it.id == id }

    suspend fun search(
        query: String = "",
        bodyPart: String? = null,
        equipment: String? = null,
    ): List<Exercise> = filter(all(), query, bodyPart, equipment)

    private fun load(): List<Exercise> {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    Exercise(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        bodyPart = item.getString("bodyPart"),
                        equipment = item.getString("equipment"),
                        target = item.getString("target"),
                        secondaryMuscles = item.getJSONArray("secondary").toStringList(),
                        steps = item.getJSONArray("steps").toStringList(),
                    ),
                )
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    companion object {
        private const val ASSET_NAME = "exercises.json"

        @Volatile
        private var instance: ExerciseCatalog? = null

        fun get(context: Context): ExerciseCatalog = instance ?: synchronized(this) {
            instance ?: ExerciseCatalog(context.applicationContext).also { instance = it }
        }

        /**
         * Recherche insensible à la casse et aux accents, sur le nom, le muscle et le matériel.
         * [bodyPart] et [equipment] filtrent en plus, quand ils sont renseignés.
         */
        fun filter(
            exercises: List<Exercise>,
            query: String = "",
            bodyPart: String? = null,
            equipment: String? = null,
        ): List<Exercise> {
            val needle = normalize(query)
            return exercises.filter { exercise ->
                (bodyPart == null || exercise.bodyPart == bodyPart) &&
                    (equipment == null || exercise.equipment == equipment) &&
                    (needle.isEmpty() || normalize(exercise.searchIndex).contains(needle))
            }
        }

        /** Minuscules sans accents, pour que « épaules » trouve « epaules ». */
        fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .trim()
    }
}
