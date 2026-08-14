package app.weeklysurprise.data

import android.content.Context
import app.weeklysurprise.core.HistoryEntry
import app.weeklysurprise.core.Phrase
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

    init {
        migrateIfNeeded()
    }

    /**
     * 把旧格式（话术存下标）就地转成新格式（话术存原文）。
     *
     * 必须在启动时立即完成并落盘，不能等到"读的时候现算"——
     * 因为一旦用户先编辑了话术库，库里条目的下标就全变了，
     * 此时再拿旧下标去查新库，会静默换算出**另一句话**。
     * 换算本身没错，错在时机：必须赶在任何编辑之前。
     */
    private fun migrateIfNeeded() {
        if (prefs.getInt(KEY_SCHEMA, 1) >= SCHEMA_VERSION) return

        // 读取会自动按旧格式换算，写回则以新格式落盘
        val migratedPlan = plan
        val migratedRecent = recentPhrases

        prefs.edit().putInt(KEY_SCHEMA, SCHEMA_VERSION).apply()
        plan = migratedPlan
        recentPhrases = migratedRecent
    }

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

    /**
     * 全部话术（含已停用的）。
     *
     * 兼容旧格式：早期版本存的是纯字符串数组，读到那种格式时全部视为启用。
     */
    var phrases: List<Phrase>
        get() {
            val raw = prefs.getString(KEY_PHRASES, null)
                ?: return Phrases.BUILT_IN.map { Phrase(it) }
            val array = JSONArray(raw)
            return (0 until array.length()).mapNotNull { i ->
                when (val item = array.get(i)) {
                    is String -> Phrase(item, enabled = true) // 旧格式
                    is JSONObject -> Phrase(
                        text = item.optString("text"),
                        enabled = item.optBoolean("enabled", true),
                    )
                    else -> null
                }
            }.filter { it.text.isNotBlank() }
        }
        set(value) {
            val array = JSONArray()
            value.filter { it.text.isNotBlank() }.distinctBy { it.text }.forEach {
                array.put(JSONObject().put("text", it.text).put("enabled", it.enabled))
            }
            prefs.edit().putString(KEY_PHRASES, array.toString()).apply()
        }

    /** 参与随机的话术原文。 */
    val enabledPhrases: List<String>
        get() = phrases.filter { it.enabled }.map { it.text }

    // ---------- 排期 ----------

    /** 已排定但尚未到期的提醒（含金额与话术，仅存本机）。 */
    var plan: List<Reminder>
        get() {
            val raw = prefs.getString(KEY_PLAN, null) ?: return emptyList()
            val all = phrases.map { it.text }
            val array = JSONArray(raw)
            // 越界下标就丢弃这一条，而不是拿"第一条话术"顶替 ——
            // 顶替会让排期显示一句从未被抽中过的话，看着像正确数据，其实是编出来的。
            // recentPhrases 迁移用的是同一套"丢弃"策略，两处保持一致。
            return (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val phrase = when (val p = o.get("phrase")) {
                    is Int -> all.getOrNull(p) // 旧格式：下标
                    else -> p.toString()
                } ?: return@mapNotNull null

                Reminder(
                    date = LocalDate.parse(o.getString("date")),
                    amountYuan = o.getInt("amount"),
                    phrase = phrase,
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
                        .put("phrase", r.phrase),
                )
            }
            prefs.edit().putString(KEY_PLAN, array.toString()).apply()
        }

    /** 已用过的话术原文，用于跨批次维持不重复。只保留最近若干条。 */
    var recentPhrases: List<String>
        get() {
            val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
            val all = phrases.map { it.text }
            val array = JSONArray(raw)
            return (0 until array.length()).mapNotNull { i ->
                when (val item = array.get(i)) {
                    is Int -> all.getOrNull(item) // 旧格式：下标
                    is String -> item
                    else -> null
                }
            }
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
                    // 存快照而非引用：话术库改动后，历史记录仍显示当时那句话
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
        const val KEY_SCHEMA = "schema_version"

        /** 2 = 话术以原文存储（1 = 以下标存储）。 */
        const val SCHEMA_VERSION = 2

        const val RECENT_KEEP = 20
        const val HISTORY_KEEP = 52
    }
}
