package com.kaz229.fittrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

/**
 * Un produit alimentaire tel qu'Open Food Facts le décrit, mis en cache localement.
 *
 * Les valeurs nutritionnelles sont toujours ramenées à 100 g : c'est l'unité de
 * référence d'OFF, et la seule qui permette de calculer une portion arbitraire.
 * Le cache existe pour deux raisons : l'API est limitée à 15 requêtes par minute,
 * et un produit déjà scanné doit rester consultable sans réseau.
 */
@Entity(tableName = "food_products")
data class FoodProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val brands: String = "",
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    /** Poids d'une portion telle qu'indiquée sur l'emballage, quand OFF la connaît. */
    val servingSizeG: Double? = null,
    val fetchedAtEpochDay: Long = 0,
) {
    /** « Nutella — Ferrero », ou juste le nom quand la marque est inconnue. */
    val displayName: String get() = if (brands.isBlank()) name else "$name — $brands"
}

/** Les valeurs d'une portion : ce qui finit réellement enregistré dans le journal. */
data class Portion(
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Calcule au prorata ce qu'apportent [grams] grammes de ce produit. */
fun FoodProduct.portion(grams: Double): Portion {
    val ratio = grams / 100.0
    return Portion(
        calories = (kcalPer100g * ratio).roundToInt(),
        proteinG = round1(proteinPer100g * ratio),
        carbsG = round1(carbsPer100g * ratio),
        fatG = round1(fatPer100g * ratio),
    )
}

/** Somme des macros d'une journée, en grammes. */
data class MacroTotals(
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    /**
     * Calories reconstituées depuis les macros (4 kcal par gramme de protéines et de
     * glucides, 9 pour les lipides). Sert de repère : l'écart avec les calories
     * déclarées vient des fibres, du sucre-alcool et des arrondis de l'étiquetage.
     */
    val caloriesFromMacros: Int get() = (proteinG * 4 + carbsG * 4 + fatG * 9).roundToInt()
}

/** Objectifs journaliers de macros, en grammes. */
data class MacroGoals(
    val proteinG: Int = DEFAULT_PROTEIN_G,
    val carbsG: Int = DEFAULT_CARBS_G,
    val fatG: Int = DEFAULT_FAT_G,
) {
    companion object {
        const val DEFAULT_PROTEIN_G = 150
        const val DEFAULT_CARBS_G = 250
        const val DEFAULT_FAT_G = 70
    }
}

/**
 * Extrait un poids en grammes d'un `serving_size` d'Open Food Facts, champ libre
 * rempli à la main par les contributeurs : « 30 g », « 2 biscuits (25g) », « 100ml ».
 * Renvoie null quand aucun nombre suivi de g/ml n'est identifiable — on demandera
 * alors le poids à l'utilisateur.
 */
fun parseServingGrams(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml)\b""", RegexOption.IGNORE_CASE).find(raw)
    val value = match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
    return value.takeIf { it > 0 }
}

private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
