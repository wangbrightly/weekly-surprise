package app.weeklysurprise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

class ReminderCoordinatorTest {

    private val today = LocalDate.of(2026, 8, 19) // 星期三
    private val settings = Settings()
    private val calendarId = 1L

    private fun coordinator(gateway: FakeCalendarGateway) = ReminderCoordinator(gateway)

    @Test
    fun 首次补足写满十二周() {
        val gateway = FakeCalendarGateway()
        val written = coordinator(gateway).topUp(calendarId, today, settings, Random(1))

        assertEquals("应当写入 12 条", 12, written.size)
        assertEquals(12, gateway.written.size)
    }

    @Test
    fun 重复运行不产生重复事件() {
        val gateway = FakeCalendarGateway()
        val co = coordinator(gateway)

        co.topUp(calendarId, today, settings, Random(1))
        val countAfterFirst = gateway.written.size

        val second = co.topUp(calendarId, today, settings, Random(2))
        val third = co.topUp(calendarId, today, settings, Random(3))

        assertTrue("第二次不该有新增，实际 ${second.size} 条", second.isEmpty())
        assertTrue("第三次不该有新增，实际 ${third.size} 条", third.isEmpty())
        assertEquals("事件总数不该变化", countAfterFirst, gateway.written.size)
    }

    @Test
    fun 同一周不会排两次() {
        val gateway = FakeCalendarGateway()
        coordinator(gateway).topUp(calendarId, today, settings, Random(4))

        val weeks = gateway.written.map { SchedulePlanner.weekStartOf(it.date) }
        assertEquals("出现了同一周排两次的情况", weeks.size, weeks.toSet().size)
    }

    @Test
    fun 补足后的事件之间仍满足最小间隔() {
        val gateway = FakeCalendarGateway()
        coordinator(gateway).topUp(calendarId, today, settings, Random(6))

        gateway.written.map { it.date }.sorted().zipWithNext { a, b ->
            val gap = ChronoUnit.DAYS.between(a, b)
            assertTrue("$a 到 $b 只隔 $gap 天", gap >= SchedulePlanner.MIN_GAP_DAYS)
        }
    }

    @Test
    fun 用掉一周后再打开会补回一周() {
        val gateway = FakeCalendarGateway()
        val co = coordinator(gateway)
        co.topUp(calendarId, today, settings, Random(7))

        // 模拟时间过去两周：最早的两条已成为过去
        val later = today.plusWeeks(2)
        val topUpAgain = co.topUp(calendarId, later, settings, Random(8))

        assertTrue("时间推进后应当补足新的周，实际补了 ${topUpAgain.size} 条", topUpAgain.isNotEmpty())

        val future = gateway.written.map { it.date }.filter { !it.isBefore(later) }
        assertEquals("未来存量应当仍是 12 周", 12, future.size)
    }

    @Test
    fun 写入的事件不泄露金额与话术() {
        val gateway = FakeCalendarGateway()
        coordinator(gateway).topUp(calendarId, today, settings, Random(9))

        gateway.written.forEach { event ->
            val text = event.title + event.description
            assertTrue("事件文本里出现了数字，可能泄露金额: $text", !text.any { it.isDigit() })
            assertTrue("事件标题不该为空", event.title.isNotBlank())
        }
    }

    @Test
    fun 事件时间用设置里的小时() {
        val gateway = FakeCalendarGateway()
        val custom = Settings(hourOfDay = 21)
        coordinator(gateway).topUp(calendarId, today, custom, Random(10))

        assertTrue("事件小时应当全部是 21", gateway.written.all { it.hourOfDay == 21 })
    }
}
