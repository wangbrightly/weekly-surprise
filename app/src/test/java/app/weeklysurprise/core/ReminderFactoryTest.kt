package app.weeklysurprise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ReminderFactoryTest {

    private val settings = Settings(minAmount = 50, maxAmount = 200)
    private val librarySize = Phrases.BUILT_IN.size

    private fun dates(n: Int) = (0 until n).map { LocalDate.of(2026, 9, 1).plusWeeks(it.toLong()) }

    @Test
    fun 每个日期都配到金额和话术() {
        val result = ReminderFactory.create(dates(12), settings, librarySize, emptyList(), Random(1))
        assertEquals(12, result.size)
        result.forEach {
            assertTrue("金额越界: ${it.amountYuan}", it.amountYuan in 50..200)
            assertTrue("话术下标越界: ${it.phraseIndex}", it.phraseIndex in 0 until librarySize)
        }
    }

    @Test
    fun 批次内话术不重复() {
        val result = ReminderFactory.create(dates(12), settings, librarySize, emptyList(), Random(2))
        val indices = result.map { it.phraseIndex }
        indices.forEachIndexed { i, index ->
            val window = indices.subList(maxOf(0, i - (PhrasePicker.NO_REPEAT_WINDOW - 1)), i)
            assertTrue("第 ${i + 1} 条与前面重复了", index !in window)
        }
    }

    @Test
    fun 跨批次也不与之前用过的话术重复() {
        val previous = listOf(0, 1, 2, 3, 4, 5, 6)
        val result = ReminderFactory.create(dates(3), settings, librarySize, previous, Random(3))

        // 新批次的第一条不能撞上前一批最后 7 条
        val forbidden = previous.takeLast(PhrasePicker.NO_REPEAT_WINDOW - 1)
        assertTrue(
            "新批次第一条 ${result.first().phraseIndex} 撞上了历史 $forbidden",
            result.first().phraseIndex !in forbidden,
        )
    }

    @Test
    fun 日期顺序被原样保留() {
        val input = dates(8)
        val result = ReminderFactory.create(input, settings, librarySize, emptyList(), Random(4))
        assertEquals(input, result.map { it.date })
    }

    @Test
    fun 空输入返回空表() {
        assertTrue(ReminderFactory.create(emptyList(), settings, librarySize, emptyList(), Random(5)).isEmpty())
    }
}
