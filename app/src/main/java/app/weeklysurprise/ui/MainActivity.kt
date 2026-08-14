package app.weeklysurprise.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.weeklysurprise.R
import app.weeklysurprise.calendar.AndroidCalendarGateway
import app.weeklysurprise.core.ReminderCoordinator
import app.weeklysurprise.data.AppStore
import app.weeklysurprise.data.PlanService
import app.weeklysurprise.databinding.ActivityMainBinding
import app.weeklysurprise.databinding.ItemHistoryBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: AppStore
    private lateinit var gateway: AndroidCalendarGateway
    private lateinit var plans: PlanService

    private var revealed = false

    private val dateFormat = DateTimeFormatter.ofPattern("M 月 d 日 EEEE")
    private val historyFormat = DateTimeFormatter.ofPattern("M 月 d 日")

    private val requestCalendar = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh() }

    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不阻塞主流程：日历自己会响 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        store = AppStore(this)
        gateway = AndroidCalendarGateway(this, ReminderCoordinator.EVENT_TITLE)
        plans = PlanService(store, gateway)

        binding.grantButton.setOnClickListener { askCalendarPermission() }
        binding.setupButton.setOnClickListener { setUpCalendarAndPlan() }
        binding.revealButton.setOnClickListener { reveal() }
        binding.copyButton.setOnClickListener { copyPhrase() }
        binding.wechatButton.setOnClickListener { openWeChat() }
        binding.doneButton.setOnClickListener { markDone() }
        binding.testButton.setOnClickListener { writeTestReminder() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        askNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        revealed = false
        refresh()
    }

    // ---------- 权限 ----------

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun askCalendarPermission() {
        requestCalendar.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }

    private fun askNotificationPermissionIfNeeded() {
        // Android 13 起通知需要运行时授权。这里申请的是本应用自身的提示通知；
        // 到点响铃的是系统日历，不受这一项影响。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------- 主流程 ----------

    private fun refresh() {
        val today = LocalDate.now()

        if (!hasCalendarPermission()) {
            showOnly(permission = true)
            return
        }

        // 用本应用自己的本地日历，不借别的应用建的日历。
        val ownCalendarId = gateway.ensureOwnCalendar()
        if (ownCalendarId == null) {
            showOnly(noCalendar = true)
            return
        }
        store.calendarId = ownCalendarId

        plans.archivePast(today)
        plans.topUp(today)

        showOnly(status = true)
        renderStatus(today)
        renderHistory()
    }

    private fun showOnly(
        permission: Boolean = false,
        noCalendar: Boolean = false,
        status: Boolean = false,
    ) {
        binding.permissionCard.visibility = if (permission) android.view.View.VISIBLE else android.view.View.GONE
        binding.noCalendarCard.visibility = if (noCalendar) android.view.View.VISIBLE else android.view.View.GONE
        binding.statusCard.visibility = if (status) android.view.View.VISIBLE else android.view.View.GONE
        if (!status) binding.revealCard.visibility = android.view.View.GONE
    }

    private fun renderStatus(today: LocalDate) {
        val todayReminder = plans.reminderOn(today)
        val isToday = todayReminder != null && !plans.isDone(today)

        binding.revealCard.visibility =
            if (isToday) android.view.View.VISIBLE else android.view.View.GONE
        binding.revealedContent.visibility =
            if (revealed) android.view.View.VISIBLE else android.view.View.GONE
        binding.revealButton.visibility =
            if (revealed) android.view.View.GONE else android.view.View.VISIBLE

        if (isToday && revealed) {
            binding.amountText.text = getString(R.string.amount_format, todayReminder.amountYuan)
            binding.phraseText.text = todayReminder.phrase
        }

        val next = plans.nextReminder(today)
        binding.nextDateText.text = next?.date?.format(dateFormat) ?: getString(R.string.no_plan_yet)
        binding.slotsText.text = getString(R.string.slots_remaining, plans.futureCount(today))
        binding.setupButton.visibility =
            if (next == null) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun renderHistory() {
        binding.historyContainer.removeAllViews()
        val recent = store.history.sortedByDescending { it.date }.take(8)

        binding.historyEmpty.visibility =
            if (recent.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        if (recent.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        recent.forEach { entry ->
            val row = ItemHistoryBinding.inflate(inflater, binding.historyContainer, false)
            row.rowDate.text = entry.date.format(historyFormat)
            row.rowAmount.text = getString(R.string.amount_format, entry.amountYuan)
            row.rowStatus.text = getString(if (entry.done) R.string.done_yes else R.string.done_no)
            binding.historyContainer.addView(row.root)
        }
    }

    // ---------- 动作 ----------

    private fun setUpCalendarAndPlan() {
        val today = LocalDate.now()
        val added = plans.topUp(today)
        Toast.makeText(this, getString(R.string.slots_remaining, plans.futureCount(today)), Toast.LENGTH_SHORT).show()
        if (added > 0) refresh()
    }

    private fun reveal() {
        revealed = true
        renderStatus(LocalDate.now())
    }

    private fun copyPhrase() {
        val reminder = plans.reminderOn(LocalDate.now()) ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("phrase", reminder.phrase))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun openWeChat() {
        val intent = packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
        if (intent == null) {
            Toast.makeText(this, R.string.wechat_not_found, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(intent)
    }

    private fun markDone() {
        plans.markDone(LocalDate.now())
        Toast.makeText(this, R.string.marked_done, Toast.LENGTH_SHORT).show()
        revealed = false
        refresh()
    }

    private fun writeTestReminder() {
        val calendarId = store.calendarId
        if (calendarId < 0) return

        // 走与真实提醒完全相同的写入路径，只是时间设成 2 分钟后。
        // 不用"直接弹通知"的捷径 —— 那样验证不到日历这条链路。
        val at = java.time.LocalDateTime.now().plusMinutes(2)

        gateway.insertReminder(
            calendarId = calendarId,
            at = at,
            title = ReminderCoordinator.EVENT_TITLE,
            description = ReminderCoordinator.EVENT_DESCRIPTION,
        )

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.test_reminder_at, at.hour, at.minute))
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
