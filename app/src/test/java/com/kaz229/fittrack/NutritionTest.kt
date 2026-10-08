package com.kaz229.fittrack

import com.kaz229.fittrack.data.FoodLookup
import com.kaz229.fittrack.data.FoodProduct
import com.kaz229.fittrack.data.MacroTotals
import com.kaz229.fittrack.data.OpenFoodFactsClient
import com.kaz229.fittrack.data.parseServingGrams
import com.kaz229.fittrack.data.portion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {

    private val nutella = FoodProduct(
        barcode = "3017620422003",
        name = "Nutella",
        brands = "Ferrero",
        kcalPer100g = 539.0,
        proteinPer100g = 6.3,
        carbsPer100g = 57.5,
        fatPer100g = 30.9,
    )

    @Test
    fun `une portion de 100 g reprend les valeurs de reference`() {
        val portion = nutella.portion(100.0)
        assertEquals(539, portion.calories)
        assertEquals(6.3, portion.proteinG, 0.001)
        assertEquals(57.5, portion.carbsG, 0.001)
        assertEquals(30.9, portion.fatG, 0.001)
    }

    @Test
    fun `une portion est calculee au prorata du poids`() {
        val portion = nutella.portion(30.0)
        assertEquals(162, portion.calories)
        assertEquals(1.9, portion.proteinG, 0.001)
        assertEquals(17.3, portion.carbsG, 0.001)
        assertEquals(9.3, portion.fatG, 0.001)
    }

    @Test
    fun `une portion nulle n'apporte rien`() {
        val portion = nutella.portion(0.0)
        assertEquals(0, portion.calories)
        assertEquals(0.0, portion.proteinG, 0.001)
    }

    @Test
    fun `les calories des macros suivent la regle 4-4-9`() {
        val totals = MacroTotals(proteinG = 100.0, carbsG = 200.0, fatG = 50.0)
        // 100×4 + 200×4 + 50×9 = 1650
        assertEquals(1650, totals.caloriesFromMacros)
    }

    @Test
    fun `le poids d'une portion est extrait des formats courants`() {
        assertEquals(30.0, parseServingGrams("30 g")!!, 0.001)
        assertEquals(25.0, parseServingGrams("2 biscuits (25g)")!!, 0.001)
        assertEquals(250.0, parseServingGrams("250 ml")!!, 0.001)
        assertEquals(12.5, parseServingGrams("12,5 g")!!, 0.001)
    }

    @Test
    fun `un serving_size inexploitable ne renvoie rien`() {
        assertNull(parseServingGrams(null))
        assertNull(parseServingGrams(""))
        assertNull(parseServingGrams("1 tranche"))
        assertNull(parseServingGrams("0 g"))
    }

    @Test
    fun `une reponse Open Food Facts complete donne un produit`() {
        val json = """
            {"code":"3017620422003","product":{
              "product_name":"Nutella","brands":"Nutella, Ferrero","serving_size":"15 g",
              "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,
                            "carbohydrates_100g":57.5,"fat_100g":30.9}}}
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProduct("3017620422003", json)

        assertTrue(result is FoodLookup.Found)
        val product = (result as FoodLookup.Found).product
        assertEquals("Nutella", product.name)
        // Seule la première marque est gardée : OFF en accumule plusieurs séparées par des virgules.
        assertEquals("Nutella", product.brands)
        assertEquals(539.0, product.kcalPer100g, 0.001)
        assertEquals(15.0, product.servingSizeG!!, 0.001)
    }

    @Test
    fun `le nom francais est prefere quand il existe`() {
        val json = """
            {"product":{"product_name":"Chocolate spread","product_name_fr":"Pâte à tartiner",
              "nutriments":{"energy-kcal_100g":500}}}
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProduct("123", json)

        assertEquals("Pâte à tartiner", (result as FoodLookup.Found).product.name)
    }

    @Test
    fun `les kilojoules servent de repli quand les kcal manquent`() {
        val json = """
            {"product":{"product_name":"Truc","nutriments":{"energy-kj_100g":2252}}}
        """.trimIndent()

        val result = OpenFoodFactsClient.parseProduct("123", json)

        // 2252 kJ ÷ 4,184 = 538,2 kcal
        assertEquals(538.2, (result as FoodLookup.Found).product.kcalPer100g, 0.1)
    }

    @Test
    fun `un statut zero signale un produit absent`() {
        val result = OpenFoodFactsClient.parseProduct("000", """{"status":0}""")
        assertEquals(FoodLookup.NotFound, result)
    }

    @Test
    fun `un produit sans energie est inexploitable`() {
        val json = """{"product":{"product_name":"Truc","nutriments":{"salt_100g":1.2}}}"""
        assertEquals(FoodLookup.NoNutrition, OpenFoodFactsClient.parseProduct("123", json))
    }

    @Test
    fun `un produit sans nom est inexploitable`() {
        val json = """{"product":{"nutriments":{"energy-kcal_100g":100}}}"""
        assertEquals(FoodLookup.NoNutrition, OpenFoodFactsClient.parseProduct("123", json))
    }

    @Test
    fun `les macros absentes valent zero et non NaN`() {
        val json = """
            {"product":{"product_name":"Eau","nutriments":{"energy-kcal_100g":0}}}
        """.trimIndent()

        val product = (OpenFoodFactsClient.parseProduct("123", json) as FoodLookup.Found).product

        assertEquals(0.0, product.proteinPer100g, 0.001)
        assertEquals(0.0, product.carbsPer100g, 0.001)
        assertEquals(0.0, product.fatPer100g, 0.001)
        assertNull(product.servingSizeG)
    }
}
