# 后续开发可行性建议（Roadmap）

> 本文基于对当前代码库的实际盘点（51 个 Kotlin 文件 / 约 6900 行、37 个单测、
> release APK 2.7 MB = 2,801,603 字节）写成，每条建议都标注了**依据**与**成本量级**。
> 成本口径：**小** ≤ 半天，**中** ≈ 1 天，**大** ≈ 2–3 天（含回归验证）。

---

## 一、当前技术栈

| 层 | 选型 | 版本 |
| --- | --- | --- |
| 构建 | Android Gradle Plugin / Gradle | AGP 8.5.2 / Gradle 8.7 |
| 语言 | Kotlin | 1.9.24（Compose Compiler 1.5.14） |
| UI | Jetpack Compose（Material3） | BOM 2024.06.00 |
| 导航 | Navigation Compose | 2.7.7 |
| 数据 | Room | 2.6.1（**kapt** 注解处理） |
| 配置 | DataStore Preferences | 1.1.1 |
| 异步 | kotlinx-coroutines-android | 1.8.1 |
| 平台 | compileSdk / targetSdk / minSdk | 34 / 34 / 24 |
| 测试 | JUnit4 + Robolectric + sqlite-jdbc | 4.13.2 / 4.12.2 / 3.46.1.3 |

工程特征：单 module、手写 DI（`AppContainer`）、`domain` 层不依赖 Android/Room、
release 开启 R8 + 资源压缩并用自签名密钥签署。

## 二、已完成的核心能力

- **对局**：4×4 / 6×6 / 9×9 三种棋盘，**10 个「尺寸×难度」组合**；唯一解生成与求解、
  提示（每日额度制）、撤销/重做、笔记与自动笔记、自动排除候选、错误标红、
  同行列宫与同值高亮、擦除、重置、切后台自动暂停计时。
- **存档**：多槽存档（`slotId = sizeId:diffId`），各尺寸难度互不覆盖，覆盖前有确认弹窗。
- **历史与统计**：按自然日分组、尺寸/难度筛选、关键词搜索、星级与得分；
  平均/最快用时、连续获胜天数、活跃天数、各难度通关分布。
- **成就墙**：按棋盘尺寸分区，已解锁/未解锁两态，长按分享。
- **设置**：主题（跟随系统/浅/深）、配色、字号、高亮色、长按照笔、提示模式、
  震动、提示音、快速生成等。
- **数据进出**：题目 JSON 导入（缺解自动求解，无解拒绝）、全量备份与恢复。
- **表现层**：通关庆祝系统（程序化合成音频 + 粒子 + 吉祥物动画，零资源文件）。
- **开源配套**：GPL-3.0、中英双语 CONTRIBUTING、Issue 表单、Discussions、
  由 Robolectric 渲染生成的 README 截图与交互动图。

## 三、主要挑战与限制（附证据）

| # | 限制 | 依据 |
| --- | --- | --- |
| 1 | **国际化缺失** | `ui` + `nav` 共 **192 处**硬编码中文串，`strings.xml` 仅 1 条（应用名） |
| 2 | **无 CI、无 gradle wrapper** | `.github/` 下只有 Issue 模板；仓库无 `gradlew`/`gradle-wrapper.jar`，外部贡献者需自备 Gradle 8.7 |
| 3 | **落子即全量写库** | `GameViewModel.saveCurrent()` 在填数/擦除/提示/重置/退出等 **6 处**被调用，每次 upsert 整条快照，并触发 `observeLatestSaved` / `observeAllSlots` 两个 Flow 重放 |
| 4 | **统计在内存聚合** | `StatsRepository.allRecords()` 返回全表 Flow，首页 `statsMap` 与成就页都在 Kotlin 侧遍历计算 |
| 5 | **依赖版本偏旧** | Kotlin 1.9.24 挡住了 Compose Compiler 插件与新版库；kapt 比 KSP 慢；Navigation 2.7.7 无类型安全路由；targetSdk 34 已低于主流商店要求 |
| 6 | **单文件偏大** | `WinCelebrationOverlay` 662 行、`AchievementsScreen` 496、`GameSession` 468、`GameScreen` 458、`HistoryScreen` 453 |
| 7 | **列表未用 Lazy** | 成就墙与首页均为 `Column + verticalScroll + forEach`（当前 15 张卡以内可接受，规模再增会退化） |
| 8 | **测试盲区** | `BoardModels` / `SettingsModels` / `GameSnapshot` 无直接用例；UI 侧只有渲染与点击，**没有断言型交互测试** |
| 9 | **无障碍不完整** | 全项目仅 **19 处** `contentDescription`，集中于主要交互；字号只有 2 档；未做过 TalkBack 全量遍历 |
| 10 | **版本号手工同步** | `versionCode/versionName`、README 更新日志、`release/` 文件名、README 正文四处需人工保持一致 |

## 四、改进方案

按「收益 ÷ 成本」排序。**P0 建议下次发版前做完，P1 是 1–2 个版本内，P2 之后。**

### P0 — 收益高、成本低

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **建 CI** | 新增 `.github/workflows/ci.yml`：JDK 17 + Gradle 缓存，PR/push 触发 `testDebugUnitTest` + `assembleDebug`；同时**把 `gradlew` / `gradle-wrapper.jar` 入库** | PR 可自动验证，杜绝「本地能过、别人拉下来不行」；同时消除第 2 条限制中最劝退的一环 | 中（半天–1 天；wrapper 入库后 CI 才顺） | 低。首次跑需下载依赖，可加缓存 |
| **存档写库去抖** | `saveCurrent()` 改为 500 ms 去抖写入；`onPause` / `onStop` / `exitToHome` / 通关时**强制 flush** | 写库与 Flow 重放次数下降一个数量级，输入更跟手；降低 SQLite 写放大 | 小（1–2 小时） | **中**：去抖必须配强制落盘，否则杀进程丢步。改完要用「填数后立刻杀进程再进」手工验证 |
| **统计下推 SQL** | 在 `GameRecordDao` 增加 `count(*)`、`avg(timeMs)`、`group by sizeId,diffId` 聚合查询，替换首页/成就页的内存遍历 | 复杂度 O(n)→O(1)，历史记录增长后首页不再变慢；与已有的 `minTimeMs` 风格一致 | 中（半天 + 补测试） | 低。domain 已有 `StatsAnalyzer`，可保留用于纯函数单测，只改数据来源 |

### P1 — 结构性收益

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **国际化（i18n）** | 192 处串分批抽到 `strings.xml`，Compose 侧统一 `stringResource()`；先落地 `values-zh`，再补 `values-en`；CI 加一条「新增硬编码中文即告警」的检查 | 打开多语言与海外分发可能；文案可集中校对 | 大（2–3 天，量大但机械，可分 3 批） | 低。注意带参数的串、数字与单复数 |
| **Kotlin 2.x + KSP** | Kotlin 升 2.0/2.1，Room 编译器切 KSP，Compose 改用 Kotlin 2 的编译器插件 | 编译更快；解锁新版依赖与语言特性；为后续升级清路 | 中（约 1 天） | 中。升级后需全量回归（正好有 37 个用例 + 渲染用例护航） |
| **拆大文件** | `WinCelebrationOverlay` 拆为「弹层/动画编排」与「绘制」；`AchievementsScreen` 抽出 `AchievementCard` 等独立可组合项；`GameSession` 视情况分离「撤销栈」 | 单文件降到 300 行内，改动定位更快 | 中（半天–1 天，纯重构） | 低。**复用 `ScreenshotTest` 做视觉回归**：拆分前后各跑一次，比对 PNG 无差异即安全 |
| **补断言型 UI 测试** | 给现有渲染/点击测试加状态断言（填数后断言某格文本、撤销后断言恢复、笔记模式断言候选数），而不是只出图 | 交互回归可自动发现，不再只靠肉眼 | 中（半天） | 低 |
| **类型安全导航** | Navigation 升 2.8.x，用 `@Serializable` 路由替代字符串拼接 | 消除路由拼写错误与参数传递事故 | 中（约 1 天，配合 Kotlin 2 一起做更省） | 中。需逐个路由核对参数 |

### P2 — 提升上限

| 项目 | 具体做法 | 预期收益 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| **升级 target/compileSdk** | 先装 platform 35/36，再升 `targetSdk`（若考虑上架，Play 已要求 35+） | 满足商店准入；可用新平台 API 与行为 | 中（约 1 天） | 中。需回归前台服务/存储/权限相关行为；本机 SDK 目前只有 34，要先安装 |
| **无障碍专项** | 补齐 `contentDescription` 与语义合并、触控目标 ≥48 dp、字号档位扩展、TalkBack 全量遍历实测 | 无障碍合规，覆盖更多用户 | 大（2–3 天） | 低 |
| **版本号自动化** | CI 校验 README 正文/更新日志/`release/` 文件名与 gradle 中的版本一致（或由脚本生成） | 消除四处手工同步出错（历史上已出现过版本条目插错位置） | 小（2 小时） | 低 |
| **列表改 Lazy** | 成就墙与首页改 `LazyColumn`/`LazyVerticalGrid` | 数据量增长后仍流畅 | 小（2 小时） | 低。**当前规模收益很小**，建议等难度组合或记录数增加再做 |

### P3 — 可选探索

- **多主题/多尺寸批量渲染**：截图流水线已就位，加几组 `qualifiers` 即可产出深色主题与平板尺寸配图（小）。
- **备份格式版本治理**：备份 JSON 已有 version 字段并兼容旧版单槽，可补一个显式的版本校验与错误提示（小）。
- **`AppContainer` 可注入化**：让单测可替换容器，进一步降低 UI 测试对真实 Room 的依赖（中）。优先级低于前两项，因为现状已能用真实 SQLite 跑通。

## 五、不建议的方向（与项目定位冲突）

- **云同步、在线排行榜、在线对战、账号体系**：与「纯单机离线、不申请网络权限、
  不收集任何数据」的设计前提直接冲突。此前提已写入 README 隐私章节与功能建议表单。
- **引入 Hilt/Koin 等 DI 框架**：单 module + 7 个 ViewModel 的体量下，
  手写 `AppContainer` 的可读性反而更好，替换收益不足以抵消迁移成本。

## 六、建议的落地节奏

维持现有「一批功能性改动 = 一个版本号」的惯例：

1. **v1.10.0（质量与工程化）**：CI + gradlew 入库 + 存档去抖 + 统计下推 SQL。
   —— 全部为低风险改动，且有 37 个用例与渲染回归护航。
2. **v1.11.0（依赖升级）**：Kotlin 2.x + KSP + Navigation 类型安全路由，配合拆分大文件。
   —— 升级类改动单独成版，出问题好回滚。
3. **v1.12.0（i18n）**：分 3 批抽取文案，配套 CI 检查。
4. **之后的维护版**：无障碍专项、targetSdk 升级、版本号自动化。

每个版本交付前保持现有验收标准：**测试全绿 + `assembleRelease` 成功 +
`apksigner verify` 通过 + 更新 README 更新日志与 `release/` 产物**。
