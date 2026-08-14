package app.weeklysurprise.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AmountPickerTest {

    private val settings = Settings(minAmount = 50, maxAmount = 200)

    @Test
    fun 金额始终落在设定区间内() {
        val random = Random(42)
        repeat(1000) {
            val amount = AmountPicker.pick(settings, random)
            assertTrue("金额越界: $amount", amount in settings.minAmount..settings.maxAmount)
        }
    }

    @Test
    fun 金额不会每次都一样() {
        val random = Random(42)
        val distinct = (1..100).map { AmountPicker.pick(settings, random) }.toSet()
        assertTrue("100 次只出现 ${distinct.size} 个不同值，随机性可疑", distinct.size > 20)
    }

    @Test
    fun 吉利数模式下尾数只出现六或八() {
        val lucky = settings.copy(preferLuckyDigits = true)
        val random = Random(42)
        repeat(500) {
            val amount = AmountPicker.pick(lucky, random)
            val lastDigit = amount % 10
            assertTrue("吉利数模式下出现了尾数 $lastDigit（金额 $amount）", lastDigit == 6 || lastDigit == 8)
            assertTrue("吉利数模式下不应出现含 4 的金额: $amount", !amount.toString().contains('4'))
            assertTrue("金额越界: $amount", amount in lucky.minAmount..lucky.maxAmount)
        }
    }

    @Test
    fun 区间极窄时也不会死循环或越界() {
        val narrow = Settings(minAmount = 66, maxAmount = 66)
        val random = Random(1)
        repeat(50) {
            assertTrue(AmountPicker.pick(narrow, random) == 66)
        }
    }
}
