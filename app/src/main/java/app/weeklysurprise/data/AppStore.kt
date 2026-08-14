package app.weeklysurprise.data

import android.content.Context
import app.weeklysurprise.core.HistoryEntry
import app.weeklysurprise.core.Phrases
import app.weeklysurprise.core.Reminder
import app.weeklysurprise.core.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 本机数据存储。
 *
 * 偏离原设计的说明：原计划用 DataStore，这里改用 SharedPreferences + JSON。
 * 理由是数据量极小（约 28 条话术 + 12 条排期 + 若干历史），
 * DataStore 会额外引入 androidx.datastore 与协程依赖，代码量反而更多。
 * 若将来数据量增长到需要异步读写或事务，再换 DataStore 不迟。
 *
 * 这里存的金额和话术**只在本机**，绝不写入日历，也不进 git 仓库。
 */
class AppStore(context: Context) {

    private val prefs = context.getSharedPreferences("weekly_surprise", Context.MODE_PRIVATE)

    // ---------- 设置 ----------

    var settings: Settings
        get() = Settings(
            minAmount = prefs.getInt(KEY_MIN, 50),
            maxAmount = prefs.getInt(KEY_MAX, 200),
            preferLuckyDigits = prefs.getBoolean(KEY_LUCKY, false),
            hourOfDay = prefs.getInt(KEY_HOUR, 20),
        )
        set(value) = prefs.edit()
            .putInt(KEY_MIN, value.minAmount)
            .putInt(KEY_MAX, value.maxAmount)
            .putBoolean(KEY_LUCKY, value.preferLuckyDigits)
            .putInt(KEY_HOUR, value.hourOfDay)
            .apply()

    /** 选定的日历账户；-1 表示尚未选择。 */
    var calendarId: Long
        get() = prefs.getLong(KEY_CALENDAR, -1L)
        set(value) = prefs.edit().putLong(KEY_CALENDAR, value).apply()

    // ---------- 话术库 ----------

    var phrases: List<String>
        get() {
            val raw = prefs.getString(KEY_PHRASES, null) ?: return Phrases.BUILT_IN
            return JSONArray(raw).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
        }
        set(value) = prefs.edit()
            .putString(KEY_PHRASES, JSONArray(value).toString())
            .apply()

    // ---------- 排期 ----------

    /** 已排定但尚未到期的提醒（含金额与话术，仅存本机）。 */
    var plan: List<Reminder>
        get() {
            val raw = prefs.getString(KEY_PLAN, null) ?: return emptyList()
            val array = JSONArray(raw)
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Reminder(
                    date = LocalDate.parse(o.getString("date")),
                    amountYuan = o.getInt("amount"),
                    phraseIndex = o.getInt("phrase"),
                )
            }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { r ->
                array.put(
                    JSONObject()
                        .put("date", r.date.toString())
                        .put("amount", r.amountYuan)
                        .put("phrase", r.phraseIndex),
                )
            }
            prefs.edit().putString(KEY_PLAN, array.toString()).apply()
        }

    /** 已用过的话术下标，用于跨批次维持不重复。只保留最近若干条。 */
    var recentPhrases: List<Int>
        get() {
            val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
            val array = JSONArray(raw)
            return (0 until array.length()).map { array.getInt(it) }
        }
        set(value) = prefs.edit()
            .putString(KEY_RECENT, JSONArray(value.takeLast(RECENT_KEEP)).toString())
            .apply()

    // ---------- 历史 ----------

    var history: List<HistoryEntry>
        get() {
            val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val array = JSONArray(raw)
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    date = LocalDate.parse(o.getString("date")),
                    amountYuan = o.getInt("amount"),
                    // 存快照而非下标：话术库改动后，历史记录仍显示当时那句话
                    phrase = o.getString("phrase"),
                    done = o.getBoolean("done"),
                )
            }
        }
        set(value) {
            val array = JSONArray()
            value.takeLast(HISTORY_KEEP).forEach { h ->
                array.put(
                    JSONObject()
                        .put("date", h.date.toString())
                        .put("amount", h.amountYuan)
                        .put("phrase", h.phrase)
                        .put("done", h.done),
                )
            }
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        }

    fun clearAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_MIN = "min_amount"
        const val KEY_MAX = "max_amount"
        const val KEY_LUCKY = "lucky_digits"
        const val KEY_HOUR = "hour_of_day"
        const val KEY_CALENDAR = "calendar_id"
        const val KEY_PHRASES = "phrases"
        const val KEY_PLAN = "plan"
        const val KEY_RECENT = "recent_phrases"
        const val KEY_HISTORY = "history"

        const val RECENT_KEEP = 20
        const val HISTORY_KEEP = 52
    }
}
