package com.jianji.ime.debug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jianji.ime.SettingsActivity
import com.jianji.ime.engine.FlatPinyinEngine
import com.jianji.ime.engine.GlossDict
import com.jianji.ime.engine.JianDict

/**
 * 仅 Debug 包含的测试页：启动即自动聚焦输入框，弹出简记键盘。
 * 设置项（学习语言 / 声音振动 / 常用语 / 剪贴板）在正式设置页（M4），这里只留引擎自测。
 */
class DebugInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val title = TextView(this).apply {
            text = "简记输入法 · 测试页"
            setTextColor(0xFFE6E8EC.toInt())
            textSize = 18f
        }

        val edit = EditText(this).apply {
            hint = "在这里随便打点字试试……"
            minLines = 3
            gravity = Gravity.TOP
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A3.toInt())
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFF232730.toInt())
                cornerRadius = 12f * d
            }
        }

        // 键盘被「收起」后，点输入框重新唤起（真实 App 的输入框也常这么做）
        edit.setOnClickListener {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.showSoftInput(edit, 0)
        }

        val settingsBtn = Button(this).apply {
            text = "打开设置页（学习语言 / 声音振动 / 常用语）"
            setOnClickListener {
                startActivity(Intent(this@DebugInputActivity, SettingsActivity::class.java))
            }
        }

        // 测试剪贴板面板用：往系统剪贴板写一条样本文本（真实使用时在别的 App 复制即可）；
        // 偶数次写入长文本，便于测试卡片折叠 / 展开与滚动
        var clipSeq = 0
        val clipBtn = Button(this).apply {
            text = "写测试剪贴板"
            setOnClickListener {
                val n = SystemClock.uptimeMillis() % 1000
                clipSeq++
                val text = if (clipSeq % 2 == 1) {
                    "测试剪贴板内容 $n"
                } else {
                    "测试长文本 $n：这是一段比较长的剪贴板内容，用来测试卡片的折叠与展开。" +
                        "默认只显示两行，超出部分会截断并出现展开箭头，点箭头可以展开查看全文。" +
                        "再补一句让内容确定超过两行，顺便测试列表滚动是否顺畅。"
                }
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("jianji-debug", text))
                Toast.makeText(this@DebugInputActivity, "已写入剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        val engineResult = TextView(this).apply {
            setTextColor(0xFF8B93A3.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }

        val testBtn = Button(this).apply {
            text = "引擎自测（词库 + 候选）"
            setOnClickListener {
                engineResult.text = "加载中……"
                Thread({
                    val sb = StringBuilder()
                    try {
                        val t0 = SystemClock.uptimeMillis()
                        val dict = assets.open("dict.bin").use { JianDict.load(it) }
                        val t1 = SystemClock.uptimeMillis()
                        val eng = FlatPinyinEngine(dict)
                        sb.append("词库 ").append(dict.size).append(" 条，")
                            .append(dict.syllableSet.size).append(" 音节，载入 ")
                            .append(t1 - t0).append(" ms\n")
                        val queries = listOf(
                            "ni", "nihao", "nh", "nhm", "nihaom", "nihaoma", "nihaozaijian",
                            "zhongguo", "kaifazhe", "xian", "shi", "w", "zaijian", "women",
                            "wsm", "jjjjj",
                        )
                        for (q in queries) {
                            val tq = SystemClock.uptimeMillis()
                            val cs = eng.candidates(q, 8)
                            val dt = SystemClock.uptimeMillis() - tq
                            sb.append(q).append(" (").append(dt).append("ms) → ")
                            sb.append(cs.joinToString(" ") { it.text + "(" + it.consumed + ")" }).append('\n')
                        }
                    } catch (t: Throwable) {
                        sb.append("失败：").append(t)
                    }
                    runOnUiThread { engineResult.text = sb }
                }, "engine-selftest").start()
            }
        }

        val glossResult = TextView(this).apply {
            setTextColor(0xFF8B93A3.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }

        val glossBtn = Button(this).apply {
            text = "释义自测（英语 / 日语）"
            setOnClickListener {
                glossResult.text = "加载中……"
                Thread({
                    val sb = StringBuilder()
                    try {
                        for (lang in listOf("en", "ja")) {
                            val t0 = SystemClock.uptimeMillis()
                            val g = assets.open("gloss-$lang.bin").use { GlossDict.load(it) }
                            val t1 = SystemClock.uptimeMillis()
                            sb.append("gloss-").append(lang).append("：").append(g.size)
                                .append(" 条，载入 ").append(t1 - t0).append(" ms\n")
                            for (w in listOf("你好", "开发", "吃饭", "今天", "什么", "我们", "谢谢")) {
                                val tq = SystemClock.uptimeMillis()
                                val a = g.annotationFor(w)
                                sb.append("  ").append(w).append(" → ").append(a ?: "（无）")
                                    .append("  (").append(SystemClock.uptimeMillis() - tq).append("ms)\n")
                            }
                        }
                    } catch (t: Throwable) {
                        sb.append("失败：").append(t)
                    }
                    runOnUiThread { glossResult.text = sb }
                }, "gloss-selftest").start()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF17191E.toInt())
            setPadding(pad, pad, pad, pad)
            addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = pad },
            )
            addView(
                edit,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                settingsBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad },
            )
            addView(
                clipBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad / 2 },
            )
            addView(
                testBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad },
            )
            addView(
                engineResult,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad / 2 },
            )
            addView(
                glossBtn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad / 2 },
            )
            addView(
                glossResult,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = pad / 2 },
            )
        }
        setContentView(root)

        edit.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}
