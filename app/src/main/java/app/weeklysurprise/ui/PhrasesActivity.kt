package app.weeklysurprise.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.weeklysurprise.R
import app.weeklysurprise.core.Phrase
import app.weeklysurprise.core.PhrasePicker
import app.weeklysurprise.data.AppStore
import app.weeklysurprise.databinding.ActivityPhrasesBinding
import app.weeklysurprise.databinding.ItemPhraseBinding

/**
 * 话术库管理：增、删、改、启用/停用。
 *
 * 「停用」而不是只能删：有些话不想丢掉，但当下不合适（比如刚吵过架），
 * 可以先关掉不参与随机，想用了再打开。
 */
class PhrasesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhrasesBinding
    private lateinit var store: AppStore

    /** 页面内的工作副本，操作完立即落盘。 */
    private var items = mutableListOf<Phrase>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhrasesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 头部避开状态栏，列表底部避开导航栏
        binding.root.applySystemBarInsets(top = true, bottom = false)
        binding.phraseContainer.applySystemBarInsets(top = false, bottom = true)

        store = AppStore(this)
        items = store.phrases.toMutableList()

        binding.addButton.setOnClickListener { promptAdd() }
        render()
    }

    // ---------- 渲染 ----------

    private fun render() {
        val enabled = items.count { it.enabled }
        binding.summaryText.text = getString(R.string.phrase_summary, items.size, enabled)

        // 两级提示：低于硬下限直接报错，低于安全线只是建议
        when {
            enabled <= PhrasePicker.NO_REPEAT_WINDOW -> showWarning(
                getString(R.string.phrase_too_few, PhrasePicker.NO_REPEAT_WINDOW + 1),
            )
            enabled < PhrasePicker.SAFE_MINIMUM -> showWarning(
                getString(R.string.phrase_below_safe, PhrasePicker.SAFE_MINIMUM),
            )
            else -> binding.warningText.visibility = View.GONE
        }

        binding.phraseContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        items.forEachIndexed { index, phrase ->
            val row = ItemPhraseBinding.inflate(inflater, binding.phraseContainer, false)
            row.phraseText.text = phrase.text
            // 停用的条目压暗，一眼能看出它不参与随机
            row.phraseText.alpha = if (phrase.enabled) 1f else 0.4f

            row.phraseEnabled.setOnCheckedChangeListener(null)
            row.phraseEnabled.isChecked = phrase.enabled
            row.phraseEnabled.setOnCheckedChangeListener { _, checked ->
                if (!checked && wouldDropBelowFloor(index)) {
                    row.phraseEnabled.isChecked = true // 撤销这次点击
                    toast(getString(R.string.phrase_too_few, PhrasePicker.NO_REPEAT_WINDOW + 1))
                    return@setOnCheckedChangeListener
                }
                items[index] = items[index].copy(enabled = checked)
                persist()
                render()
            }

            row.phraseText.setOnClickListener { promptEdit(index) }
            row.phraseDelete.setOnClickListener { confirmDelete(index) }

            binding.phraseContainer.addView(row.root)
        }
    }

    private fun showWarning(text: String) {
        binding.warningText.text = text
        binding.warningText.visibility = View.VISIBLE
    }

    private fun persist() {
        store.phrases = items.toList()
    }

    // ---------- 操作 ----------

    private fun promptAdd() = promptText(
        title = getString(R.string.phrase_add),
        initial = "",
    ) { text ->
        if (items.any { it.text == text }) {
            toast(getString(R.string.phrase_duplicate))
            return@promptText
        }
        items.add(Phrase(text))
        persist()
        render()
    }

    private fun promptEdit(index: Int) = promptText(
        title = getString(R.string.phrase_edit),
        initial = items[index].text,
    ) { text ->
        if (items.withIndex().any { (i, p) -> i != index && p.text == text }) {
            toast(getString(R.string.phrase_duplicate))
            return@promptText
        }
        val oldText = items[index].text
        items[index] = items[index].copy(text = text)
        persist()

        // "最近用过"是按话术文字内容匹配的（不是靠稳定 ID）。
        // 改措辞而不同步这份历史，会让这条话术的"最近用过"状态被静默清空，
        // 下一次抽取就可能立刻抽到它，违反"连续 8 次不重复"的承诺。
        store.recentPhrases = store.recentPhrases.map { if (it == oldText) text else it }

        render()
    }

    private fun promptText(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSingleLine(false)
            maxLines = 4
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    toast(getString(R.string.phrase_empty))
                    return@setPositiveButton
                }
                onOk(text)
            }
            .show()
    }

    private fun confirmDelete(index: Int) {
        if (items[index].enabled && wouldDropBelowFloor(index)) {
            toast(getString(R.string.phrase_too_few, PhrasePicker.NO_REPEAT_WINDOW + 1))
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.phrase_delete_confirm, items[index].text))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                items.removeAt(index)
                persist()
                render()
            }
            .show()
    }

    /**
     * 这一条如果被禁用/删除，参与随机的条数会不会跌破硬下限。
     *
     * 这是真正的拦截，不是文字警告 —— 之前的版本只在 render() 里显示红字，
     * 任何操作都能把可用条目降到窗口以下，下一次抽话术时会在
     * PhrasePicker.pick() 里崩溃，而且是在 onResume 无条件触发的路径上，
     * 导致应用直接打不开。
     */
    private fun wouldDropBelowFloor(index: Int): Boolean {
        val enabledCount = items.count { it.enabled }
        val thisOneCounted = items[index].enabled
        val afterChange = if (thisOneCounted) enabledCount - 1 else enabledCount
        return afterChange <= PhrasePicker.NO_REPEAT_WINDOW
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
