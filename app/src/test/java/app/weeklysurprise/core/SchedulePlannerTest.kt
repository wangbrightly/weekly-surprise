package app.weeklysurprise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * 抽日算法是本项目最容易悄悄出错的地方：
 * 错了不会崩溃，只会"感觉怪怪的"（比如老在周末、或者隔一天连发两次）。
 * 所以这里用大样本断言把规则钉死。
 */
class SchedulePlannerTest {

    private val monday = LocalDate.of(2026, 8, 17) // 星期一

    @Test
    fun 每周恰好一次() {
        val plan = SchedulePlanner.plan(monday, weeks = 100, lastReminderDate = null, random = Random(1))
        assertEquals("应当规划出 100 次提醒", 100, plan.size)

        plan.forEachIndexed { i, date ->
            val weekStart = monday.plusWeeks(i.toLong())
            val weekEnd = weekStart.plusDays(6)
            assertTrue(
                "第 ${i + 1} 次 $date 落在了 $weekStart..$weekEnd 之外",
                !date.isBefore(weekStart) && !date.isAfter(weekEnd),
            )
        }
    }

    @Test
    fun 任意两次间隔不少于四天() {
        repeat(50) { seed ->
            val plan = SchedulePlanner.plan(monday, 100, null, Random(seed))
            plan.zipWithNext { a, b ->
                val gap = ChronoUnit.DAYS.between(a, b)
                assertTrue("seed=$seed: $a 到 $b 只隔了 $gap 天", gap >= SchedulePlanner.MIN_GAP_DAYS)
            }
        }
    }

    @Test
    fun 承接上一批的最后一次也要满足最小间隔() {
        // 上一批最后一次落在紧邻的上周日 —— 这是最紧的边界情况
        val lastSunday = monday.minusDays(1)
        repeat(50) { seed ->
            val plan = SchedulePlanner.plan(monday, 12, lastSunday, Random(seed))
            val gap = ChronoUnit.DAYS.between(lastSunday, plan.first())
            assertTrue("seed=$seed: 承接间隔只有 $gap 天", gap >= SchedulePlanner.MIN_GAP_DAYS)
        }
    }

    @Test
    fun 跨年跨月不出错() {
        val decemberMonday = LocalDate.of(2026, 12, 21)
        val plan = SchedulePlanner.plan(decemberMonday, 20, null, Random(7))
        assertEquals(20, plan.size)
        assertTrue("应当跨到下一年", plan.any { it.year == 2027 })
        plan.zipWithNext { a, b ->
            assertTrue("$a -> $b 顺序颠倒", b.isAfter(a))
        }
    }

    @Test
    fun 哪天触发是随机的而不是固定星期() {
        val weekdays = mutableSetOf<DayOfWeek>()
        repeat(30) { seed ->
            SchedulePlanner.plan(monday, 12, null, Random(seed)).forEach { weekdays += it.dayOfWeek }
        }
        assertTrue("只出现了 ${weekdays.size} 种星期，随机性可疑", weekdays.size >= 6)
    }

    @Test
    fun 输入必须是周一() {
        val tuesday = monday.plusDays(1)
        try {
            SchedulePlanner.plan(tuesday, 4, null, Random(1))
            throw AssertionError("传入非周一应当抛异常")
        } catch (e: IllegalArgumentException) {
            // 期望的行为
        }
    }
}
