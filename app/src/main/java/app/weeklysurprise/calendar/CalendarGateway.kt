package app.weeklysurprise.calendar

import java.time.LocalDate

/**
 * 日历读写的抽象。
 *
 * 存在的意义是把"安排提醒"这件事的**决策逻辑**和**安卓框架调用**切开：
 * 决策逻辑可以在电脑上秒测，框架调用只能在真机上验。
 * 不切开的话，整条流程都得插上手机才能验证一次，改一行等三分钟。
 */
interface CalendarGateway {

    /** 设备上可用的本地日历账户。返回空表示没有可安全写入的日历。 */
    fun localCalendars(): List<LocalCalendar>

    /**
     * 找到本应用专属的日历；没有就新建一个。返回其 id，失败返回 null。
     *
     * 为什么不复用设备上已有的本地日历：那些是别的应用建的，
     * 把事件塞进去会显示在它的名下和颜色下，而且那个应用被卸载时可能连带删掉我们的事件。
     */
    fun ensureOwnCalendar(): Long?

    /** 查询本应用已写入的、指定日期之后的全部事件日期。 */
    fun existingReminderDates(calendarId: Long, from: LocalDate): Set<LocalDate>

    /** 写入一条提醒事件。标题中性，不含金额与话术。 */
    fun insertReminder(calendarId: Long, date: LocalDate, hourOfDay: Int, title: String, description: String)

    /** 删除本应用创建的、指定日期之后的全部事件；不得触碰用户自己的日程。返回删除条数。 */
    fun deleteRemindersAfter(calendarId: Long, from: LocalDate): Int
}

/**
 * 一个可写的本地日历账户。
 *
 * [isLocalOnly] 为 false 意味着这个日历会同步到云端或其他设备 ——
 * 那样家人可能在自己设备上看到这些条目，惊喜就没了。
 * 本应用只写 isLocalOnly = true 的日历。
 */
data class LocalCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isLocalOnly: Boolean,
)
