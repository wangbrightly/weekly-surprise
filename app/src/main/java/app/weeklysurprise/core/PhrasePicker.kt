package app.weeklysurprise.core

import kotlin.random.Random

/**
 * 话术抽取：保证连续 [NO_REPEAT_WINDOW] 次内不重复。
 *
 * 直接对内容本身操作而不是对下标操作 —— 下标会因为增删条目而错位，
 * 内容不会。用户随时可能编辑话术库，所以这一点不是洁癖而是必需。
 *
 * 硬性要求可用条目必须多于窗口：如果正好等于窗口大小，
 * "不重复"就退化成固定顺序轮播，随机性归零。这种退化不会报错，
 * 只会让用户几个月后察觉"怎么老是这几句"，所以在入口处直接拒绝。
 */
object PhrasePicker {

    const val NO_REPEAT_WINDOW = 8

    /** 库容量的安全下限：窗口的 3 倍，低于这个数随机感会明显变差。 */
    const val SAFE_MINIMUM = NO_REPEAT_WINDOW * 3

    /**
     * @param library 当前可用的条目（已排除停用的）
     * @param recent 最近用过的条目，按时间先后
     */
    fun <T> pick(library: List<T>, recent: List<T>, random: Random): T {
        require(library.size > NO_REPEAT_WINDOW) {
            "可用话术至少要 ${NO_REPEAT_WINDOW + 1} 条，当前 ${library.size} 条：" +
                "数量不大于不重复窗口时，随机会退化成固定轮播"
        }

        val forbidden = recent.takeLast(NO_REPEAT_WINDOW - 1).toSet()
        val candidates = library.filter { it !in forbidden }
        return candidates[random.nextInt(candidates.size)]
    }
}
