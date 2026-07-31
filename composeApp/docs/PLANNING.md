# composeApp 规划约束（Planning Constraints）

在编写或修改 composeApp 代码之前，规划阶段必须遵守本节。目标：**按现有架构增量演进，不重构架构、不引入平行体系**。

架构事实来源：[ARCHITECTURE.md](./ARCHITECTURE.md)。Harness 流程：[HARNESS.md](./HARNESS.md)。

---

## 1. 规划前必做

1. 阅读 [ARCHITECTURE.md](./ARCHITECTURE.md)（至少：分层、依赖方向、不变量、扩展检查清单）。
2. 明确改动落在哪一层（ui / MainStateHolder / base / helper / net / data / di）。
3. 若涉及多文件，先列出「拟改文件 + 所属层 + 依赖是否合法」，再动手写代码。

未完成以上步骤，不得开始改代码。

---

## 2. 规划原则

| 原则 | 要求 |
|---|---|
| 架构优先 | 方案必须能映射到现有分层；映射不上则调整方案，而不是改架构迁就方案 |
| 最小改动 | 只改完成需求所需的文件与 API；禁止顺手大重构 |
| 单一编排入口 | 跨 helper/base/net 的用例编排放在 `MainStateHolder`，不放在 Page |
| 边界清晰 | UI 无设备命令；data 无副作用；di 无业务 |
| 一致性 | 状态用现有 StateFlow/`mutableStateOf` 模式；DI 用 Koin；HTTP 用 Ktor |
| 可回滚 | 避免与需求无关的行为变更（窗口、单实例、主题、打包路径等） |

---

## 3. 方案模板（Agent 规划时建议输出）

```text
目标：
涉及层：
拟改文件：
  - path （层：…）— 原因
依赖方向检查：是否仅自上而下？
是否新增 route/DTO/DI 注册？
架构不变量：是否触及 ARCHITECTURE §7？如何避免违规？
文档影响：是否需要更新 ARCHITECTURE / AGENTS / README / i18n？
验证：compile / 真机冒烟 / 文案 key？
```

---

## 4. 禁止的规划

- “先在 Page 里写死 adb，以后再抽”  
- “新建一套 Repository/ViewModel 框架替代 MainStateHolder”  
- “把 server 源码拷进 composeApp 方便调试”  
- “为了快，UI 直接 ProcessBuilder”  
- “顺便换掉 Koin/Ktor/导航方案”  

若产品需求本身要求架构变更，必须先更新 [ARCHITECTURE.md](./ARCHITECTURE.md) 并经人工确认，再写代码。

---

## 5. 实现顺序建议

1. `data`（常量 / uistate / bean）  
2. `base` / `helper` / `net`（能力）  
3. `di`（注册）  
4. `MainStateHolder`（编排与状态）  
5. `ui`（绑定）  
6. `composeResources`（文案）  
7. 按 [HARNESS.md](./HARNESS.md) 做改后文档检查  

---

## 6. 完成定义（DoD）

- [ ] 依赖方向符合 ARCHITECTURE  
- [ ] 未违反 §7 不变量  
- [ ] 新公开行为有合理错误处理 / 用户提示（危险操作保留确认）  
- [ ] 可见文案已补 en + zh（至少）  
- [ ] `:composeApp:compileKotlinDesktop` 可通过（或等价编译）  
- [ ] 已执行 HARNESS「改后文档检查」并更新过时文档  
