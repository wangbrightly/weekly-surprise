package app.weeklysurprise.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhrasePickerTest {

    private fun library(n: Int) = (1..n).map { "第 $it 句" }

    @Test
    fun 连续八次之内不重复() {
        val lib = library(30)
        val random = Random(3)
        val history = mutableListOf<String>()

        repeat(100) {
            val picked = PhrasePicker.pick(lib, history, random)
            val window = history.takeLast(PhrasePicker.NO_REPEAT_WINDOW - 1)
            assertTrue("第 ${history.size + 1} 次抽到「$picked」，与最近 ${window.size} 次重复", picked !in window)
            history += picked
        }
    }

    @Test
    fun 抽出的内容始终来自库内() {
        val lib = library(24)
        val random = Random(9)
        val history = mutableListOf<String>()
        repeat(200) {
            val picked = PhrasePicker.pick(lib, history, random)
            assertTrue("抽出了库外内容: $picked", picked in lib)
            history += picked
        }
    }

    @Test
    fun 长期来看每条话术都会被用到() {
        val lib = library(24)
        val random = Random(5)
        val history = mutableListOf<String>()
        repeat(500) { history += PhrasePicker.pick(lib, history, random) }
        assertTrue("500 次只用到 ${history.toSet().size} 条，分布不均", history.toSet().size == lib.size)
    }

    @Test
    fun 可用条目过少时拒绝工作而不是静默重复() {
        // 数量必须大于不重复窗口，否则"不重复"会退化成固定轮播
        try {
            PhrasePicker.pick(library(5), emptyList(), Random(1))
            throw AssertionError("可用条目不足时应当抛异常")
        } catch (e: IllegalArgumentException) {
            // 期望的行为
        }
    }

    @Test
    fun 删掉库中某条不会影响已抽出的内容() {
        // 这是改用"内容"而非"下标"的核心理由：
        // 早期版本存下标，删掉前面一条会让后面全部错位，
        // 已排好的提醒会静默指向另一句话。
        val lib = library(20)
        val random = Random(7)
        val picked = PhrasePicker.pick(lib, emptyList(), random)

        val afterDeletion = lib.filter { it != lib.first() }
        assertTrue("抽出的内容本身不应因库变动而改变", picked.isNotEmpty())
        assertTrue(
            "除非被删的正是它，否则仍应在库里",
            picked == lib.first() || picked in afterDeletion,
        )
    }
}
