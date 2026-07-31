# DebugManager — Agent Handbook

面向编码 Agent（Cursor、Claude Code、Codex 等）的项目说明书。改代码前先读本文件；用户面向说明见 `README.md`。

## 项目是什么

DebugManager 是基于 **Compose Multiplatform Desktop** 的 Android 设备调试桌面应用，侧重车机/嵌入式 Android 场景。支持 Windows / macOS / Linux。

核心能力：设备信息与 root/remount、投屏/录屏（scrcpy）、应用管理、文件 Push/Pull、ADB/终端命令、性能监控、AI 对话（Kimi / DeepSeek）。

当前版本见 `composeApp/build.gradle.kts` 中的 `debugManagerAppVersion`（写作时为 `2.6.5`）。

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Compose Multiplatform **1.11.1** + Material3，自定义无边框窗口 |
| 语言 | Kotlin **2.2.21**（Version Catalog：`gradle/libs.versions.toml`） |
| DI | Koin（`di/koinModules.kt`） |
| 异步 | Kotlin Coroutines + StateFlow |
| 网络 | Ktor Client（AI API） |
| 持久化 | DataStore Preferences（主题 / 语言 / AI 模型选择） |
| 设备侧 | 内置 adb/scrcpy；AYA Server（`aya.dex`）获取应用图标等信息 |
| 构建 | Gradle **8.14** + Kotlin DSL，Compose Desktop 打包 Exe / Dmg / Deb |

工具链版本以 `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties` 为准；升级后同步本表。

## 仓库结构

```
DebugManager/
├── AGENTS.md / CLAUDE.md       # 仓库级 Agent 说明书（本文件）
├── composeApp/                 # 主应用 → 详细说明见 composeApp/AGENTS.md
├── resources/                  # 打包用原生资源（adb/scrcpy、aya.dex）
├── server/                     # AYA Server（独立 Gradle，未 include 进根工程）
├── launcher/                   # 应用图标
├── screenshots/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

改桌面应用代码时：

1. **必读** [composeApp/docs/ARCHITECTURE.md](composeApp/docs/ARCHITECTURE.md)（架构）与 [composeApp/docs/HARNESS.md](composeApp/docs/HARNESS.md)（改前/改后流程）
2. 多文件规划另读 [composeApp/docs/PLANNING.md](composeApp/docs/PLANNING.md)
3. 模块速查见 [composeApp/AGENTS.md](composeApp/AGENTS.md)

改完后按 HARNESS H3 检查并更新过时技术文档。

## 架构要点

```
UI (pages + components)
    ↓ collect StateFlow / 调用
MainStateHolder（业务编排、偏好读写）
    ↓
AdbClient / FileManager / AndroidAppHelper / *Repository
    ↓
PlatformAdapter（进程执行、路径、剪贴板、单实例锁）
    ↓
本机 adb/scrcpy 二进制  ←→  Android 设备（含 AYA socket）
```

### 分层约定

1. **UI**（`ui/pages/*`、`ui/component/*`）：Compose 展示与交互；经 Koin/`GlobalContext` 取 `MainStateHolder`，不要在页面里直接拼长串 adb 命令。
2. **MainStateHolder**：设备列表、当前设备、文件树、应用列表、性能、终端历史、主题/语言/AI 状态的唯一编排入口。
3. **base**：
   - `AdbClient`：设备发现、root/remount、shell 执行等 ADB 语义。
   - `PlatformAdapter`：OS 差异、资源路径、命令执行、用户缓存目录 `~/.debugmanagerTemp`。
   - `SingleInstanceApp`：文件锁单实例；已有实例时显示 `SingleProcessTipWindow`。
4. **helper**：文件管理、应用信息（AYA）、日志路径、DataStore 封装。
5. **net**：HTTP 客户端与各 LLM Repository。

### 导航与页面

侧栏路由常量在 `data/Constants.kt` 的 `mainItemMap`：

| Route key | 页面 |
|---|---|
| `DEVICE_INFO` | `DeviceInfoPage` |
| `APP_MANAGE` | `ApkManagePage` |
| `FILE_MANAGE` | `FileManagePage` |
| `COMMAND` | `CommandPage` |
| `PERFORMANCE` | `PerformancePage` |
| `TOOLS` | `ToolsPage` |
| `AI_MODEL` | `AiModelPage` |
| `ABOUT` | `AboutPage` |

导航在 `ui/ContentView.kt`（Navigation Compose）。

### 平台与资源路径（重要）

`PlatformAdapter.localAdbPath` / `localScrcpyPath` 按 **已安装包布局** 解析，例如 Windows：

`{user.dir}/app/resources/scrcpy/adb.exe`

开发态 `run` 时 `user.dir` 未必等于该布局；调试 ADB 相关功能时需注意资源是否已按 Compose Desktop `appResourcesRootDir`（`../resources`）正确落到运行目录。打包配置在 `composeApp/build.gradle.kts` 的 `compose.desktop.application`。

用户数据目录：`~/.debugmanagerTemp`（日志、拉取缓存、DataStore、单实例锁等）。

## 运行与构建

需 JDK 17+（Compose Desktop / 当前 Kotlin 工具链）。

```bash
# Windows
.\gradlew.bat :composeApp:run

# macOS / Linux
./gradlew :composeApp:run

# 打包分发（按当前 OS 生成对应安装包）
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
# 或
./gradlew :composeApp:packageDistributionForCurrentOS
```

常用变体（以本机 Gradle 任务为准）：

- `packageExe` / `packageDmg` / `packageDeb` — 平台特定包
- 修改版本号：只改 `composeApp/build.gradle.kts` 的 `debugManagerAppVersion`

AYA Server（一般不必动）：

```bash
cd server
./gradlew :server:assemble   # 或 Windows: gradlew.bat
```

产出/使用 `server/aya.dex`，由 `AndroidAppHelper` push 到设备并 forward `tcp:1234` → `localabstract:aya`。

## 多语言

- 文案：`composeApp/src/desktopMain/composeResources/values*/strings.xml`
- 已有：默认英文 + `zh`、`ru`、`hi`、`es`、`fr`、`de`、`ko`、`ja`、`ar`
- UI 使用 `stringResource(Res.string.xxx)`；新增可见文案时同步各语言文件，至少补英文与中文。
- 语言状态：`LanguageState` + DataStore；系统语言映射见 `PlatformAdapter` / `getLanguageLocale`。

## Agent 工作约定

### 改代码时

- 保持现有包结构与命名（`com.stephen.debugmanager.*`），新功能优先落在对应层，避免把业务堆进 Composable。
- 新依赖写入 `gradle/libs.versions.toml`，再在 `composeApp/build.gradle.kts` 引用 `libs.*`。
- 设备相关操作走 `AdbClient` / `PlatformAdapter.executeCommand*`，注意串号：`adbClient.serial` / `-s`。
- 状态对 UI 暴露用 `StateFlow` / 现有 `mutableStateOf` 模式，与 `MainStateHolder` 保持一致。
- 主题色在 `ui/theme/`；可复用组件在 `ui/component/`，不要复制一套按钮/对话框。
- 不要提交密钥、token、本机 `local.properties`、构建产物（见 `.gitignore`）。

### 不要轻易做的事

- 不要把 `server/` 贸然 merge 进根工程，除非明确要求改 AYA 协议。
- 不要删除或替换 `resources/*/scrcpy` 二进制，除非有意升级 scrcpy/adb。
- 不要破坏单实例锁与透明无边框窗口行为，除非任务明确要求。
- 不要引入与现有栈重复的 DI/网络库（已用 Koin + Ktor）。

### 验证建议

1. `./gradlew :composeApp:compileKotlinDesktop`（或 IDE 等价编译）确保通过。
2. 涉及 UI 文案：检查 `composeResources` 是否缺 key。
3. 涉及 ADB：有真机/模拟器时跑 `:composeApp:run` 做冒烟；无设备时至少保证编译与静态路径改动合理。

## 关键文件速查

| 需求 | 文件 |
|---|---|
| 入口 / 窗口 / 主题壳 | `Main.kt` |
| 业务状态 | `MainStateHolder.kt` |
| DI | `di/koinModules.kt` |
| ADB | `base/AdbClient.kt` |
| 平台路径与进程 | `base/PlatformAdapter.kt` |
| 侧栏与路由 | `ui/ContentView.kt`、`data/Constants.kt` |
| 应用列表 / AYA | `helper/AndroidAppHelper.kt` |
| 文件管理 | `helper/FileManager.kt` |
| AI | `net/KimiRepository.kt`、`net/DeepSeekRepository.kt`、`ui/pages/AiModelPage.kt` |
| 版本与打包 | `composeApp/build.gradle.kts` |
| 依赖版本 | `gradle/libs.versions.toml` |

## 安全与合规

软件声明仅供学习交流。Agent 协助调试功能时，避免生成用于未授权设备入侵、绕过保护或破坏系统的恶意脚本；文件删除/remount 等危险操作应保留现有确认与警告 UX。
