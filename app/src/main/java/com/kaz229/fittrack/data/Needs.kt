package com.kaz229.fittrack.data

import kotlin.math.roundToInt

/** Sexe biologique, dont la formule du métabolisme de base a besoin. */
enum class Sex(val id: String, val label: String) {
    MALE("homme", "Homme"),
    FEMALE("femme", "Femme");

    companion object {
        fun byId(id: String?): Sex = entries.firstOrNull { it.id == id } ?: MALE
    }
}

/** Intensité déclarée d'un entraînement, qui pèse sur sa dépense. */
enum class Intensity(val id: String, val label: String, val met: Double) {
    EASY("facile", "Tranquille", 4.0),
    MODERATE("modere", "Modérée", 6.0),
    HARD("intense", "Intense", 8.5);

    companion object {
        fun byId(id: String?): Intensity = entries.firstOrNull { it.id == id } ?: MODERATE
    }
}

/** Ce que l'utilisateur vise : perdre, tenir, ou prendre. */
enum class Goal(val id: String, val label: String) {
    LOSE("perdre", "Perdre du poids"),
    MAINTAIN("maintenir", "Maintenir"),
    GAIN("prendre", "Prendre du muscle");

    companion object {
        fun byId(id: String?): Goal = entries.firstOrNull { it.id == id } ?: MAINTAIN
    }
}

/**
 * Les réponses du questionnaire, à partir desquelles tout est calculé.
 *
 * [weeklyLossKg] est un rythme hebdomadaire : positif pour une perte, et ignoré quand
 * l'objectif n'est pas d'en perdre.
 */
data class NeedsProfile(
    val heightCm: Int = 0,
    val age: Int = 0,
    val sex: Sex = Sex.MALE,
    val dailySteps: Int = 0,
    val gymPerWeek: Int = 0,
    val gymMinutes: Int = 60,
    val gymIntensity: Intensity = Intensity.MODERATE,
    val runsPerWeek: Int = 0,
    val runMinutes: Int = 30,
    val runIntensity: Intensity = Intensity.MODERATE,
    val goal: Goal = Goal.MAINTAIN,
    val weeklyLossKg: Double = 0.5,
) {
    /** Tant que la taille et l'âge manquent, aucun calcul n'est possible. */
    val complete: Boolean get() = heightCm > 0 && age > 0
}

/** Le détail du calcul, pour que l'écran puisse montrer d'où viennent les chiffres. */
data class Needs(
    val bmr: Int,
    val stepsKcal: Int,
    val trainingKcal: Int,
    val maintenance: Int,
    val target: Int,
    val macros: MacroGoals,
    val weeklyDeficit: Int,
    /** Perte réellement permise par la cible, qui peut être inférieure à celle demandée. */
    val achievableWeeklyLossKg: Double,
    /** Vrai quand le plancher vital a borné la cible : le rythme demandé est hors d'atteinte. */
    val cappedByFloor: Boolean,
) {
    /**
     * Un rythme intenable, soit parce que le déficit dépasse ce qu'on tient sans carences,
     * soit parce que le plancher vital a dû borner la cible.
     */
    val tooAggressive: Boolean get() = cappedByFloor || maintenance - target > 1000
}

/** Un kilo de masse grasse vaut environ 7 700 kcal. */
private const val KCAL_PER_KG = 7700.0

/** En dessous, un adulte ne couvre plus ses besoins de base. */
private const val FLOOR_MALE = 1500
private const val FLOOR_FEMALE = 1200

/**
 * Estime les besoins journaliers et une répartition de macros.
 *
 * Le métabolisme de base suit Mifflin-St Jeor, plus fiable que Harris-Benedict sur les
 * populations actuelles. L'activité n'est pas prise comme un facteur multiplicateur tout
 * fait, mais reconstituée : les pas d'un côté, les entraînements déclarés de l'autre.
 * C'est plus proche du réel pour quelqu'un dont la semaine varie.
 *
 * **Les calories brûlées pendant une séance ne sont pas comptées deux fois.** Elles
 * entrent ici, dans le besoin de maintien ; l'app les soustrait déjà de la journée quand
 * une séance est enregistrée. L'objectif affiché est donc un objectif *avant* sport.
 */
fun computeNeeds(profile: NeedsProfile, weightKg: Double): Needs? {
    if (!profile.complete || weightKg <= 0) return null

    val bmr = when (profile.sex) {
        Sex.MALE -> 10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + 5
        Sex.FEMALE -> 10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age - 161
    }

    // Marcher coûte environ 0,04 kcal par pas et par kilo : 8 000 pas à 75 kg ≈ 240 kcal
    val stepsKcal = profile.dailySteps * 0.0004 * weightKg

    // Les séances sont déclarées par semaine : on les ramène à la journée
    val gym = metKcal(profile.gymIntensity, profile.gymMinutes, weightKg) * profile.gymPerWeek
    val runs = metKcal(profile.runIntensity, profile.runMinutes, weightKg) * profile.runsPerWeek
    val trainingKcal = (gym + runs) / 7.0

    // Le corps dépense environ 10 % de ce qu'il ingère rien qu'à le digérer
    val maintenance = (bmr + stepsKcal + trainingKcal) * 1.1

    val dailyDelta = when (profile.goal) {
        Goal.LOSE -> -(profile.weeklyLossKg.coerceIn(0.0, 1.5) * KCAL_PER_KG / 7.0)
        Goal.GAIN -> 250.0
        Goal.MAINTAIN -> 0.0
    }
    val floor = if (profile.sex == Sex.MALE) FLOOR_MALE else FLOOR_FEMALE
    val wanted = (maintenance + dailyDelta).roundToInt()
    val target = wanted.coerceAtLeast(floor)
    val deficit = maintenance.roundToInt() - target

    return Needs(
        bmr = bmr.roundToInt(),
        stepsKcal = stepsKcal.roundToInt(),
        trainingKcal = trainingKcal.roundToInt(),
        maintenance = maintenance.roundToInt(),
        target = target,
        macros = macrosFor(profile.goal, weightKg, target),
        weeklyDeficit = deficit * 7,
        achievableWeeklyLossKg = if (deficit > 0) deficit * 7 / KCAL_PER_KG else 0.0,
        cappedByFloor = profile.goal == Goal.LOSE && wanted < floor,
    )
}

private fun metKcal(intensity: Intensity, minutes: Int, weightKg: Double): Double =
    intensity.met * 3.5 * weightKg / 200.0 * minutes

/**
 * Protéines et lipides d'abord, glucides avec ce qui reste.
 *
 * Les protéines se calent sur le poids (plus hautes en perte, pour garder le muscle),
 * les lipides sur un plancher hormonal, et les glucides absorbent le solde — ce sont
 * eux qui bougent quand l'objectif change.
 */
private fun macrosFor(goal: Goal, weightKg: Double, target: Int): MacroGoals {
    val proteinPerKg = when (goal) {
        Goal.LOSE -> 2.2
        Goal.GAIN -> 1.8
        Goal.MAINTAIN -> 1.8
    }
    val protein = (weightKg * proteinPerKg).roundToInt()
    val fat = (weightKg * 0.9).roundToInt()
    val remaining = target - protein * 4 - fat * 9
    // Jamais de glucides négatifs : sur un objectif très bas, on rogne les lipides
    val carbs = if (remaining >= 0) {
        remaining / 4
    } else {
        0
    }
    val adjustedFat = if (remaining >= 0) fat else ((target - protein * 4) / 9).coerceAtLeast(0)
    return MacroGoals(proteinG = protein, carbsG = carbs, fatG = adjustedFat)
}
