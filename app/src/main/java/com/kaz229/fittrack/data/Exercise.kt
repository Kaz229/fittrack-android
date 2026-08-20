package com.kaz229.fittrack.data

/**
 * Un exercice du catalogue (`assets/exercises.json`, 1324 entrées).
 *
 * Les noms restent en anglais — c'est le vocabulaire courant en salle et celui du
 * jeu de données d'origine. Les consignes, elles, sont en français.
 */
data class Exercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val target: String,
    val secondaryMuscles: List<String>,
    val steps: List<String>,
) {
    val bodyPartLabel: String get() = Labels.bodyPart(bodyPart)
    val equipmentLabel: String get() = Labels.equipment(equipment)
    val targetLabel: String get() = Labels.muscle(target)
    val secondaryLabels: List<String> get() = secondaryMuscles.map(Labels::muscle)

    /** Texte sur lequel porte la recherche : nom, muscle et matériel, en FR comme en EN. */
    val searchIndex: String = listOf(
        name, target, bodyPart, equipment,
        Labels.muscle(target), Labels.bodyPart(bodyPart), Labels.equipment(equipment),
    ).joinToString(" ").lowercase()
}

/** Traduction en français des libellés techniques du jeu de données. */
object Labels {

    private val bodyParts = mapOf(
        "back" to "Dos",
        "cardio" to "Cardio",
        "chest" to "Pectoraux",
        "lower arms" to "Avant-bras",
        "lower legs" to "Mollets",
        "neck" to "Cou",
        "shoulders" to "Épaules",
        "upper arms" to "Bras",
        "upper legs" to "Cuisses",
        "waist" to "Abdos / gainage",
    )

    private val equipments = mapOf(
        "assisted" to "Assisté",
        "band" to "Élastique",
        "barbell" to "Barre",
        "body weight" to "Poids du corps",
        "bosu ball" to "Bosu",
        "cable" to "Poulie",
        "dumbbell" to "Haltères",
        "elliptical machine" to "Elliptique",
        "ez barbell" to "Barre EZ",
        "hammer" to "Marteau",
        "kettlebell" to "Kettlebell",
        "leverage machine" to "Machine guidée",
        "medicine ball" to "Medecine ball",
        "olympic barbell" to "Barre olympique",
        "resistance band" to "Bande de résistance",
        "roller" to "Rouleau",
        "rope" to "Corde",
        "skierg machine" to "SkiErg",
        "sled machine" to "Presse / sled",
        "smith machine" to "Smith machine",
        "stability ball" to "Swiss ball",
        "stationary bike" to "Vélo d'appartement",
        "stepmill machine" to "Stepper",
        "tire" to "Pneu",
        "trap bar" to "Trap bar",
        "upper body ergometer" to "Ergomètre bras",
        "weighted" to "Lesté",
        "wheel roller" to "Roue abdominale",
    )

    private val muscles = mapOf(
        "abdominals" to "abdominaux",
        "abductors" to "abducteurs",
        "abs" to "abdominaux",
        "adductors" to "adducteurs",
        "ankle stabilizers" to "stabilisateurs de cheville",
        "ankles" to "chevilles",
        "back" to "dos",
        "biceps" to "biceps",
        "brachialis" to "brachial",
        "calves" to "mollets",
        "cardiovascular system" to "système cardiovasculaire",
        "chest" to "pectoraux",
        "core" to "gainage",
        "deltoids" to "deltoïdes",
        "delts" to "deltoïdes",
        "feet" to "pieds",
        "forearms" to "avant-bras",
        "glutes" to "fessiers",
        "grip muscles" to "muscles de la préhension",
        "groin" to "aine",
        "hamstrings" to "ischio-jambiers",
        "hands" to "mains",
        "hip flexors" to "fléchisseurs de hanche",
        "inner thighs" to "intérieur des cuisses",
        "latissimus dorsi" to "grand dorsal",
        "lats" to "grand dorsal",
        "levator scapulae" to "élévateur de la scapula",
        "lower abs" to "bas des abdominaux",
        "lower back" to "bas du dos",
        "obliques" to "obliques",
        "pectorals" to "pectoraux",
        "quadriceps" to "quadriceps",
        "quads" to "quadriceps",
        "rear deltoids" to "deltoïdes postérieurs",
        "rhomboids" to "rhomboïdes",
        "rotator cuff" to "coiffe des rotateurs",
        "serratus anterior" to "grand dentelé",
        "shins" to "tibias",
        "shoulders" to "épaules",
        "soleus" to "soléaire",
        "spine" to "colonne vertébrale",
        "sternocleidomastoid" to "sterno-cléido-mastoïdien",
        "traps" to "trapèzes",
        "trapezius" to "trapèzes",
        "triceps" to "triceps",
        "upper back" to "haut du dos",
        "upper chest" to "haut des pectoraux",
        "wrist extensors" to "extenseurs du poignet",
        "wrist flexors" to "fléchisseurs du poignet",
        "wrists" to "poignets",
    )

    fun bodyPart(value: String): String = bodyParts[value] ?: value.replaceFirstChar(Char::uppercase)

    fun equipment(value: String): String = equipments[value] ?: value.replaceFirstChar(Char::uppercase)

    fun muscle(value: String): String = muscles[value] ?: value
}
