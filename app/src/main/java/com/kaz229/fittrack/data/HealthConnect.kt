package com.kaz229.fittrack.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType as HcMealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Ce que le téléphone peut faire avec Health Connect. */
enum class HealthConnectAvailability {
    /** Disponible et utilisable. */
    AVAILABLE,

    /** Health Connect est absent ou demande une mise à jour : on peut proposer l'installation. */
    UPDATE_REQUIRED,

    /** Version d'Android trop ancienne : rien à proposer. */
    UNSUPPORTED,
}

/** Une séance écrite dans Health Connect par une autre application. */
data class ForeignSession(
    val id: String,
    val title: String?,
    val exerciseType: Int,
    val source: String,
    val start: java.time.Instant,
    val minutes: Int,
    val day: LocalDate,
    val end: java.time.Instant,
) {
    /** Musculation ou cardio, selon le type déclaré par l'application d'origine. */
    val isStrength: Boolean
        get() = exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING ||
            exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING

    /** Deux séances qui se recouvrent dans le temps décrivent le même entraînement. */
    fun overlaps(other: ForeignSession): Boolean = start < other.end && other.start < end

    /** Entre deux doublons : l'application préférée, puis la séance la plus longue. */
    fun preferredOver(other: ForeignSession): Boolean {
        val mine = HealthConnectManager.rankOf(source)
        val theirs = HealthConnectManager.rankOf(other.source)
        return if (mine != theirs) mine < theirs else minutes > other.minutes
    }
}

/** Résultat d'une synchronisation, pour l'afficher sans interpréter une exception. */
sealed interface SyncResult {
    data class Success(
        val sessions: Int,
        val meals: Int,
        val weightKg: Double?,
        val imported: Int = 0,
    ) : SyncResult
    data object PermissionsMissing : SyncResult
    data object Unavailable : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Pont avec Health Connect.
 *
 * L'app reste entièrement fonctionnelle sans : chaque appel vérifie la disponibilité
 * et les autorisations, et renvoie un [SyncResult] explicite plutôt que de lever.
 *
 * L'idempotence repose sur `clientRecordId` : Health Connect remplace un enregistrement
 * portant le même identifiant au lieu d'en créer un second. Une synchronisation relancée
 * dix fois ne duplique donc rien, et corriger une séance met à jour l'enregistrement
 * existant — sans qu'on ait à retenir localement ce qui a déjà été exporté.
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNSUPPORTED
        }

    suspend fun hasAllPermissions(): Boolean {
        val granted = client?.permissionController?.getGrantedPermissions() ?: return false
        return granted.containsAll(PERMISSIONS)
    }

    /**
     * Écrit les séances et les repas de la période, puis relit le dernier poids connu.
     *
     * La fenêtre est volontairement courte (les [days] derniers jours) : réécrire tout
     * l'historique à chaque synchronisation serait coûteux et inutile, puisque les
     * enregistrements déjà envoyés restent en place.
     */
    suspend fun sync(
        sessions: List<Session>,
        meals: List<Meal>,
        today: LocalDate = LocalDate.now(),
        days: Int = 30,
    ): SyncResult = withContext(Dispatchers.IO) {
        val client = client ?: return@withContext SyncResult.Unavailable
        if (!hasAllPermissions()) return@withContext SyncResult.PermissionsMissing

        val from = today.minusDays((days - 1).toLong())
        val recentSessions = sessions.filter { it.finished && it.epochDay >= from.toEpochDay() }
        val recentMeals = meals.filter { it.epochDay >= from.toEpochDay() }

        try {
            val now = Instant.now()
            val records = buildList {
                addAll(exerciseRecords(recentSessions, now = now))
                addAll(recentMeals.map { nutritionRecord(it, now = now) })
            }
            if (records.isNotEmpty()) client.insertRecords(records)

            SyncResult.Success(
                sessions = recentSessions.size,
                meals = recentMeals.size,
                weightKg = latestWeightKg(),
            )
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Échec de la synchronisation")
        }
    }

    /**
     * Les séances enregistrées par d'autres applications sur la période.
     *
     * Celles que FitTrack a écrites sont écartées : elles viennent d'ici, les réimporter
     * créerait un doublon à chaque synchronisation.
     */
    suspend fun foreignSessions(
        today: LocalDate = LocalDate.now(),
        days: Int = 30,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ForeignSession> = withContext(Dispatchers.IO) {
        val client = client ?: return@withContext emptyList()
        val from = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        try {
            val own = context.packageName
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, Instant.now()),
                ),
            ).records
                .filter { it.metadata.dataOrigin.packageName != own }
                .map { record ->
                    ForeignSession(
                        id = record.metadata.id,
                        title = record.title?.takeIf { it.isNotBlank() },
                        exerciseType = record.exerciseType,
                        source = record.metadata.dataOrigin.packageName,
                        start = record.startTime,
                        minutes = Duration.between(record.startTime, record.endTime)
                            .toMinutes().toInt().coerceAtLeast(1),
                        day = record.startTime.atZone(zone).toLocalDate(),
                        end = record.endTime,
                    )
                }
                .let(::dropDuplicates)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Dernier poids enregistré par n'importe quelle application, en kilos. */
    suspend fun latestWeightKg(): Double? = withContext(Dispatchers.IO) {
        val client = client ?: return@withContext null
        try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records.firstOrNull()?.weight?.inKilograms
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Lien Play Store vers Health Connect, pour la proposition d'installation. */
        const val PLAY_STORE_URI =
            "market://details?id=com.google.android.apps.healthdata"

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )

        /**
         * Heure conventionnelle de début d'une séance.
         *
         * FitTrack ne retient que le jour d'une séance, jamais l'heure ; Health Connect
         * exige un créneau. Les séances d'une même journée sont donc posées à la suite
         * à partir de 18 h, dans l'ordre de leur identifiant : sans ce décalage, deux
         * séances du même jour se chevaucheraient et Health Connect les refuserait.
         */
        private val DEFAULT_SESSION_START: LocalTime = LocalTime.of(18, 0)

        /** Heures conventionnelles des repas, pour la même raison. */
        private fun mealTime(type: MealType): LocalTime = when (type) {
            MealType.BREAKFAST -> LocalTime.of(8, 0)
            MealType.LUNCH -> LocalTime.of(12, 30)
            MealType.DINNER -> LocalTime.of(19, 30)
            MealType.SNACK -> LocalTime.of(16, 0)
        }

        private fun hcMealType(type: MealType): Int = when (type) {
            MealType.BREAKFAST -> HcMealType.MEAL_TYPE_BREAKFAST
            MealType.LUNCH -> HcMealType.MEAL_TYPE_LUNCH
            MealType.DINNER -> HcMealType.MEAL_TYPE_DINNER
            MealType.SNACK -> HcMealType.MEAL_TYPE_SNACK
        }

        /**
         * Une séance donne deux enregistrements : la séance elle-même et les calories
         * qu'elle a brûlées, que Health Connect range dans des types distincts.
         */
        fun exerciseRecords(
            sessions: List<Session>,
            zone: ZoneId = ZoneId.systemDefault(),
            now: Instant = Instant.now(),
        ): List<Record> = sessions
            .groupBy { it.epochDay }
            .flatMap { (epochDay, daySessions) ->
                val ordered = daySessions.sortedBy { it.id }
                val day = LocalDate.ofEpochDay(epochDay)
                val totalMinutes = ordered.sumOf { it.durationMinutes.coerceAtLeast(1).toLong() }

                // Health Connect refuse tout enregistrement qui déborde dans le futur.
                // Le créneau conventionnel de 18 h est donc ramené en arrière quand la
                // journée en cours n'y est pas encore arrivée — une synchronisation
                // lancée le matin échouerait sinon systématiquement.
                var start = day.atTime(DEFAULT_SESSION_START)
                val lastEnd = start.plusMinutes(totalMinutes)
                val overflow = Duration.between(
                    now,
                    lastEnd.toInstant(zone.rules.getOffset(lastEnd)),
                )
                if (!overflow.isNegative && !overflow.isZero) {
                    start = start.minusMinutes(overflow.toMinutes() + 1)
                }

                ordered.flatMap { session ->
                    val duration = session.durationMinutes.coerceAtLeast(1).toLong()
                    val end = start.plusMinutes(duration)
                    val records = exerciseRecords(session, start, end, zone)
                    start = end
                    records
                }
            }

        private fun exerciseRecords(
            session: Session,
            start: LocalDateTime,
            end: LocalDateTime,
            zone: ZoneId,
        ): List<Record> {
            val offset = zone.rules.getOffset(start)
            val startInstant = start.toInstant(offset)
            val endInstant = end.toInstant(offset)
            val activity = session.activityId?.let { Activities.byId(it) }

            val exercise = ExerciseSessionRecord(
                startTime = startInstant,
                startZoneOffset = offset,
                endTime = endInstant,
                endZoneOffset = offset,
                exerciseType = exerciseType(session),
                title = activity?.label ?: "Séance de musculation",
                notes = session.note.takeIf { it.isNotBlank() },
                metadata = clientMetadata("fittrack-session-${session.id}"),
            )
            if (session.caloriesBurned <= 0) return listOf(exercise)

            return listOf(
                exercise,
                ActiveCaloriesBurnedRecord(
                    startTime = startInstant,
                    startZoneOffset = offset,
                    endTime = endInstant,
                    endZoneOffset = offset,
                    energy = Energy.kilocalories(session.caloriesBurned.toDouble()),
                    metadata = clientMetadata("fittrack-session-calories-${session.id}"),
                ),
            )
        }

        /** Une ligne du journal alimentaire, avec ses macros quand elles sont connues. */
        fun nutritionRecord(
            meal: Meal,
            zone: ZoneId = ZoneId.systemDefault(),
            now: Instant = Instant.now(),
        ): NutritionRecord {
            val type = MealType.byId(meal.mealType)
            val conventional = LocalDate.ofEpochDay(meal.epochDay).atTime(mealTime(type))
            // Même règle que pour les séances : un dîner noté le jour même ne peut pas
            // être enregistré à 19 h 30 s'il n'est que 16 h.
            val start = notInFuture(conventional, zone, now, durationMinutes = 30)
            val offset = zone.rules.getOffset(start)
            return NutritionRecord(
                startTime = start.toInstant(offset),
                startZoneOffset = offset,
                endTime = start.plusMinutes(30).toInstant(offset),
                endZoneOffset = offset,
                name = meal.name,
                mealType = hcMealType(type),
                energy = Energy.kilocalories(meal.calories.toDouble()),
                protein = meal.proteinG.takeIf { it > 0 }?.let(Mass::grams),
                totalCarbohydrate = meal.carbsG.takeIf { it > 0 }?.let(Mass::grams),
                totalFat = meal.fatG.takeIf { it > 0 }?.let(Mass::grams),
                metadata = clientMetadata("fittrack-meal-${meal.id}"),
            )
        }

        /**
         * Correspondance avec les types d'exercice de Health Connect. Le catalogue cardio
         * de FitTrack est plus fin que ce que couvrent les constantes : tout ce qui n'a
         * pas d'équivalent direct retombe sur « autre entraînement », qui reste exact.
         */
        private fun exerciseType(session: Session): Int =
            if (SessionKind.byId(session.kind) == SessionKind.GYM) {
                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            } else {
                when (session.activityId) {
                    "tapis" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
                    "course" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                    "velo" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                    "velo_appart" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
                    "rameur" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
                    "elliptique" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
                    "hiit" -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
                    "natation" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
                    "marche" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                    "football" -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
                    "basket" -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
                    "etirements" -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
                    "muscu" -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                    // Corde à sauter : aucune constante dédiée côté Health Connect.
                    else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
                }
            }

        /** Métadonnée portant l'identifiant stable qui évite les doublons. */
        private fun clientMetadata(clientRecordId: String): Metadata =
            Metadata.manualEntry(clientRecordId = clientRecordId)

        /**
         * Recule [dateTime] juste ce qu'il faut pour que le créneau se termine avant
         * [now], et le laisse intact quand il est déjà passé.
         */
        private fun notInFuture(
            dateTime: LocalDateTime,
            zone: ZoneId,
            now: Instant,
            durationMinutes: Long,
        ): LocalDateTime {
            val end = dateTime.plusMinutes(durationMinutes)
            val overflow = Duration.between(now, end.toInstant(zone.rules.getOffset(end)))
            return if (overflow.isNegative || overflow.isZero) {
                dateTime
            } else {
                dateTime.minusMinutes(overflow.toMinutes() + 1)
            }
        }

        /**
         * Applications préférées quand une même séance arrive deux fois, la première
         * l'emportant.
         *
         * Le cas vient des passerelles : une séance faite dans Lyfta part sur Strava,
         * qui l'écrit à son tour dans Health Connect. Les deux décrivent le même
         * entraînement ; celle de l'application où il a été saisi est la plus fidèle.
         */
        val PREFERRED_SOURCES = listOf("lyfta", "strava")

        /**
         * Écarte les séances qui en recouvrent une autre.
         *
         * Deux enregistrements qui se chevauchent dans le temps décrivent le même
         * entraînement : on ne garde que celui de l'application la mieux placée, et à
         * défaut le plus long, qui porte en général le plus de détail.
         */
        fun dropDuplicates(sessions: List<ForeignSession>): List<ForeignSession> {
            val kept = mutableListOf<ForeignSession>()
            for (session in sessions.sortedBy { it.start }) {
                val rival = kept.indexOfFirst { it.overlaps(session) }
                if (rival < 0) {
                    kept += session
                } else if (session.preferredOver(kept[rival])) {
                    kept[rival] = session
                }
            }
            return kept
        }

        fun rankOf(source: String): Int {
            val index = PREFERRED_SOURCES.indexOfFirst { source.contains(it, ignoreCase = true) }
            // Une application inconnue passe après celles qu'on sait préférer
            return if (index < 0) PREFERRED_SOURCES.size else index
        }

        /** Décalage horaire d'un instant local, exposé pour les tests. */
        fun offsetFor(dateTime: LocalDateTime, zone: ZoneId): ZoneOffset =
            zone.rules.getOffset(dateTime)
    }
}
