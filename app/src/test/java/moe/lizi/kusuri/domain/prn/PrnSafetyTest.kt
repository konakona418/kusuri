package moe.lizi.kusuri.domain.prn

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrnSafetyTest {

    private val now = Instant.parse("2026-09-28T10:00:00Z")

    @Test
    fun `no previous dose has no interval violation`() {
        val safety = prnSafety(
            lastTakenAt = null,
            takenTodayCount = 0,
            minIntervalMinutes = 360,
            maxPerDay = 4,
            now = now,
        )

        assertNull(safety.minutesSinceLastDose)
        assertFalse(safety.hasWarning)
    }

    @Test
    fun `dosing too soon violates the minimum interval`() {
        val safety = prnSafety(
            lastTakenAt = now.minus(Duration.ofHours(5)),
            takenTodayCount = 1,
            minIntervalMinutes = 360,
            maxPerDay = 4,
            now = now,
        )

        assertEquals(300L, safety.minutesSinceLastDose)
        assertTrue(safety.violatesMinInterval)
        assertTrue(safety.hasWarning)
    }

    @Test
    fun `enough time between doses is fine`() {
        val safety = prnSafety(
            lastTakenAt = now.minus(Duration.ofHours(7)),
            takenTodayCount = 1,
            minIntervalMinutes = 360,
            maxPerDay = 4,
            now = now,
        )

        assertFalse(safety.violatesMinInterval)
    }

    @Test
    fun `reaching the daily cap violates`() {
        val safety = prnSafety(
            lastTakenAt = null,
            takenTodayCount = 4,
            minIntervalMinutes = null,
            maxPerDay = 4,
            now = now,
        )

        assertTrue(safety.violatesMaxPerDay)
    }

    @Test
    fun `limits are optional`() {
        val safety = prnSafety(
            lastTakenAt = now.minus(Duration.ofMinutes(1)),
            takenTodayCount = 10,
            minIntervalMinutes = null,
            maxPerDay = null,
            now = now,
        )

        assertFalse(safety.hasWarning)
    }
}
