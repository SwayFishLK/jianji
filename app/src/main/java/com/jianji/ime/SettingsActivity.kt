package com.jianji.ime

import android.app.Activity
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
    private lateinit var themeValueText: TextView

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
        updateThemeValue()
        refreshPhrases()
        refreshClipCount()
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

        // —— 外观（4 套液态玻璃 + 旧版墨夜）——
        box.addView(ImUi.sectionLabel(this, "外观"), ImUi.lp(this, 26f, 14f))
        themeValueText = ImUi.valueText(this)
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_theme,
                title = "键盘主题",
                subtitle = "4 套液态玻璃 + 墨夜",
                right = themeValueText,
                chevron = true,
            ) { showThemeDialog() },
            ImUi.lp(this, 10f),
        )
        box.addView(
            ImUi.note(this, "深渊 / 极光 / 星云 / 余烬 4 套液态玻璃，另加旧版默认「墨夜」（深色部分略压暗）。"),
            ImUi.lp(this, 10f, 14f, 14f),
        )

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
        box.addView(
            ImUi.cardRow(
                this,
                iconRes = R.drawable.ic_level,
                title = "生词标记",
                right = Switch(this).apply {
                    isChecked = Prefs.isLevelMarkOn(this@SettingsActivity)
                    setOnCheckedChangeListener { _, on ->
                        Prefs.setLevelMarkOn(this@SettingsActivity, on)
                    }
                },
            ),
            ImUi.lp(this, 8f),
        )
        box.addView(
            ImUi.note(this, "在候选右上角标出译词的难度（B2 / C1 / C2，日语 N2 / N1），入门词不标。"),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        // —— 按键 ——
        box.addView(ImUi.sectionLabel(this, "按键"), ImUi.lp(this, 26f, 14f))
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

        // —— 学习记忆（不再对用户展示；词频与搭配在后台默默学，数据只存本机）——

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
                subtitle = "GPL-3.0-or-later · 词库：青简 + 白霜拼音",
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
                    "候选字体跟随系统；词库约 37 万条（青简 + 白霜拼音）。",
            ),
            ImUi.lp(this, 10f, 14f, 14f),
        )

        return ScrollView(this).apply {
            setBackgroundColor(ImUi.BG)
            addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // —————— 键盘主题（v0.5） ——————

    private fun showThemeDialog() {
        val themes = SkinThemes.all
        val cur = themes.indexOfFirst { it.id == Prefs.themeId(this) }.coerceAtLeast(0)
        ImUi.choiceDialog(
            this,
            title = "键盘主题",
            options = themes.map { "${it.name} · ${it.sub}" },
            checked = BooleanArray(themes.size) { it == cur },
            multi = false,
            note = "4 套液态玻璃 + 墨夜；墨夜为旧版实色键帽，背景与键面比旧版略暗。",
            swatches = themes.map { it.accent },
            onPick = { which ->
                Prefs.setThemeId(this, themes[which].id)
                updateThemeValue()
            },
        )
    }

    private fun updateThemeValue() {
        if (!::themeValueText.isInitialized) return
        themeValueText.text = SkinThemes.byId(Prefs.themeId(this)).name
    }

    // —————— 学习语言 ——————

    private fun showLearnLangDialog() {
        val labels = listOf("英语", "日语", "不显示")
        val keys = arrayOf("en", "ja", "off")
        val cur = keys.indexOf(Prefs.learnLang(this)).coerceAtLeast(0)
        ImUi.choiceDialog(
            this,
            title = "学习语言",
            options = labels,
            checked = BooleanArray(labels.size) { it == cur },
            multi = false,
            note = "一次只学一种；选「不显示」就不出译词。",
            onPick = { which ->
                Prefs.setLearnLang(this, keys[which])
                updateLangValue()
            },
        )
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
        val labels = listOf("弱", "标准", "强")
        val keys = arrayOf(Prefs.VIBRATE_WEAK, Prefs.VIBRATE_STANDARD, Prefs.VIBRATE_STRONG)
        val cur = keys.indexOf(Prefs.vibrateLevel(this)).coerceAtLeast(0)
        ImUi.choiceDialog(
            this,
            title = "振动强度",
            options = labels,
            checked = BooleanArray(labels.size) { it == cur },
            multi = false,
            note = "回到键盘按键试手感；弱档只是轻轻一点。",
            onPick = { which ->
                Prefs.setVibrateLevel(this, keys[which])
                updateVibrateLevelValue()
            },
        )
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
        val labels = listOf(
            "平翘舌（zh/z、ch/c、sh/s）",
            "前后鼻音（ang/an、eng/en、ing/in）",
            "n 与 l（拿 / 拉）",
        )
        val have = Prefs.fuzzyGroups(this).split(',').toHashSet()
        val checked = BooleanArray(keys.size) { keys[it] in have }
        ImUi.choiceDialog(
            this,
            title = "模糊音",
            options = labels,
            checked = checked,
            multi = true,
            note = "可同时开启多组；开启后打错也能出对词。",
            onConfirm = { result ->
                Prefs.setFuzzyGroups(this, keys.filterIndexed { i, _ -> result[i] }.joinToString(","))
                updateFuzzyValue()
            },
        )
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
