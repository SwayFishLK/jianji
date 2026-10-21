package com.jianji.ime

/**
 * 液态玻璃皮肤的配色。
 *
 * 当前保留 5 套：深渊 / 极光 / 星云 / 余烬 4 套彩色玻璃，
 * 以及旧版默认的「墨夜」经典平涂皮肤（背景比旧版略暗）。
 *
 * 这里只放「皮肤需要的基础色」；键面渐变、边框、高光、选中态等派生色
 * 统一在 [ImTheme] 里按明暗自动计算，避免每套 × 十几个透明度手写漏改。
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
    /** 液态玻璃底片的三团「彩色照片」光斑：右上亮、左侧冷色、底部深色 */
    val artHi: Int,
    val artCool: Int,
    val artDeep: Int,
    /** 底片对角渐变的 4 个色标（#1c2454 → #15516b → #3b2c6b → #191332） */
    val artBase: IntArray,
    /** 覆盖面板 / 卡片（剪贴板、表情、更多候选） */
    val panelBg: Int,
    val cardBg: Int,
    val keyText: Int,
    val fnText: Int,
    val hintText: Int,
    /** 经典平涂皮肤：不画彩色底片 / 玻璃键面，回到旧版「墨夜」的外观 */
    val classic: Boolean = false,
    /** 经典皮肤专用键面（普通玻璃主题传 0，由 [ImTheme] 自动派生） */
    val keyBg: Int = 0,
    val keyPressed: Int = 0,
    val keyFnBg: Int = 0,
)

object SkinThemes {

    const val DEFAULT_ID = "abyss"

    val all: List<SkinTheme> = listOf(
        SkinTheme(
            id = "abyss", name = "深渊", sub = "薰衣草 / 青蓝", light = false,
            accent = 0xFFA093FB.toInt(), accent2 = 0xFF4884AE.toInt(),
            windowTop = 0xFF342A58.toInt(), windowBottom = 0xFF0D1A2B.toInt(),
            glow = 0x55A093FB, glow2 = 0x3A4884AE,
            artHi = 0xF0D1C7FF.toInt(), artCool = 0xD143C4E6.toInt(), artDeep = 0xD17356D6.toInt(),
            artBase = intArrayOf(
                0xFF1C2454.toInt(), 0xFF15516B.toInt(),
                0xFF3B2C6B.toInt(), 0xFF191332.toInt(),
            ),
            panelBg = 0xFF10121B.toInt(), cardBg = 0xFF1B1F2B.toInt(),
            keyText = 0xFFEDF1FA.toInt(), fnText = 0xFFC6CDDA.toInt(), hintText = 0xFF98A3B3.toInt(),
        ),
        SkinTheme(
            id = "aurora", name = "极光", sub = "青绿 / 湖水", light = false,
            accent = 0xFF00BBC5.toInt(), accent2 = 0xFF2ABE90.toInt(),
            windowTop = 0xFF0C3A3B.toInt(), windowBottom = 0xFF071A20.toInt(),
            glow = 0x5500BBC5, glow2 = 0x3A2ABE90,
            artHi = 0xEBAEFFDC.toInt(), artCool = 0xDB23E0CF.toInt(), artDeep = 0xCC2674BE.toInt(),
            artBase = intArrayOf(
                0xFF0B3C40.toInt(), 0xFF116C70.toInt(),
                0xFF1C3C70.toInt(), 0xFF08151D.toInt(),
            ),
            panelBg = 0xFF0B1718.toInt(), cardBg = 0xFF152526.toInt(),
            keyText = 0xFFEDF9F8.toInt(), fnText = 0xFFC2DCD9.toInt(), hintText = 0xFF8FAEAC.toInt(),
        ),
        SkinTheme(
            id = "nebula", name = "星云", sub = "紫罗兰 / 洋红", light = false,
            accent = 0xFFB48DF5.toInt(), accent2 = 0xFFD269C4.toInt(),
            windowTop = 0xFF3A2354.toInt(), windowBottom = 0xFF140B26.toInt(),
            glow = 0x58B48DF5, glow2 = 0x40D269C4,
            artHi = 0xEBF0D2FF.toInt(), artCool = 0xC782A5FF.toInt(), artDeep = 0xD1E652BC.toInt(),
            artBase = intArrayOf(
                0xFF2C1B58.toInt(), 0xFF493272.toInt(),
                0xFF752C72.toInt(), 0xFF201027.toInt(),
            ),
            panelBg = 0xFF100B19.toInt(), cardBg = 0xFF1E1729.toInt(),
            keyText = 0xFFF5EEFF.toInt(), fnText = 0xFFD1C2E2.toInt(), hintText = 0xFFA392B8.toInt(),
        ),
        SkinTheme(
            id = "ember", name = "余烬", sub = "琥珀 / 珊瑚", light = false,
            accent = 0xFFEB8555.toInt(), accent2 = 0xFFB45C69.toInt(),
            windowTop = 0xFF3B1D12.toInt(), windowBottom = 0xFF1A0B07.toInt(),
            glow = 0x55EB8555, glow2 = 0x3AB45C69,
            artHi = 0xF0FFE7B9.toInt(), artCool = 0xE0FFA85D.toInt(), artDeep = 0xC7E44B71.toInt(),
            artBase = intArrayOf(
                0xFF5B2C19.toInt(), 0xFF7D4621.toInt(),
                0xFF6C2448.toInt(), 0xFF26100F.toInt(),
            ),
            panelBg = 0xFF150D09.toInt(), cardBg = 0xFF241711.toInt(),
            keyText = 0xFFFCF0E8.toInt(), fnText = 0xFFDCC4B6.toInt(), hintText = 0xFFB49383.toInt(),
        ),
        SkinTheme(
            id = "ink", name = "墨夜", sub = "原默认 / 深灰", light = false,
            accent = 0xFF5B8DEF.toInt(), accent2 = 0xFF4A7BE0.toInt(),
            // 比旧版 #1C1F26 略暗，保留一点上浅下深的层次，但不做彩色底片。
            windowTop = 0xFF171A21.toInt(), windowBottom = 0xFF101319.toInt(),
            glow = 0, glow2 = 0,
            artHi = 0, artCool = 0, artDeep = 0,
            artBase = intArrayOf(
                0xFF171A21.toInt(), 0xFF171A21.toInt(),
                0xFF101319.toInt(), 0xFF101319.toInt(),
            ),
            panelBg = 0xFF12151B.toInt(), cardBg = 0xFF1E2229.toInt(),
            keyText = 0xFFE6E8EC.toInt(), fnText = 0xFFC6CDD9.toInt(), hintText = 0xFF8B93A3.toInt(),
            classic = true,
            // 旧版键面 #2A2E37 / #232730，按下 #3A4150，整体略压暗。
            keyBg = 0xFF262A32.toInt(),
            keyPressed = 0xFF353B48.toInt(),
            keyFnBg = 0xFF1F232B.toInt(),
        ),
    )

    fun byId(id: String?): SkinTheme =
        all.firstOrNull { it.id == id } ?: all.first()
}