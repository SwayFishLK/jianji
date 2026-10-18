# 简记 · 进度与接续手册

> 这是给「未来的自己」看的项目状态快照：即使对话记忆被压缩或丢失，
> 打开这个文件就能接上进度。**每完成一个里程碑或重要步骤后更新它。**
>
> 最后更新：2026-10-18 晚（**0.1.3**：设置页「用过即焚」，最近任务里再无简记卡片、清后台不复位输入法；
> 0.1.2 英文复位修复保留。模拟器自检沙箱已建成；此前 0.1.1（M5.1b 双修）；
> `.dev-env` 保留 —— 用户明确：必须等他说清才清）

### 项目改名：`shurufa` → `jianji`（2026-10-18 下午）
- 资源管理器直接改名一直报「被占用」：占用者 = opencode 进程 ×2 + `adb.exe`（adb server 的 cwd 卡在文件夹里）。
  杀 adb 后，用「整体搬家」解决：全内容 `Move-Item` 进新建 `jianji` → 删空壳 `shurufa`（成功）→ 改名实际完成。
- 已同步 4 处路径引用：`local.properties`（`sdk.dir`）、`AGENTS.md`、`docs/dev-env.md`、`docs/PROGRESS.md`；
  源码扫描无 `shurufa` 残留（仅 `app/build` 历史日志含旧绝对路径，随下次构建刷新）。
- 构建验证：`gradle :app:testDebugUnitTest :app:assembleRelease` BUILD SUCCESSFUL（15s；配置缓存因路径变更重建），
  release APK 7,685,983 字节（与交付副本同大小，未重新分发）。
- **以后启动 opencode 一律在 `D:\Fish\documents\jianji` 目录**（旧路径已不存在）。

### 模拟器自检沙箱：Android 12（2026-10-18 下午，已装好并冒烟通过）
- 分工：**手机 = 验收/手感（不动摇）**；官方模拟器 = 开发期自检沙箱（不占用户手机、可随时脏测试/自动化）。
- 组件：`emulator 37.2.12` + `system-images;android-32;google_apis;x86_64`（Android 12 = 与手机同版本）；
  AVD `jianji`（pixel_5 档、`hw.keyboard=no` 软键盘模式、4GB 内存），全部收在 `.dev-env`
  （AVD 目录 `.dev-env\avd`，`env.ps1` 已设 `ANDROID_AVD_HOME`；将来清 `.dev-env` 即无痕）。
- 启动（二选一）：
  - 无窗口（日常自动化，用户无感）：`emulator -avd jianji -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
  - 有窗口（用户围观/演示，70% 缩放）：`emulator -avd jianji -gpu auto -scale 0.7 -no-audio`（实测 22s 开机）
  关闭：`adb -s emulator-5554 emu kill`；WHPX 加速已验证 `emulator -accel-check`。
- 自检基线（已验证）：装 0.1.1 → `ime enable/set com.jianji.ime/.JianjiImeService` → 设置搜索框敲 `nihao`
  → 候选「你好（int. hello · int. hi）」正常、空格上屏「你好」、logcat 无崩溃。截图见临时目录 emu_*。
- 系统语言：简体中文（`adb root` → `setprop persist.sys.locale zh-CN` → 重启；A12 生效，已验证重启后保持）。
- 自动化技巧：`uiautomator dump` 拿控件坐标（设置首页搜索条实测 `[209,584][577,658]`）；键盘是自绘 View，
  键位坐标用截图量（1080×2340 下：i=813 / o=923 / h=653 / a=105 / n=744，y 行 = 1686/1849/2010）。
- 坑：从设置首页点搜索条进入搜索页后，键盘可能不自动弹（`mInputShown=false`）——**再点一次输入框本身**即可（实测有效）。
- 坑：**sdkmanager/avdmanager 是 .bat —— 带分号的包名参数会被 cmd 拆成 4 个**；解法 = 写临时 `.cmd` 用引号包住整串再调用。
- 差异提醒：AOSP + 软件渲染 → **手感/性能/系统字体观感仍以真机为准**；模拟器负责功能/布局/手势/崩溃回归。

### M5.1c：两个使用反馈修复（0.1.2，2026-10-18 晚，模拟器已验证，待手机验收）
- **① 英文模式跨会话残留**：切到英文后收起键盘再打开，仍是英文。根因 = `englishMode` 只是内存状态，仅「中/英」键翻转，
  唤起归位逻辑没管它。修复 = `resetInputState()` 里 `englishMode = false`（该函数在 onStartInput / onStartInputView 都会走）。
  模拟器验证：切英文（键帽变「英/中」）→ 收起 → 重开回到「中/英」→ 敲 nihao 出「你好」。
- **② 清后台导致默认输入法被复位**：打开设置页后，在最近任务里清掉它，ROM 会把默认输入法踢回系统键盘。
  根因两层：设置页从键盘齿轮以 NEW_TASK 拉起 → 出现在最近任务；本机 ROM 清理后台卡片 = **强制停止整个包**，
  而本机在 IME 被 force-stop 时会复位默认输入法（与既有「不要 am force-stop」坑同源）。
  修复（0.1.2 初版）= `SettingsActivity` 加 `android:excludeFromRecents="true"`（不进最近任务；清理路径失效）。
  **实测在 ZUI 上不充分**：从设置页前台直接呼出最近任务时，当前任务仍被强制展示、可被划掉 → 见 M5.1d。
  模拟器验证：齿轮打开设置正常；最近任务里无「简记」卡片（intent 带 `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`）。
  残留风险：系统设置里的「强行停止」仍会复位默认输入法 —— ROM 行为，App 侧无法规避。
- 版本：0.1.2（versionCode 3）；已覆盖安装到手机（默认输入法保持简记、数据保留）。
  交付副本：`D:\Fish\Documents\简记输入法-v0.1.2.apk`（7,686,015 字节，
  sha256 `0a2597b077e6c93c5128e63ab64868258e79cff421f9177376971b3538f1d264`）；旧 0.1.1 副本已删。

### M5.1d：设置页「用过即焚」——最近任务里永不出现简记卡片（0.1.3，2026-10-18 晚，已上机验证）
- 用户反馈：0.1.2 的 excludeFromRecents 无效——打开设置页后清掉卡片，默认输入法仍被复位。
- 实机取证（手机 logcat）：触发动作 = 最近任务里**划掉简记卡片**；日志 `Killing ... (adj 200): remove task`
  + `Force stopping com.jianji.ime ... : from pid 8329`（8329 = ZUI 桌面/最近任务进程）。本机对 IME 强制停止 → 默认输入法复位。
  ZUI 会**忽略 excludeFromRecents**：从设置页前台直接呼出最近任务时当前任务必被展示（从桌面进则确实隐藏）。
- 修复（对齐搜狗：后台根本没有卡片）：
  - `SettingsActivity.onUserLeaveHint()` → `finish()`：离开设置页（回桌面/呼出最近任务/切走）即销毁，任务不存在 → 无卡片；
  - 「开源许可」同任务跳转用 `navigatingToLicense` 豁免；`LicensesActivity.onUserLeaveHint()` → `finishAffinity()`。
- 实机验证：设置页前台直接呼出最近任务 → 无卡片、输入法保持、进程存活；随后划屏无误杀；许可页进出正常。
- 版本 0.1.3（versionCode 4）；已覆盖安装到手机。
  交付副本：`D:\Fish\Documents\简记输入法-v0.1.3.apk`（7,686,131 字节，
  sha256 `8b62927dbb6176d826789c4a3e8fb51aba47f212623778ea9a218c1b278aea91`）；旧 0.1.2 副本已删。

### GitHub 公开仓库（2026-10-18 晚）
- 仓库：**https://github.com/SwayFishLK/jianji**（PUBLIC，main，GPL-3.0-or-later）。
- 首个提交 `1e82035`「简记 0.1.2 初始提交」：99 个文件 / 16.5MB（源码 + 词库 + 文档）。
- **Release v0.1.3**：https://github.com/SwayFishLK/jianji/releases/tag/v0.1.3
  （附件 `jianji-v0.1.3.apk`，7,686,131 字节，sha256 与交付副本一致；v0.1.2 因修复不完整已删除）。
- 已排除（`.gitignore`）：`keystore/`、`keystore.properties`、`local.properties`、`.dev-env/`、`reference/`、`build/` 等；
  AGENTS/PROGRESS 中的手机序列号已脱敏。
- 补充：`LICENSE`（GPL-3.0 全文，取自 `assets/legal`）、`.gitattributes`（LF 统一）；gh CLI 已登录（SwayFishLK，凭据在系统 keyring）。
- 推送：`git add -A; git commit -m "..."` → `git push`（repo 内已配代理 + 凭据助手，直接可用）。

## 当前里程碑：M5.1 收尾（设置页改版 / 候选字体跟随系统 / 应用图标，均已上机；待用户验收）

### M5.1：设置页卡片风格 + 候选字体跟随系统 + 应用图标 B（2026-10-17 深夜二轮，已上机验证，待用户验收）

用户当晚拍板三件事：设置页照参考风格改成卡片式（不用照抄）；候选字体 = 跟随系统；应用图标 = B 墨底蓝简。

**1) 设置页（与许可页）改卡片风格**（新增共用样式 `ImUi.kt`，两页重写；截图 t50/t51/t52/t53）
- 版式：顶部「← 设置」标题栏（返回箭头 = `ic_nav_back` 矢量）；分区标题（accent 13sp）：学习语言 / 按键 /
  常用语 / 剪贴板 / 关于；**每行一张 22dp 圆角卡片**（#1E232C 底、行间 8-10dp），行内 = 左侧 40dp 圆形图标泡
  （accent 15% 底 + 浅蓝图标）+ 标题 16sp / 副标题 12.5sp + 右侧控件（Switch / 值文本 + chevron / 危险文本）；
  组后说明小字（12sp 灰）。
- 图标全部自绘矢量（stroke 风，`res/drawable/ic_*.xml`）：ic_learn（A文）/ ic_sound / ic_vibrate / ic_phrase /
  ic_clip / ic_doc / ic_info / ic_nav_back / ic_chevron。
- 功能不变：学习语言改为「值文本 + 点开单选弹窗」（英语/日语/不显示）；声音/振动 Switch；常用语 输入+添加
  （药丸按钮）+ 列表（行右侧「删除」）；剪贴板 = 「当前记录 N 条」副标题 + 「清空」（红色文本，即时清 + toast）；
  关于 = 版本行（release「简记 0.1.0」/ debug「（调试版）」）+ 「开源许可与署名」chevron 行进许可页。
- 许可页同步卡片风格：每份文本一张卡片，正文可长按选中；OFL-1.1 已从清单移除（不再随包分发字体）。

**2) 候选字体 → 跟随系统**（用户选定）
- 从 APK 撤掉 `assets/fonts/jianji-kai.ttf`（3.68MB 压缩体积），删除 `Fonts.kt` 与 CandidateBar /
  CandidateGridPanel / JianjiImeService 的全部字体接入点 → 候选回归系统字体（截图 t56/t58）。
- 字体子集管线 `tools/fontgen/` 与上游字体 `reference/fonts/` 保留留档（将来若要自带字体，一条命令重建再接线即可）；
  NOTICE / 许可页同步删除「随包字体」段与 OFL 文本。关于页文案「候选字体跟随系统」。

**3) 应用图标 = 方案 B「墨底蓝简」**
- 生成器 `tools/icon/gen_icon.py`（PIL 超采样渲染，字形 = 文楷「简」）：传统图标 ic_launcher(_round).png
  （48/72/96/144/192，墨底 #17191E + 蓝简 #5B8DEF）+ 自适应前景 ic_launcher_foreground.png（108~432px，字形
  0.43 画布，留遮罩呼吸感）；`mipmap-anydpi-v26/` 自适应 XML + `colors.xml.ic_launcher_background`；
  清单加 `android:icon` / `android:roundIcon`。真机验证：应用信息页图标遮罩内比例合适（截图 t55-icon）。
- 首次生成字形 0.50 画布，真机目测偏大贴边 → 调 0.43 重出（经验值：本机 launcher 遮罩 ≈ 0.6 画布）。

**体积**：release 10.58 → **7.33MB**（debug 9.45MB）；装 release 覆盖 debug（同密钥，数据保留：剪贴板 3 条）。
注意：AGP 增量打包会把旧条目留在 APK 文件里造成 ~3.5MB 虚胖，**报体积前先 `gradle clean`** 再构建。

**收尾**：真机已装 release；手机端开发痕迹清理（/sdcard 截图与转储、`pm clear` 测试期数据）见「下一步」第 10 条。
坑：`pm clear com.jianji.ime` 会把默认输入法复位到 AOSP 键盘 —— 清完要 `ime enable` +
`ime set com.jianji.ime/.JianjiImeService` 再验键盘（已复验：候选/译文/开关全正常）。

**重复图标清理 + 交付副本（当夜续）**：桌面出现 3 个「简记」图标 = 开发期反复「卸载→重装」时联想桌面
自动补的重复快捷方式。处理：整体卸载（3 图标 + 测试数据全清）→ 重装同一 release（同签名）→ 桌面仅剩
1 个（第 5 页右下、Dock 上方）；`ime enable/set` 重设默认输入法 + 打字复验（nihao → 你好「int. hello · int. hi」）✓。
**规矩：以后升级一律覆盖安装（`install -r`）不会再加图标；只有「卸载后重装」才会补。**
交付副本已放 `D:\Fish\Documents\简记输入法-v0.1.1.apk`（7.33MB，
sha256 4d5c654f2855219f61905165162c4a6af56f57b87f58d9a0cfadc060d378a38a）。
（v0.1.0 旧副本已删；0.1.1 = M5.1b 两处修复版，versionCode 2）

### M5.1b：候选栏左划复位 + 剪贴板删除复活 双修（2026-10-18 凌晨，已上机验证）

用户报两个问题：① 候选栏向左划不跟手、划完复位到最前；② 剪贴板里删除/清空的内容会被自动抓回来。均已定位根因并修复。

**1) 候选栏左划复位：根因 = 译文加载回调的无限反馈循环（顺带修掉一个长期性能 bug）**
- 日志实证：拖动链本身正常（`drag dx=-150 → scrollX=150`，onDraw 按 scrollX 绘制），但 `setItems` 每 ~21ms 被调用一次并无条件 `scrollX = 0` → 任何滑动位置 20ms 内被清零。
- 堆栈定位（R8 mapping 反查）：循环源 = `ensureGloss()` 的加载完成回调（`mainHandler.post { … if (pinyin.isNotEmpty()) updateComposing() }`）。
  守卫缺「已加载好」判断：原 `lang == "off" || glossFailedLang == lang || glossLoading == lang` —— 成功加载后 `glossLoading = null`、
  `glossLang = lang`，下一次 `updateComposing → ensureGloss` 守卫不拦 → 又起加载线程 → 回调再 updateComposing → 无限循环（~45Hz），
  每次重建 100 项候选、重新 measure 文本并清零滚动。该循环从译文异步加载上线起一直存在（耗电/CPU 浪费 + 用户感知的滑动复位）。
- 修复：① 守卫加 `glossLang == lang`（已加载好直接返回）；回调里 `if (glossLoading == lang) glossLoading = null`（只清自己的状态，
  避免旧语言的在途回调踩掉新加载）；② `CandidateBar.setItems` 内容未变直接 return（不重置 scrollX、不重绘；双保险 + 省掉高频重建）。
- 上机验证（logcat + 截图 t95~t99）：打字后 3s 窗口内 updateComposing 仅 1 次（修复前每秒几十次）；拖动 `scrollX 100→200→300` 累积、
  第二段滑动从 300 续到 647，全程 0 次 setItems、0 次复位；截图确认候选左移（吖/爱/爱你/安）且松手保持。

**2) 剪贴板删除/清空后自动复活**
- 根因：跳过记录只存内存（`skipText`）且只有「清空」路径写入；左划单条删除完全不记录，进程重启即失效 →
  打开面板 / 键盘弹出时 `capture(force=false)` 又把系统剪贴板内容抓回列表。
- 修复：`KEY_SKIP` 持久化到 Prefs；纯逻辑 `shouldSkip(t, force, skip) = !force && t == skip`（JVM 单测覆盖）；
  新增 `removeHistory()`（删条 + 若该条仍在系统剪贴板则记 skip）与 `currentClipboardText()`；`clearHistory` 同步改写；
  `ClipboardPanel.deleteCurrent()` 改调 `removeHistory()`。force=true（剪贴板真变化）路径不受影响，新复制照旧入列。
- 上机验证（t103~t107）：左划删除 → 「共 0/50 条内容」→ 关闭面板重开 → 仍 0 条（不复活）✓；常用语页无残留 ✓。

**探针清理**：验证用的 JJDBG 日志已全部移除并重建 release 覆盖安装（同签名，数据保留）；`adb logcat -c` 已清缓冲。
版本号 bump 到 **0.1.1**（versionCode 2，关于页同步显示「简记 0.1.1」），交付副本已更新为 `简记输入法-v0.1.1.apk`。
坑：`am force-stop com.jianji.ime` 会把默认输入法复位到 AOSP 键盘（本机系统行为），需 `ime enable + ime set` 恢复；
覆盖安装（`install -r`）不会。

### M5：打磨（2026-10-17 深夜，已上机验证；字体选型与应用图标已由 M5.1 落定）

**1) 候选字体子集化（霞鹜文楷 Screen →「Jianji Kai」，已接入并真机验证）**
- 管线在 `tools/fontgen/`：`charset.py` 从 `dict.bin` 提取字符集 → `charset.txt`（9,912 字）；
  `build_font.py <字体.ttf> <charset.txt> <输出.ttf> "Jianji Kai"` 做子集化 + 改名。
  上游字体与原始 OFL 存 `reference/fonts/`（不入库）。
- 口径：只取词频 ≥ 1 的词（93,224/132,884）∪ 全部 glossary（en/ja/zh）∪ english.bin ∪ extras（ASCII / 中文标点 / 全角）；
  词频 0 的生僻字（CJK 扩展 A/B ~1.6 万，上游字体本身也无字形）不进子集 —— 设备上走系统 fallback
  （真机已验证：`eng`→鞥 正常渲染，无豆腐块）。全量字符集（2.87 万字）测算 17MB，弃用。
- OFL 合规：子集属「修改版」，保留名 `霞鹜/霞鶩/落霞孤鹜/落霞孤鶩/LXGW` 不可用（附加许可只覆盖网页字体）→
  子集 name 表整体改名「Jianji Kai」，版权/商标/许可记录保留。
- 产物 `app/src/main/assets/fonts/jianji-kai.ttf`：5.14MB raw（APK 内 3.68MB）、字形 10,140、cmap 9,911（仅 ✕ 缺）。
- 接入：`Fonts.kt`（启动时后台加载，就绪后重铺候选）；`CandidateBar` / `CandidateGridPanel` 的 `syncFont()`；
  键盘按键、剪贴板/表情面板、设置页仍用系统字体（避免任意用户文本缺字形）。真机截图：候选栏（t37）、更多候选网格（t41）。
- 对比图 `docs/mockups/font-compare-m5.png`（系统 Noto / 文楷 GB / 文楷原版 / 悠哉 4 版）；
  **当前接入 = 文楷 GB 字形，最终选型待用户确认**（换字体：对上表字体重跑 build_font 一条命令，再重建即可）。

**2) NOTICE 署名 + 应用内许可页（已上机验证）**
- 根 `NOTICE` + `app/src/main/assets/legal/`：NOTICE.txt（署名汇总）、GPL-3.0-or-later.txt、OFL-1.1.txt、
  UNICODE-3.0.txt、THIRD-PARTY-NOTICES.txt（THUOCL / CSpell / typos / ESDB 声明）、APACHE-2.0.txt。
- 新增 `LicensesActivity`：设置页「关于」区「开源许可与署名」按钮进入；纯代码布局、文本可长按选中；截图 t44。
- 署名内容：青简项目（词库/释义/英文/表情面板，GPL-3.0-or-later，上游来源逐项列出）+ 霞鹜文楷 Screen 子集
  （OFL，含 Klee 派生版权行与保留名清单）+ AndroidX / Kotlin / coroutines（Apache-2.0）。

**3) 正式签名 + release APK（10.58MB，装机验证通过）**
- `keystore/jianji-release.jks`（RSA 4096、有效期 10000 天、alias `jianji`）+ `keystore.properties`；
  **两者都已 gitignore，务必自行备份（丢失后已安装的应用无法覆盖升级）**。
- `app/build.gradle.kts`：signingConfigs 从 properties 读取；**debug 与 release 共用同一密钥**
  （两者可互相覆盖安装，真机调试不丢数据）；只有换机/换密钥才需要卸载重装。
- `app-release.apk` = **10.58MB**（R8 + 资源收缩；debug 为 12.91MB；目标 ≤25MB ✓）；`apksigner verify` 通过。
- 装机流程：旧 debug 签名包卸载前 `run-as` 备份 prefs → 装新 debug（`ime set` + 恢复 prefs）→ 覆盖装 release
  （同一密钥，数据保留）。release 上打字「nihao」候选文楷 + 译文正常（截图 t48），设置页（t49）/许可页正常；
  进程内存 PSS ≈ 26.6MB。
- 「关于」文案按构建区分：release 显示「简记 0.1.0」，debug 显示「（调试版）」。

**M5 遗留项 → 已在 M5.1 落定**：用户选定 ① 候选字体 = **跟随系统** ② 应用图标 = **方案 B 墨底蓝简**；
设置页同步改为用户提供的卡片风格（见上节 M5.1）。备忘：M5 时 release = 10.58MB（含 5.14MB 文楷子集），
M5.1 移除字体后 = **7.33MB**。

### M4.3：液态玻璃 2.0 试作 → 按用户决定移除（2026-10-17 深夜，已上机验证）

背景：用户对 M4.2 的模拟玻璃观感不满意，要求参考 GitHub 上的液态玻璃项目重做质感。
调研结论（Kyant0/AndroidLiquidGlass、QmDeve/AndroidLiquidGlassView、Prismal、Cloudy 等）：真折射型液态玻璃都依赖
API 31+ `RenderEffect` / API 33+ AGSL，且必须采样「窗口下层」的实时画面；输入法窗口拿不到下层内容、本机（API 32，
无系统背景模糊）也不满足 → 物理上做不出真折射，只能继续用 Canvas 多层叠加模拟。

试作的模拟配方（`GlassArt.kt` 材质引擎，约 450 行）：深度阴影（预烘焙位图，0.5 分辨率 + `BlurMaskFilter`）
→ 垂直渐变底 → 收窄镜面高光穹顶 + 窗光细带（glint）→ 上亮下暗 rim 描边 → 磨砂颗粒（noise；曾误用
`java.util.Random.nextInt(from, until)` —— API 32 无此方法直接崩溃，改用 `kotlin.random.Random`）→ 顶部液面亮线
→ 斜向窗光带；按下再叠跟手径向光晕 + 1.025 鼓起动画（80ms 进 / 150ms 回）；渐变/阴影位图按（槽位 + 尺寸）缓存、
`ImTheme.glassGen` 整体失效，绘制路径零逐帧分配。键帽、服务底板（`GlassSlabDrawable`）、三个面板、剪贴板卡片、
工具按钮、长按备选条全部玻璃化，真机截图逐项验证通过。

**用户最终决定：效果仍不满意，移除液态玻璃，只保留最初的「方案 A · 墨夜」。** 回退内容：
- 删除 `keyboard/GlassArt.kt`；`ImTheme.kt` 重写为单一墨夜调色板（去掉 Tint / glass / 全部材质字段 / glassGen）；
- `Prefs` 删除 KEY_THEME / KEY_GLASS_TINT / KEY_GLASS_ALPHA 及访问器（**已存的旧偏好值不再读取**）；设置页删除整个「外观」区；
- `JianjiImeService`：底板回 `setBackgroundColor(ImTheme.windowBg)`，删除 `applyTheme()` / `applyWindowGlass()` 与主题 pref 监听分支；
- `KeyboardView` 回纯平色（删除按下动画 pressAnim / 跟手光晕 / 指针坐标）；三个面板回平色整块填充；剪贴板卡片回平色；
  工具按钮 / 收起键盘键去掉按下光环；长按备选条回平色 + 描边。
- M4.2b/c 的交互修订（剪贴板盖图标行、表情 ‹ 返回、收起重开回字母页）全部保留，不受影响。
- 真机截图验证：字母页 / 剪贴板面板 / 表情面板 / 设置页，观感与 M4.2 时期「墨夜」一致。

**以后再做（用户语）时的留档** —— 模拟配方参数（当时真机观感已调好的值）：
深色 keySheen 34 / keyPress 66→38 / keyPressGlow 110 / slabSheen 20 / slabMeniscus 112 / slabBand 14；
浅色 keyGradBottom 190 / keyRimBottom 26（黑）/ keySheen 150 / slabSheen 80 / slabMeniscus 245 / slabBand 40 /
keyPressGlow 170。槽位：SLOT_KEY / SLOT_FN / SLOT_ACCENT / SLOT_SLAB / SLOT_PANEL / SLOT_CARD / SLOT_POPUP；
注意：真折射不可行（见上），若再做仍是绘制模拟，需在「质感 vs 原生感」间取舍（用户当时评价：不够好，宁可不要）。

### M4.2 实现记录（2026-10-17 晚）

用户 M4 手测反馈四条，全部完成：

**① 剪贴板面板搜狗化（`ClipboardPanel.kt` 整页重写）**：
顶栏 = ‹ 关闭 + 分段标签 [剪贴板|常用语] + ⚙ 设置（回调 onClose/onSettings）；状态行 = 「共 N/50 条内容」+ [清空]（**点一次变「确认清空」红色，再点生效**，2.5s 超时还原）；
卡片列表 = 12dp 圆角卡片、15dp 文字，**超过两行截断出 ▾，点 ▾ 展开/再点收起**（展开状态跨刷新保留）；
**左划卡片露出圆形操作按钮**（剪贴板页 = [＋ 存为常用语][🗑 删除]，常用语页 = [🗑 删除]），滑动超过 45% 或左甩自动弹开，
松手回弹（ValueAnimator 150ms）；**长按菜单彻底移除**；操作后卡片自动收回 + 状态行 flash 提示（已存为常用语/已删除/已清空）；
空态分别引导（剪贴板：复制的文字会出现在这里，左划可存为常用语或删除 / 常用语：在剪贴板里左划一条，点 ＋ 存为常用语）。
行高 = max(46dp, 文字高+22dp)，纵向滚动 + 惯性照旧。
服务：`keyboardView.visibility = INVISIBLE`（面板展开期间，保留占位窗口高度不变）——半透明面板后面不再透出键帽，面板观感干净。

**② 液态玻璃主题（新 `ImTheme.kt`）**：默认主题（Prefs 无值时 = glass）。**—— 已按用户要求在 M4.3 移除，见顶部记录**
- 全部自绘视图颜色从 `ContextCompat.getColor` 静态值改为每帧读 `ImTheme.xxx`（KeyboardView 的 8 色 getter 化、
  CandidateBar / CandidateGridPanel / EmojiPanel / ToolButton / HideKeyboardButton / ClipboardPanel）；KeyOptionsView 备选条 5 色同样主题化。
- 玻璃做法（真机 `ro.surface_flinger.supports_background_blur` 为空 = **无系统模糊** → 模拟玻璃）：
  半透明窗底 + 键帽高光描边（`ImTheme.keyHighlight`，KeyboardView 每键描 1.2dp 内圈白线）+ 层次化白/黑膜；
  窗口置透明（`applyWindowGlass()`：`setBackgroundDrawable(ColorDrawable(0))`），
  Android 12+ 仍尝试 `setBackgroundBlurRadius(26dp)`（不支持机型无害 no-op，支持机型自动叠真模糊）。
- 调色板：4 色调（石墨 #14171D / 雾蓝 #0E1826 / 月白 #F2F4F7 / 樱粉 #F7EDF2，浅色调自动切深色文字）；
  透明度 t∈0..100 → 窗底 alpha = 1 − 0.62·t（默认 t=30 ≈ 0.81），面板底比窗底更实（+39 alpha）保证内容可读。
- 设置页「外观」区：主题 radio（液态玻璃 / 墨夜）+ 玻璃颜色 4 选 + 透明度 SeekBar（实时显示百分比）；
  pref 监听（KEY_THEME / KEY_GLASS_TINT / KEY_GLASS_ALPHA）→ `Service.applyTheme()`：refresh + 底板重铺 + 全视图 invalidate + 窗口玻璃 → **输入法不重建、即时生效**（真机验证：月白+60% 即时变浅色键盘，采样像素与分析值一致）。
- 「墨夜」主题保留可切回（原配色 + 0 高光）；切换即时验证 ✓。

**③ 候选栏 ⚙ 图标 + 打字自动隐藏（`JianjiImeService.kt` + `ToolButton.kt`）**：
图标行改 **[📋 40dp][🙂 38dp][⚙ 40dp][˅ 44dp]**（⚙ 插在 🙂 与 ˅ 之间，点击 = 打开正式设置页，`ToolButton.Glyph.GEAR` 齿轮 + 8 齿）；
**打字中（pinyin 或 englishBuf 非空）自动隐藏 📋🙂⚙ 三个图标**，候选栏 rightMargin 168dp ↔ 50dp 联动，候选占满宽度；
空组句（未输入）时恢复显示；打字→删光全流程真机验证 ✓。

**④ Bug：删光输入后右侧仍显示「更多候选 ˅˅」**（用户反馈 #5）：
根因 = `clearComposing()` 没调 `updateRightButton()`（`updateComposing` 的空文本分支提前 return 绕过）——
修复 = `clearComposing()` 末尾补 `updateRightButton()`；删光后立即回到「˅ 收起键盘」✓。

**其它**：Debug 测试页「写测试剪贴板」按钮改为奇偶交替写「短文本 / 长文本（> 2 行）」，方便测卡片折叠/展开。
真机自测：面板开合/标签切换/左划＋🗑/展开▾/清空确认/点选上屏/表情面板/⚙ 打开设置/主题实时切换 全部 ✓；JVM 测试（ClipboardStore 6 + SentenceRanking 4）全绿。
注意：用户手测期间自行把外观调成了「石墨 + 透明度 60%」（prefs 实测），半透明玻璃下注意覆盖层的「透视残影」问题（见 M4.2b）。

**注（M4.3）：本节 ②「液态玻璃主题」已按用户要求整体移除（连同设置页「外观」区与 Prefs 三项），键盘回到单一「墨夜」；
M4.2b/c 两条交互修订保留。**

### M4.2b 手测两条修订（2026-10-17 晚，已上机自测，待用户手测）

用户 M4.2 手测反馈两条：

**① 剪贴板面板顶边正好盖住图标行**：`syncPanelHeight()` 对 `clipPanel` 特判 —— 高度 = 键盘高 + 候选区 56dp，
面板顶边正好落在 [📋][🙂][⚙][˅] 图标行的上沿，打开剪贴板时整行被盖住；
并且 `updateRightButton()` 在 `overlay == CLIP` 时把整行 `rightRow` 设为 INVISIBLE（**关键**：用户把透明度调高后，
纯靠"盖住"会透出图标残影 —— 半透明面板 alpha 199 时图标以 ~30% 亮度透出，实测像素验证）；
表情 / 更多候选面板不变（图标行照常显示）。

**② 表情面板右下角「‹ 返回键盘」+ 右上角改「收起键盘」**：
分类共 11 个（2×6 格空最后一格），在该空位画 ‹ 返回按钮（按下高亮，点 = 收起表情面板回键盘）；
表情面板打开时右上角按钮从「˄˄ 收起面板」改为「˅ 收起键盘」（`updateRightButton`：`overlay == EMOJI -> Mode.HIDE`，
点击走既有 HIDE 分支 `requestHideSelf(0)`）；分类占满两行时该返回按钮自动省去。
真机验证：剪贴板开→图标整行消失+面板顶上沿对齐 ✓；表情开→右下角 ‹ 键回键盘 ✓、右上角 ˅ 隐藏整键盘 ✓。

### M4.2c 手测第三条修订：收起键盘后再打开回到默认字母页（2026-10-17 晚，已上机验证）

问题：在数字页 / 符号页 / 表情面板打开状态下直接收起键盘，下次键盘弹出会从上次的状态「续开」；
预期是每次都从默认字母页打开（表情面板不残留）。
根因：**收起键盘后再打开同一输入框（焦点还在），框架只回调 `onStartInputView`、不回调 `onStartInput`** ——
而归位逻辑（⇧ 归零 / `page = LETTERS` / 清组句 / `setOverlay(NONE)`）原来只写在 `onStartInput` 里。
修复：抽出 `resetInputState()`，`onStartInput` 与 `onStartInputView` 都调用；归位时若仍有组句先 `finishComposingText()`（内联拼音落为普通文字）。
真机验证：数字页 → 收起 → 再打开 = 字母页 ✓；表情面板 → 收起 → 再打开 = 字母页、无面板残留 ✓；符号页 → 收起 → 再打开 = 字母页 ✓。

### M4 实现记录（2026-10-17）

**右侧图标行**：候选区最右侧一排 [📋 剪贴板][🙂 表情][˅/˅˅/˄˄]（40/38/44dp），全部画在词线 0.70h（`ToolButton.kt` 新类 + 原 `HideKeyboardButton`）；
候选栏 rightMargin = 128dp；有候选时 ˅˅ 更多候选照旧，剪贴板/表情展开时对应图标点亮 accent 色、模式键变 ˄˄。

**覆盖面板互斥**：`Overlay` 枚举（NONE/GRID/CLIP/EMOJI）；`setOverlay()` 统一管理三个面板显隐 + `syncPanelHeight()` 对齐键盘高度；
点 📋/🙂 互切、再点同图标收起；**按任意键自动收起剪贴板/表情面板**（handleKey 顶部）；更多候选面板照旧跟随候选刷新。

**剪贴板面板（`ClipboardPanel.kt`，M4.2 已整页重写为搜狗风格 —— 见上节）**：盖键盘区，顶栏「剪贴板」+ [清空][设置]；
内容 = 常用语区（置顶）+「最近复制」区；点一条 = 收尾组句后上屏并收面板；长按 = 底部操作条（存为常用语/删除/取消，按下高亮）；
清空 = 二次确认条（清空剪贴板记录/取消）；空态提示「剪贴板是空的 · 复制的文字会出现在这里，长按可存为常用语」。

**表情面板（`EmojiPanel.kt`）**：11 分类双行标签（常用在最前，含「颜文字」用 3 列小字自动缩放），8 列网格纵向滚动，点选连点可多次上屏；
数据 `emoji.bin`（"JJM1"：11 类 2236 条，22KB）由 `java tools\dictgen\EmojiGen.java <catalog.tsv> <emoji.bin>` 从青简 panel/catalog.tsv 生成；
`engine/EmojiDict.kt` 纯 JVM 载入。

**剪贴板存储（`ClipboardStore.kt`）**：历史上限 50 / 常用语上限 100 / 单条 1000 字；编码 = 每条 Base64（UTF-8）一行（兼容换行内容）；
入历史去重上移；`capture(context, force)` — 开键盘视图时 force=false（跳过刚被清空的那条 `skipText`）、剪贴板监听回调 force=true；
监听在 `onCreate` 注册（Android 10+ 只在允许时机回调，稳妥路径 = 打开面板/开键盘视图时主动抓一次）。

**正式设置页（`SettingsActivity.kt`）**：主题 `@android:style/Theme.Material.NoActionBar`（不用 Theme.Black —— 老主题会把 Switch 画成「开/关」文字）；
内容 = 学习语言 radio（英语/日语/不显示）+ 按键声音/振动 + 常用语管理（输入框 + 添加 + 列表删除）+ 清空剪贴板记录 + 关于（版本/署名）；
三个入口：桌面图标（MAIN/LAUNCHER）、输入法选择器齿轮（service `android:settingsActivity`）、剪贴板面板顶栏「设置」。
Debug 测试页精简：去掉声音/振动/语言控件（挪进设置页），保留引擎/释义自测 + 「打开设置页」「写测试剪贴板」按钮。

**JVM 测试**：新增 `ClipboardStoreTest`（6 例：编解码往返/坏行容忍/去重上移/上限截断/normalize 规则），与 SentenceRankingTest 同跑全绿。

**真机自测清单（2026-10-17，adb motionevent 全过）**：图标行渲染 ✓；剪贴板面板抓取（监听 + 打开时补抓）✓；
点选上屏并收面板 ✓；长按菜单（高亮行 + 三键）✓；存为常用语置顶 ✓；删除常用语 ✓；清空二次确认 + 空态 ✓；
表情面板网格/分类切换/连点上屏 ✓；颜文字 3 列 ✓；设置页三入口 + 无标题栏 + 材质开关 ✓；
设置页里用简记键盘打「好的」→ 添加常用语 ✓；更多候选面板回归（˅˅/点选上屏）✓。

### M3.6 收尾备注

2026-10-17 用户手测：**通过**（「非常完美了」）。整句输入 M3.6 关闭，进入 M4。

### M0 清单

- [x] `.dev-env/` 目录结构（git/jdk/gradle/android-sdk/gradle-home/downloads/tmp）
- [x] 下载命令行工具 → `downloads/commandlinetools-win.zip`（154,957,218 B）
- [x] 解压 → `.dev-env/android-sdk/cmdline-tools/latest`（内含新版 `android.exe` CLI）
- [x] 下载并解压 JDK 21（清华 Adoptium 镜像）→ `.dev-env/jdk/jdk-21.0.12.1+1`
- [x] 下载并解压 PortableGit 2.56.0.2（npmmirror）→ `.dev-env/git`
- [x] 下载 Gradle 9.8.1（腾讯镜像续传）→ `downloads/gradle-9.8.1-bin.zip`
      SHA256 校验：`dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f`
- [x] 解压 → `.dev-env/gradle/gradle-9.8.1`
- [x] 安装 SDK 组件（新 CLI：`android.exe sdk install`）：
      `platforms/android-37.0`、`build-tools/37.0.0`、`platform-tools`
- [x] 工程骨架文件（见下）
- [x] `.dev-env/env.ps1`（JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME / PATH）
- [x] 首次构建通过（2026-10-16，1m11s）：`gradle :app:assembleDebug`
      —— 关键坑：**AGP 9 内置 Kotlin，不要加 `org.jetbrains.kotlin.android` 插件**（加了直接报错）
- [x] 真机安装验证空壳 IME（2026-10-16，Lenovo L71091 / Android 12，`ime enable` + `ime set` 成功）

### M1 清单（已完成：v2 → v3.2 均用户手测通过）

- [x] 方案 A「墨夜」配色落为 `res/values/colors.xml`
- [x] 数据模型与布局：`keyboard/KeyDef.kt`、`keyboard/KeyboardLayouts.kt`（26 键字母页 + 符号页 + 长按备选表）
- [x] 自绘键盘：`keyboard/KeyboardView.kt`
- [x] 主服务重写：`JianjiImeService.kt`（字母上屏、⇧、⌫、回车联动编辑器动作、空格、标点、音效 + 振动）
- [x] 真机渲染验证（2026-10-16/17，adb 截图确认与方案 A 一致）
- [x] Debug 测试页（仅 Debug 包，桌面「简记测试页」）+ 声音/振动开关（写 Prefs，即时生效）
- [x] **v2 迭代（2026-10-17，按用户反馈）**：
  - 多指独立跟踪 + 键缝命中容差（修「快速连打丢键」）
  - 长按字母弹备选条（q→1 … m→”，，→。、），显示在键盘**上方 56dp 预留区**（M2 起给候选栏用）；
    实测：`input motionevent` 注入长按 t → 高亮 5 → 松手上屏 ✓
  - ⌫ 上划 = 清空输入框；长按 = 连删；⇧ 单击一次性大写 / 350ms 双击锁定
  - 回车键改「↵」图形；反馈开关基础设施（Prefs；正式设置页 M5）
- [x] 测试踩坑：`input swipe` 被系统禁注入（SecurityException），`input motionevent` 可以；
      **不要 `am force-stop` 输入法包**（默认输入法会被系统踢走）；
      **重装 APK 后输入法不自动重绑**（mInputShown=false）→ `input keyevent 4` 关测试页再 `am start` 重开即恢复
- [x] 用户手测 v2：**通过**（2026-10-17：「没有出现任何问题，一切都很好」）
- [x] **v3 迭代（2026-10-17，按用户截图的搜狗参照）**：
  - 字母页改搜狗式：字母键上方小字提示长按备选（q1…p0；a~ s! d@ f# g% h“ j” k* l?；z( x) c- v/ b' n; m：）
  - 回车改**自绘矢量图标**（更大更粗）；新增自绘图标：∧∨ 翻页、🔒 锁（⇧/⌫ 仍用字体符号）
  - **数字页与符号页分开**：数字页 %/1-3/⌫、//4-6/.、-/7-9/@、/+/返回/0/符/⏎（5 列对齐）；
    符号页 4×4 网格 + 右列 [返回][∧][∨][🔒] + 底部分类标签栏（1.常用/2.中文/3.英文/4.网络/5.数学/6.更多，各 1-2 页）
  - 长按备选条改为**贴着按键正上方显示**（根 FrameLayout 绝对定位子视图；第 1 行键的弹层落在 56dp 预留区，
    其余行盖在上方键上，与搜狗一致；不再依赖预留区）
  - 弹层为 [大写, 备选, 小写] 三格（字母键），默认高亮备选；标点键为 [本字, 备选]
  - 符号页选完符号自动回字母页（除非点 🔒 锁定）；数字页「返回」→字母页，符号页「返回」→数字页
  - 实测（adb motionevent 注入）：长按 r →「R 4 r」高亮 4 → 上屏 4 ✓；长按 f →「F # f」✓；
    123→数字页（列对齐）✓；符→符号页 ✓；切「3.英文」✓；点「！」上屏并自动回字母页 ✓
- [x] **v3.1（2026-10-17，用户 v3 手测反馈）**：
  - 左下角键改回「符」（直接进符号页）；「收起键盘」移到**候选区最右侧**（新类 `keyboard/HideKeyboardButton.kt`，
    自绘 ˅ 箭头，点击 `requestHideSelf(0)`）
  - 回车箭头方向修正：此前箭头画反（像指向右），现为标准 ⏎（右竖线→转角→箭头朝左）
  - ⇧ 改为**无时间窗的升级式状态机**（对齐搜狗官方：单击 = 下一个字母大写、自动回落；未输入时再点一下 = 大写锁定；
    锁定单击 = 解锁）。旧实现依赖 350ms 双击时间窗，慢速双击会失效——这就是用户说"搞错了"的点
  - 符号页「返回」→ 字母页（原为回数字页）；🔒 锁定实测：锁定后点符号停留符号页、锁图标蓝色高亮 ✓
  - Debug 测试页：点输入框重新唤起键盘（收起后恢复用）
  - 实测（adb motionevent）：单击 ⇧ → 打 a 上屏 "A" 并自动回落 ✓；连点两下（300ms 间隔）⇧ → 锁定后连打 s d 上屏 "SD" ✓；
    点「符」进符号页 ✓；🔒 锁定态点「？」上屏且不跳页 ✓；收起 → 点输入框 → 键盘恢复 ✓
- [x] **v3.2（2026-10-17，用户 v3.1 手测反馈 5 点）**：
  - 字母键内文字上移：偏移 `centerY + 7dp` → `+ 3dp`（更居中的观感）
  - 键上小字提示（长按备选）调暗：新增 `key_hint_text = #8B93A3`（原与字母同用亮色，分不开）
  - 长按弹层收窄：`minCellW 53→42dp`、内边距 `15→11dp`、格间距 `4→3dp`
  - 收起键盘 ˅ 箭头下移 5dp（视觉更贴键盘）
  - **符号页去掉锁定键**：右列第 4 格 🔒 → ⌫（可长按连删/上划清空）；选完符号**不再自动跳回字母页**，
    必须手动点「返回」。连带删除：`KeyAction.SYMBOL_LOCK`、`KeyIcon.LOCK/HIDE`、`symbolLocked`、commitText 自动回退逻辑
  - 实测（adb motionevent）：字母页观感 ✓；长按 f 弹层变窄 ✓；符→符号页右列为 [返回][∧][∨][⌫] ✓；
    点「？」上屏后停留符号页 ✓；点 ⌫ 删掉刚上屏的字 ✓；返回→字母页 ✓
- [x] 拼音内联 + 双行候选栏骨架 → 已在 M2 完成（见下）

### M2 清单（2026-10-17：引擎 + 候选栏已上机并 adb 实测，用户手测通过）

- [x] **数据管线** `tools/dictgen/DictGen.java`（JDK 21 单文件直跑，无需构建）：
      `java tools\dictgen\DictGen.java reference\qingjian-repo\assets\lexicon\dict.tsv app\src\main\assets\dict.bin`
      —— 青简 dict.tsv（词 / 音节 / 词频）→ 全拼连写 + 简拼 + 两套排序索引 → 二进制
      **成果：132,884 词条、413 音节、dict.bin = 4,476,000 B（542 ms）**；零频生僻字 39,660 条也保留（可打）
      `--lookup <dict.bin> <拼音>` 可在命令行近似抽查（不带排序权重）
- [x] **dict.bin 格式（大端）**：magic "JJD1" + count + syllableCount + 四个 blob 长度；
      随后依次：全拼 blob + 偏移数组（按全拼升序、同拼按词频降序）→ 词 UTF-8 blob + 偏移 →
      词频数组 → 简拼 blob + 偏移 + 简拼序→主表下标 → 音节表（查询切分用）
- [x] **引擎**（`app/src/main/java/com/jianji/ime/engine/`）：
      `JianDict.kt`（载入 + 零分配二分/前缀扫描原语）、`PinyinEngine.kt`（接口 + Candidate）、
      `FlatPinyinEngine.kt`（三类候选 + 排序 + 用户词频加分）。真机自测：**载入 38 ms、单次查询 0–5 ms**
- [x] 候选三类来源：①全拼前缀（含残缺末音节补全：nih→你好）②简拼前缀（≥2 字母：nh→你好，完全一致有加分）
      ③**前缀回退**（q 按音节切分，边界处整段精确匹配：kaifazhe→开发者 / 开发 / 开）
- [x] 排序分（常数集中在 `FlatPinyinEngine.kt`，上机后一处可调）：
      精确全拼 = ln(1+词频)+2.0；全拼补全 = ln(1+词频)−0.5；简拼 = ln×0.45（完全一致 +0.5）−0.5；
      回退 = ln×(0.45+0.06×已吃前缀长)；用户词频加分 = min(次数,20)×0.3
      （教训：补全词权重不能盖过精确词，否则 xian→「想」会压住「县先线」）
- [x] **用户词频学习**：点选/首选上屏即记次数 → `Prefs.user_words`（word\tcount 行式存 SharedPreferences）
- [x] **候选栏** `keyboard/CandidateBar.kt`（自绘 + OverScroller）：双行候选格预留（M2 只画词面，M3 填译文）、
      横向拖动 + 惯性滚动 + 点选上屏、**首选蓝色药丸高亮**（对齐视觉稿 .cand.sel）
- [x] **服务接线** `JianjiImeService.kt`：字母→拼音组句（内联 `setComposingText`，上限 24 字母）；
      「中/英」= 中英模式（英文模式字母直通；键面高亮 `KeyboardView.langEnglish`）；空格 = 首选上屏；
      标点 / 数字 / 长按备选 = 先上屏首选再输符号（搜狗行为）；回车 = 组句中原样上屏拼音字母（无组句时编辑器动作）；
      ⌫ = 先删组句（空则常规删字）；⌫ 上划 = 清空输入框（连同组句）；换输入框 / 失焦清组句并结束内联态
- [x] 引擎异步预载（`onCreate` 起线程读 assets，`@Volatile`）→ 首键零等待
- [x] Debug 页新增「引擎自测」按钮：真机跑 10 组查询打印前 8 候选 + 载入耗时
- [x] adb 实测（截图确认）：nihao→[你好|你|你好好…] 点选上屏 ✓；nh+空格→你好 ✓；shi+回车→原样 "shi" ✓；
      ni+⌫→组句退到 "n"（候选跟着更新）✓；中/英→abc 直通 + 键面蓝色 ✓；kaifazhe→开发者 开发 开 ✓；
      候选栏横滑 ✓；首选药丸 ✓；学习落盘（你好×2、你×1，`run-as` 查 prefs）✓
- [x] **用户手测反馈 5 条已全部修复并上机验证**（见下节 M2.5）

### M3.5：二轮手测 6 条修订 + 英文模式候选（2026-10-17 晚二轮，设备自测 + adb 真机验证，待用户手测）

用户 M3 手测后的 6 条反馈：

- [x] ① 无译文候选与有译文候选不在同一水平线 → 单行候选改画在**词线 0.70h**（与双行格的大字同行，不再居中）：
      `CandidateBar` / `CandidateGridPanel` 的无译文分支都改
- [x] ② 中文候选字略大 → 候选栏词面 19→18dp、网格 17→16dp（「小一点点」）
- [x] ③ ⇧ 状态可视化（自绘矢量箭头 `KeyboardView.drawShiftIcon`）：未激活 = 灰描边；
      **单击大写待用 = 蓝色描边**；**锁定 = 整块涂蓝**（原来两态都是字体符号 ⇧，几乎分不清）
- [x] ④ 回车图标改「整体涂白的白色小鱼」（`drawIcon` ENTER 分支重写：贝塞尔鱼身 + 鱼尾 + 挖空蓝眼；
      按下时眼睛用 accent_pressed 色）
- [x] ⑤ **英文模式候选**（对齐搜狗/青简）：
  - 英文模式字母不再直通上屏，而是进组句区（内联显示）出候选：**第 0 个 = 原样输入的字母**，
    往后 = 前缀联想词（按词频降序，最多 100 个，候选栏可横滚 / 网格可翻页）
  - 每格上方补**中文释义**（英→中表；第 0 个若恰是词也注，大小写不敏感回退）；
    空格 = 原样上屏 + 空格；回车 = 原样上屏（不换行）；标点 = 原样上屏再输标点；
    ⌫ = 删字母（空则常规删字）；切中/英 = 原样收尾；英文组句上限 24 字母
  - 数据管线 `tools/dictgen/EnglishGen.java`：青简 `english.tsv`（94,568 词，wordfreq Zipf×1000）
    → `english.bin` 1,549,100 B（"JJE1"：词 blob 按 UTF-8 字节序升序 + 偏移表 + 词频表）；`--lookup <bin> <前缀>` 抽查
  - 释义表：`GlossGen` 直接跑 `glossary-zh.tsv`（**不带 dict.bin = 全量**）→ `gloss-zh.bin` 1,715,800 B
    （44,924 词条 / 48,746 义项）
  - Android 端 `engine/EnglishDict.kt`：整块载入 + 字节序二分前缀起点 + 扫描 + 词频选择（同 GlossDict 风格）；
    服务端首次切英文时 `ensureEnglish()` 异步载入 english.bin + gloss-zh.bin，载好自动补画联想词
- [x] ⑥ 候选区右侧箭头（˅/˅˅/˄˄）**垂直位置对齐候选词线**：`HideKeyboardButton.alignToWordLine`
      （有候选时箭头组中心 = 0.70h；无候选收起键盘时仍居中），服务在 `updateRightButton()` 同步
      （M3.5b 再改：属性已删，三态恒 0.70h、无候选不再居中 —— 见下节 M3.5b）
- 验收（真机 adb 截图 + uiautomator dump，全部 ✓）：
  - nihao 候选「你好 / 你 / 你好好」：「你好好」无译文，与词线同高；˅˅ 与词线同高
  - ⇧ 三态截图放大确认（灰描边 → 蓝描边 → 实心蓝）；回车白色小鱼 + 挖空蓝眼 ✓
  - 英文 `hell` → [hell|hello|hellish|hellfire]，第 0 格带「n. 地狱」、hello「int. 你好」；
    点 hello 上屏 ✓；⌫ 删字母 ✓；空格上屏原样 + 空格 ✓；回车原样上屏不换行 ✓；
    乱打 `jddhjhaf` 只有原样一格（无联想）✓；˅˅ 网格同候选同释义 ✓；切中/英把 `hel` 原样收尾 ✓
  - 中文回归：nihao + 空格 → 「你好」✓
  - APK 9.8MB（原 8.0MB，仍远低于 25MB 目标）✓
- 说明：英文联想目前只做**前缀补全**（搜狗式拼写纠错/替换型建议未做，后续需要再加）

### M3.5b：三轮手测 2 条（2026-10-17 晚三轮，设备自测 + adb 真机验证，待用户手测）

用户 M3.5 手测后的 2 条反馈：

- [x] ① 回车小鱼**鱼头改朝左**，**尾巴重画**：原来尾瓣细长像叉子（尖到 ±6.6dp、窄深凹口），
      现改为两片**短圆尾瓣**（尖 ±5.7dp、外缘 quad 卷出圆尖）+ **宽浅凹口**（凹口顶点 +5.5dp）；
      仍为白鱼 + 挖空蓝眼（按下换 accent_pressed）。bbox 实测 50×40px（≈16.7×13.3dp，居中于 ⏎ 键）
- [x] ② 候选区右侧按钮**统一固定在词线 0.70h（「中间靠下一点」）**：无候选（˅ 收起键盘）不再回弹居中，
      三态（˅/˅˅/˄˄）同一水平位置 —— `HideKeyboardButton` **删掉 `alignToWordLine` 属性**、cy 恒 = 0.70h；
      服务 `updateRightButton()` 里的同步行随之删除。**以后剪贴板 / 设置 / 表情图标也统一放这条水平线**（M4 做）
- 验收：像素分析两状态箭头中心 y 均 ≈1671（= 候选区顶 1554.4 + 0.70×168，同一水平线）✓；
  小鱼 6x 放大截图确认头朝左、圆眼、圆尾瓣、无「叉子感」✓

### M3.6：中文整句输入（2026-10-17 晚四轮，用户反馈「woxiangshuruyijuhua 只出『我想』」；设备自测 + adb 真机验证，待用户手测）

用户原始诉求：打完整句拼音要出整句候选（搜狗式）。诊断：老组合层三道限制叠死——① 非末段词频门槛 12000
把「输入」(10549) 挡了；② 非末段单字直接禁掉（我爱你说不出）；③ 每多一段扣 11 分太重，组合候选排在词后。
M2.6 已定的部分上屏语义保留（整句候选 consumed = 全输入，空格 = 整句上屏；点词 = 只吃那一段）。

**做法（无语言模型的束搜索，常数全在 `FlatPinyinEngine` companion）**：

- `scanCompose` 重写为全句束搜索：路径分 = Σln(1+词频) − Σ段代价 + **全句覆盖加分**（COVER_BONUS=4），
  再打 COMPOSE_DISCOUNT=0.9 折；`PATH_BEAM` 4→16，`COMPOSE_MAX` 3→4
- 段代价（≈ 缺省语言模型的归一化项）：SEG_COST=6；单字段非末段 +9 / 末段 +4；简拼段 ABBREV_COST=4.5
- 非末段门槛放宽：多字词 SEG_MIN_FREQ 12000→2500（「输入」10549 过关）；单字词放行 SINGLE_MIN_FREQ=100000
  （实测校准：开 136k / 听 111k / 学 101k 要能过，拿 84k / 放 91k 挡掉；且 freq<60 万的单字段净分为负，碎拼自然压）
- 整段就是词库里的词且不弱（exactTop ≥ EXACT_FULL_TRUST=9）→ 不出整句候选（对齐青简「整段是一个词就不必当整句显示」）
- 整句候选只发**和最优路径咬得住的**（COMPOSE_MARGIN=2 内）：切掉「我下那个输入一句话」这类碎拼陪跑
- **裸声母语气词只在「全程简拼」读法里从轻**（nhm 的 m→吗）；跟在全拼词后按全价压（nihaozaijian 的 n→呢）——
  「你好在家呢」从此压不过「你好再见」
- `scanFull` 整段精确命中加分 2→4（EXACT_BONUS）：整段吃满该胜「只吃一截」——修「明天 压 明天见」「你 压 你在干嘛」
  （你/我/在 平白 8.8-8.9 分是这轮的隐藏元凶）
- `scanBoundary` 单字候选 ×0.85（SINGLE_BOUNDARY_DISCOUNT）：超高频单字（你 600 万）不该盖过多字词（nihaom 首选应是「你好」）
- 去掉了 M2.6 的「组合封顶 cap」：整段弱词（你在干嘛 freq 241）不再把强读法封顶——要么抑制（≥9）要么放开排
- **译文保护**：中文候选只有 ≤4 字才查释义（整句没有现成译词，不查不占位——青简同款；`GlossDict.annotationFor`
  本来就是精确二分，长句查也查不到，这是明确的语义保护）
- **JVM 测试台（新）**：`app/src/test/java/com/jianji/ime/engine/SentenceRankingTest.kt`，
  `gradle :app:testDebugUnitTest`（`app/build.gradle.kts` 加了 `testImplementation junit:junit:4.13.2` + stdout 回显），
  直读 `app/src/main/assets/dict.bin`（纯 JVM 无 Android 依赖）。四组用例：整句 7 / 词语回归 6 / 特殊行为（部分上屏、简拼、回退）/
  词库查证。**改引擎常数后先跑它，再上机**
- 青简参考（未照搬）：整句 = 词图 Viterbi + bigram LM（`crates/qingjian-core/src/sentence/`，BEAM_WIDTH=8、
  每格留 6 词、未知词罚 4；lm.qj 44MB 太重不引）；青简整句本来就没有现成译词（生词本整句翻译走云端）。
  二期可选：bigram LM 裁剪/量化版
- 真机验收（adb 注入）：woxiangshuruyijuhua → 首选「我想输入一句话」+ 空格整句上屏 ✓；nihaozaijian → 首选「你好再见」✓；
  nizaiganma → 首选「你在干嘛」（4 字词条带译文 phr. what are you doing，长句无译文）✓；nihaom 首选「你好」吃 5 字母 ✓
- 数据校准：我想 86644 / 输入 10549 / 一句话 8802 / 再见 21454 / 你在 79007 / 干嘛 56605 / 你在干嘛 241 / 明天见 2582 / 明天 142024

### M3 学习层：候选双行译文（2026-10-17 晚，设备自测 + adb 真机验证，待用户手测）

- [x] **数据管线** `tools/dictgen/GlossGen.java`（与 DictGen 同风格，JDK 21 单文件直跑，无构建）：
  - `java GlossGen.java <glossary.tsv> <gloss.bin> [dict.bin]`——给了 dict.bin 时**只保留词库里有的词**
    （候选只可能来自词库，交集显著省体积）；`--lookup <gloss.bin> <词>` 抽查
  - 青简 glossary 格式 `词\t[词性. ]译文[|读音]\t…`（# 注释行；义项最多取前 2 条，对齐 `Translation::MAX_SENSES`；
    `|` 后是读音，日语假名；词性识别：首位空格前以「.」结尾且是已知缩写）
  - gloss.bin（大端，"JJG1"）：词 blob（UTF-8 字节序升序）+ 偏移表 + 义项区间表 + 文本/读音共享 arena +
    义项表每条 13B（byte pos | int textOff | short textLen | int readOff | short readLen）
  - pos 码 1..12 = n./v./adj./adv./pron./prep./conj./num./m./part./int./phr.（对齐青简 PartOfSpeech，含 noun/interj/mw 等别名）
  - 产物：`gloss-en.bin` 3,483,924 B（88,362 词条 / 97,246 义项）、`gloss-ja.bin` 4,896,907 B（90,354 / 90,830）；
    与词库差集 ~40k 基本是零频生僻字（青简 README 同款注明生僻字释义缺失）
  - 坑：工具里按字节比词要用**词的 UTF-8 字节**，别拿 `charAt()` 直接比 blob（只对 ASCII 正确）
- [x] **Android 端** `engine/GlossDict.kt`：整块读入内存 + 字节序二分；显示串现拼：
  义项「词性 + 空格 + 译文」、多条「 · 」连接（对齐青简 `apps/cli/src/display.rs`）；
  **振假名算法已移植**（回溯对齐：`開発する|かいはつする` → `開発(かいはつ)する`，对不上整体注在后面）；
  查询带小缓存（>4096 清空）
- [x] **服务接线** `JianjiImeService.kt`：
  - `ensureGloss()`：按 `Prefs.learnLang`（`en`/`ja`/`off`，默认 `en`）异步载入；onCreate 预热，
    每次刷候选再校验一次（覆盖失败重试场景）；载好若在组句 → 重刷候选补画（青简「译文到达后再补画，不等待」）
  - 换语言先把旧译文撤下（`gloss=null`）再载新表，不串台；载入失败记 `glossFailedLang` 防重试风暴
  - **`prefListener` 监听 `key_learn_lang`：不打字也立即重载重画**（测试页 radio 一拨输入法就变）
  - `updateComposing()`：`CandidateBar.Item(text, gloss?.annotationFor(text))` → 候选栏与「更多候选」面板同享
- [x] **双行绘制补齐** `CandidateBar` / `CandidateGridPanel`：超长译文自动省略号（候选栏封顶半屏宽、网格封顶一格宽，
  `TextUtils.ellipsize`——注意它要 `TextPaint`，`trPaint` 已改）；无译文候选单行居中，与双行格混排正常
- [x] **测试页**（DebugInputActivity）：学习语言三选一 radio（英语/日语/关闭，写 Prefs 立即生效）+
  「释义自测」按钮（两份表载入耗时 + 7 词注解，含振假名样例；文本可 `uiautomator dump` 读取）
- 验收（真机 adb 截图 + 设备自测文本，全部 ✓）：
  - en：`nihao` → 你好上方「int. hello · int. hi」；`kaifa` → 开发「v. develop · v. exploit」
  - ja：开发「v. 開発(かいはつ)する」、开放「v. 開放(かいほう)する」、吃饭「v. ご飯(はん)を食(た)べる」、
    你好「int. こんにちは」（纯假名不注音）
  - 关闭 = 候选全是单行大字无译文；radio 切换不打字立即生效（撤下 / 补画）✓；
    「更多候选」网格内译文同现 + 按格宽省略 ✓
  - 性能：gloss-en 载入 29ms / ja 22ms、查询 0-1ms（设备自测）；APK 8.0MB（原 4.6MB，远低于 25MB 目标）✓

### M2.6 部分上屏 + 分词组合层（2026-10-17 晚，用户反馈「点你好不该吃掉后面的 m」）

- [x] **部分上屏（部分消费）**：`PinyinEngine.Candidate` 新增 `consumed`（吃掉的拼音字母数）：
  - 全拼/简拼前缀 = 吃光输入；边界回退 = 吃到边界处（nihaom 的「你好」= 5）；简拼前缀回退 = 前缀长
  - `JianjiImeService.commitCandidate()`：`consumed < 拼音长度` 时只提交那一段，**剩余字母留在组句里继续出候选**
    （nihaom 点「你好」→ 输入框「你好+m」组句，候选刷新为 m 的候选 → 再点「吗」得「你好吗」）✓ 真机验证
  - 空格（首选上屏）同语义；标点/数字/长按备选/切中英 = `flushComposing()`：首选上屏 + **剩余字母原样上屏**（no字不丢）
  - 对齐青简 `commit_consumes_only_the_candidate_syllables`（其测试：kaifazhe 选「开发」→ 组句剩 zhe）
- [x] **分词组合扫描 `scanCompose`（DP，路径每位置保留前 4）**：把输入切成 2~3 段词组合成短语/整句：
  nihaoma→你好吗 / youkongma→有空吗 / nihaozaijian→你好再见 / nhm→你好吗（简拼整句）/ shenmedoubu→什么+都+不
  - 段匹配：全拼完全匹配 或（简拼）简拼完全匹配（音节数=段长，×0.45 折）
  - 规则：非末段至少 2 音节且**词频 ≥ 12000**（挡「你哈」4836 这类拼串噪声）；
    单字母段只当末段语气词（吗/吧/了…白名单 ~25 字，nihaom 的 m→吗）；末段单音节全拼匹配时语气词优先（ma→吗）
  - 扣分：全拼段每多一段 −11（按真实词频校准，压长串 sum 膨胀）；简拼段 −7；
    只补其它扫描没出过的词串（不抬已有词分）；整段精确词存在时组合分**封顶在它之下 −0.1**（kaifazhe：开发者 压 开发这）
  - 简拼段开关：整段输入已被「像样的词」（≥4 分）吃掉时不用简拼段（wsm→为什么 不被「晚上吗」压）；
    但很弱的全消费词不挡（nhm 的「女孩们」freq 263 → 你好吗 照出）
- 验收（真机 adb 实测 + Debug 页引擎自测，全部 ✓）：
  - nihaom→[你好吗|你|你好…] 点「你好」→「你好+m」组句 → 点「吗」→「你好吗」；空格→整词「你好吗 」
  - youkongma→有空吗（第一）；nihaoma→你好吗（第一）；nihaozaijian→你好再见（第一）；nhm→你好吗（第一）
  - 回归：nihao/xian/shi/w/zhongguo/zaijian/women/wsm/jjjjj 排序均正常；标点收尾「…你haox，」不丢字
- 调试提示：Debug 页自测输出已带 `(consumed)`；读自测结果用 `uiautomator dump` 取文本，别截图认字

### M2.5 手测反馈修复（2026-10-17 下午，用户 5 条反馈）

- [x] ①「jjjjj 就没有候选了」→ 引擎新增 **简拼前缀回退** `scanInitialsFallback`（`FlatPinyinEngine.kt`）：
      **仅当主路径全空时**触发，从 q 的最长前缀开始找「完全等于某词简拼」的匹配（最多下探 4 级，找到即止，
      取前 8 个，权重 = ln(1+词频)×(0.45+0.06×前缀长)）。
      jjjjj→斤斤计较/将计就计 ✓；混合输入 nhao→你好/你会/女孩/年后/男 ✓（信息量不随输入变长而丢失）
- [x] ②「删 5 个字母要按 6 次 ⌫」→ `updateComposing()` 空分支修复：**finishComposingText() 只把内联拼音"落下"成普通文字，不是删除**；
      改为先 `setComposingText("", 0)` 清空组句区再 finishComposingText()。5 次 ⌫ 后输入框全空 ✓
- [x] ③ 右侧按钮三态 `HideKeyboardButton.Mode`：无候选 = ˅ 收起键盘；**有候选 = ˅˅ 更多候选（蓝色，点开网格面板）**；面板展开中 = ˄˄ 收起面板。
      新增 `keyboard/CandidateGridPanel.kt`：5 列网格 + 纵向滚动 + 惯性 + 点选上屏，**盖在键盘区上（高度对齐当前键盘，不改输入法窗口高度）**；
      组句时点候选直接上屏并收起 ✓；n 的 100 个候选滚动 ✓；˅ 收起键盘仍正常 ✓
- [x] ④ 中/英键改为**双字号 + 高亮**（`KeyboardView.drawLangLabel`）：当前语言字符 16.5dp 强调蓝，另一个 10.5dp 压暗；
      中文态「中」大蓝 / 英文态「英」大蓝，已截图验证 ✓（不再用整键变蓝）
- [x] ⑤ 回车图标重画（`KeyboardView.drawIcon` ENTER 分支）：圆角转弯 + 更大箭头 + 稍粗笔画（2.6dp）✓
- 测试小坑：`adb install -r` 流式安装偶发失败（空错误），加 **`--no-streaming`** 即成功

### 常用命令

```powershell
. .\.dev-env\env.ps1                                # 载入工具链环境（每个新终端都要）
gradle :app:assembleDebug                           # 构建 Debug APK（增量约几秒~1 分钟）
gradle :app:assembleRelease                         # Release（签名配置 M5 再做）
adb install -r app\build\outputs\apk\debug\app-debug.apk --no-streaming   # 安装到手机（流式安装偶发失败，加 --no-streaming）
# M2 词库管线（改了词库数据才需要重跑；输出 4.47MB 直接进 assets）
java tools\dictgen\DictGen.java reference\qingjian-repo\assets\lexicon\dict.tsv app\src\main\assets\dict.bin
java tools\dictgen\DictGen.java --lookup app\src\main\assets\dict.bin nihao   # 命令行抽查候选
# M3 释义表管线（改了 glossary 才需要重跑；输出 gloss-en.bin 3.48MB + gloss-ja.bin 4.90MB）
java tools\dictgen\GlossGen.java reference\qingjian-repo\assets\glossary\glossary-en.tsv app\src\main\assets\gloss-en.bin app\src\main\assets\dict.bin
java tools\dictgen\GlossGen.java reference\qingjian-repo\assets\glossary\glossary-ja.tsv app\src\main\assets\gloss-ja.bin app\src\main\assets\dict.bin
java tools\dictgen\GlossGen.java --lookup app\src\main\assets\gloss-en.bin 开发   # 命令行抽查释义
# M3.5 英文词表（改了 english.tsv 才需要重跑；输出 english.bin 1.55MB）+ 英→中释义表
java tools\dictgen\EnglishGen.java reference\qingjian-repo\assets\lexicon\english.tsv app\src\main\assets\english.bin
java tools\dictgen\EnglishGen.java --lookup app\src\main\assets\english.bin hel        # 命令行抽查前缀联想
java tools\dictgen\GlossGen.java reference\qingjian-repo\assets\glossary\glossary-zh.tsv app\src\main\assets\gloss-zh.bin
```

> PowerShell 里看中文输出：先 `[Console]::OutputEncoding = [Text.Encoding]::UTF8`，
> 且 java 加参数 `"-Dstdout.encoding=UTF-8"`（否则乱码）。

> AGP 自动下来装过 `build-tools;36.0.0`（AGP 默认工具链版本），无需干涉；
> Gradle Wrapper 已生成（gradlew / gradlew.bat），distributionUrl 指向腾讯镜像。

### 测试机信息（Lenovo）

- 设备：Lenovo L71091（`halo`），adb 序列号（已脱敏），USB 直连
- 系统：Android 12（SDK 32）
- 原默认输入法：`com.sohu.inputmethod.sogou/.SogouIME`（要切回时用
  `adb shell ime set com.sohu.inputmethod.sogou/.SogouIME`）
- 小坑：安装后立刻 `ime enable` 会报 "Unknown input method"，
  **等几秒重试即可**（系统输入法列表刷新延迟）
- 键盘几何（1080×2400 / density 3；顶部预留区 56dp=168px）：
  - 字母页/数字页（键高 48dp）：键盘顶 1722，行中心 y = 1818 / 1980 / 2142 / 2304
  - 符号页（键高 42dp + 36dp 标签栏）：键盘顶 1686，行中心 y = 1773 / 1917 / 2061 / 2205，标签栏中心 y≈2346
  - 候选栏（占预留区 y∈[1554,1722]，右侧 46dp 让给收起按钮）：第一格中心 ≈ (114,1638)；
    条宽到 x≈942；横向拖动（DOWN/MOVE×3/UP）可滑到后面的候选
  - 各键中心（字母页，实测校准过）：行1 y=1818：q67 w172 e277 r382 t487 y592 u697 i802 o907 p1012；
    行2 y=1980：a113 s220 d326 f433 g540 h647 j754 k860 l967；行3 y=2142：⇧86 z211 x321 c430 v540 b650 n759 m869 ⌫994；
    行4 y=2304：符67 123 172 ，276 空格510 。744 中/英848 ⏎952

### 工具链

| 组件 | 版本 / 路径 |
|---|---|
| Gradle | 9.8.1 → `.dev-env\gradle\gradle-9.8.1` |
| JDK | 21.0.12.1+1 → `.dev-env\jdk\jdk-21.0.12.1+1` |
| Git | 2.56.0.windows.2 → `.dev-env\git`（已设 core.longpaths=true） |
| Android SDK | `.dev-env\android-sdk`（platforms/android-37.0、build-tools/37.0.0、platform-tools） |
| AGP | 9.4.1（**内置 Kotlin 支持**：不再应用 `org.jetbrains.kotlin.android` 插件；jvmTarget 跟随 `compileOptions`=Java 17） |
| androidx / coroutines | core-ktx 1.19.1 / coroutines-android 1.11.0 |
| SDK 版本 | compileSdk = targetSdk = 37（平台 android-37.0），minSdk = 26 |
| Gradle 缓存 | `GRADLE_USER_HOME = .dev-env\gradle-home` |

用法：每个新终端先 `. D:\Fish\documents\jianji\.dev-env\env.ps1`，之后 `java/gradle/git/adb/sdkmanager/android` 均可用。

### 工程骨架（已落盘）

- `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`
- `app/build.gradle.kts`（namespace / applicationId = `com.jianji.ime`）、`app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`（IME 服务声明 + `settingsActivity` 齿轮 + 设置页桌面图标 + `res/xml/ime_method.xml`）
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/jianji/ime/JianjiImeService.kt`（主服务：键盘 + M2 拼音组句/候选/学习 + M4 覆盖面板/剪贴板/表情/设置入口）
- `app/src/main/java/com/jianji/ime/Prefs.kt`（声音/振动开关 + M2 用户词频 + M3 学习语言）
- `app/src/main/java/com/jianji/ime/ClipboardStore.kt`（M4 剪贴板历史 + 常用语存取；编码纯逻辑，可 JVM 测试）
- `app/src/main/java/com/jianji/ime/SettingsActivity.kt`（正式设置页：学习语言/声音振动/常用语/剪贴板/关于；
  M5.1 卡片风格）、`LicensesActivity.kt`（M5 许可页，M5.1 卡片风格）、`ImUi.kt`（M5.1 设置系共用样式与卡片行构造）
- `app/src/main/java/com/jianji/ime/keyboard/`（KeyDef / KeyboardLayouts / KeyboardView / HideKeyboardButton / **ToolButton** /
  CandidateBar / CandidateGridPanel / **ClipboardPanel** / **EmojiPanel**）
- `app/src/main/java/com/jianji/ime/engine/`（**JianDict / PinyinEngine / FlatPinyinEngine / GlossDict / EnglishDict / EmojiDict**）
- `app/src/main/assets/dict.bin`（M2 词库 4.47MB，由 tools 生成，不入 git 也可——生成物）；
  `gloss-en.bin` / `gloss-ja.bin`（M3 释义表 3.48MB / 4.90MB，同上）；
  `english.bin` / `gloss-zh.bin`（M3.5 英文词表 1.55MB / 英→中释义 1.72MB，同上）；
  `emoji.bin`（M4 表情 22KB，同上）；`legal/`（M5 署名与许可全文）
- `tools/dictgen/DictGen.java`（词库数据管线：tsv → dict.bin + --lookup 抽查）；
  `tools/dictgen/GlossGen.java`（释义表管线：glossary-*.tsv → gloss-*.bin，M3）；
  `tools/dictgen/EnglishGen.java`（英文词表管线：english.tsv → english.bin + 前缀抽查，M3.5）；
  `tools/dictgen/EmojiGen.java`（表情管线：panel/catalog.tsv → emoji.bin，M4）；
  `tools/fontgen/`（M5 字体子集管线，留档）、`tools/icon/gen_icon.py`（M5.1 应用图标生成器）
- `app/src/main/res/values/colors.xml`（方案 A 配色 + 图标自适应背景色）；
  `res/drawable/ic_*.xml`（M5.1 设置系矢量图标）；`res/mipmap-*/` + `res/mipmap-anydpi-v26/`（M5.1 应用图标）
- `app/src/debug/`（仅 Debug 包的测试页与清单）
- `local.properties`（sdk.dir 指向 .dev-env/android-sdk，已被 gitignore）
- `docs/ROADMAP.md`（里程碑 + 已定决策）、`docs/dev-env.md`（环境说明与清理方法）
- `docs/mockups/mockup-{a,b,c}.html`（三版视觉稿：A 墨夜 / B 雾青 / C 暖墨）
- `README.md`、`.gitignore`

### 青简参考资料（`reference/qingjian-repo/`，不入库）

- 2026-10-16 经 VPN 代理 `git clone --depth 1` 获得完整仓库（76.6 MB，1549 文件，12 个 crate）
- **已设 `core.autocrlf=false` 并强制重检出**，防止 Windows CRLF 污染数据文件
  （dict.tsv 正确大小 = 2,377,545 B，若见 2,510,432 B 说明 CRLF 又回来了）
- 关键数据：`dict.tsv`（2,377,545 B）、`english.tsv`、`glossary-en.tsv`（6,556,053 B）、
  `glossary-ja.tsv`（12,770,689 B）、`levels-{en,ja}.tsv`
- 关键文档：`docs/design/`（architecture / candidate-ui / core-internals / landscape / rendering）、
  `docs/notes/`（crate-notes：数据文件、常数、生成命令）、`CLAUDE.md`、`AGENTS.md`
- 源代码：`crates/qingjian-core`（引擎门面）、`qingjian-dictionary`（词库查询）、
  `qingjian-translate`（释义/等级表）、`qingjian-learning`（用户学习落盘）、`qingjian-lm`（bigram）、
  `qingjian-format`（.qj 容器）、`tools/`（dict-convert / gloss-gen / corpus 数据管线）
- 早期经 jsdelivr 抓取的部分文件已删除（被完整仓取代）

### 网络与镜像备忘（重要）

- 2026-10-16 用户已开 VPN（代理模式：系统代理 `http://127.0.0.1:7897`）。
  `curl` / `git` 不会自动读系统代理，需要命令里显式设 `$env:HTTPS_PROXY="http://127.0.0.1:7897"`
  （PowerShell Invoke-WebRequest 会自动走系统代理）。VPN 关闭后此变量随命令一起失效，无需清理。
- 无 VPN 时的可用镜像：
  - GitHub 内容：`cdn.jsdelivr.net`（gh/ 路径、@main）、`ghproxy.net`、`gh-proxy.com` 前缀式代理
  - npm：npmmirror；JDK：清华 Adoptium；Gradle：`mirrors.cloud.tencent.com/gradle/`、`mirrors.huaweicloud.com/gradle/`（都支持断点续传）
- `dl.google.com`、`repo1.maven.org`、`plugins.gradle.org` 一直可直连
- 注意：大文件下载要带 `--speed-limit/--speed-time` 防连接假死；`-C -` 断点续传可用

### 下一步（按顺序）

1. [x] M1 全部（v2/v3/v3.1/v3.2 均用户手测通过）
2. [x] M2：词库管线 + 全拼/简拼/回退引擎 + 候选栏 + 学习 + 中英切换（2026-10-17 上机 + adb 实测）
3. [x] **M2.5：用户手测 5 条反馈修复**（词库匹配变长输入 / 删字残留 / 更多候选面板 / 中英键高亮 / 回车图标）——
   已上机逐条验证通过（2026-10-17 下午）。用户后续手测如发现排序/匹配手感问题，仍先调 `FlatPinyinEngine.kt` 常数
4. [x] **M2.6：部分上屏 + 分词组合层**（2026-10-17 晚）——点候选只吃它匹配的那段拼音，剩余继续组句；
   多词连打/简拼整句有组合候选。见上节。用户手测时重点体验：nihaom / nihaoma / youkongma / nhm / kaifazhe
5. [x] **M3：学习层**（2026-10-17 晚）——glossary-en/ja → 双行候选译文（译文小字上 / 候选大字下），
   英语/日语一次一种、异步载入、切语言即时生效。见上节 M3。**开关位置：Debug 测试页 radio（M5 进正式设置页）**；
   用户手测重点：打字看候选上方译文（en/ja）、切「关闭」候选应无译文、更多候选面板里也有译文
6. [x] **M3.5：二轮手测 6 条修订 + 英文模式候选**（2026-10-17 晚二轮）——单行候选对齐词线、字号微调、
    ⇧ 两态可视化、回车小鱼、**英文模式：原样输入为首选 + 联想词 + 中文释义**、右侧箭头与词线同高。见上节。
    用户手测重点：英文下打 hell/jddhjhaf、点候选/空格/回车手感；⇧ 与回车新图标观感
   - [x] **M3.5b（三轮手测 2 条反馈）**：回车小鱼鱼头朝左 + 尾巴重画（短圆尾瓣 + 宽浅凹口）；
     右侧按钮三态（˅/˅˅/˄˄）**统一固定在词线 0.70h** —— 无候选时不再回弹居中，
     以后剪贴板/设置/表情图标同此水平线。见上节
7. [x] **M3.6：中文整句输入**（2026-10-17 晚四轮）—— 全句束搜索（无 LM）+ 覆盖加分；整句候选排词级前、
   空格整句上屏；长句（＞4 字）不出译文。JVM 测试台 `SentenceRankingTest` 先调参再上机。见上节 M3.6。
   用户手测重点：woxiangshuruyijuhua / nihaoma / jintiantianqihenhao / womenyiqichifanba / nizaiganma / nihaozaijian
8. [x] **M4：剪贴板 + 常用语 + 表情 + 正式设置页**（2026-10-17，实现 + adb 全流程自测；**待用户手测**）——见上节 M4。
   用户手测重点：候选区右侧 [📋][🙂][˅] 图标行；剪贴板面板（点选上屏/长按菜单/清空确认/常用语置顶）；
   表情面板（分类切换/连点/颜文字）；设置页三入口（桌面「简记」图标 / 输入法选择器齿轮 / 剪贴板面板「设置」）；
   「写测试剪贴板」按钮在 Debug 测试页
9. [x] **M4.2：用户手测四项修订**（2026-10-17 晚，已上机自测，**待用户手测**）——剪贴板搜狗化（分段标签/卡片/左划操作，
   长按菜单移除）/ 液态玻璃主题（默认，设置页「外观」：4 色调 + 透明度，即时生效）/ ⚙ 进图标行且打字自动隐藏 /
   删光输入回收起键盘 bug 修复。见上节 M4.2。
   用户手测重点：剪贴板面板左划（＋ 存常用语 / 🗑 删除）、长文本卡片 ▾ 展开、清空二次确认；设置页「外观」换主题/色调/透明度观感；
   打字时 📋🙂⚙ 隐藏、删光后恢复且 ˅˅ 变 ˅
   - [x] **M4.2b（二轮手测 2 条）**：剪贴板面板顶边上延正好盖住图标行（打开时整行隐藏，半透明玻璃下也无残影）；
     表情面板右下角空位新增「‹ 返回键盘」、表情打开时右上角改「˅ 收起键盘」。见上节 M4.2b
   - [x] **M4.2c（三轮手测 1 条）**：数字/符号页或表情面板打开时收起键盘，下次弹出回到默认字母页（`resetInputState()`
     同时挂在 `onStartInput` 与 `onStartInputView`）。见上节 M4.2c
   - [x] **M4.3（液态玻璃 2.0 试作 → 已移除）**：按用户要求参考 GitHub 液态玻璃项目重做质感（GlassArt 材质引擎：
     阴影 / 渐变 / 镜面 / rim / 磨砂 / 液面 / 窗光带 / 跟手光晕），真机逐项验证通过后，**用户决定：效果仍不满意，
     移除玻璃，只保留最初的「方案 A · 墨夜」** —— GlassArt / 双主题 / Prefs 三项 / 设置页「外观」区全部删除，
     回退为单一墨夜（真机截图验证与 M4.2 时期观感一致）。见顶部 M4.3（含以后再做时的配方与参数留档）
10. [x] **M5：打磨**（2026-10-17 深夜，已上机验证）—— 字体子集化（后按 M5.1 撤出）+ NOTICE 与应用内许可页 +
    正式签名 release APK（R8）。见上节 M5。
    - [x] **M5.1（用户拍板）**：设置页/许可页改卡片风格（`ImUi.kt`）；候选字体 → **跟随系统**（子集撤出 APK，
      管线留档 `tools/fontgen/`）；应用图标 = **B 墨底蓝简**（`tools/icon/gen_icon.py`）；release 7.33MB。见上节 M5.1
    - [x] **手机端开发痕迹清理**（2026-10-17 深夜）：/sdcard 上的 `s*.png` 开发截图、`ui*.xml` uiautomator 转储、
      `t*.png` 临时截图全部删除；`pm clear com.jianji.ime` 清掉测试期剪贴板记录与学习词频（设置回默认）
    - [ ] **待用户验收**（用户明确：**`.dev-env` 暂留，必须等用户明说才清理**；符号面板完善按需）

### 关键约定（别忘）

- **终端一律用 PowerShell 7（`pwsh`），不用 Windows PowerShell 5.1**（用户 2026-10-18 明确要求）。
  本机 pwsh = `C:\Users\Fish\AppData\Local\Microsoft\WindowsApps\pwsh.exe`（7.6.7）；
  在 5.1 宿主里跑脚本用 `pwsh -NoProfile -EncodedCommand <base64(UTF16LE)>` 包装（见根目录 `AGENTS.md`）
- 许可 GPL-3.0-or-later；青简数据（dict / glossary / levels）直接复用，需在应用内与 `NOTICE` 署名
  （M5 已建：根 `NOTICE` + `app/src/main/assets/legal/` + 设置页「开源许可与署名」入口）
- 引擎纯 Kotlin 自研、接口化（`PinyinEngine`），不引 NDK；继承青简原则：
  候选生成不阻塞 + 译文异步注释 + 一次只显示一种学习语言
- 候选区方案 A：双行格（译文小字上 / 候选大字下）横向滚动；拼音内联显示在输入框
- **M2 排序：五类候选（全拼前缀 / 简拼前缀 / 音节边界回退 / 简拼前缀回退 / 全句组合）的分权重全部集中在
  `engine/FlatPinyinEngine.kt`**（scanFull/scanInitials/scanBoundary/scanInitialsFallback/scanCompose 与 companion 常数：
  EXACT_BONUS=4 / SINGLE_BOUNDARY_DISCOUNT=0.85 / SEG_COST=6 / SINGLE_NONFINAL_EXTRA=9 / SINGLE_FINAL_EXTRA=4 /
  ABBREV_COST=4.5 / ABBREV_SEG_FACTOR=0.45 / COVER_BONUS=4 / COMPOSE_DISCOUNT=0.9 / COMPOSE_MIN_SCORE=3 /
  COMPOSE_MARGIN=2 / SEG_MIN_FREQ=2500 / ABBREV_SEG_MIN_FREQ=10000 / SINGLE_MIN_FREQ=100000 /
  EXACT_FULL_TRUST=9 / FULL_CONSUMER_TRUST=4 / PATH_BEAM=16 / COMPOSE_MAX=4）——用户觉得"首选不对"时先来这里调，
  **改完先跑 JVM 测试台**（`gradle :app:testDebugUnitTest`，`SentenceRankingTest`）再上机；
  词库数据本身在 `tools/dictgen/` 管线重新生成
- **部分上屏语义（M2.6，对齐搜狗/青简）**：候选带 `consumed`，点选/空格只消费那一段，剩余拼音留在组句继续出候选；
  标点/切语言 = 首选上屏 + 剩余字母原样上屏；`commitRaw` 只用于回车与剩余残留
- **学习语言与译文（M3）**：`Prefs.key_learn_lang`（`en`/`ja`/`off`，默认 `en`，一次一种）；
  数据管线 `java tools\dictgen\GlossGen.java <glossary.tsv> <gloss.bin> [dict.bin]`（en/ja 各跑一次，输出到 `app/src/main/assets/`）；
  显示规则：义项「词性 + 译文」多条「 · 」连接、日文振假名现拼（`GlossDict.kt`）；**无译文留空不占位**、
  超长按容器宽省略；译文异步补画不阻塞候选；开关暂在 Debug 测试页（M5 挪进正式设置页）
- **候选版式（M3.5/M3.5b）**：候选词一律画在**词线 0.70h**（无译文也不再居中，译文线 0.34h）；字号线宽：
  候选栏词面 18dp / 网格 16dp / 译文 10.5dp·9.5dp；**右侧按钮三态（˅/˅˅/˄˄）恒画在词线 0.70h、
  无候选也不回中**（`HideKeyboardButton` cy 恒 = 0.70h，无 alignToWordLine）
- **右侧图标行（M4）**：候选区最右 [📋 剪贴板][🙂 表情][˅/˅˅/˄˄]（40/38/44dp，`ToolButton` + `HideKeyboardButton`），
  图标同画词线 0.70h、展开态点亮 accent；今后再加图标**沿用这条水平线**、「设置」入口 = 剪贴板面板顶栏 + 桌面图标 + 输入法齿轮
- **覆盖面板（M4）**：`Overlay` 枚举（NONE/GRID/CLIP/EMOJI）互斥；剪贴板/表情展开时按任意键自动收起；
  面板高度 `syncPanelHeight()` 对齐当前键盘（各页高度不同）；剪贴板面板点选 = 收尾组句后 commitText
- **剪贴板数据（M4/M5.1b）**：`ClipboardStore`（Base64 行编码；历史 50 / 常用语 100 / 单条 1000 字；入历史去重上移；
  `capture(force)` 语义：开键盘视图 force=false 跳过 `KEY_SKIP`（持久化）记录的那条，剪贴板监听回调 force=true 从不跳过；
  删除单条（`removeHistory`）与清空（`clearHistory`）都记住当前系统剪贴板内容防立刻抓回；单测 `ClipboardStoreTest`）
- **表情数据（M4）**：`java tools\dictgen\EmojiGen.java reference\qingjian-repo\crates\qingjian-core\data\panel\catalog.tsv app\src\main\assets\emoji.bin`
  （"JJM1" 大端：分类名 + 条目，11 类 2236 条 22KB；曾用 CLDR emoji-zh.tsv 但它按词头排列不适合面板，已弃用）
- **⇧ 与回车图标（M3.5/M3.5b）**：⇧ = 灰描边/蓝描边/实心蓝三态（`KeyboardView.drawShiftIcon`，用 `iconFill` 画笔、
  用完还原 FILL）；回车 = 白色小鱼，**鱼头朝左**（头在左、蓝眼挖空在左上方），尾巴 = 两片短圆尾瓣 + 宽浅凹口
  （**别改回细长尖瓣**，用户点名说过「尾巴奇怪」）
- **英文模式（M3.5）**：字母进 `englishBuf` 组句（上限 24）→ 候选 = [原样输入, 前缀联想词 × 100]，
  全部带英→中释义；空格 = 原样 + 空格、回车 = 原样、标点 = 原样 + 标点、切语言 = 原样收尾（`commitEnglish`/`flushEnglish`）；
  数据 `english.bin` + `gloss-zh.bin` 首次切英文异步载入（`ensureEnglish`）；**联想只有前缀补全，无拼写纠错**
- 候选区右侧按钮语义（用户定的）：无候选 = 收起键盘；有候选 = 展开更多候选面板（`CandidateGridPanel`）；展开中 = 收起面板
- 默认深色主题；**候选字体跟随系统**（M5.1 用户选定；M5 的文楷子集已从 APK 撤出，生成管线留档
  `tools/fontgen/`，将来要自带字体一条 `build_font.py` 重建再接线；应用图标字形由 `tools/icon/gen_icon.py` 渲染）
- 首版仅 26 键；**整句 v1（无 LM 束搜索）M3.6 已做**；九宫格 / 双拼 / 整句二期（可选：青简 bigram LM 裁剪量化版）
- **视觉方案：用户 2026-10-16 选定 A「墨夜」**（`docs/mockups/mockup-a.html`）：
  中性冷暗底色、淡蓝 #5B8DEF 强调、键圆角 12dp、键高约 48dp、功能键压暗；
  **设置系页面（设置/许可）= 卡片风格**（M5.1：顶部「← 标题」栏 + 分区 accent 小标题 + 每行 22dp 圆角卡片 +
  圆形图标泡；`ImUi.kt` 共用样式 + `res/drawable/ic_*.xml` 矢量图标）；**应用图标 = B 墨底蓝简**
  （M5.1：`tools/icon/gen_icon.py`，墨底 #17191E + 蓝简 #5B8DEF，自适应前景字形 0.43 画布）
- `.dev-env/` 和 `reference/` 都在 .gitignore；**用户 2026-10-17 明确：`.dev-env` 暂留，必须等用户明说才整体删除**
