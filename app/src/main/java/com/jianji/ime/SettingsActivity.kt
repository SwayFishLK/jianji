package com.jianji.ime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup

/**
 * 正式设置页（M5.1 卡片风格）：学习语言 / 按键 / 常用语 / 剪贴板 / 关于。
 * 桌面图标与输入法选择器（android:settingsActivity）都进这里；剪贴板面板顶栏的「设置」也进这里。
 * 版式：顶部「← 设置」；分区小标题（accent）；每行一张圆角卡片：左圆形图标 + 标题/副标题 + 右侧控件。
 */
class SettingsActivity : Activity() {

    private lateinit var phraseListBox: LinearLayout
    private lateinit var clipCountText: TextView
    private lateinit var langValueText: TextView
    private lateinit var vibrateLevelText: TextView
    private lateinit var fuzzyValueText: TextView
    private lateinit var learnDataText: TextView

    /** 正在跳转「开源许可」页（同任务内的正常导航，不算离开设置页） */
    private var navigatingToLicense = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        navigatingToLicense = false
        updateLangValue()
        updateVibrateLevelValue()
        updateFuzzyValue()
        refreshPhrases()
        refreshClipCount()
        updateLearnDataValue()
    }

    /**
     * 离开设置页（回桌面 / 呼出最近任务 / 切到别的应用）时立即关闭自己。
     * 原因：本机 ROM 会把「移除最近任务里的卡片」当成强制停止，连带复位默认输入法；
     * 设置页用过即销毁 → 最近任务里永远没有简记卡片 → 怎么清后台都碰不到输入法（对齐搜狗的行为）。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!navigatingToLicense) finish()
    }

    // —————— 布局 ——————

    private fun buildUi(): ViewGroup {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ImUi.dp(this@SettingsActivity, 14f), ImUi.dp(this@SettingsActivity, 6f),
                ImUi.dp(this@SettingsActivity, 14f), ImUi.dp(this@SettingsActivity, 36f))
        }
        box.addView(ImUi.header(this, "设置") { finish() })

        // —— 学习语言 ——
        box.addView(ImUi.sectionLabel(this, "学习语言"), ImUi.lp(this, 26f, 14f))
        langValueText = ImUi.valueText(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_learn,
                title = "学习语言",
                subtitle = "候选上方显示小字译词，一次学一种",
                right = langValueText,
                chevron = true,
            ) { showLearnLangDialog() },
            ImUi.lp(this, 10f),
        )

        // —— 按键 ——
        box.addView(ImUi.sectionLabel(this, "按键"), ImUi.lp(this, 26f, 14f))
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_sound,
                title = "按键声音",
                right = Switch(this).apply {
                    isChecked = Prefs.isSoundOn(this@SettingsActivity)
                    setOnCheckedChangeListener { _, on -> Prefs.setSoundOn(this@SettingsActivity, on) }
                },
            ),
            ImUi.lp(this, 10f),
        )
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_vibrate,
                title = "按键振动",
                right = Switch(this).apply {
                    isChecked = Prefs.isVibrateOn(this@SettingsActivity)
                    setOnCheckedChangeListener { _, on -> Prefs.setVibrateOn(this@SettingsActivity, on) }
                },
            ),
            ImUi.lp(this, 8f),
        )
        vibrateLevelText = ImUi.valueText(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_vibrate,
                title = "振动强度",
                subtitle = "弱 / 标准 / 强（默认弱）",
                right = vibrateLevelText,
                chevron = true,
            ) { showVibrateLevelDialog() },
            ImUi.lp(this, 8f),
        )

        // —— 输入 ——
        box.addView(ImUi.sectionLabel(this, "输入"), ImUi.lp(this, 26f, 14f))
        fuzzyValueText = ImUi.valueText(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_fuzzy,
                title = "模糊音",
                subtitle = "平翘舌 / 前后鼻音 / n-l 按需放行，打错也算对",
                right = fuzzyValueText,
                chevron = true,
            ) { showFuzzyDialog() },
            ImUi.lp(this, 10f),
        )

        // —— 常用语 ——
        box.addView(ImUi.sectionLabel(this, "常用语"), ImUi.lp(this, 26f, 14f))
        val phraseInput = EditText(this).apply {
            hint = "输入一条常用语"
            setHintTextColor(ImUi.SUB)
            setTextColor(ImUi.TEXT)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 15f
            background = null
            maxLines = 1
            setPadding(0, ImUi.dp(this@SettingsActivity, 4f), 0, ImUi.dp(this@SettingsActivity, 4f))
        }
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_phrase,
                content = phraseInput,
                right = ImUi.pillButton(this, "添加") {
                    val text = phraseInput.text?.toString().orEmpty()
                    when {
                        text.isBlank() -> Unit
                        ClipboardStore.addPhrase(this, text) -> phraseInput.setText("")
                        else -> Toast.makeText(
                            this,
                            "重复、太长或已达 ${ClipboardStore.PHRASE_MAX} 条上限",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    refreshPhrases()
                },
            ),
            ImUi.lp(this, 10f),
        )
        phraseListBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(phraseListBox)
        box.addView(
            ImUi.note(this, "剪贴板面板里置顶显示；也可以在剪贴板面板左划一条内容、点 ＋ 存为常用语。"),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        // —— 剪贴板 ——
        box.addView(ImUi.sectionLabel(this, "剪贴板"), ImUi.lp(this, 26f, 14f))
        clipCountText = ImUi.subTextView(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_clip,
                title = "剪贴板记录",
                subtitleView = clipCountText,
                right = ImUi.dangerText(this, "清空") {
                    ClipboardStore.clearHistory(this)
                    refreshClipCount()
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                },
            ),
            ImUi.lp(this, 10f),
        )
        box.addView(
            ImUi.note(this, "清空只清历史记录，不影响常用语。"),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        // —— 学习记忆（v0.2）——
        box.addView(ImUi.sectionLabel(this, "学习记忆"), ImUi.lp(this, 26f, 14f))
        learnDataText = ImUi.subTextView(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_memory,
                title = "学习数据",
                subtitleView = learnDataText,
                chevron = true,
            ) { showLearnDataDialog() },
            ImUi.lp(this, 10f),
        )
        box.addView(
            ImUi.note(this, "打过的词会记得：常用的往前站、刚打过的优先（点击可看和删）。数据只存本机。"),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        // —— 关于 ——
        box.addView(ImUi.sectionLabel(this, "关于"), ImUi.lp(this, 26f, 14f))
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "0.1.0"
        }
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_info,
                title = "关于简记",
                subtitle = "版本 $version" + if (debuggable) "（调试版）" else "",
            ),
            ImUi.lp(this, 10f),
        )
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_doc,
                title = "开源许可与署名",
                subtitle = "GPL-3.0-or-later · 词库数据来自「青简」",
                chevron = true,
            ) {
                navigatingToLicense = true
                startActivity(Intent(this, LicensesActivity::class.java))
            },
            ImUi.lp(this, 8f),
        )
        box.addView(
            ImUi.note(
                this,
                "打字时顺带学语言的输入法：候选上方显示译词，只学英语或日语，一次一种。" +
                    "候选字体跟随系统。",
            ),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        return ScrollView(this).apply {
            setBackgroundColor(ImUi.BG)
            addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // —————— 学习语言 ——————

    private fun showLearnLangDialog() {
        val labels = arrayOf("英语", "日语", "不显示")
        val keys = arrayOf("en", "ja", "off")
        val cur = keys.indexOf(Prefs.learnLang(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("学习语言")
            .setSingleChoiceItems(labels, cur) { dialog, which ->
                Prefs.setLearnLang(this, keys[which])
                updateLangValue()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateLangValue() {
        if (!::langValueText.isInitialized) return
        langValueText.text = when (Prefs.learnLang(this)) {
            "ja" -> "日语"
            "off" -> "不显示"
            else -> "英语"
        }
    }

    // —————— 振动强度（v0.2） ——————

    private fun showVibrateLevelDialog() {
        val labels = arrayOf("弱", "标准", "强")
        val keys = arrayOf(Prefs.VIBRATE_WEAK, Prefs.VIBRATE_STANDARD, Prefs.VIBRATE_STRONG)
        val cur = keys.indexOf(Prefs.vibrateLevel(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("振动强度")
            .setSingleChoiceItems(labels, cur) { dialog, which ->
                Prefs.setVibrateLevel(this, keys[which])
                updateVibrateLevelValue()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateVibrateLevelValue() {
        if (!::vibrateLevelText.isInitialized) return
        vibrateLevelText.text = when (Prefs.vibrateLevel(this)) {
            Prefs.VIBRATE_STANDARD -> "标准"
            Prefs.VIBRATE_STRONG -> "强"
            else -> "弱"
        }
    }

    // —————— 模糊音（v0.2） ——————

    private fun showFuzzyDialog() {
        val keys = arrayOf("zh", "nasal", "nl")
        val labels = arrayOf(
            "平翘舌（zh/z、ch/c、sh/s）",
            "前后鼻音（ang/an、eng/en、ing/in）",
            "n 与 l（拿 / 拉）",
        )
        val have = Prefs.fuzzyGroups(this).split(',').toHashSet()
        val checked = BooleanArray(keys.size) { keys[it] in have }
        AlertDialog.Builder(this)
            .setTitle("模糊音")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("确定") { _, _ ->
                Prefs.setFuzzyGroups(this, keys.filterIndexed { i, _ -> checked[i] }.joinToString(","))
                updateFuzzyValue()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateFuzzyValue() {
        if (!::fuzzyValueText.isInitialized) return
        val names = Prefs.fuzzyGroups(this).split(',').filter { it.isNotEmpty() }.map {
            when (it) {
                "zh" -> "平翘舌"
                "nasal" -> "鼻音"
                "nl" -> "n/l"
                else -> it
            }
        }
        fuzzyValueText.text = if (names.isEmpty()) "关闭" else names.joinToString("·")
    }

    // —————— 学习数据（v0.2） ——————

    private fun updateLearnDataValue() {
        if (!::learnDataText.isInitialized) return
        val n = Prefs.loadWordStats(this).size
        val m = Prefs.loadPairs(this).size
        learnDataText.text = if (n == 0 && m == 0) "还没有记录" else "已学 $n 词 · 搭配 $m 组"
    }

    /** 学习数据：看常用词 Top / 删单条 / 清空全部（清空会同步通知输入法，立刻生效） */
    private fun showLearnDataDialog() {
        val words = Prefs.loadWordStats(this)
        if (words.isEmpty()) {
            Toast.makeText(this, "还没有学习记录", Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dialog = AlertDialog.Builder(this)
            .setTitle("学习数据")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空全部") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("清空全部学习数据？")
                    .setMessage("词频和习惯搭配都会清掉，打字记录会从头开始学。")
                    .setPositiveButton("清空") { _, _ ->
                        Prefs.saveWordStats(this, emptyMap())
                        Prefs.savePairs(this, emptyMap())
                        updateLearnDataValue()
                        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .create()
        val tops = words.entries.sortedByDescending { it.value.count }.take(50)
        for (e in tops) {
            lateinit var row: LinearLayout
            row = ImUi.cardRow(
                this,
                iconRes = null,
                title = e.key,
                subtitle = "打过 ${e.value.count} 次",
                right = ImUi.dangerText(this, "删除") {
                    val m = Prefs.loadWordStats(this)
                    m.remove(e.key)
                    Prefs.saveWordStats(this, m)
                    box.removeView(row)
                    if (box.childCount == 0) {
                        box.addView(ImUi.note(this, "（还没有学习记录）"), ImUi.lp(this, 10f, 14f))
                    }
                    updateLearnDataValue()
                },
            )
            box.addView(row, ImUi.lp(this, 8f))
        }
        dialog.show()
    }

    // —————— 常用语列表 ——————

    private fun refreshPhrases() {
        phraseListBox.removeAllViews()
        val phrases = ClipboardStore.phrases(this)
        if (phrases.isEmpty()) {
            phraseListBox.addView(ImUi.note(this, "（还没有常用语）"), ImUi.lp(this, 10f, 14f))
            return
        }
        for (p in phrases) {
            phraseListBox.addView(
                ImUi.cardRow(
                    this,
                    iconRes = null,
                    title = p.replace('\n', ' '),
                    right = ImUi.dangerText(this, "删除") {
                        ClipboardStore.removePhrase(this, p)
                        refreshPhrases()
                    },
                ),
                ImUi.lp(this, 8f),
            )
        }
    }

    private fun refreshClipCount() {
        val n = ClipboardStore.history(this).size
        clipCountText.text = if (n == 0) "历史为空。" else "当前记录 $n 条。"
    }
}
