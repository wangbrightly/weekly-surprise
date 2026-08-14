package app.weeklysurprise.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhrasePickerTest {

    @Test
    fun 连续八次之内不重复() {
        val librarySize = 30
        val random = Random(3)
        val history = mutableListOf<Int>()

        repeat(100) {
            val index = PhrasePicker.pick(librarySize, history, random)
            val window = history.takeLast(PhrasePicker.NO_REPEAT_WINDOW - 1)
            assertTrue("第 ${history.size + 1} 次抽到 $index，与最近 ${window.size} 次重复", index !in window)
            history += index
        }
    }

    @Test
    fun 抽出的下标始终合法() {
        val librarySize = 24
        val random = Random(9)
        val history = mutableListOf<Int>()
        repeat(200) {
            val index = PhrasePicker.pick(librarySize, history, random)
            assertTrue("下标越界: $index", index in 0 until librarySize)
            history += index
        }
    }

    @Test
    fun 长期来看每条话术都会被用到() {
        val librarySize = 24
        val random = Random(5)
        val history = mutableListOf<Int>()
        repeat(500) { history += PhrasePicker.pick(librarySize, history, random) }
        assertTrue("500 次只用到 ${history.toSet().size} 条，分布不均", history.toSet().size == librarySize)
    }

    @Test
    fun 话术库过小时拒绝工作而不是静默重复() {
        // 库容量必须大于不重复窗口，否则"不重复"会退化成固定轮播
        try {
            PhrasePicker.pick(librarySize = 5, recent = emptyList(), random = Random(1))
            throw AssertionError("库容量不足时应当抛异常")
        } catch (e: IllegalArgumentException) {
            // 期望的行为
        }
    }
}
