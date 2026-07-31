# composeApp Agent Harness 规则

本文件定义针对 **composeApp** 的 Agent 工作护栏（Harness）。Cursor Rules / Hooks / AGENTS 均指向此处；Claude Code 等工具也应遵守。

---

## 规则 H1 — 改码前必读架构（强制）

**在任何会修改 `composeApp/` 下源码或模块构建文件的操作之前**，必须先阅读并以其为上下文：

1. [ARCHITECTURE.md](./ARCHITECTURE.md)（必读）
2. [PLANNING.md](./PLANNING.md)（规划/多文件改动时必读）

建议同时打开 [../AGENTS.md](../AGENTS.md) 作包职责速查。

### 执行要求

- 使用 Read 工具实际读取上述文件（不可仅凭记忆或摘要声称已读）。
- 在开始 `Write` / `StrReplace` / 等价编辑之前完成阅读。
- 若会话中已读且本次改动未跨新层，可复用同会话上下文；**换会话或首次改 composeApp 必须重新读取**。

### 违反时

停止改码 → 先读文档 → 按 PLANNING 重新规划 → 再改。

---

## 规则 H2 — 按现有架构实现（强制）

- 代码必须符合 [ARCHITECTURE.md](./ARCHITECTURE.md) 的分层、依赖方向与 §7 不变量。
- 不得引入平行架构或“临时破坏边界”。
- 架构变更必须先改 ARCHITECTURE.md，再改代码。

---

## 规则 H3 — 改码后文档检查（强制）

**每次完成涉及 `composeApp/`（或影响其对外行为）的代码改动后**，必须检查是否需要更新技术文档，并在需要时立即更新。

### 检查清单

| 若本次改动涉及… | 应核对/更新 |
|---|---|
| 分层、包职责、依赖、不变量、AYA 流程 | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| 规划流程、扩展顺序、禁止项 | [PLANNING.md](./PLANNING.md) |
| 构建命令、包速查、Agent 约定 | [../AGENTS.md](../AGENTS.md) |
| 仓库结构、总览、运行方式 | [../../AGENTS.md](../../AGENTS.md) |
| 用户可见功能、截图说明 | [../../README.md](../../README.md) |
| Harness 流程本身 | 本文件 [HARNESS.md](./HARNESS.md) |
| Cursor 入口摘要 | [../../.cursor/rules/](../../.cursor/rules/) 中相关规则 |
| 新/改可见文案 | `composeResources/values*/strings.xml` |

### 执行要求

- 逐项判断「文档是否仍与代码一致」。
- 有偏差则更新文档，使文档与代码同任务内保持一致。
- 若确认无需更新，在回复中简短声明：「文档检查：无需更新」并说明依据（一句话）。

---

## 规则 H4 — 作用范围

| 路径 | 是否适用本 Harness |
|---|---|
| `composeApp/**`（含 `build.gradle.kts`、`composeResources`） | 是 |
| 根 `AGENTS.md` / 本 `docs/**`（为同步文档而改） | 改码后检查时允许更新 |
| 仅 `server/`、`resources/` 二进制、与 composeApp 无关的文件 | 不适用 H1；若行为影响桌面端协议/资源，仍做 H3 相关项 |

---

## 与 Cursor 自动化的关系

- **Project Rule** `.cursor/rules/composeapp-harness.mdc`：会话内始终提醒 H1–H3。
- **Hooks**（`.cursor/hooks.json`）：
  - 编辑 composeApp 相关文件前注入「先读架构文档」上下文；
  - Agent 将停止时注入「执行改后文档检查」提醒。

Hooks 是辅助；**即使 Hook 未触发，Agent 仍必须遵守本文件**。

---

## 快速对照

```text
改 composeApp 之前 → Read ARCHITECTURE.md (+ PLANNING.md)
按分层实现        → 遵守不变量，不破坏边界
改完之后          → 走 H3 清单，更新过时文档或声明无需更新
```
