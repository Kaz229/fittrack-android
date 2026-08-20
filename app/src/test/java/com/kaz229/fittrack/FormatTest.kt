package com.kaz229.fittrack

import com.kaz229.fittrack.ui.formatDay
import com.kaz229.fittrack.ui.formatVolume
import com.kaz229.fittrack.ui.formatWeight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {

    @Test
    fun `whole weights lose their decimal`() {
        assertEquals("60 kg", formatWeight(60.0))
    }

    @Test
    fun `half plates keep their decimal`() {
        assertEquals("62,5 kg", formatWeight(62.5))
    }

    @Test
    fun `volume switches to tonnes past a thousand kilos`() {
        assertEquals("840 kg", formatVolume(840.0))
        assertEquals("4,2 t", formatVolume(4_200.0))
    }

    @Test
    fun `recent days are named`() {
        val today = LocalDate.of(2026, 8, 20)
        assertEquals("Aujourd'hui", formatDay(today.toEpochDay(), today))
        assertEquals("Hier", formatDay(today.minusDays(1).toEpochDay(), today))
        assertEquals("lun. 17/8", formatDay(today.minusDays(3).toEpochDay(), today))
    }
}
