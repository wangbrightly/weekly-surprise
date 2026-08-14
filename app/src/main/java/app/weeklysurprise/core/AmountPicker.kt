package app.weeklysurprise.core

import kotlin.random.Random

/** 金额生成：区间内随机整数元。 */
object AmountPicker {

    fun pick(settings: Settings, random: Random): Int {
        if (!settings.preferLuckyDigits) {
            return random.nextInt(settings.minAmount, settings.maxAmount + 1)
        }

        val lucky = (settings.minAmount..settings.maxAmount).filter { isLucky(it) }

        // 区间里可能一个吉利数都没有（比如 41..44）。
        // 这时退回普通随机，而不是死循环重试。
        if (lucky.isEmpty()) {
            return random.nextInt(settings.minAmount, settings.maxAmount + 1)
        }
        return lucky[random.nextInt(lucky.size)]
    }

    /** 尾数是 6 或 8，且整个数字里不含 4。 */
    private fun isLucky(amount: Int): Boolean {
        val lastDigit = amount % 10
        if (lastDigit != 6 && lastDigit != 8) return false
        return !amount.toString().contains('4')
    }
}
