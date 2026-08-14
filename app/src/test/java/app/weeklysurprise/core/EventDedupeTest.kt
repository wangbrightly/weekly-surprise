package app.weeklysurprise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 补足日历存量时，重复运行不能产生重复事件。
 * 这个 bug 的表现是日历里同一天堆了好几条，用户很晚才会发现。
 */
class EventDedupeTest {

    private val d = { day: Int -> LocalDate.of(2026, 9, day) }

    @Test
    fun 已存在的日期不会被再次写入() {
        val planned = listOf(d(1), d(6), d(12), d(18))
        val existing = setOf(d(1), d(12))
        assertEquals(listOf(d(6), d(18)), EventDedupe.newDates(planned, existing))
    }

    @Test
    fun 重复运行第二次不产生任何新增() {
        val planned = listOf(d(1), d(6), d(12))
        val first = EventDedupe.newDates(planned, emptySet())
        assertEquals(planned, first)

        val second = EventDedupe.newDates(planned, first.toSet())
        assertTrue("第二次运行不应有新增，实际有 ${second.size} 条", second.isEmpty())
    }

    @Test
    fun 计划为空时返回空而不是报错() {
        assertTrue(EventDedupe.newDates(emptyList(), setOf(d(1))).isEmpty())
    }

    @Test
    fun 保持原有顺序() {
        val planned = listOf(d(18), d(1), d(12))
        assertEquals(listOf(d(18), d(1)), EventDedupe.newDates(planned, setOf(d(12))))
    }
}
