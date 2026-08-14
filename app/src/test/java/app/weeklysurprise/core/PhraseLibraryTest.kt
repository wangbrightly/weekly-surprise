package app.weeklysurprise.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 守住内置话术库的底线。
 *
 * 这个测试存在的理由：将来有人（包括我自己）可能觉得话术太多想删几条，
 * 一旦删到容量接近不重复窗口，随机性会静默退化成固定轮播 —— 不报错，
 * 只是几个月后老婆开始觉得"这话你说过"。
 */
class PhraseLibraryTest {

    @Test
    fun 内置库容量足够支撑不重复窗口() {
        val size = Phrases.BUILT_IN.size
        assertTrue(
            "内置话术只有 $size 条，少于安全下限 ${PhrasePicker.SAFE_MINIMUM} 条（不重复窗口的 3 倍）",
            size >= PhrasePicker.SAFE_MINIMUM,
        )
    }

    @Test
    fun 没有重复的话术() {
        val duplicates = Phrases.BUILT_IN.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("库里有重复条目: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun 没有空白条目() {
        Phrases.BUILT_IN.forEachIndexed { i, phrase ->
            assertTrue("第 $i 条是空的", phrase.isNotBlank())
        }
    }

    @Test
    fun 用真实库跑一遍不重复约束() {
        val random = Random(11)
        val history = mutableListOf<String>()
        repeat(200) {
            val picked = PhrasePicker.pick(Phrases.BUILT_IN, history, random)
            val window = history.takeLast(PhrasePicker.NO_REPEAT_WINDOW - 1)
            assertTrue("第 ${history.size + 1} 次抽到重复", picked !in window)
            history += picked
        }
    }
}
