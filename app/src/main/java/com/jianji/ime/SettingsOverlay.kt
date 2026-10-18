package com.jianji.ime

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 设置页（悬浮窗版，M5.1e）——不产生「最近任务」卡片。
 *
 * 背景：本机 ROM 会把「移除最近任务里的设置卡片」当成强制停止整个应用，
 * 并连带把默认输入法复位。把设置改成 TYPE_APPLICATION_OVERLAY 悬浮层后，
 * 应用不再有可被清理的任务卡片 —— 后台怎么清都碰不到输入法。
 *
 * 入口：
 *  - 桌面图标 / 系统输入法设置齿轮 → SettingsActivity 跳板（当帧结束）→ show()
 *  - 键盘齿轮 / 剪贴板面板齿轮 → 直接 show()
 * 依赖 SYSTEM_ALERT_WINDOW（未授权时先弹系统授权页）。
 */
object SettingsOverlay {

    private var appCtx: Context? = null
    private var wm: WindowManager? = null
    private var root: RootFrame? = null
    private var licensesShown = false
    private var dialogOpen = false

    private var phraseListBox: LinearLayout? = null
    private var clipCountText: TextView? = null
    private var langValueText: TextView? = null

    fun isShowing(): Boolean = root != null

    fun show(context: Context) {
        if (root != null) return
        val ctx = context.applicationContext
        if (!Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, "请允许「显示在其他应用上层」后重试", Toast.LENGTH_LONG).show()
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
            }
            return
        }
        val frame = RootFrame(ctx)
        val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.CENTER
        // 全屏覆盖（含状态栏区域）：用 insets 给内容留出状态栏/导航栏空间，背景铺满
        frame.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        try {
            w.addView(frame, lp)
        } catch (_: Throwable) {
            Toast.makeText(ctx, "无法显示设置页", Toast.LENGTH_SHORT).show()
            return
        }
        appCtx = ctx
        root = frame
        wm = w
        licensesShown = false
        showSettingsPage()
    }

    fun dismiss() {
        val r = root ?: return
        root = null
        licensesShown = false
        phraseListBox = null
        clipCountText = null
        langValueText = null
        try {
            wm?.removeView(r)
        } catch (_: Throwable) {
        }
        wm = null
        appCtx = null
    }

    /** 返回键 / 标题栏左箭头：许可页先退回设置页，设置页直接关闭 */
    private fun onBack() {
        if (licensesShown) showSettingsPage() else dismiss()
    }

    /** 窗口失焦（按 Home / 呼出最近任务 / 切走）时收起设置；语言弹窗打开时除外 */
    private fun onWindowBlur() {
        if (!dialogOpen) dismiss()
    }

    // —————— 页面容器 ——————

    private class RootFrame(ctx: Context) : FrameLayout(ctx) {
        private var hadFocus = false

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) SettingsOverlay.onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) {
                hadFocus = true
            } else if (hadFocus) {
                SettingsOverlay.onWindowBlur()
            }
        }
    }

    private fun setContent(v: View) {
        val r = root ?: return
        r.removeAllViews()
        r.setBackgroundColor(ImUi.BG)
        r.addView(
            v,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    // —————— 设置页 ——————

    private fun showSettingsPage() {
        val ctx = appCtx ?: return
        licensesShown = false
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ImUi.dp(ctx, 14f), ImUi.dp(ctx, 6f), ImUi.dp(ctx, 14f), ImUi.dp(ctx, 36f))
        }
        box.addView(ImUi.header(ctx, "设置") { onBack() })

        // —— 学习语言 ——
        box.addView(ImUi.sectionLabel(ctx, "学习语言"), ImUi.lp(ctx, 26f, 14f))
        langValueText = ImUi.valueText(ctx)
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_learn,
                title = "学习语言",
                subtitle = "候选上方显示小字译词，一次学一种",
                right = langValueText,
                chevron = true,
            ) { showLearnLangDialog() },
            ImUi.lp(ctx, 10f),
        )

        // —— 按键 ——
        box.addView(ImUi.sectionLabel(ctx, "按键"), ImUi.lp(ctx, 26f, 14f))
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_sound,
                title = "按键声音",
                right = Switch(ctx).apply {
                    isChecked = Prefs.isSoundOn(ctx)
                    setOnCheckedChangeListener { _, on -> Prefs.setSoundOn(ctx, on) }
                },
            ),
            ImUi.lp(ctx, 10f),
        )
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_vibrate,
                title = "按键振动",
                right = Switch(ctx).apply {
                    isChecked = Prefs.isVibrateOn(ctx)
                    setOnCheckedChangeListener { _, on -> Prefs.setVibrateOn(ctx, on) }
                },
            ),
            ImUi.lp(ctx, 8f),
        )

        // —— 常用语 ——
        box.addView(ImUi.sectionLabel(ctx, "常用语"), ImUi.lp(ctx, 26f, 14f))
        val phraseInput = EditText(ctx).apply {
            hint = "输入一条常用语"
            setHintTextColor(ImUi.SUB)
            setTextColor(ImUi.TEXT)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 15f
            background = null
            maxLines = 1
            setPadding(0, ImUi.dp(ctx, 4f), 0, ImUi.dp(ctx, 4f))
            // 悬浮窗里的输入框：系统不会自动请求键盘，聚焦时显式弹一次
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.post {
                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_phrase,
                content = phraseInput,
                right = ImUi.pillButton(ctx, "添加") {
                    val text = phraseInput.text?.toString().orEmpty()
                    when {
                        text.isBlank() -> Unit
                        ClipboardStore.addPhrase(ctx, text) -> phraseInput.setText("")
                        else -> Toast.makeText(
                            ctx,
                            "重复、太长或已达 ${ClipboardStore.PHRASE_MAX} 条上限",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    refreshPhrases()
                },
            ),
            ImUi.lp(ctx, 10f),
        )
        phraseListBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(phraseListBox)
        box.addView(
            ImUi.note(ctx, "剪贴板面板里置顶显示；也可以在剪贴板面板左划一条内容、点 ＋ 存为常用语。"),
            ImUi.lp(ctx, 10f, 14f, 14f),
        )

        // —— 剪贴板 ——
        box.addView(ImUi.sectionLabel(ctx, "剪贴板"), ImUi.lp(ctx, 26f, 14f))
        clipCountText = ImUi.subTextView(ctx)
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_clip,
                title = "剪贴板记录",
                subtitleView = clipCountText,
                right = ImUi.dangerText(ctx, "清空") {
                    ClipboardStore.clearHistory(ctx)
                    refreshClipCount()
                    Toast.makeText(ctx, "已清空", Toast.LENGTH_SHORT).show()
                },
            ),
            ImUi.lp(ctx, 10f),
        )
        box.addView(
            ImUi.note(ctx, "清空只清历史记录，不影响常用语。"),
            ImUi.lp(ctx, 10f, 14f, 14f),
        )

        // —— 关于 ——
        box.addView(ImUi.sectionLabel(ctx, "关于"), ImUi.lp(ctx, 26f, 14f))
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) {
            "0.1.0"
        }
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_info,
                title = "关于简记",
                subtitle = "版本 $version" + if (debuggable) "（调试版）" else "",
            ),
            ImUi.lp(ctx, 10f),
        )
        box.addView(
            ImUi.cardRow(
                ctx,
                iconRes = R.drawable.ic_doc,
                title = "开源许可与署名",
                subtitle = "GPL-3.0-or-later · 词库数据来自「青简」",
                chevron = true,
            ) { showLicensesPage() },
            ImUi.lp(ctx, 8f),
        )
        box.addView(
            ImUi.note(
                ctx,
                "打字时顺带学语言的输入法：候选上方显示译词，只学英语或日语，一次一种。" +
                    "候选字体跟随系统。",
            ),
            ImUi.lp(ctx, 10f, 14f, 14f),
        )

        updateLangValue()
        refreshPhrases()
        refreshClipCount()

        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(ImUi.BG)
            addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContent(scroll)
    }

    // —————— 学习语言 ——————

    private fun showLearnLangDialog() {
        val ctx = appCtx ?: return
        val labels = arrayOf("英语", "日语", "不显示")
        val keys = arrayOf("en", "ja", "off")
        val cur = keys.indexOf(Prefs.learnLang(ctx)).coerceAtLeast(0)
        val d = AlertDialog.Builder(ctx)
            .setTitle("学习语言")
            .setSingleChoiceItems(labels, cur) { dialog, which ->
                Prefs.setLearnLang(ctx, keys[which])
                updateLangValue()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()
        d.setOnDismissListener { dialogOpen = false }
        dialogOpen = true
        d.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        d.show()
    }

    private fun updateLangValue() {
        val t = langValueText ?: return
        val ctx = appCtx ?: return
        t.text = when (Prefs.learnLang(ctx)) {
            "ja" -> "日语"
            "off" -> "不显示"
            else -> "英语"
        }
    }

    // —————— 常用语列表 ——————

    private fun refreshPhrases() {
        val listBox = phraseListBox ?: return
        val ctx = appCtx ?: return
        listBox.removeAllViews()
        val phrases = ClipboardStore.phrases(ctx)
        if (phrases.isEmpty()) {
            listBox.addView(ImUi.note(ctx, "（还没有常用语）"), ImUi.lp(ctx, 10f, 14f))
            return
        }
        for (p in phrases) {
            listBox.addView(
                ImUi.cardRow(
                    ctx,
                    iconRes = null,
                    title = p.replace('\n', ' '),
                    right = ImUi.dangerText(ctx, "删除") {
                        ClipboardStore.removePhrase(ctx, p)
                        refreshPhrases()
                    },
                ),
                ImUi.lp(ctx, 8f),
            )
        }
    }

    private fun refreshClipCount() {
        val t = clipCountText ?: return
        val ctx = appCtx ?: return
        val n = ClipboardStore.history(ctx).size
        t.text = if (n == 0) "历史为空。" else "当前记录 $n 条。"
    }

    // —————— 开源许可页 ——————

    private fun showLicensesPage() {
        val ctx = appCtx ?: return
        licensesShown = true
        val pad = ImUi.dp(ctx, 14f)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, ImUi.dp(ctx, 6f), pad, ImUi.dp(ctx, 36f))
        }
        box.addView(ImUi.header(ctx, "开源许可") { showSettingsPage() })
        box.addView(
            ImUi.note(ctx, "下列文本随应用分发（assets/legal/），正文可长按选中复制。"),
            ImUi.lp(ctx, 12f, 14f, 14f),
        )
        for ((title, file) in LICENSE_FILES) {
            val inner = ImUi.dp(ctx, 16f)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(inner, ImUi.dp(ctx, 14f), inner, ImUi.dp(ctx, 14f))
                background = ImUi.rounded(ctx, ImUi.CARD, 22f)
            }
            card.addView(
                TextView(ctx).apply {
                    text = title
                    setTextColor(ImUi.ACCENT)
                    textSize = 14f
                },
            )
            card.addView(
                ImUi.licenseBody(ctx, readAsset(ctx, file)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = ImUi.dp(ctx, 8f) },
            )
            box.addView(card, ImUi.lp(ctx, 12f))
        }
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(ImUi.BG)
            addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContent(scroll)
    }

    private fun readAsset(ctx: Context, name: String): String = try {
        ctx.assets.open("legal/$name").use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Throwable) {
        "（无法读取 $name）"
    }

    private val LICENSE_FILES = listOf(
        "署名（NOTICE）" to "NOTICE.txt",
        "GNU General Public License v3（本应用）" to "GPL-3.0-or-later.txt",
        "Unicode License v3" to "UNICODE-3.0.txt",
        "第三方声明（词库数据上游）" to "THIRD-PARTY-NOTICES.txt",
        "Apache License 2.0（开源组件）" to "APACHE-2.0.txt",
    )
}
