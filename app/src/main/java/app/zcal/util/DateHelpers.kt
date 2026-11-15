package app.zcal.util

import app.zcal.domain.model.DaySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Section 8 & Section 11 Date, Streak & Month-Grid Helpers:
 * - Saves every meal with `localDate` (`yyyy-MM-dd`) in the user's local timezone at save time
 *   so travel across timezones never shifts meals to a different day.
 * - Computes streaks with optional 1-missed-day-per-week forgiveness (Section 11 rule).
 */
object DateHelpers {

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    fun todayLocalDateString(zoneId: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zoneId).format(ISO_DATE)

    fun parseLocalDate(dateStr: String): LocalDate =
        runCatching { LocalDate.parse(dateStr, ISO_DATE) }.getOrDefault(LocalDate.now())

    fun formatFriendlyDate(dateStr: String): String {
        val date = parseLocalDate(dateStr)
        val today = LocalDate.now()
        return when (date) {
            today -> "Today, ${date.format(SHORT_DAY)}"
            today.minusDays(1) -> "Yesterday, ${date.format(SHORT_DAY)}"
            else -> date.format(SHORT_DAY)
        }
    }

    fun formatYearMonth(yearMonth: YearMonth): String =
        yearMonth.atDay(1).format(MONTH_TITLE)

    fun currentWeekDates(
        referenceDate: LocalDate = LocalDate.now(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    ): List<LocalDate> {
        val start = referenceDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    /**
     * Computes consecutive logged days up to [today], optionally forgiving up to 1 missed day
     * per rolling 7-day window when [forgiveOneMissedDayPerWeek] is enabled (Section 11 Page 20).
     */
    fun computeCurrentStreak(
        loggedDates: Set<String>,
        today: LocalDate = LocalDate.now(),
        forgiveOneMissedDayPerWeek: Boolean = false
    ): Int {
        if (loggedDates.isEmpty()) return 0
        var streak = 0
        var cursor = if (loggedDates.contains(today.format(ISO_DATE))) {
            today
        } else if (loggedDates.contains(today.minusDays(1).format(ISO_DATE))) {
            today.minusDays(1)
        } else {
            return 0
        }

        var missesInCurrentSevenDays = 0
        var daysCheckedInWindow = 0

        while (true) {
            val key = cursor.format(ISO_DATE)
            if (loggedDates.contains(key)) {
                streak++
            } else if (forgiveOneMissedDayPerWeek && missesInCurrentSevenDays == 0) {
                missesInCurrentSevenDays++
            } else {
                break
            }

            cursor = cursor.minusDays(1)
            daysCheckedInWindow++
            if (daysCheckedInWindow >= 7) {
                daysCheckedInWindow = 0
                missesInCurrentSevenDays = 0
            }
        }
        return streak
    }

    fun computeBestStreak(summaries: List<DaySummary>): Int {
        val sortedDates = summaries
            .filter { it.mealCount > 0 }
            .map { parseLocalDate(it.localDate) }
            .distinct()
            .sorted()

        if (sortedDates.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until sortedDates.size) {
            if (sortedDates[i - 1].plusDays(1) == sortedDates[i]) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }
}
