# 贡献指南 · Contributing to Sudoku Master

感谢你愿意为《数独大师》出力。这份指南面向第一次接触本项目的贡献者，中文在前、英文在后，
两部分内容一致，选你顺手的一份读即可。

> **协议提醒 / License notice**：本项目采用 **GNU General Public License v3.0（GPL-3.0）**。
> 这意味着你可以自由使用、研究、修改和再分发本项目，但**任何衍生作品必须以同一协议开源**，
> 且不能并入专有软件。向本仓库提交代码，即表示你同意自己的贡献以 GPL-3.0 发布。
> This project is licensed under **GPL-3.0**. By contributing, you agree that your
> contributions will be licensed under GPL-3.0.

---

# 第一部分 · 中文

## 目录

1. [行为准则](#行为准则)
2. [我能帮上什么](#我能帮上什么)
3. [环境要求](#环境要求)
4. [构建与测试](#构建与测试)
5. [代码结构导读](#代码结构导读)
6. [工程约定与红线](#工程约定与红线)
7. [提交信息规范](#提交信息规范)
8. [Pull Request 流程](#pull-request-流程)
9. [新增文件的版权头](#新增文件的版权头)

## 行为准则

- 对事不对人，讨论聚焦代码与问题本身；欢迎不同意见，但不接受人身攻击。
- 提 issue 请写清复现步骤、设备/系统版本、实际与期望行为。
- 维护者可能关闭范围蔓延或与项目定位不符的提议，这不代表你的想法不好。

## 我能帮上什么

不需要从大重构开始，下面这些都是实实在在的贡献：

| 方向 | 说明 |
| --- | --- |
| **国际化（i18n）** | 目前界面文案为硬编码中文，抽取 `strings.xml` 并接入现有主题体系是最大的单点改进 |
| **无障碍** | 补 `contentDescription`、焦点顺序、TalkBack 适配 |
| **新棋盘 / 新难度** | `domain/model/BoardModels.kt` 中增加尺寸与难度（注意 `SudokuEngine` 与存档兼容） |
| **题库扩展** | 改进「导入题目 JSON」的解析与校验（`SettingsViewModel.parsePuzzleImport`） |
| **单元测试** | `app/src/test` 目前 27 个用例，domain 层尤其欢迎补充边界测试 |
| **Bug 修复** | 见 [Issues](https://github.com/siyu666888/SudokuMaster/issues) |

> 想先聊再动手？直接开一个 issue 说明想法，避免白做。

## 环境要求

| 项 | 版本 / 说明 |
| --- | --- |
| JDK | **17**（AGP 8.5.2 要求） |
| Android SDK | `platform 34` + `build-tools 34.0.0` |
| Gradle | **8.7**（见 `gradle/wrapper/gradle-wrapper.properties`） |
| minSdk / targetSdk | 24 / 34 |

> ⚠️ **仓库未包含 `gradlew` 与 `gradle-wrapper.jar`**（二进制文件未入库）。
> 因此请使用本机已安装的 Gradle 8.7 直接执行命令，或用 Android Studio（Ladybug 或更新）
> 打开本目录，让 IDE 自动补齐 wrapper。

设置 `ANDROID_HOME` 指向你的 SDK 位置，例如：

```bash
export ANDROID_HOME=~/Android/Sdk        # macOS / Linux
# Windows: setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

## 构建与测试

```bash
# 跑单元测试（JUnit 4，约 1 分钟内）
gradle testDebugUnitTest

# 打 debug 包并安装到已连接设备
gradle assembleDebug
gradle installDebug

# release 包（需要签名配置，见下）
gradle assembleRelease
```

**关于 release 签名**：正式签名用的 `keystore/sudoku-release.jks` 与 `keystore.properties`
已被 `.gitignore` 排除，**不在仓库内**。贡献者无需配置，用 `assembleDebug` 即可验证；
release 包由维护者在本地构建后放入 `release/` 目录（`release/*.apk` 同样不入库）。

**提交前请至少跑通 `gradle testDebugUnitTest`**；改了 UI 的话建议再跑一次
`gradle assembleDebug` 确认能编译。本项目目前的验收标准是
「测试全绿 + release 包构建成功 + `apksigner verify` 通过」。

## 代码结构导读

```
app/src/main/java/com/example/sudokumaster/
├── domain/          # 纯 Kotlin，无 Android 依赖，可单测
│   ├── engine/      #   SudokuEngine：回溯生成 / 唯一解校验 / 求解
│   ├── model/       #   SizeId / Difficulty / BoardDefinitions / 设置 / UI 状态
│   └── session/     #   GameSession 状态机 + GameSnapshot（领域快照）
├── data/            # Room + DataStore + Repository
│   ├── entity/ dao/ #   game_records（历史成绩）/ saved_game（多槽续玩）
│   ├── SudokuDatabase.kt      # version 3 + MIGRATION_1_2 / MIGRATION_2_3
│   └── repository/  #   含 SavedGameMapper.kt（实体↔快照映射）
├── viewmodel/       # Home / Game / History / Settings / Achievements + AppContainer
├── ui/              # theme / components / home / game / history / settings / achievements / fx
├── nav/             # NavRoutes + NavHost
└── util/            # 时间格式化、颜色解析、震动/提示音
```

依赖注入是手写的 `AppContainer`（`viewmodel/AppContainer.kt`），在 `SudokuApplication`
中惰性创建，没有引入 Hilt/Koin——**请保持这个轻量约定**，不要顺手引入 DI 框架。

## 工程约定与红线

这些是踩过坑定下来的，改动前请先读：

1. **`domain` 层不许依赖 Android 或 Room**。`GameSession` 只产出 / 消费 `GameSnapshot`，
   实体↔快照映射放在 `data/repository/SavedGameMapper.kt`。新增领域类型请照此办理。
2. **时间必须可注入**。计时相关逻辑不要直接调 `System.currentTimeMillis()`，
   通过 `clock: () -> Long` 传入（默认值即系统时钟），这样才可单测。
3. **Room 迁移必须显式写**。改 entity / 加字段要同步加 `Migration` 并提升 `version`，
   **禁止使用 `fallbackToDestructiveMigration()`**——宁可崩溃也不要静默清掉用户数据。
   `saved_game` 已从单槽改为多槽（`slotId = "sizeId:diffId"`），动这张表前先看 `MIGRATION_2_3`。
4. **release 资源引用必须用编译期 `R.raw.xxx`**，不要用 `getIdentifier()`——release 构建的
   AAPT2 会缩短资源路径名，运行时按名查找会失败。
5. **单元测试放 `app/src/test`**，用 JUnit 4（`org.junit.Test` / `org.junit.Assert`）。
   目前 27 个用例，改动 domain 层时请一并补充或修正。
6. **版本号**：一批功能性改动对应一个版本号（`versionCode` / `versionName`），
   README 的更新日志与 `release/` 目录同步维护。纯文档修正不必升版。

## 提交信息规范

现有历史大致是这个风格，请对齐：

```
<主题>：<一句话摘要>

- <改动点 1>
- <改动点 2>

验证：<测试/构建结果>
```

- 主题行用中文，`：` 后接摘要，控制在 50 字左右。
- 正文用 `-` 列表说明改了什么、为什么。
- 有验证结论就写上（如「27 个单测全绿 + assembleRelease 成功」）。
- 一个 commit 做一件事；不要把无关改动混在一起。

## Pull Request 流程

1. Fork 本仓库，从 `main` 切分支：`feature/xxx`、`fix/xxx`、`docs/xxx`。
2. 按上面的约定改代码，本地跑通 `gradle testDebugUnitTest`（改 UI 再加 `assembleDebug`）。
3. 提交并推送到你的 Fork，然后向 `siyu666888/SudokuMaster` 的 `main` 开 PR。
4. PR 描述请写清：
   - 做了什么、解决什么问题（有关联 issue 请带上 `Closes #123`）；
   - 影响范围（UI / 数据 / 兼容性）；
   - 验证方式与结果。
5. 维护者 review 后可能要求调整；合并后你的贡献会以 GPL-3.0 随项目一起发布。

> 数据库 schema、存档格式、备份 JSON 格式属于**不兼容变更高发区**，涉及它们的 PR
> 请额外说明迁移与兼容方案。

## 新增文件的版权头

按 GPL-3.0 的要求，**新建的源文件请加上版权与许可声明**（现有文件尚未统一补头，
欢迎提交批量补充的 PR）：

```kotlin
/*
 * Copyright (C) 2026 siyu666888
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
```

再次感谢你的时间与代码。🎉

---

# Part 2 · English

## Table of contents

1. [Code of conduct](#code-of-conduct)
2. [How you can help](#how-you-can-help)
3. [Prerequisites](#prerequisites)
4. [Building and testing](#building-and-testing)
5. [Codebase tour](#codebase-tour)
6. [Conventions and hard rules](#conventions-and-hard-rules)
7. [Commit message style](#commit-message-style)
8. [Pull request workflow](#pull-request-workflow)
9. [Copyright header for new files](#copyright-header-for-new-files)

## Code of conduct

- Critique code, not people. Disagreement is welcome; personal attacks are not.
- When filing an issue, include reproduction steps, device/OS version, and actual vs.
  expected behaviour.
- Maintainers may close proposals that drift out of scope — that is not a judgement of
  the idea itself.

## How you can help

You do not need to start with a big refactor. All of these are genuinely useful:

| Area | Notes |
| --- | --- |
| **Internationalization (i18n)** | UI strings are currently hardcoded in Chinese. Extracting them into `strings.xml` is the single highest-impact improvement |
| **Accessibility** | Fill in `contentDescription`, focus order, TalkBack support |
| **New board sizes / difficulties** | Add entries in `domain/model/BoardModels.kt` (mind `SudokuEngine` limits and save compatibility) |
| **Puzzle bank** | Improve the puzzle-JSON import parser and validation (`SettingsViewModel.parsePuzzleImport`) |
| **Unit tests** | 27 cases live in `app/src/test`; the `domain` layer especially welcomes edge-case coverage |
| **Bug fixes** | See [Issues](https://github.com/siyu666888/SudokuMaster/issues) |

> Want to talk before coding? Open an issue and describe the idea first.

## Prerequisites

| Item | Version / notes |
| --- | --- |
| JDK | **17** (required by AGP 8.5.2) |
| Android SDK | `platform 34` + `build-tools 34.0.0` |
| Gradle | **8.7** (see `gradle/wrapper/gradle-wrapper.properties`) |
| minSdk / targetSdk | 24 / 34 |

> ⚠️ **The repository does not include `gradlew` or `gradle-wrapper.jar`** (binary files are
> not tracked). Use a locally installed Gradle 8.7, or open the project in Android Studio
> (Ladybug or newer) and let the IDE generate the wrapper for you.

Point `ANDROID_HOME` at your SDK, e.g.:

```bash
export ANDROID_HOME=~/Android/Sdk        # macOS / Linux
# Windows: setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

## Building and testing

```bash
# Run the unit tests (JUnit 4, ~1 minute)
gradle testDebugUnitTest

# Build and install the debug APK on a connected device
gradle assembleDebug
gradle installDebug

# Release build (needs signing config, see below)
gradle assembleRelease
```

**About release signing**: the release keystore (`keystore/sudoku-release.jks`) and
`keystore.properties` are git-ignored and **not in the repository**. Contributors do not
need them — `assembleDebug` is enough to verify your change. Release APKs are built locally
by the maintainer and dropped into `release/` (`release/*.apk` is also untracked).

**Before opening a PR, at minimum run `gradle testDebugUnitTest`**; if you touched the UI,
also run `gradle assembleDebug` to confirm it compiles. The project's acceptance bar is
"all tests green + release build succeeds + `apksigner verify` passes".

## Codebase tour

```
app/src/main/java/com/example/sudokumaster/
├── domain/          # Pure Kotlin, no Android deps, unit-testable
│   ├── engine/      #   SudokuEngine: backtracking generation / unique-solution check / solver
│   ├── model/       #   SizeId / Difficulty / BoardDefinitions / settings / UI state
│   └── session/     #   GameSession state machine + GameSnapshot (domain snapshot)
├── data/            # Room + DataStore + Repository
│   ├── entity/ dao/ #   game_records (history) / saved_game (multi-slot resume)
│   ├── SudokuDatabase.kt      # version 3 + MIGRATION_1_2 / MIGRATION_2_3
│   └── repository/  #   incl. SavedGameMapper.kt (entity <-> snapshot mapping)
├── viewmodel/       # Home / Game / History / Settings / Achievements + AppContainer
├── ui/              # theme / components / home / game / history / settings / achievements / fx
├── nav/             # NavRoutes + NavHost
└── util/            # time formatting, color parsing, vibration / beep
```

Dependency injection is a hand-written `AppContainer` (`viewmodel/AppContainer.kt`) created
lazily in `SudokuApplication`. There is no Hilt/Koin — **please keep it that way** rather than
introducing a DI framework.

## Conventions and hard rules

These came out of real bugs. Please read before changing anything:

1. **`domain` must not depend on Android or Room.** `GameSession` only produces / consumes
   `GameSnapshot`; entity↔snapshot mapping lives in `data/repository/SavedGameMapper.kt`.
   Follow the same pattern for new domain types.
2. **Time must be injectable.** Do not call `System.currentTimeMillis()` directly in timing
   logic — pass a `clock: () -> Long` (its default is the system clock) so it stays testable.
3. **Room migrations must be explicit.** Changing an entity or adding a column means adding a
   `Migration` and bumping `version`. **Never use `fallbackToDestructiveMigration()`** —
   crashing is preferable to silently wiping user data. `saved_game` moved from a single slot
   to multi-slot (`slotId = "sizeId:diffId"`); read `MIGRATION_2_3` before touching that table.
4. **Release resource lookups must use compile-time `R.raw.xxx`**, never `getIdentifier()` —
   AAPT2 shortens resource paths in release builds, so name-based lookup fails at runtime.
5. **Unit tests go in `app/src/test`** using JUnit 4 (`org.junit.Test` / `org.junit.Assert`).
   There are 27 cases today; please extend or fix them when you change `domain`.
6. **Versioning**: one functional batch = one version bump (`versionCode` / `versionName`),
   with the README changelog and `release/` kept in sync. Docs-only fixes do not need a bump.

## Commit message style

Roughly matching the existing history:

```
<topic>: <one-line summary>

- <change 1>
- <change 2>

Verification: <test/build results>
```

- Topic line in Chinese, summary after `：`, keep it around 50 characters.
- Body as a `-` list of what changed and why.
- Include verification results when you have them (e.g. "27 tests green + assembleRelease OK").
- One commit does one thing; don't mix unrelated changes.

## Pull request workflow

1. Fork the repo and branch off `main`: `feature/xxx`, `fix/xxx`, `docs/xxx`.
2. Make your change following the conventions above; locally run
   `gradle testDebugUnitTest` (plus `assembleDebug` if you touched the UI).
3. Push to your fork and open a PR against `siyu666888/SudokuMaster:main`.
4. In the PR description, cover:
   - what you did and which problem it solves (link the issue with `Closes #123`);
   - scope of impact (UI / data / compatibility);
   - how you verified it.
5. Maintainers may request changes during review. Once merged, your contribution ships with
   the project under GPL-3.0.

> Database schema, save format, and the backup JSON format are **breaking-change hotspots**.
> PRs touching them should spell out the migration and compatibility plan.

## Copyright header for new files

As GPL-3.0 requires, **please add a copyright and license notice to new source files**
(existing files are not yet backfilled — a batch PR for that is very welcome):

```kotlin
/*
 * Copyright (C) 2026 siyu666888
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
```

Thanks again for your time and your code. 🎉
