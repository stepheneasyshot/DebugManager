# composeApp 架构说明

本文档是 **composeApp** 模块的架构事实来源（Source of Truth）。任何功能规划与代码改动必须符合本文描述的分层与依赖方向；禁止以“方便实现”为由破坏边界。

配套文档：

- [PLANNING.md](./PLANNING.md) — 规划与实现约束
- [HARNESS.md](./HARNESS.md) — Agent Harness：改前必读、改后检文档
- [../AGENTS.md](../AGENTS.md) — 模块手册（构建、包职责速查）
- [../../AGENTS.md](../../AGENTS.md) — 仓库级总览

---

## 1. 模块定位

| 项 | 说明 |
|---|---|
| 形态 | Compose Multiplatform Desktop（**仅** `jvm("desktop")`）应用；跨平台安装包由 `compose.desktop` 打包，无 K/N apple 目标 |
| 包名 | `com.stephen.debugmanager` |
| 主类 | `com.stephen.debugmanager.MainKt` |
| 职责 | Android 设备调试桌面端：ADB/scrcpy、应用/文件、命令、性能、AI 对话、主题与多语言 |
| 非职责 | 不内嵌 AYA 服务端源码（在仓库 `server/`，独立 Gradle）；不引入第二套 DI/网络栈 |

应用版本号：`composeApp/build.gradle.kts` → `debugManagerAppVersion`。

工具链版本（Kotlin / Compose / Gradle）见根 `gradle/libs.versions.toml` 与 `gradle/wrapper/gradle-wrapper.properties`；不在本文重复钉死次要补丁号，重大升级时同步根 [AGENTS.md](../../AGENTS.md) 技术栈表。

---

## 2. 逻辑分层与依赖方向

依赖 **只允许自上而下**，禁止反向依赖与跨层穿透。

```
┌─────────────────────────────────────────┐
│  ui  (pages / component / theme)        │  展示与交互
└──────────────────┬──────────────────────┘
                   │ 仅调用 MainStateHolder
                   ▼
┌─────────────────────────────────────────┐
│  Main.kt + MainStateHolder              │  入口 / 用例编排 / 状态
└──────────────────┬──────────────────────┘
                   │
       ┌───────────┼───────────┬──────────┐
       ▼           ▼           ▼          ▼
   base/       helper/       net/        data/
   Adb/OS      领域助手      LLM/HTTP     模型·常量·uistate
       │           │           │
       └─────► PlatformAdapter / AdbClient（进程与设备）
                   │
                   ▼
         本机 adb/scrcpy · 设备 · AYA socket
```

| 层 | 包/文件 | 允许依赖 | 禁止 |
|---|---|---|---|
| UI | `ui.*` | `MainStateHolder`、`data`（展示用类型）、Compose Resources | 直接调 `PlatformAdapter`/`AdbClient`；拼长 adb；持有业务副作用 |
| 编排 | `Main.kt`, `MainStateHolder` | `base`, `helper`, `net`, `data`, DataStore | 在 Composable 内堆业务；绕过 helper 复制协议逻辑 |
| base | `base.*` | 标准库、少量 data 枚举 | Compose UI；Ktor LLM；页面导航 |
| helper | `helper.*` | `base`, `data` | Compose State；UI 组件 |
| net | `net.*` | Ktor、`data.bean` | ADB；Compose |
| data | `data.*` | 序列化、资源类型（StringResource 等） | Koin、ADB、Compose UI、进程 |
| di | `di.*` | 上述类型的构造注册 | 业务逻辑 |
| utils | `utils.*` | 标准库 | ADB、Compose、网络 |

**DI：** 全部经 `di/koinModules.kt` 注册；UI 通过 Koin / `GlobalContext` 取 `MainStateHolder`。

---

## 3. 运行时主路径

1. `Main.kt`：`startKoin` → 取 `MainStateHolder`
2. `PlatformAdapter.init` + 单实例锁（`SingleInstanceApp` / `~/.debugmanagerTemp/app.lock`）
3. 若已有实例 → 仅 `SingleProcessTipWindow`；否则 `AdbClient.init` 与连接轮询
4. Window（透明无边框）→ Splash → `ContentView`（侧栏 + NavHost）
5. 各 Page `collectAsState` StateFlow，用户操作回调 StateHolder 方法

主题 / 语言 / AI 模型选择经 DataStore 持久化，由 StateHolder 读写。

---

## 4. 包职责详述

### 4.1 `base` — 平台与 ADB 基础设施

- `AdbClient`：设备列表、`serial`、root/remount、shell 语义
- `PlatformAdapter`：OS 分支、adb/scrcpy/dex 路径、进程执行、缓存目录、剪贴板、Locale
- `SingleInstanceApp`：文件锁

路径约定（安装包布局）：Windows 例 `{user.dir}/app/resources/scrcpy/adb.exe`。改路径必须同时考虑开发态 `run` 与打包态。

### 4.2 `helper` — 领域助手

- `FileManager`：设备目录栈、列目录、Push/Pull/删除
- `AndroidAppHelper`：AYA dex push、启动、`adb forward`、Socket JSON、拉图标
- `DataStoreHelper` / `LogFileFinder`

不直接操作 Compose State；结果返回编排层。

### 4.3 `net` — LLM / HTTP

共享 `KtorClient`；`KimiRepository` / `DeepSeekRepository`。DTO 在 `data.bean`。

### 4.4 `data` — 模型与路由

- `Constants.mainItemMap`：侧栏 route 唯一来源
- `uistate.*`：StateFlow 快照（不可变 data class）
- `bean.*`：API / AYA / 聊天 DTO

### 4.5 `ui` — 界面

- `ContentView`：设备选择 + Navigation Compose
- `pages/*`：与 `Constants` route 一一对应
- `component/*`：可复用控件；`theme/*`：色与字

文案：`composeResources` + `stringResource`。

### 4.6 AYA 协作边界

桌面（本模块）负责客户端；服务端在 `server/`（`io.liriliri.aya.Server`）。协议 method / JSON 字段变更必须 **两端同步**（`data.bean.AyaAppInfoData` + `AndroidAppHelper` + server `Connection`）。

流程：push dex → `app_process` → `forward tcp:1234 localabstract:aya` → `Socket(localhost,1234)`。

---

## 5. 导航与页面映射

| Route (`Constants`) | Page |
|---|---|
| `DEVICE_INFO` | `DeviceInfoPage` |
| `APP_MANAGE` | `ApkManagePage` |
| `FILE_MANAGE` | `FileManagePage` |
| `COMMAND` | `CommandPage` |
| `PERFORMANCE` | `PerformancePage` |
| `TOOLS` | `ToolsPage` |
| `AI_MODEL` | `AiModelPage` |
| `ABOUT` | `AboutPage` |

新增页面必须走：Constants → ContentView NavHost → Page → 多语言/图标。禁止未登记的硬编码 route。

---

## 6. 横切能力

| 能力 | 位置 | 约束 |
|---|---|---|
| 单实例 | `base` + `Main` | 勿削弱锁语义，除非需求明确 |
| 主题动画 | `ui.component.AnimatedTheme` | 色值只改 `ui.theme` |
| 多语言 | `composeResources` + `LanguageState` | 新文案至少 en + zh |
| 日志 | `utils.LogUtils` | 避免散落 `println` |
| 原生资源 | 仓库 `resources/` | 勿随意替换 scrcpy/adb 二进制 |

用户数据根目录：`~/.debugmanagerTemp`。

---

## 7. 架构不变量（Invariants）

以下破坏视为架构违规，PR/Agent 产出均不可接受：

1. UI 层直接执行 adb/shell 或 `ProcessBuilder`
2. `data` / `utils` 依赖 Compose UI 或 ADB
3. 在 Page 内复制 AYA/文件协议，而不走 `helper` + StateHolder
4. 绕过 `Constants` 增加侧栏/导航
5. 新增第二套 DI（非 Koin）或 HTTP 栈（非 Ktor）作为主路径
6. 将 `server/` 源码合并进 composeApp，或改掉 `io.liriliri.aya.Server` 入口类名却不同步桌面启动命令
7. 破坏透明无边框窗口 / 单实例提示窗的既有产品行为（除非任务明确要求）

---

## 8. 扩展检查清单

| 扩展类型 | 落点顺序 |
|---|---|
| 新调试能力 | `data.uistate`（如需）→ StateHolder 方法 → `base`/`helper` → Page 绑定 |
| 新侧栏页 | `Constants` → NavHost → `pages` → i18n/icon |
| 新 LLM | `net` Repository + `data.bean` + `AIModels` + StateHolder + AI 页 |
| 新 OS 差异 | 仅 `PlatformAdapter` |
| 新 ADB 语义 | 仅 `AdbClient`（或经其调用） |
| 新依赖 | `gradle/libs.versions.toml` → `composeApp/build.gradle.kts` 的 `libs.*` |

---

## 9. 文档维护

架构或分层发生变化时，必须更新本文，并核对 [PLANNING.md](./PLANNING.md)、[HARNESS.md](./HARNESS.md)、[../AGENTS.md](../AGENTS.md)、根 [AGENTS.md](../../AGENTS.md)、[README.md](../../README.md) 是否仍准确。详见 HARNESS 的「改后文档检查」。
