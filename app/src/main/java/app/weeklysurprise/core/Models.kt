package app.weeklysurprise.core

import java.time.LocalDate

/**
 * 一次提醒的完整内容。
 *
 * 注意：金额和话术在写入日历时**不会**出现在事件标题或描述里，
 * 只在用户打开 App 时才揭晓，以免在日历列表里被提前看光。
 */
data class Reminder(
    val date: LocalDate,
    val amountYuan: Int,
    val phraseIndex: Int,
)

/** 用户可调的设置。 */
data class Settings(
    val minAmount: Int = 50,
    val maxAmount: Int = 200,
    /** 是否偏好尾数 6 / 8 并避开 4。默认关闭。 */
    val preferLuckyDigits: Boolean = false,
    /** 提醒时间（小时，24 小时制）。 */
    val hourOfDay: Int = 20,
) {
    init {
        require(minAmount in 1..maxAmount) { "金额区间非法: $minAmount..$maxAmount" }
        require(hourOfDay in 0..23) { "小时非法: $hourOfDay" }
    }
}

/** 一次已发生的提醒的执行记录。 */
data class HistoryEntry(
    val date: LocalDate,
    val amountYuan: Int,
    val phrase: String,
    val done: Boolean,
)
