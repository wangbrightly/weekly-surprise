package app.weeklysurprise.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.weeklysurprise.R
import app.weeklysurprise.calendar.AndroidCalendarGateway
import app.weeklysurprise.core.PhrasePicker
import app.weeklysurprise.core.ReminderCoordinator
import app.weeklysurprise.core.Settings
import app.weeklysurprise.data.AppStore
import app.weeklysurprise.data.PlanService
import app.weeklysurprise.databinding.ActivitySettingsBinding
import java.time.LocalDate

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: AppStore
    private lateinit var plans: PlanService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        title = getString(R.string.settings)

        store = AppStore(this)
        plans = PlanService(store, AndroidCalendarGateway(this, ReminderCoordinator.EVENT_TITLE))

        load()

        binding.saveButton.setOnClickListener { save() }
        binding.rebuildButton.setOnClickListener { rebuild() }
        binding.clearButton.setOnClickListener { confirmClear() }
        binding.phrasesButton.setOnClickListener {
            startActivity(android.content.Intent(this, PhrasesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 从话术库页返回时刷新统计
        renderPhraseSummary()
    }

    private fun load() {
        val s = store.settings
        binding.minAmountInput.setText(s.minAmount.toString())
        binding.maxAmountInput.setText(s.maxAmount.toString())
        binding.hourInput.setText(s.hourOfDay.toString())
        binding.luckyCheck.isChecked = s.preferLuckyDigits
        renderPhraseSummary()
    }

    private fun renderPhraseSummary() {
        val all = store.phrases
        binding.phrasesSummary.text =
            getString(R.string.phrases_entry_hint, all.size, all.count { it.enabled })
    }

    private fun save() {
        val min = binding.minAmountInput.text.toString().toIntOrNull()
        val max = binding.maxAmountInput.text.toString().toIntOrNull()
        val hour = binding.hourInput.text.toString().toIntOrNull()

        val error = validate(min, max, hour)
        if (error != null) {
            Toast.makeText(this, getString(R.string.save_failed, error), Toast.LENGTH_LONG).show()
            return
        }

        store.settings = Settings(
            minAmount = min!!,
            maxAmount = max!!,
            preferLuckyDigits = binding.luckyCheck.isChecked,
            hourOfDay = hour!!,
        )
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    /** 在入口处挡住非法输入，而不是让它到深处才炸。 */
    private fun validate(min: Int?, max: Int?, hour: Int?): String? = when {
        min == null || max == null || hour == null -> "有输入不是数字"
        min < 1 -> "最低金额要大于 0"
        min > max -> "最低金额不能大于最高金额"
        hour !in 0..23 -> "小时要在 0 到 23 之间"
        else -> null
    }

    private fun rebuild() {
        val added = plans.rebuildFuture(LocalDate.now())
        Toast.makeText(this, getString(R.string.slots_remaining, added), Toast.LENGTH_SHORT).show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_events_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val removed = plans.clearFuture(LocalDate.now())
                Toast.makeText(this, getString(R.string.cleared_count, removed), Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
