package com.kaz229.fittrack

import com.kaz229.fittrack.data.Goal
import com.kaz229.fittrack.data.Intensity
import com.kaz229.fittrack.data.NeedsProfile
import com.kaz229.fittrack.data.Sex
import com.kaz229.fittrack.data.computeNeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calcul des besoins journaliers à partir du questionnaire. */
class NeedsTest {

    private val base = NeedsProfile(
        heightCm = 180,
        age = 30,
        sex = Sex.MALE,
        dailySteps = 8000,
        gymPerWeek = 3,
        gymMinutes = 60,
        gymIntensity = Intensity.MODERATE,
        runsPerWeek = 1,
        runMinutes = 30,
        runIntensity = Intensity.MODERATE,
        goal = Goal.MAINTAIN,
    )

    @Test
    fun `le metabolisme de base suit Mifflin-St Jeor`() {
        val needs = computeNeeds(base, weightKg = 75.0)!!

        // 10×75 + 6,25×180 − 5×30 + 5 = 1730
        assertEquals(1730, needs.bmr)
    }

    @Test
    fun `une femme a un metabolisme de base plus bas a gabarit egal`() {
        val homme = computeNeeds(base, 75.0)!!
        val femme = computeNeeds(base.copy(sex = Sex.FEMALE), 75.0)!!

        // La formule retranche 161 chez la femme et ajoute 5 chez l'homme
        assertEquals(166, homme.bmr - femme.bmr)
    }

    @Test
    fun `les pas et les entrainements s'ajoutent au maintien`() {
        val sedentaire = computeNeeds(
            base.copy(dailySteps = 0, gymPerWeek = 0, runsPerWeek = 0),
            75.0,
        )!!
        val actif = computeNeeds(base, 75.0)!!

        assertTrue(actif.stepsKcal > 0)
        assertTrue(actif.trainingKcal > 0)
        assertTrue(actif.maintenance > sedentaire.maintenance)
    }

    @Test
    fun `les seances sont reparties sur la semaine`() {
        val troisFois = computeNeeds(base.copy(gymPerWeek = 3, runsPerWeek = 0), 75.0)!!
        val sixFois = computeNeeds(base.copy(gymPerWeek = 6, runsPerWeek = 0), 75.0)!!

        // Doubler le nombre de séances double la part quotidienne, à l'arrondi près
        assertEquals(troisFois.trainingKcal * 2.0, sixFois.trainingKcal.toDouble(), 1.0)
    }

    @Test
    fun `une intensite plus forte coute plus cher`() {
        val tranquille = computeNeeds(base.copy(gymIntensity = Intensity.EASY), 75.0)!!
        val intense = computeNeeds(base.copy(gymIntensity = Intensity.HARD), 75.0)!!

        assertTrue(intense.trainingKcal > tranquille.trainingKcal)
    }

    @Test
    fun `perdre un demi-kilo par semaine creuse un deficit de 550 kcal par jour`() {
        val maintien = computeNeeds(base, 75.0)!!
        val perte = computeNeeds(base.copy(goal = Goal.LOSE, weeklyLossKg = 0.5), 75.0)!!

        // 0,5 kg × 7700 kcal ÷ 7 jours = 550
        assertEquals(550, maintien.target - perte.target)
        assertEquals(3850, perte.weeklyDeficit)
    }

    @Test
    fun `un rythme de perte trop ambitieux est signale`() {
        val raisonnable = computeNeeds(base.copy(goal = Goal.LOSE, weeklyLossKg = 0.5), 75.0)!!
        val excessif = computeNeeds(base.copy(goal = Goal.LOSE, weeklyLossKg = 1.5), 75.0)!!

        assertTrue(!raisonnable.tooAggressive)
        assertTrue(excessif.tooAggressive)
    }

    @Test
    fun `un rythme borne par le plancher dit ce qui est reellement atteignable`() {
        // 1,5 kg par semaine demanderait 1650 kcal de déficit : la cible bute sur le plancher
        val needs = computeNeeds(base.copy(goal = Goal.LOSE, weeklyLossKg = 1.5), 75.0)!!

        assertTrue("Le plancher a borné la cible", needs.cappedByFloor)
        assertTrue(needs.tooAggressive)
        // Ce qui est vraiment perdu est en dessous du rythme demandé
        assertTrue(needs.achievableWeeklyLossKg < 1.5)
        assertEquals(0.84, needs.achievableWeeklyLossKg, 0.02)
    }

    @Test
    fun `l'objectif ne descend jamais sous un plancher vital`() {
        // Petit gabarit, sédentaire, perte maximale : le calcul brut passerait sous 1200
        val extreme = NeedsProfile(
            heightCm = 155, age = 60, sex = Sex.FEMALE,
            goal = Goal.LOSE, weeklyLossKg = 1.5,
        )

        val needs = computeNeeds(extreme, 50.0)!!

        assertEquals(1200, needs.target)
    }

    @Test
    fun `prendre du muscle ajoute un surplus mesure`() {
        val maintien = computeNeeds(base, 75.0)!!
        val prise = computeNeeds(base.copy(goal = Goal.GAIN), 75.0)!!

        assertEquals(250, prise.target - maintien.target)
    }

    @Test
    fun `les proteines montent en perte pour garder le muscle`() {
        val maintien = computeNeeds(base, 75.0)!!
        val perte = computeNeeds(base.copy(goal = Goal.LOSE), 75.0)!!

        assertEquals(135, maintien.macros.proteinG) // 1,8 g/kg
        assertEquals(165, perte.macros.proteinG) // 2,2 g/kg
    }

    @Test
    fun `les macros couvrent a peu pres l'objectif calorique`() {
        val needs = computeNeeds(base, 75.0)!!
        val m = needs.macros

        val fromMacros = m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9
        // Les glucides absorbent le solde : l'écart tient à l'arrondi au gramme près
        assertTrue("Écart de ${needs.target - fromMacros}", needs.target - fromMacros in 0..4)
    }

    @Test
    fun `les glucides ne deviennent jamais negatifs`() {
        // Objectif très bas et poids élevé : protéines et lipides dépassent les calories
        val needs = computeNeeds(
            NeedsProfile(heightCm = 150, age = 70, sex = Sex.FEMALE, goal = Goal.LOSE, weeklyLossKg = 1.5),
            weightKg = 120.0,
        )!!

        assertTrue(needs.macros.carbsG >= 0)
        assertTrue(needs.macros.fatG >= 0)
    }

    @Test
    fun `sans taille ni age aucun calcul n'est propose`() {
        assertNull(computeNeeds(NeedsProfile(), 75.0))
        assertNull(computeNeeds(base.copy(heightCm = 0), 75.0))
        assertNull(computeNeeds(base, weightKg = 0.0))
    }
}
