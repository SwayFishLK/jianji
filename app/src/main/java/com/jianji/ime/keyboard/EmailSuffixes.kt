package com.jianji.ime.keyboard

/**
 * 常见邮箱后缀（本地静态表，零联网）。
 *
 * - [common] 用于键盘输入 `@` 后的候选栏提示（此时 `@` 已经上屏，只补后缀）；
 * - [symbols] 用于符号面板的「邮箱」分类（带上 `@`，可一次性插入完整后缀）。
 */
object EmailSuffixes {

    val common: List<String> = listOf(
        "qq.com",
        "163.com",
        "126.com",
        "gmail.com",
        "outlook.com",
        "foxmail.com",
        "sina.com",
        "icloud.com",
        "hotmail.com",
        "yahoo.com",
        "139.com",
        "aliyun.com",
        "yeah.net",
        "vip.qq.com",
        "vip.163.com",
        "21cn.com",
    )

    /** 符号面板用：完整邮箱后缀（含 @） */
    val symbols: List<String> = common.map { "@$it" }
}
