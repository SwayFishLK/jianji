package com.jianji.ime

import android.app.Activity
import android.os.Bundle

/**
 * 设置跳板（M5.1e）：桌面图标 / 系统输入法设置齿轮用它接住点击，
 * 立即以悬浮层形式显示设置（[SettingsOverlay]）并自我结束 —— 不留下最近任务卡片。
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsOverlay.show(applicationContext)
        finish()
    }
}
