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
