package app.weeklysurprise.data

import app.weeklysurprise.calendar.CalendarGateway
import app.weeklysurprise.core.HistoryEntry
import app.weeklysurprise.core.PhrasePicker
import app.weeklysurprise.core.Reminder
import app.weeklysurprise.core.ReminderCoordinator
import app.weeklysurprise.core.ReminderFactory
import java.time.LocalDate
import kotlin.random.Random

/**
 * 把存储、日历、纯逻辑三者串起来的薄薄一层。
 *
 * 坦白：这一层没有单元测试 —— 它依赖 [AppStore]（需要安卓 Context），
 * 为了测它得再抽一层接口，对这个体量的项目不划算。
 * 但它调用的每一处判断（排期、金额、话术、去重）都有测试覆盖，
 * 这里只剩"按顺序调用"这一件事。
 */
class PlanService(
    private val store: AppStore,
    private val gateway: CalendarGateway,
) {

    /** 把已经过去的排期归档进历史（未标记完成的记为未完成）。 */
    fun archivePast(today: LocalDate) {
        val past = store.plan.filter { it.date.isBefore(today) }
        if (past.isEmpty()) return

        val known = store.history.map { it.date }.toSet()
        val added = past.filter { it.date !in known }.map {
            HistoryEntry(
                date = it.date,
                amountYuan = it.amountYuan,
                phrase = it.phrase,
                done = false,
            )
        }
        store.history = (store.history + added).sortedBy { it.date }
        store.plan = store.plan.filter { !it.date.isBefore(today) }
    }

    /** 补足日历存量，返回新增条数。 */
    fun topUp(today: LocalDate, random: Random = Random.Default): Int {
        val calendarId = store.calendarId
        if (calendarId < 0) return 0

        // 防线之二：话术库管理页会在 UI 层拦住"降到底线以下"的操作，
        // 但这里再守一道 —— 万一那道拦截被绕过（未来的代码改动、
        // 或直接改动本地存储），也不能让 PhrasePicker.pick() 的
        // require() 在 onResume 无条件触发的路径上把整个应用崩掉。
        // 在这里提前退出，不写日历，避免日历事件和本地排期错位。
        if (store.enabledPhrases.size <= PhrasePicker.NO_REPEAT_WINDOW) return 0

        val settings = store.settings
        val newDates = ReminderCoordinator(gateway).topUp(calendarId, today, settings, random)
        if (newDates.isEmpty()) return 0

        val created = ReminderFactory.create(
            dates = newDates,
            settings = settings,
            phrases = store.enabledPhrases,
            recentPhrases = store.recentPhrases,
            random = random,
        )
        store.plan = (store.plan + created).sortedBy { it.date }
        store.recentPhrases = store.recentPhrases + created.map { it.phrase }
        return created.size
    }

    /** 按当前设置重排未来：先清掉本应用写入的未来事件，再重新补足。 */
    fun rebuildFuture(today: LocalDate, random: Random = Random.Default): Int {
        val calendarId = store.calendarId
        if (calendarId < 0) return 0

        gateway.deleteRemindersAfter(calendarId, today)
        store.plan = emptyList()
        return topUp(today, random)
    }

    /** 清空本应用创建的未来事件，返回删除条数。 */
    fun clearFuture(today: LocalDate): Int {
        val calendarId = store.calendarId
        if (calendarId < 0) return 0
        val removed = gateway.deleteRemindersAfter(calendarId, today)
        store.plan = emptyList()
        return removed
    }

    fun reminderOn(date: LocalDate): Reminder? = store.plan.firstOrNull { it.date == date }

    fun nextReminder(today: LocalDate): Reminder? =
        store.plan.filter { !it.date.isBefore(today) }.minByOrNull { it.date }

    fun futureCount(today: LocalDate): Int = store.plan.count { !it.date.isBefore(today) }

    /** 标记某天已完成，并写入历史。 */
    fun markDone(date: LocalDate) {
        val reminder = reminderOn(date) ?: return
        val entry = HistoryEntry(
            date = date,
            amountYuan = reminder.amountYuan,
            phrase = reminder.phrase,
            done = true,
        )
        store.history = (store.history.filter { it.date != date } + entry).sortedBy { it.date }
        store.plan = store.plan.filter { it.date != date }
    }

    fun isDone(date: LocalDate): Boolean = store.history.any { it.date == date && it.done }
}
