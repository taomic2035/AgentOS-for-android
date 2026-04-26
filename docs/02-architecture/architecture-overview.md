# 架构总览

## 目的

定义 AgentOS 的模块边界、依赖方向、运行形态，作为所有实现工作的对齐基准。

## 整体形态

```
┌─────────────────────────────────────────────────────────────┐
│                       AgentOS App                    │
│                                                             │
│  Floating UI    Notification Tile    Settings UI            │
│       └──────────────┬───────────────────┘                  │
│                      ▼                                      │
│            AgentForegroundService                           │
│                      ▼                                      │
│              ┌──────────────┐                               │
│              │  Agent Core  │                               │
│              └──────────────┘                               │
│                      ▼                                      │
│        AgentAccessibilityService                            │
│                      ▼                                      │
│  LlmAdapter   SkillStore (Room)   HabitStore (Room)         │
└─────────────────────────────────────────────────────────────┘
```

## 模块清单

| 模块 | 类型 | 职责 | 主要语言 |
|---|---|---|---|
| `app` | Android Application | 入口 Activity / Service 装配 / 主题 / 首屏 | Kotlin |
| `core` | Kotlin JVM Library | 业务核心：IntentRouter / Planner / SkillResolver / ActionExecutor / 接口契约 | Kotlin（含少量 Java POJO） |
| `a11y` | Android Library | AccessibilityService 实现与 A11y 操作原语 | Kotlin |
| `skill` | Kotlin JVM Library | DSL 模型 / 解析 / 解释执行 / 内置 skill 包 | Kotlin |
| `llm` | Kotlin JVM Library | LLM 适配（OpenAI 兼容 / Anthropic / Local） | Kotlin |
| `data` | Android Library | Room 数据库与各 Store | Kotlin |
| `uikit` | Android Library | 浮窗 + Compose 组件库 | Kotlin |

模块依赖方向（由上至下，禁止反向依赖）：

```
app → uikit, a11y, data, skill, llm, core
uikit → core
a11y → core
data → core
skill → core
llm → core
core → ⌀（无依赖）
```

## 语言策略（来自 ADR 0005）

- **业务核心 / UI / Service**：默认 Kotlin
- **POJO / 第三方 SDK 桥接 / 纯算法工具类**：Java 可选
- 同一类内不混合
- 接口契约定在 Kotlin（`core` 模块），便于 `@Jvm*` 注解控制 Java 侧的可见 API

## 运行时形态

- 一个常驻 **AgentForegroundService**（前台通知，持有 AgentCore 单例）
- 一个 **AgentAccessibilityService**（系统进程内，由用户在系统设置开启后由系统启动）
- 浮窗 / 通知 Tile / Activity 都是 AgentForegroundService 的"客户端"，通过 LiveData / StateFlow 与之通信

## 关键设计原则

1. **核心解耦 Android**：`core` 模块不引入 Android 依赖，纯 Kotlin / Java，便于单测
2. **唯一控制出口**：所有写动作必经 `ActionExecutor`，便于审计 / 限流 / RiskGuard 拦截
3. **Skill 命中优先**：节省 token，绕过 LLM
4. **失败明确化**：每一步都有超时 + 兜底 + 日志
5. **可替换 LLM / ASR**：Adapter 模式

## 接口

- 与 PRD 的关系：实现 PRD 中列出的所有 F-* 项
- 与设计文档的关系：每个模块对应一篇 `03-design/*.md`
- 与测试文档的关系：每个模块的接口对应一个测试矩阵分组

## 风险

- AccessibilityService 被国内厂商系统限制 / 杀后台
- 不同厂商 LLM 的 tools 协议差异
- Skill DSL 表达力不足时的扩展难度

## 验收

- 模块依赖关系编译期校验通过（无环路）
- 每个模块都有 owner 和测试覆盖目标
