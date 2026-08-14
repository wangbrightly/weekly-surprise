package app.weeklysurprise.core

import java.time.LocalDate
import kotlin.random.Random

/**
 * 给已排好的日期配上金额和话术。
 *
 * 为什么提前决定而不是到那天现算：提前决定后，"下一次是哪天"和"那天给什么"
 * 是一次性定下的，用户中途换手机、清缓存也不会变来变去；而且话术的不重复
 * 约束需要按顺序推进，事后补算容易算错。
 *
 * 生成的内容只存在本机，**绝不写进日历事件**，所以日历里看不到剧透。
 */
object ReminderFactory {

    /**
     * @param phrases 当前可用的话术原文（调用方负责剔除已停用的）
     * @param recentPhrases 之前已用过的话术原文（按时间先后），用于跨批次维持不重复
     */
    fun create(
        dates: List<LocalDate>,
        settings: Settings,
        phrases: List<String>,
        recentPhrases: List<String>,
        random: Random,
    ): List<Reminder> {
        val history = recentPhrases.toMutableList()
        return dates.map { date ->
            val phrase = PhrasePicker.pick(phrases, history, random)
            history += phrase
            Reminder(
                date = date,
                amountYuan = AmountPicker.pick(settings, random),
                phrase = phrase,
            )
        }
    }
}
