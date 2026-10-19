# 数独大师（Sudoku Master）· Kotlin + Jetpack Compose 单机安卓版

本项目为《数独大师（单机安卓版）产品策划方案》的可运行实现，由已验证的单文件
HTML/CSS/JS 版本**平移移植**而来：**算法层直接复用**，UI 层用 Kotlin + Jetpack
Compose 完整重写，数据层用 Room + DataStore 落地。

- 纯单机、离线运行，不请求网络权限，不收集 / 不上传任何数据。
- 支持 **四宫格 4×4 / 六宫格 6×6 / 九宫格 9×9** 三种棋盘。

## 正式版 APK（已构建并签名）

已在本机用 `gradle assembleRelease` 产出可直接安装的正式版：

- 产物：`release/SudokuMaster-1.8.0-release.apk`（约 3.0 MB，R8 混淆 + 资源压缩 + release 签名）
- 签名：自签名 release 密钥 `keystore/sudoku-release.jks`（PKCS12，RSA 2048，
  有效期 10000 天，`CN=SudokuMaster`）；`apksigner verify` 通过（v2 scheme）。
  - 密钥口令存放于 `keystore.properties`（已被 `.gitignore` 忽略，**切勿入库/外泄**）。
  - 若日后要上架应用商店，请在 Play Console 用同一密钥或改用 Play App Signing。
- 包信息：`com.example.sudokumaster` · versionCode 15 · versionName 1.8.0 · minSdk 24 / targetSdk 34。
- 启动图标：使用紫蓝渐变 3D「数独 + 手柄」插图，经中心裁剪后生成
  `mipmap-{m,h,x,xx,xxx}hdpi` 的 `ic_launcher.png` / `ic_launcher_round.png`（源图 1536×1536）。
- 权限：`VIBRATE`（震动反馈，设置内可关）+ AndroidX 自动注入的
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（自作用域、非敏感），
  **无 INTERNET / 存储 / 定位 / 通讯录等任何权限**。

本地复现构建：

```bash
# 需 JDK 17 + Android SDK(platform 34 / build-tools 34.0.0)，并设置 ANDROID_HOME
gradle assembleRelease        # 产出 app/build/outputs/apk/release/SudokuMaster-1.8.0-release.apk
apksigner verify --print-certs release/SudokuMaster-1.8.0-release.apk
```

## 技术栈与版本

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24（Compose Compiler 1.5.14） |
| compileSdk / targetSdk / minSdk | 34 / 34 / 24 |
| Compose BOM | 2024.06.00（Material3） |
| Navigation Compose | 2.7.7 |
| Room | 2.6.1（kapt） |
| DataStore Preferences | 1.1.1 |
| Coroutines | 1.8.1 |

## 如何构建 / 运行

1. 用 **Android Studio**（Ladybug 或更新）打开本目录（`SudokuMaster/`）。
2. 等待 Gradle Sync；首次会下载 Gradle 8.9 与依赖。
   - 仓库内未附带 `gradlew` 可执行文件与 `gradle-wrapper.jar`（二进制）。
     在 Android Studio 中打开后，IDE 会自动补齐 wrapper；也可命令行执行
     `gradle wrapper --gradle-version 8.9` 生成。
3. 连接设备或模拟器，Run `app`。

命令行（若已生成 wrapper）：

```bash
./gradlew assembleDebug        # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # 安装到已连接设备
```

## 工程结构

```
app/src/main/java/com/example/sudokumaster/
├── SudokuApplication.kt           # Application，持 AppContainer
├── MainActivity.kt                # 单 Activity + Compose 入口
├── domain/                        # 纯 Kotlin，无 Android 依赖，可单测
│   ├── engine/SudokuEngine.kt     # ★ 算法：回溯生成 / 唯一解校验（复用）
│   ├── model/                     # SizeId / Difficulty / BoardDefinitions / 设置 / UI 状态
│   └── session/GameSession.kt     # ★ 一局游戏状态机（输入/笔记/提示/撤销/胜负）
├── data/                          # Room + DataStore + Repository
│   ├── entity/ dao/               # game_records（历史成绩）/ saved_game（续玩）
│   ├── SudokuDatabase.kt
│   ├── SettingsDataStore.kt
│   └── repository/
├── viewmodel/                     # Home / Game / History / Settings / Achievements + AppContainer
├── ui/
│   ├── theme/                     # 主题（配色/深色/字号）、Color、Typography
│   ├── components/                # 分段控件、开关行等
│   ├── home/ game/ history/ settings/ achievements/   # 各界面
├── nav/                           # NavRoutes + NavHost
└── util/                          # 时间格式化、颜色解析、震动/提示音
```

## 算法（直接复用）

`SudokuEngine` 与 HTML 版逐行对应，仅把全局可变尺寸（`N/BROWS/BCOLS`）改为显式参数：

- `canPlace`：行 / 列 / 宫冲突检测。
- `fillGrid`：回溯生成完整合法解。
- `countSolutions(limit)`：解的数量统计（用于唯一解校验）。
- `generatePuzzle(config, cluesTarget)`：先填完整解，再逐格随机挖洞，
  每步用 `countSolutions(..., 2)` 校验，非唯一解则还原，**保证题目唯一解**。

`BoardDefinitions` 保存各尺寸难度与线索数目标（与原 `BOARD_TYPES` 一致）：

| 棋盘 | 难度 → 线索数 |
| --- | --- |
| 四宫格 4×4 | 简单 12 · 普通 10 |
| 六宫格 6×6 | 简单 26 · 普通 23 · 困难 21 |
| 九宫格 9×9 | 简单 45 · 普通 38 · 困难 32 · 专家 28 · 地狱 25 |

> 算法经 Node 脚本验证：三种尺寸均满足「唯一解 / 解可解 / 结果合法 / 生成耗时可控」。
> 「地狱」难度挖洞耗时约 300–400ms，已在生成时显示加载指示，避免卡顿。

## 通关庆祝系统（ui/fx/）

通关时弹出全屏庆祝界面，模块化设计、可直接集成：

```
ui/fx/
├── CelebrationConfig.kt         # 配置（粒子数/时长/音量/淡入淡出等，见 KDoc）
├── CelebrationController.kt     # ★ 统一入口：initialize → play(info) → stop → release
├── CelebrationAudio.kt          # 程序化音频：胜利琶音 + C-G-Am-F 庆祝 BGM（全异步）
├── CelebrationParticles.kt      # Canvas 粒子系统：双角礼花炮 + 中央爆发 + 顶部飘落
└── WinCelebrationOverlay.kt     # 全屏界面：居中面板 + 吉祥物 + 过渡动画
```

**功能对照**：
1. **居中信息面板**：关卡名称（如「九宫格 · 地狱」）、得分（滚动计数）、
   评级徽章（S/A/B/C，金/紫/蓝/灰）、三星评级（逐颗弹入）、用时/错误/提示、
   新纪录徽章、再来一局 / 返回首页。
2. **视觉反馈**：面板弹簧入场 + 六个元素错峰淡入；金色吉祥物持续弹跳 +
   落地挤压 + 挥手举杯；三层粒子系统（帧时钟驱动，掉帧自动补偿 dt）。
3. **音频体系**：胜利瞬间同步触发 C 大调上行琶音 + 结尾和弦 + 闪光音，
   随后庆祝 BGM 淡入；关闭时 BGM 淡出。**全部程序化合成（22.05kHz PCM），
   零音频资源、完全离线**；AudioTrack 流式播放，写盘/生成均在 IO/Default 线程。
4. **非阻塞**：粒子轨迹在 `Dispatchers.Default` 批量生成，主线程只做绘制；
   音频在 `Dispatchers.IO` 播放；得分计数走 Compose 动画框架。无同步等待。
5. **生命周期**：`CelebrationController` 负责初始化/触发/停止/释放；
   `WinCelebrationOverlay` 内部管理入场（`LaunchedEffect` 触发）与
   退场（先播 240ms 退出动画再回调）；页面销毁 `release()` 释放全部音频资源。
6. **评分模型**（`domain/game/ScoreCalculator.kt`，纯 Kotlin 可单测）：
   基础分 = 1000 × 尺寸倍率 × 难度倍率；时间项 = 基础分 × (0.4 + 0.6 × 速度系数)；
   罚分 = 错误 × 50 + 提示 × 80；速度系数 = 标准用时 / 实际用时（0.25~2.0）。
   星级：★★★ 零错误且提示 ≤1 且快于标准 1.25 倍；★★ 错误 ≤2 且提示 ≤3 且
   速度 ≥0.4；其余 ★。评级按得分/基础分：≥0.9 S、≥0.7 A、≥0.5 B、其余 C。

**配置说明**（`CelebrationConfig`，默认值见文件 KDoc）：

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | true | 总开关（关闭后仅不播音频与粒子，面板仍显示） |
| `respectSoundSetting` | true | 联动设置页「提示音」开关 |
| `particleCount` | 120 | 单次庆祝粒子总数（0~600） |
| `particleDurationMs` | 3400 | 粒子生命周期 |
| `cornerCannonRatio` | 0.45 | 双角礼花炮粒子占比 |
| `centerBurst` | true | 面板中央爆发 |
| `scoreCountUpMs` | 900 | 得分滚动动画时长 |
| `panelEntranceMs` / `elementStaggerMs` | 420 / 130 | 面板入场时长 / 元素错峰间隔 |
| `stingerVolume` / `bgmVolume` | 0.85 / 0.32 | 提示音 / BGM 音量（0~1） |
| `bgmFadeInMs` / `bgmFadeOutMs` | 600 / 400 | BGM 淡入 / 淡出时长 |
| `bgmLoopSeconds` | 8 | BGM 循环体长度（2~16 秒，越长越吃内存） |

修改配置后调用 `controller.updateConfig(config.copy(...))` 热生效（音频参数下次播放时读取）。
集成只需 3 行：`remember { CelebrationController() }` + `DisposableEffect { onDispose { release() } }` +
`LaunchedEffect(winInfo) { winInfo?.let { controller.play(it) } }`。

## v1.1.0 更新（布局与美化打底批次）

按用户 16 条产品建议拆分多批次落地，本版为第一批「布局与美化打底」，为后续改动提供结构性载体：

- **底部常驻导航栏**：首页 / 历史 / 成就 / 设置 四入口，选中态跟随主题 `primary`；
  对局全屏时自动隐藏底栏。首页顶栏原先散落的历史/成就/设置快捷图标已并入底栏。
- **全局主题色严格绑定**：棋盘选中格/数字沿用主题 `primary`（绿/蓝/灰配色切换时全局一致）；
  移除首页顶栏混用的硬编码图标色（橙/琥珀/蓝）。
- **卡片层次**：引入 `lifted()` 浅阴影 + 发丝描边，续玩卡、尺寸卡、难度卡、历史/成就行、
  设置模块卡均浮起，页面底色与卡片对比更清晰。
- **图标质感统一**：底栏统一使用「未选中描边 / 选中填充」图标对，线条粗细与配色风格一致。
- **空状态占位**：历史记录、成就页在无数据时显示 Canvas 插画（时钟 / 放大镜）+ 引导文案。
- **设置重组为模块卡片**：外观 / 玩法 / 反馈 / 数据 / 关于 分组收纳为 `ModuleCard` 模块卡。
- **主界面选中高亮 + 难度色延伸**：选中尺寸卡放大（scale 1.07）并加强描边；难度专属色条
  从首页难度卡延伸至成就页、历史记录（按尺寸+难度取色）。
- 注：原「隐藏不匹配的难度」理解为难度始终与所选尺寸对应（已满足），未做额外隐藏。

## v1.2.0 更新（游戏体验批次 B）

按用户建议的「功能」类落地，并在其要求下把底部导航图标改为彩色：

- **对局暂停（①）**：顶栏新增暂停 / 继续按钮；暂停后棋盘覆盖「已暂停」遮罩，点击遮罩或
  「继续游戏」恢复；计时冻结（`GameSession.paused` 经 `GameUiState.isPaused` 下发，
  计时刷新循环在暂停时停更）。切后台仍自动暂停并保存。
- **每日提示限制（②）**：提示改为「每日额度」机制（默认 20 次/天，跨天自动归零），
  存于 DataStore（`hint_date` + `hint_used`）。提示按钮实时显示「提示 N」剩余次数，
  用完置灰并提示「今日提示次数已用完」。撤销按钮此前已存在，本版保留。
  - 注：原「每局 5 次」硬上限已撤销，由每日额度统一管控（更贴合「限制每日提示次数」）。
- **进阶辅助（⑥）**：设置新增「候选数自动排除」开关——开启后，每填入一个数字即自动清除
  其所在行/列/宫同伴格笔记中的该候选（幂等、O(n²)，n≤9 无感）。原有的「错误标记/警告」开关保留。
- **底部图标改为彩色**：首页(绿) / 历史(橙) / 成就(紫) / 设置(蓝) 各用专属彩色；选中态用
  填充图标 + 专属色，未选中用淡彩色描边图标（覆盖 v1.1.0 的纯主题色绑定，按用户最新要求）。

## v1.8.0 更新（成就墙扁平化）

- **去除边框 / 阴影 / 线宽**：成就墙卡片移除 `lifted` 阴影 + 发丝描边、1dp 高亮边框、
  以及按下时的外发光阴影，实现完全扁平化；卡片状态改由「底色 + 难度色条 + 图标 + 文案」表达。
- **难度色保持不变**：左侧 5dp 难度色条与名称前 18dp 难度图标的颜色、位置、语义均不变。
- **按下反馈保留**：微缩放（0.965）+ 底色加深（已解锁 +5% / 未解锁 +8% 透明度），
  去掉阴影后触觉反馈不丢失。
- 页面顶部汇总卡与「数据统计 / 通关分布」ModuleCard 维持原有浅阴影层次，未随扁平化
  （避免扁平卡片与页面同色糊成一片）。
- 版本：versionCode 15 · versionName 1.8.0。

## v1.7.0 更新（图标简化 / 成就墙净化 / 难度标识三处统一）

- **继续游戏图标重设计**：移除圆形徽章装饰，改为纯播放键图标（30dp，Filled 风格）——
  有进行中局时用主题主色、无局时置灰（onSurfaceVariant 50%），无底色依赖，
  任意尺寸与背景下均清晰可辨。
- **成就墙净化**：移除未解锁卡内部的灰色宽线（原底部空进度底框），
  卡片只保留「难度图标 + 名称 + 状态图标 + 文案 + 前往挑战」，布局干净统一。
- **难度色条 + 难度图标三处统一**：新增共享映射 `util/DifficultyIcon.kt`
  （首页私有函数上提为全局唯一来源）：历史记录卡与成就卡均在「左侧 5dp 难度色条」+
  「名称左侧 18dp 难度图标（同色）」；图标语义固定为 简单=笑脸/普通=平静脸/困难=难过脸/
  专家=奖章/地狱=火焰，与首页难度卡一致；成就卡行尾另保留奖杯/锁状态图标。
- 版本：versionCode 14 · versionName 1.7.0。

## v1.6.0 更新（成就墙交互与动效增强）

在 v1.5.0 分区梳理的基础上，按「数独特性 + 现代化 UI」方案补强交互层：

- **难度色条回归**：成就卡左侧恢复 5dp 难度专属色竖条（简单绿/普通蓝/困难黄/专家紫/地狱红，
  与首页难度卡、历史记录的颜色延伸体系一致；未解锁时压暗至 45% 保持灰度观感）。
- **点击选中态**：卡片按下时主题色外发光阴影（10dp，主色 55% 透明度）+ 微缩放（0.965）+
  背景微加深，松开即恢复（点击仍直接进入该难度新局）。
- **奖杯弹出入场**：成就卡按全局序号错峰弹入（45ms 阶梯，scale 0.92→1 + 淡入），
  已解锁卡呈现「奖杯弹出」的仪式感。
- **长按分享**：已解锁卡长按弹出系统分享面板，文案含「尺寸 · 难度 + 局数 + 最快用时 +
  最高得分」。
- **数据行排版**：卡片内「最快/平均」改为左对齐（标签弱化 + 数值加粗），行高加到 18sp。
- **文案**：未解锁卡引导改为「前往挑战」。
- 版本：versionCode 13 · versionName 1.6.0。

## v1.5.0 更新（首页图标 / 历史评分 / 成就墙梳理）

- **继续游戏彩色图标（①）**：首页续玩卡新增主题主色圆形徽章 + 白色播放键，无进行中局时徽章置灰，
  「继续」语义一眼可辨。
- **历史记录分数与星级（②）**：通关记录新增 score / stars 两列（Room v1→v2 迁移，
  旧记录默认 0 按「无评分」优雅降级）；历史卡片右侧在用时下方展示「★☆ 评级 + 得分」，
  旧记录仍只显示用时与「点击复玩」。
- **成就墙重新梳理（③）**：由 10 张卡平铺改为**按棋盘尺寸分区**（四宫格 / 六宫格 / 九宫格），
  每区带主色短竖条标题 + 「n/m 已通关」进度；区间距 22dp、卡间距 12dp、卡片加高到 142dp、
  圆角 16dp，内容行距全面放宽，解决拥挤感。
- **搜索栏高度优化（④）**：历史页搜索框由 OutlinedTextField（约 56dp）改为 46dp 圆角胶囊
  自定义搜索栏（BasicTextField + 主题色光标），更紧凑且与筛选 chips 视觉统一。
- 版本：versionCode 12 · versionName 1.5.0。

## v1.4.0 更新（成就页视觉打磨批次）

针对 v1.3.0 成就页的用户反馈，聚焦「色彩统一、状态区分、排版细节、导航与引导」：

- **主题色统一**：底部导航选中态（图标 + 文字 + 椭圆指示块）改为绑定全局主题主色
  （`NavigationBarItemDefaults.colors(indicatorColor = primary×16%)`，修复 Material3 默认
  淡紫指示块与主题割裂的问题；未选中保留各 Tab 专属淡彩描边图标）。
- **成就卡状态规范**：已解锁卡片统一主题主色（淡背景 + 主色高亮边框 + 3dp 阴影，奖杯/局数
  同为 theme primary，不再各用难度专属色）；未解锁卡片降饱和置灰（surfaceVariant 35% 透明度、
  弱边框、1dp 轻阴影），两类状态视觉权重明确拉开。
- **图标风格统一**：成就卡的奖杯/锁统一改用 Outlined（圆角线条）图标，线条粗细一致；
  网格卡无选中态，「疑似选中」的困惑随统一高亮一并消除。
- **未解锁区装饰**：卡片底部新增空进度底框（弱化灰 track）暗示待完成，解决大片空白单调问题。
- **数据行排版**：「最快/平均」改为「标签（弱化）+ 数值（加粗）」双端对齐行，行间留白。
- **标题层级**：「成就墙」小标题与上方模块间距拉大（20dp），下间距 12dp，加粗引导。
- **挑战引导**：成就墙卡片整卡可点（直达该尺寸+难度新局）；未解锁卡新增「去挑战 ›」
  弱化文字引导，替代原先单一的「完成一局即可解锁」。
- 版本：versionCode 11 · versionName 1.4.0。

## v1.3.0 更新（数据与历史批次 C）

按用户建议的「数据 / 历史」类落地，共四项：

- **丰富统计（③）**：成就页新增「数据统计」模块——平均耗时、最快用时、当前/最长连续获胜天数
  （按本地自然日计算，昨天有局则接力、断档归零）、活跃天数、最近一局；汇总卡扩展为
  总完成局数 / 平均用时 / 连续获胜(天) / 地狱通关。统计逻辑在 `domain/game/StatsAnalyzer.kt`
  （纯 Kotlin，可单测）。
- **成就网格 + 通关分布（⑩）**：成就墙由列表行改为两列网格卡片（已解锁：局数/最快/平均；
  未解锁置灰加锁标）；新增「各难度通关分布」占比条（局数降序，动画过渡）。
- **历史筛选 / 搜索 + 日期分组（④）**：历史页新增搜索框（按尺寸/难度/用时模糊匹配）、
  尺寸筛选 chips（全部/四/六/九宫格）与难度筛选 chips（随尺寸联动，切换尺寸时自动清掉
  无效难度）；列表按自然日分组（今天/昨天/M月d日/yyyy年M月d日），保留点击复玩。
- **导入题目 + 进度备份恢复（⑤）**：设置「数据」模块扩展——
  - 导入题目：粘贴题目 JSON（`size`/`puzzle` 必填，`solution` 可选），解析校验后直接开局；
    缺解或解与题面冲突时由 `SudokuEngine.solve()` 自动求解，题目无解则拒绝开始。
  - 备份全部数据：历史记录 + 进行中进度导出为 JSON（复制到剪贴板）。
  - 恢复数据：粘贴备份 JSON 恢复（覆盖现有记录与进度，对话框内明确警示）。
- 版本：versionCode 10 · versionName 1.3.0。

## 功能对照（与 Web 版一致）

- 首页：续玩卡片、棋盘尺寸切换、难度卡片（完成局数 + 最佳用时 + 进度条）。
- 对局：可变尺寸棋盘、候选数笔记、长按切换笔记、一键自动笔记、
  提示（直接填答案 / 仅给候选数，每局 5 次）、撤销 / 重做、重置、擦除、
  同行列宫高亮、同值高亮、错误标红、剩余次数角标、计时（切后台自动暂停）。
- 通关弹窗（是否刷新最佳记录）、再来一局、返回首页。
- 历史记录（点击复玩，复玩不计入成绩）、成绩统计。
- 成就页：总完成局数 / 地狱通关 / 全难度最快 / 各难度明细。
- 设置：主题（跟随系统/浅/深）、配色（蓝/绿/灰）、字号（标准/大）、
  选区高亮色、长按笔记、错误标记、同值高亮、自动笔记按钮、快速生成、
  震动、提示音、导出题目 JSON（复制到剪贴板）、清除记录、隐私声明。

## 与 Web 版的差异 / 说明

- **数据持久化**：Web 用 `localStorage`，安卓用 Room（历史/续玩）+ DataStore（设置）。
- **导出题目**：Web 直接下载文件；安卓端为复制 JSON 到剪贴板（无需存储权限）。
- **震动 / 提示音**：用系统 `Vibrator` 与 `ToneGenerator` 实现，无需资源文件。
- **字号缩放**：通过缩放 Material3 `Typography` 全局生效。
- 应用图标为五密度 `mipmap-*/ic_launcher.png`（由用户供图中心裁剪生成），
  含圆形变体 `ic_launcher_round.png`。
- **震动反馈**：需在清单声明 `android.permission.VIBRATE`，否则 `Vibrator.vibrate()`
  会静默失败（曾因缺失该权限导致小米 13 上震动无效）。

## 隐私

纯单机离线应用：不申请网络 / 位置 / 存储 / 通讯录等权限，所有数据仅存于应用私有目录，
卸载即清除。
