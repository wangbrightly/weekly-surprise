package app.weeklysurprise.core

import app.weeklysurprise.calendar.CalendarGateway
import app.weeklysurprise.calendar.LocalCalendar
import java.time.LocalDate

/** 内存里的假日历，用于在电脑上完整测试补足流程，不需要真机。 */
class FakeCalendarGateway : CalendarGateway {

    data class Written(val date: LocalDate, val hourOfDay: Int, val title: String, val description: String)

    val written = mutableListOf<Written>()

    override fun localCalendars() = listOf(
        LocalCalendar(id = 1L, displayName = "本地日历", accountName = "local", isLocalOnly = true),
    )

    override fun ensureOwnCalendar(): Long = 1L

    override fun existingReminderDates(calendarId: Long, from: LocalDate): Set<LocalDate> =
        written.map { it.date }.filter { !it.isBefore(from) }.toSet()

    override fun insertReminder(
        calendarId: Long,
        date: LocalDate,
        hourOfDay: Int,
        title: String,
        description: String,
    ) {
        written += Written(date, hourOfDay, title, description)
    }

    override fun deleteRemindersAfter(calendarId: Long, from: LocalDate): Int {
        val before = written.size
        written.removeAll { !it.date.isBefore(from) }
        return before - written.size
    }
}
