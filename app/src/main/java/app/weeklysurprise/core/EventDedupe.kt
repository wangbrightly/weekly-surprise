package app.weeklysurprise.core

import java.time.LocalDate

/**
 * 补足日历存量时的去重。
 *
 * 每次打开 App 都会尝试把未来补满，如果不去重，同一天会堆叠出多条事件。
 * 这类 bug 不会崩溃，用户通常要到日历里翻到那天才发现。
 */
object EventDedupe {

    fun newDates(planned: List<LocalDate>, existing: Set<LocalDate>): List<LocalDate> =
        planned.filter { it !in existing }
}
