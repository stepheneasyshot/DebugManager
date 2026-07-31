# composeApp — Agent Handbook

根工程唯一的 Compose Desktop 应用模块。仓库级总览见 [../AGENTS.md](../AGENTS.md)；用户功能说明见 [../README.md](../README.md)。

## 权威文档（改码前必读）

| 文档 | 用途 |
|---|---|
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | **架构事实来源**：分层、依赖方向、不变量 |
| [docs/PLANNING.md](./docs/PLANNING.md) | 规划约束：按现有架构实现，禁止破坏边界 |
| [docs/HARNESS.md](./docs/HARNESS.md) | Agent Harness：改前必读、改后检查并更新技术文档 |

包名：`com.stephen.debugmanager`。应用版本见本模块 `build.gradle.kts` 的 `debugManagerAppVersion`。

工具链（以 Version Catalog / Wrapper 为准）：Kotlin **2.2.21**、Compose Multiplatform **1.11.1**、Gradle **8.14**。

KMP 仅保留 `jvm("desktop")`。Compose 1.11+ 已移除 `macosX64`；Win/macOS/Linux 安装包均由 `compose.desktop`（JVM）产出，不要再声明 Kotlin/Native `macos*` framework 目标。

## 职责

桌面端 UI、ADB/scrcpy 编排、文件/应用管理、性能监控、AI 对话（Kimi / DeepSeek）、主题与多语言。设备侧 AYA 协议客户端也在本模块（`helper`）；AYA 服务端源码在仓库独立目录 `../server/`（未纳入根 Gradle）。

## 源码布局

```
composeApp/
├── build.gradle.kts
└── src/desktopMain/
    ├── kotlin/com/stephen/debugmanager/
    │   ├── Main.kt                 # application：Koin、窗口、托盘、单实例
    │   ├── MainStateHolder.kt      # 全局业务状态与用例编排
    │   ├── base/                   # AdbClient、PlatformAdapter、单实例锁
    │   ├── data/                   # 模型、Constants、bean、uistate
    │   ├── di/                     # Koin（koinModules.kt）
    │   ├── helper/                 # FileManager、AndroidAppHelper、DataStore…
    │   ├── net/                    # KtorClient、Kimi/DeepSeek Repository
    │   ├── ui/                     # ContentView、pages、component、theme
    │   └── utils/                  # LogUtils、通用工具
    ├── composeResources/           # 多语言 strings、drawable
    └── resources/
```

打包用原生二进制在仓库 `../resources/{windows|macos|linux}/scrcpy/` 与 `../resources/common/aya.dex`，经 `appResourcesRootDir` 打进安装包。

## 架构（摘要）

完整说明与不变量见 [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)。摘要：

```
UI → MainStateHolder → (base | helper | net) → adb/scrcpy/AYA
data / utils 不依赖 Compose UI 与 ADB
```

| 层 | 位置 | 规则 |
|---|---|---|
| 入口/编排 | `Main.kt`, `MainStateHolder` | 新用例先加 StateHolder，再接 UI |
| UI | `ui` | 只展示与调 StateHolder，不拼长 adb |
| 设备/OS | `base` | 进程、路径、ADB 语义 |
| 领域助手 | `helper` | 文件树、AYA、DataStore、日志路径 |
| 网络 | `net` | 仅 HTTP/LLM |
| 模型 | `data` | 纯数据 / UI state / 路由常量 |
| DI | `di` | 只做 wiring |

---

## 入口与 MainStateHolder

**启动顺序（简）：**

1. `startKoin { modules(koinModules) }`
2. `PlatformAdapter.init` → 单实例锁回调
3. 已有实例 → `ui/SingleProcessTipWindow`；否则 `AdbClient.init` + 连接轮询等
4. UI：`SplashScreen` → `ContentView`

**MainStateHolder** 持有并编排：设备列表/当前设备、文件目录、应用列表、性能、终端与 ADB 命令历史、AI 对话、主题/语言/模型选择（DataStore）。UI 经 Koin / `GlobalContext.get().get<MainStateHolder>()` 获取。

- 窗口/托盘/退出确认留在 `Main.kt`。
- `isOtherInstanceRunning == true` 时不要初始化会抢 ADB/锁的逻辑。
- 增删 StateHolder 构造依赖时同步改 `di/koinModules.kt`。

---

## base — 平台与 ADB

| 文件 | 职责 |
|---|---|
| `AdbClient.kt` | 设备列表解析、当前 `serial`、root/remount、shell 执行 |
| `PlatformAdapter.kt` | OS 判定、adb/scrcpy/dex 路径、进程执行、缓存目录、剪贴板、Locale |
| `SingleInstanceApp.kt` | `~/.debugmanagerTemp/app.lock` 文件锁 |

要点：

- **ADB/scrcpy 路径按安装包布局**解析。Windows 例：`{user.dir}/app/resources/scrcpy/adb.exe`；macOS/Linux 为固定安装路径。开发态 `run` 时 `user.dir` 可能不同，改路径前确认开发/打包差异。
- 用户数据目录：`~/.debugmanagerTemp`（日志、拉取缓存、DataStore、锁）。
- 命令执行用 `executeCommandWithResult` / `executeTerminalCommand`，UI 层不要直接 `ProcessBuilder`。
- 带设备命令使用 `AdbClient.serial` 与 `-s`。
- OS 差异放 `PlatformAdapter`；ADB 语义放 `AdbClient`。改单实例需同步 `Main.kt` 分支。

---

## data — 模型与路由

```
data/
├── Constants.kt       # 侧栏 route + mainItemMap / themeMap / languageMap
├── *State.kt 等       # ThemeState、LanguageState、PlatformType、InstallParams…
├── bean/              # 网络 / AYA / 聊天 DTO（kotlinx.serialization）
└── uistate/           # StateFlow 用的界面状态快照
```

- 路由字符串只定义在 `Constants`（如 `DEVICE_INFO`），禁止魔法字符串。
- `uistate` 用不可变 `data class`，在 StateHolder 里 `MutableStateFlow.update`。
- AYA 模型在 `bean/AyaAppInfoData.kt`，须与 `../server` 端 JSON `method`/字段一致。
- 本包不引入 Koin、ADB、Compose。

**新增页面：** `Constants.mainItemMap` → `ContentView` NavHost → `ui/pages/XxxPage.kt` → 多语言与图标。

---

## di — Koin

`koinModules.kt`：

| 类型 | 作用域 |
|---|---|
| `MainStateHolder`, `PlatformAdapter`, `AdbClient`, `FileManager`, `AndroidAppHelper` | `single` |
| `SingleInstanceApp`, `DataStoreHelper`, `LogFileFinder`, `KtorClient`, LLM Repos | `factory` |

只做注册，不写业务。

---

## helper — 文件与 AYA

| 文件 | 职责 |
|---|---|
| `FileManager.kt` | 设备目录栈、列目录、Push/Pull/删除；快捷 sdcard / priv-app |
| `AndroidAppHelper.kt` | push dex、启动 AYA、forward、Socket JSON、拉图标 |
| `DataStoreHelper.kt` | Preferences 封装 |
| `LogFileFinder.kt` | 本机日志目录 |

**AYA 流程（桌面侧）：**

1. push dex → `/data/local/tmp/aya/aya.dex`
2. `app_process … io.liriliri.aya.Server`（abstract socket `aya`）
3. `adb forward tcp:1234 localabstract:aya`
4. `Socket(localhost, 1234)` 收发 `AyaRequest` / `AyaResponse`

设备端已知 `method`：`getVersion`、`getPackageInfos`、`saveAllInfoToFile`（见 `../server/.../Connection.kt`）。改协议时同步 `data/bean/AyaAppInfoData.kt` 与 server。

**FileManager：** 路径用 `currentDirPath` 列表；`ROOT_DIR` / `LAST_FOLDER` 语义勿随意改。危险操作的确认 UI 在 pages，helper 保持可复用。结果回传 StateHolder，不直接改 Compose State。

---

## net — LLM

| 文件 | 职责 |
|---|---|
| `KtorClient.kt` | 共享 HttpClient（超时、Logging、JSON、WebSockets） |
| `KimiRepository.kt` / `DeepSeekRepository.kt` | 各平台对话 |

DTO 在 `data/bean/`；UI 在 `AiModelPage`；编排在 StateHolder。新模型：Repository + `AIModels` + StateHolder 分支 + 选择 UI。勿提交密钥。

---

## ui — 界面

```
ui/
├── ContentView.kt              # 侧栏 + NavHost + 设备选择
├── SingleProcessTipWindow.kt
├── pages/                      # 功能页
├── component/ (+ skeleton/)    # 可复用控件
└── theme/                      # Color / Type
```

| Constants key | 页面 |
|---|---|
| `DEVICE_INFO` | `DeviceInfoPage` |
| `APP_MANAGE` | `ApkManagePage` |
| `FILE_MANAGE` | `FileManagePage` |
| `COMMAND` | `CommandPage` |
| `PERFORMANCE` | `PerformancePage` |
| `TOOLS` | `ToolsPage` |
| `AI_MODEL` | `AiModelPage` |
| `ABOUT` | `AboutPage` |

另有 `SplashScreen`。文案用 `stringResource(Res.string.*)`；控件复用 `component/`；主题只改 `theme/`。

---

## utils

`LogUtils`、`CommonUtils`、`DoubleClickUtils`。无 ADB/Compose 依赖；日志统一走 `LogUtils`。

---

## 多语言

- 文案：`src/desktopMain/composeResources/values*/strings.xml`
- 已有：默认 en + `zh`、`ru`、`hi`、`es`、`fr`、`de`、`ko`、`ja`、`ar`
- 新增可见文案至少同步默认与 `values-zh`
- 语言状态：`LanguageState` + DataStore；Locale 映射见 `PlatformAdapter` / `getLanguageLocale`

---

## 构建与运行

需 JDK 17+。在仓库根目录：

```bash
./gradlew :composeApp:run
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:packageDistributionForCurrentOS
```

Windows 用 `gradlew.bat`。版本号只改本模块 `debugManagerAppVersion`。主类：`com.stephen.debugmanager.MainKt`。KMP target：仅 `jvm("desktop")`；安装包由 `compose.desktop` 产出。

AYA 服务端（一般不必动）在 `../server/`：

```bash
cd ../server && ./gradlew :server:assemble
```

产出 `aya.dex`；若打包用 `resources/common/aya.dex`，构建后按需同步拷贝。保持入口类名 `io.liriliri.aya.Server`。

---

## Agent 改动约定

**要做：**

- 新依赖写入 `../gradle/libs.versions.toml`，再在本模块 `build.gradle.kts` 引用 `libs.*`
- 设备操作走 `AdbClient` / `PlatformAdapter`，注意 `-s serial`
- 状态暴露与现有 StateFlow / `mutableStateOf` 模式一致
- 保持单实例锁与无边框窗口行为（除非任务明确要求改）

**不要：**

- 把业务堆进 Composable，或在页面里直接拼 adb
- 把 `server/` merge 进根工程（除非明确重构）
- 随意替换 `../resources/*/scrcpy` 二进制
- 提交密钥、`local.properties`、构建产物
- 引入第二套 DI/网络栈（已用 Koin + Ktor）

**验证：**

1. `:composeApp:compileKotlinDesktop` 通过  
2. 改文案则检查 `composeResources` key  
3. 改 ADB 相关尽量真机/模拟器冒烟；无设备至少保证编译与路径合理  

## 关键文件速查

| 需求 | 文件 |
|---|---|
| 入口 / 窗口 | `Main.kt` |
| 业务状态 | `MainStateHolder.kt` |
| DI | `di/koinModules.kt` |
| ADB / 路径 | `base/AdbClient.kt`, `base/PlatformAdapter.kt` |
| 侧栏与路由 | `ui/ContentView.kt`, `data/Constants.kt` |
| 应用 / AYA | `helper/AndroidAppHelper.kt` |
| 文件管理 | `helper/FileManager.kt` |
| AI | `net/*Repository.kt`, `ui/pages/AiModelPage.kt` |
| 版本与打包 | `build.gradle.kts` |
