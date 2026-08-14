package app.weeklysurprise.core

import app.weeklysurprise.calendar.CalendarGateway
import java.time.LocalDate
import kotlin.random.Random

/**
 * 把"补足未来若干周的提醒"这件事串起来。
 *
 * 这一层刻意只依赖 [CalendarGateway] 接口而非安卓实现，
 * 所以可以用假日历在电脑上完整测试整条补足流程。
 *
 * 语义定义（这里曾经写错过，特此写明）：
 * [futureSlots] 是**未来始终保持的事件条数**，不是"从本周起排多少周"。
 * 每次打开 App 时，数一数未来还剩几条，缺多少补多少，接在最后一条之后排。
 * 这样无论隔多久打开一次，未来存量都稳定在 [futureSlots] 条，且永远不会在同一周排两次。
 */
class ReminderCoordinator(
    private val gateway: CalendarGateway,
    private val futureSlots: Int = DEFAULT_FUTURE_SLOTS,
) {

    /**
     * 把日历补满到未来 [futureSlots] 条。
     *
     * @param today 今天（注入以便测试）
     * @return 本次新写入的日期；已经满了就返回空表
     */
    fun topUp(
        calendarId: Long,
        today: LocalDate,
        settings: Settings,
        random: Random,
    ): List<LocalDate> {
        val existing = gateway.existingReminderDates(calendarId, today)
        val needed = futureSlots - existing.size
        if (needed <= 0) return emptyList()

        val lastExisting = existing.maxOrNull()

        // 有存量就接在最后一条的下一周；没有就从本周开始。
        val startWeek: LocalDate
        val previous: LocalDate
        if (lastExisting != null) {
            startWeek = SchedulePlanner.weekStartOf(lastExisting).plusWeeks(1)
            previous = lastExisting
        } else {
            startWeek = SchedulePlanner.weekStartOf(today)
            // 本周可能已经过了几天。把"上一次"设成 今天减最小间隔，
            // 使第一次最早只能排到今天，不会排进已经过去的星期一。
            previous = today.minusDays(SchedulePlanner.MIN_GAP_DAYS)
        }

        val planned = SchedulePlanner.plan(startWeek, needed, previous, random)
        val toWrite = EventDedupe.newDates(planned, existing)

        toWrite.forEach { date ->
            gateway.insertReminder(
                calendarId = calendarId,
                at = date.atTime(settings.hourOfDay, 0),
                title = EVENT_TITLE,
                description = EVENT_DESCRIPTION,
            )
        }
        return toWrite
    }

    companion object {
        const val DEFAULT_FUTURE_SLOTS = 12

        /** 中性标题：在日历列表里一眼扫过去看不出金额和内容。 */
        const val EVENT_TITLE = "给老婆的小惊喜"

        const val EVENT_DESCRIPTION = "打开「每周小惊喜」查看今天的金额和话术。"
    }
}
