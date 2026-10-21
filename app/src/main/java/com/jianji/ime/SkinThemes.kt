package com.jianji.ime

/**
 * 液态玻璃皮肤的 8 套 Mirage 配色（取自 dsh-dream-skin）。
 *
 * 这里只放「皮肤需要的基础色」；键面渐变、边框、高光、选中态等派生色
 * 统一在 [ImTheme] 里按明暗自动计算，避免 8 套 × 十几个透明度手写漏改。
 *
 * 注意：输入法窗口不做真实透明/背景模糊 —— 键盘下方的应用画面不采样，
 * 只在这些底色上叠一层带主题色的「底片」，再用键面高光模拟玻璃。
 */
data class SkinTheme(
    val id: String,
    val name: String,
    val sub: String,
    val light: Boolean,
    /** 主题强调色：回车、选中候选、光标、开关等 */
    val accent: Int,
    /** 副强调色：背景光晕、头像式渐变、少量装饰 */
    val accent2: Int,
    /** 键盘底片的上/下色（不透明，靠渐变与光晕显通透） */
    val windowTop: Int,
    val windowBottom: Int,
    /** 底片上的主题光晕（ARGB） */
    val glow: Int,
    val glow2: Int,
    /** 覆盖面板 / 卡片（剪贴板、表情、更多候选） */
    val panelBg: Int,
    val cardBg: Int,
    val keyText: Int,
    val fnText: Int,
    val hintText: Int,
)

object SkinThemes {

    const val DEFAULT_ID = "abyss"

    val all: List<SkinTheme> = listOf(
        SkinTheme(
            id = "abyss", name = "深渊", sub = "薰衣草 / 青蓝", light = false,
            accent = 0xFFA093FB.toInt(), accent2 = 0xFF4884AE.toInt(),
            windowTop = 0xFF342A58.toInt(), windowBottom = 0xFF0D1A2B.toInt(),
            glow = 0x55A093FB, glow2 = 0x3A4884AE,
            panelBg = 0xFF10121B.toInt(), cardBg = 0xFF1B1F2B.toInt(),
            keyText = 0xFFEDF1FA.toInt(), fnText = 0xFFC6CDDA.toInt(), hintText = 0xFF98A3B3.toInt(),
        ),
        SkinTheme(
            id = "aurora", name = "极光", sub = "青绿 / 湖水", light = false,
            accent = 0xFF00BBC5.toInt(), accent2 = 0xFF2ABE90.toInt(),
            windowTop = 0xFF0C3A3B.toInt(), windowBottom = 0xFF071A20.toInt(),
            glow = 0x5500BBC5, glow2 = 0x3A2ABE90,
            panelBg = 0xFF0B1718.toInt(), cardBg = 0xFF152526.toInt(),
            keyText = 0xFFEDF9F8.toInt(), fnText = 0xFFC2DCD9.toInt(), hintText = 0xFF8FAEAC.toInt(),
        ),
        SkinTheme(
            id = "nebula", name = "星云", sub = "紫罗兰 / 洋红", light = false,
            accent = 0xFFB48DF5.toInt(), accent2 = 0xFFD269C4.toInt(),
            windowTop = 0xFF3A2354.toInt(), windowBottom = 0xFF140B26.toInt(),
            glow = 0x58B48DF5, glow2 = 0x40D269C4,
            panelBg = 0xFF100B19.toInt(), cardBg = 0xFF1E1729.toInt(),
            keyText = 0xFFF5EEFF.toInt(), fnText = 0xFFD1C2E2.toInt(), hintText = 0xFFA392B8.toInt(),
        ),
        SkinTheme(
            id = "ember", name = "余烬", sub = "琥珀 / 珊瑚", light = false,
            accent = 0xFFEB8555.toInt(), accent2 = 0xFFB45C69.toInt(),
            windowTop = 0xFF3B1D12.toInt(), windowBottom = 0xFF1A0B07.toInt(),
            glow = 0x55EB8555, glow2 = 0x3AB45C69,
            panelBg = 0xFF150D09.toInt(), cardBg = 0xFF241711.toInt(),
            keyText = 0xFFFCF0E8.toInt(), fnText = 0xFFDCC4B6.toInt(), hintText = 0xFFB49383.toInt(),
        ),
        SkinTheme(
            id = "midnight", name = "午夜", sub = "近黑 / 冷蓝", light = false,
            accent = 0xFF889EF6.toInt(), accent2 = 0xFF587AC4.toInt(),
            windowTop = 0xFF1A1D33.toInt(), windowBottom = 0xFF080910.toInt(),
            glow = 0x44889EF6, glow2 = 0x30587AC4,
            panelBg = 0xFF0B0B0F.toInt(), cardBg = 0xFF17171D.toInt(),
            keyText = 0xFFEFF2FA.toInt(), fnText = 0xFFC3C9D6.toInt(), hintText = 0xFF9098A8.toInt(),
        ),
        SkinTheme(
            id = "ivory", name = "象牙", sub = "暖纸 / 钴蓝", light = true,
            accent = 0xFF1D6CC1.toInt(), accent2 = 0xFFCD9A5E.toInt(),
            windowTop = 0xFFF5F2E9.toInt(), windowBottom = 0xFFE8E1D4.toInt(),
            glow = 0x3096C3F0, glow2 = 0x28F0C29F,
            panelBg = 0xFFF2EDE4.toInt(), cardBg = 0xFFFBF8F2.toInt(),
            keyText = 0xFF2C241B.toInt(), fnText = 0xFF5A4A3A.toInt(), hintText = 0xFF84725B.toInt(),
        ),
        SkinTheme(
            id = "mist", name = "晨雾", sub = "雾蓝 / 湖水", light = true,
            accent = 0xFF006CB5.toInt(), accent2 = 0xFF40B4BA.toInt(),
            windowTop = 0xFFE6F0F7.toInt(), windowBottom = 0xFFD6E3EE.toInt(),
            glow = 0x3296C3F0, glow2 = 0x2A7CD6D6,
            panelBg = 0xFFE9F1F6.toInt(), cardBg = 0xFFF7FBFD.toInt(),
            keyText = 0xFF173145.toInt(), fnText = 0xFF476174.toInt(), hintText = 0xFF6C8292.toInt(),
        ),
        SkinTheme(
            id = "rose", name = "玫瑰", sub = "玫瑰 / 藕紫", light = true,
            accent = 0xFFA74693.toInt(), accent2 = 0xFFD67EAC.toInt(),
            windowTop = 0xFFFAEEF0.toInt(), windowBottom = 0xFFEFDEE7.toInt(),
            glow = 0x32E2A9D3, glow2 = 0x2AD6B7DE,
            panelBg = 0xFFF5E9ED.toInt(), cardBg = 0xFFFCF7F9.toInt(),
            keyText = 0xFF41253A.toInt(), fnText = 0xFF6F4A61.toInt(), hintText = 0xFF8F6B7E.toInt(),
        ),
    )

    fun byId(id: String?): SkinTheme =
        all.firstOrNull { it.id == id } ?: all.first()
}