package app.weeklysurprise.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/**
 * [CalendarGateway] 的真实实现，通过系统日历的 ContentProvider 读写。
 *
 * 只做增删查，不含任何决策逻辑 —— 决策都在 ReminderCoordinator 里，那部分能在电脑上测。
 * 这里的代码只能在真机上验证，所以刻意写得尽量薄。
 *
 * 识别"本应用创建的事件"用两个条件同时成立：
 * 标题等于约定值，且 CUSTOM_APP_PACKAGE 等于本应用包名。
 * 删除时也用同一组条件，因此绝不会碰到用户自己的日程。
 */
class AndroidCalendarGateway(
    private val context: Context,
    private val eventTitle: String,
) : CalendarGateway {

    private val resolver get() = context.contentResolver
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun localCalendars(): List<LocalCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )

        val result = mutableListOf<LocalCalendar>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    // 权限不足以写入的日历直接跳过
                    if (cursor.getInt(4) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue

                    result += LocalCalendar(
                        id = cursor.getLong(0),
                        displayName = cursor.getString(1).orEmpty(),
                        accountName = cursor.getString(2).orEmpty(),
                        // ACCOUNT_TYPE_LOCAL 表示纯本地、不参与任何账户同步
                        isLocalOnly = cursor.getString(3) == CalendarContract.ACCOUNT_TYPE_LOCAL,
                    )
                }
            }
        return result
    }

    override fun ensureOwnCalendar(): Long? {
        findOwnCalendarId()?.let { return it }

        // 新建纯本地日历。必须以"同步适配器"身份插入，否则 ContentProvider 会拒绝。
        // 注意 ACCOUNT_TYPE_LOCAL 表示不挂任何账户，因此不会同步到小米云或任何其他设备。
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, OWN_ACCOUNT_NAME)
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, OWN_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, OWN_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, OWN_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, OWN_COLOR)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, OWN_ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, zone.id)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }

        return runCatching { resolver.insert(uri, values) }
            .getOrNull()
            ?.let { ContentUris.parseId(it) }
            ?: findOwnCalendarId()
    }

    private fun findOwnCalendarId(): Long? {
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(OWN_ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    override fun existingReminderDates(calendarId: Long, from: LocalDate): Set<LocalDate> {
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(CalendarContract.Events.DTSTART)

        val dates = mutableSetOf<LocalDate>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.TITLE} = ? AND " +
                "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND " +
                "${CalendarContract.Events.DTSTART} >= ? AND " +
                "${CalendarContract.Events.DELETED} = 0",
            arrayOf(calendarId.toString(), eventTitle, context.packageName, fromMillis.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val millis = cursor.getLong(0)
                dates += java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            }
        }
        return dates
    }

    override fun insertReminder(
        calendarId: Long,
        at: java.time.LocalDateTime,
        title: String,
        description: String,
    ) {
        val start = at.atZone(zone).toInstant().toEpochMilli()
        val end = start + EVENT_DURATION_MILLIS

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            // 这一列是我们识别"自己创建的事件"的标记，删除时也靠它
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        }

        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return
        val eventId = ContentUris.parseId(uri)

        // 到点提醒。没有这一条，事件只会静静躺在日历里不响。
        resolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            },
        )
    }

    override fun deleteRemindersAfter(calendarId: Long, from: LocalDate): Int {
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        return resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.TITLE} = ? AND " +
                "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? AND " +
                "${CalendarContract.Events.DTSTART} >= ?",
            arrayOf(calendarId.toString(), eventTitle, context.packageName, fromMillis.toString()),
        )
    }

    private companion object {
        const val EVENT_DURATION_MILLIS = 30 * 60 * 1000L

        /** 本应用专属日历的标识。ACCOUNT_TYPE_LOCAL 保证它不挂任何账户、不参与同步。 */
        const val OWN_ACCOUNT_NAME = "weekly_surprise_local"
        const val OWN_CALENDAR_NAME = "weekly_surprise"
        const val OWN_DISPLAY_NAME = "每周小惊喜"
        const val OWN_COLOR = 0xFFE86A6A.toInt()
    }
}
