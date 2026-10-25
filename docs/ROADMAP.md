# 后续开发可行性建议（Roadmap）

> **版本**：v2（第二次代码盘点）
> **复测日期**：2026-10-25 · **基线**：v1.9.3 / versionCode 19 / commit `5cb33da`
> 上一版（v1，2026-10-20）基于同一代码库；本版**逐条复测指标**并引入了「当下时间点的外部事实」（Play 目标 API 要求、Compose 1.12 工具链门槛）。
> 相对 v1 的主要变化：
> 1. **修正**硬编码中文串口径（192 → **190 行 / 222 个字面量**）、无障碍锚点（19 → **20**）；
> 2. **新增**一个真实缺陷：历史记录被 `LIMIT 200` 静默截断（详见 §三.3）；
> 3. **上调**依赖现代化的优先级：P1 → **P0**。原因是间隔一年后，工具链差距已从「偏旧」变成「跨代」；
> 4. **新增** 5 项此前未盘到的工程缺口（无版本目录、无 Baseline Profile、无 Compose 稳定性报告、无插桩测试、wrapper 残缺）。

**成本口径**：小 ≤ 半天，中 ≈ 1 天，大 ≈ 2–3 天（含回归验证）。

---

## 一、当前技术栈

| 层 | 选型 | 项目当前 | 外部现状（2026-10） | 差距 |
| --- | --- | --- | --- | --- |
| 构建 | AGP / Gradle | 8.5.2 / 8.7 | AGP 9.x（Compose 1.12 要求 ≥ 9.1.x） | **跨一个大版本** |
| 语言 | Kotlin | 1.9.24 | 2.2.x（K2 编译器默认） | **跨一个大版本** |
| UI | Compose BOM | 2024.06.00 | 2026.08.00（Compose 1.12） | **落后约 2 年** |
| Compose 编译器 | 配置方式 | `composeOptions { kotlinCompilerExtensionVersion = '1.5.14' }` | 已迁移到 `org.jetbrains.kotlin.plugin.compose` 插件 | **范式变更**，旧写法在新版已不可用 |
| 导航 | Navigation Compose | 2.7.7（字符串路由） | 2.8+（` @Serializable ` 类型安全路由） | 可用 |
| 数据 | Room | 2.6.1（**kapt**） | 2.7.x + KSP（Room 已支持 KSP） | kapt 编译更慢 |
| 配置 | DataStore Preferences | 1.1.1 | 同期 | 无 |
| 异步 | kotlinx-coroutines | 1.8.1 | 同期 | 无 |
| 平台 | compileSdk / targetSdk / minSdk | 34 / **34** / 24 | **Play 自 2026-08-31 起要求 target ≥ 36** | **已在 Play 门槛之下** |
| 测试 | JUnit4 + Robolectric + sqlite-jdbc | 37 个纯 JVM 用例 | — | 无插桩测试 |

**工程特征**：单 module、手写 DI（`AppContainer`）、`domain` 层不依赖 Android/Room、release 已开 R8 + 资源压缩并用自签名密钥签署。
体量：51 个 Kotlin 文件 / 6,900 行（main）、8 个测试文件 / 37 个用例、APK 2,801,603 字节。

> ⚠️ 上述「外部现状」在动手前**必须到官方发布页二次确认版本号**，本表作用是给出差距量级，不是可直接粘贴的配置。

---

## 二、已完成的核心能力

- **对局**：4×4 / 6×6 / 9×9 三种棋盘、**10 个「尺寸×难度」组合**；唯一解生成与求解、提示（每日额度制）、撤销/重做、笔记与自动笔记、自动排除候选、错误标红、同行列宫与同值高亮、擦除、重置、切后台自动暂停计时。
- **存档**：多槽存档（`slotId = sizeId:diffId`），尺寸难度互不覆盖，覆盖前有确认弹窗。
- **历史与统计**：按自然日分组、尺寸/难度筛选、关键词搜索、星级与得分；平均/最快用时、连胜天数、活跃天数、难度分布。
- **成就墙**：按棋盘尺寸分区，已解锁/未解锁两态，长按分享。
- **设置**：主题（跟随系统/浅/深）、配色、字号、高亮色、长按照笔、提示模式、震动、提示音、快速生成。
- **数据进出**：题目 JSON 导入（缺解自动求解、无解拒绝）、全量备份与恢复。
- **表现层**：通关庆祝系统（程序化合成音频 + 粒子 + 吉祥物动画，**零资源文件**）。
- **开源配套**：GPL-3.0、中英双语 CONTRIBUTING、Issue 表单、Discussions、由 Robolectric 渲染生成的 README 截图与交互动图。
- **隐私**：全 manifest 仅申请 `VIBRATE` 一个权限，无任何网络/存储/电话权限——「纯离线、零收集」是可在代码层面自证的。

---

## 三、主要挑战与限制（附复测证据）

| # | 限制 | 证据 |
| --- | --- | --- |
| 1 | **国际化缺失** | `ui` + `nav` 共 **190 行**含硬编码中文、**222 个字符串字面量**；`res/values/strings.xml` 仅有 1 条（应用名），**无 `values-en`** |
| 2 | **wrapper 残缺 + 无 CI** | `git ls-files` 显示跟踪的只有 `gradle/wrapper/gradle-wrapper.properties`，**没有 `gradlew` / `gradlew.bat` / `gradle-wrapper.jar`**；本机仓库根也确实不存在这些文件。构建依赖机器本地的 Gradle 8.7。` .github/ ` 下仅 3 个 Issue 模板，**无 workflows** |
| 3 | **🔴 历史记录被静默截断（新发现）** | `HistoryViewModel.kt:40` `allRecords = statsRepo.recentRecords(200)` → DAO `SELECT * ... ORDER BY date DESC LIMIT :limit`；而 `:43` `totalCount = allRecords.map { it.size }`。**记录数超过 200 后：列表只显示最新 200 条、"总数"恒为 200、且搜索只能命中这 200 条**，无任何分页或"还有更多"提示。注释写的"未筛选时的总局数"与实际行为不符 |
| 4 | **落子即全量写库** | `saveCurrent()` 被调用点：`GameViewModel` 106 / 171 / 189 / 201 / 231 / 246 + `GameScreen:118`（`ON_PAUSE`），共 **6 个 change path**。每次 upsert 整条快照并触发 `observeLatestSaved` / `observeAllSlots` 两个 Flow 重放 |
| 5 | **统计在内存聚合（且无上限）** | `HomeViewModel:35` 与 `AchievementsViewModel:49` 都直接消费 `statsRepo.allRecords()`（**全表无 LIMIT**），再在 Kotlin 侧 `filter`/`groupBy`；DAO 里现成的聚合只有一条 `MIN(timeMs)` |
| 6 | **依赖栈跨代落后** | 见 §一。「Compose 编译器要把 `composeOptions` 换成 Kotlin Compose 插件」是不可回避的范式变更；`targetSdk 34` 已低于 Play 现行要求 |
| 7 | **单文件偏大** | `WinCelebrationOverlay` 662 / `AchievementsScreen` 496 / `GameSession` 468 / `GameScreen` 458 / `HistoryScreen` 453 / `SettingsScreen` 408 行 |
| 8 | **列表未用 Lazy** | 全项目 `LazyColumn/LazyRow/LazyVerticalGrid` 仅 **4 处**，而 `verticalScroll` **6 处**（成就墙、首页等仍为 `Column + forEach`） |
| 9 | **测试盲区** | `BoardModels` / `SettingsModels` / `GameSnapshot` 无直接用例；**完全无 `app/src/androidTest`**；UI 侧只有渲渲染与点击截图，**没有断言型交互测试** |
| 10 | **无障碍不完整** | 全项目仅 **20 处** `contentDescription`；字号档位少；未做 TalkBack 全量遍历 |
| 11 | **版本号手工同步** | `versionCode/versionName`、README 更新日志、README 产物说明、`release/` 文件名四处需人工一致 |
| 12 | **无版本目录 / 无性能基建** | 无 `gradle/libs.versions.toml`（依赖散写在 `app/build.gradle`）；**未配置** Baseline Profile、Compose 编译器稳定性报告、strong skipping |

---

## 四、改进方案

按「收益 ÷ 成本」排序。**P0 建议下个版本做完，P1 是 1–2 个版本内，P2 之后。**

### P0 — 正确性 + 工程地基（✅ 已全部落地于 v1.10.0）

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **修历史截断** | `totalCount` 改走 DAO 新增的 `SELECT COUNT(*)`；展示列表改走全表（页面本就是 `LazyColumn`，只渲染可见项，故无需分页） | 消除第 3 条**静默数据丢失**；老用户可以搜到自己的历史记录 | 小（半天，含单测） | 低 |
| **补 wrapper + 建 CI** | `gradle wrapper` 把 `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` **全部入库**（`.gitignore` 并未忽略它们，是当初就没生成）；CI 跑 `testDebugUnitTest` + `assembleDebug` + 缓存 Gradle/JDK 17 | PR 自动验证；贡献者零配置即可构建；顺带解锁后续所有自动化 | 中（半天–1 天） | 低 |
| **存档写库去抖** | `saveCurrent()` 改 500 ms 去抖；`onPause`/暂停/`exitToHome`/通关时**强制 flush**（`saveCurrentNow()`） | 写库与 Flow 重放次数降一个数量级，输入更跟手，降低 SQLite 写放大 | 小（1–2 小时） | **中**。去抖必须配强制落盘，否则杀进程丢步。验收：填数后立刻杀进程再进，进度不能丢 |
| **统计下推 SQL** | DAO 加 `COUNT(*)`、`AVG(timeMs)`、`GROUP BY sizeId,diffId` 聚合查询，替换 `HomeViewModel` / `AchievementsViewModel` 的全表拉取 | O(n)→O(1)；记录增长后首页/成就页不再变慢。`StatsAnalyzer` 作为纯函数保留，只换数据来源 | 中（半天 + 补测试） | 低 |
| **定分发渠道** | 明确「只走 GitHub Releases / F-Droid — 暂不上架 Play」还是「要上架 Play」 | 决定下面 §P1 的 targetSdk 升级是"可选"还是"阻塞"，避免盲目投入 | 小（只需决策） | 无 |

**落地记录（v1.10.0）**：
- 新增 `HistoryViewModelTest`（4 例）：用 250 条真实 Room 数据回归「截断」缺陷，
  覆盖总局数、列表完整性、跨 200 条的搜索命中、空状态语义、筛选联动。
- 新增 `GameRecordDaoTest`（6 例）：覆盖 `COUNT(*)` / 全局聚合 / `GROUP BY` /
  `MAX(score)` / `MAX(date)`，以及**「本地自然日」的 SQL 与 Kotlin 口径逐值一致**。
- `AppContainer` 增加 `databaseOverride` 参数，测试可注入内存库
  （对应下方 P3「`AppContainer` 可注入化」，已提前完成）。
- 顺带修出一致性问题：`StatsAnalyzer.dayNumber` 原口径对 UTC+8 整体比「本地日序号」
  小 1，与下推的 SQL 对不上；现统一为 `(时刻 + 时区偏移) / 86400000`。
- 单测 37 → **47**；`assembleDebug` / `assembleRelease` 均通过，编译零告警。

### P1 — 依赖现代化（建议独立成版 v1.11.0，出问题好回滚）

> **强烈建议分两步跳，不要一步到位跳到 Compose 1.12。**

| 步骤 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **①Kotlin 2.x + KSP + 版本目录** | Kotlin 升 2.x；Room 编译器 kapt → **KSP**；引入 `gradle/libs.versions.toml` 集中版本；Compose 编译器从 `composeOptions` 换成 `org.jetbrains.kotlin.plugin.compose` 插件 | 编译更快；解锁新版库与语言特性；版本一处修改；扫清后续升级路上的第一个障碍 | 中（约 1 天） | 中。需全量回归——现有 **37 个用例 + Robolectric 渲染用例**是很好的安全网 |
| **②Compose BOM 与 Navigation** | 在①稳定的基础上，BOM 升到与 Kotlin 2.x 匹配的中间版本（不必直接 2026.08.00）；Navigation 升 2.8.x 并改 ` @Serializable ` 类型安全路由，消灭字符串拼路由 | 用上新 Build/Test API；路由拼写错误与参数事故在编译期暴露 | 中（约 1 天，与①合并做更省） | 中。逐个路由核对参数；UI 比对用现有 `ScreenshotTest` 的 PNG 做前后差异 |
| **③targetSdk / compileSdk** | 若渠道决策是"要上架 Play"，则必须先装 platform 36/37 再升 `targetSdk 36`（本机目前**只有 android-34**）。若只做 GitHub/F-Droid 分发，此项降级为 P2 | 满足 Play 准入（2026-08-31 起新包要求 ≥36）；可用新平台 API 与行为 | 中（约 1 天，含 SDK 下载） | 中。需回归权限/存储/通知相关行为 |

> **不建议现在直接冲 Compose 1.12**：它要求 `compileSdk 37` + `AGP ≥ 9.1.x`，本机 SDK 只有 34，等于要同时跳 AGP 大版本、Gradle 大版本、SDK 平台和 Compose 两年代差——变量太多，出问题无法归因。

### P1 — 结构性收益（可穿插做）

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **国际化（i18n）** | 222 个字面量分批抽到 `strings.xml`，Compose 侧统一 `stringResource()`；先 `values-zh` 再 `values-en`；CI 加"新增硬编码中文即告警"检查 | 打开多语言与海外分发可能；文案集中校对 | 大（2–3 天，量大但机械，分 3 批） | 低。注意带参数串、数字与单复数 |
| **断言型 UI 测试** | 给现有 Robolectric 用例加状态断言（填数后断言某格文本、撤销后断言恢复、笔记模式断言候选数），而不是只出图 | 交互回归可自动发现，不再只靠肉眼 | 中（半天） | 低 |
| **拆大文件** | `WinCelebrationOverlay` 拆"弹层/动画编排"与"绘制"；`AchievementsScreen` 抽出 `AchievementCard`；`GameSession` 视情况分离"撤销栈" | 单文件降到 300 行内，定位更快 | 中（半天–1 天，纯重构） | 低。**复用 `ScreenshotTest` 做视觉回归**：拆分前后各跑一次比对 PNG |

### P2 — 提升上限

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **性能基建** | 在依赖升级到 Kotlin 2 之后，开启 Compose 编译器**稳定性报告**，给关键 `@Composable` 参数补 `@Stable`/不可变集合；评估 Baseline Profile | 减少无效重组，改善启动与滚动帧率。**先把报告跑出来再决定优化点，不要凭感觉改** | 中（1 天：0.5 天配报告 + 0.5 天针对性改） | 低。无 measurable baseline 前不要动 |
| **无障碍专项** | 补齐 `contentDescription` 与语义合并、触控目标 ≥48 dp、字号档位扩展、TalkBack 全量遍历实测 | 无障碍合规，覆盖更多用户 | 大（2–3 天） | 低 |
| **版本号自动化** | CI 校验 README 正文/更新日志/`release/` 文件名与 gradle 中的版本一致 | 消除四处手工同步出错（历史上已出现过更新日志条目插错位置） | 小（2 小时） | 低 |
| **列表改 Lazy** | 成就墙与首页改 `LazyColumn`/`LazyVerticalGrid` | 数据量增长后仍流畅 | 小（2 小时） | 低。**当前 15 张卡规模收益很小**，建议等难度组合或记录数增加再做 |

### P3 — 可选探索

- **多主题/多尺寸批量渲染**：截图流水线已就位，加几组 `qualifiers` 即可产出深色主题与平板配图（小）。
- **备份格式版本治理**：补显式版本校验与错误提示（小）。
- ~~**`AppContainer` 可注入化**~~：**已在 v1.10.0 完成**——`AppContainer(app, databaseOverride)` 允许测试注入内存库，`HistoryViewModelTest` 即得益于此。
- **插桩测试**：工程上缺失 `androidTest` 目录。但本机模拟器**经验证不可用**（`emulator -accel off` 直接段错误，WHPX=Disabled、AEHD 未装），因此在修复模拟器之前，**Robolectric 是本机唯一可行的 UI 测试路径**，不建议为此投入。

---

## 五、不建议的方向（与项目定位冲突）

- **云同步、在线排行榜、在线对战、账号体系**：与「纯单机离线、不申请网络权限、不收集任何数据」直接冲突。此前提已写入 README 隐私章节与 Issue 功能建议表单。
- **接入崩溃上报 / 埋点 SDK**：同上——manifest 现状只有 `VIBRATE`，这个项目最值钱的卖点之一就是这份自证的干净。除非做成默认关闭的可选项。
- **引入 Hilt/Koin 等 DI 框架**：单 module + 少量 ViewModel 的体量下，手写 `AppContainer` 可读性更好，替换收益不足以抵消迁移成本。

---

## 六、建议的落地节奏

维持「一批功能性改动 = 一个版本号」的惯例：

1. ~~**v1.10.0（正确性与地基）**：修历史截断 → wrapper 入库 + CI → 存档去抖 → 统计下推 SQL~~
   —— **已完成**（2026-10-25，versionCode 20）。全部低风险，由 47 个用例与渲染回归护航。
   ⬜ 仍未完成：**定分发渠道**（这是唯一还悬着的 P0 决策，直接决定第 2 步里 targetSdk 是否必需）。
2. **v1.11.0（依赖现代化）**：分步做 Kotlin 2.x + KSP + 版本目录 → Compose BOM/Navigation → （若决定上架）targetSdk 36。
   *升级类改动单独成版，出问题易回滚；每步之间跑一遍全量测试。*
3. **v1.12.0（i18n）**：分 3 批抽取文案，配套 CI 检查（CI 已就位，这条现在是纯代码量问题）。
4. **之后的维护版**：性能基建、无障碍专项、版本号自动化。

**每个版本交付前保持现有验收标准**：测试全绿 + `assembleRelease` 成功 + `apksigner verify` 通过 + README 更新日志与 `release/` 产物同步。
