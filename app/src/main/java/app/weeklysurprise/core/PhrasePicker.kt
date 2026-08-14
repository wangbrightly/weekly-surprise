package app.weeklysurprise.core

import kotlin.random.Random

/**
 * 话术抽取：保证连续 [NO_REPEAT_WINDOW] 次内不重复。
 *
 * 硬性要求库容量必须大于窗口 —— 如果库正好等于窗口大小，
 * "不重复"就退化成固定顺序轮播，随机性归零。这种退化不会报错，
 * 只会让用户几个月后察觉"怎么老是这几句"，所以在入口处直接拒绝。
 */
object PhrasePicker {

    const val NO_REPEAT_WINDOW = 8

    fun pick(librarySize: Int, recent: List<Int>, random: Random): Int {
        require(librarySize > NO_REPEAT_WINDOW) {
            "话术库至少要 ${NO_REPEAT_WINDOW + 1} 条，当前 $librarySize 条：" +
                "库容量不大于不重复窗口时，随机会退化成固定轮播"
        }

        val forbidden = recent.takeLast(NO_REPEAT_WINDOW - 1).toSet()
        val candidates = (0 until librarySize).filter { it !in forbidden }
        return candidates[random.nextInt(candidates.size)]
    }
}
