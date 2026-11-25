package app.nightfall.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Serializable
enum class SleepTimerOption(
    val title: String,
    private val duration: Duration?,
) {
    FIFTEEN_MINUTES("15 minutes", Duration.ofMinutes(15)),
    THIRTY_MINUTES("30 minutes", Duration.ofMinutes(30)),
    FORTY_FIVE_MINUTES("45 minutes", Duration.ofMinutes(45)),
    ONE_HOUR("60 minutes", Duration.ofHours(1)),
    NINETY_MINUTES("90 minutes", Duration.ofMinutes(90)),
    UNTIL_MORNING("Until morning", null),
    ;

    fun deadline(
        from: Instant,
        zone: ZoneId,
    ): Instant {
        duration?.let { return from.plus(it) }

        val now = from.atZone(zone)
        var morning = now.toLocalDate().atTime(LocalTime.of(7, 0)).atZone(zone)
        if (!morning.isAfter(now)) {
            morning = morning.plusDays(1)
        }
        return morning.toInstant()
    }
}

data class TimerSnapshot(
    val deadline: Instant?,
) {
    fun remainingAt(now: Instant): Duration? =
        deadline?.let { end ->
            if (end.isAfter(now)) Duration.between(now, end) else Duration.ZERO
        }

    fun fadeMultiplierAt(
        now: Instant,
        fadeDuration: Duration = Duration.ofMinutes(5),
    ): Float {
        require(!fadeDuration.isNegative && !fadeDuration.isZero) {
            "Fade duration must be positive."
        }

        val remaining = remainingAt(now) ?: return 1f
        if (remaining.isZero) return 0f
        if (remaining >= fadeDuration) return 1f
        return remaining.toMillis().toFloat() / fadeDuration.toMillis()
    }
}
