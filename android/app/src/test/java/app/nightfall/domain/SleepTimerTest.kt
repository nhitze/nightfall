package app.nightfall.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SleepTimerTest {
    @Test
    fun fifteenMinuteTimerSetsExactDeadline() {
        val start = Instant.parse("2026-01-01T22:00:00Z")

        assertEquals(
            start.plusSeconds(15 * 60),
            SleepTimerOption.FIFTEEN_MINUTES.deadline(start, ZoneOffset.UTC),
        )
    }

    @Test
    fun untilMorningEndsAtNextSevenAm() {
        val start = Instant.parse("2026-01-01T22:00:00Z")

        assertEquals(
            Instant.parse("2026-01-02T07:00:00Z"),
            SleepTimerOption.UNTIL_MORNING.deadline(start, ZoneOffset.UTC),
        )
    }

    @Test
    fun untilMorningUsesSameDayWhenStartedBeforeSeven() {
        val start = Instant.parse("2026-01-01T02:00:00Z")

        assertEquals(
            Instant.parse("2026-01-01T07:00:00Z"),
            SleepTimerOption.UNTIL_MORNING.deadline(start, ZoneOffset.UTC),
        )
    }

    @Test
    fun timerCountsDownAndFadesAcrossFinalFiveMinutes() {
        val deadline = Instant.parse("2026-01-01T22:10:00Z")
        val timer = TimerSnapshot(deadline)

        assertEquals(Duration.ofMinutes(10), timer.remainingAt(deadline.minusSeconds(600)))
        assertEquals(1f, timer.fadeMultiplierAt(deadline.minusSeconds(300)), 0.001f)
        assertEquals(0.5f, timer.fadeMultiplierAt(deadline.minusSeconds(150)), 0.001f)
        assertEquals(0f, timer.fadeMultiplierAt(deadline), 0f)
    }
}
