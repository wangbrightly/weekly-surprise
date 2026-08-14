package app.weeklysurprise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ReminderFactoryTest {

    private val settings = Settings(minAmount = 50, maxAmount = 200)
    private val phrases = Phrases.BUILT_IN

    private fun dates(n: Int) = (0 until n).map { LocalDate.of(2026, 9, 1).plusWeeks(it.toLong()) }

    @Test
    fun 每个日期都配到金额和话术() {
        val result = ReminderFactory.create(dates(12), settings, phrases, emptyList(), Random(1))
        assertEquals(12, result.size)
        result.forEach {
            assertTrue("金额越界: ${it.amountYuan}", it.amountYuan in 50..200)
            assertTrue("话术不在库里: ${it.phrase}", it.phrase in phrases)
        }
    }

    @Test
    fun 批次内话术不重复() {
        val result = ReminderFactory.create(dates(12), settings, phrases, emptyList(), Random(2))
        val used = result.map { it.phrase }
        used.forEachIndexed { i, phrase ->
            val window = used.subList(maxOf(0, i - (PhrasePicker.NO_REPEAT_WINDOW - 1)), i)
            assertTrue("第 ${i + 1} 条与前面重复了", phrase !in window)
        }
    }

    @Test
    fun 跨批次也不与之前用过的话术重复() {
        val previous = phrases.take(7)
        val result = ReminderFactory.create(dates(3), settings, phrases, previous, Random(3))

        assertTrue(
            "新批次第一条「${result.first().phrase}」撞上了历史",
            result.first().phrase !in previous,
        )
    }

    @Test
    fun 日期顺序被原样保留() {
        val input = dates(8)
        val result = ReminderFactory.create(input, settings, phrases, emptyList(), Random(4))
        assertEquals(input, result.map { it.date })
    }

    @Test
    fun 空输入返回空表() {
        assertTrue(ReminderFactory.create(emptyList(), settings, phrases, emptyList(), Random(5)).isEmpty())
    }

    @Test
    fun 只从传入的可用话术中抽取() {
        // 用户停用了大部分话术时，绝不能抽到被停用的那些
        val subset = phrases.take(12)
        val result = ReminderFactory.create(dates(10), settings, subset, emptyList(), Random(6))
        result.forEach {
            assertTrue("抽到了不在可用列表里的话术: ${it.phrase}", it.phrase in subset)
        }
    }
}
