package com.jianji.ime

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 开源许可页（M5.1 卡片风格）：署名 NOTICE 与各许可全文，文本来自 assets/legal/。
 * 入口：设置页「关于」区的「开源许可与署名」。正文可长按选中复制。
 */
class LicensesActivity : Activity() {

    /** 离开许可页 = 整个设置任务关闭（避免任务残留进最近任务，见 SettingsActivity.onUserLeaveHint） */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): ViewGroup {
        val pad = ImUi.dp(this, 14f)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, ImUi.dp(this@LicensesActivity, 6f), pad, ImUi.dp(this@LicensesActivity, 36f))
        }
        box.addView(ImUi.header(this, "开源许可") { finish() })
        box.addView(
            ImUi.note(this, "下列文本随应用分发（assets/legal/），正文可长按选中复制。"),
            ImUi.lp(this, 12f, 14f, 14f),
        )
        for ((title, file) in FILES) {
            val inner = ImUi.dp(this, 16f)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(inner, ImUi.dp(this@LicensesActivity, 14f), inner, ImUi.dp(this@LicensesActivity, 14f))
                background = ImUi.rounded(this@LicensesActivity, ImUi.CARD, 22f)
            }
            card.addView(
                TextView(this).apply {
                    text = title
                    setTextColor(ImUi.ACCENT)
                    textSize = 14f
                },
            )
            card.addView(
                ImUi.licenseBody(this, readAsset(file)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = ImUi.dp(this@LicensesActivity, 8f) },
            )
            box.addView(card, ImUi.lp(this, 12f))
        }
        return ScrollView(this).apply {
            setBackgroundColor(ImUi.BG)
            addView(box, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun readAsset(name: String): String = try {
        assets.open("legal/$name").use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Throwable) {
        "（无法读取 $name）"
    }

    private companion object {
        val FILES = listOf(
            "署名（NOTICE）" to "NOTICE.txt",
            "GNU General Public License v3（本应用）" to "GPL-3.0-or-later.txt",
            "Unicode License v3" to "UNICODE-3.0.txt",
            "第三方声明（词库数据上游）" to "THIRD-PARTY-NOTICES.txt",
            "Apache License 2.0（开源组件）" to "APACHE-2.0.txt",
        )
    }
}
