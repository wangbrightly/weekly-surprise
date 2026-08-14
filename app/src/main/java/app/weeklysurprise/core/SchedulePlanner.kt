package app.weeklysurprise.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * 抽日算法。
 *
 * 规则：每个自然周（周一起算）恰好一次；任意两次相隔不少于 [MIN_GAP_DAYS] 天；
 * 在此约束下具体哪天完全随机。
 *
 * 为什么要有最小间隔：纯随机允许"这周日 + 下周一"，两次只隔一天然后空 12 天，
 * 体感上完全不像"每周一次"。加了间隔约束后，"每周"的节奏保住，"哪天"仍不可预测。
 *
 * 为什么约束一定可满足：一周 7 天，最小间隔 4 天。最紧的情况是上次落在某周日，
 * 此时下一周仍有周四到周日共 4 个候选，候选集永远不会为空。
 */
object SchedulePlanner {

    const val MIN_GAP_DAYS = 4L

    /**
     * @param fromWeekStart 起始周的周一
     * @param weeks 规划多少周
     * @param lastReminderDate 上一批最后一次提醒的日期，用于跨批次也满足最小间隔；首次规划传 null
     */
    fun plan(
        fromWeekStart: LocalDate,
        weeks: Int,
        lastReminderDate: LocalDate?,
        random: Random,
    ): List<LocalDate> {
        require(fromWeekStart.dayOfWeek == DayOfWeek.MONDAY) {
            "起始日期必须是周一，实际是 ${fromWeekStart.dayOfWeek}（$fromWeekStart）"
        }
        require(weeks >= 0) { "周数不能为负: $weeks" }

        val result = ArrayList<LocalDate>(weeks)
        var previous = lastReminderDate

        for (i in 0 until weeks) {
            val weekStart = fromWeekStart.plusWeeks(i.toLong())
            val earliest = previous?.plusDays(MIN_GAP_DAYS)

            val candidates = (0L..6L)
                .map { weekStart.plusDays(it) }
                .filter { earliest == null || !it.isBefore(earliest) }

            check(candidates.isNotEmpty()) {
                "内部错误：第 ${i + 1} 周没有可用日期（上次=$previous，周起始=$weekStart）"
            }

            val picked = candidates[random.nextInt(candidates.size)]
            result += picked
            previous = picked
        }
        return result
    }

    /** 求包含 [date] 的那一周的周一。 */
    fun weekStartOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())
}
